package com.systemverktoy.app

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Environment
import android.os.PowerManager
import android.provider.MediaStore
import android.provider.Settings
import androidx.work.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.Writer
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.TimeUnit

internal fun diagnosticLines(access: Access, connected: Boolean, heartbeat: Long?, battery: Boolean?,
    dream: String?, launcher: String?, oldest: Long?, count: Long, zone: ZoneId): List<String> {
    fun stamp(time: Long?) = time?.let { isoTime(it, zone) } ?: "ukjent / ingen"
    fun onOff(value: Boolean) = if (value) "på" else "av"
    return listOf(
        "Brukstilgang: ${onOff(access.usage)} · Varsler: ${onOff(access.notifications)} · Lytter: ${if (connected) "tilkoblet" else "frakoblet"}",
        "Hjerteslag: ${stamp(heartbeat)} · Unntatt batterioptimalisering: ${battery?.let { if (it) "ja" else "nei" } ?: "ukjent"}",
        "Skjermsparer: ${dream ?: "ukjent / ikke tilgjengelig"}",
        "Launcher: ${launcher ?: "ukjent"}",
        "Eldst hos Android (siste lesing): ${stamp(oldest)} · Egne rader: $count",
    )
}

internal fun launcherPackage(context: Context): String? = runCatching {
    context.packageManager.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)?.activityInfo?.packageName
}.getOrNull()
internal fun batteryExempt(context: Context): Boolean? = runCatching {
    context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)
}.getOrNull()
internal fun dreamComponent(context: Context): String? = runCatching {
    Settings.Secure.getString(context.contentResolver, "screensaver_components")
}.getOrNull()

internal object DataExport {
    private val lock = Mutex()

    suspend fun run(context: Context, repo: EventRepository = context.repository): Long? = withContext(Dispatchers.IO) {
        lock.withLock {
            try {
                repo.copyUsage()
                val now = System.currentTimeMillis()
                val zone = ZoneId.systemDefault()
                val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
                val firstDay = today.minusDays(13)
                val start = dayBounds(firstDay, zone).start
                val events = repo.dao.timelineRange(historyWindow(firstDay, zone).floor, now).first()
                val batch = HistoryBatch(events, repo.dao.oldest().first(), repo.dao.firstListener().first(),
                    repo.dao.monitoringStart().first())
                write(context, "systemverktoy-raa.csv", "text/csv") { writer ->
                    writeRawCsv(writer, events.filter { it.time >= start }, zone)
                }
                write(context, "systemverktoy-tidslinje.csv", "text/csv") { writer ->
                    writer.write(csvRow("dag", "start", "slutt", "kategori", "app", "varighet_sekunder", "grunnlag"))
                    repeat(14) { offset ->
                        val day = firstDay.plusDays(offset.toLong())
                        writeTimelineCsv(writer, dayReport(batch, day, zone, now, context.packageName), day, zone) { pkg ->
                            runCatching { context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(pkg, 0)).toString() }
                                .getOrDefault(pkg)
                        }
                    }
                }
                val diagnostics = diagnosticLines(Access.read(context), repo.connected.value, repo.dao.lastHeartbeat(),
                    batteryExempt(context), dreamComponent(context), launcherPackage(context), repo.oldestUsage.value,
                    repo.dao.count().first(), zone)
                write(context, "systemverktoy-diagnose.txt", "text/plain") { writer ->
                    writer.write((diagnostics + "Appversjon: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})" +
                        "Eksporttidspunkt: ${isoTime(now, zone)}").joinToString("\n", postfix = "\n"))
                }
                now
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                repo.log(Source.APP, "EXPORT_FAILED", state = error.javaClass.simpleName)
                null
            }
        }
    }

    /** Update our existing MediaStore entry rather than creating a numbered copy. */
    private fun write(context: Context, name: String, mime: String, content: (Writer) -> Unit) {
        val resolver = context.contentResolver
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val path = "${Environment.DIRECTORY_DOWNLOADS}/Systemverktoy/"
        val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${MediaStore.MediaColumns.RELATIVE_PATH} = ? AND ${MediaStore.MediaColumns.OWNER_PACKAGE_NAME} = ?"
        val existing = resolver.query(collection, arrayOf(MediaStore.MediaColumns._ID), android.os.Bundle().apply {
            putString(android.content.ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
            putStringArray(android.content.ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, arrayOf(name, path, context.packageName))
            putInt(MediaStore.QUERY_ARG_MATCH_PENDING, MediaStore.MATCH_INCLUDE)
        }, null)?.use { cursor ->
            if (cursor.moveToFirst()) ContentUris.withAppendedId(collection, cursor.getLong(0)) else null
        }
        val uri = existing ?: checkNotNull(resolver.insert(collection, ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.RELATIVE_PATH, path)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        })) { "Could not create export" }
        try {
            if (existing == null) {
                val actual = resolver.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)?.use {
                    if (it.moveToFirst()) it.getString(0) else null
                }
                check(actual == name) { "Export filename changed" }
            }
            checkNotNull(resolver.openOutputStream(uri, "wt")) { "Could not open export" }
                .bufferedWriter(Charsets.UTF_8).use(content)
            resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        } catch (error: Exception) {
            if (existing == null) resolver.delete(uri, null, null)
            throw error
        }
    }
}

class ExportWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = if (DataExport.run(applicationContext) != null) Result.success() else Result.retry()

    companion object {
        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("export-daily", ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<ExportWorker>(1, TimeUnit.DAYS).build())
        }
        fun now(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork("export-now", ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<ExportWorker>().build())
        }
    }
}
