package com.systemverktoy.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch

class SystemNotificationListenerService : NotificationListenerService() {
    private val handler = Handler(Looper.getMainLooper())
    private val controllers = mutableMapOf<MediaSession.Token, Pair<MediaController, MediaController.Callback>>()
    private val changes = PlaybackChanges<MediaSession.Token>()
    private val sessions by lazy { getSystemService(MediaSessionManager::class.java) }
    private val audio by lazy { getSystemService(AudioManager::class.java) }
    private var receiverRegistered = false
    private var sessionsRegistered = false
    private var audioRegistered = false
    private val sessionListener = MediaSessionManager.OnActiveSessionsChangedListener { updateSessions(it.orEmpty()) }
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val source = if (intent.action == Intent.ACTION_DREAMING_STARTED ||
                intent.action == Intent.ACTION_DREAMING_STOPPED) Source.DREAM else Source.APP
            repository.log(source, intent.action?.substringAfterLast('.') ?: "UNKNOWN_BROADCAST")
            CollectorWorker.now(context)
        }
    }
    private val audioCallback = object : AudioManager.AudioPlaybackCallback() {
        override fun onPlaybackConfigChanged(configs: MutableList<AudioPlaybackConfiguration>) = logAudio(configs)
    }
    private val pulse = object : Runnable {
        override fun run() {
            repository.scope.launch { repository.heartbeat() }
            CollectorWorker.now(this@SystemNotificationListenerService)
            handler.postDelayed(this, HEARTBEAT_INTERVAL_MILLIS)
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        repository.connected.value = true
        repository.log(Source.LISTENER, "onListenerConnected")
        stopCollectors()
        guarded {
            ContextCompat.registerReceiver(this, receiver, IntentFilter().apply {
                addAction(Intent.ACTION_DREAMING_STARTED)
                addAction(Intent.ACTION_DREAMING_STOPPED)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
            }, ContextCompat.RECEIVER_EXPORTED)
            receiverRegistered = true
        }
        guarded {
            sessions.addOnActiveSessionsChangedListener(sessionListener, listenerComponent(this), handler)
            sessionsRegistered = true
            updateSessions(sessions.getActiveSessions(listenerComponent(this)))
        }
        guarded {
            audio.registerAudioPlaybackCallback(audioCallback, handler)
            audioRegistered = true
            logAudio(audio.activePlaybackConfigurations)
        }
        handler.post(pulse)
    }

    override fun onListenerDisconnected() {
        repository.connected.value = false
        repository.log(Source.LISTENER, "onListenerDisconnected")
        stopCollectors()
        guarded { requestRebind(listenerComponent(this)) }
        super.onListenerDisconnected()
    }

    override fun onDestroy() {
        repository.connected.value = false
        repository.log(Source.LISTENER, "onDestroy")
        stopCollectors()
        super.onDestroy()
    }

    private fun updateSessions(active: List<MediaController>) {
        val tokens = active.map { it.sessionToken }.toSet()
        controllers.keys.filter { it !in tokens }.forEach { removeSession(it, "SESSION_REMOVED") }
        active.filter { it.sessionToken !in controllers }.forEach { controller ->
            val callback = object : MediaController.Callback() {
                override fun onPlaybackStateChanged(state: PlaybackState?) = guarded {
                    snapshot(controller, "PLAYBACK_STATE_CHANGED", state, controller.metadata)
                }
                override fun onMetadataChanged(metadata: MediaMetadata?) = guarded {
                    snapshot(controller, "METADATA_CHANGED", controller.playbackState, metadata)
                }
                override fun onSessionDestroyed() = removeSession(controller.sessionToken, "SESSION_DESTROYED")
            }
            guarded {
                controller.registerCallback(callback, handler)
                controllers[controller.sessionToken] = controller to callback
                snapshot(controller, "SESSION_ADDED", controller.playbackState, controller.metadata)
            }
        }
    }

    private fun snapshot(controller: MediaController, type: String, state: PlaybackState?, metadata: MediaMetadata?) {
        val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() }
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)?.takeIf { it.isNotBlank() }
        val changed = changes.mediaChanged(controller.sessionToken, MediaState(state?.state, title))
        if (type == "SESSION_ADDED" || changed) {
            repository.log(Source.MEDIA, type, controller.packageName, playbackState(state?.state), title)
        }
    }

    private fun removeSession(token: MediaSession.Token, type: String) {
        val (controller, callback) = controllers.remove(token) ?: return
        changes.remove(token)
        repository.log(Source.MEDIA, type, controller.packageName)
        guarded { controller.unregisterCallback(callback) }
    }

    private fun logAudio(configs: List<AudioPlaybackConfiguration>) {
        // Android exposes only active, anonymized configurations to this app.
        val media = configs.filter { it.audioAttributes.usage == AudioAttributes.USAGE_MEDIA }
        val state = "media_active=${media.size}; " + media.map {
            "usage=${it.audioAttributes.usage}, content=${it.audioAttributes.contentType}"
        }.sorted().joinToString()
        if (changes.audioChanged(state)) repository.log(Source.AUDIO, "PLAYBACK_CONFIG_CHANGED", state = state)
    }

    private fun stopCollectors() {
        handler.removeCallbacks(pulse)
        controllers.keys.toList().forEach { removeSession(it, "SESSION_UNOBSERVED") }
        changes.clear()
        if (sessionsRegistered) guarded { sessions.removeOnActiveSessionsChangedListener(sessionListener) }
        if (audioRegistered) guarded { audio.unregisterAudioPlaybackCallback(audioCallback) }
        if (receiverRegistered) guarded { unregisterReceiver(receiver) }
        sessionsRegistered = false
        audioRegistered = false
        receiverRegistered = false
    }

    private fun guarded(block: () -> Unit) {
        try { block() } catch (error: RuntimeException) {
            repository.log(Source.LISTENER, "COLLECTOR_ERROR", state = error.javaClass.simpleName)
        }
    }
}

private fun playbackState(state: Int?): String = when (state) {
    null -> "NO_STATE"
    0 -> "STATE_NONE"
    1 -> "STATE_STOPPED"
    2 -> "STATE_PAUSED (pauset)"
    3 -> "STATE_PLAYING (spiller)"
    4 -> "STATE_FAST_FORWARDING"
    5 -> "STATE_REWINDING"
    6 -> "STATE_BUFFERING"
    7 -> "STATE_ERROR"
    8 -> "STATE_CONNECTING"
    9 -> "STATE_SKIPPING_TO_PREVIOUS"
    10 -> "STATE_SKIPPING_TO_NEXT"
    11 -> "STATE_SKIPPING_TO_QUEUE_ITEM"
    else -> "STATE_$state"
}
