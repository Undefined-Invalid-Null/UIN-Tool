package com.UIN.Tool.constants

import android.os.Environment
import java.io.File

/**
 * 应用统一常量。
 *
 * 所有常量通过此对象获取，是全项目唯一的常量来源。
 * 旧版 `com.UIN.Tool.utils.Constants` / `com.UIN.Tool.constants.Constants` / `PreferenceKeys`
 * 均已合并至此对象，外部代码请统一使用：
 * `import com.UIN.Tool.constants.AppConstants as Constants`
 */
object AppConstants {

    // ==================== 应用信息 ====================
    const val APP_NAME = "UIN_Tool"

    /**
     * 版本名/版本号统一从已安装包读取（避免与 build.gradle 硬编码漂移）。
     * 读取失败时回退到默认值。
     */
    val APP_VERSION: String by lazy {
        try {
            val context = com.UIN.Tool.UinApplication.getAppContext()
            context.packageManager.getPackageInfo(context.packageName, 0)
                .versionName ?: "1.0.0"
        } catch (e: Exception) {
            "1.0.0"
        }
    }

    val APP_VERSION_CODE: Int by lazy {
        try {
            val context = com.UIN.Tool.UinApplication.getAppContext()
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                info.longVersionCode.toInt()
            } else {
                info.versionCode
            }
        } catch (e: Exception) {
            0
        }
    }

    // ==================== 自定义权限 ====================
    const val OPEN_PLUGIN_PERMISSION = "com.UIN.Tool.permission.OPEN_PLUGIN"
    const val RUN_COMMAND_PERMISSION = "com.UIN.Tool.permission.RUN_COMMAND"

    // ==================== 目录路径 ====================
    val WORK_DIR = File(Environment.getExternalStorageDirectory(), APP_NAME).absolutePath
    val PLUGIN_DIR = File(WORK_DIR, "plugins").absolutePath

    /**
     * 日志目录固定在共享存储 UIN_Tool/logs 下，用户可直接访问并反馈日志（用户反馈要求）。
     * 注：共享目录其它应用可读，日志中不应写入 token/敏感路径。
     */
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
    const val PREF_PLUGIN_DATA_PREFIX = "plugin_data_"

    /** 旧名，指向 [PREF_MAIN]，仅为兼容旧代码 */
    const val PREF_NAME = PREF_MAIN

    // ==================== 偏好设置键 ====================
    const val KEY_WORK_FOLDER = "work_folder"
    const val KEY_VIEW_MODE = "view_mode"
    const val KEY_USE_CUSTOM_ICON_TINT = "use_custom_icon_tint"
    const val KEY_JUST_CRASHED = "just_crashed"
    const val KEY_FIRST_LAUNCH = "first_launch"
    const val KEY_LAST_VERSION = "last_version"
    const val KEY_IGNORE_VERSION = "ignore_version"
    const val KEY_FORCE_UPDATE_IGNORE = "force_update_ignore"
    const val KEY_LAST_CHANGELOG = "last_changelog"
    const val KEY_LAST_UPDATE_CHECK = "last_update_check_day"
    const val KEY_ENABLED_MIRRORS = "enabled_mirrors"
    const val KEY_USE_CDN = "use_cdn"
    const val KEY_CURRENT_THEME = "current_theme"
    const val KEY_WIDGET_PLUGIN_1 = "widget_plugin_1"
    const val KEY_WIDGET_PLUGIN_2 = "widget_plugin_2"
    const val KEY_WIDGET_PLUGIN_3 = "widget_plugin_3"
    const val KEY_WIDGET_1X1_PLUGIN = "widget_1x1_plugin"
    const val KEY_PLUGIN_PERMISSIONS = "plugin_permissions_"
    const val KEY_PLUGIN_CONFIG = "plugin_config_"
    const val KEY_PLUGIN_DATA_VERSION = "data_version"
    const val KEY_PLUGIN_DATA_MIGRATED = "_migrated_"

    // ==================== 偏好设置键（镜像/主题/小部件） ====================
    const val CUSTOM_MIRRORS = "custom_mirrors"
    const val UI_CONFIG = "ui_config"
    const val PLUGIN_SIGNATURE_PREFIX = "plugin_signature_"
    const val WIDGET_GLOBAL_PLUGIN_1 = "global_plugin_1"
    const val WIDGET_GLOBAL_PLUGIN_2 = "global_plugin_2"
    const val WIDGET_GLOBAL_PLUGIN_3 = "global_plugin_3"

    // ==================== 偏好设置键（插件多开） ====================
    /** 原生插件多开开关（默认关闭，开发工具页可开启） */
    const val KEY_NATIVE_MULTI_INSTANCE = "native_multi_instance"
    /** 共享端口模式下关闭插件时保留页面/后端会话，重开直接复用（默认开启） */
    const val KEY_SHARED_SESSION_RETAIN = "shared_session_retain"
    /** 开发者选项：忽略签名验证（仅开发调试用，默认关闭） */
    const val KEY_DEV_IGNORE_SIGNATURE = "dev_ignore_signature"

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
    // 已剔除长期失效源（hub.fastgit.xyz/fastgit 已停止服务、mirror.ghproxy.com、
    // gh.api.99988866.xyz、gh.jiewen.ltd 等不可用），保留当前可用/仍在维护的源。
    val DEFAULT_MIRRORS = listOf(
        "https://github.moeyy.xyz",
        "https://ghproxy.net",
        "https://gh-proxy.com",
        "https://gitclone.com"
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
