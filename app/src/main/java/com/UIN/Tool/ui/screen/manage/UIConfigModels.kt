package com.UIN.Tool.ui.screen.manage

import androidx.compose.ui.graphics.Color
import com.UIN.Tool.utils.AppLog
import com.UIN.Tool.utils.UIConfig
import org.json.JSONObject
import java.io.File

private const val TAG = "UIConfigScreen"

fun safeParseColor(colorString: String): Color {
    return try {
        if (colorString.isNotEmpty() && colorString.startsWith("#") && colorString.length >= 7) {
            Color(android.graphics.Color.parseColor(colorString))
        } else {
            Color(0xFF1A3A4A)
        }
    } catch (e: Exception) {
        Color(0xFF1A3A4A)
    }
}

data class ConfigState(
    val themeMode: String,
    val primaryColor: String,
    val primaryDarkColor: String,
    val primaryLightColor: String,
    val accentColor: String,
    val successColor: String,
    val warningColor: String,
    val errorColor: String,
    val infoColor: String,
    val textPrimaryColor: String,
    val textSecondaryColor: String,
    val textHintColor: String,
    val textPrimaryInverseColor: String,
    val backgroundColor: String,
    val surfaceColor: String,
    val surfaceVariantColor: String,
    val dividerColor: String,
    val glassBackgroundColor: String,
    val disabledColor: String,
    val statusBarColor: String,
    val navigationBarColor: String,
    val navSelectedColor: String,
    val navUnselectedColor: String,
    val primaryColorDark: String,
    val primaryDarkColorDark: String,
    val primaryLightColorDark: String,
    val accentColorDark: String,
    val successColorDark: String,
    val warningColorDark: String,
    val errorColorDark: String,
    val infoColorDark: String,
    val textPrimaryColorDark: String,
    val textSecondaryColorDark: String,
    val textHintColorDark: String,
    val textPrimaryInverseColorDark: String,
    val backgroundColorDark: String,
    val surfaceColorDark: String,
    val surfaceVariantColorDark: String,
    val dividerColorDark: String,
    val glassBackgroundColorDark: String,
    val disabledColorDark: String,
    val statusBarColorDark: String,
    val navigationBarColorDark: String,
    val navSelectedColorDark: String,
    val navUnselectedColorDark: String,
    val cornerRadiusSmall: Float,
    val cornerRadiusMedium: Float,
    val cornerRadiusLarge: Float,
    val cornerRadiusExtraLarge: Float,
    val buttonCornerRadius: Float,
    val cardCornerRadius: Float,
    val dialogCornerRadius: Float,
    val inputCornerRadius: Float,
    val buttonHeight: Float,
    val buttonMinWidth: Float,
    val buttonElevation: Float,
    val cardElevation: Float,
    val cardPadding: Float,
    val spacingSmall: Float,
    val spacingMedium: Float,
    val spacingLarge: Float,
    val iconSizeSmall: Float,
    val iconSizeMedium: Float,
    val iconSizeLarge: Float,
    val progressHeight: Float,
    val titleTextSize: Float,
    val bodyTextSize: Float,
    val captionTextSize: Float,
    val sectionTitleTextSize: Float,
    val enableGlassEffect: Boolean,
    val translucentAlpha: Float = 0.5f,
    val enableRipple: Boolean,
    val enableBold: Boolean,
    val enableGradientBackground: Boolean,
    val gradientMode: String,
    val gradientColor: String,
    val gradientColors: List<String>,
    val gradientFrom: String,
    val gradientTo: String,
    val enableNeumorphism: Boolean,
    val neumorphismIntensity: String,
    val enableNeumorphismInset: Boolean,
    val enableNeumorphismGlow: Boolean,
    val animationSpeed: String,
    val fontFamily: String = "sans-serif",
    val language: String = "system"
)

