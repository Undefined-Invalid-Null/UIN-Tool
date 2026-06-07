package com.UIN.Tool.ui.repo;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * GitHub API 客户端
 * 用于从 UIN-Tool-Plugins 组织获取插件列表
 */
public class GitHubApiClient {
    private static final String TAG = "GitHubApiClient";
    private static final String ORGANIZATION = "UIN-Tool-Plugins";
    private static final String API_BASE = "https://api.github.com";

    private OkHttpClient client;

    public GitHubApiClient() {
        client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    /**
     * 获取所有插件仓库列表
     */
    public List<RepoPluginInfo> fetchAllPlugins() throws Exception {
        List<RepoPluginInfo> plugins = new ArrayList<>();

        String reposUrl = API_BASE + "/orgs/" + ORGANIZATION + "/repos?per_page=100";
        Request request = new Request.Builder()
                .url(reposUrl)
                .header("Accept", "application/vnd.github.v3+json")
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new Exception("GitHub API 错误: " + response.code());
            }

            String responseBody = response.body().string();
            JSONArray repos = new JSONArray(responseBody);

            for (int i = 0; i < repos.length(); i++) {
                JSONObject repo = repos.getJSONObject(i);
                String repoName = repo.getString("name");
                String repoDesc = repo.getString("description");

                // 跳过特殊仓库（.github 和 Docs）
                if (repoName.equals(".github") || repoName.equals("Docs") || repoName.equals("docs")) {
                    continue;
                }

                // 获取最新 release
                RepoPluginInfo plugin = fetchLatestRelease(repoName, repoDesc);
                if (plugin != null) {
                    plugins.add(plugin);
                }
            }
        }

        return plugins;
    }

    /**
     * 获取指定仓库的最新 Release
     */
    private RepoPluginInfo fetchLatestRelease(String repoName, String repoDesc) {
        try {
            String releasesUrl = API_BASE + "/repos/" + ORGANIZATION + "/" + repoName + "/releases/latest";
            Request request = new Request.Builder()
                    .url(releasesUrl)
                    .header("Accept", "application/vnd.github.v3+json")
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (response.isSuccessful()) {
                    String responseBody = response.body().string();
                    JSONObject release = new JSONObject(responseBody);
                    return parseReleaseToPlugin(repoName, repoDesc, release);
                } else {
                    Log.w(TAG, "仓库 " + repoName + " 没有 Release");
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "获取 " + repoName + " 的 Release 失败: " + e.getMessage());
        }
        return null;
    }

    /**
     * 解析 Release JSON 为插件对象
     */
    private RepoPluginInfo parseReleaseToPlugin(String repoName, String repoDesc, JSONObject release) throws Exception {
        JSONArray assets = release.getJSONArray("assets");

        // 查找 .tpk 文件
        JSONObject tpkAsset = null;
        for (int i = 0; i < assets.length(); i++) {
            JSONObject asset = assets.getJSONObject(i);
            String assetName = asset.getString("name");
            if (assetName.endsWith(".tpk")) {
                tpkAsset = asset;
                break;
            }
        }

        if (tpkAsset == null) {
            Log.w(TAG, "仓库 " + repoName + " 的 Release 中没有 .tpk 文件");
            return null;
        }

        RepoPluginInfo plugin = new RepoPluginInfo();
        plugin.setPluginId(repoName);
        plugin.setName(repoDesc != null && !repoDesc.isEmpty() ? repoDesc : repoName);
        plugin.setDownloadUrl(tpkAsset.getString("browser_download_url"));
        plugin.setSize(tpkAsset.getLong("size"));
        plugin.setVersionName(release.optString("tag_name", ""));
        plugin.setUpdateLog(release.optString("body", ""));
        plugin.setRepositoryUrl(release.optString("html_url", ""));
        plugin.setLastUpdate(release.optString("published_at", ""));

        // 从 tag_name 解析版本代码（格式：版本代码-版本名称）
        String tagName = plugin.getVersionName();
        if (tagName.contains("-")) {
            String[] parts = tagName.split("-", 2);
            plugin.setVersion(parts[0]);
            if (parts.length > 1) {
                plugin.setVersionName(parts[1]);
            }
        }

        // 设置默认作者（可以从 plugin.json 解析，这里简化处理）
        plugin.setAuthor("UIN 社区");
        plugin.setDescription("这是一个 UIN Tool 插件");

        return plugin;
    }
}