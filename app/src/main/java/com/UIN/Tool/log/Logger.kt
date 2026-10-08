package com.UIN.Tool.log

import com.UIN.Tool.R
import com.UIN.Tool.utils.Str
import com.UIN.Tool.utils.formatFileSize
import android.util.Log
import com.UIN.Tool.BuildConfig
import com.UIN.Tool.constants.AppConstants as Constants
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

object Logger {

    private const val TAG = "Logger"

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

    /**
     * B-68：原先每条日志都「新开 FileWriter → 写一行 → 关闭」，且发生在调用线程（多为主线程），
     * 高频日志时是实打实的主线程磁盘 IO。改为单写线程 + 有界队列：
     * 调用线程只入队（队列满则丢弃并 logcat 一次，绝不阻塞），写线程批量落盘并长持 FileWriter。
     */
    private val logQueue = java.util.concurrent.LinkedBlockingQueue<String>(4096)
    @Volatile
    private var writer: FileWriter? = null
    @Volatile
    private var writerThread: Thread? = null

    // ✅ SimpleDateFormat 非线程安全，多线程写日志用 ThreadLocal 隔离
    private val dateFormat = ThreadLocal.withInitial {
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
    }
    private val logDateFormat = ThreadLocal.withInitial {
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    }
    private val rotateDateFormat = ThreadLocal.withInitial {
        SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
    }

