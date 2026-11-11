package com.systemverktoy.app

import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId

enum class Category { WATCHING, APP_OPEN, IDLE, HOME, UNKNOWN, HDMI, OFF }

data class TimelinePeriod(
    val start: Long,
    val endExclusive: Long,
    val category: Category,
    val packageName: String? = null,
    val title: String? = null,
    val ongoing: Boolean = false,
    val signals: List<RawEvent> = emptyList(),
) {
    val durationMillis: Long get() = endExclusive - start
}

/**
 * Pure replay of persisted signals; no Android APIs, database writes or wall clock reads.
 * Supply retained history BEFORE the day as well as all observations through [now].
 * Replay before clipping preserves state and the 60-second rule across midnight.
 * OFF is used internally but omitted from the returned, non-overlapping periods.
 */
class TimelineBuilder {
    fun build(events: List<RawEvent>, day: LocalDate, zone: ZoneId, now: Long): List<TimelinePeriod> {
        val bounds = dayBounds(day, zone)
        val end = minOf(bounds.endExclusive, now)
        if (end <= bounds.start) return emptyList()
        val ordered = events.filter { it.time <= now && relevant(it) }
            .sortedWith(compareBy<RawEvent> { it.time }.thenBy { it.id })
        if (ordered.isEmpty()) return emptyList()
        val buffering = buffersBeforePlaying(ordered)
        val times = sortedSetOf(now)
        ordered.forEach {
            times += it.time
            if (it.time + HEARTBEAT_GAP < now) times += it.time + HEARTBEAT_GAP
        }
        val grouped = ordered.groupBy { it.time }
        val state = Signals()
        val slices = mutableListOf<Slice>()
        val points = times.toList()
        for (index in points.indices) {
            val time = points[index]
            val batch = grouped[time].orEmpty()
            // Expire BEFORE applying new observations, so a late heartbeat cannot
            // resurrect playback last observed before a log gap. USAGE remains valid.
            state.expire(time, batch.any { it.source == Source.APP && it.type == "heartbeat" })
            batch.forEach { state.accept(it, it in buffering) }
            state.finishBatch()
            slices += state.slice(time, points.getOrElse(index + 1) { time })
        }
        suppressPreviews(slices)
        val live = slices.last()
        val result = mutableListOf<TimelinePeriod>()
        for (slice in slices) {
            val start = maxOf(slice.start, bounds.start)
            val stop = minOf(slice.end, end)
            if (stop <= start || slice.category == Category.OFF) continue
            val period = TimelinePeriod(start, stop, slice.category, slice.pkg, slice.title,
                ongoing = stop == now && now < bounds.endExclusive && slice.category == live.category &&
                    slice.pkg == live.pkg && slice.title == live.title,
                signals = slice.evidence.sortedWith(EVENT_ORDER))
            val previous = result.lastOrNull()
            if (previous != null && previous.endExclusive == start &&
                previous.category == period.category && previous.packageName == period.packageName &&
                previous.title == period.title) {
                result[result.lastIndex] = previous.copy(endExclusive = stop, ongoing = period.ongoing,
                    signals = (previous.signals + period.signals).distinct().sortedWith(EVENT_ORDER))
            } else result += period
        }
        return result
    }

    private enum class Playback { NONE, BUFFERING, PLAYING, AUDIO }
    private data class Slice(
        val start: Long, val end: Long, var category: Category,
        val pkg: String?, val title: String?, val playback: Playback, val evidence: List<RawEvent>,
    )

    private class Signals {
        var interactive: Boolean? = null
        var dreaming = false
        var disconnected = false
        var heartbeatAnchor: Long? = null
        var expired = false
        var foreground: Pair<String, String?>? = null
        val media = mutableMapOf<String, Pair<RawEvent, Boolean>>()
        var audio = false
        val evidence = mutableMapOf<String, RawEvent>()

        fun clearPlayback() { media.clear(); audio = false }
        fun clearApps() { foreground = null; clearPlayback() }

        fun expire(time: Long, heartbeatAtBoundary: Boolean) {
            if (!expired && interactive == true && !dreaming &&
                heartbeatAnchor?.let { time > it + HEARTBEAT_GAP ||
                    (time == it + HEARTBEAT_GAP && !heartbeatAtBoundary) } == true) {
                expired = true
                clearPlayback()
            }
        }

