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
    val pluginId: String = "",
    val version: Int = 1,
    val versionName: String = "1.0.0",
    val minHostVersion: Int = 1,
    val name: String = "",
    val author: String = "",
    val description: String = "",
    val icon: String = "icon.png",
    val mainClass: String = "",
    val updateUrl: String = "",
    val apiLevel: Int = 21,
    // D-41：存稳定 key 而非本地化串（原 Str.get(...) 被 toJson 持久化，切语言后分类错乱）
    val category: String = CATEGORY_UNCATEGORIZED,
    // D-42③：原 signature 字段删除——plugin.json 自带、toJson 回写、全仓 0 消费者（自证
    // 签名无锚点）；真校验由 SecurityUtils SHA-256 文件指纹 TOFU 钉扎承担（PluginManager 导入拦截）
    val uiType: String = "native",
    val entry: String = "web/index.html",
    val permissions: List<String> = emptyList(),
    val dependencies: List<String> = emptyList(),
    /** 来源源 ID（安装时记录） */
    val sourceId: String = "",

    // ==================== 插件说明 ====================
    val notice: String = "",

    // ==================== 后端配置 ====================
    val backend: String = "",
    val backendRuntime: String = "",
    val backendPort: Int = 0,
    // 启动命令：宿主用 sh -lc 执行插件内的启动脚本（依赖检测 + 真正后端启动），
    // 由插件配置，运行环境由用户在软件内全局设定。web+后端 插件必填。
    val backendStartCommand: String = "",
    val backendStartEntry: String = "scripts/start.sh",
    val backendEntry: String = "scripts/backend/server.py",
    val backendAutoStart: Boolean = true,
    val backendKeepAlive: Boolean = false,
    val backendPreCommand: String = "",
    val backendEnv: Map<String, String> = emptyMap(),
    val backendTimeout: Int = 30,
    val backendHealthCheck: String = "/health",
    val backendMaxRetries: Int = 3,
    val backendLogLevel: String = "info",
    val backendArgs: List<String> = emptyList(),

    // ==================== 二进制程序配置 ====================
    val backendBinary: String = "",
    val backendInstallCmd: String = "",
    val backendCheckCmd: String = "",

    // ==================== PHP 专用配置 ====================
    val backendPhpDocRoot: String = "",

    // ==================== Java 专用配置 ====================
    val backendJavaClass: String = "",
    val backendJavaJar: String = "",

    // ==================== 前端配置 ====================
    val frontendConfig: Map<String, Any> = emptyMap(),

    // ==================== 资源限制 ====================
    val maxMemory: Int = 512,
    val maxCpuTime: Int = 60,
    val maxConcurrentTasks: Int = 5,

    // ==================== 意图中转（openWith） ====================
    // 系统「分享 / 用其他应用打开」分发给插件的外部内容接收配置
    val openWith: OpenWithConfig? = null
) {

    fun hasBackend(): Boolean = backend.isNotEmpty() && backendAutoStart

    /** 是否声明可接收外部内容（用于“用…打开”意图中转） */
    fun hasOpenWith(): Boolean = openWith?.enabled == true

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

    /** D-41：展示用分类（"uncategorized" → 本地化「未分类」） */
    fun displayCategory(): String =
        if (category.isEmpty() || category == CATEGORY_UNCATEGORIZED) {
            Str.get(R.string.uncategorized)
        } else {
            category
        }
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
                "python" -> "apt install -y python3"
                "node" -> "apt install -y nodejs"
                "php" -> "apt install -y php-cli"
                "deno" -> "curl -fsSL https://deno.land/install.sh | sh"
                "go" -> "apt install -y golang"
                "ruby" -> "apt install -y ruby"
                "perl" -> "apt install -y perl"
                "lua" -> "apt install -y lua5.3"
                "java" -> "apt install -y default-jdk"
                "rust" -> "curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh -s -- -y"
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
    fun migrateLegacyBackend(): PluginInfo {
        if (backend.isBlank() || backendStartCommand.isNotBlank()) return this

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

        // D-42A2：原地改字段改为 copy（模型全 val）
        return copy(
            backendStartCommand = cmd,
            backendStartEntry = backendEntry,
            backend = "other",
            backendRuntime = "",
            backendPreCommand = ""
        )
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
            put("uiType", uiType)
            put("entry", entry)
            put("permissions", permissions.joinToString(","))
            put("dependencies", dependencies.joinToString(","))
            put("sourceId", sourceId)
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
            // D-40：backendEnv / frontendConfig 原先既不解析也不序列化 → info.backendEnv 恒为空，
            // 插件声明的后端环境变量永久失效（PluginBackendManager 注入 env 那一行从未拿到值）
            if (backendEnv.isNotEmpty()) {
                put("backendEnv", JSONObject().apply { backendEnv.forEach { (k, v) -> put(k, v) } })
            }
            if (frontendConfig.isNotEmpty()) {
                put("frontendConfig", JSONObject().apply { frontendConfig.forEach { (k, v) -> put(k, v) } })
            }
            openWith?.let { ow ->
                put("openWith", JSONObject().apply {
                    put("enabled", ow.enabled)
                    put("label", ow.label)
                    put("mimeTypes", ow.mimeTypes.joinToString(","))
                    put("acceptText", ow.acceptText)
                    put("acceptUrl", ow.acceptUrl)
                    put("acceptFile", ow.acceptFile)
                })
            }
        }.toString()
    }

    companion object {
        /** D-41：分类的稳定 key，展示时才映射为本地化文案 */
        const val CATEGORY_UNCATEGORIZED = "uncategorized"

        /**
         * 解析字符串列表字段，兼容两种声明格式：
         * - JSON 数组：`"permissions": ["a", "b"]`
         * - 逗号分隔字符串：`"permissions": "a,b"`
         * 原实现只调 optString().split(",")，遇到数组格式会解析出带引号/方括号的错误项，
         * 导致插件声明的权限永远匹配不上 → 权限管理页误报"权限被拒绝"。
         */
        private fun parseStringList(obj: JSONObject, key: String): List<String> {
            return try {
                val raw = obj.opt(key)
                when (raw) {
                    is org.json.JSONArray -> {
                        val list = mutableListOf<String>()
                        for (i in 0 until raw.length()) {
                            val v = raw.optString(i, "")
                            if (v.isNotEmpty()) list.add(v)
                        }
                        list
                    }
                    else -> obj.optString(key, "").split(",").map { it.trim() }.filter { it.isNotEmpty() }
                }
            } catch (e: Exception) {
                emptyList()
            }
        }

        /** 公开版本：供向导回读 plugin.json 时复用同一套兼容解析（JSON 数组 / 逗号字符串）。 */
        fun parseStringListCompat(obj: JSONObject, key: String): List<String> = parseStringList(obj, key)

        /** D-40：解析 JSON 对象型字段为字符串映射 */
        private fun parseStringMap(obj: JSONObject, key: String): Map<String, String> {
            return try {
                val raw = obj.optJSONObject(key) ?: return emptyMap()
                val map = LinkedHashMap<String, String>()
                val names = raw.keys()
                while (names.hasNext()) {
                    val k = names.next()
                    map[k] = raw.optString(k, "")
                }
                map
            } catch (e: Exception) {
                emptyMap()
            }
        }

        /** D-40：解析 JSON 对象型字段为任意值映射（frontendConfig） */
        private fun parseAnyMap(obj: JSONObject, key: String): Map<String, Any> {
            return try {
                val raw = obj.optJSONObject(key) ?: return emptyMap()
                val map = LinkedHashMap<String, Any>()
                val names = raw.keys()
                while (names.hasNext()) {
                    val k = names.next()
                    map[k] = raw.opt(k) ?: continue
                }
                map
            } catch (e: Exception) {
                emptyMap()
            }
        }

        fun fromJson(json: String): PluginInfo? {
            return try {
                val obj = JSONObject(json)
                // D-40：backendEnv/frontendConfig 随构造一并解析（原构造后补赋值）
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
                    category = obj.optString("category", CATEGORY_UNCATEGORIZED),
                    uiType = obj.optString("uiType", "native"),
                    entry = obj.optString("entry", "web/index.html"),
                    permissions = parseStringList(obj, "permissions"),
                    dependencies = parseStringList(obj, "dependencies"),
                    sourceId = obj.optString("sourceId", ""),
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
                    backendArgs = parseStringList(obj, "backendArgs"),
                    backendBinary = obj.optString("backendBinary", ""),
                    backendInstallCmd = obj.optString("backendInstallCmd", ""),
                    backendCheckCmd = obj.optString("backendCheckCmd", ""),
                    backendPhpDocRoot = obj.optString("backendPhpDocRoot", ""),
                    backendJavaClass = obj.optString("backendJavaClass", ""),
                    backendJavaJar = obj.optString("backendJavaJar", ""),
                    maxMemory = obj.optInt("maxMemory", 512),
                    maxCpuTime = obj.optInt("maxCpuTime", 60),
                    maxConcurrentTasks = obj.optInt("maxConcurrentTasks", 5),
                    openWith = obj.optJSONObject("openWith")?.let { ow ->
                        OpenWithConfig(
                            enabled = ow.optBoolean("enabled", true),
                            label = ow.optString("label", ""),
                            mimeTypes = parseStringList(ow, "mimeTypes"),
                            acceptText = ow.optBoolean("acceptText", true),
                            acceptUrl = ow.optBoolean("acceptUrl", true),
                            acceptFile = ow.optBoolean("acceptFile", true)
                        )
                    },
                    backendEnv = parseStringMap(obj, "backendEnv"),
                    frontendConfig = parseAnyMap(obj, "frontendConfig")
                ).migrateLegacyBackend()
                info
            } catch (e: Exception) {
                null
            }
        }
    }
}

