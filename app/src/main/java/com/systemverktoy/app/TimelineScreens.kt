package com.systemverktoy.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val muted = Color(0xFFA3ADB8)
private val ink = Color(0xFFEEF1F4)
private val panel = Color(0xFF182028)
private val focusPanel = Color(0xFF212B35)
private val shape = RoundedCornerShape(10.dp)
private fun tint(category: Category) = when (category) {
    Category.WATCHING -> Color(0xFF4E95F0)
    Category.APP_OPEN -> Color(0xFFF7C266)
    Category.IDLE -> Color(0xFF56616C)
    Category.HOME -> Color(0xFF9AA4AF)
    Category.UNKNOWN -> Color(0xFFE8875F)
    Category.HDMI -> Color(0xFF9B8AF0)
    Category.OFF -> panel
}

@Composable
internal fun DayScreen(day: LocalDate, today: LocalDate, now: Long, zone: ZoneId, report: DayReport?,
    appName: (String) -> String, onMove: (Long) -> Unit, onOpen: (TimelinePeriod) -> Unit, onDiagnostics: () -> Unit,
    list: LazyListState = rememberLazyListState(), focusedAnchor: Long? = null, onRowFocus: (Long) -> Unit = {}) {
    val heading = remember { FocusRequester() }
    val first = remember { FocusRequester() }
    val restored = remember { FocusRequester() }
    val focusIndex = report?.periods?.indexOfFirst { focusedAnchor != null && focusedAnchor >= it.start && focusedAnchor < it.endExclusive }
        ?.coerceAtLeast(0) ?: 0
    val inputMode = LocalInputModeManager.current
    var needsFocus by remember(day) { mutableStateOf(true) }
    LaunchedEffect(day, report != null) {
        if (report != null && needsFocus) {
            inputMode.requestInputMode(InputMode.Keyboard)
            withFrameNanos { }
            if (report.periods.isEmpty()) heading.requestFocus() else {
                if (list.layoutInfo.visibleItemsInfo.none { it.index == focusIndex }) {
                    list.scrollToItem(focusIndex)
                    withFrameNanos { }
                }
                if (focusIndex == 0) first.requestFocus() else restored.requestFocus()
            }
            needsFocus = false
        }
    }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(40.dp, 28.dp)
        .onPreviewKeyEvent {
            val step = when (it.key) { Key.DirectionLeft -> -1L; Key.DirectionRight -> 1L; else -> null }
            if (step == null) false else {
                if (it.type == KeyEventType.KeyDown && it.nativeKeyEvent.repeatCount == 0) onMove(step)
                true
            }
        }, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            DayHeading(when (day) { today -> "I dag"; today.minusDays(1) -> "I går"; else -> "Tidligere dag" },
                Modifier.focusRequester(heading).focusProperties {
                    down = if (report?.periods?.isNotEmpty() == true) first else FocusRequester.Cancel
                }, onDiagnostics)
            Text(day.format(DateTimeFormatter.ofPattern("EEEE d. MMMM", Locale.forLanguageTag("nb"))),
                fontSize = 20.sp, color = muted, modifier = Modifier.weight(1f))
            Text("← / → bytter dag · OK = detaljer", fontSize = 15.sp, color = muted)
        }
        if (report == null) { Text("Laster tidslinje…", color = ink, fontSize = 18.sp); return@Column }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            SummaryCard("Sett på", durationText(report.total(Category.WATCHING)), report.watchingByApp.entries
                .sortedByDescending { it.value }.joinToString(" · ") { "${appName(it.key)} ${durationText(it.value)}" }
                .ifEmpty { "Ingen avspilling registrert" }, Modifier.weight(1f))
            SummaryCard("Sto bare på", durationText(report.total(Category.IDLE)), "Skjermsparer", Modifier.weight(1f))
            SummaryCard("Første gang slått på", report.firstOn?.let { clockText(it, zone) } ?: "—",
                if (day == today) "Klokka er nå ${clockText(now, zone)}" else "Registrert påslag", Modifier.weight(1f))
        }
        Text("App åpen: ${durationText(report.total(Category.APP_OPEN))} · HDMI: ${durationText(report.total(Category.HDMI))} · Startskjerm teller ikke",
            color = muted, fontSize = 15.sp)
        if (report.gaps.isNotEmpty()) {
            Notice("Ufullstendig dag: ingen logg ${report.gaps.joinToString(", ") { periodTime(it, zone) }}. Summene dekker kjent aktivitet.")
        } else if (report.limitedContext) {
            Notice("Begrenset grunnlag: signaler fra mer enn 48 timer før dagen er utelatt. Eldre tilstand kan mangle.")
        }
        val bounds = dayBounds(day, zone)
        PeriodBar(report.periods, day.atTime(6, 0).atZone(zone).toInstant().toEpochMilli(), bounds.endExclusive,
            now.takeIf { day == today }, Modifier.fillMaxWidth().height(26.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("06", "09", "12", "15", "18", "21", "24").forEach { Text(it, fontSize = 15.sp, color = muted) }
        }
        LazyColumn(state = list, modifier = Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (report.periods.isEmpty()) item {
                Text(when {
                    report.beforeHistory -> "Ingen lagrede data fra denne dagen."
                    report.limitedContext -> "Ingen sikre perioder i det avgrensede grunnlaget."
                    report.hasActivity -> "Ingen annen aktivitet registrert denne dagen."
                    day == today -> "TV-en har ikke vært slått på i dag."
                    else -> "TV-en har ikke vært slått på denne dagen."
                }, fontSize = 20.sp, color = ink, modifier = Modifier.padding(vertical = 20.dp))
            }
            itemsIndexed(report.periods, key = { _, row -> row.start }) { index, row ->
                PeriodRow(row, zone, rowName(row, appName),
                    (when (index) {
                        0 -> Modifier.focusRequester(first).focusProperties { up = heading }
                        focusIndex -> Modifier.focusRequester(restored)
                        else -> Modifier
                    }).testTag("period-${row.start}"), onFocus = { onRowFocus(row.start) }) { onOpen(row) }
            }
        }
        Text("«Sett på» betyr avspilling, også automatisk avspilling.", fontSize = 15.sp, color = muted)
    }
}

