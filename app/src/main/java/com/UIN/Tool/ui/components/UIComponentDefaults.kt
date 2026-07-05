package com.UIN.Tool.ui.components

import androidx.compose.ui.unit.dp
import com.UIN.Tool.utils.UIConfig

/**
 * 从 UIConfig 读取组件的默认尺寸、间距等
 * 所有组件统一从这里获取默认值，方便统一调整
 */
object UIComponentDefaults {
    private val uiConfig = UIConfig.getInstance()

    // 按钮
    val buttonHeight: Float get() = uiConfig.getButtonHeight()
    val buttonMinWidth: Float get() = uiConfig.getButtonMinWidth()
    val buttonCornerRadius: Float get() = uiConfig.getButtonCornerRadius()
    val buttonElevation: Float get() = uiConfig.getButtonElevation()

    // 卡片
    val cardElevation: Float get() = uiConfig.getCardElevation()
    val cardCornerRadius: Float get() = uiConfig.getCardCornerRadius()
    val cardPadding: Float get() = uiConfig.getCardPadding()

    // 间距
    val spacingSmall: Float get() = uiConfig.getSpacingSmall()
    val spacingMedium: Float get() = uiConfig.getSpacingMedium()
    val spacingLarge: Float get() = uiConfig.getSpacingLarge()

    // 输入框
    val inputCornerRadius: Float get() = uiConfig.getInputCornerRadius()

    // 对话框
    val dialogCornerRadius: Float get() = uiConfig.getDialogCornerRadius()

    // 进度条
    val progressHeight: Float get() = uiConfig.getProgressHeight()

    // 图标
    val iconSizeSmall: Float get() = uiConfig.getIconSizeSmall()
    val iconSizeMedium: Float get() = uiConfig.getIconSizeMedium()
    val iconSizeLarge: Float get() = uiConfig.getIconSizeLarge()

    // 文本
    val titleTextSize: Float get() = uiConfig.getTitleTextSize()
    val bodyTextSize: Float get() = uiConfig.getBodyTextSize()
    val captionTextSize: Float get() = uiConfig.getCaptionTextSize()
    val sectionTitleTextSize: Float get() = uiConfig.getSectionTitleTextSize()
}