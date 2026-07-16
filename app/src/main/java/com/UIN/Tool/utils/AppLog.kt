// app/src/main/java/com/UIN/Tool/utils/AppLog.kt
package com.UIN.Tool.utils

import com.UIN.Tool.BuildConfig
import com.UIN.Tool.log.Logger

/**
 * 统一日志工具
 * 所有日志通过此对象输出，自动控制调试日志开关
 */
object AppLog {
    
    private const val TAG = "AppLog"
    
    /**
     * 调试日志 - 仅在 Debug 模式下输出
     */
    fun d(tag: String, message: String) {
        if (BuildConfig.DEBUG) {
            Logger.d(tag, message)
        }
    }
    
    /**
     * 信息日志
     */
    fun i(tag: String, message: String) {
        Logger.i(tag, message)
    }
    
    /**
     * 警告日志
     */
    fun w(tag: String, message: String) {
        Logger.w(tag, message)
    }
    
    /**
     * 错误日志
     */
    fun e(tag: String, message: String, throwable: Throwable? = null) {
        Logger.e(tag, message, throwable)
    }
    
    /**
     * 成功日志
     */
    fun success(tag: String, message: String) {
        Logger.success(tag, message)
    }
    
    /**
     * 操作日志
     */
    fun action(tag: String, action: String, target: String) {
        Logger.action(tag, action, target)
    }
    
    /**
     * 参数日志
     */
    fun param(tag: String, key: String, value: Any?) {
        Logger.param(tag, key, value)
    }
    
    /**
     * 进入方法日志
     */
    fun enter(tag: String, method: String) {
        Logger.enter(tag, method)
    }
    
    /**
     * 退出方法日志
     */
    fun exit(tag: String, method: String, startTime: Long) {
        Logger.exit(tag, method, startTime)
    }
    
    /**
     * 分隔线日志
     */
    fun separator(tag: String, title: String? = null) {
        Logger.separator(tag, title)
    }
    
    /**
     * 详细信息日志
     */
    fun detail(tag: String, title: String, content: String) {
        Logger.detail(tag, title, content)
    }
}