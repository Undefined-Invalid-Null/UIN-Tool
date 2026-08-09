// app/src/main/java/com/UIN/Tool/ui/components/UIComponents.kt
package com.UIN.Tool.ui.components

import com.UIN.Tool.R
import com.UIN.Tool.utils.Str
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.UIN.Tool.ui.theme.AppColors
import com.UIN.Tool.ui.theme.AppDimens
import com.UIN.Tool.ui.theme.isBoldEnabled
import com.UIN.Tool.ui.components.unified.ButtonSize
import com.UIN.Tool.ui.components.unified.ButtonVariant
import com.UIN.Tool.ui.components.unified.CardVariant
import com.UIN.Tool.ui.components.unified.UnifiedBodyText
import com.UIN.Tool.ui.components.unified.UnifiedButton
import com.UIN.Tool.ui.components.unified.UnifiedCaptionText
import com.UIN.Tool.ui.components.unified.UnifiedCard
import com.UIN.Tool.ui.components.unified.UnifiedChip
import com.UIN.Tool.ui.components.unified.UnifiedConfirmDialog
import com.UIN.Tool.ui.components.unified.UnifiedEmptyState
import com.UIN.Tool.ui.components.unified.UnifiedIconButton
import com.UIN.Tool.ui.components.unified.UnifiedInfoDialog
import com.UIN.Tool.ui.components.unified.UnifiedLinearProgressIndicator
import com.UIN.Tool.ui.components.unified.UnifiedListItem
import com.UIN.Tool.ui.components.unified.UnifiedLoadingDialog
import com.UIN.Tool.ui.components.unified.UnifiedLoadingIndicator
import com.UIN.Tool.ui.components.unified.UnifiedSectionTitle
import com.UIN.Tool.ui.components.unified.UnifiedSwitch
import com.UIN.Tool.ui.components.unified.UnifiedTextField
import com.UIN.Tool.ui.components.unified.UnifiedTitleText
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

private fun Modifier.glassSheen(): Modifier = drawBehind {
    val w = size.width
    val h = size.height
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color.White.copy(alpha = 0.28f), Color.White.copy(alpha = 0f)),
            radius = w * 0.55f
        ),
        radius = w * 0.55f,
        center = Offset(w * 0.06f, h * 0.04f)
    )
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color(0xFFD6E4FF).copy(alpha = 0.14f), Color.Transparent),
            radius = w * 0.5f
        ),
        radius = w * 0.5f,
        center = Offset(w * 0.96f, h * 0.94f)
    )
}

object UIComponents {

    // ==================== 按钮 ====================

    @Composable
    fun PrimaryButton(
        text: String,
        onClick: () -> Unit,
        modifier: Modifier = Modifier,
        enabled: Boolean = true,
        icon: ImageVector? = null,
        loading: Boolean = false
    ) {
        UnifiedButton(
            text = text,
            onClick = onClick,
            modifier = modifier,
            variant = ButtonVariant.Primary,
            size = ButtonSize.Medium,
            enabled = enabled,
            loading = loading,
            icon = icon
        )
    }

    @Composable
    fun SecondaryButton(
        text: String,
        onClick: () -> Unit,
        modifier: Modifier = Modifier,
        enabled: Boolean = true,
        icon: ImageVector? = null
    ) {
        UnifiedButton(
            text = text,
            onClick = onClick,
            modifier = modifier,
            variant = ButtonVariant.Outlined,
            size = ButtonSize.Medium,
            enabled = enabled,
            icon = icon
        )
    }

    @Composable
    fun TextButton(
        text: String,
        onClick: () -> Unit,
        modifier: Modifier = Modifier,
        enabled: Boolean = true,
        textStyle: TextStyle = MaterialTheme.typography.titleMedium
    ) {
        UnifiedButton(
            text = text,
            onClick = onClick,
            modifier = modifier,
            variant = ButtonVariant.Text,
            size = ButtonSize.Medium,
            enabled = enabled
        )
    }

    @Composable
    fun IconButton(
        icon: ImageVector,
        onClick: () -> Unit,
        modifier: Modifier = Modifier,
        tint: Color? = null,
        contentDescription: String? = null
    ) {
        UnifiedIconButton(
            icon = icon,
            onClick = onClick,
            modifier = modifier,
            tint = tint,
            contentDescription = contentDescription
        )
    }

    // ==================== 卡片 ====================

