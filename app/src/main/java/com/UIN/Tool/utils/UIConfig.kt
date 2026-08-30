// app/src/main/java/com/UIN/Tool/utils/UIConfig.kt
package com.UIN.Tool.utils

import com.UIN.Tool.R
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import com.UIN.Tool.log.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject

object UIConfig {
    
    private const val TAG = "UIConfig"
    private const val PREF_NAME = "ui_config"
    private const val KEY_CONFIG = "config_json"
    private const val KEY_USE_ICON_TINT = "use_icon_tint"
    private const val KEY_CURRENT_THEME = "current_theme"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_CURRENT_STYLE = "current_style"
    
    const val THEME_MODE_SYSTEM = "system"
    const val THEME_MODE_LIGHT = "light"
    const val THEME_MODE_DARK = "dark"
    
    private lateinit var context: Context
    internal var config: JSONObject = JSONObject()
    private var useIconTint: Boolean = true
    private var currentTheme: String = "default"
    private var themeMode: String = THEME_MODE_SYSTEM
    private var currentStyle: String = "default"
    private var isInitialized = false
    
    // 配置版本号：每次保存自增，供 Compose 主题观察以触发重组
    private val _configVersion = MutableStateFlow(0)
    val configVersion: StateFlow<Int> = _configVersion
    
    fun init(context: Context) {
        if (isInitialized) return
        this.context = context.applicationContext
        loadConfig()
        isInitialized = true
        Logger.i(TAG, Str.get(R.string.uiconfig_initialized))
    }
    
    fun getInstance(): UIConfig {
        if (!isInitialized) {
            throw IllegalStateException(Str.get(R.string.uiconfig_not_initialized_call_init_f))
        }
        return this
    }

    fun isInitialized(): Boolean = isInitialized
    
    private fun loadConfig() {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val configJson = prefs.getString(KEY_CONFIG, null)
        
        if (configJson != null) {
            try {
                config = JSONObject(configJson)
            } catch (e: Exception) {
                Logger.e(TAG, Str.get(R.string.failed_to_parse_config_using_default), e)
                config = getDefaultConfig()
            }
        } else {
            config = getDefaultConfig()
        }
        
        ensureDefaultSections()
        migrateLegacyDefaults()
        
        useIconTint = prefs.getBoolean(KEY_USE_ICON_TINT, true)
        currentTheme = prefs.getString(KEY_CURRENT_THEME, "default") ?: "default"
        themeMode = prefs.getString(KEY_THEME_MODE, THEME_MODE_SYSTEM) ?: THEME_MODE_SYSTEM
        currentStyle = prefs.getString(KEY_CURRENT_STYLE, "default") ?: "default"
    }

