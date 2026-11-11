package com.systemverktoy.app

import android.app.Application
import androidx.room.Room
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class)
class EventDatabaseTest {
    @Test fun `database ignores reimports even after reopening and preserves other activities`() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val name = "dedup-test.db"
        context.deleteDatabase(name)
        fun open() = Room.databaseBuilder(context, EventDatabase::class.java, name).build()
        val menu = RawEvent(time = 1000, source = Source.USAGE, type = "ACTIVITY_RESUMED",
            packageName = "example.player", activity = "Menu", usageKey = usageKey(1000, 1, "example.player", "Menu"))
        open().withClose { it.events().insert(listOf(menu, menu)) }
        open().withClose { db ->
            val player = menu.copy(activity = "Player", usageKey = usageKey(1000, 1, "example.player", "Player"))
            val late = menu.copy(time = 900, usageKey = usageKey(900, 1, "example.player", "Menu"))
            db.events().insert(listOf(menu, player, late, menu))
            assertEquals(3L, db.events().count().first())
            assertEquals(listOf(1000L, 1000L, 900L), db.events().day(0, 2000).first().map { it.time })
            val app = RawEvent(time = 1000, source = Source.APP, type = "APP_OPENED")
            db.events().insert(listOf(app, app))
            assertEquals(5L, db.events().count().first())
        }
        context.deleteDatabase(name)
        Unit
    }

    @Test fun `day query excludes next midnight on both DST transition days`() = runBlocking {
        Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), EventDatabase::class.java).build().withClose { db ->
            for (date in listOf("2026-03-29", "2026-10-25")) {
                val bounds = dayBounds(LocalDate.parse(date), ZoneId.of("Europe/Oslo"))
                db.events().insert(listOf(bounds.start - 1, bounds.start, bounds.endExclusive - 1, bounds.endExclusive)
                    .map { RawEvent(time = it, source = Source.APP, type = "boundary") })
                assertEquals(listOf(bounds.endExclusive - 1, bounds.start),
                    db.events().day(bounds.start, bounds.endExclusive).first().map { it.time })
            }
        }
    }
}

private suspend fun <T> EventDatabase.withClose(block: suspend (EventDatabase) -> T): T =
    try { block(this) } finally { close() }
