package com.systemverktoy.app

import org.junit.Assert.*
import org.junit.Test

class SessionBuilderTest {
    private fun row(a: Int, b: Int, cat: Category = Category.WATCHING, pkg: String? = "example.player", unknown: Boolean = false) =
        TimelinePeriod(a * 60_000L, b * 60_000L, cat, pkg, playbackUnknown = unknown)
    @Test fun `pause and short home detour join and count only watching`() {
        val rows = listOf(row(0, 2), row(2, 4, Category.APP_OPEN), row(4, 5, Category.HOME, "home"), row(5, 8))
        val session = viewingSessions(rows).single()
        assertEquals(rows, session.periods)
        assertEquals(300_000L, session.watching)
        assertEquals(1f, session.cumulative)
    }
    @Test fun `five minutes joins but a longer break and another player split`() {
        assertEquals(1, viewingSessions(listOf(row(0, 2), row(7, 9))).size)
        assertEquals(2, viewingSessions(listOf(row(0, 2), row(8, 10))).size)
        assertEquals(3, viewingSessions(listOf(row(0, 2), row(2, 3, pkg = "example.other"), row(3, 5))).size)
    }
    @Test fun `long pause and a log hole split sessions`() {
        assertEquals(2, viewingSessions(listOf(row(0, 2), row(2, 8, Category.APP_OPEN), row(8, 10))).size)
        assertEquals(2, viewingSessions(listOf(row(0, 2), row(2, 3, Category.UNKNOWN, null), row(3, 5))).size)
    }
    @Test fun `cumulative fractions use watched minutes with full final row`() {
        val sessions = viewingSessions(listOf(row(0, 1), row(10, 13), row(20, 24)))
        assertEquals(listOf(0.125f, 0.5f, 1f), sessions.map { it.cumulative })
    }
    @Test fun `empty HDMI and known open days have no viewing sessions`() {
        assertTrue(viewingSessions(emptyList()).isEmpty())
        assertTrue(viewingSessions(listOf(row(0, 8, Category.HDMI), row(8, 9, Category.APP_OPEN))).isEmpty())
    }
    @Test fun `unknown playback stays visible without contributing`() {
        val sessions = viewingSessions(listOf(row(0, 3, Category.APP_OPEN, unknown = true), row(3, 5)))
        assertEquals(2, sessions.size)
        assertEquals(0L, sessions.first().watching)
        assertEquals(0f, sessions.first().cumulative)
        assertEquals(1f, sessions.last().cumulative)
    }
}
