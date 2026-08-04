package com.UIN.Tool.ui.viewmodel

import com.UIN.Tool.R
import com.UIN.Tool.utils.Str
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.UIN.Tool.data.local.PreferenceManager
import com.UIN.Tool.data.remote.MirrorManager
import com.UIN.Tool.domain.model.MirrorItem
import com.UIN.Tool.log.Logger
import com.UIN.Tool.constants.AppConstants as Constants
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

class MirrorViewModel(
    context: Context
) : ViewModel() {
    
    companion object {
        private const val TAG = "MirrorViewModel"
    }
    
    private val preferenceManager = PreferenceManager(context)
    private val mirrorManager = MirrorManager(OkHttpClient())
    
    private val _mirrors = MutableStateFlow<List<MirrorItem>>(emptyList())
    val mirrors: StateFlow<List<MirrorItem>> = _mirrors.asStateFlow()
    
    private val _enabledMirrors = MutableStateFlow<Set<String>>(emptySet())
    val enabledMirrors: StateFlow<Set<String>> = _enabledMirrors.asStateFlow()
    
    private val _uiState = MutableStateFlow(MirrorUiState())
    val uiState: StateFlow<MirrorUiState> = _uiState.asStateFlow()
    
    init {
        loadMirrors()
    }
    
    fun loadMirrors() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true)
                
                // 加载默认镜像
                val defaultMirrors = mirrorManager.getDefaultMirrors()
                _mirrors.value = defaultMirrors
                
                // 加载启用的镜像
                val enabled = preferenceManager.getEnabledMirrors()
                _enabledMirrors.value = enabled.toSet()
                
                // 加载CDN设置
                _uiState.value = _uiState.value.copy(
                    useCdn = preferenceManager.isUseCdn(),
                    isLoading = false
                )
                
                Logger.i(TAG, Str.get(R.string.loading_mirrors_value_size_mirror_s, _mirrors.value.size))
            } catch (e: Exception) {
                Logger.e(TAG, Str.get(R.string.failed_to_load_mirrors), e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message
                )
            }
        }
    }
    
    fun toggleMirror(url: String) {
        _enabledMirrors.value = if (_enabledMirrors.value.contains(url)) {
            _enabledMirrors.value - url
        } else {
            _enabledMirrors.value + url
        }
    }
    
    fun toggleCdn() {
        _uiState.value = _uiState.value.copy(
            useCdn = !_uiState.value.useCdn
        )
    }
    
    fun addMirror(mirror: MirrorItem) {
        _mirrors.value = _mirrors.value + mirror.copy(isDefault = false)
        Logger.action(TAG, Str.get(R.string.add_mirror_2), mirror.name)
    }
    
    fun deleteMirror(mirror: MirrorItem) {
        if (!mirror.isDefault) {
            _mirrors.value = _mirrors.value - mirror
            _enabledMirrors.value = _enabledMirrors.value - mirror.url
            Logger.action(TAG, Str.get(R.string.delete_mirror), mirror.name)
        }
    }
    
    fun resetToDefault() {
        viewModelScope.launch {
            val defaultMirrors = mirrorManager.getDefaultMirrors()
            _mirrors.value = defaultMirrors
            _enabledMirrors.value = defaultMirrors.take(3).map { it.url }.toSet()
            Logger.action(TAG, Str.get(R.string.reset_mirrors), Str.get(R.string.restore_defaults))
        }
    }
    
    fun saveSettings() {
        viewModelScope.launch {
            preferenceManager.setEnabledMirrors(_enabledMirrors.value.toList())
            preferenceManager.setUseCdn(_uiState.value.useCdn)
            Logger.success(TAG, Str.get(R.string.mirror_settings_saved))
        }
    }
    
    data class MirrorUiState(
        val isLoading: Boolean = false,
        val useCdn: Boolean = true,
        val error: String? = null
    )
}