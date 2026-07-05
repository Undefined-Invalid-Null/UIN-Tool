package com.UIN.Tool.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.UIN.Tool.data.local.PreferenceManager
import com.UIN.Tool.log.Logger
import com.UIN.Tool.utils.Constants
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val context: Context
) : ViewModel() {

    companion object {
        private const val TAG = "SettingsViewModel"
    }

    private val preferenceManager = PreferenceManager(context)

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        _uiState.value = _uiState.value.copy(
            workFolder = preferenceManager.getWorkFolder(),
            viewMode = preferenceManager.getViewMode(),
            isUseCustomIconTint = preferenceManager.isUseCustomIconTint(),
            isFirstLaunch = preferenceManager.isFirstLaunch(),
            isUseCdn = preferenceManager.isUseCdn()
        )
    }

    fun setWorkFolder(path: String) {
        viewModelScope.launch {
            preferenceManager.setWorkFolder(path)
            _uiState.value = _uiState.value.copy(workFolder = path)
            Logger.param(TAG, "工作目录", path)
        }
    }

    fun setViewMode(mode: String) {
        viewModelScope.launch {
            preferenceManager.setViewMode(mode)
            _uiState.value = _uiState.value.copy(viewMode = mode)
        }
    }

    fun setUseCustomIconTint(use: Boolean) {
        viewModelScope.launch {
            preferenceManager.setUseCustomIconTint(use)
            _uiState.value = _uiState.value.copy(isUseCustomIconTint = use)
        }
    }

    fun setUseCdn(use: Boolean) {
        viewModelScope.launch {
            preferenceManager.setUseCdn(use)
            _uiState.value = _uiState.value.copy(isUseCdn = use)
        }
    }

    fun resetSettings() {
        viewModelScope.launch {
            preferenceManager.setWorkFolder(Constants.WORK_DIR)
            preferenceManager.setViewMode("list")
            preferenceManager.setUseCustomIconTint(true)
            preferenceManager.setUseCdn(true)
            loadSettings()
            Logger.action(TAG, "重置设置", "已重置所有配置")
        }
    }

    fun checkForceUpdate(): Boolean {
        return false
    }

    data class SettingsUiState(
        val workFolder: String = "",
        val viewMode: String = "list",
        val isUseCustomIconTint: Boolean = true,
        val isFirstLaunch: Boolean = true,
        val isUseCdn: Boolean = true,
        val isLoading: Boolean = false,
        val error: String? = null
    )
}