        fun screenOn(time: Long, restart: Boolean = false) {
            if (restart) { clearApps(); evidence.clear() }
            if (restart || interactive != true || dreaming) heartbeatAnchor = time
            interactive = true
            dreaming = false
            expired = false
        }

        fun accept(event: RawEvent, buffer: Boolean) {
            val type = event.type.substringBefore(' ')
            val pkg = event.packageName
            when {
                type in setOf("DEVICE_STARTUP", "BOOT_COMPLETED") -> {
                    screenOn(event.time, restart = true)
                    evidence["power"] = event
                }
                type in setOf("SCREEN_INTERACTIVE", "SCREEN_ON") -> {
                    screenOn(event.time)
                    evidence["power"] = event
                }
                type in setOf("SCREEN_NON_INTERACTIVE", "SCREEN_OFF", "DEVICE_SHUTDOWN") -> {
                    interactive = false
                    if (type == "DEVICE_SHUTDOWN") dreaming = false
                    evidence["power"] = event
                }
                type == "DREAMING_STARTED" || (type == "ACTIVITY_RESUMED" && isDream(event)) -> {
                    dreaming = true
                    evidence["dream"] = event
                }
                type == "DREAMING_STOPPED" -> {
                    // Ending a dream does not prove the screen woke up. A real
                    // wake has its own SCREEN_INTERACTIVE / SCREEN_ON signal.
                    if (interactive == true) screenOn(event.time) else dreaming = false
                    evidence["dream"] = event
                }
                event.source == Source.LISTENER -> {
                    when (type) {
                        "onListenerConnected" -> disconnected = false
                        "onListenerDisconnected", "onDestroy" -> {
                            disconnected = true
                            clearPlayback()
                        }
                    }
                    evidence["listener"] = event
                }
                event.source == Source.APP && type == "heartbeat" -> {
                    if (interactive == null) interactive = true
                    heartbeatAnchor = event.time
                    expired = false
                    evidence["heartbeat"] = event
                }
                event.source == Source.USAGE && type == "ACTIVITY_RESUMED" && pkg != null -> {
                    if (interactive == null) { interactive = true; heartbeatAnchor = event.time }
                    foreground = pkg to event.activity
                    evidence["foreground"] = event
                }
                event.source == Source.USAGE && type in setOf("ACTIVITY_PAUSED", "ACTIVITY_STOPPED") -> {
                    // Lifecycle interruptions (including filtered overlays) do
                    // not replace the last resumed app. Neither does a late
                    // stop for Menu after Player has already resumed.
                    evidence["activity"] = event
                }
                event.source == Source.MEDIA && pkg != null -> {
                    if (type in setOf("SESSION_REMOVED", "SESSION_DESTROYED", "SESSION_UNOBSERVED")) {
                        media.remove(pkg)
                    } else media[pkg] = event to buffer
                    evidence["media:$pkg"] = event
                }
                event.source == Source.AUDIO -> {
                    audio = Regex("media_active=(\\d+)").find(event.state.orEmpty())
                        ?.groupValues?.get(1)?.toIntOrNull()?.let { it > 0 } == true
                    evidence["audio"] = event
                }
            }
        }

        fun finishBatch() {
            // Dream and screen-off can share a timestamp, in either database order.
            if (interactive == false && !dreaming) clearApps()
        }

