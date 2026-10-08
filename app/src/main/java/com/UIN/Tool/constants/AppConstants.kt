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
                .versionName ?: com.UIN.Tool.BuildConfig.VERSION_NAME
        } catch (e: Exception) {
            // B-77：getAppContext() 在 Application 赋值前会抛；回退 BuildConfig 而不是写死 1.0.0
            com.UIN.Tool.BuildConfig.VERSION_NAME
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
            // B-77：同上，回退 BuildConfig.VERSION_CODE
            com.UIN.Tool.BuildConfig.VERSION_CODE
        }
    }

    // ==================== 目录路径 ====================
    /**
     * B-71/B-78/D-34/G-06/G-11：日志/备份/TPK 曾于批次17·下58 迁 getExternalFilesDir()，
     * 拍板 2.0 方向回调（便利优先）：回迁共享工作目录 /sdcard/UIN_Tool 目录——
     * （注意：此处不可写「/UIN_Tool/ *」式通配，Kotlin 块注释嵌套会吞掉后续代码）——
     * 整个工作目录本就共享且 App 硬门 MANAGE_EXTERNAL_STORAGE，隐私孤岛无安全增益；
     * 「其他应用可读日志」风险由 B-71 脱敏（不写 token/绝对路径/剪贴板）兜底。
     * 用户文件管理器可直接取用（原始「用户反馈要求」路径恢复）。
     */
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
    const val KEY_PLUGIN_PERMISSIONS = "plugin_permissions_"
    const val KEY_PLUGIN_DATA_VERSION = "data_version"
    const val KEY_PLUGIN_DATA_MIGRATED = "_migrated_"

    // ==================== 偏好设置键（插件多开） ====================
    /** 原生插件多开开关（默认关闭，开发工具页可开启） */
    const val KEY_NATIVE_MULTI_INSTANCE = "native_multi_instance"
    /** 共享端口模式下关闭插件时保留页面/后端会话，重开直接复用（默认开启） */
    const val KEY_SHARED_SESSION_RETAIN = "shared_session_retain"
    /** 开发者选项：忽略签名验证（仅开发调试用，默认关闭） */
    const val KEY_DEV_IGNORE_SIGNATURE = "dev_ignore_signature"
    // D-27③：原先散在 PreferenceManager 里的裸字符串 key 收敛到常量
    const val KEY_SOURCES_JSON = "sources_json"
    const val KEY_ENABLED_SOURCE_IDS = "enabled_source_ids"
    const val KEY_LAST_REPO_REFRESH_TIME = "last_repo_refresh_time"

    // ==================== 插件相关 ====================
    const val PLUGIN_EXTENSION = ".tpk"
    const val PLUGIN_CONFIG_FILE = "plugin.json"
    const val PLUGIN_DEX_FILE = "plugin.dex"

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

    // ==================== 默认源 ====================
    val DEFAULT_SOURCES_JSON = """
        [
            {
                "sourceId": "official",
                "name": "Official Source",
                "owner": "UIN-Tool-Plugins",
                "repo": "Official",
                "branch": "dist",
                "description": "UIN Tool official plugin repository",
                "trustLevel": "official",
                "addedAt": "2026-08-30T14:07:27+08:00"
            }
        ]
    """.trimIndent()

    // ==================== 日志配置 ====================
    const val LOG_MAX_SIZE = 2 * 1024 * 1024L
    const val LOG_RETENTION_DAYS = 7

    // ==================== Proot/Container ====================
    const val PREF_PROOT = "proot_prefs"
    const val KEY_DEFAULT_CONTAINER = "default_container"
    // D-33：是否把宿主共享存储 bind 进容器（默认保持旧行为；关闭后容器只能看自己的 rootfs）
    const val KEY_PROOT_BIND_SHARED_STORAGE = "proot_bind_shared_storage"
    const val CONTAINER_PLUGINS_PREFIX = "/plugins"

}
