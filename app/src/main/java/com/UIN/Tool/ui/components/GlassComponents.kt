// app/src/main/java/com/UIN/Tool/ui/components/GlassComponents.kt
package com.UIN.Tool.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.UIN.Tool.ui.theme.AppDimens

// ==================== 玻璃卡片 ====================

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(AppDimens.cardCornerRadius)
    
    Surface(
        modifier = modifier
            .clip(shape)
            .clickable(enabled = onClick != null) { onClick?.invoke() },
        color = Color.White.copy(alpha = 0.85f),
        shadowElevation = AppDimens.cardElevation,
        shape = shape
    ) {
        Column(
            modifier = Modifier.padding(AppDimens.cardPadding),
            content = content
        )
    }
}

// ==================== 玻璃按钮 ====================

@Composable
fun GlassButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    text: String? = null,
    icon: ImageVector? = null,
    colors: GlassButtonColors = GlassButtonColors.default()
) {
    val shape = RoundedCornerShape(AppDimens.buttonCornerRadius)
    
    Surface(
        modifier = modifier
            .clip(shape)
            .clickable(enabled = enabled) { onClick() },
        color = colors.backgroundColor ?: Color.White.copy(alpha = 0.85f),
        shadowElevation = AppDimens.buttonElevation,
        shape = shape
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = AppDimens.spacingXLarge, vertical = AppDimens.spacingLarge),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = colors.contentColor ?: Color(0xFF1A1A1A),
                    modifier = Modifier.size(AppDimens.iconMedium)
                )
                if (text != null) Spacer(modifier = Modifier.width(AppDimens.spacingSmall))
            }
            if (text != null) {
                Text(
                    text = text,
                    color = colors.contentColor ?: Color(0xFF1A1A1A),
                    fontSize = AppDimens.bodyTextSize.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

data class GlassButtonColors(
    val backgroundColor: Color? = null,
    val contentColor: Color? = null
) {
    companion object {
        fun default() = GlassButtonColors(
            backgroundColor = Color.White.copy(alpha = 0.85f),
            contentColor = Color(0xFF1A1A1A)
        )
        
        fun primary() = GlassButtonColors(
            backgroundColor = Color(0xFF1A1A1A).copy(alpha = 0.9f),
            contentColor = Color.White
        )
        
        fun secondary() = GlassButtonColors(
            backgroundColor = Color.White.copy(alpha = 0.5f),
            contentColor = Color(0xFF1A1A1A)
        )
    }
}

// ==================== 玻璃输入框 ====================

@Composable
fun GlassTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    leadingIcon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    singleLine: Boolean = true,
    isError: Boolean = false
) {
    val shape = RoundedCornerShape(AppDimens.inputCornerRadius)
    
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .clip(shape)
            .background(
                Color.White.copy(alpha = 0.5f),
                shape
            ),
        label = label?.let { { Text(it) } },
        placeholder = placeholder?.let { { Text(it) } },
        leadingIcon = leadingIcon?.let { { Icon(it, null) } },
        trailingIcon = trailingIcon?.let { { Icon(it, null) } },
        singleLine = singleLine,
        isError = isError,
        shape = shape,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Color(0xFF1A1A1A).copy(alpha = 0.5f),
            unfocusedBorderColor = Color(0xFF999999).copy(alpha = 0.3f),
            focusedLabelColor = Color(0xFF1A1A1A),
            unfocusedLabelColor = Color(0xFF666666),
            cursorColor = Color(0xFF1A1A1A)
        )
    )
}

// ==================== 玻璃对话框 ====================

@Composable
fun GlassDialog(
    onDismissRequest: () -> Unit,
    properties: DialogProperties = DialogProperties(
        usePlatformDefaultWidth = false,
        decorFitsSystemWindows = false
    ),
    content: @Composable () -> Unit
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = properties
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .wrapContentSize(Alignment.Center)
        ) {
            AnimatedVisibility(
                visible = true,
                enter = scaleIn(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                ) + fadeIn(),
                exit = scaleOut() + fadeOut()
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth(AppDimens.dialogWidthFraction)
                        .wrapContentHeight()
                        .clip(RoundedCornerShape(AppDimens.dialogCornerRadius))
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.95f),
                                    Color.White.copy(alpha = 0.8f)
                                )
                            )
                        )
                        .border(
                            width = AppDimens.dividerThickness,
                            color = Color.White.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(AppDimens.dialogCornerRadius)
                        ),
                    color = Color.Transparent,
                    shadowElevation = AppDimens.dialogElevation,
                    shape = RoundedCornerShape(AppDimens.dialogCornerRadius)
                ) {
                    content()
                }
            }
        }
    }
}

