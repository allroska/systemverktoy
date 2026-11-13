package com.systemverktoy.app

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class WeekSummaryBuilderTest {
    private val zone = ZoneId.of("Europe/Oslo")
    private val monday = LocalDate.parse("2026-11-09")
    private fun at(date: LocalDate, hour: Int = 0) = date.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()
    private fun report(rows: List<TimelinePeriod>) = DayReport(rows, emptyList(), null, rows.isNotEmpty(), false, false)
    private fun summary(apps: Int = 1, now: Long = at(monday.plusDays(4), 12), monitored: LocalDate = monday.minusWeeks(1)) =
        weekSummary(HistoryBatch(emptyList(), at(monitored), monitoringStart = at(monitored)), monday, zone, now, "own") { day ->
            report((1..apps).map { i -> TimelinePeriod(at(day, i), at(day, i) + 90_000, Category.WATCHING, "player$i") })
        }
    @Test fun `ISO week changes only at local Monday including year boundary`() {
        assertEquals(monday, weekStart(monday.plusDays(6)))
        assertEquals(monday.plusWeeks(1), weekStart(monday.plusDays(7)))
        assertEquals(monday, shiftedWeek(monday, 1, monday.plusDays(6), monday.minusWeeks(1)))
        assertEquals(monday.minusWeeks(1), shiftedWeek(monday.minusWeeks(1), -1, monday, monday.minusWeeks(1)))
        val sunday = LocalDate.parse("2027-01-03")
        val s = weekSummary(HistoryBatch(emptyList(), null), sunday, zone, at(sunday, 23), "own") { report(emptyList()) }
        assertEquals(53, s.number)
        assertEquals(LocalDate.parse("2026-12-28"), s.start)
        assertEquals(LocalDate.parse("2027-01-04"), weekStart(sunday.plusDays(1)))
    }
    @Test fun `started days and monitoring boundary determine both averages`() {
        val s = summary()
        assertEquals(10L, s.total) // five days, each 90 seconds rounds to two minutes
        assertEquals(2L, s.average)
        assertEquals(2L, s.previousAverage)
        assertEquals(5, s.days.count { !it.future })
        assertTrue(s.ongoing)
        val partial = summary(monitored = monday.plusDays(2))
        assertEquals(6L, partial.total)
        assertEquals(2L, partial.average)
        assertNull(partial.previousAverage)
        assertEquals(listOf(0L, 0L), partial.days.take(2).map { it.minutes })
        val completed = summary(now = at(monday.plusWeeks(1), 12))
        assertFalse(completed.ongoing)
        assertEquals(14L, completed.total)
    }
    @Test fun `four apps stay separate and five group the last two`() {
        assertEquals(listOf(1, 1, 1, 1), summary(4).appLines.map { it.apps.size })
        val s = summary(5)
        assertEquals(listOf(1, 1, 1, 2), s.appLines.map { it.apps.size })
        assertEquals(s.total, s.apps.sumOf { it.minutes })
        assertEquals(s.total, s.appLines.sumOf { it.minutes })
    }
    @Test fun `hour cells sum intersections and use watching HDMI unknown priority`() {
        val a = at(monday, 10)
        val rows = listOf(TimelinePeriod(a, a + 60_000, Category.UNKNOWN),
            TimelinePeriod(a + 60_000, a + 120_000, Category.HDMI),
            TimelinePeriod(a + 120_000, a + 180_000, Category.WATCHING),
            TimelinePeriod(a + 3_600_000, a + 3_660_000, Category.HDMI),
            TimelinePeriod(a + 7_200_000, a + 7_260_000, Category.UNKNOWN),
            TimelinePeriod(a - 30_000, a + 10_000, Category.WATCHING))
        val cells = hourCells(monday, rows, zone, at(monday, 13))
        assertEquals(HourCell.EMPTY, cells[9])
        assertEquals(listOf(HourCell.WATCHING, HourCell.HDMI, HourCell.UNKNOWN, HourCell.EMPTY, HourCell.FUTURE), cells.subList(10, 15))
        assertTrue(hourCells(monday.plusDays(1), emptyList(), zone, at(monday, 13)).all { it == HourCell.FUTURE })
    }
    @Test fun `spring skips 02 and autumn combines both 02 intervals`() {
        for ((date, expectedHours) in listOf("2026-03-29" to 23, "2026-10-25" to 25)) {
            val day = LocalDate.parse(date)
            assertEquals(expectedHours * 3_600_000L, at(day.plusDays(1)) - at(day))
            val full = TimelinePeriod(at(day), at(day.plusDays(1)), Category.WATCHING, "player")
            val batch = HistoryBatch(emptyList(), at(day.minusWeeks(1)), monitoringStart = at(day.minusWeeks(1)))
            val s = weekSummary(batch, day, zone, at(day.plusDays(1)), "own") { d -> report(if (d == day) listOf(full) else emptyList()) }
            assertEquals(expectedHours * 60L, s.days.last().minutes)
            assertEquals(if (expectedHours == 23) HourCell.EMPTY else HourCell.WATCHING, s.days.last().cells[2])
        }
        val autumn = LocalDate.parse("2026-10-25")
        val a = Instant.parse("2026-10-25T00:00:00Z").toEpochMilli()
        val b = Instant.parse("2026-10-25T01:00:00Z").toEpochMilli()
        assertEquals(HourCell.WATCHING, hourCells(autumn, listOf(
            TimelinePeriod(a, a + 30_000, Category.WATCHING), TimelinePeriod(b, b + 30_000, Category.WATCHING)), zone, at(autumn.plusDays(1)))[2])
    }
    @Test fun `largest remainders reconcile sessions days and weekly app totals`() {
        assertEquals(listOf(2L, 1L, 1L), allocateMinutes(listOf(90_000, 80_000, 70_000)))
        val day = report(listOf(TimelinePeriod(0, 90_000, Category.WATCHING, "a"),
            TimelinePeriod(600_000, 690_000, Category.WATCHING, "b")))
        assertEquals(day.watchingMinutes(), allocateMinutes(viewingSessions(day.periods).map { it.watching }).sum())
        // Independent day rounding can be above or below the rounded weekly seconds.
        for (seconds in listOf(61L, 89L, 91L, 119L)) {
            val s = weekSummary(HistoryBatch(emptyList(), at(monday), monitoringStart = at(monday)), monday, zone, at(monday.plusWeeks(1)), "own") { d ->
                report(listOf(TimelinePeriod(at(d), at(d) + seconds * 1000, Category.WATCHING, "player")))
            }
            assertEquals(s.days.sumOf { it.minutes }, s.total)
            assertEquals(s.total, s.apps.sumOf { it.minutes })
        }
    }
    @Test fun `real event replay has identical totals in Overview and week`() {
        val start = at(monday, 14)
        val events = listOf(
            RawEvent(time = start, source = Source.LISTENER, type = "onListenerConnected"),
            RawEvent(time = start, source = Source.APP, type = "heartbeat"),
            RawEvent(time = start, source = Source.USAGE, type = "SCREEN_INTERACTIVE"),
            RawEvent(time = start, source = Source.USAGE, type = "ACTIVITY_RESUMED", packageName = "player"),
            RawEvent(time = start, source = Source.MEDIA, type = "SESSION_ADDED", packageName = "player", state = "STATE_PLAYING"),
            RawEvent(time = start + 95_000, source = Source.USAGE, type = "SCREEN_NON_INTERACTIVE"))
        val batch = HistoryBatch(events, start)
        val now = at(monday.plusDays(1))
        val daily = dayReport(batch, monday, zone, now, "own")
        val weekly = weekSummary(batch, monday, zone, now, "own")
        assertEquals(2L, daily.watchingMinutes())
        assertEquals(daily.watchingMinutes(), weekly.days.first().minutes)
        assertEquals(weekly.total, weekly.apps.sumOf { it.minutes })
        assertEquals(HourCell.WATCHING, weekly.days.first().cells[14])
    }
    @Test fun `empty week has no apps no comparison and seven empty days`() {
        val s = weekSummary(HistoryBatch(emptyList(), null), monday, zone, at(monday.plusWeeks(1)), "own") { report(emptyList()) }
        assertEquals(0L, s.total)
        assertNull(s.average)
        assertNull(s.previousAverage)
        assertTrue(s.apps.isEmpty())
        assertEquals(7, s.days.size)
        assertTrue(s.days.flatMap { it.cells }.all { it == HourCell.EMPTY })
    }
}
