package com.systemverktoy.app

import androidx.compose.runtime.*
import androidx.compose.ui.input.key.Key
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
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp-mdpi", application = android.app.Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalTestApi::class)
class WeekScreenTest {
    @get:Rule val compose = createComposeRule()
    private val monday = LocalDate.parse("2026-11-09")
    private val today = monday.plusDays(4)
    private val zone = ZoneId.of("Europe/Oslo")
    private val now = today.atTime(21, 0).atZone(zone).toInstant().toEpochMilli()
    private fun back() = compose.runOnIdle {
        ((compose as AndroidComposeTestRule<*, *>).activity as androidx.activity.OnBackPressedDispatcherOwner)
            .onBackPressedDispatcher.onBackPressed()
    }
    @Test fun `seven days fit and D-pad opens other apps and selected day`() {
        val summary = WeekSummary(monday, (0L..6L).map { d -> WeekDay(monday.plusDays(d), if (d > 4) 0 else 5,
            List(24) { HourCell.EMPTY }, d > 4) }, 5, 10,
            (1..5).map { WeekApp("player$it", 5, 20) }, 0, true)
        var opened: LocalDate? = null
        var moved = 0L
        var closed = false
        compose.setContent { SystemverktoyTheme {
            WeekScreen(summary, today, today, { it }, { moved += it }, { opened = it }, { closed = true })
        } }
        compose.onNodeWithText("Uke 46 · mandag 9. – søndag 15. november").assertExists()
        summary.days.forEach { compose.onNodeWithTag("week-day-${it.day}").assertIsDisplayed() }
        compose.onNodeWithTag("week-day-$today").assertIsFocused().performKeyInput { repeat(4) { pressKey(Key.DirectionUp) } }
        compose.onNodeWithTag("week-day-$monday").assertIsFocused().performKeyInput { pressKey(Key.DirectionUp) }
        compose.onNodeWithText("Andre (2 apper) ›").assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithText("Alle ukens apper · Tilbake lukker listen").assertIsFocused()
        (1..5).forEach { compose.onNodeWithText("player$it").assertIsDisplayed() }
        back()
        compose.onNodeWithTag("week-day-$monday").assertIsFocused().performKeyInput { pressKey(Key.DirectionDown); pressKey(Key.DirectionCenter) }
        assertEquals(monday.plusDays(1), opened)
        compose.onNodeWithTag("week-day-${monday.plusDays(1)}").performKeyInput { pressKey(Key.DirectionLeft) }
        assertEquals(-1L, moved)
        compose.onNodeWithTag("week-day-${monday.plusDays(1)}").performKeyInput { pressKey(Key.DirectionRight) }
        assertEquals(0L, moved)
        compose.onNodeWithTag("week-day-${monday.plusDays(1)}").performKeyInput { repeat(5) { pressKey(Key.DirectionDown) }; pressKey(Key.DirectionCenter) }
        assertEquals(monday.plusDays(1), opened)
        back()
        assertTrue(closed)
    }
    @Test fun `week heading includes both months when crossing a month boundary`() {
        val start = LocalDate.parse("2026-11-30")
        val summary = WeekSummary(start, (0L..6L).map { WeekDay(start.plusDays(it), 0, List(24) { HourCell.EMPTY }, false) },
            0, null, emptyList(), 0, false)
        compose.setContent { SystemverktoyTheme {
            WeekScreen(summary, start, start.plusWeeks(1), { it }, {}, {}, {})
        } }
        compose.onNodeWithText("Uke 49 · mandag 30. november – søndag 6. desember").assertExists()
    }

    @Test fun `up opens week from both first session and an empty day`() {
        var empty by mutableStateOf(false)
        var opened = 0
        val row = TimelinePeriod(now - 120_000, now, Category.WATCHING, "player")
        compose.setContent { SystemverktoyTheme {
            key(empty) { TimelineNavigation(today, today, now, zone,
                DayReport(if (empty) emptyList() else listOf(row), emptyList(), null, true, false, false), { it }, {},
                onWeek = { opened++ }) { } }
        } }
        compose.onNodeWithText("player").assertIsFocused().performKeyInput { pressKey(Key.DirectionUp) }
        assertEquals(1, opened)
        compose.runOnIdle { empty = true }
        compose.onNodeWithText("↓ Alle detaljer").assertIsFocused().performKeyInput { pressKey(Key.DirectionUp) }
        assertEquals(2, opened)
    }
}
