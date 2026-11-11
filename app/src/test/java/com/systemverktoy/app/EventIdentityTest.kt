package com.systemverktoy.app

import org.junit.Assert.*
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class EventIdentityTest {
    private val zone = ZoneId.of("Europe/Oslo")

    @Test fun `overlapping imports and delayed events keep one key per raw event`() {
        val first = usageKey(1000, 1, "example.player", "Menu")
        val delayed = usageKey(900, 2, "example.player", "Menu")
        val keys = (listOf(first) + listOf(delayed, first) + listOf(first, delayed)).toSet()
        assertEquals(2, keys.size)
        assertTrue(keys.contains(delayed))
    }

    @Test fun `time type package and activity all distinguish events`() {
        val original = usageKey(1000, 1, "example.player", "Menu")
        listOf(
            usageKey(1001, 1, "example.player", "Menu"),
            usageKey(1000, 2, "example.player", "Menu"),
            usageKey(1000, 1, "example.other", "Menu"),
            usageKey(1000, 1, "example.player", "Player"),
        ).forEach { assertNotEquals(original, it) }
    }

    @Test fun `null empty and delimiter values cannot collide`() {
        assertNotEquals(usageKey(1, 1, null, ""), usageKey(1, 1, "", null))
        assertNotEquals(usageKey(1, 1, "a:b", "c"), usageKey(1, 1, "a", "b:c"))
    }

    @Test fun `midnight belongs only to the next day`() {
        val day = LocalDate.of(2026, 11, 11)
        val bounds = dayBounds(day, zone)
        val next = dayBounds(day.plusDays(1), zone)
        assertEquals(Instant.parse("2026-11-10T23:00:00Z").toEpochMilli(), bounds.start)
        assertTrue(bounds.start in bounds)
        assertTrue(bounds.endExclusive - 1 in bounds)
        assertFalse(bounds.endExclusive in bounds)
        assertTrue(bounds.endExclusive in next)
        assertEquals(bounds.endExclusive, next.start)
    }

    @Test fun `spring day lasts 23 hours and autumn day lasts 25 hours`() {
        for ((date, hours) in listOf("2026-03-29" to 23L, "2026-10-25" to 25L)) {
            val day = LocalDate.parse(date)
            val bounds = dayBounds(day, zone)
            assertEquals(Duration.ofHours(hours).toMillis(), bounds.endExclusive - bounds.start)
            assertEquals(bounds.endExclusive, dayBounds(day.plusDays(1), zone).start)
        }
    }

    @Test fun `both occurrences of autumn 0230 belong to the same day`() {
        val bounds = dayBounds(LocalDate.of(2026, 10, 25), zone)
        assertTrue(Instant.parse("2026-10-25T00:30:00Z").toEpochMilli() in bounds)
        assertTrue(Instant.parse("2026-10-25T01:30:00Z").toEpochMilli() in bounds)
    }

    @Test fun `day follows supplied system zone rather than a fixed offset`() {
        val utc = dayBounds(LocalDate.of(2026, 3, 29), ZoneId.of("UTC"))
        assertEquals(Duration.ofHours(24).toMillis(), utc.endExclusive - utc.start)
        assertNotEquals(utc.start, dayBounds(LocalDate.of(2026, 3, 29), zone).start)
    }
}
