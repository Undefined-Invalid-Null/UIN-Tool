// app/src/main/java/com/UIN/Tool/ui/screen/manage/UIConfigScreen.kt
package com.UIN.Tool.ui.screen.manage

import android.net.Uri
import android.widget.Toast
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.UIN.Tool.log.Logger
import com.UIN.Tool.ui.components.FullColorPickerDialog
import com.UIN.Tool.ui.components.UIComponents
import com.UIN.Tool.utils.FileUtils
import com.UIN.Tool.utils.UIConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File

private const val TAG = "UIConfigScreen"

// ==================== 安全颜色解析 ====================
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

// ==================== 配置数据类 ====================
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

// ==================== 从 UIConfig 加载配置 ====================
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

// ==================== 保存配置到 UIConfig ====================
private fun saveConfigToUIConfig(config: ConfigState) {
    Logger.i(TAG, "========== 保存UI配置到 UIConfig ==========")
    
    val uiConfig = UIConfig.getInstance()
    
    // 颜色
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
    
    // 形状
    uiConfig.updateShape("cornerRadiusSmall", config.cornerRadiusSmall.toInt())
    uiConfig.updateShape("cornerRadiusMedium", config.cornerRadiusMedium.toInt())
    uiConfig.updateShape("cornerRadiusLarge", config.cornerRadiusLarge.toInt())
    uiConfig.updateShape("cornerRadiusExtraLarge", config.cornerRadiusExtraLarge.toInt())
    uiConfig.updateShape("buttonCornerRadius", config.buttonCornerRadius.toInt())
    uiConfig.updateShape("cardCornerRadius", config.cardCornerRadius.toInt())
    uiConfig.updateShape("dialogCornerRadius", config.dialogCornerRadius.toInt())
    uiConfig.updateShape("inputCornerRadius", config.inputCornerRadius.toInt())
    
    // 尺寸
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
    
    // 字体
    uiConfig.updateSize("titleTextSize", config.titleTextSize.toInt())
    uiConfig.updateSize("bodyTextSize", config.bodyTextSize.toInt())
    uiConfig.updateSize("captionTextSize", config.captionTextSize.toInt())
    uiConfig.updateSize("sectionTitleTextSize", config.sectionTitleTextSize.toInt())
    
    // 特效
    uiConfig.updateBoolean("enableGlassEffect", config.enableGlassEffect)
    uiConfig.updateBoolean("enableRipple", config.enableRipple)
    uiConfig.updateBoolean("enableBold", config.enableBold)
    
    // 保存到文件
    uiConfig.saveConfig()
    
    Logger.success(TAG, "✅ UI配置保存成功")
}

