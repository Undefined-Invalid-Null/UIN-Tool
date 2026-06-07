package com.UIN.Tool.ui.repo;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
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

import org.json.JSONArray;
import org.json.JSONObject;

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
    private List<RepoPluginInfo> allPlugins = new ArrayList<>();
    private List<RepoPluginInfo> filteredPlugins = new ArrayList<>();
    private OkHttpClient unsafeHttpClient;
    private Handler mainHandler;
    private String currentKeyword = "";
    private boolean isDownloading = false;
    
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
            
            @Override
            public void onShowDetail(RepoPluginInfo plugin) {
                showPluginDetailDialog(plugin);
            }
        });

        binding.recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerView.setAdapter(adapter);
        
        pluginManager = PluginManager.getInstance(requireContext());
        
        binding.tvEmpty.setText(getString(R.string.repo_no_plugins));
        
        LogUtils.exit(TAG, "initViews", System.currentTimeMillis());
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
        // 下拉刷新
        binding.swipeRefreshLayout.setOnRefreshListener(() -> {
            if (!isFragmentAttached()) {
                binding.swipeRefreshLayout.setRefreshing(false);
                return;
            }
            LogUtils.action(TAG, "下拉刷新", "刷新插件列表");
            loadPluginsFromRepo();
        });
        
        // 搜索框文本变化监听
        binding.etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                performSearch(s.toString());
            }
            
            @Override
            public void afterTextChanged(Editable s) {
                // 显示/清除按钮
                if (s.length() > 0) {
                    binding.btnClearSearch.setVisibility(View.VISIBLE);
                } else {
                    binding.btnClearSearch.setVisibility(View.GONE);
                }
            }
        });
        
        // 搜索按钮点击
        binding.btnSearch.setOnClickListener(v -> {
            // 搜索已经在文本变化时执行，这里只是确保键盘收起
            binding.etSearch.clearFocus();
        });
        
        // 清除搜索按钮
        binding.btnClearSearch.setOnClickListener(v -> {
            binding.etSearch.setText("");
            performSearch("");
            binding.etSearch.requestFocus();
        });
        
        // 键盘搜索键
        binding.etSearch.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                binding.etSearch.clearFocus();
                return true;
            }
            return false;
        });
    }
    
    private void performSearch(String keyword) {
        currentKeyword = keyword == null ? "" : keyword.trim().toLowerCase();
        
        filteredPlugins.clear();
        
        if (currentKeyword.isEmpty()) {
            filteredPlugins.addAll(allPlugins);
        } else {
            for (RepoPluginInfo plugin : allPlugins) {
                if (matchesSearch(plugin, currentKeyword)) {
                    filteredPlugins.add(plugin);
                }
            }
        }
        
        updateUiOnMainThread(() -> {
            if (isFragmentAttached() && adapter != null) {
                adapter.setPlugins(filteredPlugins);
                
                if (filteredPlugins.isEmpty()) {
                    binding.tvEmpty.setVisibility(View.VISIBLE);
                    if (!currentKeyword.isEmpty()) {
                        binding.tvEmpty.setText(getString(R.string.no_search_results, currentKeyword));
                    } else {
                        binding.tvEmpty.setText(getString(R.string.repo_no_plugins));
                    }
                } else {
                    binding.tvEmpty.setVisibility(View.GONE);
                }
            }
        });
        
        LogUtils.i(TAG, "搜索: \"" + keyword + "\", 找到 " + filteredPlugins.size() + " 个插件");
    }
    
    private boolean matchesSearch(RepoPluginInfo plugin, String keyword) {
        if (keyword.isEmpty()) return true;
        
        return (plugin.getName() != null && plugin.getName().toLowerCase().contains(keyword)) ||
               (plugin.getPluginId() != null && plugin.getPluginId().toLowerCase().contains(keyword)) ||
               (plugin.getDescription() != null && plugin.getDescription().toLowerCase().contains(keyword)) ||
               (plugin.getAuthor() != null && plugin.getAuthor().toLowerCase().contains(keyword));
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
                    binding.tvEmpty.setText(getString(R.string.repo_load_failed));
                    Toast.makeText(requireContext(), getString(R.string.repo_load_failed), Toast.LENGTH_SHORT).show();
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
                
                allPlugins.clear();
                allPlugins.addAll(fetchedPlugins);
                
                performSearch(currentKeyword);
                refreshInstalledStatus();
                
                updateUiOnMainThread(() -> {
                    if (isFragmentAttached()) {
                        binding.progressBar.setVisibility(View.GONE);
                        binding.swipeRefreshLayout.setRefreshing(false);
                    }
                });
                
            } catch (Exception e) {
                LogUtils.e(TAG, "加载插件列表失败", e);
                updateUiOnMainThread(() -> {
                    if (isFragmentAttached()) {
                        binding.progressBar.setVisibility(View.GONE);
                        binding.swipeRefreshLayout.setRefreshing(false);
                        binding.tvEmpty.setVisibility(View.VISIBLE);
                        binding.tvEmpty.setText(getString(R.string.repo_load_failed));
                        Toast.makeText(requireContext(), getString(R.string.repo_load_failed), Toast.LENGTH_LONG).show();
                    }
                });
            }
            LogUtils.exit(TAG, "loadPluginsFromRepo", startTime);
        }).start();
    }

    private void parseReposFromJson(String json, List<RepoPluginInfo> resultList) {
        try {
            JSONArray repos = new JSONArray(json);
            for (int i = 0; i < repos.length(); i++) {
                JSONObject repo = repos.getJSONObject(i);
                String repoName = repo.getString("name");
                
                if (repoName.equals(".github") || repoName.equals("Docs") || repoName.equals("docs")) {
                    continue;
                }
                
                String description = repo.optString("description", "");
                
                RepoPluginInfo plugin = fetchLatestReleaseForRepo(repoName);
                if (plugin != null) {
                    plugin.setName(description.isEmpty() ? repoName : description);
                    resultList.add(plugin);
                }
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
            JSONObject release = new JSONObject(json);
            RepoPluginInfo plugin = new RepoPluginInfo();
            plugin.setPluginId(repoName);
            
            String tagName = release.optString("tag_name", "");
            if (tagName.contains("-")) {
                String[] parts = tagName.split("-", 2);
                plugin.setVersion(parts[0]);
                plugin.setVersionName(parts.length > 1 ? parts[1] : parts[0]);
            } else {
                plugin.setVersion("1");
                plugin.setVersionName(tagName.isEmpty() ? "1.0.0" : tagName);
            }
            
            plugin.setUpdateLog(release.optString("body", ""));
            plugin.setLastUpdate(release.optString("published_at", ""));
            plugin.setRepositoryUrl(release.optString("html_url", ""));
            
            JSONArray assets = release.getJSONArray("assets");
            String downloadUrl = null;
            long size = 0;
            
            for (int i = 0; i < assets.length(); i++) {
                JSONObject asset = assets.getJSONObject(i);
                String assetName = asset.getString("name");
                if (assetName.endsWith(".tpk")) {
                    downloadUrl = asset.getString("browser_download_url");
                    size = asset.getLong("size");
                    break;
                }
            }
            
            if (downloadUrl == null) {
                return null;
            }
            
            plugin.setDownloadUrl(downloadUrl);
            plugin.setSize(size);
            plugin.setAuthor("UIN 社区");
            plugin.setDescription("UIN Tool 插件");
            
            return plugin;
        } catch (Exception e) {
            LogUtils.e(TAG, "解析 Release 失败: " + repoName, e);
            return null;
        }
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
                                lastUpdateTime = now;
                                
                                final int finalProgress = progress;
                                updateUiOnMainThread(() -> {
                                    if (isFragmentAttached()) {
                                        binding.downloadProgressBar.setProgress(finalProgress);
                                        binding.downloadProgressText.setText(
                                            String.format("正在下载: %d%%", finalProgress));
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

    private void showPluginDetailDialog(RepoPluginInfo plugin) {
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_repo_plugin_detail, null);
        
        TextView tvName = dialogView.findViewById(R.id.tv_detail_name);
        TextView tvPluginId = dialogView.findViewById(R.id.tv_detail_plugin_id);
        TextView tvVersion = dialogView.findViewById(R.id.tv_detail_version);
        TextView tvSize = dialogView.findViewById(R.id.tv_detail_size);
        TextView tvAuthor = dialogView.findViewById(R.id.tv_detail_author);
        TextView tvDate = dialogView.findViewById(R.id.tv_detail_date);
        TextView tvDescription = dialogView.findViewById(R.id.tv_detail_description);
        TextView tvUpdateLog = dialogView.findViewById(R.id.tv_detail_update_log);
        Button btnInstall = dialogView.findViewById(R.id.btn_detail_install);
        Button btnOpen = dialogView.findViewById(R.id.btn_detail_open);
        
        tvName.setText(plugin.getName());
        tvPluginId.setText(plugin.getPluginId());
        tvVersion.setText(plugin.getVersionName());
        tvSize.setText(plugin.getFormattedSize());
        tvAuthor.setText(plugin.getAuthor());
        tvDate.setText(plugin.getFormattedDate());
        tvDescription.setText(plugin.getDescription() != null ? plugin.getDescription() : "无描述");
        tvUpdateLog.setText(plugin.getUpdateLog() != null ? plugin.getUpdateLog() : "无更新日志");
        
        boolean isInstalled = pluginManager.getPluginInfo(plugin.getPluginId()) != null;
        if (isInstalled) {
            btnInstall.setVisibility(View.GONE);
            btnOpen.setVisibility(View.VISIBLE);
        } else {
            btnInstall.setVisibility(View.VISIBLE);
            btnOpen.setVisibility(View.GONE);
        }
        
        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setView(dialogView)
                .setPositiveButton(R.string.close, null)
                .create();
        
        btnInstall.setOnClickListener(v -> {
            int position = filteredPlugins.indexOf(plugin);
            downloadAndInstall(plugin, position);
            dialog.dismiss();
        });
        
        btnOpen.setOnClickListener(v -> {
            pluginManager.openPlugin(plugin.getPluginId(), requireContext());
            dialog.dismiss();
        });
        
        dialog.show();
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
                if (isFragmentAttached() && adapter != null) {
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