// app/src/main/java/com/UIN/Tool/ui/components/unified/UnifiedProgressDialog.kt
package com.UIN.Tool.ui.components.unified

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.UIN.Tool.R
import com.UIN.Tool.ui.components.unified.UnifiedIconButton
import com.UIN.Tool.ui.theme.AppDimens
import com.UIN.Tool.ui.theme.dialogBackgroundOf
import com.UIN.Tool.utils.Str
import com.UIN.Tool.utils.UIConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * 七层进度对话框要显示的**全部数据**（MT.md §4.12）。
 *
 * 这里刻意不依赖任何业务枚举（复制/压缩/解压…），调用方自己把标签翻成字符串塞进来 ——
 * 这样文件操作（M3）、压缩解压（M4d）、复制到缓存（M5）都能复用同一个控件。
 */
data class UnifiedProgressState(
    /** 第 1 层：操作标签（复制 / 移动 / 删除 / 压缩 …），右上角「隐藏」旁边。 */
    val opLabel: String,
    /** 第 2 层：标题「正在复制 3 项…」。 */
    val title: String,
    /** 第 3 层：当前文件名（单行、中间截断）。 */
    val currentName: String = "",
    /** 第 4 层：源 / 目标两行路径。 */
    val sourcePath: String = "",
    val targetPath: String = "",
    /** 第 5/6 层的两套口径：字节已知用字节，否则用条目数，都没有就不显示进度条。 */
    val totalCount: Int = 0,
    val doneCount: Int = 0,
    val totalBytes: Long = -1L,
    val doneBytes: Long = 0L,
    /** 正在跑（false = 已收尾，速度行停算）。 */
    val running: Boolean = true,
    /**
     * 用户已经按过取消，正在等底层收尾（f19）。
     *
     * 取消是协程级的，单个大文件是一整段阻塞 IO，插不进取消点 —— 这段时间按钮必须
     * 变成「正在取消…」并禁用，否则按下去像没反应，用户只能反复点。
     */
    val cancelling: Boolean = false
) {
    /** 字节总量已知且 > 0 才走字节口径（第 5/6 层的第一种形态）。 */
    val hasBytes: Boolean get() = totalBytes > 0L

    /** 连字节带条目都不知道 → 不显示进度条（§4.12 第 5 条「皆无不显示」）。 */
    val showBar: Boolean get() = hasBytes || totalCount > 0

    /** 已知总量时的百分比 0..1；未知返回 -1。 */
    val fraction: Float
        get() = when {
            hasBytes -> (doneBytes.toFloat() / totalBytes).coerceIn(0f, 1f)
            totalCount > 0 -> (doneCount.toFloat() / totalCount).coerceIn(0f, 1f)
            else -> -1f
        }
}

/**
 * 统一进度对话框（MT.md §4.12「七层信息 + 必须可取消」）。
 *
 * 七层自上而下：① 操作标签 + 右上「隐藏」 ② 标题 ③ 当前文件名 ④ 源/目标两行
 * ⑤ 进度条（已知字节按字节、只知条目按条目、皆无不显示）⑥ 统计行 ⑦ 速度行。
 *
 * - **`取消` 常驻且一定可点** —— 修正参考实现「只能隐藏不能中止」的硬伤（§4.14）；
 * - `隐藏` 只把对话框收起来，**不转常驻通知**（问题 12 已拍板）；调用方自己负责
 *   给一个「有任务进行中」的入口把对话框再唤回来；
 * - 速度行按约 400ms 刷新、**移动平均**（取最近 2 秒窗口），避免数字跳得让人看不清；
 * - 统计与速度里的 `剩 …` 用移动平均速度反推，速度未知时这半句直接不显示。
 */
