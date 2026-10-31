package com.UIN.Tool.ui.common

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.UIN.Tool.ui.screen.manage.ConfigState
import com.UIN.Tool.ui.screen.manage.refreshGradientForMode
import com.UIN.Tool.utils.UIConfig

object StyleManager {

    private val styleCustomizations = mutableMapOf<String, ConfigState>()
    private var _currentStyle by mutableStateOf("default")

    fun init() {
        _currentStyle = if (UIConfig.isInitialized()) UIConfig.getInstance().getCurrentStyle() else "default"
    }

    fun getCurrentStyleName(): String = _currentStyle

    fun getCustomizedStyles(): Set<String> = styleCustomizations.keys.toSet()

    fun hasCustomization(styleName: String): Boolean = styleCustomizations.containsKey(styleName)

    fun switchStyle(currentConfig: ConfigState, newStyleName: String): ConfigState {
        styleCustomizations[_currentStyle] = currentConfig
        _currentStyle = newStyleName
        UIConfig.getInstance().setCurrentStyle(newStyleName)
        val ui = UIConfig.getInstance()
        val cached = styleCustomizations[newStyleName]
        if (cached != null) {
            // 6.2.0 批次58（用户「新拟态风格下渐变背景改不了」）：缓存存的是该风格
            // 自己的整套配置 —— 切出时写入、深浅切换已由主题色 chip 的 refreshGradientForMode
            // 归位过槽值，本身就是当前模式的正确槽位。此前这里**无条件**再 refresh 一次，
            // 等于每次切回都用全局槽把该风格缓存的渐变踩回存储值：按风格的渐变永远留不住
            // （新拟态里改完一切风格就还原），预设渐变也永远落不了地。缓存路径原样返回。
            return cached.copy(themeMode = ui.getThemeMode())
        }
        // 首次进入该风格：预设只带浅色一组渐变、themeMode="system" —— 不能覆盖用户当前
        // 深浅模式，也不能把浅色槽值在深色下写进 *__dark（默认→深色→新拟态→默认后
        // 深色模式却显示浅色渐变的根因）；渐变先归位到当前模式槽位（无缓存时回落当前
        // 全局槽值）再交给随后的保存。
        val defaults = StylePresets.getDefaults(newStyleName).toConfigState()
        return defaults.copy(themeMode = ui.getThemeMode()).refreshGradientForMode()
    }

    fun saveCustomization(styleName: String, config: ConfigState) {
        styleCustomizations[styleName] = config
    }

    fun clearCache() {
        styleCustomizations.clear()
    }

    fun resetStyle(styleName: String): ConfigState {
        styleCustomizations.remove(styleName)
        _currentStyle = styleName
        return StylePresets.getDefaults(styleName).toConfigState()
    }

    fun getCurrentDefaults(): ConfigState {
        return StylePresets.getDefaults(_currentStyle).toConfigState()
    }
}
