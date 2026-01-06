package com.example.livemedia.handlers

import android.app.Notification
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.service.notification.StatusBarNotification

/**
 * Handler for Download Progress Live Activity.
 * 
 * Responsibilities:
 * - Detect download progress from notifications
 * - Track download completion
 * - Manage download state timeout
 * - Report state for the notification
 */
class DownloadHandler(
    private val context: Context,
    private val onStateChanged: () -> Unit
) : LiveActivityHandler {
    
    companion object {
        private const val STALE_TIMEOUT_MS = 60_000L  // 60 seconds for active downloads
        private const val DONE_TIMEOUT_MS = 5_000L   // 5 seconds for completed downloads
    }
    
    override val priority: Int = 20  // Lower than OTP
    
    // Download State
    private var packageName: String? = null
    private var notificationId: Int = 0
    private var title: String = ""
    private var progressCurrent: Int = 0
    private var progressMax: Int = 0
    private var isDone: Boolean = false
    private var lastUpdated: Long = 0L
    private var sourceActions: List<Notification.Action> = emptyList()
    
    override fun getCurrentState(): LiveActivityState? {
        val pkg = packageName ?: return null
        val now = SystemClock.elapsedRealtime()
        
        // Check timeout
        val timeout = if (isDone) DONE_TIMEOUT_MS else STALE_TIMEOUT_MS
        if (now - lastUpdated > timeout) {
            clearState()
            return null
        }
        
        return if (isDone) {
            LiveActivityState(
                title = "Download Complete",
                subtitle = pkg,
                isPlaying = false,
                chipText = "Done",
                type = LiveActivityType.DOWNLOAD,
                sourcePackage = pkg,
                duration = 100L,
                position = 100L,
                isProgressScaled = true,
                actions = emptyList()
            )
        } else {
            val percent = if (progressMax > 0) (progressCurrent * 100 / progressMax) else 0
            
            // Filter actions: Only show Cancel/Stop, not Pause
            val filteredActions = sourceActions.filter { action ->
                val label = action.title?.toString()?.lowercase() ?: ""
                label.contains("cancel") || label.contains("stop") || label.contains("abort")
            }
            
            LiveActivityState(
                title = title,
                subtitle = "$percent%",
                isPlaying = true,  // Animate
                chipText = "$percent%",
                type = LiveActivityType.DOWNLOAD,
                sourcePackage = pkg,
                duration = progressMax.toLong(),
                position = progressCurrent.toLong(),
                isProgressScaled = true,
                actions = filteredActions
            )
        }
    }
    
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        checkForDownload(sbn)
    }
    
    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) {
            clearState()
            onStateChanged()
        }
    }
    
    private fun checkForDownload(sbn: StatusBarNotification) {
        val extras = sbn.notification.extras
        
        if (extras.containsKey(Notification.EXTRA_PROGRESS)) {
            val max = extras.getInt(Notification.EXTRA_PROGRESS_MAX)
            val indeterminate = extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE)
            
            if (max > 0 && !indeterminate) {
                // Ignore if it looks like a media notification (handled by MediaHandler)
                if (!isMediaNotification(sbn)) {
                    val current = extras.getInt(Notification.EXTRA_PROGRESS)
                    val notifTitle = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: "Downloading..."
                    
                    val actionsArray = sbn.notification.actions
                    val actions = if (actionsArray != null) listOf(*actionsArray) else emptyList()
                    
                    updateState(sbn.packageName, sbn.id, notifTitle, current, max, actions)
                    return
                }
            }
        }
        
        // Check for "Download Complete" or similar text
        val text = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        
        if (text.contains("Download complete", ignoreCase = true) ||
            text.contains("Finished", ignoreCase = true) ||
            text.contains("Done", ignoreCase = true)) {
            
            // Only mark complete if we were tracking this download
            if (sbn.packageName == packageName) {
                markComplete()
            }
            return
        }
        
        // Not a valid download update - if this was our tracked download, try to clear it
        tryClearIfStale(sbn.packageName, sbn.id)
    }
    
    private fun isMediaNotification(sbn: StatusBarNotification): Boolean {
        val extras = sbn.notification.extras
        return extras.containsKey(Notification.EXTRA_MEDIA_SESSION) ||
                extras.getString(Notification.EXTRA_TEMPLATE)?.contains("MediaStyle") == true
    }
    
    private fun updateState(pkg: String, id: Int, notifTitle: String, current: Int, max: Int, actions: List<Notification.Action>) {
        if (max <= 0) return
        
        packageName = pkg
        notificationId = id
        title = notifTitle
        progressCurrent = current
        progressMax = max
        isDone = false
        lastUpdated = SystemClock.elapsedRealtime()
        sourceActions = actions
        
        onStateChanged()
    }
    
    private fun markComplete() {
        isDone = true
        title = "Download Complete"
        lastUpdated = SystemClock.elapsedRealtime()
        onStateChanged()
    }
    
    private fun tryClearIfStale(pkg: String, id: Int) {
        // Only clear if it matches our tracked download and isn't marked as done
        if (pkg == packageName && id == notificationId && !isDone) {
            clearState()
            onStateChanged()
        }
    }
    
    fun clearState() {
        packageName = null
        notificationId = 0
        title = ""
        progressCurrent = 0
        progressMax = 0
        isDone = false
        lastUpdated = 0L
        sourceActions = emptyList()
    }
    
    fun clearForPackage(pkg: String) {
        if (packageName == pkg) {
            clearState()
        }
    }
    
    override fun cleanup() {
        clearState()
    }
}
