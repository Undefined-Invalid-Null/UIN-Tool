package com.UIN.Tool.ui.theme

import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.UIN.Tool.utils.AppToast
import com.UIN.Tool.utils.UIConfig
import android.app.Activity

// ==================== 自定义主色（蓝灰色系） ====================
val PrimaryBlue = Color(0xFF1A3A4A)
val PrimaryDarkBlue = Color(0xFF0F2838)
val PrimaryLightBlue = Color(0xFF2D5A70)
val AccentBlue = Color(0xFF4A8A9E)

// ==================== 辅助色 ====================
val ErrorRed = Color(0xFFF44336)
val InfoBlue = Color(0xFF2196F3)

// ==================== 文本颜色 ====================
val TextPrimary = Color(0xFF212121)
val TextSecondary = Color(0xFF757575)

// ==================== 背景色 ====================
val BackgroundGray = Color(0xFFF5F7FA)

// ==================== 表面色 ====================
val SurfaceWhite = Color(0xFFFFFFFF)
val SurfaceVariantGray = Color(0xFFF5F7FA)

// ==================== 边框和分割线 ====================
val DividerGray = Color(0xFFE0E4E8)
val BorderGray = Color(0xFFD0D5DA)

// ==================== 深色模式（浅灰色主题） ====================
val DarkPrimaryGray = Color(0xFF8B949E)
val DarkBackground = Color(0xFF2A2A2A)
val DarkSurface = Color(0xFF363636)
val DarkTextPrimary = Color(0xFFE8E8E8)
val DarkTextSecondary = Color(0xFFA8A8A8)
val DarkSurfaceVariant = Color(0xFF3F3F3F)
val DarkOutline = Color(0xFF484848)

// ==================== 浅色主题配色方案 ====================
val LightColorScheme = lightColorScheme(
    primary = PrimaryBlue,
    onPrimary = Color.White,
    primaryContainer = PrimaryLightBlue,
    onPrimaryContainer = Color.White,
    secondary = AccentBlue,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD7E8EE),
    onSecondaryContainer = PrimaryDarkBlue,
    tertiary = InfoBlue,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE3F2FD),
    onTertiaryContainer = Color(0xFF0D47A1),
    background = BackgroundGray,
    onBackground = TextPrimary,
    surface = SurfaceWhite,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceVariantGray,
    onSurfaceVariant = TextSecondary,
    error = ErrorRed,
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    outline = BorderGray,
    outlineVariant = DividerGray,
    inverseSurface = PrimaryDarkBlue,
    inverseOnSurface = Color.White,
    inversePrimary = PrimaryLightBlue,
    surfaceTint = PrimaryBlue,
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF8FAFB),
    surfaceContainer = Color(0xFFF2F5F8),
    surfaceContainerHigh = Color(0xFFEDF1F5),
    surfaceContainerHighest = Color(0xFFE7EBEF)
)

// ==================== 深色主题配色方案（浅灰色） ====================
val DarkColorScheme = darkColorScheme(
    primary = DarkPrimaryGray,
    onPrimary = Color(0xFF1A1A1A),
    primaryContainer = Color(0xFF4A4A4A),
    onPrimaryContainer = Color(0xFFE6E6E6),
    secondary = Color(0xFFA8A8A8),
    onSecondary = Color(0xFF141414),
    secondaryContainer = Color(0xFF3A3A3A),
    onSecondaryContainer = Color(0xFFD0D0D0),
    tertiary = Color(0xFF8C8C8C),
    onTertiary = Color(0xFF101010),
    tertiaryContainer = Color(0xFF333333),
    onTertiaryContainer = Color(0xFFC4C4C4),
    background = DarkBackground,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkTextSecondary,
    error = Color(0xFFFF6E6E),
    onError = Color(0xFF2A0000),
    errorContainer = Color(0xFF6B1E1E),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF484848),
    outlineVariant = Color(0xFF3F3F3F),
    inverseSurface = Color(0xFFE8E8E8),
    inverseOnSurface = Color(0xFF1E1E1E),
    inversePrimary = DarkPrimaryGray,
    surfaceTint = DarkPrimaryGray,
    surfaceContainerLowest = Color(0xFF232323),
    surfaceContainerLow = Color(0xFF2E2E2E),
    surfaceContainer = Color(0xFF333333),
    surfaceContainerHigh = Color(0xFF3A3A3A),
    surfaceContainerHighest = Color(0xFF404040)
)

// ==================== 从 UIConfig 解析颜色 ====================

private fun parseColor(str: String, fallback: Color): Color {
    return try {
        if (str.isNotEmpty() && str.startsWith("#") && str.length >= 7) {
            Color(android.graphics.Color.parseColor(str))
        } else {
            fallback
        }
    } catch (e: Exception) {
        fallback
    }
}

