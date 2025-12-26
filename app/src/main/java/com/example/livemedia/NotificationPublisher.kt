package com.example.livemedia

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.Icon
import android.media.session.MediaSession
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationCompat

class NotificationPublisher(private val context: Context) {

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private var playbackStartTime = 0L
    private var currentNotificationId = BASE_NOTIFICATION_ID

    companion object {
        // Increment version to apply new channel config
        const val CHANNEL_ID = "live_updates_channel_v4" 
        const val BASE_NOTIFICATION_ID = 1001
    }

    init { createChannel() }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Use IMPORTANCE_DEFAULT to avoid heads-up popups (peeking) but keep status bar icon
            // IMPORTANCE_HIGH = Peek + Sound
            // IMPORTANCE_DEFAULT = Status bar + Sound (we set sound to null) -> No Peek
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
        sourcePackage: String
    ): Int {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(sourcePackage)
        val pendingIntent = launchIntent?.let {
            PendingIntent.getActivity(context, 0, it, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }

        val appName = try {
            context.packageManager.getApplicationLabel(
                context.packageManager.getApplicationInfo(sourcePackage, 0)
            ).toString()
        } catch (e: Exception) { "Music" }

        if (isPlaying && playbackStartTime == 0L) playbackStartTime = SystemClock.elapsedRealtime()
        else if (!isPlaying) playbackStartTime = 0L

        // First word only, add ... if there are more words
        val words = title.trim().split(" ")
        val firstWord = words.firstOrNull() ?: "Playing"
        val chipText = if (words.size > 1) "$firstWord..." else firstWord
        
        val notification = if (Build.VERSION.SDK_INT >= 36) {
            buildAndroid16Notification(title, artist, appName, bitmap, isPlaying, chipText, pendingIntent, actions)
        } else {
            buildLegacyNotification(title, artist, appName, bitmap, isPlaying, pendingIntent, actions)
        }

        if (currentNotificationId != notificationId) {
            notificationManager.cancel(currentNotificationId)
        }
        currentNotificationId = notificationId
        notificationManager.notify(notificationId, notification)
        return notificationId
    }

    private fun buildAndroid16Notification(
        title: String, artist: String, appName: String, bitmap: Bitmap?,
        isPlaying: Boolean, chipText: String, pendingIntent: PendingIntent?,
        actions: List<Notification.Action>
    ): Notification {
        return Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(title)
            .setContentText(artist)
            .setLargeIcon(bitmap)
            .setOngoing(true)
            .setOnlyAlertOnce(true) // Set true to prevent sound/vibration on updates
            .setCategory(Notification.CATEGORY_TRANSPORT)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setShortCriticalText(chipText)
            .apply {
                // Use BigTextStyle for the expandable content
                // Collapsed: Title + Text (Artist)
                // Expanded: BigTitle + BigText (Full artist + App name)
                setStyle(Notification.BigTextStyle()
                    .bigText("$artist • $appName")
                    .setBigContentTitle(title))
                
                extras.putBoolean("android.requestPromotedOngoing", true)
                pendingIntent?.let { setContentIntent(it) }
                
                // Add actions with text labels
                for (action in actions.take(3)) {
                    val actionTitle = action.title?.toString()?.lowercase() ?: ""
                    val label = when {
                        actionTitle.contains("pause") -> "Pause"
                        actionTitle.contains("play") -> "Play"
                        actionTitle.contains("next") -> "Next"
                        actionTitle.contains("previous") || actionTitle.contains("prev") -> "Prev"
                        else -> action.title?.toString() ?: ""
                    }
                    addAction(Notification.Action.Builder(action.getIcon(), label, action.actionIntent).build())
                }
            }
            .build()
    }

    private fun buildLegacyNotification(
        title: String, artist: String, appName: String, bitmap: Bitmap?,
        isPlaying: Boolean, pendingIntent: PendingIntent?, actions: List<Notification.Action>
    ): Notification {
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(title)
            .setContentText(artist)
            .setLargeIcon(bitmap)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setColorized(true)
            .setColor(Color.parseColor("#1DB954"))
            .apply {
                pendingIntent?.let { setContentIntent(it) }
                for (action in actions.take(3)) {
                    val actionTitle = action.title?.toString()?.lowercase() ?: ""
                    val label = when {
                        actionTitle.contains("pause") -> "Pause"
                        actionTitle.contains("play") -> "Play"
                        actionTitle.contains("next") -> "Next"
                        actionTitle.contains("previous") || actionTitle.contains("prev") -> "Prev"
                        else -> action.title?.toString() ?: ""
                    }
                    addAction(NotificationCompat.Action.Builder(action.icon, label, action.actionIntent).build())
                }
            }
            .build()
    }

    fun cancelNotification() {
        playbackStartTime = 0L
        notificationManager.cancel(currentNotificationId)
    }

    fun canPostPromotedNotifications() = Build.VERSION.SDK_INT >= 36 && 
        try { notificationManager.canPostPromotedNotifications() } catch (e: Exception) { false }
}
