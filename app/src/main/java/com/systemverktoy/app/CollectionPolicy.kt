package com.systemverktoy.app

import java.time.Duration

val HEARTBEAT_INTERVAL_MILLIS: Long = Duration.ofMinutes(15).toMillis()
private val HEARTBEAT_MIN_SPACING = Duration.ofMinutes(14).toMillis()

fun heartbeatDue(last: Long?, now: Long): Boolean =
    last == null || now < last || now - last >= HEARTBEAT_MIN_SPACING

data class MediaState(val status: Int?, val title: String?)

// Track sessions independently, including multiple sessions from the same package.
class PlaybackChanges<Session> {
    private val media = mutableMapOf<Session, MediaState>()
    private var audio: String? = null

    fun mediaChanged(session: Session, state: MediaState): Boolean = media.put(session, state) != state
    fun remove(session: Session) { media.remove(session) }
    fun audioChanged(state: String): Boolean {
        val changed = audio != state
        audio = state
        return changed
    }
    fun clear() { media.clear(); audio = null }
}

data class UsageWindow(val start: Long, val end: Long, val full: Boolean)

fun usageWindow(now: Long, copiedThrough: Long?, lastFullScan: Long?): UsageWindow {
    val retentionStart = now - Duration.ofDays(14).toMillis()
    val day = Duration.ofDays(1).toMillis()
    // A daily full scan also refreshes the diagnostic "oldest Android event".
    val full = copiedThrough == null || copiedThrough > now || lastFullScan == null ||
        lastFullScan > now || now - lastFullScan >= day
    val start = if (full) retentionStart else maxOf(retentionStart, copiedThrough!! - day)
    return UsageWindow(start, now, full)
}
