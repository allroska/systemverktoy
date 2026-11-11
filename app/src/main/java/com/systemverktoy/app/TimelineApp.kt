package com.systemverktoy.app

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId

@Composable
internal fun TimelineApp() {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var access by remember { mutableStateOf(Access.read(context)) }
    var zone by remember { mutableStateOf(ZoneId.systemDefault()) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val day = selectedDay(selected, today)
    val navigation = rememberTimelineNavigation(day)
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                access = Access.read(context)
                zone = ZoneId.systemDefault()
                now = System.currentTimeMillis()
                delay(1000)
            }
        }
    }
    LaunchedEffect(access.ready) {
        if (!access.ready) { selected = null; navigation.clear() }
    }
    if (!access.ready) { SetupScreen(access); return }
    val window = remember(day, zone) { historyWindow(day, zone) }
    val history = remember(window) {
        combine(context.repository.dao.timelineRange(window.floor, window.end), context.repository.dao.oldest(),
            context.repository.dao.firstListener(), context.repository.dao.monitoringStart(), ::HistoryBatch)
    }
    val batch by key(window) { history.collectAsStateWithLifecycle<HistoryBatch?>(null) }
    val report by rememberDayReport(batch, day, today, zone, now, context.packageName)
    val labels = remember(context) { mutableMapOf<String, String>() }
    val appName: (String) -> String = { pkg -> labels.getOrPut(pkg) {
        runCatching { context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(pkg, 0)).toString() }
            .getOrElse { when (pkg) {
                "com.netflix.ninja" -> "Netflix"
                "no.nrk.tv" -> "NRK TV"
                "com.google.android.apps.tv.launcherx" -> "Startskjerm"
                "com.sony.dtv.tvx" -> "Sony TV"
                else -> pkg
            } }
    } }
    TimelineNavigation(day, today, now, zone, report, appName,
        onMove = { selected = shiftedDay(selected, today, it) }, state = navigation) {
        RawLog(access, today, zone, day)
    }
}

@Composable
internal fun rememberDayReport(batch: HistoryBatch?, day: java.time.LocalDate, today: java.time.LocalDate,
    zone: ZoneId, now: Long, ownPackage: String,
    calculate: (HistoryBatch, java.time.LocalDate, ZoneId, Long, String) -> DayReport = ::dayReport,
): State<DayReport?> {
    val latestNow by rememberUpdatedState(now)
    val latestCalculate by rememberUpdatedState(calculate)
    // Keep the clock/now marker live without cancelling replay each second.
    // Historical days replay only for new data or when the selected day changes.
    val tick = if (day == today) now / 30_000 else null
    return key(day, zone) {
        produceState<DayReport?>(null, batch, tick) {
            val data = batch ?: return@produceState
            val observedNow = latestNow
            val build = latestCalculate
            value = withContext(Dispatchers.Default) { build(data, day, zone, observedNow, ownPackage) }
        }
    }
}

internal class TimelineNavigationState(
    detail: MutableState<Long?>, diagnostics: MutableState<Boolean>, focused: MutableState<Long?>,
    val list: LazyListState,
) {
    var detail by detail
    var diagnostics by diagnostics
    var focused by focused
    fun clear() { detail = null; diagnostics = false; focused = null }
}

@Composable
internal fun rememberTimelineNavigation(day: java.time.LocalDate): TimelineNavigationState {
    val detail = rememberSaveable(day.toString()) { mutableStateOf<Long?>(null) }
    val diagnostics = rememberSaveable { mutableStateOf(false) }
    val focused = rememberSaveable(day.toString()) { mutableStateOf<Long?>(null) }
    val list = key(day) { rememberLazyListState() }
    return remember(detail, diagnostics, focused, list) { TimelineNavigationState(detail, diagnostics, focused, list) }
}

@Composable
internal fun TimelineNavigation(day: java.time.LocalDate, today: java.time.LocalDate, now: Long, zone: ZoneId,
    report: DayReport?, appName: (String) -> String, onMove: (Long) -> Unit,
    state: TimelineNavigationState = rememberTimelineNavigation(day), diagnosticContent: @Composable () -> Unit,
) {
    BackHandler(state.diagnostics || state.detail != null) {
        if (state.diagnostics) state.diagnostics = false else state.detail = null
    }
    val session = state.detail?.let { anchor -> report?.let { appSession(it.periods, anchor) } }.orEmpty()
    LaunchedEffect(state.detail, report) {
        if (report != null && state.detail != null && session.isEmpty()) state.detail = null
    }
    when {
        state.diagnostics -> diagnosticContent()
        session.isNotEmpty() -> SessionScreen(session, sessionSignals(session, report!!.raw), zone, appName, firstListener = report.firstListener) { state.detail = null }
        else -> DayScreen(day, today, now, zone, report, appName, onMove,
            onOpen = { state.focused = it.start; state.detail = it.start },
            onDiagnostics = { state.diagnostics = true }, list = state.list,
            focusedAnchor = state.focused, onRowFocus = { state.focused = it })
    }
}
