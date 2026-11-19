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
    // v.txt 第 1 条（用户）：每个风格系统各自记录自己的整份配置。
    // 旧实现只有一份全局 config —— 切风格时把预设写进同一份配置，新拟态里改的
    // 渐变/颜色会覆盖默认风格，反过来也一样。现在按风格名分开存：切出时把当前
    // 配置写回旧风格，切入时载入目标风格自己那份（没有则留空，由调用方用预设）。
    private const val KEY_STYLE_CONFIGS = "style_configs"

    // v.txt 第 7 条（用户）：出厂默认 UI 配置的数据源（资产目录，不再写死在代码里）。
    private const val DEFAULT_CONFIG_ASSET = "config/uiconfig.json"
    
    const val THEME_MODE_SYSTEM = "system"
    const val THEME_MODE_LIGHT = "light"
    const val THEME_MODE_DARK = "dark"

    // 6.3.0 M1.5（决策 2）：底部导航的合法 tab id 全集。顺序由 nav.tabOrder 决定，
    // 这里只是"有哪些页"——用于校验外部写入（导入配置/深链）避免停在不存在的页上。
    val NAV_TAB_IDS = listOf("dev", "tools", "files", "repo", "manage")
    // 默认启动页：按决策 2「默认 tab 保持插件」
    const val NAV_DEFAULT_TAB = "tools"
    
    private lateinit var context: Context
    internal var config: JSONObject = JSONObject()
    private var useIconTint: Boolean = true
    private var currentTheme: String = "default"
    private var themeMode: String = THEME_MODE_SYSTEM
    private var currentStyle: String = "default"
    /** 风格名 → 该风格自己的整份配置（含浅/深两套渐变槽）。 */
    private var styleConfigs: JSONObject = JSONObject()
    /** `assets/config/uiconfig.json` 的原始解析结果（只读缓存，对外一律返回深拷贝）。 */
    private var defaultConfigCache: JSONObject? = null
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
            throw IllegalStateException(Str.get(R.string.uiconfig_not_initialized_call_init_first))
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
                Logger.e(TAG, Str.get(R.string.failed_to_parse_config_using_defaults), e)
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
        styleConfigs = try {
            JSONObject(prefs.getString(KEY_STYLE_CONFIGS, "{}") ?: "{}")
        } catch (e: Exception) {
            Logger.w(TAG, "parse style_configs failed: ${e.message}")
            JSONObject()
        }
        // 旧版本没有 style_configs：把当前这份全局配置认作「当前风格」的记录，
        // 这样第一次切走时旧风格的自定义不会丢。
        if (!styleConfigs.has(currentStyle)) {
            styleConfigs.put(currentStyle, JSONObject(config.toString()))
        }
    }

    /**
     * 为旧版本配置补齐缺失的区块（如 theme_dark），保证升级后深色模式仍有默认配色。
     */
    private fun ensureDefaultSections(target: JSONObject = config) {
        val def = getDefaultConfig()
        // 6.3.0 M1.5：新增「nav」段（默认启动页 + 底部导航顺序），旧配置升级时自动补齐
        val sections = listOf("theme", "theme_dark", "shape", "size", "font", "experimental", "gradient", "nav")
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
            migrateNeutralNeumorphismPalette()
        } catch (e: Exception) {
            Logger.w(TAG, "migrateLegacyDefaults failed: ${e.message}")
        }
    }

    /**
     * o.txt 第 3 条：新拟态整体偏绿。旧预设的 primary/accent 是青绿系、底色 #E8ECF1 又带
     * 蓝绿，叠起来整页发绿。预设已换成中性石板灰（见 StylePresets.NEUMORPHISM），但**已保存**
     * 的配置不会自动跟着变 —— 这里只在「当前风格就是新拟态、且该项仍是旧默认值」时替换，
     * 用户自己调过的颜色一律不动。
     */
    private fun migrateNeutralNeumorphismPalette() {
        try {
            val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            if (prefs.getString(KEY_CURRENT_STYLE, "default") != "neumorphism") return
            val light = mapOf(
                "primary" to ("#FF1A3A4A" to "#FF4A5568"),
                "primary_dark" to ("#FF0F2838" to "#FF374151"),
                "primary_light" to ("#FF2D5A70" to "#FF6B7280"),
                "accent" to ("#FF4A8A9E" to "#FF6B7280"),
                "background" to ("#FFE8ECF1" to "#FFECECEC"),
                "surface" to ("#FFE8ECF1" to "#FFECECEC"),
                "surface_variant" to ("#FFDDE3EA" to "#FFE2E2E2"),
                "divider" to ("#FFE0E4E8" to "#FFDCDCDC"),
                "nav_selected" to ("#FF1A3A4A" to "#FF4A5568"),
                "nav_unselected" to ("#FF9AA6B2" to "#FF9A9A9A"),
                "status_bar" to ("#FF0F2838" to "#FF374151")
            )
            val dark = mapOf(
                "primary" to ("#FF7A8490" to "#FF9AA5B1"),
                "accent" to ("#FF6A9AAE" to "#FF9AA5B1"),
                "background" to ("#FF2A2E34" to "#FF2B2B2B"),
                "surface" to ("#FF2A2E34" to "#FF2B2B2B"),
                "surface_variant" to ("#FF252930" to "#FF353535")
            )
            var changed = false
            for ((section, table) in listOf("theme" to light, "theme_dark" to dark)) {
                val obj = config.optJSONObject(section) ?: continue
                for ((key, pair) in table) {
                    if (obj.optString(key, "") == pair.first) {
                        obj.put(key, pair.second)
                        changed = true
                    }
                }
            }
            if (changed) Logger.i(TAG, "migrated neumorphism palette to neutral gray")
        } catch (e: Exception) {
            Logger.w(TAG, "migrateNeutralNeumorphismPalette failed: ${e.message}")
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
            // 6.2.0 批次58（用户）：浅色单色渐变默认改为 #FFF9F9F9（默认与新拟态两风格
            // 浅色底统一）—— 旧默认 #FFC4D6DF 视为「未自定义」一并升级；上面旧多色迁移
            // 刚归位成的 #FFC4D6DF 也在同一次初始化里接着升级。改过其他色的保持不动。
            // 必须排在 color_dark 修正之后：旧配置浅=深=#FFC4D6DF 时先拆开深槽再升级浅槽。
            if (gradient.optString("color", "") == "#FFC4D6DF") {
                gradient.put("color", "#FFF9F9F9")
                config.put("gradient", gradient)
            }
        } catch (e: Exception) {
            Logger.w(TAG, "migrateLegacyGradientDefault failed: ${e.message}")
        }
    }

    fun saveConfig() {
        // v.txt 第 1 条：任何一次保存都先镜像进「当前风格」的槽位，
        // 这样即使调用方只改了全局配置，切风格时也不会把这次修改丢掉。
        styleConfigs.put(currentStyle, JSONObject(config.toString()))
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString(KEY_CONFIG, config.toString())
            putString(KEY_STYLE_CONFIGS, styleConfigs.toString())
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
    
    /**
     * v.txt 第 7 条（用户）：出厂默认配置不再硬编码在 Kotlin 里，改为
     * `assets/config/uiconfig.json`（颜色 / 圆角 / 尺寸 / 字体 / 实验特性 / 渐变 / 导航）。
     *
     * 资产只解析一次并缓存原始对象；每次调用返回**深拷贝** —— 调用方
     * （[ensureDefaultSections]、[resetToDefault]）会把它并进用户配置甚至直接改键，
     * 共享同一实例会让「默认值」被用户配置污染。
     */
    private fun getDefaultConfig(): JSONObject {
        val cached = defaultConfigCache ?: loadDefaultConfigFromAssets()?.also {
            defaultConfigCache = it
        }
        return if (cached != null) JSONObject(cached.toString()) else JSONObject()
    }

    /** 读取并清洗 `assets/config/uiconfig.json`；失败返回 null（随后退回空配置并打日志）。 */
    private fun loadDefaultConfigFromAssets(): JSONObject? {
        if (!::context.isInitialized) return null
        return try {
            val text = context.assets.open(DEFAULT_CONFIG_ASSET).bufferedReader().use { it.readText() }
            val json = JSONObject(text)
            // 下划线开头的键只是给维护者看的注释，不进入运行时配置。
            val keys = json.keys()
            val commentKeys = ArrayList<String>()
            while (keys.hasNext()) {
                val key = keys.next()
                if (key.startsWith("_")) commentKeys.add(key)
            }
            commentKeys.forEach { json.remove(it) }
            if (json.length() == 0) null else json
        } catch (e: Exception) {
            Logger.e(TAG, "load $DEFAULT_CONFIG_ASSET failed: ${e.message}")
            null
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
    
    // ==================== 导航（6.3.0 M1.5，决策 2） ====================

    /** 默认启动页；非法值一律回落到插件页，保证不会停在不存在的 tab 上 */
    fun getDefaultTab(): String {
        val v = config.optJSONObject("nav")?.optString("defaultTab", NAV_DEFAULT_TAB) ?: NAV_DEFAULT_TAB
        return if (NAV_TAB_IDS.contains(v)) v else NAV_DEFAULT_TAB
    }

    fun setDefaultTab(id: String) {
        if (!NAV_TAB_IDS.contains(id)) {
            Logger.w(TAG, "setDefaultTab ignored, unknown id: $id")
            return
        }
        try {
            val nav = config.optJSONObject("nav") ?: JSONObject().also { config.put("nav", it) }
            nav.put("defaultTab", id)
            saveConfig()
        } catch (e: Exception) {
            Logger.e(TAG, "setDefaultTab failed: ${e.message}")
        }
    }

    /**
     * 底部导航顺序——**只排序、不隐藏**（决策 2）。
     * 调用方按下标渲染，所以返回值必须恒为 5 个 id 的完整排列；
     * 三重容错：去重 → 丢非法 id → 缺的补到末尾（导入的旧/坏配置也不会让导航缺页）。
     */
    fun getTabOrder(): List<String> {
        val arr = config.optJSONObject("nav")?.optJSONArray("tabOrder")
        val raw = if (arr == null) emptyList() else (0 until arr.length()).map { arr.optString(it, "") }
        return normalizeTabOrder(raw)
    }

    fun setTabOrder(order: List<String>) {
        try {
            val nav = config.optJSONObject("nav") ?: JSONObject().also { config.put("nav", it) }
            nav.put("tabOrder", org.json.JSONArray().apply {
                normalizeTabOrder(order).forEach { put(it) }
            })
            saveConfig()
        } catch (e: Exception) {
            Logger.e(TAG, "setTabOrder failed: ${e.message}")
        }
    }

    private fun normalizeTabOrder(order: List<String>): List<String> {
        val valid = order.filter { NAV_TAB_IDS.contains(it) }.distinct()
        return valid + NAV_TAB_IDS.filterNot { valid.contains(it) }
    }

    fun resetToDefault() {
        val savedStyle = currentStyle
        config = getDefaultConfig()
        useIconTint = true
        currentTheme = "default"
        themeMode = THEME_MODE_SYSTEM
        currentStyle = savedStyle
        // v.txt 第 1 条：重置是「所有风格一起重置」，否则切回另一个风格会把它
        // 以前的自定义整份带回来，看起来像重置没生效。
        styleConfigs = JSONObject()
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

    /**
     * v.txt 第 1 条：按风格隔离的配置槽。
     *
     * - [hasStyleConfig] / [getStyleConfig] 供 StyleManager 判断「这个风格以前有没有存过」；
     * - [setCurrentStyle] 切出时把当前配置写回旧风格，切入时载入目标风格那份。
     *
     * 深浅色模式（themeMode）仍是全局设置：它属于「系统级」而不是某个风格，
     * 切风格只换配色/渐变/形状，不改变用户选的浅色/深色/跟随系统。
     */
    fun hasStyleConfig(style: String): Boolean = styleConfigs.optJSONObject(style) != null

    fun getStyleConfig(style: String): JSONObject? =
        styleConfigs.optJSONObject(style)?.let { JSONObject(it.toString()) }

    fun setCurrentStyle(style: String) {
        if (style == currentStyle) {
            saveConfig()
            return
        }
        // 1) 先把旧风格的当前状态落进它自己的槽位。
        styleConfigs.put(currentStyle, JSONObject(config.toString()))
        currentStyle = style
        // 2) 目标风格有历史配置就整份载入。
        val saved = styleConfigs.optJSONObject(style)
        if (saved != null) {
            val next = JSONObject(saved.toString())
            carryGlobalSections(next)
            config = next
            ensureDefaultSections()
            saveConfig()
        }
        // w.txt 第 5 条（用户）：目标风格**没有**历史配置时这里不能再 saveConfig()。
        // 旧实现会顺手把「上一个风格」的整份 config（含它的浅/深渐变槽）复制进
        // 新风格的槽位 —— 于是切一次风格、换一次深浅色，渐变就跑到另一个风格系统里。
        // 现在留空，由 [com.UIN.Tool.ui.common.StyleManager.switchStyle] 用该风格
        // 自己的预设调 [seedStyleConfig] 铺底。
    }

    /**
     * w.txt 第 5 条（用户）：首次进入某个风格时，用它**自己**的配置铺底。
     *
     * 语言 / 导航 / 小部件仍从当前配置带过来（全局设置，不属于任何风格），
     * 其余配色、渐变、形状全部来自 [seed] —— 绝不继承另一个风格的槽值。
     */
    fun seedStyleConfig(style: String, seed: JSONObject) {
        val next = JSONObject(seed.toString())
        carryGlobalSections(next)
        ensureDefaultSections(next)
        config = next
        currentStyle = style
        styleConfigs.put(style, JSONObject(next.toString()))
        saveConfig()
    }

    /**
     * 语言 / 导航 / 小部件属于**全局**设置，不属于任何风格系统 ——
     * 切风格载入另一份配置时要把它们带过去，否则切一次风格就换一次语言。
     */
    private fun carryGlobalSections(target: JSONObject) {
        try {
            val font = config.optJSONObject("font")
            val targetFont = target.optJSONObject("font") ?: JSONObject().also { target.put("font", it) }
            if (font != null && font.has("language")) {
                targetFont.put("language", font.get("language"))
            }
            for (section in listOf("nav", "widget")) {
                config.optJSONObject(section)?.let { target.put(section, JSONObject(it.toString())) }
            }
        } catch (e: Exception) {
            Logger.w(TAG, "carryGlobalSections failed: ${e.message}")
        }
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

    // ==================== 小部件（G-28） ====================

    /** G-28：小部件后台自动刷新开关（WorkManager 周期任务），默认开。 */
    fun isWidgetAutoRefreshEnabled(): Boolean =
        config.optJSONObject("widget")?.optBoolean("autoRefresh", true) ?: true

    fun setWidgetAutoRefreshEnabled(enabled: Boolean) {
        try {
            if (!config.has("widget")) config.put("widget", JSONObject())
            config.getJSONObject("widget").put("autoRefresh", enabled)
            saveConfig()
        } catch (e: Exception) { Logger.e(TAG, "Failed to set widget autoRefresh", e) }
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
        // 批次58：浅色单色默认 #FFC4D6DF → #FFF9F9F9（仅缺省兜底；旧默认存量由
        // migrateLegacyGradientDefault 升级，用户自定义色不动）。
        return config.optJSONObject("gradient")?.optString("color", "#FFF9F9F9") ?: "#FFF9F9F9"
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
            // 旧配置缺 colors_dark 时必须回落到深色默认，回落浅色会让深色模式显示浅色渐变
            return listOf("#FF4C4F51", "#FF5A5E62", "#FF6B6F73")
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