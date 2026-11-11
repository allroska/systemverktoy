package com.systemverktoy.app

import org.junit.Assert.*
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId

class TimelineBuilderTest {
    private val day = LocalDate.of(2026, 11, 11)
    private val zone = ZoneId.of("Europe/Oslo")
    private val base = day.atTime(17, 19).atZone(zone).toInstant().toEpochMilli()
    private val netflix = "com.netflix.ninja"
    private val nrk = "no.nrk.tv"
    private val home = "com.google.android.apps.tv.launcherx"
    private val tvx = "com.sony.dtv.tvx"
    private val builder = TimelineBuilder()

    private fun event(second: Long, source: Source, type: String, pkg: String? = null,
        activity: String? = null, state: String? = null, title: String? = null) =
        RawEvent(time = base + second * 1000, source = source, type = type,
            packageName = pkg, activity = activity, state = state, title = title)
    private fun usage(second: Long, type: String, pkg: String? = null, activity: String? = null) =
        event(second, Source.USAGE, type, pkg, activity)
    private fun resume(second: Long = 0, pkg: String = netflix, activity: String = "MainActivity") =
        usage(second, "ACTIVITY_RESUMED (1)", pkg, activity)
    private fun media(second: Long, state: String, pkg: String = netflix, title: String? = null,
        type: String = "PLAYBACK_STATE_CHANGED") =
        event(second, Source.MEDIA, type, pkg, state = state, title = title)
    private fun pulse(second: Long = 0) = event(second, Source.APP, "heartbeat")
    private fun audio(second: Long, active: Int) = event(second, Source.AUDIO, "PLAYBACK_CONFIG_CHANGED",
        state = "media_active=$active; usage=1, content=3")
    private fun listener(second: Long, connected: Boolean) = event(second, Source.LISTENER,
        if (connected) "onListenerConnected" else "onListenerDisconnected")
    private fun build(vararg events: RawEvent, end: Long = 180) =
        builder.build(events.toList(), day, zone, base + end * 1000)
    private fun categories(periods: List<TimelinePeriod>) = periods.map { it.category }
    private fun duration(periods: List<TimelinePeriod>, category: Category) =
        periods.filter { it.category == category }.sumOf { it.durationMillis } / 1000

    // RESEARCH records signal order, packages/statuses and the exact menu interval,
    // not a full timestamped export. Other offsets below are explicit test fixtures.
    @Test fun `Netflix MainActivity menu play pause stop and removal follow evening findings`() {
        val periods = build(pulse(), resume(), media(10, "STATE_PLAYING (spiller)", type = "SESSION_ADDED"),
            media(130, "STATE_PAUSED (pauset)"), media(150, "STATE_STOPPED"),
            event(151, Source.MEDIA, "SESSION_REMOVED", netflix))
        assertEquals(listOf(Category.APP_OPEN, Category.WATCHING, Category.APP_OPEN), categories(periods))
        assertEquals(120L, duration(periods, Category.WATCHING))
        assertTrue(periods.all { it.title == null })
        assertEquals(netflix, periods[1].packageName)
    }

    @Test fun `observed Netflix menu 172754 to 173110 has no playing signal`() {
        val start = day.atTime(17, 27, 54).atZone(zone).toInstant().toEpochMilli()
        val stop = day.atTime(17, 31, 10).atZone(zone).toInstant().toEpochMilli()
        val periods = builder.build(listOf(pulse().copy(time = start), resume().copy(time = start)), day, zone, stop)
        assertEquals(listOf(Category.APP_OPEN), categories(periods))
        assertEquals(196_000L, periods.single().durationMillis)
    }

    @Test fun `NRK none buffering playing pause and replacement episode follow evening findings`() {
        val periods = build(pulse(), resume(pkg = nrk, activity = "MainActivityTv"),
            media(0, "STATE_NONE", nrk, type = "SESSION_ADDED"), media(5, "STATE_BUFFERING", nrk),
            media(10, "STATE_PLAYING (spiller)", nrk), media(80, "STATE_PAUSED (pauset)", nrk),
            event(90, Source.MEDIA, "SESSION_REMOVED", nrk), media(91, "STATE_NONE", nrk, type = "SESSION_ADDED"),
            media(95, "STATE_BUFFERING", nrk), media(100, "STATE_PLAYING (spiller)", nrk), end = 170)
        assertEquals(listOf(Category.APP_OPEN, Category.WATCHING, Category.APP_OPEN, Category.WATCHING), categories(periods))
        assertEquals(base + 5000, periods[1].start)
        assertEquals(150L, duration(periods, Category.WATCHING))
    }

