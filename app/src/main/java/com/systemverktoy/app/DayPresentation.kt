package com.systemverktoy.app

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal data class HistoryWindow(val floor: Long, val end: Long)
internal fun historyWindow(day: LocalDate, zone: ZoneId): HistoryWindow {
    val bounds = dayBounds(day, zone)
    return HistoryWindow(bounds.start - Duration.ofHours(48).toMillis(),
        bounds.endExclusive + Duration.ofMinutes(2).toMillis())
}

internal data class HistoryBatch(val events: List<RawEvent>, val oldest: Long?)
internal data class DayReport(
    val periods: List<TimelinePeriod>, val raw: List<RawEvent>,
    val firstOn: Long?, val hasActivity: Boolean, val beforeHistory: Boolean, val limitedContext: Boolean,
) {
    fun total(category: Category) = periods.filter { it.category == category }.sumOf { it.durationMillis }
    val watchingByApp get() = periods.filter { it.category == Category.WATCHING }
        .groupBy { it.packageName.orEmpty() }.mapValues { (_, rows) -> rows.sumOf { it.durationMillis } }
    val gaps get() = periods.filter { it.category == Category.UNKNOWN }
}

/** Bound database reads as well as replay. A boot/shutdown resets playback state;
 * retain the last listener observation before that checkpoint, since it is independent. */
internal fun timelineInput(events: List<RawEvent>, dayStart: Long): List<RawEvent> {
    val checkpoint = events.indexOfLast { it.time < dayStart && it.source == Source.USAGE &&
        it.type.substringBefore(' ') in setOf("DEVICE_STARTUP", "DEVICE_SHUTDOWN") }
    if (checkpoint < 0) return events
    val listener = events.take(checkpoint).lastOrNull { it.source == Source.LISTENER &&
        it.type in setOf("onListenerConnected", "onListenerDisconnected", "onDestroy") }
    return listOfNotNull(listener) + events.drop(checkpoint)
}

internal fun dayReport(batch: HistoryBatch, day: LocalDate, zone: ZoneId, now: Long, ownPackage: String): DayReport {
    val bounds = dayBounds(day, zone)
    val window = historyWindow(day, zone)
    val input = timelineInput(batch.events.filter { it.time in window.floor..minOf(window.end, now) }, bounds.start)
    val all = TimelineBuilder().build(input, day, zone, minOf(window.end, now))
    val dayEvents = input.filter { it.time in bounds && it.time <= now }
    val firstKnown = all.firstOrNull { it.category != Category.UNKNOWN }
    val firstOn = firstKnown?.start?.takeIf { it == bounds.start }
        ?: dayEvents.firstOrNull { it.type.substringBefore(' ') in setOf("SCREEN_INTERACTIVE", "SCREEN_ON", "DEVICE_STARTUP", "BOOT_COMPLETED") }?.time
        ?: firstKnown?.start
    return DayReport(all.filter { it.packageName != ownPackage }, input, firstOn,
        all.isNotEmpty() || dayEvents.any { it.type.substringBefore(' ') in setOf(
            "SCREEN_ON", "SCREEN_INTERACTIVE", "ACTIVITY_RESUMED", "heartbeat", "DREAMING_STARTED") },
        batch.oldest == null || batch.oldest >= bounds.endExclusive,
        !hasContextReset(input, bounds.start) && batch.oldest?.let { it < window.floor } == true)
}

/** Warning only: standby clears foreground/playback too, but a dream does not.
 * Batch simultaneous signals so dream start + screen off is independent of order. */
private fun hasContextReset(events: List<RawEvent>, dayStart: Long): Boolean {
    var dreaming = false
    for (batch in events.filter { it.time <= dayStart }.groupBy { it.time }.values) {
        var off = false
        for (event in batch) {
            val type = event.type.substringBefore(' ')
            when {
                event.source == Source.USAGE && type in setOf("DEVICE_STARTUP", "DEVICE_SHUTDOWN") -> return true
                type == "DREAMING_STARTED" || (type == "ACTIVITY_RESUMED" &&
                    event.packageName == "com.google.android.apps.tv.dreamx" && event.activity?.substringAfterLast('.') == "DreamActivity") -> dreaming = true
                type in setOf("DREAMING_STOPPED", "SCREEN_INTERACTIVE", "SCREEN_ON") -> dreaming = false
                type in setOf("SCREEN_NON_INTERACTIVE", "SCREEN_OFF") -> off = true
            }
        }
        if (off && !dreaming) return true
    }
    return false
}

