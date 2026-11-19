package com.UIN.Tool.ui.screen.manage

import com.UIN.Tool.R
import com.UIN.Tool.utils.Str
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
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
import com.UIN.Tool.ui.screen.files.FilePrefs
import com.UIN.Tool.ui.screen.manage.uiconfig.UIConfigSidebar
import com.UIN.Tool.ui.screen.manage.uiconfig.NumericPropertyRow
import com.UIN.Tool.ui.screen.manage.uiconfig.DropdownPropertyRow
import com.UIN.Tool.ui.screen.manage.uiconfig.ColorPropertyRow
import com.UIN.Tool.ui.screen.manage.uiconfig.BooleanPropertyRow
import com.UIN.Tool.utils.UIConfig
import com.UIN.Tool.utils.AppLog
import com.UIN.Tool.utils.AppToast
import com.UIN.Tool.utils.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import com.UIN.Tool.ui.theme.AppColors
import com.UIN.Tool.ui.theme.AppDimens
import com.UIN.Tool.ui.theme.gradientBackgroundBrush
import com.UIN.Tool.ui.screen.files.InAppFilePicker

private const val TAG = "UIConfigScreen"

/**
 * v.txt 第 4 条（用户）：切深浅色/语言不再重建 Activity，但进程级兜底仍留着 ——
 * 万一系统回收后重建、saved state 也丢了，至少还停在上次看的分区，而不是跳回「风格」。
 */
