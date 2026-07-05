package com.UIN.Tool.utils

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher
import androidx.core.content.ContextCompat

object PermissionUtils {

    fun hasPermission(context: Context, permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) ==
               android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    fun hasSpecialPermission(context: Context, permission: String): Boolean {
        return when (permission) {
            "MANAGE_EXTERNAL_STORAGE" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    android.os.Environment.isExternalStorageManager()
                } else true
            }
            "SYSTEM_ALERT_WINDOW" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    Settings.canDrawOverlays(context)
                } else true
            }
            "WRITE_SETTINGS" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    Settings.System.canWrite(context)
                } else true
            }
            "REQUEST_INSTALL_PACKAGES" -> {
                context.packageManager.canRequestPackageInstalls()
            }
            "PACKAGE_USAGE_STATS" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    try {
                        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
                        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            appOps.unsafeCheckOpNoThrow(
                                android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                                android.os.Process.myUid(),
                                context.packageName
                            )
                        } else {
                            appOps.checkOpNoThrow(
                                android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                                android.os.Process.myUid(),
                                context.packageName
                            )
                        }
                        mode == android.app.AppOpsManager.MODE_ALLOWED
                    } catch (e: Exception) {
                        false
                    }
                } else true
            }
            "ACCESSIBILITY" -> {
                try {
                    val enabledServices = Settings.Secure.getString(
                        context.contentResolver,
                        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                    )
                    enabledServices?.contains(context.packageName) == true
                } catch (e: Exception) {
                    false
                }
            }
            else -> false
        }
    }

    fun isSpecialPermission(permission: String): Boolean {
        return permission in setOf(
            "MANAGE_EXTERNAL_STORAGE",
            "SYSTEM_ALERT_WINDOW",
            "WRITE_SETTINGS",
            "REQUEST_INSTALL_PACKAGES",
            "PACKAGE_USAGE_STATS",
            "ACCESSIBILITY"
        )
    }

    fun requestSpecialPermission(
        activity: Activity,
        permission: String,
        launcher: ActivityResultLauncher<Intent>
    ) {
        val intent = when (permission) {
            "WRITE_SETTINGS" -> {
                Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                    data = Uri.parse("package:${activity.packageName}")
                }
            }
            "SYSTEM_ALERT_WINDOW" -> {
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                    data = Uri.parse("package:${activity.packageName}")
                }
            }
            "MANAGE_EXTERNAL_STORAGE" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                        data = Uri.parse("package:${activity.packageName}")
                    }
                } else null
            }
            "REQUEST_INSTALL_PACKAGES" -> {
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${activity.packageName}")
                }
            }
            "PACKAGE_USAGE_STATS" -> {
                Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            }
            "ACCESSIBILITY" -> {
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            }
            else -> null
        }

        if (intent != null) {
            launcher.launch(intent)
        }
    }

    fun getPermissionDisplayName(permission: String): String {
        return when (permission) {
            "android.permission.READ_EXTERNAL_STORAGE" -> "读取存储"
            "android.permission.WRITE_EXTERNAL_STORAGE" -> "写入存储"
            "MANAGE_EXTERNAL_STORAGE" -> "管理所有文件"
            "android.permission.INTERNET" -> "访问网络"
            "android.permission.ACCESS_NETWORK_STATE" -> "获取网络状态"
            "android.permission.ACCESS_WIFI_STATE" -> "获取WiFi状态"
            "android.permission.CAMERA" -> "相机"
            "android.permission.RECORD_AUDIO" -> "录音"
            "android.permission.ACCESS_FINE_LOCATION" -> "精确位置"
            "android.permission.ACCESS_COARSE_LOCATION" -> "粗略位置"
            "android.permission.ACCESS_BACKGROUND_LOCATION" -> "后台位置"
            "android.permission.CALL_PHONE" -> "拨打电话"
            "android.permission.READ_PHONE_STATE" -> "读取手机状态"
            "android.permission.SEND_SMS" -> "发送短信"
            "android.permission.READ_SMS" -> "读取短信"
            "android.permission.RECEIVE_SMS" -> "接收短信"
            "android.permission.READ_CONTACTS" -> "读取联系人"
            "android.permission.WRITE_CONTACTS" -> "写入联系人"
            "android.permission.READ_CALENDAR" -> "读取日历"
            "android.permission.WRITE_CALENDAR" -> "写入日历"
            "SYSTEM_ALERT_WINDOW" -> "悬浮窗"
            "WRITE_SETTINGS" -> "修改系统设置"
            "POST_NOTIFICATIONS" -> "通知"
            "android.permission.VIBRATE" -> "震动"
            "android.permission.WAKE_LOCK" -> "唤醒锁"
            "FLASHLIGHT" -> "闪光灯"
            "android.permission.BLUETOOTH" -> "蓝牙"
            "android.permission.BLUETOOTH_ADMIN" -> "蓝牙管理"
            "android.permission.NFC" -> "NFC"
            "ACCESSIBILITY" -> "无障碍权限"
            "REQUEST_INSTALL_PACKAGES" -> "安装未知应用"
            "PACKAGE_USAGE_STATS" -> "使用情况访问"
            "android.permission.KILL_BACKGROUND_PROCESSES" -> "结束后台进程"
            "android.permission.READ_LOGS" -> "读取日志"
            "ROOT" -> "Root权限"
            "SHIZUKU" -> "Shizuku"
            "DHIZUKU" -> "Dhizuku"
            else -> permission
        }
    }
}