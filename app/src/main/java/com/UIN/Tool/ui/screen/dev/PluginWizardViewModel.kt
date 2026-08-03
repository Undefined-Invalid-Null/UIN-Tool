// ui/screen/dev/PluginWizardViewModel.kt
package com.UIN.Tool.ui.screen.dev

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.UIN.Tool.log.Logger
import com.UIN.Tool.utils.AppLog
import com.UIN.Tool.utils.Constants
import com.UIN.Tool.utils.TemplateUtils
import org.json.JSONObject
import java.io.File

private const val TAG = "PluginWizardViewModel"

class PluginWizardViewModel(
    private val context: Context,
    val uiType: String,
    val backendType: String = ""
) : ViewModel() {

    // ==================== 插件信息 ====================
    var pluginId = mutableStateOf("com.example.myplugin")
    var pluginName = mutableStateOf("我的插件")
    var pluginAuthor = mutableStateOf("开发者")
    var pluginDescription = mutableStateOf("这是一个示例插件")
    var pluginVersion = mutableStateOf("1")
    var pluginVersionName = mutableStateOf("1.0.0")
    var mainClass = mutableStateOf("com.example.MainPlugin")
    var entryPath = mutableStateOf("web/index.html")
    var webTemplateType = mutableStateOf(0)

    // ==================== 后端运行配置 ====================
    var backendRuntime = mutableStateOf("termux")
    var backendPreCommand = mutableStateOf("")

    // ==================== 插件说明 ====================
    var pluginNotice = mutableStateOf("")

    // ==================== 文件管理 ====================
    var fileList = mutableStateOf<List<String>>(emptyList())
    var fileContents = mutableStateOf<Map<String, String>>(emptyMap())

    // ==================== 图标和资源 ====================
    var iconPath = mutableStateOf("")
    var resourcePaths = mutableStateOf<List<String>>(emptyList())

    // ==================== 二进制文件 ====================
    var binaryFilePath = mutableStateOf("")

    // ==================== 编译状态 ====================
    var isCompiling = mutableStateOf(false)
    var compileMessage = mutableStateOf("")
    var compileProgress = mutableStateOf(0)
    var tpkFile = mutableStateOf<File?>(null)
    var projectDir = mutableStateOf<File?>(null)

    init {
        if (uiType == "web" && backendType == "binary") {
            entryPath.value = "backend/myapp"
        }
        initDefaultFiles()
    }

    fun initDefaultFiles() {
        if (fileList.value.isEmpty() || fileContents.value.isEmpty()) {
            if (uiType == "native") {
                generateNativeCode()
            } else {
                generateWebTemplates()
                if (backendType.isNotEmpty() && backendType != "binary") {
                    generateBackendFiles()
                }
            }
        }
    }

    fun generateNativeCode() {
        val className = mainClass.value.substringAfterLast('.')
        val packageName = mainClass.value.substringBeforeLast('.')
        val packagePath = packageName.replace('.', '/')
        val javaFilePath = "src/$packagePath/$className.java"

        val vars = mapOf(
            "PACKAGE_NAME" to packageName,
            "CLASS_NAME" to className,
            "PLUGIN_NAME" to pluginName.value,
            "PLUGIN_ID" to pluginId.value,
            "PLUGIN_VERSION" to pluginVersion.value,
            "PLUGIN_VERSION_NAME" to pluginVersionName.value,
            "PLUGIN_AUTHOR" to pluginAuthor.value,
            "PLUGIN_DESCRIPTION" to pluginDescription.value
        )

        try {
            val javaCode = TemplateUtils.generateJavaCode(context, "native", vars)
            if (javaCode.isNotEmpty()) {
                fileList.value = listOf(javaFilePath)
                fileContents.value = mapOf(javaFilePath to javaCode)
            }
        } catch (e: Exception) {
            AppLog.e(TAG, "生成Java代码失败", e)
        }
    }

    fun generateWebTemplates() {
        try {
            val files = mutableMapOf<String, String>()
            files["web/index.html"] = ""
            files["web/style.css"] = ""
            files["web/script.js"] = ""

            fileList.value = files.keys.toList()
            fileContents.value = files

        } catch (e: Exception) {
            AppLog.e(TAG, "生成Web模板失败", e)
        }
    }

    private fun generateBackendFiles() {
        if (uiType != "web" || backendType.isEmpty() || backendType == "binary") {
            return
        }

        val files = fileContents.value.toMutableMap()
        val fileNames = fileList.value.toMutableList()

        val backendFile = when (backendType) {
            "python" -> "scripts/backend/server.py"
            "node" -> "scripts/backend/server.js"
            "php" -> "scripts/backend/index.php"
            "deno" -> "scripts/backend/server.ts"
            "go" -> "scripts/backend/main.go"
            "ruby" -> "scripts/backend/server.rb"
            "perl" -> "scripts/backend/server.pl"
            "lua" -> "scripts/backend/server.lua"
            "java" -> "scripts/backend/Main.java"
            else -> null
        }

        if (backendFile != null) {
            files[backendFile] = ""
            if (backendFile !in fileNames) {
                fileNames.add(backendFile)
            }
            entryPath.value = backendFile
        }

        fileList.value = fileNames
        fileContents.value = files
    }

    fun updateFiles(files: List<String>, contents: Map<String, String>) {
        fileList.value = files
        fileContents.value = contents
    }

    suspend fun generateProjectFiles(workDir: File): Boolean {
        return try {
            workDir.mkdirs()

            val jsonContent = generatePluginJsonContent()
            val jsonFile = File(workDir, "plugin.json")
            jsonFile.writeText(jsonContent)

            fileContents.value.forEach { (path, content) ->
                val file = File(workDir, path)
                file.parentFile?.mkdirs()
                file.writeText(content)
            }

            if (iconPath.value.isNotEmpty() && File(iconPath.value).exists()) {
                val iconFile = File(workDir, "icon.png")
                File(iconPath.value).copyTo(iconFile, overwrite = true)
            }

            if (resourcePaths.value.isNotEmpty()) {
                val resDir = File(workDir, "res")
                resDir.mkdirs()
                resourcePaths.value.forEach { resPath ->
                    val srcRes = File(resPath)
                    if (srcRes.exists()) {
                        val dstRes = File(resDir, srcRes.name)
                        srcRes.copyTo(dstRes, overwrite = true)
                    }
                }
            }

            generateReadme(workDir)

            AppLog.success(TAG, "项目文件生成成功: ${workDir.absolutePath}")
            true

        } catch (e: Exception) {
            AppLog.e(TAG, "生成项目文件失败", e)
            false
        }
    }

    private fun generatePluginJsonContent(): String {
        val json = JSONObject().apply {
            put("pluginId", pluginId.value)
            put("version", pluginVersion.value.toIntOrNull() ?: 1)
            put("versionName", pluginVersionName.value)
            put("minHostVersion", 1)
            put("name", pluginName.value)
            put("author", pluginAuthor.value)
            put("description", pluginDescription.value)
            put("icon", "icon.png")
            put("mainClass", if (uiType == "native") mainClass.value else "")
            put("apiLevel", 21)
            put("uiType", uiType)
            put("entry", if (uiType == "web") entryPath.value else "")
            put("permissions", "")
            put("dependencies", "")
            put("notice", pluginNotice.value)

            if (uiType == "web" && backendType.isNotEmpty()) {
                put("backend", backendType)
                put("backendPort", 8000)
                if (backendType == "binary") {
                    val binaryName = File(binaryFilePath.value).name
                    put("backendEntry", "backend/$binaryName")
                    put("backendBinary", binaryName)
                } else if (backendType == "other") {
                    // other 模式：宿主不自动启动后端，由 pre-command 负责启动
                    put("backendEntry", "")
                } else {
                    val backendFile = when (backendType) {
                        "python" -> "scripts/backend/server.py"
                        "node" -> "scripts/backend/server.js"
                        "php" -> "scripts/backend/index.php"
                        "deno" -> "scripts/backend/server.ts"
                        "go" -> "scripts/backend/main.go"
                        "ruby" -> "scripts/backend/server.rb"
                        "perl" -> "scripts/backend/server.pl"
                        "lua" -> "scripts/backend/server.lua"
                        "java" -> "scripts/backend/Main.java"
                        else -> "scripts/backend/server"
                    }
                    put("backendEntry", backendFile)
                }
                put("backendAutoStart", true)
                put("backendTimeout", 30)
                put("backendHealthCheck", "/health")
                put("backendRuntime", backendRuntime.value.ifEmpty { "termux" })
                put("backendPreCommand", backendPreCommand.value.trim())
            }
        }
        return json.toString()
    }

    private fun generateReadme(workDir: File) {
        try {
            val vars = mapOf(
                "PLUGIN_NAME" to pluginName.value,
                "PLUGIN_ID" to pluginId.value,
                "PLUGIN_VERSION" to pluginVersion.value,
                "PLUGIN_VERSION_NAME" to pluginVersionName.value,
                "PLUGIN_AUTHOR" to pluginAuthor.value,
                "UI_TYPE" to if (uiType == "web") "WebView" else "原生代码"
            )
            val readme = TemplateUtils.generateReadme(context, vars)
            File(workDir, "README.md").writeText(readme)
        } catch (e: Exception) {
            AppLog.e(TAG, "生成README失败", e)
        }
    }
}