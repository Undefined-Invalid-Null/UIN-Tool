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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.UIN.Tool.ui.components.FullColorPickerDialog
import com.UIN.Tool.ui.components.unified.UnifiedButton
import com.UIN.Tool.ui.components.unified.UnifiedCard
import com.UIN.Tool.ui.components.unified.UnifiedCaptionText
import com.UIN.Tool.ui.components.unified.UnifiedChip
import com.UIN.Tool.ui.components.unified.UnifiedConfirmDialog
import com.UIN.Tool.ui.components.unified.UnifiedIconButton
import com.UIN.Tool.ui.components.unified.UnifiedSlider
import com.UIN.Tool.ui.common.StyleManager
import com.UIN.Tool.ui.common.StylePresets
import com.UIN.Tool.ui.screen.manage.uiconfig.UIConfigSidebar
import com.UIN.Tool.ui.screen.manage.uiconfig.NumericPropertyRow
import com.UIN.Tool.ui.screen.manage.uiconfig.DropdownPropertyRow
import com.UIN.Tool.ui.screen.manage.uiconfig.ColorPropertyRow
import com.UIN.Tool.ui.screen.manage.uiconfig.BooleanPropertyRow
import com.UIN.Tool.utils.UIConfig
import com.UIN.Tool.utils.AppLog
import com.UIN.Tool.utils.AppToast
import com.UIN.Tool.utils.FileUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import com.UIN.Tool.ui.theme.AppColors
import com.UIN.Tool.ui.theme.AppDimens
import com.UIN.Tool.ui.theme.gradientBackgroundBrush

