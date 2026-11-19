package com.UIN.Tool.ui.common

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.UIN.Tool.ui.screen.manage.ConfigState
import com.UIN.Tool.ui.screen.manage.buildUiConfigJson
import com.UIN.Tool.ui.screen.manage.loadConfigFromUIConfig
import com.UIN.Tool.utils.UIConfig
import org.json.JSONObject

object StyleManager {

    private val styleCustomizations = mutableMapOf<String, ConfigState>()
    private var _currentStyle by mutableStateOf("default")

    fun init() {
        _currentStyle = if (UIConfig.isInitialized()) UIConfig.getInstance().getCurrentStyle() else "default"
    }

    fun getCurrentStyleName(): String = _currentStyle

    fun getCustomizedStyles(): Set<String> = styleCustomizations.keys.toSet()

    fun hasCustomization(styleName: String): Boolean = styleCustomizations.containsKey(styleName)

    /**
     * 切换风格系统。
     *
     * w.txt 第 5 条（用户）：两个风格系统的配置必须**在最底层**就分开 —— 以前
     * 深浅色一换、风格一切，渐变就会从一套跑到另一套。根因有两处，这里一并修掉：
     *
     *  1. 切出时只靠 UIConfig 里那份全局 config 落盘，界面里还没点「保存」的编辑会丢；
     *     这里先把 [currentConfig] 整份写回旧风格自己的槽位（含两个模式的渐变槽）。
     *  2. 目标风格没有历史配置时，旧实现让 UIConfig 沿用「上一个风格」的整份配置，
     *     等于把旧风格的渐变复制进新风格。现在改成用该风格自己的预设铺底
     *     （[UIConfig.seedStyleConfig]，两个模式槽都写它自己的值）。
     *
     * 切入后一律从该风格自己的配置重新读状态，不再用内存缓存返回 —— 缓存里只有
     * 一组渐变槽，深浅模式一变就不知道它属于哪个模式，正是「渐变串风格」的来源。
     * 缓存仍保留（切出时写入），供 [hasCustomization] 之类的判断使用。
     */
    fun switchStyle(currentConfig: ConfigState, newStyleName: String): ConfigState {
        val ui = UIConfig.getInstance()
        val dark = ui.shouldUseDarkTheme()

        if (newStyleName == _currentStyle) {
            // 同一个风格：把当前编辑落盘即可，别让「点当前风格卡片」丢改动。
            ui.applyJson(buildUiConfigJson(currentConfig, ui.getConfig(), dark))
            styleCustomizations[_currentStyle] = currentConfig
            return currentConfig
        }

        // 1) 切出：把当前风格（含未保存的编辑）整份写回它自己的槽位。
        ui.applyJson(buildUiConfigJson(currentConfig, ui.getConfig(), dark))
        styleCustomizations[_currentStyle] = currentConfig

        // 2) 目标风格以前有没有存过 —— 必须在 setCurrentStyle 之前问
        //    （它会载入目标风格那份配置）。
        val hadPersisted = ui.hasStyleConfig(newStyleName)
        _currentStyle = newStyleName
        ui.setCurrentStyle(newStyleName)

        // 3) 首次进入：用该风格自己的预设铺底。语言 / 深浅模式是全局设置，
        //    不能被预设里的 "system" 覆盖。
        if (!hadPersisted) {
            val defaults = StylePresets.getDefaults(newStyleName).toConfigState().copy(
                themeMode = ui.getThemeMode(),
                language = ui.getLanguage()
            )
            ui.seedStyleConfig(
                newStyleName,
                buildUiConfigJson(
                    defaults,
                    JSONObject(),
                    ui.shouldUseDarkTheme(),
                    seedGradientBothModes = true
                )
            )
        }

        val loaded = loadConfigFromUIConfig().copy(
            themeMode = ui.getThemeMode(),
            language = ui.getLanguage()
        )
        styleCustomizations[newStyleName] = loaded
        return loaded
    }

    fun saveCustomization(styleName: String, config: ConfigState) {
        styleCustomizations[styleName] = config
    }

    fun clearCache() {
        styleCustomizations.clear()
    }

    /**
     * 把某个风格重置为它**自己的**预设（全局的语言 / 深浅模式保持不变）。
     *
     * w.txt 第 5 条：重置必须按风格各自铺底 —— 以前重置后新拟态会拿到默认风格的
     * 出厂配置（颜色与渐变全串味），因为 UIConfig 里那份全局 config 被整体覆盖了。
     */
    fun resetStyle(styleName: String): ConfigState {
        styleCustomizations.remove(styleName)
        _currentStyle = styleName
        val ui = UIConfig.getInstance()
        val defaults = StylePresets.getDefaults(styleName).toConfigState().copy(
            themeMode = ui.getThemeMode(),
            language = ui.getLanguage()
        )
        ui.seedStyleConfig(
            styleName,
            buildUiConfigJson(
                defaults,
                JSONObject(),
                ui.shouldUseDarkTheme(),
                seedGradientBothModes = true
            )
        )
        return loadConfigFromUIConfig().copy(
            themeMode = ui.getThemeMode(),
            language = ui.getLanguage()
        )
    }

    fun getCurrentDefaults(): ConfigState {
        return StylePresets.getDefaults(_currentStyle).toConfigState()
    }
}
