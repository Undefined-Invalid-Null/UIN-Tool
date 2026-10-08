package com.UIN.Tool.ui.screen.proot

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.UIN.Tool.log.Logger
import com.UIN.Tool.proot.ProotInstaller
import com.UIN.Tool.proot.RootfsManager
import com.UIN.Tool.ui.components.unified.*
import com.UIN.Tool.ui.components.LongTaskRegistry
import com.UIN.Tool.ui.theme.AppDimens
import com.UIN.Tool.ui.theme.UINToolTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class DownloadableDistro(
    val name: String,
    val displayName: String,
    val assetName: String,
    val descriptionKey: String,
    val estimatedSize: String,
    // F-01：是否随 APK 内置（只有 debian-rootfs.tar.xz 在 assets 里）。
    // 其余发行版走 installFromAsset 必然 "No rootfs asset found"，原先界面却照常可选。
    val bundled: Boolean = false
)

/** F-16：容器长任务统一注册表键（导入/导出/下载共用；isInstalling 串行门禁下同时只有一个） */
private const val CONTAINER_TASK = "container_op"

/**
 * D-07：容器列表 + 默认容器的统一加载入口。
 * listContainers 会对每个容器做全树扫描（size/count），必须在 IO 线程执行。
 */
private suspend fun loadContainerState(
    context: android.content.Context
): Pair<List<RootfsManager.ContainerInfo>, String> =
    withContext(Dispatchers.IO) {
        RootfsManager.listContainers(context) to RootfsManager.getDefaultContainer(context)
    }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ContainerManagementScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // D-07：listContainers 会对每个容器做全树扫描（size+count），原先在 remember{} 里
    // 同步执行＝主线程全树扫描；改为先空列表，再由 LaunchedEffect 在 IO 线程加载。
    var containers by remember { mutableStateOf<List<RootfsManager.ContainerInfo>>(emptyList()) }
    var defaultContainer by remember { mutableStateOf(RootfsManager.getDefaultContainer(context)) }
    // D-33：共享存储 bind 开关（与 ProotRuntime.bindMountsFor 读同一个 key）
    var bindSharedStorage by remember {
        mutableStateOf(
            context.getSharedPreferences(
                com.UIN.Tool.constants.AppConstants.PREF_NAME,
                android.content.Context.MODE_PRIVATE
            ).getBoolean(com.UIN.Tool.constants.AppConstants.KEY_PROOT_BIND_SHARED_STORAGE, true)
        )
    }
    var prootReady by remember { mutableStateOf(false) }
    var showDownloadDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf<String?>(null) }
    var showResetConfirm by remember { mutableStateOf<String?>(null) }
    var showExportConfirm by remember { mutableStateOf<String?>(null) }
    var showInfoDialog by remember { mutableStateOf<String?>(null) }
    var showRenameDialog by remember { mutableStateOf<String?>(null) }
    var renameText by remember { mutableStateOf("") }
    var exportContainerName by remember { mutableStateOf<String?>(null) }
    var exportCompressionLevel by remember { mutableIntStateOf(9) }
    var isInstalling by remember { mutableStateOf(false) }
    // G-21：门禁与 RootfsManager 底层 per-container 锁联动（isInstalling 只是本屏幕的局部状态）
    val rootfsLocked = isInstalling || RootfsManager.anyBusy()
    var statusMessage by remember { mutableStateOf<String?>(null) }
    // F-16：RootfsManager 进度回调 → 统一长任务注册表（原局部进度状态 + 进度对话框由 LongTaskHost 接管）
    fun publishContainerProgress(
        title: String, current: Int, total: Int,
        bytesProcessed: Long, totalBytes: Long, currentFile: String = ""
    ) {
        val sizeText = if (totalBytes > 0) "${formatSize(bytesProcessed)} / ${formatSize(totalBytes)}" else ""
        val countText = when {
            total > 0 -> context.getString(com.UIN.Tool.R.string.container_export_progress, current, total)
            current > 0 -> context.getString(com.UIN.Tool.R.string.container_import_entry_count, current)
            else -> ""
        }
        var detail = listOfNotNull(
            countText.takeIf { it.isNotEmpty() },
            sizeText.takeIf { it.isNotEmpty() }
        ).joinToString(" · ")
        if (detail.isEmpty()) detail = context.getString(com.UIN.Tool.R.string.container_export_scanning)
        if (currentFile.isNotEmpty()) detail = "$detail · $currentFile"
        LongTaskRegistry.update(CONTAINER_TASK, detail, if (total > 0) current.toFloat() / total else null)
    }

    /** D-07：容器列表/默认值刷新统一放 IO，避免主线程全树扫描 */
    suspend fun refreshContainers() {
        val (list, def) = loadContainerState(context)
        containers = list
        defaultContainer = def
    }

    LaunchedEffect(Unit) {
        val (list, def) = loadContainerState(context)
        containers = list
        defaultContainer = def
        prootReady = withContext(Dispatchers.IO) { ProotInstaller.isInstalled(context) }
    }

    // F-01：口径统一 —— Debian 是**内置资源**（assets 43MB，安装不走网络），
    // 其余发行版并未内置、离线安装必然失败，这里标注出来并禁用选择。
    val downloadableDistros = listOf(
        DownloadableDistro("debian", "Debian (trixie)", "debian-rootfs.tar.xz", "distro_debian_desc", "43 MB", bundled = true),
        DownloadableDistro("alpine", "Alpine Linux", "alpine-minirootfs.tar.gz", "distro_alpine_desc", "~4 MB", bundled = false),
        DownloadableDistro("ubuntu", "Ubuntu (24.04 LTS)", "ubuntu-rootfs.tar.xz", "distro_ubuntu_desc", "~60 MB", bundled = false),
        DownloadableDistro("arch", "Arch Linux", "archlinux-rootfs.tar.xz", "distro_arch_desc", "~80 MB", bundled = false)
    )

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
                    result.data?.data?.let { uri ->
                scope.launch {
                    isInstalling = true
                    try {
                        val inputStream = context.contentResolver.openInputStream(uri) ?: return@launch
                        val displayName = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                            cursor.moveToFirst()
                            if (nameIndex >= 0) cursor.getString(nameIndex) else null
                        } ?: "imported-rootfs"
                        val validExtensions = listOf(".tar.xz", ".tar.gz", ".tgz", ".tar.zst", ".zst", ".tar")
                        val ext = validExtensions.firstOrNull { displayName.lowercase().endsWith(it) }
                        if (ext == null) {
                            statusMessage = context.getString(com.UIN.Tool.R.string.container_import_failed, "Unsupported format: $displayName")
                            isInstalling = false
                            return@launch
                        }
                        val tempFile = File(context.cacheDir, "imported-$displayName")
                        tempFile.outputStream().use { out -> inputStream.copyTo(out) }
                        inputStream.close()

                        val containerName = displayName.removeSuffix(ext)
                            .replace(Regex("[^a-zA-Z0-9_-]"), "_")
                            .ifEmpty { "imported" }

                        val totalEntries = withContext(Dispatchers.IO) {
                            RootfsManager.countTarEntries(tempFile)
                        }

                        LongTaskRegistry.begin(CONTAINER_TASK, context.getString(com.UIN.Tool.R.string.container_importing))
                        val result = RootfsManager.installFromFile(context, containerName, tempFile) { entryCount, entryName, bytesProcessed, totalBytes ->
                            publishContainerProgress(
                                context.getString(com.UIN.Tool.R.string.container_importing),
                                entryCount, totalEntries, bytesProcessed, totalBytes, entryName
                            )
                        }
                        if (result.isSuccess) LongTaskRegistry.finish(CONTAINER_TASK)
                        else LongTaskRegistry.fail(CONTAINER_TASK, result.exceptionOrNull()?.message ?: "")
                        tempFile.delete()

                        refreshContainers()
                        statusMessage = if (result.isSuccess) {
                            context.getString(com.UIN.Tool.R.string.container_import_success, containerName)
                        } else {
                            context.getString(com.UIN.Tool.R.string.container_import_failed, result.exceptionOrNull()?.message ?: "")
                        }
                    } catch (e: Exception) {
                        statusMessage = context.getString(com.UIN.Tool.R.string.container_import_failed, e.message ?: "")
                        LongTaskRegistry.fail(CONTAINER_TASK, e.message ?: "")
                    } finally {
                        isInstalling = false
                    }
                }
            }
        }
    }

    val exportSaverLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                val name = exportContainerName ?: return@rememberLauncherForActivityResult
                scope.launch {
                    isInstalling = true
                    LongTaskRegistry.begin(CONTAINER_TASK, context.getString(com.UIN.Tool.R.string.container_exporting, name))
                    try {
                        val exportDir = File(context.cacheDir, "export")
                        val result = RootfsManager.exportContainer(context, name, exportDir, exportCompressionLevel) { fileCount, totalFiles, bytesWritten, totalBytes, currentFile ->
                            publishContainerProgress(
                                context.getString(com.UIN.Tool.R.string.container_exporting, name),
                                fileCount, totalFiles, bytesWritten, totalBytes, currentFile
                            )
                        }
                        if (result.isSuccess) LongTaskRegistry.finish(CONTAINER_TASK)
                        else LongTaskRegistry.fail(CONTAINER_TASK, result.exceptionOrNull()?.message ?: "")
                        if (result.isSuccess) {
                            val exportedFile = result.getOrThrow()
                            withContext(Dispatchers.IO) {
                                context.contentResolver.openOutputStream(uri)?.use { out ->
                                    exportedFile.inputStream().use { inp -> inp.copyTo(out) }
                                }
                            }
                            exportedFile.delete()
                            statusMessage = context.getString(com.UIN.Tool.R.string.container_export_success, name)
                        } else {
                            statusMessage = context.getString(com.UIN.Tool.R.string.container_export_failed, result.exceptionOrNull()?.message ?: "")
                        }
                    } catch (e: Exception) {
                        statusMessage = context.getString(com.UIN.Tool.R.string.container_export_failed, e.message ?: "")
                        LongTaskRegistry.fail(CONTAINER_TASK, e.message ?: "")
                    } finally {
                        isInstalling = false
                        exportContainerName = null
                    }
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(AppDimens.spacingXLarge)
    ) {
        UnifiedTitleText(
            text = context.getString(com.UIN.Tool.R.string.proot_container_management),
            modifier = Modifier.padding(bottom = AppDimens.spacingXLarge)
        )

        // Proot status
        UnifiedCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = AppDimens.spacingXLarge)
        ) {
            Column(modifier = Modifier.padding(AppDimens.cardPadding)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (prootReady) Icons.Default.CheckCircle else Icons.Default.Error,
                        contentDescription = null,
                        tint = if (prootReady) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(AppDimens.iconLarge)
                    )
                    Spacer(modifier = Modifier.width(AppDimens.spacingMedium))
                    UnifiedBodyText(
                        text = if (prootReady) context.getString(com.UIN.Tool.R.string.proot_status_ready)
                               else context.getString(com.UIN.Tool.R.string.proot_status_not_installed)
                    )
                }
                if (!prootReady) {
                    Spacer(modifier = Modifier.height(AppDimens.spacingMedium))
                    UnifiedButton(
                        text = context.getString(com.UIN.Tool.R.string.proot_install_button),
                        onClick = {
                            scope.launch {
                                // D-01/P0：ensureInstalled 是阻塞 IO（解压+chmod），放 IO 线程
                                val result = withContext(Dispatchers.IO) {
                                    ProotInstaller.ensureInstalled(context)
                                }
                                prootReady = result.isSuccess
                                statusMessage = if (result.isSuccess) context.getString(com.UIN.Tool.R.string.proot_install_success)
                                               else {
                                                   val reason = result.exceptionOrNull()?.message
                                                   val base = context.getString(com.UIN.Tool.R.string.proot_install_failed)
                                                   if (reason.isNullOrBlank()) base else "$base ($reason)"
                                               }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // Current default
        UnifiedCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = AppDimens.spacingXLarge)
        ) {
            Column(modifier = Modifier.padding(AppDimens.cardPadding)) {
                UnifiedSectionTitle(text = context.getString(com.UIN.Tool.R.string.container_current_default))
                Spacer(modifier = Modifier.height(AppDimens.spacingSmall))
                UnifiedBodyText(
                    text = defaultContainer,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        // D-33：是否把宿主共享存储挂进容器（关掉后容器内进程只能看到自己的 rootfs）
        UnifiedCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = AppDimens.spacingXLarge)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(AppDimens.cardPadding),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    UnifiedSectionTitle(text = context.getString(com.UIN.Tool.R.string.bind_shared_storage))
                    Spacer(modifier = Modifier.height(AppDimens.spacingSmall))
                    UnifiedBodyText(
                        text = context.getString(com.UIN.Tool.R.string.bind_shared_storage_desc),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                UnifiedSwitch(
                    checked = bindSharedStorage,
                    onCheckedChange = { enabled ->
                        bindSharedStorage = enabled
                        context.getSharedPreferences(
                            com.UIN.Tool.constants.AppConstants.PREF_NAME,
                            android.content.Context.MODE_PRIVATE
                        ).edit()
                            .putBoolean(com.UIN.Tool.constants.AppConstants.KEY_PROOT_BIND_SHARED_STORAGE, enabled)
                            .apply()
                    }
                )
            }
        }

        // Installed containers
        UnifiedSectionTitle(
            text = context.getString(com.UIN.Tool.R.string.container_installed_list),
            modifier = Modifier.padding(bottom = AppDimens.spacingMedium)
        )
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = AppDimens.spacingXLarge)
        ) {
            items(containers) { container ->
                UnifiedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = AppDimens.spacingMedium)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = {
                                    defaultContainer = container.name
                                    RootfsManager.setDefaultContainer(context, container.name)
                                    statusMessage = context.getString(com.UIN.Tool.R.string.container_default_switched, container.name)
                                },
                                onLongClick = { showInfoDialog = container.name }
                            )
                            .padding(AppDimens.cardPadding),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            UnifiedBodyText(text = container.name)
                            UnifiedCaptionText(
                                text = "${container.sizeDisplay} · ${if (container.ready) context.getString(com.UIN.Tool.R.string.container_ready) else context.getString(com.UIN.Tool.R.string.container_not_ready)}",
                                color = if (container.ready) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            )
                        }
                        if (container.name == defaultContainer) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = context.getString(com.UIN.Tool.R.string.container_default_label),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        if (containers.size > 1 && container.name != defaultContainer) {
                            UnifiedIconButton(
                                icon = Icons.Default.Delete,
                                onClick = { showDeleteConfirm = container.name }
                            )
                        }
                        if (container.ready) {
                            UnifiedIconButton(
                                icon = Icons.Default.Share,
                                onClick = { showExportConfirm = container.name }
                            )
                        }
                        // F-02：重置按钮原先完全缺失，对话框状态永远无法置位（死开关）。
                        // 仅对「存在内置 rootfs 资产」的容器开放：否则 reset 会先删除再安装失败＝数据丢失。
                        if (container.ready &&
                            com.UIN.Tool.plugin.ProotContainerManager.findRootfsAsset(context, container.name) != null
                        ) {
                            UnifiedIconButton(
                                icon = Icons.Default.Refresh,
                                onClick = { showResetConfirm = container.name },
                                contentDescription = context.getString(com.UIN.Tool.R.string.container_reset)
                            )
                        }
                    }
                }
            }
        }

        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppDimens.spacingMedium)
        ) {
            UnifiedButton(
                text = context.getString(com.UIN.Tool.R.string.container_import),
                onClick = {
                    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "*/*"
                    }
                    filePickerLauncher.launch(intent)
                },
                modifier = Modifier.weight(1f),
                enabled = !rootfsLocked,
                icon = Icons.Default.FolderOpen
            )
            // F-02：下载对话框原先无任何入口（showDownloadDialog 永远为 false），补上触发按钮
            // F-01：内置 Debian 的安装不走网络，按钮口径改为「安装」
            UnifiedButton(
                text = context.getString(com.UIN.Tool.R.string.install),
                onClick = { showDownloadDialog = true },
                modifier = Modifier.weight(1f),
                enabled = !rootfsLocked,
                icon = Icons.Default.Download
            )
        }

        // Status message
        statusMessage?.let {
            Spacer(modifier = Modifier.height(AppDimens.spacingMedium))
            UnifiedCaptionText(text = it)
        }
    }

    // Delete confirmation
    showDeleteConfirm?.let { name ->
        UnifiedConfirmDialog(
            title = context.getString(com.UIN.Tool.R.string.container_delete_confirm_title),
            message = context.getString(com.UIN.Tool.R.string.container_delete_confirm_message, name),
            onConfirm = {
                val target = name
                showDeleteConfirm = null
                scope.launch {
                    // G-21：与底层锁联动，忙时不再叠第二条删除
                    if (RootfsManager.isBusy(target)) {
                        statusMessage = context.getString(com.UIN.Tool.R.string.container_busy)
                        return@launch
                    }
                    isInstalling = true
                    val ok = withContext(Dispatchers.IO) { RootfsManager.deleteContainer(context, target) }
                    refreshContainers()
                    if (defaultContainer == target) {
                        defaultContainer = com.UIN.Tool.plugin.BackendConfig.BUILTIN_CONTAINER
                        withContext(Dispatchers.IO) {
                            RootfsManager.setDefaultContainer(context, com.UIN.Tool.plugin.BackendConfig.BUILTIN_CONTAINER)
                        }
                    }
                    statusMessage = if (ok) context.getString(com.UIN.Tool.R.string.container_deleted, target)
                                    else context.getString(com.UIN.Tool.R.string.delete_failed)
                    isInstalling = false
                }
            },
            onDismiss = { showDeleteConfirm = null },
            confirmText = context.getString(com.UIN.Tool.R.string.container_delete),
            dismissText = context.getString(com.UIN.Tool.R.string.cancel),
            isDestructive = true
        )
    }

    // Reset confirmation
    showResetConfirm?.let { name ->
        UnifiedConfirmDialog(
            title = context.getString(com.UIN.Tool.R.string.container_reset),
            message = context.getString(com.UIN.Tool.R.string.container_reset_confirm, name),
            onConfirm = {
                showResetConfirm = null
                scope.launch {
                    if (RootfsManager.isBusy(name)) {
                        statusMessage = context.getString(com.UIN.Tool.R.string.container_busy)
                        return@launch
                    }
                    isInstalling = true
                    val result = RootfsManager.resetContainer(context, name)
                    refreshContainers()
                    statusMessage = if (result.isSuccess) context.getString(com.UIN.Tool.R.string.container_reset_done, name)
                                   else context.getString(com.UIN.Tool.R.string.container_import_failed, result.exceptionOrNull()?.message ?: "")
                    isInstalling = false
                }
            },
            onDismiss = { showResetConfirm = null },
            confirmText = context.getString(com.UIN.Tool.R.string.confirm),
            dismissText = context.getString(com.UIN.Tool.R.string.cancel),
            isDestructive = true
        )
    }

    // Export confirmation
    showExportConfirm?.let { name ->
        UnifiedDialog(
            onDismissRequest = { showExportConfirm = null },
            title = context.getString(com.UIN.Tool.R.string.container_export),
            content = {
                Column {
                    Text(
                        text = context.getString(com.UIN.Tool.R.string.container_export_confirm, name),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "压缩级别: $exportCompressionLevel (0=快 22=小)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    UnifiedSlider(
                        value = exportCompressionLevel.toFloat(),
                        onValueChange = { exportCompressionLevel = it.toInt() },
                        valueRange = 0f..22f,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("快", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("小", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            confirmButton = {
                UnifiedButton(
                    text = context.getString(com.UIN.Tool.R.string.confirm),
                    onClick = {
                        showExportConfirm = null
                        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE)
                            type = "application/zstd"
                            putExtra(Intent.EXTRA_TITLE, "${name}-rootfs.tar.zst")
                        }
                        exportContainerName = name
                        exportSaverLauncher.launch(intent)
                    }
                )
            },
            dismissButton = {
                UnifiedButton(
                    text = context.getString(com.UIN.Tool.R.string.cancel),
                    onClick = { showExportConfirm = null },
                    variant = ButtonVariant.Outlined
                )
            }
        )
    }

    // Info dialog
    showInfoDialog?.let { name ->
        val info = containers.find { it.name == name }
        if (info != null) {
            UnifiedDialog(
                onDismissRequest = { showInfoDialog = null },
                title = name,
                content = {
                    Column(verticalArrangement = Arrangement.spacedBy(AppDimens.spacingSmall)) {
                        InfoRow(context.getString(com.UIN.Tool.R.string.container_info_path), info.path)
                        InfoRow(context.getString(com.UIN.Tool.R.string.container_info_size), info.sizeDisplay)
                        InfoRow(context.getString(com.UIN.Tool.R.string.container_info_files), "${info.fileCount}")
                        InfoRow(context.getString(com.UIN.Tool.R.string.container_info_status),
                            if (info.ready) context.getString(com.UIN.Tool.R.string.container_ready)
                            else context.getString(com.UIN.Tool.R.string.container_not_ready))
                        if (info.lastModified > 0) {
                            InfoRow(context.getString(com.UIN.Tool.R.string.container_info_modified),
                                java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
                                    .format(java.util.Date(info.lastModified)))
                        }
                    }
                },
                confirmButton = {
                    UnifiedButton(
                        text = context.getString(com.UIN.Tool.R.string.container_rename),
                        onClick = {
                            showInfoDialog = null
                            renameText = name
                            showRenameDialog = name
                        },
                        modifier = Modifier.weight(1f)
                    )
                },
                dismissButton = {
                    UnifiedButton(
                        text = context.getString(com.UIN.Tool.R.string.cancel),
                        onClick = { showInfoDialog = null },
                        variant = ButtonVariant.Outlined,
                        modifier = Modifier.weight(1f)
                    )
                }
            )
        }
    }

    // Rename dialog
    showRenameDialog?.let { oldName ->
        UnifiedDialog(
            onDismissRequest = { showRenameDialog = null },
            title = context.getString(com.UIN.Tool.R.string.container_rename),
                content = {
                    Column {
                        val textColor = MaterialTheme.colorScheme.onSurface
                        val hintColor = MaterialTheme.colorScheme.onSurfaceVariant
                        val bgColor = MaterialTheme.colorScheme.surfaceVariant
                        val primaryColor = MaterialTheme.colorScheme.primary
                        val outlineColor = MaterialTheme.colorScheme.outline
                        val radius = AppDimens.radiusMedium
                        val density = LocalContext.current.resources.displayMetrics.density
                        androidx.compose.ui.viewinterop.AndroidView(
                            factory = { ctx ->
                                android.widget.EditText(ctx).apply {
                                    setText(renameText)
                                    setSelectAllOnFocus(true)
                                    isSingleLine = true
                                    hint = context.getString(com.UIN.Tool.R.string.container_rename)
                                    setTextColor(textColor.toArgb())
                                    setHintTextColor(hintColor.toArgb())
                                    background = android.graphics.drawable.GradientDrawable().apply {
                                        setColor(bgColor.toArgb())
                                        cornerRadius = radius.value * density
                                        setStroke((1.5f * density).toInt(), outlineColor.toArgb())
                                    }
                                    setPadding(
                                        (16 * density).toInt(),
                                        (12 * density).toInt(),
                                        (16 * density).toInt(),
                                        (12 * density).toInt()
                                    )
                                    setOnFocusChangeListener { _, hasFocus ->
                                        if (hasFocus) {
                                            background = android.graphics.drawable.GradientDrawable().apply {
                                                setColor(bgColor.toArgb())
                                                cornerRadius = radius.value * density
                                                setStroke((1.5f * density).toInt(), primaryColor.toArgb())
                                            }
                                        } else {
                                            background = android.graphics.drawable.GradientDrawable().apply {
                                                setColor(bgColor.toArgb())
                                                cornerRadius = radius.value * density
                                                setStroke((1.5f * density).toInt(), outlineColor.toArgb())
                                            }
                                        }
                                    }
                                    post {
                                        requestFocus()
                                        val imm = ctx.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                                        imm.showSoftInput(this, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
                                    }
                                }
                            },
                            update = { view ->
                                renameText = view.text.toString()
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        val trimmed = renameText.trim()
                        val isError = trimmed.isEmpty() || (trimmed != oldName && containers.any { it.name == trimmed })
                    if (isError) {
                        Spacer(modifier = Modifier.height(4.dp))
                        UnifiedCaptionText(
                            text = when {
                                trimmed.isEmpty() -> context.getString(com.UIN.Tool.R.string.container_rename_empty)
                                else -> context.getString(com.UIN.Tool.R.string.container_rename_duplicate)
                            },
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                val trimmed = renameText.trim()
                val canConfirm = trimmed.isNotEmpty() && trimmed != oldName && containers.none { it.name == trimmed }
                UnifiedButton(
                    text = context.getString(com.UIN.Tool.R.string.confirm),
                    onClick = {
                        scope.launch {
                            val old = RootfsManager.containerDir(context, oldName)
                            val newDir = File(old.parent, trimmed)
                            if (old.exists() && !newDir.exists()) {
                                val renamed = withContext(Dispatchers.IO) { old.renameTo(newDir) }
                                if (renamed) {
                                    if (defaultContainer == oldName) {
                                        withContext(Dispatchers.IO) {
                                            RootfsManager.setDefaultContainer(context, trimmed)
                                        }
                                        defaultContainer = trimmed
                                    }
                                    refreshContainers()
                                    statusMessage = context.getString(com.UIN.Tool.R.string.container_renamed, trimmed)
                                } else {
                                    statusMessage = context.getString(com.UIN.Tool.R.string.delete_failed)
                                }
                            }
                            showRenameDialog = null
                        }
                    },
                    enabled = canConfirm,
                    modifier = Modifier.weight(1f)
                )
            },
            dismissButton = {
                UnifiedButton(
                    text = context.getString(com.UIN.Tool.R.string.cancel),
                    onClick = { showRenameDialog = null },
                    variant = ButtonVariant.Outlined,
                    modifier = Modifier.weight(1f)
                )
            }
        )
    }

    // Download dialog
    if (showDownloadDialog) {
        DownloadDistroDialog(
            distros = downloadableDistros,
            onDismiss = { showDownloadDialog = false },
            onSelect = { distro ->
                showDownloadDialog = false
                scope.launch {
                    isInstalling = true
                    val progressTitle = context.getString(com.UIN.Tool.R.string.container_downloading, distro.displayName)
                    statusMessage = progressTitle
                    LongTaskRegistry.begin(CONTAINER_TASK, progressTitle)
                    // D-04/D-11：内置发行版安装原先完全无进度；每条目回调做 100ms 节流，避免刷爆重组
                    var lastProgressAt = 0L
                    val result = RootfsManager.installFromAsset(context, distro.name, distro.assetName) { count, file ->
                        val now = System.currentTimeMillis()
                        if (now - lastProgressAt >= 100) {
                            lastProgressAt = now
                            publishContainerProgress(progressTitle, count, 0, 0, 0, file)
                        }
                    }
                    if (result.isSuccess) LongTaskRegistry.finish(CONTAINER_TASK)
                    else LongTaskRegistry.fail(CONTAINER_TASK, result.exceptionOrNull()?.message ?: "")
                    refreshContainers()
                    // G-20：失败时把原因带上（原先只有笼统的「下载失败」，无从排查）
                    statusMessage = if (result.isSuccess) context.getString(com.UIN.Tool.R.string.container_download_success, distro.displayName)
                                   else {
                                       val base = context.getString(com.UIN.Tool.R.string.container_download_failed, distro.assetName)
                                       val reason = result.exceptionOrNull()?.message
                                       if (reason.isNullOrBlank()) base else "$base ($reason)"
                                   }
                    isInstalling = false
                }
            }
        )
    }

}

private fun formatSize(bytes: Long): String {
    return when {
        bytes >= 1024L * 1024 * 1024 -> "%.1f GB".format(bytes / (1024.0 * 1024 * 1024))
        bytes >= 1024L * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024))
        bytes >= 1024L -> "%.1f KB".format(bytes / 1024.0)
        else -> "$bytes B"
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        UnifiedCaptionText(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        UnifiedBodyText(text = value)
    }
}

@Composable
fun DownloadDistroDialog(
    distros: List<DownloadableDistro>,
    onDismiss: () -> Unit,
    onSelect: (DownloadableDistro) -> Unit
) {
    val context = LocalContext.current
    var selected by remember { mutableStateOf<DownloadableDistro?>(null) }

    UnifiedDialog(
        onDismissRequest = onDismiss,
        title = context.getString(com.UIN.Tool.R.string.download_linux_distro),
        content = {
            UnifiedBodyText(
                text = context.getString(com.UIN.Tool.R.string.select_distro_to_download),
                modifier = Modifier.padding(bottom = AppDimens.spacingMedium)
            )
            distros.forEach { distro ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = AppDimens.spacingSmall),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NeuRadio(
                        selected = selected == distro,
                        // F-01：未内置的发行版没有可用资源，禁用选择
                        onClick = { if (distro.bundled) selected = distro }
                    )
                    Spacer(modifier = Modifier.width(AppDimens.spacingMedium))
                    Column {
                        UnifiedBodyText(
                            text = distro.displayName,
                            color = if (distro.bundled) null
                                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                        val descResId = context.resources.getIdentifier(distro.descriptionKey, "string", context.packageName)
                        val desc = if (descResId != 0) context.getString(descResId) else distro.descriptionKey
                        val hint = if (distro.bundled) {
                            context.getString(com.UIN.Tool.R.string.distro_bundled_hint)
                        } else {
                            context.getString(com.UIN.Tool.R.string.distro_not_bundled)
                        }
                        UnifiedCaptionText(text = "$desc · ${distro.estimatedSize} · $hint")
                    }
                }
            }
        },
        confirmButton = {
            UnifiedButton(
                text = context.getString(com.UIN.Tool.R.string.confirm),
                onClick = { selected?.takeIf { it.bundled }?.let { onSelect(it) } },
                enabled = selected?.bundled == true,
                variant = ButtonVariant.Primary
            )
        },
        dismissButton = {
            UnifiedButton(
                text = context.getString(com.UIN.Tool.R.string.cancel),
                onClick = onDismiss,
                variant = ButtonVariant.Outlined
            )
        }
    )
}
