package com.example.livemedia

import android.app.Notification
import android.graphics.Bitmap
import android.media.session.MediaSession
import android.os.SystemClock

/**
 * Manages the state for Media Live Activity.
 * 
 * Note: OTP, Download, and Torch are now handled by dedicated handlers in the handlers/ package.
 */
object LiveActivityManager {

    // --- State Definitions ---
    data class MediaState(
        val title: String,
        val artist: String,
        val art: Bitmap?,
        val token: MediaSession.Token?,
        val isPlaying: Boolean,
        val actions: List<Notification.Action>,
        val sourcePackage: String,
        val duration: Long,
        val position: Long,
        val updatedAt: Long = SystemClock.elapsedRealtime()
    )

    // --- Current State ---
    private var mediaState: MediaState? = null

    // --- Updates ---

    fun updateMediaState(state: MediaState?) {
        // Optimization: Avoid churn if identical (Data class equals works)
        if (this.mediaState == state) return
        mediaState = state
    }

    // --- Resolution ---

    fun getBestState(): PublishedState? {
        // Only returns Media state now - OTP/Download/Torch handled by handlers
        mediaState?.let { media ->
            return PublishedState(
                title = media.title,
                artist = media.artist,
                bitmap = media.art,
                token = media.token,
                isPlaying = media.isPlaying,
                actions = media.actions,
                sourcePackage = media.sourcePackage,
                duration = media.duration,
                position = media.position,
                isProgressScaled = false
            )
        }

        return null
    }

    // --- Output DTO ---
    data class PublishedState(
        val title: String,
        val artist: String,
        val bitmap: Bitmap?,
        val token: MediaSession.Token?,
        val isPlaying: Boolean,
        val actions: List<Notification.Action>,
        val sourcePackage: String,
        val duration: Long,
        val position: Long,
        val isProgressScaled: Boolean,
        val chipText: String? = null,
        val isOtp: Boolean = false,
        val isDownload: Boolean = false,
        val isTorch: Boolean = false
    )
}
