// app/src/main/java/com/UIN/Tool/ui/screen/dev/PluginWizardViewModel.kt
package com.UIN.Tool.ui.screen.dev

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.UIN.Tool.log.Logger
import com.UIN.Tool.utils.AppLog
import com.UIN.Tool.utils.Constants
import com.UIN.Tool.utils.TemplateUtils
import java.io.File

private const val TAG = "PluginWizardViewModel"

class PluginWizardViewModel(
    private val context: Context,
    val uiType: String
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

    // ==================== 文件管理 ====================
    var fileList = mutableStateOf<List<String>>(emptyList())
    var fileContents = mutableStateOf<Map<String, String>>(emptyMap())

    // ==================== 图标和资源 ====================
    var iconPath = mutableStateOf("")
    var resourcePaths = mutableStateOf<List<String>>(emptyList())

    // ==================== 编译状态 ====================
    var isCompiling = mutableStateOf(false)
    var compileMessage = mutableStateOf("")
    var compileProgress = mutableStateOf(0)
    var tpkFile = mutableStateOf<File?>(null)
    var projectDir = mutableStateOf<File?>(null)

    fun initDefaultFiles() {
        if (fileList.value.isEmpty() || fileContents.value.isEmpty()) {
            if (uiType == "native") {
                generateNativeCode()
            } else {
                generateWebTemplates()
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
        val vars = mapOf(
            "PLUGIN_NAME" to pluginName.value,
            "PLUGIN_DESCRIPTION" to pluginDescription.value,
            "PLUGIN_ID" to pluginId.value
        )

        try {
            val indexHtml = TemplateUtils.renderTemplate(
                TemplateUtils.loadTemplate(context, "web/index.html"),
                vars
            )
            val styleCss = TemplateUtils.renderTemplate(
                TemplateUtils.loadTemplate(context, "web/style.css"),
                vars
            )
            val scriptJs = TemplateUtils.renderTemplate(
                TemplateUtils.loadTemplate(context, "web/script.js"),
                vars
            )

            val files = mutableMapOf<String, String>()
            files["web/index.html"] = indexHtml
            files["web/style.css"] = styleCss
            files["web/script.js"] = scriptJs

            fileList.value = files.keys.toList()
            fileContents.value = files

        } catch (e: Exception) {
            AppLog.e(TAG, "生成Web模板失败", e)
        }
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
        return if (uiType == "web") {
            """
            {
                "pluginId": "${pluginId.value}",
                "version": ${pluginVersion.value.toIntOrNull() ?: 1},
                "versionName": "${pluginVersionName.value}",
                "minHostVersion": 1,
                "name": "${pluginName.value}",
                "author": "${pluginAuthor.value}",
                "description": "${pluginDescription.value}",
                "icon": "icon.png",
                "mainClass": "",
                "apiLevel": 21,
                "uiType": "web",
                "entry": "${entryPath.value}"
            }
            """.trimIndent()
        } else {
            """
            {
                "pluginId": "${pluginId.value}",
                "version": ${pluginVersion.value.toIntOrNull() ?: 1},
                "versionName": "${pluginVersionName.value}",
                "minHostVersion": 1,
                "name": "${pluginName.value}",
                "author": "${pluginAuthor.value}",
                "description": "${pluginDescription.value}",
                "icon": "icon.png",
                "mainClass": "${mainClass.value}",
                "apiLevel": 21,
                "uiType": "native",
                "entry": ""
            }
            """.trimIndent()
        }
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