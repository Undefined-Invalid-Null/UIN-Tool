package com.UIN.Tool.utils

import android.os.Environment
import java.io.File

object Constants {
    
    const val APP_NAME = "UIN_Tool"
    const val APP_VERSION = "4.0.0"
    const val APP_VERSION_CODE = 10  // Build 10
    
    // 目录路径
    val WORK_DIR = File(Environment.getExternalStorageDirectory(), APP_NAME).absolutePath
    val PLUGIN_DIR = File(WORK_DIR, "plugins").absolutePath
    val LOG_DIR = File(WORK_DIR, "logs").absolutePath
    val BACKUP_DIR = File(WORK_DIR, "backups").absolutePath
    val DOWNLOAD_DIR = File(WORK_DIR, "downloads").absolutePath
    val TEMP_DIR = File(WORK_DIR, "temp").absolutePath
    val CACHE_DIR = File(WORK_DIR, "cache").absolutePath
    val TPK_DIR = File(WORK_DIR, "tpk").absolutePath
    
    // 偏好设置
    const val PREF_NAME = "uin_tool_prefs"
    const val PREF_CRASH = "crash_prefs"
    const val PREF_PLUGIN_SIGNATURES = "plugin_signatures"
    const val PREF_GITHUB_MIRROR = "github_mirror"
    const val PREF_WIDGET = "uin_widget_prefs"
    const val PREF_WIDGET_1X1 = "uin_widget_1x1_prefs"
    const val PREF_UI_CONFIG = "ui_config_prefs"
    
    // 偏好设置Key
    const val KEY_WORK_FOLDER = "work_folder"
    const val KEY_VIEW_MODE = "view_mode"
    const val KEY_USE_CUSTOM_ICON_TINT = "use_custom_icon_tint"
    const val KEY_JUST_CRASHED = "just_crashed"
    const val KEY_FIRST_LAUNCH = "first_launch"
    const val KEY_LAST_VERSION = "last_version"
    const val KEY_IGNORE_VERSION = "ignore_version"
    const val KEY_FORCE_UPDATE_IGNORE = "force_update_ignore"
    const val KEY_ENABLED_MIRRORS = "enabled_mirrors"
    const val KEY_USE_CDN = "use_cdn"
    const val KEY_CURRENT_THEME = "current_theme"
    const val KEY_WIDGET_PLUGIN_1 = "widget_plugin_1"
    const val KEY_WIDGET_PLUGIN_2 = "widget_plugin_2"
    const val KEY_WIDGET_PLUGIN_3 = "widget_plugin_3"
    const val KEY_WIDGET_1X1_PLUGIN = "widget_1x1_plugin"
    const val KEY_PLUGIN_PERMISSIONS = "plugin_permissions_"
    const val KEY_PLUGIN_CONFIG = "plugin_config_"
    
    // 日志配置
    const val LOG_MAX_SIZE = 2 * 1024 * 1024L
    const val LOG_MAX_LINES = 2000
    const val LOG_RETENTION_DAYS = 7
    
    // 插件相关
    const val PLUGIN_EXTENSION = ".tpk"
    const val PLUGIN_CONFIG_FILE = "plugin.json"
    const val PLUGIN_ICON_FILE = "icon.png"
    const val PLUGIN_DEX_FILE = "plugin.dex"
    const val PLUGIN_WEB_DIR = "web"
    const val PLUGIN_WEB_INDEX = "web/index.html"
    const val PLUGIN_PERMISSIONS_FILE = "permissions.json"
    
    // 网络配置
    const val NETWORK_TIMEOUT = 30L
    const val NETWORK_READ_TIMEOUT = 60L
    const val NETWORK_WRITE_TIMEOUT = 60L
    const val CACHE_SIZE = 50 * 1024 * 1024L
    
    // 默认镜像站
    val DEFAULT_MIRRORS = listOf(
        "https://hub.fastgit.xyz",
        "https://github.moeyy.xyz",
        "https://ghproxy.net",
        "https://mirror.ghproxy.com",
        "https://gh.api.99988866.xyz",
        "https://gitclone.com",
        "https://gh.jiewen.ltd"
    )
    
    // UI配置
    const val UI_CONFIG_FILE = "ui_config.json"
    
    // 权限请求码
    const val PERMISSION_REQUEST_STORAGE = 100
    const val PERMISSION_REQUEST_CAMERA = 101
    const val PERMISSION_REQUEST_LOCATION = 102
    const val PERMISSION_REQUEST_MICROPHONE = 103
    const val PERMISSION_REQUEST_CONTACTS = 104
    const val PERMISSION_REQUEST_PHONE = 105
    const val PERMISSION_REQUEST_SMS = 106
    const val PERMISSION_REQUEST_CALENDAR = 107
    const val PERMISSION_REQUEST_SENSORS = 108
    
    // Activity结果码
    const val REQUEST_CODE_IMPORT_PLUGIN = 1001
    const val REQUEST_CODE_IMPORT_ZIP = 1002
    const val REQUEST_CODE_EXPORT_PLUGIN = 1003
    const val REQUEST_CODE_SELECT_ICON = 1004
    const val REQUEST_CODE_SELECT_RESOURCE = 1005
    const val REQUEST_CODE_CODE_EDITOR = 1006
    const val REQUEST_CODE_SETTINGS = 1007
    
    // 其他
    const val SPLASH_DELAY = 1500L
    const val SEARCH_DEBOUNCE = 300L
    const val MAX_PLUGIN_NAME_LENGTH = 50
    const val MAX_PLUGIN_DESCRIPTION_LENGTH = 500
}