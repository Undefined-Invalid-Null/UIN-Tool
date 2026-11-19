// app/src/main/java/com/UIN/Tool/ui/screen/files/FileTasksScreen.kt
package com.UIN.Tool.ui.screen.files

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.UIN.Tool.ui.theme.AppDimens
import com.UIN.Tool.R
import com.UIN.Tool.ui.components.unified.UnifiedCard
import com.UIN.Tool.ui.components.unified.ButtonVariant
import com.UIN.Tool.ui.components.unified.ManageTopAppBar
import com.UIN.Tool.ui.components.unified.UnifiedButton
import com.UIN.Tool.ui.theme.AppColors
import com.UIN.Tool.utils.Str
import com.UIN.Tool.utils.formatFileSize

/**
 * 后台任务页（g18）。
 *
 * 原先只有侧边栏里一个弹窗 —— 弹窗一关就只剩顶部一条小胶囊，用户想确认
 * 「到底还在不在跑、跑到哪了」得再点开一次。这里改成**整页**：
 *
 * - 顶部带返回键，返回只关页面，任务照跑（与回收站/远程页同一套整页替换）；
 * - 每条任务一张卡：进度条 + 已完成/总数 + 字节 + 当前文件 + 源/目标路径；
 * - 「查看进度」把七层进度框唤回（同一份状态，不另起一套）；
 * - 「取消」直接落到 [FileManageViewModel.cancelOp]，按钮按下去立即变「正在取消…」。
 *
 * 当前 VM 同一时刻只跑一条操作（`opJob` 只有一个），所以列表最多一行；
 * 结构仍按「多条」排好，将来要并发时只换数据源。
 */
@Composable
fun FileTasksScreen(
    op: OpUiState.Running?,
    onBack: () -> Unit,
    onShow: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.pageBackground())
    ) {
        // k.txt 第 7 条：后台任务页与文件页/开发页/管理页统一顶栏。
        ManageTopAppBar(
            titleText = Str.get(R.string.files_tasks_title),
            // 文件页嵌在 MainActivity Scaffold 内，状态栏已由外层让出。
            windowInsets = WindowInsets(0, 0, 0, 0),
            onBack = onBack
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = Str.get(R.string.files_tasks_page_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (op == null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 64.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(AppDimens.cardSpacing)
                ) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                    Text(
                        text = Str.get(R.string.files_tasks_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                TaskCard(
                    op = op,
                    onShow = onShow,
                    onCancel = onCancel
                )
            }
        }
    }
}

@Composable
private fun TaskCard(
    op: OpUiState.Running,
    onShow: () -> Unit,
    onCancel: () -> Unit
) {
    val p = op.progress
    // l.txt 第 15 条：任务卡片改用统一卡片组件 —— 开启新拟态时自动浮起，
    // 圆角/内边距/深浅色与文件管理器其它卡片同一套口径。
    UnifiedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(AppDimens.cardSpacing)
        ) {
            Text(
                text = progressTitle(p),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            // 总数未知（-1 / 0）时用不定长进度条 —— 与进度框口径一致
            if (p.totalCount > 0) {
                LinearProgressIndicator(
                    progress = {
                        (p.doneCount.toFloat() / p.totalCount.toFloat()).coerceIn(0f, 1f)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                )
            } else {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                )
            }

            Text(
                text = if (p.totalCount > 0) {
                    Str.get(
                        R.string.files_tasks_detail,
                        p.doneCount.toString(),
                        p.totalCount.toString()
                    )
                } else {
                    Str.get(R.string.files_tasks_detail, p.doneCount.toString(), "?")
                } + if (p.totalBytes > 0) {
                    " · " + formatFileSize(p.doneBytes) + " / " + formatFileSize(p.totalBytes)
                } else if (p.doneBytes > 0) {
                    " · " + formatFileSize(p.doneBytes)
                } else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (p.currentName.isNotEmpty()) {
                TaskPathLine(label = null, value = p.currentName)
            }
            if (p.sourcePath.isNotEmpty()) {
                TaskPathLine(
                    label = Str.get(R.string.files_tasks_source),
                    value = p.sourcePath
                )
            }
            if (p.targetPath.isNotEmpty()) {
                TaskPathLine(
                    label = Str.get(R.string.files_tasks_target),
                    value = p.targetPath
                )
            }

            Spacer(modifier = Modifier.height(2.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                UnifiedButton(
                    text = Str.get(
                        if (op.cancelling) R.string.files_op_cancelling
                        else R.string.files_op_cancel
                    ),
                    onClick = onCancel,
                    enabled = !op.cancelling,
                    variant = ButtonVariant.Text
                )
                Spacer(modifier = Modifier.weight(1f))
                UnifiedButton(
                    text = Str.get(R.string.files_tasks_show),
                    onClick = onShow,
                    variant = ButtonVariant.Outlined
                )
            }
        }
    }
}

@Composable
private fun TaskPathLine(label: String?, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (label != null) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(52.dp)
            )
        }
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.MiddleEllipsis
        )
    }
}
