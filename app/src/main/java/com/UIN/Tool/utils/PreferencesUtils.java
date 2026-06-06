package com.UIN.Tool.utils;

import android.content.Context;
import android.content.SharedPreferences;

public class PreferencesUtils {
    private static final String PREF_NAME = "uin_tool_prefs";
    private static final String KEY_VIEW_MODE = "view_mode";
    private static final String KEY_WORK_FOLDER = "work_folder";
    private static final String KEY_PLUGIN_SIGNATURES = "plugin_signatures";

    private static SharedPreferences getPrefs(Context context) {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }
    
    private static SharedPreferences getSignaturePrefs(Context context) {
        return context.getSharedPreferences(KEY_PLUGIN_SIGNATURES, Context.MODE_PRIVATE);
    }

    public static void setViewMode(Context context, String mode) {
        getPrefs(context).edit().putString(KEY_VIEW_MODE, mode).apply();
    }

    public static String getViewMode(Context context) {
        return getPrefs(context).getString(KEY_VIEW_MODE, "list");
    }

    public static void setWorkFolder(Context context, String path) {
        getPrefs(context).edit().putString(KEY_WORK_FOLDER, path).apply();
    }

    public static String getWorkFolder(Context context) {
        String folder = getPrefs(context).getString(KEY_WORK_FOLDER, null);
        if (folder == null || folder.isEmpty()) {
            folder = "/storage/emulated/0/UIN_Tool";
        }
        return folder;
    }
    
    /**
     * 保存插件签名
     */
    public static void savePluginSignature(Context context, String pluginName, String signature) {
        getSignaturePrefs(context).edit().putString(pluginName, signature).apply();
    }
    
    /**
     * 获取插件签名
     */
    public static String getPluginSignature(Context context, String pluginName) {
        return getSignaturePrefs(context).getString(pluginName, null);
    }
    
    /**
     * 移除插件签名
     */
    public static void removePluginSignature(Context context, String pluginName) {
        getSignaturePrefs(context).edit().remove(pluginName).apply();
    }
}