fun loadConfigFromUIConfig(): ConfigState {
    val uiConfig = UIConfig.getInstance()
    return ConfigState(
        themeMode = uiConfig.getThemeMode(),
        primaryColor = uiConfig.getColorString("primary"),
        primaryDarkColor = uiConfig.getColorString("primary_dark"),
        primaryLightColor = uiConfig.getColorString("primary_light"),
        accentColor = uiConfig.getColorString("accent"),
        successColor = uiConfig.getColorString("success"),
        warningColor = uiConfig.getColorString("warning"),
        errorColor = uiConfig.getColorString("error"),
        infoColor = uiConfig.getColorString("info"),
        textPrimaryColor = uiConfig.getColorString("text_primary"),
        textSecondaryColor = uiConfig.getColorString("text_secondary"),
        textHintColor = uiConfig.getColorString("text_hint"),
        textPrimaryInverseColor = uiConfig.getColorString("text_primary_inverse"),
        backgroundColor = uiConfig.getColorString("background"),
        surfaceColor = uiConfig.getColorString("surface"),
        surfaceVariantColor = uiConfig.getColorString("surface_variant"),
        dividerColor = uiConfig.getColorString("divider"),
        glassBackgroundColor = uiConfig.getColorString("glass_background"),
        disabledColor = uiConfig.getColorString("disabled"),
        statusBarColor = uiConfig.getColorString("status_bar"),
        navigationBarColor = uiConfig.getColorString("navigation_bar"),
        navSelectedColor = uiConfig.getColorString("nav_selected"),
        navUnselectedColor = uiConfig.getColorString("nav_unselected"),
        primaryColorDark = uiConfig.getColorStringDark("primary"),
        primaryDarkColorDark = uiConfig.getColorStringDark("primary_dark"),
        primaryLightColorDark = uiConfig.getColorStringDark("primary_light"),
        accentColorDark = uiConfig.getColorStringDark("accent"),
        successColorDark = uiConfig.getColorStringDark("success"),
        warningColorDark = uiConfig.getColorStringDark("warning"),
        errorColorDark = uiConfig.getColorStringDark("error"),
        infoColorDark = uiConfig.getColorStringDark("info"),
        textPrimaryColorDark = uiConfig.getColorStringDark("text_primary"),
        textSecondaryColorDark = uiConfig.getColorStringDark("text_secondary"),
        textHintColorDark = uiConfig.getColorStringDark("text_hint"),
        textPrimaryInverseColorDark = uiConfig.getColorStringDark("text_primary_inverse"),
        backgroundColorDark = uiConfig.getColorStringDark("background"),
        surfaceColorDark = uiConfig.getColorStringDark("surface"),
        surfaceVariantColorDark = uiConfig.getColorStringDark("surface_variant"),
        dividerColorDark = uiConfig.getColorStringDark("divider"),
        glassBackgroundColorDark = uiConfig.getColorStringDark("glass_background"),
        disabledColorDark = uiConfig.getColorStringDark("disabled"),
        statusBarColorDark = uiConfig.getColorStringDark("status_bar"),
        navigationBarColorDark = uiConfig.getColorStringDark("navigation_bar"),
        navSelectedColorDark = uiConfig.getColorStringDark("nav_selected"),
        navUnselectedColorDark = uiConfig.getColorStringDark("nav_unselected"),
        cornerRadiusSmall = uiConfig.getShape("cornerRadiusSmall"),
        cornerRadiusMedium = uiConfig.getShape("cornerRadiusMedium"),
        cornerRadiusLarge = uiConfig.getShape("cornerRadiusLarge"),
        cornerRadiusExtraLarge = uiConfig.getShape("cornerRadiusExtraLarge"),
        buttonCornerRadius = uiConfig.getShape("buttonCornerRadius"),
        cardCornerRadius = uiConfig.getShape("cardCornerRadius"),
        dialogCornerRadius = uiConfig.getShape("dialogCornerRadius"),
        inputCornerRadius = uiConfig.getShape("inputCornerRadius"),
        buttonHeight = uiConfig.getSize("buttonHeight"),
        buttonMinWidth = uiConfig.getSize("buttonMinWidth"),
        buttonElevation = uiConfig.getSize("buttonElevation"),
        cardElevation = uiConfig.getSize("cardElevation"),
        cardPadding = uiConfig.getSize("cardPadding"),
        spacingSmall = uiConfig.getSize("spacingSmall"),
        spacingMedium = uiConfig.getSize("spacingMedium"),
        spacingLarge = uiConfig.getSize("spacingLarge"),
        iconSizeSmall = uiConfig.getSize("iconSizeSmall"),
        iconSizeMedium = uiConfig.getSize("iconSizeMedium"),
        iconSizeLarge = uiConfig.getSize("iconSizeLarge"),
        progressHeight = uiConfig.getSize("progressHeight"),
        titleTextSize = uiConfig.getSize("titleTextSize"),
        bodyTextSize = uiConfig.getSize("bodyTextSize"),
        captionTextSize = uiConfig.getSize("captionTextSize"),
        sectionTitleTextSize = uiConfig.getSize("sectionTitleTextSize"),
        enableGlassEffect = uiConfig.isGlassEffectEnabled(),
        translucentAlpha = uiConfig.getTranslucentAlpha(),
        enableRipple = uiConfig.isRippleEnabled(),
        enableBold = uiConfig.isBoldEnabled(),
        enableGradientBackground = uiConfig.isGradientBackgroundEnabled(),
        gradientMode = uiConfig.getGradientMode(),
        gradientColor = if (uiConfig.shouldUseDarkTheme()) uiConfig.getGradientColorStringDark() else uiConfig.getGradientColorString(),
        gradientColors = uiConfig.getGradientColorsString(),
        gradientFrom = uiConfig.getGradientFrom(),
        gradientTo = uiConfig.getGradientTo(),
        enableNeumorphism = uiConfig.isNeumorphismEnabled(),
        neumorphismIntensity = uiConfig.getNeumorphismIntensity(),
        enableNeumorphismInset = uiConfig.isNeumorphismInsetEnabled(),
        enableNeumorphismGlow = uiConfig.isNeumorphismGlowEnabled(),
        animationSpeed = uiConfig.getAnimationSpeed(),
        fontFamily = uiConfig.getFontFamily(),
        language = uiConfig.getLanguage()
    )
}

