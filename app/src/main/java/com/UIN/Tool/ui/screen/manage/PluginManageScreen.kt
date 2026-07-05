// app/src/main/java/com/UIN/Tool/ui/screen/manage/PluginManageScreen.kt
package com.UIN.Tool.ui.screen.manage

import android.net.Uri
import android.provider.Settings
import android.widget.Toast
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.UIN.Tool.R
import com.UIN.Tool.core.di.ServiceLocator
import com.UIN.Tool.data.local.PreferenceManager
import com.UIN.Tool.domain.model.PluginInfo
import com.UIN.Tool.log.Logger
import com.UIN.Tool.plugin.PluginManager
import com.UIN.Tool.plugin.PluginPermissionManager
import com.UIN.Tool.ui.components.UIComponents
import com.UIN.Tool.utils.Constants
import com.UIN.Tool.utils.FileUtils
import com.UIN.Tool.utils.SecurityUtils
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

private const val TAG = "PluginManageScreen"

// ==================== 数据类 ====================
data class ButtonColors(
    val containerColor: Color,
    val contentColor: Color
)

// ==================== 插件管理主界面 ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PluginManageScreen(
    navController: NavController = rememberNavController()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pluginManager = ServiceLocator.getPluginManager()
    val preferenceManager = PreferenceManager(context)

    // ==================== 状态 ====================
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

    // ==================== 加载插件 ====================
    fun loadPlugins() {
        pluginManager.refreshPlugins()
        plugins = pluginManager.plugins.value
        selectedPluginIds = emptySet()
        Logger.d(TAG, "加载了 ${plugins.size} 个插件")
    }

    fun refreshWidgets() {
        try {
            com.UIN.Tool.widget.WidgetProvider.forceRefreshAllWidgets(context)
            com.UIN.Tool.widget.Widget1x1Provider.refresh1x1Widgets(context)
        } catch (e: Exception) {
            Logger.e(TAG, "刷新小部件失败", e)
        }
    }

    // ==================== 获取分类列表 ====================
    val categories = remember(plugins) {
        listOf("全部") + plugins.map { it.category }.distinct()
    }

    // ==================== 过滤插件 ====================
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

    // ==================== 导入单个插件 ====================
    fun importSinglePlugin(uri: Uri) {
        scope.launch {
            try {
                isLoading = true
                val fileName = uri.lastPathSegment?.substringAfterLast("/") ?: "plugin.tpk"
                val tempFile = File(context.cacheDir, "temp_import_${System.currentTimeMillis()}.tpk")
                if (FileUtils.copyUriToFile(context, uri, tempFile)) {
                    val info = pluginManager.installPlugin(tempFile, fileName)
                    if (info != null) {
                        Logger.success(TAG, "导入成功: ${info.name}")
                        loadPlugins()
                        refreshWidgets()
                        showResultDialog = "✅ 导入成功: ${info.name}"
                    } else {
                        showResultDialog = "❌ 导入失败: $fileName"
                    }
                }
                tempFile.delete()
            } catch (e: Exception) {
                Logger.e(TAG, "导入失败", e)
                showResultDialog = "❌ 导入失败: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    // ==================== 批量导入插件 ====================
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
                    "✅ $message"
                }
                if (successCount > 0) {
                    loadPlugins()
                    refreshWidgets()
                }
            } catch (e: Exception) {
                Logger.e(TAG, "批量导入失败", e)
                showResultDialog = "❌ 批量导入失败: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    // ==================== 插件集导入 ====================
    fun importPluginSet(uri: Uri) {
        scope.launch {
            try {
                isLoading = true
                exportProgress = "正在处理插件集..."

                val zipFile = File(context.cacheDir, "temp_plugin_set_${System.currentTimeMillis()}.zip")
                if (!FileUtils.copyUriToFile(context, uri, zipFile)) {
                    showResultDialog = "❌ 无法读取文件"
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
                            showResultDialog = "❌ 插件集中没有找到 .tpk 文件"
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
                                    Logger.success(TAG, "导入成功: ${info.name}")
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
                    "✅ 成功导入 $successCount 个插件\n❌ 失败 ${failCount} 个：\n${failNames.joinToString("\n")}"
                } else {
                    "✅ 成功导入 $successCount 个插件"
                }
                showResultDialog = message

                if (successCount > 0) {
                    loadPlugins()
                    refreshWidgets()
                    Toast.makeText(context, "成功导入 $successCount 个插件", Toast.LENGTH_LONG).show()
                }

            } catch (e: Exception) {
                Logger.e(TAG, "导入插件集失败", e)
                showResultDialog = "❌ 导入插件集失败: ${e.message}"
            } finally {
                isLoading = false
                exportProgress = ""
            }
        }
    }

    // ==================== 导出选中插件 ====================
    fun exportSelectedPlugins() {
        val selectedIds = selectedPluginIds.toList()
        if (selectedIds.isEmpty()) {
            Toast.makeText(context, "请先选择要导出的插件", Toast.LENGTH_SHORT).show()
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
                                Logger.success(TAG, "导出成功: ${info.name}")
                            } else {
                                failedList.add("${info.name} (目录不存在)")
                            }
                        } else {
                            failedList.add("$pluginId (插件不存在)")
                        }
                    }
                }

                val message = if (failedList.isNotEmpty()) {
                    "✅ 成功导出 $successCount 个插件到:\n${zipFile.absolutePath}\n\n❌ 失败 ${failedList.size} 个：\n${failedList.joinToString("\n")}"
                } else {
                    "✅ 成功导出 $successCount 个插件到:\n${zipFile.absolutePath}"
                }
                showResultDialog = message

                Toast.makeText(
                    context,
                    "成功导出 $successCount 个插件",
                    Toast.LENGTH_LONG
                ).show()

                selectedPluginIds = emptySet()

            } catch (e: Exception) {
                Logger.e(TAG, "导出插件失败", e)
                showResultDialog = "❌ 导出失败: ${e.message}"
            } finally {
                isLoading = false
                exportProgress = ""
            }
        }
    }

    // ==================== 卸载插件 ====================
    fun uninstallPlugin(plugin: PluginInfo) {
        scope.launch {
            try {
                isLoading = true
                pluginManager.uninstallPlugin(plugin.pluginId)
                loadPlugins()
                refreshWidgets()
                Logger.success(TAG, "卸载成功: ${plugin.name}")
                showDeleteDialog = null
            } catch (e: Exception) {
                Logger.e(TAG, "卸载失败", e)
                Toast.makeText(context, "卸载失败: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                isLoading = false
            }
        }
    }

    // ==================== 请求权限 ====================
    fun requestPluginPermissions(plugin: PluginInfo) {
        scope.launch {
            try {
                isLoading = true
                pluginManager.requestPluginPermissionsByGroups(
                    plugin.pluginId,
                    onProgress = { group, current, total ->
                        Logger.d(TAG, "权限请求进度: ($current/$total) $group")
                    },
                    onComplete = { allGranted ->
                        isLoading = false
                        if (allGranted) {
                            Toast.makeText(context, "✅ 所有权限已授予", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "⚠️ 部分权限被拒绝", Toast.LENGTH_SHORT).show()
                        }
                        showPermissionDialog = null
                    }
                )
            } catch (e: Exception) {
                Logger.e(TAG, "请求权限失败", e)
                isLoading = false
                Toast.makeText(context, "请求权限失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ==================== Launchers ====================
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

    // ==================== 初始化加载 ====================
    LaunchedEffect(Unit) { 
        loadPlugins()
        Logger.i(TAG, "插件管理界面初始化完成")
    }

    // ==================== UI ====================
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // ==================== 标题栏 ====================
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

        // ==================== 搜索栏 ====================
        UIComponents.TextInput(
            value = searchText,
            onValueChange = { searchText = it },
            placeholder = "搜索插件...",
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            leadingIcon = Icons.Default.Search
        )

        // ==================== 分类筛选 ====================
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

        // ==================== 进度显示 ====================
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

        // ==================== 操作按钮 ====================
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

        // ==================== 统计信息 ====================
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

        // ==================== 插件列表 ====================
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

    // ==================== 对话框 ====================

    // 删除确认对话框
    if (showDeleteDialog != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text("确认删除") },
            text = { 
                Text(
                    text = "确定要删除插件 \"${showDeleteDialog?.name}\" 吗？\n\n删除后插件数据将被清除，不可恢复。",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                UIComponents.PrimaryButton(
                    text = "删除",
                    onClick = { 
                        showDeleteDialog?.let { uninstallPlugin(it) }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            dismissButton = {
                UIComponents.TextButton(
                    text = "取消",
                    onClick = { showDeleteDialog = null }
                )
            }
        )
    }

    // 操作结果对话框
    if (showResultDialog != null) {
        AlertDialog(
            onDismissRequest = { showResultDialog = null },
            title = { Text("操作结果") },
            text = { 
                Text(
                    text = showResultDialog!!,
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                UIComponents.PrimaryButton(
                    text = "确定",
                    onClick = { showResultDialog = null },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        )
    }

    // 插件详情对话框
    if (showDetailDialog != null) {
        PluginDetailDialog(
            plugin = showDetailDialog!!,
            onDismiss = { showDetailDialog = null },
            onOpen = { 
                pluginManager.openPlugin(showDetailDialog!!.pluginId, context)
                showDetailDialog = null
            },
            onUninstall = {
                showDeleteDialog = showDetailDialog
                showDetailDialog = null
            },
            onManagePermissions = {
                showPermissionDialog = showDetailDialog
                showDetailDialog = null
            }
        )
    }

    // 权限管理对话框
    if (showPermissionDialog != null) {
        PermissionManagementDialog(
            plugin = showPermissionDialog!!,
            onDismiss = { showPermissionDialog = null },
            onRequestPermissions = { 
                requestPluginPermissions(showPermissionDialog!!)
            },
            onOpenSettings = {
                try {
                    val intent = android.content.Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    intent.data = Uri.parse("package:${context.packageName}")
                    context.startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(context, "打开设置失败", Toast.LENGTH_SHORT).show()
                }
            },
            onRefresh = {
                showPermissionDialog?.let { plugin ->
                    Toast.makeText(context, "权限状态已刷新", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    // 权限详情页面
    if (showPermissionDetail != null) {
        PermissionDetailDialog(
            plugin = showPermissionDetail!!,
            onDismiss = { showPermissionDetail = null },
            onRequestAll = {
                requestPluginPermissions(showPermissionDetail!!)
            }
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
                Logger.e("PluginManage", "添加文件到ZIP失败: ${file.name}", e)
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
                Color(0xFFFFF3E0) 
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
                    text = plugin.name.take(1).uppercase(),
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
                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "⚠️",
                                        fontSize = 10.sp
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text(
                                        text = "${permissionSummary.denied}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        } else if (permissionSummary.isAllGranted) {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = "✅ ${permissionSummary.granted}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
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
                            color = Color(0xFFE3F2FD),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "🌐 Web",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF1565C0),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                            )
                        }
                    } else {
                        Surface(
                            color = Color(0xFFFFF3E0),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "📱 原生",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFE65100),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                            )
                        }
                    }
                    
                    if (hasPermissions) {
                        Surface(
                            color = if (hasMissingPermissions)
                                MaterialTheme.colorScheme.tertiaryContainer
                            else
                                MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "${plugin.permissions.size} 项权限",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (hasMissingPermissions)
                                    MaterialTheme.colorScheme.onTertiaryContainer
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
                            MaterialTheme.colorScheme.tertiary 
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

// ==================== 插件详情对话框 ====================

@Composable
fun PluginDetailDialog(
    plugin: PluginInfo,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onUninstall: () -> Unit,
    onManagePermissions: () -> Unit
) {
    val pluginManager = ServiceLocator.getPluginManager()
    val permissionSummary = pluginManager.getPluginPermissionSummary(plugin.pluginId)
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(plugin.name, style = MaterialTheme.typography.titleLarge)
                if (plugin.isWebPlugin()) {
                    Surface(
                        color = Color(0xFFE3F2FD),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            "Web",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF1565C0),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                } else {
                    Surface(
                        color = Color(0xFFFFF3E0),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            "原生",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFE65100),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
            ) {
                DetailInfoRow("插件ID", plugin.pluginId)
                DetailInfoRow("版本", "${plugin.versionName} (${plugin.version})")
                DetailInfoRow("作者", plugin.author.ifEmpty { "未知" })
                DetailInfoRow("分类", plugin.category)
                
                if (plugin.description.isNotEmpty()) {
                    Divider(modifier = Modifier.padding(vertical = 8.dp))
                    Text(
                        text = "📝 描述",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = plugin.description,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                
                if (plugin.dependencies.isNotEmpty()) {
                    Divider(modifier = Modifier.padding(vertical = 8.dp))
                    Text(
                        text = "📦 依赖",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = plugin.dependencies.joinToString(", "),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                
                Divider(modifier = Modifier.padding(vertical = 8.dp))
                
                if (plugin.permissions.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🔐 权限状态",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "${permissionSummary.granted}/${permissionSummary.total}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium
                            )
                            if (permissionSummary.isAllGranted) {
                                Surface(
                                    color = Color(0xFF4CAF50),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        "✅ 已授权",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            } else {
                                Surface(
                                    color = Color(0xFFFF9800),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        "⚠️ ${permissionSummary.denied} 项未授权",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                    
                    if (plugin.permissions.isNotEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp)
                        ) {
                            plugin.permissions.take(3).forEach { permission ->
                                val granted = pluginManager.getPluginPermissionStatus(plugin.pluginId)[permission] ?: false
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(vertical = 2.dp)
                                ) {
                                    Text(
                                        if (granted) "✅" else "⬜",
                                        fontSize = 12.sp
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        PluginPermissionManager.getPermissionDisplayName(permission),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (granted) 
                                            MaterialTheme.colorScheme.onSurface 
                                        else 
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            if (plugin.permissions.size > 3) {
                                Text(
                                    "... 还有 ${plugin.permissions.size - 3} 项权限",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    Text(
                        text = "🔓 未声明任何权限",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    UIComponents.PrimaryButton(
                        text = "运行",
                        icon = Icons.Default.PlayArrow,
                        onClick = onOpen,
                        modifier = Modifier.weight(1f)
                    )
                    UIComponents.SecondaryButton(
                        text = "权限",
                        icon = Icons.Default.Security,
                        onClick = onManagePermissions,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    UIComponents.SecondaryButton(
                        text = "卸载",
                        icon = Icons.Default.Delete,
                        onClick = onUninstall,
                        modifier = Modifier.weight(1f)
                    )
                    UIComponents.TextButton(
                        text = "关闭",
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    )
}

@Composable
fun DetailInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1
        )
    }
}

// ==================== 权限管理对话框 ====================

@Composable
fun PermissionManagementDialog(
    plugin: PluginInfo,
    onDismiss: () -> Unit,
    onRequestPermissions: () -> Unit,
    onOpenSettings: () -> Unit,
    onRefresh: () -> Unit
) {
    val pluginManager = ServiceLocator.getPluginManager()
    var permissions by remember {
        mutableStateOf(pluginManager.getPluginPermissionStatus(plugin.pluginId))
    }
    var isRequesting by remember { mutableStateOf(false) }
    var progressMessage by remember { mutableStateOf("") }
    var allGranted by remember { mutableStateOf(permissions.values.all { it }) }
    
    fun refreshPermissions() {
        permissions = pluginManager.getPluginPermissionStatus(plugin.pluginId)
        allGranted = permissions.values.all { it }
    }
    
    LaunchedEffect(Unit) {
        refreshPermissions()
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("🔐 权限管理", style = MaterialTheme.typography.titleMedium)
                if (allGranted && permissions.isNotEmpty()) {
                    Surface(
                        color = Color(0xFF4CAF50),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            "已授权",
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
            ) {
                Text(
                    text = "插件: ${plugin.name}",
                    style = MaterialTheme.typography.bodyMedium,
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
                            onClick = onRefresh,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "刷新",
                                modifier = Modifier.size(18.dp)
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
                                        onRequestPermissions()
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
            }
        },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (permissions.isNotEmpty() && !allGranted) {
                    UIComponents.PrimaryButton(
                        text = if (isRequesting) "请求中..." else "一键授权所有权限",
                        icon = Icons.Default.Check,
                        onClick = onRequestPermissions,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isRequesting,
                        loading = isRequesting
                    )
                }
                
                val hasSpecial = permissions.keys.any { 
                    PluginPermissionManager.isSpecialPermission(it) && !(permissions[it] ?: false)
                }
                if (hasSpecial) {
                    UIComponents.SecondaryButton(
                        text = "⚙️ 去系统设置开启特殊权限",
                        onClick = onOpenSettings,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                
                UIComponents.TextButton(
                    text = "关闭",
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    )
}

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
            containerColor = if (granted)
                MaterialTheme.colorScheme.surface
            else
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (granted) Icons.Default.CheckCircle else Icons.Default.Warning,
                contentDescription = null,
                tint = if (granted)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(20.dp)
            )
            
            Spacer(modifier = Modifier.width(10.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = PluginPermissionManager.getPermissionDisplayName(permission),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (granted)
                        MaterialTheme.colorScheme.onSurface
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (PluginPermissionManager.isSpecialPermission(permission)) {
                    Text(
                        text = "⚠️ 特殊权限，需在系统设置中开启",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary
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
                UIComponents.PrimaryButton(
                    text = "授权",
                    onClick = onRequest,
                    modifier = Modifier.height(28.dp)
                )
            }
        }
    }
}

// ==================== 权限详情对话框 ====================

@Composable
fun PermissionDetailDialog(
    plugin: PluginInfo,
    onDismiss: () -> Unit,
    onRequestAll: () -> Unit
) {
    val pluginManager = ServiceLocator.getPluginManager()
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
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "权限详情 - ${plugin.name}",
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 450.dp)
            ) {
                val grantedCount = permissions.values.count { it }
                val totalCount = permissions.size
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "已授权: $grantedCount / $totalCount",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (grantedCount == totalCount)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.tertiary
                    )
                    if (grantedCount < totalCount) {
                        UIComponents.PrimaryButton(
                            text = if (isRequesting) "请求中..." else "一键授权",
                            onClick = onRequestAll,
                            modifier = Modifier.height(32.dp),
                            enabled = !isRequesting,
                            loading = isRequesting
                        )
                    }
                }
                
                Divider()
                
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
                            PermissionDetailItem(
                                permission = permission,
                                granted = granted
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            UIComponents.TextButton(
                text = "关闭",
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            )
        }
    )
}

@Composable
fun PermissionDetailItem(
    permission: String,
    granted: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (granted) Icons.Default.CheckCircle else Icons.Default.Warning,
                contentDescription = null,
                tint = if (granted)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(24.dp)
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = PluginPermissionManager.getPermissionDisplayName(permission),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (granted)
                        MaterialTheme.colorScheme.onSurface
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = PluginPermissionManager.getPermissionDescription(permission),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (PluginPermissionManager.isSpecialPermission(permission)) {
                    Text(
                        text = "⚠️ 特殊权限：需在系统设置中手动开启",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary
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
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            } else {
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = "未授权",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}