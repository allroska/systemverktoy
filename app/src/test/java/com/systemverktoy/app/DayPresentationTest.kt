package com.systemverktoy.app

import org.junit.Assert.*
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId

class DayPresentationTest {
    private val day = LocalDate.of(2026, 11, 11)
    private val zone = ZoneId.of("Europe/Oslo")
    private val start = dayBounds(day, zone).start
    private val own = "com.systemverktoy.app"
    private fun event(minutes: Long, source: Source, type: String, pkg: String? = null, state: String? = null) =
        RawEvent(time = start + minutes * 60_000, source = source, type = type, packageName = pkg, state = state)

    @Test fun `today follows midnight while a chosen historical date stays fixed`() {
        assertEquals(day, selectedDay(null, day))
        assertEquals(day.plusDays(1), selectedDay(null, day.plusDays(1)))
        assertEquals(day.minusDays(1), selectedDay(day.minusDays(1).toString(), day.plusDays(1)))
        assertNull(shiftedDay(null, day, 1))
        assertNull(shiftedDay(day.minusDays(1).toString(), day, 1))
        assertEquals(day.minusDays(1).toString(), shiftedDay(null, day, -1))
    }

    @Test fun `database window is bounded even on both daylight saving days`() {
        for (date in listOf(day, LocalDate.parse("2026-03-29"), LocalDate.parse("2026-10-25"))) {
            val bounds = dayBounds(date, zone)
            val window = historyWindow(date, zone)
            assertEquals(Duration.ofHours(48).toMillis(), bounds.start - window.floor)
            assertEquals(120_000L, window.end - bounds.endExclusive)
        }
    }

    @Test fun `checkpoint trimming keeps listener status and all events from last reset`() {
        val events = listOf(event(-100, Source.USAGE, "ACTIVITY_RESUMED", "example.old"),
            event(-90, Source.LISTENER, "onListenerDisconnected"), event(-60, Source.USAGE, "DEVICE_STARTUP (27)"),
            event(-50, Source.USAGE, "ACTIVITY_RESUMED", "example.player"), event(-40, Source.APP, "BOOT_COMPLETED"))
        assertEquals(events.drop(1), timelineInput(events, start))
        val result = dayReport(HistoryBatch(events, events.first().time), day, zone, start + 10 * 60_000, own)
        assertEquals(Category.UNKNOWN, result.periods.single().category)
        assertFalse(result.limitedContext)
    }

    @Test fun `own app is hidden after replay without extending previous foreground or open total`() {
        val events = listOf(event(0, Source.APP, "SCREEN_ON"), event(0, Source.APP, "heartbeat"),
            event(0, Source.USAGE, "ACTIVITY_RESUMED", "example.player"),
            event(10, Source.USAGE, "ACTIVITY_RESUMED", own), event(20, Source.USAGE, "ACTIVITY_RESUMED", "example.player"))
        val result = dayReport(HistoryBatch(events, start), day, zone, start + 30 * 60_000, own)
        assertEquals(20 * 60_000L, result.total(Category.APP_OPEN))
        assertFalse(result.periods.any { it.packageName == own })
        assertEquals(start + 10 * 60_000, result.periods.first().endExclusive)
        assertEquals(1, appSession(result.periods, start).size)
        assertTrue(result.hasActivity)
        assertEquals(start, result.firstOn)
    }

    @Test fun `own app alone cannot become watched time or the TV off empty state`() {
        val events = listOf(event(0, Source.APP, "heartbeat"), event(0, Source.USAGE, "ACTIVITY_RESUMED", own),
            event(0, Source.AUDIO, "PLAYBACK_CONFIG_CHANGED", state = "media_active=1"))
        val result = dayReport(HistoryBatch(events, start), day, zone, start + 10 * 60_000, own)
        assertTrue(result.periods.isEmpty())
        assertTrue(result.hasActivity)
        assertEquals(0L, result.total(Category.APP_OPEN))
        assertEquals(0L, result.total(Category.WATCHING))
    }

    @Test fun `all sums exclude home and HDMI from watching and keep HDMI separate`() {
        val rows = listOf(Category.WATCHING, Category.APP_OPEN, Category.HOME, Category.HDMI, Category.IDLE)
            .mapIndexed { i, cat -> TimelinePeriod(i * 60_000L, (i + 1) * 60_000L, cat, "example.app") }
        val report = DayReport(rows, emptyList(), null, true, false, false)
        assertEquals(60_000L, report.total(Category.WATCHING))
        assertEquals(60_000L, report.total(Category.HDMI))
        assertEquals(mapOf("example.app" to 60_000L), report.watchingByApp)
    }

    @Test fun `details group open watching paused and keep prior and closing raw evidence`() {
        val resume = event(-1, Source.USAGE, "ACTIVITY_RESUMED", "example.player")
        val closing = event(3, Source.USAGE, "ACTIVITY_RESUMED", "example.other")
        val rows = listOf(TimelinePeriod(start, start + 60_000, Category.APP_OPEN, "example.player", signals = listOf(resume)),
            TimelinePeriod(start + 60_000, start + 120_000, Category.WATCHING, "example.player"),
            TimelinePeriod(start + 120_000, start + 180_000, Category.APP_OPEN, "example.player"),
            TimelinePeriod(start + 180_000, start + 240_000, Category.APP_OPEN, "example.other"))
        val session = appSession(rows, start + 60_000)
        assertEquals(rows.take(3), session)
        assertEquals(listOf(resume, closing), sessionSignals(session, listOf(closing)))
    }

