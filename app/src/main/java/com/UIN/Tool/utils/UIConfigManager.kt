// app/src/main/java/com/UIN/Tool/utils/UIConfigManager.kt
package com.UIN.Tool.utils

import android.content.Context
import android.util.Log
import com.UIN.Tool.log.Logger
import org.json.JSONObject
import java.io.File

/**
 * UI配置管理器 - 后端逻辑
 * 负责配置的读取、保存、导入、导出
 */
class UIConfigManager(private val context: Context) {

    companion object {
        private const val TAG = "UIConfigManager"
        private const val PREF_NAME = "ui_config"
        private const val KEY_CONFIG = "config_json"
        private const val UI_CONFIG_FILE = "ui_config.json"
        
        @Volatile
        private var instance: UIConfigManager? = null
        
        fun getInstance(context: Context): UIConfigManager {
            return instance ?: synchronized(this) {
                instance ?: UIConfigManager(context.applicationContext).also { instance = it }
            }
        }
    }

    // ==================== 配置数据类 ====================
    
    data class UIConfigData(
        // 颜色
        val primaryColor: String = "#FF1A3A4A",
        val primaryDarkColor: String = "#FF0F2838",
        val primaryLightColor: String = "#FF2D5A70",
        val accentColor: String = "#FF4A8A9E",
        val successColor: String = "#FF4CAF50",
        val warningColor: String = "#FFFF9800",
        val errorColor: String = "#FFF44336",
        val infoColor: String = "#FF2196F3",
        val textPrimaryColor: String = "#FF212121",
        val textSecondaryColor: String = "#FF757575",
        val textHintColor: String = "#FFBDBDBD",
        val textPrimaryInverseColor: String = "#FFFFFFFF",
        val backgroundColor: String = "#FFF5F7FA",
        val surfaceColor: String = "#FFFFFFFF",
        val surfaceVariantColor: String = "#FFF5F7FA",
        val dividerColor: String = "#FFE0E4E8",
        val glassBackgroundColor: String = "#E6FFFFFF",
        val disabledColor: String = "#FFBDBDBD",
        // 形状
        val cornerRadiusSmall: Float = 8f,
        val cornerRadiusMedium: Float = 12f,
        val cornerRadiusLarge: Float = 16f,
        val cornerRadiusExtraLarge: Float = 24f,
        val buttonCornerRadius: Float = 12f,
        val cardCornerRadius: Float = 16f,
        val dialogCornerRadius: Float = 20f,
        val inputCornerRadius: Float = 8f,
        // 尺寸
        val buttonHeight: Float = 44f,
        val buttonMinWidth: Float = 80f,
        val buttonElevation: Float = 2f,
        val cardElevation: Float = 4f,
        val cardPadding: Float = 16f,
        val spacingSmall: Float = 4f,
        val spacingMedium: Float = 8f,
        val spacingLarge: Float = 16f,
        val iconSizeSmall: Float = 16f,
        val iconSizeMedium: Float = 20f,
        val iconSizeLarge: Float = 24f,
        val progressHeight: Float = 4f,
        val titleTextSize: Float = 20f,
        val bodyTextSize: Float = 14f,
        val captionTextSize: Float = 12f,
        val sectionTitleTextSize: Float = 18f,
        // 特效
        val enableGlassEffect: Boolean = true,
        val enableRipple: Boolean = true,
        val enableBold: Boolean = true
    )

    // ==================== 当前配置 ====================
    
    private var currentConfig: UIConfigData = UIConfigData()
    private val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    
    init {
        loadConfig()
    }

    // ==================== 加载配置 ====================
    
    fun loadConfig(): UIConfigData {
        Logger.i(TAG, "========== 加载UI配置 ==========")
        
        val configJson = prefs.getString(KEY_CONFIG, null)
        
        if (configJson != null) {
            try {
                Logger.d(TAG, "从SharedPreferences加载配置")
                currentConfig = parseConfigFromJson(configJson)
                Logger.success(TAG, "配置加载成功")
                logCurrentConfig()
                return currentConfig
            } catch (e: Exception) {
                Logger.e(TAG, "解析配置失败，使用默认配置", e)
                currentConfig = UIConfigData()
                saveConfigInternal(currentConfig)
            }
        } else {
            // 尝试从文件加载
            Logger.d(TAG, "SharedPreferences无配置，尝试从文件加载")
            val fileConfig = loadConfigFromFile()
            if (fileConfig != null) {
                currentConfig = fileConfig
                Logger.success(TAG, "从文件加载配置成功")
                // 保存到SharedPreferences
                saveConfigInternal(currentConfig)
            } else {
                Logger.d(TAG, "使用默认配置")
                currentConfig = UIConfigData()
                saveConfigInternal(currentConfig)
            }
        }
        
        logCurrentConfig()
        Logger.i(TAG, "========== 配置加载完成 ==========")
        return currentConfig
    }
    
