package com.systemverktoy.app

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.systemverktoy.app.ui.theme.SystemverktoyTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { SystemverktoyTheme { DiagnosticApp() } }
    }

    override fun onResume() {
        super.onResume()
        repository.log(Source.APP, "APP_OPENED", packageName)
        CollectorWorker.now(this)
        if (!repository.connected.value) requestListener(this)
    }
}

@Composable
private fun DiagnosticApp() {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var access by remember { mutableStateOf(Access.read(context)) }
    var zone by remember { mutableStateOf(ZoneId.systemDefault()) }
    var today by remember { mutableStateOf(LocalDate.now(zone)) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                access = Access.read(context)
                zone = ZoneId.systemDefault()
                today = LocalDate.now(zone)
                delay(2000)
            }
        }
    }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp, 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (!access.ready) {
            Text("To tillatelser må slås på", fontSize = 28.sp)
            Text("Finn «Systemverktøy» i listen. Slå bryteren på, og trykk Tilbake.", fontSize = 18.sp)
            PermissionRow("1. Brukstilgang", "Viser hvilke apper som har vært åpne, og når.", access.usage) {
                error = openSettings(context, Settings.ACTION_USAGE_ACCESS_SETTINGS)
            }
            PermissionRow("2. Tilgang til varsler", "Viser avspilling og pause.", access.notifications) {
                error = openSettings(context, Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            }
            error?.let { Text(it, fontSize = 17.sp) }
            Text("Ingenting sendes ut av TV-en. All logg lagres bare her.", fontSize = 17.sp)
        } else {
            RawLog(access, today, zone)
        }
    }
}

@Composable
private fun PermissionRow(title: String, explanation: String, enabled: Boolean, open: () -> Unit) {
    Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(20.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, fontSize = 22.sp)
            Text(explanation, fontSize = 17.sp)
        }
        if (enabled) Text("Slått på", color = Color(0xFF8FD6A6), fontSize = 18.sp)
        else Button(onClick = open) { Text("Åpne innstillinger", fontSize = 18.sp) }
    }
}

private fun openSettings(context: android.content.Context, action: String): String? {
    for (candidate in listOf(action, Settings.ACTION_SETTINGS)) {
        try {
            context.startActivity(Intent(candidate))
            return null
        } catch (_: ActivityNotFoundException) {
            // Some TV vendors omit the specific settings activity.
        } catch (_: SecurityException) {
            // Continue with the general settings screen.
        }
    }
    return "Åpne TV-ens Innstillinger → Apper → Spesiell apptilgang manuelt."
}

