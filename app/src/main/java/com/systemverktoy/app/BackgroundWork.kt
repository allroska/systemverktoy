package com.systemverktoy.app

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.*
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class SystemverktoyApplication : Application() {
    val repository by lazy { EventRepository(this) }
    override fun onCreate() {
        super.onCreate()
        CollectorWorker.schedule(this)
    }
}

class CollectorWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = try {
        applicationContext.repository.copyUsage()
        applicationContext.repository.heartbeat()
        Result.success()
    } catch (cancelled: kotlinx.coroutines.CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        applicationContext.repository.log(Source.APP, "IMPORT_FAILED", state = error.javaClass.simpleName)
        Result.retry()
    }

    companion object {
        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "usage-periodic", ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<CollectorWorker>(15, TimeUnit.MINUTES).build(),
            )
        }
        fun now(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                "usage-now", ExistingWorkPolicy.APPEND_OR_REPLACE,
                OneTimeWorkRequestBuilder<CollectorWorker>().build(),
            )
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        context.repository.scope.launch {
            try {
                context.repository.dao.insert(listOf(RawEvent(source = Source.APP, type = "BOOT_COMPLETED")))
                CollectorWorker.schedule(context)
                CollectorWorker.now(context)
                requestListener(context)
            } finally { pending.finish() }
        }
    }
}
