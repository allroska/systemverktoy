package com.systemverktoy.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

private val blue = Color(0xFF4E95F0)
private val muted = Color(0xFFA3ADB8)
private val purple = Color(0xFF9B8AF0)
private val orange = Color(0xFFE8875F)
private val surface = Color(0xFF182028)
private val norwegian = Locale.forLanguageTag("nb")

@Composable
internal fun WeekScreen(summary: WeekSummary?, selected: LocalDate, today: LocalDate,
    appName: (String) -> String, onMove: (Long) -> Unit, onOpen: (LocalDate) -> Unit, onClose: () -> Unit) {
    var showApps by remember(summary?.start) { mutableStateOf(false) }
    BackHandler { if (showApps) showApps = false else onClose() }
    val inputMode = LocalInputModeManager.current
    val requests = remember { List(7) { FocusRequester() } }
    val other = remember { FocusRequester() }
    var focusedDay by remember(summary?.start) { mutableIntStateOf(
        if (summary?.days?.any { it.day == today } == true) today.dayOfWeek.value - 1
        else if (summary?.days?.any { it.day == selected } == true) selected.dayOfWeek.value - 1 else 0) }
    LaunchedEffect(summary?.start, showApps) {
        if (summary != null && !showApps) {
            inputMode.requestInputMode(InputMode.Keyboard); withFrameNanos { }
            requests[focusedDay].requestFocus()
        }
    }
    if (showApps && summary != null) {
        WeekApps(summary.apps, appName) { showApps = false }
        return
    }
    Column(Modifier.fillMaxSize().background(Color(0xFF0F1317)).padding(32.dp, 18.dp)
        .onPreviewKeyEvent {
            val step = when (it.key) { Key.DirectionLeft -> -1L; Key.DirectionRight -> 1L; else -> null }
            if (step == null) false else {
                if (it.type == KeyEventType.KeyDown && it.nativeKeyEvent.repeatCount == 0) onMove(step)
                true
            }
        }) {
        if (summary == null) { Text("Laster ukesoversikt…", color = Color.White); return@Column }
        val end = summary.start.plusDays(6)
        val dateFormat = DateTimeFormatter.ofPattern("EEEE d. MMMM", norwegian)
        val startFormat = if (summary.start.month == end.month) DateTimeFormatter.ofPattern("EEEE d.", norwegian) else dateFormat
        Row {
            Text("Uke ${summary.number} · ${summary.start.format(startFormat)} – ${end.format(dateFormat)}",
                fontSize = 11.sp, color = muted, modifier = Modifier.weight(1f))
            Text("↑ / ↓ dag · OK åpne dagen · ← / → uke", fontSize = 11.sp, color = muted)
        }
        Row(Modifier.fillMaxWidth().padding(top = 10.dp).heightIn(min = 131.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 24.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("TV-titting, snitt per dag", fontSize = 13.sp, color = muted)
                Row {
                    Text("Uke ${summary.number}", fontSize = 15.sp, color = Color.White, modifier = Modifier.width(100.dp))
                    Text(summary.average?.let(::minutesText) ?: "–", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = blue)
                }
                Row {
                    Text("Forrige uke", fontSize = 15.sp, color = muted, modifier = Modifier.width(100.dp))
                    Text(summary.previousAverage?.let(::minutesText) ?: "–", fontSize = 15.sp, color = muted)
                }
                val comparison = if (summary.average != null && summary.previousAverage != null) {
                    val difference = summary.average - summary.previousAverage
                    if (difference == 0L) "Samme som forrige uke · " else
                        "${abs(difference)} min ${if (difference > 0) "mer" else "mindre"} per dag enn forrige uke · "
                } else ""
                Text(comparison + "totalt ${minutesText(summary.total)}${if (summary.ongoing) " så langt" else ""}", fontSize = 12.sp, color = muted)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Andel av TV-tittingen denne uka", fontSize = 11.sp, color = muted)
                summary.appLines.forEach { line ->
                    val others = line.apps.size > 1
                    AppShare(if (others) "Andre (${line.apps.size} apper) ›" else appName(line.apps.single().packageName),
                        line.minutes, line.percent, if (others) Color(0xFF7D8893) else blue,
                        if (others) Modifier.focusRequester(other).focusProperties { down = requests[0]; up = FocusRequester.Cancel }
                            .clickable { showApps = true } else Modifier)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.size(8.dp).background(purple))
                    Text("HDMI / TV-kanaler ${durationText(summary.hdmiMillis)}", fontSize = 12.sp, color = muted)
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(top = 8.dp, bottom = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.width(90.dp))
            repeat(24) { Text("%02d".format(it), fontSize = 11.sp, color = muted, modifier = Modifier.weight(1f), maxLines = 1) }
            Text("sett på", fontSize = 11.sp, color = muted, modifier = Modifier.width(100.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            summary.days.forEachIndexed { index, day ->
                var focused by remember { mutableStateOf(false) }
                Row(Modifier.fillMaxWidth().weight(1f).focusRequester(requests[index])
                    .focusProperties {
                        up = if (index > 0) requests[index - 1] else if (summary.apps.size > 4) other else FocusRequester.Cancel
                        down = if (index < 6) requests[index + 1] else FocusRequester.Cancel
                    }
                    .onFocusChanged { focused = it.isFocused; if (focused) focusedDay = index }
                    .testTag("week-day-${day.day}")
                    .border(2.dp, if (focused) Color.White else Color.Transparent, RoundedCornerShape(8.dp))
                    .background(if (focused) Color(0xFF212B35) else Color.Transparent, RoundedCornerShape(8.dp))
                    .clickable { if (!day.future) onOpen(day.day) }.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    val color = if (day.future) muted else Color(0xFFEEF1F4)
                    Text(day.day.format(DateTimeFormatter.ofPattern("EEE d.", norwegian)), fontSize = 15.sp, fontWeight = FontWeight.Bold,
                        color = color, modifier = Modifier.width(90.dp), maxLines = 1)
                    day.cells.forEach { cell -> HourTile(cell, Modifier.weight(1f).padding(end = 2.dp).height(26.dp)) }
                    Text(if (day.future) "–" else minutesText(day.minutes), fontSize = 15.sp, fontWeight = FontWeight.Bold,
                        color = color, modifier = Modifier.width(100.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End)
                }
            }
        }
        Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(HourCell.WATCHING to "Sett på", HourCell.HDMI to "HDMI / TV-kanaler", HourCell.UNKNOWN to "Ukjent – mangler logg",
                HourCell.FUTURE to "Ikke kommet ennå").forEach { (cell, name) ->
                HourTile(cell, Modifier.size(11.dp)); Text(name, color = muted, fontSize = 12.sp)
            }
            Spacer(Modifier.weight(1f))
            Text("Hver rute = én time", color = muted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun HourTile(cell: HourCell, modifier: Modifier) {
    Canvas(modifier) {
        val color = when (cell) { HourCell.WATCHING -> blue; HourCell.HDMI -> purple; HourCell.UNKNOWN -> Color(0xFF6B3B28); else -> surface }
        if (cell == HourCell.FUTURE) drawRect(Color(0xFF26303A), style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
        else {
            drawRect(color)
            if (cell == HourCell.UNKNOWN) {
                // Lines stay inside the tile, including on narrow 720p columns.
                var x = -size.height
                while (x < size.width) {
                    val a = maxOf(0f, -x); val b = minOf(size.height, size.width - x)
                    if (a < b) drawLine(orange, Offset(x + a, size.height - a), Offset(x + b, size.height - b), 2.dp.toPx())
                    x += 6.dp.toPx()
                }
            }
        }
    }
}

@Composable
private fun AppShare(name: String, minutes: Long, percent: Long, color: Color, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    // Focus observer and border precede clickable so the complete row is highlighted.
    Row(Modifier.onFocusChanged { focused = it.hasFocus }.border(2.dp, if (focused) Color.White else Color.Transparent)
        .then(modifier).heightIn(min = 22.dp).padding(horizontal = 3.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(8.dp).background(color, RoundedCornerShape(4.dp)))
        Text(name, fontSize = 14.sp, color = Color(0xFFEEF1F4), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text("$percent %", fontSize = 14.sp, color = muted, modifier = Modifier.width(45.dp))
        Text(minutesText(minutes), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFFEEF1F4), modifier = Modifier.width(90.dp))
    }
}

@Composable
private fun WeekApps(apps: List<WeekApp>, appName: (String) -> String, onClose: () -> Unit) {
    val first = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { first.requestFocus() }
    Column(Modifier.fillMaxSize().background(Color(0xFF0F1317)).padding(32.dp, 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Alle ukens apper · Tilbake lukker listen", color = Color.White, fontSize = 18.sp,
            modifier = Modifier.focusRequester(first).onFocusChanged { focused = it.isFocused }
                .border(2.dp, if (focused) Color.White else Color.Transparent).clickable(onClick = onClose).padding(8.dp))
        LazyColumn {
            items(apps) { app -> AppShare(appName(app.packageName), app.minutes, app.percent, blue, Modifier.clickable { }) }
        }
    }
}
