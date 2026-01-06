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
import com.example.livemedia.handlers.TorchHandler
import com.example.livemedia.handlers.OtpHandler
import com.example.livemedia.handlers.DownloadHandler
import com.example.livemedia.handlers.LiveActivityState
import com.example.livemedia.handlers.LiveActivityType
import kotlinx.coroutines.*
import kotlin.math.abs

/**
 * Listens for media notifications and creates live update notifications.
 */
class MediaNotificationListener : NotificationListenerService() {

    private lateinit var publisher: NotificationPublisher
    // LiveActivityManager is now a Singleton Object

    
    // Media Specifics
    private var activeSourcePackage: String? = null
    private var activeSourceNotificationId: Int? = null
    private var activeToken: MediaSession.Token? = null
    private var activeController: MediaController? = null
    private var cachedActions: List<Notification.Action> = emptyList()
    private var cachedLargeIcon: Bitmap? = null
    
    // Handlers (modular architecture)
    private lateinit var torchHandler: TorchHandler
    private lateinit var otpHandler: OtpHandler
    private lateinit var downloadHandler: DownloadHandler
    
    // Optimization: Cache last published state to avoid redundant notification updates
    private var lastPublishedState: LiveActivityManager.PublishedState? = null
    
    // Track if ANY notification is currently showing (for proper cancellation)
    private var isNotificationShowing: Boolean = false
    
    private val handler = Handler(Looper.getMainLooper())
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    // 30 Seconds for OTP, so we need faster updates to show countdown smoothly? 
    // The Progress bar max is 30, so 1 update per sec is fine.
    // For downloads, 1 sec is also fine.
    
    companion object {
        private const val TICK_INTERVAL_MS = 1000L
        private const val ACTION_RESUME_MEDIA = "com.example.livemedia.ACTION_RESUME_MEDIA"
        private const val EXTRA_PACKAGE_NAME = "package_name"
    }
    
    // Caches for resumption
    private var cachedMetadata: MediaMetadata? = null
    private var cachedTitle: String = ""
    private var cachedArtist: String = ""
    private var cachedDuration: Long = 0L
    
    // Removed updateRunnable in favor of Coroutine Loop