    @Test fun `buffering without subsequent playing or interrupted by pause is not watching`() {
        for (tail in listOf(emptyList(), listOf(media(40, "STATE_PAUSED (pauset)", nrk)))) {
            val events = listOf(pulse(), resume(pkg = nrk), media(1, "STATE_BUFFERING", nrk)) + tail
            assertEquals(0L, duration(builder.build(events, day, zone, base + 180_000), Category.WATCHING))
        }
    }

    @Test fun `buffering cannot turn a 59 second preview into a 60 second playing run`() {
        val periods = build(pulse(), resume(), media(0, "STATE_BUFFERING"),
            media(30, "STATE_PLAYING (spiller)"), media(89, "STATE_STOPPED"))
        assertEquals(listOf(Category.APP_OPEN), categories(periods))
    }

    @Test fun `preview boundary is strictly less than 60 seconds`() {
        for (seconds in listOf(59L, 60L, 61L)) {
            val periods = build(pulse(), resume(), media(0, "STATE_PLAYING (spiller)"),
                media(seconds, "STATE_PAUSED (pauset)"))
            assertEquals(if (seconds < 60) 0L else seconds, duration(periods, Category.WATCHING))
        }
    }

    @Test fun `short playing runs separated by pause cannot be added together`() {
        assertEquals(0L, duration(build(pulse(), resume(), media(0, "STATE_PLAYING"),
            media(40, "STATE_PAUSED"), media(50, "STATE_PLAYING"), media(90, "STATE_STOPPED")), Category.WATCHING))
    }

    @Test fun `buffering separates playing runs instead of adding short previews together`() {
        for (firstRun in listOf(30L, 70L)) {
            val periods = build(pulse(), resume(), media(0, "STATE_PLAYING"),
                media(firstRun, "STATE_BUFFERING"), media(firstRun + 10, "STATE_PLAYING"),
                media(firstRun + 40, "STATE_STOPPED"))
            assertEquals(if (firstRun < 60) 0L else firstRun, duration(periods, Category.WATCHING))
        }
    }

    @Test fun `metadata changes and heartbeat do not restart playing threshold`() {
        val periods = build(pulse(), resume(), media(0, "STATE_PLAYING", title = "Episode A"),
            pulse(30), media(40, "STATE_PLAYING", title = "Episode B", type = "METADATA_CHANGED"), end = 60)
        assertEquals(60L, duration(periods, Category.WATCHING))
        assertEquals(listOf("Episode A", "Episode B"), periods.map { it.title })
        assertTrue(periods.last().ongoing)
    }

    @Test fun `ongoing preview is reclassified after full 60 seconds`() {
        val events = arrayOf(pulse(), resume(), media(0, "STATE_PLAYING"))
        assertEquals(Category.APP_OPEN, build(*events, end = 59).single().category)
        assertEquals(Category.WATCHING, build(*events, end = 60).single().category)
    }

    @Test fun `dream plus non-interactive is idle in either simultaneous database order`() {
        val off = usage(30, "SCREEN_NON_INTERACTIVE (16)", "android")
        val dream = event(30, Source.DREAM, "DREAMING_STARTED")
        for (signals in listOf(listOf(off, dream), listOf(dream, off))) {
            val periods = builder.build(listOf(pulse(), resume(), media(0, "STATE_PLAYING")) + signals +
                usage(100, "SCREEN_INTERACTIVE (15)") + event(100, Source.DREAM, "DREAMING_STOPPED"),
                day, zone, base + 180_000)
            assertEquals(70L, duration(periods, Category.IDLE))
            assertFalse(periods.any { it.category == Category.OFF })
        }
    }

    @Test fun `DreamActivity alone starts idle and screen interactive ends it`() {
        val periods = build(pulse(), resume(pkg = home),
            resume(10, "com.google.android.apps.tv.dreamx", "android.service.dreams.DreamActivity"),
            usage(100, "SCREEN_INTERACTIVE (15)"))
        assertEquals(listOf(Category.HOME, Category.IDLE, Category.HOME), categories(periods))
    }

