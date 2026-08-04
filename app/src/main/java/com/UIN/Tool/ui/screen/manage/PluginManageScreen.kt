// ui/screen/manage/PluginManageScreen.kt
package com.UIN.Tool.ui.screen.manage

import com.UIN.Tool.R
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.UIN.Tool.constants.AppConstants as Constants
import com.UIN.Tool.core.di.ServiceLocator
import com.UIN.Tool.data.local.PreferenceManager
import com.UIN.Tool.domain.model.PluginInfo
import com.UIN.Tool.plugin.PluginManager
import com.UIN.Tool.plugin.PluginPermissionManager
import com.UIN.Tool.ui.components.UIComponents
import com.UIN.Tool.utils.*
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

private const val TAG = "PluginManageScreen"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PluginManageScreen(
    navController: NavController = rememberNavController()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pluginManager = ServiceLocator.getPluginManager()
    val preferenceManager = PreferenceManager(context)

    var plugins by remember { mutableStateOf<List<PluginInfo>>(emptyList()) }
    var selectedPluginIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var isLoading by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf<PluginInfo?>(null) }
    var showDetailDialog by remember { mutableStateOf<PluginInfo?>(null) }
    var showPermissionDialog by remember { mutableStateOf<PluginInfo?>(null) }
    var showResultDialog by remember { mutableStateOf<String?>(null) }
    var exportProgress by remember { mutableStateOf("") }
    var showPermissionDetail by remember { mutableStateOf<PluginInfo?>(null) }
    var searchText by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(Str.get(R.string.all)) }

    fun loadPlugins() {
        pluginManager.refreshPlugins()
        plugins = pluginManager.plugins.value
        selectedPluginIds = emptySet()
        AppLog.d(TAG, Str.get(R.string.loaded_plugins_size_plugin_s, plugins.size))
    }

    fun refreshWidgets() {
        try {
            com.UIN.Tool.widget.WidgetProvider.forceRefreshAllWidgets(context)
            com.UIN.Tool.widget.Widget1x1Provider.refresh1x1Widgets(context)
        } catch (e: Exception) {
            AppLog.e(TAG, Str.get(R.string.failed_to_refresh_widget), e)
        }
    }

    val categories = remember(plugins) {
        listOf(Str.get(R.string.all)) + plugins.map { it.category }.distinct()
    }

    val filteredPlugins = remember(searchText, selectedCategory, plugins) {
        var result = plugins
        if (searchText.isNotEmpty()) {
            result = result.filter { plugin ->
                plugin.name.contains(searchText, ignoreCase = true) ||
                plugin.pluginId.contains(searchText, ignoreCase = true) ||
                plugin.description.contains(searchText, ignoreCase = true)
            }
        }
        if (selectedCategory != Str.get(R.string.all)) {
            result = result.filter { it.category == selectedCategory }
        }
        result
    }

    fun importSinglePlugin(uri: Uri) {
        scope.launch {
            try {
                isLoading = true
                val fileName = uri.lastPathSegment?.substringAfterLast("/") ?: "plugin.tpk"
                val tempFile = File(context.cacheDir, "temp_import_${System.currentTimeMillis()}.tpk")
                if (FileUtils.copyUriToFile(context, uri, tempFile)) {
                    val info = pluginManager.installPlugin(tempFile, fileName)
                    if (info != null) {
                        AppLog.success(TAG, Str.get(R.string.import_successful_info_name, info.name))
                        loadPlugins()
                        refreshWidgets()
                        showResultDialog = Str.get(R.string.import_successful_info_name, info.name)
                    } else {
                        showResultDialog = Str.get(R.string.import_failed_filename, fileName)
                    }
                }
                tempFile.delete()
            } catch (e: Exception) {
                AppLog.e(TAG, Str.get(R.string.import_failed), e)
                showResultDialog = Str.get(R.string.import_failed_e_message, e.message)
            } finally {
                isLoading = false
            }
        }
    }

    fun batchImportPlugins(uris: List<Uri>) {
        scope.launch {
            try {
                isLoading = true
                var successCount = 0
                var failCount = 0
                val failNames = mutableListOf<String>()

                uris.forEach { uri ->
                    try {
                        val fileName = uri.lastPathSegment?.substringAfterLast("/") ?: "plugin.tpk"
                        val tempFile = File(context.cacheDir, "temp_import_${System.currentTimeMillis()}.tpk")
                        if (FileUtils.copyUriToFile(context, uri, tempFile)) {
                            val info = pluginManager.installPlugin(tempFile, fileName)
                            if (info != null) successCount++ else { failCount++; failNames.add(fileName) }
                        }
                        tempFile.delete()
                    } catch (e: Exception) {
                        failCount++
                        failNames.add(uri.lastPathSegment ?: Str.get(R.string.unknown))
                    }
                }

                val message = Str.get(R.string.successfully_imported_successcount_p, successCount)
                showResultDialog = if (failCount > 0) {
                    Str.get(R.string.import_result_partial_fail, message, failCount, failNames.joinToString("\n"))
                } else {
                    Str.get(R.string.successfully_imported_message, message)
                }
                if (successCount > 0) {
                    loadPlugins()
                    refreshWidgets()
                }
            } catch (e: Exception) {
                AppLog.e(TAG, Str.get(R.string.batch_import_failed), e)
                showResultDialog = Str.get(R.string.batch_import_failed_e_message, e.message)
            } finally {
                isLoading = false
            }
        }
    }

    fun importPluginSet(uri: Uri) {
        scope.launch {
            try {
                isLoading = true
                exportProgress = Str.get(R.string.processing_plugin_set)

                val zipFile = File(context.cacheDir, "temp_plugin_set_${System.currentTimeMillis()}.zip")
                if (!FileUtils.copyUriToFile(context, uri, zipFile)) {
                    showResultDialog = Str.get(R.string.failed_to_read_file)
                    return@launch
                }

                val extractDir = File(Constants.TEMP_DIR, "extract_${System.currentTimeMillis()}")
                extractDir.mkdirs()

                var successCount = 0
                var failCount = 0
                val failNames = mutableListOf<String>()

                try {
                    java.util.zip.ZipFile(zipFile).use { zf ->
                        val entries = zf.entries()
                        var total = 0
                        var processed = 0

                        while (entries.hasMoreElements()) {
                            val entry = entries.nextElement()
                            if (!entry.isDirectory && entry.name.endsWith(".tpk")) {
                                total++
                            }
                        }

                        if (total == 0) {
                            showResultDialog = Str.get(R.string.no_tpk_files_found_in_the_plugin_set)
                            return@launch
                        }

                        val newEntries = zf.entries()
                        while (newEntries.hasMoreElements()) {
                            val entry = newEntries.nextElement()
                            if (!entry.isDirectory && entry.name.endsWith(".tpk")) {
                                processed++
                                val fileName = File(entry.name).name
                                exportProgress = Str.get(R.string.processing_filename_processed_total, fileName, processed, total)

                                val outFile = File(extractDir, fileName)
                                zf.getInputStream(entry).use { input ->
                                    outFile.outputStream().use { output ->
                                        input.copyTo(output)
                                    }
                                }

                                if (!SecurityUtils.verifyFileSignature(outFile, preferenceManager)) {
                                    failCount++
                                    failNames.add(Str.get(R.string.filename_signature_verification_fail, fileName))
                                    continue
                                }

                                val info = pluginManager.installPlugin(outFile, fileName)
                                if (info != null) {
                                    successCount++
                                    AppLog.success(TAG, Str.get(R.string.import_successful_info_name, info.name))
                                } else {
                                    failCount++
                                    failNames.add(fileName)
                                }
                            }
                        }
                    }
                } finally {
                    zipFile.delete()
                    FileUtils.deleteRecursively(extractDir)
                }

                val message = if (failCount > 0) {
                    Str.get(R.string.import_result_count_fail, successCount, failCount, failNames.joinToString("\n"))
                } else {
                    Str.get(R.string.successfully_imported_successcount_p, successCount)
                }
                showResultDialog = message

                if (successCount > 0) {
                    loadPlugins()
                    refreshWidgets()
                    AppToast.success(context, Str.get(R.string.successfully_imported_successcount_p, successCount))
                }

            } catch (e: Exception) {
                AppLog.e(TAG, Str.get(R.string.failed_to_import_plugin_set), e)
                showResultDialog = Str.get(R.string.failed_to_import_plugin_set_e_messag, e.message)
            } finally {
                isLoading = false
                exportProgress = ""
            }
        }
    }

    fun exportSelectedPlugins() {
        val selectedIds = selectedPluginIds.toList()
        if (selectedIds.isEmpty()) {
            AppToast.warning(context, Str.get(R.string.please_select_plugins_to_export_firs))
            return
        }

        scope.launch {
            try {
                isLoading = true
                exportProgress = Str.get(R.string.exporting_plugins)

                val exportDir = File(Constants.DOWNLOAD_DIR, "exports")
                if (!exportDir.exists()) {
                    exportDir.mkdirs()
                }

                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val zipFile = File(exportDir, "Plugins_${selectedIds.size}_$timestamp.zip")

                var successCount = 0
                val failedList = mutableListOf<String>()

                java.util.zip.ZipOutputStream(
                    java.io.FileOutputStream(zipFile)
                ).use { zos ->
                    selectedIds.forEach { pluginId ->
                        val info = pluginManager.getPluginInfo(pluginId)
                        if (info != null) {
                            val pluginDir = pluginManager.getPluginDirFile(pluginId)
                            if (pluginDir != null && pluginDir.exists()) {
                                exportProgress = Str.get(R.string.exporting_info_name, info.name)
                                addPluginDirToZip(zos, pluginDir, "${info.pluginId}/")
                                successCount++
                                AppLog.success(TAG, Str.get(R.string.export_successful_info_name, info.name))
                            } else {
                                failedList.add(Str.get(R.string.info_name_directory_does_not_exist, info.name))
                            }
                        } else {
                            failedList.add(Str.get(R.string.pluginid_plugin_does_not_exist, pluginId))
                        }
                    }
                }

                val message = if (failedList.isNotEmpty()) {
                    Str.get(R.string.export_result_count_fail, successCount, zipFile.absolutePath, failedList.size, failedList.joinToString("\n"))
                } else {
                    Str.get(R.string.successfully_exported_successcount_p_2, successCount, zipFile.absolutePath)
                }
                showResultDialog = message

                AppToast.success(context, Str.get(R.string.successfully_exported_successcount_p, successCount))

                selectedPluginIds = emptySet()

            } catch (e: Exception) {
                AppLog.e(TAG, Str.get(R.string.failed_to_export_plugin), e)
                showResultDialog = Str.get(R.string.export_failed_e_message, e.message)
            } finally {
                isLoading = false
                exportProgress = ""
            }
        }
    }

    fun uninstallPlugin(plugin: PluginInfo) {
        scope.launch {
            try {
                isLoading = true
                pluginManager.uninstallPlugin(plugin.pluginId)
                loadPlugins()
                refreshWidgets()
                AppLog.success(TAG, Str.get(R.string.uninstall_successful_plugin_name, plugin.name))
                showDeleteDialog = null
            } catch (e: Exception) {
                AppLog.e(TAG, Str.get(R.string.uninstall_failed), e)
                AppToast.error(context, Str.get(R.string.uninstall_failed_e_message, e.message))
            } finally {
                isLoading = false
            }
        }
    }

    fun requestPluginPermissions(plugin: PluginInfo) {
        scope.launch {
            try {
                isLoading = true
                pluginManager.requestPluginPermissionsByGroups(
                    plugin.pluginId,
                    onProgress = { group, current, total ->
                        AppLog.d(TAG, Str.get(R.string.permission_request_progress_current_, current, total, group))
                    },
                    onComplete = { allGranted ->
                        isLoading = false
                        if (allGranted) {
                            AppToast.success(context, Str.get(R.string.all_permissions_granted))
                        } else {
                            AppToast.warning(context, Str.get(R.string.some_permissions_denied))
                        }
                        showPermissionDialog = null
                    }
                )
            } catch (e: Exception) {
                AppLog.e(TAG, Str.get(R.string.failed_to_request_permissions), e)
                isLoading = false
                AppToast.error(context, Str.get(R.string.failed_to_request_permissions_e_mess, e.message))
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { importSinglePlugin(it) }
    }

    val batchImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri>? ->
        if (!uris.isNullOrEmpty()) {
            batchImportPlugins(uris)
        }
    }

    val importZipLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { importPluginSet(it) }
    }

    LaunchedEffect(Unit) {
        loadPlugins()
        AppLog.i(TAG, Str.get(R.string.plugin_management_ui_initialized))
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
            UIComponents.TitleText(Str.get(R.string.plugin_management))
            Row {
                UIComponents.IconButton(
                    icon = Icons.Default.Refresh,
                    onClick = { loadPlugins() }
                )
            }
        }

        UIComponents.TextInput(
            value = searchText,
            onValueChange = { searchText = it },
            placeholder = Str.get(R.string.search_plugins),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            leadingIcon = Icons.Default.Search
        )

        if (categories.size > 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                categories.forEach { category ->
                    UIComponents.Chip(
                        label = category,
                        selected = selectedCategory == category,
                        onClick = { selectedCategory = category }
                    )
                }
            }
        }

        if (exportProgress.isNotEmpty()) {
            UIComponents.Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = exportProgress,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            UIComponents.PrimaryButton(
                text = Str.get(R.string.import_label),
                icon = Icons.Default.FileUpload,
                onClick = { importLauncher.launch("*/*") },
                modifier = Modifier.weight(1f),
                enabled = !isLoading
            )
            UIComponents.SecondaryButton(
                text = Str.get(R.string.batch),
                icon = Icons.Default.Add,
                onClick = { batchImportLauncher.launch(arrayOf("*/*")) },
                modifier = Modifier.weight(1f),
                enabled = !isLoading
            )
            UIComponents.SecondaryButton(
                text = Str.get(R.string.plugin_set),
                icon = Icons.Default.Archive,
                onClick = { importZipLauncher.launch("application/zip") },
                modifier = Modifier.weight(1f),
                enabled = !isLoading
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            UIComponents.SecondaryButton(
                text = Str.get(R.string.export_selectedpluginids_size, selectedPluginIds.size),
                icon = Icons.Default.FileDownload,
                onClick = { exportSelectedPlugins() },
                modifier = Modifier.weight(1f),
                enabled = selectedPluginIds.isNotEmpty() && !isLoading
            )
            UIComponents.SecondaryButton(
                text = Str.get(R.string.delete),
                icon = Icons.Default.Delete,
                onClick = {
                    if (selectedPluginIds.isNotEmpty()) {
                        val plugin = plugins.find { it.pluginId == selectedPluginIds.first() }
                        plugin?.let { showDeleteDialog = it }
                    }
                },
                modifier = Modifier.weight(1f),
                enabled = selectedPluginIds.isNotEmpty() && !isLoading
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = Str.get(R.string.filteredplugins_size_plugin_s_in_tot, filteredPlugins.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(
                    onClick = {
                        selectedPluginIds = if (selectedPluginIds.size == filteredPlugins.size) {
                            emptySet()
                        } else {
                            filteredPlugins.map { it.pluginId }.toSet()
                        }
                    }
                ) {
                    Text(
                        if (selectedPluginIds.size == filteredPlugins.size) Str.get(R.string.deselect_all_2) else Str.get(R.string.select_all),
                        fontSize = 12.sp
                    )
                }
                if (selectedPluginIds.isNotEmpty()) {
                    Text(
                        text = Str.get(R.string.selectedpluginids_size_selected, selectedPluginIds.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        when {
            isLoading && plugins.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = Str.get(R.string.loading_plugins),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            filteredPlugins.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Extension,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (searchText.isNotEmpty() || selectedCategory != Str.get(R.string.all)) {
                                Str.get(R.string.no_matching_plugins)
                            } else {
                                Str.get(R.string.no_installed_plugins)
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (searchText.isEmpty() && selectedCategory == Str.get(R.string.all)) {
                            Text(
                                text = Str.get(R.string.tap_the_import_button_to_import_plug),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }
                }
            }
            else -> {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredPlugins) { plugin ->
                        PluginManageItem(
                            plugin = plugin,
                            isSelected = selectedPluginIds.contains(plugin.pluginId),
                            onToggle = {
                                selectedPluginIds = if (selectedPluginIds.contains(plugin.pluginId)) {
                                    selectedPluginIds - plugin.pluginId
                                } else {
                                    selectedPluginIds + plugin.pluginId
                                }
                            },
                            onOpen = { pluginManager.openPlugin(plugin.pluginId, context) },
                            onDelete = { showDeleteDialog = plugin },
                            onDetail = { showDetailDialog = plugin },
                            onManagePermissions = { showPermissionDialog = plugin },
                            onViewPermissions = { showPermissionDetail = plugin }
                        )
                    }
                }
            }
        }
    }

    // ==================== 删除确认对话框 ====================
    if (showDeleteDialog != null) {
        UIComponents.ConfirmDialog(
            title = Str.get(R.string.confirm_delete),
            message = Str.get(R.string.delete_plugin_showdeletedialog_name_, showDeleteDialog!!.name),
            confirmText = Str.get(R.string.delete),
            dismissText = Str.get(R.string.cancel),
            onConfirm = {
                showDeleteDialog?.let { uninstallPlugin(it) }
            },
            onDismiss = { showDeleteDialog = null },
            isDestructive = true
        )
    }

    // ==================== 操作结果对话框 ====================
    if (showResultDialog != null) {
        UIComponents.InfoDialog(
            title = Str.get(R.string.result),
            message = showResultDialog!!,
            onDismiss = { showResultDialog = null }
        )
    }

    // ==================== 插件详情对话框（包含说明，无 emoji） ====================
    if (showDetailDialog != null) {
        UIComponents.ConfirmDialog(
            title = showDetailDialog!!.name,
            message = buildDetailMessage(showDetailDialog!!),
            confirmText = Str.get(R.string.run),
            dismissText = Str.get(R.string.close),
            onConfirm = {
                ServiceLocator.getPluginManager().openPlugin(showDetailDialog!!.pluginId, context)
                showDetailDialog = null
            },
            onDismiss = { showDetailDialog = null }
        )
    }

    // ==================== 权限管理对话框 ====================
    if (showPermissionDialog != null) {
        PermissionManagementDialog(
            plugin = showPermissionDialog!!,
            onDismiss = { showPermissionDialog = null }
        )
    }

    // ==================== 权限详情对话框 ====================
    if (showPermissionDetail != null) {
        PermissionDetailDialog(
            plugin = showPermissionDetail!!,
            onDismiss = { showPermissionDetail = null }
        )
    }
}

// ==================== 辅助函数 ====================

private fun addPluginDirToZip(
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
            addPluginDirToZip(zos, file, "$entryName/")
        } else {
            try {
                zos.putNextEntry(java.util.zip.ZipEntry(entryName))
                java.io.FileInputStream(file).use { fis ->
                    fis.copyTo(zos)
                }
                zos.closeEntry()
            } catch (e: Exception) {
                AppLog.e("PluginManage", Str.get(R.string.failed_to_add_file_to_zip_file_name, file.name), e)
            }
        }
    }
}

// ============================================================
// buildDetailMessage 包含 notice 字段（无 emoji）
// ============================================================

private fun buildDetailMessage(plugin: PluginInfo): String {
    return buildString {
        append("ID: ${plugin.pluginId}\n")
        append(Str.get(R.string.version_plugin_versionname_plugin_ve, plugin.versionName, plugin.version))
        append(Str.get(R.string.plugin_author, plugin.author.ifEmpty { Str.get(R.string.unknown) }))
        append(Str.get(R.string.category_plugin_category_n, plugin.category))
        if (plugin.description.isNotEmpty()) {
            append(Str.get(R.string.ndescription_plugin_description_n, plugin.description))
        }
        // 显示插件说明（如果存在）
        if (plugin.hasNotice()) {
            append(Str.get(R.string.nnotice_n_plugin_notice_n, plugin.notice))
        }
        if (plugin.dependencies.isNotEmpty()) {
            append(Str.get(R.string.plugin_dependencies, plugin.dependencies.joinToString(", ")))
        }
        if (plugin.permissions.isNotEmpty()) {
            val pluginManager = ServiceLocator.getPluginManager()
            val summary = pluginManager.getPluginPermissionSummary(plugin.pluginId)
            append(Str.get(R.string.npermissions_summary_granted_summary, summary.granted, summary.total))
            if (!summary.isAllGranted) {
                append(Str.get(R.string.summary_denied_permission_s_not_gran, summary.denied))
            }
        }
    }
}

// ==================== 权限管理对话框 ====================

@Composable
fun PermissionManagementDialog(
    plugin: PluginInfo,
    onDismiss: () -> Unit
) {
    val pluginManager = ServiceLocator.getPluginManager()
    val context = LocalContext.current
    var permissions by remember {
        mutableStateOf(pluginManager.getPluginPermissionStatus(plugin.pluginId))
    }
    var isRequesting by remember { mutableStateOf(false) }
    var progressMessage by remember { mutableStateOf("") }
    val allGranted = permissions.values.all { it }

    fun refreshPermissions() {
        permissions = pluginManager.getPluginPermissionStatus(plugin.pluginId)
    }

    LaunchedEffect(Unit) {
        refreshPermissions()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight()
                .heightIn(max = 450.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 12.dp)
                ) {
                    Icon(
                        Icons.Default.Security,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Text(
                        Str.get(R.string.permission_management),
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    )
                    if (allGranted && permissions.isNotEmpty()) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                Str.get(R.string.granted),
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Text(
                    text = Str.get(R.string.plugin_plugin_name, plugin.name),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                if (permissions.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = Str.get(R.string.permissions_permissions_values_count, permissions.values.count { it }, permissions.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        IconButton(
                            onClick = { refreshPermissions() },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = Str.get(R.string.refresh),
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (permissions.isEmpty()) {
                    Text(
                        text = Str.get(R.string.this_plugin_declares_no_permissions),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(permissions.entries.toList()) { (permission, granted) ->
                            PermissionItemCompact(
                                permission = permission,
                                granted = granted,
                                onRequest = {
                                    if (!granted && !isRequesting) {
                                        isRequesting = true
                                        pluginManager.requestPluginPermissionsByGroups(
                                            plugin.pluginId,
                                            onProgress = { group, current, total ->
                                                progressMessage = Str.get(R.string.requesting_group_current_total, group, current, total)
                                            },
                                            onComplete = { allGranted ->
                                                isRequesting = false
                                                progressMessage = ""
                                                refreshPermissions()
                                                if (allGranted) {
                                                    AppToast.success(context, Str.get(R.string.all_permissions_granted))
                                                } else {
                                                    AppToast.warning(context, Str.get(R.string.some_permissions_denied))
                                                }
                                            }
                                        )
                                    }
                                }
                            )
                        }
                    }
                }

                if (progressMessage.isNotEmpty()) {
                    Text(
                        text = progressMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (permissions.isNotEmpty() && !allGranted) {
                        Button(
                            onClick = {
                                isRequesting = true
                                pluginManager.requestPluginPermissionsByGroups(
                                    plugin.pluginId,
                                    onProgress = { group, current, total ->
                                        progressMessage = Str.get(R.string.requesting_group_current_total, group, current, total)
                                    },
                                    onComplete = { allGranted ->
                                        isRequesting = false
                                        progressMessage = ""
                                        refreshPermissions()
                                        if (allGranted) {
                                            AppToast.success(context, Str.get(R.string.all_permissions_granted))
                                        } else {
                                            AppToast.warning(context, Str.get(R.string.some_permissions_denied))
                                        }
                                    }
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            shape = RoundedCornerShape(8.dp),
                            enabled = !isRequesting
                        ) {
                            if (isRequesting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            } else {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text(if (isRequesting) Str.get(R.string.requesting) else Str.get(R.string.grant_all_permissions), fontSize = 14.sp)
                        }
                    }

                    val hasSpecial = permissions.keys.any {
                        PluginPermissionManager.isSpecialPermission(it) && !(permissions[it] ?: false)
                    }
                    if (hasSpecial) {
                        Button(
                            onClick = {
                                try {
                                    val intent = android.content.Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                    intent.data = Uri.parse("package:${context.packageName}")
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    AppToast.error(context, Str.get(R.string.failed_to_open_settings))
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Outlined.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(Str.get(R.string.go_to_system_settings_to_enable_spec), fontSize = 14.sp)
                        }
                    }

                    Button(
                        onClick = onDismiss,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(Str.get(R.string.close), fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

// ==================== 权限详情对话框 ====================

@Composable
fun PermissionDetailDialog(
    plugin: PluginInfo,
    onDismiss: () -> Unit
) {
    val pluginManager = ServiceLocator.getPluginManager()
    val context = LocalContext.current
    var permissions by remember {
        mutableStateOf(pluginManager.getPluginPermissionStatus(plugin.pluginId))
    }
    var isRequesting by remember { mutableStateOf(false) }

    fun refreshPermissions() {
        permissions = pluginManager.getPluginPermissionStatus(plugin.pluginId)
    }

    LaunchedEffect(Unit) {
        refreshPermissions()
    }

    UIComponents.ConfirmDialog(
        title = Str.get(R.string.permission_details_plugin_name, plugin.name),
        message = buildString {
            val grantedCount = permissions.values.count { it }
            val totalCount = permissions.size
            append(Str.get(R.string.granted_grantedcount_totalcount_n_n, grantedCount, totalCount))
            if (permissions.isEmpty()) {
                append(Str.get(R.string.this_plugin_declares_no_permissions))
            } else {
                permissions.entries.forEach { (permission, granted) ->
                    val status = if (granted) Str.get(R.string.granted_2) else Str.get(R.string.not_granted)
                    append("• ${PluginPermissionManager.getPermissionDisplayName(permission)}: $status\n")
                }
            }
        },
        confirmText = if (permissions.values.any { !it }) Str.get(R.string.grant_all) else Str.get(R.string.ok_2),
        dismissText = Str.get(R.string.close),
        onConfirm = {
            if (permissions.values.any { !it }) {
                isRequesting = true
                pluginManager.requestPluginPermissionsByGroups(
                    plugin.pluginId,
                    onComplete = { allGranted ->
                        isRequesting = false
                        refreshPermissions()
                        if (allGranted) {
                            AppToast.success(context, Str.get(R.string.all_permissions_granted))
                        } else {
                            AppToast.warning(context, Str.get(R.string.some_permissions_denied))
                        }
                    }
                )
            } else {
                onDismiss()
            }
        },
        onDismiss = onDismiss
    )
}

// ==================== 权限项紧凑版 ====================

@Composable
fun PermissionItemCompact(
    permission: String,
    granted: Boolean,
    onRequest: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (granted) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (granted) Icons.Default.CheckCircle else Icons.Outlined.Warning,
                contentDescription = null,
                tint = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp)
            )

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = PluginPermissionManager.getPermissionDisplayName(permission),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (granted) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (PluginPermissionManager.isSpecialPermission(permission)) {
                    Text(
                        text = Str.get(R.string.special_permission_requires_enabling),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            if (granted) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = Str.get(R.string.granted_2),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            } else {
                Button(
                    onClick = onRequest,
                    modifier = Modifier.height(28.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(Str.get(R.string.grant), fontSize = 11.sp)
                }
            }
        }
    }
}

// ==================== 插件列表项 ====================

@Composable
fun PluginManageItem(
    plugin: PluginInfo,
    isSelected: Boolean,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    onDetail: () -> Unit,
    onManagePermissions: () -> Unit,
    onViewPermissions: () -> Unit
) {
    val pluginManager = ServiceLocator.getPluginManager()

    val permissionSummary = remember(plugin.pluginId) {
        pluginManager.getPluginPermissionSummary(plugin.pluginId)
    }
    val hasMissingPermissions = permissionSummary.hasMissing
    val hasPermissions = plugin.permissions.isNotEmpty()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onDetail() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (hasMissingPermissions)
                MaterialTheme.colorScheme.errorContainer
            else
                MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggle() },
                colors = CheckboxDefaults.colors(
                    checkedColor = MaterialTheme.colorScheme.primary,
                    uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                modifier = Modifier.size(36.dp)
            )

            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        MaterialTheme.colorScheme.primaryContainer,
                        RoundedCornerShape(10.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    plugin.name.take(1).uppercase(),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = plugin.name,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Medium
                        ),
                        maxLines = 1,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    if (hasPermissions) {
                        if (hasMissingPermissions) {
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer,
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Icon(
                                        Icons.Outlined.Warning,
                                        contentDescription = null,
                                        modifier = Modifier.size(10.dp),
                                        tint = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text(
                                        text = "${permissionSummary.denied}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        } else if (permissionSummary.isAllGranted) {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        modifier = Modifier.size(10.dp),
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text(
                                        text = "${permissionSummary.granted}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }
                    }
                }

                Text(
                    text = plugin.pluginId,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "v${plugin.versionName}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                        )
                    }

                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = plugin.category,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                        )
                    }

                    if (plugin.isWebPlugin()) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "Web",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                            )
                        }
                    } else {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = Str.get(R.string.native_label),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                            )
                        }
                    }

                    if (hasPermissions) {
                        Surface(
                            color = if (hasMissingPermissions)
                                MaterialTheme.colorScheme.errorContainer
                            else
                                MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = Str.get(R.string.plugin_permissions_size_permission_s, plugin.permissions.size),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (hasMissingPermissions)
                                    MaterialTheme.colorScheme.onErrorContainer
                                else
                                    MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                if (hasPermissions) {
                    UIComponents.IconButton(
                        icon = if (hasMissingPermissions)
                            Icons.Default.Security
                        else
                            Icons.Default.Verified,
                        onClick = onManagePermissions,
                        tint = if (hasMissingPermissions)
                            MaterialTheme.colorScheme.error
                        else
                            MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                }

                UIComponents.IconButton(
                    icon = Icons.Default.PlayArrow,
                    onClick = onOpen,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )

                UIComponents.IconButton(
                    icon = Icons.Default.Delete,
                    onClick = onDelete,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}