// app/src/main/java/com/UIN/Tool/ui/components/unified/UnifiedComponents.kt
package com.UIN.Tool.ui.components.unified

import com.UIN.Tool.R
import com.UIN.Tool.utils.Str
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip          // ✅ 添加这行
import androidx.compose.ui.draw.shadow      // ✅ 添加这行
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.UIN.Tool.ui.theme.AppColors
import com.UIN.Tool.ui.theme.AppDimens

enum class ButtonVariant {
    Primary,
    Secondary,
    Outlined,
    Text,
    Destructive,
    Success,
    Warning
}

enum class ButtonSize(
    val height: Dp,
    val minWidth: Dp,
    val cornerRadius: Dp,
    val fontSize: Float
) {
    Small(
        height = 32.dp,
        minWidth = 64.dp,
        cornerRadius = 8.dp,
        fontSize = 12f
    ),
    Medium(
        height = 44.dp,
        minWidth = 80.dp,
        cornerRadius = AppDimens.buttonCornerRadius,
        fontSize = 14f
    ),
    Large(
        height = 56.dp,
        minWidth = 100.dp,
        cornerRadius = 16.dp,
        fontSize = 16f
    )
}

enum class IconPosition {
    Start,
    End
}

// ==================== 统一按钮 ====================

@Composable
fun UnifiedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ButtonVariant = ButtonVariant.Primary,
    size: ButtonSize = ButtonSize.Medium,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: ImageVector? = null,
    iconPosition: IconPosition = IconPosition.Start
) {
    val (containerColor, contentColor) = when (variant) {
        ButtonVariant.Primary -> MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.onPrimary
        ButtonVariant.Secondary -> MaterialTheme.colorScheme.secondary to MaterialTheme.colorScheme.onSecondary
        ButtonVariant.Destructive -> MaterialTheme.colorScheme.error to MaterialTheme.colorScheme.onError
        ButtonVariant.Success -> Color(0xFF4CAF50) to Color.White
        ButtonVariant.Warning -> Color(0xFFFF9800) to Color.White
        else -> MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.onPrimary
    }
    
    val shape = RoundedCornerShape(size.cornerRadius)
    
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier
            .height(size.height)
            .defaultMinSize(minWidth = size.minWidth),
        shape = shape,
        colors = when (variant) {
            ButtonVariant.Outlined -> ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.primary
            )
            ButtonVariant.Text -> ButtonDefaults.textButtonColors(
                contentColor = MaterialTheme.colorScheme.primary
            )
            else -> ButtonDefaults.buttonColors(
                containerColor = containerColor,
                contentColor = contentColor,
                disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            )
        },
        elevation = when (variant) {
            ButtonVariant.Outlined, ButtonVariant.Text -> ButtonDefaults.buttonElevation(
                defaultElevation = 0.dp
            )
            else -> ButtonDefaults.buttonElevation(
                defaultElevation = 2.dp,
                pressedElevation = 4.dp
            )
        }
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = when (variant) {
                    ButtonVariant.Outlined, ButtonVariant.Text -> MaterialTheme.colorScheme.primary
                    else -> contentColor
                },
                strokeWidth = 2.dp
            )
        } else {
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (icon != null && iconPosition == IconPosition.Start) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(AppDimens.spacingSmall))
                }
                Text(
                    text = text,
                    fontSize = size.fontSize.sp,
                    fontWeight = FontWeight.Medium
                )
                if (icon != null && iconPosition == IconPosition.End) {
                    Spacer(modifier = Modifier.width(AppDimens.spacingSmall))
                    Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

// ==================== 统一卡片 ====================

enum class CardVariant {
    Elevated,
    Filled,
    Outlined,
    Glass
}

@Composable
fun UnifiedCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    variant: CardVariant = CardVariant.Elevated,
    shape: Shape = RoundedCornerShape(AppDimens.cardCornerRadius),
    elevation: Dp = AppDimens.cardElevation,
    content: @Composable ColumnScope.() -> Unit
) {
    val finalModifier = modifier
        .clip(shape)
        .border(1.dp, AppColors.glassBorder(), shape)
        .then(
            if (onClick != null) {
                Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = LocalIndication.current,
                    onClick = onClick
                )
            } else {
                Modifier
            }
        )

    val glass = AppColors.glassEnabled()

    when (variant) {
        CardVariant.Elevated -> {
            Card(
                modifier = finalModifier,
                colors = CardDefaults.cardColors(
                    containerColor = if (glass) AppColors.glassBackground() else MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = elevation),
                shape = shape
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(AppDimens.cardPadding),
                    content = content
                )
            }
        }
        CardVariant.Filled -> {
            Card(
                modifier = finalModifier,
                colors = CardDefaults.cardColors(
                    containerColor = if (glass) AppColors.glassBackground() else MaterialTheme.colorScheme.surfaceVariant
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                shape = shape
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(AppDimens.cardPadding),
                    content = content
                )
            }
        }
        CardVariant.Outlined -> {
            Card(
                modifier = finalModifier,
                colors = CardDefaults.cardColors(
                    containerColor = Color.Transparent
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                shape = shape
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(AppDimens.cardPadding),
                    content = content
                )
            }
        }
        CardVariant.Glass -> {
            Surface(
                modifier = finalModifier
                    .background(AppColors.glassBackground()),
                color = Color.Transparent,
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
    }
}

