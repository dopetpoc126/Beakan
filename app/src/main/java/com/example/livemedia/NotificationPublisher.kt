package com.example.livemedia

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.media.session.MediaSession
import android.os.Build
import androidx.core.app.NotificationCompat

/**
 * Handles creation and management of media notifications with live update support.
 */
class NotificationPublisher(private val context: Context) {

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private var currentNotificationId = BASE_NOTIFICATION_ID

    companion object {
        private const val CHANNEL_ID = "beakan_live_updates"
        const val BASE_NOTIFICATION_ID = 1001
        private const val NOTIFICATION_COLOR = "#1DB954"
        private const val CHIP_MAX_LENGTH = 15
        const val ACTION_NOTIFICATION_DISMISSED = "com.example.livemedia.ACTION_NOTIFICATION_DISMISSED"
    }

    init { createChannel() }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel(CHANNEL_ID, "Live Updates", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Active media live updates"
                setSound(null, null)
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                notificationManager.createNotificationChannel(this)
            }
        }
    }

    fun updateNotification(
        notificationId: Int,
        title: String,
        artist: String,
        bitmap: Bitmap?,
        token: MediaSession.Token?,
        isPlaying: Boolean,
        actions: List<Notification.Action>,
        sourcePackage: String,
        launchIntent: PendingIntent?,
        duration: Long = 0L,
        position: Long = 0L,
        overrideChipText: String? = null,
        skipMediaFilter: Boolean = false,
        isOtp: Boolean = false,
        isDownload: Boolean = false,
        isTorch: Boolean = false,
        smallIconResId: Int? = null
    ): Int {
        val pendingIntent = launchIntent ?: createLaunchIntent(sourcePackage)
        val chipText = overrideChipText ?: createChipText(title)
        
        // For OTP/Download/Torch, we pass actions directly. For media, we filter.
        val finalActions = if (skipMediaFilter) actions else filterMediaActions(actions)
        
        val notification = if (Build.VERSION.SDK_INT >= 36) {
            buildAndroid16Notification(title, artist, bitmap, chipText, pendingIntent, finalActions, duration, position, isOtp, isDownload, isTorch, smallIconResId)
        } else {
            buildLegacyNotification(title, artist, bitmap, pendingIntent, finalActions, duration, position, isOtp, isDownload, isTorch, smallIconResId)
        }

        if (currentNotificationId != notificationId) {
            notificationManager.cancel(currentNotificationId)
        }
        currentNotificationId = notificationId
        notificationManager.notify(notificationId, notification)
        return notificationId
    }

    private fun createLaunchIntent(packageName: String): PendingIntent? {
        return context.packageManager.getLaunchIntentForPackage(packageName)?.let {
            PendingIntent.getActivity(
                context, 0, it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }

    private fun createChipText(title: String): String {
        val words = title.trim().split(" ")
        val firstWord = words.firstOrNull() ?: "Playing"
        val sanitized = firstWord.replace(Regex("[^\\p{L}\\p{N}\\s]"), "").take(CHIP_MAX_LENGTH)
        return when {
            sanitized.isBlank() -> "Playing"
            words.size > 1 -> "$sanitized..."
            else -> sanitized
        }
    }

    private fun filterMediaActions(actions: List<Notification.Action>): List<Notification.Action> {
        val mediaKeywords = listOf("pause", "play", "next", "skip", "forward", "prev", "back", "rewind", "track")
        return actions.filter { action ->
            val title = action.title?.toString()?.lowercase() ?: ""
            mediaKeywords.any { title.contains(it) }
        }.take(3)
    }

    private fun getActionLabel(actionTitle: String): String {
        val title = actionTitle.lowercase()
        return when {
            title.contains("pause") -> "Pause"
            title.contains("play") -> "Play"
            title.contains("next") || title.contains("skip") || title.contains("forward") -> "Next"
            title.contains("prev") || title.contains("back") || title.contains("rewind") -> "Prev"
            else -> actionTitle
        }
    }

    private fun buildAndroid16Notification(
        title: String, artist: String, bitmap: Bitmap?,
        chipText: String, pendingIntent: PendingIntent?,
        actions: List<Notification.Action>,
        duration: Long, position: Long,
        isOtp: Boolean,
        isDownload: Boolean,
        isTorch: Boolean,
        smallIconResId: Int?
    ): Notification {
        val max = if (duration > 0) (duration / 1000).toInt() else 100
        val progress = if (duration > 0) (position / 1000).toInt() else 0
        
        // Use static icons
        val iconRes = when {
            isOtp -> android.R.drawable.ic_lock_lock
            isDownload -> {
                 if (chipText.equals("Done", ignoreCase = true) || title.contains("Complete", ignoreCase = true)) {
                     android.R.drawable.stat_sys_download_done 
                 } else {
                     android.R.drawable.stat_sys_download
                 }
            }
            isTorch -> android.R.drawable.ic_lock_idle_charging // Lightning bolt for Torch
            smallIconResId != null -> smallIconResId
            else -> android.R.drawable.ic_media_play
        }
        
        val builder = Notification.Builder(context, CHANNEL_ID)
        builder.setSmallIcon(iconRes)


        return builder
            .setContentTitle(title)
            .setContentText(artist)
            .setLargeIcon(bitmap)
            .setOngoing(true)
            .setDeleteIntent(createDismissIntent())
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_TRANSPORT)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setShortCriticalText(chipText)
            // Hide progress for Torch
            .apply {
                if (!isTorch) {
                     setProgress(max, progress, false)
                }
            }
            .setColor(Color.parseColor(NOTIFICATION_COLOR))
            .apply {
                extras.putBoolean("android.requestPromotedOngoing", true)
                pendingIntent?.let { setContentIntent(it) }
                actions.forEach { action ->
                    // For non-media (OTP/Download/Torch), preserve original label
                    val label = if (isOtp || isDownload || isTorch) {
                        action.title?.toString() ?: ""
                    } else {
                        getActionLabel(action.title?.toString() ?: "")
                    }
                    addAction(Notification.Action.Builder(action.getIcon(), label, action.actionIntent).build())
                }
            }
            .build()
    }

    private fun buildLegacyNotification(
        title: String, artist: String, bitmap: Bitmap?,
        pendingIntent: PendingIntent?, actions: List<Notification.Action>,
        duration: Long, position: Long,
        isOtp: Boolean,
        isDownload: Boolean,
        isTorch: Boolean,
        smallIconResId: Int?
    ): Notification {
        val max = if (duration > 0) (duration / 1000).toInt() else 100
        val progress = if (duration > 0) (position / 1000).toInt() else 0
        
        val iconRes = when {
            isOtp -> android.R.drawable.ic_lock_lock
            isDownload -> {
                 // Check title or we'd need to pass chipText here too, but we can infer from title "Download Complete"
                 if (title.contains("Complete", ignoreCase = true) || title.contains("Done", ignoreCase = true)) {
                     android.R.drawable.stat_sys_download_done 
                 } else {
                     android.R.drawable.stat_sys_download
                 }
            }
            isTorch -> android.R.drawable.ic_lock_idle_charging
            smallIconResId != null -> smallIconResId
            else -> android.R.drawable.ic_media_play
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
        builder.setSmallIcon(iconRes)
            
        return builder
            .setContentTitle(title)
            .setContentText(artist)
            .setLargeIcon(bitmap)
            .setOngoing(true)
            .setDeleteIntent(createDismissIntent()) // Detect when user clears it
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setColorized(true)
            .setColor(Color.parseColor(NOTIFICATION_COLOR))
            .apply {
                if (!isTorch) {
                    setProgress(max, progress, false)
                }
            }
            .apply {
                pendingIntent?.let { setContentIntent(it) }
                actions.forEach { action ->
                    // For non-media (OTP/Download/Torch), preserve original label
                    val label = if (isOtp || isDownload || isTorch) {
                        action.title?.toString() ?: ""
                    } else {
                        getActionLabel(action.title?.toString() ?: "")
                    }
                    addAction(NotificationCompat.Action.Builder(action.icon, label, action.actionIntent).build())
                }
            }
            .build()
    }

    fun cancelNotification() {
        notificationManager.cancel(currentNotificationId)
    }

    private fun createDismissIntent(): PendingIntent {
        val intent = android.content.Intent(ACTION_NOTIFICATION_DISMISSED).apply {
            setPackage(context.packageName)
        }
        return PendingIntent.getBroadcast(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
