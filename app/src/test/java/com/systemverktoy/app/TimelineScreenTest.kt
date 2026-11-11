package com.systemverktoy.app

import androidx.compose.runtime.*
import androidx.activity.compose.BackHandler
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import com.systemverktoy.app.ui.theme.SystemverktoyTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w1280dp-h720dp-mdpi", application = android.app.Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalTestApi::class)
class TimelineScreenTest {
    @get:Rule val compose = createComposeRule()
    private val day = LocalDate.of(2026, 11, 11)
    private val zone = ZoneId.of("Europe/Oslo")
    private fun time(hour: Int, minute: Int) = day.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()
    private val rows = listOf(
        TimelinePeriod(time(14, 8), time(14, 12), Category.HOME, "home"),
        TimelinePeriod(time(14, 12), time(14, 18), Category.APP_OPEN, "Netflix"),
        TimelinePeriod(time(14, 18), time(15, 33), Category.WATCHING, "Netflix"),
        TimelinePeriod(time(15, 33), time(15, 40), Category.APP_OPEN, "Netflix", signals = listOf(
            RawEvent(time = time(15, 33), source = Source.MEDIA, type = "PLAYBACK_STATE_CHANGED", packageName = "Netflix", state = "STATE_PAUSED"))),
        TimelinePeriod(time(15, 40), time(16, 0), Category.HDMI, "tvx", title = "HDMI 4"),
        TimelinePeriod(time(16, 0), time(17, 50), Category.IDLE),
        TimelinePeriod(time(17, 50), time(17, 52), Category.HOME, "home", ongoing = true),
    ).asReversed()
    private val report = DayReport(rows.asReversed(), emptyList(), time(14, 8), true, false, false)

    @Test fun `timeline initially focuses first row and details return to the opened row`() {
        val raw = listOf(
            RawEvent(time = time(14, 12), source = Source.USAGE, type = "ACTIVITY_RESUMED (1)", packageName = "Netflix", activity = "MainActivity"),
            RawEvent(time = time(14, 18), source = Source.MEDIA, type = "SESSION_ADDED", packageName = "Netflix", state = "STATE_PLAYING"),
            RawEvent(time = time(15, 33), source = Source.MEDIA, type = "PLAYBACK_STATE_CHANGED", packageName = "Netflix", state = "STATE_PAUSED"))
        compose.setContent {
            SystemverktoyTheme {
                TimelineNavigation(day, day, time(17, 52), zone, report.copy(raw = raw), { it }, {}, rememberTimelineNavigation(day).also { it.overview = false }) { }
            }
        }
        compose.onNodeWithTag("period-${rows.first().start}").assertIsFocused()
        screenshot("today")
        compose.onNodeWithTag("period-${rows.first().start}").performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag("period-${rows[1].start}").assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithTag("detail-back").assertIsFocused()
        compose.onNodeWithText("Grunnlag – rå signaler").assertExists()
        screenshot("details")
        compose.onNodeWithTag("detail-back").performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag("session-history").assertIsFocused()
        compose.onNodeWithTag("session-history").performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithTag("session-signals").assertIsFocused()
        compose.onNodeWithTag("session-signals").performKeyInput { pressKey(Key.DirectionUp) }
        compose.onNodeWithTag("detail-back").assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithTag("period-${rows[1].start}").assertIsFocused()
    }

    @Test fun `details and raw log preserve a scrolled viewport and the opened row`() {
        val many = (0..30).map { i -> TimelinePeriod(time(10, 0) + i * 180_000L,
            time(10, 0) + (i + 1) * 180_000L, Category.APP_OPEN, "App $i") }.asReversed()
        lateinit var navigation: TimelineNavigationState
        compose.setContent { SystemverktoyTheme {
            val state = rememberTimelineNavigation(day)
            LaunchedEffect(Unit) { state.overview = false }
            SideEffect { navigation = state }
            TimelineNavigation(day, day, time(17, 52), zone, report.copy(periods = many.asReversed()), { it }, {}, state) {
                androidx.tv.material3.Text("Råloggtest")
            }
        } }
        for (i in 1..25) compose.onNodeWithTag("period-${many[i - 1].start}").performKeyInput { pressKey(Key.DirectionDown) }
        val target = compose.onNodeWithTag("period-${many[25].start}").assertIsFocused()
        var position = 0 to 0
        compose.runOnIdle { position = navigation.list.firstVisibleItemIndex to navigation.list.firstVisibleItemScrollOffset }
        assertTrue(position.first > 0)
        target.performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithTag("detail-back").assertIsFocused()
        back()
        target.assertIsFocused().assertIsDisplayed()
        compose.runOnIdle { assertEquals(position, navigation.list.firstVisibleItemIndex to navigation.list.firstVisibleItemScrollOffset) }
        compose.runOnIdle { navigation.diagnostics = true }
        compose.onNodeWithText("Råloggtest").assertExists()
        back()
        target.assertIsFocused().assertIsDisplayed()
        compose.runOnIdle { assertEquals(position, navigation.list.firstVisibleItemIndex to navigation.list.firstVisibleItemScrollOffset) }
    }