    fun init(logDir: String, level: Level = if (BuildConfig.DEBUG) Level.DEBUG else Level.INFO) {
        if (isInitialized) return
        try {
            currentLevel = level
            val dir = File(logDir)
            if (!dir.exists()) {
                dir.mkdirs()
            }
            val date = logDateFormat.get().format(Date())
            logFile = File(dir, "uin_tool_$date.log")
            if (!logFile!!.exists()) {
                logFile!!.createNewFile()
            }
            isInitialized = true
            ensureWriterThread()
            i(TAG, Str.get(R.string.log_system_initialized_level_level_n, level.name))
            // B-71：日志目录在共享存储，绝对路径（含用户名/包名目录）不再写进日志本身
            i(TAG, Str.get(R.string.log_file_logfile_absolutepath, logFile!!.name))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun setLevel(level: Level) {
        currentLevel = level
        i(TAG, Str.get(R.string.log_level_switched_to_level_name, level.name))
    }

    private fun shouldLog(level: Level): Boolean {
        return level.priority >= currentLevel.priority
    }

    private fun writeToFile(message: String) {
        if (logFile == null) return
        if (!logQueue.offer(message)) {
            // B-68/B-69：队列满说明写线程跟不上，丢弃本条并留下一次性痕迹，绝不阻塞调用线程
            if (droppedCounter.getAndIncrement() == 0) {
                Log.w(TAG, "log queue full, further file lines dropped until queue drains")
            }
        }
        ensureWriterThread()
    }

    private val droppedCounter = java.util.concurrent.atomic.AtomicInteger(0)

    private fun ensureWriterThread() {
        if (writerThread != null) return
        synchronized(this) {
            if (writerThread != null) return
            writerThread = Thread {
                try {
                    val batch = ArrayList<String>(64)
                    while (true) {
                        batch.add(logQueue.take())
                        logQueue.drainTo(batch)
                        writeBatch(batch)
                        batch.clear()
                    }
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                } catch (e: Exception) {
                    // B-69：写线程自身出错至少进 logcat，不再完全静默
                    Log.e(TAG, "log writer thread failed", e)
                }
            }.apply {
                name = "uin-logger-writer"
                isDaemon = true
                start()
            }
        }
    }

    private fun writeBatch(batch: List<String>) {
        val file = logFile ?: return
        try {
            if (file.length() > Constants.LOG_MAX_SIZE) {
                rotateLogFile()
            }
            var w = writer
            if (w == null) {
                w = FileWriter(file, true)
                writer = w
            }
            for (line in batch) {
                w.write("$line\n")
            }
            w.flush()
        } catch (e: Exception) {
            // B-69：不再吞掉异常（至少 logcat 一次），并释放句柄下次重建
            Log.e(TAG, "log write failed: ${e.message}")
            try {
                writer?.close()
            } catch (_: Exception) {
            }
            writer = null
        }
    }

    private fun rotateLogFile() {
        try {
            // B-68：轮转前必须关掉指向旧文件的 writer，否则继续往已 rename 的句柄写
            try {
                writer?.flush()
                writer?.close()
            } catch (_: Exception) {
            }
            writer = null
            val dir = logFile?.parentFile ?: return
            val name = logFile?.nameWithoutExtension ?: return
            val ext = logFile?.extension ?: "log"

            val timestamp = rotateDateFormat.get().format(Date())
            val newFile = File(dir, "${name}_${timestamp}.$ext")
            logFile?.renameTo(newFile)

            logFile = File(dir, "$name.$ext")
            logFile?.createNewFile()

            cleanOldLogs(dir)
        } catch (e: Exception) {
            // B-69：轮转失败不再 printStackTrace 了事，进 logcat 便于日志查看器排障
            Log.e(TAG, "log rotate failed: ${e.message}")
        }
    }

    private fun cleanOldLogs(dir: File) {
        try {
            val currentTime = System.currentTimeMillis()
            val retentionMillis = Constants.LOG_RETENTION_DAYS * 24 * 60 * 60 * 1000L

            dir.listFiles()?.forEach { file ->
                if (file.isFile && file.name.endsWith(".log")) {
                    val lastModified = file.lastModified()
                    if (currentTime - lastModified > retentionMillis) {
                        file.delete()
                        d(TAG, Str.get(R.string.deleting_expired_log_file_name, file.name))
                    }
                }
            }
        } catch (e: Exception) {
            // B-69：清理失败至少进 logcat
            Log.e(TAG, "log cleanup failed: ${e.message}")
        }
    }

    private fun formatMessage(level: Level, tag: String, message: String): String {
        return "${dateFormat.get().format(Date())} [${level.tag}] [$tag] $message"
    }

    private fun write(level: Level, tag: String, message: String) {
        if (!shouldLog(level)) return

        val formatted = formatMessage(level, tag, message)

        when (level) {
            Level.DEBUG -> Log.d(tag, message)
            Level.INFO, Level.SUCCESS, Level.ACTION, Level.PARAM, Level.ENTER, Level.EXIT -> Log.i(tag, message)
            Level.WARN -> Log.w(tag, message)
            Level.ERROR -> Log.e(tag, message)
        }

        if (isInitialized) {
            writeToFile(formatted)
        }
    }

    // ==================== 基础日志方法 ====================

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

    fun enter(tag: String, method: String) = write(Level.ENTER, tag, Str.get(R.string.method_entered, method))

    fun exit(tag: String, method: String, startTime: Long) {
        val cost = System.currentTimeMillis() - startTime
        write(Level.EXIT, tag, Str.get(R.string.method_exited_took_cost_ms, method, cost))
    }

    /**
     * 分隔线 - 用于调试
     */
    fun separator(tag: String, title: String? = null) {
        if (title != null) {
            i(tag, "══════════════════════════════════════════════════")
            i(tag, "  $title")
            i(tag, "──────────────────────────────────────────────────")
        } else {
            i(tag, "══════════════════════════════════════════════════")
        }
    }

    fun file(tag: String, operation: String, file: File) {
        val size = if (file.exists() && file.isFile) {
            Str.get(R.string.size_file_length_formatfilesize, formatFileSize(file.length()))
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

    // ==================== 文件管理 ====================

    fun getLogFile(): File? = logFile

    fun getLogDir(): String = logFile?.parent ?: ""

    fun getAllLogFiles(): List<File> {
        val files = mutableListOf<File>()
        try {
            val dir = logFile?.parentFile
            if (dir != null && dir.exists()) {
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
            // B-70：原先只把 isInitialized 置 false，logFile 仍指向已删文件 →
            // 之后所有日志静默丢失。这里关掉 writer 并重新 init 出新文件。
            try {
                writer?.close()
            } catch (_: Exception) {
            }
            writer = null
            isInitialized = false
            val dir = logFile?.parent ?: Constants.LOG_DIR
            init(dir)
            true
        } catch (e: Exception) {
            Log.e(TAG, "clearAllLogs failed: ${e.message}")
            false
        }
    }

    fun readLog(): String {
        return try {
            logFile?.readText() ?: ""
        } catch (e: Exception) {
            Str.get(R.string.failed_to_read_log_e_message, e.message)
        }
    }

    fun clear(context: android.content.Context) {
        try {
            if (logFile != null && logFile!!.exists()) {
                logFile!!.delete()
            }
            try {
                writer?.close()
            } catch (_: Exception) {
            }
            writer = null
            isInitialized = false
            val logDir = logFile?.parent ?: Constants.LOG_DIR
            init(logDir)
            i(TAG, Str.get(R.string.log_cleared))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}