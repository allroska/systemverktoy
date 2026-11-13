package com.systemverktoy.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun OverviewScreen(day: LocalDate, today: LocalDate, zone: ZoneId, report: DayReport?,
    appName: (String) -> String, onMove: (Long) -> Unit, onOpen: (ViewingSession) -> Unit,
    onTimeline: () -> Unit, onWeek: () -> Unit = {}, onDiagnostics: () -> Unit, list: LazyListState, focusedAnchor: Long?, onFocus: (Long) -> Unit) {
    val sessions = remember(report) { viewingSessions(report?.periods.orEmpty()) }
    val minutes = remember(sessions) { allocateMinutes(sessions.map { it.watching }) }
    val first = remember { FocusRequester() }
    val heading = remember { FocusRequester() }
    val footer = remember { FocusRequester() }
    val restored = remember { FocusRequester() }
    val count = sessions.count { !it.unknown }
    val target = sessions.indexOfFirst { it.start == focusedAnchor }.coerceAtLeast(0)
    val inputMode = LocalInputModeManager.current
    val blue = Color(0xFF4E95F0)
    val muted = Color(0xFFA3ADB8)
    LaunchedEffect(day, report != null) {
        if (report != null) {
            inputMode.requestInputMode(InputMode.Keyboard)
            withFrameNanos { }
            if (sessions.isEmpty()) footer.requestFocus() else {
                val item = target + if (sessions.none { !it.unknown }) 1 else 0
                if (list.layoutInfo.visibleItemsInfo.none { it.index == item }) {
                    list.scrollToItem(item); withFrameNanos { }
                }
                if (target == 0) first.requestFocus() else restored.requestFocus()
            }
        }
    }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(32.dp, 18.dp)
        .onPreviewKeyEvent {
            val step = when (it.key) { Key.DirectionLeft -> -1L; Key.DirectionRight -> 1L; else -> null }
            if (step == null) false else {
                if (it.type == KeyEventType.KeyDown && it.nativeKeyEvent.repeatCount == 0) onMove(step)
                true
            }
        }, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DayHeading(if (day == today) "I dag" else if (day == today.minusDays(1)) "I går" else "Tidligere dag",
                Modifier.focusRequester(heading).onPreviewKeyEvent {
                    if (it.key == Key.DirectionUp) { if (it.type == KeyEventType.KeyDown) onWeek(); true } else false
                }.focusProperties { down = if (sessions.isEmpty()) footer else first }, onDiagnostics)
            Text(" · " + day.format(DateTimeFormatter.ofPattern("EEEE d. MMMM", Locale.forLanguageTag("nb"))),
                fontSize = 11.sp, color = muted, modifier = Modifier.weight(1f))
            Text("← / → dag · OK økt · ↑ uke · ↓ detaljer", fontSize = 11.sp, color = muted)
        }
        if (report == null) { Text("Laster oversikt…", color = Color(0xFFEEF1F4)); return@Column }
        Row(Modifier.padding(vertical = 12.dp), verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(if (day == today) "TV-titting i dag" else "TV-titting", fontSize = 16.sp, color = muted)
            Text(minutesText(report.watchingMinutes()), fontSize = 44.sp, color = blue)
            Text("${if (count == 1) "1 økt" else "$count økter"} · første påslag ${firstOnText(report, zone)}", fontSize = 15.sp, color = muted)
        }
        monitoringText(report, day, zone)?.let { Text(it, fontSize = 11.sp, color = muted) }
        if (report.limitedContext) Text("Begrenset grunnlag: eldre tilstand kan mangle.", fontSize = 11.sp, color = muted)
        LazyColumn(state = list, modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (sessions.none { !it.unknown }) item {
                Text(if (day == today) "Ingen TV-titting i dag." else "Ingen TV-titting denne dagen.", fontSize = 18.sp, color = Color(0xFFEEF1F4))
            }
            itemsIndexed(sessions, key = { _, session -> session.start }) { index, session ->
                var focused by remember { mutableStateOf(false) }
                val shape = RoundedCornerShape(8.dp)
                val focusModifier = when (index) { 0 -> Modifier.focusRequester(first); target -> Modifier.focusRequester(restored); else -> Modifier }
                Row(focusModifier.fillMaxWidth().focusProperties { if (index == 0) up = heading; if (index == sessions.lastIndex) down = footer }
                    .onFocusChanged { focused = it.isFocused; if (focused) onFocus(session.start) }
                    .onPreviewKeyEvent {
                        if (index == 0 && it.key == Key.DirectionUp) {
                            if (it.type == KeyEventType.KeyDown && it.nativeKeyEvent.repeatCount == 0) onWeek()
                            true
                        } else if (index == sessions.lastIndex && it.key == Key.DirectionDown) {
                            if (it.type == KeyEventType.KeyDown && it.nativeKeyEvent.repeatCount == 0) onTimeline()
                            true
                        } else false
                    }
                    .border(2.dp, if (focused) Color.White else Color.Transparent, shape)
                    .background(if (focused) Color(0xFF212B35) else Color(0xFF182028), shape)
                    .clickable { onOpen(session) }.heightIn(min = 40.dp).padding(12.dp, 6.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val color = if (session.unknown) muted else blue
                    val period = session.periods.first().copy(endExclusive = session.end, ongoing = session.periods.last().ongoing)
                    Text(periodTime(period, zone), fontSize = 15.sp, color = muted, modifier = Modifier.width(104.dp))
                    Box(Modifier.size(10.dp).background(color, RoundedCornerShape(5.dp)))
                    Text(appName(session.packageName), color = Color(0xFFEEF1F4), fontSize = 15.sp, maxLines = 1, modifier = Modifier.width(112.dp))
                    Box(Modifier.weight(1f).height(8.dp).background(Color(0xFF2B3640), shape)) {
                        if (!session.unknown) Box(Modifier.fillMaxHeight().fillMaxWidth(session.cumulative).background(blue, shape))
                    }
                    Text(if (session.unknown) "avspilling ukjent" else minutesText(minutes[index]), fontSize = 15.sp,
                        color = if (session.unknown) muted else Color.White, modifier = Modifier.width(if (session.unknown) 142.dp else 100.dp))
                }
            }
        }
        var footerFocused by remember { mutableStateOf(false) }
        Row(Modifier.fillMaxWidth().focusRequester(footer).onFocusChanged { footerFocused = it.isFocused }
            .border(2.dp, if (footerFocused) Color.White else Color.Transparent).onPreviewKeyEvent {
                if (sessions.isEmpty() && it.key == Key.DirectionUp) {
                    if (it.type == KeyEventType.KeyDown && it.nativeKeyEvent.repeatCount == 0) onWeek(); true
                } else if (it.key == Key.DirectionDown) { if (it.type == KeyEventType.KeyDown) onTimeline(); true } else false
            }.clickable(onClick = onTimeline).padding(vertical = 4.dp)) {
            Text("HDMI / TV-kanaler ${durationText(report.total(Category.HDMI))} · Sto bare på ${durationText(report.total(Category.IDLE))}",
                color = muted, fontSize = 13.sp, modifier = Modifier.weight(1f))
            Text("↓ Alle detaljer", fontSize = 13.sp, color = muted)
        }
    }
}
