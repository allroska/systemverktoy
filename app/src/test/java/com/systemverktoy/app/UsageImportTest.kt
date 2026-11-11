package com.systemverktoy.app

import android.app.AppOpsManager
import android.app.Application
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import androidx.core.content.edit
import androidx.room.Room
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowUsageStatsManager.EventBuilder
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class)
class UsageImportTest {
    @Test fun `checkpoint survives repository restart and delayed events are imported once`() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val preferences = context.getSharedPreferences("diagnostics", Context.MODE_PRIVATE)
        preferences.edit(commit = true) { clear() }
        shadowOf(context.getSystemService(AppOpsManager::class.java)).setMode(
            AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName, AppOpsManager.MODE_ALLOWED)
        val usage = shadowOf(context.getSystemService(UsageStatsManager::class.java))
        val now = System.currentTimeMillis()
        fun add(time: Long, activity: String) {
            usage.addEvent(EventBuilder.buildEvent().setPackage("example.player").setClass(activity)
                .setTimeStamp(time).setEventType(1).build())
        }
        val oldest = now - Duration.ofDays(3).toMillis()
        add(oldest, "Menu")
        add(now - 60_000, "Player")
        val database = Room.inMemoryDatabaseBuilder(context, EventDatabase::class.java).build()
        val repositories = mutableListOf<EventRepository>()
        fun repository() = EventRepository(context, database).also { repositories += it }
        try {
            val first = repository()
            first.copyUsage()
            assertEquals(2L, database.events().count().first())
            assertEquals(oldest, first.oldestUsage.value)
            val copiedThrough = preferences.getLong("usageCopiedThrough", 0)
            assertTrue(copiedThrough > 0)
            // Appears late, before the saved checkpoint, but inside the overlap.
            add(now - 600_000, "Delayed")
            // Outside the incremental window: must wait for the daily full scan.
            val older = now - Duration.ofDays(4).toMillis()
            add(older, "Older")
            val restarted = repository()
            restarted.copyUsage()
            restarted.copyUsage()
            assertEquals(3L, database.events().count().first())
            assertEquals(oldest, restarted.oldestUsage.value)
            preferences.edit(commit = true) { putLong("usageFullScan", now - Duration.ofDays(1).toMillis()) }
            restarted.copyUsage()
            assertEquals(4L, database.events().count().first())
            assertEquals(older, restarted.oldestUsage.value)
            assertTrue(preferences.getLong("usageCopiedThrough", 0) >= copiedThrough)
        } finally {
            repositories.forEach { it.scope.cancel() }
            database.close()
        }
    }

    @Test fun `missing usage permission does not advance checkpoint`() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val preferences = context.getSharedPreferences("diagnostics", Context.MODE_PRIVATE)
        preferences.edit(commit = true) { clear(); putLong("usageCopiedThrough", 123) }
        shadowOf(context.getSystemService(AppOpsManager::class.java)).setMode(
            AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName, AppOpsManager.MODE_IGNORED)
        val database = Room.inMemoryDatabaseBuilder(context, EventDatabase::class.java).build()
        val repository = EventRepository(context, database)
        try {
            repository.copyUsage()
            assertEquals(123L, preferences.getLong("usageCopiedThrough", 0))
            assertEquals(0L, database.events().count().first())
        } finally {
            repository.scope.cancel()
            database.close()
        }
    }
}
