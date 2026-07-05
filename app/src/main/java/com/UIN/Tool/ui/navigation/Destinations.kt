package com.UIN.Tool.ui.navigation

sealed class Destinations(val route: String) {
    // ==================== 启动和主界面 ====================
    object Splash : Destinations("splash")
    object Onboarding : Destinations("onboarding")
    object Main : Destinations("main")
    
    // ==================== 四大主页面 ====================
    object Dev : Destinations("dev")
    object Tools : Destinations("tools")
    object Repo : Destinations("repo")
    object Manage : Destinations("manage")
    
    // ==================== 开发相关 ====================
    object NativePluginWizard : Destinations("native_plugin_wizard")
    object WebPluginWizard : Destinations("web_plugin_wizard")
    object CodeEditor : Destinations("code_editor")
    object DevDoc : Destinations("dev_doc")
    
    // ==================== 文档相关 ====================
    object DocBrowser : Destinations("doc_browser")
    object DocViewer : Destinations("doc_viewer")
    object Help : Destinations("help")
    
    // ==================== 管理相关 ====================
    object PluginManage : Destinations("plugin_manage")
    object PermissionManager : Destinations("permission_manager")
    object PluginPermission : Destinations("plugin_permission")
    object PermissionExplain : Destinations("permission_explain")
    object LogViewer : Destinations("log_viewer")
    object Backup : Destinations("backup")
    object UIConfig : Destinations("ui_config")
    object GitHubMirror : Destinations("github_mirror")
    object DeveloperOptions : Destinations("developer_options")
    object WidgetGuide : Destinations("widget_guide")
    object CheckUpdate : Destinations("check_update")
    
    // ==================== 小部件相关 ====================
    object WidgetConfigure : Destinations("widget_configure")
    object Widget1x1Configure : Destinations("widget_1x1_configure")
    
    // ==================== 其他 ====================
    object About : Destinations("about")
    object Contributors : Destinations("contributors")
    object Changelog : Destinations("changelog")
    
    companion object {
        // 便捷方法：构建带参数的路径
        fun docViewer(docType: String): String {
            return "doc_viewer/$docType"
        }
        
        fun codeEditor(files: List<String>? = null, contents: Map<String, String>? = null): String {
            // 简单实现，实际使用需要序列化
            return "code_editor"
        }
        
        fun devDoc(docType: String): String {
            return "dev_doc/$docType"
        }
    }
}

// ==================== 导航参数键 ====================
object NavArgs {
    const val PLUGIN_ID = "plugin_id"
    const val DOC_TYPE = "doc_type"
    const val TITLE = "title"
    const val IS_VERSION_UPDATE = "is_version_update"
    const val VERSION_NAME = "version_name"
    const val SELECTED_TAB = "selected_tab"
    const val CHECK_UPDATE = "check_update"
    const val AUTO_OPEN = "auto_open"
    const val FILE_LIST = "file_list"
    const val FILE_CONTENTS = "file_contents"
    const val UI_TYPE = "ui_type"
    const val MAIN_CLASS = "main_class"
    const val PLUGIN_NAME = "plugin_name"
    const val PLUGIN_ID_KEY = "plugin_id"
}