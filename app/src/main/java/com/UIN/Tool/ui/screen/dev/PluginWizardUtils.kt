package com.UIN.Tool.ui.screen.dev

import android.content.Context
import android.widget.Toast
import com.UIN.Tool.utils.TemplateUtils
import java.io.File

/**
 * 查找 Web 项目目录
 */
fun findWebDirectory(dir: File): File? {
    val webDir = File(dir, "web")
    if (webDir.exists() && webDir.isDirectory) return webDir

    dir.listFiles()?.forEach { file ->
        if (file.isDirectory) {
            val result = findWebDirectory(file)
            if (result != null) return result
        } else if (file.name.equals("index.html", ignoreCase = true)) {
            return dir
        }
    }
    return null
}

/**
 * 生成 plugin.json 内容
 */
fun generatePluginJson(
    uiType: String,
    pluginId: String,
    pluginVersion: String,
    pluginVersionName: String,
    pluginName: String,
    pluginAuthor: String,
    pluginDescription: String,
    mainClass: String,
    entryPath: String
): String {
    return if (uiType == "web") {
        """
        {
            "pluginId": "$pluginId",
            "version": ${pluginVersion.toIntOrNull() ?: 1},
            "versionName": "$pluginVersionName",
            "minHostVersion": 1,
            "name": "$pluginName",
            "author": "$pluginAuthor",
            "description": "$pluginDescription",
            "icon": "icon.png",
            "mainClass": "",
            "apiLevel": 21,
            "uiType": "web",
            "entry": "$entryPath"
        }
        """.trimIndent()
    } else {
        """
        {
            "pluginId": "$pluginId",
            "version": ${pluginVersion.toIntOrNull() ?: 1},
            "versionName": "$pluginVersionName",
            "minHostVersion": 1,
            "name": "$pluginName",
            "author": "$pluginAuthor",
            "description": "$pluginDescription",
            "icon": "icon.png",
            "mainClass": "$mainClass",
            "apiLevel": 21,
            "uiType": "native",
            "entry": ""
        }
        """.trimIndent()
    }
}

/**
 * 生成 README.md
 */
fun generateReadme(
    context: Context,
    workDir: File,
    pluginName: String,
    pluginId: String,
    pluginVersion: String,
    pluginVersionName: String,
    pluginAuthor: String,
    uiType: String
) {
    try {
        val vars = mapOf(
            "PLUGIN_NAME" to pluginName,
            "PLUGIN_ID" to pluginId,
            "PLUGIN_VERSION" to pluginVersion,
            "PLUGIN_VERSION_NAME" to pluginVersionName,
            "PLUGIN_AUTHOR" to pluginAuthor,
            "UI_TYPE" to if (uiType == "web") "WebView" else "原生代码"
        )
        val readme = TemplateUtils.generateReadme(context, vars)
        File(workDir, "README.md").writeText(readme)
    } catch (e: Exception) {
        // Log error
    }
}

/**
 * 验证当前步骤
 */
fun validateCurrentStep(
    currentStep: Int,
    pluginId: String,
    pluginName: String,
    mainClass: String,
    context: Context
): Boolean {
    return when (currentStep) {
        0 -> {
            if (pluginId.isEmpty() || pluginName.isEmpty()) {
                Toast.makeText(context, "请填写插件ID和名称", Toast.LENGTH_SHORT).show()
                return false
            }
            if (!pluginId.matches(Regex("^[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+$"))) {
                Toast.makeText(context, "插件ID格式不正确，应为域名倒序格式", Toast.LENGTH_SHORT).show()
                return false
            }
            true
        }
        1 -> true // 图标步骤可选
        2 -> true // 代码步骤可选
        3 -> true // 资源步骤可选
        else -> true
    }
}

/**
 * 格式化文件大小
 */
fun formatFileSize(size: Long): String {
    return when {
        size < 1024 -> "$size B"
        size < 1024 * 1024 -> String.format("%.2f KB", size / 1024.0)
        else -> String.format("%.2f MB", size / (1024.0 * 1024.0))
    }
}