    @Test fun `vanished detail is cleared so one back press reaches the parent`() {
        var data by mutableStateOf(report)
        var exits = 0
        lateinit var navigation: TimelineNavigationState
        compose.setContent { SystemverktoyTheme {
            BackHandler { exits++ }
            val state = rememberTimelineNavigation(day)
            LaunchedEffect(Unit) { state.overview = false }
            SideEffect { navigation = state }
            TimelineNavigation(day, day, time(17, 52), zone, data, { it }, {}, state) { }
        } }
        compose.onNodeWithTag("period-${rows.first().start}").performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithTag("detail-back").assertExists()
        compose.runOnIdle { data = report.copy(periods = emptyList()) }
        compose.onNodeWithTag("day-heading").assertIsFocused()
        compose.runOnIdle { assertNull(navigation.detail) }
        back()
        compose.onNodeWithText("↓ Alle detaljer").assertExists()
        compose.runOnIdle { assertEquals(0, exits) }
        back()
        compose.runOnIdle { assertEquals(1, exits) }
    }

    @Test fun `historical replay waits for data changes and today replays every thirty seconds`() {
        val today = day.plusDays(1)
        var selected by mutableStateOf(day)
        var now by mutableLongStateOf(today.atTime(12, 0).atZone(zone).toInstant().toEpochMilli())
        var batch by mutableStateOf(HistoryBatch(emptyList(), time(10, 0)))
        val calls = AtomicInteger()
        val observedTimes = CopyOnWriteArrayList<Long>()
        compose.setContent {
            val result = rememberDayReport(batch, selected, today, zone, now, "own") { _, _, _, observedNow, _ ->
                observedTimes += observedNow
                calls.incrementAndGet()
                report
            }
            androidx.tv.material3.Text(if (result.value == null) "Laster" else "Beregnet")
        }
        fun awaitCalls(expected: Int) {
            compose.waitForIdle()
            compose.waitUntil(5000) { calls.get() >= expected }
            compose.runOnIdle { assertEquals(expected, calls.get()) }
        }
        awaitCalls(1)
        compose.onNodeWithText("Beregnet").assertExists()
        repeat(10) { compose.runOnIdle { now += 1000 } }
        compose.runOnIdle { assertEquals(1, calls.get()) }
        compose.runOnIdle { batch = batch.copy(events = listOf(RawEvent(time = now, source = Source.APP, type = "heartbeat"))) }
        awaitCalls(2)
        compose.runOnIdle { assertEquals(now, observedTimes.last()) }
        compose.runOnIdle { selected = today }
        awaitCalls(3)
        compose.runOnIdle { now += 5000 }
        compose.runOnIdle { assertEquals(3, calls.get()) }
        compose.runOnIdle { now += 15_000 }
        awaitCalls(4)
    }

    @Test fun `day keys work and today advances at midnight`() {
        var today by mutableStateOf(day)
        var selected by mutableStateOf<String?>(null)
        compose.setContent { SystemverktoyTheme {
            DayScreen(selectedDay(selected, today), today, time(17, 52), zone, report, { it },
                { selected = shiftedDay(selected, today, it) }, {}, {})
        } }
        compose.onNodeWithTag("period-${rows.first().start}").performKeyInput { pressKey(Key.DirectionLeft) }
        compose.onNodeWithText("I går").assertExists()
        compose.onNodeWithTag("period-${rows.first().start}").performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithText("I dag").assertExists()
        compose.runOnIdle { today = day.plusDays(1) }
        compose.onNodeWithText("torsdag 12. november", substring = true).assertExists()
    }

