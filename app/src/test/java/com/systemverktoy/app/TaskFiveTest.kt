package com.systemverktoy.app

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class TaskFiveTest {
    private val day = LocalDate.parse("2026-11-11")
    private val zone = ZoneId.of("Europe/Oslo")
    private val start = dayBounds(day, zone).start
    private fun event(minute: Int, type: String, pkg: String? = null, source: Source = Source.USAGE) =
        RawEvent(time = start + minute * 60_000L, source = source, type = type, packageName = pkg)
    private fun report(events: List<RawEvent>, end: Int) = dayReport(HistoryBatch(events, start - 60_000,
        firstListener = null, monitoringStart = null), day, zone, start + end * 60_000L, "own")

    @Test fun `carried midnight activity then standby and automatic reboot gives 0844`() {
        val events = listOf(event(-10, "ACTIVITY_RESUMED", "example.player"), event(278, "SCREEN_NON_INTERACTIVE"),
            event(288, "DEVICE_SHUTDOWN"), event(289, "SCREEN_INTERACTIVE"), event(289, "DEVICE_STARTUP"),
            event(289, "ACTIVITY_RESUMED", "com.android.tv.settings"), event(291, "SCREEN_NON_INTERACTIVE"),
            event(524, "SCREEN_INTERACTIVE"), event(524, "ACTIVITY_RESUMED", "example.player"))
        assertEquals(start + 524 * 60_000L, report(events, 540).firstOn)
    }
    @Test fun `uninterrupted carried activity says on since yesterday`() {
        val result = report(listOf(event(-10, "ACTIVITY_RESUMED", "example.player")), 600)
        assertNull(result.firstOn)
        assertTrue(result.onFromYesterday)
        assertEquals("på fra i går", firstOnText(result, zone))
    }
    @Test fun `dream stop while noninteractive closes reboot bout before normal use`() {
        val events = listOf(event(280, "DEVICE_STARTUP"), event(280, "SCREEN_INTERACTIVE"),
            event(281, "ACTIVITY_RESUMED", "com.android.tv.settings"),
            event(282, "DREAMING_STARTED", source = Source.DREAM), event(282, "SCREEN_NON_INTERACTIVE"),
            event(290, "DREAMING_STOPPED", source = Source.DREAM),
            event(420, "SCREEN_INTERACTIVE"), event(420, "ACTIVITY_RESUMED", "example.player"))
        assertEquals(start + 420 * 60_000L, report(events, 430).firstOn)
    }
    @Test fun `dream screen off is not standby on a carried day`() {
        val events = listOf(event(-10, "ACTIVITY_RESUMED", "example.player"),
            event(20, "DREAMING_STARTED", source = Source.DREAM), event(20, "SCREEN_NON_INTERACTIVE"))
        assertTrue(report(events, 30).onFromYesterday)
    }
}
