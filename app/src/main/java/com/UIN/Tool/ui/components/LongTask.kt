package com.UIN.Tool.ui.components

import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.UIN.Tool.R
import com.UIN.Tool.ui.components.unified.UnifiedCard
import com.UIN.Tool.ui.components.unified.UnifiedCaptionText
import com.UIN.Tool.ui.components.unified.UnifiedLinearProgressIndicator
import com.UIN.Tool.ui.theme.AppDimens
import com.UIN.Tool.utils.Str
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * F-16（方案B）：统一长任务框架。
 *
 * - 业务屏通过 [LongTaskRegistry] 的 begin/update/finish/fail 推进状态；
 * - 全局 [LongTaskHost]（挂在 MainActivity 根部）统一展示模态进度/终态；
 * - 单活跃任务：begin 覆盖旧任务；所有更新按 key 匹配，过期任务的晚到更新被忽略；
 * - progress = null 表示不定长。
 */
data class LongTask(
    val key: String,
    val title: String,
    val detail: String? = null,
    val progress: Float? = null,
    val error: String? = null,
    val done: Boolean = false,
    val cancellable: Boolean = false,
    val onCancel: (() -> Unit)? = null,
)

object LongTaskRegistry {
    private val _current = MutableStateFlow<LongTask?>(null)
    val current: StateFlow<LongTask?> = _current.asStateFlow()

    fun begin(
        key: String,
        title: String,
        cancellable: Boolean = false,
        onCancel: (() -> Unit)? = null
    ) {
        _current.value = LongTask(key, title, cancellable = cancellable, onCancel = onCancel)
    }

    /** 仅当活跃任务的 key 匹配时更新（detail 必填，progress null=不定长）。 */
    fun update(key: String, detail: String, progress: Float?) {
        _current.update { cur ->
            if (cur?.key == key) cur.copy(detail = detail, progress = progress) else cur
        }
    }

    /** 只更新文案，进度保持不变。 */
    fun updateDetail(key: String, detail: String) {
        _current.update { cur -> if (cur?.key == key) cur.copy(detail = detail) else cur }
    }

    fun finish(key: String) {
        _current.update { cur ->
            if (cur?.key == key) cur.copy(done = true, progress = 1f) else cur
        }
    }

    fun fail(key: String, error: String) {
        _current.update { cur ->
            if (cur?.key == key) cur.copy(error = error, progress = null) else cur
        }
    }

    fun dismiss() {
        _current.value = null
    }

    fun isActive(key: String): Boolean {
        val cur = _current.value ?: return false
        return cur.key == key && !cur.done && cur.error == null
    }
}

/**
 * 全局长任务宿主：模态遮罩 + 居中卡片。成功终态 1.5s 自动收起，
 * 失败/进行中常驻（失败可关闭，进行中可按 cancellable 取消）。
 */
@Composable
fun LongTaskHost() {
    val task by LongTaskRegistry.current.collectAsState()
    val t = task ?: return

    if (t.done) {
        LaunchedEffect(t.key) {
            delay(1500)
            LongTaskRegistry.dismiss()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.35f))
            // 模态：Initial pass 消费全部指针事件，阻断底层交互
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        event.changes.forEach { it.consume() }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        UnifiedCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = t.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = AppDimens.bodyTextSize.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (t.progress == null) {
                    androidx.compose.material3.LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(AppDimens.progressHeight)
                            .clip(RoundedCornerShape(AppDimens.progressCornerRadius)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.outlineVariant
                    )
                } else {
                    UnifiedLinearProgressIndicator(progress = t.progress)
                }
                t.detail?.let { UnifiedCaptionText(it) }
                t.error?.let {
                    Text(
                        text = it,
                        fontSize = AppDimens.captionTextSize.sp,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    when {
                        t.error != null || t.done -> TextButton(onClick = { LongTaskRegistry.dismiss() }) {
                            Text(Str.get(R.string.close))
                        }
                        t.cancellable && t.onCancel != null -> TextButton(onClick = { t.onCancel.invoke() }) {
                            Text(Str.get(R.string.cancel))
                        }
                    }
                }
            }
        }
    }
}
