package com.example.livemedia.handlers

import android.app.Notification
import android.content.Context
import android.content.Intent
import android.service.notification.StatusBarNotification

/**
 * Base interface for all Live Activity handlers.
 * Each handler is responsible for one type of live activity (Media, OTP, Download, Torch).
 */
interface LiveActivityHandler {
    
    /**
     * Priority of this handler. Higher values take precedence.
     * Torch = 40, OTP = 30, Download = 20, Media = 10
     */
    val priority: Int
    
    /**
     * Returns the current state as a PublishedState, or null if inactive.
     */
    fun getCurrentState(): LiveActivityState?
    
    /**
     * Called when any notification is posted. Handler should check if it's relevant.
     */
    fun onNotificationPosted(sbn: StatusBarNotification) {}
    
    /**
     * Called when any notification is removed. Handler should check if it's relevant.
     */
    fun onNotificationRemoved(sbn: StatusBarNotification) {}
    
    /**
     * Returns the list of Intent actions this handler wants to receive.
     */
    fun getActionIntentFilters(): List<String> = emptyList()
    
    /**
     * Handles an action intent (e.g., "Copy OTP", "Turn Off Torch").
     * @return true if handled, false otherwise
     */
    fun handleAction(context: Context, action: String, intent: Intent): Boolean = false
    
    /**
     * Creates the notification actions for this handler's state.
     */
    fun createActions(): List<Notification.Action> = emptyList()
    
    /**
     * Called when the service is being destroyed. Clean up resources.
     */
    fun cleanup() {}
}

/**
 * Unified state representation for all live activity types.
 * This replaces the old PublishedState in LiveActivityManager.
 */
data class LiveActivityState(
    val title: String,
    val subtitle: String = "",
    val icon: android.graphics.Bitmap? = null,
    val isPlaying: Boolean = false,
    val chipText: String? = null,
    val sourcePackage: String = "",
    
    // Progress (for media/downloads)
    val duration: Long = 0L,
    val position: Long = 0L,
    val isProgressScaled: Boolean = false,
    
    // Type flags
    val type: LiveActivityType,
    
    // For media: the session token
    val mediaToken: android.media.session.MediaSession.Token? = null,
    
    // Actions to show
    val actions: List<Notification.Action> = emptyList()
)

enum class LiveActivityType {
    MEDIA,
    OTP,
    DOWNLOAD,
    TORCH
}
