// app/src/main/java/com/UIN/Tool/ui/components/unified/UnifiedComponents.kt
package com.UIN.Tool.ui.components.unified

import com.UIN.Tool.R
import com.UIN.Tool.utils.Str
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.tween
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.BasicTextField
import com.UIN.Tool.ui.theme.AppColors
import com.UIN.Tool.ui.theme.AppDimens
import com.UIN.Tool.ui.theme.isBoldEnabled
import com.UIN.Tool.utils.UIConfig
import androidx.compose.material3.LocalContentColor

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
    iconPosition: IconPosition = IconPosition.Start,
    containerColor: Color? = null,
    contentColor: Color? = null
) {
    val (resolvedContainerColor, resolvedContentColor) = when {
        containerColor != null && contentColor != null -> containerColor to contentColor
        containerColor != null -> containerColor to MaterialTheme.colorScheme.onSurface
        contentColor != null -> MaterialTheme.colorScheme.primary to contentColor
        else -> when (variant) {
            ButtonVariant.Primary -> MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.onPrimary
            ButtonVariant.Secondary -> MaterialTheme.colorScheme.secondary to MaterialTheme.colorScheme.onSecondary
            ButtonVariant.Destructive -> MaterialTheme.colorScheme.error to MaterialTheme.colorScheme.onError
            ButtonVariant.Success -> Color(0xFF4CAF50) to Color.White
            ButtonVariant.Warning -> Color(0xFFFF9800) to Color.White
            else -> MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.onPrimary
        }
    }

    val shape = RoundedCornerShape(size.cornerRadius)
    val neu = UIConfig.isNeumorphismEnabled()
    val isDark = UIConfig.shouldUseDarkTheme()
    val glass = AppColors.glassEnabled()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val neuModifier = if (neu) {
        when (variant) {
            ButtonVariant.Outlined, ButtonVariant.Text -> {
                Modifier
            }
            else -> {
                val neuInset = UIConfig.isNeumorphismInsetEnabled()
                val currentOffset = if (isPressed) 0f else 5f
                if (neuInset && isPressed) {
                    Modifier.neuInset(shape, isDark, NeuDefaults.Intensity.MEDIUM, cornerRadius = size.cornerRadius, backgroundColor = resolvedContainerColor, offset = currentOffset)
                } else {
                    Modifier.neuRaised(shape, isDark, NeuDefaults.Intensity.MEDIUM, cornerRadius = size.cornerRadius, backgroundColor = resolvedContainerColor, offset = currentOffset)
                }
            }
        }
    } else Modifier

    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier
            .then(neuModifier)
            .defaultMinSize(minHeight = size.height, minWidth = size.minWidth),
        shape = shape,
        interactionSource = interactionSource,
        border = if (variant == ButtonVariant.Outlined) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        } else {
            null
        },
        colors = when (variant) {
            ButtonVariant.Outlined -> ButtonDefaults.outlinedButtonColors(
                contentColor = if (contentColor != null) resolvedContentColor else MaterialTheme.colorScheme.primary
            )
            ButtonVariant.Text -> ButtonDefaults.textButtonColors(
                contentColor = if (contentColor != null) resolvedContentColor else MaterialTheme.colorScheme.primary
            )
            else -> ButtonDefaults.buttonColors(
                containerColor = resolvedContainerColor,
                contentColor = resolvedContentColor,
                disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            )
        },
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp,
            hoveredElevation = 0.dp,
            focusedElevation = 0.dp
        )
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = when (variant) {
                    ButtonVariant.Outlined, ButtonVariant.Text -> MaterialTheme.colorScheme.primary
                    else -> resolvedContentColor
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
                    fontWeight = FontWeight.Medium,
                    maxLines = Int.MAX_VALUE,
                    softWrap = true,
                    overflow = TextOverflow.Ellipsis
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
    containerColor: Color? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val glass = AppColors.glassEnabled()
    val neu = UIConfig.isNeumorphismEnabled()
    val isDark = UIConfig.shouldUseDarkTheme()

    val finalModifier = modifier
        .then(
            if (neu) {
                val surfaceColor = containerColor ?: MaterialTheme.colorScheme.surface
                val bgColor = if (glass) surfaceColor.copy(alpha = AppColors.translucentAlpha()) else surfaceColor
                val neuInset = UIConfig.isNeumorphismInsetEnabled()
                Modifier.then(
                        if (neuInset && isPressed) {
                            Modifier.neuInset(shape, isDark, NeuDefaults.Intensity.MEDIUM, cornerRadius = AppDimens.cardCornerRadius, backgroundColor = Color.Transparent)
                        } else {
                            Modifier.neuRaised(shape, isDark, NeuDefaults.Intensity.MEDIUM, cornerRadius = AppDimens.cardCornerRadius, backgroundColor = Color.Transparent)
                        }
                    )
                    .clip(shape)
                    .background(bgColor, shape)
            } else {
                Modifier.clip(shape)
                    .then(
                        if (!glass) Modifier.background(containerColor ?: MaterialTheme.colorScheme.surface, shape) else Modifier
                    )
            }
        )
        .then(
            if (onClick != null) {
                Modifier.clickable(
                    interactionSource = interactionSource,
                    indication = LocalIndication.current,
                    onClick = onClick
                )
            } else {
                Modifier
            }
        )

    when (variant) {
        CardVariant.Elevated -> {
            Card(
                modifier = finalModifier,
                border = null,
                colors = CardDefaults.cardColors(
                    containerColor = containerColor
                        ?: if (glass) AppColors.glassBackground() else MaterialTheme.colorScheme.surface
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
        CardVariant.Filled -> {
            Card(
                modifier = finalModifier,
                border = null,
                colors = CardDefaults.cardColors(
                    containerColor = containerColor
                        ?: if (glass) AppColors.glassBackground() else MaterialTheme.colorScheme.surfaceVariant
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
                border = null,
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
    supportingText: String? = null,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    onTrailingIconClick: (() -> Unit)? = null,
    containerColor: Color? = null
) {
    val neu = UIConfig.isNeumorphismEnabled()
    val isDark = UIConfig.shouldUseDarkTheme()
    var passwordVisible by remember { mutableStateOf(!isPassword) }

    val inputShape = RoundedCornerShape(AppDimens.inputCornerRadius)

    if (neu) {
        val surfaceColor = containerColor ?: MaterialTheme.colorScheme.background
        val glass = AppColors.glassEnabled()
        val bgColor = if (containerColor != null) containerColor else if (glass) AppColors.glassBackground() else surfaceColor
        Box(
            modifier = modifier
                .neuInset(RoundedCornerShape(AppDimens.inputCornerRadius), isDark, NeuDefaults.Intensity.MEDIUM, cornerRadius = AppDimens.inputCornerRadius, backgroundColor = bgColor)
                .background(bgColor, RoundedCornerShape(AppDimens.inputCornerRadius))
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (leadingIcon != null) {
                    Icon(leadingIcon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.weight(1f),
                    textStyle = TextStyle(color = Color(0xFF333333), fontSize = 15.sp),
                    singleLine = singleLine,
                    maxLines = if (singleLine) 1 else Int.MAX_VALUE,
                    enabled = enabled,
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    visualTransformation = if (isPassword && !passwordVisible) PasswordVisualTransformation() else VisualTransformation.None,
                    decorationBox = { innerTextField ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (value.isEmpty() && placeholder != null) {
                                Text(placeholder, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 15.sp)
                            }
                            innerTextField()
                        }
                    }
                )
                if (isPassword) {
                    IconButton(onClick = { passwordVisible = !passwordVisible }, modifier = Modifier.size(28.dp)) {
                        Icon(
                            if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                if (showClearButton && value.isNotEmpty()) {
                    IconButton(onClick = { onValueChange("") }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(20.dp))
                    }
                }
                trailingIcon?.let {
                    IconButton(onClick = { onTrailingIconClick?.invoke() }, modifier = Modifier.size(28.dp)) {
                        Icon(it, contentDescription = null, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
        if (error != null || supportingText != null) {
            Text(
                error ?: supportingText ?: "",
                color = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp)
            )
        }
    } else {
        val glass = AppColors.glassEnabled()
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
            supportingText = error?.let { { Text(it) } } ?: supportingText?.let { { Text(it) } },
            enabled = enabled,
            singleLine = singleLine,
            shape = RoundedCornerShape(AppDimens.inputCornerRadius),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                focusedLabelColor = MaterialTheme.colorScheme.primary,
                cursorColor = MaterialTheme.colorScheme.primary,
                focusedContainerColor = containerColor ?: if (glass) AppColors.glassBackground() else MaterialTheme.colorScheme.background,
                unfocusedContainerColor = containerColor ?: if (glass) AppColors.glassBackground() else MaterialTheme.colorScheme.background
            )
        )
    }
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
            fontWeight = if (isBoldEnabled()) FontWeight.Bold else FontWeight.SemiBold
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
            fontWeight = if (isBoldEnabled()) FontWeight.SemiBold else FontWeight.Medium
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
    val neu = UIConfig.isNeumorphismEnabled()
    val isDark = UIConfig.shouldUseDarkTheme()

    val glass = AppColors.glassEnabled()
    if (neu) {
        val isToggleOn = remember { mutableStateOf(checked) }
        LaunchedEffect(checked) { isToggleOn.value = checked }

        val trackColor by animateColorAsState(
            targetValue = if (checked) MaterialTheme.colorScheme.primary
            else if (AppColors.glassEnabled()) AppColors.glassBackground() else MaterialTheme.colorScheme.surfaceVariant,
            animationSpec = tween(NeuDefaults.animationDuration()),
            label = "switch_track"
        )
        val thumbColor by animateColorAsState(
            targetValue = if (checked) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            animationSpec = tween(NeuDefaults.animationDuration()),
            label = "switch_thumb"
        )
        val thumbOffset by animateFloatAsState(
            targetValue = if (checked) 24f else 0f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium
            ),
            label = "switch_offset"
        )

        Box(
            modifier = modifier
                .size(54.dp, 30.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(trackColor, RoundedCornerShape(15.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = enabled,
                    onClick = { onCheckedChange(!checked) }
                ),
            contentAlignment = Alignment.CenterStart
        ) {
            Box(
                modifier = Modifier
                    .offset(x = thumbOffset.dp)
                    .padding(3.dp)
                    .size(24.dp)
                    .background(thumbColor, CircleShape)
            )
        }
    } else {
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
    val neu = UIConfig.isNeumorphismEnabled()
    val isDark = UIConfig.shouldUseDarkTheme()

    if (neu) {
        val bgColor = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
        else if (AppColors.glassEnabled()) AppColors.glassBackground() else MaterialTheme.colorScheme.surface
        val textColor = if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurfaceVariant
        Box(
            modifier = modifier
                .then(
                    if (!selected) Modifier.neuRaised(
                        RoundedCornerShape(AppDimens.radiusXXXLarge),
                        isDark, NeuDefaults.Intensity.LIGHT,
                        AppDimens.radiusXXXLarge,
                        backgroundColor = Color.Transparent
                    ) else Modifier
                )
                .clip(RoundedCornerShape(AppDimens.radiusXXXLarge))
                .background(bgColor)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = LocalIndication.current,
                    onClick = onClick
                )
                .padding(horizontal = 12.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (leadingIcon != null) {
                    Box(contentAlignment = Alignment.Center) { leadingIcon() }
                }
                Text(
                    text = label,
                    fontSize = AppDimens.captionTextSize.sp,
                    color = textColor
                )
            }
        }
    } else {
        AssistChip(
            onClick = onClick,
            label = { Text(text = label, fontSize = AppDimens.captionTextSize.sp) },
            modifier = modifier,
            enabled = enabled,
            leadingIcon = leadingIcon,
            colors = AssistChipDefaults.assistChipColors(
                containerColor = if (selected) MaterialTheme.colorScheme.primary else if (AppColors.glassEnabled()) AppColors.glassBackground() else MaterialTheme.colorScheme.surface,
                labelColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                leadingIconContentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
            ),
            border = if (selected) null else AssistChipDefaults.assistChipBorder(
                borderColor = MaterialTheme.colorScheme.outline,
                enabled = enabled
            ),
            shape = RoundedCornerShape(AppDimens.radiusXXXLarge)
        )
    }
}

// ==================== 统一滑块 ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnifiedSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    enabled: Boolean = true
) {
    val isDark = UIConfig.shouldUseDarkTheme()
    val neu = UIConfig.isNeumorphismEnabled()
    val surfaceColor = if (AppColors.glassEnabled()) AppColors.glassBackground() else MaterialTheme.colorScheme.surface
    val primaryColor = MaterialTheme.colorScheme.primary

    Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = valueRange,
        enabled = enabled,
        modifier = modifier,
        colors = SliderDefaults.colors(
            thumbColor = primaryColor,
            activeTrackColor = primaryColor,
            inactiveTrackColor = surfaceColor
        ),
        thumb = {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .background(primaryColor, CircleShape)
            )
        },
        track = { sliderState ->
            Box(modifier = Modifier.height(8.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .then(
                            if (neu) Modifier.neuInset(RoundedCornerShape(4.dp), isDark, NeuDefaults.Intensity.LIGHT, backgroundColor = surfaceColor)
                            else Modifier.background(surfaceColor, RoundedCornerShape(4.dp))
                        )
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth((sliderState.value - valueRange.start) / (valueRange.endInclusive - valueRange.start))
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(primaryColor)
                )
            }
        }
    )
}

// ==================== 统一加载指示器 ====================

@Composable
fun UnifiedLinearProgressIndicator(
    progress: Float,
    modifier: Modifier = Modifier
) {
    androidx.compose.material3.LinearProgressIndicator(
        progress = { progress.coerceIn(0f, 1f) },
        color = MaterialTheme.colorScheme.primary,
        trackColor = MaterialTheme.colorScheme.outlineVariant,
        modifier = modifier
            .fillMaxWidth()
            .height(AppDimens.progressHeight)
            .clip(RoundedCornerShape(AppDimens.progressCornerRadius))
    )
}

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

// ==================== 统一图标按钮 ====================

@Composable
fun UnifiedIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color? = null,
    contentDescription: String? = null
) {
    val neu = UIConfig.isNeumorphismEnabled()
    val isDark = UIConfig.shouldUseDarkTheme()

    if (neu) {
        val glass = AppColors.glassEnabled()
        val surfaceColor = if (glass) MaterialTheme.colorScheme.surface.copy(alpha = AppColors.translucentAlpha()) else MaterialTheme.colorScheme.surface
        Box(
            modifier = modifier
                .neuRaised(CircleShape, isDark, NeuDefaults.Intensity.LIGHT, cornerRadius = 20.dp, backgroundColor = Color.Transparent)
                .clip(CircleShape)
                .background(surfaceColor, CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = LocalIndication.current,
                    onClick = onClick
                )
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription, modifier = Modifier.size(24.dp), tint = tint ?: LocalContentColor.current)
        }
    } else {
        androidx.compose.material3.IconButton(
            onClick = onClick,
            modifier = modifier
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = tint ?: LocalContentColor.current,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

// ==================== 统一列表项 ====================

@Composable
fun UnifiedListItem(
    leadingContent: @Composable (() -> Unit)? = null,
    title: String,
    subtitle: String? = null,
    trailingContent: @Composable (() -> Unit)? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    UnifiedCard(
        modifier = modifier,
        onClick = onClick,
        variant = CardVariant.Elevated
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            leadingContent?.let {
                it()
                Spacer(modifier = Modifier.width(AppDimens.spacingMedium))
            }
            Column(modifier = Modifier.weight(1f)) {
                UnifiedBodyText(
                    text = title,
                    color = MaterialTheme.colorScheme.onSurface
                )
                subtitle?.let {
                    Spacer(modifier = Modifier.height(AppDimens.spacingXSmall))
                    UnifiedCaptionText(text = it)
                }
            }
            trailingContent?.invoke()
        }
    }
}