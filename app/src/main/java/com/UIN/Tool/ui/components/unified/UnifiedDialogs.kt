// app/src/main/java/com/UIN/Tool/ui/components/unified/UnifiedDialogs.kt
package com.UIN.Tool.ui.components.unified

import com.UIN.Tool.R
import com.UIN.Tool.utils.Str
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.UIN.Tool.ui.theme.AppDimens
import com.UIN.Tool.ui.theme.dialogBackgroundOf

// ==================== 统一弹窗文本按钮（带描边） ====================

/**
 * 弹窗内的「透明」文本按钮：默认无底色，但带一圈描边，
 * 让按钮在加深的弹窗背景上仍清晰可辨。
 */
@Composable
fun UnifiedDialogTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentColor: Color = MaterialTheme.colorScheme.primary,
    content: @Composable RowScope.() -> Unit
) {
    TextButton(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(AppDimens.buttonCornerRadius),
        colors = ButtonDefaults.textButtonColors(contentColor = contentColor),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        content()
    }
}

// ==================== 动画常量 - 纯淡入淡出 ====================

private val FadeInAnimation = fadeIn(
    animationSpec = tween(
        durationMillis = 250,
        easing = FastOutSlowInEasing
    )
)

private val FadeOutAnimation = fadeOut(
    animationSpec = tween(
        durationMillis = 200,
        easing = FastOutSlowInEasing
    )
)

private val DialogEnterTransition = FadeInAnimation
private val DialogExitTransition = FadeOutAnimation

// ==================== 基础统一对话框 ====================

@Composable
fun UnifiedDialog(
    onDismissRequest: () -> Unit,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
    confirmButton: @Composable (() -> Unit)? = null,
    dismissButton: @Composable (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    showScrim: Boolean = true
) {
    // 使用 Popup 替代 Dialog，完全控制动画
    Popup(
        onDismissRequest = onDismissRequest,
        properties = PopupProperties(
            focusable = true,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        // 背景遮罩 - 纯淡入淡出
        AnimatedVisibility(
            visible = true,
            enter = FadeInAnimation,
            exit = FadeOutAnimation
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (showScrim) {
                            Modifier.background(Color.Black.copy(alpha = 0.5f))
                        } else {
                            Modifier
                        }
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismissRequest
                    ),
                contentAlignment = Alignment.Center
            ) {
                // 卡片内容 - 纯淡入淡出
                AnimatedVisibility(
                    visible = true,
                    enter = FadeInAnimation,
                    exit = FadeOutAnimation
                ) {
Surface(
                    modifier = modifier
                        .fillMaxWidth(0.92f)
                        .wrapContentHeight()
                        .then(Modifier.dialogBackgroundOf(RoundedCornerShape(AppDimens.dialogCornerRadius)))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { /* 阻止点击穿透到背景 */ }
                        ),
                    shape = RoundedCornerShape(AppDimens.dialogCornerRadius),
                    color = Color.Transparent,
                    shadowElevation = 0.dp
                ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(AppDimens.dialogPadding)
                        ) {
                            // 标题
                            title?.let {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(bottom = AppDimens.spacingMedium)
                                )
                            }
                            
                            // 内容
                            content()
                            
                            // 按钮
                            if (confirmButton != null || dismissButton != null) {
                                Spacer(modifier = Modifier.height(AppDimens.spacingLarge))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    dismissButton?.invoke()
                                    if (dismissButton != null && confirmButton != null) {
                                        Spacer(modifier = Modifier.width(AppDimens.spacingSmall))
                                    }
                                    confirmButton?.invoke()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==================== 统一确认对话框 ====================

@Composable
fun UnifiedConfirmDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmText: String = Str.get(R.string.ok_2),
    dismissText: String = Str.get(R.string.cancel),
    isDestructive: Boolean = false,
    confirmButtonColor: Color? = null
) {
    UnifiedDialog(
        onDismissRequest = onDismiss,
        title = title,
        content = {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = confirmButtonColor ?: if (isDestructive) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    contentColor = if (isDestructive) {
                        MaterialTheme.colorScheme.onError
                    } else {
                        MaterialTheme.colorScheme.onPrimary
                    }
                ),
                shape = RoundedCornerShape(AppDimens.radiusLarge)
            ) {
                Text(confirmText)
            }
        },
        dismissButton = {
            UnifiedDialogTextButton(onClick = onDismiss) {
                Text(dismissText)
            }
        }
    )
}

// ==================== 统一信息对话框 ====================

@Composable
fun UnifiedInfoDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
    buttonText: String = Str.get(R.string.ok_2)
) {
    UnifiedDialog(
        onDismissRequest = onDismiss,
        title = title,
        content = {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(AppDimens.radiusLarge)
            ) {
                Text(buttonText)
            }
        }
    )
}

