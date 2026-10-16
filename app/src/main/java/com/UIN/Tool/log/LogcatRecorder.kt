// app/src/main/java/com/UIN/Tool/log/LogcatRecorder.kt
package com.UIN.Tool.log

import android.os.Build
import com.UIN.Tool.constants.AppConstants as Constants
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 6.2.0 批次37：把 logcat **默认持续落盘**到与 AppLog 同一个目录
 * （`UIN_Tool/logs/uin_tool_logcat_yyyy-MM-dd.log`）。
 *
 * 动机：`AppLog`/`Logger` 只收本应用主动写的内容，而库直接打到 logcat 的内容
 * （sora 的 `AsyncAnalysis` 分析异常、ART/Compose 的报错）并不进文件，是排查时
 * 的盲区。此处默认在进程生命周期内持续抓取，文件与 `uin_tool_yyyy-MM-dd.log`
 * 同目录，轮转（超 `LOG_MAX_SIZE`）与保留（`LOG_RETENTION_DAYS`）策略与
 * `Logger` 一致，因此「清空日志」「7 天过期清理」对它同样生效。
 *
 * 权限：`READ_LOGS` 是 signature|privileged|development 级，普通安装拿不到。
 * 但 **Android 允许进程读自己 UID 的日志行**，故未授权时本记录器依然有效，
 * 只是只覆盖本应用自身的输出 —— 这也正是需要抓取的那部分。
 * API 24+ 用 `logcat --pid=` 让系统侧过滤，避免读取整机日志的开销。
 */
object LogcatRecorder {
    private const val TAG = "LogcatRecorder"
    private const val FILE_PREFIX = "uin_tool_logcat_"

    @Volatile
    private var running = false

    @Volatile
    private var worker: Thread? = null

    @Volatile
    private var process: Process? = null

    /** 幂等启动；已在运行时直接返回。后台守护线程，不阻塞任何 UI。 */
    fun start() {
        if (running) return
        running = true
        worker = Thread({ loop() }, "logcat-recorder").apply {
            isDaemon = true
            priority = Thread.MIN_PRIORITY
            start()
        }
    }

    fun stop() {
        running = false
        process?.destroy()
        worker?.interrupt()
        worker = null
    }

    private fun loop() {
        val dir = File(Constants.LOG_DIR)
        if (!dir.exists() && !dir.mkdirs()) {
            running = false
            return
        }
        cleanOldLogs(dir)

        val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        var writer: BufferedWriter? = null
        var file: File? = null
        var fileDate = ""
        var bytesWritten = 0L

        try {
            // redirectErrorStream(true)：把 stderr 并进 stdout，避免先读 stdout 时
            // 进程因 stderr 缓冲区写满而阻塞的经典死锁。
            val proc = ProcessBuilder(*buildCommand()).redirectErrorStream(true).start()
            process = proc
            val reader = proc.inputStream.bufferedReader()
            while (running) {
                val line = reader.readLine() ?: break
                val today = dateFmt.format(Date())
                if (today != fileDate || bytesWritten >= Constants.LOG_MAX_SIZE) {
                    writer?.flush()
                    writer?.close()
                    writer = null
                    if (today != fileDate && fileDate.isNotEmpty()) {
                        // 换天：旧文件留档，只新开当天文件
                    } else if (today == fileDate && fileDate.isNotEmpty()) {
                        // 同日超限：与 Logger 同款轮转命名
                        val old = file ?: File(dir, "$FILE_PREFIX$fileDate.log")
                        if (old.exists()) {
                            val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                            old.renameTo(File(dir, "${old.nameWithoutExtension}_$ts.log"))
                        }
                    }
                    val target = File(dir, "$FILE_PREFIX$today.log")
                    if (!target.exists()) target.createNewFile()
                    file = target
                    fileDate = today
                    bytesWritten = target.length()
                    writer = target.appendBufferedText()
                }
                writer?.let {
                    it.write(line)
                    it.write("\n")
                    bytesWritten += line.length + 1L
                }
            }
        } catch (e: Exception) {
            // logcat 不可用 / 未授权读不到时，静默降级：不写文件也不反复重试
            android.util.Log.e(TAG, "logcat recorder stopped: ${e.message}")
        } finally {
            try {
                writer?.flush()
                writer?.close()
            } catch (_: Exception) {
            }
            running = false
        }
    }

    /**
     * `logcat --pid=` 为 API 24+ 语法，minSdk 23 需降级；未授权时系统侧过滤同样
     * 只会返回本 UID 的行，故命令本身无需根据权限调整。
     * `-T 1` 表示从当前时刻起跟踪，不回灌历史缓冲区（历史可由日志页实时读取）。
     */
    private fun buildCommand(): Array<String> {
        val cmd = mutableListOf("logcat", "-v", "threadtime")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            cmd += "--pid=${android.os.Process.myPid()}"
        }
        cmd += "-T"
        cmd += "1"
        return cmd.toTypedArray()
    }

    /** 与 [Logger.cleanOldLogs] 同一保留策略，保证两类日志一起过期删除。 */
    private fun cleanOldLogs(dir: File) {
        try {
            val now = System.currentTimeMillis()
            val retention = Constants.LOG_RETENTION_DAYS * 24 * 60 * 60 * 1000L
            dir.listFiles()?.forEach { f ->
                if (f.isFile && f.name.endsWith(".log") && now - f.lastModified() > retention) {
                    f.delete()
                }
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "log cleanup failed: ${e.message}")
        }
    }

    /** 当日落盘文件名，供日志页读取与导出复用。 */
    fun fileForToday(): String =
        FILE_PREFIX + SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()) + ".log"

    private fun File.appendBufferedText(): BufferedWriter =
        FileWriter(this, true).buffered()
}
