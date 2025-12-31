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
}