fun saveConfigToUIConfig(config: ConfigState) {
    AppLog.i(TAG, "Saving UI config...")

    val uiConfig = UIConfig.getInstance()
    val saveWasDark = uiConfig.shouldUseDarkTheme()
    val full = JSONObject()

    val theme = JSONObject()
    theme.put("primary", config.primaryColor)
    theme.put("primary_dark", config.primaryDarkColor)
    theme.put("primary_light", config.primaryLightColor)
    theme.put("accent", config.accentColor)
    theme.put("success", config.successColor)
    theme.put("warning", config.warningColor)
    theme.put("error", config.errorColor)
    theme.put("info", config.infoColor)
    theme.put("text_primary", config.textPrimaryColor)
    theme.put("text_secondary", config.textSecondaryColor)
    theme.put("text_hint", config.textHintColor)
    theme.put("text_primary_inverse", config.textPrimaryInverseColor)
    theme.put("background", config.backgroundColor)
    theme.put("surface", config.surfaceColor)
    theme.put("surface_variant", config.surfaceVariantColor)
    theme.put("divider", config.dividerColor)
    theme.put("glass_background", config.glassBackgroundColor)
    theme.put("disabled", config.disabledColor)
    theme.put("status_bar", config.statusBarColor)
    theme.put("navigation_bar", config.navigationBarColor)
    theme.put("nav_selected", config.navSelectedColor)
    theme.put("nav_unselected", config.navUnselectedColor)
    full.put("theme", theme)

    val themeDark = JSONObject()
    themeDark.put("primary", config.primaryColorDark)
    themeDark.put("primary_dark", config.primaryDarkColorDark)
    themeDark.put("primary_light", config.primaryLightColorDark)
    themeDark.put("accent", config.accentColorDark)
    themeDark.put("success", config.successColorDark)
    themeDark.put("warning", config.warningColorDark)
    themeDark.put("error", config.errorColorDark)
    themeDark.put("info", config.infoColorDark)
    themeDark.put("text_primary", config.textPrimaryColorDark)
    themeDark.put("text_secondary", config.textSecondaryColorDark)
    themeDark.put("text_hint", config.textHintColorDark)
    themeDark.put("text_primary_inverse", config.textPrimaryInverseColorDark)
    themeDark.put("background", config.backgroundColorDark)
    themeDark.put("surface", config.surfaceColorDark)
    themeDark.put("surface_variant", config.surfaceVariantColorDark)
    themeDark.put("divider", config.dividerColorDark)
    themeDark.put("glass_background", config.glassBackgroundColorDark)
    themeDark.put("disabled", config.disabledColorDark)
    themeDark.put("status_bar", config.statusBarColorDark)
    themeDark.put("navigation_bar", config.navigationBarColorDark)
    themeDark.put("nav_selected", config.navSelectedColorDark)
    themeDark.put("nav_unselected", config.navUnselectedColorDark)
    full.put("theme_dark", themeDark)

    val shape = JSONObject()
    shape.put("cornerRadiusSmall", config.cornerRadiusSmall.toInt())
    shape.put("cornerRadiusMedium", config.cornerRadiusMedium.toInt())
    shape.put("cornerRadiusLarge", config.cornerRadiusLarge.toInt())
    shape.put("cornerRadiusExtraLarge", config.cornerRadiusExtraLarge.toInt())
    shape.put("buttonCornerRadius", config.buttonCornerRadius.toInt())
    shape.put("cardCornerRadius", config.cardCornerRadius.toInt())
    shape.put("dialogCornerRadius", config.dialogCornerRadius.toInt())
    shape.put("inputCornerRadius", config.inputCornerRadius.toInt())
    full.put("shape", shape)

    val size = JSONObject()
    size.put("buttonHeight", config.buttonHeight.toInt())
    size.put("buttonMinWidth", config.buttonMinWidth.toInt())
    size.put("buttonElevation", config.buttonElevation.toInt())
    size.put("cardElevation", config.cardElevation.toInt())
    size.put("cardPadding", config.cardPadding.toInt())
    size.put("spacingSmall", config.spacingSmall.toInt())
    size.put("spacingMedium", config.spacingMedium.toInt())
    size.put("spacingLarge", config.spacingLarge.toInt())
    size.put("iconSizeSmall", config.iconSizeSmall.toInt())
    size.put("iconSizeMedium", config.iconSizeMedium.toInt())
    size.put("iconSizeLarge", config.iconSizeLarge.toInt())
    size.put("progressHeight", config.progressHeight.toInt())
    size.put("titleTextSize", config.titleTextSize.toInt())
    size.put("bodyTextSize", config.bodyTextSize.toInt())
    size.put("captionTextSize", config.captionTextSize.toInt())
    size.put("sectionTitleTextSize", config.sectionTitleTextSize.toInt())
    full.put("size", size)

    val font = JSONObject()
    font.put("fontFamily", config.fontFamily)
    font.put("enableBold", config.enableBold)
    font.put("language", config.language)
    full.put("font", font)

    val experimental = JSONObject()
    experimental.put("enableGlassEffect", config.enableGlassEffect)
    experimental.put("translucentAlpha", config.translucentAlpha.toDouble())
    experimental.put("enableRipple", config.enableRipple)
    experimental.put("enableNeumorphism", config.enableNeumorphism)
    experimental.put("enableNeumorphismInset", config.enableNeumorphismInset)
    experimental.put("enableNeumorphismGlow", config.enableNeumorphismGlow)
    experimental.put("neumorphismIntensity", config.neumorphismIntensity)
    experimental.put("animationSpeed", config.animationSpeed)
    full.put("experimental", experimental)

    val gradient = JSONObject()
    gradient.put("enabled", config.enableGradientBackground)
    val existingGradient = uiConfig.getConfig().optJSONObject("gradient")

    if (saveWasDark) {
        gradient.put("color_dark", config.gradientColor)
        gradient.put("color", existingGradient?.optString("color", "#FFC4D6DF") ?: "#FFC4D6DF")
        gradient.put("mode_dark", config.gradientMode)
        gradient.put("mode", existingGradient?.optString("mode", UIConfig.GRADIENT_MODE_SINGLE) ?: UIConfig.GRADIENT_MODE_SINGLE)
        val darkColors = org.json.JSONArray()
        config.gradientColors.forEach { darkColors.put(it) }
        gradient.put("colors_dark", darkColors)
        gradient.put("colors", existingGradient?.optJSONArray("colors") ?: org.json.JSONArray())
        gradient.put("from_dark", config.gradientFrom)
        gradient.put("from", existingGradient?.optString("from", UIConfig.GRADIENT_DIR_BOTTOM_RIGHT) ?: UIConfig.GRADIENT_DIR_BOTTOM_RIGHT)
        gradient.put("to_dark", config.gradientTo)
        gradient.put("to", existingGradient?.optString("to", UIConfig.GRADIENT_DIR_TOP_LEFT) ?: UIConfig.GRADIENT_DIR_TOP_LEFT)
    } else {
        gradient.put("color", config.gradientColor)
        gradient.put("color_dark", existingGradient?.optString("color_dark", "#FF4C4F51") ?: "#FF4C4F51")
        gradient.put("mode", config.gradientMode)
        gradient.put("mode_dark", existingGradient?.optString("mode_dark", UIConfig.GRADIENT_MODE_SINGLE) ?: UIConfig.GRADIENT_MODE_SINGLE)
        val lightColors = org.json.JSONArray()
        config.gradientColors.forEach { lightColors.put(it) }
        gradient.put("colors", lightColors)
        gradient.put("colors_dark", existingGradient?.optJSONArray("colors_dark") ?: org.json.JSONArray())
        gradient.put("from", config.gradientFrom)
        gradient.put("from_dark", existingGradient?.optString("from_dark", UIConfig.GRADIENT_DIR_BOTTOM_RIGHT) ?: UIConfig.GRADIENT_DIR_BOTTOM_RIGHT)
        gradient.put("to", config.gradientTo)
        gradient.put("to_dark", existingGradient?.optString("to_dark", UIConfig.GRADIENT_DIR_TOP_LEFT) ?: UIConfig.GRADIENT_DIR_TOP_LEFT)
    }
    full.put("gradient", gradient)

    uiConfig.setThemeMode(config.themeMode)
    uiConfig.applyJson(full)

    AppLog.success(TAG, "UI config saved")
}