    /**
     * 为旧版本配置补齐缺失的区块（如 theme_dark），保证升级后深色模式仍有默认配色。
     */
    private fun ensureDefaultSections(target: JSONObject = config) {
        val def = getDefaultConfig()
        val sections = listOf("theme", "theme_dark", "shape", "size", "font", "experimental", "gradient")
        for (section in sections) {
            val current = target.optJSONObject(section)
            val defaultSection = def.optJSONObject(section) ?: continue
            if (current == null) {
                target.put(section, defaultSection)
            } else {
                val keys = defaultSection.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    if (!current.has(key)) {
                        current.put(key, defaultSection.get(key))
                    }
                }
            }
        }
    }

    /**
     * 迁移旧版本遗留的默认值：
     * 旧深色模式 primary_dark 默认值 #FF4A4A4A（无消费方）迁移为新默认值
     * #FFD0D0D0（与深色 onSecondaryContainer 一致，避免接入后深色文本对比度回退）。
     */
    private fun migrateLegacyDefaults() {
        try {
            val themeDark = config.optJSONObject("theme_dark") ?: return
            val oldDefault = "#FF4A4A4A"
            val newDefault = "#FFD0D0D0"
            if (themeDark.optString("primary_dark", "") == oldDefault) {
                themeDark.put("primary_dark", newDefault)
            }
            migrateLegacyGradientDefault()
        } catch (e: Exception) {
            Logger.w(TAG, "migrateLegacyDefaults failed: ${e.message}")
        }
    }

    /**
     * 迁移旧版「多色默认渐变」（multi + 三色默认值）为新版「单色默认渐变」：
     * 浅色 #FFC4D6DF / 深色 #FF4C4F51，方向自右下 → 左上。
     * 仅当用户未自定义（仍为旧默认三色列表）时迁移，保留用户自定义配置。
     */
    private fun migrateLegacyGradientDefault() {
        try {
            val gradient = config.optJSONObject("gradient") ?: return
            val oldDefaultColors = listOf("#FFC4D6DF", "#FFD6E8F2", "#FFEAF4FA")
            val currentColors = gradient.optJSONArray("colors")?.let { arr ->
                (0 until arr.length()).map { arr.optString(it, "") }
            } ?: emptyList()
            val isOldDefault = gradient.optString("mode", "") == GRADIENT_MODE_MULTI &&
                gradient.optString("color", "") == "#FFC4D6DF" &&
                currentColors == oldDefaultColors
            if (isOldDefault) {
                gradient.put("mode", GRADIENT_MODE_SINGLE)
                gradient.put("color", "#FFC4D6DF")
                gradient.put("color_dark", "#FF4C4F51")
                config.put("gradient", gradient)
            } else if (!gradient.has("color_dark")) {
                gradient.put("color_dark", "#FF4C4F51")
                config.put("gradient", gradient)
            }
            if (gradient.optString("color_dark", "") == "#FF4D4F50") {
                gradient.put("color_dark", "#FF4C4F51")
                config.put("gradient", gradient)
            }
            // 旧版 setGradientColorBoth 会把同一个颜色同时写入浅色与深色
            // （如深色也存成浅色默认 #FFC4D6DF）；这种「深色等于浅色」的旧配置
            // 需迁移回深色独立的默认单色 #FF4C4F51，否则深色模式仍显示浅色渐变。
            if (gradient.optString("color_dark", "") == gradient.optString("color", "")) {
                gradient.put("color_dark", "#FF4C4F51")
                config.put("gradient", gradient)
            }
        } catch (e: Exception) {
            Logger.w(TAG, "migrateLegacyGradientDefault failed: ${e.message}")
        }
    }

    fun saveConfig() {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString(KEY_CONFIG, config.toString())
            putBoolean(KEY_USE_ICON_TINT, useIconTint)
            putString(KEY_CURRENT_THEME, currentTheme)
            putString(KEY_THEME_MODE, themeMode)
            putString(KEY_CURRENT_STYLE, currentStyle)
        }.apply()
        _configVersion.value += 1
        Logger.d(TAG, Str.get(R.string.config_saved))
    }

    /**
     * 批量原子写入整份配置：
     * - 单次序列化 + 单次 SharedPreferences 写入（替代逐色 update* 每次全量序列化）
     * - 写入前校验必须是合法 JSON 对象，避免半途失败写入残缺配置
     */
    fun applyJson(json: JSONObject) {
        if (!isInitialized) return
        try {
            val validated = JSONObject(json.toString())
            if (validated.length() == 0) {
                Logger.w(TAG, "applyJson rejected: empty config")
                return
            }
            ensureDefaultSections(validated)
            config = validated
            saveConfig()
        } catch (e: Exception) {
            Logger.e(TAG, Str.get(R.string.failed_to_apply_config), e)
        }
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
                    put("glass_background", "#B3FFFFFF")
                    put("disabled", "#FFBDBDBD")
                    put("status_bar", "#FF0F2838")
                    put("navigation_bar", "#FFFFFFFF")
                    put("nav_selected", "#FF1A3A4A")
                    put("nav_unselected", "#FF9AA6B2")
                })
                put("theme_dark", JSONObject().apply {
                    put("primary", "#FF8B949E")
                    put("primary_dark", "#FFD0D0D0")
                    put("primary_light", "#FFE6E6E6")
                    put("accent", "#FF8B949E")
                    put("success", "#FF66BB6A")
                    put("warning", "#FFFFB74D")
                    put("error", "#FFFF6E6E")
                    put("info", "#FF26C6DA")
                    put("text_primary", "#FFE8E8E8")
                    put("text_secondary", "#FFA8A8A8")
                    put("text_hint", "#FF6E6E6E")
                    put("text_primary_inverse", "#FF1A1A1A")
                    put("background", "#FF2A2A2A")
                    put("surface", "#FF363636")
                    put("surface_variant", "#FF3F3F3F")
                    put("divider", "#FF484848")
                    put("glass_background", "#B32A2A2A")
                    put("disabled", "#FF666666")
                    put("status_bar", "#FF1F1F1F")
                    put("navigation_bar", "#FF2A2A2A")
                    put("nav_selected", "#FF8B949E")
                    put("nav_unselected", "#FF7A7A7A")
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
                    put("enableNeumorphism", false)
                    put("enableNeumorphismInset", true)
                    put("enableNeumorphismGlow", true)
                    put("neumorphismIntensity", "light")
                    put("animationSpeed", "medium")
                })
                put("gradient", JSONObject().apply {
                    put("enabled", true)
                    put("mode", GRADIENT_MODE_SINGLE)
                    put("mode_dark", GRADIENT_MODE_SINGLE)
                    put("color", "#FFC4D6DF")
                    put("color_dark", "#FF4C4F51")
                    put("from", GRADIENT_DIR_BOTTOM_RIGHT)
                    put("from_dark", GRADIENT_DIR_BOTTOM_RIGHT)
                    put("to", GRADIENT_DIR_TOP_LEFT)
                    put("to_dark", GRADIENT_DIR_TOP_LEFT)
                    put("colors", org.json.JSONArray().apply {
                        put("#FFC4D6DF")
                        put("#FFD6E8F2")
                        put("#FFEAF4FA")
                    })
                    put("colors_dark", org.json.JSONArray().apply {
                        put("#FF4C4F51")
                        put("#FF5A5E62")
                        put("#FF6B6F73")
                    })
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
            Logger.w(TAG, Str.get(R.string.color_parse_failed_key_using_default, key))
            Color.parseColor("#FFFFFFFF")
        }
    }
    
    fun getColorString(key: String): String {
        val theme = config.optJSONObject("theme")
        val value = theme?.optString(key, "#FFFFFFFF") ?: "#FFFFFFFF"
        return if (value.isNotEmpty()) value else "#FFFFFFFF"
    }
    
    fun getColorStringDark(key: String): String {
        val theme = config.optJSONObject("theme_dark")
        val value = theme?.optString(key, "#FFFFFFFF") ?: "#FFFFFFFF"
        return if (value.isNotEmpty()) value else "#FFFFFFFF"
    }
    
    /**
     * 根据当前是否深色模式读取对应配色区的颜色字符串。
     */
    fun getColorStringForMode(key: String, dark: Boolean): String {
        return if (dark) getColorStringDark(key) else getColorString(key)
    }
    
    /**
     * 根据是否深色模式读取对应配色区的 ARGB 颜色值。
     */
    fun getColorForMode(key: String, dark: Boolean): Int {
        return if (dark) getColorDark(key) else getColor(key)
    }
    
    fun getColorDark(key: String): Int {
        return try {
            val theme = config.optJSONObject("theme_dark")
            val colorStr = theme?.optString(key, "#FFFFFFFF") ?: "#FFFFFFFF"
            if (colorStr.isNotEmpty() && colorStr.startsWith("#") && colorStr.length >= 7) {
                Color.parseColor(colorStr)
            } else {
                Color.parseColor("#FFFFFFFF")
            }
        } catch (e: Exception) {
            Logger.w(TAG, Str.get(R.string.color_parse_failed_key_using_default, key))
            Color.parseColor("#FFFFFFFF")
        }
    }
    
    fun updateColor(key: String, value: String) {
        try {
            val theme = config.optJSONObject("theme")
            if (theme != null) {
                theme.put(key, value)
                saveConfig()
                Logger.d(TAG, Str.get(R.string.updated_color_key_value, key, value))
            }
        } catch (e: Exception) {
            Logger.e(TAG, Str.get(R.string.failed_to_update_color), e)
        }
    }
    
    fun updateColorDark(key: String, value: String) {
        try {
            val theme = config.optJSONObject("theme_dark")
            if (theme != null) {
                theme.put(key, value)
                saveConfig()
                Logger.d(TAG, Str.get(R.string.updated_color_key_value, key, value))
            }
        } catch (e: Exception) {
            Logger.e(TAG, Str.get(R.string.failed_to_update_color), e)
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
                Logger.d(TAG, Str.get(R.string.updated_shape_key_value, key, value))
            }
        } catch (e: Exception) {
            Logger.e(TAG, Str.get(R.string.failed_to_update_shape), e)
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
                Logger.d(TAG, Str.get(R.string.updated_size_key_value, key, value))
            }
        } catch (e: Exception) {
            Logger.e(TAG, Str.get(R.string.failed_to_update_size), e)
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
                Logger.d(TAG, Str.get(R.string.updated_boolean_key_value, key, value))
            }
        } catch (e: Exception) {
            Logger.e(TAG, Str.get(R.string.failed_to_update_boolean), e)
        }
    }
    
    fun resetToDefault() {
        val savedStyle = currentStyle
        config = getDefaultConfig()
        useIconTint = true
        currentTheme = "default"
        themeMode = THEME_MODE_SYSTEM
        currentStyle = savedStyle
        try {
            com.UIN.Tool.ui.common.StyleManager.clearCache()
        } catch (_: Exception) {}
        saveConfig()
        Logger.i(TAG, Str.get(R.string.reset_to_default_config))
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
    
    fun getThemeMode(): String = themeMode
    
    fun setThemeMode(mode: String) {
        if (mode != THEME_MODE_SYSTEM && mode != THEME_MODE_LIGHT && mode != THEME_MODE_DARK) {
            themeMode = THEME_MODE_SYSTEM
        } else {
            themeMode = mode
        }
        saveConfig()
    }
    
    fun getCurrentStyle(): String = currentStyle

    fun setCurrentStyle(style: String) {
        currentStyle = style
        saveConfig()
    }

    fun isSystemDark(): Boolean {
        val uiMode = context.resources.configuration.uiMode
        return (uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }
    
    /**
     * 根据主题模式与系统深色状态，解析实际是否使用深色主题。
     */
    fun shouldUseDarkTheme(systemDark: Boolean = isSystemDark()): Boolean {
        return when (themeMode) {
            THEME_MODE_DARK -> true
            THEME_MODE_LIGHT -> false
            else -> systemDark
        }
    }
    
    fun isGlassEffectEnabled(): Boolean = getBoolean("enableGlassEffect", true)
    fun getTranslucentAlpha(): Float {
        val v = config.optJSONObject("experimental")?.optDouble("translucentAlpha", 0.5) ?: 0.5
        return v.toFloat().coerceIn(0.05f, 0.95f)
    }
    fun setTranslucentAlpha(value: Float) {
        try {
            if (!config.has("experimental")) config.put("experimental", JSONObject())
            config.getJSONObject("experimental").put("translucentAlpha", value.toDouble())
            saveConfig()
        } catch (e: Exception) { Logger.e(TAG, "Failed to set translucentAlpha", e) }
    }
    fun isRippleEnabled(): Boolean = getBoolean("enableRipple", true)
    fun isBoldEnabled(): Boolean = config.optJSONObject("font")?.optBoolean("enableBold", true) ?: true
    fun getFontFamily(): String = config.optJSONObject("font")?.optString("fontFamily", "sans-serif") ?: "sans-serif"

    fun setFontFamily(value: String) {
        try {
            if (!config.has("font")) config.put("font", JSONObject())
            config.getJSONObject("font").put("fontFamily", value)
            saveConfig()
        } catch (e: Exception) { Logger.e(TAG, "Failed to set fontFamily", e) }
    }

    fun getLanguage(): String = config.optJSONObject("font")?.optString("language", "system") ?: "system"
    fun setLanguage(value: String) {
        try {
            if (!config.has("font")) config.put("font", JSONObject())
            config.getJSONObject("font").put("language", value)
            saveConfig()
        } catch (e: Exception) { Logger.e(TAG, "Failed to set language", e) }
    }

    // ==================== 新拟态配置 ====================

    fun isNeumorphismEnabled(): Boolean = getBoolean("enableNeumorphism", false)
    fun setNeumorphismEnabled(enabled: Boolean) = updateBoolean("enableNeumorphism", enabled)

    fun getNeumorphismIntensity(): String {
        val exp = config.optJSONObject("experimental")
        return exp?.optString("neumorphismIntensity", "light") ?: "light"
    }

    fun setNeumorphismIntensity(intensity: String) {
        val normalized = if (intensity in listOf("light", "medium", "strong")) intensity else "light"
        val exp = config.optJSONObject("experimental") ?: JSONObject()
        exp.put("neumorphismIntensity", normalized)
        config.put("experimental", exp)
        saveConfig()
    }

    fun isNeumorphismInsetEnabled(): Boolean = getBoolean("enableNeumorphismInset", true)

    fun setNeumorphismInsetEnabled(enabled: Boolean) = updateBoolean("enableNeumorphismInset", enabled)

    fun isNeumorphismGlowEnabled(): Boolean = getBoolean("enableNeumorphismGlow", true)

    fun setNeumorphismGlowEnabled(enabled: Boolean) = updateBoolean("enableNeumorphismGlow", enabled)

    fun getAnimationSpeed(): String {
        val exp = config.optJSONObject("experimental")
        return exp?.optString("animationSpeed", "medium") ?: "medium"
    }

    fun setAnimationSpeed(speed: String) {
        val normalized = if (speed in listOf("fast", "medium", "slow")) speed else "medium"
        val exp = config.optJSONObject("experimental") ?: JSONObject()
        exp.put("animationSpeed", normalized)
        config.put("experimental", exp)
        saveConfig()
    }

    fun getAnimationSpeedMultiplier(): Float {
        return when (getAnimationSpeed()) {
            "fast" -> 0.6f
            "slow" -> 1.5f
            else -> 1.0f
        }
    }

    // ==================== 渐变背景配置 ====================

    const val GRADIENT_MODE_SINGLE = "single"
    const val GRADIENT_MODE_MULTI = "multi"

    const val GRADIENT_DIR_TOP = "top"
    const val GRADIENT_DIR_BOTTOM = "bottom"
    const val GRADIENT_DIR_TOP_LEFT = "top_left"
    const val GRADIENT_DIR_TOP_RIGHT = "top_right"
    const val GRADIENT_DIR_BOTTOM_LEFT = "bottom_left"
    const val GRADIENT_DIR_BOTTOM_RIGHT = "bottom_right"

    val GRADIENT_DIRECTIONS = listOf(
        GRADIENT_DIR_TOP,
        GRADIENT_DIR_BOTTOM,
        GRADIENT_DIR_TOP_LEFT,
        GRADIENT_DIR_TOP_RIGHT,
        GRADIENT_DIR_BOTTOM_LEFT,
        GRADIENT_DIR_BOTTOM_RIGHT
    )

    fun isGradientBackgroundEnabled(): Boolean = config.optJSONObject("gradient")?.optBoolean("enabled", true) ?: true

    fun getGradientMode(): String {
        val mode = config.optJSONObject("gradient")?.optString("mode", GRADIENT_MODE_SINGLE) ?: GRADIENT_MODE_SINGLE
        return if (mode == GRADIENT_MODE_SINGLE || mode == GRADIENT_MODE_MULTI) mode else GRADIENT_MODE_SINGLE
    }

    fun getGradientFrom(): String {
        val dir = config.optJSONObject("gradient")?.optString("from", GRADIENT_DIR_BOTTOM_RIGHT)
            ?: GRADIENT_DIR_BOTTOM_RIGHT
        return if (GRADIENT_DIRECTIONS.contains(dir)) dir else GRADIENT_DIR_BOTTOM_RIGHT
    }

    fun getGradientTo(): String {
        val dir = config.optJSONObject("gradient")?.optString("to", GRADIENT_DIR_TOP_LEFT)
            ?: GRADIENT_DIR_TOP_LEFT
        return if (GRADIENT_DIRECTIONS.contains(dir)) dir else GRADIENT_DIR_TOP_LEFT
    }

    fun getGradientColorString(): String {
        return config.optJSONObject("gradient")?.optString("color", "#FFC4D6DF") ?: "#FFC4D6DF"
    }

    fun getGradientColorStringDark(): String {
        return config.optJSONObject("gradient")?.optString("color_dark", "#FF4C4F51") ?: "#FF4C4F51"
    }

    fun getGradientColorsString(): List<String> {
        val arr = config.optJSONObject("gradient")?.optJSONArray("colors") ?: return listOf("#FFC4D6DF", "#FFD6E8F2", "#FFEAF4FA")
        val list = mutableListOf<String>()
        for (i in 0 until arr.length()) {
            arr.optString(i, "").takeIf { it.isNotEmpty() }?.let { list.add(it) }
        }
        return if (list.isNotEmpty()) list else listOf("#FFC4D6DF", "#FFD6E8F2", "#FFEAF4FA")
    }

    fun getGradientColorsStringDark(): List<String> {
        val arr = config.optJSONObject("gradient")?.optJSONArray("colors_dark")
        if (arr == null) {
            return getGradientColorsString()
        }
        val list = mutableListOf<String>()
        for (i in 0 until arr.length()) {
            arr.optString(i, "").takeIf { it.isNotEmpty() }?.let { list.add(it) }
        }
        return if (list.isNotEmpty()) list else listOf("#FF4C4F51", "#FF5A5E62", "#FF6B6F73")
    }

    fun getGradientFromDark(): String {
        val dir = config.optJSONObject("gradient")?.optString("from_dark", GRADIENT_DIR_BOTTOM_RIGHT)
            ?: GRADIENT_DIR_BOTTOM_RIGHT
        return if (GRADIENT_DIRECTIONS.contains(dir)) dir else GRADIENT_DIR_BOTTOM_RIGHT
    }

    fun getGradientToDark(): String {
        val dir = config.optJSONObject("gradient")?.optString("to_dark", GRADIENT_DIR_TOP_LEFT)
            ?: GRADIENT_DIR_TOP_LEFT
        return if (GRADIENT_DIRECTIONS.contains(dir)) dir else GRADIENT_DIR_TOP_LEFT
    }

    fun getGradientModeDark(): String {
        val mode = config.optJSONObject("gradient")?.optString("mode_dark", GRADIENT_MODE_SINGLE) ?: GRADIENT_MODE_SINGLE
        return if (mode == GRADIENT_MODE_SINGLE || mode == GRADIENT_MODE_MULTI) mode else GRADIENT_MODE_SINGLE
    }

    fun setGradientBackgroundEnabled(enabled: Boolean) {
        val gradient = config.optJSONObject("gradient") ?: JSONObject()
        gradient.put("enabled", enabled)
        config.put("gradient", gradient)
        saveConfig()
    }

    fun setGradientMode(mode: String) {
        val normalized = if (mode == GRADIENT_MODE_SINGLE) GRADIENT_MODE_SINGLE else GRADIENT_MODE_MULTI
        val gradient = config.optJSONObject("gradient") ?: JSONObject()
        gradient.put("mode", normalized)
        config.put("gradient", gradient)
        saveConfig()
    }

    fun setGradientColor(color: String) {
        val gradient = config.optJSONObject("gradient") ?: JSONObject()
        gradient.put("color", color)
        config.put("gradient", gradient)
        saveConfig()
    }

    fun setGradientColorDark(color: String) {
        val gradient = config.optJSONObject("gradient") ?: JSONObject()
        gradient.put("color_dark", color)
        config.put("gradient", gradient)
        saveConfig()
    }

    fun setGradientFrom(dir: String) {
        val normalized = if (GRADIENT_DIRECTIONS.contains(dir)) dir else GRADIENT_DIR_BOTTOM_RIGHT
        val gradient = config.optJSONObject("gradient") ?: JSONObject()
        gradient.put("from", normalized)
        config.put("gradient", gradient)
        saveConfig()
    }

    fun setGradientTo(dir: String) {
        val normalized = if (GRADIENT_DIRECTIONS.contains(dir)) dir else GRADIENT_DIR_TOP_LEFT
        val gradient = config.optJSONObject("gradient") ?: JSONObject()
        gradient.put("to", normalized)
        config.put("gradient", gradient)
        saveConfig()
    }

    fun setGradientColors(colors: List<String>) {
        val gradient = config.optJSONObject("gradient") ?: JSONObject()
        val arr = org.json.JSONArray()
        colors.forEach { arr.put(it) }
        gradient.put("colors", arr)
        config.put("gradient", gradient)
        saveConfig()
    }
    
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

    // ==================== 深色配色便捷方法 ====================

    fun getDarkPrimaryColor(): Int = getColorDark("primary")
    fun getDarkPrimaryDarkColor(): Int = getColorDark("primary_dark")
    fun getDarkPrimaryLightColor(): Int = getColorDark("primary_light")
    fun getDarkAccentColor(): Int = getColorDark("accent")
    fun getDarkSurfaceColor(): Int = getColorDark("surface")
    fun getDarkBackgroundColor(): Int = getColorDark("background")

    // ==================== WebView 主题 CSS 注入 ====================

    /**
     * 生成 Web 插件可用的主题 CSS 变量（--uin-*），
     * 供插件网页通过 var(--uin-primary) 等使用当前主题配色。
     */
    fun getThemeCssVariables(): String {
        val dark = shouldUseDarkTheme(isSystemDark())
        fun color(key: String): String = hexColor(getColorStringForMode(key, dark))
        return buildString {
            append(":root{")
            append("--uin-primary:#").append(color("primary")).append(';')
            append("--uin-primary-dark:#").append(color("primary_dark")).append(';')
            append("--uin-primary-light:#").append(color("primary_light")).append(';')
            append("--uin-accent:#").append(color("accent")).append(';')
            append("--uin-success:#").append(color("success")).append(';')
            append("--uin-warning:#").append(color("warning")).append(';')
            append("--uin-error:#").append(color("error")).append(';')
            append("--uin-info:#").append(color("info")).append(';')
            append("--uin-text-primary:#").append(color("text_primary")).append(';')
            append("--uin-text-secondary:#").append(color("text_secondary")).append(';')
            append("--uin-text-hint:#").append(color("text_hint")).append(';')
            append("--uin-background:#").append(color("background")).append(';')
            append("--uin-surface:#").append(color("surface")).append(';')
            append("--uin-surface-variant:#").append(color("surface_variant")).append(';')
            append("--uin-divider:#").append(color("divider")).append(';')
            append("--uin-glass-background:#").append(color("glass_background")).append(';')
            append("--uin-disabled:#").append(color("disabled")).append(';')
            append("--uin-button-radius:").append(getShape("buttonCornerRadius").toInt()).append("px;")
            append("--uin-card-radius:").append(getShape("cardCornerRadius").toInt()).append("px;")
            append("--uin-input-radius:").append(getShape("inputCornerRadius").toInt()).append("px;")
            append('}')
        }
    }

    /**
     * 生成将主题 CSS 注入到 WebView 页面的 JS 脚本。
     * 通过 document 注入 <style> 并更新 :root 变量。
     */
    fun getThemeCssInjectionScript(): String {
        val css = getThemeCssVariables().replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n")
        return """
            (function() {
                var css = '$css';
                var styleId = 'uin-theme-style';
                var el = document.getElementById(styleId);
                if (!el) {
                    el = document.createElement('style');
                    el.id = styleId;
                    document.head.appendChild(el);
                }
                el.textContent = css;
                if (window.__uinThemeApplied) {
                    window.__uinThemeApplied();
                }
            })();
        """.trimIndent()
    }

    private fun hexColor(colorStr: String): String {
        return try {
            if (colorStr.startsWith("#") && colorStr.length >= 7) {
                colorStr.substring(colorStr.length - 6).uppercase()
            } else {
                "FFFFFFFF"
            }
        } catch (e: Exception) {
            "FFFFFFFF"
        }
    }
    
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