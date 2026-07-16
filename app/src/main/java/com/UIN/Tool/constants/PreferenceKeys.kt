// app/src/main/java/com/UIN/Tool/constants/PreferenceKeys.kt
package com.UIN.Tool.constants

/**
 * 统一偏好设置键
 * 所有 SharedPreferences 键通过此对象获取
 */
object PreferenceKeys {
    
    // ==================== 工作目录 ====================
    const val WORK_FOLDER = "work_folder"
    const val VIEW_MODE = "view_mode"
    const val USE_CUSTOM_ICON_TINT = "use_custom_icon_tint"
    
    // ==================== 启动状态 ====================
    const val FIRST_LAUNCH = "first_launch"
    const val LAST_VERSION = "last_version"
    
    // ==================== 更新相关 ====================
    const val IGNORE_VERSION = "ignore_version"
    const val FORCE_UPDATE_IGNORE = "force_update_ignore"
    
    // ==================== 崩溃相关 ====================
    const val JUST_CRASHED = "just_crashed"
    
    // ==================== 镜像站 ====================
    const val ENABLED_MIRRORS = "enabled_mirrors"
    const val USE_CDN = "use_cdn"
    const val CUSTOM_MIRRORS = "custom_mirrors"
    
    // ==================== 主题 ====================
    const val CURRENT_THEME = "current_theme"
    const val UI_CONFIG = "ui_config"
    
    // ==================== 插件 ====================
    const val PLUGIN_PERMISSIONS_PREFIX = "plugin_permissions_"
    const val PLUGIN_CONFIG_PREFIX = "plugin_config_"
    const val PLUGIN_SIGNATURE_PREFIX = "plugin_signature_"
    
    // ==================== 小部件 ====================
    const val WIDGET_PLUGIN_1 = "widget_plugin_1"
    const val WIDGET_PLUGIN_2 = "widget_plugin_2"
    const val WIDGET_PLUGIN_3 = "widget_plugin_3"
    const val WIDGET_1X1_PLUGIN = "widget_1x1_plugin"
    const val WIDGET_GLOBAL_PLUGIN_1 = "global_plugin_1"
    const val WIDGET_GLOBAL_PLUGIN_2 = "global_plugin_2"
    const val WIDGET_GLOBAL_PLUGIN_3 = "global_plugin_3"
}