        fun slice(start: Long, end: Long): Slice {
            val pkg = foreground?.first
            val session = media[pkg]
            val status = session?.first?.state?.substringBefore(' ')
            val playback = when {
                status == "STATE_PLAYING" -> Playback.PLAYING
                status == "STATE_BUFFERING" && session?.second == true -> Playback.BUFFERING
                session == null && audio && media.none { (other, signal) ->
                    other != pkg && signal.first.state?.substringBefore(' ') == "STATE_PLAYING"
                } -> Playback.AUDIO
                else -> Playback.NONE
            }
            val on = interactive == true || dreaming
            val category = when {
                on && (disconnected || expired) -> Category.UNKNOWN
                dreaming -> Category.IDLE
                interactive != true -> Category.OFF
                pkg == TVX -> Category.HDMI
                pkg != null && pkg != HOME && playback != Playback.NONE -> Category.WATCHING
                pkg == HOME -> Category.HOME
                pkg != null -> Category.APP_OPEN
                // Healthy screen-on time without a known app is omitted, not
                // reported as missing logging. Power/liveness remain tracked.
                else -> Category.OFF
            }
            val showApp = category in setOf(Category.HDMI, Category.WATCHING, Category.HOME, Category.APP_OPEN)
            return Slice(start, end, category, pkg.takeIf { showApp },
                session?.first?.title?.takeIf { showApp }, playback, evidence.values.toList())
        }
    }

    private fun suppressPreviews(slices: List<Slice>) {
        var index = 0
        while (index < slices.size) {
            val first = slices[index]
            if (first.category != Category.WATCHING || first.playback == Playback.AUDIO) { index++; continue }
            var end = index
            var playing = 0L
            while (end < slices.size && slices[end].category == Category.WATCHING &&
                slices[end].pkg == first.pkg && slices[end].playback != Playback.AUDIO) {
                // Buffering belongs to the following PLAYING run, never a bridge
                // that adds two short PLAYING runs up to the preview threshold.
                if (playing > 0 && slices[end].playback == Playback.BUFFERING) break
                if (slices[end].playback == Playback.PLAYING) playing += slices[end].end - slices[end].start
                end++
            }
            if (playing < MIN_PLAYING) for (i in index until end) slices[i].category = Category.APP_OPEN
            index = end
        }
    }

    private fun buffersBeforePlaying(events: List<RawEvent>): Set<RawEvent> {
        val next = mutableMapOf<String, Boolean>()
        val result = mutableSetOf<RawEvent>()
        for (event in events.asReversed()) {
            val pkg = event.packageName ?: continue
            if (event.source != Source.MEDIA) continue
            val status = event.state?.substringBefore(' ')
            when {
                event.type in setOf("SESSION_REMOVED", "SESSION_DESTROYED", "SESSION_UNOBSERVED") -> next[pkg] = false
                status == "STATE_BUFFERING" -> if (next[pkg] == true) result += event
                else -> next[pkg] = status == "STATE_PLAYING"
            }
        }
        return result
    }

    companion object {
        private val EVENT_ORDER = compareBy<RawEvent> { it.time }.thenBy { it.id }
        private const val HOME = "com.google.android.apps.tv.launcherx"
        private const val TVX = "com.sony.dtv.tvx"
        private val HEARTBEAT_GAP = Duration.ofMinutes(35).toMillis()
        private val MIN_PLAYING = Duration.ofSeconds(60).toMillis()
        private fun isDream(event: RawEvent) = event.packageName == "com.google.android.apps.tv.dreamx" &&
            event.activity?.substringAfterLast('.') == "DreamActivity"

        private fun relevant(event: RawEvent): Boolean {
            val pkg = event.packageName.orEmpty()
            val type = event.type.substringBefore(' ')
            // Global power events sometimes carry a system package: keep them.
            if (event.source == Source.USAGE && type in setOf("SCREEN_INTERACTIVE", "SCREEN_NON_INTERACTIVE",
                    "DEVICE_STARTUP", "DEVICE_SHUTDOWN")) return true
            if (pkg in setOf("android", "com.android.systemui", "com.google.android.gsf",
                    "com.google.android.tungsten.setupwraith") || (pkg.startsWith("com.sony.dtv.") && pkg != TVX)) return false
            return when (event.source) {
                Source.USAGE -> type in setOf("ACTIVITY_RESUMED", "ACTIVITY_PAUSED", "ACTIVITY_STOPPED")
                Source.APP -> type in setOf("heartbeat", "BOOT_COMPLETED", "SCREEN_ON", "SCREEN_OFF")
                Source.LISTENER -> type in setOf("onListenerConnected", "onListenerDisconnected", "onDestroy")
                Source.DREAM -> type in setOf("DREAMING_STARTED", "DREAMING_STOPPED")
                Source.MEDIA, Source.AUDIO -> true
            }
        }
    }
}
