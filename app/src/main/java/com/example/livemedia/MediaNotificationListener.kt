package com.example.livemedia

import android.content.Context
import com.example.livemedia.handlers.DownloadHandler
import com.example.livemedia.handlers.LiveActivityState
import com.example.livemedia.handlers.LiveActivityType
import com.example.livemedia.handlers.OtpHandler
import com.example.livemedia.handlers.TorchHandler
import com.example.livemedia.media.MediaStateManager
import com.example.livemedia.media.MusicState
import com.example.livemedia.media.MusicProvider
import com.example.livemedia.utils.Logger
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.*
import com.example.livemedia.utils.PillContent 
import com.example.livemedia.utils.providePillText
import com.example.livemedia.utils.getAppName
import com.example.livemedia.lockscreen.LockScreenManager

class MediaNotificationListener : NotificationListenerService() {

    private val logger = Logger("MediaNotificationListener")
    private lateinit var prefs: AppPreferences
    private lateinit var publisher: NotificationPublisher
    
    // Media Manager
    private lateinit var mediaStateManager: MediaStateManager
    private var lastMediaState: MusicState? = null
    
    // Visibility Managers
    private lateinit var lockScreenManager: LockScreenManager
    private var isQsOpen = false
    
    // Handlers
    private lateinit var torchHandler: TorchHandler
    private lateinit var otpHandler: OtpHandler
    private lateinit var downloadHandler: DownloadHandler
    
    private val handler = Handler(Looper.getMainLooper())
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    private var titleStartTime: Long = 0L
    private var lastTitle: String? = null
    
    // Settings (Defaults until AppPreferences is fully integrated for these)
    private val isScrollEnabled = true
    private val pillContent = PillContent.TITLE
    
