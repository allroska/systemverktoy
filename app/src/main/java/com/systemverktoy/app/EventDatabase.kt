package com.systemverktoy.app

import androidx.room.*
import kotlinx.coroutines.flow.Flow

enum class Source { USAGE, MEDIA, DREAM, AUDIO, LISTENER, APP }

@Entity(tableName = "raw_events", indices = [Index(value = ["usageKey"], unique = true), Index("time")])
data class RawEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val time: Long = System.currentTimeMillis(),
    val source: Source,
    val type: String,
    val packageName: String? = null,
    val activity: String? = null,
    val state: String? = null,
    val title: String? = null,
    val usageKey: String? = null,
)

@Dao
interface EventDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(events: List<RawEvent>)

    @Query("SELECT * FROM raw_events WHERE time >= :start AND time < :end ORDER BY time DESC, id DESC")
    fun day(start: Long, end: Long): Flow<List<RawEvent>>

    @Query("SELECT * FROM raw_events WHERE time >= :start AND time <= :end ORDER BY time, id")
    fun timelineRange(start: Long, end: Long): Flow<List<RawEvent>>

    @Query("SELECT MIN(time) FROM raw_events")
    fun oldest(): Flow<Long?>

    @Query("SELECT COUNT(*) FROM raw_events")
    fun count(): Flow<Long>

    @Query("SELECT MAX(time) FROM raw_events WHERE source = 'APP' AND type = 'heartbeat'")
    fun heartbeat(): Flow<Long?>

    @Query("SELECT MAX(time) FROM raw_events WHERE source = 'APP' AND type = 'heartbeat'")
    suspend fun lastHeartbeat(): Long?
}

@Database(entities = [RawEvent::class], version = 1, exportSchema = true)
abstract class EventDatabase : RoomDatabase() {
    abstract fun events(): EventDao
}