private var lastSelectedSection: String? = null

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UIConfigScreen(
    navController: NavController,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // l.txt 第 4 条：「默认启动页」选中后要立刻回显。配置每次保存都会自增版本号，
    // 这里订阅它，保存完就重组，下拉行重新读取真正的当前值（而不是首帧那份）。
    val configVersion by UIConfig.getInstance().configVersion.collectAsState()

    var styleVersion by remember { mutableIntStateOf(0) }
    var configState by remember(styleVersion) { mutableStateOf(loadConfigFromUIConfig()) }
    var isSaving by remember { mutableStateOf(false) }
    var showColorPicker by remember { mutableStateOf(false) }
    var selectedColorKey by remember { mutableStateOf<String?>(null) }
    var selectedGradientColorIndex by remember { mutableStateOf(-1) }
    var showResetDialog by remember { mutableStateOf(false) }
    var showDarkPalette by remember { mutableStateOf(false) }
    var saveMessage by remember { mutableStateOf<String?>(null) }
    // u.txt 第 4 条（用户）：切换深浅色 / 语言会重建 Activity —— 用 rememberSaveable
    // 把当前分区存进 saved state，重建后仍停在同一分区，而不是跳回第一项「风格」。
    var selectedSection by rememberSaveable { mutableStateOf(lastSelectedSection ?: "appearance_style") }
    LaunchedEffect(selectedSection) { lastSelectedSection = selectedSection }

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
        // 重置必须连同各风格缓存一并清空，否则旧自定义会被当作该风格的配置原样恢复。
        StyleManager.clearCache()
        // w.txt 第 5 条（用户）：重置要按**当前风格自己的预设**铺底 —— 以前这里
        // 走 switchStyle，拿到的是刚被 resetToDefault 覆盖的出厂（默认风格）配置，
        // 新拟态重置后颜色/渐变全变成默认风格那一套。
        try {
            configState = StyleManager.resetStyle(savedStyle)
        } catch (_: Exception) {
            configState = loadConfigFromUIConfig()
        }
        saveConfigToUIConfig(configState)
        AppToast.info(context, Str.get(R.string.reset_to_default_config))
        showResetDialog = false
        AppLog.success(TAG, Str.get(R.string.config_reset))
    }

    fun paletteColorValue(key: String): String = configState.paletteColorValue(key)

    fun updatePaletteColor(key: String, value: String) {
        configState = configState.updatePaletteColor(key, value)
    }

    /** 真正的导入：拷贝 → 解析 → 逐项写入 → 落盘，全程不占主线程。 */
    suspend fun applyImport(uri: Uri) {
        try {
            AppLog.i(TAG, Str.get(R.string.start_importing_ui_config))
            val tempFile = File(context.cacheDir, "ui_config_import.json")
            val copied = withContext(Dispatchers.IO) {
                FileUtils.copyUriToFile(context, uri, tempFile)
            }
            if (copied) {
                val json = withContext(Dispatchers.IO) { tempFile.readText() }
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
                    uiConfig.setGradientColor(gradient.optString("color", "#FFF9F9F9"))
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

                // 6.3.0：文件页设置（FilePrefs，4.17/N12）随统一配置一起导入
                obj.optJSONObject("files")?.let { files -> FilePrefs.importJson(files) }

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

    /**
     * 导入入口（[androidx.activity.result.ActivityResultCallback] 里调）。
     *
     * 声明必须排在 [applyImport] **之后** —— Kotlin 的局部函数不许前向引用，
     * 这也正是上一版编不过的原因（`Unresolved reference 'applyImport'`）。
     */
    fun importConfig(uri: Uri) {
        // 拷贝 + JSON 解析 + 上百次配置写入全在主线程会把导入页直接卡死（横向排查），
        // 整段挪到 IO。落盘与提示都是线程安全的（AppToast 自带切主线程）。
        scope.launch { applyImport(uri) }
    }

    fun exportConfig(uri: Uri) {
        try {
            AppLog.i(TAG, Str.get(R.string.start_exporting_ui_config))
            val uiConfig = UIConfig.getInstance()
            // 6.3.0：把文件页设置（FilePrefs，4.17/N12）合进导出 JSON。**先复制一份再 put**——
            // getConfig() 返回的是活配置对象，直接写会把它污染（下次导出/保存都带着 files 段）
            val merged = JSONObject(uiConfig.getConfig().toString())
            merged.put("files", FilePrefs.exportJson())
            val json = merged.toString(4)
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
        InAppFilePicker.OpenFile()
    ) { uri: Uri? ->
        if (uri != null) importConfig(uri)
    }

    val exportLauncher = rememberLauncherForActivityResult(
        InAppFilePicker.CreateDocument("application/json")
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
                verticalArrangement = Arrangement.spacedBy(AppDimens.cardSpacing)
            ) {
            when (selectedSection) {
                // ===== 6.3.0 M1.5（决策 2）：侧栏顶级节点「导航」=====
                "nav_default" -> {
                    item {
                        DropdownPropertyRow(
                            label = Str.get(R.string.sidebar_nav_default),
                            value = remember(configVersion) {
                                UIConfig.getInstance().getDefaultTab()
                            },
                            options = navTabOptions(),
                            onValueChange = { UIConfig.getInstance().setDefaultTab(it) }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        UnifiedCaptionText(
                            text = Str.get(R.string.sidebar_nav_default_hint),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }

                "nav_order" -> {
                    item {
                        UnifiedCaptionText(
                            text = Str.get(R.string.sidebar_nav_order_hint),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(AppDimens.cardCornerRadius))
                                .background(if (AppColors.glassEnabled()) AppColors.glassBackground() else Color.Transparent)
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        NavOrderEditor()
                    }
                }

                "appearance_style", "style_default", "style_neumorphism" -> {
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
                                        // 模式切换后按新模式归位渐变单槽位，避免陈旧浅值随后续保存写进 *_dark
                                        configState = configState.refreshGradientForMode()
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

// ===== 6.3.0 M1.5（决策 2）：导航设置（侧栏顶级节点「导航」） =====

/** 底部导航 5 个 tab 的 id → 显示名；复用各 tab 自己的标题串，避免出现两套文案 */
private fun navTabOptions(): List<Pair<String, String>> = listOf(
    "dev" to Str.get(R.string.developer),
    "tools" to Str.get(R.string.plugins),
    "files" to Str.get(R.string.files),
    "repo" to Str.get(R.string.repository),
    "manage" to Str.get(R.string.manage)
)

private fun navTabLabel(id: String): String =
    navTabOptions().firstOrNull { it.first == id }?.second ?: id

/**
 * 导航顺序编辑器：**只排序、不隐藏**（决策 2 自行拍板，可否决）。
 * 观察 configVersion —— 导入配置/恢复默认后会重新取值，避免界面顺序与实际生效顺序脱节。
 *
 * l.txt 第 4 条：上下箭头改成**长按拖动** —— 按住一行上下拖，松手落位并持久化。
 * 拖动中不实时改 [order]（那会让 pointerInput 因 key 变化而中断手势），只在抬手时
 * 按位移换算目标下标，一次性交换并保存。
 *
 * m.txt 第 4 条：拖动起来的卡片外观必须与静止时一致（不再放大/抬阴影/换底色），
 * 并且其余行要按落点平滑上移/下移让位，松手即落位。
 */
@Composable
private fun NavOrderEditor() {
    var order by remember { mutableStateOf(UIConfig.getInstance().getTabOrder()) }
    val cfgVersion by UIConfig.configVersion.collectAsState()
    LaunchedEffect(cfgVersion) { order = UIConfig.getInstance().getTabOrder() }

    val density = LocalDensity.current
    // n.txt 第 7 条：行改成**卡片**，行间留 6dp；拖动的落点换算必须把这段间隙
    // 一起算进去，否则往下拖一格会差半个卡片。
    val rowHeight = 48.dp
    val rowGap = 6.dp
    val stepPx = with(density) { (rowHeight + rowGap).toPx() }
    var draggingIndex by remember { mutableIntStateOf(-1) }
    var dragOffset by remember { mutableFloatStateOf(0f) }

    // 落点槽位：拖动过程中实时算，让其余行提前动画让位；松手时直接用它提交。
    val targetIndex = if (draggingIndex < 0) -1
    else (draggingIndex + (dragOffset / stepPx).toInt()).coerceIn(0, order.lastIndex)

    val cardShape = RoundedCornerShape(10.dp)
    // 卡片底**必须不透明**：拖动时被拖的行会从别的行上经过，半透明底会让两行文字
    // 叠在一起，看上去像重影（用户反馈的原话）。玻璃模式下也一样用实色 ——
    // 这里要的是可读性，不是透出背景。
    val rowBg = MaterialTheme.colorScheme.surface
    val idleBorder = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
    val dragBorder = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(rowGap)
    ) {
        order.forEachIndexed { index, id ->
            val dragging = draggingIndex == index
            // 被拖动的行跟手；其余行按目标槽位让位一格，并带过渡动画。
            val shiftPx = when {
                dragging || targetIndex < 0 || targetIndex == draggingIndex -> 0f
                draggingIndex < index && index <= targetIndex -> -stepPx
                draggingIndex > index && index >= targetIndex -> stepPx
                else -> 0f
            }
            val animatedShift by animateFloatAsState(
                targetValue = shiftPx,
                animationSpec = tween(durationMillis = 150),
                label = "navRowShift$index"
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(rowHeight)
                    .zIndex(if (dragging) 1f else 0f)
                    .graphicsLayer {
                        translationY = if (dragging) dragOffset else animatedShift
                    }
                    .clip(cardShape)
                    .background(rowBg)
                    .border(1.dp, if (dragging) dragBorder else idleBorder, cardShape)
                    .pointerInput(order, index) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                draggingIndex = index
                                dragOffset = 0f
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                dragOffset += amount.y
                            },
                            onDragEnd = {
                                val target = (index + (dragOffset / stepPx).toInt())
                                    .coerceIn(0, order.lastIndex)
                                draggingIndex = -1
                                dragOffset = 0f
                                if (target != index) {
                                    val next = order.toMutableList()
                                    next.removeAt(index)
                                    next.add(target, id)
                                    UIConfig.getInstance().setTabOrder(next)
                                    order = next
                                }
                            },
                            onDragCancel = {
                                draggingIndex = -1
                                dragOffset = 0f
                            }
                        )
                    }
            ) {
                Icon(
                    imageVector = Icons.Default.DragHandle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.padding(start = 8.dp).size(20.dp)
                )
                Text(
                    text = navTabLabel(id),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 10.dp)
                )
            }
        }
        Text(
            text = Str.get(R.string.nav_order_drag_hint),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 12.dp, top = 2.dp, bottom = 6.dp)
        )
    }
}