private const val TAG = "UIConfigScreen"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UIConfigScreen(
    navController: NavController,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var styleVersion by remember { mutableIntStateOf(0) }
    var configState by remember(styleVersion) { mutableStateOf(loadConfigFromUIConfig()) }
    var isSaving by remember { mutableStateOf(false) }
    var showColorPicker by remember { mutableStateOf(false) }
    var selectedColorKey by remember { mutableStateOf<String?>(null) }
    var selectedGradientColorIndex by remember { mutableStateOf(-1) }
    var showResetDialog by remember { mutableStateOf(false) }
    var showDarkPalette by remember { mutableStateOf(false) }
    var saveMessage by remember { mutableStateOf<String?>(null) }
    var selectedSection by remember { mutableStateOf("style_default") }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

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
        val savedStyle = uiConfig.getCurrentStyle()
        uiConfig.resetToDefault()
        configState = loadConfigFromUIConfig()
        try {
            configState = StyleManager.switchStyle(configState, savedStyle)
        } catch (_: Exception) {}
        saveConfigToUIConfig(configState)
        AppToast.info(context, Str.get(R.string.reset_to_default_config))
        showResetDialog = false
        AppLog.success(TAG, Str.get(R.string.config_reset))
    }

    fun paletteColorValue(key: String): String = configState.paletteColorValue(key)

    fun updatePaletteColor(key: String, value: String) {
        configState = configState.updatePaletteColor(key, value)
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
                    uiConfig.updateBoolean("enableNeumorphism", experimental.optBoolean("enableNeumorphism", false))
                    uiConfig.updateBoolean("enableNeumorphismInset", experimental.optBoolean("enableNeumorphismInset", true))
                    uiConfig.updateBoolean("enableNeumorphismGlow", experimental.optBoolean("enableNeumorphismGlow", true))
                    uiConfig.setNeumorphismIntensity(experimental.optString("neumorphismIntensity", "light"))
                    uiConfig.setAnimationSpeed(experimental.optString("animationSpeed", "medium"))
                }

                val gradient = obj.optJSONObject("gradient")
                if (gradient != null) {
                    uiConfig.setGradientBackgroundEnabled(gradient.optBoolean("enabled", true))
                    uiConfig.setGradientMode(gradient.optString("mode", UIConfig.GRADIENT_MODE_SINGLE))
                    uiConfig.setGradientColor(gradient.optString("color", "#FFC4D6DF"))
                    if (gradient.has("color_dark")) {
                        uiConfig.setGradientColorDark(gradient.optString("color_dark", "#FF4C4F51"))
                    }
                    uiConfig.setGradientFrom(gradient.optString("from", UIConfig.GRADIENT_DIR_BOTTOM_RIGHT))
                    uiConfig.setGradientTo(gradient.optString("to", UIConfig.GRADIENT_DIR_TOP_LEFT))
                    val colorsArr = gradient.optJSONArray("colors")
                    if (colorsArr != null) {
                        val colors = mutableListOf<String>()
                        for (i in 0 until colorsArr.length()) {
                            colors.add(colorsArr.optString(i, "#FFC4D6DF"))
                        }
                        uiConfig.setGradientColors(colors)
                    }
                }

                val font = obj.optJSONObject("font")
                if (font != null) {
                    uiConfig.updateBoolean("enableBold", font.optBoolean("enableBold", true))
                    if (font.has("fontFamily")) {
                        uiConfig.setFontFamily(font.getString("fontFamily"))
                    }
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

    UIConfigSidebar(
        selectedSection = selectedSection,
        onSectionSelected = { selectedSection = it },
        onReset = { showResetDialog = true },
        drawerState = drawerState,
        drawerScope = scope
    ) { paddingValues ->
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val widthPx = with(LocalDensity.current) { maxWidth.toPx() }
            val heightPx = with(LocalDensity.current) { maxHeight.toPx() }
            val gradientBrush = gradientBackgroundBrush(widthPx, heightPx)
            if (gradientBrush != null) {
                Box(modifier = Modifier.fillMaxSize().background(gradientBrush))
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
            when (selectedSection) {
                "style_default", "style_neumorphism" -> {
                    item {
                        val isCurrentDefault = StyleManager.getCurrentStyleName() == "default"
                        val isCurrentNeumorphism = StyleManager.getCurrentStyleName() == "neumorphism"
                        Column {
                            StyleCard(
                                title = Str.get(R.string.style_default_title),
                                subtitle = Str.get(R.string.style_default_subtitle),
                                selected = isCurrentDefault,
                                onClick = {
                                    configState = StyleManager.switchStyle(configState, "default")
                                    saveConfigToUIConfig(configState)
                                    UIConfig.getInstance().setCurrentStyle("default")
                                    configState = loadConfigFromUIConfig()
                                    styleVersion++
                                }
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            StyleCard(
                                title = Str.get(R.string.style_neumorphism_title),
                                subtitle = Str.get(R.string.style_neumorphism_subtitle),
                                selected = isCurrentNeumorphism,
                                onClick = {
                                    configState = StyleManager.switchStyle(configState, "neumorphism")
                                    saveConfigToUIConfig(configState)
                                    UIConfig.getInstance().setCurrentStyle("neumorphism")
                                    configState = loadConfigFromUIConfig()
                                    styleVersion++
                                }
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            UnifiedCaptionText(
                                text = Str.get(R.string.current_style, StyleManager.getCurrentStyleName()),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                "appearance_colors" -> {
                    item {
                        UnifiedCaptionText(
                            text = Str.get(R.string.theme_mode),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(AppDimens.cardCornerRadius))
                                .background(if (AppColors.glassEnabled()) AppColors.glassBackground() else Color.Transparent)
                                .padding(horizontal = 16.dp, vertical = 8.dp)
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
                                UnifiedChip(
                                    label = label,
                                    selected = configState.themeMode == mode,
                                    onClick = {
                                        configState = configState.copy(themeMode = mode)
                                        UIConfig.getInstance().setThemeMode(mode)
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    item {
                        UnifiedCaptionText(
                            text = Str.get(R.string.palette_editing),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(AppDimens.cardCornerRadius))
                                .background(if (AppColors.glassEnabled()) AppColors.glassBackground() else Color.Transparent)
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(
                                "light" to Str.get(R.string.light_palette),
                                "dark" to Str.get(R.string.dark_palette)
                            ).forEach { (prefix, label) ->
                                UnifiedChip(
                                    label = label,
                                    selected = if (prefix == "light") !showDarkPalette else showDarkPalette,
                                    onClick = { showDarkPalette = prefix == "dark" },
                                    modifier = Modifier.weight(1f)
                                )
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

                    val activePrefix = if (showDarkPalette) "dark" else "light"
                    val activeTitle = if (showDarkPalette) Str.get(R.string.dark_palette) else Str.get(R.string.light_palette)

                    item {
                        UnifiedCaptionText(
                            text = activeTitle,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                        )
                    }

                    items(colorKeys.size) { index ->
                        val (key, displayName) = colorKeys[index]
                        val prefixedKey = "$activePrefix:$key"
                        val colorValue = paletteColorValue(prefixedKey)

                        ColorPropertyRow(
                            label = displayName,
                            colorValue = colorValue,
                            onClick = {
                                selectedColorKey = prefixedKey
                                showColorPicker = true
                            }
                        )
                    }
                }

                "appearance_shapes_corner" -> {
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

                    items(shapeKeys.size) { index ->
                        val (key, displayName) = shapeKeys[index]
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

                        NumericPropertyRow(
                            label = displayName,
                            value = value,
                            onValueChange = { newValue ->
                                configState = when (key) {
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
                            },
                            valueRange = 0f..50f,
                            unit = "dp",
                            step = 1f
                        )
                    }
                }

                "appearance_shapes_size" -> {
                    val sizeKeys = listOf(
                        "buttonHeight" to Str.get(R.string.button_height),
                        "buttonMinWidth" to Str.get(R.string.button_min_width),
                        "cardPadding" to Str.get(R.string.card_padding),
                        "progressHeight" to Str.get(R.string.progress_bar_height)
                    )

                    items(sizeKeys.size) { index ->
                        val (key, displayName) = sizeKeys[index]
                        val value = when (key) {
                            "buttonHeight" -> configState.buttonHeight
                            "buttonMinWidth" -> configState.buttonMinWidth
                            "cardPadding" -> configState.cardPadding
                            "progressHeight" -> configState.progressHeight
                            else -> 16f
                        }
                        val range = when (key) {
                            "buttonHeight" -> 28f..64f
                            "buttonMinWidth" -> 60f..160f
                            "cardPadding" -> 0f..32f
                            "progressHeight" -> 2f..12f
                            else -> 0f..100f
                        }

                        NumericPropertyRow(
                            label = displayName,
                            value = value,
                            onValueChange = { newValue ->
                                configState = when (key) {
                                    "buttonHeight" -> configState.copy(buttonHeight = newValue)
                                    "buttonMinWidth" -> configState.copy(buttonMinWidth = newValue)
                                    "cardPadding" -> configState.copy(cardPadding = newValue)
                                    "progressHeight" -> configState.copy(progressHeight = newValue)
                                    else -> configState
                                }
                            },
                            valueRange = range,
                            unit = "dp",
                            step = 1f
                        )
                    }
                }

                "appearance_shapes_border" -> {
                    item {
                        UnifiedCaptionText(
                            text = Str.get(R.string.border_settings),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(AppDimens.cardCornerRadius))
                                .background(if (AppColors.glassEnabled()) AppColors.glassBackground() else Color.Transparent)
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        UnifiedCaptionText(
                            text = Str.get(R.string.border_settings_coming_soon),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }

                "appearance_material_shadow" -> {
                    item {
                        BooleanPropertyRow(
                            label = Str.get(R.string.enable_neumorphism),
                            value = configState.enableNeumorphism,
                            onValueChange = {
                                configState = configState.copy(enableNeumorphism = it)
                                UIConfig.getInstance().setNeumorphismEnabled(it)
                            }
                        )
                    }

                    if (configState.enableNeumorphism) {
                        item {
                            DropdownPropertyRow(
                                label = Str.get(R.string.neumorphism_intensity),
                                value = configState.neumorphismIntensity,
                                options = listOf(
                                    "light" to Str.get(R.string.intensity_light),
                                    "medium" to Str.get(R.string.intensity_medium),
                                    "strong" to Str.get(R.string.intensity_strong)
                                ),
                                onValueChange = {
                                    configState = configState.copy(neumorphismIntensity = it)
                                    try {
                                        val uiConfig = UIConfig.getInstance()
                                        uiConfig.setNeumorphismIntensity(it)
                                    } catch (_: Exception) {}
                                }
                            )
                        }

                        item {
                            BooleanPropertyRow(
                                label = Str.get(R.string.neumorphism_inset),
                                value = configState.enableNeumorphismInset,
                                onValueChange = {
                                    configState = configState.copy(enableNeumorphismInset = it)
                                    UIConfig.getInstance().setNeumorphismInsetEnabled(it)
                                }
                            )
                        }

                        item {
                            BooleanPropertyRow(
                                label = Str.get(R.string.neumorphism_glow),
                                value = configState.enableNeumorphismGlow,
                                onValueChange = {
                                    configState = configState.copy(enableNeumorphismGlow = it)
                                    UIConfig.getInstance().setNeumorphismGlowEnabled(it)
                                }
                            )
                        }
                    }
                }

                "appearance_material_blur" -> {
                    item {
                        BooleanPropertyRow(
                            label = Str.get(R.string.glass_effect),
                            value = configState.enableGlassEffect,
                            onValueChange = {
                                configState = configState.copy(enableGlassEffect = it)
                                try { UIConfig.getInstance().updateBoolean("enableGlassEffect", it) } catch (_: Exception) {}
                            }
                        )
                    }

                    if (configState.enableGlassEffect) {
                        item {
                            ColorPropertyRow(
                                label = Str.get(R.string.glass_background_color),
                                colorValue = configState.glassBackgroundColor,
                                onClick = {
                                    selectedColorKey = "glass_background"
                                    showColorPicker = true
                                }
                            )
                        }
                        item {
                            val alpha = configState.translucentAlpha
                            Column(modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(AppDimens.cardCornerRadius))
                                .background(if (AppColors.glassEnabled()) AppColors.glassBackground() else Color.Transparent)
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = "${Str.get(R.string.glass_effect)} (Alpha: ${(alpha * 100).toInt()}%)",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                UnifiedSlider(
                                    value = alpha,
                                    onValueChange = {
                                        configState = configState.copy(translucentAlpha = it)
                                        try { UIConfig.getInstance().setTranslucentAlpha(it) } catch (_: Exception) {}
                                    },
                                    valueRange = 0.05f..0.95f,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }

                "appearance_bg_gradient" -> {
                    item {
                        BooleanPropertyRow(
                            label = Str.get(R.string.gradient_background),
                            value = configState.enableGradientBackground,
                            onValueChange = { configState = configState.copy(enableGradientBackground = it) }
                        )
                    }

                    if (configState.enableGradientBackground) {
                        item {
                            DropdownPropertyRow(
                                label = Str.get(R.string.gradient_mode),
                                value = configState.gradientMode,
                                options = listOf(
                                    UIConfig.GRADIENT_MODE_SINGLE to Str.get(R.string.single_color_gradient),
                                    UIConfig.GRADIENT_MODE_MULTI to Str.get(R.string.multi_color_gradient)
                                ),
                                onValueChange = { configState = configState.copy(gradientMode = it) }
                            )
                        }

                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(modifier = Modifier.weight(1f)) {
                                    DropdownPropertyRow(
                                        label = Str.get(R.string.gradient_from),
                                        value = configState.gradientFrom,
                                        options = UIConfig.GRADIENT_DIRECTIONS.map { it to gradientDirectionLabel(it) },
                                        onValueChange = { configState = configState.copy(gradientFrom = it) }
                                    )
                                }
                                Box(modifier = Modifier.weight(1f)) {
                                    DropdownPropertyRow(
                                        label = Str.get(R.string.gradient_to),
                                        value = configState.gradientTo,
                                        options = UIConfig.GRADIENT_DIRECTIONS.map { it to gradientDirectionLabel(it) },
                                        onValueChange = { configState = configState.copy(gradientTo = it) }
                                    )
                                }
                            }
                        }

                        if (configState.gradientMode == UIConfig.GRADIENT_MODE_SINGLE) {
                            item {
                                ColorPropertyRow(
                                    label = Str.get(R.string.gradient_color),
                                    colorValue = configState.gradientColor,
                                    onClick = {
                                        selectedColorKey = "gradient_color"
                                        showColorPicker = true
                                    }
                                )
                            }
                        } else {
                            items(configState.gradientColors.size) { index ->
                                val color = configState.gradientColors[index]
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    ColorPropertyRow(
                                        label = "${Str.get(R.string.gradient_color)} ${index + 1}",
                                        colorValue = color,
                                        onClick = {
                                            selectedGradientColorIndex = index
                                            selectedColorKey = "gradient_multi_color"
                                            showColorPicker = true
                                        }
                                    )
                                    UnifiedIconButton(
                                        icon = Icons.Default.RemoveCircle,
                                        onClick = {
                                            if (configState.gradientColors.size > 1) {
                                                configState = configState.copy(
                                                    gradientColors = configState.gradientColors.toMutableList().apply {
                                                        removeAt(index)
                                                    }
                                                )
                                            }
                                        },
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }

                            if (configState.gradientColors.size < 6) {
                                item {
                                    UnifiedButton(
                                        text = Str.get(R.string.add_gradient_color),
                                        icon = Icons.Default.Add,
                                        onClick = {
                                            if (configState.gradientColors.size < 6) {
                                                configState = configState.copy(
                                                    gradientColors = configState.gradientColors + "#FF4FC3F7"
                                                )
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                }

                "appearance_bg_solid" -> {
                    item {
                        ColorPropertyRow(
                            label = Str.get(R.string.background_color),
                            colorValue = paletteColorValue("light:background"),
                            onClick = {
                                selectedColorKey = "light:background"
                                showColorPicker = true
                            }
                        )
                    }
                    item {
                        ColorPropertyRow(
                            label = Str.get(R.string.surface_color),
                            colorValue = paletteColorValue("light:surface"),
                            onClick = {
                                selectedColorKey = "light:surface"
                                showColorPicker = true
                            }
                        )
                    }
                    item {
                        ColorPropertyRow(
                            label = Str.get(R.string.surface_variant_color),
                            colorValue = paletteColorValue("light:surface_variant"),
                            onClick = {
                                selectedColorKey = "light:surface_variant"
                                showColorPicker = true
                            }
                        )
                    }
                }

                "appearance_bg_image" -> {
                    item {
                        UnifiedCaptionText(
                            text = Str.get(R.string.background_image_settings),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(AppDimens.cardCornerRadius))
                                .background(if (AppColors.glassEnabled()) AppColors.glassBackground() else Color.Transparent)
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        UnifiedCaptionText(
                            text = Str.get(R.string.background_image_coming_soon),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }

                "content_text_size" -> {
                    item {
                        NumericPropertyRow(
                            label = Str.get(R.string.title_font_size),
                            value = configState.titleTextSize,
                            onValueChange = { configState = configState.copy(titleTextSize = it) },
                            valueRange = 14f..36f,
                            unit = "sp",
                            step = 1f
                        )
                    }
                    item {
                        NumericPropertyRow(
                            label = Str.get(R.string.body_font_size),
                            value = configState.bodyTextSize,
                            onValueChange = { configState = configState.copy(bodyTextSize = it) },
                            valueRange = 10f..22f,
                            unit = "sp",
                            step = 1f
                        )
                    }
                    item {
                        NumericPropertyRow(
                            label = Str.get(R.string.caption_font_size),
                            value = configState.captionTextSize,
                            onValueChange = { configState = configState.copy(captionTextSize = it) },
                            valueRange = 8f..16f,
                            unit = "sp",
                            step = 1f
                        )
                    }
                    item {
                        NumericPropertyRow(
                            label = Str.get(R.string.section_title_size),
                            value = configState.sectionTitleTextSize,
                            onValueChange = { configState = configState.copy(sectionTitleTextSize = it) },
                            valueRange = 12f..28f,
                            unit = "sp",
                            step = 1f
                        )
                    }
                }

                "content_text_font" -> {
                    item {
                        DropdownPropertyRow(
                            label = Str.get(R.string.font_family),
                            value = configState.fontFamily,
                            options = listOf(
                                "sans-serif" to Str.get(R.string.sans_serif_default),
                                "serif" to "Serif",
                                "monospace" to "Monospace"
                            ),
                            onValueChange = { configState = configState.copy(fontFamily = it) }
                        )
                    }
                }

                "content_text_language" -> {
                    item {
                        val appContext = LocalContext.current.applicationContext as? com.UIN.Tool.UinApplication
                        DropdownPropertyRow(
                            label = Str.get(R.string.language_label),
                            value = configState.language,
                            options = listOf(
                                "system" to Str.get(R.string.follow_system),
                                "zh" to Str.get(R.string.simplified_chinese),
                                "en" to "English"
                            ),
                            onValueChange = {
                                configState = configState.copy(language = it)
                                try { UIConfig.getInstance().setLanguage(it) } catch (_: Exception) {}
                                try { appContext?.applyLocale() } catch (_: Exception) {}
                            }
                        )
                    }
                }

                "content_text_weight" -> {
                    item {
                        BooleanPropertyRow(
                            label = Str.get(R.string.bold_text),
                            value = configState.enableBold,
                            onValueChange = { configState = configState.copy(enableBold = it) }
                        )
                    }
                }

                "content_image" -> {
                    item {
                        UnifiedCaptionText(
                            text = Str.get(R.string.icon_image_replace),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(AppDimens.cardCornerRadius))
                                .background(if (AppColors.glassEnabled()) AppColors.glassBackground() else Color.Transparent)
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        UnifiedCaptionText(
                            text = Str.get(R.string.icon_image_replace_coming_soon),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }

                "interaction_click" -> {
                    item {
                        BooleanPropertyRow(
                            label = Str.get(R.string.ripple_effect),
                            value = configState.enableRipple,
                            onValueChange = { configState = configState.copy(enableRipple = it) }
                        )
                    }
                }

                "interaction_animation" -> {
                    item {
                        DropdownPropertyRow(
                            label = Str.get(R.string.animation_speed),
                            value = configState.animationSpeed,
                            options = listOf(
                                "fast" to Str.get(R.string.speed_fast),
                                "medium" to Str.get(R.string.speed_medium),
                                "slow" to Str.get(R.string.speed_slow)
                            ),
                            onValueChange = { configState = configState.copy(animationSpeed = it) }
                        )
                    }
                }

                "layout_margin" -> {
                    item {
                        NumericPropertyRow(
                            label = Str.get(R.string.small_spacing),
                            value = configState.spacingSmall,
                            onValueChange = { configState = configState.copy(spacingSmall = it) },
                            valueRange = 0f..16f,
                            unit = "dp",
                            step = 1f
                        )
                    }
                    item {
                        NumericPropertyRow(
                            label = Str.get(R.string.medium_spacing),
                            value = configState.spacingMedium,
                            onValueChange = { configState = configState.copy(spacingMedium = it) },
                            valueRange = 0f..24f,
                            unit = "dp",
                            step = 1f
                        )
                    }
                    item {
                        NumericPropertyRow(
                            label = Str.get(R.string.large_spacing),
                            value = configState.spacingLarge,
                            onValueChange = { configState = configState.copy(spacingLarge = it) },
                            valueRange = 0f..48f,
                            unit = "dp",
                            step = 1f
                        )
                    }
                    item {
                        NumericPropertyRow(
                            label = Str.get(R.string.card_shadow),
                            value = configState.cardElevation,
                            onValueChange = { configState = configState.copy(cardElevation = it) },
                            valueRange = 0f..16f,
                            unit = "dp",
                            step = 1f
                        )
                    }
                    item {
                        NumericPropertyRow(
                            label = Str.get(R.string.button_shadow),
                            value = configState.buttonElevation,
                            onValueChange = { configState = configState.copy(buttonElevation = it) },
                            valueRange = 0f..12f,
                            unit = "dp",
                            step = 1f
                        )
                    }
                }

                "layout_position" -> {
                    item {
                        NumericPropertyRow(
                            label = Str.get(R.string.small_icon),
                            value = configState.iconSizeSmall,
                            onValueChange = { configState = configState.copy(iconSizeSmall = it) },
                            valueRange = 12f..24f,
                            unit = "dp",
                            step = 1f
                        )
                    }
                    item {
                        NumericPropertyRow(
                            label = Str.get(R.string.medium_icon),
                            value = configState.iconSizeMedium,
                            onValueChange = { configState = configState.copy(iconSizeMedium = it) },
                            valueRange = 16f..32f,
                            unit = "dp",
                            step = 1f
                        )
                    }
                    item {
                        NumericPropertyRow(
                            label = Str.get(R.string.large_icon),
                            value = configState.iconSizeLarge,
                            onValueChange = { configState = configState.copy(iconSizeLarge = it) },
                            valueRange = 20f..40f,
                            unit = "dp",
                            step = 1f
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                saveMessage?.let {
                    UnifiedCaptionText(
                        text = it,
                        color = if (it.contains(Str.get(R.string.saved_successfully)) || it.contains(Str.get(R.string.saved)))
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }

            item {
                UnifiedButton(
                    text = if (isSaving) Str.get(R.string.saving) else Str.get(R.string.save_config),
                    onClick = { saveConfig() },
                    loading = isSaving,
                    icon = Icons.Default.Save,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
        }
    }

    val pickerKey = selectedColorKey
    if (showColorPicker && pickerKey != null) {
        var colorValue by remember { mutableStateOf("") }
        LaunchedEffect(pickerKey) {
            colorValue = when {
                pickerKey == "gradient_color" -> configState.gradientColor
                pickerKey == "gradient_multi_color" && selectedGradientColorIndex in configState.gradientColors.indices ->
                    configState.gradientColors[selectedGradientColorIndex]
                pickerKey == "glass_background" -> configState.glassBackgroundColor
                else -> paletteColorValue(pickerKey)
            }
        }

        val initialColor = safeParseColor(colorValue)
        FullColorPickerDialog(
            initialColor = initialColor,
            onColorSelected = { selectedColor ->
                val newColor = String.format(
                    "#%02X%02X%02X%02X",
                    (selectedColor.alpha * 255).toInt(),
                    (selectedColor.red * 255).toInt(),
                    (selectedColor.green * 255).toInt(),
                    (selectedColor.blue * 255).toInt()
                )
                when {
                    pickerKey == "gradient_color" -> configState = configState.copy(gradientColor = newColor)
                    pickerKey == "gradient_multi_color" && selectedGradientColorIndex in configState.gradientColors.indices -> {
                        configState = configState.copy(
                            gradientColors = configState.gradientColors.toMutableList().apply {
                                this[selectedGradientColorIndex] = newColor
                            }
                        )
                    }
                    pickerKey == "glass_background" -> configState = configState.copy(glassBackgroundColor = newColor)
                    else -> updatePaletteColor(pickerKey, newColor)
                }
                showColorPicker = false
            },
            onDismiss = {
                showColorPicker = false
            }
        )
    }

    if (showResetDialog) {
        UnifiedConfirmDialog(
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

@Composable
private fun StyleCard(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    UnifiedCard(
        modifier = Modifier.fillMaxWidth()
            .then(
                if (selected) Modifier.border(
                    width = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(AppDimens.cardCornerRadius)
                ) else Modifier
            ),
        containerColor = if (selected) {
            if (AppColors.glassEnabled()) AppColors.glassBackground().copy(alpha = 0.85f) else MaterialTheme.colorScheme.surfaceVariant
        } else {
            if (AppColors.glassEnabled()) AppColors.glassBackground() else MaterialTheme.colorScheme.surface
        },
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = title,
                    fontSize = AppDimens.bodyTextSize.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    fontSize = AppDimens.captionTextSize.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (selected) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

private fun gradientDirectionLabel(dir: String): String {
    return when (dir) {
        UIConfig.GRADIENT_DIR_TOP -> Str.get(R.string.gradient_dir_top)
        UIConfig.GRADIENT_DIR_BOTTOM -> Str.get(R.string.gradient_dir_bottom)
        UIConfig.GRADIENT_DIR_TOP_LEFT -> Str.get(R.string.gradient_dir_top_left)
        UIConfig.GRADIENT_DIR_TOP_RIGHT -> Str.get(R.string.gradient_dir_top_right)
        UIConfig.GRADIENT_DIR_BOTTOM_LEFT -> Str.get(R.string.gradient_dir_bottom_left)
        else -> Str.get(R.string.gradient_dir_bottom_right)
    }
}
