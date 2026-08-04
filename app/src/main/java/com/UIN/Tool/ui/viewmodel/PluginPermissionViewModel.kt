package com.UIN.Tool.ui.viewmodel

import com.UIN.Tool.R
import com.UIN.Tool.utils.Str
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.UIN.Tool.plugin.PluginManager
import com.UIN.Tool.domain.model.PluginInfo
import com.UIN.Tool.log.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PluginPermissionViewModel(
    private val pluginManager: PluginManager
) : ViewModel() {
    
    companion object {
        private const val TAG = "PluginPermissionViewModel"
    }
    
    private val _plugins = MutableStateFlow<List<PluginInfo>>(emptyList())
    val plugins: StateFlow<List<PluginInfo>> = _plugins.asStateFlow()
    
    private val _selectedPluginId = MutableStateFlow<String?>(null)
    val selectedPluginId: StateFlow<String?> = _selectedPluginId.asStateFlow()
    
    private val _permissions = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val permissions: StateFlow<Map<String, Boolean>> = _permissions.asStateFlow()
    
    private val _uiState = MutableStateFlow(PermissionUiState())
    val uiState: StateFlow<PermissionUiState> = _uiState.asStateFlow()
    
    fun loadPlugins() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true)
                pluginManager.refreshPlugins()
                _plugins.value = pluginManager.plugins.value
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    pluginCount = _plugins.value.size
                )
                Logger.i(TAG, Str.get(R.string.loading_plugins_value_size_plugin_s, _plugins.value.size))
            } catch (e: Exception) {
                Logger.e(TAG, Str.get(R.string.failed_to_load_plugins), e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message
                )
            }
        }
    }
    
    fun selectPlugin(pluginId: String) {
        _selectedPluginId.value = pluginId
        loadPluginPermissions(pluginId)
        Logger.action(TAG, Str.get(R.string.select_plugin_2), pluginId)
    }
    
    private fun loadPluginPermissions(pluginId: String) {
        val plugin = pluginManager.getPluginInfo(pluginId)
        if (plugin != null) {
            // 从插件配置加载权限
            val perms = plugin.permissions.associateWith { false }
            _permissions.value = perms
            Logger.i(TAG, Str.get(R.string.loading_perms_size_permission_s, perms.size))
        }
    }
    
    fun togglePermission(permission: String) {
        val current = _permissions.value.toMutableMap()
        current[permission] = !(current[permission] ?: false)
        _permissions.value = current
        Logger.action(TAG, Str.get(R.string.toggle_permission), "$permission = ${current[permission]}")
    }
    
    fun selectAllPermissions(select: Boolean) {
        val current = _permissions.value.toMutableMap()
        current.keys.forEach { key ->
            current[key] = select
        }
        _permissions.value = current
        Logger.action(TAG, if (select) Str.get(R.string.select_all) else Str.get(R.string.select_none), Str.get(R.string.all_permissions))
    }
    
    data class PermissionUiState(
        val isLoading: Boolean = false,
        val pluginCount: Int = 0,
        val error: String? = null
    )
}