// ==================== 统一Alert对话框（渐变背景） ====================

/**
 * AlertDialog 风格的对话框：与 Material3 [AlertDialog] 参数对齐，
 * 但背景用与主页面同款渐变铺底（[dialogBackgroundOf]），
 * 关闭或未启用渐变时回退到与页面一致的纯色背景。
 */
@Composable
fun UnifiedAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    shape: Shape = RoundedCornerShape(AppDimens.dialogCornerRadius),
    properties: DialogProperties = DialogProperties()
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = properties
    ) {
        Surface(
            modifier = modifier.then(Modifier.dialogBackgroundOf(shape)),
            shape = shape,
            color = Color.Transparent,
            shadowElevation = 0.dp
        ) {
            Column(
                modifier = Modifier
                    .wrapContentSize()
                    .padding(vertical = AppDimens.dialogPadding),
                verticalArrangement = Arrangement.spacedBy(AppDimens.spacingMedium)
            ) {
                if (title != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = AppDimens.dialogPadding),
                        horizontalAlignment = Alignment.Start
                    ) {
                        title()
                    }
                }
                if (text != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = AppDimens.dialogPadding),
                        horizontalAlignment = Alignment.Start
                    ) {
                        text()
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AppDimens.dialogPadding),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    dismissButton?.invoke()
                    confirmButton()
                }
            }
        }
    }
}

// ==================== 统一加载对话框 ====================

@Composable
fun UnifiedLoadingDialog(
    message: String = Str.get(R.string.loading),
    onCancel: (() -> Unit)? = null
) {
    Popup(
        onDismissRequest = { onCancel?.invoke() },
        properties = PopupProperties(
            focusable = true,
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    ) {
        // 背景遮罩 - 纯淡入淡出
        AnimatedVisibility(
            visible = true,
            enter = FadeInAnimation,
            exit = FadeOutAnimation
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                // 卡片内容 - 纯淡入淡出
                AnimatedVisibility(
                    visible = true,
                    enter = FadeInAnimation,
                    exit = FadeOutAnimation
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .wrapContentHeight()
                            .then(Modifier.dialogBackgroundOf(RoundedCornerShape(AppDimens.dialogCornerRadius))),
                        shape = RoundedCornerShape(AppDimens.dialogCornerRadius),
                        color = Color.Transparent,
                        shadowElevation = 0.dp
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(AppDimens.dialogPadding),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(AppDimens.spacingXLarge))
                            Text(
                                text = message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            onCancel?.let {
                                Spacer(modifier = Modifier.height(AppDimens.spacingMedium))
                                UnifiedDialogTextButton(onClick = it) {
                                    Text(Str.get(R.string.cancel))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==================== 统一列表对话框 ====================

@Composable
fun UnifiedListDialog(
    title: String,
    items: List<String>,
    onItemClick: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    UnifiedDialog(
        onDismissRequest = onDismiss,
        title = title,
        content = {
            Column {
                items.forEachIndexed { index, item ->
                    UnifiedDialogTextButton(
                        onClick = { onItemClick(index) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = item,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    if (index < items.size - 1) {
                        Divider(
                            color = MaterialTheme.colorScheme.outlineVariant,
                            thickness = AppDimens.dividerThickness
                        )
                    }
                }
            }
        },
        dismissButton = {
            UnifiedDialogTextButton(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = AppDimens.spacingMedium)
            ) {
                Text(Str.get(R.string.cancel))
            }
        }
    )
}

// ==================== 统一底部弹窗 ====================

@Suppress("DEPRECATION")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnifiedBottomSheet(
    onDismissRequest: () -> Unit,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = rememberModalBottomSheetState(
            skipPartiallyExpanded = true
        ),
        containerColor = Color.Transparent,
        modifier = Modifier.dialogBackgroundOf(
            RoundedCornerShape(
                topStart = AppDimens.radiusXXLarge,
                topEnd = AppDimens.radiusXXLarge
            )
        ),
        shape = RoundedCornerShape(
            topStart = AppDimens.radiusXXLarge,
            topEnd = AppDimens.radiusXXLarge
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppDimens.dialogPadding)
        ) {
            title?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = AppDimens.spacingMedium)
                )
            }
            content()
        }
    }
}