    // ==================== 管理页顶部栏 ====================

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun ManageTopAppBar(
        titleText: String,
        onBack: (() -> Unit)? = null,
        actions: @Composable RowScope.() -> Unit = {}
    ) {
        val container = AppColors.pageBackground()
        val content = MaterialTheme.colorScheme.onBackground
        TopAppBar(
            title = {
                Text(titleText, color = content)
            },
            navigationIcon = {
                if (onBack != null) {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = Str.get(R.string.back),
                            tint = content
                        )
                    }
                }
            },
            actions = {
                CompositionLocalProvider(LocalContentColor provides content) {
                    actions()
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = container,
                titleContentColor = content,
                navigationIconContentColor = content,
                actionIconContentColor = content
            )
        )
    }

    @Composable
    fun Card(
        modifier: Modifier = Modifier,
        onClick: (() -> Unit)? = null,
        elevation: Dp? = null,
        shape: Shape? = null,
        content: @Composable ColumnScope.() -> Unit
    ) {
        UnifiedCard(
            modifier = modifier,
            onClick = onClick,
            variant = CardVariant.Elevated,
            shape = shape ?: RoundedCornerShape(AppDimens.cardCornerRadius),
            elevation = elevation ?: AppDimens.cardElevation,
            content = content
        )
    }

    @Composable
    fun GlassCard(
        modifier: Modifier = Modifier,
        content: @Composable ColumnScope.() -> Unit
    ) {
        UnifiedCard(
            modifier = modifier,
            variant = CardVariant.Glass,
            content = content
        )
    }

    // ==================== 输入框 ====================

    @Composable
    fun TextInput(
        value: String,
        onValueChange: (String) -> Unit,
        modifier: Modifier = Modifier,
        label: String? = null,
        placeholder: String? = null,
        leadingIcon: ImageVector? = null,
        trailingIcon: ImageVector? = null,
        singleLine: Boolean = true,
        isError: Boolean = false,
        supportingText: String? = null,
        enabled: Boolean = true
    ) {
        UnifiedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier,
            label = label,
            placeholder = placeholder,
            leadingIcon = leadingIcon,
            trailingIcon = trailingIcon,
            singleLine = singleLine,
            error = if (isError) supportingText else null,
            supportingText = if (isError) null else supportingText,
            enabled = enabled
        )
    }

    // ==================== 对话框 ====================

    @Composable
    fun ConfirmDialog(
        title: String,
        message: String,
        onConfirm: () -> Unit,
        onDismiss: () -> Unit,
        confirmText: String = Str.get(R.string.ok_2),
        dismissText: String = Str.get(R.string.cancel),
        isDestructive: Boolean = false
    ) {
        com.UIN.Tool.ui.components.unified.UnifiedConfirmDialog(
            title = title,
            message = message,
            onConfirm = onConfirm,
            onDismiss = onDismiss,
            confirmText = confirmText,
            dismissText = dismissText,
            isDestructive = isDestructive
        )
    }

    @Composable
    fun InfoDialog(
        title: String,
        message: String,
        onDismiss: () -> Unit,
        buttonText: String = Str.get(R.string.ok_2)
    ) {
        com.UIN.Tool.ui.components.unified.UnifiedInfoDialog(
            title = title,
            message = message,
            onDismiss = onDismiss,
            buttonText = buttonText
        )
    }

    @Composable
    fun LoadingDialog(
        message: String,
        onCancel: (() -> Unit)? = null
    ) {
        com.UIN.Tool.ui.components.unified.UnifiedLoadingDialog(
            message = message,
            onCancel = onCancel
        )
    }

    // ==================== 列表项 ====================

    @Composable
    fun ListItem(
        leadingContent: @Composable (() -> Unit)? = null,
        title: String,
        subtitle: String? = null,
        trailingContent: @Composable (() -> Unit)? = null,
        onClick: () -> Unit,
        modifier: Modifier = Modifier
    ) {
        UnifiedListItem(
            leadingContent = leadingContent,
            title = title,
            subtitle = subtitle,
            trailingContent = trailingContent,
            onClick = onClick,
            modifier = modifier
        )
    }

    // ==================== 加载指示器 ====================

    @Composable
    fun FullScreenLoading(message: String = Str.get(R.string.loading)) {
        UnifiedLoadingIndicator(message = message)
    }

    @Composable
    fun LinearProgressIndicator(progress: Float) {
        UnifiedLinearProgressIndicator(progress = progress)
    }

    // ==================== 文本 ====================

    @Composable
    fun TitleText(
        text: String,
        modifier: Modifier = Modifier,
        color: Color? = null,
        textAlign: TextAlign? = null
    ) {
        UnifiedTitleText(
            text = text,
            modifier = modifier,
            color = color,
            textAlign = textAlign
        )
    }

    @Composable
    fun BodyText(
        text: String,
        modifier: Modifier = Modifier,
        color: Color? = null,
        textAlign: TextAlign? = null
    ) {
        UnifiedBodyText(
            text = text,
            modifier = modifier,
            color = color,
            textAlign = textAlign
        )
    }

    @Composable
    fun CaptionText(
        text: String,
        modifier: Modifier = Modifier,
        color: Color? = null,
        textAlign: TextAlign? = null
    ) {
        UnifiedCaptionText(
            text = text,
            modifier = modifier,
            color = color,
            textAlign = textAlign
        )
    }

    @Composable
    fun SectionTitle(
        text: String,
        modifier: Modifier = Modifier,
        color: Color? = null
    ) {
        UnifiedSectionTitle(
            text = text,
            modifier = modifier.padding(vertical = AppDimens.spacingSmall),
            color = color
        )
    }

    // ==================== 开关 ====================

    @Composable
    fun ToggleSwitch(
        checked: Boolean,
        onCheckedChange: (Boolean) -> Unit,
        modifier: Modifier = Modifier,
        enabled: Boolean = true
    ) {
        UnifiedSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = modifier,
            enabled = enabled
        )
    }

    // ==================== 标签 ====================

    @Composable
    fun Chip(
        label: String,
        selected: Boolean,
        onClick: () -> Unit,
        modifier: Modifier = Modifier,
        enabled: Boolean = true,
        leadingIcon: @Composable (() -> Unit)? = null
    ) {
        UnifiedChip(
            label = label,
            selected = selected,
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            leadingIcon = leadingIcon
        )
    }

    // ==================== 空状态 ====================

    @Composable
    fun EmptyState(
        title: String,
        description: String? = null,
        icon: ImageVector = Icons.Default.Info,
        modifier: Modifier = Modifier
    ) {
        com.UIN.Tool.ui.components.unified.UnifiedEmptyState(
            title = title,
            description = description,
            icon = icon,
            modifier = modifier
        )
    }

    /**
     * 当前时间字符串（HH:mm），用于“上次更新”展示
     */
    fun currentTimeString(): String =
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

    /**
     * “上次更新: HH:mm” 说明文字，显示时淡入、1 秒后淡出
     */
    @Composable
    fun LastUpdatedCaption(time: String?, modifier: Modifier = Modifier) {
        var visible by remember { mutableStateOf(false) }
        LaunchedEffect(time) {
            if (time != null) {
                visible = true
                delay(1000)
                visible = false
            } else {
                visible = false
            }
        }
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(animationSpec = tween(300)),
            exit = fadeOut(animationSpec = tween(400)),
            modifier = modifier
        ) {
            CaptionText(text = Str.get(R.string.last_update_time, time ?: ""))
        }
    }

    /**
     * 下拉刷新指示器：使用主题色替换 Material 默认紫色背景
     */
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun PullRefreshIndicator(
        isRefreshing: Boolean,
        state: PullToRefreshState,
        modifier: Modifier = Modifier
    ) {
        PullToRefreshDefaults.Indicator(
            state = state,
            isRefreshing = isRefreshing,
            modifier = modifier,
            containerColor = MaterialTheme.colorScheme.surface,
            color = MaterialTheme.colorScheme.primary
        )
    }

    /**
     * 骨架屏：插件/仓库列表加载占位，用呼吸闪烁的灰色卡片替代整屏转圈
     */
    @Composable
    fun PluginListSkeleton(
        itemCount: Int = 5,
        modifier: Modifier = Modifier
    ) {
        val transition = rememberInfiniteTransition(label = "skeleton")
        val pulse by transition.animateFloat(
            initialValue = 0.35f,
            targetValue = 0.75f,
            animationSpec = infiniteRepeatable(
                animation = tween(700),
                repeatMode = RepeatMode.Reverse
            ),
            label = "skeleton_alpha"
        )
        Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            repeat(itemCount) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(76.dp),
                    shape = RoundedCornerShape(AppDimens.cardCornerRadius),
                    border = null,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(AppDimens.radiusMedium))
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = pulse)
                                )
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.6f)
                                    .height(14.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = pulse)
                                    )
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.35f)
                                    .height(12.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = pulse)
                                    )
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .height(32.dp)
                                .clip(RoundedCornerShape(AppDimens.radiusMedium))
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = pulse)
                                )
                        )
                    }
                }
            }
        }
    }
}