// plugin/PluginBackendManager.kt
package com.UIN.Tool.plugin

import android.content.Context
import com.UIN.Tool.domain.model.PluginInfo
import com.UIN.Tool.log.Logger
import com.UIN.Tool.utils.Constants
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import java.io.File
import java.util.concurrent.TimeUnit

object PluginBackendManager {

    private const val TAG = "PluginBackendManager"

    private val runningProcesses = mutableMapOf<String, Process>()
    private val runningPorts = mutableMapOf<String, Int>()
    private val processLocks = mutableMapOf<String, Any>()
    private val startTimes = mutableMapOf<String, Long>()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(2, TimeUnit.SECONDS)
        .readTimeout(2, TimeUnit.SECONDS)
        .build()

    fun isRunning(pluginId: String): Boolean {
        val process = runningProcesses[pluginId]
        return process != null && process.isAlive
    }

    fun getPort(pluginId: String): Int {
        return runningPorts[pluginId] ?: 0
    }

    fun startBackend(context: Context, info: PluginInfo): Boolean {
        if (!info.hasBackend()) {
            Logger.w(TAG, "插件 ${info.pluginId} 没有配置后端")
            return false
        }

        val pluginId = info.pluginId
        synchronized(processLocks.getOrPut(pluginId) { Any() }) {
            if (isRunning(pluginId)) {
                Logger.d(TAG, "后端已在运行: $pluginId")
                return true
            }

            val pluginDir = File(Constants.PLUGIN_DIR, pluginId)
            val entryPath = info.getBackendEntryPath(pluginDir.absolutePath)
            val entryFile = File(entryPath)

            if (!entryFile.exists() && info.backend.lowercase() != "php") {
                Logger.e(TAG, "后端入口文件不存在: $entryPath")
                return false
            }

            val port = if (info.backendPort > 0) info.backendPort else findAvailablePort()
            runningPorts[pluginId] = port

            // ✅ 获取 Python 完整路径
            val interpreter = getPythonPath(context)
            Logger.d(TAG, "🔧 使用 Python: $interpreter")
            Logger.d(TAG, "🔧 是否存在: ${File(interpreter).exists()}")

            // 构建命令
            val command = listOf(interpreter, entryFile.absolutePath)
            Logger.d(TAG, "📝 执行命令: ${command.joinToString(" ")}")
            Logger.d(TAG, "📂 工作目录: ${entryFile.parentFile?.absolutePath}")

            try {
                val processBuilder = ProcessBuilder(command)
                    .directory(entryFile.parentFile ?: pluginDir)
                    .redirectErrorStream(false)

                // ✅ 设置完整的环境变量
                val env = processBuilder.environment()
                val termuxBinDir = "/data/data/${context.packageName}/files/usr/bin"
                
                // 设置 PATH（包含 Termux bin 目录）
                val currentPath = env["PATH"] ?: ""
                env["PATH"] = "$termuxBinDir:/system/bin:/system/xbin:/vendor/bin:$currentPath"
                
                // 设置 Python 相关环境
                env["PYTHONUNBUFFERED"] = "1"
                env["PORT"] = port.toString()
                env["PLUGIN_ID"] = pluginId
                env["PLUGIN_DIR"] = pluginDir.absolutePath
                env["WORK_DIR"] = Constants.WORK_DIR
                env["TERMUX_HOME"] = "/data/data/${context.packageName}/files/home"
                env["TERMUX_PREFIX"] = "/data/data/${context.packageName}/files/usr"

                // 自定义环境变量
                info.backendEnv.forEach { (k, v) -> env[k] = v }

                Logger.d(TAG, "PATH: ${env["PATH"]}")

                val process = processBuilder.start()
                runningProcesses[pluginId] = process
                startTimes[pluginId] = System.currentTimeMillis()

                monitorOutput(pluginId, process)

                val readyTimeout = minOf(info.backendTimeout, 15)
                val ready = waitForReady(port, readyTimeout, info.backendHealthCheck)

                if (ready) {
                    Logger.success(TAG, "✅ 后端启动成功: $pluginId (端口 $port)")
                    return true
                } else {
                    if (process.isAlive) {
                        Logger.w(TAG, "后端进程运行中但健康检查超时，继续使用")
                        return true
                    } else {
                        Logger.e(TAG, "后端进程已退出")
                        process.destroy()
                        runningProcesses.remove(pluginId)
                        runningPorts.remove(pluginId)
                        return false
                    }
                }

            } catch (e: Exception) {
                Logger.e(TAG, "启动后端异常: ${e.message}", e)
                runningProcesses.remove(pluginId)
                runningPorts.remove(pluginId)
                return false
            }
        }
    }

    // ============================================================
    // ✅ 获取 Python 完整路径
    // ============================================================

