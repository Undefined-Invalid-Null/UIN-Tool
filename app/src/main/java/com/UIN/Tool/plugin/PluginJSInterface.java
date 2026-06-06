package com.UIN.Tool.plugin;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Vibrator;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.webkit.JavascriptInterface;
import android.widget.Toast;

import com.UIN.Tool.utils.LogUtils;

import org.json.JSONObject;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class PluginJSInterface {
    
    private final Context context;
    private final String pluginId;
    private final PluginInfo pluginInfo;
    
    public PluginJSInterface(Context context, String pluginId, PluginInfo pluginInfo) {
        this.context = context;
        this.pluginId = pluginId;
        this.pluginInfo = pluginInfo;
    }
    
    // ==================== 基础功能 ====================
    
    @JavascriptInterface
    public void callHost(String action, String data) {
        LogUtils.i("PluginJSInterface", "JS 调用: " + action + " -> " + data);
        
        switch (action) {
            case "toast":
                showToast(data);
                break;
            case "finish":
                closePlugin();
                break;
            case "log":
                LogUtils.i("WebPlugin[" + pluginId + "]", data);
                break;
            case "alert":
                showAlert(data);
                break;
            case "confirm":
                showConfirm(data);
                break;
            case "vibrate":
                vibrate(data);
                break;
            case "copy":
                copyToClipboard(data);
                break;
            case "openUrl":
                openUrl(data);
                break;
            case "share":
                share(data);
                break;
            default:
                LogUtils.w("PluginJSInterface", "未知调用: " + action);
        }
    }
    
    @JavascriptInterface
    public void callPlugin(String method, String params) {
        LogUtils.i("PluginJSInterface", "调用插件方法: " + method + " -> " + params);
    }
    
    // ==================== 获取信息 ====================
    
    @JavascriptInterface
    public String getPluginInfo() {
        if (pluginInfo != null) {
            return pluginInfo.toJson();
        }
        return "{}";
    }
    
    @JavascriptInterface
    public String getAppVersion() {
        try {
            return context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0)
                    .versionName;
        } catch (Exception e) {
            return "unknown";
        }
    }
    
    @JavascriptInterface
    public String getDeviceInfo() {
        JSONObject info = new JSONObject();
        try {
            info.put("android", Build.VERSION.RELEASE);
            info.put("api", Build.VERSION.SDK_INT);
            info.put("device", Build.MODEL);
            info.put("manufacturer", Build.MANUFACTURER);
            info.put("brand", Build.BRAND);
            info.put("product", Build.PRODUCT);
            info.put("board", Build.BOARD);
            info.put("hardware", Build.HARDWARE);
            info.put("fingerprint", Build.FINGERPRINT);
            
            DisplayMetrics dm = context.getResources().getDisplayMetrics();
            info.put("screenWidth", dm.widthPixels);
            info.put("screenHeight", dm.heightPixels);
            info.put("screenDensity", dm.density);
            info.put("screenDensityDpi", dm.densityDpi);
            
        } catch (Exception e) {
            e.printStackTrace();
        }
        return info.toString();
    }
    
    @JavascriptInterface
    public String getNetworkInfo() {
        JSONObject info = new JSONObject();
        try {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
            boolean isConnected = activeNetwork != null && activeNetwork.isConnected();
            info.put("connected", isConnected);
            if (isConnected) {
                info.put("type", activeNetwork.getTypeName());
                info.put("subtype", activeNetwork.getSubtypeName());
                info.put("isWifi", activeNetwork.getType() == ConnectivityManager.TYPE_WIFI);
                info.put("isMobile", activeNetwork.getType() == ConnectivityManager.TYPE_MOBILE);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return info.toString();
    }
    
    @JavascriptInterface
    public String getCurrentTime() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        return sdf.format(new Date());
    }
    
    // ==================== UI 操作 ====================
    
    @JavascriptInterface
    public void setTitle(String title) {
        LogUtils.i("PluginJSInterface", "设置标题: " + title);
        if (context instanceof Activity) {
            Activity activity = (Activity) context;
            activity.setTitle(title);
        }
    }
    
    @JavascriptInterface
    public void setFullscreen(boolean fullscreen) {
        if (context instanceof Activity) {
            Activity activity = (Activity) context;
            if (fullscreen) {
                activity.getWindow().getDecorView().setSystemUiVisibility(
                    android.view.View.SYSTEM_UI_FLAG_FULLSCREEN |
                    android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                    android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                );
            } else {
                activity.getWindow().getDecorView().setSystemUiVisibility(android.view.View.SYSTEM_UI_FLAG_VISIBLE);
            }
        }
    }
    
    // ==================== 存储操作 ====================
    
    @JavascriptInterface
    public void setStorage(String key, String value) {
        context.getSharedPreferences("web_plugin_" + pluginId, Context.MODE_PRIVATE)
                .edit()
                .putString(key, value)
                .apply();
        LogUtils.i("PluginJSInterface", "存储数据: " + key + " = " + value);
    }
    
    @JavascriptInterface
    public String getStorage(String key) {
        String value = context.getSharedPreferences("web_plugin_" + pluginId, Context.MODE_PRIVATE)
                .getString(key, "");
        LogUtils.i("PluginJSInterface", "读取数据: " + key + " = " + value);
        return value;
    }
    
    @JavascriptInterface
    public void removeStorage(String key) {
        context.getSharedPreferences("web_plugin_" + pluginId, Context.MODE_PRIVATE)
                .edit()
                .remove(key)
                .apply();
        LogUtils.i("PluginJSInterface", "删除数据: " + key);
    }
    
    @JavascriptInterface
    public void clearStorage() {
        context.getSharedPreferences("web_plugin_" + pluginId, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .apply();
        LogUtils.i("PluginJSInterface", "清空所有存储数据");
    }
    
    // ==================== 文件操作 ====================
    
    @JavascriptInterface
    public String getPluginDir() {
        File pluginDir = new File("/storage/emulated/0/UIN_Tool/" + pluginId);
        if (!pluginDir.exists()) {
            pluginDir.mkdirs();
        }
        LogUtils.i("PluginJSInterface", "插件目录: " + pluginDir.getAbsolutePath());
        return pluginDir.getAbsolutePath();
    }
    
    @JavascriptInterface
    public boolean fileExists(String path) {
        File file = new File(path);
        return file.exists();
    }
    
    // ==================== 系统操作 ====================
    
    @JavascriptInterface
    public void openSettings() {
        Intent intent = new Intent(Settings.ACTION_SETTINGS);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
        LogUtils.i("PluginJSInterface", "打开系统设置");
    }
    
    @JavascriptInterface
    public void openAppSettings() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.parse("package:" + context.getPackageName()));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
        LogUtils.i("PluginJSInterface", "打开应用设置");
    }
    
    @JavascriptInterface
    public boolean checkPermission(String permission) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }
    
    // ==================== 粘贴功能 ====================
    
    @JavascriptInterface
    public String paste() {
        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null && clipboard.hasPrimaryClip()) {
            ClipData.Item item = clipboard.getPrimaryClip().getItemAt(0);
            if (item != null) {
                String text = item.getText().toString();
                LogUtils.i("PluginJSInterface", "粘贴文本: " + (text.length() > 50 ? text.substring(0, 50) + "..." : text));
                return text;
            }
        }
        return "";
    }
    
    // ==================== 私有方法 ====================
    
    private void showToast(String message) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
        LogUtils.i("PluginJSInterface", "显示Toast: " + message);
    }
    
    private void closePlugin() {
        if (context instanceof Activity) {
            ((Activity) context).finish();
            LogUtils.i("PluginJSInterface", "关闭插件");
        }
    }
    
    private void showAlert(String message) {
        new AlertDialog.Builder(context)
                .setTitle("提示")
                .setMessage(message)
                .setPositiveButton("确定", null)
                .show();
        LogUtils.i("PluginJSInterface", "显示弹窗: " + message);
    }
    
    private void showConfirm(String message) {
        new AlertDialog.Builder(context)
                .setTitle("确认")
                .setMessage(message)
                .setPositiveButton("确定", (dialog, which) -> {})
                .setNegativeButton("取消", (dialog, which) -> {})
                .show();
        LogUtils.i("PluginJSInterface", "显示确认框: " + message);
    }
    
    private void vibrate(String durationMs) {
        try {
            Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator != null) {
                long duration = Long.parseLong(durationMs);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(android.os.VibrationEffect.createOneShot(duration, android.os.VibrationEffect.DEFAULT_AMPLITUDE));
                } else {
                    vibrator.vibrate(duration);
                }
                LogUtils.i("PluginJSInterface", "震动: " + duration + "ms");
            }
        } catch (Exception e) {
            LogUtils.e("PluginJSInterface", "震动失败: " + e.getMessage());
        }
    }
    
    private void copyToClipboard(String text) {
        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("plugin_text", text);
        clipboard.setPrimaryClip(clip);
        showToast("已复制到剪贴板");
        LogUtils.i("PluginJSInterface", "复制到剪贴板: " + text);
    }
    
    private void openUrl(String url) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            LogUtils.i("PluginJSInterface", "打开链接: " + url);
        } catch (Exception e) {
            showToast("无法打开链接");
            LogUtils.e("PluginJSInterface", "打开链接失败: " + e.getMessage());
        }
    }
    
    private void share(String text) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, text);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(Intent.createChooser(intent, "分享"));
        LogUtils.i("PluginJSInterface", "分享: " + text);
    }
}