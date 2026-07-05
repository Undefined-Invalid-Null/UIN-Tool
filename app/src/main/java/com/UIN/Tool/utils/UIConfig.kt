// app/src/main/java/com/UIN/Tool/utils/UIConfig.kt
package com.UIN.Tool.utils

import android.content.Context
import android.graphics.Color
import com.UIN.Tool.log.Logger
import org.json.JSONObject

object UIConfig {
    
    private const val TAG = "UIConfig"
    private const val PREF_NAME = "ui_config"
    private const val KEY_CONFIG = "config_json"
    private const val KEY_USE_ICON_TINT = "use_icon_tint"
    private const val KEY_CURRENT_THEME = "current_theme"
    
    private lateinit var context: Context
    internal var config: JSONObject = JSONObject()
    private var useIconTint: Boolean = true
    private var currentTheme: String = "default"
    private var isInitialized = false
    
    fun init(context: Context) {
        if (isInitialized) return
        this.context = context.applicationContext
        loadConfig()
        isInitialized = true
        Logger.i(TAG, "UIConfig初始化完成")
    }
    
    fun getInstance(): UIConfig {
        if (!isInitialized) {
            throw IllegalStateException("UIConfig未初始化，请先调用init()")
        }
        return this
    }
    
    private fun loadConfig() {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val configJson = prefs.getString(KEY_CONFIG, null)
        
        if (configJson != null) {
            try {
                config = JSONObject(configJson)
            } catch (e: Exception) {
                Logger.e(TAG, "解析配置失败，使用默认配置", e)
                config = getDefaultConfig()
            }
        } else {
            config = getDefaultConfig()
        }
        
        useIconTint = prefs.getBoolean(KEY_USE_ICON_TINT, true)
        currentTheme = prefs.getString(KEY_CURRENT_THEME, "default") ?: "default"
    }
    
