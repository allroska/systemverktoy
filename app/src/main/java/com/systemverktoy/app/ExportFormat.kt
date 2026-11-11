package com.systemverktoy.app

import java.io.Writer
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** RFC 4180 records; UTF-8 is selected by the file writer. */
internal fun csvRow(vararg cells: Any?): String = cells.joinToString(",") { cell ->
    val text = cell?.toString().orEmpty()
    if (text.any { it in ",\"\r\n" }) "\"${text.replace("\"", "\"\"") }\"" else text
} + "\r\n"

internal fun isoTime(time: Long, zone: ZoneId): String =
    Instant.ofEpochMilli(time).atZone(zone).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

internal fun writeRawCsv(writer: Writer, events: List<RawEvent>, zone: ZoneId) {
    writer.write(csvRow("tid", "kilde", "type", "pakke", "activity-klasse", "status", "tittel", "detaljer"))
    events.forEach { event ->
        writer.write(csvRow(isoTime(event.time, zone), event.source, event.type, event.packageName,
            event.activity, event.state?.substringBefore(';'), event.title, event.state))
    }
}

internal fun writeTimelineCsv(writer: Writer, report: DayReport, day: java.time.LocalDate, zone: ZoneId,
    appName: (String) -> String) {
    report.periods.forEach { row ->
        val evidence = row.signals.joinToString(" | ") { event ->
            listOfNotNull(isoTime(event.time, zone), event.source.name, event.type, event.packageName,
                event.activity, event.state, event.title).joinToString(" · ")
        }
        writer.write(csvRow(day, isoTime(row.start, zone), isoTime(row.endExclusive, zone), row.category,
            row.title.takeIf { row.category == Category.HDMI } ?: row.packageName?.let(appName),
            row.durationMillis / 1000, categoryText(row) + " | " + evidence))
    }
}
