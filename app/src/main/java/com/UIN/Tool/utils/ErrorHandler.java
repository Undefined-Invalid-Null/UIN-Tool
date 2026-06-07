package com.UIN.Tool.utils;

import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * 统一的错误处理工具类
 * 
 * 提供统一的错误日志记录、异常处理和用户提示功能
 */
public class ErrorHandler {
    
    private static final String TAG = "ErrorHandler";
    
    /**
     * 记录错误日志（带异常信息）
     * 
     * @param tag 日志标签
     * @param message 错误消息
     * @param throwable 异常对象
     */
    public static void logError(@NonNull String tag, @NonNull String message, @Nullable Throwable throwable) {
        String errorMsg = message + (throwable != null ? ": " + throwable.getMessage() : "");
        Log.e(tag, errorMsg, throwable);
        
        // 同时记录到应用日志文件
        if (throwable != null) {
            LogUtils.e(tag, message, throwable);
        } else {
            LogUtils.e(tag, message);
        }
    }
    
    /**
     * 记录错误日志（无异常信息）
     * 
     * @param tag 日志标签
     * @param message 错误消息
     */
    public static void logError(@NonNull String tag, @NonNull String message) {
        logError(tag, message, null);
    }
    
    /**
     * 记录警告日志
     * 
     * @param tag 日志标签
     * @param message 警告消息
     */
    public static void logWarning(@NonNull String tag, @NonNull String message) {
        Log.w(tag, message);
        LogUtils.w(tag, message);
    }
    
    /**
     * 记录信息日志
     * 
     * @param tag 日志标签
     * @param message 信息消息
     */
    public static void logInfo(@NonNull String tag, @NonNull String message) {
        Log.i(tag, message);
        LogUtils.i(tag, message);
    }
    
    /**
     * 记录调试日志
     * 
     * @param tag 日志标签
     * @param message 调试消息
     */
    public static void logDebug(@NonNull String tag, @NonNull String message) {
        Log.d(tag, message);
        LogUtils.d(tag, message);
    }
    
    /**
     * 处理异常（记录日志并可选显示用户提示）
     * 
     * @param tag 日志标签
     * @param e 异常对象
     * @param userMessage 给用户显示的错误消息（可选）
     * @param showToast 是否显示 Toast 提示
     */
    public static void handleException(@NonNull String tag, @NonNull Exception e, 
                                        @Nullable String userMessage, boolean showToast) {
        logError(tag, userMessage != null ? userMessage : "操作失败", e);
        
        // 这里不直接显示 Toast，因为需要 Context，由调用方处理
        // 只记录日志，Toast 在 UI 层处理
    }
    
    /**
     * 处理异常（简化版）
     * 
     * @param tag 日志标签
     * @param e 异常对象
     * @param userMessage 给用户显示的错误消息
     */
    public static void handleException(@NonNull String tag, @NonNull Exception e, 
                                        @Nullable String userMessage) {
        handleException(tag, e, userMessage, false);
    }
    
    /**
     * 记录方法进入日志
     * 
     * @param tag 日志标签
     * @param methodName 方法名
     */
    public static void logEnter(@NonNull String tag, @NonNull String methodName) {
        String message = "→ " + methodName + "() 进入";
        Log.d(tag, message);
        LogUtils.enter(tag, methodName);
    }
    
    /**
     * 记录方法退出日志
     * 
     * @param tag 日志标签
     * @param methodName 方法名
     * @param startTime 开始时间戳
     */
    public static void logExit(@NonNull String tag, @NonNull String methodName, long startTime) {
        long cost = System.currentTimeMillis() - startTime;
        String message = "← " + methodName + "() 退出, 耗时: " + cost + "ms";
        Log.d(tag, message);
        LogUtils.exit(tag, methodName, startTime);
    }
    