/**
 * 意图中转配置（plugin.json 中的 `openWith` 字段）
 *
 * 声明该插件可接收从系统“分享 / 用其他应用打开”传入的内容。
 *
 * ```json
 * "openWith": {
 *   "enabled": true,
 *   "label": "编写助手",
 *   "mimeTypes": "text/plain,application/pdf",
 *   "acceptText": true,
 *   "acceptUrl": true,
 *   "acceptFile": true
 * }
 * ```
 */
data class OpenWithConfig(
    /** 是否启用本插件接收外部内容 */
    val enabled: Boolean = true,
    /** 在“用…打开”中转页显示的名称（留空则使用插件名） */
    val label: String = "",
    /** 支持的 MIME 类型，支持通配形式（text 通配、全类型通配），留空表示接受任意 */
    val mimeTypes: List<String> = emptyList(),
    /** 是否接受纯文本（ACTION_SEND 的 EXTRA_TEXT） */
    val acceptText: Boolean = true,
    /** 是否接受 URL / 链接 */
    val acceptUrl: Boolean = true,
    /** 是否接受文件（content Uri / 本地文件） */
    val acceptFile: Boolean = true
) {

    /** 判断是否匹配某次外部打开请求。kind ∈ {file, text, url} */
    fun matches(mimeType: String?, kind: String): Boolean {
        if (!enabled) return false
        when (kind) {
            "file" -> if (!acceptFile) return false
            "text" -> if (!acceptText) return false
            "url" -> if (!acceptUrl) return false
            else -> return false
        }
        if (mimeType.isNullOrEmpty()) return true
        if (mimeTypes.isEmpty() || mimeTypes.any { it.equals("*/*", ignoreCase = true) }) return true
        val m = mimeType.lowercase()
        return mimeTypes.any { rule -> matchesMime(rule.lowercase(), m) }
    }

    private fun matchesMime(rule: String, mime: String): Boolean {
        if (rule == "*/*") return true
        if (rule.endsWith("/*")) {
            return mime.substringBefore('/').equals(rule.substringBefore('/'), ignoreCase = true)
        }
        return mime == rule
    }
}