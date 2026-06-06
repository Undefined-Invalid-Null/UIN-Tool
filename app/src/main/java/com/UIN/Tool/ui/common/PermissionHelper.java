// app/src/main/java/com/UIN/Tool/ui/common/PermissionHelper.java
package com.UIN.Tool.ui.common;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import androidx.activity.result.ActivityResultLauncher;
import androidx.core.content.ContextCompat;

import com.UIN.Tool.R;

/**
 * 权限管理辅助类
 * 统一处理权限请求和检查
 */
public class PermissionHelper {

    /**
     * 检查普通权限是否已授予
     */
    public static boolean hasPermission(Context context, String permission) {
        return ContextCompat.checkSelfPermission(context, permission)
                == android.content.pm.PackageManager.PERMISSION_GRANTED;
    }

    /**
     * 检查特殊权限是否已授予
     */
    public static boolean hasSpecialPermission(Context context, String permission) {
        switch (permission) {
            case "MANAGE_EXTERNAL_STORAGE":
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    return android.os.Environment.isExternalStorageManager();
                }
                return true;
            case "SYSTEM_ALERT_WINDOW":
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    return Settings.canDrawOverlays(context);
                }
                return true;
            case "WRITE_SETTINGS":
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    return Settings.System.canWrite(context);
                }
                return true;
            case "REQUEST_INSTALL_PACKAGES":
                return context.getPackageManager().canRequestPackageInstalls();
            default:
                return false;
        }
    }

    /**
     * 请求特殊权限
     */
    public static void requestSpecialPermission(Activity activity, String permission,
                                                 ActivityResultLauncher<Intent> launcher) {
        Intent intent = null;
        switch (permission) {
            case "WRITE_SETTINGS":
                intent = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS);
                intent.setData(Uri.parse("package:" + activity.getPackageName()));
                break;
            case "SYSTEM_ALERT_WINDOW":
                intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
                intent.setData(Uri.parse("package:" + activity.getPackageName()));
                break;
            case "MANAGE_EXTERNAL_STORAGE":
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                    intent.setData(Uri.parse("package:" + activity.getPackageName()));
                }
                break;
            case "REQUEST_INSTALL_PACKAGES":
                intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
                intent.setData(Uri.parse("package:" + activity.getPackageName()));
                break;
            case "PACKAGE_USAGE_STATS":
                intent = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
                break;
            case "ACCESSIBILITY":
                intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                break;
        }
        if (intent != null && launcher != null) {
            launcher.launch(intent);
        }
    }

    /**
     * 获取权限显示名称
     */
    public static String getPermissionDisplayName(Context context, String permission) {
        switch (permission) {
            case "android.permission.READ_EXTERNAL_STORAGE":
                return context.getString(R.string.permission_storage_read);
            case "android.permission.WRITE_EXTERNAL_STORAGE":
                return context.getString(R.string.permission_storage_write);
            case "MANAGE_EXTERNAL_STORAGE":
                return context.getString(R.string.permission_storage_manage);
            case "android.permission.INTERNET":
                return context.getString(R.string.permission_network);
            case "android.permission.ACCESS_NETWORK_STATE":
                return context.getString(R.string.permission_network_state);
            case "android.permission.ACCESS_WIFI_STATE":
                return context.getString(R.string.permission_wifi_state);
            case "android.permission.CAMERA":
                return context.getString(R.string.permission_camera);
            case "android.permission.RECORD_AUDIO":
                return context.getString(R.string.permission_microphone);
            case "android.permission.ACCESS_FINE_LOCATION":
                return context.getString(R.string.permission_location_fine);
            case "android.permission.ACCESS_COARSE_LOCATION":
                return context.getString(R.string.permission_location_coarse);
            case "android.permission.ACCESS_BACKGROUND_LOCATION":
                return context.getString(R.string.permission_location_background);
            case "android.permission.CALL_PHONE":
                return context.getString(R.string.permission_phone_call);
            case "android.permission.READ_PHONE_STATE":
                return context.getString(R.string.permission_phone_state);
            case "android.permission.SEND_SMS":
                return context.getString(R.string.permission_sms_send);
            case "android.permission.READ_SMS":
                return context.getString(R.string.permission_sms_read);
            case "android.permission.RECEIVE_SMS":
                return context.getString(R.string.permission_sms_receive);
            case "android.permission.READ_CONTACTS":
                return context.getString(R.string.permission_contacts_read);
            case "android.permission.WRITE_CONTACTS":
                return context.getString(R.string.permission_contacts_write);
            case "android.permission.READ_CALENDAR":
                return context.getString(R.string.permission_calendar_read);
            case "android.permission.WRITE_CALENDAR":
                return context.getString(R.string.permission_calendar_write);
            case "SYSTEM_ALERT_WINDOW":
                return context.getString(R.string.permission_overlay);
            case "WRITE_SETTINGS":
                return context.getString(R.string.permission_write_settings);
            case "POST_NOTIFICATIONS":
                return context.getString(R.string.permission_notification);
            case "android.permission.VIBRATE":
                return context.getString(R.string.permission_vibrate);
            case "android.permission.WAKE_LOCK":
                return context.getString(R.string.permission_wake_lock);
            case "FLASHLIGHT":
                return context.getString(R.string.permission_flashlight);
            case "android.permission.BLUETOOTH":
                return context.getString(R.string.permission_bluetooth);
            case "android.permission.BLUETOOTH_ADMIN":
                return context.getString(R.string.permission_bluetooth_admin);
            case "android.permission.NFC":
                return context.getString(R.string.permission_nfc);
            case "ACCESSIBILITY":
                return context.getString(R.string.permission_accessibility);
            case "REQUEST_INSTALL_PACKAGES":
                return context.getString(R.string.permission_install_unknown);
            case "PACKAGE_USAGE_STATS":
                return context.getString(R.string.permission_usage_stats);
            case "android.permission.KILL_BACKGROUND_PROCESSES":
                return context.getString(R.string.permission_kill_background);
            case "ROOT":
                return context.getString(R.string.permission_root);
            case "SHIZUKU":
                return context.getString(R.string.permission_shizuku);
            case "DHIZUKU":
                return context.getString(R.string.permission_dhizuku);
            default:
                return permission;
        }
    }
}