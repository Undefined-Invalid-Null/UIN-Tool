package com.UIN.Tool.utils

import com.UIN.Tool.R
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
            "android.permission.READ_EXTERNAL_STORAGE" -> Str.get(R.string.read_storage)
            "android.permission.WRITE_EXTERNAL_STORAGE" -> Str.get(R.string.write_storage)
            "MANAGE_EXTERNAL_STORAGE" -> Str.get(R.string.manage_all_files)
            "android.permission.INTERNET" -> Str.get(R.string.access_network)
            "android.permission.ACCESS_NETWORK_STATE" -> Str.get(R.string.get_network_status)
            "android.permission.ACCESS_WIFI_STATE" -> Str.get(R.string.get_wifi_status)
            "android.permission.CAMERA" -> Str.get(R.string.camera)
            "android.permission.RECORD_AUDIO" -> Str.get(R.string.microphone_2)
            "android.permission.ACCESS_FINE_LOCATION" -> Str.get(R.string.precise_location)
            "android.permission.ACCESS_COARSE_LOCATION" -> Str.get(R.string.approximate_location)
            "android.permission.ACCESS_BACKGROUND_LOCATION" -> Str.get(R.string.background_location)
            "android.permission.CALL_PHONE" -> Str.get(R.string.make_phone_calls)
            "android.permission.READ_PHONE_STATE" -> Str.get(R.string.read_phone_status)
            "android.permission.SEND_SMS" -> Str.get(R.string.send_sms)
            "android.permission.READ_SMS" -> Str.get(R.string.read_sms)
            "android.permission.RECEIVE_SMS" -> Str.get(R.string.receive_sms)
            "android.permission.READ_CONTACTS" -> Str.get(R.string.read_contacts)
            "android.permission.WRITE_CONTACTS" -> Str.get(R.string.write_contacts)
            "android.permission.READ_CALENDAR" -> Str.get(R.string.read_calendar)
            "android.permission.WRITE_CALENDAR" -> Str.get(R.string.write_calendar)
            "SYSTEM_ALERT_WINDOW" -> Str.get(R.string.overlay)
            "WRITE_SETTINGS" -> Str.get(R.string.modify_system_settings)
            "POST_NOTIFICATIONS" -> Str.get(R.string.notifications)
            "android.permission.VIBRATE" -> Str.get(R.string.vibrate)
            "android.permission.WAKE_LOCK" -> Str.get(R.string.wake_lock)
            "FLASHLIGHT" -> Str.get(R.string.flashlight)
            "android.permission.BLUETOOTH" -> Str.get(R.string.bluetooth)
            "android.permission.BLUETOOTH_ADMIN" -> Str.get(R.string.bluetooth_management)
            "android.permission.NFC" -> "NFC"
            "ACCESSIBILITY" -> Str.get(R.string.accessibility_permission)
            "REQUEST_INSTALL_PACKAGES" -> Str.get(R.string.install_unknown_apps)
            "PACKAGE_USAGE_STATS" -> Str.get(R.string.usage_access)
            "android.permission.KILL_BACKGROUND_PROCESSES" -> Str.get(R.string.kill_background_processes)
            "android.permission.READ_LOGS" -> Str.get(R.string.read_logs)
            "ROOT" -> Str.get(R.string.root_access)
            "SHIZUKU" -> "Shizuku"
            "DHIZUKU" -> "Dhizuku"
            else -> permission
        }
    }
}