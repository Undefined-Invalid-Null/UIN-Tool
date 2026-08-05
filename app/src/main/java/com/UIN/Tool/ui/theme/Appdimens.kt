// app/src/main/java/com/UIN/Tool/ui/theme/AppDimens.kt
package com.UIN.Tool.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.UIN.Tool.utils.UIConfig

/**
 * 统一尺寸管理器
 *
 * 可配置的尺寸项改为从 [UIConfig] 动态读取，配合主题版本号触发重组，
 * 实现「保存后立即生效」；未初始化时回退到静态默认值。
 */
object AppDimens {

    // ==================== 间距 ====================
    val spacingNone: Dp = 0.dp
    val spacingXSmall: Dp = 2.dp
    val spacingSmall: Dp
        get() = if (UIConfig.isInitialized()) UIConfig.getSpacingSmall().dp else 4.dp
    val spacingMedium: Dp
        get() = if (UIConfig.isInitialized()) UIConfig.getSpacingMedium().dp else 8.dp
    val spacingLarge: Dp
        get() = if (UIConfig.isInitialized()) UIConfig.getSpacingLarge().dp else 12.dp
    val spacingXLarge: Dp = 16.dp
    val spacingXXLarge: Dp = 20.dp
    val spacingXXXLarge: Dp = 24.dp
    val spacingXXXXLarge: Dp = 32.dp

    // ==================== 圆角 ====================
    val radiusNone: Dp = 0.dp
    val radiusXSmall: Dp = 2.dp
    val radiusSmall: Dp
        get() = if (UIConfig.isInitialized()) UIConfig.getCornerRadiusSmall().dp else 4.dp
    val radiusMedium: Dp
        get() = if (UIConfig.isInitialized()) UIConfig.getCornerRadiusMedium().dp else 8.dp
    val radiusLarge: Dp
        get() = if (UIConfig.isInitialized()) UIConfig.getCornerRadiusLarge().dp else 12.dp
    val radiusXLarge: Dp
        get() = if (UIConfig.isInitialized()) UIConfig.getCardCornerRadius().dp else 16.dp
    val radiusXXLarge: Dp
        get() = if (UIConfig.isInitialized()) UIConfig.getDialogCornerRadius().dp else 20.dp
    val radiusXXXLarge: Dp
        get() = if (UIConfig.isInitialized()) UIConfig.getCornerRadiusExtraLarge().dp else 24.dp

    // ==================== 按钮 ====================
    val buttonHeight: Dp
        get() = if (UIConfig.isInitialized()) UIConfig.getButtonHeight().dp else 44.dp
    val buttonMinWidth: Dp
        get() = if (UIConfig.isInitialized()) UIConfig.getButtonMinWidth().dp else 80.dp
    val buttonElevation: Dp
        get() = if (UIConfig.isInitialized()) UIConfig.getButtonElevation().dp else 2.dp
    val buttonPressedElevation: Dp = 4.dp
    val buttonCornerRadius: Dp
        get() = if (UIConfig.isInitialized()) UIConfig.getButtonCornerRadius().dp else 12.dp

    // ==================== 输入框 ====================
    val inputHeight: Dp = 48.dp
    val inputCornerRadius: Dp
        get() = if (UIConfig.isInitialized()) UIConfig.getInputCornerRadius().dp else 8.dp

    // ==================== 卡片 ====================
    val cardElevation: Dp
        get() = if (UIConfig.isInitialized()) UIConfig.getCardElevation().dp else 4.dp
    val cardPressedElevation: Dp = 8.dp
    val cardPadding: Dp
        get() = if (UIConfig.isInitialized()) UIConfig.getCardPadding().dp else 16.dp
    val cardCornerRadius: Dp
        get() = if (UIConfig.isInitialized()) UIConfig.getCardCornerRadius().dp else 16.dp

    // ==================== 对话框 ====================
    val dialogElevation: Dp = 8.dp
    val dialogPadding: Dp = spacingXXLarge
    val dialogCornerRadius: Dp
        get() = if (UIConfig.isInitialized()) UIConfig.getDialogCornerRadius().dp else 20.dp
    val dialogWidthFraction: Float = 0.92f

    // ==================== 图标 ====================
    val iconXSmall: Dp = 12.dp
    val iconSmall: Dp
        get() = if (UIConfig.isInitialized()) UIConfig.getIconSizeSmall().dp else 16.dp
    val iconMedium: Dp
        get() = if (UIConfig.isInitialized()) UIConfig.getIconSizeMedium().dp else 20.dp
    val iconLarge: Dp
        get() = if (UIConfig.isInitialized()) UIConfig.getIconSizeLarge().dp else 24.dp
    val iconXLarge: Dp = 32.dp
    val iconXXLarge: Dp = 48.dp

    // ==================== 文本 ====================
    val titleTextSize: Float
        get() = if (UIConfig.isInitialized()) UIConfig.getTitleTextSize() else 20f
    val bodyTextSize: Float
        get() = if (UIConfig.isInitialized()) UIConfig.getBodyTextSize() else 14f
    val captionTextSize: Float
        get() = if (UIConfig.isInitialized()) UIConfig.getCaptionTextSize() else 12f
    val sectionTitleTextSize: Float
        get() = if (UIConfig.isInitialized()) UIConfig.getSectionTitleTextSize() else 18f
    val headlineTextSize: Float = 24f

    // ==================== 进度 ====================
    val progressHeight: Dp
        get() = if (UIConfig.isInitialized()) UIConfig.getProgressHeight().dp else 4.dp
    val progressCornerRadius: Dp
        get() = radiusSmall

    // ==================== 导航栏 ====================
    val bottomNavHeight: Dp = 56.dp
    val topAppBarHeight: Dp = 56.dp

    // ==================== 分割线 ====================
    val dividerThickness: Dp = 1.dp
}
