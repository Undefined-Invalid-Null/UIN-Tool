package com.UIN.Tool.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ==================== 自定义主色（蓝灰色系） ====================
val PrimaryBlue = Color(0xFF1A3A4A)
val PrimaryDarkBlue = Color(0xFF0F2838)
val PrimaryLightBlue = Color(0xFF2D5A70)
val AccentBlue = Color(0xFF4A8A9E)

// ==================== 辅助色 ====================
val SuccessGreen = Color(0xFF4CAF50)
val WarningOrange = Color(0xFFFF9800)
val ErrorRed = Color(0xFFF44336)
val InfoBlue = Color(0xFF2196F3)

// ==================== 文本颜色 ====================
val TextPrimary = Color(0xFF212121)
val TextSecondary = Color(0xFF757575)
val TextHint = Color(0xFFBDBDBD)
val TextDisabled = Color(0xFF9E9E9E)

// ==================== 背景色 ====================
val BackgroundGray = Color(0xFFF5F7FA)
val BackgroundCardWhite = Color(0xFFFFFFFF)
val BackgroundDarkGray = Color(0xFFE8ECF0)

// ==================== 表面色 ====================
val SurfaceWhite = Color(0xFFFFFFFF)
val SurfaceVariantGray = Color(0xFFF5F7FA)
val SurfaceCardWhite = Color(0xFFFFFFFF)

// ==================== 边框和分割线 ====================
val DividerGray = Color(0xFFE0E4E8)
val BorderGray = Color(0xFFD0D5DA)

// ==================== 深色模式 ====================
val DarkPrimaryBlue = Color(0xFF4A8A9E)
val DarkBackground = Color(0xFF121212)
val DarkSurface = Color(0xFF1E1E1E)
val DarkTextPrimary = Color(0xFFEEEEEE)
val DarkTextSecondary = Color(0xFFBDBDBD)
val DarkSurfaceVariant = Color(0xFF2A2A2A)

// ==================== 卡片相关 ====================
val CardShadow = Color(0x1A000000)
val CardRipple = Color(0x0D1A3A4A)

// ==================== 玻璃效果 ====================
val GlassBackground = Color(0xE6FFFFFF)
val GlassBorder = Color(0x40FFFFFF)
val GlassShadow = Color(0x20000000)

// ==================== 叠加色 ====================
val OverlayLight = Color(0x0D000000)
val OverlayMedium = Color(0x1A000000)
val OverlayDark = Color(0x33000000)

// ==================== 灰色调 ====================
val GrayLight = Color(0xFFF5F7FA)
val GrayMedium = Color(0xFFEEF1F4)
val GrayDark = Color(0xFF9AA6B2)
val GrayText = Color(0xFF616F7E)

// ==================== 导航栏 ====================
val NavItemSelected = Color(0xFF1A3A4A)
val NavItemUnselected = Color(0xFFB0BCC8)

// ==================== 渐变 ====================
val GradientStart = Color(0xFF1A3A4A)
val GradientEnd = Color(0xFF2D5A70)
val GradientAccentStart = Color(0xFF4A8A9E)
val GradientAccentEnd = Color(0xFF6AAEC2)

// ==================== 浅色主题配色方案 ====================
val LightColorScheme = lightColorScheme(
    primary = PrimaryBlue,
    onPrimary = Color.White,
    primaryContainer = PrimaryLightBlue,
    onPrimaryContainer = Color.White,
    secondary = AccentBlue,
    onSecondary = Color.White,
    tertiary = InfoBlue,
    onTertiary = Color.White,
    background = BackgroundGray,
    onBackground = TextPrimary,
    surface = SurfaceWhite,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceVariantGray,
    onSurfaceVariant = TextSecondary,
    error = ErrorRed,
    onError = Color.White,
    outline = BorderGray,
    outlineVariant = DividerGray,
    inverseSurface = PrimaryDarkBlue,
    inverseOnSurface = Color.White,
    inversePrimary = PrimaryLightBlue,
    surfaceTint = PrimaryBlue
)

// ==================== 深色主题配色方案 ====================
val DarkColorScheme = darkColorScheme(
    primary = DarkPrimaryBlue,
    onPrimary = Color.White,
    primaryContainer = PrimaryLightBlue,
    onPrimaryContainer = Color.White,
    secondary = AccentBlue,
    onSecondary = Color.White,
    tertiary = InfoBlue,
    onTertiary = Color.White,
    background = DarkBackground,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkTextSecondary,
    error = ErrorRed,
    onError = Color.White,
    outline = Color(0xFF444444),
    outlineVariant = Color(0xFF333333),
    inverseSurface = Color(0xFF333333),
    inverseOnSurface = DarkTextPrimary,
    inversePrimary = DarkPrimaryBlue,
    surfaceTint = DarkPrimaryBlue
)

// ==================== 主题函数 ====================
@Composable
fun UINToolTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = Shapes,
        content = content
    )
}