fun ConfigState.paletteColorValue(key: String): String {
    return when {
        key.startsWith("dark:") -> when {
            key.endsWith(":primary") -> primaryColorDark
            key.endsWith(":primary_dark") -> primaryDarkColorDark
            key.endsWith(":primary_light") -> primaryLightColorDark
            key.endsWith(":accent") -> accentColorDark
            key.endsWith(":success") -> successColorDark
            key.endsWith(":warning") -> warningColorDark
            key.endsWith(":error") -> errorColorDark
            key.endsWith(":info") -> infoColorDark
            key.endsWith(":text_primary") -> textPrimaryColorDark
            key.endsWith(":text_secondary") -> textSecondaryColorDark
            key.endsWith(":text_hint") -> textHintColorDark
            key.endsWith(":text_primary_inverse") -> textPrimaryInverseColorDark
            key.endsWith(":background") -> backgroundColorDark
            key.endsWith(":surface") -> surfaceColorDark
            key.endsWith(":surface_variant") -> surfaceVariantColorDark
            key.endsWith(":divider") -> dividerColorDark
            key.endsWith(":glass_background") -> glassBackgroundColorDark
            key.endsWith(":disabled") -> disabledColorDark
            key.endsWith(":status_bar") -> statusBarColorDark
            key.endsWith(":navigation_bar") -> navigationBarColorDark
            key.endsWith(":nav_selected") -> navSelectedColorDark
            key.endsWith(":nav_unselected") -> navUnselectedColorDark
            else -> "#FF0000"
        }
        else -> when {
            key.endsWith(":primary") -> primaryColor
            key.endsWith(":primary_dark") -> primaryDarkColor
            key.endsWith(":primary_light") -> primaryLightColor
            key.endsWith(":accent") -> accentColor
            key.endsWith(":success") -> successColor
            key.endsWith(":warning") -> warningColor
            key.endsWith(":error") -> errorColor
            key.endsWith(":info") -> infoColor
            key.endsWith(":text_primary") -> textPrimaryColor
            key.endsWith(":text_secondary") -> textSecondaryColor
            key.endsWith(":text_hint") -> textHintColor
            key.endsWith(":text_primary_inverse") -> textPrimaryInverseColor
            key.endsWith(":background") -> backgroundColor
            key.endsWith(":surface") -> surfaceColor
            key.endsWith(":surface_variant") -> surfaceVariantColor
            key.endsWith(":divider") -> dividerColor
            key.endsWith(":glass_background") -> glassBackgroundColor
            key.endsWith(":disabled") -> disabledColor
            key.endsWith(":status_bar") -> statusBarColor
            key.endsWith(":navigation_bar") -> navigationBarColor
            key.endsWith(":nav_selected") -> navSelectedColor
            key.endsWith(":nav_unselected") -> navUnselectedColor
            else -> "#FF0000"
        }
    }
}

