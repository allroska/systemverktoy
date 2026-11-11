package com.systemverktoy.app

import android.app.Application
import android.content.ContentProvider
import android.content.ContentUris
import android.content.ContentValues
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.MediaStore.MediaColumns
import androidx.room.Room
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver
import java.io.File
import java.io.FileNotFoundException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class)
class DataExportTest {
    @Test fun `export creates three owned downloads overwrites without duplicates and reports failure`() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val provider = DownloadsProvider(context.cacheDir)
        provider.attachInfo(context, android.content.pm.ProviderInfo().apply { authority = "media" })
        ShadowContentResolver.registerProviderInternal("media", provider)
        val db = Room.inMemoryDatabaseBuilder(context, EventDatabase::class.java).build()
        val repo = EventRepository(context, db)
        try {
            val now = System.currentTimeMillis()
            repo.dao.insert(listOf(
                RawEvent(time = now - 60_000, source = Source.USAGE, type = "ACTIVITY_RESUMED", packageName = "example.player"),
                RawEvent(time = now - 20 * 86_400_000L, source = Source.APP, type = "TOO_OLD")))
            val exported = DataExport.run(context, repo)
            if (exported == null) {
                val failed = withTimeout(5000) { repo.dao.day(0, Long.MAX_VALUE).first { events -> events.any { it.type == "EXPORT_FAILED" } } }
                fail("Export failed: ${failed.first { it.type == "EXPORT_FAILED" }.state}")
            }
            assertEquals(3, provider.rows.size)
            assertEquals(setOf("systemverktoy-raa.csv", "systemverktoy-tidslinje.csv", "systemverktoy-diagnose.txt"),
                provider.rows.values.map { it.getAsString(MediaColumns.DISPLAY_NAME) }.toSet())
            assertTrue(provider.rows.values.all { it.getAsString(MediaColumns.RELATIVE_PATH) == "Download/Systemverktoy/" &&
                it.getAsInteger(MediaColumns.IS_PENDING) == 0 })
            val raw = provider.text("systemverktoy-raa.csv")
            assertTrue(raw.contains("example.player"))
            assertFalse(raw.contains("TOO_OLD"))
            assertTrue(provider.text("systemverktoy-tidslinje.csv").contains("APP_OPEN"))
            assertTrue(provider.text("systemverktoy-diagnose.txt").contains("Eksporttidspunkt:"))
            provider.files.values.forEach { it.appendText("STALE_TRAILER") }
            assertNotNull(DataExport.run(context, repo))
            assertEquals(3, provider.rows.size)
            assertFalse(provider.text("systemverktoy-raa.csv").contains("STALE_TRAILER"))
            provider.fail = true
            assertNull(DataExport.run(context, repo))
            withTimeout(5000) { repo.dao.day(0, Long.MAX_VALUE).first { events -> events.any { it.type == "EXPORT_FAILED" } } }
        } finally { repo.scope.cancel(); db.close() }
        Unit
    }
}

/** Local provider exercises the resolver contract; Android's actual storage remains a TV check. */
private class DownloadsProvider(private val directory: File) : ContentProvider() {
    val rows = linkedMapOf<Long, ContentValues>()
    val files = linkedMapOf<Long, File>()
    var fail = false
    override fun onCreate() = true
    override fun getType(uri: Uri) = "text/plain"
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?) =
        MatrixCursor(arrayOf(MediaColumns._ID)).apply {
            rows.filterValues { it.getAsString(MediaColumns.DISPLAY_NAME) == selectionArgs?.get(0) &&
                it.getAsString(MediaColumns.RELATIVE_PATH) == selectionArgs?.get(1) }.keys.forEach { addRow(arrayOf(it)) }
        }
    override fun insert(uri: Uri, values: ContentValues?): Uri {
        val id = rows.size + 1L
        rows[id] = ContentValues(values).apply { put(MediaColumns.OWNER_PACKAGE_NAME, "com.systemverktoy.app") }
        files[id] = File(directory, "export-$id")
        return ContentUris.withAppendedId(uri, id)
    }
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int {
        rows.getValue(ContentUris.parseId(uri)).putAll(values)
        return 1
    }
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        rows.remove(ContentUris.parseId(uri)); return 1
    }
    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (fail) throw FileNotFoundException("Test storage failure")
        return ParcelFileDescriptor.open(files.getValue(ContentUris.parseId(uri)), ParcelFileDescriptor.parseMode(mode))
    }
    fun text(name: String) = files.getValue(rows.entries.single { it.value.getAsString(MediaColumns.DISPLAY_NAME) == name }.key).readText()
}
