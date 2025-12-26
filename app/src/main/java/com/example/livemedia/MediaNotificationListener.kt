package com.example.livemedia

import android.app.Notification
import android.graphics.Bitmap
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

class MediaNotificationListener : NotificationListenerService() {

    private lateinit var publisher: NotificationPublisher
    
    private var activeSourcePackage: String? = null
    private var activeToken: MediaSession.Token? = null
    private var activeController: MediaController? = null
    private var currentSongId: Int = NotificationPublisher.BASE_NOTIFICATION_ID
    private var lastTitle: String? = null
    private var cachedActions: List<Notification.Action> = emptyList()
    private var cachedLargeIcon: Bitmap? = null
    
    private val handler = Handler(Looper.getMainLooper())
    private var pendingUpdate = false
    private var lastIsPlaying: Boolean? = null

    private val controllerCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            val newIsPlaying = state?.state == PlaybackState.STATE_PLAYING || 
                               state?.state == PlaybackState.STATE_BUFFERING
            
            if (lastIsPlaying != null && lastIsPlaying != newIsPlaying) {
                lastIsPlaying = newIsPlaying
                refreshActionsFromSource()
                handler.post { doPost() }
            } else {
                lastIsPlaying = newIsPlaying
                scheduleUpdate()
            }
        }
        override fun onMetadataChanged(metadata: MediaMetadata?) { scheduleUpdate() }
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
        try {
            activeNotifications?.find { it.packageName == pkg }?.let { sbn ->
                cachedActions = sbn.notification.actions?.toList() ?: emptyList()
            }
        } catch (e: Exception) { /* ignore */ }
    }
    
    private fun scheduleUpdate() {
        if (!pendingUpdate) {
            pendingUpdate = true
            handler.postDelayed({
                pendingUpdate = false
                postNotification()
            }, 800)
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
        val prefs = AppPreferences(this)
        val selectedPackages = prefs.getSelectedPackages()
        val notifications = activeNotifications ?: return
        
        // First pass: find any playing media
        for (sbn in notifications) {
            if (!isMediaNotification(sbn)) continue
            if (selectedPackages.isNotEmpty() && !selectedPackages.contains(sbn.packageName)) continue
            
            val token = getToken(sbn) ?: continue
            val controller = MediaController(this, token)
            if (isPlaying(controller.playbackState)) {
                switchToSource(sbn, token)
                return
            }
        }
        
        // Second pass: take first available media if nothing is playing
        for (sbn in notifications) {
            if (!isMediaNotification(sbn)) continue
            if (selectedPackages.isNotEmpty() && !selectedPackages.contains(sbn.packageName)) continue
            
            val token = getToken(sbn) ?: continue
            switchToSource(sbn, token)
            return
        }
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
        
        val prefs = AppPreferences(this)
        val selectedPackages = prefs.getSelectedPackages()
        if (selectedPackages.isNotEmpty() && !selectedPackages.contains(sbn.packageName)) return
        
        val token = getToken(sbn) ?: return
        val isNewSourcePlaying = MediaController(this, token).let { isPlaying(it.playbackState) }
        val isCurrentPlaying = isPlaying(activeController?.playbackState)
        
        when {
            sbn.packageName == activeSourcePackage -> {
                cachedActions = sbn.notification.actions?.toList() ?: emptyList()
                updateLargeIcon(sbn)
                scheduleUpdate()
            }
            // Always switch if new source is playing (user intent to play new media), 
            // even if current source hasn't reported 'paused' yet (fixes 2s lag).
            activeSourcePackage == null || isNewSourcePlaying -> {
                switchToSource(sbn, token)
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (sbn.packageName == activeSourcePackage) {
            releaseController()
            activeSourcePackage = null
            lastTitle = null
            lastIsPlaying = null
            publisher.cancelNotification()
            handler.postDelayed({ findActiveMedia() }, 300)
        }
    }
    
    private fun switchToSource(sbn: StatusBarNotification, token: MediaSession.Token) {
        releaseController()
        
        activeSourcePackage = sbn.packageName
        activeToken = token
        cachedActions = sbn.notification.actions?.toList() ?: emptyList()
        updateLargeIcon(sbn)
        lastIsPlaying = null
        lastTitle = null
        currentSongId = NotificationPublisher.BASE_NOTIFICATION_ID
        
        activeController = MediaController(this, token).apply {
            registerCallback(controllerCallback, handler)
        }
        
        doPost()
    }
    
    private fun updateLargeIcon(sbn: StatusBarNotification) {
        try {
            sbn.notification.getLargeIcon()?.let { icon ->
                (icon.loadDrawable(this) as? android.graphics.drawable.BitmapDrawable)?.let {
                    cachedLargeIcon = it.bitmap
                }
            }
        } catch (e: Exception) { /* ignore */ }
    }

    private fun releaseController() {
        try { activeController?.unregisterCallback(controllerCallback) } catch (e: Exception) {}
        activeController = null
        activeToken = null
    }

    private fun isPlaying(state: PlaybackState?) = 
        state?.state == PlaybackState.STATE_PLAYING || state?.state == PlaybackState.STATE_BUFFERING

    private fun postNotification() {
        val pkg = activeSourcePackage ?: return
        val controller = activeController
        val metadata = controller?.metadata
        val isPlaying = isPlaying(controller?.playbackState)

        val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
        
        if (title.isNullOrBlank() || title == "Audio") {
            publisher.cancelNotification()
            return
        }

        val newSongId = NotificationPublisher.BASE_NOTIFICATION_ID + abs(title.hashCode() % 10000)
        currentSongId = newSongId
        lastTitle = title
        doPost()
    }
    
    private fun doPost() {
        val pkg = activeSourcePackage ?: return
        val metadata = activeController?.metadata
        val isPlaying = isPlaying(activeController?.playbackState)

        val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
            ?: "Audio"
        val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: ""
        val art = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
            ?: cachedLargeIcon

        publisher.updateNotification(
            notificationId = currentSongId,
            title = title,
            artist = artist,
            bitmap = art,
            token = activeToken,
            isPlaying = isPlaying,
            actions = cachedActions,
            sourcePackage = pkg
        )
    }

    override fun onDestroy() {
        releaseController()
        super.onDestroy()
    }
}
