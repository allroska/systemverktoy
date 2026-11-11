package com.systemverktoy.app

import org.junit.Assert.*
import org.junit.Test
import java.time.ZoneId

class RawLogPresentationTest {
    private val launcher = "example.launcher"
    private val own = "com.systemverktoy.app"

    @Test fun importantFilterHidesAppNoiseButKeepsGlobalSignals() {
        for (pkg in listOf(launcher, own)) {
            for (source in listOf(Source.USAGE, Source.MEDIA, Source.APP)) {
                assertFalse(isImportantEvent(RawEvent(source = source, type = "noise", packageName = pkg), launcher, own))
            }
        }
        for (source in listOf(Source.MEDIA, Source.DREAM, Source.AUDIO, Source.LISTENER, Source.USAGE)) {
            assertTrue(isImportantEvent(RawEvent(source = source, type = "signal", packageName = "example.player"), launcher, own))
        }
        for (source in listOf(Source.DREAM, Source.AUDIO, Source.LISTENER)) {
            for (pkg in listOf(null, own)) {
                assertTrue(isImportantEvent(RawEvent(source = source, type = "signal", packageName = pkg), launcher, own))
            }
        }
        for (type in listOf("SCREEN_ON", "SCREEN_OFF", "SCREEN_INTERACTIVE (15)", "SCREEN_NON_INTERACTIVE (16)")) {
            assertTrue(isImportantEvent(RawEvent(source = Source.APP, type = type, packageName = own), launcher, own))
        }
        assertFalse(isImportantEvent(RawEvent(source = Source.APP, type = "APP_OPENED", packageName = own), launcher, own))
        assertTrue(isImportantEvent(RawEvent(source = Source.USAGE, type = "ACTIVITY_RESUMED", packageName = "example.player"), null, own))
    }

    @Test fun importantFilterKeepsAppDiagnosticsWithAndWithoutOwnPackage() {
        for (type in listOf("BOOT_COMPLETED", "IMPORT_FAILED", "USAGE_ACCESS_REVOKED", "heartbeat")) {
            for (pkg in listOf(null, own)) {
                assertTrue("$type ($pkg)", isImportantEvent(
                    RawEvent(source = Source.APP, type = type, packageName = pkg), launcher, own))
            }
        }
        assertFalse(isImportantEvent(RawEvent(source = Source.APP, type = "APP_OPENED"), launcher, own))
    }

    @Test fun compactLineKeepsFieldsAndUsesLocalTimeWithoutEmbeddedNewlines() {
        val event = RawEvent(time = 0, source = Source.MEDIA, type = "SESSION_ADDED",
            packageName = "example.player", activity = "example.player.PlaybackActivity",
            state = "STATE_PLAYING", title = "En\nny\ttittel")
        assertEquals("01:00:00 · MEDIA · SESSION_ADDED · example.player · PlaybackActivity / STATE_PLAYING / En ny tittel",
            formatRawEvent(event, ZoneId.of("Europe/Oslo")))
    }
}
