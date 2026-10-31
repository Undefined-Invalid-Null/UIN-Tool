// app/src/main/java/com/UIN/Tool/ui/theme/AppDimens.kt
package com.UIN.Tool.ui.theme

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.UIN.Tool.utils.UIConfig

/**
 * 统一尺寸管理器
 *
 * 可配置的尺寸项改为从 [UIConfig] 动态读取，配合主题版本号触发重组，
 * 实现「保存后立即生效」；未初始化时回退到静态默认值。
 *
 * 重组依赖：[UIConfig] 本身只在 StateFlow 上发版本号，直接读 JSON 拿不到快照通知，
 * 所以动态 getter 一律经 [tracked] 先读一次 [configTick]（由 UINToolTheme 在每次组合后
 * 写入当前配置版本）—— 读到它即把调用方的组合作用域登记为依赖，配置保存后自动重组重读。
 */
object AppDimens {

    /** 配置版本快照；写入方见 UINToolTheme，读取方为本对象各动态 getter。 */
    internal val configTick = mutableStateOf(0)

    /** 计算尺寸前先读一次 [configTick]，登记重组依赖后返回计算结果。 */
    private inline fun <T> tracked(calc: () -> T): T {
        configTick.value
        return calc()
    }

    // ==================== 间距 ====================
    val spacingNone: Dp = 0.dp
    val spacingXSmall: Dp = 2.dp
    val spacingSmall: Dp
        get() = tracked { if (UIConfig.isInitialized()) UIConfig.getSpacingSmall().dp else 4.dp }
    val spacingMedium: Dp
        get() = tracked { if (UIConfig.isInitialized()) UIConfig.getSpacingMedium().dp else 8.dp }
    val spacingLarge: Dp
        get() = tracked { if (UIConfig.isInitialized()) UIConfig.getSpacingLarge().dp else 12.dp }
    val spacingXLarge: Dp = 16.dp
    val spacingXXLarge: Dp = 20.dp
    val spacingXXXLarge: Dp = 24.dp
    val spacingXXXXLarge: Dp = 32.dp

    // ==================== 圆角 ====================
    val radiusNone: Dp = 0.dp
    val radiusXSmall: Dp = 2.dp
    // fallback ≡ UIConfig 默认（cornerRadiusSmall/Medium/Large = 8/12/16，E-13 对齐）
    val radiusSmall: Dp
        get() = tracked { if (UIConfig.isInitialized()) UIConfig.getCornerRadiusSmall().dp else 8.dp }
    val radiusMedium: Dp
        get() = tracked { if (UIConfig.isInitialized()) UIConfig.getCornerRadiusMedium().dp else 12.dp }
    val radiusLarge: Dp
        get() = tracked { if (UIConfig.isInitialized()) UIConfig.getCornerRadiusLarge().dp else 16.dp }
    val radiusXLarge: Dp
        get() = tracked { if (UIConfig.isInitialized()) UIConfig.getCardCornerRadius().dp else 16.dp }
    val radiusXXLarge: Dp
        get() = tracked { if (UIConfig.isInitialized()) UIConfig.getDialogCornerRadius().dp else 20.dp }
    val radiusXXXLarge: Dp
        get() = tracked { if (UIConfig.isInitialized()) UIConfig.getCornerRadiusExtraLarge().dp else 24.dp }

    // ==================== 按钮 ====================
    val buttonHeight: Dp
        get() = tracked { if (UIConfig.isInitialized()) UIConfig.getButtonHeight().dp else 44.dp }
    val buttonMinWidth: Dp
        get() = tracked { if (UIConfig.isInitialized()) UIConfig.getButtonMinWidth().dp else 80.dp }
    val buttonElevation: Dp
        get() = tracked { if (UIConfig.isInitialized()) UIConfig.getButtonElevation().dp else 2.dp }
    val buttonPressedElevation: Dp = 4.dp
    val buttonCornerRadius: Dp
        get() = tracked { if (UIConfig.isInitialized()) UIConfig.getButtonCornerRadius().dp else 12.dp }

    // ==================== 输入框 ====================
    val inputHeight: Dp = 48.dp
    val inputCornerRadius: Dp
        get() = tracked { if (UIConfig.isInitialized()) UIConfig.getInputCornerRadius().dp else 8.dp }

    // ==================== 卡片 ====================
    val cardElevation: Dp
        get() = tracked { if (UIConfig.isInitialized()) UIConfig.getCardElevation().dp else 4.dp }
    val cardPressedElevation: Dp = 8.dp
    val cardPadding: Dp
        get() = tracked { if (UIConfig.isInitialized()) UIConfig.getCardPadding().dp else 16.dp }
    val cardCornerRadius: Dp
        get() = tracked { if (UIConfig.isInitialized()) UIConfig.getCardCornerRadius().dp else 16.dp }

    // ==================== 对话框 ====================
    val dialogElevation: Dp = 8.dp
    val dialogPadding: Dp = spacingXXLarge
    val dialogCornerRadius: Dp
        get() = tracked { if (UIConfig.isInitialized()) UIConfig.getDialogCornerRadius().dp else 20.dp }
    val dialogWidthFraction: Float = 0.92f

    // ==================== 图标 ====================
    val iconXSmall: Dp = 12.dp
    val iconSmall: Dp
        get() = tracked { if (UIConfig.isInitialized()) UIConfig.getIconSizeSmall().dp else 16.dp }
    val iconMedium: Dp
        get() = tracked { if (UIConfig.isInitialized()) UIConfig.getIconSizeMedium().dp else 20.dp }
    val iconLarge: Dp
        get() = tracked { if (UIConfig.isInitialized()) UIConfig.getIconSizeLarge().dp else 24.dp }
    val iconXLarge: Dp = 32.dp
    val iconXXLarge: Dp = 48.dp

    // ==================== 文本 ====================
    val titleTextSize: Float
        get() = tracked { if (UIConfig.isInitialized()) UIConfig.getTitleTextSize() else 20f }
    val bodyTextSize: Float
        get() = tracked { if (UIConfig.isInitialized()) UIConfig.getBodyTextSize() else 14f }
    val captionTextSize: Float
        get() = tracked { if (UIConfig.isInitialized()) UIConfig.getCaptionTextSize() else 12f }
    val sectionTitleTextSize: Float
        get() = tracked { if (UIConfig.isInitialized()) UIConfig.getSectionTitleTextSize() else 18f }
    val headlineTextSize: Float = 24f

    // ==================== 进度 ====================
    val progressHeight: Dp
        get() = tracked { if (UIConfig.isInitialized()) UIConfig.getProgressHeight().dp else 4.dp }
    val progressCornerRadius: Dp
        get() = radiusSmall

    // ==================== 导航栏 ====================
    val bottomNavHeight: Dp = 56.dp
    val topAppBarHeight: Dp = 56.dp

    // ==================== 分割线 ====================
    val dividerThickness: Dp = 1.dp
}
