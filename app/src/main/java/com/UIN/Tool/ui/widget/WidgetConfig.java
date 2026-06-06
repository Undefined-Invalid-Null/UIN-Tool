package com.UIN.Tool.widget;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;

public class WidgetConfig {

    private static final String PREF_NAME = "uin_widget_prefs";

    public String pluginId1;
    public String pluginId2;
    public String pluginId3;

    public WidgetConfig() {
        this.pluginId1 = "";
        this.pluginId2 = "";
        this.pluginId3 = "";
    }

    public WidgetConfig(String id1, String id2, String id3) {
        this.pluginId1 = id1 != null ? id1 : "";
        this.pluginId2 = id2 != null ? id2 : "";
        this.pluginId3 = id3 != null ? id3 : "";
    }

    public boolean hasSelectedPlugins() {
        return !pluginId1.isEmpty() || !pluginId2.isEmpty() || !pluginId3.isEmpty();
    }

    public void save(Context context, int appWidgetId) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String key = "widget_" + appWidgetId;
        prefs.edit()
                .putString(key + "_1", pluginId1)
                .putString(key + "_2", pluginId2)
                .putString(key + "_3", pluginId3)
                .apply();
    }

    @Nullable
    public static WidgetConfig load(Context context, int appWidgetId) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String key = "widget_" + appWidgetId;

        String id1 = prefs.getString(key + "_1", "");
        String id2 = prefs.getString(key + "_2", "");
        String id3 = prefs.getString(key + "_3", "");

        if (id1.isEmpty() && id2.isEmpty() && id3.isEmpty()) {
            return null;
        }

        return new WidgetConfig(id1, id2, id3);
    }

    public static void delete(Context context, int appWidgetId) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String key = "widget_" + appWidgetId;
        prefs.edit()
                .remove(key + "_1")
                .remove(key + "_2")
                .remove(key + "_3")
                .apply();
    }
}