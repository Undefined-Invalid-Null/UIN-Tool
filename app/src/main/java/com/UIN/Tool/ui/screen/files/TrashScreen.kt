// app/src/main/java/com/UIN/Tool/ui/screen/files/TrashScreen.kt
package com.UIN.Tool.ui.screen.files

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.UIN.Tool.R
import com.UIN.Tool.ui.components.unified.ButtonVariant
import com.UIN.Tool.ui.components.unified.ManageTopAppBar
import com.UIN.Tool.ui.components.unified.UnifiedButton
import com.UIN.Tool.ui.components.unified.UnifiedCaptionText
import com.UIN.Tool.ui.components.unified.UnifiedDialog
import com.UIN.Tool.ui.components.unified.UnifiedIconButton
import com.UIN.Tool.ui.theme.AppColors
import com.UIN.Tool.utils.AppToast
import com.UIN.Tool.utils.Str
import com.UIN.Tool.utils.formatFileSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.UIN.Tool.ui.screen.files.InAppFilePicker

/**
 * 回收站（用户要求：删除的文件进回收站，可恢复）。
 *
 * 列表 = `UIN_Tool/trash/deleted/` 下的条目，最新在前；每行点开只有两个动作
 * **恢复 / 彻底删除**。恢复优先按原路径落回，原位置被占用时自动改用 `名字 (1)`，
 * 落点在 toast 里如实报出来 —— 不静默替换任何东西。
 *
 * 下方还会列出 `trash/overwrite/` 的**覆盖前备份**（覆盖已有文件时自动留下的旧副本）。
 * 它们没有记录原路径，所以只提供「导出到… / 删除」，不冒充可恢复的回收站条目。
 *
 * 两处清空都是不可逆的批量动作，必须二次确认。
 */
