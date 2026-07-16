// app/src/main/java/com/UIN/Tool/ui/screen/backup/BackupScreen.kt
package com.UIN.Tool.ui.screen.backup

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.UIN.Tool.core.di.ServiceLocator
import com.UIN.Tool.domain.model.BackupInfo
import com.UIN.Tool.log.Logger
import com.UIN.Tool.plugin.PluginManager
import com.UIN.Tool.ui.components.UIComponents
import com.UIN.Tool.utils.*
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

private fun formatFileSize(size: Long): String {
    return when {
        size < 1024 -> "$size B"
        size < 1024 * 1024 -> String.format("%.2f KB", size / 1024.0)
        else -> String.format("%.2f MB", size / (1024.0 * 1024.0))
    }
}

private fun addFileToZip(
    zos: java.util.zip.ZipOutputStream,
    file: File,
    entryName: String
) {
    if (!file.exists()) return
    try {
        zos.putNextEntry(java.util.zip.ZipEntry(entryName))
        java.io.FileInputStream(file).use { fis ->
            fis.copyTo(zos)
        }
        zos.closeEntry()
    } catch (e: Exception) {
        AppLog.e("Backup", "添加文件到ZIP失败: $entryName", e)
    }
}

private fun addDirToZip(
    zos: java.util.zip.ZipOutputStream,
    dir: File,
    basePath: String
) {
    if (!dir.exists()) return
    dir.listFiles()?.forEach { file ->
        if (file.name.startsWith(".")) return@forEach
        val entryName = basePath + file.name
        if (file.isDirectory()) {
            zos.putNextEntry(java.util.zip.ZipEntry("$entryName/"))
            zos.closeEntry()
            addDirToZip(zos, file, "$entryName/")
        } else {
            try {
                zos.putNextEntry(java.util.zip.ZipEntry(entryName))
                java.io.FileInputStream(file).use { fis ->
                    fis.copyTo(zos)
                }
                zos.closeEntry()
            } catch (e: Exception) {
                AppLog.e("Backup", "添加文件失败: $entryName", e)
            }
        }
    }
}

