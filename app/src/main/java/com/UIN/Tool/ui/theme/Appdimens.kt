// app/src/main/java/com/UIN/Tool/ui/theme/AppDimens.kt
package com.UIN.Tool.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 统一尺寸管理器
 */
object AppDimens {

    // ==================== 间距 ====================
    val spacingNone: Dp = 0.dp
    val spacingXSmall: Dp = 2.dp
    val spacingSmall: Dp = 4.dp
    val spacingMedium: Dp = 8.dp
    val spacingLarge: Dp = 12.dp
    val spacingXLarge: Dp = 16.dp
    val spacingXXLarge: Dp = 20.dp
    val spacingXXXLarge: Dp = 24.dp
    val spacingXXXXLarge: Dp = 32.dp

    // ==================== 圆角 ====================
    val radiusNone: Dp = 0.dp
    val radiusXSmall: Dp = 2.dp
    val radiusSmall: Dp = 4.dp
    val radiusMedium: Dp = 8.dp
    val radiusLarge: Dp = 12.dp
    val radiusXLarge: Dp = 16.dp
    val radiusXXLarge: Dp = 20.dp
    val radiusXXXLarge: Dp = 24.dp

    // ==================== 按钮 ====================
    val buttonHeight: Dp = 44.dp
    val buttonMinWidth: Dp = 80.dp
    val buttonElevation: Dp = 2.dp
    val buttonPressedElevation: Dp = 4.dp
    val buttonCornerRadius: Dp = radiusLarge

    // ==================== 输入框 ====================
    val inputHeight: Dp = 48.dp
    val inputCornerRadius: Dp = radiusMedium

    // ==================== 卡片 ====================
    val cardElevation: Dp = 4.dp
    val cardPressedElevation: Dp = 8.dp
    val cardPadding: Dp = spacingXLarge
    val cardCornerRadius: Dp = radiusXLarge

    // ==================== 对话框 ====================
    val dialogElevation: Dp = 8.dp
    val dialogPadding: Dp = spacingXXLarge
    val dialogCornerRadius: Dp = radiusXXLarge
    val dialogWidthFraction: Float = 0.92f

    // ==================== 图标 ====================
    val iconXSmall: Dp = 12.dp
    val iconSmall: Dp = 16.dp
    val iconMedium: Dp = 20.dp
    val iconLarge: Dp = 24.dp
    val iconXLarge: Dp = 32.dp
    val iconXXLarge: Dp = 48.dp

    // ==================== 文本 ====================
    val titleTextSize: Float = 20f
    val bodyTextSize: Float = 14f
    val captionTextSize: Float = 12f
    val sectionTitleTextSize: Float = 18f
    val headlineTextSize: Float = 24f

    // ==================== 进度 ====================
    val progressHeight: Dp = 4.dp
    val progressCornerRadius: Dp = radiusSmall

    // ==================== 导航栏 ====================
    val bottomNavHeight: Dp = 56.dp
    val topAppBarHeight: Dp = 56.dp

    // ==================== 分割线 ====================
    val dividerThickness: Dp = 1.dp
}