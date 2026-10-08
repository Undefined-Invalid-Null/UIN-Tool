package com.UIN.Tool.ui.viewmodel

import android.app.Application
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import com.UIN.Tool.plugin.BackendConfig

/**
 * F-05：后端设置页状态收进 ViewModel。
 *
 * 原先 6 个 `remember { mutableStateOf(...) }` 随组合销毁而重置，旋转/重建后
 * 用户未保存的输入直接丢；这里把 `MutableState` 持有在 ViewModel 中，
 * 组合侧仍以 `var impl by vm.implState` 的形式读写，写入语义完全不变。
 *
 * 这 5 组 key（implementation/environment/container/idleTimeout/keepAlive）全仓
 * 只有本页会写，因此不额外做「回到页面重读配置」，避免把未保存的编辑冲掉。
 */
class BackendSettingsViewModel(app: Application) : AndroidViewModel(app) {

    val implState: MutableState<String> = mutableStateOf(BackendConfig.getImplementation(app))
    val envState: MutableState<String> = mutableStateOf(BackendConfig.getEnvironment(app))
    val containerState: MutableState<String> = mutableStateOf(BackendConfig.getContainer(app))
    val idleMinState: MutableState<Int> = mutableStateOf(BackendConfig.getIdleTimeoutMinutes(app))
    val idleInputState: MutableState<String> = mutableStateOf(
        if (BackendConfig.isInfiniteIdleTimeout(app)) ""
        else BackendConfig.getIdleTimeoutMinutes(app).toString()
    )
    val keepAliveState: MutableState<Boolean> = mutableStateOf(BackendConfig.isKeepAliveEnabled(app))
}