    private val controllerCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            updateMediaState()
        }
        override fun onMetadataChanged(metadata: MediaMetadata?) {
            cachedMetadata = metadata
            updateMediaState()
        }
        override fun onSessionDestroyed() {
            handler.post { 
                // Don't release, persist instead
                persistMediaState()
                // Do not nullify source immediately, wait for scan
                findActiveMedia()
            }
        }
    }

    private val actionReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context, intent: android.content.Intent?) {
            val action = intent?.action ?: return
            
            // Delegate to handlers
            if (otpHandler.handleAction(context, action, intent)) return
            if (torchHandler.handleAction(context, action, intent)) return
            
            // Handle remaining actions
            if (action == ACTION_RESUME_MEDIA) {
                 val pkg = intent.getStringExtra(EXTRA_PACKAGE_NAME) ?: return
                 // Attempt to resume playback via Media Button Intent
                 val keyIntent = android.content.Intent(android.content.Intent.ACTION_MEDIA_BUTTON).apply {
                     setPackage(pkg)
                     putExtra(android.content.Intent.EXTRA_KEY_EVENT, android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_MEDIA_PLAY))
                 }
                 context.sendBroadcast(keyIntent)
                 
                 // Also try key up immediately
                 val keyUpIntent = android.content.Intent(android.content.Intent.ACTION_MEDIA_BUTTON).apply {
                     setPackage(pkg)
                     putExtra(android.content.Intent.EXTRA_KEY_EVENT, android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_MEDIA_PLAY))
                 }
                 context.sendBroadcast(keyUpIntent)
            } else if (action == NotificationPublisher.ACTION_NOTIFICATION_DISMISSED) {
                // User swiped it away.
                // CRITICAL FIX: If media is playing, DO NOT release controller. Just force a refresh.
                val isPlaying = activeController?.playbackState?.state == PlaybackState.STATE_PLAYING
                
                if (!isPlaying) {
                     // Manual Dismissal by user -> Really clear it
                     otpHandler.clearState()
                     fullyReleaseController()
                } else {
                     // Just force a refresh
                }
                lastPublishedState = null
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        publisher = NotificationPublisher(this)

        // Initialize Handlers
        torchHandler = TorchHandler(this, handler) { updateLiveActivity() }
        otpHandler = OtpHandler(this) { updateLiveActivity() }
        downloadHandler = DownloadHandler(this) { updateLiveActivity() }
        
        // Register Receivers
        val filter = android.content.IntentFilter().apply {
            addAction(OtpHandler.ACTION_COPY_OTP)
            addAction(TorchHandler.ACTION_TURN_OFF_TORCH)
            addAction(ACTION_RESUME_MEDIA)
            addAction(NotificationPublisher.ACTION_NOTIFICATION_DISMISSED)
        }
        registerReceiver(actionReceiver, filter, android.content.Context.RECEIVER_NOT_EXPORTED)
        
        // Start the eternal tick loop
        serviceScope.launch {
            while (isActive) {
                updateLiveActivity()
                delay(TICK_INTERVAL_MS)
            }
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        unregisterReceiver(actionReceiver)
        torchHandler.cleanup()
        otpHandler.cleanup()
        downloadHandler.cleanup()
        releaseController()
        publisher.cancelNotification()
        super.onDestroy()
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        findActiveMedia()
    }

    private fun updateLiveActivity() {
        // Run on Background to avoid Jitter
        serviceScope.launch(Dispatchers.Default) {
             // 1. Update Media State (in case position changed naturally)
            if (activeController != null) updateMediaState()
            
            // 2. Check handlers by priority (Torch > OTP > Download > Media)
            val torchState = torchHandler.getCurrentState()
            if (torchState != null) {
                publishState(torchState)
                return@launch
            }
            
            val otpState = otpHandler.getCurrentState()
            if (otpState != null) {
                publishState(otpState)
                return@launch
            }
            
            val downloadState = downloadHandler.getCurrentState()
            if (downloadState != null) {
                publishState(downloadState)
                return@launch
            }
            
            // 3. Fall back to legacy LiveActivityManager for Media only
            val state = LiveActivityManager.getBestState()
            
            // If nothing is active, cancel the notification
            if (state == null) {
                if (isNotificationShowing) {
                    publisher.cancelNotification()
                    isNotificationShowing = false
                    lastPublishedState = null
                }
                return@launch
            }
            
            // Optimization: Skip update if title & artist are identical
            val shouldSkip = lastPublishedState != null &&
                state.title == lastPublishedState?.title &&
                state.artist == lastPublishedState?.artist &&
                state.isPlaying == lastPublishedState?.isPlaying
            
            if (shouldSkip) return@launch
            lastPublishedState = state
            
            // 4. Publish Media state
            publisher.updateNotification(
                notificationId = NotificationPublisher.BASE_NOTIFICATION_ID, 
                title = state.title,
                artist = state.artist,
                bitmap = state.bitmap,
                token = state.token,
                isPlaying = state.isPlaying,
                actions = state.actions,
                sourcePackage = state.sourcePackage,
                launchIntent = activeNotifications?.find { it.packageName == state.sourcePackage }?.notification?.contentIntent,
                duration = state.duration,
                position = state.position,
                overrideChipText = state.chipText,
                skipMediaFilter = false,
                isOtp = false,
                isDownload = false,
                isTorch = false
            )
            isNotificationShowing = true
        }
    }
    
    private fun publishState(state: LiveActivityState) {
        publisher.updateNotification(
            notificationId = NotificationPublisher.BASE_NOTIFICATION_ID,
            title = state.title,
            artist = state.subtitle,
            bitmap = state.icon,
            token = state.mediaToken,
            isPlaying = state.isPlaying,
            actions = state.actions,
            sourcePackage = state.sourcePackage,
            launchIntent = null,
            duration = state.duration,
            position = state.position,
            overrideChipText = state.chipText,
            skipMediaFilter = true,
            isOtp = state.type == LiveActivityType.OTP,
            isDownload = state.type == LiveActivityType.DOWNLOAD,
            isTorch = state.type == LiveActivityType.TORCH
        )
        isNotificationShowing = true
        lastPublishedState = null // Reset legacy cache since we're using new state
    }
    




    private fun createResumeAction(packageName: String): Notification.Action {
        val intent = android.content.Intent(ACTION_RESUME_MEDIA).apply {
            putExtra(EXTRA_PACKAGE_NAME, packageName)
            setPackage(this@MediaNotificationListener.packageName)
        }
        val pendingIntent = android.app.PendingIntent.getBroadcast(
            this, packageName.hashCode(), intent, 
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Action.Builder(
            android.R.drawable.ic_media_play, 
            "Play", 
            pendingIntent
        ).build()
    }

    private fun updateMediaState() {
        val controller = activeController
        
        // Offline / Resumable Mode
        if (controller == null) {
            if (activeSourcePackage != null) { // We are in persisted state
                 LiveActivityManager.updateMediaState(
                    LiveActivityManager.MediaState(
                        title = cachedTitle,
                        artist = cachedArtist,
                        art = cachedLargeIcon,
                        token = null,
                        isPlaying = false,
                        actions = cachedActions, // Use the synthesized ones
                        sourcePackage = activeSourcePackage ?: "",
                        duration = cachedDuration,
                        position = 0L
                    )
                )
            } else {
                 LiveActivityManager.updateMediaState(null)
            }
            return
        }

        val metadata = controller.metadata
        val state = controller.playbackState
        
        if (metadata == null) return // Wait for metadata

        val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE) 
            ?: metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE) 
            ?: "Audio"
            
        // Filter out junk
        if (title.isNullOrBlank()) {
             // Fix Zombie: If metadata is empty, clear the state instead of ignoring it
             fullyReleaseController()
             return
        }
        
        cachedTitle = title
        cachedArtist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: ""
        cachedDuration = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION)
        cachedMetadata = metadata

        LiveActivityManager.updateMediaState(
            LiveActivityManager.MediaState(
                title = cachedTitle,
                artist = cachedArtist,
                art = metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: cachedLargeIcon,
                token = activeToken,
                isPlaying = state?.state == PlaybackState.STATE_PLAYING || state?.state == PlaybackState.STATE_BUFFERING,
                actions = cachedActions,
                sourcePackage = activeSourcePackage ?: "",
                duration = cachedDuration,
                position = state?.position ?: 0L
            )
        )
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return

        // 1. Check for OTP (delegated to handler)
        otpHandler.onNotificationPosted(sbn)

        // 2. Check for Downloads (delegated to handler)
        downloadHandler.onNotificationPosted(sbn)

        // 3. Check for Media
        if (isMediaNotification(sbn)) {
             handleMediaNotification(sbn)
        } else if (sbn.packageName == activeSourcePackage && sbn.id == activeSourceNotificationId) {
             // Active source updated specifically the media notification to a non-media state
             fullyReleaseController()
             activeSourcePackage = null
             activeSourceNotificationId = null
             handler.postDelayed({ findActiveMedia() }, 300L)
        }
    }
    



    private fun handleMediaNotification(sbn: StatusBarNotification) {
        val token = getToken(sbn) ?: return
        
        when {
            // Same package AND we have an active controller - just update metadata
            sbn.packageName == activeSourcePackage && activeController != null -> {
                cachedActions = sbn.notification.actions?.toList() ?: emptyList()
                updateLargeIcon(sbn)
                updateMediaState()
            }
            // Same package but controller is null (resuming from persisted state) - re-attach
            sbn.packageName == activeSourcePackage && activeController == null -> {
                switchToSource(sbn, token)
            }
            // Different package or no active source - switch if playing
            activeSourcePackage == null || isNewSourcePlaying(token) -> {
                switchToSource(sbn, token)
            }
        }
    }

    private fun isNewSourcePlaying(token: MediaSession.Token): Boolean {
         val controller = MediaController(this, token)
         return controller.playbackState?.state == PlaybackState.STATE_PLAYING
    }
    
    /* ... Helper methods ... */
    
    private fun findActiveMedia() {
        val notifications = activeNotifications ?: return
        // ... (Same logic, just calling switchToSource) ... 
        notifications.asSequence()
            .filter { isMediaNotification(it) }
            .mapNotNull { sbn -> getToken(sbn)?.let { sbn to it } }
            .find { (sbn, token) -> isNewSourcePlaying(token) }
            ?.let { (sbn, token) -> switchToSource(sbn, token); return }
            
        notifications.asSequence()
            .filter { isMediaNotification(it) }
            .mapNotNull { sbn -> getToken(sbn)?.let { sbn to it } }
            .firstOrNull()
            ?.let { (sbn, token) -> switchToSource(sbn, token) }
    }

    private fun switchToSource(sbn: StatusBarNotification, token: MediaSession.Token) {
        releaseController()
        
        activeSourcePackage = sbn.packageName
        activeSourceNotificationId = sbn.id
        activeToken = token
        cachedActions = sbn.notification.actions?.toList() ?: emptyList()
        updateLargeIcon(sbn)
        
        activeController = MediaController(this, token).apply {
            registerCallback(controllerCallback, handler)
        }
        
        updateMediaState()
        updateLiveActivity()
    }
    
    private fun persistMediaState() {
        val pkg = activeSourcePackage ?: return
        
        // Synthesize actions: Replace Pause with Resume, or just Prepend Resume
        // Simple heuristic: If we don't have a Play action, add our component
        val currentActions = cachedActions.toMutableList()
        val hasPlay = currentActions.any { it.title?.toString()?.contains("Play", ignoreCase = true) == true }
        
        if (!hasPlay) {
            // Likely was "Pause" before. Let's put "Play" at the front or replace "Pause"
            val pauseIndex = currentActions.indexOfFirst { it.title?.toString()?.contains("Pause", ignoreCase = true) == true }
            val resumeAction = createResumeAction(pkg)
            
            if (pauseIndex != -1) {
                currentActions[pauseIndex] = resumeAction
            } else {
                currentActions.add(0, resumeAction)
            }
        }
        cachedActions = currentActions
        
        // Release controller but KEEP activeSourcePackage
        runCatching { activeController?.unregisterCallback(controllerCallback) }
        activeController = null
        activeToken = null
        // activeSourcePackage remains!
        // activeSourceNotificationId remains (or ignored)
        
        updateMediaState()
    }

    private fun fullyReleaseController() {
        runCatching { activeController?.unregisterCallback(controllerCallback) }
        activeController = null
        activeToken = null
        activeSourcePackage = null
        activeSourceNotificationId = null
        cachedMetadata = null
        LiveActivityManager.updateMediaState(null)
        
        // Also clear handlers if needed (they manage their own state now)
        otpHandler.clearState()
        downloadHandler.clearState()
        publisher.cancelNotification()
    }
    
    // Replaces the old releaseController with fullyReleaseController OR persist logic depending on context
    // But since releaseController was used in many places, let's redefine it as fullyRelease
    private fun releaseController() {
        // Default behavior for "releaseController" calls in the code was "Stop Tracking".
        // But for "Removed" logic, we now want "Persist".
        // So we should update call sites.
        fullyReleaseController()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        // 1. Check if User dismissed OUR notification
        // 1. Check if User dismissed OUR notification
        if (sbn.packageName == packageName && sbn.id == NotificationPublisher.BASE_NOTIFICATION_ID) {
            
            val isPlaying = activeController?.playbackState?.state == PlaybackState.STATE_PLAYING
            if (!isPlaying) {
                 fullyReleaseController()
            }
            // Force state clear to trigger regeneration if playing
            lastPublishedState = null
        }

        // Delegate to download handler
        downloadHandler.onNotificationRemoved(sbn)
        
        if (sbn.packageName == activeSourcePackage) {
            // Robust Check: Instead of relying on ID or specific SBN properties of the removed notification
            // (which might be incomplete), check if the OS still reports ANY media notification for this package.
            // If the app was force-closed or swiped, this should return false.
            val stillHasMedia = try {
                activeNotifications?.any { 
                    it.packageName == activeSourcePackage && isMediaNotification(it) 
                } == true
            } catch (e: Exception) {
                false // If we can't check, assume it's gone to prevent sticking
            }

            if (!stillHasMedia) {
                // Was: releaseController() -> NOW: persistState
                persistMediaState()
                
                // Do NOT nullify package, we want to remember it
                handler.postDelayed({ findActiveMedia() }, 500L)
            }
        }
    }
    
    // ... Utils ...
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
    
    private fun updateLargeIcon(sbn: StatusBarNotification) {
        runCatching {
            sbn.notification.getLargeIcon()?.let { icon ->
                (icon.loadDrawable(this) as? BitmapDrawable)?.let {
                    cachedLargeIcon = it.bitmap
                }
            }
        }
    }
}

