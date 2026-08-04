package com.UIN.Tool.ui.viewmodel

import com.UIN.Tool.R
import com.UIN.Tool.utils.Str
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.UIN.Tool.log.Logger
import com.UIN.Tool.utils.TemplateUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class NativePluginWizardViewModel : ViewModel() {
    
    companion object {
        private const val TAG = "NativePluginWizardViewModel"
    }
    
    private val _uiState = MutableStateFlow(NativeWizardUiState())
    val uiState: StateFlow<NativeWizardUiState> = _uiState.asStateFlow()
    
    private val _pluginId = MutableStateFlow("com.example.myplugin")
    val pluginId: StateFlow<String> = _pluginId.asStateFlow()
    
    private val _pluginName = MutableStateFlow(Str.get(R.string.my_native_plugin))
    val pluginName: StateFlow<String> = _pluginName.asStateFlow()
    
    private val _pluginAuthor = MutableStateFlow(Str.get(R.string.developer_2))
    val pluginAuthor: StateFlow<String> = _pluginAuthor.asStateFlow()
    
    private val _pluginDescription = MutableStateFlow(Str.get(R.string.this_is_a_native_plugin_example))
    val pluginDescription: StateFlow<String> = _pluginDescription.asStateFlow()
    
    private val _pluginVersion = MutableStateFlow("1")
    val pluginVersion: StateFlow<String> = _pluginVersion.asStateFlow()
    
    private val _pluginVersionName = MutableStateFlow("1.0.0")
    val pluginVersionName: StateFlow<String> = _pluginVersionName.asStateFlow()
    
    private val _mainClass = MutableStateFlow("com.example.MainPlugin")
    val mainClass: StateFlow<String> = _mainClass.asStateFlow()
    
    private val _fileContents = MutableStateFlow<Map<String, String>>(emptyMap())
    val fileContents: StateFlow<Map<String, String>> = _fileContents.asStateFlow()
    
    private val _fileList = MutableStateFlow<List<String>>(emptyList())
    val fileList: StateFlow<List<String>> = _fileList.asStateFlow()
    
    fun updatePluginId(value: String) {
        _pluginId.value = value
        generateCode()
    }
    
    fun updatePluginName(value: String) {
        _pluginName.value = value
        generateCode()
    }
    
    fun updatePluginAuthor(value: String) {
        _pluginAuthor.value = value
    }
    
    fun updatePluginDescription(value: String) {
        _pluginDescription.value = value
    }
    
    fun updatePluginVersion(value: String) {
        _pluginVersion.value = value
    }
    
    fun updatePluginVersionName(value: String) {
        _pluginVersionName.value = value
    }
    
    fun updateMainClass(value: String) {
        _mainClass.value = value
        generateCode()
    }
    
    fun generateCode() {
        val className = _mainClass.value.substringAfterLast('.')
        val packageName = _mainClass.value.substringBeforeLast('.')
        val packagePath = packageName.replace('.', '/')
        val javaFilePath = "src/$packagePath/$className.java"
        
        val vars = mapOf(
            "PACKAGE_NAME" to packageName,
            "CLASS_NAME" to className,
            "PLUGIN_NAME" to _pluginName.value,
            "PLUGIN_ID" to _pluginId.value,
            "PLUGIN_VERSION" to _pluginVersion.value,
            "PLUGIN_VERSION_NAME" to _pluginVersionName.value,
            "PLUGIN_AUTHOR" to _pluginAuthor.value,
            "PLUGIN_DESCRIPTION" to _pluginDescription.value
        )
        
        viewModelScope.launch {
            try {
                val context = com.UIN.Tool.UinApplication.getInstance()
                val javaCode = TemplateUtils.generateJavaCode(context, "native", vars)
                
                val files = mutableMapOf<String, String>()
                files[javaFilePath] = javaCode
                _fileContents.value = files
                _fileList.value = files.keys.toList()
                
                Logger.i(TAG, Str.get(R.string.java_code_generated_javafilepath, javaFilePath))
            } catch (e: Exception) {
                Logger.e(TAG, Str.get(R.string.failed_to_generate_java_code), e)
            }
        }
    }
    
    fun updateFileContent(fileName: String, content: String) {
        val current = _fileContents.value.toMutableMap()
        current[fileName] = content
        _fileContents.value = current
    }
    
    data class NativeWizardUiState(
        val isLoading: Boolean = false,
        val error: String? = null
    )
}