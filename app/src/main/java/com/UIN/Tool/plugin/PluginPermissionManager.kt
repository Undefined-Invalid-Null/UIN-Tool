// app/src/main/java/com/UIN/Tool/plugin/PluginPermissionManager.kt
package com.UIN.Tool.plugin

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.UIN.Tool.data.local.PreferenceManager
import com.UIN.Tool.log.Logger
import com.UIN.Tool.utils.Constants
import com.UIN.Tool.utils.PermissionUtils

/**
 * 插件权限管理器
 */
object PluginPermissionManager {

    private const val TAG = "PluginPermissionManager"
    private const val PERMISSION_REQUEST_CODE_BASE = 2000
    
    private val SPECIAL_PERMISSIONS = setOf(
        "MANAGE_EXTERNAL_STORAGE",
        "SYSTEM_ALERT_WINDOW",
        "WRITE_SETTINGS",
        "REQUEST_INSTALL_PACKAGES",
        "PACKAGE_USAGE_STATS",
        "ACCESSIBILITY"
    )

    // ==================== 权限状态管理 ====================
    
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
    
    fun savePluginPermissionConfig(
        context: Context,
        pluginId: String,
        permission: String,
        granted: Boolean
    ) {
        PreferenceManager(context).savePluginPermission(pluginId, permission, granted)
        Logger.d(TAG, "保存权限配置: $pluginId -> $permission = $granted")
    }
    
    fun savePluginPermissionConfigs(
        context: Context,
        pluginId: String,
        permissions: Map<String, Boolean>
    ) {
        PreferenceManager(context).savePluginPermissions(pluginId, permissions)
        Logger.d(TAG, "保存权限配置: $pluginId (${permissions.size} 个权限)")
    }
    
    fun getPluginPermissionConfigs(context: Context, pluginId: String): Map<String, Boolean> {
        return PreferenceManager(context).getPluginPermissions(pluginId)
    }
    
    // ✅ 添加缺失的方法
    fun clearPluginPermissionConfigs(context: Context, pluginId: String) {
        PreferenceManager(context).clearPluginPermissions(pluginId)
        Logger.d(TAG, "清除权限配置: $pluginId")
    }

    // ==================== 权限请求 ====================
    
    fun requestPermissions(
        context: Context,
        pluginId: String,
        onResult: (Boolean) -> Unit
    ) {
        requestPermissions(context, pluginId, null, onResult)
    }
    
    fun requestPermissions(
        context: Context,
        pluginId: String,
        specificPermissions: List<String>? = null,
        onResult: (Boolean) -> Unit
    ) {
        Logger.enter(TAG, "requestPermissions")
        Logger.param(TAG, "pluginId", pluginId)
        
        val activity = context as? Activity
        if (activity == null) {
            Logger.e(TAG, "Context 不是 Activity，无法请求权限")
            onResult(false)
            return
        }
        
        val permissions = specificPermissions ?: getPluginDeclaredPermissions(context, pluginId)
        if (permissions.isEmpty()) {
            Logger.d(TAG, "插件没有声明任何权限")
            onResult(true)
            return
        }
        
        val normalPermissions = permissions.filter { !isSpecialPermission(it) }
        val specialPermissions = permissions.filter { isSpecialPermission(it) }
        
        val missingNormal = normalPermissions.filter { !checkPermission(context, it) }
        val missingSpecial = specialPermissions.filter { !checkPermission(context, it) }
        
        if (missingNormal.isEmpty() && missingSpecial.isEmpty()) {
            Logger.success(TAG, "所有权限已授予")
            onResult(true)
            return
        }
        
        if (missingNormal.isNotEmpty()) {
            requestNormalPermissions(activity, missingNormal) { allGranted ->
                if (allGranted && missingSpecial.isEmpty()) {
                    onResult(true)
                } else if (!allGranted) {
                    onResult(false)
                }
            }
        }
        
        if (missingSpecial.isNotEmpty()) {
            requestSpecialPermissions(activity, missingSpecial) { allGranted ->
                onResult(allGranted && missingNormal.all { checkPermission(context, it) })
            }
        } else if (missingNormal.isEmpty()) {
            onResult(true)
        }
    }
    
