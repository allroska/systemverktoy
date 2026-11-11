package com.systemverktoy.app

import org.junit.Assert.*
import org.junit.Test
import java.time.Duration

class CollectionPolicyTest {
    private val day = Duration.ofDays(1).toMillis()
    private val now = day * 30

    @Test fun `heartbeat accepts a pulse a few milliseconds early`() {
        assertTrue(heartbeatDue(now, now + HEARTBEAT_INTERVAL_MILLIS - 2))
        assertTrue(heartbeatDue(now, now + Duration.ofMinutes(14).toMillis()))
        assertFalse(heartbeatDue(now, now + 1000))
        assertTrue(heartbeatDue(null, now))
        assertTrue(heartbeatDue(now + 1, now))
    }

    @Test fun `two heartbeat sources do not suppress the following regular pulse`() {
        val attempts = listOf(now, now + 50, now + HEARTBEAT_INTERVAL_MILLIS - 2,
            now + HEARTBEAT_INTERVAL_MILLIS, now + HEARTBEAT_INTERVAL_MILLIS * 2)
        val saved = mutableListOf<Long>()
        for (time in attempts) if (heartbeatDue(saved.lastOrNull(), time)) saved += time
        assertEquals(listOf(attempts[0], attempts[2], attempts[4]), saved)
    }

    @Test fun `unchanged media is ignored but pause resume and title changes survive`() {
        val changes = PlaybackChanges<String>()
        val playing = MediaState(3, "Example title")
        assertTrue(changes.mediaChanged("session", playing))
        assertFalse(changes.mediaChanged("session", playing))
        assertTrue(changes.mediaChanged("session", playing.copy(status = 2)))
        assertTrue(changes.mediaChanged("session", playing))
        assertTrue(changes.mediaChanged("session", playing.copy(title = "Next title")))
        assertTrue(changes.mediaChanged("session", playing.copy(title = null)))
    }

    @Test fun `sessions are independent and reconnect records initial state again`() {
        val changes = PlaybackChanges<String>()
        val state = MediaState(null, null)
        assertTrue(changes.mediaChanged("first-token", state))
        assertTrue(changes.mediaChanged("second-token", state))
        assertFalse(changes.mediaChanged("first-token", state))
        changes.remove("first-token")
        assertTrue(changes.mediaChanged("first-token", state))
        assertFalse(changes.mediaChanged("second-token", state))
        changes.clear()
        assertTrue(changes.mediaChanged("second-token", state))
    }

    @Test fun `audio only records changes including returning to silence`() {
        val changes = PlaybackChanges<String>()
        assertTrue(changes.audioChanged("media_active=0"))
        assertFalse(changes.audioChanged("media_active=0"))
        assertTrue(changes.audioChanged("media_active=1"))
        assertFalse(changes.audioChanged("media_active=1"))
        assertTrue(changes.audioChanged("media_active=0"))
        changes.clear()
        assertTrue(changes.audioChanged("media_active=0"))
    }

    @Test fun `initial import reads retention and later imports resume with overlap`() {
        assertEquals(UsageWindow(now - day * 14, now, true), usageWindow(now, null, null))
        val next = now + Duration.ofMinutes(15).toMillis()
        assertEquals(UsageWindow(now - day, next, false), usageWindow(next, now, now))
    }

    @Test fun `daily full import refreshes oldest Android event and clock rollback is safe`() {
        assertTrue(usageWindow(now + day, now + day - 1000, now).full)
        assertTrue(usageWindow(now - 1, now, now).full)
        assertTrue(usageWindow(now, now - 1000, null).full)
        val longAbsence = usageWindow(now + day * 20, now, now)
        assertEquals(now + day * 6, longAbsence.start)
    }
}
