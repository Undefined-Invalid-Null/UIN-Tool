package com.UIN.Tool.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.UIN.Tool.data.local.PreferenceManager
import com.UIN.Tool.data.remote.MirrorManager
import com.UIN.Tool.domain.model.MirrorItem
import com.UIN.Tool.log.Logger
import com.UIN.Tool.utils.Constants
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
                
                Logger.i(TAG, "加载 ${_mirrors.value.size} 个镜像站")
            } catch (e: Exception) {
                Logger.e(TAG, "加载镜像站失败", e)
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
        Logger.action(TAG, "添加镜像", mirror.name)
    }
    
    fun deleteMirror(mirror: MirrorItem) {
        if (!mirror.isDefault) {
            _mirrors.value = _mirrors.value - mirror
            _enabledMirrors.value = _enabledMirrors.value - mirror.url
            Logger.action(TAG, "删除镜像", mirror.name)
        }
    }
    
    fun resetToDefault() {
        viewModelScope.launch {
            val defaultMirrors = mirrorManager.getDefaultMirrors()
            _mirrors.value = defaultMirrors
            _enabledMirrors.value = defaultMirrors.take(3).map { it.url }.toSet()
            Logger.action(TAG, "重置镜像", "恢复默认")
        }
    }
    
    fun saveSettings() {
        viewModelScope.launch {
            preferenceManager.setEnabledMirrors(_enabledMirrors.value.toList())
            preferenceManager.setUseCdn(_uiState.value.useCdn)
            Logger.success(TAG, "镜像设置已保存")
        }
    }
    
    data class MirrorUiState(
        val isLoading: Boolean = false,
        val useCdn: Boolean = true,
        val error: String? = null
    )
}