@Composable
private fun RawLog(access: Access, today: LocalDate, zone: ZoneId) {
    val context = LocalContext.current
    val repo = context.repository
    var selected by rememberSaveable { mutableStateOf(today.toString()) }
    val day = minOf(LocalDate.parse(selected), today)
    val bounds = remember(day, zone) { dayBounds(day, zone) }
    val dayFlow = remember(bounds) { repo.dao.day(bounds.start, bounds.endExclusive) }
    val events by key(bounds) { dayFlow.collectAsStateWithLifecycle(emptyList()) }
    val count by remember { repo.dao.count() }.collectAsStateWithLifecycle(0)
    val heartbeat by remember { repo.dao.heartbeat() }.collectAsStateWithLifecycle(null)
    val connected by repo.connected.collectAsStateWithLifecycle()
    val oldest by repo.oldestUsage.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    var importantOnly by rememberSaveable { mutableStateOf(false) }
    val launcher = remember(context) {
        runCatching { context.packageManager.resolveActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)?.activityInfo?.packageName }.getOrNull()
    }
    val visibleEvents = remember(events, importantOnly, launcher) {
        if (importantOnly) events.filter { isImportantEvent(it, launcher, context.packageName) } else events
    }
    val scope = rememberCoroutineScope()
    val filterFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { filterFocus.requestFocus() }
    // Reset on raw input as well: a newly collected, filtered-out event must also return to the top.
    LaunchedEffect(day, events, importantOnly) { listState.scrollToItem(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { listState.requestScrollToItem(0) }
    val buttonColors = ButtonDefaults.colors(
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = Color.White,
        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
        focusedContentColor = Color.White,
        disabledContainerColor = MaterialTheme.colorScheme.background,
        disabledContentColor = Color.White,
    )
    fun move(days: Long) { selected = minOf(day.plusDays(days), today).toString() }
    Column(Modifier.fillMaxSize().onPreviewKeyEvent {
        val direction = when (it.key) { Key.DirectionLeft -> -1L; Key.DirectionRight -> 1L; else -> null }
        val page = when (it.nativeKeyEvent.keyCode) {
            android.view.KeyEvent.KEYCODE_CHANNEL_UP, android.view.KeyEvent.KEYCODE_PAGE_UP -> -1
            android.view.KeyEvent.KEYCODE_CHANNEL_DOWN, android.view.KeyEvent.KEYCODE_PAGE_DOWN -> 1
            else -> null
        }
        if (page != null) {
            if (it.type == KeyEventType.KeyDown) scope.launch {
                val layout = listState.layoutInfo
                listState.scrollBy(page * (layout.viewportEndOffset - layout.viewportStartOffset).toFloat())
            }
            true
        } else if (direction == null) false else {
            if (it.type == KeyEventType.KeyDown) move(direction)
            true
        }
    }, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text((if (day == today) "I dag · " else "") + day.format(
                DateTimeFormatter.ofPattern("EEEE d. MMMM yyyy", Locale.forLanguageTag("nb"))),
                modifier = Modifier.weight(1f), fontSize = 20.sp, color = Color.White,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("← / → bytter dag", fontSize = 15.sp, color = Color.White)
            Button(onClick = { importantOnly = !importantOnly }, colors = buttonColors,
                modifier = Modifier.focusRequester(filterFocus)) {
                Text(if (importantOnly) "Bare viktige: på" else "Bare viktige: av", fontSize = 15.sp)
            }
        }
        Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
            DiagnosticText("Brukstilgang: ${onOff(access.usage)} · Varsler: ${onOff(access.notifications)} · Lytter: ${if (connected) "tilkoblet" else "frakoblet"}")
            val battery = runCatching { context.getSystemService(PowerManager::class.java)
                .isIgnoringBatteryOptimizations(context.packageName) }.getOrNull()
            DiagnosticText("Hjerteslag: ${stamp(heartbeat, zone)} · Unntatt batterioptimalisering: ${battery?.let { if (it) "ja" else "nei" } ?: "ukjent"}")
            val dream = runCatching { Settings.Secure.getString(context.contentResolver, "screensaver_components") }.getOrNull()
            DiagnosticText("Skjermsparer: ${dream ?: "ukjent / ikke tilgjengelig"}")
            DiagnosticText("Launcher: ${launcher ?: "ukjent"}")
            DiagnosticText("Eldst hos Android (siste lesing): ${stamp(oldest, zone)} · Egne rader: $count")
        }
        Text("Rå logg · ${visibleEvents.size}/${events.size} hendelser · nyeste øverst · Kanal +/−: én side · " +
            stringResource(R.string.version, BuildConfig.VERSION_NAME), fontSize = 15.sp, color = Color.White)
        var listFocused by remember { mutableStateOf(false) }
        // One focus target prevents a focused row from pulling paging or a reset back to its old position.
        LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxWidth()
            .onFocusChanged { listFocused = it.isFocused }
            .border(1.dp, if (listFocused) Color.White else Color.Transparent)
            .onPreviewKeyEvent {
                val step = when (it.key) { Key.DirectionUp -> -1; Key.DirectionDown -> 1; else -> null }
                if (step == null || (step < 0 && !listState.canScrollBackward)) false else {
                    if (it.type == KeyEventType.KeyDown) scope.launch {
                        val height = listState.layoutInfo.visibleItemsInfo.firstOrNull()?.size ?: 0
                        listState.scrollBy(step * height.toFloat())
                    }
                    true
                }
            }.focusable()) {
            if (visibleEvents.isEmpty()) item {
                Text(if (events.isEmpty()) "Ingen lagrede hendelser for denne dagen."
                    else "Ingen viktige hendelser for denne dagen.", fontSize = 15.sp, color = Color.White)
            }
            items(visibleEvents, key = { it.id }) { event ->
                Text(formatRawEvent(event, zone), modifier = Modifier.fillMaxWidth().heightIn(min = 20.dp)
                    .padding(horizontal = 3.dp), fontSize = 15.sp, lineHeight = 18.sp,
                    color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun DiagnosticText(text: String) { Text(text, fontSize = 15.sp, lineHeight = 18.sp,
    color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis) }
private fun onOff(value: Boolean) = if (value) "på" else "av"
private fun stamp(time: Long?, zone: ZoneId): String = time?.let {
    Instant.ofEpochMilli(it).atZone(zone).format(DateTimeFormatter.ofPattern("dd.MM HH:mm:ss xxx"))
} ?: "ukjent / ingen"
