package com.systemverktoy.app

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class MonitoringHistoryTest {
    private val day = LocalDate.parse("2026-11-11")
    private val zone = ZoneId.of("Europe/Oslo")
    private val start = dayBounds(day, zone).start
    private fun e(minute: Int, source: Source, type: String, pkg: String? = null) =
        RawEvent(time = start + minute * 60_000L, source = source, type = type, packageName = pkg)
    private fun report(events: List<RawEvent>, listener: Int?, monitoring: Int?, end: Int = 180) =
        dayReport(HistoryBatch(events.sortedBy { it.time }, start, listener?.let { start + it * 60_000L },
            monitoring?.let { start + it * 60_000L }), day, zone, start + end * 60_000L, "own")

    @Test fun `old imported usage is playback unknown and never invents a heartbeat gap`() {
        val result = report(listOf(e(0, Source.USAGE, "ACTIVITY_RESUMED", "example.player")), 120, 120)
        val historical = result.periods.first()
        assertTrue(historical.playbackUnknown)
        assertEquals("App åpen – avspilling ukjent", categoryText(historical))
        assertEquals(start + 120 * 60_000L, historical.endExclusive)
        assertFalse(result.periods.first { it.start == historical.endExclusive }.playbackUnknown)
        assertEquals(0L, result.total(Category.WATCHING))
        assertTrue(result.gaps.all { it.start >= start + 120 * 60_000L })
    }

    @Test fun `global first listener survives a daily window without listener events`() {
        val result = report(listOf(e(0, Source.USAGE, "ACTIVITY_RESUMED", "example.player"),
            e(0, Source.APP, "heartbeat")), -4000, -4000, 10)
        assertFalse(result.periods.single().playbackUnknown)
        assertEquals("App åpen, ingenting spilt", categoryText(result.periods.single()))
    }

    @Test fun `no listener ever means playback unknown even with an earlier heartbeat`() {
        val result = report(listOf(e(0, Source.USAGE, "ACTIVITY_RESUMED", "example.player")), null, null, 180)
        assertTrue(result.periods.single().playbackUnknown)
        assertTrue(result.gaps.isEmpty())
    }

    @Test fun `gaps before collection and sub-minute gaps are not warnings`() {
        val unknown = TimelinePeriod(start, start + 60_000, Category.UNKNOWN)
        val result = DayReport(listOf(unknown, unknown.copy(start = start + 120_000, endExclusive = start + 150_000)),
            emptyList(), null, true, false, false, monitoringStart = start + 120_000)
        assertTrue(result.gaps.isEmpty())
        assertEquals("Appen fulgte med fra kl. 00:02", monitoringText(result, day, zone))
        val real = result.copy(periods = result.periods + unknown.copy(start = start + 180_000, endExclusive = start + 240_000))
        assertEquals("Ufullstendig: 1 hull, 1 min", monitoringText(real, day, zone))
    }

    @Test fun `sub-minute listener gap is kept in raw evidence but hidden from timeline and warnings`() {
        val disconnected = e(10, Source.LISTENER, "onListenerDisconnected")
        val connected = disconnected.copy(time = disconnected.time + 30_000, type = "onListenerConnected")
        val result = report(listOf(e(0, Source.APP, "heartbeat"),
            e(0, Source.USAGE, "ACTIVITY_RESUMED", "example.player"), disconnected, connected), 0, 0, 15)
        assertTrue(result.gaps.isEmpty())
        assertFalse(result.periods.any { it.category == Category.UNKNOWN })
        assertTrue(result.raw.contains(disconnected))
        assertTrue(result.raw.contains(connected))
    }

    @Test fun `short system wake remains visible but does not set first on`() {
        val result = report(listOf(e(288, Source.USAGE, "SCREEN_INTERACTIVE"),
            e(288, Source.USAGE, "ACTIVITY_RESUMED", "com.android.tv.settings"),
            e(289, Source.USAGE, "SCREEN_NON_INTERACTIVE"),
            e(1020, Source.USAGE, "SCREEN_INTERACTIVE"),
            e(1020, Source.USAGE, "ACTIVITY_RESUMED", "example.player")), null, null, 1025)
        assertEquals(start + 1020 * 60_000L, result.firstOn)
        assertEquals("com.android.tv.settings", result.periods.first().packageName)
    }

    @Test fun `system-only wake is excluded through five minutes only after the screen turns off`() {
        for ((minutes, screenOff) in listOf(5 to true, 6 to true, 3 to false)) {
            val events = listOf(e(0, Source.USAGE, "SCREEN_INTERACTIVE"),
                e(0, Source.USAGE, "ACTIVITY_RESUMED", "com.google.android.apps.tv.launcherx").copy(activity = "launcher.HomeActivity")) +
                if (screenOff) listOf(e(minutes, Source.USAGE, "SCREEN_NON_INTERACTIVE")) else emptyList()
            val result = report(events, null, null, minutes + 1)
            assertEquals("$minutes $screenOff", if (minutes <= 5 && screenOff) null else start, result.firstOn)
            assertTrue(result.periods.isNotEmpty())
        }
    }

    @Test fun `boot wake never counts even if it lasts longer and opens an ordinary app`() {
        val result = report(listOf(e(-1, Source.USAGE, "DEVICE_STARTUP"),
            e(0, Source.USAGE, "SCREEN_INTERACTIVE"), e(0, Source.USAGE, "ACTIVITY_RESUMED", "example.player"),
            e(10, Source.USAGE, "SCREEN_NON_INTERACTIVE"), e(20, Source.USAGE, "SCREEN_INTERACTIVE"),
            e(20, Source.USAGE, "ACTIVITY_RESUMED", "example.player")), null, null, 25)
        assertEquals(start + 20 * 60_000L, result.firstOn)
        assertEquals(start, result.periods.first().start)
    }

    @Test fun `screen on beyond one minute after startup can qualify`() {
        val events = listOf(e(0, Source.USAGE, "DEVICE_STARTUP"), e(2, Source.USAGE, "SCREEN_INTERACTIVE"),
            e(2, Source.USAGE, "ACTIVITY_RESUMED", "example.player"))
        assertEquals(start + 120_000L, report(events, null, null, 10).firstOn)
    }

    @Test fun `observed 0448 reboot with delayed startup and activity gaps is not first on`() {
        fun at(second: Int, type: String, pkg: String? = null, activity: String? = null) =
            RawEvent(time = start + second * 1000L, source = Source.USAGE, type = type, packageName = pkg, activity = activity)
        val events = listOf(at(4 * 3600 + 48 * 60 + 12, "DEVICE_SHUTDOWN"),
            at(4 * 3600 + 48 * 60 + 47, "SCREEN_INTERACTIVE"),
            at(4 * 3600 + 48 * 60 + 47, "ACTIVITY_RESUMED", "com.android.tv.settings", "settings.system.FallbackHome"),
            at(4 * 3600 + 48 * 60 + 48, "DEVICE_STARTUP"),
            at(4 * 3600 + 48 * 60 + 50, "ACTIVITY_RESUMED", "com.google.android.apps.tv.launcherx", "home.HomeActivity"),
            at(4 * 3600 + 49 * 60, "ACTIVITY_RESUMED", "com.google.android.apps.tv.launcherx", "profile.ProfileChooserActivity"),
            at(4 * 3600 + 50 * 60 + 56, "SCREEN_NON_INTERACTIVE"),
            e(1020, Source.USAGE, "SCREEN_INTERACTIVE"), e(1020, Source.USAGE, "ACTIVITY_RESUMED", "example.player"))
        val result = report(events, null, null, 1025)
        assertEquals(start + 1020 * 60_000L, result.firstOn)
        assertTrue(result.periods.any { it.start < start + 5 * 3600_000L })
    }

    @Test fun `ordinary app or different launcher activity qualifies without a reboot`() {
        for ((pkg, activity) in listOf("example.player" to "Player", "com.google.android.apps.tv.launcherx" to "SearchActivity")) {
            val events = listOf(e(0, Source.USAGE, "SCREEN_INTERACTIVE"),
                e(0, Source.USAGE, "ACTIVITY_RESUMED", pkg).copy(activity = activity), e(1, Source.USAGE, "SCREEN_NON_INTERACTIVE"))
            assertEquals(start, report(events, null, null, 2).firstOn)
        }
    }
}