@Composable
private fun DayHeading(text: String, modifier: Modifier, onDiagnostics: () -> Unit) {
    val scope = rememberCoroutineScope()
    var held by remember { mutableStateOf<Job?>(null) }
    var focused by remember { mutableStateOf(false) }
    DisposableEffect(Unit) { onDispose { held?.cancel() } }
    Text(text, fontSize = 32.sp, color = ink, modifier = modifier.testTag("day-heading")
        .onFocusChanged { focused = it.isFocused; if (!it.isFocused) held?.cancel() }
        .border(2.dp, if (focused) ink else Color.Transparent, shape).padding(8.dp, 4.dp)
        .onKeyEvent {
            if (it.key !in listOf(Key.DirectionCenter, Key.Enter, Key.NumPadEnter)) false else {
                if (it.type == KeyEventType.KeyDown && it.nativeKeyEvent.repeatCount == 0) {
                    held?.cancel()
                    held = scope.launch { delay(3000); onDiagnostics() }
                } else if (it.type == KeyEventType.KeyUp) held?.cancel()
                true
            }
        }.focusable())
}

@Composable
private fun SummaryCard(title: String, value: String, detail: String, modifier: Modifier) {
    Column(modifier.background(panel, shape).padding(16.dp, 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, color = muted, fontSize = 15.sp)
        Text(value, color = ink, fontSize = 30.sp)
        Text(detail, color = muted, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun Notice(text: String) { Text(text, fontSize = 17.sp, color = Color(0xFFF4B79C), maxLines = 3,
    modifier = Modifier.fillMaxWidth().border(1.dp, tint(Category.UNKNOWN), shape).background(panel, shape).padding(12.dp, 8.dp)) }

private fun rowName(row: TimelinePeriod, appName: (String) -> String): String = when (row.category) {
    Category.IDLE -> "Skjermsparer"
    Category.UNKNOWN -> "Ukjent"
    Category.HOME -> "Startskjerm"
    Category.HDMI -> row.title ?: row.packageName?.let(appName) ?: "HDMI"
    else -> row.packageName?.let(appName) ?: categoryText(row)
}

@Composable
private fun PeriodRow(row: TimelinePeriod, zone: ZoneId, name: String, modifier: Modifier, onFocus: () -> Unit, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocus() }
        .border(2.dp, if (focused) ink else Color.Transparent, shape)
        .background(if (focused) focusPanel else Color.Transparent, shape).clickable(onClick = onClick)
        .padding(14.dp, 10.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(periodTime(row, zone), fontSize = 18.sp, color = ink, modifier = Modifier.width(180.dp))
        Box(Modifier.size(12.dp).background(tint(row.category), RoundedCornerShape(6.dp)))
        Text(name, fontSize = 19.sp, color = ink, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
        Box(Modifier.weight(1.5f)) { CategoryChip(row) }
        Column(Modifier.width(120.dp), horizontalAlignment = Alignment.End) {
            Text(durationText(row.durationMillis), fontSize = 18.sp, color = ink)
            if (row.ongoing) Text("pågår", fontSize = 15.sp, color = muted)
        }
    }
}

@Composable
private fun CategoryChip(row: TimelinePeriod) {
    val color = tint(row.category)
    Box(Modifier.background(color.copy(alpha = 0.14f), RoundedCornerShape(18.dp))) {
        if (row.category == Category.HOME) Canvas(Modifier.matchParentSize()) {
            drawRoundRect(color, cornerRadius = CornerRadius(18.dp.toPx()), style = Stroke(1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))))
        }
        Text(categoryText(row), color = if (row.category == Category.IDLE) ink else color, fontSize = 15.sp,
            modifier = Modifier.padding(10.dp, 5.dp))
    }
}

@Composable
internal fun PeriodBar(rows: List<TimelinePeriod>, start: Long, end: Long, now: Long?, modifier: Modifier) {
    Canvas(modifier.background(panel, RoundedCornerShape(6.dp))) {
        val span = (end - start).coerceAtLeast(1).toFloat()
        rows.forEach { row ->
            val a = (maxOf(start, row.start) - start) / span * size.width
            val b = (minOf(end, row.endExclusive) - start) / span * size.width
            if (b > a) {
                drawRect(tint(row.category).copy(alpha = if (row.category == Category.UNKNOWN) 0.2f else 1f), Offset(a, 0f), Size(b - a, size.height))
                if (row.category == Category.UNKNOWN) clipRect(a, 0f, b, size.height) {
                    var x = a - size.height
                    while (x < b) {
                        drawLine(tint(Category.UNKNOWN), Offset(x, size.height), Offset(x + size.height, 0f), 2.dp.toPx())
                        x += 8.dp.toPx()
                    }
                }
            }
        }
        if (now != null && now in start..end) {
            val x = (now - start) / span * size.width
            drawLine(ink, Offset(x, 0f), Offset(x, size.height), 2.dp.toPx())
        }
    }
}

@Composable
internal fun SessionScreen(rows: List<TimelinePeriod>, signals: List<RawEvent>, zone: ZoneId,
    appName: (String) -> String, onBack: () -> Unit) {
    val back = remember { FocusRequester() }
    val leftFocus = remember { FocusRequester() }
    val rightFocus = remember { FocusRequester() }
    val history = rememberLazyListState()
    val raw = rememberLazyListState()
    val inputMode = LocalInputModeManager.current
    LaunchedEffect(rows.first().start) { inputMode.requestInputMode(InputMode.Keyboard); back.requestFocus() }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(40.dp, 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(rowName(rows.first(), appName), fontSize = 32.sp, color = ink, modifier = Modifier.weight(1f))
            Text("${clockText(rows.first().start, zone)} – ${clockText(rows.last().endExclusive, zone)} · ${durationText(rows.sumOf { it.durationMillis })}",
                color = muted, fontSize = 18.sp)
            Button(onClick = onBack, modifier = Modifier.focusRequester(back).focusProperties { down = leftFocus }.testTag("detail-back")) {
                Text("Tilbake", fontSize = 18.sp)
            }
        }
        PeriodBar(rows, rows.first().start, rows.last().endExclusive, null, Modifier.fillMaxWidth().height(40.dp))
        Text(rows.groupBy(::categoryText).entries.joinToString(" · ") { (label, group) ->
            "$label: ${durationText(group.sumOf { it.durationMillis })}"
        }, fontSize = 15.sp, color = muted)
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            LazyColumn(state = history, modifier = Modifier.weight(1f).fillMaxHeight()
                .focusRequester(leftFocus).focusProperties { up = back; right = rightFocus }.testTag("session-history").scrollPane(history),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item { Text("Hva skjedde", color = muted, fontSize = 18.sp) }
                items(rows, key = { it.start }) { row ->
                    Text("${periodTime(row, zone)} · ${categoryText(row)} · ${durationText(row.durationMillis)}",
                        fontSize = 18.sp, color = ink, modifier = Modifier.fillMaxWidth().background(panel, shape).padding(12.dp))
                }
                item {
                    val titles = rows.mapNotNull { it.title?.takeIf(String::isNotBlank) }.distinct()
                    Text("Hva ble sett?\n" + if (titles.isNotEmpty()) titles.joinToString("\n")
                        else "Appen oppga ingen tittel. Se appens egen seerhistorikk, hvis den har det.",
                        fontSize = 17.sp, color = ink, modifier = Modifier.fillMaxWidth().border(1.dp, muted, shape).padding(16.dp))
                }
                if (rows.first().category == Category.HDMI) item {
                    Text("HDMI viser valgt inngang. Konsollen oppgir ikke om noe faktisk spilles.", fontSize = 17.sp, color = muted)
                }
            }
            LazyColumn(state = raw, modifier = Modifier.weight(1f).fillMaxHeight()
                .focusRequester(rightFocus).focusProperties { up = back; left = leftFocus }.testTag("session-signals").scrollPane(raw),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item { Text("Grunnlag – rå signaler", color = muted, fontSize = 18.sp) }
                item { Text("Signaler fra før økten kan forklare tilstanden ved start.", fontSize = 15.sp, color = muted) }
                items(signals) { event ->
                    val source = when (event.source) {
                        Source.USAGE -> "App-logg"; Source.MEDIA -> "Avspilling"; Source.DREAM -> "Skjermsparer"
                        Source.AUDIO -> "Lyd"; Source.LISTENER -> "Logger"; Source.APP -> "App-status"
                    }
                    Text(Instant.ofEpochMilli(event.time).atZone(zone).format(DateTimeFormatter.ofPattern("dd.MM HH:mm:ss xxx")) +
                        " · $source\n" + listOfNotNull(event.type, event.packageName, event.activity, event.state, event.title).joinToString(" · "),
                        fontSize = 15.sp, color = ink, modifier = Modifier.fillMaxWidth().background(panel, shape).padding(12.dp))
                }
            }
        }
        Text("Opp/ned: bla · Venstre/høyre: velg liste · Tilbake: tidslinjen", fontSize = 15.sp, color = muted)
    }
}

@Composable
private fun Modifier.scrollPane(state: LazyListState): Modifier {
    val scope = rememberCoroutineScope()
    var focused by remember { mutableStateOf(false) }
    return onFocusChanged { focused = it.isFocused }.border(2.dp, if (focused) ink else Color.Transparent, shape)
        .onPreviewKeyEvent {
            val step = when (it.key) { Key.DirectionUp -> -1; Key.DirectionDown -> 1; else -> null }
            if (step == null || (step < 0 && !state.canScrollBackward) || (step > 0 && !state.canScrollForward)) false
            else {
                if (it.type == KeyEventType.KeyDown) scope.launch {
                    state.scrollBy(step * (state.layoutInfo.visibleItemsInfo.firstOrNull()?.size ?: 60).toFloat())
                }
                true
            }
        }.focusable()
}
