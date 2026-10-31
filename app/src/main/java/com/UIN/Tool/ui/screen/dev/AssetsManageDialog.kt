package com.UIN.Tool.ui.screen.dev

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.UIN.Tool.R
import com.UIN.Tool.ui.components.unified.UnifiedConfirmDialog
import com.UIN.Tool.ui.components.unified.UnifiedDialog
import com.UIN.Tool.ui.components.unified.UnifiedDialogTextButton
import com.UIN.Tool.ui.theme.AppColors
import com.UIN.Tool.ui.theme.AppDimens
import com.UIN.Tool.ui.screen.dev.editor.EditorAssetsStore
import com.UIN.Tool.utils.AppToast
import com.UIN.Tool.utils.Str
import kotlinx.coroutines.launch

/**
 * 6.2.0 批次66追加：**语法 / 主题文件管理**（用户要求「提供语法/主题文件管理入口，
 * 可以新增或者删除」，入口放在编辑器设置页）。
 *
 * - 两个分页：主题 / 语法，列出**内置 + 用户导入**的全部文件；内置项标「内置」只读，
 *   用户项带删除按钮（sora 注册表无热移除接口 → 删除提示「重启后生效」）；
 * - 「导入文件」走 SAF **多选**（用户指定批量），按内容自动判定主题 / 语法并逐个校验，
 *   非法文件单独记失败，不影响同批其余文件；导入的主题/语法**当场生效**。
 */
@Composable
fun AssetsManageDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // 0 = 主题，1 = 语法；refresh 用于导入/删除后重算列表
    var tab by remember { mutableIntStateOf(0) }
    var refresh by remember { mutableIntStateOf(0) }

    val themes = remember(refresh) { EditorAssetsStore.listThemes(context) }
    val grammars = remember(refresh) { EditorAssetsStore.listGrammars(context) }

    var pendingDelete by remember { mutableStateOf<(() -> Unit)?>(null) }
    var pendingDeleteName by remember { mutableStateOf("") }
    var outcome by remember { mutableStateOf<EditorAssetsStore.ImportOutcome?>(null) }

    // SAF 多选：mime 用 */*，扩展名与内容校验在存储层做（.tmLanguage 等常无标准 mime）
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (!uris.isNullOrEmpty()) {
            scope.launch {
                outcome = EditorAssetsStore.import(context, uris)
                refresh++
            }
        }
    }

    UnifiedDialog(
        onDismissRequest = onDismiss,
        title = Str.get(R.string.editor_assets_title),
        content = {
            // 分页：主题 / 语法（带各自数量）
            Row(verticalAlignment = Alignment.CenterVertically) {
                AssetTab(
                    label = "${Str.get(R.string.editor_theme)} (${themes.size})",
                    selected = tab == 0
                ) { tab = 0 }
                AssetTab(
                    label = "${Str.get(R.string.editor_assets_grammar)} (${grammars.size})",
                    selected = tab == 1
                ) { tab = 1 }
            }
            HorizontalDivider()
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
            ) {
                if (tab == 0) {
                    items(themes.size) { i ->
                        val item = themes[i]
                        AssetRow(
                            name = item.name,
                            subtitle = if (item.builtIn) null else (item.rawName?.takeIf { it.isNotEmpty() }),
                            builtIn = item.builtIn,
                            onDelete = {
                                pendingDeleteName = item.name
                                pendingDelete = {
                                    if (EditorAssetsStore.deleteTheme(item)) {
                                        refresh++
                                        AppToast.success(
                                            context,
                                            Str.get(R.string.editor_assets_deleted, item.name)
                                        )
                                    }
                                }
                            }
                        )
                    }
                } else {
                    items(grammars.size) { i ->
                        val item = grammars[i]
                        AssetRow(
                            name = item.name,
                            subtitle = if (item.builtIn) null else item.scope,
                            builtIn = item.builtIn,
                            onDelete = {
                                pendingDeleteName = item.name
                                pendingDelete = {
                                    if (EditorAssetsStore.deleteGrammar(item)) {
                                        refresh++
                                        AppToast.success(
                                            context,
                                            Str.get(R.string.editor_assets_deleted, item.name)
                                        )
                                    }
                                }
                            }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(AppDimens.spacingSmall))
            Text(
                text = Str.get(R.string.editor_assets_hint),
                style = MaterialTheme.typography.labelSmall,
                color = AppColors.textSecondary()
            )
        },
        confirmButton = {
            UnifiedDialogTextButton(onClick = { importLauncher.launch(arrayOf("*/*")) }) {
                Text(Str.get(R.string.editor_assets_import))
            }
        },
        dismissButton = {
            UnifiedDialogTextButton(onClick = onDismiss) { Text(Str.get(R.string.close)) }
        }
    )

    // 删除确认（仅用户导入的文件会出现该入口）
    if (pendingDelete != null) {
        UnifiedConfirmDialog(
            title = Str.get(R.string.editor_assets_delete_title),
            message = Str.get(R.string.editor_assets_delete_msg, pendingDeleteName),
            isDestructive = true,
            confirmText = Str.get(R.string.delete),
            dismissText = Str.get(R.string.cancel),
            onConfirm = {
                val action = pendingDelete
                pendingDelete = null
                action?.invoke()
            },
            onDismiss = { pendingDelete = null }
        )
    }

    // 导入结果：全部成功 → Toast；有失败 → 弹窗逐条列出原因。
    // Toast 与状态清空放在 LaunchedEffect 里，避免在组合期改状态。
    val result = outcome
    if (result != null && result.failed.isEmpty()) {
        LaunchedEffect(result) {
            AppToast.success(
                context,
                Str.get(
                    R.string.editor_assets_import_done,
                    result.imported.size,
                    result.deferred.size
                )
            )
            outcome = null
        }
    } else if (result != null) {
        val detail = buildString {
            append(
                Str.get(
                    R.string.editor_assets_import_done,
                    result.imported.size,
                    result.deferred.size
                )
            )
            result.failed.take(5).forEach { (name, why) ->
                append("\n• ").append(name).append("：").append(reasonText(why))
            }
            if (result.failed.size > 5) append("\n…")
        }
        UnifiedConfirmDialog(
            title = Str.get(R.string.editor_assets_import_result),
            message = detail,
            confirmText = Str.get(R.string.confirm),
            dismissText = Str.get(R.string.close),
            onConfirm = { outcome = null },
            onDismiss = { outcome = null }
        )
    }
}

/** 分页小按钮：选中态加粗 + 主色，未选中为次要色。 */
@Composable
private fun AssetTab(label: String, selected: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.primary
            else AppColors.textSecondary()
        )
    }
}

