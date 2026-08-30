package com.UIN.Tool.ui.common

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.UIN.Tool.ui.screen.manage.ConfigState
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
        val saved = styleCustomizations[newStyleName]
        return saved ?: defaults
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
