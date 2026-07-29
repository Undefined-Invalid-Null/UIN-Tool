package com.UIN.Tool.plugin

import android.content.Context
import com.UIN.Tool.core.plugin.PluginEventBus
import com.UIN.Tool.core.plugin.PluginMessage
import com.UIN.Tool.domain.model.PluginInfo
import com.UIN.Tool.log.Logger
import com.UIN.Tool.utils.Constants
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit

/**
 * 插件后端管理器 - 增强版
 * 同时作为后台消息监听和转发服务
 */
object PluginBackendManager {

    private const val TAG = "PluginBackendManager"
    private const val MAX_MESSAGE_AGE_MS = 60000L
    private const val CLEANUP_INTERVAL_MS = 30000L

    // ==================== 后端进程管理 ====================
    
    private val runningProcesses = mutableMapOf<String, Process>()
    private val runningPorts = mutableMapOf<String, Int>()
    private val processLocks = mutableMapOf<String, Any>()
    private val startTimes = mutableMapOf<String, Long>()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .build()

    // ==================== 消息存储 (内存) ====================
    
    private val messageQueues = ConcurrentHashMap<String, ConcurrentLinkedQueue<StoredMessage>>()
    private val messageListeners = ConcurrentHashMap<String, MutableList<(PluginMessage) -> Unit>>()
    
    private var isMessageServiceRunning = false
    private lateinit var context: Context

    // ==================== 消息存储类 ====================
    
    private data class StoredMessage(
        val sender: String,
        val target: String?,
        val action: String,
        val data: Map<String, Any>?,
        val timestamp: Long = System.currentTimeMillis()
    ) {
        fun toMap(): Map<String, Any> {
            return mapOf(
                "sender" to sender,
                "target" to (target ?: ""),
                "action" to action,
                "data" to (data ?: emptyMap<String, Any>()),
                "timestamp" to timestamp
            )
        }
    }

    // ==================== 初始化 ====================
    
    fun init(context: Context) {
        this.context = context.applicationContext
        if (!isMessageServiceRunning) {
            startMessageService()
        }
    }

    // ==================== 消息服务 ====================
    
    private fun startMessageService() {
        if (isMessageServiceRunning) return
        isMessageServiceRunning = true
        
        Logger.i(TAG, "📡 后台消息服务已启动")
        
        val listener = object : PluginEventBus.PluginMessageListener {
            override fun onMessage(message: PluginMessage) {
                handleIncomingMessage(message)
            }
        }
        PluginEventBus.registerPluginListener("system_backend", listener)
        
        val eventListener = object : PluginEventBus.EventListener {
            override fun onEvent(eventType: String, data: Map<String, Any>?) {
                handleEvent(eventType, data)
            }
        }
        PluginEventBus.register("system_event", eventListener)
        
        startCleanupTask()
        
        Logger.success(TAG, "✅ 后台消息服务已就绪")
    }

    // ==================== 定时清理任务 ====================
    
    private fun startCleanupTask() {
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(object : Runnable {
            override fun run() {
                cleanupExpiredMessages()
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(this, CLEANUP_INTERVAL_MS)
            }
        }, CLEANUP_INTERVAL_MS)
    }

    private fun cleanupExpiredMessages() {
        val now = System.currentTimeMillis()
        var totalRemoved = 0
        
        messageQueues.forEach { (queueKey, queue) ->
            var removed = 0
            val iterator = queue.iterator()
            while (iterator.hasNext()) {
                val msg = iterator.next()
                if (now - msg.timestamp > MAX_MESSAGE_AGE_MS) {
                    iterator.remove()
                    removed++
                }
            }
            if (removed > 0) {
                Logger.d(TAG, "🧹 清理 $queueKey 队列: 移除 $removed 条过期消息")
                totalRemoved += removed
            }
        }
        
        if (totalRemoved > 0) {
            Logger.d(TAG, "🧹 总计清理 $totalRemoved 条过期消息")
        }
    }

