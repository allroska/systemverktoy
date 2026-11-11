package com.systemverktoy.app

import android.app.Application
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class)
class PinStoreTest {
    @Test fun `reset receiver requires shell permission`() {
        val context = RuntimeEnvironment.getApplication()
        val receiver = context.packageManager.getReceiverInfo(android.content.ComponentName(context, PinResetReceiver::class.java), 0)
        assertTrue(receiver.exported)
        assertEquals("android.permission.DUMP", receiver.permission)
    }
    @Test fun `salted PIN persists blocks after five failures and resets independently`() {
        val context = RuntimeEnvironment.getApplication()
        val store = PinStore(context)
        store.reset()
        assertFalse(store.configured)
        store.create("1234")
        val prefs = context.getSharedPreferences("pin", 0)
        val firstHash = prefs.getString("hash", null)
        assertFalse(prefs.all.values.any { it == "1234" })
        assertTrue(PinStore(context).verify("1234", 1000))
        repeat(5) { assertFalse(store.verify("9999", 1000)) }
        assertEquals(61_000L, store.blockedUntil)
        assertFalse(PinStore(context).verify("1234", 60_999))
        assertTrue(store.verify("1234", 61_000))
        store.reset()
        assertFalse(store.configured)
        store.create("1234")
        assertNotEquals(firstHash, prefs.getString("hash", null))
        store.reset()
    }
}
