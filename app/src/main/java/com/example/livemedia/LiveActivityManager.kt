package com.example.livemedia

import android.app.Notification
import android.graphics.Bitmap
import android.media.session.MediaSession
import android.os.SystemClock

/**
 * Manages the state and priority of different Live Activities (Media, OTP, Downloads).
 * Priority: OTP > Download > Media
 */
/**
 * Manages the state and priority of different Live Activities (Media, OTP, Downloads).
 * Priority: OTP > Download > Media
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

    data class OtpState(
        val code: String,
        val expiryTime: Long, // ElapsedRealtime
        val sourcePackage: String
    )

    data class DownloadState(
        val packageName: String,
        val title: String,
        val progressCurrent: Int,
        val progressMax: Int,
        val notificationId: Int,
        val lastUpdated: Long = SystemClock.elapsedRealtime(),
        val sourceActions: List<Notification.Action> = emptyList()
    )

    // --- Current States ---
    private var mediaState: MediaState? = null
    private var otpState: OtpState? = null
    private var downloadState: DownloadState? = null

    // --- Updates ---

    fun updateMediaState(state: MediaState?) {
        // Optimization: Avoid churn if identical (Data class equals works)
        if (this.mediaState == state) return
        mediaState = state
    }

    fun updateOtpState(code: String, sourcePackage: String) {
        // OTP lasts for 30 seconds
        otpState = OtpState(
            code = code,
            expiryTime = SystemClock.elapsedRealtime() + 30_000L,
            sourcePackage = sourcePackage
        )
    }

    fun clearOtpState() {
        otpState = null
    }

    fun updateDownloadState(pkg: String, title: String, current: Int, max: Int, actions: List<Notification.Action>, notificationId: Int) {
        if (max <= 0) return // Invalid or indeterminate
        
        // Optimization: Check for equality before assignment? 
        // Need to handle 'lastUpdated' carefully. If we just update 'lastUpdated' but progress is same, is it worth it?
        // Let's create the candidate.
        // If progress is same, we might not want to update 'lastUpdated' to keep timeout logic logic?
        // Actually, if download is progressing, 'current' changes. If stalled, 'current' same.
        // If stalled, we update 'lastUpdated' to keep it alive? No, stalled usually means broken.
        // Let's just blindly update for downloads as progress changes frequently.
        downloadState = DownloadState(pkg, title, current, max, notificationId = notificationId, sourceActions = actions)
    }

    fun tryClearDownloadState(pkg: String, notificationId: Int) {
        if (downloadState?.packageName == pkg && downloadState?.notificationId == notificationId) {
            downloadState = null
        }
    }

    fun clearDownloadState(pkg: String) {
        if (downloadState?.packageName == pkg) {
            downloadState = null
        }
    }

    // --- Resolution ---

    fun getBestState(): PublishedState? {
        val now = SystemClock.elapsedRealtime()

        // 1. Check OTP (High Priority)
        otpState?.let { otp ->
            if (now < otp.expiryTime) {
                val remainingMs = otp.expiryTime - now
                // We map this to a "track":
                // Duration = 30s (30000ms)
                // Position = (30000 - remaining) -> Counts UP from 0 to 30s
                // OR
                // Position = remaining -> Counts DOWN from 30s to 0. 
                // Let's do Countdown: Duration 30s, Position = remaining.
                return PublishedState(
                    title = "Security Code",
                    artist = otp.code, // The code allows it to be readable
                    bitmap = null, // Use the source app icon (Not available)
                    token = null,
                    isPlaying = true, // Keeps the progress bar moving if UI supports it
                    actions = emptyList(), // Handled by listener injection for Copy
                    sourcePackage = otp.sourcePackage,
                    duration = 30_000L,
                    position = remainingMs, // Show countdown
                    isProgressScaled = false, // It's real time
                    chipText = otp.code,
                    isOtp = true // Flag to tell listener to inject Copy
                )
            } else {
                otpState = null // Expired
            }
        }

        // 2. Check Download (Medium Priority)
        downloadState?.let { download ->
            // Assume download is stale if no update in 1 minute? Optional.
            if (now - download.lastUpdated < 60_000L) {
                 // Map to "track":
                 // Duration = 10000L (Arbitrary "10s" scale for smooth bar)
                 // Position = (current / max) * 10000L
                val scaleFactor = 100_000L // 100 seconds scale for high precision
                val percentage = if (download.progressMax > 0) (download.progressCurrent * 100 / download.progressMax) else 0
                val progress = if (download.progressMax > 0) {
                    (download.progressCurrent.toFloat() / download.progressMax.toFloat() * scaleFactor).toLong()
                } else 0L

                // Find Cancel Action only
                val cancelAction = download.sourceActions.find { 
                    val title = it.title?.toString()?.lowercase() ?: ""
                    title.contains("cancel") || title.contains("stop") || title.contains("abort")
                }
                
                val actions = listOfNotNull(cancelAction)

                return PublishedState(
                    title = "Downloading...",
                    artist = download.title, // Keep title as artist
                    bitmap = null, 
                    token = null,
                    isPlaying = true, 
                    actions = actions,
                    sourcePackage = download.packageName,
                    duration = scaleFactor,
                    position = progress,
                    isProgressScaled = true,
                    chipText = "$percentage%",
                    isDownload = true
                )
            } else {
                downloadState = null // Stale
            }
        }

        // 3. Fallback to Media (Low Priority)
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
        val isDownload: Boolean = false
    )
}
