// ui/screen/dev/PluginWizardViewModel.kt
package com.UIN.Tool.ui.screen.dev

import com.UIN.Tool.R
import com.UIN.Tool.utils.Str
import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.UIN.Tool.log.Logger
import com.UIN.Tool.utils.AppLog
import com.UIN.Tool.constants.AppConstants as Constants
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
    var pluginName = mutableStateOf(Str.get(R.string.my_plugins))
    var pluginAuthor = mutableStateOf(Str.get(R.string.developer_2))
    var pluginDescription = mutableStateOf(Str.get(R.string.this_is_an_example_plugin))
    var pluginVersion = mutableStateOf("1")
    var pluginVersionName = mutableStateOf("1.0.0")
    var mainClass = mutableStateOf("com.example.MainPlugin")
    var entryPath = mutableStateOf("web/index.html")
    var webTemplateType = mutableStateOf(0)

    // ==================== 后端运行配置 ====================
    var backendRuntime = mutableStateOf("termux")
    var backendPreCommand = mutableStateOf("")
    var backendStartCommand = mutableStateOf("")

    // ==================== 插件说明 ====================
    var pluginNotice = mutableStateOf("")

    // ==================== 扩展配置 ====================
    var permissions = mutableStateOf<List<String>>(emptyList())
    var dependencies = mutableStateOf("")
    var minHostVersion = mutableStateOf("1")
    var apiLevel = mutableStateOf("21")
    var category = mutableStateOf("")
    var updateUrl = mutableStateOf("")

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
        // 二进制后端：前端入口固定为 web/index.html；二进制由打包阶段复制到 backend/myapp
        if (uiType == "web" && backendType == "binary") {
            entryPath.value = "web/index.html"
        }
        initDefaultFiles()
    }

    fun initDefaultFiles() {
        if (fileList.value.isEmpty() || fileContents.value.isEmpty()) {
            if (uiType == "native") {
                generateNativeCode()
            } else if (uiType == "cui") {
                generateCuiFiles()
            } else {
                generateWebTemplates()
                if (backendType.isNotEmpty()) {
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
            AppLog.e(TAG, Str.get(R.string.failed_to_generate_java_code), e)
        }
    }

    fun generateWebTemplates() {
        try {
            val vars = mapOf(
                "PLUGIN_NAME" to pluginName.value,
                "PLUGIN_ID" to pluginId.value,
                "PLUGIN_VERSION" to pluginVersion.value,
                "PLUGIN_VERSION_NAME" to pluginVersionName.value,
                "PLUGIN_AUTHOR" to pluginAuthor.value
            )
            val files = TemplateUtils.generateWebTemplates(
                context,
                vars,
                if (uiType == "web" && backendType.isNotEmpty()) 2 else webTemplateType.value
            )

            fileList.value = files.keys.toList()
            fileContents.value = files

        } catch (e: Exception) {
            AppLog.e(TAG, Str.get(R.string.failed_to_generate_web_template), e)
        }
    }

    private fun generateCuiFiles() {
        try {
            val script = """
                #!/usr/bin/env python3
                # -*- coding: utf-8 -*-
                # ${Str.get(R.string.cui_plugin_script_comment)}
                import os

                print("=" * 48)
                print(Str.get(R.string.cui_plugin_terminal_started))
                print("=" * 48)
                print(Str.get(R.string.plugin_id_2) + os.environ.get("PLUGIN_ID", "?"))
                print(Str.get(R.string.plugin_dir) + os.getcwd())
                print("-" * 48)
                print(Str.get(R.string.type_exit_or_ctrl_d_to_end_the_sessi))

                import code
                code.interact(banner="", local=locals())
            """.trimIndent()

            fileList.value = listOf("scripts/script.py")
            fileContents.value = mapOf("scripts/script.py" to script)

            if (backendPreCommand.value.isBlank()) {
                backendPreCommand.value = "python3 scripts/script.py"
            }
        } catch (e: Exception) {
            AppLog.e(TAG, Str.get(R.string.failed_to_generate_cui_script), e)
        }
    }

    private fun generateBackendFiles() {
        if (uiType != "web" || backendType.isEmpty()) {
            return
        }

        val files = fileContents.value.toMutableMap()
        val fileNames = fileList.value.toMutableList()

        val vars = mapOf(
            "PLUGIN_NAME" to pluginName.value,
            "PLUGIN_ID" to pluginId.value,
            "PLUGIN_VERSION" to pluginVersion.value,
            "PLUGIN_VERSION_NAME" to pluginVersionName.value,
            "PLUGIN_AUTHOR" to pluginAuthor.value
        )

        // 二进制后端：启动脚本直接运行用户选择的二进制 backend/myapp
        if (backendType == "binary") {
            val startScript = TemplateUtils.generateBinaryBackendStartScript(context, vars)
            files["scripts/start.sh"] = startScript
            if ("scripts/start.sh" !in fileNames) fileNames.add("scripts/start.sh")

            entryPath.value = "web/index.html"
            if (backendStartCommand.value.isBlank()) {
                backendStartCommand.value = "sh scripts/start.sh"
            }

            fileList.value = fileNames
            fileContents.value = files
            return
        }

        // 新式后端：生成启动脚本 + 后端服务示例（不再按语言生成）
        val startScript = TemplateUtils.generateBackendStartScript(context, vars)
        val serverScript = TemplateUtils.generateBackendServer(context, vars)

        files["scripts/start.sh"] = startScript
        files["scripts/backend/server.py"] = serverScript

        for (path in listOf("scripts/start.sh", "scripts/backend/server.py")) {
            if (path !in fileNames) fileNames.add(path)
        }

        entryPath.value = "web/index.html"
        if (backendStartCommand.value.isBlank()) {
            backendStartCommand.value = "sh scripts/start.sh"
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

            AppLog.success(TAG, Str.get(R.string.project_files_generated_workdir_abso, workDir.absolutePath))
            true

        } catch (e: Exception) {
            AppLog.e(TAG, Str.get(R.string.failed_to_generate_project_files_2), e)
            false
        }
    }

    private fun generatePluginJsonContent(): String {
        val json = JSONObject().apply {
            put("pluginId", pluginId.value)
            put("version", pluginVersion.value.toIntOrNull() ?: 1)
            put("versionName", pluginVersionName.value)
            put("minHostVersion", minHostVersion.value.toIntOrNull() ?: 1)
            put("name", pluginName.value)
            put("author", pluginAuthor.value)
            put("description", pluginDescription.value)
            put("icon", "icon.png")
            put("mainClass", if (uiType == "native") mainClass.value else "")
            put("apiLevel", apiLevel.value.toIntOrNull() ?: 21)
            put("uiType", uiType)
            put("entry", if (uiType == "web") entryPath.value else "")
            put("permissions", permissions.value.joinToString(","))
            put("dependencies", dependencies.value.split(",").map { it.trim() }.filter { it.isNotEmpty() }.joinToString(","))
            put("notice", pluginNotice.value)
            put("category", category.value)
            put("updateUrl", updateUrl.value)

            if (uiType == "web" && backendType.isNotEmpty()) {
                // 新式后端：统一 other 模式 + 必填启动命令，运行环境由用户在软件内全局设定
                put("backend", "other")
                put("backendStartCommand", backendStartCommand.value.trim().ifBlank { "sh scripts/start.sh" })
                put("backendStartEntry", "scripts/start.sh")
                put("backendAutoStart", true)
                put("backendTimeout", 30)
                put("backendHealthCheck", "/health")
            }

            // CUI 插件：无后端，打开终端时执行 pre-command 进入脚本
            if (uiType == "cui") {
                put("backendRuntime", backendRuntime.value.ifEmpty { "termux" })
                put("backendPreCommand", backendPreCommand.value.ifBlank { "python3 scripts/script.py" })
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
                "UI_TYPE" to when (uiType) {
                    "web" -> "WebView"
                    "cui" -> Str.get(R.string.cui_terminal)
                    else -> Str.get(R.string.native_code)
                }
            )
            val readme = TemplateUtils.generateReadme(context, vars)
            File(workDir, "README.md").writeText(readme)
        } catch (e: Exception) {
            AppLog.e(TAG, Str.get(R.string.failed_to_generate_readme), e)
        }
    }
}