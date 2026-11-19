// app/src/main/java/com/UIN/Tool/ui/screen/files/FileOpsDialogs.kt
package com.UIN.Tool.ui.screen.files

import com.UIN.Tool.ui.components.unified.UnifiedDropdownMenu
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.UIN.Tool.R
import com.UIN.Tool.ui.components.unified.ButtonVariant
import com.UIN.Tool.ui.components.unified.UnifiedButton
import com.UIN.Tool.ui.components.unified.UnifiedDialog
import com.UIN.Tool.ui.components.unified.UnifiedIconButton
import com.UIN.Tool.ui.components.unified.UnifiedTextField
import com.UIN.Tool.ui.components.unified.dialogAutoFocus
import com.UIN.Tool.ui.components.unified.middleEllipsize
import com.UIN.Tool.ui.theme.AppDimens
import com.UIN.Tool.utils.AppToast
import com.UIN.Tool.utils.Str
import com.UIN.Tool.utils.formatFileSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.UIN.Tool.ui.screen.files.InAppFilePicker

// ==================== 冲突三选（4.4 定稿） ====================

/**
 * 撞名对话框（MT.md §4.4）。
 *
 * 单项走「三段式」：`复制：名字 · 大小 · 时间` → `↓ 替换 ↓` → `已存在：名字 · 大小 · 时间`
 * → `保留两份将命名为：<建议名>`；多项换成可滚动清单，每行 `源名(大小) → 目标名(大小)`。
 *
 * - 三个动作：`替换` / `保留两份` / `跳过` + 勾选**对剩余全部应用**；
 * - **点外面 = 整批取消**（[onCancel]），不是「选了个默认值」—— 文件管理器不能替用户做这个决定；
 * - 大小与修改时间都相同的项默认提示「已存在且相同，跳过？」。
 */
