package com.systemverktoy.app

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Presentation only; the stored raw events are never modified. */
internal fun isImportantEvent(event: RawEvent, launcher: String?, ownPackage: String): Boolean {
    val type = event.type.substringBefore(" (")
    if (event.source == Source.APP && type in setOf(
            "BOOT_COMPLETED", "EXPORT_FAILED", "IMPORT_FAILED", "USAGE_ACCESS_REVOKED", "heartbeat",
        )) return true
    // Global signals may have no package, or refer to the listener's own package.
    if (event.source in setOf(Source.DREAM, Source.AUDIO, Source.LISTENER) ||
        type in setOf("SCREEN_ON", "SCREEN_OFF", "SCREEN_INTERACTIVE", "SCREEN_NON_INTERACTIVE")) return true
    if (event.packageName == ownPackage || (launcher != null && event.packageName == launcher)) return false
    return event.source == Source.MEDIA || event.source == Source.USAGE
}

private val logTime = DateTimeFormatter.ofPattern("HH:mm:ss")
private val whitespace = Regex("\\s+")

internal fun formatRawEvent(event: RawEvent, zone: ZoneId): String {
    val detail = listOfNotNull(
        event.activity?.substringAfterLast('.'), event.state, event.title,
    ).filter { it.isNotBlank() }.joinToString(" / ").ifEmpty { "—" }
    return listOf(
        Instant.ofEpochMilli(event.time).atZone(zone).format(logTime),
        event.source.name, event.type, event.packageName ?: "—", detail,
    ).joinToString(" · ").replace(whitespace, " ")
}
