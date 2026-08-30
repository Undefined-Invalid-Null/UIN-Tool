// app/src/main/java/com/UIN/Tool/ui/screen/backup/BackupScreen.kt
package com.UIN.Tool.ui.screen.backup

import com.UIN.Tool.R
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.UIN.Tool.constants.AppConstants as Constants
import com.UIN.Tool.core.di.ServiceLocator
import com.UIN.Tool.domain.model.BackupInfo
import com.UIN.Tool.log.Logger
import com.UIN.Tool.plugin.PluginManager
import com.UIN.Tool.ui.components.UIComponents
import com.UIN.Tool.ui.components.unified.UnifiedBodyText
import com.UIN.Tool.ui.components.unified.UnifiedButton
import com.UIN.Tool.ui.components.unified.UnifiedCaptionText
import com.UIN.Tool.ui.components.unified.UnifiedCard
import com.UIN.Tool.ui.components.unified.UnifiedConfirmDialog
import com.UIN.Tool.ui.components.unified.UnifiedDialog
import com.UIN.Tool.ui.components.unified.UnifiedDialogTextButton
import com.UIN.Tool.ui.components.unified.UnifiedLinearProgressIndicator
import com.UIN.Tool.ui.components.unified.UnifiedTitleText
import com.UIN.Tool.ui.components.unified.ButtonVariant
import com.UIN.Tool.ui.components.unified.ButtonSize
import com.UIN.Tool.utils.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import com.UIN.Tool.ui.theme.AppColors
import com.UIN.Tool.ui.theme.AppDimens

