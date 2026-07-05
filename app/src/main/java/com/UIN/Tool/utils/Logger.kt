package com.UIN.Tool.utils

import android.util.Log
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

/**
 * 统一日志系统
 * 支持日志级别控制、文件输出、日志轮转
 */
object Logger {

    private const val TAG = "Logger"

    // 日志级别
    enum class Level(val priority: Int, val tag: String) {
        DEBUG(1, "D"),
        INFO(2, "I"),
        SUCCESS(2, "✓"),
        WARN(3, "W"),
        ERROR(4, "E"),
        ACTION(2, "▶"),
        PARAM(2, "📌"),
        ENTER(2, "→"),
        EXIT(2, "←")
    }

    private var logFile: File? = null
    private var isInitialized = false
    private var currentLevel = Level.INFO
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
    private val logDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    /**
     * 初始化日志系统
     */
    fun init(logDir: String, level: Level = Level.INFO) {
        if (isInitialized) return
        try {
            currentLevel = level
            val dir = File(logDir)
            if (!dir.exists()) {
                dir.mkdirs()
            }
            val date = logDateFormat.format(Date())
            logFile = File(dir, "uin_tool_$date.log")
            if (!logFile!!.exists()) {
                logFile!!.createNewFile()
            }
            isInitialized = true
            i(TAG, "日志系统初始化完成，级别: ${level.name}")
            i(TAG, "日志文件: ${logFile!!.absolutePath}")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 设置日志级别
     */
    fun setLevel(level: Level) {
        currentLevel = level
        i(TAG, "日志级别已切换为: ${level.name}")
    }

    /**
     * 检查是否应该记录该级别的日志
     */
    private fun shouldLog(level: Level): Boolean {
        return level.priority >= currentLevel.priority
    }

    /**
     * 写入日志到文件
     */
    private fun writeToFile(message: String) {
        logFile?.let { file ->
            try {
                // 检查文件大小，超过限制则轮转
                if (file.length() > Constants.LOG_MAX_SIZE) {
                    rotateLogFile()
                }
                FileWriter(file, true).use { writer ->
                    writer.write("$message\n")
                }
            } catch (e: Exception) {
                // 忽略写入错误
            }
        }
    }

    /**
     * 日志轮转
     */
    private fun rotateLogFile() {
        try {
            val dir = logFile?.parentFile ?: return
            val name = logFile?.nameWithoutExtension ?: return
            val ext = logFile?.extension ?: "log"

            // 重命名旧文件
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val newFile = File(dir, "${name}_${timestamp}.$ext")
            logFile?.renameTo(newFile)

            // 创建新文件
            logFile = File(dir, "$name.$ext")
            logFile?.createNewFile()

            // 删除超过保留天数的旧日志
            cleanOldLogs(dir)

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 清理过期日志
     */
    private fun cleanOldLogs(dir: File) {
        try {
            val currentTime = System.currentTimeMillis()
            val retentionMillis = Constants.LOG_RETENTION_DAYS * 24 * 60 * 60 * 1000L

            dir.listFiles()?.forEach { file ->
                if (file.isFile && file.name.endsWith(".log")) {
                    val lastModified = file.lastModified()
                    if (currentTime - lastModified > retentionMillis) {
                        file.delete()
                        d(TAG, "删除过期日志: ${file.name}")
                    }
                }
            }
        } catch (e: Exception) {
            // 忽略清理错误
        }
    }

    /**
     * 格式化日志消息
     */
    private fun formatMessage(level: Level, tag: String, message: String): String {
        return "${dateFormat.format(Date())} [${level.tag}] [$tag] $message"
    }

    /**
     * 核心日志方法
     */
    private fun write(level: Level, tag: String, message: String) {
        if (!shouldLog(level)) return

        val formatted = formatMessage(level, tag, message)

        // 输出到Logcat
        when (level) {
            Level.DEBUG -> Log.d(tag, message)
            Level.INFO, Level.SUCCESS, Level.ACTION, Level.PARAM, Level.ENTER, Level.EXIT -> Log.i(tag, message)
            Level.WARN -> Log.w(tag, message)
            Level.ERROR -> Log.e(tag, message)
        }

        // 写入文件
        if (isInitialized) {
            writeToFile(formatted)
        }
    }

    // ==================== 公开API ====================

    fun d(tag: String, message: String) = write(Level.DEBUG, tag, message)

    fun i(tag: String, message: String) = write(Level.INFO, tag, message)

    fun w(tag: String, message: String) = write(Level.WARN, tag, message)

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        write(Level.ERROR, tag, message)
        throwable?.printStackTrace()
        throwable?.let {
            write(Level.ERROR, tag, it.stackTraceToString())
        }
    }

    fun success(tag: String, message: String) = write(Level.SUCCESS, tag, message)

    fun action(tag: String, action: String, target: String) = write(Level.ACTION, tag, "▶ $action: $target")

    fun param(tag: String, key: String, value: Any?) = write(Level.PARAM, tag, "📌 $key = $value")

    fun enter(tag: String, method: String) = write(Level.ENTER, tag, "→ $method() 进入")

    fun exit(tag: String, method: String, startTime: Long) {
        val cost = System.currentTimeMillis() - startTime
        write(Level.EXIT, tag, "← $method() 退出, 耗时: ${cost}ms")
    }

    fun file(tag: String, operation: String, file: File) {
        val size = if (file.exists() && file.isFile) {
            ", 大小: ${file.length().formatFileSize()}"
        } else ""
        i(tag, "📁 $operation: ${file.absolutePath}$size")
    }

    fun detail(tag: String, title: String, content: String) {
        i(tag, "┌────────────────────────────────────────")
        i(tag, "│ $title")
        i(tag, "├────────────────────────────────────────")
        content.lines().forEach { line ->
            i(tag, "│ $line")
        }
        i(tag, "└────────────────────────────────────────")
    }

    fun separator(tag: String, title: String? = null) {
        i(tag, "══════════════════════════════════════════════════")
        if (!title.isNullOrEmpty()) {
            i(tag, "  $title")
            i(tag, "──────────────────────────────────────────────────")
        }
    }

    // ==================== 文件管理 ====================

    fun getLogFile(): File? = logFile

    fun getLogDir(): String = Constants.LOG_DIR

    fun getAllLogFiles(): List<File> {
        val files = mutableListOf<File>()
        try {
            val dir = File(Constants.LOG_DIR)
            if (dir.exists()) {
                dir.listFiles()?.forEach { file ->
                    if (file.isFile && file.name.endsWith(".log")) {
                        files.add(file)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return files
    }

    fun clearAllLogs(): Boolean {
        return try {
            val files = getAllLogFiles()
            files.forEach { it.delete() }
            logFile?.delete()
            isInitialized = false
            true
        } catch (e: Exception) {
            false
        }
    }

    fun readLog(): String {
        return try {
            logFile?.readText() ?: ""
        } catch (e: Exception) {
            "读取日志失败: ${e.message}"
        }
    }

    fun clear(context: android.content.Context) {
        try {
            if (logFile != null && logFile!!.exists()) {
                logFile!!.delete()
            }
            isInitialized = false
            init(Constants.LOG_DIR)
            i(TAG, "日志已清空")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

// ==================== 扩展函数 ====================

/**
 * 文件大小格式化扩展函数
 */
fun Long.formatFileSize(): String {
    return when {
        this < 1024 -> "$this B"
        this < 1024 * 1024 -> String.format("%.2f KB", this / 1024.0)
        else -> String.format("%.2f MB", this / (1024.0 * 1024.0))
    }
}