package com.example.livemedia

import android.content.Context
import android.content.SharedPreferences

class AppPreferences(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("live_media_prefs", Context.MODE_PRIVATE)

    fun getSelectedPackages(): Set<String> {
        return prefs.getStringSet("selected_packages", emptySet()) ?: emptySet()
    }

    fun addPackage(packageName: String) {
        val current = getSelectedPackages().toMutableSet()
        current.add(packageName)
        prefs.edit().putStringSet("selected_packages", current).apply()
    }

    fun removePackage(packageName: String) {
        val current = getSelectedPackages().toMutableSet()
        current.remove(packageName)
        prefs.edit().putStringSet("selected_packages", current).apply()
    }

    fun isPackageSelected(packageName: String): Boolean {
        val selected = getSelectedPackages()
        // If no apps are explicitly selected, allow ALL apps (better first-time UX)
        if (selected.isEmpty()) return true
        return selected.contains(packageName)
    }

    // FEATURE TOGGLES
    var isMediaEnabled: Boolean
        get() = prefs.getBoolean("feature_media_enabled", true)
        set(value) = prefs.edit().putBoolean("feature_media_enabled", value).apply()

    var isDownloadEnabled: Boolean
        get() = prefs.getBoolean("feature_download_enabled", true)
        set(value) = prefs.edit().putBoolean("feature_download_enabled", value).apply()

    var isOtpEnabled: Boolean
        get() = prefs.getBoolean("feature_otp_enabled", true)
        set(value) = prefs.edit().putBoolean("feature_otp_enabled", value).apply()

    var isTorchEnabled: Boolean
        get() = prefs.getBoolean("feature_torch_enabled", true)
        set(value) = prefs.edit().putBoolean("feature_torch_enabled", value).apply()
        
    // VISIBILITY SETTINGS
    var showOnLockscreen: Boolean
        get() = prefs.getBoolean("visibility_lockscreen", true)
        set(value) = prefs.edit().putBoolean("visibility_lockscreen", value).apply()

    // MEDIA DETAILING SETTINGS (From Screenshot)
    var showAlbumArt: Boolean
        get() = prefs.getBoolean("media_show_album_art", true)
        set(value) = prefs.edit().putBoolean("media_show_album_art", value).apply()

    var showArtistName: Boolean
        get() = prefs.getBoolean("media_show_artist_name", true)
        set(value) = prefs.edit().putBoolean("media_show_artist_name", value).apply()

    var showAlbumName: Boolean
        get() = prefs.getBoolean("media_show_album_name", false)
        set(value) = prefs.edit().putBoolean("media_show_album_name", value).apply()

    var showActionButtons: Boolean
        get() = prefs.getBoolean("media_show_action_buttons", true)
        set(value) = prefs.edit().putBoolean("media_show_action_buttons", value).apply()

    var showProgress: Boolean
        get() = prefs.getBoolean("media_show_progress", true)
        set(value) = prefs.edit().putBoolean("media_show_progress", value).apply()

    var showMusicProvider: Boolean
        get() = prefs.getBoolean("media_show_provider", true)
        set(value) = prefs.edit().putBoolean("media_show_provider", value).apply()

    var showTimestamps: Boolean
        get() = prefs.getBoolean("media_show_timestamps", false)
        set(value) = prefs.edit().putBoolean("media_show_timestamps", value).apply()

    var hideOnQs: Boolean
        get() = prefs.getBoolean("media_hide_on_qs", true)
        set(value) = prefs.edit().putBoolean("media_hide_on_qs", value).apply()
}
