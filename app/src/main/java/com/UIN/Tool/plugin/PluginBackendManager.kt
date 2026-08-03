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
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
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
    private val processPids = mutableMapOf<String, Int>()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .proxy(Proxy.NO_PROXY)
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
        if (process != null && process.isAlive) return true
        // other 模式无独立进程，通过端口探测判断
        val port = runningPorts[pluginId] ?: return false
        return if (port > 0) isPortOpen(port) else true
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

            // other 模式：宿主不自动启动后端进程，仅注册端口并轮询就绪
            if (info.isOtherBackend()) {
                return startOtherBackend(info)
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

            val command: List<String>
            val workDir: File
            // 进程被杀/应用被系统回收后，子进程会残留并继续占用端口；启动前清理，
            // 否则新后端会因 "Address already in use" 反复失败。
            cleanupLingeringBackend(info, pluginDir, port)

            if (info.useProotRuntime()) {
                command = buildProotCommand(info, pluginDir, port)
                workDir = pluginDir
            } else {
                val interpreter = getPythonPath(context)
                Logger.d(TAG, "🔧 使用 Python: $interpreter")
                Logger.d(TAG, "🔧 是否存在: ${File(interpreter).exists()}")
                command = listOf(interpreter, entryFile.absolutePath)
                workDir = entryFile.parentFile ?: pluginDir
            }
            Logger.d(TAG, "📝 执行命令: ${command.joinToString(" ")}")
            Logger.d(TAG, "📂 工作目录: ${workDir.absolutePath}")

            try {
                val processBuilder = ProcessBuilder(command)
                    .directory(workDir)
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

                val termuxHomeDir = "/data/data/${context.packageName}/files/home"
                val termuxPrefixDir = "/data/data/${context.packageName}/files/usr"
                env["TERMUX_HOME"] = termuxHomeDir
                env["TERMUX_PREFIX"] = termuxPrefixDir

                // proot 运行时需完整的 Termux 环境变量，否则 proot-distro 会按默认 com.termux
                // 前缀解析容器目录（CONTAINERS_DIR），导致 login 报 "container 'alpine' is not installed"。
                // 还原 restore（经 AppShell/TermuxShellEnvironment）时这些变量已就位，此处补全保持一致。
                if (info.useProotRuntime()) {
                    env["PREFIX"] = termuxPrefixDir
                    env["HOME"] = termuxHomeDir
                    env["TMPDIR"] = "$termuxPrefixDir/tmp"
                    env["TERMUX__PREFIX"] = termuxPrefixDir
                    env["TERMUX__HOME"] = termuxHomeDir
                    env["TERMUX_APP__PACKAGE_NAME"] = context.packageName
                    env["TERMUX_VERSION"] = try {
                        context.packageManager.getPackageInfo(context.packageName, 0).versionName
                    } catch (_: Exception) {
                        null
                    } ?: "1"
                }

                info.backendEnv.forEach { (k, v) -> env[k] = v }

                Logger.d(TAG, "🔧 PATH: ${env["PATH"]}")
                if (info.useProotRuntime()) {
                    Logger.d(TAG, "🔧 proot 环境: PREFIX=${env["PREFIX"]} TERMUX__PREFIX=${env["TERMUX__PREFIX"]} TERMUX_APP__PACKAGE_NAME=${env["TERMUX_APP__PACKAGE_NAME"]} HOME=${env["HOME"]} TMPDIR=${env["TMPDIR"]}")
                }

                Logger.i(TAG, "🚀 启动进程...")
                val process = processBuilder.start()
                runningProcesses[pluginId] = process
                startTimes[pluginId] = System.currentTimeMillis()
                processPids[pluginId] = getProcessPid(process)
                Logger.d(TAG, "✅ 进程已启动, pid: ${processPids[pluginId]}")

                monitorOutput(pluginId, process, info.useProotRuntime())

                // proot 容器运行时冷启动较慢（proot-distro login + proot chroot + 链接转换 + 解释器），
                // 放宽就绪超时（至少 120s）；常规 termux 运行时仍以 backendTimeout 为上限（最多 30s）
                val readyTimeout = if (info.useProotRuntime()) {
                    maxOf(info.backendTimeout, 120)
                } else {
                    minOf(info.backendTimeout, 30)
                }
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
            val pid = processPids[pluginId]
            if (pid != null && pid > 0) {
                killProcessGroup(pid)
            }
            runningProcesses[pluginId]?.destroy()
            runningProcesses.remove(pluginId)
            runningPorts.remove(pluginId)
            startTimes.remove(pluginId)
            processPids.remove(pluginId)
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

    private fun monitorOutput(pluginId: String, process: Process, isProot: Boolean) {
        Thread {
            try {
                val reader = process.inputStream.bufferedReader()
                reader.lineSequence().forEach { line ->
                    // proot 容器内后端输出日志级别提到 INFO，便于定位容器内启动/报错
                    if (isProot) Logger.i("Backend[$pluginId]", "[stdout] $line")
                    else Logger.d("Backend[$pluginId]", "[stdout] $line")
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
                processPids.remove(pluginId)
            } catch (_: Exception) { }
        }.start()
    }

    /**
     * other 模式后端启动：宿主不自动启动后端进程，仅注册端口并轮询 TCP 就绪。
     * backendPort > 0 时轮询端口；backendPort == 0 视为无端口插件，pre-command 会话存活即运行中。
     */
    private fun startOtherBackend(info: PluginInfo): Boolean {
        val pluginId = info.pluginId
        Logger.i(TAG, "🔌 other 模式后端：不自动启动进程，等待 pre-command 启动 $pluginId")

        if (info.backendPort > 0) {
            runningPorts[pluginId] = info.backendPort
            // other 模式 pre-command 可能需等待容器启动，超时放宽到 90s+
            val readyTimeout = maxOf(info.backendTimeout, 90)
            Logger.d(TAG, "⏳ other 模式轮询端口: ${info.backendPort}, 超时: ${readyTimeout}s")
            val ready = waitForPortOpen(info.backendPort, readyTimeout)
            if (ready) {
                Logger.success(TAG, "✅ other 模式后端就绪: $pluginId (端口 ${info.backendPort})")
                return true
            } else {
                Logger.w(TAG, "⚠️ other 模式端口未就绪: $pluginId")
                return false
            }
        } else {
            // 无端口插件：pre-command 进程会话存活即运行中
            runningPorts[pluginId] = 0
            Logger.success(TAG, "✅ other 模式无端口后端已标记运行: $pluginId")
            return true
        }
    }

    /**
     * 构建 proot 容器内后端启动命令：
     *   proot-distro login alpine --bind <pluginDir>:/plugins/<id> --env KEY=VALUE ... -- <interpreter> <entry>
     *
     * 将宿主插件目录绑定到容器内 /plugins/<pluginId>，使入口文件在容器中可见。
     *
     * 关键：proot-distro login 会给容器进程组装一份最小环境（不继承宿主 ProcessBuilder 的 env），
     * 因此后端需要的变量（PORT/PLUGIN_ID/PLUGIN_DIR/WORK_DIR 等）必须经 login 的 --env 参数透传，
     * 否则容器内拿不到端口号，宿主健康检查永远失败。PLUGIN_DIR/WORK_DIR 在容器内需用 /plugins/<id>。
     */
    private fun buildProotCommand(info: PluginInfo, pluginDir: File, port: Int): List<String> {
        val prootBin = "/data/data/com.UIN.Tool/files/usr/bin/proot-distro"
        val entryPath = info.getBackendEntryPath(pluginDir.absolutePath)
        val entryInContainer = entryPath.replace(pluginDir.absolutePath, "/plugins/${info.pluginId}")

        val envFlags = buildList {
            add("--env")
            add("PORT=$port")
            add("--env")
            add("PLUGIN_ID=${info.pluginId}")
            add("--env")
            add("PLUGIN_DIR=/plugins/${info.pluginId}")
            add("--env")
            add("WORK_DIR=/plugins/${info.pluginId}")
            add("--env")
            add("PYTHONUNBUFFERED=1")
            info.backendEnv.forEach { (k, v) ->
                add("--env")
                add("$k=$v")
            }
        }

        val inner: List<String> = when (info.backend.lowercase()) {
            "python" -> listOf("python3", entryInContainer)
            "node" -> listOf("node", entryInContainer)
            "php" -> listOf("php", "-S", "127.0.0.1:${if (info.backendPort > 0) info.backendPort else 8000}", "-t", info.backendPhpDocRoot.ifEmpty { "/plugins/${info.pluginId}" })
            "binary" -> listOf(info.backendBinary.ifEmpty { entryInContainer }) + info.backendArgs
            "deno" -> listOf("deno", "run", "--allow-net", "--allow-read", entryInContainer)
            "ruby" -> listOf("ruby", entryInContainer)
            "perl" -> listOf("perl", entryInContainer)
            "lua" -> listOf("lua", entryInContainer)
            "java" -> {
                if (info.backendJavaJar.isNotEmpty()) {
                    listOf("java", "-jar", "/plugins/${info.pluginId}/${info.backendJavaJar}")
                } else if (info.backendJavaClass.isNotEmpty()) {
                    listOf("java", "-cp", "/plugins/${info.pluginId}", info.backendJavaClass)
                } else {
                    listOf("java", entryInContainer)
                }
            }
            else -> listOf("bash", entryInContainer)
        }

        return listOf(
            prootBin, "login", "alpine",
            "--bind", "${pluginDir.absolutePath}:/plugins/${info.pluginId}",
        ) + envFlags + listOf("--") + inner
    }

    /**
     * 清理上一次进程残留的后端子进程。
     * Android 应用进程被杀后，其派生的 proot/python 子进程不会随之退出（被 init 接管），
     * 仍占用端口，导致下次启动 Address already in use。通过 /proc/<pid>/cmdline 匹配
     * 本插件标识（proot 用 /plugins/<id>，普通用宿主插件目录路径）并 SIGKILL。
     */
    private fun cleanupLingeringBackend(info: PluginInfo, pluginDir: File, port: Int) {
        if (!isPortOpen(port)) return
        val marker = if (info.useProotRuntime()) "/plugins/${info.pluginId}" else pluginDir.absolutePath
        val lingering = findPidsByCmdline(marker).filter { it != android.os.Process.myPid() }
        if (lingering.isEmpty()) return
        Logger.w(TAG, "⚠️ 端口 $port 被残留后端占用 (pid: $lingering)，清理后重启")
        lingering.forEach { pid ->
            try {
                android.system.Os.kill(pid, android.system.OsConstants.SIGKILL)
            } catch (_: Exception) {
            }
        }
        try {
            Thread.sleep(300)
        } catch (_: InterruptedException) {
        }
    }

    private fun findPidsByCmdline(pattern: String): List<Int> {
        val result = mutableListOf<Int>()
        try {
            File("/proc").listFiles()?.forEach { f ->
                val name = f.name
                if (name.isEmpty() || !name.all { it.isDigit() }) return@forEach
                val pid = name.toIntOrNull() ?: return@forEach
                if (pid <= 0) return@forEach
                try {
                    val cmdline = File(f, "cmdline").readText().trimEnd('\u0000')
                    if (cmdline.contains(pattern)) result.add(pid)
                } catch (_: Exception) {
                }
            }
        } catch (_: Exception) {
        }
        return result
    }

    @Suppress("DEPRECATION")
    private fun getProcessPid(process: Process): Int {
        // Android 的 java.lang.Process 未提供 pid() 方法（Java 9+ API），统一通过反射读取
        var cls: Class<*>? = process.javaClass
        while (cls != null) {
            try {
                val field = cls.getDeclaredField("pid")
                field.isAccessible = true
                return field.getInt(process)
            } catch (_: NoSuchFieldException) {
                cls = cls.superclass
            } catch (_: Exception) {
                return -1
            }
        }
        return -1
    }

    private fun killProcessGroup(pid: Int) {
        try {
            // 向整个进程组发送 SIGKILL（负数 pid 表示进程组）
            android.system.Os.kill(-pid, android.system.OsConstants.SIGKILL)
            Logger.d(TAG, "🛑 已向进程组 $pid 发送 SIGKILL")
        } catch (_: Exception) {
            // 进程组不存在时直接杀单个进程
            try {
                android.system.Os.kill(pid, android.system.OsConstants.SIGKILL)
            } catch (_: Exception) {
            }
        }
    }

    private fun waitForReady(port: Int, timeout: Int, healthPath: String): Boolean {
        val startTime = System.currentTimeMillis()
        val timeoutMs = timeout * 1000L

        Logger.d(TAG, "⏳ 等待服务就绪: http://127.0.0.1:$port$healthPath (超时: ${timeout}s)")

        var attempts = 0
        var lastError = ""

        while (System.currentTimeMillis() - startTime < timeoutMs) {
            attempts++
            // 先做一次 TCP 端口探测，避免 OkHttp 对未就绪端口做多余的 HTTP 握手
            if (!isPortOpen(port)) {
                try {
                    Thread.sleep(200)
                } catch (_: InterruptedException) {
                    break
                }
                continue
            }

            try {
                // 用 GET 而非 HEAD：不少后端（如 Python http.server）未实现 do_HEAD，HEAD 会返回 501
                val request = Request.Builder()
                    .url("http://127.0.0.1:$port$healthPath")
                    .get()
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
                Thread.sleep(200)
            } catch (_: InterruptedException) {
                break
            }
        }

        Logger.w(TAG, "❌ 健康检查超时 (尝试 $attempts 次): $lastError")
        return false
    }

    /** 探测 TCP 端口是否已监听。 */
    private fun isPortOpen(port: Int): Boolean {
        if (port <= 0) return false
        return try {
            val socket = Socket()
            socket.connect(InetSocketAddress("127.0.0.1", port), 500)
            socket.close()
            true
        } catch (_: Exception) {
            false
        }
    }

    /** 轮询等待 TCP 端口监听（other 模式就绪判定）。 */
    private fun waitForPortOpen(port: Int, timeout: Int): Boolean {
        val startTime = System.currentTimeMillis()
        val timeoutMs = timeout * 1000L
        Logger.d(TAG, "⏳ 等待端口监听: 127.0.0.1:$port (超时: ${timeout}s)")
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            if (isPortOpen(port)) {
                Logger.success(TAG, "✅ 端口 $port 已监听")
                return true
            }
            try {
                Thread.sleep(200)
            } catch (_: InterruptedException) {
                return false
            }
        }
        Logger.w(TAG, "❌ 端口 $port 监听超时")
        return false
    }
}