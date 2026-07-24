// ui/screen/dev/PluginWizardUtils.kt
package com.UIN.Tool.ui.screen.dev

import android.content.Context
import android.net.Uri
import android.widget.Toast
import com.UIN.Tool.utils.AppLog
import com.UIN.Tool.utils.AppToast
import com.UIN.Tool.utils.Constants
import com.UIN.Tool.utils.FileUtils
import com.UIN.Tool.utils.TemplateUtils
import java.io.File

private const val TAG = "PluginWizardUtils"

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

fun generatePluginJson(
    uiType: String,
    pluginId: String,
    pluginVersion: String,
    pluginVersionName: String,
    pluginName: String,
    pluginAuthor: String,
    pluginDescription: String,
    mainClass: String,
    entryPath: String,
    pluginNotice: String,
    backendType: String = "",
    binaryFilePath: String = ""
): String {
    return if (uiType == "web") {
        val backendJson = if (backendType.isNotEmpty()) {
            val binaryEntry = if (backendType == "binary" && binaryFilePath.isNotEmpty()) {
                "backend/${File(binaryFilePath).name}"
            } else {
                entryPath
            }
            """
                "backend": "$backendType",
                "backendPort": 8000,
                "backendEntry": "$binaryEntry",
                "backendAutoStart": true,
                "backendTimeout": 30,
                "backendHealthCheck": "/health",
            """.trimIndent()
        } else ""
        
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
            "entry": "$entryPath",
            $backendJson
            "permissions": [],
            "dependencies": [],
            "notice": "$pluginNotice"
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
            "entry": "",
            "permissions": [],
            "dependencies": [],
            "notice": "$pluginNotice"
        }
        """.trimIndent()
    }
}

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
        AppLog.e(TAG, "生成README失败", e)
    }
}

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
        2 -> {
            if (mainClass.isNotEmpty() && !mainClass.contains(".")) {
                Toast.makeText(context, "主类名必须包含包名", Toast.LENGTH_SHORT).show()
                return false
            }
            true
        }
        else -> true
    }
}

fun formatFileSize(size: Long): String {
    return when {
        size < 1024 -> "$size B"
        size < 1024 * 1024 -> String.format("%.2f KB", size / 1024.0)
        else -> String.format("%.2f MB", size / (1024.0 * 1024.0))
    }
}

fun handleBinaryFileSelection(
    context: android.content.Context,
    uri: Uri,
    onSelected: (String) -> Unit
) {
    try {
        val tempDir = File(Constants.TEMP_DIR, "binary_${System.currentTimeMillis()}")
        tempDir.mkdirs()
        
        val fileName = uri.lastPathSegment?.substringAfterLast("/") ?: "myapp"
        val destFile = File(tempDir, fileName)
        
        if (FileUtils.copyUriToFile(context, uri, destFile)) {
            destFile.setExecutable(true)
            onSelected(destFile.absolutePath)
            AppLog.d(TAG, "二进制文件已保存: ${destFile.absolutePath}")
        } else {
            AppLog.e(TAG, "二进制文件复制失败")
        }
    } catch (e: Exception) {
        AppLog.e(TAG, "选择二进制文件失败", e)
        AppToast.error(context, "选择失败: ${e.message}")
    }
}

fun handleWebProjectImport(
    context: android.content.Context,
    uri: Uri,
    onSuccess: (List<String>, Map<String, String>) -> Unit
) {
    try {
        val tempFile = File(context.cacheDir, "web_import_${System.currentTimeMillis()}.zip")
        if (!FileUtils.copyUriToFile(context, uri, tempFile)) {
            AppToast.error(context, "无法读取文件")
            return
        }

        val extractDir = File(context.cacheDir, "web_extract_${System.currentTimeMillis()}")
        extractDir.mkdirs()

        if (FileUtils.unzipFile(tempFile, extractDir)) {
            val webDir = findWebDirectory(extractDir)
            if (webDir != null) {
                val files = mutableMapOf<String, String>()
                webDir.listFiles()?.forEach { file ->
                    if (file.isFile) {
                        val relativePath = "web/${file.name}"
                        files[relativePath] = file.readText()
                    }
                }
                webDir.listFiles()?.filter { it.isDirectory }?.forEach { subDir ->
                    subDir.listFiles()?.forEach { file ->
                        if (file.isFile) {
                            val relativePath = "web/${subDir.name}/${file.name}"
                            files[relativePath] = file.readText()
                        }
                    }
                }
                if (files.isNotEmpty()) {
                    onSuccess(files.keys.toList(), files)
                    AppLog.d(TAG, "Web项目导入成功，${files.size} 个文件")
                } else {
                    AppToast.warning(context, "Web项目为空")
                }
            } else {
                AppToast.warning(context, "未找到有效的Web项目")
            }
        } else {
            AppToast.error(context, "解压ZIP文件失败")
        }

        FileUtils.deleteRecursively(extractDir)
        tempFile.delete()

    } catch (e: Exception) {
        AppLog.e(TAG, "导入Web项目失败", e)
        AppToast.error(context, "导入失败: ${e.message}")
    }
}