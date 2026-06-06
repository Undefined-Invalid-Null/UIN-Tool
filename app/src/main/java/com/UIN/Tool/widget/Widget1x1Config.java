package com.UIN.Tool.widget;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;

/**
 * 1x1 小部件配置存储。
 * 每个小部件实例可绑定一个插件 ID，点击时直接启动该插件。
 */
public class Widget1x1Config {

    private static final String PREF_NAME = "uin_widget_1x1_prefs";

    public String pluginId;

    public Widget1x1Config() {
        this.pluginId = "";
    }

    public Widget1x1Config(String pluginId) {
        this.pluginId = pluginId != null ? pluginId : "";
    }

    public boolean hasPlugin() {
        return !pluginId.isEmpty();
    }

    public void save(Context context, int appWidgetId) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit()
                .putString("widget_1x1_" + appWidgetId + "_plugin", pluginId)
                .apply();
    }

    @Nullable
    public static Widget1x1Config load(Context context, int appWidgetId) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String id = prefs.getString("widget_1x1_" + appWidgetId + "_plugin", "");
        if (id.isEmpty()) {
            return null;
        }
        return new Widget1x1Config(id);
    }

    public static void delete(Context context, int appWidgetId) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit()
                .remove("widget_1x1_" + appWidgetId + "_plugin")
                .apply();
    }
}