@Composable
fun BackupScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pluginManager = ServiceLocator.getPluginManager()

    var backups by remember { mutableStateOf<List<BackupInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var progressMessage by remember { mutableStateOf("") }
    var progressValue by remember { mutableStateOf(0f) }
    var showProgress by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<BackupInfo?>(null) }
    var restoreTarget by remember { mutableStateOf<BackupInfo?>(null) }
    var showRestoreConfirm by remember { mutableStateOf(false) }

    var includeUiConfig by remember { mutableStateOf(true) }
    var includeSettings by remember { mutableStateOf(true) }

    val backupDir = File(Constants.BACKUP_DIR)

    fun loadBackups() {
        try {
            if (!backupDir.exists()) backupDir.mkdirs()

            val files = backupDir.listFiles()
                ?.filter { it.isFile && it.name.endsWith(".zip") }
                ?.map { file ->
                    val pluginCount = try {
                        val parts = file.nameWithoutExtension.split("_")
                        parts.getOrNull(parts.size - 2)?.toIntOrNull() ?: 0
                    } catch (e: Exception) { 0 }

                    BackupInfo(
                        file = file,
                        name = file.name,
                        size = file.length(),
                        date = file.lastModified(),
                        pluginCount = pluginCount
                    )
                }
                ?.sortedByDescending { it.date }
                ?: emptyList()

            backups = files
            AppLog.d("BackupScreen", "加载了 ${files.size} 个备份文件")
        } catch (e: Exception) {
            AppLog.e("BackupScreen", "加载备份列表失败", e)
        }
    }

    fun createBackup() {
        scope.launch {
            try {
                AppLog.i("Backup", "========== 开始创建备份 ==========")
                isLoading = true
                showProgress = true
                progressValue = 0f
                progressMessage = "正在创建备份..."

                val plugins = pluginManager.plugins.value
                val pluginDir = File(Constants.PLUGIN_DIR)

                if (!pluginDir.exists()) {
                    progressMessage = "没有插件可备份"
                    showProgress = false
                    isLoading = false
                    AppToast.info(context, "没有插件可备份")
                    return@launch
                }

                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val backupFile = File(backupDir, "UIN_Tool_Backup_${plugins.size}_$timestamp.zip")

                java.util.zip.ZipOutputStream(
                    java.io.FileOutputStream(backupFile)
                ).use { zos ->

                    progressValue = 0.1f
                    progressMessage = "正在打包 ${plugins.size} 个插件..."
                    addDirToZip(zos, pluginDir, "plugins/")
                    AppLog.d("Backup", "插件目录已打包")

                    if (includeUiConfig) {
                        progressValue = 0.4f
                        progressMessage = "正在备份 UI 配置..."
                        val uiConfigFile = File(context.filesDir, "ui_config.json")
                        if (uiConfigFile.exists()) {
                            addFileToZip(zos, uiConfigFile, "config/ui_config.json")
                            AppLog.d("Backup", "UI 配置已备份")
                        } else {
                            AppLog.w("Backup", "UI 配置文件不存在")
                        }
                    }

                    if (includeSettings) {
                        progressValue = 0.55f
                        progressMessage = "正在备份应用设置..."
                        val prefsDir = File(context.filesDir.parent, "shared_prefs")
                        if (prefsDir.exists()) {
                            prefsDir.listFiles()?.forEach { prefFile ->
                                if (prefFile.name.endsWith(".xml")) {
                                    addFileToZip(
                                        zos,
                                        prefFile,
                                        "config/shared_prefs/${prefFile.name}"
                                    )
                                }
                            }
                            AppLog.d("Backup", "SharedPreferences 已备份")
                        }
                    }

                    progressValue = 0.7f
                    progressMessage = "正在备份工作目录配置..."
                    val configJson = org.json.JSONObject().apply {
                        put("work_folder", Constants.WORK_DIR)
                        put("backup_time", timestamp)
                        put("plugin_count", plugins.size)
                        put("app_version", Constants.APP_VERSION)
                        put("app_version_code", Constants.APP_VERSION_CODE)
                    }
                    val tempConfig = File(context.cacheDir, "temp_config.json")
                    tempConfig.writeText(configJson.toString(4))
                    addFileToZip(zos, tempConfig, "config/work_folder.json")
                    tempConfig.delete()

                    if (includeSettings) {
                        progressValue = 0.8f
                        progressMessage = "正在备份应用设置..."
                        val prefs = context.getSharedPreferences("uin_tool_prefs", Context.MODE_PRIVATE)
                        val settings = org.json.JSONObject().apply {
                            put("view_mode", prefs.getString("view_mode", "list"))
                            put("use_custom_icon_tint", prefs.getBoolean("use_custom_icon_tint", true))
                            put("current_theme", prefs.getString("current_theme", "default"))
                        }
                        val tempSettings = File(context.cacheDir, "temp_settings.json")
                        tempSettings.writeText(settings.toString(4))
                        addFileToZip(zos, tempSettings, "config/settings.json")
                        tempSettings.delete()
                    }

                    progressValue = 0.85f
                    progressMessage = "正在备份镜像配置..."
                    val mirrorPrefs = context.getSharedPreferences("github_mirror", Context.MODE_PRIVATE)
                    val mirrorConfig = org.json.JSONObject().apply {
                        put("enabled_mirrors", mirrorPrefs.getString("enabled_mirrors", ""))
                        put("use_cdn", mirrorPrefs.getBoolean("use_cdn", true))
                    }
                    val tempMirror = File(context.cacheDir, "temp_mirror.json")
                    tempMirror.writeText(mirrorConfig.toString(4))
                    addFileToZip(zos, tempMirror, "config/github_mirror.json")
                    tempMirror.delete()
                }

                progressValue = 0.95f
                progressMessage = "备份完成！"
                progressValue = 1f

                loadBackups()
                AppLog.success("Backup", "备份创建成功")
                AppLog.d("Backup", "备份文件大小: ${formatFileSize(backupFile.length())}")
                AppToast.success(context, "备份成功 (${formatFileSize(backupFile.length())})")

            } catch (e: Exception) {
                AppLog.e("Backup", "创建备份异常", e)
                progressMessage = "备份失败: ${e.message}"
                AppToast.error(context, "备份失败: ${e.message}")
            } finally {
                showProgress = false
                isLoading = false
                progressValue = 0f
            }
        }
    }

    fun restoreBackup(backup: BackupInfo) {
        scope.launch {
            try {
                AppLog.i("Backup", "========== 开始恢复备份 ==========")
                isLoading = true
                showProgress = true
                progressValue = 0f
                progressMessage = "正在恢复备份..."

                val tempDir = File(Constants.TEMP_DIR, "restore_${System.currentTimeMillis()}")
                tempDir.mkdirs()

                progressValue = 0.1f
                progressMessage = "解压备份文件..."

                java.util.zip.ZipFile(backup.file).use { zipFile ->
                    val entries = zipFile.entries()
                    while (entries.hasMoreElements()) {
                        val entry = entries.nextElement()
                        val targetFile = File(tempDir, entry.name)
                        if (entry.isDirectory) {
                            targetFile.mkdirs()
                        } else {
                            targetFile.parentFile?.mkdirs()
                            zipFile.getInputStream(entry).use { input ->
                                targetFile.outputStream().use { output ->
                                    input.copyTo(output)
                                }
                            }
                        }
                    }
                }

                progressValue = 0.3f
                progressMessage = "解压完成，准备恢复..."

                val pluginsBackup = File(tempDir, "plugins")
                if (pluginsBackup.exists()) {
                    progressValue = 0.4f
                    progressMessage = "正在恢复插件..."
                    val pluginDir = File(Constants.PLUGIN_DIR)
                    if (pluginDir.exists()) {
                        FileUtils.deleteRecursively(pluginDir)
                    }
                    pluginDir.mkdirs()
                    FileUtils.copyDirectory(pluginsBackup, pluginDir)
                    AppLog.success("Backup", "插件恢复完成")
                }

                val uiConfigBackup = File(tempDir, "config/ui_config.json")
                if (uiConfigBackup.exists()) {
                    progressValue = 0.6f
                    progressMessage = "正在恢复 UI 配置..."
                    val destUiConfig = File(context.filesDir, "ui_config.json")
                    uiConfigBackup.copyTo(destUiConfig, overwrite = true)

                    val uiConfig = UIConfig.getInstance()
                    try {
                        val configJson = destUiConfig.readText()
                        val obj = org.json.JSONObject(configJson)
                        val theme = obj.optJSONObject("theme")
                        if (theme != null) {
                            val keys = theme.keys()
                            while (keys.hasNext()) {
                                val key = keys.next()
                                val value = theme.getString(key)
                                when (key) {
                                    "primary" -> uiConfig.updateColor("primary", value)
                                    "primary_dark" -> uiConfig.updateColor("primary_dark", value)
                                    "primary_light" -> uiConfig.updateColor("primary_light", value)
                                    "accent" -> uiConfig.updateColor("accent", value)
                                    "success" -> uiConfig.updateColor("success", value)
                                    "warning" -> uiConfig.updateColor("warning", value)
                                    "error" -> uiConfig.updateColor("error", value)
                                    "info" -> uiConfig.updateColor("info", value)
                                    "text_primary" -> uiConfig.updateColor("text_primary", value)
                                    "text_secondary" -> uiConfig.updateColor("text_secondary", value)
                                    "text_hint" -> uiConfig.updateColor("text_hint", value)
                                    "text_primary_inverse" -> uiConfig.updateColor("text_primary_inverse", value)
                                    "background" -> uiConfig.updateColor("background", value)
                                    "surface" -> uiConfig.updateColor("surface", value)
                                    "surface_variant" -> uiConfig.updateColor("surface_variant", value)
                                    "divider" -> uiConfig.updateColor("divider", value)
                                    "glass_background" -> uiConfig.updateColor("glass_background", value)
                                    "disabled" -> uiConfig.updateColor("disabled", value)
                                }
                            }
                        }
                        val shape = obj.optJSONObject("shape")
                        if (shape != null) {
                            uiConfig.updateShape("cornerRadiusSmall", shape.optInt("cornerRadiusSmall", 8))
                            uiConfig.updateShape("cornerRadiusMedium", shape.optInt("cornerRadiusMedium", 12))
                            uiConfig.updateShape("cornerRadiusLarge", shape.optInt("cornerRadiusLarge", 16))
                            uiConfig.updateShape("cornerRadiusExtraLarge", shape.optInt("cornerRadiusExtraLarge", 24))
                            uiConfig.updateShape("buttonCornerRadius", shape.optInt("buttonCornerRadius", 12))
                            uiConfig.updateShape("cardCornerRadius", shape.optInt("cardCornerRadius", 16))
                            uiConfig.updateShape("dialogCornerRadius", shape.optInt("dialogCornerRadius", 20))
                            uiConfig.updateShape("inputCornerRadius", shape.optInt("inputCornerRadius", 8))
                        }
                        val size = obj.optJSONObject("size")
                        if (size != null) {
                            uiConfig.updateSize("buttonHeight", size.optInt("buttonHeight", 44))
                            uiConfig.updateSize("buttonMinWidth", size.optInt("buttonMinWidth", 80))
                            uiConfig.updateSize("buttonElevation", size.optInt("buttonElevation", 2))
                            uiConfig.updateSize("cardElevation", size.optInt("cardElevation", 4))
                            uiConfig.updateSize("cardPadding", size.optInt("cardPadding", 16))
                            uiConfig.updateSize("spacingSmall", size.optInt("spacingSmall", 4))
                            uiConfig.updateSize("spacingMedium", size.optInt("spacingMedium", 8))
                            uiConfig.updateSize("spacingLarge", size.optInt("spacingLarge", 16))
                            uiConfig.updateSize("iconSizeSmall", size.optInt("iconSizeSmall", 16))
                            uiConfig.updateSize("iconSizeMedium", size.optInt("iconSizeMedium", 20))
                            uiConfig.updateSize("iconSizeLarge", size.optInt("iconSizeLarge", 24))
                            uiConfig.updateSize("progressHeight", size.optInt("progressHeight", 4))
                            uiConfig.updateSize("titleTextSize", size.optInt("titleTextSize", 20))
                            uiConfig.updateSize("bodyTextSize", size.optInt("bodyTextSize", 14))
                            uiConfig.updateSize("captionTextSize", size.optInt("captionTextSize", 12))
                            uiConfig.updateSize("sectionTitleTextSize", size.optInt("sectionTitleTextSize", 18))
                        }
                        val experimental = obj.optJSONObject("experimental")
                        if (experimental != null) {
                            uiConfig.updateBoolean("enableGlassEffect", experimental.optBoolean("enableGlassEffect", true))
                            uiConfig.updateBoolean("enableRipple", experimental.optBoolean("enableRipple", true))
                        }
                        val font = obj.optJSONObject("font")
                        if (font != null) {
                            uiConfig.updateBoolean("enableBold", font.optBoolean("enableBold", true))
                        }
                        uiConfig.saveConfig()
                        AppLog.success("Backup", "UI 配置恢复完成")
                    } catch (e: Exception) {
                        AppLog.e("Backup", "恢复UI配置失败", e)
                    }
                }

                val prefsBackupDir = File(tempDir, "config/shared_prefs")
                if (prefsBackupDir.exists()) {
                    progressValue = 0.7f
                    progressMessage = "正在恢复应用设置..."
                    val destPrefsDir = File(context.filesDir.parent, "shared_prefs")
                    destPrefsDir.mkdirs()
                    prefsBackupDir.listFiles()?.forEach { prefFile ->
                        val destFile = File(destPrefsDir, prefFile.name)
                        prefFile.copyTo(destFile, overwrite = true)
                    }
                    AppLog.success("Backup", "SharedPreferences 恢复完成")
                }

                val workFolderBackup = File(tempDir, "config/work_folder.json")
                if (workFolderBackup.exists()) {
                    progressValue = 0.8f
                    progressMessage = "正在恢复工作目录配置..."
                    val json = workFolderBackup.readText()
                    try {
                        val config = org.json.JSONObject(json)
                        val workFolder = config.optString("work_folder")
                        if (workFolder.isNotEmpty()) {
                            val prefs = context.getSharedPreferences("uin_tool_prefs", Context.MODE_PRIVATE)
                            prefs.edit().putString("work_folder", workFolder).apply()
                        }
                    } catch (e: Exception) {
                        AppLog.e("Backup", "解析工作目录配置失败", e)
                    }
                }

                val settingsBackup = File(tempDir, "config/settings.json")
                if (settingsBackup.exists()) {
                    progressValue = 0.85f
                    progressMessage = "正在恢复应用设置..."
                    val json = settingsBackup.readText()
                    try {
                        val settings = org.json.JSONObject(json)
                        val prefs = context.getSharedPreferences("uin_tool_prefs", Context.MODE_PRIVATE)
                        prefs.edit().apply {
                            putString("view_mode", settings.optString("view_mode", "list"))
                            putBoolean("use_custom_icon_tint", settings.optBoolean("use_custom_icon_tint", true))
                            putString("current_theme", settings.optString("current_theme", "default"))
                        }.apply()
                    } catch (e: Exception) {
                        AppLog.e("Backup", "解析应用设置失败", e)
                    }
                }

                val mirrorBackup = File(tempDir, "config/github_mirror.json")
                if (mirrorBackup.exists()) {
                    progressValue = 0.9f
                    progressMessage = "正在恢复镜像配置..."
                    val json = mirrorBackup.readText()
                    try {
                        val config = org.json.JSONObject(json)
                        val prefs = context.getSharedPreferences("github_mirror", Context.MODE_PRIVATE)
                        prefs.edit().apply {
                            putString("enabled_mirrors", config.optString("enabled_mirrors", ""))
                            putBoolean("use_cdn", config.optBoolean("use_cdn", true))
                        }.apply()
                    } catch (e: Exception) {
                        AppLog.e("Backup", "解析镜像配置失败", e)
                    }
                }

                progressValue = 0.95f
                progressMessage = "清理临时文件..."
                FileUtils.deleteRecursively(tempDir)

                progressMessage = "刷新插件列表..."
                pluginManager.refreshPlugins()

                progressValue = 1f
                progressMessage = "恢复完成！"

                loadBackups()
                AppLog.success("Backup", "备份恢复成功")
                AppToast.success(context, "恢复成功")

            } catch (e: Exception) {
                AppLog.e("Backup", "恢复备份异常", e)
                progressMessage = "恢复失败: ${e.message}"
                AppToast.error(context, "恢复失败: ${e.message}")
            } finally {
                showProgress = false
                isLoading = false
                showRestoreConfirm = false
                progressValue = 0f
            }
        }
    }

    fun deleteBackup(backup: BackupInfo) {
        try {
            if (backup.file.delete()) {
                loadBackups()
                AppToast.success(context, "已删除")
                AppLog.i("Backup", "删除备份: ${backup.name}")
            } else {
                AppToast.error(context, "删除失败")
            }
        } catch (e: Exception) {
            AppLog.e("BackupScreen", "删除备份失败", e)
            AppToast.error(context, "删除失败: ${e.message}")
        }
    }

    LaunchedEffect(Unit) {
        loadBackups()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            UIComponents.TitleText("备份恢复")

            if (!isLoading) {
                UIComponents.IconButton(
                    icon = Icons.Default.Refresh,
                    onClick = { loadBackups() }
                )
            }
        }

        UIComponents.PrimaryButton(
            text = "创建备份",
            icon = Icons.Default.Backup,
            onClick = { createBackup() },
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            enabled = !isLoading
        )

        // 备份选项
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                Text(
                    text = "备份选项",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = includeUiConfig,
                        onCheckedChange = { includeUiConfig = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = MaterialTheme.colorScheme.primary,
                            uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                    Text("包含 UI 配置", color = MaterialTheme.colorScheme.onSurface)
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = includeSettings,
                        onCheckedChange = { includeSettings = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = MaterialTheme.colorScheme.primary,
                            uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                    Text("包含应用设置", color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }

        if (showProgress) {
            UIComponents.Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                ) {
                    UIComponents.LinearProgressIndicator(progress = progressValue)
                    Spacer(modifier = Modifier.height(4.dp))
                    UIComponents.CaptionText(progressMessage)
                }
            }
        }

        if (backups.isEmpty() && !showProgress) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Backup,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    UIComponents.TitleText("暂无备份文件")
                    UIComponents.BodyText("点击「创建备份」来备份您的插件")
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(backups) { backup ->
                    BackupItemCardCompact(
                        backup = backup,
                        onRestore = {
                            restoreTarget = backup
                            showRestoreConfirm = true
                        },
                        onDelete = { deleteTarget = backup }
                    )
                }
            }
        }
    }

    deleteTarget?.let { backup ->
        UIComponents.ConfirmDialog(
            title = "确认删除",
            message = "确定要删除备份 \"${backup.name}\" 吗？",
            onConfirm = {
                deleteBackup(backup)
                deleteTarget = null
            },
            onDismiss = { deleteTarget = null }
        )
    }

    if (showRestoreConfirm && restoreTarget != null) {
        UIComponents.ConfirmDialog(
            title = "确认恢复",
            message = "恢复操作将覆盖现有插件、配置和 UI 主题！\n\n确定要继续吗？",
            onConfirm = {
                restoreTarget?.let { restoreBackup(it) }
                restoreTarget = null
            },
            onDismiss = {
                showRestoreConfirm = false
                restoreTarget = null
            }
        )
    }
}

@Composable
fun BackupItemCardCompact(
    backup: BackupInfo,
    onRestore: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = backup.name,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 13.sp
                    )
                )

                Spacer(modifier = Modifier.height(2.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = backup.getFormattedDate(),
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.sp
                        )
                    )
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.sp
                        )
                    )
                    Text(
                        text = backup.getFormattedSize(),
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.sp
                        )
                    )
                    if (backup.pluginCount > 0) {
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 10.sp
                            )
                        )
                        Text(
                            text = "${backup.pluginCount}个",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 10.sp
                            )
                        )
                    }
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Button(
                    onClick = onRestore,
                    modifier = Modifier
                        .height(28.dp)
                        .width(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = RoundedCornerShape(4.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp)
                ) {
                    Text(
                        text = "恢复",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Button(
                    onClick = onDelete,
                    modifier = Modifier
                        .height(28.dp)
                        .width(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    ),
                    shape = RoundedCornerShape(4.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp)
                ) {
                    Text(
                        text = "删除",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}