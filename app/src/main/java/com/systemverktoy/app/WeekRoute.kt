package com.systemverktoy.app

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

@Composable
internal fun WeekRoute(initial: LocalDate, today: LocalDate, now: Long, zone: ZoneId,
    appName: (String) -> String, onOpen: (LocalDate) -> Unit, onClose: () -> Unit) {
    val dao = LocalContext.current.repository.dao
    val ownPackage = LocalContext.current.packageName
    var selected by rememberSaveable { mutableStateOf(weekStart(initial).toString()) }
    val start = minOf(LocalDate.parse(selected), weekStart(today))
    val window = remember(start, zone) {
        HistoryWindow(historyWindow(start.minusWeeks(1), zone).floor, historyWindow(start.plusDays(6), zone).end)
    }
    val history = remember(window) { combine(dao.timelineRange(window.floor, window.end), dao.oldest(), dao.firstListener(), dao.monitoringStart(), ::HistoryBatch) }
    val batch by key(window) { history.collectAsStateWithLifecycle<HistoryBatch?>(null) }
    val earliest = batch?.oldest?.let { java.time.Instant.ofEpochMilli(it).atZone(zone).toLocalDate() } ?: today
    LaunchedEffect(batch?.oldest, start, today) {
        if (batch != null) selected = shiftedWeek(start, 0, today, earliest).toString()
    }
    val latestNow by rememberUpdatedState(now)
    val summary by key(start, zone) { produceState<WeekSummary?>(null, batch, now / 30_000) {
        val data = batch ?: return@produceState
        val time = latestNow
        value = withContext(Dispatchers.Default) { weekSummary(data, start, zone, time, ownPackage) }
    } }
    WeekScreen(summary, initial, today, appName, onMove = { step ->
        selected = shiftedWeek(start, step, today, earliest).toString()
    }, onOpen = onOpen, onClose = onClose)
}
