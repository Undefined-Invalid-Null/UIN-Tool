package com.UIN.Tool.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.UIN.Tool.log.Logger
import com.UIN.Tool.utils.Constants
import com.UIN.Tool.utils.TemplateUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

class WebPluginWizardViewModel : ViewModel() {
    
    companion object {
        private const val TAG = "WebPluginWizardViewModel"
    }
    
    private val _uiState = MutableStateFlow(WebWizardUiState())
    val uiState: StateFlow<WebWizardUiState> = _uiState.asStateFlow()
    
    private val _pluginId = MutableStateFlow("com.example.mywebplugin")
    val pluginId: StateFlow<String> = _pluginId.asStateFlow()
    
    private val _pluginName = MutableStateFlow("我的Web插件")
    val pluginName: StateFlow<String> = _pluginName.asStateFlow()
    
    private val _pluginAuthor = MutableStateFlow("开发者")
    val pluginAuthor: StateFlow<String> = _pluginAuthor.asStateFlow()
    
    private val _pluginDescription = MutableStateFlow("这是一个Web插件示例")
    val pluginDescription: StateFlow<String> = _pluginDescription.asStateFlow()
    
    private val _pluginVersion = MutableStateFlow("1")
    val pluginVersion: StateFlow<String> = _pluginVersion.asStateFlow()
    
    private val _pluginVersionName = MutableStateFlow("1.0.0")
    val pluginVersionName: StateFlow<String> = _pluginVersionName.asStateFlow()
    
    private val _templateType = MutableStateFlow(0) // 0=完整, 1=空白, 2=导入, 3=跳过
    val templateType: StateFlow<Int> = _templateType.asStateFlow()
    
    private val _fileContents = MutableStateFlow<Map<String, String>>(emptyMap())
    val fileContents: StateFlow<Map<String, String>> = _fileContents.asStateFlow()
    
    private val _fileList = MutableStateFlow<List<String>>(emptyList())
    val fileList: StateFlow<List<String>> = _fileList.asStateFlow()
    
    fun updatePluginId(value: String) {
        _pluginId.value = value
    }
    
    fun updatePluginName(value: String) {
        _pluginName.value = value
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
    
    fun updateTemplateType(type: Int) {
        _templateType.value = type
        generateTemplates()
    }
    
    fun generateTemplates() {
        val vars = mapOf(
            "PLUGIN_ID" to _pluginId.value,
            "PLUGIN_NAME" to _pluginName.value,
            "PLUGIN_AUTHOR" to _pluginAuthor.value,
            "PLUGIN_DESCRIPTION" to _pluginDescription.value,
            "PLUGIN_VERSION" to _pluginVersion.value,
            "PLUGIN_VERSION_NAME" to _pluginVersionName.value,
            "UI_TYPE" to "web",
            "ENTRY" to "web/index.html"
        )
        
        viewModelScope.launch {
            try {
                // 生成Web模板
                val files = mutableMapOf<String, String>()
                val context = com.UIN.Tool.UinApplication.getInstance()
                
                when (_templateType.value) {
                    0 -> { // 完整模板
                        val indexHtml = TemplateUtils.renderTemplate(
                            TemplateUtils.loadTemplate(context, "web/index.html"),
                            vars
                        )
                        val styleCss = TemplateUtils.renderTemplate(
                            TemplateUtils.loadTemplate(context, "web/style.css"),
                            vars
                        )
                        val scriptJs = TemplateUtils.renderTemplate(
                            TemplateUtils.loadTemplate(context, "web/script.js"),
                            vars
                        )
                        files["web/index.html"] = indexHtml
                        files["web/style.css"] = styleCss
                        files["web/script.js"] = scriptJs
                    }
                    1 -> { // 空白模板
                        val blankIndex = TemplateUtils.renderTemplate(
                            TemplateUtils.loadTemplate(context, "web/blank_index.html"),
                            vars
                        )
                        files["web/index.html"] = blankIndex
                    }
                    2, 3 -> { // 导入或跳过
                        // 不生成文件
                    }
                }
                
                _fileContents.value = files
                _fileList.value = files.keys.toList()
                
                Logger.i(TAG, "生成模板完成，共 ${files.size} 个文件")
            } catch (e: Exception) {
                Logger.e(TAG, "生成模板失败", e)
            }
        }
    }
    
    fun updateFileContent(fileName: String, content: String) {
        val current = _fileContents.value.toMutableMap()
        current[fileName] = content
        _fileContents.value = current
    }
    
    data class WebWizardUiState(
        val isLoading: Boolean = false,
        val error: String? = null
    )
}