// ==================== 玻璃确认弹窗 ====================

@Composable
fun GlassConfirmDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmText: String = "确定",
    dismissText: String = "取消"
) {
    GlassDialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppDimens.dialogPadding)
        ) {
            Text(
                text = title,
                fontSize = AppDimens.titleTextSize.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1A1A1A)
            )
            
            Spacer(modifier = Modifier.height(AppDimens.spacingMedium))
            
            Text(
                text = message,
                fontSize = AppDimens.bodyTextSize.sp,
                color = Color(0xFF666666),
                lineHeight = 20.sp
            )
            
            Spacer(modifier = Modifier.height(AppDimens.spacingXLarge))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) {
                    Text(dismissText, color = Color(0xFF666666))
                }
                
                Spacer(modifier = Modifier.width(AppDimens.spacingSmall))
                
                Button(
                    onClick = onConfirm,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF1A1A1A),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(AppDimens.radiusLarge)
                ) {
                    Text(confirmText)
                }
            }
        }
    }
}

// ==================== 玻璃底部弹窗 ====================

@Composable
fun GlassBottomSheet(
    onDismissRequest: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .wrapContentSize(Alignment.BottomCenter)
        ) {
            AnimatedVisibility(
                visible = true,
                enter = slideInVertically(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessMedium
                    ),
                    initialOffsetY = { it }
                ) + fadeIn(),
                exit = slideOutVertically() + fadeOut()
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(topStart = AppDimens.radiusXXLarge, topEnd = AppDimens.radiusXXLarge))
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.95f),
                                    Color.White.copy(alpha = 0.85f)
                                )
                            )
                        )
                        .border(
                            width = AppDimens.dividerThickness,
                            color = Color.White.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(topStart = AppDimens.radiusXXLarge, topEnd = AppDimens.radiusXXLarge)
                        ),
                    color = Color.Transparent,
                    shadowElevation = AppDimens.cardElevation,
                    shape = RoundedCornerShape(topStart = AppDimens.radiusXXLarge, topEnd = AppDimens.radiusXXLarge)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(AppDimens.spacingXLarge)
                            .padding(bottom = AppDimens.spacingXXLarge),
                        content = content
                    )
                }
            }
        }
    }
}

// ==================== 玻璃加载指示器 ====================

@Composable
fun GlassLoadingIndicator(
    message: String = "加载中..."
) {
    GlassDialog(onDismissRequest = {}) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppDimens.spacingXXXLarge),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(48.dp),
                color = Color(0xFF1A1A1A),
                strokeWidth = 3.dp
            )
            
            Spacer(modifier = Modifier.height(AppDimens.spacingXLarge))
            
            Text(
                text = message,
                fontSize = AppDimens.bodyTextSize.sp,
                color = Color(0xFF666666)
            )
        }
    }
}

// ==================== 玻璃列表项 ====================

@Composable
fun GlassListItem(
    modifier: Modifier = Modifier,
    leadingContent: @Composable (() -> Unit)? = null,
    title: String,
    subtitle: String? = null,
    trailingContent: @Composable (() -> Unit)? = null,
    onClick: () -> Unit
) {
    GlassCard(
        modifier = modifier.clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            leadingContent?.invoke()
            
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = if (leadingContent != null) AppDimens.spacingLarge else 0.dp)
            ) {
                Text(
                    text = title,
                    fontSize = AppDimens.bodyTextSize.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF1A1A1A)
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        fontSize = AppDimens.captionTextSize.sp,
                        color = Color(0xFF666666)
                    )
                }
            }
            
            trailingContent?.invoke()
        }
    }
}

// ==================== 玻璃页面容器 ====================

@Composable
fun GlassScreen(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFF5F5F5),
                        Color(0xFFEEEEEE)
                    )
                )
            )
    ) {
        content()
    }
}