package com.systemverktoy.app

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import com.systemverktoy.app.ui.theme.SystemverktoyTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
@OptIn(ExperimentalTestApi::class)
class PinGateTest {
    @get:Rule val compose = createComposeRule()
    @Test fun `arrows and numbers unlock but background locks the protected content again`() {
        val store = PinStore(RuntimeEnvironment.getApplication())
        store.reset()
        compose.setContent { SystemverktoyTheme { PinGate { androidx.tv.material3.Text("Beskyttet innhold") } } }
        compose.onNodeWithText("Beskyttet innhold").assertDoesNotExist()
        // 0001, entered with arrows; confirm twice.
        repeat(2) {
            compose.onRoot().performKeyInput {
                repeat(3) { pressKey(Key.DirectionRight) }
                pressKey(Key.DirectionUp)
                pressKey(Key.DirectionCenter)
            }
        }
        compose.waitUntil(10000) { compose.onAllNodesWithText("Beskyttet innhold").fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
        compose.onNodeWithText("Beskyttet innhold").assertExists()
        compose.runOnIdle {
            val activity = (compose as AndroidComposeTestRule<*, *>).activity
            val lifecycle = activity.lifecycle as androidx.lifecycle.LifecycleRegistry
            lifecycle.handleLifecycleEvent(androidx.lifecycle.Lifecycle.Event.ON_STOP)
            lifecycle.handleLifecycleEvent(androidx.lifecycle.Lifecycle.Event.ON_START)
            lifecycle.handleLifecycleEvent(androidx.lifecycle.Lifecycle.Event.ON_RESUME)
        }
        compose.onNodeWithText("Beskyttet innhold").assertDoesNotExist()
        compose.onNodeWithText("Tast PIN-kode").assertExists()
        compose.onRoot().performKeyInput {
            repeat(3) { pressKey(Key.Zero) }
            pressKey(Key.One)
            pressKey(Key.DirectionCenter)
        }
        compose.waitUntil(10000) { compose.onAllNodesWithText("Beskyttet innhold").fetchSemanticsNodes().isNotEmpty() }
        store.reset()
    }
}