    @Test fun `long dream does not invent heartbeat gap while collector cannot write pulses`() {
        val periods = build(pulse(), resume(pkg = home), usage(1, "SCREEN_NON_INTERACTIVE (16)"),
            event(1, Source.DREAM, "DREAMING_STARTED"), end = 7200)
        assertEquals(7199L, duration(periods, Category.IDLE))
        assertEquals(0L, duration(periods, Category.UNKNOWN))
    }

    @Test fun `dream stopping without interactive signal leaves the screen off`() {
        val periods = build(pulse(), resume(pkg = home), usage(10, "SCREEN_NON_INTERACTIVE (16)"),
            event(10, Source.DREAM, "DREAMING_STARTED"), event(100, Source.DREAM, "DREAMING_STOPPED"))
        assertEquals(listOf(Category.HOME, Category.IDLE), categories(periods))
        assertEquals(base + 100_000, periods.last().endExclusive)
        assertFalse(periods.last().ongoing)
    }

    @Test fun `plain screen off is omitted and wake never reuses old playing state`() {
        val periods = build(pulse(), resume(), media(0, "STATE_PLAYING"),
            event(70, Source.APP, "SCREEN_OFF"), usage(100, "SCREEN_INTERACTIVE (15)"), end = 180)
        assertEquals(listOf(Category.WATCHING), categories(periods))
        assertEquals(base + 70_000, periods.first().endExclusive)
    }

    @Test fun `shutdown ends dream and startup or boot completed ends off`() {
        for (wake in listOf(usage(100, "DEVICE_STARTUP (27)"), event(100, Source.APP, "BOOT_COMPLETED"))) {
            val periods = build(pulse(), event(0, Source.DREAM, "DREAMING_STARTED"),
                usage(20, "DEVICE_SHUTDOWN (26)"), wake, resume(100, home))
            assertEquals(listOf(Category.IDLE, Category.HOME), categories(periods))
            assertEquals(base + 20_000, periods.first().endExclusive)
        }
    }

    @Test fun `HDMI 4 playing is HDMI with own duration and title never watching`() {
        val periods = build(pulse(), resume(pkg = tvx),
            media(0, "STATE_PLAYING (spiller)", tvx, "HDMI 4", "SESSION_ADDED"),
            media(120, "STATE_NONE", tvx), event(120, Source.MEDIA, "SESSION_REMOVED", tvx), resume(120, home))
        assertEquals(listOf(Category.HDMI, Category.HOME), categories(periods))
        assertEquals("HDMI 4", periods.first().title)
        assertEquals(120L, duration(periods, Category.HDMI))
        assertEquals(0L, duration(periods, Category.WATCHING))
    }

    @Test fun `home ignores both its own playing session and anonymous trailer audio`() {
        assertEquals(listOf(Category.HOME), categories(build(pulse(), resume(pkg = home),
            media(0, "STATE_PLAYING", home), audio(1, 1))))
    }

    @Test fun `audio fallback needs foreground and no session for that package`() {
        assertEquals(80L, duration(build(pulse(), resume(), audio(10, 2), audio(90, 0)), Category.WATCHING))
        assertEquals(0L, duration(build(pulse(), resume(), media(0, "STATE_PAUSED"), audio(10, 1)), Category.WATCHING))
        assertEquals(0L, duration(build(pulse(), audio(10, 1)), Category.WATCHING))
        assertEquals(0L, duration(build(pulse(), resume(), media(0, "STATE_PLAYING", nrk)), Category.WATCHING))
    }

    @Test fun `late stop or pause of another activity never closes foreground player`() {
        val periods = build(pulse(), resume(activity = "Menu"), resume(1, activity = "Player"),
            usage(2, "ACTIVITY_STOPPED (23)", netflix, "Menu"),
            usage(3, "ACTIVITY_PAUSED (2)", netflix, "Menu"), media(1, "STATE_PLAYING"))
        assertEquals(179L, duration(periods, Category.WATCHING))
    }

    @Test fun `matching activity pause and stop preserve last resumed foreground`() {
        val periods = build(pulse(), resume(pkg = home), resume(1),
            usage(100, "ACTIVITY_PAUSED (2)", netflix, "MainActivity"),
            usage(101, "ACTIVITY_STOPPED (23)", netflix, "MainActivity"))
        assertEquals(listOf(Category.HOME, Category.APP_OPEN), categories(periods))
        assertEquals(netflix, periods.last().packageName)
        assertEquals(base + 1000, periods.last().start)
    }