enum class RestoreMode { MERGE, REPLACE }

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
        AppLog.e("Backup", Str.get(R.string.backup_add_zip_failed_entry, entryName), e)
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
                AppLog.e("Backup", Str.get(R.string.failed_to_add_file_entryname, entryName), e)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pluginManager = ServiceLocator.getPluginManager()

    var backups by remember { mutableStateOf<List<BackupInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }
    var lastRefreshTime by remember { mutableStateOf<String?>(null) }
    val pullRefreshState = rememberPullToRefreshState()
    var progressMessage by remember { mutableStateOf("") }
    var progressValue by remember { mutableStateOf(0f) }
    var showProgress by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<BackupInfo?>(null) }
    var restoreTarget by remember { mutableStateOf<BackupInfo?>(null) }
    var showRestoreConfirm by remember { mutableStateOf(false) }
    var restoreMode by remember { mutableStateOf(RestoreMode.MERGE) }

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
            AppLog.d("BackupScreen", Str.get(R.string.loaded_files_size_backup_files, files.size))
        } catch (e: Exception) {
            AppLog.e("BackupScreen", Str.get(R.string.failed_to_load_backup_list), e)
        }
    }

    fun createBackup() {
        scope.launch {
            try {
                AppLog.i("Backup", Str.get(R.string.start_creating_backup))
                isLoading = true
                showProgress = true
                progressValue = 0f
                progressMessage = Str.get(R.string.creating_backup)

                val plugins = pluginManager.plugins.value
                val pluginDir = File(Constants.PLUGIN_DIR)

                if (!pluginDir.exists()) {
                    progressMessage = Str.get(R.string.no_plugins_to_back_up)
                    showProgress = false
                    isLoading = false
                    AppToast.info(context, Str.get(R.string.no_plugins_to_back_up))
                    return@launch
                }

                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val backupFile = File(backupDir, "UIN_Tool_Backup_${plugins.size}_$timestamp.zip")

                java.util.zip.ZipOutputStream(
                    java.io.FileOutputStream(backupFile)
                ).use { zos ->

                    progressValue = 0.1f
                    progressMessage = Str.get(R.string.packing_plugins_size_plugin_s, plugins.size)
                    addDirToZip(zos, pluginDir, "plugins/")
                    AppLog.d("Backup", Str.get(R.string.plugin_directory_packed))

                    if (includeUiConfig) {
                        progressValue = 0.4f
                        progressMessage = Str.get(R.string.backing_up_ui_config)
                        // ✅ 直接序列化 UIConfig.config（实际存储为 SharedPreferences，
                        //    原先备份 filesDir/ui_config.json 从不存在的双轨不一致问题）
                        if (UIConfig.isInitialized()) {
                            val tempUiConfig = File(context.cacheDir, "temp_ui_config.json")
                            tempUiConfig.writeText(UIConfig.getInstance().getConfig().toString(4))
                            addFileToZip(zos, tempUiConfig, "config/ui_config.json")
                            tempUiConfig.delete()
                            AppLog.d("Backup", Str.get(R.string.ui_config_backed_up))
                        } else {
                            AppLog.w("Backup", Str.get(R.string.ui_config_file_does_not_exist))
                        }
                    }

                    if (includeSettings) {
                        progressValue = 0.55f
                        progressMessage = Str.get(R.string.backing_up_app_settings)
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
                            AppLog.d("Backup", Str.get(R.string.sharedpreferences_backed_up))
                        }
                    }

                    progressValue = 0.7f
                    progressMessage = Str.get(R.string.backing_up_work_directory_config)
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
                        progressMessage = Str.get(R.string.backing_up_app_settings)
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
                    progressMessage = Str.get(R.string.backing_up_mirror_config)
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
                progressMessage = Str.get(R.string.backup_complete)
                progressValue = 1f

                loadBackups()
                AppLog.success("Backup", Str.get(R.string.backup_created_successfully))
                AppLog.d("Backup", Str.get(R.string.backup_file_size_formatfilesize_back, formatFileSize(backupFile.length())))
                AppToast.success(context, Str.get(R.string.backup_successful_formatfilesize_bac, formatFileSize(backupFile.length())))

            } catch (e: Exception) {
                AppLog.e("Backup", Str.get(R.string.backup_creation_exception), e)
                progressMessage = Str.get(R.string.backup_failed_e_message, e.message)
                AppToast.error(context, Str.get(R.string.backup_failed_e_message, e.message))
            } finally {
                showProgress = false
                isLoading = false
                progressValue = 0f
            }
        }
    }

    fun restoreBackup(backup: BackupInfo, mode: RestoreMode = RestoreMode.MERGE) {
        scope.launch {
            try {
                AppLog.i("Backup", Str.get(R.string.start_restoring_backup))
                isLoading = true
                showProgress = true
                progressValue = 0f
                progressMessage = Str.get(R.string.restoring_backup)

                val tempDir = File(Constants.TEMP_DIR, "restore_${System.currentTimeMillis()}")
                tempDir.mkdirs()

                progressValue = 0.1f
                progressMessage = Str.get(R.string.extracting_backup_file)

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
                progressMessage = Str.get(R.string.extraction_complete_preparing_to_res)

                val pluginsBackup = File(tempDir, "plugins")
                if (pluginsBackup.exists()) {
                    progressValue = 0.4f
                    progressMessage = Str.get(R.string.restoring_plugins)
                    val pluginDir = File(Constants.PLUGIN_DIR)
                    if (mode == RestoreMode.REPLACE && pluginDir.exists()) {
                        FileUtils.deleteRecursively(pluginDir)
                    }
                    pluginDir.mkdirs()
                    FileUtils.copyDirectory(pluginsBackup, pluginDir)
                    AppLog.success("Backup", Str.get(R.string.plugin_restore_complete))
                }

                val uiConfigBackup = File(tempDir, "config/ui_config.json")
                if (uiConfigBackup.exists()) {
                    progressValue = 0.6f
                    progressMessage = Str.get(R.string.restoring_ui_config)

                    val uiConfig = UIConfig.getInstance()
                    try {
                        // ✅ 一次性反序列化 + applyJson 原子写入，
                        //    替代原先逐 key update* 全量序列化 + 半途失败配置残缺
                        val obj = org.json.JSONObject(uiConfigBackup.readText())
                        uiConfig.applyJson(obj)
                        AppLog.success("Backup", Str.get(R.string.ui_config_restore_complete))
                    } catch (e: Exception) {
                        AppLog.e("Backup", Str.get(R.string.failed_to_restore_ui_config), e)
                    }
                }

                val prefsBackupDir = File(tempDir, "config/shared_prefs")
                if (prefsBackupDir.exists()) {
                    progressValue = 0.7f
                    progressMessage = Str.get(R.string.restoring_app_settings)
                    val destPrefsDir = File(context.filesDir.parent, "shared_prefs")
                    destPrefsDir.mkdirs()
                    prefsBackupDir.listFiles()?.forEach { prefFile ->
                        val destFile = File(destPrefsDir, prefFile.name)
                        prefFile.copyTo(destFile, overwrite = true)
                    }
                    AppLog.success("Backup", Str.get(R.string.sharedpreferences_restore_complete))
                }

                val workFolderBackup = File(tempDir, "config/work_folder.json")
                if (workFolderBackup.exists()) {
                    progressValue = 0.8f
                    progressMessage = Str.get(R.string.restoring_work_directory_config)
                    val json = workFolderBackup.readText()
                    try {
                        val config = org.json.JSONObject(json)
                        val workFolder = config.optString("work_folder")
                        if (workFolder.isNotEmpty()) {
                            val prefs = context.getSharedPreferences("uin_tool_prefs", Context.MODE_PRIVATE)
                            prefs.edit().putString("work_folder", workFolder).apply()
                        }
                    } catch (e: Exception) {
                        AppLog.e("Backup", Str.get(R.string.failed_to_parse_work_directory_confi), e)
                    }
                }

                val settingsBackup = File(tempDir, "config/settings.json")
                if (settingsBackup.exists()) {
                    progressValue = 0.85f
                    progressMessage = Str.get(R.string.restoring_app_settings)
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
                        AppLog.e("Backup", Str.get(R.string.failed_to_parse_app_settings), e)
                    }
                }

                val mirrorBackup = File(tempDir, "config/github_mirror.json")
                if (mirrorBackup.exists()) {
                    progressValue = 0.9f
                    progressMessage = Str.get(R.string.restoring_mirror_config)
                    val json = mirrorBackup.readText()
                    try {
                        val config = org.json.JSONObject(json)
                        val prefs = context.getSharedPreferences("github_mirror", Context.MODE_PRIVATE)
                        prefs.edit().apply {
                            putString("enabled_mirrors", config.optString("enabled_mirrors", ""))
                            putBoolean("use_cdn", config.optBoolean("use_cdn", true))
                        }.apply()
                    } catch (e: Exception) {
                        AppLog.e("Backup", Str.get(R.string.failed_to_parse_mirror_config), e)
                    }
                }

                progressValue = 0.95f
                progressMessage = Str.get(R.string.cleaning_up_temporary_files)
                FileUtils.deleteRecursively(tempDir)

                progressMessage = Str.get(R.string.refreshing_plugin_list)
                pluginManager.refreshPlugins()

                progressValue = 1f
                progressMessage = Str.get(R.string.restore_complete)

                loadBackups()
                AppLog.success("Backup", Str.get(R.string.backup_restore_successful))
                AppToast.success(context, Str.get(R.string.restore_successful))

            } catch (e: Exception) {
                AppLog.e("Backup", Str.get(R.string.backup_restore_exception), e)
                progressMessage = Str.get(R.string.restore_failed_e_message, e.message)
                AppToast.error(context, Str.get(R.string.restore_failed_e_message, e.message))
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
                AppToast.success(context, Str.get(R.string.deleted))
                AppLog.i("Backup", Str.get(R.string.deleting_backup_backup_name, backup.name))
            } else {
                AppToast.error(context, Str.get(R.string.delete_failed))
            }
        } catch (e: Exception) {
            AppLog.e("BackupScreen", Str.get(R.string.failed_to_delete_backup), e)
            AppToast.error(context, Str.get(R.string.delete_failed_e_message, e.message))
        }
    }

    LaunchedEffect(Unit) {
        loadBackups()
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = {
            scope.launch {
                isRefreshing = true
                loadBackups()
                delay(400)
                lastRefreshTime = UIComponents.currentTimeString()
                isRefreshing = false
            }
        },
        state = pullRefreshState,
        modifier = Modifier.fillMaxSize(),
        indicator = {
            UIComponents.PullRefreshIndicator(
                isRefreshing = isRefreshing,
                state = pullRefreshState,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            item { Spacer(modifier = Modifier.height(8.dp)) }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    UnifiedTitleText(Str.get(R.string.backup_restore))
                }
            }
            item {
                UIComponents.LastUpdatedCaption(
                    time = lastRefreshTime,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            item {
                UnifiedButton(
                    text = Str.get(R.string.create_backup),
                    icon = Icons.Default.Backup,
                    onClick = { createBackup() },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    enabled = !isLoading
                )
            }

            // 备份选项
            item {
                UnifiedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    ) {
                        Text(
                            text = Str.get(R.string.backup_options),
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
                            Text(Str.get(R.string.include_ui_config), color = MaterialTheme.colorScheme.onSurface)
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
                            Text(Str.get(R.string.include_app_settings), color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }

            if (showProgress) {
                item {
                    UnifiedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        ) {
                            UnifiedLinearProgressIndicator(progress = progressValue)
                            Spacer(modifier = Modifier.height(4.dp))
                            UnifiedCaptionText(progressMessage)
                        }
                    }
                }
            }

            if (backups.isEmpty() && !showProgress) {
                item {
                    Box(
                        modifier = Modifier.fillParentMaxSize(),
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
                            UnifiedTitleText(Str.get(R.string.no_backup_files_yet))
                            UnifiedBodyText(Str.get(R.string.tap_create_backup_to_back_up_your_pl))
                        }
                    }
                }
            } else {
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
        UnifiedConfirmDialog(
            title = Str.get(R.string.confirm_delete),
            message = Str.get(R.string.delete_backup_backup_name, backup.name),
            onConfirm = {
                deleteBackup(backup)
                deleteTarget = null
            },
            onDismiss = { deleteTarget = null }
        )
    }

    if (showRestoreConfirm && restoreTarget != null) {
        UnifiedDialog(
            onDismissRequest = {
                showRestoreConfirm = false
                restoreTarget = null
            },
            title = Str.get(R.string.confirm_restore),
            content = {
                Text(
                    text = Str.get(R.string.restoring_will_overwrite_existing_pl),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(AppDimens.spacingLarge))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(AppDimens.radiusLarge))
                        .background(
                            if (restoreMode == RestoreMode.MERGE) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            }
                        )
                        .clickable { restoreMode = RestoreMode.MERGE }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = restoreMode == RestoreMode.MERGE,
                        onClick = { restoreMode = RestoreMode.MERGE }
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = Str.get(R.string.restore_mode_merge_title),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = Str.get(R.string.restore_mode_merge_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(AppDimens.radiusLarge))
                        .background(
                            if (restoreMode == RestoreMode.REPLACE) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            }
                        )
                        .clickable { restoreMode = RestoreMode.REPLACE }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = restoreMode == RestoreMode.REPLACE,
                        onClick = { restoreMode = RestoreMode.REPLACE }
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = Str.get(R.string.restore_mode_replace_title),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = Str.get(R.string.restore_mode_replace_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                UnifiedButton(
                    text = Str.get(R.string.confirm_restore),
                    onClick = {
                        restoreTarget?.let { restoreBackup(it, restoreMode) }
                        restoreTarget = null
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            },
            dismissButton = {
                UnifiedDialogTextButton(
                    onClick = {
                        showRestoreConfirm = false
                        restoreTarget = null
                    }
                ) {
                    Text(Str.get(R.string.cancel))
                }
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
    UnifiedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AppDimens.cardCornerRadius),
        elevation = 0.5.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Text(
                text = backup.name,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = AppDimens.bodyTextSize.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = backup.getFormattedDate(),
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = AppDimens.captionTextSize.sp
                    )
                )
                Text(
                    text = "•",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = AppDimens.captionTextSize.sp
                    )
                )
                Text(
                    text = backup.getFormattedSize(),
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = AppDimens.captionTextSize.sp
                    )
                )
                if (backup.pluginCount > 0) {
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = AppDimens.captionTextSize.sp
                        )
                    )
                    Text(
                        text = Str.get(R.string.backup_plugincount, backup.pluginCount),
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = AppDimens.captionTextSize.sp
                        )
                    )
                }
            }

            // 按钮放最下方一行，不与文件名/元信息同一行
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                UnifiedButton(
                    text = Str.get(R.string.restore),
                    onClick = onRestore,
                    modifier = Modifier.weight(1f),
                    variant = ButtonVariant.Primary,
                    size = ButtonSize.Small
                )
                UnifiedButton(
                    text = Str.get(R.string.delete),
                    onClick = onDelete,
                    modifier = Modifier.weight(1f),
                    variant = ButtonVariant.Destructive,
                    size = ButtonSize.Small
                )
            }
        }
    }
}