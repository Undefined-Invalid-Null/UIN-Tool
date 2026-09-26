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
    val estimatedSize: String
)

data class ProgressInfo(
    val title: String,
    val current: Int,
    val total: Int,
    val bytesProcessed: Long,
    val totalBytes: Long,
    val currentFile: String = ""
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ContainerManagementScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var containers by remember { mutableStateOf(RootfsManager.listContainers(context)) }
    var defaultContainer by remember { mutableStateOf(RootfsManager.getDefaultContainer(context)) }
    var prootReady by remember { mutableStateOf(ProotInstaller.isInstalled(context)) }
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
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var progressInfo by remember { mutableStateOf<ProgressInfo?>(null) }

    val downloadableDistros = listOf(
        DownloadableDistro("debian", "Debian (trixie)", "debian-rootfs.tar.xz", "distro_debian_desc", "~27 MB"),
        DownloadableDistro("alpine", "Alpine Linux", "alpine-minirootfs.tar.gz", "distro_alpine_desc", "~4 MB"),
        DownloadableDistro("ubuntu", "Ubuntu (24.04 LTS)", "ubuntu-rootfs.tar.xz", "distro_ubuntu_desc", "~60 MB"),
        DownloadableDistro("arch", "Arch Linux", "archlinux-rootfs.tar.xz", "distro_arch_desc", "~80 MB")
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

                        val result = RootfsManager.installFromFile(context, containerName, tempFile) { entryCount, entryName, bytesProcessed, totalBytes ->
                            progressInfo = ProgressInfo(
                                title = context.getString(com.UIN.Tool.R.string.container_importing),
                                current = entryCount,
                                total = totalEntries,
                                bytesProcessed = bytesProcessed,
                                totalBytes = totalBytes,
                                currentFile = entryName
                            )
                        }
                        progressInfo = null
                        tempFile.delete()

                        containers = RootfsManager.listContainers(context)
                        statusMessage = if (result.isSuccess) {
                            context.getString(com.UIN.Tool.R.string.container_import_success, containerName)
                        } else {
                            context.getString(com.UIN.Tool.R.string.container_import_failed, result.exceptionOrNull()?.message ?: "")
                        }
                    } catch (e: Exception) {
                        statusMessage = context.getString(com.UIN.Tool.R.string.container_import_failed, e.message ?: "")
                    } finally {
                        progressInfo = null
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
                    progressInfo = ProgressInfo(
                        title = context.getString(com.UIN.Tool.R.string.container_exporting, name),
                        current = 0, total = 0, bytesProcessed = 0, totalBytes = 0
                    )
                    try {
                        val exportDir = File(context.cacheDir, "export")
                        val result = RootfsManager.exportContainer(context, name, exportDir, exportCompressionLevel) { fileCount, totalFiles, bytesWritten, totalBytes, currentFile ->
                            progressInfo = ProgressInfo(
                                title = context.getString(com.UIN.Tool.R.string.container_exporting, name),
                                current = fileCount,
                                total = totalFiles,
                                bytesProcessed = bytesWritten,
                                totalBytes = totalBytes,
                                currentFile = currentFile
                            )
                        }
                        progressInfo = null
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
                    } finally {
                        progressInfo = null
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
                                val result = ProotInstaller.ensureInstalled(context)
                                prootReady = result.isSuccess
                                statusMessage = if (result.isSuccess) context.getString(com.UIN.Tool.R.string.proot_install_success)
                                               else context.getString(com.UIN.Tool.R.string.proot_install_failed)
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
                enabled = !isInstalling,
                icon = Icons.Default.FolderOpen
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
                RootfsManager.deleteContainer(context, name)
                containers = RootfsManager.listContainers(context)
                if (defaultContainer == name) {
                    defaultContainer = com.UIN.Tool.plugin.BackendConfig.BUILTIN_CONTAINER
                    RootfsManager.setDefaultContainer(context, com.UIN.Tool.plugin.BackendConfig.BUILTIN_CONTAINER)
                }
                statusMessage = context.getString(com.UIN.Tool.R.string.container_deleted, name)
                showDeleteConfirm = null
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
                    isInstalling = true
                    val result = RootfsManager.resetContainer(context, name)
                    containers = RootfsManager.listContainers(context)
                    defaultContainer = RootfsManager.getDefaultContainer(context)
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
                        val oldDir = RootfsManager.containerDir(context, oldName)
                        val newDir = File(oldDir.parent, trimmed)
                        if (oldDir.exists() && !newDir.exists()) {
                            oldDir.renameTo(newDir)
                            if (defaultContainer == oldName) {
                                RootfsManager.setDefaultContainer(context, trimmed)
                                defaultContainer = trimmed
                            }
                            containers = RootfsManager.listContainers(context)
                            statusMessage = context.getString(com.UIN.Tool.R.string.container_renamed, trimmed)
                        }
                        showRenameDialog = null
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
                    statusMessage = context.getString(com.UIN.Tool.R.string.container_downloading, distro.displayName)
                    val result = RootfsManager.installFromAsset(context, distro.name, distro.assetName)
                    containers = RootfsManager.listContainers(context)
                    statusMessage = if (result.isSuccess) context.getString(com.UIN.Tool.R.string.container_download_success, distro.displayName)
                                   else context.getString(com.UIN.Tool.R.string.container_download_failed, distro.assetName)
                    isInstalling = false
                }
            }
        )
    }

    // Progress dialog
    progressInfo?.let { info ->
        UnifiedDialog(
            onDismissRequest = { /* 不可关闭 */ },
            title = info.title,
                content = {
                Column {
                    val progress = if (info.total > 0) info.current.toFloat() / info.total else 0f
                    UnifiedLinearProgressIndicator(
                        progress = progress,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    val sizeText = if (info.totalBytes > 0) {
                        "${formatSize(info.bytesProcessed)} / ${formatSize(info.totalBytes)}"
                    } else ""
                    val countText = when {
                        info.total > 0 -> context.getString(com.UIN.Tool.R.string.container_export_progress, info.current, info.total)
                        info.current > 0 -> context.getString(com.UIN.Tool.R.string.container_import_entry_count, info.current)
                        else -> ""
                    }

                    val detailText = listOfNotNull(
                        countText.takeIf { it.isNotEmpty() },
                        sizeText.takeIf { it.isNotEmpty() }
                    ).joinToString(" · ")

                    if (detailText.isNotEmpty()) {
                        UnifiedBodyText(text = detailText)
                    } else {
                        UnifiedBodyText(text = context.getString(com.UIN.Tool.R.string.container_export_scanning))
                    }

                    if (info.currentFile.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        UnifiedCaptionText(
                            text = info.currentFile,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {}
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
                        onClick = { selected = distro }
                    )
                    Spacer(modifier = Modifier.width(AppDimens.spacingMedium))
                    Column {
                        UnifiedBodyText(text = distro.displayName)
                        val descResId = context.resources.getIdentifier(distro.descriptionKey, "string", context.packageName)
                        val desc = if (descResId != 0) context.getString(descResId) else distro.descriptionKey
                        UnifiedCaptionText(text = "$desc · ${distro.estimatedSize}")
                    }
                }
            }
        },
        confirmButton = {
            UnifiedButton(
                text = context.getString(com.UIN.Tool.R.string.confirm),
                onClick = { selected?.let { onSelect(it) } },
                enabled = selected != null,
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
