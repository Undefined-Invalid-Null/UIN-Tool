package com.UIN.Tool.plugin

import com.UIN.Tool.R
import com.UIN.Tool.utils.Str
import android.content.Context
import com.UIN.Tool.core.plugin.PluginEventBus
import com.UIN.Tool.core.plugin.PluginMessage
import com.UIN.Tool.domain.model.PluginInfo
import com.UIN.Tool.log.Logger
import com.UIN.Tool.constants.AppConstants as Constants
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
        
        Logger.i(TAG, Str.get(R.string.backend_message_service_started))
        
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
        
        Logger.success(TAG, Str.get(R.string.backend_message_service_ready))
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
                Logger.d(TAG, Str.get(R.string.cleaning_queuekey_queue_removed_remo, queueKey, removed))
                totalRemoved += removed
            }
        }
        
        if (totalRemoved > 0) {
            Logger.d(TAG, Str.get(R.string.total_cleaned_totalremoved_expired_m, totalRemoved))
        }
    }

    // ==================== 消息处理 ====================
    
    private fun handleIncomingMessage(message: PluginMessage) {
        Logger.d(TAG, Str.get(R.string.received_message_message_action_from, message.action, message.sender))
        
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
                    Logger.e(TAG, Str.get(R.string.listener_execution_error), e)
                }
            }
        }
        
        Logger.d(TAG, Str.get(R.string.message_stored_queue_size_gettotalqu, getTotalQueueSize()))
    }

    private fun handleEvent(eventType: String, data: Map<String, Any>?) {
        Logger.d(TAG, Str.get(R.string.received_event_eventtype, eventType))
        
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
            Logger.i(TAG, Str.get(R.string.backend_command_open_plugin_pluginid, pluginId))
            openPlugin(pluginId)
            
            PluginEventBus.sendToPlugin(
                message.sender,
                "open_plugin_response",
                mapOf(
                    "success" to true,
                    "pluginId" to pluginId,
                    "message" to Str.get(R.string.opened_plugin_pluginid, pluginId)
                )
            )
        } else {
            PluginEventBus.sendToPlugin(
                message.sender,
                "open_plugin_response",
                mapOf(
                    "success" to false,
                    "error" to Str.get(R.string.missing_pluginid)
                )
            )
        }
    }

    private fun handleCallPluginCommand(message: PluginMessage) {
        val targetPluginId = message.data?.get("targetPlugin") as? String
        val method = message.data?.get("method") as? String
        val params = message.data?.get("params") as? Map<String, Any>
        
        if (targetPluginId != null && method != null) {
            Logger.i(TAG, Str.get(R.string.backend_command_call_targetpluginid_, targetPluginId, method))
            
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
                    "error" to Str.get(R.string.missing_targetplugin_or_method)
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
            Logger.success(TAG, Str.get(R.string.opened_plugin_pluginid_2, pluginId))
        } catch (e: Exception) {
            Logger.e(TAG, Str.get(R.string.failed_to_open_plugin_pluginid, pluginId), e)
        }
    }

    fun callPluginMethod(pluginId: String, method: String, params: Map<String, Any>?): Any? {
        return try {
            val pluginManager = PluginManager.getInstance(context)
            val instance = pluginManager.getPluginInstance(pluginId)
            if (instance == null) {
                Logger.w(TAG, Str.get(R.string.plugin_instance_not_found_pluginid, pluginId))
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
            Logger.d(TAG, Str.get(R.string.call_pluginid_method_sent, pluginId, method))
            "success"
            
        } catch (e: Exception) {
            Logger.e(TAG, Str.get(R.string.failed_to_call_plugin_method_plugini, pluginId, method), e)
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
        Logger.d(TAG, Str.get(R.string.messages_cleared_pluginid, pluginId))
    }

    fun getTotalQueueSize(): Int {
        var total = 0
        messageQueues.values.forEach { total += it.size }
        return total
    }

    fun registerMessageListener(pluginId: String, listener: (PluginMessage) -> Unit) {
        messageListeners.computeIfAbsent(pluginId) { mutableListOf() }.add(listener)
        Logger.d(TAG, Str.get(R.string.registering_listener_pluginid, pluginId))
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
        Logger.d(TAG, Str.get(R.string.sending_message_to_targetplugin_acti, targetPlugin, action))
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
        Logger.d(TAG, Str.get(R.string.broadcasting_message_action, action))
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
        Logger.i(TAG, Str.get(R.string.backend_message_service_stopped))
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
        Logger.d(TAG, Str.get(R.string.pluginbackendmanager_startbackend_ca))
        Logger.d(TAG, "📦 pluginId: ${info.pluginId}")
        Logger.d(TAG, "🐍 backend: ${info.backend}")
        Logger.d(TAG, "📄 backendEntry: ${info.backendEntry}")
        Logger.d(TAG, "🔌 backendPort: ${info.backendPort}")
        Logger.d(TAG, Str.get(R.string.time_system_currenttimemillis, System.currentTimeMillis()))
        
        // 确保消息服务已启动
        if (!isMessageServiceRunning) {
            Logger.d(TAG, Str.get(R.string.message_service_not_running_initiali))
            init(context)
        }

        if (!info.hasBackend()) {
            Logger.w(TAG, Str.get(R.string.plugin_info_pluginid_has_no_backend_, info.pluginId))
            return false
        }

        val pluginId = info.pluginId
        synchronized(processLocks.getOrPut(pluginId) { Any() }) {
            Logger.d(TAG, Str.get(R.string.lock_acquired))
            
            if (isRunning(pluginId)) {
                Logger.d(TAG, Str.get(R.string.backend_already_running_pluginid, pluginId))
                return true
            }

            // other 模式：宿主不自动启动后端进程，仅注册端口并轮询就绪
            if (info.isOtherBackend()) {
                return startOtherBackend(info)
            }

            val pluginDir = File(Constants.PLUGIN_DIR, pluginId)
            Logger.d(TAG, Str.get(R.string.plugin_dir_plugindir_absolutepath, pluginDir.absolutePath))
            Logger.d(TAG, Str.get(R.string.dir_exists_plugindir_exists, pluginDir.exists()))
            
            if (!pluginDir.exists()) {
                Logger.e(TAG, Str.get(R.string.plugin_dir_not_found_plugindir_absol, pluginDir.absolutePath))
                return false
            }

            val entryPath = info.getBackendEntryPath(pluginDir.absolutePath)
            val entryFile = File(entryPath)
            Logger.d(TAG, Str.get(R.string.entry_path_entrypath, entryPath))
            Logger.d(TAG, Str.get(R.string.file_exists_entryfile_exists, entryFile.exists()))

            if (!entryFile.exists() && info.backend.lowercase() != "php") {
                Logger.e(TAG, Str.get(R.string.backend_entry_file_not_found_entrypa, entryPath))
                return false
            }

            val port = if (info.backendPort > 0) info.backendPort else findAvailablePort()
            runningPorts[pluginId] = port
            Logger.d(TAG, Str.get(R.string.allocating_port_port, port))

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
                Logger.d(TAG, Str.get(R.string.using_python_interpreter, interpreter))
                Logger.d(TAG, Str.get(R.string.exists_file_interpreter_exists, File(interpreter).exists()))
                command = listOf(interpreter, entryFile.absolutePath)
                workDir = entryFile.parentFile ?: pluginDir
            }
            Logger.d(TAG, Str.get(R.string.executing_command, command.joinToString(" ")))
            Logger.d(TAG, Str.get(R.string.work_dir_workdir_absolutepath, workDir.absolutePath))

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
                    Logger.d(TAG, Str.get(R.string.proot_env_prefix, env["PREFIX"], env["TERMUX__PREFIX"], env["TERMUX_APP__PACKAGE_NAME"], env["HOME"], env["TMPDIR"]))
                }

                Logger.i(TAG, Str.get(R.string.starting_process))
                val process = processBuilder.start()
                runningProcesses[pluginId] = process
                startTimes[pluginId] = System.currentTimeMillis()
                processPids[pluginId] = getProcessPid(process)
                Logger.d(TAG, Str.get(R.string.process_started_pid_processpids_plug, processPids[pluginId]))

                monitorOutput(pluginId, process, info.useProotRuntime())

                // proot 容器运行时冷启动较慢（proot-distro login + proot chroot + 链接转换 + 解释器），
                // 放宽就绪超时（至少 120s）；常规 termux 运行时仍以 backendTimeout 为上限（最多 30s）
                val readyTimeout = if (info.useProotRuntime()) {
                    maxOf(info.backendTimeout, 120)
                } else {
                    minOf(info.backendTimeout, 30)
                }
                Logger.d(TAG, Str.get(R.string.starting_health_check_timeout_readyt, readyTimeout))
                Logger.d(TAG, Str.get(R.string.backend_health_check_url, "http://127.0.0.1:$port${info.backendHealthCheck}"))
                val ready = waitForReady(port, readyTimeout, info.backendHealthCheck)

                if (ready) {
                    Logger.success(TAG, Str.get(R.string.backend_started_pluginid_port_port, pluginId, port))
                    return true
                } else {
                    if (process.isAlive) {
                        Logger.w(TAG, Str.get(R.string.backend_running_but_health_check_tim))
                        return true
                    } else {
                        Logger.e(TAG, Str.get(R.string.backend_process_exited))
                        process.destroy()
                        runningProcesses.remove(pluginId)
                        runningPorts.remove(pluginId)
                        return false
                    }
                }

            } catch (e: Exception) {
                Logger.e(TAG, Str.get(R.string.backend_start_error_e_message, e.message), e)
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
            Logger.i(TAG, Str.get(R.string.stopping_backend_pluginid, pluginId))
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
            callback(false, Str.get(R.string.backend_not_running))
            return
        }

        val port = runningPorts[pluginId] ?: 0
        if (port == 0) {
            callback(false, Str.get(R.string.invalid_port))
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
                Logger.e(TAG, Str.get(R.string.api_call_failed_e_message, e.message), e)
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
                Logger.d(TAG, Str.get(R.string.found_python_path, path))
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
                    Logger.d(TAG, Str.get(R.string.found_via_which_result, result))
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
                    Logger.d(TAG, Str.get(R.string.found_via_which_python3_result, result))
                    return result
                }
            }
        } catch (_: Exception) { }

        Logger.w(TAG, Str.get(R.string.python_not_found_using_default_pytho))
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
                Logger.d("Backend[$pluginId]", Str.get(R.string.process_exited_code_exitcode, exitCode))
                val duration = System.currentTimeMillis() - (startTimes[pluginId] ?: System.currentTimeMillis())
                Logger.d("Backend[$pluginId]", Str.get(R.string.runtime_duration_ms, duration))
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
        Logger.i(TAG, Str.get(R.string.other_mode_not_auto_starting_waiting, pluginId))

        if (info.backendPort > 0) {
            runningPorts[pluginId] = info.backendPort
            // other 模式 pre-command 可能需等待容器启动，超时放宽到 90s+
            val readyTimeout = maxOf(info.backendTimeout, 90)
            Logger.d(TAG, Str.get(R.string.other_mode_polling_port_info_backend, info.backendPort, readyTimeout))
            val ready = waitForPortOpen(info.backendPort, readyTimeout)
            if (ready) {
                Logger.success(TAG, Str.get(R.string.other_mode_backend_ready_pluginid_po, pluginId, info.backendPort))
                return true
            } else {
                Logger.w(TAG, Str.get(R.string.other_mode_port_not_ready_pluginid, pluginId))
                return false
            }
        } else {
            // 无端口插件：pre-command 进程会话存活即运行中
            runningPorts[pluginId] = 0
            Logger.success(TAG, Str.get(R.string.other_mode_port_less_backend_marked_, pluginId))
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
        Logger.w(TAG, Str.get(R.string.port_port_held_by_lingering_backend_, port, lingering))
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
            Logger.d(TAG, Str.get(R.string.sigkill_sent_to_process_group_pid, pid))
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

        Logger.d(TAG, Str.get(R.string.backend_waiting_for_ready, "http://127.0.0.1:$port$healthPath", timeout))

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
                    Logger.d(TAG, Str.get(R.string.health_check_passed_after_attempts_a, attempts))
                    return true
                }
                response.close()
            } catch (e: Exception) {
                lastError = e.message ?: "unknown"
                if (attempts % 5 == 0) {
                    Logger.d(TAG, Str.get(R.string.health_check_attempts_attempt_s_last, attempts, lastError))
                }
            }

            try {
                Thread.sleep(200)
            } catch (_: InterruptedException) {
                break
            }
        }

        Logger.w(TAG, Str.get(R.string.health_check_timed_out_after_attempt, attempts, lastError))
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
        Logger.d(TAG, Str.get(R.string.waiting_for_port_listen_127_0_0_1_po, port, timeout))
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            if (isPortOpen(port)) {
                Logger.success(TAG, Str.get(R.string.port_port_is_listening, port))
                return true
            }
            try {
                Thread.sleep(200)
            } catch (_: InterruptedException) {
                return false
            }
        }
        Logger.w(TAG, Str.get(R.string.port_port_listen_timed_out, port))
        return false
    }
}