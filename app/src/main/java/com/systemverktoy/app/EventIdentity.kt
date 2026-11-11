package com.systemverktoy.app

import java.security.MessageDigest
import java.time.LocalDate
import java.time.ZoneId

// Length framing distinguishes null, empty strings and delimiters in Android values.
fun usageKey(time: Long, type: Int, pkg: String?, activity: String?): String {
    val input = listOf(time.toString(), type.toString(), pkg, activity)
        .joinToString("") { it?.let { value -> "${value.length}:$value" } ?: "-1:" }
    return MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        .joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
}

data class DayBounds(val start: Long, val endExclusive: Long) {
    operator fun contains(time: Long) = time >= start && time < endExclusive
}

fun dayBounds(day: LocalDate, zone: ZoneId): DayBounds = DayBounds(
    day.atStartOfDay(zone).toInstant().toEpochMilli(),
    day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(),
)
