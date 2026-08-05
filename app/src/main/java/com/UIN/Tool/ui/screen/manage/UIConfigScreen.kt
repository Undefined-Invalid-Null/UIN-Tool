// app/src/main/java/com/UIN/Tool/ui/screen/manage/UIConfigScreen.kt
package com.UIN.Tool.ui.screen.manage

import com.UIN.Tool.R
import com.UIN.Tool.utils.Str
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.UIN.Tool.ui.components.FullColorPickerDialog
import com.UIN.Tool.ui.components.UIComponents
import com.UIN.Tool.utils.AppLog
import com.UIN.Tool.utils.AppToast
import com.UIN.Tool.utils.FileUtils
import com.UIN.Tool.utils.UIConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import com.UIN.Tool.ui.theme.AppColors
import com.UIN.Tool.ui.theme.AppDimens

private const val TAG = "UIConfigScreen"

private fun safeParseColor(colorString: String): Color {
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

private data class ConfigState(
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
    val enableRipple: Boolean,
    val enableBold: Boolean
)

private fun loadConfigFromUIConfig(): ConfigState {
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
        enableRipple = uiConfig.isRippleEnabled(),
        enableBold = uiConfig.isBoldEnabled()
    )
}

private fun saveConfigToUIConfig(config: ConfigState) {
    AppLog.i(TAG, Str.get(R.string.saving_ui_config_to_uiconfig))

    val uiConfig = UIConfig.getInstance()

    uiConfig.updateColor("primary", config.primaryColor)
    uiConfig.updateColor("primary_dark", config.primaryDarkColor)
    uiConfig.updateColor("primary_light", config.primaryLightColor)
    uiConfig.updateColor("accent", config.accentColor)
    uiConfig.updateColor("success", config.successColor)
    uiConfig.updateColor("warning", config.warningColor)
    uiConfig.updateColor("error", config.errorColor)
    uiConfig.updateColor("info", config.infoColor)
    uiConfig.updateColor("text_primary", config.textPrimaryColor)
    uiConfig.updateColor("text_secondary", config.textSecondaryColor)
    uiConfig.updateColor("text_hint", config.textHintColor)
    uiConfig.updateColor("text_primary_inverse", config.textPrimaryInverseColor)
    uiConfig.updateColor("background", config.backgroundColor)
    uiConfig.updateColor("surface", config.surfaceColor)
    uiConfig.updateColor("surface_variant", config.surfaceVariantColor)
    uiConfig.updateColor("divider", config.dividerColor)
    uiConfig.updateColor("glass_background", config.glassBackgroundColor)
    uiConfig.updateColor("disabled", config.disabledColor)
    uiConfig.updateColor("status_bar", config.statusBarColor)
    uiConfig.updateColor("navigation_bar", config.navigationBarColor)
    uiConfig.updateColor("nav_selected", config.navSelectedColor)
    uiConfig.updateColor("nav_unselected", config.navUnselectedColor)

    uiConfig.updateColorDark("primary", config.primaryColorDark)
    uiConfig.updateColorDark("primary_dark", config.primaryDarkColorDark)
    uiConfig.updateColorDark("primary_light", config.primaryLightColorDark)
    uiConfig.updateColorDark("accent", config.accentColorDark)
    uiConfig.updateColorDark("success", config.successColorDark)
    uiConfig.updateColorDark("warning", config.warningColorDark)
    uiConfig.updateColorDark("error", config.errorColorDark)
    uiConfig.updateColorDark("info", config.infoColorDark)
    uiConfig.updateColorDark("text_primary", config.textPrimaryColorDark)
    uiConfig.updateColorDark("text_secondary", config.textSecondaryColorDark)
    uiConfig.updateColorDark("text_hint", config.textHintColorDark)
    uiConfig.updateColorDark("text_primary_inverse", config.textPrimaryInverseColorDark)
    uiConfig.updateColorDark("background", config.backgroundColorDark)
    uiConfig.updateColorDark("surface", config.surfaceColorDark)
    uiConfig.updateColorDark("surface_variant", config.surfaceVariantColorDark)
    uiConfig.updateColorDark("divider", config.dividerColorDark)
    uiConfig.updateColorDark("glass_background", config.glassBackgroundColorDark)
    uiConfig.updateColorDark("disabled", config.disabledColorDark)
    uiConfig.updateColorDark("status_bar", config.statusBarColorDark)
    uiConfig.updateColorDark("navigation_bar", config.navigationBarColorDark)
    uiConfig.updateColorDark("nav_selected", config.navSelectedColorDark)
    uiConfig.updateColorDark("nav_unselected", config.navUnselectedColorDark)

    uiConfig.setThemeMode(config.themeMode)

    uiConfig.updateShape("cornerRadiusSmall", config.cornerRadiusSmall.toInt())
    uiConfig.updateShape("cornerRadiusMedium", config.cornerRadiusMedium.toInt())
    uiConfig.updateShape("cornerRadiusLarge", config.cornerRadiusLarge.toInt())
    uiConfig.updateShape("cornerRadiusExtraLarge", config.cornerRadiusExtraLarge.toInt())
    uiConfig.updateShape("buttonCornerRadius", config.buttonCornerRadius.toInt())
    uiConfig.updateShape("cardCornerRadius", config.cardCornerRadius.toInt())
    uiConfig.updateShape("dialogCornerRadius", config.dialogCornerRadius.toInt())
    uiConfig.updateShape("inputCornerRadius", config.inputCornerRadius.toInt())

    uiConfig.updateSize("buttonHeight", config.buttonHeight.toInt())
    uiConfig.updateSize("buttonMinWidth", config.buttonMinWidth.toInt())
    uiConfig.updateSize("buttonElevation", config.buttonElevation.toInt())
    uiConfig.updateSize("cardElevation", config.cardElevation.toInt())
    uiConfig.updateSize("cardPadding", config.cardPadding.toInt())
    uiConfig.updateSize("spacingSmall", config.spacingSmall.toInt())
    uiConfig.updateSize("spacingMedium", config.spacingMedium.toInt())
    uiConfig.updateSize("spacingLarge", config.spacingLarge.toInt())
    uiConfig.updateSize("iconSizeSmall", config.iconSizeSmall.toInt())
    uiConfig.updateSize("iconSizeMedium", config.iconSizeMedium.toInt())
    uiConfig.updateSize("iconSizeLarge", config.iconSizeLarge.toInt())
    uiConfig.updateSize("progressHeight", config.progressHeight.toInt())

    uiConfig.updateSize("titleTextSize", config.titleTextSize.toInt())
    uiConfig.updateSize("bodyTextSize", config.bodyTextSize.toInt())
    uiConfig.updateSize("captionTextSize", config.captionTextSize.toInt())
    uiConfig.updateSize("sectionTitleTextSize", config.sectionTitleTextSize.toInt())

    uiConfig.updateBoolean("enableGlassEffect", config.enableGlassEffect)
    uiConfig.updateBoolean("enableRipple", config.enableRipple)
    uiConfig.updateBoolean("enableBold", config.enableBold)

    uiConfig.saveConfig()

    AppLog.success(TAG, Str.get(R.string.ui_config_saved))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UIConfigScreen(
    navController: NavController,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val cardElevation = if (AppColors.glassEnabled()) 0.dp else 1.dp

    var configState by remember { mutableStateOf(loadConfigFromUIConfig()) }
    var isSaving by remember { mutableStateOf(false) }
    var showColorPicker by remember { mutableStateOf(false) }
    var selectedColorKey by remember { mutableStateOf<String?>(null) }
    var showResetDialog by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(0) }
    var saveMessage by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        AppLog.i(TAG, Str.get(R.string.loading_config_from_uiconfig))
        configState = loadConfigFromUIConfig()
        AppLog.success(TAG, Str.get(R.string.config_loaded))
    }

    fun saveConfig() {
        if (isSaving) {
            AppLog.d(TAG, Str.get(R.string.save_in_progress_skipping))
            return
        }

        isSaving = true
        AppLog.i(TAG, Str.get(R.string.user_tapped_save))

        try {
            saveConfigToUIConfig(configState)
            saveMessage = Str.get(R.string.config_saved)
            AppToast.success(context, Str.get(R.string.config_saved))
        } catch (e: Exception) {
            AppLog.e(TAG, Str.get(R.string.save_exception), e)
            saveMessage = Str.get(R.string.save_failed_e_message, e.message)
            AppToast.error(context, Str.get(R.string.save_exception_e_message, e.message))
        } finally {
            scope.launch {
                delay(500)
                isSaving = false
                delay(2000)
                saveMessage = null
            }
        }
    }

    fun resetConfig() {
        AppLog.i(TAG, Str.get(R.string.reset_config))
        val uiConfig = UIConfig.getInstance()
        uiConfig.resetToDefault()
        configState = loadConfigFromUIConfig()
        AppToast.info(context, Str.get(R.string.reset_to_default_config))
        showResetDialog = false
        AppLog.success(TAG, Str.get(R.string.config_reset))
    }

    fun paletteColorValue(key: String): String {
        val dark = key.startsWith("dark:")
        val baseKey = key.substringAfter(':')
        return when (baseKey) {
            "primary" -> if (dark) configState.primaryColorDark else configState.primaryColor
            "primary_dark" -> if (dark) configState.primaryDarkColorDark else configState.primaryDarkColor
            "primary_light" -> if (dark) configState.primaryLightColorDark else configState.primaryLightColor
            "accent" -> if (dark) configState.accentColorDark else configState.accentColor
            "success" -> if (dark) configState.successColorDark else configState.successColor
            "warning" -> if (dark) configState.warningColorDark else configState.warningColor
            "error" -> if (dark) configState.errorColorDark else configState.errorColor
            "info" -> if (dark) configState.infoColorDark else configState.infoColor
            "text_primary" -> if (dark) configState.textPrimaryColorDark else configState.textPrimaryColor
            "text_secondary" -> if (dark) configState.textSecondaryColorDark else configState.textSecondaryColor
            "text_hint" -> if (dark) configState.textHintColorDark else configState.textHintColor
            "text_primary_inverse" -> if (dark) configState.textPrimaryInverseColorDark else configState.textPrimaryInverseColor
            "background" -> if (dark) configState.backgroundColorDark else configState.backgroundColor
            "surface" -> if (dark) configState.surfaceColorDark else configState.surfaceColor
            "surface_variant" -> if (dark) configState.surfaceVariantColorDark else configState.surfaceVariantColor
            "divider" -> if (dark) configState.dividerColorDark else configState.dividerColor
            "glass_background" -> if (dark) configState.glassBackgroundColorDark else configState.glassBackgroundColor
            "disabled" -> if (dark) configState.disabledColorDark else configState.disabledColor
            "status_bar" -> if (dark) configState.statusBarColorDark else configState.statusBarColor
            "navigation_bar" -> if (dark) configState.navigationBarColorDark else configState.navigationBarColor
            "nav_selected" -> if (dark) configState.navSelectedColorDark else configState.navSelectedColor
            "nav_unselected" -> if (dark) configState.navUnselectedColorDark else configState.navUnselectedColor
            else -> "#FFFFFFFF"
        }
    }

    fun updatePaletteColor(key: String, value: String) {
        val dark = key.startsWith("dark:")
        val baseKey = key.substringAfter(':')
        configState = when (baseKey) {
            "primary" -> if (dark) configState.copy(primaryColorDark = value) else configState.copy(primaryColor = value)
            "primary_dark" -> if (dark) configState.copy(primaryDarkColorDark = value) else configState.copy(primaryDarkColor = value)
            "primary_light" -> if (dark) configState.copy(primaryLightColorDark = value) else configState.copy(primaryLightColor = value)
            "accent" -> if (dark) configState.copy(accentColorDark = value) else configState.copy(accentColor = value)
            "success" -> if (dark) configState.copy(successColorDark = value) else configState.copy(successColor = value)
            "warning" -> if (dark) configState.copy(warningColorDark = value) else configState.copy(warningColor = value)
            "error" -> if (dark) configState.copy(errorColorDark = value) else configState.copy(errorColor = value)
            "info" -> if (dark) configState.copy(infoColorDark = value) else configState.copy(infoColor = value)
            "text_primary" -> if (dark) configState.copy(textPrimaryColorDark = value) else configState.copy(textPrimaryColor = value)
            "text_secondary" -> if (dark) configState.copy(textSecondaryColorDark = value) else configState.copy(textSecondaryColor = value)
            "text_hint" -> if (dark) configState.copy(textHintColorDark = value) else configState.copy(textHintColor = value)
            "text_primary_inverse" -> if (dark) configState.copy(textPrimaryInverseColorDark = value) else configState.copy(textPrimaryInverseColor = value)
            "background" -> if (dark) configState.copy(backgroundColorDark = value) else configState.copy(backgroundColor = value)
            "surface" -> if (dark) configState.copy(surfaceColorDark = value) else configState.copy(surfaceColor = value)
            "surface_variant" -> if (dark) configState.copy(surfaceVariantColorDark = value) else configState.copy(surfaceVariantColor = value)
            "divider" -> if (dark) configState.copy(dividerColorDark = value) else configState.copy(dividerColor = value)
            "glass_background" -> if (dark) configState.copy(glassBackgroundColorDark = value) else configState.copy(glassBackgroundColor = value)
            "disabled" -> if (dark) configState.copy(disabledColorDark = value) else configState.copy(disabledColor = value)
            "status_bar" -> if (dark) configState.copy(statusBarColorDark = value) else configState.copy(statusBarColor = value)
            "navigation_bar" -> if (dark) configState.copy(navigationBarColorDark = value) else configState.copy(navigationBarColor = value)
            "nav_selected" -> if (dark) configState.copy(navSelectedColorDark = value) else configState.copy(navSelectedColor = value)
            "nav_unselected" -> if (dark) configState.copy(navUnselectedColorDark = value) else configState.copy(navUnselectedColor = value)
            else -> configState
        }
    }

    fun importConfig(uri: Uri) {
        try {
            AppLog.i(TAG, Str.get(R.string.start_importing_ui_config))
            val tempFile = File(context.cacheDir, "ui_config_import.json")
            if (FileUtils.copyUriToFile(context, uri, tempFile)) {
                val json = tempFile.readText()
                val obj = JSONObject(json)

                val uiConfig = UIConfig.getInstance()
                val theme = obj.optJSONObject("theme")
                if (theme != null) {
                    val keys = theme.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        val value = theme.getString(key)
                        when (key) {
                            "primary" -> uiConfig.updateColor("primary", value)
                            "primary_dark" -> uiConfig.updateColor("primary_dark", value)
                            "primary_light" -> uiConfig.updateColor("primary_light", value)
                            "accent" -> uiConfig.updateColor("accent", value)
                            "success" -> uiConfig.updateColor("success", value)
                            "warning" -> uiConfig.updateColor("warning", value)
                            "error" -> uiConfig.updateColor("error", value)
                            "info" -> uiConfig.updateColor("info", value)
                            "text_primary" -> uiConfig.updateColor("text_primary", value)
                            "text_secondary" -> uiConfig.updateColor("text_secondary", value)
                            "text_hint" -> uiConfig.updateColor("text_hint", value)
                            "text_primary_inverse" -> uiConfig.updateColor("text_primary_inverse", value)
                            "background" -> uiConfig.updateColor("background", value)
                            "surface" -> uiConfig.updateColor("surface", value)
                            "surface_variant" -> uiConfig.updateColor("surface_variant", value)
                            "divider" -> uiConfig.updateColor("divider", value)
                            "glass_background" -> uiConfig.updateColor("glass_background", value)
                            "disabled" -> uiConfig.updateColor("disabled", value)
                            "status_bar" -> uiConfig.updateColor("status_bar", value)
                            "navigation_bar" -> uiConfig.updateColor("navigation_bar", value)
                            "nav_selected" -> uiConfig.updateColor("nav_selected", value)
                            "nav_unselected" -> uiConfig.updateColor("nav_unselected", value)
                        }
                    }
                }

                val themeDark = obj.optJSONObject("theme_dark")
                if (themeDark != null) {
                    val darkKeys = themeDark.keys()
                    while (darkKeys.hasNext()) {
                        val key = darkKeys.next()
                        val value = themeDark.getString(key)
                        when (key) {
                            "primary" -> uiConfig.updateColorDark("primary", value)
                            "primary_dark" -> uiConfig.updateColorDark("primary_dark", value)
                            "primary_light" -> uiConfig.updateColorDark("primary_light", value)
                            "accent" -> uiConfig.updateColorDark("accent", value)
                            "success" -> uiConfig.updateColorDark("success", value)
                            "warning" -> uiConfig.updateColorDark("warning", value)
                            "error" -> uiConfig.updateColorDark("error", value)
                            "info" -> uiConfig.updateColorDark("info", value)
                            "text_primary" -> uiConfig.updateColorDark("text_primary", value)
                            "text_secondary" -> uiConfig.updateColorDark("text_secondary", value)
                            "text_hint" -> uiConfig.updateColorDark("text_hint", value)
                            "text_primary_inverse" -> uiConfig.updateColorDark("text_primary_inverse", value)
                            "background" -> uiConfig.updateColorDark("background", value)
                            "surface" -> uiConfig.updateColorDark("surface", value)
                            "surface_variant" -> uiConfig.updateColorDark("surface_variant", value)
                            "divider" -> uiConfig.updateColorDark("divider", value)
                            "glass_background" -> uiConfig.updateColorDark("glass_background", value)
                            "disabled" -> uiConfig.updateColorDark("disabled", value)
                            "status_bar" -> uiConfig.updateColorDark("status_bar", value)
                            "navigation_bar" -> uiConfig.updateColorDark("navigation_bar", value)
                            "nav_selected" -> uiConfig.updateColorDark("nav_selected", value)
                            "nav_unselected" -> uiConfig.updateColorDark("nav_unselected", value)
                        }
                    }
                }

                val importedThemeMode = obj.optString("theme_mode", UIConfig.THEME_MODE_SYSTEM)
                uiConfig.setThemeMode(importedThemeMode)

                val shape = obj.optJSONObject("shape")
                if (shape != null) {
                    uiConfig.updateShape("cornerRadiusSmall", shape.optInt("cornerRadiusSmall", 8))
                    uiConfig.updateShape("cornerRadiusMedium", shape.optInt("cornerRadiusMedium", 12))
                    uiConfig.updateShape("cornerRadiusLarge", shape.optInt("cornerRadiusLarge", 16))
                    uiConfig.updateShape("cornerRadiusExtraLarge", shape.optInt("cornerRadiusExtraLarge", 24))
                    uiConfig.updateShape("buttonCornerRadius", shape.optInt("buttonCornerRadius", 12))
                    uiConfig.updateShape("cardCornerRadius", shape.optInt("cardCornerRadius", 16))
                    uiConfig.updateShape("dialogCornerRadius", shape.optInt("dialogCornerRadius", 20))
                    uiConfig.updateShape("inputCornerRadius", shape.optInt("inputCornerRadius", 8))
                }

                val size = obj.optJSONObject("size")
                if (size != null) {
                    uiConfig.updateSize("buttonHeight", size.optInt("buttonHeight", 44))
                    uiConfig.updateSize("buttonMinWidth", size.optInt("buttonMinWidth", 80))
                    uiConfig.updateSize("buttonElevation", size.optInt("buttonElevation", 2))
                    uiConfig.updateSize("cardElevation", size.optInt("cardElevation", 4))
                    uiConfig.updateSize("cardPadding", size.optInt("cardPadding", 16))
                    uiConfig.updateSize("spacingSmall", size.optInt("spacingSmall", 4))
                    uiConfig.updateSize("spacingMedium", size.optInt("spacingMedium", 8))
                    uiConfig.updateSize("spacingLarge", size.optInt("spacingLarge", 16))
                    uiConfig.updateSize("iconSizeSmall", size.optInt("iconSizeSmall", 16))
                    uiConfig.updateSize("iconSizeMedium", size.optInt("iconSizeMedium", 20))
                    uiConfig.updateSize("iconSizeLarge", size.optInt("iconSizeLarge", 24))
                    uiConfig.updateSize("progressHeight", size.optInt("progressHeight", 4))
                    uiConfig.updateSize("titleTextSize", size.optInt("titleTextSize", 20))
                    uiConfig.updateSize("bodyTextSize", size.optInt("bodyTextSize", 14))
                    uiConfig.updateSize("captionTextSize", size.optInt("captionTextSize", 12))
                    uiConfig.updateSize("sectionTitleTextSize", size.optInt("sectionTitleTextSize", 18))
                }

                val experimental = obj.optJSONObject("experimental")
                if (experimental != null) {
                    uiConfig.updateBoolean("enableGlassEffect", experimental.optBoolean("enableGlassEffect", true))
                    uiConfig.updateBoolean("enableRipple", experimental.optBoolean("enableRipple", true))
                }

                val font = obj.optJSONObject("font")
                if (font != null) {
                    uiConfig.updateBoolean("enableBold", font.optBoolean("enableBold", true))
                }

                uiConfig.saveConfig()
                configState = loadConfigFromUIConfig()

                AppToast.success(context, Str.get(R.string.config_imported))
                tempFile.delete()
                AppLog.success(TAG, Str.get(R.string.ui_config_imported_successfully))
            }
        } catch (e: Exception) {
            AppLog.e(TAG, Str.get(R.string.failed_to_import_config), e)
            AppToast.error(context, Str.get(R.string.import_failed_e_message, e.message))
        }
    }

    fun exportConfig(uri: Uri) {
        try {
            AppLog.i(TAG, Str.get(R.string.start_exporting_ui_config))
            val uiConfig = UIConfig.getInstance()
            val json = uiConfig.getConfig().toString(4)
            context.contentResolver.openOutputStream(uri)?.use { output ->
                output.write(json.toByteArray())
            }
            AppToast.success(context, Str.get(R.string.config_exported))
            AppLog.success(TAG, Str.get(R.string.ui_config_exported_successfully))
        } catch (e: Exception) {
            AppLog.e(TAG, Str.get(R.string.failed_to_export_config), e)
            AppToast.error(context, Str.get(R.string.export_failed_e_message, e.message))
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) importConfig(uri)
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) exportConfig(uri)
    }

    Scaffold(
        topBar = {
            UIComponents.ManageTopAppBar(
                titleText = Str.get(R.string.ui_customization),
                onBack = onBack,
                actions = {
                    UIComponents.IconButton(
                        icon = Icons.Default.Save,
                        onClick = { saveConfig() }
                    )
                    UIComponents.IconButton(
                        icon = Icons.Default.FileUpload,
                        onClick = { importLauncher.launch("application/json") }
                    )
                    UIComponents.IconButton(
                        icon = Icons.Default.FileDownload,
                        onClick = { exportLauncher.launch("ui_config.json") }
                    )
                    UIComponents.IconButton(
                        icon = Icons.Default.Restore,
                        onClick = { showResetDialog = true }
                    )
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(
                    if (AppColors.glassEnabled())
                        AppColors.glassBackground()
                    else
                        MaterialTheme.colorScheme.background
                )
        ) {
            // 标签页
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                containerColor = if (AppColors.glassEnabled())
                    AppColors.glassBackground()
                else
                    MaterialTheme.colorScheme.surface,
                edgePadding = 0.dp
            ) {
                listOf(Str.get(R.string.colors), Str.get(R.string.shapes), Str.get(R.string.sizes), Str.get(R.string.fonts), Str.get(R.string.effects)).forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Text(
                                title,
                                color = if (selectedTab == index)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = if (selectedTab == index)
                                    FontWeight.Bold
                                else
                                    FontWeight.Normal
                            )
                        }
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when (selectedTab) {
                    0 -> {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(AppDimens.cardCornerRadius),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (AppColors.glassEnabled())
                    AppColors.glassBackground()
                else
                    MaterialTheme.colorScheme.surface
                                ),
                                elevation = CardDefaults.cardElevation(defaultElevation = cardElevation)
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp)
                                ) {
                                    Text(
                                        Str.get(R.string.theme_mode),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        listOf(
                                            UIConfig.THEME_MODE_SYSTEM to Str.get(R.string.follow_system),
                                            UIConfig.THEME_MODE_LIGHT to Str.get(R.string.light_mode),
                                            UIConfig.THEME_MODE_DARK to Str.get(R.string.dark_mode)
                                        ).forEach { (mode, label) ->
                                            FilterChip(
                                                selected = configState.themeMode == mode,
                                                onClick = { configState = configState.copy(themeMode = mode) },
                                                label = { Text(label) },
                                                modifier = Modifier.weight(1f),
                                                colors = FilterChipDefaults.filterChipColors(
                                                    containerColor = MaterialTheme.colorScheme.surface,
                                                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        val colorKeys = listOf(
                            "primary" to Str.get(R.string.primary_color),
                            "primary_dark" to Str.get(R.string.dark_primary),
                            "primary_light" to Str.get(R.string.light_primary),
                            "accent" to Str.get(R.string.accent_color),
                            "success" to Str.get(R.string.success_color),
                            "warning" to Str.get(R.string.warning_color),
                            "error" to Str.get(R.string.error_color),
                            "info" to Str.get(R.string.info_color),
                            "text_primary" to Str.get(R.string.primary_text_color),
                            "text_secondary" to Str.get(R.string.secondary_text_color),
                            "text_hint" to Str.get(R.string.hint_text_color),
                            "text_primary_inverse" to Str.get(R.string.on_color_text),
                            "background" to Str.get(R.string.background_color),
                            "surface" to Str.get(R.string.surface_color),
                            "surface_variant" to Str.get(R.string.surface_variant_color),
                            "divider" to Str.get(R.string.divider_color),
                            "glass_background" to Str.get(R.string.glass_background_color),
                            "disabled" to Str.get(R.string.disabled_color),
                            "status_bar" to Str.get(R.string.status_bar_color),
                            "navigation_bar" to Str.get(R.string.navigation_bar_color),
                            "nav_selected" to Str.get(R.string.bottom_nav_selected_color),
                            "nav_unselected" to Str.get(R.string.bottom_nav_unselected_color)
                        )

                        listOf(
                            "light" to Str.get(R.string.light_palette),
                            "dark" to Str.get(R.string.dark_palette)
                        ).forEach { (prefix, sectionLabel) ->
                            item(key = "section_$prefix") {
                                Text(
                                    sectionLabel,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                                )
                            }

                            items(colorKeys, key = { "$prefix:${it.first}" }) { (key, displayName) ->
                                val prefixedKey = "$prefix:$key"
                                val colorValue = paletteColorValue(prefixedKey)

                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(AppDimens.cardCornerRadius))
                                        .clickable {
                                            selectedColorKey = prefixedKey
                                            showColorPicker = true
                                        },
                                    shape = RoundedCornerShape(AppDimens.cardCornerRadius),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (AppColors.glassEnabled())
                    AppColors.glassBackground()
                else
                    MaterialTheme.colorScheme.surface
                                    ),
                                    elevation = CardDefaults.cardElevation(defaultElevation = cardElevation)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            displayName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                colorValue,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(end = 8.dp)
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .size(24.dp)
                                                    .background(
                                                        safeParseColor(colorValue),
                                                        RoundedCornerShape(AppDimens.radiusSmall)
                                                    )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    1 -> {
                        val shapeKeys = listOf(
                            "cornerRadiusSmall" to Str.get(R.string.small_radius),
                            "cornerRadiusMedium" to Str.get(R.string.medium_radius),
                            "cornerRadiusLarge" to Str.get(R.string.large_radius),
                            "cornerRadiusExtraLarge" to Str.get(R.string.extra_large_radius),
                            "buttonCornerRadius" to Str.get(R.string.button_radius),
                            "cardCornerRadius" to Str.get(R.string.card_radius),
                            "dialogCornerRadius" to Str.get(R.string.dialog_radius),
                            "inputCornerRadius" to Str.get(R.string.input_field_radius)
                        )

                        items(shapeKeys) { (key, displayName) ->
                            val value = when (key) {
                                "cornerRadiusSmall" -> configState.cornerRadiusSmall
                                "cornerRadiusMedium" -> configState.cornerRadiusMedium
                                "cornerRadiusLarge" -> configState.cornerRadiusLarge
                                "cornerRadiusExtraLarge" -> configState.cornerRadiusExtraLarge
                                "buttonCornerRadius" -> configState.buttonCornerRadius
                                "cardCornerRadius" -> configState.cardCornerRadius
                                "dialogCornerRadius" -> configState.dialogCornerRadius
                                "inputCornerRadius" -> configState.inputCornerRadius
                                else -> 8f
                            }

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(AppDimens.cardCornerRadius),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (AppColors.glassEnabled())
                    AppColors.glassBackground()
                else
                    MaterialTheme.colorScheme.surface
                                ),
                                elevation = CardDefaults.cardElevation(defaultElevation = cardElevation)
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            displayName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            "${value.toInt()} dp",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Slider(
                                        value = value,
                                        onValueChange = { newValue ->
                                            val updated = when (key) {
                                                "cornerRadiusSmall" -> configState.copy(cornerRadiusSmall = newValue)
                                                "cornerRadiusMedium" -> configState.copy(cornerRadiusMedium = newValue)
                                                "cornerRadiusLarge" -> configState.copy(cornerRadiusLarge = newValue)
                                                "cornerRadiusExtraLarge" -> configState.copy(cornerRadiusExtraLarge = newValue)
                                                "buttonCornerRadius" -> configState.copy(buttonCornerRadius = newValue)
                                                "cardCornerRadius" -> configState.copy(cardCornerRadius = newValue)
                                                "dialogCornerRadius" -> configState.copy(dialogCornerRadius = newValue)
                                                "inputCornerRadius" -> configState.copy(inputCornerRadius = newValue)
                                                else -> configState
                                            }
                                            configState = updated
                                        },
                                        valueRange = 0f..50f,
                                        steps = 10,
                                        colors = SliderDefaults.colors(
                                            thumbColor = MaterialTheme.colorScheme.primary,
                                            activeTrackColor = MaterialTheme.colorScheme.primary,
                                            inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant
                                        )
                                    )
                                }
                            }
                        }
                    }

                    2 -> {
                        val sizeKeys = listOf(
                            "buttonHeight" to Str.get(R.string.button_height),
                            "buttonMinWidth" to Str.get(R.string.button_min_width),
                            "buttonElevation" to Str.get(R.string.button_shadow),
                            "cardElevation" to Str.get(R.string.card_shadow),
                            "cardPadding" to Str.get(R.string.card_padding),
                            "spacingSmall" to Str.get(R.string.small_spacing),
                            "spacingMedium" to Str.get(R.string.medium_spacing),
                            "spacingLarge" to Str.get(R.string.large_spacing),
                            "iconSizeSmall" to Str.get(R.string.small_icon),
                            "iconSizeMedium" to Str.get(R.string.medium_icon),
                            "iconSizeLarge" to Str.get(R.string.large_icon),
                            "progressHeight" to Str.get(R.string.progress_bar_height)
                        )

                        items(sizeKeys) { (key, displayName) ->
                            val value = when (key) {
                                "buttonHeight" -> configState.buttonHeight
                                "buttonMinWidth" -> configState.buttonMinWidth
                                "buttonElevation" -> configState.buttonElevation
                                "cardElevation" -> configState.cardElevation
                                "cardPadding" -> configState.cardPadding
                                "spacingSmall" -> configState.spacingSmall
                                "spacingMedium" -> configState.spacingMedium
                                "spacingLarge" -> configState.spacingLarge
                                "iconSizeSmall" -> configState.iconSizeSmall
                                "iconSizeMedium" -> configState.iconSizeMedium
                                "iconSizeLarge" -> configState.iconSizeLarge
                                "progressHeight" -> configState.progressHeight
                                else -> 16f
                            }
                            val range = when (key) {
                                "buttonHeight" -> 28f..64f
                                "buttonMinWidth" -> 60f..160f
                                "buttonElevation" -> 0f..12f
                                "cardElevation" -> 0f..16f
                                "cardPadding" -> 0f..32f
                                "spacingSmall" -> 0f..16f
                                "spacingMedium" -> 0f..24f
                                "spacingLarge" -> 0f..48f
                                "iconSizeSmall" -> 12f..24f
                                "iconSizeMedium" -> 16f..32f
                                "iconSizeLarge" -> 20f..40f
                                "progressHeight" -> 2f..12f
                                else -> 0f..100f
                            }

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(AppDimens.cardCornerRadius),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (AppColors.glassEnabled())
                    AppColors.glassBackground()
                else
                    MaterialTheme.colorScheme.surface
                                ),
                                elevation = CardDefaults.cardElevation(defaultElevation = cardElevation)
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            displayName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            "${value.toInt()} dp",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Slider(
                                        value = value,
                                        onValueChange = { newValue ->
                                            val updated = when (key) {
                                                "buttonHeight" -> configState.copy(buttonHeight = newValue)
                                                "buttonMinWidth" -> configState.copy(buttonMinWidth = newValue)
                                                "buttonElevation" -> configState.copy(buttonElevation = newValue)
                                                "cardElevation" -> configState.copy(cardElevation = newValue)
                                                "cardPadding" -> configState.copy(cardPadding = newValue)
                                                "spacingSmall" -> configState.copy(spacingSmall = newValue)
                                                "spacingMedium" -> configState.copy(spacingMedium = newValue)
                                                "spacingLarge" -> configState.copy(spacingLarge = newValue)
                                                "iconSizeSmall" -> configState.copy(iconSizeSmall = newValue)
                                                "iconSizeMedium" -> configState.copy(iconSizeMedium = newValue)
                                                "iconSizeLarge" -> configState.copy(iconSizeLarge = newValue)
                                                "progressHeight" -> configState.copy(progressHeight = newValue)
                                                else -> configState
                                            }
                                            configState = updated
                                        },
                                        valueRange = range,
                                        steps = 10,
                                        colors = SliderDefaults.colors(
                                            thumbColor = MaterialTheme.colorScheme.primary,
                                            activeTrackColor = MaterialTheme.colorScheme.primary,
                                            inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant
                                        )
                                    )
                                }
                            }
                        }
                    }

                    3 -> {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(AppDimens.cardCornerRadius),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (AppColors.glassEnabled())
                    AppColors.glassBackground()
                else
                    MaterialTheme.colorScheme.surface
                                ),
                                elevation = CardDefaults.cardElevation(defaultElevation = cardElevation)
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            Str.get(R.string.title_font_size),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            "${configState.titleTextSize.toInt()} sp",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Slider(
                                        value = configState.titleTextSize,
                                        onValueChange = { newValue ->
                                            configState = configState.copy(titleTextSize = newValue)
                                        },
                                        valueRange = 14f..36f,
                                        steps = 11,
                                        colors = SliderDefaults.colors(
                                            thumbColor = MaterialTheme.colorScheme.primary,
                                            activeTrackColor = MaterialTheme.colorScheme.primary,
                                            inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant
                                        )
                                    )
                                }
                            }
                        }

                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(AppDimens.cardCornerRadius),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (AppColors.glassEnabled())
                    AppColors.glassBackground()
                else
                    MaterialTheme.colorScheme.surface
                                ),
                                elevation = CardDefaults.cardElevation(defaultElevation = cardElevation)
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            Str.get(R.string.body_font_size),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            "${configState.bodyTextSize.toInt()} sp",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Slider(
                                        value = configState.bodyTextSize,
                                        onValueChange = { newValue ->
                                            configState = configState.copy(bodyTextSize = newValue)
                                        },
                                        valueRange = 10f..22f,
                                        steps = 6,
                                        colors = SliderDefaults.colors(
                                            thumbColor = MaterialTheme.colorScheme.primary,
                                            activeTrackColor = MaterialTheme.colorScheme.primary,
                                            inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant
                                        )
                                    )
                                }
                            }
                        }

                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(AppDimens.cardCornerRadius),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (AppColors.glassEnabled())
                    AppColors.glassBackground()
                else
                    MaterialTheme.colorScheme.surface
                                ),
                                elevation = CardDefaults.cardElevation(defaultElevation = cardElevation)
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            Str.get(R.string.caption_font_size),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            "${configState.captionTextSize.toInt()} sp",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Slider(
                                        value = configState.captionTextSize,
                                        onValueChange = { newValue ->
                                            configState = configState.copy(captionTextSize = newValue)
                                        },
                                        valueRange = 8f..16f,
                                        steps = 4,
                                        colors = SliderDefaults.colors(
                                            thumbColor = MaterialTheme.colorScheme.primary,
                                            activeTrackColor = MaterialTheme.colorScheme.primary,
                                            inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant
                                        )
                                    )
                                }
                            }
                        }

                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(AppDimens.cardCornerRadius),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (AppColors.glassEnabled())
                    AppColors.glassBackground()
                else
                    MaterialTheme.colorScheme.surface
                                ),
                                elevation = CardDefaults.cardElevation(defaultElevation = cardElevation)
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            Str.get(R.string.section_title_size),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            "${configState.sectionTitleTextSize.toInt()} sp",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Slider(
                                        value = configState.sectionTitleTextSize,
                                        onValueChange = { newValue ->
                                            configState = configState.copy(sectionTitleTextSize = newValue)
                                        },
                                        valueRange = 12f..28f,
                                        steps = 8,
                                        colors = SliderDefaults.colors(
                                            thumbColor = MaterialTheme.colorScheme.primary,
                                            activeTrackColor = MaterialTheme.colorScheme.primary,
                                            inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant
                                        )
                                    )
                                }
                            }
                        }

                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(AppDimens.cardCornerRadius),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (AppColors.glassEnabled())
                    AppColors.glassBackground()
                else
                    MaterialTheme.colorScheme.surface
                                ),
                                elevation = CardDefaults.cardElevation(defaultElevation = cardElevation)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        Str.get(R.string.bold_text),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Switch(
                                        checked = configState.enableBold,
                                        onCheckedChange = { configState = configState.copy(enableBold = it) },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = MaterialTheme.colorScheme.primary,
                                            checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                            uncheckedTrackColor = Color.White
                                        )
                                    )
                                }
                            }
                        }
                    }

                    4 -> {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(AppDimens.cardCornerRadius),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (AppColors.glassEnabled())
                    AppColors.glassBackground()
                else
                    MaterialTheme.colorScheme.surface
                                ),
                                elevation = CardDefaults.cardElevation(defaultElevation = cardElevation)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        Str.get(R.string.glass_effect),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Switch(
                                        checked = configState.enableGlassEffect,
                                        onCheckedChange = { configState = configState.copy(enableGlassEffect = it) },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = MaterialTheme.colorScheme.primary,
                                            checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                            uncheckedTrackColor = Color.White
                                        )
                                    )
                                }
                            }
                        }

                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(AppDimens.cardCornerRadius),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (AppColors.glassEnabled())
                    AppColors.glassBackground()
                else
                    MaterialTheme.colorScheme.surface
                                ),
                                elevation = CardDefaults.cardElevation(defaultElevation = cardElevation)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        Str.get(R.string.ripple_effect),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Switch(
                                        checked = configState.enableRipple,
                                        onCheckedChange = { configState = configState.copy(enableRipple = it) },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = MaterialTheme.colorScheme.primary,
                                            checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                            uncheckedTrackColor = Color.White
                                        )
                                    )
                                }
                            }
                        }

                        item {
                            val previewGlass = configState.enableGlassEffect
                            val previewShape = RoundedCornerShape(AppDimens.cardCornerRadius)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(previewShape)
                                    .background(
                                        Brush.linearGradient(
                                            colors = listOf(
                                                Color(0xFF6A5AE0),
                                                Color(0xFF00C6AE),
                                                Color(0xFFFFB36B)
                                            )
                                        )
                                    )
                                    .padding(14.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(previewShape)
                                        .then(
                                            if (previewGlass) {
                                                Modifier
                                                    .background(safeParseColor(paletteColorValue("glass_background")))
                                                    .border(1.dp, Color.White.copy(alpha = 0.35f), previewShape)
                                            } else {
                                                Modifier.background(MaterialTheme.colorScheme.surfaceVariant)
                                            }
                                        )
                                        .padding(16.dp)
                                ) {
                                    Text(
                                        Str.get(R.string.glass_effect_preview),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        if (previewGlass) Str.get(R.string.this_is_a_glass_effect_card) else Str.get(R.string.glass_effect_disabled),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    saveMessage?.let {
                        Text(
                            it,
                            color = if (it.contains(Str.get(R.string.saved_successfully)) || it.contains(Str.get(R.string.saved)))
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }

                    Button(
                        onClick = { saveConfig() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(AppDimens.buttonCornerRadius),
                        enabled = !isSaving
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        } else {
                            Icon(
                                Icons.Default.Save,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(if (isSaving) Str.get(R.string.saving) else Str.get(R.string.save_config), fontSize = AppDimens.bodyTextSize.sp, fontWeight = FontWeight.Medium)
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }

    // ==================== 颜色选择对话框 ====================
    val pickerKey = selectedColorKey
    if (showColorPicker && pickerKey != null) {
        var colorValue by remember { mutableStateOf("") }
        LaunchedEffect(pickerKey) {
            colorValue = paletteColorValue(pickerKey)
        }

        val initialColor = safeParseColor(colorValue)
        FullColorPickerDialog(
            initialColor = initialColor,
            onColorSelected = { selectedColor ->
                val newColor = String.format("#%02X%02X%02X%02X",
                    (selectedColor.alpha * 255).toInt(),
                    (selectedColor.red * 255).toInt(),
                    (selectedColor.green * 255).toInt(),
                    (selectedColor.blue * 255).toInt()
                )
                updatePaletteColor(pickerKey, newColor)
                showColorPicker = false
            },
            onDismiss = {
                showColorPicker = false
            }
        )
    }

    // ==================== 重置对话框 ====================
    if (showResetDialog) {
        UIComponents.ConfirmDialog(
            title = Str.get(R.string.confirm_reset),
            message = Str.get(R.string.reset_all_ui_config_to_defaults_this),
            confirmText = Str.get(R.string.reset),
            dismissText = Str.get(R.string.cancel),
            onConfirm = { resetConfig() },
            onDismiss = { showResetDialog = false },
            isDestructive = true
        )
    }
}