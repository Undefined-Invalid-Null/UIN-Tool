package com.UIN.Tool.core.accessibility

import com.UIN.Tool.R
import com.UIN.Tool.utils.Str
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityEvent
import com.UIN.Tool.log.Logger

class UinAccessibilityService : AccessibilityService() {
    
    companion object {
        private const val TAG = "UinAccessibilityService"
        private var instance: UinAccessibilityService? = null
        
        fun getInstance(): UinAccessibilityService? = instance
        fun isRunning(): Boolean = instance != null
    }
    
    override fun onCreate() {
        super.onCreate()
        instance = this
        Logger.i(TAG, Str.get(R.string.accessibility_service_created))
    }
    
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event?.let {
            Logger.d(TAG, Str.get(R.string.accessibility_event_it_eventtype, it.eventType))
        }
    }
    
    override fun onInterrupt() {
        Logger.i(TAG, Str.get(R.string.accessibility_service_interrupted))
    }
    
    override fun onDestroy() {
        instance = null
        super.onDestroy()
        Logger.i(TAG, Str.get(R.string.accessibility_service_destroyed))
    }
    
    override fun onServiceConnected() {
        super.onServiceConnected()
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPES_ALL_MASK
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 100
        }
        setServiceInfo(info)
        Logger.success(TAG, Str.get(R.string.accessibility_service_connected))
    }
    
    fun performBack() {
        performGlobalAction(GLOBAL_ACTION_BACK)
    }
    
    fun performHome() {
        performGlobalAction(GLOBAL_ACTION_HOME)
    }
    
    fun performRecents() {
        performGlobalAction(GLOBAL_ACTION_RECENTS)
    }
    
    fun performNotifications() {
        performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
    }
    
    fun performQuickSettings() {
        performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
    }
    
    fun performLockScreen() {
        performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
    }
    
    fun performPowerDialog() {
        performGlobalAction(GLOBAL_ACTION_POWER_DIALOG)
    }
    
    fun performTakeScreenshot() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)
        }
    }
}