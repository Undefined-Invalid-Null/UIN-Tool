// app/src/main/java/com/UIN/Tool/constants/AppConstants.kt
package com.UIN.Tool.constants

import android.os.Environment
import java.io.File

/**
 * 统一应用常量
 * 所有常量通过此对象获取
 * 
 * 注意：Constants 保持兼容，内部委托给此对象
 */
object AppConstants {
    
    // ==================== 应用信息 ====================
    const val APP_NAME = "UIN_Tool"
    const val APP_VERSION = "4.2.0"
    const val APP_VERSION_CODE = 12
    
    // ==================== 目录路径 ====================
    val WORK_DIR = File(Environment.getExternalStorageDirectory(), APP_NAME).absolutePath
    val PLUGIN_DIR = File(WORK_DIR, "plugins").absolutePath
    val LOG_DIR = File(WORK_DIR, "logs").absolutePath
    val BACKUP_DIR = File(WORK_DIR, "backups").absolutePath
    val DOWNLOAD_DIR = File(WORK_DIR, "downloads").absolutePath
    val TEMP_DIR = File(WORK_DIR, "temp").absolutePath
    val CACHE_DIR = File(WORK_DIR, "cache").absolutePath
    val TPK_DIR = File(WORK_DIR, "tpk").absolutePath
    
    // ==================== 偏好设置文件名 ====================
    const val PREF_MAIN = "uin_tool_prefs"
    const val PREF_CRASH = "crash_prefs"
    const val PREF_PLUGIN_SIGNATURES = "plugin_signatures"
    const val PREF_GITHUB_MIRROR = "github_mirror"
    const val PREF_WIDGET = "uin_widget_prefs"
    const val PREF_WIDGET_1X1 = "uin_widget_1x1_prefs"
    const val PREF_UI_CONFIG = "ui_config_prefs"
    const val PREF_PLUGIN_CONFIG = "plugin_config_"
    const val PREF_PLUGIN_PERMISSIONS = "plugin_permissions_"
    
    // ==================== 插件相关 ====================
    const val PLUGIN_EXTENSION = ".tpk"
    const val PLUGIN_CONFIG_FILE = "plugin.json"
    const val PLUGIN_ICON_FILE = "icon.png"
    const val PLUGIN_DEX_FILE = "plugin.dex"
    const val PLUGIN_WEB_DIR = "web"
    const val PLUGIN_WEB_INDEX = "web/index.html"
    const val PLUGIN_PERMISSIONS_FILE = "permissions.json"
    const val PLUGIN_MAX_NAME_LENGTH = 50
    const val PLUGIN_MAX_DESCRIPTION_LENGTH = 500
    
    // ==================== 网络配置 ====================
    const val NETWORK_TIMEOUT = 30L
    const val NETWORK_READ_TIMEOUT = 60L
    const val NETWORK_WRITE_TIMEOUT = 60L
    const val CACHE_SIZE = 50 * 1024 * 1024L
    
    // ==================== 默认镜像站 ====================
    val DEFAULT_MIRRORS = listOf(
        "https://hub.fastgit.xyz",
        "https://github.moeyy.xyz",
        "https://ghproxy.net",
        "https://mirror.ghproxy.com",
        "https://gh.api.99988866.xyz",
        "https://gitclone.com",
        "https://gh.jiewen.ltd"
    )
    
    // ==================== UI配置 ====================
    const val UI_CONFIG_FILE = "ui_config.json"
    
    // ==================== 日志配置 ====================
    const val LOG_MAX_SIZE = 2 * 1024 * 1024L
    const val LOG_MAX_LINES = 2000
    const val LOG_RETENTION_DAYS = 7
    
    // ==================== 权限请求码 ====================
    const val PERMISSION_REQUEST_STORAGE = 100
    const val PERMISSION_REQUEST_CAMERA = 101
    const val PERMISSION_REQUEST_LOCATION = 102
    const val PERMISSION_REQUEST_MICROPHONE = 103
    const val PERMISSION_REQUEST_CONTACTS = 104
    const val PERMISSION_REQUEST_PHONE = 105
    const val PERMISSION_REQUEST_SMS = 106
    const val PERMISSION_REQUEST_CALENDAR = 107
    const val PERMISSION_REQUEST_SENSORS = 108
    
