package com.UIN.Tool.plugin

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.UIN.Tool.data.local.PreferenceManager
import com.UIN.Tool.log.Logger
import com.UIN.Tool.utils.Constants  // ✅ 添加
import com.UIN.Tool.utils.PermissionUtils

/**
 * 插件权限管理器 - 仅提供工具方法，不包含UI弹窗
 * 所有UI弹窗由 PluginHostActivity 的 Compose UI 负责
 */
object PluginPermissionManager {

    private const val TAG = "PluginPermissionManager"

    private val SPECIAL_PERMISSIONS = setOf(
        "MANAGE_EXTERNAL_STORAGE",
        "SYSTEM_ALERT_WINDOW",
        "WRITE_SETTINGS",
        "REQUEST_INSTALL_PACKAGES",
        "PACKAGE_USAGE_STATS",
        "ACCESSIBILITY",
        "POST_NOTIFICATIONS"
    )

    // ==================== 权限状态查询 ====================

    fun getPluginDeclaredPermissions(context: Context, pluginId: String): List<String> {
        val plugin = PluginManager.getInstance(context).getPluginInfo(pluginId)
        return plugin?.permissions ?: emptyList()
    }

    fun getPluginPermissionStatus(context: Context, pluginId: String): Map<String, Boolean> {
        val declared = getPluginDeclaredPermissions(context, pluginId)
        return declared.associateWith { permission ->
            checkPermission(context, permission)
        }
    }

    fun checkPermission(context: Context, permission: String): Boolean {
        return PermissionUtils.hasPermission(context, permission) ||
                PermissionUtils.hasSpecialPermission(context, permission)
    }

    fun areAllPermissionsGranted(context: Context, pluginId: String): Boolean {
        val permissions = getPluginDeclaredPermissions(context, pluginId)
        if (permissions.isEmpty()) return true
        return permissions.all { checkPermission(context, it) }
    }

    fun getMissingPermissions(context: Context, pluginId: String): List<String> {
        val permissions = getPluginDeclaredPermissions(context, pluginId)
        return permissions.filter { !checkPermission(context, it) }
    }

    fun isSpecialPermission(permission: String): Boolean {
        return permission in SPECIAL_PERMISSIONS ||
                PermissionUtils.isSpecialPermission(permission)
    }

    fun getPermissionDisplayName(permission: String): String {
        return PermissionUtils.getPermissionDisplayName(permission)
    }

    fun getPermissionDescription(permission: String): String {
        return when (permission) {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE -> "用于读取和写入插件文件"
            "MANAGE_EXTERNAL_STORAGE" -> "用于管理插件目录的所有文件"
            Manifest.permission.CAMERA -> "用于插件调用相机拍照"
            Manifest.permission.RECORD_AUDIO -> "用于插件录音功能"
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION -> "用于插件获取位置信息"
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.WRITE_CONTACTS -> "用于插件读取/修改联系人"
            Manifest.permission.CALL_PHONE -> "用于插件拨打电话"
            Manifest.permission.READ_PHONE_STATE -> "用于插件获取设备信息"
            Manifest.permission.SEND_SMS -> "用于插件发送短信"
            Manifest.permission.READ_SMS -> "用于插件读取短信"
            Manifest.permission.RECEIVE_SMS -> "用于插件接收短信"
            Manifest.permission.READ_CALENDAR,
            Manifest.permission.WRITE_CALENDAR -> "用于插件读取/修改日历"
            "SYSTEM_ALERT_WINDOW" -> "用于插件显示悬浮窗"
            "WRITE_SETTINGS" -> "用于插件修改系统设置"
            "REQUEST_INSTALL_PACKAGES" -> "用于插件安装应用"
            "PACKAGE_USAGE_STATS" -> "用于插件获取应用使用统计"
            "ACCESSIBILITY" -> "用于插件的无障碍自动化操作"
            "POST_NOTIFICATIONS" -> "用于插件发送通知"
            Manifest.permission.VIBRATE -> "用于插件震动反馈"
            Manifest.permission.INTERNET -> "用于插件网络请求"
            Manifest.permission.ACCESS_NETWORK_STATE -> "用于检查网络状态"
            else -> "插件所需权限"
        }
    }

    fun getPermissionIcon(permission: String): Int {
        return when {
            permission.contains("STORAGE") || permission.contains("EXTERNAL") -> android.R.drawable.ic_menu_manage
            permission.contains("CAMERA") -> android.R.drawable.ic_menu_camera
            permission.contains("RECORD_AUDIO") -> android.R.drawable.ic_menu_manage
            permission.contains("LOCATION") -> android.R.drawable.ic_menu_mylocation
            permission.contains("CONTACTS") -> android.R.drawable.ic_menu_manage
            permission.contains("PHONE") || permission.contains("CALL") -> android.R.drawable.ic_menu_call
            permission.contains("SMS") -> android.R.drawable.ic_menu_send
            permission.contains("CALENDAR") -> android.R.drawable.ic_menu_agenda
            isSpecialPermission(permission) -> android.R.drawable.ic_menu_preferences
            else -> android.R.drawable.ic_menu_info_details
        }
    }

    // ==================== 权限状态管理（持久化） ====================

    fun getPermissionState(context: Context, pluginId: String): Int {
        val prefs = context.getSharedPreferences("${Constants.PREF_PLUGIN_DATA_PREFIX}$pluginId", Context.MODE_PRIVATE)
        return prefs.getInt("permission_state", 0)
    }

    fun setPermissionState(context: Context, pluginId: String, state: Int) {
        val prefs = context.getSharedPreferences("${Constants.PREF_PLUGIN_DATA_PREFIX}$pluginId", Context.MODE_PRIVATE)
        prefs.edit().putInt("permission_state", state).apply()
        Logger.d(TAG, "权限状态已更新: $pluginId -> $state")
    }

    fun shouldShowPermissionDialog(context: Context, pluginId: String): Boolean {
        return getPermissionState(context, pluginId) == 0
    }

    fun getPermissionStateDescription(state: Int): String {
        return when (state) {
            0 -> "未授权"
            1 -> "已授权"
            2 -> "已拒绝"
            else -> "未知"
        }
    }

    // ==================== 权限请求（仅用于Activity，不包含UI） ====================

    fun requestPermissions(
        activity: Activity,
        permissions: Array<String>,
        requestCode: Int
    ) {
        ActivityCompat.requestPermissions(activity, permissions, requestCode)
    }

    // ==================== 特殊权限引导（打开系统设置） ====================

    fun openAppSettings(activity: Activity) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            intent.data = Uri.parse("package:${activity.packageName}")
            activity.startActivity(intent)
        } catch (e: Exception) {
            Logger.e(TAG, "打开设置失败", e)
        }
    }

    // ==================== 权限状态摘要 ====================

    data class PermissionStatusSummary(
        val total: Int,
        val granted: Int,
        val denied: Int,
        val isAllGranted: Boolean,
        val hasMissing: Boolean
    )

    fun getPermissionStatusSummary(context: Context, pluginId: String): PermissionStatusSummary {
        val permissions = getPluginDeclaredPermissions(context, pluginId)
        if (permissions.isEmpty()) {
            return PermissionStatusSummary(0, 0, 0, true, false)
        }
        val granted = permissions.count { checkPermission(context, it) }
        val denied = permissions.size - granted
        return PermissionStatusSummary(
            total = permissions.size,
            granted = granted,
            denied = denied,
            isAllGranted = granted == permissions.size,
            hasMissing = denied > 0
        )
    }
}