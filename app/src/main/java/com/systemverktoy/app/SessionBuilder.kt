package com.systemverktoy.app

internal data class ViewingSession(val packageName: String, val periods: List<TimelinePeriod>, val cumulative: Float = 0f) {
    val start get() = periods.first().start
    val end get() = periods.last().endExclusive
    val watching get() = periods.filter { it.category == Category.WATCHING }.sumOf { it.durationMillis }
    val unknown get() = periods.any { it.playbackUnknown }
}

/** Keep intervening evidence in details, but only actual playback contributes to the total. */
internal fun viewingSessions(periods: List<TimelinePeriod>): List<ViewingSession> {
    val rows = periods.sortedBy { it.start }
    val sessions = mutableListOf<ViewingSession>()
    var index = 0
    while (index < rows.size) {
        val first = rows[index]
        val pkg = first.packageName
        if (pkg == null || first.category !in setOf(Category.APP_OPEN, Category.WATCHING) ||
            first.category == Category.APP_OPEN && !first.playbackUnknown && first.durationMillis > 300_000) { index++; continue }
        var last = index
        var scan = index + 1
        while (scan < rows.size) {
            val next = rows[scan]
            if (next.category in setOf(Category.UNKNOWN, Category.HDMI, Category.OFF) ||
                next.category == Category.WATCHING && next.packageName != pkg ||
                next.playbackUnknown != first.playbackUnknown && next.packageName == pkg ||
                next.category == Category.APP_OPEN && next.durationMillis > 300_000 && next.packageName == pkg ||
                next.start - rows[last].endExclusive > 300_000) break
            if (next.packageName == pkg && next.category in setOf(Category.APP_OPEN, Category.WATCHING) &&
                next.playbackUnknown == first.playbackUnknown) last = scan
            scan++
        }
        val session = ViewingSession(pkg, rows.subList(index, last + 1))
        if (session.watching >= 60_000 || session.unknown) sessions += session
        index = last + 1
    }
    val total = sessions.sumOf { it.watching }
    var sum = 0L
    return sessions.map { sum += it.watching; it.copy(cumulative = if (total == 0L) 0f else sum.toFloat() / total) }
}