internal fun selectedDay(selected: String?, today: LocalDate): LocalDate =
    minOf(selected?.let(LocalDate::parse) ?: today, today)

internal fun shiftedDay(selected: String?, today: LocalDate, step: Long): String? =
    minOf(selectedDay(selected, today).plusDays(step), today).takeUnless { it == today }?.toString()

internal fun appSession(periods: List<TimelinePeriod>, anchor: Long): List<TimelinePeriod> {
    val index = periods.indexOfFirst { anchor >= it.start && anchor < it.endExclusive }
    if (index < 0) return emptyList()
    val row = periods[index]
    if (row.packageName == null || row.category == Category.HOME) return listOf(row)
    fun joined(left: TimelinePeriod, right: TimelinePeriod) = left.endExclusive == right.start &&
        left.packageName == right.packageName && left.category in setOf(Category.WATCHING, Category.APP_OPEN, Category.HDMI) &&
        right.category in setOf(Category.WATCHING, Category.APP_OPEN, Category.HDMI) &&
        (left.category != Category.HDMI || left.title == right.title)
    var first = index
    var last = index
    while (first > 0 && joined(periods[first - 1], periods[first])) first--
    while (last < periods.lastIndex && joined(periods[last], periods[last + 1])) last++
    return periods.subList(first, last + 1)
}

internal fun sessionSignals(periods: List<TimelinePeriod>, events: List<RawEvent>): List<RawEvent> {
    if (periods.isEmpty()) return emptyList()
    val pkg = periods.first().packageName
    return (periods.flatMap { it.signals } + events.filter { it.time in periods.first().start..periods.last().endExclusive &&
        (it.packageName == null || it.packageName == pkg || it.source in setOf(Source.DREAM, Source.AUDIO, Source.LISTENER, Source.APP) ||
            (it.source == Source.USAGE && it.type.startsWith("ACTIVITY_RESUMED"))) })
        .distinct().sortedWith(compareBy<RawEvent> { it.time }.thenBy { it.id })
}

internal fun durationText(millis: Long): String {
    if (millis in 1..59_999) return "< 1 min"
    val minutes = millis / 60_000
    return if (minutes < 60) "$minutes min" else "${minutes / 60} t ${minutes % 60} min"
}

internal fun clockText(time: Long, zone: ZoneId): String = Instant.ofEpochMilli(time).atZone(zone)
    .format(DateTimeFormatter.ofPattern("HH:mm"))

internal fun periodTime(period: TimelinePeriod, zone: ZoneId): String {
    val a = Instant.ofEpochMilli(period.start).atZone(zone)
    val b = Instant.ofEpochMilli(period.endExclusive).atZone(zone)
    val pattern = if (a.offset != b.offset) "HH:mm xxx" else "HH:mm"
    val fmt = DateTimeFormatter.ofPattern(pattern)
    return a.format(fmt) + " – " + if (period.ongoing) "nå" else b.format(fmt)
}

internal fun categoryText(period: TimelinePeriod): String = when (period.category) {
    Category.WATCHING -> "Sett på"
    Category.APP_OPEN -> if (period.signals.lastOrNull { it.source == Source.MEDIA && it.packageName == period.packageName }
            ?.state?.startsWith("STATE_PAUSED") == true) "App åpen, pauset" else "App åpen, ingenting spilt"
    Category.IDLE -> "Sto bare på"
    Category.HOME -> "Startskjerm – teller ikke"
    Category.UNKNOWN -> "Ukjent – mangler logg"
    Category.HDMI -> "HDMI – egen sum"
    Category.OFF -> "Av"
}