// ==================== 统一输入框 ====================

@Composable
fun UnifiedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    leadingIcon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    isPassword: Boolean = false,
    showClearButton: Boolean = false,
    error: String? = null,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    onTrailingIconClick: (() -> Unit)? = null
) {
    var passwordVisible by remember { mutableStateOf(!isPassword) }
    
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = label?.let { { Text(it) } },
        placeholder = placeholder?.let { { Text(it) } },
        leadingIcon = leadingIcon?.let { { Icon(it, contentDescription = null) } },
        trailingIcon = {
            Row {
                if (isPassword) {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (passwordVisible) Str.get(R.string.hide_password) else Str.get(R.string.show_password)
                        )
                    }
                }
                if (showClearButton && value.isNotEmpty()) {
                    IconButton(onClick = { onValueChange("") }) {
                        Icon(Icons.Default.Clear, contentDescription = Str.get(R.string.clear_2))
                    }
                }
                trailingIcon?.let {
                    IconButton(onClick = { onTrailingIconClick?.invoke() }) {
                        Icon(it, contentDescription = null)
                    }
                }
            }
        },
        visualTransformation = if (isPassword && !passwordVisible) {
            PasswordVisualTransformation()
        } else {
            VisualTransformation.None
        },
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        enabled = enabled,
        singleLine = singleLine,
        shape = RoundedCornerShape(AppDimens.inputCornerRadius),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            focusedLabelColor = MaterialTheme.colorScheme.primary,
            cursorColor = MaterialTheme.colorScheme.primary
        )
    )
}

// ==================== 统一文本 ====================

@Composable
fun UnifiedTitleText(
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
fun UnifiedBodyText(
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
fun UnifiedCaptionText(
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
fun UnifiedSectionTitle(
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
        modifier = modifier
    )
}

// ==================== 统一开关 ====================

@Composable
fun UnifiedSwitch(
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

// ==================== 统一标签 ====================

@Composable
fun UnifiedChip(
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

// ==================== 统一加载指示器 ====================

@Composable
fun UnifiedLoadingIndicator(
    modifier: Modifier = Modifier,
    message: String? = null
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary
            )
            message?.let {
                Spacer(modifier = Modifier.height(AppDimens.spacingMedium))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ==================== 统一空状态 ====================

@Composable
fun UnifiedEmptyState(
    title: String,
    description: String? = null,
    icon: ImageVector = Icons.Default.Info,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(AppDimens.spacingXXXLarge),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(AppDimens.iconXXLarge),
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        )
        Spacer(modifier = Modifier.height(AppDimens.spacingXLarge))
        UnifiedTitleText(
            text = title,
            textAlign = TextAlign.Center
        )
        if (!description.isNullOrEmpty()) {
            Spacer(modifier = Modifier.height(AppDimens.spacingMedium))
            UnifiedBodyText(
                text = description,
                textAlign = TextAlign.Center
            )
        }
        if (actionText != null && onAction != null) {
            Spacer(modifier = Modifier.height(AppDimens.spacingXXLarge))
            UnifiedButton(
                text = actionText,
                onClick = onAction,
                variant = ButtonVariant.Primary
            )
        }
    }
}