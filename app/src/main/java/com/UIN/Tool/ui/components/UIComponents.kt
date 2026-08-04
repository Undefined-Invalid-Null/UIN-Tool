// app/src/main/java/com/UIN/Tool/ui/components/UIComponents.kt
package com.UIN.Tool.ui.components

import com.UIN.Tool.R
import com.UIN.Tool.utils.Str
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
import com.UIN.Tool.ui.theme.AppDimens

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
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier
                .height(AppDimens.buttonHeight)
                .defaultMinSize(minWidth = AppDimens.buttonMinWidth),
            shape = RoundedCornerShape(AppDimens.buttonCornerRadius),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            ),
            elevation = ButtonDefaults.buttonElevation(
                defaultElevation = AppDimens.buttonElevation,
                pressedElevation = AppDimens.buttonPressedElevation
            )
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
            } else {
                icon?.let {
                    Icon(it, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(AppDimens.spacingSmall))
                }
                Text(text)
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
                .height(AppDimens.buttonHeight)
                .defaultMinSize(minWidth = AppDimens.buttonMinWidth),
            shape = RoundedCornerShape(AppDimens.buttonCornerRadius),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.primary,
                disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            ),
            border = ButtonDefaults.outlinedButtonBorder(enabled)
        ) {
            icon?.let {
                Icon(it, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(AppDimens.spacingSmall))
            }
            Text(text)
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
                contentColor = MaterialTheme.colorScheme.primary,
                disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
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
                tint = tint ?: MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(24.dp)
            )
        }
    }

    // ==================== 卡片 ====================

    @Composable
    fun Card(
        modifier: Modifier = Modifier,
        onClick: (() -> Unit)? = null,
        elevation: Dp? = null,
        shape: Shape? = null,
        content: @Composable ColumnScope.() -> Unit
    ) {
        val finalElevation = elevation ?: AppDimens.cardElevation
        val finalShape = shape ?: RoundedCornerShape(AppDimens.cardCornerRadius)

        val cardModifier = modifier
            .shadow(finalElevation, finalShape)
            .clip(finalShape)
            .background(MaterialTheme.colorScheme.surface)

        val clickableModifier = if (onClick != null) {
            cardModifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
        } else cardModifier

        Surface(
            modifier = clickableModifier,
            color = MaterialTheme.colorScheme.surface,
            shape = finalShape,
            shadowElevation = 0.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(AppDimens.cardPadding),
                content = content
            )
        }
    }

    @Composable
    fun GlassCard(
        modifier: Modifier = Modifier,
        content: @Composable ColumnScope.() -> Unit
    ) {
        val shape = RoundedCornerShape(AppDimens.cardCornerRadius)
        Surface(
            modifier = modifier
                .shadow(AppDimens.cardElevation, shape)
                .clip(shape)
                .background(Color.White.copy(alpha = 0.85f)),
            color = Color.White.copy(alpha = 0.85f),
            shape = shape,
            shadowElevation = 0.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(AppDimens.cardPadding),
                content = content
            )
        }
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
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier,
            label = label?.let { { Text(it, fontSize = AppDimens.bodyTextSize.sp) } },
            placeholder = placeholder?.let { { Text(it, fontSize = AppDimens.bodyTextSize.sp) } },
            leadingIcon = leadingIcon?.let { { Icon(it, null) } },
            trailingIcon = trailingIcon?.let { { Icon(it, null) } },
            singleLine = singleLine,
            isError = isError,
            supportingText = supportingText?.let { { Text(it, fontSize = AppDimens.captionTextSize.sp) } },
            enabled = enabled,
            shape = RoundedCornerShape(AppDimens.inputCornerRadius),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                focusedLabelColor = MaterialTheme.colorScheme.primary,
                cursorColor = MaterialTheme.colorScheme.primary
            ),
            textStyle = LocalTextStyle.current.copy(fontSize = AppDimens.bodyTextSize.sp)
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
        Card(
            modifier = modifier.clickable(onClick = onClick),
            onClick = null
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(AppDimens.cardPadding),
                verticalAlignment = Alignment.CenterVertically
            ) {
                leadingContent?.invoke()
                if (leadingContent != null) Spacer(Modifier.width(AppDimens.spacingMedium))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = AppDimens.bodyTextSize.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    subtitle?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = AppDimens.captionTextSize.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                trailingContent?.invoke()
            }
        }
    }

    // ==================== 加载指示器 ====================

    @Composable
    fun FullScreenLoading(message: String = Str.get(R.string.loading)) {
        com.UIN.Tool.ui.components.unified.UnifiedLoadingIndicator(
            message = message
        )
    }

    @Composable
    fun LinearProgressIndicator(progress: Float) {
        androidx.compose.material3.LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier
                .fillMaxWidth()
                .height(AppDimens.progressHeight)
                .clip(RoundedCornerShape(AppDimens.progressCornerRadius))
        )
    }

    // ==================== 文本 ====================

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
                fontSize = AppDimens.titleTextSize.sp,
                fontWeight = FontWeight.Bold
            ),
            color = color ?: MaterialTheme.colorScheme.onSurface,
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
                fontSize = AppDimens.bodyTextSize.sp
            ),
            color = color ?: MaterialTheme.colorScheme.onSurfaceVariant,
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
                fontSize = AppDimens.captionTextSize.sp
            ),
            color = color ?: MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
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
                fontSize = AppDimens.sectionTitleTextSize.sp,
                fontWeight = FontWeight.SemiBold
            ),
            color = color ?: MaterialTheme.colorScheme.onSurface,
            modifier = modifier.padding(vertical = AppDimens.spacingSmall)
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
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = modifier,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.primary,
                checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                uncheckedThumbColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                uncheckedTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
            )
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
        AssistChip(
            onClick = onClick,
            label = {
                Text(
                    text = label,
                    fontSize = AppDimens.captionTextSize.sp
                )
            },
            modifier = modifier,
            enabled = enabled,
            leadingIcon = leadingIcon,
            colors = AssistChipDefaults.assistChipColors(
                containerColor = if (selected)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.surface,
                labelColor = if (selected)
                    MaterialTheme.colorScheme.onPrimary
                else
                    MaterialTheme.colorScheme.onSurface,
                leadingIconContentColor = if (selected)
                    MaterialTheme.colorScheme.onPrimary
                else
                    MaterialTheme.colorScheme.onSurface
            ),
            border = if (selected) null else AssistChipDefaults.assistChipBorder(
                borderColor = MaterialTheme.colorScheme.outline,
                enabled = enabled
            ),
            shape = RoundedCornerShape(AppDimens.radiusXXXLarge)
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
}