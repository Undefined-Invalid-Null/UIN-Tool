package com.UIN.Tool.utils;

/**
 * 常量定义 - Stub 版本
 * 对应: utils/Constants.kt
 */
public class Constants {

    // ==================== 目录路径 ====================
    public static final String WORK_DIR = "/storage/emulated/0/UIN_Tool";
    public static final String PLUGIN_DIR = WORK_DIR + "/plugins";
    public static final String LOG_DIR = WORK_DIR + "/logs";
    public static final String BACKUP_DIR = WORK_DIR + "/backups";
    public static final String DOWNLOAD_DIR = WORK_DIR + "/downloads";
    public static final String TEMP_DIR = WORK_DIR + "/temp";
    public static final String CACHE_DIR = WORK_DIR + "/cache";
    public static final String TPK_DIR = WORK_DIR + "/tpk";

    // ==================== 偏好设置 ====================
    public static final String PREF_NAME = "uin_tool_prefs";
    public static final String PREF_CRASH = "crash_prefs";
    public static final String PREF_PLUGIN_SIGNATURES = "plugin_signatures";
    public static final String PREF_GITHUB_MIRROR = "github_mirror";
    public static final String PREF_WIDGET = "uin_widget_prefs";
    public static final String PREF_WIDGET_1X1 = "uin_widget_1x1_prefs";
    public static final String PREF_UI_CONFIG = "ui_config_prefs";

    // ==================== 偏好设置Key ====================
    public static final String KEY_WORK_FOLDER = "work_folder";
    public static final String KEY_VIEW_MODE = "view_mode";
    public static final String KEY_USE_CUSTOM_ICON_TINT = "use_custom_icon_tint";
    public static final String KEY_JUST_CRASHED = "just_crashed";
    public static final String KEY_FIRST_LAUNCH = "first_launch";
    public static final String KEY_LAST_VERSION = "last_version";
    public static final String KEY_IGNORE_VERSION = "ignore_version";
    public static final String KEY_FORCE_UPDATE_IGNORE = "force_update_ignore";
    public static final String KEY_ENABLED_MIRRORS = "enabled_mirrors";
    public static final String KEY_USE_CDN = "use_cdn";
    public static final String KEY_CURRENT_THEME = "current_theme";
    public static final String KEY_WIDGET_PLUGIN_1 = "widget_plugin_1";
    public static final String KEY_WIDGET_PLUGIN_2 = "widget_plugin_2";
    public static final String KEY_WIDGET_PLUGIN_3 = "widget_plugin_3";
    public static final String KEY_WIDGET_1X1_PLUGIN = "widget_1x1_plugin";
    public static final String KEY_PLUGIN_PERMISSIONS = "plugin_permissions_";

    // ==================== 插件相关 ====================
    public static final String PLUGIN_EXTENSION = ".tpk";
    public static final String PLUGIN_CONFIG_FILE = "plugin.json";
    public static final String PLUGIN_ICON_FILE = "icon.png";
    public static final String PLUGIN_DEX_FILE = "plugin.dex";
    public static final String PLUGIN_WEB_DIR = "web";
    public static final String PLUGIN_WEB_INDEX = "web/index.html";
    public static final String PLUGIN_PERMISSIONS_FILE = "permissions.json";

    // ==================== 网络配置 ====================
    public static final long NETWORK_TIMEOUT = 30L;
    public static final long NETWORK_READ_TIMEOUT = 60L;
    public static final long NETWORK_WRITE_TIMEOUT = 60L;
    public static final long CACHE_SIZE = 50 * 1024 * 1024L;

    // ==================== 日志配置 ====================
    public static final long LOG_MAX_SIZE = 2 * 1024 * 1024L;
    public static final int LOG_MAX_LINES = 2000;
    public static final int LOG_RETENTION_DAYS = 7;
}