fun ConfigState.updatePaletteColor(key: String, value: String): ConfigState {
    return when {
        key.startsWith("dark:") -> when {
            key.endsWith(":primary") -> copy(primaryColorDark = value)
            key.endsWith(":primary_dark") -> copy(primaryDarkColorDark = value)
            key.endsWith(":primary_light") -> copy(primaryLightColorDark = value)
            key.endsWith(":accent") -> copy(accentColorDark = value)
            key.endsWith(":success") -> copy(successColorDark = value)
            key.endsWith(":warning") -> copy(warningColorDark = value)
            key.endsWith(":error") -> copy(errorColorDark = value)
            key.endsWith(":info") -> copy(infoColorDark = value)
            key.endsWith(":text_primary") -> copy(textPrimaryColorDark = value)
            key.endsWith(":text_secondary") -> copy(textSecondaryColorDark = value)
            key.endsWith(":text_hint") -> copy(textHintColorDark = value)
            key.endsWith(":text_primary_inverse") -> copy(textPrimaryInverseColorDark = value)
            key.endsWith(":background") -> copy(backgroundColorDark = value)
            key.endsWith(":surface") -> copy(surfaceColorDark = value)
            key.endsWith(":surface_variant") -> copy(surfaceVariantColorDark = value)
            key.endsWith(":divider") -> copy(dividerColorDark = value)
            key.endsWith(":glass_background") -> copy(glassBackgroundColorDark = value)
            key.endsWith(":disabled") -> copy(disabledColorDark = value)
            key.endsWith(":status_bar") -> copy(statusBarColorDark = value)
            key.endsWith(":navigation_bar") -> copy(navigationBarColorDark = value)
            key.endsWith(":nav_selected") -> copy(navSelectedColorDark = value)
            key.endsWith(":nav_unselected") -> copy(navUnselectedColorDark = value)
            else -> this
        }
        else -> when {
            key.endsWith(":primary") -> copy(primaryColor = value)
            key.endsWith(":primary_dark") -> copy(primaryDarkColor = value)
            key.endsWith(":primary_light") -> copy(primaryLightColor = value)
            key.endsWith(":accent") -> copy(accentColor = value)
            key.endsWith(":success") -> copy(successColor = value)
            key.endsWith(":warning") -> copy(warningColor = value)
            key.endsWith(":error") -> copy(errorColor = value)
            key.endsWith(":info") -> copy(infoColor = value)
            key.endsWith(":text_primary") -> copy(textPrimaryColor = value)
            key.endsWith(":text_secondary") -> copy(textSecondaryColor = value)
            key.endsWith(":text_hint") -> copy(textHintColor = value)
            key.endsWith(":text_primary_inverse") -> copy(textPrimaryInverseColor = value)
            key.endsWith(":background") -> copy(backgroundColor = value)
            key.endsWith(":surface") -> copy(surfaceColor = value)
            key.endsWith(":surface_variant") -> copy(surfaceVariantColor = value)
            key.endsWith(":divider") -> copy(dividerColor = value)
            key.endsWith(":glass_background") -> copy(glassBackgroundColor = value)
            key.endsWith(":disabled") -> copy(disabledColor = value)
            key.endsWith(":status_bar") -> copy(statusBarColor = value)
            key.endsWith(":navigation_bar") -> copy(navigationBarColor = value)
            key.endsWith(":nav_selected") -> copy(navSelectedColor = value)
            key.endsWith(":nav_unselected") -> copy(navUnselectedColor = value)
            else -> this
        }
    }
}
