// app/src/main/java/com/UIN/Tool/ui/components/UIComponents.kt
package com.UIN.Tool.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

// 原来重构前的颜色定义
object UITheme {
    // 主色调 - 蓝灰色系（重构前）
    val Primary = Color(0xFF1A3A4A)
    val PrimaryDark = Color(0xFF0F2838)
    val PrimaryLight = Color(0xFF2D5A70)
    val Accent = Color(0xFF4A8A9E)
    
    // 辅助色
    val Success = Color(0xFF4CAF50)
    val Warning = Color(0xFFFF9800)
    val Error = Color(0xFFF44336)
    val Info = Color(0xFF2196F3)
    
    // 文本颜色
    val TextPrimary = Color(0xFF212121)
    val TextSecondary = Color(0xFF757575)
    val TextHint = Color(0xFFBDBDBD)
    
    // 背景色
    val Background = Color(0xFFF5F7FA)
    val Surface = Color(0xFFFFFFFF)
    val SurfaceVariant = Color(0xFFF5F7FA)
    
    // 边框和分割线
    val Divider = Color(0xFFE0E4E8)
    
    // 卡片阴影
    val CardShadow = Color(0x1A000000)
    
    // 间距
    val SpacingSmall = 4.dp
    val SpacingMedium = 8.dp
    val SpacingLarge = 16.dp
    val SpacingXLarge = 24.dp
    
    // 圆角
    val RadiusSmall = 8.dp
    val RadiusMedium = 12.dp
    val RadiusLarge = 16.dp
    val RadiusXLarge = 24.dp
}

object UIComponents {

    // ======================== 按钮 ========================

