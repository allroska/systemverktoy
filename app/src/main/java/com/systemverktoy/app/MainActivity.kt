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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.systemverktoy.app.ui.theme.SystemverktoyTheme
import kotlinx.coroutines.delay
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
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(40.dp, 24.dp),
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
    LaunchedEffect(day) { listState.scrollToItem(0) }
    fun move(days: Long) { selected = minOf(day.plusDays(days), today).toString() }
    Column(Modifier.fillMaxSize().onPreviewKeyEvent {
        val direction = when (it.key) { Key.DirectionLeft -> -1L; Key.DirectionRight -> 1L; else -> null }
        if (direction == null) false else {
            if (it.type == KeyEventType.KeyDown) move(direction)
            true
        }
    }, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Button(onClick = { move(-1) }) { Text("Forrige dag", fontSize = 17.sp) }
            Text((if (day == today) "I dag · " else "") + day.format(
                DateTimeFormatter.ofPattern("EEEE d. MMMM yyyy", Locale.forLanguageTag("nb"))), fontSize = 24.sp)
            Button(onClick = { move(1) }, enabled = day < today) { Text("Neste dag", fontSize = 17.sp) }
        }
        Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            DiagnosticText("Brukstilgang: ${onOff(access.usage)} · Varsler: ${onOff(access.notifications)} · Lytter: ${if (connected) "tilkoblet" else "frakoblet"}")
            val battery = runCatching { context.getSystemService(PowerManager::class.java)
                .isIgnoringBatteryOptimizations(context.packageName) }.getOrNull()
            DiagnosticText("Hjerteslag: ${stamp(heartbeat, zone)} · Unntatt batterioptimalisering: ${battery?.let { if (it) "ja" else "nei" } ?: "ukjent"}")
            val dream = runCatching { Settings.Secure.getString(context.contentResolver, "screensaver_components") }.getOrNull()
            DiagnosticText("Skjermsparer: ${dream ?: "ukjent / ikke tilgjengelig"}")
            val launcher = runCatching { context.packageManager.resolveActivity(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)?.activityInfo?.packageName }.getOrNull()
            DiagnosticText("Launcher: ${launcher ?: "ukjent"}")
            DiagnosticText("Eldst hos Android (siste lesing): ${stamp(oldest, zone)} · Egne rader: $count")
        }
        Text("Rå logg · ${events.size} hendelser · nyeste øverst · " +
            stringResource(R.string.version, BuildConfig.VERSION_NAME), fontSize = 17.sp)
        LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
            if (events.isEmpty()) item { Text("Ingen lagrede hendelser for denne dagen.", fontSize = 20.sp) }
            items(events, key = { it.id }) { event ->
                var focused by remember { mutableStateOf(false) }
                Column(Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused }
                    .border(2.dp, if (focused) Color.White else Color.Transparent)
                    .background(if (focused) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface)
                    .focusable().padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("${Instant.ofEpochMilli(event.time).atZone(zone).format(DateTimeFormatter.ofPattern("HH:mm:ss xxx"))} · ${event.source} · ${event.type}", fontSize = 18.sp)
                    event.packageName?.let { Text(it, fontSize = 17.sp) }
                    event.activity?.let { Text(it, fontSize = 17.sp) }
                    event.state?.let { Text(it, fontSize = 17.sp) }
                    event.title?.takeIf { it.isNotBlank() }?.let { Text(it, fontSize = 18.sp) }
                }
            }
        }
    }
}

@Composable
private fun DiagnosticText(text: String) { Text(text, fontSize = 17.sp) }
private fun onOff(value: Boolean) = if (value) "på" else "av"
private fun stamp(time: Long?, zone: ZoneId): String = time?.let {
    Instant.ofEpochMilli(it).atZone(zone).format(DateTimeFormatter.ofPattern("dd.MM HH:mm:ss xxx"))
} ?: "ukjent / ingen"