    private fun getPythonPath(context: Context): String {
        val packageName = context.packageName
        
        // Termux 中 Python 的完整路径
        val termuxPythonPaths = listOf(
            "/data/data/$packageName/files/usr/bin/python",
            "/data/data/$packageName/files/usr/bin/python3",
            "/data/data/$packageName/files/usr/bin/python3.14",
            "/data/data/$packageName/files/usr/bin/python3.13",
            "/data/data/$packageName/files/usr/bin/python3.12",
            "/data/data/$packageName/files/usr/bin/python3.11",
            "/data/data/$packageName/files/usr/bin/python3.10",
        )

        // 1. 尝试 Termux 路径
        for (path in termuxPythonPaths) {
            val file = File(path)
            if (file.exists()) {
                Logger.d(TAG, "✅ 找到 Python: $path")
                return path
            }
        }

        // 2. 尝试使用 which 命令
        try {
            val process = ProcessBuilder("which", "python")
                .redirectErrorStream(true)
                .start()
            val result = process.inputStream.bufferedReader().readText().trim()
            if (result.isNotEmpty()) {
                val file = File(result)
                if (file.exists()) {
                    Logger.d(TAG, "✅ 通过 which 找到: $result")
                    return result
                }
            }
        } catch (_: Exception) { }

        // 3. 尝试使用 which python3
        try {
            val process = ProcessBuilder("which", "python3")
                .redirectErrorStream(true)
                .start()
            val result = process.inputStream.bufferedReader().readText().trim()
            if (result.isNotEmpty()) {
                val file = File(result)
                if (file.exists()) {
                    Logger.d(TAG, "✅ 通过 which python3 找到: $result")
                    return result
                }
            }
        } catch (_: Exception) { }

        // 4. 返回默认
        Logger.w(TAG, "⚠️ 未找到 Python，使用默认: python")
        return "python"
    }

    // ============================================================
    // 停止后端
    // ============================================================

    fun stopBackend(pluginId: String) {
        synchronized(processLocks.getOrPut(pluginId) { Any() }) {
            runningProcesses[pluginId]?.destroy()
            runningProcesses.remove(pluginId)
            runningPorts.remove(pluginId)
            startTimes.remove(pluginId)
            Logger.i(TAG, "🛑 停止后端: $pluginId")
        }
    }

    fun stopAllBackends() {
        runningProcesses.keys.toList().forEach { stopBackend(it) }
    }

    // ============================================================
    // HTTP API 调用
    // ============================================================

    fun callApi(
        pluginId: String,
        path: String,
        method: String = "GET",
        body: String? = null,
        callback: (Boolean, String?) -> Unit
    ) {
        if (!isRunning(pluginId)) {
            callback(false, "后端未运行")
            return
        }

        val port = runningPorts[pluginId] ?: 0
        if (port == 0) {
            callback(false, "端口无效")
            return
        }

        Thread {
            try {
                val url = "http://127.0.0.1:$port$path"
                val builder = Request.Builder().url(url)

                when (method.uppercase()) {
                    "GET" -> builder.get()
                    "POST" -> {
                        val mediaType = "application/json; charset=utf-8".toMediaType()
                        val requestBody = (body ?: "{}").toRequestBody(mediaType)
                        builder.post(requestBody)
                    }
                    else -> builder.get()
                }

                val response = httpClient.newCall(builder.build()).execute()
                val responseBody = response.body?.string()
                response.close()

                if (response.isSuccessful) {
                    callback(true, responseBody)
                } else {
                    callback(false, "HTTP ${response.code}")
                }
            } catch (e: Exception) {
                Logger.e(TAG, "调用API失败: ${e.message}", e)
                callback(false, e.message)
            }
        }.start()
    }

    // ============================================================
    // 私有方法
    // ============================================================

    private fun findAvailablePort(): Int {
        var port = 8000
        while (port < 9000) {
            if (!runningPorts.values.contains(port)) {
                return port
            }
            port++
        }
        return 8000 + runningPorts.size
    }

    private fun monitorOutput(pluginId: String, process: Process) {
        Thread {
            try {
                val reader = process.inputStream.bufferedReader()
                reader.lineSequence().forEach { line ->
                    Logger.d("Backend[$pluginId]", "[stdout] $line")
                }
            } catch (_: Exception) { }
        }.start()

        Thread {
            try {
                val reader = process.errorStream.bufferedReader()
                reader.lineSequence().forEach { line ->
                    Logger.e("Backend[$pluginId]", "[stderr] $line")
                }
            } catch (_: Exception) { }
        }.start()

        Thread {
            try {
                val exitCode = process.waitFor()
                Logger.d("Backend[$pluginId]", "进程结束，退出码: $exitCode")
                val duration = System.currentTimeMillis() - (startTimes[pluginId] ?: System.currentTimeMillis())
                Logger.d("Backend[$pluginId]", "运行时长: ${duration}ms")
                runningProcesses.remove(pluginId)
                runningPorts.remove(pluginId)
                startTimes.remove(pluginId)
            } catch (_: Exception) { }
        }.start()
    }

    private fun waitForReady(port: Int, timeout: Int, healthPath: String): Boolean {
        val startTime = System.currentTimeMillis()
        val timeoutMs = timeout * 1000L

        Logger.d(TAG, "等待服务就绪: http://127.0.0.1:$port$healthPath (超时: ${timeout}s)")

        var attempts = 0
        var lastError = ""

        while (System.currentTimeMillis() - startTime < timeoutMs) {
            attempts++
            try {
                val request = Request.Builder()
                    .url("http://127.0.0.1:$port$healthPath")
                    .head()
                    .build()
                val response = httpClient.newCall(request).execute()
                if (response.isSuccessful) {
                    response.close()
                    Logger.d(TAG, "健康检查成功 (尝试 $attempts 次)")
                    return true
                }
                response.close()
            } catch (e: Exception) {
                lastError = e.message ?: "unknown"
            }

            try {
                Thread.sleep(300)
            } catch (_: InterruptedException) {
                break
            }
        }

        Logger.w(TAG, "健康检查超时 (尝试 $attempts 次): $lastError")
        return false
    }
}