    // ==================== 消息处理 ====================
    
    private fun handleIncomingMessage(message: PluginMessage) {
        Logger.d(TAG, "📨 收到消息: ${message.action} from ${message.sender}")
        
        when (message.action) {
            "open_plugin" -> {
                handleOpenPluginCommand(message)
                return
            }
            "call_plugin" -> {
                handleCallPluginCommand(message)
                return
            }
            "get_messages" -> {
                handleGetMessagesCommand(message)
                return
            }
        }
        
        val stored = StoredMessage(
            sender = message.sender,
            target = message.target,
            action = message.action,
            data = message.data
        )
        
        if (message.target != null && message.target.isNotEmpty()) {
            messageQueues.computeIfAbsent(message.target) { ConcurrentLinkedQueue() }.add(stored)
        }
        
        messageQueues.computeIfAbsent("_broadcast_") { ConcurrentLinkedQueue() }.add(stored)
        
        trimQueues()
        
        messageListeners.values.forEach { list ->
            list.forEach { listener ->
                try {
                    listener(message)
                } catch (e: Exception) {
                    Logger.e(TAG, "监听器执行异常", e)
                }
            }
        }
        
        Logger.d(TAG, "📦 消息已存储，队列大小: ${getTotalQueueSize()}")
    }

    private fun handleEvent(eventType: String, data: Map<String, Any>?) {
        Logger.d(TAG, "📡 收到事件: $eventType")
        
        when (eventType) {
            "open_plugin" -> {
                val pluginId = data?.get("pluginId") as? String
                if (pluginId != null) {
                    openPlugin(pluginId)
                }
            }
        }
    }

    // ==================== 命令处理 ====================
    
    private fun handleOpenPluginCommand(message: PluginMessage) {
        val pluginId = message.data?.get("pluginId") as? String
        if (pluginId != null) {
            Logger.i(TAG, "🔓 后台命令: 打开插件 $pluginId")
            openPlugin(pluginId)
            
            PluginEventBus.sendToPlugin(
                message.sender,
                "open_plugin_response",
                mapOf(
                    "success" to true,
                    "pluginId" to pluginId,
                    "message" to "已打开插件 $pluginId"
                )
            )
        } else {
            PluginEventBus.sendToPlugin(
                message.sender,
                "open_plugin_response",
                mapOf(
                    "success" to false,
                    "error" to "缺少 pluginId"
                )
            )
        }
    }

    private fun handleCallPluginCommand(message: PluginMessage) {
        val targetPluginId = message.data?.get("targetPlugin") as? String
        val method = message.data?.get("method") as? String
        val params = message.data?.get("params") as? Map<String, Any>
        
        if (targetPluginId != null && method != null) {
            Logger.i(TAG, "📞 后台命令: 调用 $targetPluginId.$method")
            
            val result = callPluginMethod(targetPluginId, method, params)
            
            PluginEventBus.sendToPlugin(
                message.sender,
                "call_plugin_response",
                mapOf(
                    "success" to true,
                    "targetPlugin" to targetPluginId,
                    "method" to method,
                    "result" to (result ?: "null")
                )
            )
        } else {
            PluginEventBus.sendToPlugin(
                message.sender,
                "call_plugin_response",
                mapOf(
                    "success" to false,
                    "error" to "缺少 targetPlugin 或 method"
                )
            )
        }
    }

    private fun handleGetMessagesCommand(message: PluginMessage) {
        val target = message.data?.get("target") as? String
        val limit = (message.data?.get("limit") as? Number)?.toInt() ?: 10
        
        val messages = getMessages(target, limit)
        
        PluginEventBus.sendToPlugin(
            message.sender,
            "get_messages_response",
            mapOf(
                "success" to true,
                "target" to (target ?: "_broadcast_"),
                "messages" to messages,
                "count" to messages.size
            )
        )
    }