    // ==================== Activity结果码 ====================
    const val REQUEST_CODE_IMPORT_PLUGIN = 1001
    const val REQUEST_CODE_IMPORT_ZIP = 1002
    const val REQUEST_CODE_EXPORT_PLUGIN = 1003
    const val REQUEST_CODE_SELECT_ICON = 1004
    const val REQUEST_CODE_SELECT_RESOURCE = 1005
    const val REQUEST_CODE_CODE_EDITOR = 1006
    const val REQUEST_CODE_SETTINGS = 1007
    
    // ==================== 其他常量 ====================
    const val SPLASH_DELAY = 1500L
    const val SEARCH_DEBOUNCE = 300L
    const val MAX_PLUGIN_NAME_LENGTH = 50
    const val MAX_PLUGIN_DESCRIPTION_LENGTH = 500
}

// ==================== Constants 兼容层 ====================
// 保持 Constants 类名不变，内部委托给 AppConstants

@Suppress("DEPRECATED")
object Constants {
    
    @Deprecated("使用 AppConstants.APP_NAME", ReplaceWith("AppConstants.APP_NAME"))
    const val APP_NAME = AppConstants.APP_NAME
    
    @Deprecated("使用 AppConstants.APP_VERSION", ReplaceWith("AppConstants.APP_VERSION"))
    const val APP_VERSION = AppConstants.APP_VERSION
    
    @Deprecated("使用 AppConstants.APP_VERSION_CODE", ReplaceWith("AppConstants.APP_VERSION_CODE"))
    const val APP_VERSION_CODE = AppConstants.APP_VERSION_CODE
    
    @Deprecated("使用 AppConstants.WORK_DIR", ReplaceWith("AppConstants.WORK_DIR"))
    val WORK_DIR = AppConstants.WORK_DIR
    
    @Deprecated("使用 AppConstants.PLUGIN_DIR", ReplaceWith("AppConstants.PLUGIN_DIR"))
    val PLUGIN_DIR = AppConstants.PLUGIN_DIR
    
    @Deprecated("使用 AppConstants.LOG_DIR", ReplaceWith("AppConstants.LOG_DIR"))
    val LOG_DIR = AppConstants.LOG_DIR
    
    @Deprecated("使用 AppConstants.BACKUP_DIR", ReplaceWith("AppConstants.BACKUP_DIR"))
    val BACKUP_DIR = AppConstants.BACKUP_DIR
    
    @Deprecated("使用 AppConstants.DOWNLOAD_DIR", ReplaceWith("AppConstants.DOWNLOAD_DIR"))
    val DOWNLOAD_DIR = AppConstants.DOWNLOAD_DIR
    
    @Deprecated("使用 AppConstants.TEMP_DIR", ReplaceWith("AppConstants.TEMP_DIR"))
    val TEMP_DIR = AppConstants.TEMP_DIR
    
    @Deprecated("使用 AppConstants.CACHE_DIR", ReplaceWith("AppConstants.CACHE_DIR"))
    val CACHE_DIR = AppConstants.CACHE_DIR
    
    @Deprecated("使用 AppConstants.TPK_DIR", ReplaceWith("AppConstants.TPK_DIR"))
    val TPK_DIR = AppConstants.TPK_DIR
    
    @Deprecated("使用 AppConstants.PREF_MAIN", ReplaceWith("AppConstants.PREF_MAIN"))
    const val PREF_NAME = AppConstants.PREF_MAIN
    
    @Deprecated("使用 AppConstants.PREF_CRASH", ReplaceWith("AppConstants.PREF_CRASH"))
    const val PREF_CRASH = AppConstants.PREF_CRASH
    