    @Composable
    fun PrimaryButton(
        text: String,
        onClick: () -> Unit,
        modifier: Modifier = Modifier,
        enabled: Boolean = true,
        icon: ImageVector? = null,
        loading: Boolean = false
    ) {
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier
                .height(48.dp)
                .defaultMinSize(minWidth = 80.dp),
            shape = RoundedCornerShape(UITheme.RadiusLarge),
            colors = ButtonDefaults.buttonColors(
                containerColor = UITheme.Primary,
                contentColor = Color.White,
                disabledContainerColor = UITheme.TextHint,
                disabledContentColor = Color.White.copy(alpha = 0.5f)
            ),
            elevation = ButtonDefaults.buttonElevation(
                defaultElevation = 2.dp
            )
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
            } else {
                icon?.let {
                    Icon(it, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(UITheme.SpacingSmall))
                }
                Text(
                    text = text,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 14.sp
                    )
                )
            }
        }
    }

    @Composable
    fun SecondaryButton(
        text: String,
        onClick: () -> Unit,
        modifier: Modifier = Modifier,
        enabled: Boolean = true,
        icon: ImageVector? = null
    ) {
        OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier
                .height(48.dp)
                .defaultMinSize(minWidth = 80.dp),
            shape = RoundedCornerShape(UITheme.RadiusLarge),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = UITheme.Primary,
                disabledContentColor = UITheme.TextHint
            ),
            border = ButtonDefaults.outlinedButtonBorder(enabled)
        ) {
            icon?.let {
                Icon(it, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(UITheme.SpacingSmall))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 14.sp
                )
            )
        }
    }

    @Composable
    fun TextButton(
        text: String,
        onClick: () -> Unit,
        modifier: Modifier = Modifier,
        enabled: Boolean = true,
        textStyle: TextStyle = MaterialTheme.typography.titleMedium
    ) {
        androidx.compose.material3.TextButton(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier,
            colors = ButtonDefaults.textButtonColors(
                contentColor = UITheme.Primary,
                disabledContentColor = UITheme.TextHint
            )
        ) {
            Text(
                text = text,
                style = textStyle.copy(fontSize = 14.sp)
            )
        }
    }

    @Composable
    fun IconButton(
        icon: ImageVector,
        onClick: () -> Unit,
        modifier: Modifier = Modifier,
        tint: Color? = null,
        contentDescription: String? = null
    ) {
        androidx.compose.material3.IconButton(
            onClick = onClick,
            modifier = modifier
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = tint ?: UITheme.TextPrimary,
                modifier = Modifier.size(24.dp)
            )
        }
    }

    // ======================== 卡片 ========================

    @Composable
    fun Card(
        modifier: Modifier = Modifier,
        onClick: (() -> Unit)? = null,
        elevation: Dp? = null,
        shape: Shape? = null,
        content: @Composable ColumnScope.() -> Unit
    ) {
        val finalElevation = elevation ?: 4.dp
        val finalShape = shape ?: RoundedCornerShape(UITheme.RadiusLarge)
        val cardModifier = modifier
            .shadow(finalElevation, finalShape)
            .clip(finalShape)
            .background(UITheme.Surface)

        val clickableModifier = if (onClick != null) {
            cardModifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
        } else cardModifier

        Surface(
            modifier = clickableModifier,
            color = UITheme.Surface,
            shape = finalShape,
            shadowElevation = 0.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(UITheme.SpacingLarge),
                content = content
            )
        }
    }

    @Composable
    fun GlassCard(
        modifier: Modifier = Modifier,
        content: @Composable ColumnScope.() -> Unit
    ) {
        val shape = RoundedCornerShape(UITheme.RadiusLarge)
        Surface(
            modifier = modifier
                .shadow(4.dp, shape)
                .clip(shape)
                .background(Color.White.copy(alpha = 0.85f)),
            color = Color.White.copy(alpha = 0.85f),
            shape = shape,
            shadowElevation = 0.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(UITheme.SpacingLarge),
                content = content
            )
        }
    }

    // ======================== 输入框 ========================

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
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier,
            label = label?.let { { Text(it, fontSize = 14.sp) } },
            placeholder = placeholder?.let { { Text(it, fontSize = 14.sp) } },
            leadingIcon = leadingIcon?.let { { Icon(it, null) } },
            trailingIcon = trailingIcon?.let { { Icon(it, null) } },
            singleLine = singleLine,
            isError = isError,
            supportingText = supportingText?.let { { Text(it, fontSize = 12.sp) } },
            enabled = enabled,
            shape = RoundedCornerShape(UITheme.RadiusMedium),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = UITheme.Primary,
                unfocusedBorderColor = UITheme.Divider,
                focusedLabelColor = UITheme.Primary,
                cursorColor = UITheme.Primary
            ),
            textStyle = LocalTextStyle.current.copy(fontSize = 14.sp)
        )
    }

    // ======================== 对话框 ========================

    @Composable
    fun ConfirmDialog(
        title: String,
        message: String,
        onConfirm: () -> Unit,
        onDismiss: () -> Unit,
        confirmText: String = "确定",
        dismissText: String = "取消"
    ) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(title, style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp)) },
            text = { Text(message, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp)) },
            confirmButton = {
                PrimaryButton(
                    text = confirmText,
                    onClick = onConfirm,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            dismissButton = {
                TextButton(
                    text = dismissText,
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            containerColor = UITheme.Surface,
            shape = RoundedCornerShape(UITheme.RadiusXLarge)
        )
    }

    @Composable
    fun LoadingDialog(
        message: String,
        onCancel: (() -> Unit)? = null
    ) {
        Dialog(
            onDismissRequest = { onCancel?.invoke() },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            Surface(
                shape = RoundedCornerShape(UITheme.RadiusXLarge),
                color = UITheme.Surface,
                shadowElevation = 4.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(UITheme.SpacingXLarge),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(color = UITheme.Primary)
                    Spacer(Modifier.height(UITheme.SpacingMedium))
                    Text(message, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp))
                    if (onCancel != null) {
                        Spacer(Modifier.height(UITheme.SpacingMedium))
                        TextButton(
                            text = "取消",
                            onClick = onCancel,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }

    // ======================== 列表项 ========================

    @Composable
    fun ListItem(
        leadingContent: @Composable (() -> Unit)? = null,
        title: String,
        subtitle: String? = null,
        trailingContent: @Composable (() -> Unit)? = null,
        onClick: () -> Unit,
        modifier: Modifier = Modifier
    ) {
        Card(
            modifier = modifier.clickable(onClick = onClick),
            onClick = null
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(UITheme.SpacingLarge),
                verticalAlignment = Alignment.CenterVertically
            ) {
                leadingContent?.invoke()
                if (leadingContent != null) Spacer(Modifier.width(UITheme.SpacingMedium))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = UITheme.TextPrimary
                    )
                    subtitle?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 13.sp
                            ),
                            color = UITheme.TextSecondary
                        )
                    }
                }
                trailingContent?.invoke()
            }
        }
    }

    // ======================== 加载指示器 ========================

    @Composable
    fun FullScreenLoading(message: String = "加载中...") {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = UITheme.Primary)
                Spacer(Modifier.height(UITheme.SpacingMedium))
                Text(message, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp))
            }
        }
    }

    @Composable
    fun LinearProgressIndicator(progress: Float) {
        androidx.compose.material3.LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            color = UITheme.Primary,
            trackColor = UITheme.Divider,
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
        )
    }

    // ======================== 文本 ========================

    @Composable
    fun TitleText(
        text: String,
        modifier: Modifier = Modifier,
        color: Color? = null,
        textAlign: TextAlign? = null
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            ),
            color = color ?: UITheme.Primary,
            modifier = modifier,
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
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 14.sp
            ),
            color = color ?: UITheme.TextSecondary,
            modifier = modifier,
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
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 12.sp
            ),
            color = color ?: UITheme.TextHint,
            modifier = modifier,
            textAlign = textAlign
        )
    }

    @Composable
    fun SectionTitle(
        text: String,
        modifier: Modifier = Modifier,
        color: Color? = null
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.headlineSmall.copy(
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            ),
            color = color ?: UITheme.TextPrimary,
            modifier = modifier.padding(vertical = UITheme.SpacingSmall)
        )
    }

    // ======================== 开关 ========================

    @Composable
    fun ToggleSwitch(
        checked: Boolean,
        onCheckedChange: (Boolean) -> Unit,
        modifier: Modifier = Modifier,
        enabled: Boolean = true
    ) {
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = modifier,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = UITheme.Primary,
                checkedTrackColor = UITheme.PrimaryLight,
                uncheckedThumbColor = UITheme.TextHint,
                uncheckedTrackColor = UITheme.Divider
            )
        )
    }

    // ======================== 标签（Chip）- 白色背景 ========================

    @Composable
    fun Chip(
        label: String,
        selected: Boolean,
        onClick: () -> Unit,
        modifier: Modifier = Modifier,
        enabled: Boolean = true,
        leadingIcon: @Composable (() -> Unit)? = null
    ) {
        AssistChip(
            onClick = onClick,
            label = { 
                Text(
                    text = label,
                    fontSize = 13.sp
                ) 
            },
            modifier = modifier,
            enabled = enabled,
            leadingIcon = leadingIcon,
            colors = AssistChipDefaults.assistChipColors(
                containerColor = if (selected) UITheme.Primary else UITheme.Surface,  // 白色背景
                labelColor = if (selected) Color.White else UITheme.TextPrimary,
                leadingIconContentColor = if (selected) Color.White else UITheme.TextPrimary
            ),
            border = if (selected) null else AssistChipDefaults.assistChipBorder(
                borderColor = UITheme.Divider,
                enabled = enabled
            ),
            shape = RoundedCornerShape(UITheme.RadiusLarge)
        )
    }

    // ======================== 空状态 ========================

    @Composable
    fun EmptyState(
        title: String,
        description: String? = null,
        icon: ImageVector = Icons.Default.Info,
        modifier: Modifier = Modifier
    ) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = UITheme.TextHint
            )
            Spacer(modifier = Modifier.height(16.dp))
            TitleText(
                text = title,
                textAlign = TextAlign.Center
            )
            if (!description.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                BodyText(
                    text = description,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}