    /**
     * 记录操作成功日志
     * 
     * @param tag 日志标签
     * @param operation 操作名称
     */
    public static void logSuccess(@NonNull String tag, @NonNull String operation) {
        String message = "✓ " + operation + " 成功";
        Log.i(tag, message);
        LogUtils.success(tag, operation);
    }
    
    /**
     * 记录操作失败日志
     * 
     * @param tag 日志标签
     * @param operation 操作名称
     * @param reason 失败原因
     */
    public static void logFailure(@NonNull String tag, @NonNull String operation, @Nullable String reason) {
        String message = "✗ " + operation + " 失败" + (reason != null ? ": " + reason : "");
        Log.w(tag, message);
        LogUtils.w(tag, message);
    }
    
    /**
     * 记录参数日志
     * 
     * @param tag 日志标签
     * @param key 参数名
     * @param value 参数值
     */
    public static void logParam(@NonNull String tag, @NonNull String key, @Nullable Object value) {
        String valueStr = value != null ? value.toString() : "null";
        Log.d(tag, "  📌 " + key + " = " + valueStr);
        LogUtils.param(tag, key, valueStr);
    }
    
    /**
     * 创建 RuntimeException 并记录日志
     * 
     * @param tag 日志标签
     * @param message 错误消息
     * @return RuntimeException 实例
     */
    public static RuntimeException throwRuntimeError(@NonNull String tag, @NonNull String message) {
        logError(tag, message);
        return new RuntimeException(message);
    }
    
    /**
     * 创建 RuntimeException 并记录日志（带原因）
     * 
     * @param tag 日志标签
     * @param message 错误消息
     * @param cause 原因异常
     * @return RuntimeException 实例
     */
    public static RuntimeException throwRuntimeError(@NonNull String tag, @NonNull String message, 
                                                      @Nullable Throwable cause) {
        logError(tag, message, cause);
        return new RuntimeException(message, cause);
    }
    
    /**
     * 安全执行代码块，自动捕获异常并记录
     * 
     * @param tag 日志标签
     * @param operation 操作名称
     * @param block 要执行的代码块
     * @param defaultValue 发生异常时的默认返回值
     * @param <T> 返回值类型
     * @return 执行结果或默认值
     */
    @Nullable
    public static <T> T safeExecute(@NonNull String tag, @NonNull String operation, 
                                      @NonNull SafeCallable<T> block, @Nullable T defaultValue) {
        try {
            return block.call();
        } catch (Exception e) {
            logError(tag, "执行 " + operation + " 失败", e);
            return defaultValue;
        }
    }
    
    /**
     * 安全执行代码块（无返回值）
     * 
     * @param tag 日志标签
     * @param operation 操作名称
     * @param block 要执行的代码块
     * @return 是否执行成功
     */
    public static boolean safeExecuteVoid(@NonNull String tag, @NonNull String operation, 
                                           @NonNull SafeRunnable block) {
        try {
            block.run();
            return true;
        } catch (Exception e) {
            logError(tag, "执行 " + operation + " 失败", e);
            return false;
        }
    }
    
    /**
     * 安全执行代码块接口（有返回值）
     */
    public interface SafeCallable<T> {
        T call() throws Exception;
    }
    
    /**
     * 安全执行代码块接口（无返回值）
     */
    public interface SafeRunnable {
        void run() throws Exception;
    }
}

/**
 * 使用示例：
 * 
 * // 记录错误
 * ErrorHandler.logError("MyClass", "加载数据失败", exception);
 * 
 * // 记录成功
 * ErrorHandler.logSuccess("MyClass", "导入插件");
 * 
 * // 记录参数
 * ErrorHandler.logParam("MyClass", "pluginId", pluginId);
 * 
 * // 安全执行
 * String result = ErrorHandler.safeExecute("MyClass", "解析JSON", 
 *     () -> jsonObject.getString("key"), "默认值");
 * 
 * // 安全执行无返回值
 * boolean success = ErrorHandler.safeExecuteVoid("MyClass", "保存文件",
 *     () -> file.save());
 */