    @Test fun `limited history and dates before installation never assert a complete empty day`() {
        val result = dayReport(HistoryBatch(emptyList(), start - Duration.ofDays(60).toMillis()), day, zone, start + 60_000, own)
        assertTrue(result.limitedContext)
        val missing = dayReport(HistoryBatch(emptyList(), dayBounds(day, zone).endExclusive), day, zone, start + 60_000, own)
        assertTrue(missing.beforeHistory)
    }

    @Test fun `lookahead preserves midnight preview classification but ignores events beyond its cap`() {
        val midnight = dayBounds(day, zone).endExclusive
        val events = listOf(event(0, Source.APP, "heartbeat"), event(0, Source.USAGE, "ACTIVITY_RESUMED", "example.player"),
            event(0, Source.MEDIA, "SESSION_ADDED", "example.player", "STATE_PLAYING"))
            .map { it.copy(time = midnight - 30_000) }
        val report = dayReport(HistoryBatch(events + event(0, Source.APP, "BOOT_COMPLETED").copy(time = midnight + 180_000), events.first().time),
            day, zone, midnight + 300_000, own)
        assertEquals(Category.WATCHING, report.periods.single().category)
        assertEquals(30_000L, report.periods.single().durationMillis)
        assertTrue(report.raw.none { it.time > historyWindow(day, zone).end })
    }

    @Test fun `Netflix across midnight takes priority over the later 1700 power on`() {
        val events = listOf(event(-30, Source.USAGE, "ACTIVITY_RESUMED", "com.netflix.ninja"),
            event(-30, Source.MEDIA, "SESSION_ADDED", "com.netflix.ninja", "STATE_PLAYING")) +
            listOf(-30L, -15L, 0L, 15L, 30L).map { event(it, Source.APP, "heartbeat") } +
            listOf(event(40, Source.USAGE, "SCREEN_NON_INTERACTIVE (16)"),
                event(1020, Source.USAGE, "SCREEN_INTERACTIVE (15)"), event(1020, Source.APP, "heartbeat"),
                event(1021, Source.USAGE, "ACTIVITY_RESUMED", "example.home"))
        val ordered = events.sortedBy { it.time }
        val report = dayReport(HistoryBatch(ordered, ordered.first().time), day, zone, start + 1040 * 60_000, own)
        assertEquals(start, report.firstOn)
        assertEquals(start, report.periods.first().start)
        assertEquals(40 * 60_000L, report.periods.first().durationMillis)
    }

    @Test fun `first known period is the fallback when no power on signal exists`() {
        val events = listOf(event(5, Source.USAGE, "ACTIVITY_RESUMED", "example.player"))
        val report = dayReport(HistoryBatch(events, start), day, zone, start + 15 * 60_000, own)
        assertEquals(start + 5 * 60_000, report.firstOn)
    }

    @Test fun `unknown periods are not a fallback for first power on`() {
        val events = listOf(event(0, Source.APP, "heartbeat"))
        val report = dayReport(HistoryBatch(events, start), day, zone, start + 50 * 60_000, own)
        assertEquals(Category.UNKNOWN, report.periods.single().category)
        assertNull(report.firstOn)
    }

    @Test fun `ordinary standby is a complete context for the warning without trimming replay`() {
        for (off in listOf(event(-60, Source.USAGE, "SCREEN_NON_INTERACTIVE (16)"), event(-60, Source.APP, "SCREEN_OFF"))) {
            val events = listOf(event(-100, Source.APP, "heartbeat"),
                event(-100, Source.USAGE, "ACTIVITY_RESUMED", "example.player"), off)
            val report = dayReport(HistoryBatch(events, start - Duration.ofDays(60).toMillis()), day, zone, start + 15 * 60_000, own)
            assertFalse(report.limitedContext)
            assertEquals(events, report.raw)
        }
    }

    @Test fun `screen non-interactive during a dream never clears the limited context warning`() {
        for (dream in listOf(event(-70, Source.DREAM, "DREAMING_STARTED"),
            event(-70, Source.USAGE, "ACTIVITY_RESUMED", "com.google.android.apps.tv.dreamx").copy(activity = "DreamActivity"))) {
            val events = listOf(event(-80, Source.APP, "heartbeat"), dream,
                event(-60, Source.USAGE, "SCREEN_NON_INTERACTIVE (16)"))
            val report = dayReport(HistoryBatch(events, start - Duration.ofDays(60).toMillis()), day, zone, start + 15 * 60_000, own)
            assertTrue(report.limitedContext)
        }
    }

    @Test fun `simultaneous screen off and dream start do not count as standby in either order`() {
        val off = event(-60, Source.USAGE, "SCREEN_NON_INTERACTIVE (16)")
        val dream = event(-60, Source.DREAM, "DREAMING_STARTED")
        for (batch in listOf(listOf(off, dream), listOf(dream, off))) {
            val events = listOf(event(-70, Source.APP, "heartbeat")) + batch
            val report = dayReport(HistoryBatch(events, start - Duration.ofDays(60).toMillis()), day, zone, start + 15 * 60_000, own)
            assertTrue(report.limitedContext)
        }
    }

    @Test fun `screen off after dream stopped counts as a real context reset`() {
        val events = listOf(event(-100, Source.APP, "heartbeat"), event(-90, Source.DREAM, "DREAMING_STARTED"),
            event(-89, Source.USAGE, "SCREEN_NON_INTERACTIVE (16)"), event(-60, Source.DREAM, "DREAMING_STOPPED"),
            event(-60, Source.USAGE, "SCREEN_INTERACTIVE (15)"), event(-10, Source.USAGE, "SCREEN_NON_INTERACTIVE (16)"))
        val report = dayReport(HistoryBatch(events, start - Duration.ofDays(60).toMillis()), day, zone, start + 15 * 60_000, own)
        assertFalse(report.limitedContext)
    }
}