    @Test fun `noise never replaces foreground while settings and Downloader follow SPEC`() {
        val noise = listOf("android", "com.android.systemui", "com.google.android.gsf",
            "com.google.android.tungsten.setupwraith", "com.sony.dtv.settings", "com.sony.dtv.tvx.extra")
            .mapIndexed { index, pkg -> resume(index + 1L, pkg) } +
            listOf(11, 12, 14, 99).map { usage(it.toLong(), "EVENT_$it ($it)", nrk) }
        val periods = builder.build(listOf(pulse(), resume()) + noise, day, zone, base + 180_000)
        assertEquals(netflix, periods.single().packageName)
        assertTrue(periods.single().signals.none { it in noise })
        for (pkg in listOf("com.android.tv.settings", "com.esaba.downloader")) {
            assertEquals(pkg, build(pulse(), resume(pkg = pkg)).single().packageName)
        }
    }

    @Test fun `35 minute deadline creates unknown without needing a later event`() {
        val periods = build(pulse(), resume(pkg = home), end = 2400)
        assertEquals(listOf(Category.HOME, Category.UNKNOWN), categories(periods))
        assertEquals(base + 2_100_000, periods.last().start)
        assertEquals(300L, duration(periods, Category.UNKNOWN))
    }

    @Test fun `screen on without any heartbeat also expires after 35 minutes`() {
        val periods = build(usage(0, "SCREEN_INTERACTIVE (15)"), resume(pkg = home), end = 2400)
        assertEquals(300L, duration(periods, Category.UNKNOWN))
    }

    @Test fun `heartbeat arriving exactly at 35 minutes preserves current app and playback`() {
        val periods = build(pulse(), resume(), media(0, "STATE_PLAYING"), pulse(2100), end = 2400)
        assertEquals(listOf(Category.WATCHING), categories(periods))
        assertEquals(2400L, duration(periods, Category.WATCHING))
    }

    @Test fun `known heartbeat gap outranks later dream evidence`() {
        val periods = build(pulse(), resume(pkg = home),
            event(2200, Source.DREAM, "DREAMING_STARTED"), end = 2400)
        assertEquals(listOf(Category.HOME, Category.UNKNOWN), categories(periods))
    }

    @Test fun `late heartbeat restores known app without resurrecting old session`() {
        val periods = build(pulse(), resume(), media(0, "STATE_PLAYING"), pulse(2400),
            resume(2410, home), end = 2500)
        assertEquals(listOf(Category.WATCHING, Category.UNKNOWN, Category.APP_OPEN, Category.HOME), categories(periods))
        assertEquals(300L, duration(periods, Category.UNKNOWN))
        assertEquals(netflix, periods[2].packageName)
        assertEquals(base + 2_400_000, periods[2].start)
    }

    @Test fun `listener reconnect resumes Netflix watching without a new activity resume`() {
        val periods = build(pulse(), resume(), media(0, "STATE_PLAYING"), pulse(900),
            listener(1200, false), listener(1205, true),
            media(1205, "STATE_PLAYING (spiller)", type = "SESSION_ADDED"), pulse(1800), end = 2400)
        assertEquals(listOf(Category.WATCHING, Category.UNKNOWN, Category.WATCHING), categories(periods))
        assertEquals(5L, duration(periods, Category.UNKNOWN))
        assertEquals(netflix, periods.last().packageName)
        assertEquals(base + 1_205_000, periods.last().start)
    }

    @Test fun `fresh session observed during heartbeat gap survives until heartbeat recovery`() {
        val periods = build(pulse(), resume(), media(0, "STATE_PLAYING"),
            media(2370, "STATE_PLAYING", type = "SESSION_ADDED"), pulse(2400), end = 2500)
        assertEquals(listOf(Category.WATCHING, Category.UNKNOWN, Category.WATCHING), categories(periods))
        assertEquals(300L, duration(periods, Category.UNKNOWN))
        assertEquals(base + 2_400_000, periods.last().start)
    }

