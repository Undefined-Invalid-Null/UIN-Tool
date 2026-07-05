package com.UIN.Tool.core.accessibility

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
        Logger.i(TAG, "无障碍服务创建")
    }
    
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event?.let {
            Logger.d(TAG, "无障碍事件: ${it.eventType}")
        }
    }
    
    override fun onInterrupt() {
        Logger.i(TAG, "无障碍服务中断")
    }
    
    override fun onDestroy() {
        instance = null
        super.onDestroy()
        Logger.i(TAG, "无障碍服务销毁")
    }
    
    override fun onServiceConnected() {
        super.onServiceConnected()
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPES_ALL_MASK
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 100
        }
        setServiceInfo(info)
        Logger.success(TAG, "无障碍服务已连接")
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