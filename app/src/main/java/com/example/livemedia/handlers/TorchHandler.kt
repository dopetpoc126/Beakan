package com.example.livemedia.handlers

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.os.Handler

/**
 * Handler for Torch/Flashlight Live Activity.
 * 
 * Responsibilities:
 * - Listen to torch state changes via CameraManager.TorchCallback
 * - Provide "Turn Off" action
 * - Report state for the notification
 */
class TorchHandler(
    private val context: Context,
    private val handler: Handler,
    private val onStateChanged: () -> Unit
) : LiveActivityHandler {
    
    companion object {
        const val ACTION_TURN_OFF_TORCH = "com.example.livemedia.ACTION_TURN_OFF_TORCH"
    }
    
    override val priority: Int = 40  // Highest priority
    
    private val cameraManager: CameraManager = 
        context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    
    private var isOn: Boolean = false
    private var activeCameraId: String? = null
    
    private val torchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            super.onTorchModeChanged(cameraId, enabled)
            if (enabled) {
                activeCameraId = cameraId
            }
            isOn = enabled
            onStateChanged()
        }
    }
    
    init {
        cameraManager.registerTorchCallback(torchCallback, handler)
    }
    
    override fun getCurrentState(): LiveActivityState? {
        if (!isOn) return null
        
        return LiveActivityState(
            title = "Torch",
            subtitle = "Flashlight is on",
            isPlaying = true,  // Animation indicator
            chipText = "Torch",
            type = LiveActivityType.TORCH,
            actions = createActions()
        )
    }
    
    override fun getActionIntentFilters(): List<String> = listOf(ACTION_TURN_OFF_TORCH)
    
    override fun handleAction(context: Context, action: String, intent: Intent): Boolean {
        if (action != ACTION_TURN_OFF_TORCH) return false
        
        activeCameraId?.let { id ->
            try {
                cameraManager.setTorchMode(id, false)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return true
    }
    
    override fun createActions(): List<Notification.Action> {
        val intent = Intent(ACTION_TURN_OFF_TORCH).apply {
            setPackage(context.packageName)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val action = Notification.Action.Builder(
            android.R.drawable.ic_lock_power_off,
            "Turn Off",
            pendingIntent
        ).build()
        
        return listOf(action)
    }
    
    override fun cleanup() {
        cameraManager.unregisterTorchCallback(torchCallback)
    }
}
