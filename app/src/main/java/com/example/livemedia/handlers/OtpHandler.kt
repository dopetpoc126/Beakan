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
        android.util.Log.d("OtpHandler", "=== Checking notification from: ${sbn.packageName} ===")
        
        val notification = sbn.notification
        val extras = notification.extras
        
        // Log all available extras for debugging
        android.util.Log.d("OtpHandler", "Available extras: ${extras.keySet().joinToString()}")
        
        // Build comprehensive text from ALL possible sources
        val textParts = mutableListOf<String>()
        
        // 1. Standard notification extras
        extras.getCharSequence(android.app.Notification.EXTRA_TITLE)?.let {
            textParts.add(it.toString())
            android.util.Log.d("OtpHandler", "EXTRA_TITLE: $it")
        }
        
        extras.getCharSequence(android.app.Notification.EXTRA_TEXT)?.let {
            textParts.add(it.toString())
            android.util.Log.d("OtpHandler", "EXTRA_TEXT: $it")
        }
        
        extras.getCharSequence(android.app.Notification.EXTRA_BIG_TEXT)?.let {
            textParts.add(it.toString())
            android.util.Log.d("OtpHandler", "EXTRA_BIG_TEXT: $it")
        }
        
        extras.getCharSequence(android.app.Notification.EXTRA_SUB_TEXT)?.let {
            textParts.add(it.toString())
            android.util.Log.d("OtpHandler", "EXTRA_SUB_TEXT: $it")
        }
        
        extras.getCharSequence(android.app.Notification.EXTRA_INFO_TEXT)?.let {
            textParts.add(it.toString())
            android.util.Log.d("OtpHandler", "EXTRA_INFO_TEXT: $it")
        }
        
        extras.getCharSequence(android.app.Notification.EXTRA_SUMMARY_TEXT)?.let {
            textParts.add(it.toString())
            android.util.Log.d("OtpHandler", "EXTRA_SUMMARY_TEXT: $it")
        }
        
        // 2. MessagingStyle (Google Messages, WhatsApp, etc.)
        @Suppress("DEPRECATION")
        val messages = extras.getParcelableArray(android.app.Notification.EXTRA_MESSAGES)
        if (messages != null) {
            android.util.Log.d("OtpHandler", "Found EXTRA_MESSAGES with ${messages.size} messages")
            for (msg in messages) {
                if (msg is android.os.Bundle) {
                    msg.getCharSequence("text")?.let {
                        textParts.add(it.toString())
                        android.util.Log.d("OtpHandler", "Message text: $it")
                    }
                }
            }
        }
        
        // 3. InboxStyle text lines
        val textLines = extras.getCharSequenceArray(android.app.Notification.EXTRA_TEXT_LINES)
        if (textLines != null) {
            android.util.Log.d("OtpHandler", "Found EXTRA_TEXT_LINES with ${textLines.size} lines")
            for (line in textLines) {
                line?.let {
                    textParts.add(it.toString())
                    android.util.Log.d("OtpHandler", "Text line: $it")
                }
            }
        }
        
        // 4. Ticker text (legacy but sometimes contains OTP)
        notification.tickerText?.let {
            textParts.add(it.toString())
            android.util.Log.d("OtpHandler", "Ticker: $it")
        }
        
        // Combine all text
        val combinedText = textParts.joinToString(" ")
        android.util.Log.d("OtpHandler", "Combined text for extraction: $combinedText")
        
        if (combinedText.isBlank()) {
            android.util.Log.w("OtpHandler", "No text found in notification!")
            return
        }
        
        // Try to extract OTP
        val extractedCode = OtpExtractor.extract(combinedText)
        android.util.Log.d("OtpHandler", "Extracted OTP result: $extractedCode")
        
        if (extractedCode != null) {
            otpCode = extractedCode
            otpExpiryTime = SystemClock.elapsedRealtime() + OTP_EXPIRY_MS
            sourcePackage = sbn.packageName
            android.util.Log.i("OtpHandler", "✓ OTP detected and set: $otpCode")
            onStateChanged()
        } else {
            android.util.Log.d("OtpHandler", "✗ No OTP found in this notification")
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
