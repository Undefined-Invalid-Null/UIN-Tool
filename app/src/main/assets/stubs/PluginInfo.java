package com.UIN.Tool.domain.model;

import org.json.JSONObject;
import java.util.List;
import java.util.ArrayList;

/**
 * 插件信息数据模型 - Stub 版本
 * 对应: domain/model/PluginInfo.kt
 */
public class PluginInfo {

    private String pluginId = "";
    private int version = 1;
    private String versionName = "1.0.0";
    private int minHostVersion = 1;
    private String name = "";
    private String author = "";
    private String description = "";
    private String icon = "icon.png";
    private String mainClass = "";
    private String updateUrl = "";
    private int apiLevel = 21;
    private String category = "未分类";
    private String signature = "";
    private String uiType = "native";
    private String entry = "web/index.html";
    private List<String> permissions = new ArrayList<>();
    private List<String> dependencies = new ArrayList<>();

    public PluginInfo() {}

    public String toJson() {
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
        obj.put("category", category);
        obj.put("signature", signature);
        obj.put("uiType", uiType);
        obj.put("entry", entry);
        obj.put("permissions", String.join(",", permissions));
        obj.put("dependencies", String.join(",", dependencies));
        return obj.toString();
    }

    public static PluginInfo fromJson(String json) {
        try {
            JSONObject obj = new JSONObject(json);
            PluginInfo info = new PluginInfo();
            info.pluginId = obj.optString("pluginId", "");
            info.version = obj.optInt("version", 1);
            info.versionName = obj.optString("versionName", "1.0.0");
            info.minHostVersion = obj.optInt("minHostVersion", 1);
            info.name = obj.optString("name", "");
            info.author = obj.optString("author", "");
            info.description = obj.optString("description", "");
            info.icon = obj.optString("icon", "icon.png");
            info.mainClass = obj.optString("mainClass", "");
            info.updateUrl = obj.optString("updateUrl", "");
            info.apiLevel = obj.optInt("apiLevel", 21);
            info.category = obj.optString("category", "未分类");
            info.signature = obj.optString("signature", "");
            info.uiType = obj.optString("uiType", "native");
            info.entry = obj.optString("entry", "web/index.html");
            return info;
        } catch (Exception e) {
            return null;
        }
    }

    public boolean isWebPlugin() { return "web".equals(uiType); }
    public boolean isNativePlugin() { return "native".equals(uiType); }

    // ==================== Getters/Setters ====================
    public String getPluginId() { return pluginId; }
    public void setPluginId(String pluginId) { this.pluginId = pluginId; }
    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }
    public String getVersionName() { return versionName; }
    public void setVersionName(String versionName) { this.versionName = versionName; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getMainClass() { return mainClass; }
    public void setMainClass(String mainClass) { this.mainClass = mainClass; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getUiType() { return uiType; }
    public void setUiType(String uiType) { this.uiType = uiType; }
    public String getEntry() { return entry; }
    public void setEntry(String entry) { this.entry = entry; }
}