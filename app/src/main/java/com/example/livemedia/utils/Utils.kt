package com.example.livemedia.utils

import android.content.pm.PackageManager
import android.util.Log

class Logger(private val tag: String) {
    fun info(message: String) {
        Log.i(tag, message)
    }
    
    fun error(message: String, throwable: Throwable? = null) {
        Log.e(tag, message, throwable)
    }
}

fun PackageManager.getAppName(pkg: String): CharSequence {
    return try {
        getApplicationLabel(getApplicationInfo(pkg, 0))
    } catch (e: Exception) {
        "Unknown App"
    }
}

// Helper functions ported from LiveMedia-main

private const val MAX_LENGTH = 70
const val EMPTY_ALBUM = "Unknown Album"
const val EMPTY_ARTIST = "Unknown Artist"

enum class PillContent {
    TITLE, ELAPSED, REMAINING
}

fun buildArtisAlbumTitle(
    showArtistName: Boolean,
    showAlbumName: Boolean,
    musicState: com.example.livemedia.media.MusicState
): String {
    val parts = mutableListOf<String>()

    val showArtist =
        showArtistName && musicState.artist.isNotBlank() && musicState.artist != EMPTY_ARTIST
    val showAlbum =
        showAlbumName && musicState.albumName.isNotBlank() && musicState.albumName != EMPTY_ALBUM

    if (showArtist) {
        parts.add(musicState.artist)
    }

    if (showAlbum) {
        parts.add(musicState.albumName)
    }

    val result = parts.joinToString(" - ")

    return if (result.length > MAX_LENGTH) {
        result.take(MAX_LENGTH) + "..."
    } else {
        result
    }
}

fun formatMusicProgress(currentPosition: Int, duration: Int): String {
    val positionStr = formatTime(currentPosition)
    val durationStr = formatTime(duration)
    return "$positionStr / $durationStr"
}

fun formatTime(millis: Int): String {
    if (millis <= 0) return "0:00"

    val totalSeconds = millis / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60

    return if (hours > 0) {
        // Format: H:MM:SS
        java.lang.String.format(java.util.Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        // Format: M:SS
        java.lang.String.format(java.util.Locale.US, "%d:%02d", minutes, seconds)
    }
}

fun combineProviderAndTimestamp(
    musicProvider: String,
    showMusicProvider: Boolean,
    showTimestamp: Boolean,
    position: Int,
    duration: Int
) = buildList {
    if (showMusicProvider) add(musicProvider)
    if (showTimestamp) add(formatMusicProgress(position, duration))
}.joinToString(" • ").ifBlank { null }

fun providePillText(
    title: String,
    position: Int,
    duration: Int,
    isPlaying: Boolean,
    pillContent: PillContent,
    isScrollEnabled: Boolean,
    elapsedTimeMs: Long
): String {
    val showTime = isPlaying && duration > 0

    if (pillContent == PillContent.ELAPSED && showTime) return formatTime(position)
    if (pillContent == PillContent.REMAINING && showTime) return formatTime(duration - position)

    // TITLE mode or time not available (or paused)
    val trimmedTitle = title.trim()
    if (!isScrollEnabled || trimmedTitle.length <= 7) return trimmedTitle.take(7)

    return provideScrollableText(trimmedTitle, elapsedTimeMs)
}

private fun provideScrollableText(title: String, elapsedTimeMs: Long): String {
    val speedMs = 500 // Scroll every 500ms
    val waitAtStartSteps = 2 
    val waitAtEndSteps = 2 
    
    val scrollRange = title.length - 7
    val cycleSteps = waitAtStartSteps + scrollRange + waitAtEndSteps
    
    if (cycleSteps <= 0) return title.take(7) // Safety check

    val totalSteps = elapsedTimeMs / speedMs
    val stepInCycle = (totalSteps % cycleSteps).toInt()
    
    val offset = when {
        stepInCycle < waitAtStartSteps -> 0
        stepInCycle < waitAtStartSteps + scrollRange -> stepInCycle - waitAtStartSteps
        else -> scrollRange
    }
    
    return title.substring(offset, (offset + 7).coerceAtMost(title.length))
}