    @Deprecated("使用 AppConstants.PREF_PLUGIN_SIGNATURES", ReplaceWith("AppConstants.PREF_PLUGIN_SIGNATURES"))
    const val PREF_PLUGIN_SIGNATURES = AppConstants.PREF_PLUGIN_SIGNATURES
    
    @Deprecated("使用 AppConstants.PREF_GITHUB_MIRROR", ReplaceWith("AppConstants.PREF_GITHUB_MIRROR"))
    const val PREF_GITHUB_MIRROR = AppConstants.PREF_GITHUB_MIRROR
    
    @Deprecated("使用 AppConstants.PREF_WIDGET", ReplaceWith("AppConstants.PREF_WIDGET"))
    const val PREF_WIDGET = AppConstants.PREF_WIDGET
    
    @Deprecated("使用 AppConstants.PREF_WIDGET_1X1", ReplaceWith("AppConstants.PREF_WIDGET_1X1"))
    const val PREF_WIDGET_1X1 = AppConstants.PREF_WIDGET_1X1
    
    @Deprecated("使用 AppConstants.PREF_UI_CONFIG", ReplaceWith("AppConstants.PREF_UI_CONFIG"))
    const val PREF_UI_CONFIG = AppConstants.PREF_UI_CONFIG
    
    @Deprecated("使用 AppConstants.DEFAULT_MIRRORS", ReplaceWith("AppConstants.DEFAULT_MIRRORS"))
    val DEFAULT_MIRRORS = AppConstants.DEFAULT_MIRRORS
    
    @Deprecated("使用 AppConstants.NETWORK_TIMEOUT", ReplaceWith("AppConstants.NETWORK_TIMEOUT"))
    const val NETWORK_TIMEOUT = AppConstants.NETWORK_TIMEOUT
    
    @Deprecated("使用 AppConstants.NETWORK_READ_TIMEOUT", ReplaceWith("AppConstants.NETWORK_READ_TIMEOUT"))
    const val NETWORK_READ_TIMEOUT = AppConstants.NETWORK_READ_TIMEOUT
    
    @Deprecated("使用 AppConstants.NETWORK_WRITE_TIMEOUT", ReplaceWith("AppConstants.NETWORK_WRITE_TIMEOUT"))
    const val NETWORK_WRITE_TIMEOUT = AppConstants.NETWORK_WRITE_TIMEOUT
    
    @Deprecated("使用 AppConstants.CACHE_SIZE", ReplaceWith("AppConstants.CACHE_SIZE"))
    const val CACHE_SIZE = AppConstants.CACHE_SIZE
    
    @Deprecated("使用 AppConstants.LOG_MAX_SIZE", ReplaceWith("AppConstants.LOG_MAX_SIZE"))
    const val LOG_MAX_SIZE = AppConstants.LOG_MAX_SIZE
    
    @Deprecated("使用 AppConstants.LOG_RETENTION_DAYS", ReplaceWith("AppConstants.LOG_RETENTION_DAYS"))
    const val LOG_RETENTION_DAYS = AppConstants.LOG_RETENTION_DAYS
    
    @Deprecated("使用 AppConstants.KEY_WORK_FOLDER", ReplaceWith("PreferenceKeys.WORK_FOLDER"))
    const val KEY_WORK_FOLDER = "work_folder"
    
    @Deprecated("使用 AppConstants.KEY_VIEW_MODE", ReplaceWith("PreferenceKeys.VIEW_MODE"))
    const val KEY_VIEW_MODE = "view_mode"
    
    @Deprecated("使用 AppConstants.KEY_FIRST_LAUNCH", ReplaceWith("PreferenceKeys.FIRST_LAUNCH"))
    const val KEY_FIRST_LAUNCH = "first_launch"
    
    @Deprecated("使用 AppConstants.KEY_LAST_VERSION", ReplaceWith("PreferenceKeys.LAST_VERSION"))
    const val KEY_LAST_VERSION = "last_version"
    
    @Deprecated("使用 AppConstants.KEY_IGNORE_VERSION", ReplaceWith("PreferenceKeys.IGNORE_VERSION"))
    const val KEY_IGNORE_VERSION = "ignore_version"
    
