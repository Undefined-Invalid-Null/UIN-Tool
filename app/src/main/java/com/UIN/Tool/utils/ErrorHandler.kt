// app/src/main/java/com/UIN/Tool/utils/ErrorHandler.kt
package com.UIN.Tool.utils

import android.util.Log
import java.io.PrintWriter
import java.io.StringWriter

/**
 * 统一的错误处理工具类
 * 
 * 提供统一的错误日志记录、异常处理和用户提示功能
 */
object ErrorHandler {
    
    private const val TAG = "ErrorHandler"
    
    /**
     * 记录错误日志（带异常信息）
     * 
     * @param tag 日志标签
     * @param message 错误消息
     * @param throwable 异常对象
     */
    @JvmStatic
    fun logError(tag: String, message: String, throwable: Throwable? = null) {
        val errorMsg = message + (throwable?.let { ": ${it.message}" } ?: "")
        Log.e(tag, errorMsg, throwable)
        
        if (throwable != null) {
            Logger.e(tag, message, throwable)
        } else {
            Logger.e(tag, message)
        }
    }
    
    /**
     * 记录警告日志
     */
    @JvmStatic
    fun logWarning(tag: String, message: String) {
        Log.w(tag, message)
        Logger.w(tag, message)
    }
    
    /**
     * 记录信息日志
     */
    @JvmStatic
    fun logInfo(tag: String, message: String) {
        Log.i(tag, message)
        Logger.i(tag, message)
    }
    
    /**
     * 记录调试日志
     */
    @JvmStatic
    fun logDebug(tag: String, message: String) {
        Log.d(tag, message)
        Logger.d(tag, message)
    }
    
    /**
     * 处理异常
     */
    @JvmStatic
    fun handleException(tag: String, e: Exception, userMessage: String? = null) {
        logError(tag, userMessage ?: "操作失败", e)
    }
    
    /**
     * 记录方法进入日志
     */
    @JvmStatic
    fun logEnter(tag: String, methodName: String) {
        Log.d(tag, "→ $methodName() 进入")
        Logger.enter(tag, methodName)
    }
    
    /**
     * 记录方法退出日志
     */
    @JvmStatic
    fun logExit(tag: String, methodName: String, startTime: Long) {
        val cost = System.currentTimeMillis() - startTime
        Log.d(tag, "← $methodName() 退出, 耗时: ${cost}ms")
        Logger.exit(tag, methodName, startTime)
    }
    
    /**
     * 记录操作成功日志
     */
    @JvmStatic
    fun logSuccess(tag: String, operation: String) {
        Log.i(tag, "✓ $operation 成功")
        Logger.success(tag, operation)
    }
    
    /**
     * 记录操作失败日志
     */
    @JvmStatic
    fun logFailure(tag: String, operation: String, reason: String? = null) {
        val message = "✗ $operation 失败" + (reason?.let { ": $it" } ?: "")
        Log.w(tag, message)
        Logger.w(tag, message)
    }
    
    /**
     * 记录参数日志
     */
    @JvmStatic
    fun logParam(tag: String, key: String, value: Any?) {
        val valueStr = value?.toString() ?: "null"
        Log.d(tag, "  📌 $key = $valueStr")
        Logger.param(tag, key, valueStr)
    }
    
    /**
     * 安全执行代码块（有返回值）
     */
    @JvmStatic
    fun <T> safeExecute(tag: String, operation: String, block: () -> T, defaultValue: T? = null): T? {
        return try {
            block()
        } catch (e: Exception) {
            logError(tag, "执行 $operation 失败", e)
            defaultValue
        }
    }
    
    /**
     * 安全执行代码块（无返回值）
     */
    @JvmStatic
    fun safeExecuteVoid(tag: String, operation: String, block: () -> Unit): Boolean {
        return try {
            block()
            true
        } catch (e: Exception) {
            logError(tag, "执行 $operation 失败", e)
            false
        }
    }
}