package com.example.livemedia

import android.app.Notification
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlin.math.abs

/**
 * Listens for media notifications and creates live update notifications.
 */
class MediaNotificationListener : NotificationListenerService() {

    private lateinit var publisher: NotificationPublisher
    
    private var activeSourcePackage: String? = null
    private var activeToken: MediaSession.Token? = null
    private var activeController: MediaController? = null
    private var currentSongId: Int = NotificationPublisher.BASE_NOTIFICATION_ID
    private var cachedActions: List<Notification.Action> = emptyList()
    private var cachedLargeIcon: Bitmap? = null
    
    private val handler = Handler(Looper.getMainLooper())
    private var pendingUpdate = false
    private var lastIsPlaying: Boolean? = null
    
    companion object {
        private const val PROGRESS_UPDATE_INTERVAL_MS = 1000L
        private const val UPDATE_DEBOUNCE_MS = 800L
        private const val SOURCE_SWITCH_DELAY_MS = 300L
    }
    
    private val progressUpdateRunnable = object : Runnable {
        override fun run() {
            if (isPlaying(activeController?.playbackState)) {
                doPost()
                handler.postDelayed(this, PROGRESS_UPDATE_INTERVAL_MS)
            }
        }
    }

    private val controllerCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            val newIsPlaying = isPlaying(state)
            val wasPlaying = lastIsPlaying
            lastIsPlaying = newIsPlaying
            
            if (wasPlaying != newIsPlaying) {
                refreshActionsFromSource()
                handler.post { doPost() }
                if (newIsPlaying) startProgressUpdates() else stopProgressUpdates()
            } else {
                scheduleUpdate()
            }
        }
        
        override fun onMetadataChanged(metadata: MediaMetadata?) = scheduleUpdate()
        
        override fun onSessionDestroyed() {
            handler.post { 
                releaseController()
                activeSourcePackage = null
                findActiveMedia()
            }
        }
    }
    
    private fun refreshActionsFromSource() {
        val pkg = activeSourcePackage ?: return
        runCatching {
            activeNotifications?.find { it.packageName == pkg }?.let { sbn ->
                cachedActions = sbn.notification.actions?.toList() ?: emptyList()
            }
        }
    }
    
    private fun scheduleUpdate() {
        if (!pendingUpdate) {
            pendingUpdate = true
            handler.postDelayed({
                pendingUpdate = false
                postNotification()
            }, UPDATE_DEBOUNCE_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        publisher = NotificationPublisher(this)
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        findActiveMedia()
    }

    private fun findActiveMedia() {
        val notifications = activeNotifications ?: return
        
        // First pass: find playing media
        notifications.asSequence()
            .filter { isMediaNotification(it) }
            .mapNotNull { sbn -> getToken(sbn)?.let { sbn to it } }
            .find { (sbn, token) -> isPlaying(MediaController(this, token).playbackState) }
            ?.let { (sbn, token) -> 
                switchToSource(sbn, token)
                return
            }
        
        // Second pass: take first available media
        notifications.asSequence()
            .filter { isMediaNotification(it) }
            .mapNotNull { sbn -> getToken(sbn)?.let { sbn to it } }
            .firstOrNull()
            ?.let { (sbn, token) -> switchToSource(sbn, token) }
    }

    private fun isMediaNotification(sbn: StatusBarNotification): Boolean {
        val extras = sbn.notification.extras
        return extras.containsKey(Notification.EXTRA_MEDIA_SESSION) ||
               extras.getString(Notification.EXTRA_TEMPLATE)?.contains("MediaStyle") == true
    }
    
    private fun getToken(sbn: StatusBarNotification): MediaSession.Token? {
        val extras = sbn.notification.extras
        return if (Build.VERSION.SDK_INT >= 33) {
            extras.getParcelable(Notification.EXTRA_MEDIA_SESSION, MediaSession.Token::class.java)
        } else {
            @Suppress("DEPRECATION")
            extras.getParcelable(Notification.EXTRA_MEDIA_SESSION)
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return
        if (!isMediaNotification(sbn)) return
        
        val token = getToken(sbn) ?: return
        val isNewSourcePlaying = isPlaying(MediaController(this, token).playbackState)
        
        when {
            sbn.packageName == activeSourcePackage -> {
                cachedActions = sbn.notification.actions?.toList() ?: emptyList()
                updateLargeIcon(sbn)
                scheduleUpdate()
            }
            activeSourcePackage == null || isNewSourcePlaying -> {
                switchToSource(sbn, token)
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (sbn.packageName == activeSourcePackage) {
            releaseController()
            activeSourcePackage = null
            lastIsPlaying = null
            publisher.cancelNotification()
            handler.postDelayed({ findActiveMedia() }, SOURCE_SWITCH_DELAY_MS)
        }
    }
    
    private fun switchToSource(sbn: StatusBarNotification, token: MediaSession.Token) {
        releaseController()
        
        activeSourcePackage = sbn.packageName
        activeToken = token
        cachedActions = sbn.notification.actions?.toList() ?: emptyList()
        updateLargeIcon(sbn)
        lastIsPlaying = null
        currentSongId = NotificationPublisher.BASE_NOTIFICATION_ID
        
        activeController = MediaController(this, token).apply {
            registerCallback(controllerCallback, handler)
        }
        
        doPost()
        
        if (isPlaying(activeController?.playbackState)) {
            startProgressUpdates()
        }
    }
    
    private fun startProgressUpdates() {
        handler.removeCallbacks(progressUpdateRunnable)
        handler.postDelayed(progressUpdateRunnable, PROGRESS_UPDATE_INTERVAL_MS)
    }
    
    private fun stopProgressUpdates() {
        handler.removeCallbacks(progressUpdateRunnable)
    }
    
    private fun updateLargeIcon(sbn: StatusBarNotification) {
        runCatching {
            sbn.notification.getLargeIcon()?.let { icon ->
                (icon.loadDrawable(this) as? BitmapDrawable)?.let {
                    cachedLargeIcon = it.bitmap
                }
            }
        }
    }

    private fun releaseController() {
        stopProgressUpdates()
        runCatching { activeController?.unregisterCallback(controllerCallback) }
        activeController = null
        activeToken = null
    }

    private fun isPlaying(state: PlaybackState?) = 
        state?.state == PlaybackState.STATE_PLAYING || state?.state == PlaybackState.STATE_BUFFERING

    private fun postNotification() {
        val metadata = activeController?.metadata ?: return
        
        val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
        
        if (title.isNullOrBlank() || title == "Audio") {
            publisher.cancelNotification()
            return
        }

        currentSongId = NotificationPublisher.BASE_NOTIFICATION_ID + abs(title.hashCode() % 10000)
        doPost()
    }
    
    private fun doPost() {
        val pkg = activeSourcePackage ?: return
        val metadata = activeController?.metadata ?: return
        val playbackState = activeController?.playbackState

        val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
            ?: "Audio"
        val artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: ""
        val art = metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
            ?: cachedLargeIcon
        
        publisher.updateNotification(
            notificationId = currentSongId,
            title = title,
            artist = artist,
            bitmap = art,
            token = activeToken,
            isPlaying = isPlaying(playbackState),
            actions = cachedActions,
            sourcePackage = pkg,
            launchIntent = activeNotifications?.find { it.packageName == pkg }?.notification?.contentIntent,
            duration = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION),
            position = playbackState?.position ?: 0L
        )
    }

    override fun onDestroy() {
        releaseController()
        super.onDestroy()
    }
}