@Composable
fun TrashScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<TrashStore.Item>>(emptyList()) }
    // 覆盖前备份（`trash/overwrite/`）。与「删除的条目」分两组展示：
    // 前者没有原路径可回，只能导出；后者一键恢复回原位。混在一张表里会让人
    // 以为备份也能「恢复」，点下去却没有反应。
    var backups by remember { mutableStateOf<List<RecycleBin.Backup>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var selected by remember { mutableStateOf<TrashStore.Item?>(null) }
    var selectedBackup by remember { mutableStateOf<RecycleBin.Backup?>(null) }
    var confirmEmpty by remember { mutableStateOf(false) }
    var confirmEmptyBackups by remember { mutableStateOf(false) }
    // SAF「导出到…」：备份没有记录原路径（老版本也没记），所以不假装能原位恢复，
    // 让用户自己选落点。launcher 回调里拿不到被点的对象，先暂存一份。
    var exportTarget by remember { mutableStateOf<RecycleBin.Backup?>(null) }

    suspend fun reload() {
        loading = true
        val loaded = withContext(Dispatchers.IO) {
            TrashStore.list() to RecycleBin.listBackups()
        }
        items = loaded.first
        backups = loaded.second
        loading = false
    }

    val exportLauncher = rememberLauncherForActivityResult(
        InAppFilePicker.CreateDocument("application/octet-stream")
    ) { uri ->
        val backup = exportTarget
        exportTarget = null
        if (uri == null || backup == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val out = context.contentResolver.openOutputStream(uri)
                        ?: return@runCatching false
                    out.use { o -> backup.file.inputStream().use { it.copyTo(o) } }
                    true
                }.getOrDefault(false)
            }
            if (ok) AppToast.success(context, Str.get(R.string.files_backup_exported))
            else AppToast.warning(context, Str.get(R.string.files_backup_export_failed))
        }
    }

    LaunchedEffect(Unit) { reload() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.pageBackground())
    ) {
        // k.txt 第 7 条：与文件页/开发页/管理页统一顶栏（同高度、同容器色、同动作区）。
        ManageTopAppBar(
            titleText = Str.get(R.string.files_trash),
            // 文件页嵌在 MainActivity Scaffold 内，状态栏已由外层让出。
            windowInsets = WindowInsets(0, 0, 0, 0),
            onBack = onBack,
            actions = {
                if (items.isNotEmpty() || backups.isNotEmpty()) {
                    UnifiedIconButton(
                        icon = Icons.Default.DeleteSweep,
                        onClick = { confirmEmpty = true },
                        contentDescription = Str.get(R.string.files_trash_empty),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        )

        if (items.isEmpty() && backups.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = Str.get(
                        if (loading) R.string.loading else R.string.files_trash_empty_hint
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 24.dp)
            ) {
                items(items, key = { it.id }) { item ->
                    TrashRow(item) { selected = item }
                }
                if (items.isNotEmpty()) {
                    item {
                        UnifiedCaptionText(
                            text = Str.get(R.string.files_trash_hint),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                // ---- 覆盖前备份 ----
                if (backups.isNotEmpty()) {
                    item(key = "_backup_header") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp, bottom = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = null,
                                tint = AppColors.primary(),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                // 该文案的占位符是 %1$d（整数）—— 传 String 会抛
                                // IllegalFormatConversionException: d != java.lang.String。
                                text = Str.get(
                                    R.string.files_backup_section,
                                    backups.size
                                ),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            UnifiedIconButton(
                                icon = Icons.Default.DeleteSweep,
                                onClick = { confirmEmptyBackups = true },
                                contentDescription = Str.get(R.string.files_backup_clear),
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                    items(backups, key = { "b:" + it.file.absolutePath }) { b ->
                        BackupRow(b) { selectedBackup = b }
                    }
                    item {
                        UnifiedCaptionText(
                            text = Str.get(R.string.files_backup_hint),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    selected?.let { item ->
        UnifiedDialog(
            onDismissRequest = { selected = null },
            title = item.name,
            content = {
                Text(
                    text = item.originalPath.ifBlank { item.name },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = formatTime(item.deletedAt) + " · " +
                        if (item.isDir) Str.get(R.string.files_trash_folder)
                        else formatFileSize(item.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = Str.get(R.string.files_trash_restore_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            confirmButton = {
                UnifiedButton(
                    text = Str.get(R.string.files_trash_restore),
                    icon = Icons.Default.Restore,
                    onClick = {
                        val target = item
                        selected = null
                        scope.launch {
                            val dest = TrashStore.restore(target)
                            if (dest != null) {
                                AppToast.success(
                                    context,
                                    Str.get(R.string.files_trash_restored) + " " + dest.absolutePath
                                )
                            } else {
                                AppToast.warning(context, Str.get(R.string.files_trash_restore_failed))
                            }
                            reload()
                        }
                    }
                )
            },
            dismissButton = {
                UnifiedButton(
                    text = Str.get(R.string.files_trash_delete_forever),
                    variant = ButtonVariant.Destructive,
                    onClick = {
                        val target = item
                        selected = null
                        scope.launch {
                            val ok = TrashStore.deleteForever(target)
                            if (ok) {
                                AppToast.success(context, Str.get(R.string.files_trash_deleted))
                            } else {
                                AppToast.warning(context, Str.get(R.string.files_trash_delete_failed))
                            }
                            reload()
                        }
                    }
                )
            }
        )
    }

    selectedBackup?.let { b ->
        UnifiedDialog(
            onDismissRequest = { selectedBackup = null },
            title = b.name,
            content = {
                Text(
                    text = formatTime(b.savedAt) + " · " + formatFileSize(b.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = Str.get(R.string.files_backup_dialog_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            confirmButton = {
                UnifiedButton(
                    text = Str.get(R.string.files_backup_export),
                    icon = Icons.Default.Restore,
                    onClick = {
                        exportTarget = b
                        selectedBackup = null
                        // 默认文件名用原名；用户可以在系统选择器里改。
                        exportLauncher.launch(b.name)
                    }
                )
            },
            dismissButton = {
                UnifiedButton(
                    text = Str.get(R.string.files_backup_delete),
                    variant = ButtonVariant.Destructive,
                    onClick = {
                        val target = b
                        selectedBackup = null
                        scope.launch {
                            val ok = RecycleBin.deleteBackup(target)
                            if (ok) AppToast.success(context, Str.get(R.string.files_backup_deleted))
                            else AppToast.warning(context, Str.get(R.string.files_backup_delete_failed))
                            reload()
                        }
                    }
                )
            }
        )
    }

    if (confirmEmptyBackups) {
        UnifiedDialog(
            onDismissRequest = { confirmEmptyBackups = false },
            title = Str.get(R.string.files_backup_clear),
            content = {
                Text(
                    text = Str.get(R.string.files_backup_clear_confirm),
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                UnifiedButton(
                    text = Str.get(R.string.files_backup_clear),
                    variant = ButtonVariant.Destructive,
                    onClick = {
                        confirmEmptyBackups = false
                        scope.launch {
                            val n = RecycleBin.emptyBackups()
                            AppToast.success(
                                context,
                                // 占位符是 %1$d：传 String 会抛 IllegalFormatConversionException
                                // （crash_2026-11-18.log 23:20:47 那条就是这里）。
                                Str.get(R.string.files_backup_cleared, n)
                            )
                            reload()
                        }
                    }
                )
            },
            dismissButton = {
                UnifiedButton(
                    text = Str.get(R.string.cancel),
                    variant = ButtonVariant.Secondary,
                    onClick = { confirmEmptyBackups = false }
                )
            }
        )
    }

    if (confirmEmpty) {
        UnifiedDialog(
            onDismissRequest = { confirmEmpty = false },
            title = Str.get(R.string.files_trash_empty),
            content = {
                Text(
                    text = Str.get(R.string.files_trash_empty_confirm),
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                UnifiedButton(
                    text = Str.get(R.string.files_trash_empty),
                    variant = ButtonVariant.Destructive,
                    onClick = {
                        confirmEmpty = false
                        scope.launch {
                            // 页面上的「清空」指**整页**：删除条目 + 覆盖前备份一起清。
                            // 只清前一半的话，用户看到备份还在会以为按钮没生效。
                            val n = TrashStore.emptyAll() + RecycleBin.emptyBackups()
                            AppToast.success(
                                context,
                                Str.get(R.string.files_trash_emptied, n)
                            )
                            reload()
                        }
                    }
                )
            },
            dismissButton = {
                UnifiedButton(
                    text = Str.get(R.string.cancel),
                    variant = ButtonVariant.Secondary,
                    onClick = { confirmEmpty = false }
                )
            }
        )
    }
}

@Composable
private fun BackupRow(backup: RecycleBin.Backup, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // l.txt 第 15 条：回收站行在新拟态下是浮起的小卡片；关闭新拟态时
            // 仍是「8dp 圆角 + 透明底」，行级涟漪收在圆角内（k.txt 第 6 条）。
            .filesNeuSurface(
                shape = RoundedCornerShape(8.dp),
                cornerRadius = 8.dp,
                fallback = androidx.compose.ui.graphics.Color.Transparent
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FileTypeIcon(fileName = backup.name, size = 22.dp)
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = backup.name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = Str.get(R.string.files_backup_row_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = formatFileSize(backup.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = formatTime(backup.savedAt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TrashRow(item: TrashStore.Item, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // l.txt 第 15 条：回收站行在新拟态下是浮起的小卡片；关闭新拟态时
            // 仍是「8dp 圆角 + 透明底」，行级涟漪收在圆角内（k.txt 第 6 条）。
            .filesNeuSurface(
                shape = RoundedCornerShape(8.dp),
                cornerRadius = 8.dp,
                fallback = androidx.compose.ui.graphics.Color.Transparent
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FileTypeIcon(fileName = item.name, isDir = item.isDir, size = 22.dp)
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = item.originalPath,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = if (item.isDir) Str.get(R.string.files_trash_folder) else formatFileSize(item.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = formatTime(item.deletedAt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
