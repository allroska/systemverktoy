package com.systemverktoy.app

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
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
    var diagnostics by rememberSaveable { mutableStateOf(false) }
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val day = selectedDay(selected, today)
    var detail by rememberSaveable(day.toString()) { mutableStateOf<Long?>(null) }
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
        if (!access.ready) { selected = null; diagnostics = false; detail = null }
    }
    BackHandler(diagnostics || detail != null) {
        if (diagnostics) diagnostics = false else detail = null
    }
    if (!access.ready) { SetupScreen(access); return }
    if (diagnostics) { RawLog(access, today, zone, day); return }
    val window = remember(day, zone) { historyWindow(day, zone) }
    val history = remember(window) {
        combine(context.repository.dao.timelineRange(window.floor, window.end), context.repository.dao.oldest(), ::HistoryBatch)
    }
    val batch by key(window) { history.collectAsStateWithLifecycle<HistoryBatch?>(null) }
    val report by key(day, zone) {
        produceState<DayReport?>(null, batch, now) {
            val data = batch ?: return@produceState
            value = withContext(Dispatchers.Default) { dayReport(data, day, zone, now, context.packageName) }
        }
    }
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
    val session = detail?.let { anchor -> report?.let { appSession(it.periods, anchor) } }.orEmpty()
    if (session.isNotEmpty()) {
        SessionScreen(session, sessionSignals(session, report!!.raw), zone, appName) { detail = null }
    } else {
        DayScreen(day, today, now, zone, report, appName,
            onMove = { selected = shiftedDay(selected, today, it) },
            onOpen = { detail = it.start }, onDiagnostics = { diagnostics = true })
    }
}