    private fun loadConfigFromFile(): UIConfigData? {
        try {
            val file = File(context.filesDir, UI_CONFIG_FILE)
            if (!file.exists()) {
                Logger.d(TAG, "配置文件不存在: ${file.absolutePath}")
                return null
            }
            
            val json = file.readText()
            Logger.d(TAG, "从文件读取配置: ${file.absolutePath}, 大小: ${json.length} bytes")
            return parseConfigFromJson(json)
        } catch (e: Exception) {
            Logger.e(TAG, "从文件加载配置失败", e)
            return null
        }
    }
    
    private fun parseConfigFromJson(json: String): UIConfigData {
        val obj = JSONObject(json)
        val theme = obj.optJSONObject("theme") ?: JSONObject()
        val shape = obj.optJSONObject("shape") ?: JSONObject()
        val size = obj.optJSONObject("size") ?: JSONObject()
        val experimental = obj.optJSONObject("experimental") ?: JSONObject()
        val font = obj.optJSONObject("font") ?: JSONObject()
        
        return UIConfigData(
            primaryColor = theme.optString("primary", "#FF1A3A4A"),
            primaryDarkColor = theme.optString("primary_dark", "#FF0F2838"),
            primaryLightColor = theme.optString("primary_light", "#FF2D5A70"),
            accentColor = theme.optString("accent", "#FF4A8A9E"),
            successColor = theme.optString("success", "#FF4CAF50"),
            warningColor = theme.optString("warning", "#FFFF9800"),
            errorColor = theme.optString("error", "#FFF44336"),
            infoColor = theme.optString("info", "#FF2196F3"),
            textPrimaryColor = theme.optString("text_primary", "#FF212121"),
            textSecondaryColor = theme.optString("text_secondary", "#FF757575"),
            textHintColor = theme.optString("text_hint", "#FFBDBDBD"),
            textPrimaryInverseColor = theme.optString("text_primary_inverse", "#FFFFFFFF"),
            backgroundColor = theme.optString("background", "#FFF5F7FA"),
            surfaceColor = theme.optString("surface", "#FFFFFFFF"),
            surfaceVariantColor = theme.optString("surface_variant", "#FFF5F7FA"),
            dividerColor = theme.optString("divider", "#FFE0E4E8"),
            glassBackgroundColor = theme.optString("glass_background", "#E6FFFFFF"),
            disabledColor = theme.optString("disabled", "#FFBDBDBD"),
            cornerRadiusSmall = shape.optDouble("cornerRadiusSmall", 8.0).toFloat(),
            cornerRadiusMedium = shape.optDouble("cornerRadiusMedium", 12.0).toFloat(),
            cornerRadiusLarge = shape.optDouble("cornerRadiusLarge", 16.0).toFloat(),
            cornerRadiusExtraLarge = shape.optDouble("cornerRadiusExtraLarge", 24.0).toFloat(),
            buttonCornerRadius = shape.optDouble("buttonCornerRadius", 12.0).toFloat(),
            cardCornerRadius = shape.optDouble("cardCornerRadius", 16.0).toFloat(),
            dialogCornerRadius = shape.optDouble("dialogCornerRadius", 20.0).toFloat(),
            inputCornerRadius = shape.optDouble("inputCornerRadius", 8.0).toFloat(),
            buttonHeight = size.optDouble("buttonHeight", 44.0).toFloat(),
            buttonMinWidth = size.optDouble("buttonMinWidth", 80.0).toFloat(),
            buttonElevation = size.optDouble("buttonElevation", 2.0).toFloat(),
            cardElevation = size.optDouble("cardElevation", 4.0).toFloat(),
            cardPadding = size.optDouble("cardPadding", 16.0).toFloat(),
            spacingSmall = size.optDouble("spacingSmall", 4.0).toFloat(),
            spacingMedium = size.optDouble("spacingMedium", 8.0).toFloat(),
            spacingLarge = size.optDouble("spacingLarge", 16.0).toFloat(),
            iconSizeSmall = size.optDouble("iconSizeSmall", 16.0).toFloat(),
            iconSizeMedium = size.optDouble("iconSizeMedium", 20.0).toFloat(),
            iconSizeLarge = size.optDouble("iconSizeLarge", 24.0).toFloat(),
            progressHeight = size.optDouble("progressHeight", 4.0).toFloat(),
            titleTextSize = size.optDouble("titleTextSize", 20.0).toFloat(),
            bodyTextSize = size.optDouble("bodyTextSize", 14.0).toFloat(),
            captionTextSize = size.optDouble("captionTextSize", 12.0).toFloat(),
            sectionTitleTextSize = size.optDouble("sectionTitleTextSize", 18.0).toFloat(),
            enableGlassEffect = experimental.optBoolean("enableGlassEffect", true),
            enableRipple = experimental.optBoolean("enableRipple", true),
            enableBold = font.optBoolean("enableBold", true)
        )
    }

