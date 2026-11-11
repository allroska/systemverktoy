package com.systemverktoy.app

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.PowerManager
import android.util.Log
import androidx.room.Room
import androidx.room.withTransaction
import androidx.core.content.edit
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class EventRepository(
    private val context: Context,
    private val database: EventDatabase = Room.databaseBuilder(context, EventDatabase::class.java, "raw-events.db").build(),
) {
    val dao = database.events()
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, error ->
        Log.e("Systemverktoy", "Could not save raw event", error)
    })
    val connected = MutableStateFlow(false)
    private val preferences = context.getSharedPreferences("diagnostics", Context.MODE_PRIVATE)
    val oldestUsage = MutableStateFlow(preferences.getLong("oldestUsage", 0).takeIf { it != 0L })
    private val importLock = Mutex()
    private val heartbeatLock = Mutex()

    fun log(source: Source, type: String, pkg: String? = null, state: String? = null, title: String? = null) {
        val event = RawEvent(source = source, type = type, packageName = pkg, state = state, title = title)
        scope.launch { dao.insert(listOf(event)) }
    }

    suspend fun heartbeat() = heartbeatLock.withLock {
        if (!context.getSystemService(PowerManager::class.java).isInteractive) return@withLock
        val now = System.currentTimeMillis()
        val last = dao.lastHeartbeat()
        if (heartbeatDue(last, now)) {
            dao.insert(listOf(RawEvent(time = now, source = Source.APP, type = "heartbeat")))
        }
    }

    // Persist the successful query end, with overlap for delayed Android events.
    // Advance only after the transaction commits; replay after a crash is harmless.
    suspend fun copyUsage() = importLock.withLock {
        if (!Access.read(context).usage) return@withLock
        val window = usageWindow(System.currentTimeMillis(),
            preferences.getLong("usageCopiedThrough", 0).takeIf { it > 0 },
            preferences.getLong("usageFullScan", 0).takeIf { it > 0 })
        val events = try {
            context.getSystemService(UsageStatsManager::class.java)
                .queryEvents(window.start, window.end)
        } catch (_: SecurityException) {
            log(Source.APP, "USAGE_ACCESS_REVOKED")
            return@withLock
        } ?: return@withLock
        var oldest: Long? = null
        val batch = ArrayList<RawEvent>(500)
        database.withTransaction {
            val event = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                oldest = oldest?.let { minOf(it, event.timeStamp) } ?: event.timeStamp
                batch += RawEvent(
                    time = event.timeStamp, source = Source.USAGE,
                    type = usageType(event.eventType), packageName = event.packageName,
                    activity = event.className,
                    usageKey = usageKey(event.timeStamp, event.eventType, event.packageName, event.className),
                )
                if (batch.size == 500) { dao.insert(batch); batch.clear() }
            }
            dao.insert(batch)
        }
        if (window.full) oldestUsage.value = oldest
        preferences.edit(commit = true) {
            putLong("usageCopiedThrough", window.end)
            if (window.full) {
                putLong("usageFullScan", window.end)
                putLong("oldestUsage", oldest ?: 0)
            }
        }
    }
}

private fun usageType(type: Int): String = when (type) {
    1 -> "ACTIVITY_RESUMED"
    2 -> "ACTIVITY_PAUSED"
    15 -> "SCREEN_INTERACTIVE"
    16 -> "SCREEN_NON_INTERACTIVE"
    23 -> "ACTIVITY_STOPPED"
    26 -> "DEVICE_SHUTDOWN"
    27 -> "DEVICE_STARTUP"
    else -> "EVENT_$type"
} + " ($type)"