private fun uiColor(uiConfig: UIConfig, key: String, fallback: Color, dark: Boolean): Color {
    return parseColor(uiConfig.getColorStringForMode(key, dark), fallback)
}

/**
 * 读取当前激活配色区（浅色/深色）中指定键的颜色，
 * 供组件（如 AppColors 语义色）在主题切换后自动刷新。
 */
@Composable
fun dynamicColor(key: String, fallback: Color): Color {
    if (!UIConfig.isInitialized()) return fallback
    val dark = UIConfig.shouldUseDarkTheme(isSystemInDarkTheme())
    return parseColor(UIConfig.getColorStringForMode(key, dark), fallback)
}

/**
 * 解析实际是否使用深色主题（含用户主题模式开关），供组件复用。
 */
@Composable
fun isAppDarkTheme(): Boolean {
    if (!UIConfig.isInitialized()) return isSystemInDarkTheme()
    return UIConfig.shouldUseDarkTheme(isSystemInDarkTheme())
}

private fun parseUiColor(uiConfig: UIConfig, key: String, dark: Boolean, fallback: Color): Color {
    return parseColor(uiConfig.getColorStringForMode(key, dark), fallback)
}

/**
 * 读取全局渐变背景画刷。
 * 单选模式：所选颜色向背景色渐变；多选模式：多个颜色构成渐变。
 * 渐变方向由「from（起始方向）→ to（结束方向）」决定，需传入绘制区域宽高（px）。
 * 关闭时返回 null，由根布局回退到背景色。
 */
@Composable
fun gradientBackgroundBrush(widthPx: Float, heightPx: Float): Brush? {
    if (!UIConfig.isInitialized() || !UIConfig.isGradientBackgroundEnabled()) return null
    val dark = isAppDarkTheme()
    val background = parseUiColor(UIConfig.getInstance(), "background", dark, MaterialTheme.colorScheme.background)
    val colors = if (dark) {
        when (UIConfig.getInstance().getGradientModeDark()) {
            UIConfig.GRADIENT_MODE_SINGLE -> {
                val stored = UIConfig.getInstance().getGradientColorStringDark()
                val c = parseColor(stored, Color(0xFF4C4F51))
                listOf(c, background)
            }
            else -> {
                val parsed = UIConfig.getInstance().getGradientColorsStringDark()
                    .map { parseColor(it, Color(0xFF4C4F51)) }
                    .filter { it != Color.Unspecified }
                if (parsed.isEmpty()) {
                    val stored = UIConfig.getInstance().getGradientColorStringDark()
                    listOf(parseColor(stored, Color(0xFF4C4F51)), background)
                } else if (parsed.size == 1) {
                    listOf(parsed[0], background)
                } else {
                    parsed
                }
            }
        }
    } else when (UIConfig.getInstance().getGradientMode()) {
        UIConfig.GRADIENT_MODE_SINGLE -> {
            val stored = UIConfig.getInstance().getGradientColorString()
            val c = parseColor(stored, Color(0xFFC4D6DF))
            listOf(c, background)
        }
        else -> {
            val parsed = UIConfig.getInstance().getGradientColorsString()
                .map { parseColor(it, Color(0xFFC4D6DF)) }
                .filter { it != Color.Unspecified }
            if (parsed.isEmpty()) {
                listOf(Color(0xFFC4D6DF), background)
            } else if (parsed.size == 1) {
                listOf(parsed[0], background)
            } else {
                parsed
            }
        }
    }
    val fromDirection = if (dark) UIConfig.getInstance().getGradientFromDark() else UIConfig.getInstance().getGradientFrom()
    val toDirection = if (dark) UIConfig.getInstance().getGradientToDark() else UIConfig.getInstance().getGradientTo()
    val from = gradientDirectionOffset(fromDirection, widthPx, heightPx)
    val to = gradientDirectionOffset(toDirection, widthPx, heightPx)
    return Brush.linearGradient(colors = colors, start = from, end = to)
}

private fun gradientDirectionOffset(direction: String, width: Float, height: Float): Offset {
    return when (direction) {
        UIConfig.GRADIENT_DIR_TOP -> Offset(width / 2, 0f)
        UIConfig.GRADIENT_DIR_BOTTOM -> Offset(width / 2, height)
        UIConfig.GRADIENT_DIR_TOP_LEFT -> Offset(0f, 0f)
        UIConfig.GRADIENT_DIR_TOP_RIGHT -> Offset(width, 0f)
        UIConfig.GRADIENT_DIR_BOTTOM_LEFT -> Offset(0f, height)
        else -> Offset(width, height)
    }
}

/**
 * 是否启用粗体（字体页开关），供文本组件复用。
 */
