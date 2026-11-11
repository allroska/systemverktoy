package com.systemverktoy.app

import android.app.AppOpsManager
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.os.Process
import android.service.notification.NotificationListenerService

data class Access(val usage: Boolean, val notifications: Boolean) {
    val ready get() = usage && notifications
    companion object {
        fun read(context: Context) = Access(
            runCatching {
                context.getSystemService(AppOpsManager::class.java).unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName,
                ) == AppOpsManager.MODE_ALLOWED
            }.getOrDefault(false),
            runCatching {
                context.getSystemService(NotificationManager::class.java)
                    .isNotificationListenerAccessGranted(listenerComponent(context))
            }.getOrDefault(false),
        )
    }
}

fun listenerComponent(context: Context) = ComponentName(context, SystemNotificationListenerService::class.java)

fun requestListener(context: Context) {
    if (Access.read(context).notifications) {
        runCatching { NotificationListenerService.requestRebind(listenerComponent(context)) }
            .onFailure { context.repository.log(Source.LISTENER, "REBIND_FAILED", state = it.javaClass.simpleName) }
    }
}

val Context.repository: EventRepository
    get() = (applicationContext as SystemverktoyApplication).repository
