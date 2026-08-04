// app/src/main/java/com/UIN/Tool/ui/viewmodel/MainViewModel.kt
package com.UIN.Tool.ui.viewmodel

import com.UIN.Tool.R
import com.UIN.Tool.utils.Str
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.UIN.Tool.core.di.ServiceLocator
import com.UIN.Tool.domain.model.PluginInfo
import com.UIN.Tool.domain.repository.IConfigRepository
import com.UIN.Tool.domain.repository.IPluginRepository
import com.UIN.Tool.log.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel : ViewModel() {

    companion object {
        private const val TAG = "MainViewModel"
    }

    private val pluginRepository: IPluginRepository = ServiceLocator.getPluginRepository()
    private val configRepository: IConfigRepository = ServiceLocator.getConfigRepository()

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val _plugins = MutableStateFlow<List<PluginInfo>>(emptyList())
    val plugins: StateFlow<List<PluginInfo>> = _plugins.asStateFlow()

    init {
        loadPlugins()
        loadPreferences()
    }

    private fun loadPlugins() {
        viewModelScope.launch {
            try {
                pluginRepository.refreshPlugins()
                _plugins.value = pluginRepository.getPlugins().value
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    pluginCount = _plugins.value.size
                )
                Logger.i(TAG, Str.get(R.string.loaded_plugins_value_size_plugin_s, _plugins.value.size))
            } catch (e: Exception) {
                Logger.e(TAG, Str.get(R.string.failed_to_load_plugins), e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message
                )
            }
        }
    }

    private fun loadPreferences() {
        _uiState.value = _uiState.value.copy(
            viewMode = configRepository.getViewMode().value,
            workFolder = configRepository.getWorkFolder().value,
            isFirstLaunch = configRepository.isFirstLaunch()
        )
    }

    fun setViewMode(mode: String) {
        viewModelScope.launch {
            configRepository.setViewMode(mode)
            _uiState.value = _uiState.value.copy(viewMode = mode)
        }
    }

    fun refreshPlugins() {
        loadPlugins()
    }

    fun searchPlugins(keyword: String): List<PluginInfo> {
        return if (keyword.isEmpty()) {
            _plugins.value
        } else {
            pluginRepository.searchPlugins(keyword)
        }
    }

    fun getPluginsByCategory(category: String): List<PluginInfo> {
        return pluginRepository.getPluginsByCategory(category)
    }

    fun getAllCategories(): List<String> {
        return pluginRepository.getAllCategories()
    }

    data class MainUiState(
        val isLoading: Boolean = true,
        val pluginCount: Int = 0,
        val viewMode: String = "list",
        val workFolder: String = "",
        val isFirstLaunch: Boolean = true,
        val error: String? = null
    )
}