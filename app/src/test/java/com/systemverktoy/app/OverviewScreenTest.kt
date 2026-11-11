package com.systemverktoy.app

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.systemverktoy.app.ui.theme.SystemverktoyTheme
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
class OverviewScreenTest {
    @get:Rule val compose = createComposeRule()
    @Test fun `overview opens whole session then down opens timeline and back restores overview`() {
        val day = LocalDate.parse("2026-11-11")
        val zone = ZoneId.of("Europe/Oslo")
        val start = day.atTime(14, 0).atZone(zone).toInstant().toEpochMilli()
        val rows = listOf(TimelinePeriod(start, start + 120_000, Category.WATCHING, "Player"),
            TimelinePeriod(start + 120_000, start + 180_000, Category.APP_OPEN, "Player"),
            TimelinePeriod(start + 180_000, start + 300_000, Category.WATCHING, "Player"))
        lateinit var navigation: TimelineNavigationState
        compose.setContent { SystemverktoyTheme {
            val state = rememberTimelineNavigation(day)
            androidx.compose.runtime.SideEffect { navigation = state }
            TimelineNavigation(day, day, start + 300_000, zone,
                DayReport(rows, emptyList(), start, true, false, false), { it }, {}, state) { }
        } }
        compose.onNodeWithText("Player").assertIsDisplayed().assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithText("Hva skjedde").assertExists()
        compose.onNodeWithTag("detail-back").assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle { org.junit.Assert.assertNull(navigation.detail) }
        compose.onNodeWithText("Player").assertIsFocused().performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag("period-${rows.last().start}").assertIsFocused()
    }
}
