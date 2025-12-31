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
    private val liveActivityManager = LiveActivityManager()

    
    // Media Specifics
    private var activeSourcePackage: String? = null
    private var activeSourceNotificationId: Int? = null
    private var activeToken: MediaSession.Token? = null
    private var activeController: MediaController? = null
    private var cachedActions: List<Notification.Action> = emptyList()
    private var cachedLargeIcon: Bitmap? = null
    
    // Optimization: Cache last published state to avoid redundant notification updates
    private var lastPublishedState: LiveActivityManager.PublishedState? = null
    
    private val handler = Handler(Looper.getMainLooper())
    // 30 Seconds for OTP, so we need faster updates to show countdown smoothly? 
    // The Progress bar max is 30, so 1 update per sec is fine.
    // For downloads, 1 sec is also fine.
    
    companion object {
        private const val TICK_INTERVAL_MS = 1000L
        private const val ACTION_COPY_OTP = "com.example.livemedia.ACTION_COPY_OTP"
        private const val EXTRA_OTP_CODE = "otp_code"
    }
    
    private val updateRunnable = object : Runnable {
        override fun run() {
            updateLiveActivity()
            handler.postDelayed(this, TICK_INTERVAL_MS)
        }
    }

    private val controllerCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            updateMediaState()
        }
        override fun onMetadataChanged(metadata: MediaMetadata?) {
            updateMediaState()
        }
        override fun onSessionDestroyed() {
            handler.post { 
                releaseController()
                // Do not nullify source immediately, wait for scan
                findActiveMedia()
            }
        }
    }

    private val copyReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context, intent: android.content.Intent?) {
            if (intent?.action == ACTION_COPY_OTP) {
                val code = intent.getStringExtra(EXTRA_OTP_CODE) ?: return
                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("OTP", code)
                clipboard.setPrimaryClip(clip)
            } else if (intent?.action == NotificationPublisher.ACTION_NOTIFICATION_DISMISSED) {
                // User swiped it away. Clear all states.
                liveActivityManager.updateMediaState(null)
                liveActivityManager.clearOtpState()
                // For downloads, we might want to keep tracking internally, but stop showing the chip.
                // Resetting download state is safest to stop reposting.
                val pkg = liveActivityManager.getBestState()?.sourcePackage
                if (pkg != null) liveActivityManager.clearDownloadState(pkg)
                
                // Clear the actual notification just in case
                publisher.cancelNotification()
                lastPublishedState = null
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        publisher = NotificationPublisher(this)

        
        // Register Receivers
        val filter = android.content.IntentFilter().apply {
            addAction(ACTION_COPY_OTP)
            addAction(NotificationPublisher.ACTION_NOTIFICATION_DISMISSED)
        }
        registerReceiver(copyReceiver, filter, android.content.Context.RECEIVER_NOT_EXPORTED)
        
        // Start the eternal tick loop
        handler.post(updateRunnable)
    }

    override fun onDestroy() {
        handler.removeCallbacks(updateRunnable)
        unregisterReceiver(copyReceiver)
        releaseController()
        publisher.cancelNotification()
        super.onDestroy()
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        findActiveMedia()
    }

    private fun updateLiveActivity() {
        // 1. Update Media State (in case position changed naturally)
        if (activeController != null) updateMediaState()
        
        // 2. Get Best State
        val state = liveActivityManager.getBestState()
        
        // Optimization: Skip update if state is identical
        if (state == lastPublishedState) return
        lastPublishedState = state // Update cache
        
        // 3. Publish
        if (state != null) {
            
            // Logic to inject specific actions
            var finalActions = state.actions
            if (state.isOtp) {
                 finalActions = listOf(createCopyAction(state.artist)) // artist holds the code
            }

            publisher.updateNotification(
                notificationId = NotificationPublisher.BASE_NOTIFICATION_ID, 
                title = state.title,
                artist = state.artist,
                bitmap = state.bitmap,
                token = state.token,
                isPlaying = state.isPlaying,
                actions = finalActions,
                sourcePackage = state.sourcePackage,
                launchIntent = activeNotifications?.find { it.packageName == state.sourcePackage }?.notification?.contentIntent,
                duration = state.duration,
                position = state.position,
                overrideChipText = state.chipText,
                skipMediaFilter = state.isOtp || state.isDownload,
                isOtp = state.isOtp,
                isDownload = state.isDownload
            )
        } else {

            if (lastPublishedState != null) {
                // Only cancel if we were previously showing something
                publisher.cancelNotification()
                lastPublishedState = null
            }
        }
    }
    
    private fun createCopyAction(code: String): Notification.Action {
        val intent = android.content.Intent(ACTION_COPY_OTP).apply {
            putExtra(EXTRA_OTP_CODE, code)
            setPackage(packageName)
        }
        val pendingIntent = android.app.PendingIntent.getBroadcast(
            this, code.hashCode(), intent, 
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        
        // Using a standard icon, usually would want a dedicated copy icon
        return Notification.Action.Builder(
            android.R.drawable.ic_menu_save, 
            "Copy", 
            pendingIntent
        ).build()
    }

    private fun updateMediaState() {
        val controller = activeController
        if (controller == null) {
            liveActivityManager.updateMediaState(null)
            return
        }

        val metadata = controller.metadata
        val state = controller.playbackState
        
        if (metadata == null) return // Wait for metadata

        val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE) 
            ?: metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE) 
            ?: "Audio"
            
        // Filter out junk
        if (title.isNullOrBlank()) return

        liveActivityManager.updateMediaState(
            LiveActivityManager.MediaState(
                title = title,
                artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: "",
                art = metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: cachedLargeIcon,
                token = activeToken,
                isPlaying = state?.state == PlaybackState.STATE_PLAYING || state?.state == PlaybackState.STATE_BUFFERING,
                actions = cachedActions,
                sourcePackage = activeSourcePackage ?: "",
                duration = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION),
                position = state?.position ?: 0L
            )
        )
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return

        // 1. Check for OTP
        checkForOtp(sbn)

        // 2. Check for Downloads
        checkForDownload(sbn)

        // 3. Check for Media
        if (isMediaNotification(sbn)) {
             handleMediaNotification(sbn)
        } else if (sbn.packageName == activeSourcePackage && sbn.id == activeSourceNotificationId) {
             // Active source updated specifically the media notification to a non-media state
             releaseController()
             activeSourcePackage = null
             activeSourceNotificationId = null
             handler.postDelayed({ findActiveMedia() }, 300L)
        }
    }
    
    private fun checkForOtp(sbn: StatusBarNotification) {
        val extras = sbn.notification.extras
        // Combine Title, Text and BigText for full context
        // Optimized: Avoid allocations (List, Filter, Join)
        val sb = StringBuilder()
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)
        if (!title.isNullOrEmpty()) sb.append(title).append(" ")
        
        val textContent = extras.getCharSequence(Notification.EXTRA_TEXT)
        if (!textContent.isNullOrEmpty()) sb.append(textContent).append(" ")
        
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
        if (!bigText.isNullOrEmpty()) sb.append(bigText)
        
        val text = sb.toString()
        
        OtpExtractor.extract(text)?.let { code ->
            liveActivityManager.updateOtpState(code, sbn.packageName)
            // Force immediate update
            handler.post { updateLiveActivity() }
        }
    }
    
    private fun checkForDownload(sbn: StatusBarNotification) {
        val extras = sbn.notification.extras
        if (extras.containsKey(Notification.EXTRA_PROGRESS)) {
            val max = extras.getInt(Notification.EXTRA_PROGRESS_MAX)
            val indeterminate = extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE)
            
            if (max > 0 && !indeterminate) {
                // Ignore if it looks like a media music player track progress (usually handled by media session)
                if (!isMediaNotification(sbn)) {
                    val current = extras.getInt(Notification.EXTRA_PROGRESS)
                    val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: "Downloading..."
                    // Optimized: Avoid copying array to list if possible, or use lightweight wrapper
                    val actionsArray = sbn.notification.actions
                    val actions = if (actionsArray != null) java.util.Arrays.asList(*actionsArray) else emptyList()
                    liveActivityManager.updateDownloadState(sbn.packageName, title, current, max, actions, sbn.id)
                    return
                }
            }
        }
        // If we reach here, it's not a valid download update.
        // If this specific notification was the active download, clear it.
        liveActivityManager.tryClearDownloadState(sbn.packageName, sbn.id)
    }
    
    private fun checkForDownloadRemoval(sbn: StatusBarNotification) {
        liveActivityManager.clearDownloadState(sbn.packageName)
    }

    private fun handleMediaNotification(sbn: StatusBarNotification) {
        val token = getToken(sbn) ?: return
        
        when {
            sbn.packageName == activeSourcePackage -> {
                cachedActions = sbn.notification.actions?.toList() ?: emptyList()
                updateLargeIcon(sbn)
                updateMediaState()
            }
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
        handler.post { updateLiveActivity() }
    }
    
    private fun releaseController() {
        runCatching { activeController?.unregisterCallback(controllerCallback) }
        activeController = null
        activeToken = null
        liveActivityManager.updateMediaState(null)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        // 1. Check if User dismissed OUR notification
        if (sbn.packageName == packageName && sbn.id == NotificationPublisher.BASE_NOTIFICATION_ID) {
            liveActivityManager.clearOtpState()
            // We might want to clear downloads too? The user verified "otp notification".
            // Let's assume swipe means "Stop showing me this", so clearing OTP is safe. 
            // If download is running, it might pop back up? LiveActivityManager prioritizes OTP. 
            // If we clear OTP, and download is active, it might fall back to Download. 
            // If user swiped, they likely want the slot clear. But standard behavior is just removing the current top.
        }

        checkForDownloadRemoval(sbn)
        
        if (sbn.packageName == activeSourcePackage) {
            // Only release if the removed notification IS the media notification
            // Or if we don't track ID yet (shouldn't happen)
            if (activeSourceNotificationId == null || sbn.id == activeSourceNotificationId) {
                releaseController()
                activeSourcePackage = null
                activeSourceNotificationId = null
                handler.postDelayed({ findActiveMedia() }, 300L)
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

