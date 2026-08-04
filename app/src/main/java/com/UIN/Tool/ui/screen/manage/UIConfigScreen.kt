// app/src/main/java/com/UIN/Tool/ui/screen/manage/UIConfigScreen.kt
package com.UIN.Tool.ui.screen.manage

import com.UIN.Tool.R
import com.UIN.Tool.utils.Str
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
                        }
                    }
                }

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
            TopAppBar(
                title = { Text(Str.get(R.string.ui_customization)) },
                navigationIcon = {
                    UIComponents.IconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        onClick = onBack
                    )
                },
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
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 标签页
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
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
                            "disabled" to Str.get(R.string.disabled_color)
                        )

                        items(colorKeys) { (key, displayName) ->
                            val colorValue = when (key) {
                                "primary" -> configState.primaryColor
                                "primary_dark" -> configState.primaryDarkColor
                                "primary_light" -> configState.primaryLightColor
                                "accent" -> configState.accentColor
                                "success" -> configState.successColor
                                "warning" -> configState.warningColor
                                "error" -> configState.errorColor
                                "info" -> configState.infoColor
                                "text_primary" -> configState.textPrimaryColor
                                "text_secondary" -> configState.textSecondaryColor
                                "text_hint" -> configState.textHintColor
                                "text_primary_inverse" -> configState.textPrimaryInverseColor
                                "background" -> configState.backgroundColor
                                "surface" -> configState.surfaceColor
                                "surface_variant" -> configState.surfaceVariantColor
                                "divider" -> configState.dividerColor
                                "glass_background" -> configState.glassBackgroundColor
                                "disabled" -> configState.disabledColor
                                else -> "#FFFFFFFF"
                            }

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedColorKey = key
                                        showColorPicker = true
                                    },
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                ),
                                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
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
                                                    RoundedCornerShape(4.dp)
                                                )
                                        )
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
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                ),
                                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
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
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                ),
                                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
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
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                ),
                                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
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
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                ),
                                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
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
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                ),
                                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
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
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                ),
                                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
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
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                ),
                                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
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
                                            checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
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
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                ),
                                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
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
                                            checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                        )
                                    )
                                }
                            }
                        }

                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                ),
                                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
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
                                            checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                        )
                                    )
                                }
                            }
                        }

                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (configState.enableGlassEffect)
                                        Color.White.copy(alpha = 0.9f)
                                    else
                                        MaterialTheme.colorScheme.surfaceVariant
                                ),
                                elevation = CardDefaults.cardElevation(defaultElevation = if (configState.enableGlassEffect) 4.dp else 1.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp)
                                ) {
                                    Text(
                                        Str.get(R.string.glass_effect_preview),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        if (configState.enableGlassEffect) Str.get(R.string.this_is_a_glass_effect_card) else Str.get(R.string.glass_effect_disabled),
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
                        shape = RoundedCornerShape(12.dp),
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
                        Text(if (isSaving) Str.get(R.string.saving) else Str.get(R.string.save_config), fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }

    // ==================== 颜色选择对话框 ====================
    if (showColorPicker && selectedColorKey != null) {
        var colorValue by remember { mutableStateOf("") }
        LaunchedEffect(selectedColorKey) {
            colorValue = when (selectedColorKey) {
                "primary" -> configState.primaryColor
                "primary_dark" -> configState.primaryDarkColor
                "primary_light" -> configState.primaryLightColor
                "accent" -> configState.accentColor
                "success" -> configState.successColor
                "warning" -> configState.warningColor
                "error" -> configState.errorColor
                "info" -> configState.infoColor
                "text_primary" -> configState.textPrimaryColor
                "text_secondary" -> configState.textSecondaryColor
                "text_hint" -> configState.textHintColor
                "text_primary_inverse" -> configState.textPrimaryInverseColor
                "background" -> configState.backgroundColor
                "surface" -> configState.surfaceColor
                "surface_variant" -> configState.surfaceVariantColor
                "divider" -> configState.dividerColor
                "glass_background" -> configState.glassBackgroundColor
                "disabled" -> configState.disabledColor
                else -> "#FFFFFFFF"
            }
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
                when (selectedColorKey) {
                    "primary" -> configState = configState.copy(primaryColor = newColor)
                    "primary_dark" -> configState = configState.copy(primaryDarkColor = newColor)
                    "primary_light" -> configState = configState.copy(primaryLightColor = newColor)
                    "accent" -> configState = configState.copy(accentColor = newColor)
                    "success" -> configState = configState.copy(successColor = newColor)
                    "warning" -> configState = configState.copy(warningColor = newColor)
                    "error" -> configState = configState.copy(errorColor = newColor)
                    "info" -> configState = configState.copy(infoColor = newColor)
                    "text_primary" -> configState = configState.copy(textPrimaryColor = newColor)
                    "text_secondary" -> configState = configState.copy(textSecondaryColor = newColor)
                    "text_hint" -> configState = configState.copy(textHintColor = newColor)
                    "text_primary_inverse" -> configState = configState.copy(textPrimaryInverseColor = newColor)
                    "background" -> configState = configState.copy(backgroundColor = newColor)
                    "surface" -> configState = configState.copy(surfaceColor = newColor)
                    "surface_variant" -> configState = configState.copy(surfaceVariantColor = newColor)
                    "divider" -> configState = configState.copy(dividerColor = newColor)
                    "glass_background" -> configState = configState.copy(glassBackgroundColor = newColor)
                    "disabled" -> configState = configState.copy(disabledColor = newColor)
                }
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