package com.UIN.Tool.plugin;

import org.json.JSONObject;

public class PluginInfo {
    public String pluginId;
    public int version;
    public String versionName;
    public int minHostVersion;
    public String name;
    public String author;
    public String description;
    public String icon;
    public String mainClass;
    public String updateUrl;
    public int apiLevel;
    public String category;      // 插件分类
    public String signature;     // 插件签名
    public String uiType;        // UI 类型: "native" 或 "web"
    public String entry;         // Web 插件的入口文件路径，如 "web/index.html"

    public static PluginInfo fromJson(String jsonString) {
        try {
            JSONObject obj = new JSONObject(jsonString);
            PluginInfo info = new PluginInfo();
            info.pluginId = obj.optString("pluginId");
            info.version = obj.optInt("version", 1);
            info.versionName = obj.optString("versionName", "1.0");
            info.minHostVersion = obj.optInt("minHostVersion", 1);
            info.name = obj.optString("name");
            info.author = obj.optString("author", "");
            info.description = obj.optString("description", "");
            info.icon = obj.optString("icon", "icon.png");
            info.mainClass = obj.optString("mainClass");
            info.updateUrl = obj.optString("updateUrl", "");
            info.apiLevel = obj.optInt("apiLevel", 21);
            info.category = obj.optString("category", "未分类");
            info.signature = obj.optString("signature", "");
            info.uiType = obj.optString("uiType", "native");
            info.entry = obj.optString("entry", "web/index.html");
            return info;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public String toJson() {
        try {
            JSONObject obj = new JSONObject();
            obj.put("pluginId", pluginId);
            obj.put("version", version);
            obj.put("versionName", versionName);
            obj.put("minHostVersion", minHostVersion);
            obj.put("name", name);
            obj.put("author", author);
            obj.put("description", description);
            obj.put("icon", icon);
            obj.put("mainClass", mainClass);
            obj.put("updateUrl", updateUrl);
            obj.put("apiLevel", apiLevel);
            obj.put("category", category != null ? category : "未分类");
            obj.put("signature", signature != null ? signature : "");
            obj.put("uiType", uiType != null ? uiType : "native");
            obj.put("entry", entry != null ? entry : "web/index.html");
            return obj.toString();
        } catch (Exception e) {
            return "{}";
        }
    }
}