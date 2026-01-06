package com.example.livemedia.handlers

import android.app.Notification
import android.app.PendingIntent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.service.notification.StatusBarNotification
import com.example.livemedia.OtpExtractor

/**
 * Handler for OTP (One-Time Password) Live Activity.
 * 
 * Responsibilities:
 * - Detect OTP codes from incoming notifications
 * - Manage OTP expiry (30 seconds)
 * - Provide "Copy" action
 * - Report state for the notification
 */
class OtpHandler(
    private val context: Context,
    private val onStateChanged: () -> Unit
) : LiveActivityHandler {
    
    companion object {
        const val ACTION_COPY_OTP = "com.example.livemedia.ACTION_COPY_OTP"
        const val EXTRA_OTP_CODE = "otp_code"
        private const val OTP_EXPIRY_MS = 30_000L  // 30 seconds
    }
    
    override val priority: Int = 30  // Second highest (after Torch)
    
    // OTP State
    private var otpCode: String? = null
    private var otpExpiryTime: Long = 0L
    private var sourcePackage: String = ""
    
    override fun getCurrentState(): LiveActivityState? {
        val code = otpCode ?: return null
        val now = SystemClock.elapsedRealtime()
        
        // Check if OTP has expired
        if (now >= otpExpiryTime) {
            clearState()
            return null
        }
        
        // Calculate remaining time for progress bar
        val remainingMs = otpExpiryTime - now
        val remainingSeconds = (remainingMs / 1000).coerceAtLeast(0)
        
        return LiveActivityState(
            title = "OTP: $code",
            subtitle = "Tap to copy",
            isPlaying = true,  // Show animation
            chipText = code,
            type = LiveActivityType.OTP,
            sourcePackage = sourcePackage,
            duration = OTP_EXPIRY_MS,
            position = OTP_EXPIRY_MS - remainingMs,  // Progress towards expiry
            isProgressScaled = true,
            actions = createActions()
        )
    }
    
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        checkForOtp(sbn)
    }
    
    override fun getActionIntentFilters(): List<String> = listOf(ACTION_COPY_OTP)
    
    override fun handleAction(context: Context, action: String, intent: Intent): Boolean {
        if (action != ACTION_COPY_OTP) return false
        
        val code = intent.getStringExtra(EXTRA_OTP_CODE) ?: return false
        
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("OTP", code)
        clipboard.setPrimaryClip(clip)
        
        // Clear OTP after copying (optional: user might want to see it longer)
        // clearState()
        // onStateChanged()
        
        return true
    }
    
    override fun createActions(): List<Notification.Action> {
        val code = otpCode ?: return emptyList()
        
        val intent = Intent(ACTION_COPY_OTP).apply {
            putExtra(EXTRA_OTP_CODE, code)
            setPackage(context.packageName)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, code.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val action = Notification.Action.Builder(
            android.R.drawable.ic_menu_save,
            "Copy",
            pendingIntent
        ).build()
        
        return listOf(action)
    }
    
    private fun checkForOtp(sbn: StatusBarNotification) {
        val extras = sbn.notification.extras
        
        // Combine Title, Text and BigText for full context
        val sb = StringBuilder()
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)
        if (!title.isNullOrEmpty()) sb.append(title).append(" ")
        
        val textContent = extras.getCharSequence(Notification.EXTRA_TEXT)
        if (!textContent.isNullOrEmpty()) sb.append(textContent).append(" ")
        
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
        if (!bigText.isNullOrEmpty()) sb.append(bigText)
        
        val text = sb.toString()
        
        OtpExtractor.extract(text)?.let { code ->
            otpCode = code
            otpExpiryTime = SystemClock.elapsedRealtime() + OTP_EXPIRY_MS
            sourcePackage = sbn.packageName
            onStateChanged()
        }
    }
    
    fun clearState() {
        otpCode = null
        otpExpiryTime = 0L
        sourcePackage = ""
    }
    
    override fun cleanup() {
        clearState()
    }
}
