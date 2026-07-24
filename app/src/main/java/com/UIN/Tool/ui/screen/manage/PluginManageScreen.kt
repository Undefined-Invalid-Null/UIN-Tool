// ui/screen/manage/PluginManageScreen.kt
package com.UIN.Tool.ui.screen.manage

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
    var selectedCategory by remember { mutableStateOf("全部") }

    fun loadPlugins() {
        pluginManager.refreshPlugins()
        plugins = pluginManager.plugins.value
        selectedPluginIds = emptySet()
        AppLog.d(TAG, "加载了 ${plugins.size} 个插件")
    }

    fun refreshWidgets() {
        try {
            com.UIN.Tool.widget.WidgetProvider.forceRefreshAllWidgets(context)
            com.UIN.Tool.widget.Widget1x1Provider.refresh1x1Widgets(context)
        } catch (e: Exception) {
            AppLog.e(TAG, "刷新小部件失败", e)
        }
    }

    val categories = remember(plugins) {
        listOf("全部") + plugins.map { it.category }.distinct()
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
        if (selectedCategory != "全部") {
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
                        AppLog.success(TAG, "导入成功: ${info.name}")
                        loadPlugins()
                        refreshWidgets()
                        showResultDialog = "导入成功: ${info.name}"
                    } else {
                        showResultDialog = "导入失败: $fileName"
                    }
                }
                tempFile.delete()
            } catch (e: Exception) {
                AppLog.e(TAG, "导入失败", e)
                showResultDialog = "导入失败: ${e.message}"
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
                        failNames.add(uri.lastPathSegment ?: "未知")
                    }
                }

                val message = "成功导入 $successCount 个插件"
                showResultDialog = if (failCount > 0) {
                    "$message，失败 ${failCount} 个：\n${failNames.joinToString("\n")}"
                } else {
                    "成功导入 $message"
                }
                if (successCount > 0) {
                    loadPlugins()
                    refreshWidgets()
                }
            } catch (e: Exception) {
                AppLog.e(TAG, "批量导入失败", e)
                showResultDialog = "批量导入失败: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    fun importPluginSet(uri: Uri) {
        scope.launch {
            try {
                isLoading = true
                exportProgress = "正在处理插件集..."

                val zipFile = File(context.cacheDir, "temp_plugin_set_${System.currentTimeMillis()}.zip")
                if (!FileUtils.copyUriToFile(context, uri, zipFile)) {
                    showResultDialog = "无法读取文件"
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
                            showResultDialog = "插件集中没有找到 .tpk 文件"
                            return@launch
                        }

                        val newEntries = zf.entries()
                        while (newEntries.hasMoreElements()) {
                            val entry = newEntries.nextElement()
                            if (!entry.isDirectory && entry.name.endsWith(".tpk")) {
                                processed++
                                val fileName = File(entry.name).name
                                exportProgress = "正在处理: $fileName ($processed/$total)"

                                val outFile = File(extractDir, fileName)
                                zf.getInputStream(entry).use { input ->
                                    outFile.outputStream().use { output ->
                                        input.copyTo(output)
                                    }
                                }

                                if (!SecurityUtils.verifyFileSignature(outFile, preferenceManager)) {
                                    failCount++
                                    failNames.add("$fileName (签名验证失败)")
                                    continue
                                }

                                val info = pluginManager.installPlugin(outFile, fileName)
                                if (info != null) {
                                    successCount++
                                    AppLog.success(TAG, "导入成功: ${info.name}")
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
                    "成功导入 $successCount 个插件，失败 ${failCount} 个：\n${failNames.joinToString("\n")}"
                } else {
                    "成功导入 $successCount 个插件"
                }
                showResultDialog = message

                if (successCount > 0) {
                    loadPlugins()
                    refreshWidgets()
                    AppToast.success(context, "成功导入 $successCount 个插件")
                }

            } catch (e: Exception) {
                AppLog.e(TAG, "导入插件集失败", e)
                showResultDialog = "导入插件集失败: ${e.message}"
            } finally {
                isLoading = false
                exportProgress = ""
            }
        }
    }

    fun exportSelectedPlugins() {
        val selectedIds = selectedPluginIds.toList()
        if (selectedIds.isEmpty()) {
            AppToast.warning(context, "请先选择要导出的插件")
            return
        }

        scope.launch {
            try {
                isLoading = true
                exportProgress = "正在导出插件..."

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
                                exportProgress = "正在导出: ${info.name}"
                                addPluginDirToZip(zos, pluginDir, "${info.pluginId}/")
                                successCount++
                                AppLog.success(TAG, "导出成功: ${info.name}")
                            } else {
                                failedList.add("${info.name} (目录不存在)")
                            }
                        } else {
                            failedList.add("$pluginId (插件不存在)")
                        }
                    }
                }

                val message = if (failedList.isNotEmpty()) {
                    "成功导出 $successCount 个插件到:\n${zipFile.absolutePath}\n\n失败 ${failedList.size} 个：\n${failedList.joinToString("\n")}"
                } else {
                    "成功导出 $successCount 个插件到:\n${zipFile.absolutePath}"
                }
                showResultDialog = message

                AppToast.success(context, "成功导出 $successCount 个插件")

                selectedPluginIds = emptySet()

            } catch (e: Exception) {
                AppLog.e(TAG, "导出插件失败", e)
                showResultDialog = "导出失败: ${e.message}"
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
                AppLog.success(TAG, "卸载成功: ${plugin.name}")
                showDeleteDialog = null
            } catch (e: Exception) {
                AppLog.e(TAG, "卸载失败", e)
                AppToast.error(context, "卸载失败: ${e.message}")
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
                        AppLog.d(TAG, "权限请求进度: ($current/$total) $group")
                    },
                    onComplete = { allGranted ->
                        isLoading = false
                        if (allGranted) {
                            AppToast.success(context, "所有权限已授予")
                        } else {
                            AppToast.warning(context, "部分权限被拒绝")
                        }
                        showPermissionDialog = null
                    }
                )
            } catch (e: Exception) {
                AppLog.e(TAG, "请求权限失败", e)
                isLoading = false
                AppToast.error(context, "请求权限失败: ${e.message}")
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
        AppLog.i(TAG, "插件管理界面初始化完成")
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
            UIComponents.TitleText("插件管理")
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
            placeholder = "搜索插件...",
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
                text = "导入",
                icon = Icons.Default.FileUpload,
                onClick = { importLauncher.launch("*/*") },
                modifier = Modifier.weight(1f),
                enabled = !isLoading
            )
            UIComponents.SecondaryButton(
                text = "批量",
                icon = Icons.Default.Add,
                onClick = { batchImportLauncher.launch(arrayOf("*/*")) },
                modifier = Modifier.weight(1f),
                enabled = !isLoading
            )
            UIComponents.SecondaryButton(
                text = "插件集",
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
                text = "导出 (${selectedPluginIds.size})",
                icon = Icons.Default.FileDownload,
                onClick = { exportSelectedPlugins() },
                modifier = Modifier.weight(1f),
                enabled = selectedPluginIds.isNotEmpty() && !isLoading
            )
            UIComponents.SecondaryButton(
                text = "删除",
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
                text = "共 ${filteredPlugins.size} 个插件",
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
                        if (selectedPluginIds.size == filteredPlugins.size) "取消全选" else "全选",
                        fontSize = 12.sp
                    )
                }
                if (selectedPluginIds.isNotEmpty()) {
                    Text(
                        text = "已选 ${selectedPluginIds.size} 个",
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
                            text = "加载插件中...",
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
                            text = if (searchText.isNotEmpty() || selectedCategory != "全部") {
                                "没有匹配的插件"
                            } else {
                                "暂无已安装插件"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (searchText.isEmpty() && selectedCategory == "全部") {
                            Text(
                                text = "点击「导入」按钮导入插件文件",
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
            title = "确认删除",
            message = "确定要删除插件 \"${showDeleteDialog!!.name}\" 吗？\n\n删除后插件数据将被清除，不可恢复。",
            confirmText = "删除",
            dismissText = "取消",
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
            title = "操作结果",
            message = showResultDialog!!,
            onDismiss = { showResultDialog = null }
        )
    }

    // ==================== 插件详情对话框（包含说明，无 emoji） ====================
    if (showDetailDialog != null) {
        UIComponents.ConfirmDialog(
            title = showDetailDialog!!.name,
            message = buildDetailMessage(showDetailDialog!!),
            confirmText = "运行",
            dismissText = "关闭",
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
                AppLog.e("PluginManage", "添加文件到ZIP失败: ${file.name}", e)
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
        append("版本: ${plugin.versionName} (${plugin.version})\n")
        append("作者: ${plugin.author.ifEmpty { "未知" }}\n")
        append("分类: ${plugin.category}\n")
        if (plugin.description.isNotEmpty()) {
            append("\n描述: ${plugin.description}\n")
        }
        // 显示插件说明（如果存在）
        if (plugin.hasNotice()) {
            append("\n说明:\n${plugin.notice}\n")
        }
        if (plugin.dependencies.isNotEmpty()) {
            append("\n依赖: ${plugin.dependencies.joinToString(", ")}\n")
        }
        if (plugin.permissions.isNotEmpty()) {
            val pluginManager = ServiceLocator.getPluginManager()
            val summary = pluginManager.getPluginPermissionSummary(plugin.pluginId)
            append("\n权限状态: ${summary.granted}/${summary.total}\n")
            if (!summary.isAllGranted) {
                append("${summary.denied} 项权限未授予")
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
                        "权限管理",
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
                                "已授权",
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Text(
                    text = "插件: ${plugin.name}",
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
                            text = "权限状态: ${permissions.values.count { it }}/${permissions.size} 已授予",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        IconButton(
                            onClick = { refreshPermissions() },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "刷新",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (permissions.isEmpty()) {
                    Text(
                        text = "该插件没有声明任何权限",
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
                                                progressMessage = "请求中: $group ($current/$total)"
                                            },
                                            onComplete = { allGranted ->
                                                isRequesting = false
                                                progressMessage = ""
                                                refreshPermissions()
                                                if (allGranted) {
                                                    AppToast.success(context, "所有权限已授予")
                                                } else {
                                                    AppToast.warning(context, "部分权限被拒绝")
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
                                        progressMessage = "请求中: $group ($current/$total)"
                                    },
                                    onComplete = { allGranted ->
                                        isRequesting = false
                                        progressMessage = ""
                                        refreshPermissions()
                                        if (allGranted) {
                                            AppToast.success(context, "所有权限已授予")
                                        } else {
                                            AppToast.warning(context, "部分权限被拒绝")
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
                            Text(if (isRequesting) "请求中..." else "一键授权所有权限", fontSize = 14.sp)
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
                                    AppToast.error(context, "打开设置失败")
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
                            Text("去系统设置开启特殊权限", fontSize = 14.sp)
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
                        Text("关闭", fontSize = 14.sp)
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
        title = "权限详情 - ${plugin.name}",
        message = buildString {
            val grantedCount = permissions.values.count { it }
            val totalCount = permissions.size
            append("已授权: $grantedCount / $totalCount\n\n")
            if (permissions.isEmpty()) {
                append("该插件没有声明任何权限")
            } else {
                permissions.entries.forEach { (permission, granted) ->
                    val status = if (granted) "已授予" else "未授权"
                    append("• ${PluginPermissionManager.getPermissionDisplayName(permission)}: $status\n")
                }
            }
        },
        confirmText = if (permissions.values.any { !it }) "一键授权" else "确定",
        dismissText = "关闭",
        onConfirm = {
            if (permissions.values.any { !it }) {
                isRequesting = true
                pluginManager.requestPluginPermissionsByGroups(
                    plugin.pluginId,
                    onComplete = { allGranted ->
                        isRequesting = false
                        refreshPermissions()
                        if (allGranted) {
                            AppToast.success(context, "所有权限已授予")
                        } else {
                            AppToast.warning(context, "部分权限被拒绝")
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
                        text = "特殊权限，需在系统设置中开启",
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
                        text = "已授予",
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
                    Text("授权", fontSize = 11.sp)
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
                                text = "原生",
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
                                text = "${plugin.permissions.size} 项权限",
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