/** 列表行：名称 + 副标题（scope / 内置标记），用户文件右侧给删除按钮。 */
@Composable
private fun AssetRow(
    name: String,
    subtitle: String?,
    builtIn: Boolean,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppDimens.spacingSmall, vertical = AppDimens.spacingXSmall),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = name, style = MaterialTheme.typography.bodyMedium)
            val sub = subtitle ?: if (builtIn) Str.get(R.string.editor_assets_builtin) else null
            if (sub != null) {
                Text(
                    text = sub,
                    style = MaterialTheme.typography.labelSmall,
                    color = AppColors.textSecondary()
                )
            }
        }
        if (builtIn) {
            Text(
                text = Str.get(R.string.editor_assets_builtin),
                style = MaterialTheme.typography.labelSmall,
                color = AppColors.textSecondary(),
                modifier = Modifier.padding(start = AppDimens.spacingSmall)
            )
        } else {
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = Str.get(R.string.delete),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

/** 导入失败原因 → 用户可读文案。 */
private fun reasonText(e: EditorAssetsStore.ImportError): String = when (e) {
    EditorAssetsStore.ImportError.UNSUPPORTED ->
        Str.get(R.string.editor_assets_reason_unsupported)
    EditorAssetsStore.ImportError.READ_FAILED ->
        Str.get(R.string.editor_assets_reason_read)
    EditorAssetsStore.ImportError.INVALID ->
        Str.get(R.string.editor_assets_reason_invalid)
    EditorAssetsStore.ImportError.SCOPE_DUP ->
        Str.get(R.string.editor_assets_reason_scope_dup)
    EditorAssetsStore.ImportError.THEME_DUP ->
        Str.get(R.string.editor_assets_reason_theme_dup)
}
