package com.UIN.Tool.ui.repo;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.UIN.Tool.R;
import com.UIN.Tool.databinding.FragmentRepoBinding;
import com.UIN.Tool.plugin.PluginInfo;
import com.UIN.Tool.plugin.PluginManager;
import com.UIN.Tool.ui.common.BaseFragment;
import com.UIN.Tool.utils.LogUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class RepoFragment extends BaseFragment {

    private static final String TAG = "RepoFragment";
    private FragmentRepoBinding binding;
    private RepoAdapter adapter;
    private PluginManager pluginManager;
    private List<RepoPluginInfo> plugins = new ArrayList<>();
    private OkHttpClient unsafeHttpClient;
    private Handler mainHandler;
    
    // GitHub 镜像站列表
    private static final String[] GITHUB_MIRRORS = {
        "https://hub.fastgit.xyz",
        "https://github.moeyy.xyz",
        "https://ghproxy.net",
        "https://mirror.ghproxy.com",
        "https://gh.api.99988866.xyz",
        "https://gitclone.com",
        "https://gh.jiewen.ltd",
    };
    
    private String currentMirror = "";
    private boolean isDownloading = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentRepoBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    protected void initViews(View view) {
        LogUtils.enter(TAG, "initViews");
        
        mainHandler = new Handler(Looper.getMainLooper());
        
        unsafeHttpClient = getUnsafeOkHttpClient();

        adapter = new RepoAdapter(requireContext(), new RepoAdapter.OnPluginActionListener() {
            @Override
            public void onInstall(RepoPluginInfo plugin, int position) {
                if (!isFragmentAttached()) {
                    LogUtils.w(TAG, "Fragment 未附加，无法安装");
                    return;
                }
                LogUtils.action(TAG, "用户点击安装", plugin.getName());
                downloadAndInstall(plugin, position);
            }

            @Override
            public void onOpen(RepoPluginInfo plugin) {
                if (!isFragmentAttached()) {
                    LogUtils.w(TAG, "Fragment 未附加，无法打开");
                    return;
                }
                LogUtils.action(TAG, "用户点击打开", plugin.getName());
                if (pluginManager != null) {
                    pluginManager.openPlugin(plugin.getPluginId(), requireContext());
                }
            }
        });

        binding.recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerView.setAdapter(adapter);
        
        pluginManager = PluginManager.getInstance(requireContext());
        
        LogUtils.exit(TAG, "initViews", System.currentTimeMillis());
    }

    private OkHttpClient getUnsafeOkHttpClient() {
        try {
            final TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManager() {
                    @Override
                    public void checkClientTrusted(X509Certificate[] chain, String authType) {}
                    @Override
                    public void checkServerTrusted(X509Certificate[] chain, String authType) {}
                    @Override
                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[]{};
                    }
                }
            };

            final SSLContext sslContext = SSLContext.getInstance("SSL");
            sslContext.init(null, trustAllCerts, new SecureRandom());

            final HostnameVerifier hostnameVerifier = (hostname, session) -> true;

            return new OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(60, TimeUnit.SECONDS)
                    .writeTimeout(60, TimeUnit.SECONDS)
                    .sslSocketFactory(sslContext.getSocketFactory(), (X509TrustManager) trustAllCerts[0])
                    .hostnameVerifier(hostnameVerifier)
                    .build();
        } catch (Exception e) {
            LogUtils.e(TAG, "创建OkHttpClient失败", e);
            return new OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(60, TimeUnit.SECONDS)
                    .writeTimeout(60, TimeUnit.SECONDS)
                    .build();
        }
    }

    @Override
    protected void initData() {
        LogUtils.enter(TAG, "initData");
        testMirrors();
        loadPluginsFromRepo();
        LogUtils.exit(TAG, "initData", System.currentTimeMillis());
    }

    @Override
    protected void setupListeners() {
        binding.swipeRefreshLayout.setOnRefreshListener(() -> {
            if (!isFragmentAttached()) {
                binding.swipeRefreshLayout.setRefreshing(false);
                return;
            }
            LogUtils.action(TAG, "下拉刷新", "刷新插件列表");
            loadPluginsFromRepo();
        });
    }

    private void testMirrors() {
        new Thread(() -> {
            for (String mirror : GITHUB_MIRRORS) {
                try {
                    String testUrl = mirror + "/https://api.github.com/orgs/UIN-Tool-Plugins/repos?per_page=1";
                    Request request = new Request.Builder()
                            .url(testUrl)
                            .header("Accept", "application/vnd.github.v3+json")
                            .build();
                    
                    try (Response response = unsafeHttpClient.newCall(request).execute()) {
                        if (response.isSuccessful()) {
                            currentMirror = mirror;
                            LogUtils.success(TAG, "找到可用镜像: " + mirror);
                            break;
                        }
                    }
                } catch (Exception e) {
                    LogUtils.d(TAG, "镜像不可用: " + mirror);
                }
            }
            if (currentMirror.isEmpty()) {
                LogUtils.w(TAG, "未找到可用镜像，将使用直连");
            }
        }).start();
    }

    private String getMirrorUrl(String originalUrl) {
        if (currentMirror.isEmpty()) {
            return originalUrl;
        }
        if (currentMirror.contains("ghproxy")) {
            return currentMirror + "/" + originalUrl;
        } else if (currentMirror.contains("fastgit")) {
            return originalUrl.replace("https://api.github.com", currentMirror + "/api.github.com");
        } else {
            return currentMirror + "/" + originalUrl;
        }
    }

    private String getMirrorUrlForDownload(String originalUrl) {
        if (currentMirror.isEmpty()) {
            return originalUrl;
        }
        if (currentMirror.contains("ghproxy")) {
            return currentMirror + "/" + originalUrl;
        }
        return originalUrl;
    }

    private boolean isNetworkAvailable() {
        if (!isFragmentAttached()) {
            return false;
        }
        ConnectivityManager connectivityManager = (ConnectivityManager) requireContext()
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }

    private boolean isFragmentAttached() {
        return isAdded() && getActivity() != null && !isDetached() && !isRemoving() && binding != null;
    }

    private void updateUiOnMainThread(Runnable runnable) {
        if (isFragmentAttached()) {
            mainHandler.post(runnable);
        }
    }

    private void loadPluginsFromRepo() {
        LogUtils.enter(TAG, "loadPluginsFromRepo");
        
        if (!isFragmentAttached()) {
            LogUtils.w(TAG, "Fragment 未附加，跳过加载");
            return;
        }
        
        if (!isNetworkAvailable()) {
            LogUtils.w(TAG, "网络不可用");
            updateUiOnMainThread(() -> {
                if (isFragmentAttached()) {
                    binding.progressBar.setVisibility(View.GONE);
                    binding.swipeRefreshLayout.setRefreshing(false);
                    binding.tvEmpty.setVisibility(View.VISIBLE);
                    binding.tvEmpty.setText("网络不可用\n\n请检查网络连接后下拉刷新");
                    Toast.makeText(requireContext(), "网络不可用，请检查网络连接", Toast.LENGTH_SHORT).show();
                }
            });
            LogUtils.exit(TAG, "loadPluginsFromRepo", System.currentTimeMillis());
            return;
        }
        
        updateUiOnMainThread(() -> {
            if (isFragmentAttached()) {
                binding.progressBar.setVisibility(View.VISIBLE);
                binding.tvEmpty.setVisibility(View.GONE);
                binding.swipeRefreshLayout.setRefreshing(true);
            }
        });
        
        LogUtils.d(TAG, "开始从 GitHub API 获取插件列表");

        new Thread(() -> {
            long startTime = System.currentTimeMillis();
            final List<RepoPluginInfo> fetchedPlugins = new ArrayList<>();
            
            try {
                String reposUrl = "https://api.github.com/orgs/UIN-Tool-Plugins/repos?per_page=100";
                String mirroredUrl = getMirrorUrl(reposUrl);
                LogUtils.d(TAG, "使用 URL: " + mirroredUrl);
                
                Request request = new Request.Builder()
                        .url(mirroredUrl)
                        .header("Accept", "application/vnd.github.v3+json")
                        .header("User-Agent", "UIN-Tool-Android")
                        .build();
                
                try (Response response = unsafeHttpClient.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        throw new Exception("GitHub API 错误: " + response.code());
                    }
                    String responseBody = response.body().string();
                    parseReposFromJson(responseBody, fetchedPlugins);
                }
                
                LogUtils.param(TAG, "获取到插件数量", fetchedPlugins.size());
                
                List<String> installedIds = new ArrayList<>();
                if (pluginManager != null) {
                    List<PluginInfo> installedPlugins = pluginManager.getInstalledPlugins();
                    for (PluginInfo info : installedPlugins) {
                        installedIds.add(info.pluginId);
                    }
                }

                final List<String> finalInstalledIds = installedIds;
                
                updateUiOnMainThread(() -> {
                    if (isFragmentAttached()) {
                        plugins.clear();
                        plugins.addAll(fetchedPlugins);
                        adapter.setPlugins(plugins);
                        adapter.setInstalledPlugins(finalInstalledIds);
                        binding.progressBar.setVisibility(View.GONE);
                        binding.swipeRefreshLayout.setRefreshing(false);

                        if (plugins.isEmpty()) {
                            binding.tvEmpty.setVisibility(View.VISIBLE);
                            binding.tvEmpty.setText("暂无可用插件\n\n请确保 GitHub 组织中有已发布的插件");
                        } else {
                            binding.tvEmpty.setVisibility(View.GONE);
                            LogUtils.success(TAG, "成功加载 " + plugins.size() + " 个插件");
                        }
                    }
                });
            } catch (Exception e) {
                LogUtils.e(TAG, "加载插件列表失败", e);
                updateUiOnMainThread(() -> {
                    if (isFragmentAttached()) {
                        binding.progressBar.setVisibility(View.GONE);
                        binding.swipeRefreshLayout.setRefreshing(false);
                        binding.tvEmpty.setVisibility(View.VISIBLE);
                        binding.tvEmpty.setText("加载失败: " + (e.getMessage() != null ? e.getMessage() : "未知错误"));
                        Toast.makeText(requireContext(), "加载失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
            }
            LogUtils.exit(TAG, "loadPluginsFromRepo", startTime);
        }).start();
    }

    private void parseReposFromJson(String json, List<RepoPluginInfo> resultList) {
        try {
            int pos = 0;
            while (true) {
                int startIdx = json.indexOf("\"name\":", pos);
                if (startIdx == -1) break;
                
                int nameStart = json.indexOf("\"", startIdx + 7) + 1;
                int nameEnd = json.indexOf("\"", nameStart);
                if (nameStart == 0 || nameEnd == -1) break;
                String repoName = json.substring(nameStart, nameEnd);
                
                if (repoName.equals(".github") || repoName.equals("Docs") || repoName.equals("docs")) {
                    pos = nameEnd + 1;
                    continue;
                }
                
                int descStartIdx = json.indexOf("\"description\":", nameEnd);
                String description = "";
                if (descStartIdx != -1) {
                    int descStart = json.indexOf("\"", descStartIdx + 13) + 1;
                    if (descStart > 0 && descStart < json.length()) {
                        int descEnd = json.indexOf("\"", descStart);
                        if (descEnd > descStart) {
                            description = json.substring(descStart, descEnd);
                        }
                    }
                }
                
                RepoPluginInfo plugin = fetchLatestReleaseForRepo(repoName);
                if (plugin != null) {
                    plugin.setName(description.isEmpty() ? repoName : description);
                    resultList.add(plugin);
                }
                pos = nameEnd + 1;
            }
        } catch (Exception e) {
            LogUtils.e(TAG, "解析 JSON 失败", e);
        }
    }

    private RepoPluginInfo fetchLatestReleaseForRepo(String repoName) {
        try {
            String releasesUrl = "https://api.github.com/repos/UIN-Tool-Plugins/" + repoName + "/releases/latest";
            String mirroredUrl = getMirrorUrl(releasesUrl);
            
            Request request = new Request.Builder()
                    .url(mirroredUrl)
                    .header("Accept", "application/vnd.github.v3+json")
                    .header("User-Agent", "UIN-Tool-Android")
                    .build();

            try (Response response = unsafeHttpClient.newCall(request).execute()) {
                if (response.isSuccessful()) {
                    return parseReleaseToPlugin(repoName, response.body().string());
                }
            }
        } catch (Exception e) {
            LogUtils.e(TAG, "获取 " + repoName + " Release 失败: " + e.getMessage());
        }
        return null;
    }

    private RepoPluginInfo parseReleaseToPlugin(String repoName, String json) {
        try {
            RepoPluginInfo plugin = new RepoPluginInfo();
            plugin.setPluginId(repoName);
            
            String tagName = extractJsonValue(json, "tag_name");
            if (tagName != null && tagName.contains("-")) {
                String[] parts = tagName.split("-", 2);
                plugin.setVersion(parts[0]);
                plugin.setVersionName(parts.length > 1 ? parts[1] : parts[0]);
            } else {
                plugin.setVersion("1");
                plugin.setVersionName(tagName != null ? tagName : "1.0.0");
            }
            
            plugin.setUpdateLog(extractJsonValue(json, "body"));
            plugin.setLastUpdate(extractJsonValue(json, "published_at"));
            plugin.setRepositoryUrl(extractJsonValue(json, "html_url"));
            
            String downloadUrl = extractDownloadUrlFromAssets(json);
            if (downloadUrl != null) {
                plugin.setDownloadUrl(downloadUrl);
                plugin.setSize(extractFileSizeFromAssets(json, downloadUrl));
            } else {
                return null;
            }
            
            plugin.setAuthor("UIN 社区");
            plugin.setDescription("UIN Tool 插件");
            
            return plugin;
        } catch (Exception e) {
            LogUtils.e(TAG, "解析 Release 失败: " + repoName, e);
            return null;
        }
    }

    private String extractDownloadUrlFromAssets(String json) {
        try {
            String searchStr = ".tpk\"";
            int idx = 0;
            while (true) {
                int found = json.indexOf(searchStr, idx);
                if (found == -1) break;
                
                int urlStart = json.lastIndexOf("\"browser_download_url\":", found);
                if (urlStart != -1) {
                    int urlValueStart = json.indexOf("\"", urlStart + 23) + 1;
                    int urlValueEnd = json.indexOf("\"", urlValueStart);
                    if (urlValueStart > 0 && urlValueEnd > urlValueStart) {
                        return json.substring(urlValueStart, urlValueEnd);
                    }
                }
                idx = found + 5;
            }
        } catch (Exception e) {
            LogUtils.e(TAG, "提取下载 URL 失败", e);
        }
        return null;
    }

    private long extractFileSizeFromAssets(String json, String downloadUrl) {
        try {
            int urlIdx = json.indexOf(downloadUrl);
            if (urlIdx == -1) return 0;
            
            int sizeStart = json.lastIndexOf("\"size\":", urlIdx);
            if (sizeStart != -1) {
                int sizeValueStart = sizeStart + 6;
                int sizeValueEnd = json.indexOf(",", sizeValueStart);
                if (sizeValueEnd == -1 || sizeValueEnd > urlIdx) {
                    sizeValueEnd = json.indexOf("}", sizeValueStart);
                }
                return Long.parseLong(json.substring(sizeValueStart, sizeValueEnd).trim());
            }
        } catch (Exception e) {
            LogUtils.e(TAG, "提取文件大小失败", e);
        }
        return 0;
    }

    private String extractJsonValue(String json, String key) {
        String searchKey = "\"" + key + "\":";
        int keyIdx = json.indexOf(searchKey);
        if (keyIdx == -1) return null;
        
        int valueStart = json.indexOf("\"", keyIdx + searchKey.length()) + 1;
        if (valueStart == 0 || valueStart >= json.length()) return null;
        
        int valueEnd = json.indexOf("\"", valueStart);
        if (valueEnd == -1) return null;
        
        return json.substring(valueStart, valueEnd);
    }

    private void downloadAndInstall(RepoPluginInfo plugin, int position) {
        LogUtils.enter(TAG, "downloadAndInstall");
        
        if (!isFragmentAttached()) {
            LogUtils.w(TAG, "Fragment 未附加，无法下载");
            return;
        }
        
        if (isDownloading) {
            Toast.makeText(requireContext(), "正在下载中，请稍后...", Toast.LENGTH_SHORT).show();
            return;
        }
        
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(requireContext(), "请先授予存储权限", Toast.LENGTH_SHORT).show();
            return;
        }

        isDownloading = true;
        
        updateUiOnMainThread(() -> {
            if (isFragmentAttached()) {
                binding.downloadProgressBar.setVisibility(View.VISIBLE);
                binding.downloadProgressBar.setProgress(0);
                binding.downloadProgressText.setVisibility(View.VISIBLE);
                binding.downloadProgressText.setText("正在下载: 0%");
                adapter.setButtonLoading(position, true);
            }
        });
        
        Toast.makeText(requireContext(), "正在下载: " + plugin.getName(), Toast.LENGTH_SHORT).show();

        new Thread(() -> {
            File tempFile = null;
            try {
                String downloadUrl = plugin.getDownloadUrl();
                String mirroredUrl = getMirrorUrlForDownload(downloadUrl);
                
                Request request = new Request.Builder()
                        .url(mirroredUrl)
                        .header("User-Agent", "UIN-Tool-Android")
                        .build();
                
                try (Response response = unsafeHttpClient.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        throw new Exception("下载失败: " + response.code());
                    }
                    
                    ResponseBody body = response.body();
                    if (body == null) {
                        throw new Exception("响应体为空");
                    }
                    
                    long contentLength = body.contentLength();
                    tempFile = new File(requireContext().getCacheDir(), "repo_plugin_" + System.currentTimeMillis() + ".tpk");
                    
                    try (InputStream is = body.byteStream();
                         FileOutputStream fos = new FileOutputStream(tempFile)) {
                        
                        byte[] buffer = new byte[8192];
                        long totalBytesRead = 0;
                        int bytesRead;
                        long lastUpdateTime = 0;
                        
                        while ((bytesRead = is.read(buffer)) != -1) {
                            fos.write(buffer, 0, bytesRead);
                            totalBytesRead += bytesRead;
                            
                            long now = System.currentTimeMillis();
                            if (now - lastUpdateTime > 200 && contentLength > 0) {
                                final int progress = (int) (totalBytesRead * 100 / contentLength);
                                final long downloaded = totalBytesRead;
                                lastUpdateTime = now;
                                
                                updateUiOnMainThread(() -> {
                                    if (isFragmentAttached()) {
                                        binding.downloadProgressBar.setProgress(progress);
                                        binding.downloadProgressText.setText(
                                            String.format("正在下载: %d%% (%.2f MB / %.2f MB)", 
                                                progress, 
                                                downloaded / (1024.0 * 1024.0),
                                                contentLength / (1024.0 * 1024.0)));
                                    }
                                });
                            }
                        }
                    }
                    
                    plugin.setSize(tempFile.length());
                    PluginInfo installedInfo = pluginManager.installPlugin(tempFile.getAbsolutePath(), plugin.getPluginId() + ".tpk");
                    
                    if (installedInfo != null) {
                        updateUiOnMainThread(() -> {
                            if (isFragmentAttached()) {
                                binding.downloadProgressBar.setVisibility(View.GONE);
                                binding.downloadProgressText.setVisibility(View.GONE);
                                Toast.makeText(requireContext(), "安装成功: " + installedInfo.name, Toast.LENGTH_SHORT).show();
                                refreshInstalledStatus();
                                loadPluginsFromRepo();
                            }
                        });
                    } else {
                        throw new Exception("安装失败");
                    }
                }
            } catch (Exception e) {
                LogUtils.e(TAG, "下载安装失败", e);
                updateUiOnMainThread(() -> {
                    if (isFragmentAttached()) {
                        binding.downloadProgressBar.setVisibility(View.GONE);
                        binding.downloadProgressText.setVisibility(View.GONE);
                        Toast.makeText(requireContext(), "下载安装失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
            } finally {
                isDownloading = false;
                updateUiOnMainThread(() -> {
                    if (isFragmentAttached()) {
                        adapter.setButtonLoading(position, false);
                    }
                });
                if (tempFile != null && tempFile.exists()) {
                    tempFile.delete();
                }
            }
        }).start();
    }

    private void refreshInstalledStatus() {
        if (!isFragmentAttached()) return;
        
        new Thread(() -> {
            if (pluginManager == null && isFragmentAttached()) {
                pluginManager = PluginManager.getInstance(requireContext());
            }
            List<PluginInfo> installedPlugins = pluginManager != null ? pluginManager.getInstalledPlugins() : new ArrayList<>();
            final List<String> installedIds = new ArrayList<>();
            for (PluginInfo info : installedPlugins) {
                installedIds.add(info.pluginId);
            }
            
            updateUiOnMainThread(() -> {
                if (isFragmentAttached()) {
                    adapter.setInstalledPlugins(installedIds);
                }
            });
        }).start();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (isFragmentAttached()) {
            refreshInstalledStatus();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        LogUtils.d(TAG, "onDestroyView");
        isDownloading = false;
        binding = null;
    }
}