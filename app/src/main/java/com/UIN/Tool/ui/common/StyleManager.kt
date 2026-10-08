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
        val defaults = StylePresets.getDefaults(newStyleName).toConfigState()
        val result = styleCustomizations[newStyleName] ?: defaults
        // 风格预设只带浅色一组渐变、themeMode="system"：切风格不得覆盖用户当前深浅
        // 模式，也不得把浅色槽值在深色下写进 *__dark（默认→深色→新拟态→默认后
        // 深色模式却显示浅色渐变的根因）—— 归位后再交给保存。
        val ui = UIConfig.getInstance()
        return result.copy(themeMode = ui.getThemeMode()).refreshGradientForMode()
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
