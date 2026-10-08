package com.UIN.Tool.accessibility

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
        // B-52：原先每个事件都打一条日志（TYPES_ALL_MASK → 几乎所有 UI 事件都会唤醒本服务并写盘）。
        // 本服务当前无事件消费者，这里保持空实现、只收敛订阅范围。
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
            // B-52：由 TYPES_ALL_MASK 收敛到窗口状态变化，超时 100ms → 5s，降低无谓唤醒
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 5000L
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