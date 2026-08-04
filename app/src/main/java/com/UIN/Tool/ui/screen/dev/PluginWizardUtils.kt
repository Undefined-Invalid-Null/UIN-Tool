// ui/screen/dev/PluginWizardUtils.kt
package com.UIN.Tool.ui.screen.dev

import com.UIN.Tool.R
import com.UIN.Tool.utils.Str
import android.content.Context
import android.net.Uri
import android.widget.Toast
import com.UIN.Tool.utils.AppLog
import com.UIN.Tool.utils.AppToast
import com.UIN.Tool.constants.AppConstants as Constants
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
            "UI_TYPE" to if (uiType == "web") "WebView" else Str.get(R.string.native_code)
        )
        val readme = TemplateUtils.generateReadme(context, vars)
        File(workDir, "README.md").writeText(readme)
    } catch (e: Exception) {
        AppLog.e(TAG, Str.get(R.string.failed_to_generate_readme), e)
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
                Toast.makeText(context, Str.get(R.string.please_fill_in_the_plugin_id_and_nam), Toast.LENGTH_SHORT).show()
                return false
            }
            if (!pluginId.matches(Regex("^[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+$"))) {
                Toast.makeText(context, Str.get(R.string.invalid_plugin_id_it_should_be_a_rev), Toast.LENGTH_SHORT).show()
                return false
            }
            true
        }
        2 -> {
            if (mainClass.isNotEmpty() && !mainClass.contains(".")) {
                Toast.makeText(context, Str.get(R.string.the_main_class_name_must_include_the_2), Toast.LENGTH_SHORT).show()
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
            AppLog.d(TAG, Str.get(R.string.binary_file_saved_destfile_absolutep, destFile.absolutePath))
        } else {
            AppLog.e(TAG, Str.get(R.string.failed_to_copy_binary_file))
        }
    } catch (e: Exception) {
        AppLog.e(TAG, Str.get(R.string.failed_to_select_binary_file), e)
        AppToast.error(context, Str.get(R.string.selection_failed_e_message, e.message))
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
            AppToast.error(context, Str.get(R.string.failed_to_read_file))
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
                    AppLog.d(TAG, Str.get(R.string.web_project_imported_files_size_file_2, files.size))
                } else {
                    AppToast.warning(context, Str.get(R.string.web_project_is_empty))
                }
            } else {
                AppToast.warning(context, Str.get(R.string.no_valid_web_project_found))
            }
        } else {
            AppToast.error(context, Str.get(R.string.failed_to_extract_zip_file))
        }

        FileUtils.deleteRecursively(extractDir)
        tempFile.delete()

    } catch (e: Exception) {
        AppLog.e(TAG, Str.get(R.string.failed_to_import_web_project), e)
        AppToast.error(context, Str.get(R.string.import_failed_e_message, e.message))
    }
}