@Composable
fun isBoldEnabled(): Boolean {
    if (!UIConfig.isInitialized()) return true
    return UIConfig.isBoldEnabled()
}

/** E-08：buildDynamicColorScheme 的全部输入快照（结构相等比较，未变则复用旧实例） */
private fun colorInputsSnapshot(dark: Boolean): List<Any> {
    val keys = listOf(
        "primary", "primary_dark", "primary_light", "accent", "info", "error",
        "text_primary", "text_secondary", "text_primary_inverse",
        "background", "surface", "surface_variant", "divider"
    )
    return listOf(dark) + keys.map { UIConfig.getColorStringForMode(it, dark) }
}

/** E-08：buildDynamicShapes 的全部输入快照 */
private fun shapeInputsSnapshot(): List<Float> = listOf(
    UIConfig.getCornerRadiusSmall(),
    UIConfig.getCornerRadiusMedium(),
    UIConfig.getCornerRadiusLarge(),
    UIConfig.getCornerRadiusExtraLarge(),
    UIConfig.getDialogCornerRadius()
)

/** E-08：buildDynamicTypography 的全部输入快照 */
private fun typographyInputsSnapshot(): List<Float> = listOf(
    UIConfig.getTitleTextSize(),
    UIConfig.getBodyTextSize(),
    UIConfig.getCaptionTextSize(),
    UIConfig.getSectionTitleTextSize()
)

private fun buildDynamicColorScheme(uiConfig: UIConfig, dark: Boolean): ColorScheme {
    val base = if (dark) DarkColorScheme else LightColorScheme

    val primary = uiColor(uiConfig, "primary", base.primary, dark)
    val primaryDark = uiColor(uiConfig, "primary_dark", base.onSecondaryContainer, dark)
    val primaryLight = uiColor(uiConfig, "primary_light", base.primaryContainer, dark)
    val accent = uiColor(uiConfig, "accent", base.secondary, dark)
    val info = uiColor(uiConfig, "info", base.tertiary, dark)
    val error = uiColor(uiConfig, "error", base.error, dark)
    val textPrimary = uiColor(uiConfig, "text_primary", base.onSurface, dark)
    val textSecondary = uiColor(uiConfig, "text_secondary", base.onSurfaceVariant, dark)
    val textInverse = uiColor(uiConfig, "text_primary_inverse", Color.White, dark)
    val background = uiColor(uiConfig, "background", base.background, dark)
    val surface = uiColor(uiConfig, "surface", base.surface, dark)
    val surfaceVariant = uiColor(uiConfig, "surface_variant", base.surfaceVariant, dark)
    val divider = uiColor(uiConfig, "divider", base.outlineVariant, dark)

    return base.copy(
        primary = primary,
        onPrimary = textInverse,
        primaryContainer = primaryLight,
        onPrimaryContainer = textInverse,
        secondary = accent,
        onSecondary = textInverse,
        onSecondaryContainer = primaryDark,
        tertiary = info,
        onTertiary = textInverse,
        background = background,
        onBackground = textPrimary,
        surface = surface,
        onSurface = textPrimary,
        surfaceVariant = surfaceVariant,
        onSurfaceVariant = textSecondary,
        error = error,
        onError = textInverse,
        outline = divider,
        outlineVariant = divider,
        surfaceTint = primary
    )
}

private fun buildDynamicShapes(): Shapes = buildShapes(
    cornerSmall = UIConfig.getCornerRadiusSmall(),
    cornerMedium = UIConfig.getCornerRadiusMedium(),
    cornerLarge = UIConfig.getCornerRadiusLarge(),
    cornerExtraLarge = UIConfig.getCornerRadiusExtraLarge(),
    dialog = UIConfig.getDialogCornerRadius()
)

private fun buildDynamicTypography(): Typography = buildTypography(
    title = UIConfig.getTitleTextSize(),
    body = UIConfig.getBodyTextSize(),
    caption = UIConfig.getCaptionTextSize(),
    section = UIConfig.getSectionTitleTextSize()
)

// ==================== 主题函数 ====================

/**
 * 无涟漪指示器：当用户关闭「涟漪效果」时作为全局 LocalIndication 提供，
 * 使所有使用 LocalIndication 的可点击组件不再绘制涟漪。
 */
private object NoRippleIndication : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = NoRippleNode()
    override fun hashCode(): Int = -1
    override fun equals(other: Any?): Boolean = other === this
}