    @Test fun `diagnostics requires an uninterrupted three second OK hold on heading`() {
        var opened = 0
        compose.setContent { SystemverktoyTheme {
            DayScreen(day, day, time(17, 52), zone, report.copy(periods = emptyList()), { it }, {}, {}, { opened++ })
        } }
        val heading = compose.onNodeWithTag("day-heading")
        heading.assertIsFocused()
        heading.performKeyInput { keyDown(Key.DirectionCenter) }
        compose.mainClock.advanceTimeBy(1500)
        heading.performKeyInput { keyUp(Key.DirectionCenter) }
        compose.mainClock.advanceTimeBy(2000)
        compose.runOnIdle { assertEquals(0, opened) }
        heading.performKeyInput { keyDown(Key.DirectionCenter) }
        compose.mainClock.advanceTimeBy(3100)
        compose.runOnIdle { assertEquals(1, opened) }
        heading.performKeyInput { keyUp(Key.DirectionCenter) }
    }

    @Test fun `historical gaps have visible warning and remain in timeline`() {
        val gap = TimelinePeriod(time(15, 50), time(16, 30), Category.UNKNOWN)
        compose.setContent { SystemverktoyTheme {
            DayScreen(day, day.plusDays(1), time(17, 52), zone, report.copy(periods = listOf(gap), monitoringStart = time(14, 0)), { it }, {}, {}, {})
        } }
        compose.onNodeWithText("Ufullstendig:", substring = true).assertExists()
        compose.onNodeWithText("Ukjent – mangler logg").assertExists()
        compose.onNodeWithTag("period-${gap.start}").assertIsFocused()
        screenshot("history-gap")
    }

    @Test fun `details explain playback unknown before the first listener`() {
        val historical = rows.first().copy(category = Category.APP_OPEN, playbackUnknown = true)
        compose.setContent { SystemverktoyTheme {
            SessionScreen(listOf(historical), emptyList(), zone, { it }, firstListener = time(17, 15)) { }
        } }
        compose.onNodeWithText("Appen fulgte ikke med på avspilling før 11. november 2026 kl. 17:15.").assertExists()
        compose.onAllNodesWithText("App åpen – avspilling ukjent", substring = true).assertCountEquals(2)
    }

    @Test fun `setup focuses first missing permission and keeps high contrast`() {
        var access by mutableStateOf(Access(false, false))
        compose.setContent { SystemverktoyTheme { SetupScreen(access) } }
        compose.onAllNodesWithText("Åpne innstillinger")[0].assertIsFocused()
        screenshot("setup")
        compose.runOnIdle { access = Access(true, false) }
        compose.onNodeWithText("Åpne innstillinger").assertIsFocused()
    }

    @Test
    @Config(qualifiers = "w1280dp-h720dp-hdpi")
    fun `1080p with larger text scrolls all rows using only the remote`() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.3f)) {
                SystemverktoyTheme { DayScreen(day, day, time(17, 52), zone, report, { it }, {}, {}, {}) }
            }
        }
        compose.onNodeWithTag("period-${rows.first().start}").assertIsFocused()
        for (index in 1..rows.lastIndex) {
            compose.onNodeWithTag("period-${rows[index - 1].start}").performKeyInput { pressKey(Key.DirectionDown) }
            compose.onNodeWithTag("period-${rows[index].start}").assertIsFocused().assertIsDisplayed()
        }
        screenshot("today-1080-large-text")
    }

    @Test
    @Config(qualifiers = "w960dp-h540dp-mdpi")
    fun `compact screen shows twelve complete rows at 960 by 540 dp`() {
        val many = (0..20).map { i -> TimelinePeriod(time(10, 0) + i * 180_000L,
            time(10, 0) + (i + 1) * 180_000L, Category.APP_OPEN, "App $i") }
        compose.setContent { SystemverktoyTheme {
            DayScreen(day, day, time(17, 52), zone, report.copy(periods = many, monitoringStart = time(9, 0)), { it }, {}, {}, {})
        } }
        many.asReversed().take(12).forEach { row ->
            compose.onNodeWithTag("period-${row.start}").assertIsDisplayed()
            val rect = compose.onNodeWithTag("period-${row.start}").fetchSemanticsNode().boundsInRoot
            assertTrue("Complete row inside screen: $rect", rect.top >= 0 && rect.bottom <= 540)
        }
        compose.onNodeWithTag("period-${many.last().start}").assertIsFocused()
        screenshot("compact-960")
    }

    private fun screenshot(name: String) {
        val file = File("../work/ui-$name.png")
        file.parentFile?.mkdirs()
        compose.runOnIdle {
            val view = (compose as AndroidComposeTestRule<*, *>).activity.window.decorView
            val bitmap = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap))
            file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }

    private fun back() = compose.runOnIdle {
        (compose as AndroidComposeTestRule<*, *>).activity.onBackPressedDispatcher.onBackPressed()
    }
}