// ==================== UIConfigScreen - UI 入口 ====================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UIConfigScreen(
    navController: NavController,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // ==================== 状态 ====================
    var configState by remember { mutableStateOf(loadConfigFromUIConfig()) }
    var isSaving by remember { mutableStateOf(false) }
    var showColorPicker by remember { mutableStateOf(false) }
    var selectedColorKey by remember { mutableStateOf<String?>(null) }
    var showResetDialog by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(0) }
    var saveMessage by remember { mutableStateOf<String?>(null) }

    // ==================== 加载配置 ====================
    LaunchedEffect(Unit) {
        Logger.i(TAG, "========== 从 UIConfig 加载配置 ==========")
        configState = loadConfigFromUIConfig()
        Logger.success(TAG, "配置加载完成")
    }

    // ==================== 保存配置 ====================
    fun saveConfig() {
        if (isSaving) {
            Logger.d(TAG, "保存正在进行中，跳过")
            return
        }
        
        isSaving = true
        Logger.i(TAG, "========== 用户点击保存 ==========")

        try {
            saveConfigToUIConfig(configState)
            saveMessage = "✅ 配置已保存"
            Toast.makeText(context, "配置已保存", Toast.LENGTH_SHORT).show()
            Logger.success(TAG, "✅ 配置保存成功")
        } catch (e: Exception) {
            Logger.e(TAG, "❌ 保存异常", e)
            saveMessage = "❌ 保存失败: ${e.message}"
            Toast.makeText(context, "保存异常: ${e.message}", Toast.LENGTH_SHORT).show()
        } finally {
            scope.launch {
                delay(500)
                isSaving = false
                delay(2000)
                saveMessage = null
            }
        }
    }

    // ==================== 重置配置 ====================
    fun resetConfig() {
        Logger.i(TAG, "重置配置")
        val uiConfig = UIConfig.getInstance()
        uiConfig.resetToDefault()
        configState = loadConfigFromUIConfig()
        Toast.makeText(context, "已重置为默认配置", Toast.LENGTH_SHORT).show()
        showResetDialog = false
        Logger.success(TAG, "配置已重置")
    }

    // ==================== 导入配置 ====================
    fun importConfig(uri: Uri) {
        try {
            Logger.i(TAG, "========== 开始导入UI配置 ==========")
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
                
                Toast.makeText(context, "配置已导入", Toast.LENGTH_SHORT).show()
                tempFile.delete()
                Logger.success(TAG, "✅ UI配置导入成功")
            }
        } catch (e: Exception) {
            Logger.e(TAG, "❌ 导入配置失败", e)
            Toast.makeText(context, "导入失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // ==================== 导出配置 ====================
    fun exportConfig(uri: Uri) {
        try {
            Logger.i(TAG, "========== 开始导出UI配置 ==========")
            val uiConfig = UIConfig.getInstance()
            val json = uiConfig.getConfig().toString(4)
            context.contentResolver.openOutputStream(uri)?.use { output ->
                output.write(json.toByteArray())
            }
            Toast.makeText(context, "配置已导出", Toast.LENGTH_SHORT).show()
            Logger.success(TAG, "✅ UI配置导出成功")
        } catch (e: Exception) {
            Logger.e(TAG, "❌ 导出配置失败", e)
            Toast.makeText(context, "导出失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // ==================== Launcher ====================
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

    // ==================== UI ====================
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("UI 个性化") },
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
                    containerColor = safeParseColor(configState.primaryColor),
                    titleContentColor = safeParseColor(configState.textPrimaryInverseColor),
                    navigationIconContentColor = safeParseColor(configState.textPrimaryInverseColor),
                    actionIconContentColor = safeParseColor(configState.textPrimaryInverseColor)
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
                containerColor = Color.White,
                edgePadding = 0.dp
            ) {
                listOf("颜色", "形状", "尺寸", "字体", "特效").forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Text(
                                title,
                                color = if (selectedTab == index) 
                                    safeParseColor(configState.primaryColor) 
                                else 
                                    Color(0xFF9AA6B2),
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
                            "primary" to "主题色",
                            "primary_dark" to "深色主题色",
                            "primary_light" to "浅色主题色",
                            "accent" to "强调色",
                            "success" to "成功色",
                            "warning" to "警告色",
                            "error" to "错误色",
                            "info" to "信息色",
                            "text_primary" to "主文本色",
                            "text_secondary" to "副文本色",
                            "text_hint" to "提示文本色",
                            "text_primary_inverse" to "反色文本",
                            "background" to "背景色",
                            "surface" to "表面色",
                            "surface_variant" to "变体表面色",
                            "divider" to "分割线色",
                            "glass_background" to "玻璃背景色",
                            "disabled" to "禁用色"
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

                            UIComponents.Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedColorKey = key
                                        showColorPicker = true
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    UIComponents.BodyText(displayName)
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        UIComponents.CaptionText(
                                            colorValue,
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
                            "cornerRadiusSmall" to "小圆角",
                            "cornerRadiusMedium" to "中圆角",
                            "cornerRadiusLarge" to "大圆角",
                            "cornerRadiusExtraLarge" to "超大圆角",
                            "buttonCornerRadius" to "按钮圆角",
                            "cardCornerRadius" to "卡片圆角",
                            "dialogCornerRadius" to "对话框圆角",
                            "inputCornerRadius" to "输入框圆角"
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

                            UIComponents.Card {
                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        UIComponents.BodyText(displayName)
                                        UIComponents.CaptionText("${value.toInt()} dp")
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
                                            thumbColor = safeParseColor(configState.primaryColor),
                                            activeTrackColor = safeParseColor(configState.primaryColor),
                                            inactiveTrackColor = Color(0xFFE0E4E8)
                                        )
                                    )
                                }
                            }
                        }
                    }
                    
                    2 -> {
                        val sizeKeys = listOf(
                            "buttonHeight" to "按钮高度",
                            "buttonMinWidth" to "按钮最小宽度",
                            "buttonElevation" to "按钮阴影",
                            "cardElevation" to "卡片阴影",
                            "cardPadding" to "卡片内边距",
                            "spacingSmall" to "小间距",
                            "spacingMedium" to "中间距",
                            "spacingLarge" to "大间距",
                            "iconSizeSmall" to "小图标",
                            "iconSizeMedium" to "中图标",
                            "iconSizeLarge" to "大图标",
                            "progressHeight" to "进度条高度"
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

                            UIComponents.Card {
                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        UIComponents.BodyText(displayName)
                                        UIComponents.CaptionText("${value.toInt()} dp")
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
                                            thumbColor = safeParseColor(configState.primaryColor),
                                            activeTrackColor = safeParseColor(configState.primaryColor),
                                            inactiveTrackColor = Color(0xFFE0E4E8)
                                        )
                                    )
                                }
                            }
                        }
                    }
                    
                    3 -> {
                        item {
                            UIComponents.Card {
                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        UIComponents.BodyText("标题字体大小")
                                        UIComponents.CaptionText("${configState.titleTextSize.toInt()} sp")
                                    }
                                    Slider(
                                        value = configState.titleTextSize,
                                        onValueChange = { newValue ->
                                            configState = configState.copy(titleTextSize = newValue)
                                        },
                                        valueRange = 14f..36f,
                                        steps = 11,
                                        colors = SliderDefaults.colors(
                                            thumbColor = safeParseColor(configState.primaryColor),
                                            activeTrackColor = safeParseColor(configState.primaryColor),
                                            inactiveTrackColor = Color(0xFFE0E4E8)
                                        )
                                    )
                                }
                            }
                        }

                        item {
                            UIComponents.Card {
                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        UIComponents.BodyText("正文字体大小")
                                        UIComponents.CaptionText("${configState.bodyTextSize.toInt()} sp")
                                    }
                                    Slider(
                                        value = configState.bodyTextSize,
                                        onValueChange = { newValue ->
                                            configState = configState.copy(bodyTextSize = newValue)
                                        },
                                        valueRange = 10f..22f,
                                        steps = 6,
                                        colors = SliderDefaults.colors(
                                            thumbColor = safeParseColor(configState.primaryColor),
                                            activeTrackColor = safeParseColor(configState.primaryColor),
                                            inactiveTrackColor = Color(0xFFE0E4E8)
                                        )
                                    )
                                }
                            }
                        }

                        item {
                            UIComponents.Card {
                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        UIComponents.BodyText("辅助字体大小")
                                        UIComponents.CaptionText("${configState.captionTextSize.toInt()} sp")
                                    }
                                    Slider(
                                        value = configState.captionTextSize,
                                        onValueChange = { newValue ->
                                            configState = configState.copy(captionTextSize = newValue)
                                        },
                                        valueRange = 8f..16f,
                                        steps = 4,
                                        colors = SliderDefaults.colors(
                                            thumbColor = safeParseColor(configState.primaryColor),
                                            activeTrackColor = safeParseColor(configState.primaryColor),
                                            inactiveTrackColor = Color(0xFFE0E4E8)
                                        )
                                    )
                                }
                            }
                        }

                        item {
                            UIComponents.Card {
                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        UIComponents.BodyText("章节标题大小")
                                        UIComponents.CaptionText("${configState.sectionTitleTextSize.toInt()} sp")
                                    }
                                    Slider(
                                        value = configState.sectionTitleTextSize,
                                        onValueChange = { newValue ->
                                            configState = configState.copy(sectionTitleTextSize = newValue)
                                        },
                                        valueRange = 12f..28f,
                                        steps = 8,
                                        colors = SliderDefaults.colors(
                                            thumbColor = safeParseColor(configState.primaryColor),
                                            activeTrackColor = safeParseColor(configState.primaryColor),
                                            inactiveTrackColor = Color(0xFFE0E4E8)
                                        )
                                    )
                                }
                            }
                        }

                        item {
                            UIComponents.Card {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    UIComponents.BodyText("文字加粗")
                                    UIComponents.ToggleSwitch(
                                        checked = configState.enableBold,
                                        onCheckedChange = { configState = configState.copy(enableBold = it) }
                                    )
                                }
                            }
                        }
                    }
                    
                    4 -> {
                        item {
                            UIComponents.Card {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    UIComponents.BodyText("玻璃效果")
                                    UIComponents.ToggleSwitch(
                                        checked = configState.enableGlassEffect,
                                        onCheckedChange = { configState = configState.copy(enableGlassEffect = it) }
                                    )
                                }
                            }
                        }

                        item {
                            UIComponents.Card {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    UIComponents.BodyText("波纹效果")
                                    UIComponents.ToggleSwitch(
                                        checked = configState.enableRipple,
                                        onCheckedChange = { configState = configState.copy(enableRipple = it) }
                                    )
                                }
                            }
                        }

                        item {
                            UIComponents.GlassCard(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                if (configState.enableGlassEffect) {
                                    UIComponents.BodyText("玻璃效果预览")
                                    UIComponents.CaptionText("这是一个玻璃效果卡片")
                                } else {
                                    UIComponents.BodyText("玻璃效果已禁用")
                                }
                            }
                        }
                    }
                }

                item {
                    saveMessage?.let {
                        Text(
                            it,
                            color = if (it.contains("✅")) Color(0xFF4CAF50) else Color(0xFFF44336),
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                    
                    UIComponents.PrimaryButton(
                        text = if (isSaving) "保存中..." else "保存配置",
                        icon = Icons.Default.Save,
                        onClick = { saveConfig() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isSaving,
                        loading = isSaving
                    )
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

        // 使用完整的颜色选择器
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
            title = "确认重置",
            message = "确定要重置所有 UI 配置为默认值吗？此操作不可撤销。",
            onConfirm = { resetConfig() },
            onDismiss = { showResetDialog = false }
        )
    }
}