    // ==================== 保存配置 ====================
    
    fun saveConfig(config: UIConfigData): Boolean {
        Logger.i(TAG, "========== 保存UI配置 ==========")
        Logger.d(TAG, "📌 保存配置详情:")
        Logger.d(TAG, "  主色: ${config.primaryColor}")
        Logger.d(TAG, "  深色主题: ${config.primaryDarkColor}")
        Logger.d(TAG, "  强调色: ${config.accentColor}")
        Logger.d(TAG, "  按钮圆角: ${config.buttonCornerRadius}")
        Logger.d(TAG, "  卡片圆角: ${config.cardCornerRadius}")
        Logger.d(TAG, "  按钮高度: ${config.buttonHeight}")
        Logger.d(TAG, "  标题字体: ${config.titleTextSize}")
        Logger.d(TAG, "  正文字体: ${config.bodyTextSize}")
        Logger.d(TAG, "  玻璃效果: ${config.enableGlassEffect}")
        
        return try {
            // 更新内存配置
            currentConfig = config
            
            // 保存到SharedPreferences
            val success = saveConfigInternal(config)
            
            // 同时保存到文件
            saveConfigToFile(config)
            
            // 同步到UIConfig（兼容旧代码）
            syncToUIConfig(config)
            
            Logger.success(TAG, "✅ 配置保存成功")
            Logger.i(TAG, "========== 配置保存完成 ==========")
            success
        } catch (e: Exception) {
            Logger.e(TAG, "❌ 保存配置失败", e)
            false
        }
    }
    
    private fun saveConfigInternal(config: UIConfigData): Boolean {
        return try {
            val json = configToJson(config)
            prefs.edit().putString(KEY_CONFIG, json).apply()
            Logger.d(TAG, "SharedPreferences保存成功，大小: ${json.length} bytes")
            true
        } catch (e: Exception) {
            Logger.e(TAG, "SharedPreferences保存失败", e)
            false
        }
    }
    
    private fun saveConfigToFile(config: UIConfigData) {
        try {
            val file = File(context.filesDir, UI_CONFIG_FILE)
            val json = configToJson(config)
            file.writeText(json)
            Logger.d(TAG, "文件保存成功: ${file.absolutePath}, 大小: ${json.length} bytes")
        } catch (e: Exception) {
            Logger.e(TAG, "文件保存失败", e)
        }
    }
    