    // ==================== 公开 API ====================

    fun openPlugin(pluginId: String) {
        try {
            val pluginManager = PluginManager.getInstance(context)
            pluginManager.openPlugin(pluginId, context)
            Logger.success(TAG, "✅ 已打开插件: $pluginId")
        } catch (e: Exception) {
            Logger.e(TAG, "❌ 打开插件失败: $pluginId", e)
        }
    }

    fun callPluginMethod(pluginId: String, method: String, params: Map<String, Any>?): Any? {
        return try {
            val pluginManager = PluginManager.getInstance(context)
            val instance = pluginManager.getPluginInstance(pluginId)
            if (instance == null) {
                Logger.w(TAG, "插件实例不存在: $pluginId")
                return null
            }
            
            val bundle = android.os.Bundle().apply {
                params?.forEach { (key, value) ->
                    when (value) {
                        is String -> putString(key, value)
                        is Int -> putInt(key, value)
                        is Long -> putLong(key, value)
                        is Boolean -> putBoolean(key, value)
                        is Double -> putDouble(key, value)
                        is Float -> putFloat(key, value)
                        else -> putString(key, value.toString())
                    }
                }
            }
            
            instance.onHostEvent("plugin_call_$method", bundle)
            Logger.d(TAG, "📞 调用 $pluginId.$method 已发送")
            "success"
            
        } catch (e: Exception) {
            Logger.e(TAG, "❌ 调用插件方法失败: $pluginId.$method", e)
            null
        }
    }

    fun getMessages(pluginId: String?, limit: Int): List<Map<String, Any>> {
        val target = pluginId ?: "_broadcast_"
        val queue = messageQueues[target] ?: return emptyList()
        
        val result = mutableListOf<Map<String, Any>>()
        var count = 0
        for (msg in queue) {
            if (count >= limit) break
            result.add(msg.toMap())
            count++
        }
        return result
    }

    fun clearMessages(pluginId: String) {
        messageQueues[pluginId]?.clear()
        Logger.d(TAG, "🗑️ 已清空消息: $pluginId")
    }

    fun getTotalQueueSize(): Int {
        var total = 0
        messageQueues.values.forEach { total += it.size }
        return total
    }

    fun registerMessageListener(pluginId: String, listener: (PluginMessage) -> Unit) {
        messageListeners.computeIfAbsent(pluginId) { mutableListOf() }.add(listener)
        Logger.d(TAG, "📡 注册监听器: $pluginId")
    }

    fun unregisterMessageListener(pluginId: String, listener: (PluginMessage) -> Unit) {
        messageListeners[pluginId]?.remove(listener)
    }

    fun sendToPlugin(targetPlugin: String, action: String, data: Map<String, Any>? = null) {
        val message = PluginMessage(
            type = "direct",
            action = action,
            sender = "system_backend",
            target = targetPlugin,
            data = data
        )
        PluginEventBus.postMessage(message)
        Logger.d(TAG, "📤 发送消息到 $targetPlugin: $action")
    }

    fun broadcastToAll(action: String, data: Map<String, Any>? = null) {
        val message = PluginMessage(
            type = "broadcast",
            action = action,
            sender = "system_backend",
            target = null,
            data = data
        )
        PluginEventBus.postMessage(message)
        Logger.d(TAG, "📢 广播消息: $action")
    }

    // ==================== 清理 ====================

    private fun trimQueues() {
        val maxSize = 100
        messageQueues.values.forEach { queue ->
            while (queue.size > maxSize) {
                queue.poll()
            }
        }
    }

    fun shutdown() {
        isMessageServiceRunning = false
        messageQueues.clear()
        messageListeners.clear()
        Logger.i(TAG, "⏹️ 后台消息服务已停止")
    }

    // ==================== 核心后端管理方法 ====================
    
    fun isRunning(pluginId: String): Boolean {
        val process = runningProcesses[pluginId]
        return process != null && process.isAlive
    }

