// domain/model/PluginInfo.kt
package com.UIN.Tool.domain.model

import com.UIN.Tool.R
import com.UIN.Tool.utils.Str
import org.json.JSONObject  // ✅ 添加这个导入

/**
 * 插件信息数据模型
 */
data class PluginInfo(
    // ==================== 基础信息 ====================
    var pluginId: String = "",
    var version: Int = 1,
    var versionName: String = "1.0.0",
    var minHostVersion: Int = 1,
    var name: String = "",
    var author: String = "",
    var description: String = "",
    var icon: String = "icon.png",
    var mainClass: String = "",
    var updateUrl: String = "",
    var apiLevel: Int = 21,
    var category: String = Str.get(R.string.uncategorized),
    var signature: String = "",
    var uiType: String = "native",
    var entry: String = "web/index.html",
    var permissions: List<String> = emptyList(),
    var dependencies: List<String> = emptyList(),

    // ==================== 插件说明 ====================
    var notice: String = "",

    // ==================== 后端配置 ====================
    var backend: String = "",
    var backendRuntime: String = "",
    var backendPort: Int = 0,
    // 启动命令：宿主用 sh -lc 执行插件内的启动脚本（依赖检测 + 真正后端启动），
    // 由插件配置，运行环境由用户在软件内全局设定。web+后端 插件必填。
    var backendStartCommand: String = "",
    var backendStartEntry: String = "scripts/start.sh",
    var backendEntry: String = "scripts/backend/server.py",
    var backendAutoStart: Boolean = true,
    var backendKeepAlive: Boolean = false,
    var backendPreCommand: String = "",
    var backendEnv: Map<String, String> = emptyMap(),
    var backendTimeout: Int = 30,
    var backendHealthCheck: String = "/health",
    var backendMaxRetries: Int = 3,
    var backendLogLevel: String = "info",
    var backendArgs: List<String> = emptyList(),

    // ==================== 二进制程序配置 ====================
    var backendBinary: String = "",
    var backendInstallCmd: String = "",
    var backendCheckCmd: String = "",

    // ==================== PHP 专用配置 ====================
    var backendPhpDocRoot: String = "",

    // ==================== Java 专用配置 ====================
    var backendJavaClass: String = "",
    var backendJavaJar: String = "",

    // ==================== 前端配置 ====================
    var frontendConfig: Map<String, Any> = emptyMap(),

    // ==================== 资源限制 ====================
    var maxMemory: Int = 512,
    var maxCpuTime: Int = 60,
    var maxConcurrentTasks: Int = 5,

    // ==================== 运行时状态 ====================
    var isBackendRunning: Boolean = false,
    var currentBackendPort: Int = 0,
    var backendPid: Int = -1,
    var lastStartTime: Long = 0
) {

    fun hasBackend(): Boolean = backend.isNotEmpty() && backendAutoStart

    /** 新式 web+后端 插件：通过启动命令（sh 执行插件内脚本）启动，而非语言解释器 */
    fun hasStartCommand(): Boolean = backendStartCommand.isNotBlank()

    /** 实际要执行的启动命令（缺省回退到入口脚本） */
    fun getStartCommandText(): String =
        backendStartCommand.ifBlank { "sh $backendStartEntry" }

    /** 启动脚本在插件目录内的完整路径 */
    fun getBackendStartEntryPath(pluginDir: String): String = "$pluginDir/$backendStartEntry"

    fun useProotRuntime(): Boolean = backendRuntime.equals("proot", ignoreCase = true)

    fun isOtherBackend(): Boolean = backend.equals("other", ignoreCase = true)

    fun isCui(): Boolean = uiType.equals("cui", ignoreCase = true)

    fun isWebPlugin(): Boolean = uiType == "web"
    fun isNativePlugin(): Boolean = uiType == "native"
    fun hasNotice(): Boolean = notice.isNotEmpty()

    fun getBackendDisplayName(): String {
        return when (backend.lowercase()) {
            "python" -> "Python"
            "node" -> "Node.js"
            "php" -> "PHP"
            "binary" -> Str.get(R.string.binary_program)
            "deno" -> "Deno"
            "go" -> "Go"
            "ruby" -> "Ruby"
            "perl" -> "Perl"
            "lua" -> "Lua"
            "java" -> "Java"
            "rust" -> "Rust"
            else -> Str.get(R.string.unknown)
        }
    }

    fun getInterpreter(): String {
        return when (backend.lowercase()) {
            "python" -> "python"
            "node" -> "node"
            "php" -> "php"
            "binary" -> backendBinary.ifEmpty { "bash" }
            "deno" -> "deno"
            "go" -> "go"
            "ruby" -> "ruby"
            "perl" -> "perl"
            "lua" -> "lua"
            "java" -> "java"
            "rust" -> "cargo"
            else -> "bash"
        }
    }

    fun getBackendEntryPath(pluginDir: String): String {
        return "$pluginDir/$backendEntry"
    }

    fun getBackendCommand(pluginDir: String): List<String> {
        val entryPath = getBackendEntryPath(pluginDir)
        val port = if (backendPort > 0) backendPort else 8000

        return when (backend.lowercase()) {
            "python" -> listOf("python", entryPath)
            "node" -> listOf("node", entryPath)
            "php" -> {
                val docRoot = backendPhpDocRoot.ifEmpty { pluginDir }
                listOf("php", "-S", "127.0.0.1:$port", "-t", docRoot)
            }
            "binary" -> {
                val binary = if (backendBinary.isNotEmpty()) backendBinary else entryPath
                listOf(binary) + backendArgs
            }
            "deno" -> listOf("deno", "run", "--allow-net", "--allow-read", entryPath)
            "go" -> listOf("go", "run", entryPath)
            "ruby" -> listOf("ruby", entryPath)
            "perl" -> listOf("perl", entryPath)
            "lua" -> listOf("lua", entryPath)
            "java" -> {
                if (backendJavaJar.isNotEmpty()) {
                    listOf("java", "-jar", "$pluginDir/${backendJavaJar}")
                } else if (backendJavaClass.isNotEmpty()) {
                    listOf("java", "-cp", pluginDir, backendJavaClass)
                } else {
                    listOf("java", entryPath)
                }
            }
            "rust" -> {
                val binaryName = entryPath.substringAfterLast("/").substringBeforeLast(".")
                listOf("cargo", "run", "--release", "--bin", binaryName)
            }
            else -> listOf("bash", entryPath)
        }
    }

    fun getInstallCommand(): String {
        return backendInstallCmd.ifEmpty {
            when (backend.lowercase()) {
                "python" -> "pkg install python"
                "node" -> "pkg install nodejs"
                "php" -> "pkg install php"
                "deno" -> "pkg install deno"
                "go" -> "pkg install golang"
                "ruby" -> "pkg install ruby"
                "perl" -> "pkg install perl"
                "lua" -> "pkg install lua"
                "java" -> "pkg install openjdk-17"
                "rust" -> "pkg install rust"
                else -> ""
            }
        }
    }

    fun getCheckCommand(): String {
        return backendCheckCmd.ifEmpty {
            when (backend.lowercase()) {
                "python" -> "python --version"
                "node" -> "node --version"
                "php" -> "php --version"
                "deno" -> "deno --version"
                "go" -> "go version"
                "ruby" -> "ruby --version"
                "perl" -> "perl --version"
                "lua" -> "lua --version"
                "java" -> "java -version"
                "rust" -> "rustc --version"
                else -> "which ${getInterpreter()}"
            }
        }
    }

    fun getHealthCheckPath(): String {
        return if (backendHealthCheck.startsWith("/")) backendHealthCheck else "/$backendHealthCheck"
    }

    /**
     * 旧式后端强制迁移为新式（内存中完成，不写回插件文件）：
     * - 无 backendStartCommand 的旧式语言后端（python/node/php/...）→ 由语言+入口合成启动命令，backend 置为 "other"。
     * - 旧式 other（由 pre-command 启动长驻服务）→ pre-command 即启动命令。
     * 迁移后宿主统一走「other + backendStartCommand」单一路径，旧式启动分支不再存在。
     */
    fun migrateLegacyBackend() {
        if (backend.isBlank() || backendStartCommand.isNotBlank()) return

        val lang = backend.lowercase()
        val cmd = if (lang == "other") {
            backendPreCommand.ifBlank { backendEntry }
        } else {
            when (lang) {
                "python" -> "python3 $backendEntry"
                "node" -> "node $backendEntry"
                "php" -> "php -S 127.0.0.1:\${PORT:-8000} -t ${backendPhpDocRoot.ifBlank { "." }}"
                "binary" -> {
                    val bin = backendBinary.ifBlank { backendEntry }
                    if (backendArgs.isNotEmpty()) "$bin ${backendArgs.joinToString(" ")}" else bin
                }
                "deno" -> "deno run --allow-net --allow-read $backendEntry"
                "go" -> "go run $backendEntry"
                "ruby" -> "ruby $backendEntry"
                "perl" -> "perl $backendEntry"
                "lua" -> "lua $backendEntry"
                "java" -> when {
                    backendJavaJar.isNotEmpty() -> "java -jar $backendJavaJar"
                    backendJavaClass.isNotEmpty() -> "java -cp . $backendJavaClass"
                    else -> "java $backendEntry"
                }
                else -> backendPreCommand.ifBlank { backendEntry }
            }
        }.trim()

        backendStartCommand = cmd
        backendStartEntry = backendEntry
        backend = "other"
        backendRuntime = ""
        backendPreCommand = ""
    }

    fun toJson(): String {
        return JSONObject().apply {
            put("pluginId", pluginId)
            put("version", version)
            put("versionName", versionName)
            put("minHostVersion", minHostVersion)
            put("name", name)
            put("author", author)
            put("description", description)
            put("icon", icon)
            put("mainClass", mainClass)
            put("updateUrl", updateUrl)
            put("apiLevel", apiLevel)
            put("category", category)
            put("signature", signature)
            put("uiType", uiType)
            put("entry", entry)
            put("permissions", permissions.joinToString(","))
            put("dependencies", dependencies.joinToString(","))
            put("notice", notice)
            put("backend", backend)
            put("backendRuntime", backendRuntime)
            put("backendPort", backendPort)
            put("backendStartCommand", backendStartCommand)
            put("backendStartEntry", backendStartEntry)
            put("backendEntry", backendEntry)
            put("backendAutoStart", backendAutoStart)
            put("backendKeepAlive", backendKeepAlive)
            put("backendPreCommand", backendPreCommand)
            put("backendTimeout", backendTimeout)
            put("backendHealthCheck", backendHealthCheck)
            put("backendMaxRetries", backendMaxRetries)
            put("backendLogLevel", backendLogLevel)
            put("backendArgs", backendArgs.joinToString(","))
            put("backendBinary", backendBinary)
            put("backendInstallCmd", backendInstallCmd)
            put("backendCheckCmd", backendCheckCmd)
            put("backendPhpDocRoot", backendPhpDocRoot)
            put("backendJavaClass", backendJavaClass)
            put("backendJavaJar", backendJavaJar)
            put("maxMemory", maxMemory)
            put("maxCpuTime", maxCpuTime)
            put("maxConcurrentTasks", maxConcurrentTasks)
        }.toString()
    }

    companion object {
        fun fromJson(json: String): PluginInfo? {
            return try {
                val obj = JSONObject(json)
                val info = PluginInfo(
                    pluginId = obj.optString("pluginId", ""),
                    version = obj.optInt("version", 1),
                    versionName = obj.optString("versionName", "1.0.0"),
                    minHostVersion = obj.optInt("minHostVersion", 1),
                    name = obj.optString("name", ""),
                    author = obj.optString("author", ""),
                    description = obj.optString("description", ""),
                    icon = obj.optString("icon", "icon.png"),
                    mainClass = obj.optString("mainClass", ""),
                    updateUrl = obj.optString("updateUrl", ""),
                    apiLevel = obj.optInt("apiLevel", 21),
                    category = obj.optString("category", Str.get(R.string.uncategorized)),
                    signature = obj.optString("signature", ""),
                    uiType = obj.optString("uiType", "native"),
                    entry = obj.optString("entry", "web/index.html"),
                    permissions = obj.optString("permissions", "").split(",").filter { it.isNotEmpty() },
                    dependencies = obj.optString("dependencies", "").split(",").filter { it.isNotEmpty() },
                    notice = obj.optString("notice", ""),
                    backend = obj.optString("backend", ""),
                    backendRuntime = obj.optString("backendRuntime", ""),
                    backendPort = obj.optInt("backendPort", 0),
                    backendStartCommand = obj.optString("backendStartCommand", ""),
                    backendStartEntry = obj.optString("backendStartEntry", "scripts/start.sh"),
                    backendEntry = obj.optString("backendEntry", "scripts/backend/server.py"),
                    backendAutoStart = obj.optBoolean("backendAutoStart", true),
                    backendKeepAlive = obj.optBoolean("backendKeepAlive", false),
                    backendPreCommand = obj.optString("backendPreCommand", ""),
                    backendTimeout = obj.optInt("backendTimeout", 30),
                    backendHealthCheck = obj.optString("backendHealthCheck", "/health"),
                    backendMaxRetries = obj.optInt("backendMaxRetries", 3),
                    backendLogLevel = obj.optString("backendLogLevel", "info"),
                    backendArgs = obj.optString("backendArgs", "").split(",").filter { it.isNotEmpty() },
                    backendBinary = obj.optString("backendBinary", ""),
                    backendInstallCmd = obj.optString("backendInstallCmd", ""),
                    backendCheckCmd = obj.optString("backendCheckCmd", ""),
                    backendPhpDocRoot = obj.optString("backendPhpDocRoot", ""),
                    backendJavaClass = obj.optString("backendJavaClass", ""),
                    backendJavaJar = obj.optString("backendJavaJar", ""),
                    maxMemory = obj.optInt("maxMemory", 512),
                    maxCpuTime = obj.optInt("maxCpuTime", 60),
                    maxConcurrentTasks = obj.optInt("maxConcurrentTasks", 5)
                )
                info.migrateLegacyBackend()
                info
            } catch (e: Exception) {
                null
            }
        }
    }
}