    private fun configToJson(config: UIConfigData): String {
        val theme = JSONObject().apply {
            put("primary", config.primaryColor)
            put("primary_dark", config.primaryDarkColor)
            put("primary_light", config.primaryLightColor)
            put("accent", config.accentColor)
            put("success", config.successColor)
            put("warning", config.warningColor)
            put("error", config.errorColor)
            put("info", config.infoColor)
            put("text_primary", config.textPrimaryColor)
            put("text_secondary", config.textSecondaryColor)
            put("text_hint", config.textHintColor)
            put("text_primary_inverse", config.textPrimaryInverseColor)
            put("background", config.backgroundColor)
            put("surface", config.surfaceColor)
            put("surface_variant", config.surfaceVariantColor)
            put("divider", config.dividerColor)
            put("glass_background", config.glassBackgroundColor)
            put("disabled", config.disabledColor)
        }
        
        val shape = JSONObject().apply {
            put("cornerRadiusSmall", config.cornerRadiusSmall.toInt())
            put("cornerRadiusMedium", config.cornerRadiusMedium.toInt())
            put("cornerRadiusLarge", config.cornerRadiusLarge.toInt())
            put("cornerRadiusExtraLarge", config.cornerRadiusExtraLarge.toInt())
            put("buttonCornerRadius", config.buttonCornerRadius.toInt())
            put("cardCornerRadius", config.cardCornerRadius.toInt())
            put("dialogCornerRadius", config.dialogCornerRadius.toInt())
            put("inputCornerRadius", config.inputCornerRadius.toInt())
        }
        
        val size = JSONObject().apply {
            put("buttonHeight", config.buttonHeight.toInt())
            put("buttonMinWidth", config.buttonMinWidth.toInt())
            put("buttonElevation", config.buttonElevation.toInt())
            put("cardElevation", config.cardElevation.toInt())
            put("cardPadding", config.cardPadding.toInt())
            put("spacingSmall", config.spacingSmall.toInt())
            put("spacingMedium", config.spacingMedium.toInt())
            put("spacingLarge", config.spacingLarge.toInt())
            put("iconSizeSmall", config.iconSizeSmall.toInt())
            put("iconSizeMedium", config.iconSizeMedium.toInt())
            put("iconSizeLarge", config.iconSizeLarge.toInt())
            put("progressHeight", config.progressHeight.toInt())
            put("titleTextSize", config.titleTextSize.toInt())
            put("bodyTextSize", config.bodyTextSize.toInt())
            put("captionTextSize", config.captionTextSize.toInt())
            put("sectionTitleTextSize", config.sectionTitleTextSize.toInt())
        }
        
        val experimental = JSONObject().apply {
            put("enableGlassEffect", config.enableGlassEffect)
            put("enableRipple", config.enableRipple)
        }
        
        val font = JSONObject().apply {
            put("enableBold", config.enableBold)
            put("fontFamily", "sans-serif")
        }
        
        return JSONObject().apply {
            put("theme", theme)
            put("shape", shape)
            put("size", size)
            put("experimental", experimental)
            put("font", font)
        }.toString(4)
    }
    
    private fun syncToUIConfig(config: UIConfigData) {
        try {
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
            Logger.d(TAG, "已同步到UIConfig")
        } catch (e: Exception) {
            Logger.e(TAG, "同步到UIConfig失败", e)
        }
    }

    // ==================== 获取配置 ====================
    
    fun getConfig(): UIConfigData = currentConfig

    // ==================== 重置配置 ====================
    
    fun resetConfig(): UIConfigData {
        Logger.i(TAG, "重置UI配置为默认值")
        currentConfig = UIConfigData()
        saveConfig(currentConfig)
        Logger.success(TAG, "配置已重置")
        return currentConfig
    }

    // ==================== 导入导出 ====================
    
    fun importConfig(json: String): Boolean {
        Logger.i(TAG, "导入UI配置")
        return try {
            val config = parseConfigFromJson(json)
            currentConfig = config
            saveConfig(config)
            Logger.success(TAG, "导入成功")
            true
        } catch (e: Exception) {
            Logger.e(TAG, "导入失败", e)
            false
        }
    }
    
    fun exportConfig(): String {
        Logger.i(TAG, "导出UI配置")
        val json = configToJson(currentConfig)
        Logger.d(TAG, "导出大小: ${json.length} bytes")
        return json
    }

    // ==================== 日志 ====================
    
    private fun logCurrentConfig() {
        Logger.d(TAG, "📌 当前配置:")
        Logger.d(TAG, "  主色: ${currentConfig.primaryColor}")
        Logger.d(TAG, "  深色主题: ${currentConfig.primaryDarkColor}")
        Logger.d(TAG, "  强调色: ${currentConfig.accentColor}")
        Logger.d(TAG, "  按钮圆角: ${currentConfig.buttonCornerRadius}")
        Logger.d(TAG, "  卡片圆角: ${currentConfig.cardCornerRadius}")
        Logger.d(TAG, "  按钮高度: ${currentConfig.buttonHeight}")
        Logger.d(TAG, "  标题字体: ${currentConfig.titleTextSize}")
        Logger.d(TAG, "  玻璃效果: ${currentConfig.enableGlassEffect}")
    }
}