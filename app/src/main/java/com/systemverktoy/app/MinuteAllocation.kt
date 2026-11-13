package com.systemverktoy.app

internal fun roundedMinutes(millis: Long): Long = (millis + 30_000) / 60_000

/** Largest remainders, with stable input order for ties. A supplied total lets
 * weekly app rows match the sum of the seven independently rounded days. */
internal fun allocateMinutes(millis: List<Long>, total: Long = roundedMinutes(millis.sum())): List<Long> {
    require(millis.all { it >= 0 } && total >= 0)
    if (millis.isEmpty()) return emptyList()
    val result = millis.map { it / 60_000 }.toMutableList()
    val order = millis.indices.sortedByDescending { millis[it] % 60_000 }
    var remaining = total - result.sum()
    var index = 0
    while (remaining > 0) { result[order[index++ % order.size]]++; remaining-- }
    index = 0
    while (remaining < 0) {
        val next = order.asReversed()[index++ % order.size]
        if (result[next] > 0) { result[next]--; remaining++ }
    }
    return result
}

internal fun DayReport.watchingMinutes() = roundedMinutes(total(Category.WATCHING))
internal fun minutesText(minutes: Long) = durationText(minutes * 60_000)