    @Test fun `300 ms activity transitions never create missing log warnings`() {
        val events = listOf(pulse(), resume(pkg = home),
            usage(1, "ACTIVITY_PAUSED (2)", home, "MainActivity"),
            resume(1).copy(time = base + 1300),
            usage(1, "ACTIVITY_STOPPED (23)", home, "MainActivity").copy(time = base + 1600))
        val periods = builder.build(events, day, zone, base + 180_000)
        assertEquals(listOf(Category.HOME, Category.APP_OPEN), categories(periods))
        assertEquals(base + 1300, periods.first().endExclusive)
        assertEquals(base + 1300, periods.last().start)
    }

    @Test fun `filtered Sony overlay does not remove known foreground`() {
        val periods = build(pulse(), resume(), media(0, "STATE_PLAYING"),
            usage(80, "ACTIVITY_PAUSED (2)", netflix, "MainActivity"),
            resume(81, "com.sony.dtv.overlay"), usage(82, "ACTIVITY_STOPPED (23)", netflix, "MainActivity"))
        assertEquals(listOf(Category.WATCHING), categories(periods))
        assertEquals(netflix, periods.single().packageName)
    }

    @Test fun `screen on without foreground is omitted until a real heartbeat gap`() {
        val on = usage(0, "SCREEN_INTERACTIVE (15)")
        assertTrue(build(on, pulse()).isEmpty())
        val periods = build(on, pulse(), end = 2400)
        assertEquals(listOf(Category.UNKNOWN), categories(periods))
        assertEquals(base + 2_100_000, periods.single().start)
    }

    @Test fun `startup after power loss without screen off resets expired heartbeat deadline`() {
        for (wake in listOf(usage(4000, "DEVICE_STARTUP (27)"), event(4000, Source.APP, "BOOT_COMPLETED"))) {
            val periods = build(pulse(), resume(), media(0, "STATE_PLAYING"), audio(0, 1),
                wake, resume(4001), media(4010, "STATE_PLAYING", type = "SESSION_ADDED"), end = 4100)
            val afterBoot = periods.filter { it.start >= wake.time }
            assertEquals(listOf(Category.APP_OPEN, Category.WATCHING), categories(afterBoot))
            assertEquals(base + 4_001_000, afterBoot.first().start)
            assertEquals(90L, duration(afterBoot, Category.WATCHING))
            assertEquals(wake.time, periods.single { it.category == Category.UNKNOWN }.endExclusive)
        }
    }

    @Test fun `startup clears old foreground media and audio even before heartbeat expires`() {
        for (wake in listOf(usage(100, "DEVICE_STARTUP (27)"), event(100, Source.APP, "BOOT_COMPLETED"))) {
            val periods = build(pulse(), resume(), media(0, "STATE_PLAYING"), audio(0, 1),
                wake, resume(110), end = 200)
            assertEquals(listOf(Category.WATCHING, Category.APP_OPEN), categories(periods))
            assertEquals(base + 100_000, periods.first().endExclusive)
            assertEquals(base + 110_000, periods.last().start)
            assertEquals(90L, duration(periods, Category.APP_OPEN))
        }
    }

    @Test fun `background Spotify playing prevents audio from being assigned to settings or Downloader`() {
        for (pkg in listOf("com.android.tv.settings", "com.esaba.downloader")) {
            val periods = build(pulse(), resume(pkg = pkg),
                media(0, "STATE_PLAYING (spiller)", "com.spotify.tv.android"), audio(0, 1),
                media(100, "STATE_PAUSED (pauset)", "com.spotify.tv.android"), audio(100, 0))
            assertEquals(listOf(Category.APP_OPEN), categories(periods))
            assertEquals(pkg, periods.single().packageName)
        }
    }

    @Test fun `audio reserve still works when other sessions are paused or removed`() {
        val periods = build(pulse(), resume(), media(0, "STATE_PLAYING", "com.spotify.tv.android"),
            audio(0, 1), media(50, "STATE_PAUSED", "com.spotify.tv.android"),
            event(60, Source.MEDIA, "SESSION_REMOVED", "com.spotify.tv.android"), audio(65, 0))
        assertEquals(15L, duration(periods, Category.WATCHING))
    }