    fun getPort(pluginId: String): Int {
        return runningPorts[pluginId] ?: 0
    }

    // ============================================================
    // ✅ startBackend 带详细日志
    // ============================================================

    fun startBackend(context: Context, info: PluginInfo): Boolean {
        Logger.d(TAG, "========================================")
        Logger.d(TAG, "🔍 PluginBackendManager.startBackend() 被调用")
        Logger.d(TAG, "📦 pluginId: ${info.pluginId}")
        Logger.d(TAG, "🐍 backend: ${info.backend}")
        Logger.d(TAG, "📄 backendEntry: ${info.backendEntry}")
        Logger.d(TAG, "🔌 backendPort: ${info.backendPort}")
        Logger.d(TAG, "⏰ 时间: ${System.currentTimeMillis()}")
        
        // 确保消息服务已启动
        if (!isMessageServiceRunning) {
            Logger.d(TAG, "📡 消息服务未运行，初始化...")
            init(context)
        }

        if (!info.hasBackend()) {
            Logger.w(TAG, "❌ 插件 ${info.pluginId} 没有配置后端")
            return false
        }

        val pluginId = info.pluginId
        synchronized(processLocks.getOrPut(pluginId) { Any() }) {
            Logger.d(TAG, "🔒 获取锁成功")
            
            if (isRunning(pluginId)) {
                Logger.d(TAG, "✅ 后端已在运行: $pluginId")
                return true
            }

            val pluginDir = File(Constants.PLUGIN_DIR, pluginId)
            Logger.d(TAG, "📂 插件目录: ${pluginDir.absolutePath}")
            Logger.d(TAG, "📂 目录是否存在: ${pluginDir.exists()}")
            
            if (!pluginDir.exists()) {
                Logger.e(TAG, "❌ 插件目录不存在: ${pluginDir.absolutePath}")
                return false
            }

            val entryPath = info.getBackendEntryPath(pluginDir.absolutePath)
            val entryFile = File(entryPath)
            Logger.d(TAG, "📄 入口路径: $entryPath")
            Logger.d(TAG, "📄 文件是否存在: ${entryFile.exists()}")

            if (!entryFile.exists() && info.backend.lowercase() != "php") {
                Logger.e(TAG, "❌ 后端入口文件不存在: $entryPath")
                return false
            }

            val port = if (info.backendPort > 0) info.backendPort else findAvailablePort()
            runningPorts[pluginId] = port
            Logger.d(TAG, "🔌 分配端口: $port")

            val interpreter = getPythonPath(context)
            Logger.d(TAG, "🔧 使用 Python: $interpreter")
            Logger.d(TAG, "🔧 是否存在: ${File(interpreter).exists()}")

            val command = listOf(interpreter, entryFile.absolutePath)
            Logger.d(TAG, "📝 执行命令: ${command.joinToString(" ")}")
            Logger.d(TAG, "📂 工作目录: ${entryFile.parentFile?.absolutePath}")

            try {
                val processBuilder = ProcessBuilder(command)
                    .directory(entryFile.parentFile ?: pluginDir)
                    .redirectErrorStream(false)

                val env = processBuilder.environment()
                val termuxBinDir = "/data/data/${context.packageName}/files/usr/bin"
                
                val currentPath = env["PATH"] ?: ""
                env["PATH"] = "$termuxBinDir:/system/bin:/system/xbin:/vendor/bin:$currentPath"
                
                env["PYTHONUNBUFFERED"] = "1"
                env["PORT"] = port.toString()
                env["PLUGIN_ID"] = pluginId
                env["PLUGIN_DIR"] = pluginDir.absolutePath
                env["WORK_DIR"] = Constants.WORK_DIR
                env["TERMUX_HOME"] = "/data/data/${context.packageName}/files/home"
                env["TERMUX_PREFIX"] = "/data/data/${context.packageName}/files/usr"

                info.backendEnv.forEach { (k, v) -> env[k] = v }

                Logger.d(TAG, "🔧 PATH: ${env["PATH"]}")

                Logger.i(TAG, "🚀 启动进程...")
                val process = processBuilder.start()
                runningProcesses[pluginId] = process
                startTimes[pluginId] = System.currentTimeMillis()
                Logger.d(TAG, "✅ 进程已启动")

                monitorOutput(pluginId, process)

                val readyTimeout = minOf(info.backendTimeout, 30)
                Logger.d(TAG, "⏳ 开始健康检查，超时: ${readyTimeout}s")
                Logger.d(TAG, "📡 健康检查地址: http://127.0.0.1:$port${info.backendHealthCheck}")
                val ready = waitForReady(port, readyTimeout, info.backendHealthCheck)

                if (ready) {
                    Logger.success(TAG, "✅ 后端启动成功: $pluginId (端口 $port)")
                    return true
                } else {
                    if (process.isAlive) {
                        Logger.w(TAG, "⚠️ 后端进程运行中但健康检查超时，继续使用")
                        return true
                    } else {
                        Logger.e(TAG, "❌ 后端进程已退出")
                        process.destroy()
                        runningProcesses.remove(pluginId)
                        runningPorts.remove(pluginId)
                        return false
                    }
                }

            } catch (e: Exception) {
                Logger.e(TAG, "❌ 启动后端异常: ${e.message}", e)
                runningProcesses.remove(pluginId)
                runningPorts.remove(pluginId)
                return false
            }
        }
    }

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
                    "PUT" -> {
                        val mediaType = "application/json; charset=utf-8".toMediaType()
                        val requestBody = (body ?: "{}").toRequestBody(mediaType)
                        builder.put(requestBody)
                    }
                    "DELETE" -> builder.delete()
                    else -> builder.get()
                }

                val response = httpClient.newCall(builder.build()).execute()
                val responseBody = response.body?.string()
                response.close()

                if (response.isSuccessful) {
                    callback(true, responseBody)
                } else {
                    callback(false, "HTTP ${response.code}: $responseBody")
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

    private fun getPythonPath(context: Context): String {
        val packageName = context.packageName

        val termuxPythonPaths = listOf(
            "/data/data/$packageName/files/usr/bin/python",
            "/data/data/$packageName/files/usr/bin/python3",
            "/data/data/$packageName/files/usr/bin/python3.14",
            "/data/data/$packageName/files/usr/bin/python3.13",
            "/data/data/$packageName/files/usr/bin/python3.12",
            "/data/data/$packageName/files/usr/bin/python3.11",
            "/data/data/$packageName/files/usr/bin/python3.10",
        )

        for (path in termuxPythonPaths) {
            val file = File(path)
            if (file.exists()) {
                Logger.d(TAG, "✅ 找到 Python: $path")
                return path
            }
        }

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

        Logger.w(TAG, "⚠️ 未找到 Python，使用默认: python")
        return "python"
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

        // 等待1秒让进程初始化
        try {
            Logger.d(TAG, "⏳ 等待1秒让进程初始化...")
            Thread.sleep(1000)
        } catch (_: InterruptedException) {
            return false
        }

        Logger.d(TAG, "⏳ 等待服务就绪: http://127.0.0.1:$port$healthPath (超时: ${timeout}s)")

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
                    Logger.d(TAG, "✅ 健康检查成功 (尝试 $attempts 次)")
                    return true
                }
                response.close()
            } catch (e: Exception) {
                lastError = e.message ?: "unknown"
                if (attempts % 5 == 0) {
                    Logger.d(TAG, "📡 健康检查 $attempts 次: $lastError")
                }
            }

            try {
                Thread.sleep(500)
            } catch (_: InterruptedException) {
                break
            }
        }

        Logger.w(TAG, "❌ 健康检查超时 (尝试 $attempts 次): $lastError")
        return false
    }
}