@Composable
fun UnifiedProgressDialog(
    state: UnifiedProgressState,
    onCancel: () -> Unit,
    onDismissRequest: () -> Unit
) {
    val dark = UIConfig.shouldUseDarkTheme()

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    ) {
        val dialogView = androidx.compose.ui.platform.LocalView.current
        androidx.compose.runtime.SideEffect {
            (dialogView.context as? android.app.Dialog)?.window?.setDimAmount(0f)
        }
        AnimatedVisibility(visible = true, enter = fadeIn(), exit = fadeOut()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    // 0.5 → 0.25（用户反馈 c15：遮罩太黑，底下列表看不清）
                    .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier
                        // e22：与内容型弹窗同一口径（0.92 + 宽度上限）——
                        // 原先这里是 0.9，比别的弹窗窄一截，一排弹窗来回开就看得出跳动。
                        .widthIn(max = AppDimens.dialogMaxWidth)
                        .fillMaxWidth(AppDimens.dialogWidthFraction)
                        .wrapContentHeight()
                        .then(
                            if (UIConfig.isNeumorphismEnabled()) {
                                Modifier.neuRaised(
                                    RoundedCornerShape(AppDimens.dialogCornerRadius),
                                    dark,
                                    NeuDefaults.Intensity.LIGHT,
                                    cornerRadius = AppDimens.dialogCornerRadius,
                                    backgroundColor = androidx.compose.ui.graphics.Color.Transparent
                                )
                            } else Modifier
                        )
                        .then(Modifier.dialogBackgroundOf(RoundedCornerShape(AppDimens.dialogCornerRadius))),
                    shape = RoundedCornerShape(AppDimens.dialogCornerRadius),
                    color = androidx.compose.ui.graphics.Color.Transparent,
                    shadowElevation = 0.dp
                ) {
                    Column(modifier = Modifier.padding(AppDimens.dialogPadding)) {

                        // ==================== ① 操作标签 + 隐藏 ====================
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = state.opLabel,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            UnifiedIconButton(
                                icon = Icons.Default.Close,
                                onClick = onDismissRequest,
                                contentDescription = Str.get(R.string.files_op_hide),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                iconSize = 18.dp,
                            )
                        }

                        // ==================== ② 标题 ====================
                        Text(
                            text = state.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        // ==================== ③ 当前文件名（中间截断）====================
                        if (state.currentName.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(AppDimens.spacingSmall))
                            Text(
                                text = middleEllipsize(state.currentName, 46),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // ==================== ④ 源 / 目标两行 ====================
                        if (state.sourcePath.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(AppDimens.spacingSmall))
                            PathLine(Str.get(R.string.files_op_source), state.sourcePath)
                        }
                        if (state.targetPath.isNotEmpty()) {
                            PathLine(Str.get(R.string.files_op_target), state.targetPath)
                        }

                        // ==================== ⑤ 进度条 ====================
                        if (state.showBar) {
                            Spacer(modifier = Modifier.height(AppDimens.spacingMedium))
                            if (state.fraction >= 0f) {
                                LinearProgressIndicator(
                                    progress = { state.fraction },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                )
                            } else {
                                // 总量未知：跑马灯（仍属「显示进度」，只是给不出百分比）
                                LinearProgressIndicator(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                )
                            }
                        }

                        // ==================== ⑥⑦ 统计行 + 速度行 ====================
                        ProgressStats(state)

                        Spacer(modifier = Modifier.height(AppDimens.spacingMedium))

                        // ==================== 取消（必须可点）====================
                        UnifiedButton(
                            text = if (state.cancelling) Str.get(R.string.files_op_cancelling)
                            else Str.get(R.string.files_op_cancel),
                            onClick = onCancel,
                            variant = ButtonVariant.Outlined,
                            enabled = !state.cancelling,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

// ==================== 内部小件 ====================

/** 「源：…」/「目标：…」单行，路径中间截断（两端保留，中间省略）。 */
@Composable
private fun PathLine(label: String, path: String) {
    Text(
        text = "$label ${middleEllipsize(path, 52)}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

/**
 * 第 ⑥⑦ 层：统计行 + 速度行。
 *
 * 速度在这儿自己采样算（约 400ms 一次、最近 2 秒移动平均），调用方不必关心刷新节奏 ——
 * 业务层只管喂 [UnifiedProgressState.doneBytes] 的累计值。
 */
@Composable
private fun ProgressStats(state: UnifiedProgressState) {
    // 采样窗口：(时间戳, 累计字节)。字节口径才知道速度，条目口径不算。
    var samples by remember { mutableStateOf(listOf<Pair<Long, Long>>()) }
    var speed by remember { mutableStateOf(0L) }

    LaunchedEffect(state.doneBytes, state.hasBytes, state.running) {
        if (!state.hasBytes) {
            samples = emptyList()
            speed = 0L
            return@LaunchedEffect
        }
        while (isActive && state.running) {
            delay(400)
            val now = System.currentTimeMillis()
            val trimmed = (samples + (now to state.doneBytes))
                .filter { now - it.first <= 2000L }
            samples = trimmed
            if (trimmed.size >= 2) {
                val (t0, b0) = trimmed.first()
                val (t1, b1) = trimmed.last()
                val dt = (t1 - t0).coerceAtLeast(1L)
                speed = ((b1 - b0) * 1000L) / dt
            }
        }
    }

    // ⑥ 统计行
    val statsText = when {
        state.hasBytes -> {
            val percent = (state.fraction * 100).toInt()
            val remain = (state.totalBytes - state.doneBytes).coerceAtLeast(0L)
            Str.get(
                R.string.files_op_stats_bytes,
                percent.toString(),
                FileOpsSize(state.doneBytes),
                FileOpsSize(state.totalBytes),
                FileOpsSize(remain)
            )
        }

        state.totalCount > 0 -> Str.get(
            R.string.files_op_stats_items,
            state.doneCount.toString(),
            state.totalCount.toString(),
            ((state.doneCount.toFloat() / state.totalCount) * 100).toInt().toString()
        )

        else -> ""
    }
    if (statsText.isNotEmpty()) {
        Spacer(modifier = Modifier.height(AppDimens.spacingSmall))
        Text(
            text = statsText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }

    // ⑦ 速度行：速度未知时整句不显示，避免出现「· 剩 0s」这种噪音
    if (state.hasBytes && speed > 0L) {
        val remainMs = if (speed > 0L) {
            ((state.totalBytes - state.doneBytes).coerceAtLeast(0L) * 1000L) / speed
        } else 0L
        Text(
            text = Str.get(
                R.string.files_op_speed,
                FileOpsSize(speed),
                formatDuration(remainMs)
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 路径/文件名**中间截断**：保留头尾，中间用省略号。
 * 比 `TextOverflow.Ellipsis` 的尾截断更适合路径 —— 尾巴的扩展名和开头的目录名都还看得见。
 */
fun middleEllipsize(text: String, max: Int): String {
    if (text.length <= max) return text
    if (max <= 1) return "…"
    val head = (max + 1) / 2
    val tail = max - head - 1
    return text.take(head) + "…" + if (tail > 0) text.takeLast(tail) else ""
}

/** 剩余时长：`12s` / `3m` / `1h 5m`；算不出来给 `—`。 */
private fun formatDuration(ms: Long): String {
    if (ms <= 0L) return "—"
    val totalSeconds = ms / 1000L
    return when {
        totalSeconds < 60L -> Str.get(R.string.files_op_duration_s, totalSeconds.toString())
        totalSeconds < 3600L -> Str.get(
            R.string.files_op_duration_m,
            (totalSeconds / 60L).toString(),
            (totalSeconds % 60L).toString()
        )

        else -> Str.get(
            R.string.files_op_duration_h,
            (totalSeconds / 3600L).toString(),
            ((totalSeconds % 3600L) / 60L).toString()
        )
    }
}

/** 统一走 [com.UIN.Tool.utils.formatFileSize] 的口径，避免三处大小文案不一致。 */
@Composable
private fun FileOpsSize(bytes: Long): String = com.UIN.Tool.utils.formatFileSize(bytes)
