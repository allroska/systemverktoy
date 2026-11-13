package com.systemverktoy.app

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import kotlin.math.roundToLong

internal fun weekStart(day: LocalDate): LocalDate = day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
internal fun shiftedWeek(start: LocalDate, step: Long, today: LocalDate, firstDay: LocalDate): LocalDate =
    start.plusWeeks(step).coerceIn(minOf(weekStart(firstDay), weekStart(today)), weekStart(today))
internal enum class HourCell { EMPTY, WATCHING, HDMI, UNKNOWN, FUTURE }
internal data class WeekDay(val day: LocalDate, val minutes: Long, val cells: List<HourCell>, val future: Boolean)
internal data class WeekApp(val packageName: String, val minutes: Long, val percent: Long)
internal data class WeekAppLine(val apps: List<WeekApp>) {
    val minutes get() = apps.sumOf { it.minutes }
    val percent get() = apps.sumOf { it.percent }
}
internal data class WeekSummary(val start: LocalDate, val days: List<WeekDay>, val average: Long?,
    val previousAverage: Long?, val apps: List<WeekApp>, val hdmiMillis: Long, val ongoing: Boolean) {
    val number get() = start.get(WeekFields.ISO.weekOfWeekBasedYear())
    val total get() = days.sumOf { it.minutes }
    val appLines get() = if (apps.size <= 4) apps.map { WeekAppLine(listOf(it)) }
        else apps.take(3).map { WeekAppLine(listOf(it)) } + WeekAppLine(apps.drop(3))
}

/** Uses exactly the same dayReport replay and rounded day totals as Overview. */
internal fun weekSummary(batch: HistoryBatch, selected: LocalDate, zone: ZoneId, now: Long,
    ownPackage: String, calculate: (LocalDate) -> DayReport = { dayReport(batch, it, zone, now, ownPackage) }): WeekSummary {
    val start = weekStart(selected)
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val monitoringDay = batch.monitoringStart?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
    fun eligible(day: LocalDate) = monitoringDay != null && day >= monitoringDay && day <= today
    val reports = (0L..6L).associate { offset -> val date = start.plusDays(offset); date to calculate(date) }
    val days = reports.map { (date, report) -> WeekDay(date,
        if (eligible(date)) report.watchingMinutes() else 0, hourCells(date, report.periods, zone, now), date > today) }
    val counted = days.filter { eligible(it.day) }
    val previous = (1L..7L).map { start.minusDays(it) }.filter(::eligible)
    val durations = reports.filterKeys(::eligible).values.flatMap { it.watchingByApp.entries }
        .groupBy({ it.key }, { it.value }).mapValues { it.value.sum() }.entries.sortedByDescending { it.value }
    val minutes = allocateMinutes(durations.map { it.value }, days.sumOf { it.minutes })
    val apps = durations.mapIndexed { i, entry -> WeekApp(entry.key, minutes[i],
        if (durations.sumOf { it.value } == 0L) 0 else (100.0 * entry.value / durations.sumOf { it.value }).roundToLong()) }
    return WeekSummary(start, days, counted.takeIf { it.isNotEmpty() }?.let { (it.sumOf { d -> d.minutes }.toDouble() / it.size).roundToLong() },
        previous.takeIf { it.isNotEmpty() }?.let { (it.sumOf { d -> calculate(d).watchingMinutes() }.toDouble() / it.size).roundToLong() },
        apps, reports.filterKeys(::eligible).values.sumOf { it.total(Category.HDMI) }, today <= start.plusDays(6))
}

internal fun hourCells(day: LocalDate, periods: List<TimelinePeriod>, zone: ZoneId, now: Long): List<HourCell> {
    val totals = Array(24) { mutableMapOf<Category, Long>() }
    var cursor = day.atStartOfDay(zone)
    val end = day.plusDays(1).atStartOfDay(zone)
    // Walk actual hours: both occurrences of 02 map to one column in autumn;
    // the missing spring hour has no interval and remains empty.
    while (cursor < end) {
        val next = cursor.plusHours(1)
        val a = cursor.toInstant().toEpochMilli()
        val b = minOf(next.toInstant().toEpochMilli(), now)
        periods.forEach { p ->
            val overlap = (minOf(b, p.endExclusive) - maxOf(a, p.start)).coerceAtLeast(0)
            val cell = totals[cursor.hour]
            cell[p.category] = (cell[p.category] ?: 0) + overlap
        }
        cursor = next
    }
    val localNow = Instant.ofEpochMilli(now).atZone(zone)
    return (0..23).map { hour ->
        if (day > localNow.toLocalDate() || day == localNow.toLocalDate() && hour > localNow.hour) HourCell.FUTURE
        else when {
            (totals[hour][Category.WATCHING] ?: 0) >= 60_000 -> HourCell.WATCHING
            (totals[hour][Category.HDMI] ?: 0) >= 60_000 -> HourCell.HDMI
            (totals[hour][Category.UNKNOWN] ?: 0) >= 60_000 -> HourCell.UNKNOWN
            else -> HourCell.EMPTY
        }
    }
}
