package com.UIN.Tool.ui.repo;

/**
 * 插件信息数据模型
 */
public class RepoPluginInfo {
    private String pluginId;      // 插件ID（仓库名）
    private String name;          // 插件名称（仓库描述）
    private String author;        // 作者（从 plugin.json 解析）
    private String description;   // 描述
    private String version;       // 版本代码
    private String versionName;   // 版本名称
    private String downloadUrl;   // 下载链接
    private String iconUrl;       // 图标链接
    private String updateLog;     // 更新日志
    private long size;            // 文件大小
    private String lastUpdate;    // 最后更新时间
    private String repositoryUrl; // 仓库地址
    private int downloadCount;    // 下载次数（暂不支持）
    private boolean isInstalled;  // 是否已安装

    // Getters and Setters
    public String getPluginId() { return pluginId; }
    public void setPluginId(String pluginId) { this.pluginId = pluginId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public String getVersionName() { return versionName; }
    public void setVersionName(String versionName) { this.versionName = versionName; }

    public String getDownloadUrl() { return downloadUrl; }
    public void setDownloadUrl(String downloadUrl) { this.downloadUrl = downloadUrl; }

    public String getIconUrl() { return iconUrl; }
    public void setIconUrl(String iconUrl) { this.iconUrl = iconUrl; }

    public String getUpdateLog() { return updateLog; }
    public void setUpdateLog(String updateLog) { this.updateLog = updateLog; }

    public long getSize() { return size; }
    public void setSize(long size) { this.size = size; }

    public String getLastUpdate() { return lastUpdate; }
    public void setLastUpdate(String lastUpdate) { this.lastUpdate = lastUpdate; }

    public String getRepositoryUrl() { return repositoryUrl; }
    public void setRepositoryUrl(String repositoryUrl) { this.repositoryUrl = repositoryUrl; }

    public int getDownloadCount() { return downloadCount; }
    public void setDownloadCount(int downloadCount) { this.downloadCount = downloadCount; }

    public boolean isInstalled() { return isInstalled; }
    public void setInstalled(boolean installed) { isInstalled = installed; }

    // 格式化文件大小
    public String getFormattedSize() {
        if (size < 1024) return size + " B";
        if (size < 1024 * 1024) return String.format("%.2f KB", size / 1024.0);
        return String.format("%.2f MB", size / (1024.0 * 1024.0));
    }

    // 格式化日期
    public String getFormattedDate() {
        if (lastUpdate == null || lastUpdate.isEmpty()) return "";
        try {
            // 2026-06-07T06:41:39Z -> 2026-06-07
            return lastUpdate.substring(0, 10);
        } catch (Exception e) {
            return lastUpdate;
        }
    }
}