@Composable
fun FileConflictDialog(
    items: List<ConflictItem>,
    onDecision: (policy: ConflictPolicy, applyToAll: Boolean) -> Unit,
    onCancel: () -> Unit
) {
    var applyToAll by remember { mutableStateOf(false) }
    val single = items.size == 1
    val item = items.firstOrNull()
    // 全部同内容时直接把「跳过」摆成建议动作，少一次误替换
    val allIdentical = items.isNotEmpty() && items.all { it.identical }
    // f16：三个做法改成**单选框**（原来是一排并列按钮）。内容全同时默认停在
    // 「跳过该文件」—— 同内容的重复拷贝没有替换的意义。
    var policy by remember(items) {
        mutableStateOf(if (allIdentical) ConflictPolicy.SKIP else ConflictPolicy.REPLACE)
    }

    UnifiedDialog(
        onDismissRequest = onCancel,
        title = Str.get(R.string.files_conflict_title),
        content = {
            if (single && item != null) {
                ConflictDetailView(item)
            } else {
                ConflictListView(items)
            }

            Spacer(modifier = Modifier.height(AppDimens.spacingMedium))
            ConflictChoiceRow(
                selected = policy == ConflictPolicy.REPLACE,
                label = Str.get(R.string.files_conflict_replace),
                onSelect = { policy = ConflictPolicy.REPLACE },
                danger = true
            )
            ConflictChoiceRow(
                selected = policy == ConflictPolicy.KEEP_BOTH,
                label = Str.get(R.string.files_conflict_keep_both),
                onSelect = { policy = ConflictPolicy.KEEP_BOTH }
            )
            ConflictChoiceRow(
                selected = policy == ConflictPolicy.SKIP,
                label = Str.get(R.string.files_conflict_skip),
                onSelect = { policy = ConflictPolicy.SKIP }
            )

            Spacer(modifier = Modifier.height(AppDimens.spacingSmall))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Checkbox(checked = applyToAll, onCheckedChange = { applyToAll = it })
                Spacer(modifier = Modifier.width(AppDimens.spacingSmall))
                Text(
                    text = Str.get(R.string.files_conflict_apply_all),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            UnifiedButton(
                text = Str.get(R.string.confirm),
                onClick = { onDecision(policy, applyToAll) },
                variant = if (policy == ConflictPolicy.REPLACE) ButtonVariant.Destructive
                else ButtonVariant.Primary
            )
        },
        dismissButton = {
            UnifiedButton(
                text = Str.get(R.string.cancel),
                onClick = onCancel,
                variant = ButtonVariant.Text
            )
        }
    )
}

/** 单项三段式：源 → ↓替换↓ → 已存在 → 保留两份的建议名。 */
@Composable
private fun ConflictDetailView(item: ConflictItem) {
    Column(verticalArrangement = Arrangement.spacedBy(AppDimens.spacingSmall)) {
        if (item.identical) {
            Text(
                text = Str.get(R.string.files_conflict_identical),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }
        ConflictSideLine(
            label = Str.get(R.string.files_conflict_source),
            name = item.sourceName,
            size = item.sourceSize,
            modified = item.sourceModified
        )
        Text(
            text = Str.get(R.string.files_conflict_arrow),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary
        )
        ConflictSideLine(
            label = Str.get(R.string.files_conflict_existing),
            name = item.targetName,
            size = item.targetSize,
            modified = item.targetModified
        )
        Text(
            text = Str.get(
                R.string.files_conflict_keep_both_name,
                FileOps.uniqueSibling(File(item.targetPath)).name
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 「复制：名字 · 大小 · 时间」一行。 */
@Composable
private fun ConflictSideLine(label: String, name: String, size: Long, modified: Long) {
    Column {
        Text(
            text = "$label ${middleEllipsize(name, 40)}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "${formatFileSize(size)} · ${formatTime(modified)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 多项清单：每行 `源名(大小) → 目标名(大小)`，最多占约半屏、可滚。 */
@Composable
private fun ConflictListView(items: List<ConflictItem>) {
    Column(
        modifier = Modifier
            .heightIn(max = 200.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(AppDimens.spacingSmall)
    ) {
        Text(
            text = Str.get(R.string.files_conflict_count, items.size.toString()),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        items.forEach { it ->
            Text(
                text = "${middleEllipsize(it.sourceName, 24)} (${formatFileSize(it.sourceSize)}) → " +
                    "${middleEllipsize(it.targetName, 24)} (${formatFileSize(it.targetSize)})",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ==================== 删除确认（4.5） ====================

/**
 * 删除确认框（4.5）。标题「警告」，正文先 `删除 …？`，**异步补体积统计** ——
 * 算完才把「统计中…」换成「8.26 MB · 3 项」；关键系统目录再叠一层危险提示。
 */
@Composable
fun FileDeleteDialog(
    request: DeleteRequest,
    onConfirm: (permanent: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    // 只对**真实文件**给「彻底删除」：归档内条目走的是重新打包，没有回收站概念。
    val realOnly = request.paths.none { VirtualPath.isInArchive(it) }
    // h.txt 第 2 条：回收站总开关关掉后，删除默认就是彻底删除 —— 少一次手动勾选。
    val recycleEnabled = FilePrefs.recycleBinEnabled()
    var permanent by remember(request.paths) { mutableStateOf(!recycleEnabled) }
    UnifiedDialog(
        onDismissRequest = onDismiss,
        title = Str.get(R.string.warning),
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(AppDimens.spacingSmall)) {
                if (request.dangerous) {
                    Text(
                        text = Str.get(R.string.files_delete_danger),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                }
                // g9：名单行**不再拼「等 N 项」**。那个 N 原来写的是「剩下几项」，
                // 用户读成「一共 N 项」，与下一行的总数对不上（勾 5 项显示「等 2 项」）。
                // 总数下一行已有，名单只负责列前三条。
                val names = if (request.names.size <= 3) {
                    request.names.joinToString("、")
                } else {
                    request.names.take(3).joinToString("、") + " …"
                }
                Text(
                    text = Str.get(
                        R.string.files_delete_confirm,
                        middleEllipsize(names, 60)
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (request.measuring) {
                        Str.get(R.string.files_calculating)
                    } else {
                        // f7：这里原来写的是 `entryCount` —— 那是「递归条目数」（文件夹里
                        // 的文件也算进去），给进度条当分母用的。选一个装有 100 个文件的文件夹，
                        // 确认框就报「101 项」，与屏上勾选的 1 条对不上。条数必须取**将删的
                        // 顶层条目数**，与名单行「等 N 项」同口径。
                        Str.get(
                            R.string.files_delete_summary,
                            formatFileSize(request.totalBytes),
                            request.paths.size.toString()
                        )
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                // 反馈 e8：包内的「删除」= 从包里去掉条目，引擎会**整包重写**（= 重新打包）。
                // 大包上这一步明显比删几个文件慢，先说清楚比事后让用户猜强。
                if (request.paths.any { VirtualPath.isInArchive(it) }) {
                    Text(
                        text = Str.get(R.string.files_delete_repack),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                // 回收站（用户要求）：默认删除 = 移入回收站，可从侧边栏「回收站」恢复。
                // 想一次清干净的人有「彻底删除」这一档；不默认勾上 —— 勾错一次就是真没了。
                if (realOnly) {
                    Text(
                        text = Str.get(
                            if (recycleEnabled) {
                                R.string.files_delete_trash_hint
                            } else {
                                R.string.files_delete_trash_off_hint
                            }
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    DialogCheckRow(
                        label = Str.get(R.string.files_delete_permanent),
                        checked = permanent
                    ) { permanent = it }
                }
            }
        },
        confirmButton = {
            UnifiedButton(
                text = Str.get(R.string.delete),
                onClick = { onConfirm(permanent) },
                variant = ButtonVariant.Destructive
            )
        },
        dismissButton = {
            UnifiedButton(
                text = Str.get(R.string.cancel),
                onClick = onDismiss,
                variant = ButtonVariant.Text
            )
        }
    )
}

// ==================== 重命名（4.5） ====================

/**
 * 重命名框（4.5）。**打开即选中主干名**（不含扩展名）—— MP 式行为，省掉「删后缀再重打」。
 * 撞名时把建议名**填进输入框**（并选中主干），用户看得到将要改成什么，也能接着改。
 *
 * f8：原先撞名还多给一个「互换两个文件名」的键，已整套去掉 —— 两个名字对调这件事
 * 用户可以用两次重命名完成，单为它挂一个按钮，撞名时反而要停下来读一遍才知道哪个是它。
 */
@Composable
fun FileRenameDialog(
    request: RenameRequest,
    onConfirm: (newName: String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    // 打开即选中主干名（不含扩展名）—— MP 式行为，省掉「删后缀再重打」。
    // 用 `TextFieldValue.selection` 表达选区；用户一改字（onValueChange）就不再抢选区，
    // 否则每敲一个字母选区都会跳回原位。
    var value by remember(request.path) {
        val name = request.initialName
        val dot = name.lastIndexOf('.')
        val stemEnd = if (dot > 0) dot else name.length
        mutableStateOf(TextFieldValue(text = name, selection = TextRange(0, stemEnd)))
    }
    // g15：撞名后 VM 会回填建议名 —— 直接把它**填进输入框**，让「将要改成什么」
    // 看得见。原先要用户先按一次「重命名」、失败后才在提示行里出现建议，再按一次
    // 才悄悄落到 `(1)` 上，整个过程像是文件自己改名了。
    LaunchedEffect(request.suggestedName) {
        val suggest = request.suggestedName
        if (suggest.isNotEmpty()) {
            val dot = suggest.lastIndexOf('.')
            val stemEnd = if (dot > 0) dot else suggest.length
            value = TextFieldValue(text = suggest, selection = TextRange(0, stemEnd))
        }
    }

    UnifiedDialog(
        onDismissRequest = onDismiss,
        title = Str.get(R.string.rename),
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(AppDimens.spacingSmall)) {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text(Str.get(R.string.files_new_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().dialogAutoFocus()
                )
                // 「粘贴」中性键 —— 重命名常常是从别处复制来的名字
                UnifiedButton(
                    text = Str.get(R.string.files_paste),
                    onClick = {
                        val pasted = readClipboard(context)
                        if (pasted.isNotEmpty()) {
                            value = TextFieldValue(pasted)
                        }
                    },
                    variant = ButtonVariant.Text
                )
                if (request.suggestedName.isNotEmpty()) {
                    Text(
                        text = Str.get(R.string.files_rename_conflict, value.text),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = Str.get(R.string.files_rename_suggested, request.suggestedName),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        confirmButton = {
            UnifiedButton(
                text = Str.get(R.string.rename),
                // 输入框里是什么就改成什么 —— 建议名已经替用户填进去了，
                // 不再有一条「没动过就偷偷改用建议名」的暗路
                onClick = { onConfirm(value.text) },
                variant = ButtonVariant.Primary
            )
        },
        dismissButton = {
            UnifiedButton(
                text = Str.get(R.string.cancel),
                onClick = onDismiss,
                variant = ButtonVariant.Text
            )
        }
    )
}

// ==================== 新建（4.5） ====================

/**
 * 新建框（4.5）：一个对话框**三个键**（取消 / 文件夹 / 文件）。
 * 留空给默认名、重名自动加序号（建议名实时显示），成功后新条目高亮并滚到可视处。
 */
@Composable
fun FileCreateDialog(
    request: CreateRequest,
    onNameChange: (String) -> Unit,
    onCreate: (isDir: Boolean, name: String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember(request.dirPath) { mutableStateOf(request.initialName) }
    // 必须在组合上下文里取：`LocalClipboardManager.current` 是 @Composable，
    // 写进 IconButton 的 onClick 里会编译错（回调不是组合上下文）。
    val clipboard = LocalClipboardManager.current

    UnifiedDialog(
        onDismissRequest = onDismiss,
        title = Str.get(R.string.files_new),
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(AppDimens.spacingSmall)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        onNameChange(it)
                    },
                    label = { Text(Str.get(R.string.files_new_name)) },
                    singleLine = true,
                    // 子 agent 对比建议：新建框给个**粘贴**入口 —— 名字多半是从别处复制来的
                    // （长串、带子目录的路径），逼用户手打一遍没有任何意义。
                    // 粘整段而不是只取末段：本框本来就支持 `a/b/c` 这种带子目录的路径。
                    trailingIcon = {
                        UnifiedIconButton(
                            icon = Icons.Default.ContentPaste,
                            onClick = {
                            clipboard.getText()?.text?.let { clip ->
                                if (clip.isNotBlank()) {
                                    name = clip
                                    onNameChange(clip)
                                }
                            }
                        },
                            contentDescription = Str.get(R.string.paste),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    modifier = Modifier.fillMaxWidth().dialogAutoFocus()
                )
                if (request.suggestedName.isNotEmpty()) {
                    Text(
                        text = Str.get(R.string.files_rename_suggested, request.suggestedName),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    text = middleEllipsize(request.dirPath, 52),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        confirmButton = {
            // 顺序照用户反馈⑫ C 的原话：「下面有取消，文件，文件夹字样」——
            // dismissButton 先排，所以这里先是「文件」后是「文件夹」。
            UnifiedButton(
                text = Str.get(R.string.file),
                onClick = { onCreate(false, name) },
                variant = ButtonVariant.Outlined
            )
            UnifiedButton(
                text = Str.get(R.string.folder),
                onClick = { onCreate(true, name) },
                variant = ButtonVariant.Primary
            )
        },
        dismissButton = {
            UnifiedButton(
                text = Str.get(R.string.cancel),
                onClick = onDismiss,
                variant = ButtonVariant.Text
            )
        }
    )
}

// ==================== 属性（4.5） ====================

/** 粘贴校验的一句结论。[ok] = null 表示「格式都不对」，与「对不上」是两回事。 */
private data class HashVerdict(val text: String, val ok: Boolean?)

/**
 * 把别处抄来的摘要贴进来，看对不对得上本文件（e16「属性缺粘贴校验」）。
 *
 * 只认 32 位（MD5）/ 64 位（SHA-256）十六进制，其余一律说「格式不对」而不是
 * 笼统的「不一致」—— 一个是格式问题，一个是内容问题，混着说会把人引到错方向。
 *
 * 摘要还没算出来（`…` / `—`）时返回 null、**不给结论**：拿占位符去比，
 * 比出来的必是「不一致」，那是一句假话。
 */
private fun hashVerdict(pasted: String, md5: String, sha: String): HashVerdict? {
    val v = pasted.trim().lowercase()
    if (v.isEmpty()) return null
    val isHex = v.all { it in '0'..'9' || it in 'a'..'f' }
    if (!isHex || (v.length != 32 && v.length != 64)) {
        return HashVerdict(Str.get(R.string.file_hash_check_bad), null)
    }
    val target = if (v.length == 32) md5 else sha
    // 占位符 / 算失败了 —— 这时没有可比的东西，别硬给一个「不一致」
    if (target.length != v.length || !target.all { it in '0'..'9' || it in 'a'..'f' }) {
        return null
    }
    val ok = target.equals(v, ignoreCase = true)
    return HashVerdict(
        Str.get(if (ok) R.string.file_hash_check_match else R.string.file_hash_check_mismatch),
        ok
    )
}

/**
 * 粘贴校验输入行。两个属性框（普通文件 / 包内条目）共用一份 ——
 * 摘要算法与判定完全一样，各写一遍只会让日后改判定时漏改一处。
 */
@Composable
private fun PasteVerify(md5: String, sha: String) {
    var pasted by remember { mutableStateOf("") }
    val verdict = hashVerdict(pasted, md5, sha)
    UnifiedTextField(
        value = pasted,
        onValueChange = { pasted = it },
        label = Str.get(R.string.file_hash_check),
        singleLine = true,
        // m.txt 第 6 条：属性弹窗打开时不自动弹键盘。粘贴校验是低频功能，
        // 不该成为弹窗的默认焦点（否则键盘会盖住大半属性内容）。
        autoFocus = false,
        modifier = Modifier.fillMaxWidth()
    )
    if (verdict != null) {
        Text(
            text = verdict.text,
            style = MaterialTheme.typography.bodySmall,
            color = when (verdict.ok) {
                true -> MaterialTheme.colorScheme.primary
                false -> MaterialTheme.colorScheme.error
                null -> MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}

// ==================== 属性框：两文件比对（e16） ====================

/**
 * 所选文件的字节数，拿不到给 -1（`OpenableColumns.SIZE` 不是每个提供方都给）。
 */
private fun queryPickedSize(context: Context, uri: Uri): Long = runCatching {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
        if (!c.moveToFirst()) return@use -1L
        val i = c.getColumnIndex(OpenableColumns.SIZE)
        if (i >= 0 && !c.isNull(i)) c.getLong(i) else -1L
    } ?: -1L
}.getOrDefault(-1L)

/**
 * 把选中的文件拷进缓存，给 [CompareActivity] 当右侧 —— 它只吃绝对路径，吃不了 `content://`。
 *
 * 对比页要**两边都随机读**：`content://` 每读一次都过一道 Binder，长文件会明显发飘，
 * 所以先落成缓存里的普通文件。每次挑之前清一遍，缓存里最多留一份。
 *
 * @return 拷好的绝对路径；`null` = 打不开或拷失败
 */
private fun stageForCompare(context: Context, uri: Uri): String? {
    val dir = File(context.cacheDir, "compare")
    dir.listFiles()?.forEach { it.delete() }
    dir.mkdirs()
    val dst = File(dir, "pick_" + System.nanoTime())
    return try {
        val input = context.contentResolver.openInputStream(uri) ?: return null
        input.use { ins -> dst.outputStream().use { out -> ins.copyTo(out) } }
        dst.absolutePath
    } catch (e: Exception) {
        dst.delete()
        null
    }
}

/**
 * 「与另一个文件对比」入口（e16「属性缺…两文件比对」）。
 *
 * 另一侧用**本应用内置的**文件选择器挑（m.txt 第 7 条：不再弹系统选择器）。
 * 挑到的是 `content://`（内置 provider），先量大小再拷进缓存（见 [stageForCompare]）。
 */
@Composable
private fun CompareWithAnother(file: File, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(InAppFilePicker.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            // 先问多大：过不了对比页那道字节闸就别拷 —— 拷完也是白拷，
            // 它那边只会回一句「文件过大，无法对比」
            val size = withContext(Dispatchers.IO) { queryPickedSize(context, uri) }
            if (size > MAX_COMPARE_BYTES) {
                busy = false
                AppToast.warning(context, Str.get(R.string.cmp_too_large))
                return@launch
            }
            val path = withContext(Dispatchers.IO) { stageForCompare(context, uri) }
            busy = false
            if (path == null) {
                AppToast.warning(context, Str.get(R.string.cmp_pick_failed))
            } else {
                // 关掉属性框再开对比：留着它，比完回来还挂着一个旧属性框
                onDismiss()
                CompareActivity.start(context, file.absolutePath, path)
            }
        }
    }
    Spacer(modifier = Modifier.height(8.dp))
    UnifiedButton(
        text = Str.get(R.string.cmp_pick),
        onClick = { picker.launch(arrayOf("*/*")) },
        variant = ButtonVariant.Outlined,
        enabled = !busy,
        loading = busy,
        modifier = Modifier.fillMaxWidth()
    )
}

/**
 * 「查看安装包信息」入口（e10「安装包信息不支持查看」）。
 *
 * 属性框答的是「这个文件多大、什么时候改的、摘要对不对」—— 包名 / 版本 / 权限 /
 * 签名 / 加壳特征它**一个都答不上来**，那些在 [ApkInfoActivity] 里。原先属性框
 * 对 `.apk` 和对 `.txt` 一视同仁，想看安装包信息只能退回列表去点开那个文件。
 *
 * 与 [CompareWithAnother] 同一个做法：**先关属性框再开信息页** —— 否则看完回来
 * 还挂着一个已经过期的属性框（文件可能在期间被改过）。
 */
@Composable
private fun ApkInfoEntry(file: File, onDismiss: () -> Unit) {
    val context = LocalContext.current
    Spacer(modifier = Modifier.height(8.dp))
    UnifiedButton(
        text = Str.get(R.string.files_open_apk_info),
        onClick = {
            onDismiss()
            try {
                ApkInfoActivity.start(context, file.absolutePath)
            } catch (e: Exception) {
                AppToast.warning(context, Str.get(R.string.apk_info_load_failed))
            }
        },
        variant = ButtonVariant.Outlined,
        modifier = Modifier.fillMaxWidth()
    )
}

/**
 * e20：这个文件该不该显示「行数 / 字符数」。
 *
 * 对着一个 apk、一张图、一段视频数「几行几字」没有意义 —— 它们根本没有「行」
 * 这个概念。判定与文件页的打开分流**共用一份后缀表**（[TEXT_EXTENSIONS]，同包直接可见），
 * 少了这份白名单就会出现「属性框显示文本统计、点开却进不了编辑器」的两套口径。
 *
 * 后缀表认不出时再补一道 **MIME 兜底**：系统认得出是 `text/` 开头的 MIME 也算
 * （某些工具生成的无后缀 / 怪后缀文件就是这种）。两者都不认 → 不显示那两行，
 * 也就**不去读盘**。
 */
private fun showsTextStats(file: File): Boolean {
    if (file.isDirectory) return false
    val ext = file.extension.lowercase(Locale.getDefault())
    if (ext in TEXT_EXTENSIONS) return true
    val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: return false
    return mime.startsWith("text/")
}

/**
 * 属性框（4.5）：名称 / 父目录 / 类型 / 大小 / 修改时间 / MIME，**长按任一行复制该值**。
 * 行数字符数沿用流式统计（大文件不一次性读进内存），且**只在文本类文件上显示**（e20）。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FilePropertiesDialog(file: File, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val isDir = file.isDirectory
    val typeValue = if (isDir) Str.get(R.string.folder)
    else file.extension.ifEmpty { Str.get(R.string.file) }
    // o.txt 第 1 条：文件夹也要给出占用大小（递归统计，异步进行，避免大目录卡住组合）。
    var sizeValue by remember(file) {
        mutableStateOf(if (isDir) Str.get(R.string.files_calculating) else formatFileSize(file.length()))
    }
    val timeValue = if (file.lastModified() > 0L) {
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(file.lastModified()))
    } else "—"
    // o.txt 第 1 条：目录没有扩展名，MimeTypeMap 给不出结果；用 Android 文档层
    // 约定的目录 MIME（DocumentsContract.Document.MIME_TYPE_DIR），而不是一排「—」。
    val mimeValue = if (isDir) android.provider.DocumentsContract.Document.MIME_TYPE_DIR
    else MimeTypeMap.getSingleton()
        .getMimeTypeFromExtension(file.extension.lowercase(Locale.getDefault()))
        ?: "—"
    var linesValue by remember { mutableStateOf("…") }
    var charsValue by remember { mutableStateOf("…") }
    var md5Value by remember { mutableStateOf("…") }
    var shaValue by remember { mutableStateOf("…") }

    // e20：行数 / 字符数**只在文本类文件上显示**。对着一个 apk、一张图、一段视频
    // 数「几行几字」没有意义（它们根本没有「行」这个概念），而且不显示也照样会去
    // 读一遍盘。判定与文件页的打开分流**共用一份后缀表**，再补一道 MIME 兜底。
    val showTextStats = remember(file) { showsTextStats(file) }

    // 用户反馈㉑：属性框补「权限 / 所有者 / 用户组」。`java.io.File` 只有 canRead/canWrite
    // 两个粗粒度开关，POSIX 的 rwx 位与 uid/gid 只能问 `Os.stat` —— 一次系统调用，
    // 与上面 isDirectory/length/lastModified 同量级，直接在组合里同步取即可。
    // 取不到（权限不足 / 极端 provider）就**整段不摆**：摆一排「—」只会让人以为坏了。
    val stat = remember(file) {
        runCatching { android.system.Os.stat(file.absolutePath) }.getOrNull()
    }

    androidx.compose.runtime.LaunchedEffect(file) {
        if (isDir) {
            val (bytes, capped, scanned) = withContext(Dispatchers.IO) {
                aggregateSize(listOf(file))
            }
            sizeValue = if (capped) {
                Str.get(R.string.files_prop_multi_total_capped, formatFileSize(bytes), scanned)
            } else {
                formatFileSize(bytes)
            }
        }
    }

    androidx.compose.runtime.LaunchedEffect(file) {
        if (showTextStats) {
            val (l, c) = countLinesAndChars(file)
            linesValue = l
            charsValue = c
        } else {
            // 不显示那两行，值也直接置成「—」—— 免得状态停在首帧的「…」
            linesValue = "—"
            charsValue = "—"
        }
    }

    // 与行数统计分开一个 LaunchedEffect：两者各跑在 IO 线程池上，**并行**读同一条文件。
    // 合进前一个 effect 就成了串行 —— 同一份数据读两遍，属性框要多等一倍时间。
    androidx.compose.runtime.LaunchedEffect(file) {
        if (isDir) {
            md5Value = "—"
            shaValue = "—"
        } else {
            val (m, s) = checksums(file)
            md5Value = m
            shaValue = s
        }
    }

    UnifiedDialog(
        onDismissRequest = onDismiss,
        title = Str.get(R.string.file_properties),
        content = {
            // 用户反馈㉑又补了三行（权限/所有者/用户组），加上 SHA-256 那行 maxLines=4，
            // 矮屏上会把底部按钮顶出可视区 —— 限高 + 内部滚动，按钮留在 Dialog 固定底栏。
            Column(
                modifier = Modifier
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(AppDimens.spacingSmall)
            ) {
                // o.txt 第 10 条：单击复制该行；长按「名称」复制完整路径。
                PropRow(
                    Str.get(R.string.name),
                    file.name,
                    longPressValue = file.absolutePath,
                    // p.txt 第 8 条（用户）：长按名称复制的是**完整路径**，
                    // 提示就得说完整路径，不能再说笼统的「已复制」。
                    longPressMessage = R.string.files_copied_full_path
                ) { copyText(context, it) }
                PropRow(Str.get(R.string.location), file.parent ?: "—") { copyText(context, it) }
                PropRow(Str.get(R.string.file_type), typeValue) { copyText(context, it) }
                PropRow(Str.get(R.string.container_info_size), sizeValue) { copyText(context, it) }
                PropRow(Str.get(R.string.editor_modified_time), timeValue) { copyText(context, it) }
                PropRow(Str.get(R.string.files_prop_mime), mimeValue) { copyText(context, it) }
                // 用户反馈㉑：POSIX 三件套（权限 / 所有者 / 用户组）。
                // 三行一起挂在一个 stat 上 —— 各自再 stat 一次纯属浪费，而且
                // 中途失败会出现「有权限没所有者」的半截状态。
                stat?.let { st ->
                    PropRow(
                        Str.get(R.string.files_prop_permissions),
                        posixModeString(st.st_mode, isDir)
                    ) { copyText(context, it) }
                    PropRow(
                        Str.get(R.string.files_prop_owner),
                        st.st_uid.toString()
                    ) { copyText(context, it) }
                    PropRow(
                        Str.get(R.string.files_prop_group),
                        st.st_gid.toString()
                    ) { copyText(context, it) }
                }
                if (!isDir) {
                    // e20：行数 / 字符数只在文本类文件上显示（见 showsTextStats）
                    if (showTextStats) {
                        PropRow(Str.get(R.string.editor_prop_lines), linesValue) { copyText(context, it) }
                        PropRow(Str.get(R.string.editor_prop_chars), charsValue) { copyText(context, it) }
                    }
                    // 算法名是专有名词，中英文完全一样，不进字符串表。
                    // SHA-256 是 64 个字符，在 PropRow 的标签列之外只剩 ~216dp，
                    // 默认 2 行会被省略号截断 —— 单独放宽到 4 行。
                    // 长按复制走的是**完整值**而不是显示文本，截不截断都不影响复制。
                    PropRow("MD5", md5Value) { copyText(context, it) }
                    PropRow("SHA-256", shaValue, maxLines = 4) { copyText(context, it) }
                    // 安装包信息（e10）：属性框答的是「多大、什么时候改的」，
                    // 包名 / 版本 / 权限 / 签名 / 加壳特征它一个都答不上来
                    if (!isDir && file.extension.equals("apk", ignoreCase = true)) {
                        ApkInfoEntry(file, onDismiss)
                    }
                    // 粘贴校验（e16）：手上抄来的摘要，对不对得上本文件
                    PasteVerify(md5Value, shaValue)
                    // 两文件比对（e16）：手上这份是左侧，另一份从系统选择器里挑
                    CompareWithAnother(file, onDismiss)
                }
                Text(
                    text = Str.get(R.string.files_prop_copy_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            UnifiedButton(text = Str.get(R.string.close), onClick = onDismiss, variant = ButtonVariant.Primary)
        }
    )
}

/**
 * 把 `st_mode` 摆成 `drwxr-xr-x` 这样的九位串（用户反馈㉑）。
 *
 * Android 的 `java.io.File` 拿不到这些位，只能来自 `Os.stat`；位含义与 POSIX 一致
 * （owner 在 6~8 位、group 在 3~5、other 在 0~2）。特殊位按惯例**顶掉**执行位：
 * setuid/setgid 显示成 s/S，sticky 显示成 t/T。
 */
private fun posixModeString(mode: Int, isDir: Boolean): String {
    fun tri(shift: Int, specialMask: Int, specialChar: Char): String {
        val v = (mode shr shift) and 7
        val r = if (v and 4 != 0) 'r' else '-'
        val w = if (v and 2 != 0) 'w' else '-'
        val x = when {
            mode and specialMask == 0 -> if (v and 1 != 0) 'x' else '-'
            v and 1 != 0 -> specialChar
            else -> specialChar.uppercaseChar()
        }
        return "$r$w$x"
    }
    return buildString {
        append(if (isDir) 'd' else '-')
        append(tri(6, 0x800, 's')) // setuid
        append(tri(3, 0x400, 's')) // setgid
        append(tri(0, 0x200, 't')) // sticky
    }
}

/**
 * n.txt 第 1 条：多选后的**整体属性**。
 *
 * 原先多选长按「属性」只把 `targets.firstOrNull()` 交给单文件属性框，界面上是
 * 「第一个文件的名称/大小/MIME」，用户明明勾了 6 个却只看到 1 个。这里给出整批
 * 能回答的问题：选了几项、多少文件夹/文件、合计多大、最近一次修改、是否同目录、
 * 权限是否一致。**单文件才有的东西**（MIME、行数、MD5、粘贴校验、比对）一概不摆 ——
 * 它们没有「整批」的语义。
 *
 * 合计大小可能非常贵（一个勾选的目录里可能有十万个文件），所以：
 *  - 跑在 IO 线程，界面先显示 [R.string.files_calculating]；
 *  - 统计项数封顶 [AGGREGATE_MAX_ENTRIES]，超了就显示「≥ x」并注明截止项数，
 *    绝不为了一个数字把主线程/电量拖死（与删除确认的流式统计同一套思路）。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MultiFilePropertiesDialog(paths: List<String>, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val files = remember(paths) { paths.map(::File) }
    val dirCount = remember(files) { files.count { it.isDirectory } }
    val fileCount = files.size - dirCount
    val parents = remember(files) { files.mapNotNull { it.parent }.distinct() }
    val locationValue = if (parents.size == 1) parents[0]
    else Str.get(R.string.files_prop_multi_location_mixed)
    val newest = remember(files) { files.maxOfOrNull { it.lastModified() } ?: 0L }
    var totalValue by remember(paths) { mutableStateOf(Str.get(R.string.files_calculating)) }

    // 公共 POSIX 身份：所有选中项完全一致才显示，否则整段不摆（一排「—」只会让人以为坏了）。
    val commonStat = remember(files) {
        val first = files.firstOrNull()?.let { f ->
            runCatching { android.system.Os.stat(f.absolutePath) }.getOrNull()
        } ?: return@remember null
        val same = files.all { f ->
            val st = runCatching { android.system.Os.stat(f.absolutePath) }.getOrNull()
            st != null && st.st_mode == first.st_mode && st.st_uid == first.st_uid && st.st_gid == first.st_gid
        }
        if (same) first else null
    }

    LaunchedEffect(paths) {
        val (bytes, capped, scanned) = withContext(Dispatchers.IO) { aggregateSize(files) }
        totalValue = if (capped) {
            Str.get(R.string.files_prop_multi_total_capped, formatFileSize(bytes), scanned)
        } else {
            formatFileSize(bytes)
        }
    }

    UnifiedDialog(
        onDismissRequest = onDismiss,
        title = Str.get(R.string.files_prop_multi_title, files.size),
        content = {
            Column(
                modifier = Modifier
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(AppDimens.spacingSmall)
            ) {
                PropRow(Str.get(R.string.files_prop_multi_count), files.size.toString()) { copyText(context, it) }
                PropRow(Str.get(R.string.location), locationValue) { copyText(context, it) }
                PropRow(
                    Str.get(R.string.file_type),
                    Str.get(R.string.files_prop_multi_types, dirCount, fileCount)
                ) { copyText(context, it) }
                PropRow(Str.get(R.string.files_prop_multi_total), totalValue) { copyText(context, it) }
                PropRow(Str.get(R.string.files_prop_multi_latest), formatTime(newest)) { copyText(context, it) }
                commonStat?.let { st ->
                    PropRow(
                        Str.get(R.string.files_prop_permissions),
                        posixModeString(st.st_mode, files.firstOrNull()?.isDirectory == true)
                    ) { copyText(context, it) }
                    PropRow(Str.get(R.string.files_prop_owner), st.st_uid.toString()) { copyText(context, it) }
                    PropRow(Str.get(R.string.files_prop_group), st.st_gid.toString()) { copyText(context, it) }
                }
                Text(
                    text = Str.get(R.string.files_prop_copy_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            UnifiedButton(text = Str.get(R.string.close), onClick = onDismiss, variant = ButtonVariant.Primary)
        }
    )
}

/** 整体属性统计的项数上限（含目录本身）；超限就停并标记 capped。 */
private const val AGGREGATE_MAX_ENTRIES = 200_000

/**
 * 汇总一批条目的字节数（目录递归）。返回 `(bytes, capped, scannedEntries)`。
 *
 * 用显式栈而不是递归：目录深到几千层时递归会 StackOverflow，而这个函数跑在
 * IO 线程上，崩了就是整个进程。符号链接不跟随（`listFiles` 本来就只给直接子项），
 * 因此也不会被循环链接卡死。
 */
private fun aggregateSize(files: List<File>): Triple<Long, Boolean, Int> {
    var total = 0L
    var scanned = 0
    val stack = ArrayDeque<File>()
    files.forEach { stack.addLast(it) }
    while (stack.isNotEmpty()) {
        if (scanned >= AGGREGATE_MAX_ENTRIES) return Triple(total, true, scanned)
        val f = stack.removeLast()
        scanned++
        if (f.isDirectory) {
            runCatching { f.listFiles()?.forEach { stack.addLast(it) } }
        } else {
            total += runCatching { f.length() }.getOrDefault(0L)
        }
    }
    return Triple(total, false, scanned)
}

/**
 * **包内条目**的属性（e15 **P1-7**）。
 *
 * 与 [FilePropertiesDialog] 分成两个，是因为数据来源根本不同：那个问的是文件系统
 * （`length` / `lastModified` / `Os.stat`），这个问的是**归档的条目表** ——
 * `File("x.zip!/a.txt")` 是条虚拟路径，`exists()` 恒为 false，
 * 拿它去 stat 什么都问不出来。原来就卡在这儿：框还没弹出就被自己收掉了，
 * 看上去就是「点了属性没反应」。
 *
 * **权限 / 所有者 / 用户组**、**行数 / 字符数**刻意不摆：包内没有 POSIX 身份；
 * 行数要整条解出来才数得到，为一行数字解一个大文件不划算。
 *
 * **校验区**给两样：
 * - `CRC32` —— 归档自己记的那份，不解压；格式没有这一项就**整行不摆**；
 * - `MD5` / `SHA-256` —— 真解一遍算出来，可与别处的摘要对账。走的是编辑器开包内文件
 *   **同一条**通路（解到缓存临时副本 → 算 → 删），不另造一套按格式的流读取器；
 *   `checksums` 自带 256MB 门槛，超了给「—」，不会为一个摘要把缓存撑爆。
 *   这条通路目前只有 zip 真能走通，别的格式异常被 try/catch 兜住、如实显示「—」——
 *   **宁可少给一个数，也不编一个假的**。
 */
@Composable
fun ArchiveEntryPropertiesDialog(virtualPath: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val split = remember(virtualPath) { VirtualPath.split(virtualPath) }
    val archivePath = split?.first.orEmpty()
    val internalPath = split?.second.orEmpty()
    val archive = remember(archivePath) { File(archivePath) }
    val name = remember(internalPath) {
        internalPath.substringAfterLast('/').ifBlank { archivePath.substringAfterLast('/') }
    }
    val ext = remember(name) { name.substringAfterLast('.', "").lowercase(Locale.getDefault()) }

    var entry by remember(virtualPath) { mutableStateOf<ArchiveEntry?>(null) }
    var crcValue by remember(virtualPath) { mutableStateOf<String?>(null) }
    var md5Value by remember(virtualPath) { mutableStateOf("…") }
    var shaValue by remember(virtualPath) { mutableStateOf("…") }

    androidx.compose.runtime.LaunchedEffect(virtualPath) {
        if (split == null || !archive.isFile) return@LaunchedEffect
        entry = withContext(Dispatchers.IO) {
            runCatching { findArchiveEntry(archive, internalPath) }.getOrNull()
        }
        crcValue = withContext(Dispatchers.IO) {
            runCatching { archiveEntryCrc32(archive, internalPath) }.getOrNull()
        }?.let { "%08x".format(it) }

        val e = entry
        val shaLimit = 256L * 1024 * 1024
        if (e == null || e.isDir || e.size > shaLimit) {
            md5Value = "—"
            shaValue = "—"
        } else {
            val tmp = try {
                ArchiveEdit.extract(context.applicationContext.cacheDir, archive, internalPath)
            } catch (_: Exception) {
                null
            }
            if (tmp == null) {
                md5Value = "—"
                shaValue = "—"
            } else {
                val (m, s) = checksums(tmp)
                md5Value = m
                shaValue = s
                // 临时副本只为算这一趟，算完就删 —— 留着等于每次看属性都往缓存塞一份
                runCatching { tmp.delete() }
            }
        }
    }

    UnifiedDialog(
        onDismissRequest = onDismiss,
        title = Str.get(R.string.file_properties),
        content = {
            Column(
                modifier = Modifier
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(AppDimens.spacingSmall)
            ) {
                PropRow(Str.get(R.string.name), name) { copyText(context, it) }
                PropRow(
                    Str.get(R.string.location),
                    archivePath + if (internalPath.isEmpty()) "" else "!/$internalPath"
                ) { copyText(context, it) }
                PropRow(
                    Str.get(R.string.file_type),
                    if (ext.isEmpty()) Str.get(R.string.file) else ext
                ) { copyText(context, it) }
                PropRow(
                    Str.get(R.string.container_info_size),
                    entry?.let { if (it.isDir) "—" else formatFileSize(it.size) } ?: "—"
                ) { copyText(context, it) }
                PropRow(
                    Str.get(R.string.editor_modified_time),
                    entry?.modified?.takeIf { t -> t > 0L }?.let { t ->
                        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(t))
                    } ?: "—"
                ) { copyText(context, it) }
                PropRow(
                    Str.get(R.string.files_prop_mime),
                    if (ext.isEmpty()) "—" else MimeTypeMap.getSingleton()
                        .getMimeTypeFromExtension(ext) ?: "—"
                ) { copyText(context, it) }
                crcValue?.let { crc -> PropRow("CRC32", crc) { copyText(context, it) } }
                PropRow("MD5", md5Value) { copyText(context, it) }
                PropRow("SHA-256", shaValue, maxLines = 4) { copyText(context, it) }
                // 粘贴校验（e16）：包内条目与普通文件同一套判定
                PasteVerify(md5Value, shaValue)
                Text(
                    text = Str.get(R.string.files_prop_copy_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            UnifiedButton(
                text = Str.get(R.string.close),
                onClick = onDismiss,
                variant = ButtonVariant.Primary
            )
        }
    )
}

/**
 * 属性行：左标签右值，长按整行复制值（4.5 明确要求）。
 *
 * [maxLines] 默认 2 —— 路径这类长值截断成两行才不会把框撑高；
 * 需要显示长串的行（校验和）单独放宽，否则省略号会把内容吃掉一半。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PropRow(
    label: String,
    value: String,
    maxLines: Int = 2,
    longPressValue: String? = null,
    longPressMessage: Int? = null,
    onCopy: (String) -> Unit
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // k.txt 第 6 条：属性行涟漪按 6dp 圆角裁切。
            .clip(RoundedCornerShape(6.dp))
            .combinedClickable(
                onClick = { onCopy(value) },
                onLongClick = {
                    val copied = longPressValue ?: value
                    if (longPressMessage != null) {
                        copyText(context, copied, longPressMessage)
                    } else {
                        onCopy(copied)
                    }
                }
            )
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(96.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ==================== 小工具 ====================

/** 时间戳 → `yyyy-MM-dd HH:mm`；0 或负数给 `—`。 */
internal fun formatTime(epochMillis: Long): String =
    if (epochMillis > 0L) {
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(epochMillis))
    } else "—"

/** 剪贴板读文本；空剪贴板给空串（不抛异常）。 */
private fun readClipboard(context: Context): String =
    try {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        cm?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
            ?: ""
    } catch (_: Exception) {
        ""
    }

/** 复制到剪贴板并给一条轻提示。 */
internal fun copyText(
    context: Context,
    text: String,
    messageRes: Int = R.string.files_copied
) {
    if (text.isEmpty() || text == "—") return
    try {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        cm.setPrimaryClip(ClipData.newPlainText(text, text))
        com.UIN.Tool.utils.AppToast.info(context, Str.get(messageRes))
    } catch (_: Exception) {
        // 剪贴板被系统策略拦住（工作资料等）时静默，不打断操作
    }
}

/**
 * 流式统计行数与字符数 —— 大文件不一次性读进内存（4.5「行数字符数沿用流式统计」）。
 * 超过 [LIMIT] 字节就停手报「> N」，避免在一个 4GB 日志上把整个对话框卡住。
 */
internal suspend fun countLinesAndChars(file: File): Pair<String, String> = try {
    withContext(Dispatchers.IO) {
        val limit = 32L * 1024 * 1024
        var lines = 0L
        var chars = 0L
        var truncated = false
        file.inputStream().bufferedReader().use { reader ->
            val buf = CharArray(64 * 1024)
            while (true) {
                val n = reader.read(buf)
                if (n < 0) break
                chars += n
                for (i in 0 until n) if (buf[i] == '\n') lines++
                if (chars >= limit) {
                    truncated = true
                    break
                }
            }
        }
        val prefix = if (truncated) "> " else ""
        "$prefix$lines" to "$prefix$chars"
    }
} catch (_: Exception) {
    "—" to "—"
}

/**
 * 流式算 MD5 与 SHA-256 —— 与 [countLinesAndChars] 同一套护栏：
 * 跑在 [Dispatchers.IO]、超过 [LIMIT] 直接报「—」，不在一个 4GB 文件上把属性框卡死。
 *
 * **一次读喂两个摘要**：分两遍读等于把 IO 翻倍，而属性框一次只弹一个文件，
 * 没有复用第二次的可能。256MB 的门槛比行数统计的 32MB 宽 ——
 * 校验和本来就是用来验大文件的，卡在小文件上等于没做。
 */
internal suspend fun checksums(file: File): Pair<String, String> = try {
    withContext(Dispatchers.IO) {
        val limit = 256L * 1024 * 1024
        if (file.length() > limit) return@withContext "—" to "—"
        val md5 = MessageDigest.getInstance("MD5")
        val sha = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                md5.update(buf, 0, n)
                sha.update(buf, 0, n)
            }
        }
        // 与仓库里另外 4 处 hex 写法**逐字一致**（SecurityUtils / ArchiveEdit /
        // UpdateDownloader / DiskCache）。UpdateDownloader 拿同一行代码给下载的
        // 安装包做完整性校验，真印错早就全线炸了 —— 所以这里不自造第二种写法。
        fun hex(d: ByteArray) = d.joinToString("") { "%02x".format(it) }
        hex(md5.digest()) to hex(sha.digest())
    }
} catch (_: Exception) {
    "—" to "—"
}

// ==================== 压缩（§4.7）====================

/**
 * 压缩框：名字 + 格式 + 压缩级别 + （zip 的文件名编码）+ （zip / 7z 的密码）+ 保留时间戳。
 *
 * 级别、编码、时间戳三项**全部待在这儿**，设置页里一个都不摆（用户反馈⑪：
 * 「压缩，弹窗选择格式级别和编码等，不要放在设置」）。它们仍写回 `FilePrefs`，
 * 只是为了下次开框记住上次的选择 —— 那是记忆，不是设置。
 *
 * 三处刻意的设计：
 * 1. **格式用下拉而不是一排按钮** —— 6 种格式在窄屏上排按钮会挤成两行且看不全后缀；
 *    下拉里直接显示 `.tar.gz` 这类完整后缀，用户不必猜「gz 是不是单独一种」。
 * 2. **密码框只在 zip / 7z 出现**。tar 系根本没有密码概念，摆一个填了没用的框是在骗人；
 *    反过来，用户切到 tar 时若已填了密码，会顺手清掉 —— 免得他以为这个包是加密的。
 *    7z 多一个「加密文件名」复选：勾上就连文件列表一起加密（`-mhe=on`）。
 * 3. **默认不带密码**。加密要额外算一遍 AES，且忘了密码的数据是真找不回来的；
 *    默认不加密，要加密得用户主动填。
 */
@Composable
fun FileCompressDialog(
    request: CompressRequest,
    onConfirm: (
        name: String,
        format: ArchiveCompress.Format,
        password: String?,
        level: String,
        encoding: String,
        keepMeta: Boolean,
        splitSize: Long,
        /** 7z 专属：连文件名一起加密（`-mhe=on`）。 */
        encryptHeader: Boolean
    ) -> Unit,
    /** 「加入已有压缩包」（e15 P1-10）：目标是**已存在**的包，与新建走不同回调。 */
    onAppend: (
        archiveName: String,
        mode: UpdateMode,
        level: String
    ) -> Unit,
    onDismiss: () -> Unit
) {
    var format by remember {
        // 记住上次压成的格式（用户反馈⑪：格式也待在弹窗里，不进设置）。
        // 存的是 `Format.extension`，认不出（旧版本存过 "7z" 这种本版压不了的）
        // 就回落到 zip —— 与「不摆点了必然失败的选项」是同一条底线。
        mutableStateOf(ArchiveCompress.detect(FilePrefs.zipFormat()) ?: ArchiveCompress.Format.ZIP)
    }
    var name by remember(request.initialName) {
        // 名字框给的是**完整文件名**（带后缀）。只给主干名的话，用户看不出压出来
        // 到底叫什么，切格式时也没有「后缀跟着变」这回事（用户反馈⑪）。
        mutableStateOf(request.initialName + format.extension)
    }
    // 级别 / 编码 / 保留时间戳的默认值**只从上次的选择来**，不再另设一份设置项
    // （用户反馈⑪：「不要放在设置」）。写回由 performCompress 负责，取消则不记。
    var level by remember { mutableStateOf(FilePrefs.zipLevel()) }
    var encoding by remember { mutableStateOf(FilePrefs.zipEncoding()) }
    var keepMeta by remember { mutableStateOf(FilePrefs.keepMeta()) }
    var password by remember { mutableStateOf("") }
    // 7z 的「加密文件名」。只在 7z 且填了密码时才有意义（没有密码就没有加密）。
    // 与级别互斥是上游属性缺陷（issue #77），界面按「加密文件名优先」呈现。
    var encryptHeader by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var levelMenuOpen by remember { mutableStateOf(false) }
    var encMenuOpen by remember { mutableStateOf(false) }
    // 分卷字节数（0 = 不分卷）。只在弹窗里选，但**记住上次的选择**（i.txt 第 12 条）；
    // 只有 zip 有分卷概念，切走格式时一并归零。
    var splitSize by remember { mutableStateOf(FilePrefs.splitSize()) }
    var splitMenuOpen by remember { mutableStateOf(false) }

    // 「加入已有压缩包」（e15 P1-10）。目标是当前目录里**已有的 zip**；一个都没有
    // 就整行不摆 —— 摆一个点了必然失败的开关，比不摆糟得多。
    // 列表放 IO 线程拉：大目录在主线程 listFiles 会让弹窗先卡一下。
    var appendMode by remember { mutableStateOf(false) }
    var appendTarget by remember { mutableStateOf("") }
    var appendTargets by remember { mutableStateOf<List<String>>(emptyList()) }
    var appendMenuOpen by remember { mutableStateOf(false) }
    var updateMode by remember { mutableStateOf(UpdateMode.ADD_NEW) }
    var updateMenuOpen by remember { mutableStateOf(false) }

    androidx.compose.runtime.LaunchedEffect(request.dirPath) {
        val list = withContext(Dispatchers.IO) {
            runCatching {
                File(request.dirPath).listFiles().orEmpty()
                    .filter {
                        it.isFile &&
                            ArchiveCompress.detect(it.name) == ArchiveCompress.Format.ZIP
                    }
                    .map { it.name }
                    .sorted()
            }.getOrDefault(emptyList())
        }
        appendTargets = list
        appendTarget = list.firstOrNull().orEmpty()
    }

    UnifiedDialog(
        onDismissRequest = onDismiss,
        title = Str.get(R.string.files_compress),
        content = {
            Column(
                verticalArrangement = Arrangement.spacedBy(AppDimens.spacingSmall),
                // 字段变多了（名字 / 格式 / 级别 / 编码 / 密码 / 条数），小屏上必须能滚 ——
                // 被挤出屏幕外的「确定」按钮等于没有
                modifier = Modifier
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(Str.get(R.string.files_new_name)) },
                    singleLine = true,
                    // 加入已有包时目标是下面选的那个包，名字框不起作用 → 禁掉，
                    // 免得用户打了个名字，压完发现包名跟自己打的不一样
                    enabled = !appendMode,
                    modifier = Modifier.fillMaxWidth().dialogAutoFocus(enabled = !appendMode)
                )

                // 格式下拉。显示完整后缀（`.tar.gz`），免得用户以为 gz 是独立格式。
                OutlinedButton(
                    onClick = { menuOpen = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(format.label, modifier = Modifier.weight(1f))
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                }
                UnifiedDropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    ArchiveCompress.Format.entries.forEach { f ->
                        DropdownMenuItem(
                            text = { Text(f.label) },
                            onClick = {
                                // 后缀跟着格式走：把**已知的归档后缀全部剥掉**再拼新的，
                                // 免得「a.zip」一换格式变成「a.zip.tar」
                                val base = ArchiveCompress.Format.entries
                                    .fold(name) { acc, ff -> acc.removeSuffix(ff.extension) }
                                    .ifBlank { request.initialName }
                                name = base + f.extension
                                format = f
                                // 切到不支持密码的格式就清空已填的密码 —— 否则用户会
                                // 以为这个包是加密的，而实际上一个字节都没加密
                                if (!f.supportsPassword) password = ""
                                // 「加密文件名」只有 7z 有；切走就复位，免得再切回来时
                                // 一个没注意就压出了加密包头的包
                                if (f != ArchiveCompress.Format.SEVEN_Z) encryptHeader = false
                                // 换成不支持分卷的格式就把分卷一并清掉 —— 留着的话，
                                // 切到 tar 再切回来会莫名其妙多出一个自己没选过的分卷
                                if (!f.supportsSplit) splitSize = 0L
                                // 加入已有包只对 zip 有意义（列的是 zip、写的是 zip4j）
                                if (f != ArchiveCompress.Format.ZIP) appendMode = false
                                menuOpen = false
                            }
                        )
                    }
                }

                // 压缩级别：三档。档位差别肉眼难辨，给 10 个只会让人怀疑选错了。
                //
                // 7z + 加密文件名时这一档被上游缺陷锁死（issue #77：两个属性互相重置，
                // 只能有一个生效）。按「加密优先」的取舍，此时级别固定默认 ——
                // 按钮直接置灰并给一行说明，比让用户选了却被静默忽略强。
                val levelLocked = format == ArchiveCompress.Format.SEVEN_Z &&
                    encryptHeader && password.isNotEmpty()
                OutlinedButton(
                    onClick = { levelMenuOpen = true },
                    enabled = !levelLocked,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = when (level) {
                            FilePrefs.LEVEL_FAST -> Str.get(R.string.fs_level_fast)
                            FilePrefs.LEVEL_BEST -> Str.get(R.string.fs_level_best)
                            else -> Str.get(R.string.fs_level_default)
                        },
                        modifier = Modifier.weight(1f)
                    )
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                }
                if (levelLocked) {
                    Text(
                        text = Str.get(R.string.files_compress_level_locked),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                UnifiedDropdownMenu(expanded = levelMenuOpen, onDismissRequest = { levelMenuOpen = false }) {
                    listOf(
                        FilePrefs.LEVEL_FAST to Str.get(R.string.fs_level_fast),
                        FilePrefs.LEVEL_DEFAULT to Str.get(R.string.fs_level_default),
                        FilePrefs.LEVEL_BEST to Str.get(R.string.fs_level_best)
                    ).forEach { (v, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = { level = v; levelMenuOpen = false }
                        )
                    }
                }

                // 文件名编码：只对 zip 有意义（tar 的条目名没有编码开关）。
                // 写侧与读侧吃的是**同一个** pref，改这里同时改掉读旧包时的回退编码。
                if (format == ArchiveCompress.Format.ZIP) {
                    OutlinedButton(
                        onClick = { encMenuOpen = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(encoding, modifier = Modifier.weight(1f))
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                    }
                    UnifiedDropdownMenu(expanded = encMenuOpen, onDismissRequest = { encMenuOpen = false }) {
                        // 代码页名全球同形，不进字符串资源
                        listOf(
                            FilePrefs.ENCODING_UTF8,
                            FilePrefs.ENCODING_GBK,
                            FilePrefs.ENCODING_BIG5,
                            FilePrefs.ENCODING_ISO88591,
                            FilePrefs.ENCODING_CP437
                        ).forEach { e ->
                            DropdownMenuItem(
                                text = { Text(e) },
                                onClick = { encoding = e; encMenuOpen = false }
                            )
                        }
                    }
                }

                // 「加入已有压缩包」（用户反馈 e15 **P1-10**）。
                // 与 e17 那条撞名三选是同一条底层路（ZipWrite.addFiles），区别是这条
                // **用户主动选的**，而不是撞了名才被动问；而且它自带级别与更新方式三选。
                // 只列当前目录下的 zip：目标必须是用户**看得见**的东西 —— 开个文件选择器
                // 让他去回忆「我那个包放哪了」，是把简单事变复杂。
                if (format == ArchiveCompress.Format.ZIP && appendTargets.isNotEmpty()) {
                    DialogCheckRow(
                        label = Str.get(R.string.files_compress_append_entry),
                        checked = appendMode
                    ) { appendMode = it }

                    if (appendMode) {
                        OutlinedButton(
                            onClick = { appendMenuOpen = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = appendTarget.ifBlank {
                                    Str.get(R.string.files_compress_append_entry)
                                },
                                modifier = Modifier.weight(1f)
                            )
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                        }
                        UnifiedDropdownMenu(
                            expanded = appendMenuOpen,
                            onDismissRequest = { appendMenuOpen = false }
                        ) {
                            appendTargets.forEach { n ->
                                DropdownMenuItem(
                                    text = { Text(n) },
                                    onClick = { appendTarget = n; appendMenuOpen = false }
                                )
                            }
                        }

                        // 更新方式三选：模式定了就**不再逐项问**（见 UpdateMode 的说明）
                        OutlinedButton(
                            onClick = { updateMenuOpen = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = when (updateMode) {
                                    UpdateMode.ADD_NEW ->
                                        Str.get(R.string.files_compress_update_add_new)
                                    UpdateMode.UPDATE ->
                                        Str.get(R.string.files_compress_update_update)
                                    UpdateMode.REPLACE_ALL ->
                                        Str.get(R.string.files_compress_update_replace)
                                },
                                modifier = Modifier.weight(1f)
                            )
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                        }
                        UnifiedDropdownMenu(
                            expanded = updateMenuOpen,
                            onDismissRequest = { updateMenuOpen = false }
                        ) {
                            listOf(
                                UpdateMode.ADD_NEW to Str.get(R.string.files_compress_update_add_new),
                                UpdateMode.UPDATE to Str.get(R.string.files_compress_update_update),
                                UpdateMode.REPLACE_ALL to Str.get(R.string.files_compress_update_replace)
                            ).forEach { (m, label) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = { updateMode = m; updateMenuOpen = false }
                                )
                            }
                        }

                        // 级别用的是下面那行同一个 `level` 变量，这里不重复摆一份
                    }
                }

                // 分卷（用户反馈 e15 **P1-9**「分卷给入口」）：底层 `createSplitZipFile`
                // 早就能用，缺的只是这个入口。只对 zip 给 —— 别的格式 `supportsSplit`
                // 恒为 false，摆上去就是个点了没反应的开关。
                // 档位给 100 / 200 / 700 MB 三档 + 不分卷：自由输入只会让人问「多少合适」，
                // 而这三档已经覆盖了「发给别人」的全部常见场景（700 MB ≈ 一张 CD）。
                if (format.supportsSplit) {
                    OutlinedButton(
                        onClick = { splitMenuOpen = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = Str.get(R.string.files_compress_split),
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = if (splitSize > 0L) formatFileSize(splitSize)
                            else Str.get(R.string.files_compress_split_off),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                    }
                    UnifiedDropdownMenu(
                        expanded = splitMenuOpen,
                        onDismissRequest = { splitMenuOpen = false }
                    ) {
                        listOf(
                            0L,
                            100L * 1024 * 1024,
                            200L * 1024 * 1024,
                            700L * 1024 * 1024
                        ).forEach { v ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (v == 0L) Str.get(R.string.files_compress_split_off)
                                        else formatFileSize(v)
                                    )
                                },
                                onClick = { splitSize = v; splitMenuOpen = false }
                            )
                        }
                    }
                }

                // 密码：zip 与 7z 给框（tar 系没有这个概念）
                if (format.supportsPassword) {
                    OutlinedTextField(
                        value = password,
                        onValueChange = {
                            password = it
                            // 密码清空 = 不再加密，文件名加密也就无从谈起
                            if (it.isEmpty()) encryptHeader = false
                        },
                        label = { Text(Str.get(R.string.files_compress_password)) },
                        singleLine = true,
                        supportingText = {
                            Text(
                                Str.get(
                                    if (format == ArchiveCompress.Format.SEVEN_Z)
                                        R.string.files_compress_password_hint_7z
                                    else R.string.files_compress_password_hint
                                )
                            )
                        },
                        modifier = Modifier.fillMaxWidth().dialogAutoFocus()
                    )
                    // 7z 专属：连文件名表一起加密（-mhe=on）。只有填了密码才有意义 ——
                    // 没密码时这一项摆出来也压不出加密包。
                    if (format == ArchiveCompress.Format.SEVEN_Z && password.isNotEmpty()) {
                        DialogCheckRow(
                            label = Str.get(R.string.files_compress_encrypt_header),
                            checked = encryptHeader
                        ) { encryptHeader = it }
                        Text(
                            text = Str.get(R.string.files_compress_encrypt_header_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // 保留时间戳：压缩框自带，不再往设置里塞一份（用户反馈⑪）
                DialogCheckRow(
                    label = Str.get(R.string.fs_keep_meta),
                    checked = keepMeta
                ) { keepMeta = it }

                Text(
                    text = Str.get(R.string.files_compress_items, request.sources.size.toString()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            UnifiedButton(
                text = Str.get(R.string.files_compress),
                onClick = {
                    if (appendMode && appendTarget.isNotBlank()) {
                        onAppend(appendTarget, updateMode, level)
                    } else {
                        onConfirm(
                            name,
                            format,
                            password.takeIf { it.isNotEmpty() },
                            level,
                            encoding,
                            keepMeta,
                            if (format.supportsSplit) splitSize else 0L,
                            encryptHeader && format == ArchiveCompress.Format.SEVEN_Z &&
                                password.isNotEmpty()
                        )
                    }
                },
                variant = ButtonVariant.Primary
            )
        },
        dismissButton = {
            UnifiedButton(
                text = Str.get(R.string.cancel),
                onClick = onDismiss,
                variant = ButtonVariant.Outlined
            )
        }
    )
}

/**
 * 输出名撞车的**三选**（反馈 e17）：追加进已有包 / 换个名字新建 / 取消。
 *
 * 为什么有这一层：原先 `performCompress` 一上来就 `uniqueSibling()` 把名字静默改成
 * `x (1).zip`，用户压完才发现包名不对，也永远没有「把内容并进已有包」这条路。
 *
 * 三个键一律**纯文字**（反馈 e29：弹窗按钮不铺底色），靠字色区分语义。
 * 只有 zip 能往已有包里追加 —— `ZipWrite.addFiles` 是 zip 专用，
 * 别的格式只给「换个名字 / 取消」两选，**不摆点了必然失败的键**。
 */
@Composable
fun FileCompressConflictDialog(
    conflict: CompressConflict,
    onAppend: () -> Unit,
    onNewName: () -> Unit,
    onSkip: () -> Unit,
    onDismiss: () -> Unit
) {
    val canAppend = ArchiveCompress.detect(File(conflict.existing).name) == ArchiveCompress.Format.ZIP
    // f16：几个做法从「一排并列按钮」改成**单选框** —— 并列按钮分不清哪几个是
    // 同一件事的不同做法、哪个只是退出去；单选框把这几种做法摆成一列，只有一项生效。
    // 新增的「跳过该文件」= 这次不压，已有包原样不动。
    var choice by remember(conflict) {
        mutableStateOf(if (canAppend) CompressOutcome.APPEND else CompressOutcome.RENAME)
    }
    UnifiedDialog(
        onDismissRequest = onDismiss,
        title = Str.get(R.string.files_compress_conflict_title),
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(AppDimens.spacingSmall)) {
                Text(
                    text = Str.get(R.string.files_compress_conflict_msg, File(conflict.existing).name),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                // 只有 zip 能往已有包里追加 —— 别的格式不摆点了必然失败的选项
                if (canAppend) {
                    ConflictChoiceRow(
                        selected = choice == CompressOutcome.APPEND,
                        label = Str.get(R.string.files_compress_append),
                        onSelect = { choice = CompressOutcome.APPEND }
                    )
                }
                ConflictChoiceRow(
                    selected = choice == CompressOutcome.RENAME,
                    label = Str.get(R.string.files_compress_new_name, conflict.suggested),
                    onSelect = { choice = CompressOutcome.RENAME }
                )
                ConflictChoiceRow(
                    selected = choice == CompressOutcome.SKIP,
                    label = Str.get(R.string.files_conflict_skip),
                    onSelect = { choice = CompressOutcome.SKIP }
                )
            }
        },
        confirmButton = {
            UnifiedButton(
                text = Str.get(R.string.confirm),
                onClick = {
                    when (choice) {
                        CompressOutcome.APPEND -> onAppend()
                        CompressOutcome.RENAME -> onNewName()
                        CompressOutcome.SKIP -> onSkip()
                    }
                },
                variant = ButtonVariant.Primary
            )
        },
        dismissButton = {
            UnifiedButton(
                text = Str.get(R.string.cancel),
                onClick = onDismiss,
                variant = ButtonVariant.Text
            )
        }
    )
}

/**
 * 后台任务清单（f20）。
 *
 * 进度框上的「隐藏」只是把**框**收起来，操作本身照跑；原先收起来之后没有任何地方
 * 能再看到它 —— 想确认还剩多少、想取消，只能重新触发一次操作把框顶出来。
 * 侧边栏这一项就是那扇门：列出手头正在跑的文件操作，点进去看细节或直接取消。
 *
 * 当前实现同一时刻只有一条操作（[com.UIN.Tool.ui.screen.files.FileManageViewModel]
 * 里 `opJob` 只有一个），所以清单最多一行；列表结构先按「多条」写好，将来要并发时
 * 只换数据源，不动 UI。
 */
@Composable
fun FileBackgroundTasksDialog(
    op: OpUiState.Running?,
    onShow: () -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit
) {
    UnifiedDialog(
        onDismissRequest = onDismiss,
        title = Str.get(R.string.files_tasks_title),
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(AppDimens.spacingSmall)) {
                if (op == null) {
                    Text(
                        text = Str.get(R.string.files_tasks_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    val p = op.progress
                    Text(
                        text = progressTitle(p),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    // 已完成 / 总数 + 已处理字节：与进度框同一套口径，数字直接在代码里拼，
                    // 只有「项」这一层语义走文案
                    Text(
                        text = Str.get(
                            R.string.files_tasks_detail,
                            p.doneCount.toString(),
                            p.totalCount.toString()
                        ) + if (p.doneBytes > 0) " · " + formatFileSize(p.doneBytes) else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (p.currentName.isNotEmpty()) {
                        Text(
                            text = p.currentName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.MiddleEllipsis
                        )
                    }
                    if (p.sourcePath.isNotEmpty()) {
                        Text(
                            text = p.sourcePath,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.MiddleEllipsis
                        )
                    }
                    if (p.targetPath.isNotEmpty()) {
                        Text(
                            text = p.targetPath,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.MiddleEllipsis
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (op != null) {
                UnifiedButton(
                    text = Str.get(R.string.files_tasks_show),
                    onClick = onShow,
                    variant = ButtonVariant.Outlined
                )
            }
        },
        dismissButton = {
            if (op != null && !op.cancelling) {
                UnifiedButton(
                    text = Str.get(R.string.files_op_cancel),
                    onClick = onCancel,
                    variant = ButtonVariant.Text
                )
            }
            UnifiedButton(
                text = Str.get(R.string.cancel),
                onClick = onDismiss,
                variant = ButtonVariant.Text
            )
        }
    )
}

/** 压缩撞名时的三种做法（f16 改成单选项）。 */
private enum class CompressOutcome { APPEND, RENAME, SKIP }

/**
 * 单选项一行：圆点 + 标题（可带危险色）+ 可选说明，整行可点。
 *
 * 冲突类弹窗共用（复制/移动撞名、压缩输出名撞名）—— 两处选项语义相同，
 * 摆两套样式迟早走形。
 */
@Composable
private fun ConflictChoiceRow(
    selected: Boolean,
    label: String,
    onSelect: () -> Unit,
    hint: String? = null,
    danger: Boolean = false
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            // k.txt 第 6 条：冲突选项行涟漪按 8dp 圆角裁切。
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onSelect)
            .padding(vertical = 2.dp)
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                color = if (danger) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurface
            )
            if (!hint.isNullOrEmpty()) {
                Text(
                    text = hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * 把「相对当前目录的路径」落成真实路径；越出 [cwd]（`..` 顶到头）返回 null。
 *
 * 纯文本计算：`.` 消掉、`..` 回退一层、**回退到头就判非法** —— 不用 `canonicalPath`，
 * 那是 IO（解析符号链接要 stat），而这个函数在「解压到文件夹」输入框的**每次按键**上都会跑。
 * 空输入也返回 null：没有名字就没有子目录，交给「确定」按钮的禁用态去表达。
 */
private fun resolveUnderCwd(cwd: String, rel: String): String? {
    val base = cwd.trimEnd('/')
    val segs = ArrayList<String>()
    for (part in rel.trim().split('/')) {
        val seg = part.trim()
        if (seg.isEmpty() || seg == ".") continue
        if (seg == "..") {
            if (segs.isEmpty()) return null
            segs.removeAt(segs.lastIndex)
            continue
        }
        segs.add(seg)
    }
    if (segs.isEmpty()) return null
    return base + "/" + segs.joinToString("/")
}

/**
 * 整包解压框：目标目录四选一 + 还原时间戳 + 「解压后删除源文件」（用户反馈⑪/c14）。
 *
 * 落点为什么是这四个：
 * - **当前目录** —— 最常见的意图，也是默认值（长按哪个包就在它所在的目录里解）；
 * - **另一窗口的路径** —— 双窗格「这边挑、那边放」的落点（用户反馈 c14）。
 *   原来的「归档旁」被顶掉：列表里长按的包就躺在 cwd 下面，归档旁永远等于当前目录，
 *   两条选项指向同一个地方，摆着纯属凑数。归档在归档内（cwd 是虚拟路径）时，
 *   `requestExtract` 开框时会替用户改选到这一项；
 * - **解压到文件夹** —— 在当前目录下建一个子文件夹，名字在下面的输入框里给，
 *   默认是归档名去掉扩展名（`photo.zip` → `photo`），可改成任意名字；
 * - **指定目录** —— 记住上次那条路径，来回倒腾同一个落点时不必每次重打。
 *
 * 撞名不在这儿问：目标里已有同名时由**统一的冲突对话框**接手（替换 / 跳过 / 保留两份），
 * 与复制、移动是同一套口径 —— 一个策略摆两个地方，改了一处忘另一处是迟早的事。
 *
 * @param otherCwd 另一窗格的当前目录 —— 「另一窗口」这一项显示什么、解到哪儿，全看它。
 */
@Composable
fun FileExtractDialog(
    request: ExtractRequest,
    otherCwd: String,
    onConfirm: (
        targetKey: String,
        targetDir: String,
        restoreTimestamps: Boolean,
        deleteSource: Boolean,
        password: String?
    ) -> Unit,
    onDismiss: () -> Unit
) {
    var target by remember(request) { mutableStateOf(request.initialTarget) }
    var customPath by remember(request) { mutableStateOf(FilePrefs.customExtractTarget()) }
    var restore by remember(request) { mutableStateOf(FilePrefs.restoreMeta()) }
    var deleteSource by remember(request) { mutableStateOf(FilePrefs.extractDeleteSource()) }

    // 加密包才要密码（b10）。**探测不需要密码** —— 只读中央目录里的通用标志位，
    // commons-compress 读标志位不解密；据此决定摆不摆这个框 —— 没加密也摆的话，
    // 就是在一个用不上的弹窗里再加一行（用户反馈 c15「弹窗太高」）。
    // 探测放 IO 线程：中央目录在包尾，大包在主线程读会掉帧。
    var encrypted by remember(request) { mutableStateOf(false) }
    var password by remember(request) { mutableStateOf("") }
    LaunchedEffect(request.archivePath) {
        encrypted = withContext(Dispatchers.IO) {
            runCatching {
                val f = File(request.archivePath)
                val fmt = detectArchiveFormat(f)
                // 7z 也摆密码框（e15 P1-12）：头被加密的连条目表都读不出来
                (fmt == ArchiveFormat.ZIP || fmt == ArchiveFormat.SEVEN_Z) &&
                    ArchiveEncoding.hasEncryptedEntry(f)
            }.getOrDefault(false)
        }
    }

    /** 「解压到文件夹」默认给的目录名：归档名去掉最后一个扩展名（`photo.zip` → `photo`）。 */
    val defaultFolderName = remember(request) {
        val name = File(request.archivePath).name
        if (name.contains('.')) name.substringBeforeLast('.') else name
    }
    var folderInput by remember(request) { mutableStateOf(defaultFolderName) }

    // 「解压到文件夹」给的是**相对当前目录的路径**（用户反馈 c14 的原话），所以支持
    // `子目录/更里层` 这种写法；`..` 越出当前目录直接判非法 —— 纯文本归一，不碰磁盘
    // （canonicalPath 是 IO，输入框每敲一个字都会走一次）。
    val folderPath = resolveUnderCwd(request.cwd, folderInput)

    val targetDir = when (target) {
        FilePrefs.TARGET_OTHER -> otherCwd
        FilePrefs.TARGET_FOLDER -> folderPath.orEmpty()
        FilePrefs.TARGET_CUSTOM -> customPath.trim()
        else -> request.cwd
    }
    // 空路径直接不给按：与其点了换一句报错，不如让「确定」自己说还差什么
    val canConfirm = targetDir.isNotEmpty()

    val options = listOf(
        Triple(FilePrefs.TARGET_HERE, Str.get(R.string.files_extract_here), request.cwd),
        Triple(FilePrefs.TARGET_OTHER, Str.get(R.string.fs_target_other), otherCwd),
        Triple(
            FilePrefs.TARGET_FOLDER,
            Str.get(R.string.fs_target_folder),
            // 还没给名字时露出「当前目录/」这半截，看得出它建在哪儿
            folderPath ?: request.cwd.trimEnd('/') + "/"
        ),
        Triple(FilePrefs.TARGET_CUSTOM, Str.get(R.string.fs_target_custom), "")
    )

    UnifiedDialog(
        onDismissRequest = onDismiss,
        title = Str.get(R.string.fs_group_extract),
        content = {
            Column(
                verticalArrangement = Arrangement.spacedBy(AppDimens.spacingSmall),
                modifier = Modifier
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = Str.get(R.string.files_extract_target),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                options.forEach { (value, label, hint) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { target = value }
                            .padding(vertical = 2.dp)
                    ) {
                        RadioButton(
                            selected = target == value,
                            onClick = { target = value }
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1
                            )
                            if (hint.isNotEmpty()) {
                                Text(
                                    text = hint,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                // 选了哪一项才给哪一项的输入框：不选时摆一条空路径框只会让人猜它是干嘛的
                if (target == FilePrefs.TARGET_FOLDER) {
                    UnifiedTextField(
                        value = folderInput,
                        onValueChange = { folderInput = it },
                        label = Str.get(R.string.fs_folder_name),
                        placeholder = defaultFolderName,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (target == FilePrefs.TARGET_CUSTOM) {
                    UnifiedTextField(
                        value = customPath,
                        onValueChange = { customPath = it },
                        label = Str.get(R.string.fs_target_custom),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                HorizontalDivider()

                // 加密包：探测出来才给框。密码空着也能按「解压」—— 真空的话引擎那侧
                // 会照「没有密码」处理并给出一句说人话的拒绝，不在这儿拦死
                if (encrypted) {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text(Str.get(R.string.files_extract_password)) },
                        singleLine = true,
                        supportingText = {
                            Text(Str.get(R.string.files_extract_password_hint))
                        },
                        modifier = Modifier.fillMaxWidth().dialogAutoFocus()
                    )
                }

                DialogCheckRow(
                    label = Str.get(R.string.fs_restore_meta),
                    checked = restore
                ) { restore = it }

                DialogCheckRow(
                    label = Str.get(R.string.fs_extract_delete_source),
                    checked = deleteSource
                ) { deleteSource = it }

                // 勾上才出这行警告：删原包不可逆，得让人在按「确定」之前**看见**这句话，
                // 而不是常驻一句灰字，看久了等于没有
                if (deleteSource) {
                    Text(
                        text = Str.get(R.string.fs_extract_delete_source_warn),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            UnifiedButton(
                text = Str.get(R.string.fs_group_extract),
                enabled = canConfirm,
                onClick = {
                    onConfirm(
                        target,
                        targetDir,
                        restore,
                        deleteSource,
                        password.takeIf { it.isNotBlank() }
                    )
                },
                variant = ButtonVariant.Primary
            )
        },
        dismissButton = {
            UnifiedButton(
                text = Str.get(R.string.cancel),
                onClick = onDismiss,
                variant = ButtonVariant.Outlined
            )
        }
    )
}

/**
 * 归档内条目复制 / 移动到另一窗格时的密码框（b10 的补口）。
 *
 * 引擎在**动手之前**就拒绝没有密码的加密包，可「粘贴到另一窗格」这条路上原先一个密码框
 * 都没有 —— 报一句「请在解压时输入密码」，用户却无处可输。所以把框摆在动手之前：
 * 取消 = 这次拷贝整个不做（一个字节都不写），确定 = 带着密码原样重跑同一次拷贝。
 *
 * 文案与整包解压框里的密码行同一条（[R.string.files_extract_password]）——
 * 同一件事在两个入口必须有一样的手感。
 */
@Composable
fun FileArchivePasswordDialog(
    onConfirm: (password: String) -> Unit,
    onDismiss: () -> Unit
) {
    var password by remember { mutableStateOf("") }

    UnifiedDialog(
        onDismissRequest = onDismiss,
        title = Str.get(R.string.files_archive_password_title),
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(AppDimens.spacingSmall)) {
                Text(
                    text = Str.get(R.string.files_extract_password_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(Str.get(R.string.files_extract_password)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().dialogAutoFocus()
                )
            }
        },
        confirmButton = {
            UnifiedButton(
                text = Str.get(R.string.confirm),
                // 空密码交上去只会撞引擎的 fail-fast，再弹一次同一句拒绝 —— 那是白跑一趟
                enabled = password.isNotBlank(),
                onClick = { onConfirm(password) },
                variant = ButtonVariant.Primary
            )
        },
        dismissButton = {
            UnifiedButton(
                text = Str.get(R.string.cancel),
                onClick = onDismiss,
                variant = ButtonVariant.Outlined
            )
        }
    )
}