    private val actionReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context, intent: android.content.Intent?) {
            val action = intent?.action ?: return
            
            // Delegate to handlers
            if (otpHandler.handleAction(context, action, intent)) return
            if (torchHandler.handleAction(context, action, intent)) return
            
            // Handle Media Actions via Manager
            mediaStateManager.handleTransportControl(action)
            
            if (action == "com.example.livemedia.ACTION_REFRESH_MEDIA_UI") {
                logger.info("Refreshing Media UI from Settings")
                updateLiveActivity()
                return
            }

            if (action == NotificationPublisher.ACTION_NOTIFICATION_DISMISSED) {
                // User dismissed notification
                // If media is playing, maybe we shouldn't dismiss?
                // LiveMedia logic: if playing, don't dismiss.
                if (lastMediaState?.isPlaying == true) {
                    // Do nothing or force update
                } else {
                    otpHandler.clearState()
                    // Stop media?
                    // mediaStateManager.noActiveMedia() logic is internal mostly.
                    publisher.cancelNotification()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        logger.info("onCreate")
        
        prefs = AppPreferences(this)
        publisher = NotificationPublisher(this)
        
        // Initialize Visibility Managers
        lockScreenManager = com.example.livemedia.lockscreen.LockScreenManager(
            this,
            deviceLocked = { 
                logger.info("Device Locked -> Update")
                updateLiveActivity() 
            },
            deviceUnlocked = { 
                logger.info("Device Unlocked -> Update")
                updateLiveActivity() 
            }
        )
        
        serviceScope.launch {
            com.example.livemedia.qs.QSStateProvider.isQsOpen.collect { isOpen ->
                isQsOpen = isOpen
                logger.info("QS Open: $isOpen -> Update")
                updateLiveActivity()
            }
        }
        
        // Initialize Media Manager
        mediaStateManager = MediaStateManager(
            context = this,
            onStateUpdated = { state ->
                lastMediaState = state
                updateLiveActivity()
            },
            noActiveMedia = {
                lastMediaState = null
                updateLiveActivity()
            }
        )
        
        // Initialize Handlers
        torchHandler = TorchHandler(this, handler) { updateLiveActivity() }
        otpHandler = OtpHandler(this) { updateLiveActivity() }
        downloadHandler = DownloadHandler(this) { updateLiveActivity() }
        
        // Register Receivers
        val filter = android.content.IntentFilter().apply {
            addAction(OtpHandler.ACTION_COPY_OTP)
            addAction(TorchHandler.ACTION_TURN_OFF_TORCH)
            addAction(MediaStateManager.ACTION_PLAY_PAUSE)
            addAction(MediaStateManager.ACTION_SKIP_TO_NEXT)
            addAction(MediaStateManager.ACTION_SKIP_TO_PREVIOUS)
            addAction(NotificationPublisher.ACTION_NOTIFICATION_DISMISSED)
            addAction("com.example.livemedia.ACTION_REFRESH_MEDIA_UI")
        }
        registerReceiver(actionReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        
        // Ticker loop for progress/scrolling
        serviceScope.launch {
            while (isActive) {
                updateLiveActivity()
                delay(1000L) // Update every second
            }
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        unregisterReceiver(actionReceiver)
        lockScreenManager.unregister()
        torchHandler.cleanup()
        otpHandler.cleanup()
        downloadHandler.cleanup()
        publisher.cancelNotification()
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return
        
        if (prefs.isOtpEnabled) otpHandler.onNotificationPosted(sbn)
        if (prefs.isDownloadEnabled) downloadHandler.onNotificationPosted(sbn)
        
        // Media check
        if (prefs.isMediaEnabled && sbn.notification.extras.containsKey(android.app.Notification.EXTRA_MEDIA_SESSION)) {
             mediaStateManager.maybeUpdateMediaController()
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        // Delegate
        if (prefs.isDownloadEnabled) downloadHandler.onNotificationRemoved(sbn)
        
        // Media manager check
        if (prefs.isMediaEnabled && sbn.packageName == lastMediaState?.packageName) {
             // Maybe it was removed?
             handler.postDelayed({ mediaStateManager.maybeUpdateMediaController() }, 500)
        }
    }
    
    private fun updateLiveActivity() {
        serviceScope.launch(Dispatchers.Main) {
            // Visibility Checks
            // If screen is locked AND lockscreen setting is OFF -> Hide
            if (!lockScreenManager.isScreenUnlocked() && !prefs.showOnLockscreen) {
                logger.info("Screen locked + setting off -> hide")
                publisher.cancelNotification()
                // Do not proceed
                return@launch
            }
            
            if (isQsOpen) {
                logger.info("QS open, hiding notification")
                publisher.cancelNotification()
                return@launch
            }

             // 1. Torch (High Priority)
            if (prefs.isTorchEnabled) {
                val torchState = torchHandler.getCurrentState()
                if (torchState != null) {
                    publishGenericState(torchState)
                    return@launch
                }
            }
            
            // 2. OTP
             if (prefs.isOtpEnabled) {
                val otpState = otpHandler.getCurrentState()
                if (otpState != null) {
                    publishGenericState(otpState)
                    return@launch
                }
            }
            
            // 3. Download
            if (prefs.isDownloadEnabled) {
                val downloadState = downloadHandler.getCurrentState()
                if (downloadState != null) {
                    publishGenericState(downloadState)
                    return@launch
                }
            }
            
            // 4. Media
            if (prefs.isMediaEnabled) {
                val mediaState = lastMediaState
                if (mediaState != null) {
                    publishMediaState(mediaState)
                } else {
                    publisher.cancelNotification()
                }
            } else {
                publisher.cancelNotification()
            }
        }
    }
    
    private fun publishGenericState(state: LiveActivityState) {
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
            isTorch = state.type == LiveActivityType.TORCH,
            smallIconResId = null // Use default logic for non-media
        )
    }
    
    private fun publishMediaState(state: MusicState) {
        
        // Scroll Logic
        if (state.title != lastTitle) {
            lastTitle = state.title
            titleStartTime = System.currentTimeMillis()
        }
        
        val chipText = providePillText(
            title = state.title,
            position = state.position.toInt(),
            duration = state.duration.toInt(),
            isPlaying = state.isPlaying,
            pillContent = PillContent.TITLE,
            isScrollEnabled = isScrollEnabled,
            elapsedTimeMs = System.currentTimeMillis() - titleStartTime
        )
        
        // Resolve Icon
        val musicAppName = packageManager.getAppName(state.packageName).toString()
        val provider = MusicProvider.getByAppName(musicAppName)
        val smallIcon = if (provider != MusicProvider.UNKNOWN) provider.iconRes else null

        // Build Actions
        // We typically need to build actions that point to OUR PendingIntents (which hit BroadcastReceiver -> MediaStateManager)
        // BUT NotificationPublisher takes Notification.Action.
        // If we want custom buttons (Play/Pause), we need to generate them here.
        // LiveMedia NotificationViewModel generates them.
        
        // Build Actions (Only if enabled)
        val actions = mutableListOf<android.app.Notification.Action>()
        if (prefs.showActionButtons) {
             actions.add(createAction("Prev", MediaStateManager.ACTION_SKIP_TO_PREVIOUS))
             actions.add(createAction(if (state.isPlaying) "Pause" else "Play", MediaStateManager.ACTION_PLAY_PAUSE))
             actions.add(createAction("Next", MediaStateManager.ACTION_SKIP_TO_NEXT))
        }
        
        
        
        // Prepare Data based on flags
        val showAlbumArt = prefs.showAlbumArt
        val showArtist = prefs.showArtistName
        val showAlbum = prefs.showAlbumName
        val showProgress = prefs.showProgress
        val showProvider = prefs.showMusicProvider
        
        val finalArtist = if (showArtist) state.artist else ""
        
        val finalBitmap = if (showAlbumArt) state.albumArt else null
        val finalSmallIcon = if (showProvider) smallIcon else null
        val finalDuration = if (showProgress) state.duration else -1L
        val finalPosition = if (showProgress) state.position else -1L

        publisher.updateNotification(
            notificationId = NotificationPublisher.BASE_NOTIFICATION_ID,
            title = state.title,
            artist = finalArtist,
            bitmap = finalBitmap,
            token = null, // We aren't using MediaSession.Token directly for controls anymore? 
                          // Actually Android 13+ wants the token for the media controls to show up on lockscreen properly?
                          // NotificationPublisher needs token? 
                          // LiveMedia-main NotificationViewModel does NOT pass token. It builds a standard notification.
                          // But wait, system media controls need the token?
                          // LiveMedia logic seems to be "Custom Notification" that LOOKS like media, but might not be a MediaStyle notification?
                          // Let's check NotificationViewModel again. It uses setStyle(buildBaseBigTextStyle()).
                          // So it is NOT using MediaStyle? Interesting. Beakan WAS using MediaStyle or at least passing token.
                          // If I want "System Media Controls" I should pass token.
                          // If I want "Live Activity" look, maybe BigTextStyle is okay?
                          // The User's prompt "LiveMedia... uses proper app icons in the chip".
                          // If I use BigTextStyle, I lose the seeking bar on Lockscreen?
                          // LiveMedia's NotificationViewModel passes "progress" to notification builder.
                          // It seems LiveMedia is building a custom notification to emulate dynamic island/live activity style?
                          // I should stick to Beakan's `NotificationPublisher` logic but feed it the new data.
            isPlaying = state.isPlaying,
            actions = actions,
            sourcePackage = state.packageName,
            launchIntent = null, // Publisher handles it
            duration = finalDuration,
            position = finalPosition,
            overrideChipText = chipText,
            skipMediaFilter = true, // We are providing exact actions (Prev, Play, Next)
            smallIconResId = finalSmallIcon
        )
    }
    
    private fun createAction(title: String, action: String): android.app.Notification.Action {
        // Simple helper. Icon is handled by Publisher or we can pass 0/generic here and Publisher fixes it?
        // Publisher.filterMediaActions uses logic to find icons?
        // LiveMedia uses specific icons.
        
        val icon = when(title) {
            "Prev" -> android.R.drawable.ic_media_previous
            "Next" -> android.R.drawable.ic_media_next
            "Play" -> android.R.drawable.ic_media_play
            "Pause" -> android.R.drawable.ic_media_pause
            else -> android.R.drawable.ic_media_play
        }
        
        val intent = android.content.Intent(action).apply {
            setPackage(packageName)
        }
        val pendingIntent = android.app.PendingIntent.getBroadcast(
            this, title.hashCode(), intent, 
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        
        return android.app.Notification.Action.Builder(icon, title, pendingIntent).build()
    }
}
