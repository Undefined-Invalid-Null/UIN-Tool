// app/src/main/java/com/UIN/Tool/ui/theme/AppColors.kt
package com.UIN.Tool.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.MaterialTheme
import com.UIN.Tool.utils.UIConfig

/**
 * 统一颜色管理器
 *
 * 语义色（success/warning/info/文本/玻璃效果等）从 UIConfig 动态读取，
 * 随主题切换与保存自动刷新；未配置时回退到默认值。
 */
object AppColors {

    @Composable
    fun primary(): Color = MaterialTheme.colorScheme.primary
    
    @Composable
    fun onPrimary(): Color = MaterialTheme.colorScheme.onPrimary
    
    @Composable
    fun primaryContainer(): Color = MaterialTheme.colorScheme.primaryContainer
    
    @Composable
    fun onPrimaryContainer(): Color = MaterialTheme.colorScheme.onPrimaryContainer
    
    @Composable
    fun secondary(): Color = MaterialTheme.colorScheme.secondary
    
    @Composable
    fun onSecondary(): Color = MaterialTheme.colorScheme.onSecondary
    
    @Composable
    fun tertiary(): Color = MaterialTheme.colorScheme.tertiary
    
    @Composable
    fun onTertiary(): Color = MaterialTheme.colorScheme.onTertiary
    
    @Composable
    fun background(): Color = MaterialTheme.colorScheme.background
    
    @Composable
    fun onBackground(): Color = MaterialTheme.colorScheme.onBackground
    
    @Composable
    fun surface(): Color = MaterialTheme.colorScheme.surface
    
    @Composable
    fun onSurface(): Color = MaterialTheme.colorScheme.onSurface
    
    @Composable
    fun surfaceVariant(): Color = MaterialTheme.colorScheme.surfaceVariant
    
    @Composable
    fun onSurfaceVariant(): Color = MaterialTheme.colorScheme.onSurfaceVariant
    
    @Composable
    fun error(): Color = MaterialTheme.colorScheme.error
    
    @Composable
    fun onError(): Color = MaterialTheme.colorScheme.onError
    
    @Composable
    fun errorContainer(): Color = MaterialTheme.colorScheme.errorContainer
    
    @Composable
    fun onErrorContainer(): Color = MaterialTheme.colorScheme.onErrorContainer
    
    @Composable
    fun outline(): Color = MaterialTheme.colorScheme.outline
    
    @Composable
    fun outlineVariant(): Color = MaterialTheme.colorScheme.outlineVariant
    
    @Composable
    fun surfaceTint(): Color = MaterialTheme.colorScheme.surfaceTint
    
    @Composable
    fun inverseSurface(): Color = MaterialTheme.colorScheme.inverseSurface
    
    @Composable
    fun inverseOnSurface(): Color = MaterialTheme.colorScheme.inverseOnSurface
    
    @Composable
    fun inversePrimary(): Color = MaterialTheme.colorScheme.inversePrimary

    // ==================== 语义颜色 ====================
    
    @Composable
    fun success(): Color = dynamicColor("success", Color(0xFF4CAF50))
    
    @Composable
    fun successContainer(): Color = Color(0xFFE8F5E9)
    
    @Composable
    fun onSuccessContainer(): Color = Color(0xFF1B5E20)
    
    @Composable
    fun warning(): Color = dynamicColor("warning", Color(0xFFFF9800))
    
    @Composable
    fun warningContainer(): Color = Color(0xFFFFF3E0)
    
    @Composable
    fun onWarningContainer(): Color = Color(0xFFE65100)
    
    @Composable
    fun info(): Color = dynamicColor("info", Color(0xFF2196F3))
    
    @Composable
    fun infoContainer(): Color = Color(0xFFE3F2FD)
    
    @Composable
    fun onInfoContainer(): Color = Color(0xFF0D47A1)

    // ==================== 文本颜色 ====================
    
    @Composable
    fun textPrimary(): Color = onSurface()
    
    @Composable
    fun textSecondary(): Color = onSurfaceVariant()
    
    @Composable
    fun textHint(): Color = dynamicColor("text_hint", onSurface().copy(alpha = 0.38f))
    
    @Composable
    fun textDisabled(): Color = dynamicColor("disabled", onSurface().copy(alpha = 0.38f))
    
    @Composable
    fun textInverse(): Color = inverseOnSurface()

    // ==================== 背景颜色 ====================
    
    @Composable
    fun cardBackground(): Color = surface()
    
    @Composable
    fun divider(): Color = outlineVariant()
    
    @Composable
    fun shadow(): Color = Color.Black.copy(alpha = 0.1f)
    
    @Composable
    fun glassEnabled(): Boolean = UIConfig.isInitialized() && UIConfig.isGlassEffectEnabled()
    
    @Composable
    fun glassBackground(): Color = if (glassEnabled()) {
        dynamicColor("glass_background", surface().copy(alpha = 0.85f))
    } else {
        surface()
    }
    
    @Composable
    fun glassBorder(): Color = if (glassEnabled()) Color.White.copy(alpha = 0.35f) else Color.Transparent
}