    private fun requestNormalPermissions(
        activity: Activity,
        permissions: List<String>,
        onResult: (Boolean) -> Unit
    ) {
        if (permissions.isEmpty()) {
            onResult(true)
            return
        }
        
        Logger.d(TAG, "请求普通权限: ${permissions.joinToString()}")
        
        val requestCode = generateRequestCode(permissions)
        PermissionRequestRegistry.register(
            requestCode,
            permissions,
            object : PermissionRequestCallback {
                override fun onResult(allGranted: Boolean, granted: Map<String, Boolean>) {
                    Logger.d(TAG, "权限请求结果: allGranted=$allGranted")
                    onResult(allGranted)
                }
            }
        )
        
        ActivityCompat.requestPermissions(
            activity,
            permissions.toTypedArray(),
            requestCode
        )
    }
    
    private fun requestSpecialPermissions(
        activity: Activity,
        permissions: List<String>,
        onResult: (Boolean) -> Unit
    ) {
        Logger.d(TAG, "请求特殊权限: ${permissions.joinToString()}")
        // 简化实现，直接打开应用设置
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            intent.data = Uri.parse("package:${activity.packageName}")
            activity.startActivity(intent)
            // 用户从设置返回后需要重新检查权限
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                val allGranted = permissions.all { checkPermission(activity, it) }
                onResult(allGranted)
            }, 3000)
        } catch (e: Exception) {
            Logger.e(TAG, "打开设置失败: ${e.message}", e)
            onResult(false)
        }
    }
    
    // ==================== 权限分组请求 ====================
    
    fun requestPermissionsByGroups(
        context: Context,
        pluginId: String,
        onProgress: ((String, Int, Int) -> Unit)? = null,
        onComplete: (Boolean) -> Unit
    ) {
        Logger.enter(TAG, "requestPermissionsByGroups")
        
        val activity = context as? Activity
        if (activity == null) {
            Logger.e(TAG, "Context 不是 Activity")
            onComplete(false)
            return
        }
        
        val permissions = getPluginDeclaredPermissions(context, pluginId)
        if (permissions.isEmpty()) {
            onComplete(true)
            return
        }
        
        val groupedPermissions = groupPermissions(permissions)
        val totalGroups = groupedPermissions.size
        
        if (totalGroups == 0) {
            onComplete(true)
            return
        }
        
        var completedGroups = 0
        var allGranted = true
        
        groupedPermissions.forEach { (groupName, permList) ->
            val missing = permList.filter { !checkPermission(context, it) }
            if (missing.isEmpty()) {
                completedGroups++
                onProgress?.invoke("$groupName (已授予)", completedGroups, totalGroups)
                if (completedGroups == totalGroups) {
                    onComplete(allGranted)
                }
                return@forEach
            }
            
            onProgress?.invoke("正在请求 $groupName...", completedGroups, totalGroups)
            
            requestPermissions(
                context,
                pluginId,
                missing
            ) { granted ->
                completedGroups++
                if (!granted) allGranted = false
                onProgress?.invoke(
                    if (granted) "$groupName (已授予)" else "$groupName (已拒绝)",
                    completedGroups,
                    totalGroups
                )
                if (completedGroups == totalGroups) {
                    onComplete(allGranted)
                }
            }
        }
    }
    
    private fun groupPermissions(permissions: List<String>): Map<String, List<String>> {
        val result = mutableMapOf<String, MutableList<String>>()
        
        permissions.forEach { permission ->
            val groupName = findPermissionGroup(permission)
            result.getOrPut(groupName) { mutableListOf() }.add(permission)
        }
        
        return result
    }
    
    private fun findPermissionGroup(permission: String): String {
        return when {
            permission.contains("STORAGE") || permission.contains("EXTERNAL") -> "存储权限"
            permission.contains("CAMERA") -> "相机权限"
            permission.contains("RECORD_AUDIO") -> "麦克风权限"
            permission.contains("LOCATION") -> "位置权限"
            permission.contains("CONTACTS") -> "联系人权限"
            permission.contains("PHONE") || permission.contains("CALL") -> "电话权限"
            permission.contains("SMS") || permission.contains("SEND") -> "短信权限"
            permission.contains("CALENDAR") -> "日历权限"
            isSpecialPermission(permission) -> "特殊权限"
            else -> "其他权限"
        }
    }
    
    // ==================== 权限说明 ====================
    
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
            permission.contains("RECORD_AUDIO") -> android.R.drawable.ic_menu_manage // 替换 ic_menu_mic
            permission.contains("LOCATION") -> android.R.drawable.ic_menu_mylocation
            permission.contains("CONTACTS") -> android.R.drawable.ic_menu_manage
            permission.contains("PHONE") || permission.contains("CALL") -> android.R.drawable.ic_menu_call
            permission.contains("SMS") -> android.R.drawable.ic_menu_send
            permission.contains("CALENDAR") -> android.R.drawable.ic_menu_agenda
            isSpecialPermission(permission) -> android.R.drawable.ic_menu_preferences // 替换 ic_menu_settings
            else -> android.R.drawable.ic_menu_info_details
        }
    }
    
    fun isSpecialPermission(permission: String): Boolean {
        return permission in SPECIAL_PERMISSIONS ||
                PermissionUtils.isSpecialPermission(permission)
    }
    
    // ==================== 权限请求回调注册 ====================
    
    fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        val callback = PermissionRequestRegistry.getCallback(requestCode)
        if (callback != null) {
            val resultMap = permissions.mapIndexed { index, permission ->
                permission to (grantResults.getOrNull(index) == PackageManager.PERMISSION_GRANTED)
            }.toMap()
            
            val allGranted = resultMap.values.all { it }
            callback.onResult(allGranted, resultMap)
            PermissionRequestRegistry.removeCallback(requestCode)
        }
    }
    
    private fun generateRequestCode(permissions: List<String>): Int {
        return PERMISSION_REQUEST_CODE_BASE + (permissions.hashCode() and 0x7FFFFFFF) % 1000
    }
    
    // ==================== 权限引导 ====================
    
    fun showPermissionGuidance(
        context: Context,
        pluginId: String,
        onReRequest: () -> Unit,
        onOpenSettings: () -> Unit
    ) {
        val missingPermissions = getMissingPermissions(context, pluginId)
        if (missingPermissions.isEmpty()) {
            onReRequest()
            return
        }
        
        val activity = context as? Activity
        if (activity == null) {
            Logger.e(TAG, "Context 不是 Activity")
            return
        }
        
        val normal = missingPermissions.filter { !isSpecialPermission(it) }
        val special = missingPermissions.filter { isSpecialPermission(it) }
        
        val message = buildString {
            append("插件需要以下权限才能正常运行：\n\n")
            missingPermissions.forEach { permission ->
                append("• ${getPermissionDisplayName(permission)}\n")
                append("  ${getPermissionDescription(permission)}\n")
            }
            if (normal.isNotEmpty()) {
                append("\n普通权限可以在此直接授权。")
            }
            if (special.isNotEmpty()) {
                append("\n\n⚠️ 特殊权限需要在系统设置中手动开启。")
            }
        }
        
        android.app.AlertDialog.Builder(activity)
            .setTitle("权限说明")
            .setMessage(message)
            .setPositiveButton("授权") { _, _ ->
                onReRequest()
            }
            .setNegativeButton("取消", null)
            .setNeutralButton("去设置") { _, _ ->
                if (special.isNotEmpty()) {
                    onOpenSettings()
                } else {
                    onReRequest()
                }
            }
            .show()
    }
    
    // ==================== 权限状态查询 ====================
    
    fun getPermissionStatusSummary(context: Context, pluginId: String): PermissionStatusSummary {
        val permissions = getPluginDeclaredPermissions(context, pluginId)
        if (permissions.isEmpty()) {
            return PermissionStatusSummary(
                total = 0,
                granted = 0,
                denied = 0,
                isAllGranted = true,
                hasMissing = false
            )
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
    
    data class PermissionStatusSummary(
        val total: Int,
        val granted: Int,
        val denied: Int,
        val isAllGranted: Boolean,
        val hasMissing: Boolean
    )
}

// ==================== 权限请求回调注册 ====================

interface PermissionRequestCallback {
    fun onResult(allGranted: Boolean, granted: Map<String, Boolean>)
}

object PermissionRequestRegistry {
    private val callbacks = mutableMapOf<Int, PermissionRequestCallback>()
    
    fun register(requestCode: Int, permissions: List<String>, callback: PermissionRequestCallback) {
        callbacks[requestCode] = callback
        Logger.d("PermissionRegistry", "注册权限请求: $requestCode (${permissions.size} 个权限)")
    }
    
    fun getCallback(requestCode: Int): PermissionRequestCallback? {
        return callbacks[requestCode]
    }
    
    fun removeCallback(requestCode: Int) {
        callbacks.remove(requestCode)
        Logger.d("PermissionRegistry", "移除权限请求回调: $requestCode")
    }
    
    fun clear() {
        callbacks.clear()
    }
}