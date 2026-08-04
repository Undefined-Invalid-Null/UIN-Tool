package com.UIN.Tool.plugin

import com.UIN.Tool.R
import com.UIN.Tool.utils.Str
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
import com.UIN.Tool.constants.AppConstants as Constants
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
            Manifest.permission.WRITE_EXTERNAL_STORAGE -> Str.get(R.string.for_reading_and_writing_plugin_files)
            "MANAGE_EXTERNAL_STORAGE" -> Str.get(R.string.for_managing_all_files_in_the_plugin)
            Manifest.permission.CAMERA -> Str.get(R.string.for_the_plugin_to_take_photos_with_t)
            Manifest.permission.RECORD_AUDIO -> Str.get(R.string.for_plugin_recording)
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION -> Str.get(R.string.for_the_plugin_to_get_location)
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.WRITE_CONTACTS -> Str.get(R.string.for_the_plugin_to_read_modify_contac)
            Manifest.permission.CALL_PHONE -> Str.get(R.string.for_the_plugin_to_make_phone_calls)
            Manifest.permission.READ_PHONE_STATE -> Str.get(R.string.for_the_plugin_to_get_device_info)
            Manifest.permission.SEND_SMS -> Str.get(R.string.for_the_plugin_to_send_sms)
            Manifest.permission.READ_SMS -> Str.get(R.string.for_the_plugin_to_read_sms)
            Manifest.permission.RECEIVE_SMS -> Str.get(R.string.for_the_plugin_to_receive_sms)
            Manifest.permission.READ_CALENDAR,
            Manifest.permission.WRITE_CALENDAR -> Str.get(R.string.for_the_plugin_to_read_modify_calend)
            "SYSTEM_ALERT_WINDOW" -> Str.get(R.string.for_the_plugin_to_show_overlays)
            "WRITE_SETTINGS" -> Str.get(R.string.for_the_plugin_to_modify_system_sett)
            "REQUEST_INSTALL_PACKAGES" -> Str.get(R.string.for_the_plugin_to_install_apps)
            "PACKAGE_USAGE_STATS" -> Str.get(R.string.for_the_plugin_to_get_app_usage_stat)
            "ACCESSIBILITY" -> Str.get(R.string.for_the_plugin_s_accessibility_autom)
            "POST_NOTIFICATIONS" -> Str.get(R.string.for_the_plugin_to_send_notifications)
            Manifest.permission.VIBRATE -> Str.get(R.string.for_the_plugin_s_vibration_feedback)
            Manifest.permission.INTERNET -> Str.get(R.string.for_the_plugin_s_network_requests)
            Manifest.permission.ACCESS_NETWORK_STATE -> Str.get(R.string.for_checking_network_status)
            else -> Str.get(R.string.permissions_required_by_the_plugin)
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
        Logger.d(TAG, Str.get(R.string.permission_state_updated_pluginid_st, pluginId, state))
    }

    fun shouldShowPermissionDialog(context: Context, pluginId: String): Boolean {
        return getPermissionState(context, pluginId) == 0
    }

    fun getPermissionStateDescription(state: Int): String {
        return when (state) {
            0 -> Str.get(R.string.not_granted)
            1 -> Str.get(R.string.granted)
            2 -> Str.get(R.string.denied)
            else -> Str.get(R.string.unknown)
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
            Logger.e(TAG, Str.get(R.string.failed_to_open_settings), e)
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