    @Deprecated("使用 AppConstants.KEY_FORCE_UPDATE_IGNORE", ReplaceWith("PreferenceKeys.FORCE_UPDATE_IGNORE"))
    const val KEY_FORCE_UPDATE_IGNORE = "force_update_ignore"
    
    @Deprecated("使用 AppConstants.KEY_ENABLED_MIRRORS", ReplaceWith("PreferenceKeys.ENABLED_MIRRORS"))
    const val KEY_ENABLED_MIRRORS = "enabled_mirrors"
    
    @Deprecated("使用 AppConstants.KEY_USE_CDN", ReplaceWith("PreferenceKeys.USE_CDN"))
    const val KEY_USE_CDN = "use_cdn"
    
    @Deprecated("使用 AppConstants.KEY_CURRENT_THEME", ReplaceWith("PreferenceKeys.CURRENT_THEME"))
    const val KEY_CURRENT_THEME = "current_theme"
    
    @Deprecated("使用 AppConstants.KEY_WIDGET_PLUGIN_1", ReplaceWith("PreferenceKeys.WIDGET_PLUGIN_1"))
    const val KEY_WIDGET_PLUGIN_1 = "widget_plugin_1"
    
    @Deprecated("使用 AppConstants.KEY_WIDGET_PLUGIN_2", ReplaceWith("PreferenceKeys.WIDGET_PLUGIN_2"))
    const val KEY_WIDGET_PLUGIN_2 = "widget_plugin_2"
    
    @Deprecated("使用 AppConstants.KEY_WIDGET_PLUGIN_3", ReplaceWith("PreferenceKeys.WIDGET_PLUGIN_3"))
    const val KEY_WIDGET_PLUGIN_3 = "widget_plugin_3"
    
    @Deprecated("使用 AppConstants.KEY_PLUGIN_PERMISSIONS", ReplaceWith("PreferenceKeys.PLUGIN_PERMISSIONS_PREFIX"))
    const val KEY_PLUGIN_PERMISSIONS = "plugin_permissions_"
    
    @Deprecated("使用 AppConstants.KEY_PLUGIN_CONFIG", ReplaceWith("PreferenceKeys.PLUGIN_CONFIG_PREFIX"))
    const val KEY_PLUGIN_CONFIG = "plugin_config_"
    
    @Deprecated("使用 AppConstants.PLUGIN_EXTENSION", ReplaceWith("AppConstants.PLUGIN_EXTENSION"))
    const val PLUGIN_EXTENSION = AppConstants.PLUGIN_EXTENSION
    
    @Deprecated("使用 AppConstants.PLUGIN_CONFIG_FILE", ReplaceWith("AppConstants.PLUGIN_CONFIG_FILE"))
    const val PLUGIN_CONFIG_FILE = AppConstants.PLUGIN_CONFIG_FILE
    
    @Deprecated("使用 AppConstants.PLUGIN_DEX_FILE", ReplaceWith("AppConstants.PLUGIN_DEX_FILE"))
    const val PLUGIN_DEX_FILE = AppConstants.PLUGIN_DEX_FILE
    
    @Deprecated("使用 AppConstants.PLUGIN_WEB_INDEX", ReplaceWith("AppConstants.PLUGIN_WEB_INDEX"))
    const val PLUGIN_WEB_INDEX = AppConstants.PLUGIN_WEB_INDEX
    
    @Deprecated("使用 AppConstants.REQUEST_CODE_IMPORT_PLUGIN", ReplaceWith("AppConstants.REQUEST_CODE_IMPORT_PLUGIN"))
    const val REQUEST_CODE_IMPORT_PLUGIN = AppConstants.REQUEST_CODE_IMPORT_PLUGIN
    
    @Deprecated("使用 AppConstants.SPLASH_DELAY", ReplaceWith("AppConstants.SPLASH_DELAY"))
    const val SPLASH_DELAY = AppConstants.SPLASH_DELAY
}