    fun saveConfig() {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString(KEY_CONFIG, config.toString())
            putBoolean(KEY_USE_ICON_TINT, useIconTint)
            putString(KEY_CURRENT_THEME, currentTheme)
        }.apply()
        Logger.d(TAG, "配置已保存")
    }
    
    fun getConfig(): JSONObject = config
    
    private fun getDefaultConfig(): JSONObject {
        return try {
            JSONObject().apply {
                put("theme", JSONObject().apply {
                    put("primary", "#FF1A3A4A")
                    put("primary_dark", "#FF0F2838")
                    put("primary_light", "#FF2D5A70")
                    put("accent", "#FF4A8A9E")
                    put("success", "#FF4CAF50")
                    put("warning", "#FFFF9800")
                    put("error", "#FFF44336")
                    put("info", "#FF2196F3")
                    put("text_primary", "#FF212121")
                    put("text_secondary", "#FF757575")
                    put("text_hint", "#FFBDBDBD")
                    put("text_primary_inverse", "#FFFFFFFF")
                    put("background", "#FFF5F7FA")
                    put("surface", "#FFFFFFFF")
                    put("surface_variant", "#FFF5F7FA")
                    put("divider", "#FFE0E4E8")
                    put("glass_background", "#E6FFFFFF")
                    put("disabled", "#FFBDBDBD")
                })
                put("shape", JSONObject().apply {
                    put("cornerRadiusSmall", 8)
                    put("cornerRadiusMedium", 12)
                    put("cornerRadiusLarge", 16)
                    put("cornerRadiusExtraLarge", 24)
                    put("buttonCornerRadius", 12)
                    put("cardCornerRadius", 16)
                    put("dialogCornerRadius", 20)
                    put("inputCornerRadius", 8)
                })
                put("size", JSONObject().apply {
                    put("buttonHeight", 44)
                    put("buttonMinWidth", 80)
                    put("buttonElevation", 2)
                    put("cardElevation", 4)
                    put("cardPadding", 16)
                    put("spacingSmall", 4)
                    put("spacingMedium", 8)
                    put("spacingLarge", 16)
                    put("iconSizeSmall", 16)
                    put("iconSizeMedium", 20)
                    put("iconSizeLarge", 24)
                    put("progressHeight", 4)
                    put("titleTextSize", 20)
                    put("bodyTextSize", 14)
                    put("captionTextSize", 12)
                    put("sectionTitleTextSize", 18)
                })
                put("font", JSONObject().apply {
                    put("fontFamily", "sans-serif")
                    put("enableBold", true)
                })
                put("experimental", JSONObject().apply {
                    put("enableGlassEffect", true)
                    put("enableRipple", true)
                })
            }
        } catch (e: Exception) {
            JSONObject()
        }
    }
    
    // ==================== 通用方法 ====================
    
    fun getColor(key: String): Int {
        return try {
            val theme = config.optJSONObject("theme")
            val colorStr = theme?.optString(key, "#FFFFFFFF") ?: "#FFFFFFFF"
            if (colorStr.isNotEmpty() && colorStr.startsWith("#") && colorStr.length >= 7) {
                Color.parseColor(colorStr)
            } else {
                Color.parseColor("#FFFFFFFF")
            }
        } catch (e: Exception) {
            Logger.w(TAG, "颜色解析失败: $key, 使用默认颜色")
            Color.parseColor("#FFFFFFFF")
        }
    }
    
    fun getColorString(key: String): String {
        val theme = config.optJSONObject("theme")
        val value = theme?.optString(key, "#FFFFFFFF") ?: "#FFFFFFFF"
        return if (value.isNotEmpty()) value else "#FFFFFFFF"
    }
    
    fun updateColor(key: String, value: String) {
        try {
            val theme = config.optJSONObject("theme")
            if (theme != null) {
                theme.put(key, value)
                saveConfig()
                Logger.d(TAG, "更新颜色: $key = $value")
            }
        } catch (e: Exception) {
            Logger.e(TAG, "更新颜色失败", e)
        }
    }
    
    fun getShape(key: String): Float {
        val shape = config.optJSONObject("shape")
        return shape?.optDouble(key, 8.0)?.toFloat() ?: 8f
    }
    
    fun updateShape(key: String, value: Int) {
        try {
            val shape = config.optJSONObject("shape")
            if (shape != null) {
                shape.put(key, value)
                saveConfig()
                Logger.d(TAG, "更新形状: $key = $value")
            }
        } catch (e: Exception) {
            Logger.e(TAG, "更新形状失败", e)
        }
    }
    
    fun getSize(key: String): Float {
        val size = config.optJSONObject("size")
        return size?.optDouble(key, 16.0)?.toFloat() ?: 16f
    }
    
    fun updateSize(key: String, value: Int) {
        try {
            val size = config.optJSONObject("size")
            if (size != null) {
                size.put(key, value)
                saveConfig()
                Logger.d(TAG, "更新尺寸: $key = $value")
            }
        } catch (e: Exception) {
            Logger.e(TAG, "更新尺寸失败", e)
        }
    }
    
    fun getBoolean(key: String, def: Boolean): Boolean {
        val exp = config.optJSONObject("experimental")
        return exp?.optBoolean(key, def) ?: def
    }
    
    fun updateBoolean(key: String, value: Boolean) {
        try {
            val exp = config.optJSONObject("experimental")
            if (exp != null) {
                exp.put(key, value)
                saveConfig()
                Logger.d(TAG, "更新布尔值: $key = $value")
            }
        } catch (e: Exception) {
            Logger.e(TAG, "更新布尔值失败", e)
        }
    }
    
    fun resetToDefault() {
        config = getDefaultConfig()
        useIconTint = true
        currentTheme = "default"
        saveConfig()
        Logger.i(TAG, "已重置为默认配置")
    }
    
    fun isUseIconTint(): Boolean = useIconTint
    
    fun setUseIconTint(use: Boolean) {
        useIconTint = use
        saveConfig()
    }
    
    fun getCurrentTheme(): String = currentTheme
    
    fun setCurrentTheme(theme: String) {
        currentTheme = theme
        saveConfig()
    }
    
    fun isGlassEffectEnabled(): Boolean = getBoolean("enableGlassEffect", true)
    fun isRippleEnabled(): Boolean = getBoolean("enableRipple", true)
    fun isBoldEnabled(): Boolean = config.optJSONObject("font")?.optBoolean("enableBold", true) ?: true
    fun getFontFamily(): String = config.optJSONObject("font")?.optString("fontFamily", "sans-serif") ?: "sans-serif"
    
    // ==================== 颜色便捷方法 ====================
    
    fun getPrimaryColor(): Int = getColor("primary")
    fun getPrimaryDarkColor(): Int = getColor("primary_dark")
    fun getPrimaryLightColor(): Int = getColor("primary_light")
    fun getAccentColor(): Int = getColor("accent")
    fun getSuccessColor(): Int = getColor("success")
    fun getWarningColor(): Int = getColor("warning")
    fun getErrorColor(): Int = getColor("error")
    fun getInfoColor(): Int = getColor("info")
    fun getTextPrimaryColor(): Int = getColor("text_primary")
    fun getTextSecondaryColor(): Int = getColor("text_secondary")
    fun getTextHintColor(): Int = getColor("text_hint")
    fun getTextPrimaryInverseColor(): Int = getColor("text_primary_inverse")
    fun getBackgroundColor(): Int = getColor("background")
    fun getSurfaceColor(): Int = getColor("surface")
    fun getSurfaceVariantColor(): Int = getColor("surface_variant")
    fun getDividerColor(): Int = getColor("divider")
    fun getGlassBackgroundColor(): Int = getColor("glass_background")
    fun getDisabledColor(): Int = getColor("disabled")
    
    // ==================== 形状便捷方法 ====================
    
    fun getCornerRadiusSmall(): Float = getShape("cornerRadiusSmall")
    fun getCornerRadiusMedium(): Float = getShape("cornerRadiusMedium")
    fun getCornerRadiusLarge(): Float = getShape("cornerRadiusLarge")
    fun getCornerRadiusExtraLarge(): Float = getShape("cornerRadiusExtraLarge")
    fun getButtonCornerRadius(): Float = getShape("buttonCornerRadius")
    fun getCardCornerRadius(): Float = getShape("cardCornerRadius")
    fun getDialogCornerRadius(): Float = getShape("dialogCornerRadius")
    fun getInputCornerRadius(): Float = getShape("inputCornerRadius")
    
    // ==================== 尺寸便捷方法 ====================
    
    fun getButtonHeight(): Float = getSize("buttonHeight")
    fun getButtonMinWidth(): Float = getSize("buttonMinWidth")
    fun getButtonElevation(): Float = getSize("buttonElevation")
    fun getCardElevation(): Float = getSize("cardElevation")
    fun getCardPadding(): Float = getSize("cardPadding")
    fun getSpacingSmall(): Float = getSize("spacingSmall")
    fun getSpacingMedium(): Float = getSize("spacingMedium")
    fun getSpacingLarge(): Float = getSize("spacingLarge")
    fun getIconSizeSmall(): Float = getSize("iconSizeSmall")
    fun getIconSizeMedium(): Float = getSize("iconSizeMedium")
    fun getIconSizeLarge(): Float = getSize("iconSizeLarge")
    fun getProgressHeight(): Float = getSize("progressHeight")
    fun getTitleTextSize(): Float = getSize("titleTextSize")
    fun getBodyTextSize(): Float = getSize("bodyTextSize")
    fun getCaptionTextSize(): Float = getSize("captionTextSize")
    fun getSectionTitleTextSize(): Float = getSize("sectionTitleTextSize")
}