    @Test fun `observed 15 minute heartbeat cadence keeps full evening covered`() {
        val start = day.atTime(17, 15, 36).atZone(zone).toInstant().toEpochMilli()
        val pulses = (0L..5L).map { pulse().copy(time = start + it * 900_000) }
        val periods = builder.build(pulses + resume(pkg = home).copy(time = start), day, zone,
            day.atTime(18, 31).atZone(zone).toInstant().toEpochMilli())
        assertEquals(listOf(Category.HOME), categories(periods))
    }

    @Test fun `disconnect outranks dream HDMI and playing but not known off`() {
        val periods = build(pulse(), resume(pkg = tvx), listener(20, false),
            event(30, Source.DREAM, "DREAMING_STARTED"), listener(40, true),
            usage(50, "DEVICE_SHUTDOWN (26)"), listener(60, false), end = 100)
        assertEquals(listOf(Category.HDMI, Category.UNKNOWN, Category.IDLE), categories(periods))
        assertEquals(20L, duration(periods, Category.UNKNOWN))
        val playback = build(pulse(), resume(), media(0, "STATE_PLAYING"), listener(100, false))
        assertEquals(Category.UNKNOWN, playback.last().category)
    }

    @Test fun `unobserved session and listener destruction stop watching`() {
        assertEquals(80L, duration(build(pulse(), resume(), media(0, "STATE_PLAYING"),
            event(80, Source.MEDIA, "SESSION_UNOBSERVED", netflix)), Category.WATCHING))
        assertEquals(Category.UNKNOWN, build(pulse(), resume(),
            event(100, Source.LISTENER, "onDestroy")).last().category)
    }

    @Test fun `midnight clips only after evaluating continuous playing over boundary`() {
        val midnight = dayBounds(day, zone).endExclusive
        val events = listOf(pulse(), resume(), media(0, "STATE_PLAYING")).map { it.copy(time = midnight - 30_000) }
        val now = midnight + 40_000
        val today = builder.build(events, day, zone, now).single()
        val tomorrow = builder.build(events, day.plusDays(1), zone, now).single()
        assertEquals(Category.WATCHING, today.category)
        assertEquals(30_000L, today.durationMillis)
        assertEquals(midnight, today.endExclusive)
        assertFalse(today.ongoing)
        assertEquals(midnight, tomorrow.start)
        assertEquals(40_000L, tomorrow.durationMillis)
        assertTrue(tomorrow.ongoing)
        assertTrue(tomorrow.signals.any { it.time < midnight })
    }

    @Test fun `both daylight saving days use real elapsed time and local boundaries`() {
        for ((date, hours) in listOf("2026-03-29" to 23L, "2026-10-25" to 25L)) {
            val dateValue = LocalDate.parse(date)
            val bounds = dayBounds(dateValue, zone)
            val events = listOf(event(0, Source.DREAM, "DREAMING_STARTED").copy(time = bounds.start - 1000))
            val period = builder.build(events, dateValue, zone, bounds.endExclusive).single()
            assertEquals(Duration.ofHours(hours).toMillis(), period.durationMillis)
        }
    }

    @Test fun `replay sorts delayed database imports and preserves raw input and signal provenance`() {
        val events = listOf(media(80, "STATE_PAUSED"), resume(), pulse(), media(0, "STATE_PLAYING"))
        val copy = events.toList()
        val result = builder.build(events, day, zone, base + 180_000)
        assertEquals(80L, duration(result, Category.WATCHING))
        assertEquals(copy, events)
        assertTrue(result.first().signals.contains(events.last()))
        assertTrue(result.zipWithNext().all { (a, b) -> a.endExclusive <= b.start })
        assertTrue(result.all { it.start < it.endExclusive })
        assertTrue(result.all { period -> period.signals.zipWithNext().all { (a, b) -> a.time <= b.time } })
    }

    @Test fun `future events are ignored and an unobserved or future day is empty`() {
        assertTrue(build(end = 180).isEmpty())
        assertEquals(Category.APP_OPEN, build(pulse(), resume(), media(200, "STATE_PLAYING")).single().category)
        assertTrue(builder.build(listOf(pulse()), day.plusDays(1), zone, base).isEmpty())
    }

    @Test fun `an event exactly at now ends the preceding period without marking it ongoing`() {
        val periods = build(pulse(), resume(), media(0, "STATE_PLAYING"),
            usage(180, "SCREEN_NON_INTERACTIVE (16)"))
        assertFalse(periods.single().ongoing)
    }
}
