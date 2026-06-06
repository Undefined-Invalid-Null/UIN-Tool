package com.UIN.Tool.service;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.view.accessibility.AccessibilityEvent;

public class UinAccessibilityService extends AccessibilityService {
    
    private static UinAccessibilityService instance;
    
    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
    }
    
    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // 处理无障碍事件
        // 插件可以通过此服务执行自动化操作
    }
    
    @Override
    public void onInterrupt() {
        // 服务被中断时调用
    }
    
    @Override
    public void onDestroy() {
        instance = null;
        super.onDestroy();
    }
    
    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        // 配置无障碍服务
        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPES_ALL_MASK;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        info.notificationTimeout = 100;
        setServiceInfo(info);
    }
    
    public static UinAccessibilityService getInstance() {
        return instance;
    }
    
    public static boolean isRunning() {
        return instance != null;
    }
    
    /**
     * 执行返回操作（使用 AccessibilityService 的方法）
     */
    public void performBack() {
        performGlobalAction(GLOBAL_ACTION_BACK);
    }
    
    /**
     * 执行主页操作
     */
    public void performHome() {
        performGlobalAction(GLOBAL_ACTION_HOME);
    }
    
    /**
     * 执行最近任务操作
     */
    public void performRecents() {
        performGlobalAction(GLOBAL_ACTION_RECENTS);
    }
    
    /**
     * 执行通知栏操作
     */
    public void performNotifications() {
        performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS);
    }
    
    /**
     * 执行快速设置操作
     */
    public void performQuickSettings() {
        performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS);
    }
    
    /**
     * 执行锁屏操作
     */
    public void performLockScreen() {
        performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN);
    }
    
    /**
     * 执行电源对话框操作
     */
    public void performPowerDialog() {
        performGlobalAction(GLOBAL_ACTION_POWER_DIALOG);
    }
    
    /**
     * 执行截图操作（Android 9+）
     */
    public void performTakeScreenshot() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT);
        }
    }
}