private class NoRippleNode : Modifier.Node(), DrawModifierNode {
    override fun ContentDrawScope.draw() {
        drawContent()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UINToolTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    fillBackground: Boolean = true,
    content: @Composable () -> Unit
) {
    // 观察配置版本号：订阅触发本组合重组（快照计算很廉价）
    val configVersion by UIConfig.configVersion.collectAsState()

    val resolvedDark = if (UIConfig.isInitialized()) {
        UIConfig.shouldUseDarkTheme(isSystemInDarkTheme())
    } else {
        darkTheme
    }

    // E-08：configVersion 只负责「订阅 + 重算快照」，三类主题对象按**真实输入**做 remember 键：
    // 改字号只换 typography、改圆角只换 shapes、改单色只换 colorScheme —— 未变的一类保持
    // 同一实例，读该 Local 的组合树不再被连带重组（原实现三者共用 configVersion 粗键，
    // 任何配置保存都会让 ColorScheme/Shapes/Typography 全部换实例）。
    val colorScheme = if (UIConfig.isInitialized()) {
        val colorKey = remember(configVersion, resolvedDark) { colorInputsSnapshot(resolvedDark) }
        remember(colorKey) { buildDynamicColorScheme(UIConfig, resolvedDark) }
    } else if (resolvedDark) DarkColorScheme else LightColorScheme

    val shapes = if (UIConfig.isInitialized()) {
        val shapeKey = remember(configVersion) { shapeInputsSnapshot() }
        remember(shapeKey) { buildDynamicShapes() }
    } else Shapes

    val typography = if (UIConfig.isInitialized()) {
        val typeKey = remember(configVersion) { typographyInputsSnapshot() }
        remember(typeKey) { buildDynamicTypography() }
    } else Typography

    val view = LocalView.current
    val window = (view.context as? Activity)?.window
    SideEffect {
        window?.let {
            val barColor = colorScheme.background.toArgb()
            val statusColor = if (UIConfig.isInitialized()) {
                UIConfig.getInstance().getColorForMode("status_bar", resolvedDark)
            } else barColor
            val navColor = if (UIConfig.isInitialized()) {
                UIConfig.getInstance().getColorForMode("navigation_bar", resolvedDark)
            } else barColor
            it.statusBarColor = statusColor
            it.navigationBarColor = navColor
            it.decorView.setBackgroundColor(barColor)
            WindowCompat.getInsetsController(it, view).apply {
                isAppearanceLightStatusBars = !resolvedDark
                isAppearanceLightNavigationBars = !resolvedDark
            }
        }
    }

    val rippleEnabled = if (UIConfig.isInitialized()) UIConfig.isRippleEnabled() else true
    val rippleConfig: RippleConfiguration? = if (rippleEnabled) RippleConfiguration() else null
    CompositionLocalProvider(LocalRippleConfiguration provides rippleConfig) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            shapes = shapes,
            content = {
                // 根级内容色基准：各页 Scaffold 用 pageBackground()（渐变开启时为 Transparent），
                // m3 contentColorFor(Transparent) 无匹配会回落到 LocalContentColor——其根默认是
                // Color.Black，导致深色下所有未显式着色的 Text（如插件名）渲染成黑色。
                // 这里用 onBackground 作全树兜底：深色=浅字、浅色=深字，就近的 Surface/Card/
                // Button 提供的 contentColor 仍然优先，不受影响。
                CompositionLocalProvider(LocalContentColor provides colorScheme.onBackground) {
                val snackbarHostState = remember { SnackbarHostState() }
                val lifecycleOwner = LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner, snackbarHostState) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_START) {
                            AppToast.bindHost(snackbarHostState)
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                        AppToast.bindHost(snackbarHostState)
                    }
                    onDispose {
                        lifecycleOwner.lifecycle.removeObserver(observer)
                        AppToast.unbindHost(snackbarHostState)
                    }
                }

                BoxWithConstraints(
                    modifier = Modifier.fillMaxSize()
                ) {
                    val isDark = isAppDarkTheme()
                    key(isDark) {
                    val widthPx = with(LocalDensity.current) { maxWidth.toPx() }
                    val heightPx = with(LocalDensity.current) { maxHeight.toPx() }
                    val gradientBrush = gradientBackgroundBrush(widthPx, heightPx)
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .then(
                                if (fillBackground) {
                                    if (gradientBrush != null) Modifier.background(gradientBrush)
                                    else Modifier.background(colorScheme.background)
                                } else {
                                    Modifier
                                }
                            )
                    ) {
                        content()
                        SnackbarHost(
                            hostState = snackbarHostState,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .navigationBarsPadding()
                                .padding(start = 12.dp, end = 12.dp, bottom = 64.dp)
                        ) { data ->
                            Snackbar(
                                snackbarData = data,
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurface,
                                actionColor = MaterialTheme.colorScheme.primary,
                                dismissActionContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    } // key(isDark)
                }
                } // CompositionLocalProvider(LocalContentColor)
            }
        )
    }
}
