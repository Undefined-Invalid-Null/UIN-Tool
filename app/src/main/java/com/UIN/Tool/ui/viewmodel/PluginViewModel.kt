package com.UIN.Tool.ui.viewmodel

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
import java.io.File

class PluginViewModel(
    private val pluginManager: PluginManager
) : ViewModel() {
    
    companion object {
        private const val TAG = "PluginViewModel"
    }
    
    private val _uiState = MutableStateFlow(PluginUiState())
    val uiState: StateFlow<PluginUiState> = _uiState.asStateFlow()
    
    private val _selectedPlugin = MutableStateFlow<PluginInfo?>(null)
    val selectedPlugin: StateFlow<PluginInfo?> = _selectedPlugin.asStateFlow()
    
    private val _installedPlugins = MutableStateFlow<List<PluginInfo>>(emptyList())
    val installedPlugins: StateFlow<List<PluginInfo>> = _installedPlugins.asStateFlow()
    
    init {
        loadPlugins()
    }
    
    fun loadPlugins() {
        viewModelScope.launch {
            try {
                pluginManager.refreshPlugins()
                _installedPlugins.value = pluginManager.plugins.value
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    pluginCount = _installedPlugins.value.size
                )
                Logger.i(TAG, "已加载 ${_installedPlugins.value.size} 个插件")
            } catch (e: Exception) {
                Logger.e(TAG, "加载插件失败", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message
                )
            }
        }
    }
    
    fun selectPlugin(pluginId: String) {
        val plugin = pluginManager.getPluginInfo(pluginId)
        _selectedPlugin.value = plugin
        Logger.action(TAG, "选择插件", plugin?.name ?: "未知")
    }
    
    fun openPlugin(pluginId: String, context: Context) {
        pluginManager.openPlugin(pluginId, context)
        Logger.action(TAG, "打开插件", pluginId)
    }
    
    suspend fun uninstallPlugin(pluginId: String): Boolean {
        return try {
            val result = pluginManager.uninstallPlugin(pluginId)
            if (result) {
                loadPlugins()
                Logger.success(TAG, "卸载成功: $pluginId")
            }
            result
        } catch (e: Exception) {
            Logger.e(TAG, "卸载失败", e)
            false
        }
    }
    
    suspend fun installPlugin(file: File, fileName: String): PluginInfo? {
        return try {
            val info = pluginManager.installPlugin(file, fileName)
            if (info != null) {
                loadPlugins()
                Logger.success(TAG, "安装成功: ${info.name}")
            }
            info
        } catch (e: Exception) {
            Logger.e(TAG, "安装失败", e)
            null
        }
    }
    
    fun searchPlugins(keyword: String): List<PluginInfo> {
        return pluginManager.searchPlugins(keyword)
    }
    
    fun getPluginsByCategory(category: String): List<PluginInfo> {
        return pluginManager.getPluginsByCategory(category)
    }
    
    fun getAllCategories(): List<String> {
        return pluginManager.getAllCategories()
    }
    
    data class PluginUiState(
        val isLoading: Boolean = true,
        val pluginCount: Int = 0,
        val error: String? = null
    )
}