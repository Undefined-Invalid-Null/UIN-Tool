// ui/screen/manage/PluginManageScreen.kt
package com.UIN.Tool.ui.screen.manage

import com.UIN.Tool.R
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
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
import com.UIN.Tool.constants.AppConstants as Constants
import com.UIN.Tool.core.di.ServiceLocator
import com.UIN.Tool.data.local.PreferenceManager
import com.UIN.Tool.domain.model.PluginInfo
import com.UIN.Tool.plugin.PluginManager
import com.UIN.Tool.plugin.PluginPermissionManager
import com.UIN.Tool.ui.components.UIComponents
import com.UIN.Tool.ui.components.unified.*
import com.UIN.Tool.utils.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import com.UIN.Tool.ui.theme.AppColors
import com.UIN.Tool.ui.theme.AppDimens
import com.UIN.Tool.ui.theme.dialogBackgroundOf

private const val TAG = "PluginManageScreen"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PluginManageScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pluginManager = ServiceLocator.getPluginManager()
    val preferenceManager = PreferenceManager(context)

    var plugins by remember { mutableStateOf<List<PluginInfo>>(emptyList()) }
    var selectedPluginIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var isLoading by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }
    var lastRefreshTime by remember { mutableStateOf<String?>(null) }
    val pullRefreshState = rememberPullToRefreshState()
    var showDeleteDialog by remember { mutableStateOf<PluginInfo?>(null) }
    var batchDeleteIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showDetailDialog by remember { mutableStateOf<PluginInfo?>(null) }
    var showPermissionDialog by remember { mutableStateOf<PluginInfo?>(null) }
    var showResultDialog by remember { mutableStateOf<String?>(null) }
    var exportProgress by remember { mutableStateOf("") }
    var showPermissionDetail by remember { mutableStateOf<PluginInfo?>(null) }
    var searchText by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(Str.get(R.string.all)) }
    var categoryTargetIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var selectionMode by remember { mutableStateOf(false) }

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

                                // 签名校验改由 installPlugin 内部按 pluginId 完成，这里不再重复校验
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

    fun uninstallPluginsBatch(ids: Set<String>) {
        if (ids.isEmpty()) {
            batchDeleteIds = emptySet()
            return
        }
        scope.launch {
            try {
                isLoading = true
                val successList = pluginManager.uninstallPluginsBatch(ids.toList())
                val failedIds = ids - successList.toSet()
                loadPlugins()
                refreshWidgets()
                batchDeleteIds = emptySet()
                selectedPluginIds = emptySet()
                if (failedIds.isEmpty()) {
                    AppToast.success(context, Str.get(R.string.uninstall_successful_plugin_count, successList.size))
                } else {
                    AppToast.warning(
                        context,
                        Str.get(R.string.batch_uninstall_result_success_failed, successList.size, failedIds.size)
                    )
                }
                AppLog.success(TAG, Str.get(R.string.batch_uninstall_result_success_failed, successList.size, failedIds.size))
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

    Scaffold(
        containerColor = AppColors.pageBackground(),
        topBar = {
            UIComponents.ManageTopAppBar(
                titleText = Str.get(R.string.plugin_management),
                onBack = onBack,
                actions = {
                    UnifiedIconButton(
                        icon = if (selectionMode) Icons.Default.Close else Icons.Default.Checklist,
                        onClick = {
                            selectionMode = !selectionMode
                            selectedPluginIds = emptySet()
                        },
                        tint = if (selectionMode) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            )
        }
    ) { paddingValues ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                scope.launch {
                    isRefreshing = true
                    loadPlugins()
                    delay(400)
                    lastRefreshTime = UIComponents.currentTimeString()
                    isRefreshing = false
                }
            },
            state = pullRefreshState,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            indicator = {
                UIComponents.PullRefreshIndicator(
                    isRefreshing = isRefreshing,
                    state = pullRefreshState,
                    modifier = Modifier.align(Alignment.TopCenter)
                )
            }
        ) {
        val manageListState = rememberLazyListState()
        Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = manageListState,
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            item {
                UIComponents.LastUpdatedCaption(
                    time = lastRefreshTime,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
            item {
                UnifiedTextField(
                    value = searchText,
                    onValueChange = { searchText = it },
                    placeholder = Str.get(R.string.search_plugins),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    leadingIcon = Icons.Default.Search
                )
            }

        if (categories.size > 1) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    categories.forEach { category ->
                        UnifiedChip(
                            label = category,
                            selected = selectedCategory == category,
                            onClick = { selectedCategory = category }
                        )
                    }
                }
            }
        }

        if (exportProgress.isNotEmpty()) {
            item {
                UnifiedCard(
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
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                UnifiedButton(
                    text = Str.get(R.string.import_label),
                    icon = Icons.Default.FileUpload,
                    onClick = { importLauncher.launch("*/*") },
                    modifier = Modifier.weight(1f),
                    enabled = !isLoading
                )
                UnifiedButton(
                    text = Str.get(R.string.batch),
                    icon = Icons.Default.Add,
                    onClick = { batchImportLauncher.launch(arrayOf("*/*")) },
                    modifier = Modifier.weight(1f),
                    enabled = !isLoading,
                    variant = ButtonVariant.Outlined
                )
                UnifiedButton(
                    text = Str.get(R.string.plugin_set),
                    icon = Icons.Default.Archive,
                    onClick = { importZipLauncher.launch("application/zip") },
                    modifier = Modifier.weight(1f),
                    enabled = !isLoading,
                    variant = ButtonVariant.Outlined
                )
            }
        }

        if (selectionMode) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    UnifiedButton(
                        text = Str.get(R.string.export_selectedpluginids_size, selectedPluginIds.size),
                        icon = Icons.Default.FileDownload,
                        onClick = { exportSelectedPlugins() },
                        modifier = Modifier.weight(1f),
                        enabled = selectedPluginIds.isNotEmpty() && !isLoading,
                        variant = ButtonVariant.Outlined
                    )
                    UnifiedButton(
                        text = Str.get(R.string.delete),
                        icon = Icons.Default.Delete,
                        onClick = {
                            if (selectedPluginIds.isNotEmpty()) {
                                batchDeleteIds = selectedPluginIds.toSet()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = selectedPluginIds.isNotEmpty() && !isLoading,
                        variant = ButtonVariant.Outlined
                    )
                    UnifiedButton(
                        text = Str.get(R.string.btn_change_category),
                        icon = Icons.Default.Category,
                        onClick = { categoryTargetIds = selectedPluginIds.toSet() },
                        modifier = Modifier.weight(1f),
                        enabled = selectedPluginIds.isNotEmpty() && !isLoading,
                        variant = ButtonVariant.Outlined
                    )
                }
            }
        }

        item {
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
                    if (selectionMode) {
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
                                fontSize = AppDimens.captionTextSize.sp
                            )
                        }
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
        }

        when {
            isLoading && plugins.isEmpty() -> item {
                UIComponents.PluginListSkeleton(
                    modifier = Modifier.fillParentMaxSize()
                )
            }
            filteredPlugins.isEmpty() -> item {
                Box(
                    modifier = Modifier.fillParentMaxSize(),
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
            else -> itemsIndexed(filteredPlugins, key = { _, plugin -> plugin.pluginId }) { index, plugin ->
                PluginManageItem(
                    plugin = plugin,
                    isSelected = selectedPluginIds.contains(plugin.pluginId),
                    selectionMode = selectionMode,
                    modifier = Modifier.animateItem(),
                    onToggle = {
                        selectedPluginIds = if (selectedPluginIds.contains(plugin.pluginId)) {
                            selectedPluginIds - plugin.pluginId
                        } else {
                            selectedPluginIds + plugin.pluginId
                        }
                    },
                    onOpen = { PluginShortcutHelper.createShortcut(context, plugin) },
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
    }

    // ==================== 删除确认对话框 ====================
    if (showDeleteDialog != null) {
        UnifiedConfirmDialog(
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

    // ==================== 批量删除确认对话框 ====================
    if (batchDeleteIds.isNotEmpty()) {
        UnifiedConfirmDialog(
            title = Str.get(R.string.confirm_delete),
            message = Str.get(R.string.delete_selected_plugins_count, batchDeleteIds.size),
            confirmText = Str.get(R.string.delete),
            dismissText = Str.get(R.string.cancel),
            onConfirm = { uninstallPluginsBatch(batchDeleteIds) },
            onDismiss = { batchDeleteIds = emptySet() },
            isDestructive = true
        )
    }

    // ==================== 操作结果对话框 ====================
    if (showResultDialog != null) {
        UnifiedInfoDialog(
            title = Str.get(R.string.result),
            message = showResultDialog!!,
            onDismiss = { showResultDialog = null }
        )
    }

    // ==================== 插件详情对话框（plugin.json 字段 + 文件结构 + 大小） ====================
    if (showDetailDialog != null) {
        PluginDetailDialog(
            plugin = showDetailDialog!!,
            onDismiss = { showDetailDialog = null },
            onChangeCategory = { categoryTargetIds = setOf(showDetailDialog!!.pluginId) },
            onUninstall = { showDeleteDialog = showDetailDialog }
        )
    }

    // ==================== 更换分类对话框 ====================
    if (categoryTargetIds.isNotEmpty()) {
        CategoryChangeDialog(
            targetPluginIds = categoryTargetIds,
            onDismiss = { categoryTargetIds = emptySet() },
            onCategoryUpdated = {
                loadPlugins()
                refreshWidgets()
            }
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
        UnifiedCard(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight()
                .heightIn(max = 450.dp)
                .then(Modifier.dialogBackgroundOf(RoundedCornerShape(AppDimens.dialogCornerRadius))),
            containerColor = Color.Transparent
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
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
                            shape = RoundedCornerShape(AppDimens.radiusSmall)
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
                            shape = RoundedCornerShape(AppDimens.buttonCornerRadius),
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
                            Text(if (isRequesting) Str.get(R.string.requesting) else Str.get(R.string.grant_all_permissions), fontSize = AppDimens.bodyTextSize.sp)
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
                            shape = RoundedCornerShape(AppDimens.buttonCornerRadius)
                        ) {
                            Icon(Icons.Outlined.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(Str.get(R.string.go_to_system_settings_to_enable_spec), fontSize = AppDimens.bodyTextSize.sp)
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
                        shape = RoundedCornerShape(AppDimens.buttonCornerRadius)
                    ) {
                        Text(Str.get(R.string.close), fontSize = AppDimens.bodyTextSize.sp)
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

    UnifiedConfirmDialog(
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
        shape = RoundedCornerShape(AppDimens.cardCornerRadius),
        border = null,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (AppColors.glassEnabled())
                AppColors.glassBackground()
            else
                if (granted) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.errorContainer
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
                    shape = RoundedCornerShape(AppDimens.radiusSmall)
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
                    shape = RoundedCornerShape(AppDimens.buttonCornerRadius)
                ) {
                    Text(Str.get(R.string.grant), fontSize = AppDimens.captionTextSize.sp)
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
    selectionMode: Boolean,
    modifier: Modifier = Modifier,
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

    UnifiedCard(
        modifier = modifier
            .fillMaxWidth(),
        onClick = { onDetail() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onToggle() },
                    colors = CheckboxDefaults.colors(
                        checkedColor = MaterialTheme.colorScheme.primary,
                        uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.size(36.dp)
                )
            }

            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        MaterialTheme.colorScheme.primaryContainer,
                        RoundedCornerShape(AppDimens.radiusMedium)
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
                                shape = RoundedCornerShape(AppDimens.radiusSmall)
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
                                shape = RoundedCornerShape(AppDimens.radiusSmall)
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
                        shape = RoundedCornerShape(AppDimens.radiusSmall)
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
                        shape = RoundedCornerShape(AppDimens.radiusSmall)
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
                            shape = RoundedCornerShape(AppDimens.radiusSmall)
                        ) {
                            Text(
                                text = Str.get(R.string.web_label),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                            )
                        }
                    } else {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(AppDimens.radiusSmall)
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
                            shape = RoundedCornerShape(AppDimens.radiusSmall)
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
                    UnifiedIconButton(
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

                UnifiedIconButton(
                    icon = Icons.Default.Add,
                    onClick = onOpen,
                    tint = MaterialTheme.colorScheme.primary,
                    contentDescription = Str.get(R.string.add_shortcut),
                    modifier = Modifier.size(32.dp)
                )

                UnifiedIconButton(
                    icon = Icons.Default.Delete,
                    onClick = onDelete,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}

// ==================== 插件详情对话框（plugin.json 字段 + 文件结构 + 大小） ====================

@Composable
fun PluginDetailDialog(
    plugin: PluginInfo,
    onDismiss: () -> Unit,
    onChangeCategory: (() -> Unit)? = null,
    onUninstall: (() -> Unit)? = null
) {
    val pluginManager = ServiceLocator.getPluginManager()
    val context = LocalContext.current
    val pluginDir = pluginManager.getPluginDirFile(plugin.pluginId)

    // plugin.json 原文（格式化）
    val rawJson = remember(plugin.pluginId) {
        val jsonFile = File(pluginDir, Constants.PLUGIN_CONFIG_FILE)
        if (jsonFile.exists()) {
            try {
                JSONObject(jsonFile.readText()).toString(2)
            } catch (e: Exception) {
                jsonFile.readText()
            }
        } else {
            try {
                JSONObject(plugin.toJson()).toString(2)
            } catch (e: Exception) {
                plugin.toJson()
            }
        }
    }

    // 插件目录统计
    val dirSize = remember(plugin.pluginId) { calculateDirSize(pluginDir) }
    val fileCount = remember(plugin.pluginId) { countDirFiles(pluginDir) }
    val fileTree = remember(plugin.pluginId) { buildFileTree(pluginDir) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        UnifiedCard(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .heightIn(max = 620.dp)
                .then(Modifier.dialogBackgroundOf(RoundedCornerShape(AppDimens.dialogCornerRadius))),
            containerColor = Color.Transparent
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
            ) {
                // ==================== 头部 ====================
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 4.dp)
                ) {
                    Icon(
                        Icons.Default.Extension,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Text(
                        Str.get(R.string.plugin_details),
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    )
                }

                Text(
                    text = "${plugin.name}  (${plugin.pluginId})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        SectionHeader(
                            Str.get(R.string.plugin_config_fields),
                            MaterialTheme.colorScheme.primary
                        )
                        DetailKeyValue(
                            Str.get(R.string.plugin_id), plugin.pluginId
                        )
                        DetailKeyValue(
                            Str.get(R.string.version_code), plugin.version.toString()
                        )
                        DetailKeyValue(
                            Str.get(R.string.version_name), plugin.versionName
                        )
                        DetailKeyValue(
                            Str.get(R.string.min_host_version), plugin.minHostVersion.toString()
                        )
                        DetailKeyValue(
                            Str.get(R.string.api_level), plugin.apiLevel.toString()
                        )
                        DetailKeyValue(
                            Str.get(R.string.plugin_name), plugin.name
                        )
                        DetailKeyValue(
                            Str.get(R.string.author), plugin.author
                        )
                        DetailKeyValue(
                            Str.get(R.string.description), plugin.description
                        )
                        DetailKeyValue(
                            Str.get(R.string.category), plugin.category
                        )
                        DetailKeyValue(
                            Str.get(R.string.ui_type), plugin.uiType
                        )
                        DetailKeyValue(
                            Str.get(R.string.entry_file), plugin.entry
                        )
                        DetailKeyValue(
                            Str.get(R.string.main_class), plugin.mainClass
                        )
                        DetailKeyValue(
                            Str.get(R.string.update_url), plugin.updateUrl
                        )
                        DetailKeyValue(
                            Str.get(R.string.plugin_notice_optional), plugin.notice
                        )
                        if (plugin.dependencies.isNotEmpty()) {
                            DetailKeyValue(
                                Str.get(R.string.dependencies),
                                plugin.dependencies.joinToString(", ")
                            )
                        }
                        if (plugin.permissions.isNotEmpty()) {
                            DetailKeyValue(
                                Str.get(R.string.permissions),
                                plugin.permissions.joinToString("\n")
                            )
                        }
                        if (plugin.backendStartCommand.isNotEmpty()) {
                            DetailKeyValue(
                                Str.get(R.string.start_command_required),
                                plugin.backendStartCommand
                            )
                        }
                    }

                    item {
                        SectionHeader(
                            Str.get(R.string.plugin_file_structure),
                            MaterialTheme.colorScheme.secondary
                        )
                        Text(
                            text = fileTree.ifEmpty { Str.get(R.string.plugin_details_dir_not_found) },
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    item {
                        SectionHeader(
                            Str.get(R.string.plugin_json_raw),
                            MaterialTheme.colorScheme.tertiary
                        )
                        Text(
                            text = rawJson,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // ==================== 大小与统计 ====================
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(AppDimens.radiusMedium)
                    ) {
                        Text(
                            text = Str.get(R.string.plugin_size),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    Text(
                        text = formatFileSize(dirSize),
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(AppDimens.radiusMedium)
                    ) {
                        Text(
                            text = Str.get(R.string.plugin_file_count, fileCount),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        ServiceLocator.getPluginManager().openPlugin(plugin.pluginId, context)
                        onDismiss()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = RoundedCornerShape(AppDimens.buttonCornerRadius)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(Str.get(R.string.run), fontSize = AppDimens.bodyTextSize.sp)
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (onChangeCategory != null) {
                    Button(
                        onClick = {
                            onChangeCategory()
                            onDismiss()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        ),
                        shape = RoundedCornerShape(AppDimens.buttonCornerRadius)
                    ) {
                        Icon(Icons.Default.Category, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(Str.get(R.string.btn_change_category), fontSize = AppDimens.bodyTextSize.sp)
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (onUninstall != null) {
                    Button(
                        onClick = {
                            onUninstall()
                            onDismiss()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        ),
                        shape = RoundedCornerShape(AppDimens.buttonCornerRadius)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(Str.get(R.string.btn_uninstall), fontSize = AppDimens.bodyTextSize.sp)
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                }

                UnifiedDialogTextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(Str.get(R.string.close), fontSize = AppDimens.bodyTextSize.sp)
                }
            }
        }
    }
}

// ==================== 更换分类对话框 ====================

@Composable
fun CategoryChangeDialog(
    targetPluginIds: Set<String>,
    onDismiss: () -> Unit,
    onCategoryUpdated: () -> Unit
) {
    val pluginManager = ServiceLocator.getPluginManager()
    val context = LocalContext.current
    val existingCategories = remember {
        pluginManager.getAllCategories()
            .filter { it != Str.get(R.string.all) }
            .distinct()
    }
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var customCategory by remember { mutableStateOf("") }
    val category = customCategory.trim().ifEmpty { selectedCategory }

    UnifiedDialog(
        onDismissRequest = onDismiss,
        title = Str.get(R.string.btn_change_category),
        content = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = Str.get(R.string.select_category),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 200.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(existingCategories) { itemCategory ->
                        UnifiedChip(
                            label = itemCategory,
                            selected = selectedCategory == itemCategory,
                            onClick = { selectedCategory = itemCategory },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (existingCategories.isEmpty()) {
                        item {
                            Text(
                                text = Str.get(R.string.uncategorized),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                UnifiedTextField(
                    value = customCategory,
                    onValueChange = { customCategory = it },
                    label = Str.get(R.string.custom_category),
                    placeholder = Str.get(R.string.custom_category),
                    leadingIcon = Icons.Default.Edit,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val newCategory = category ?: return@Button
                    targetPluginIds.forEach { id ->
                        pluginManager.updatePluginCategory(id, newCategory)
                    }
                    AppToast.success(context, Str.get(R.string.category_updated, newCategory))
                    onCategoryUpdated()
                    onDismiss()
                },
                enabled = category != null && category.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(AppDimens.radiusLarge)
            ) {
                Text(Str.get(R.string.ok_2))
            }
        },
        dismissButton = {
            UnifiedDialogTextButton(onClick = onDismiss) {
                Text(Str.get(R.string.cancel))
            }
        }
    )
}

// ==================== 详情对话框内部组件 ====================

@Composable
private fun SectionHeader(
    title: String,
    color: androidx.compose.ui.graphics.Color
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall.copy(
            fontWeight = FontWeight.Bold,
            color = color
        ),
        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
    )
}

@Composable
private fun DetailKeyValue(
    label: String,
    value: String
) {
    if (value.isBlank()) return
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(140.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
    }
}

// ==================== 插件目录统计辅助函数 ====================

private fun calculateDirSize(dir: File?): Long {
    if (dir == null || !dir.exists()) return 0
    return dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
}

private fun countDirFiles(dir: File?): Int {
    if (dir == null || !dir.exists()) return 0
    return dir.walkTopDown().filter { it.isFile }.count()
}

private fun buildFileTree(dir: File?): String {
    if (dir == null || !dir.exists()) return ""
    val sb = StringBuilder()
    dir.listFiles()?.sortedBy { if (it.isDirectory) 0 else 1 }?.forEach { file ->
        appendFileTree(sb, file, "")
    }
    return sb.toString().trim()
}

private fun appendFileTree(sb: StringBuilder, file: File, indent: String) {
    if (file.name.startsWith(".")) return
    sb.append(indent)
        .append(if (file.isDirectory) "[+] " else "    ")
        .append(file.name)
    if (file.isDirectory) {
        sb.append("/\n")
        val childIndent = indent + "    "
        file.listFiles()?.sortedBy { if (it.isDirectory) 0 else 1 }?.forEach { child ->
            appendFileTree(sb, child, childIndent)
        }
    } else {
        sb.append("  (").append(formatFileSize(file.length())).append(")\n")
    }
}