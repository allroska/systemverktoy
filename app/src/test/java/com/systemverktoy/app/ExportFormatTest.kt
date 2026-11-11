package com.systemverktoy.app

import org.junit.Assert.*
import org.junit.Test
import java.io.StringWriter
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class ExportFormatTest {
    @Test fun `CSV quotes commas quotes and line breaks and preserves empty fields`() {
        assertEquals("plain,\"a,b\",\"a\"\"b\",\"a\r\nb\",,æøå\r\n",
            csvRow("plain", "a,b", "a\"b", "a\r\nb", null, "æøå"))
    }

    @Test fun `export distinguishes the repeated autumn hour using real offsets`() {
        val zone = ZoneId.of("Europe/Oslo")
        val early = Instant.parse("2026-10-25T00:30:00Z").toEpochMilli()
        val late = Instant.parse("2026-10-25T01:30:00Z").toEpochMilli()
        assertEquals("2026-10-25T02:30:00+02:00", isoTime(early, zone))
        assertEquals("2026-10-25T02:30:00+01:00", isoTime(late, zone))
        val event = RawEvent(time = late, source = Source.MEDIA, type = "STATE", packageName = "example.player",
            activity = "Player", state = "STATE_PLAYING; detail=1", title = "A, \"B\"")
        val raw = StringWriter().also { writeRawCsv(it, listOf(event), zone) }.toString()
        assertTrue(raw.contains("2026-10-25T02:30:00+01:00,MEDIA,STATE,example.player,Player,STATE_PLAYING,\"A, \"\"B\"\"\",STATE_PLAYING; detail=1"))
        val row = TimelinePeriod(early, late, Category.WATCHING, "example.player", signals = listOf(event))
        val report = DayReport(listOf(row), listOf(event), early, true, false, false)
        val timeline = StringWriter().also { writeTimelineCsv(it, report, LocalDate.parse("2026-10-25"), zone) { "Player" } }.toString()
        assertTrue(timeline.startsWith("2026-10-25,2026-10-25T02:30:00+02:00,2026-10-25T02:30:00+01:00,WATCHING,Player,3600,"))
        assertTrue(timeline.contains("MEDIA · STATE"))
    }
}
