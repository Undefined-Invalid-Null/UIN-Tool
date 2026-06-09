package com.UIN.Tool.utils;

import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class UpdateChecker {
    
    private static final String TAG = "UpdateChecker";
    private static final String GITHUB_API = "https://api.github.com/repos/Undefined-Invalid-Null/UIN-Tool/releases";
    private static final String GITHUB_REPO = "https://github.com/Undefined-Invalid-Null/UIN-Tool/releases/latest";
    private static final String PREF_NAME = "github_mirror";
    private static final String KEY_ENABLED_MIRRORS = "enabled_mirrors";
    private static final String KEY_USE_CDN = "use_cdn";
    private static final String KEY_FORCE_UPDATE_IGNORE = "force_update_ignore";
    
    // 默认镜像站列表
    private static final String[] DEFAULT_MIRRORS = {
        "https://hub.fastgit.xyz",
        "https://github.moeyy.xyz",
        "https://ghproxy.net",
        "https://mirror.ghproxy.com",
        "https://gh.api.99988866.xyz",
        "https://gitclone.com",
        "https://gh.jiewen.ltd"
    };
    
    private Context context;
    private Handler mainHandler;
    private OnUpdateCheckListener listener;
    private String currentMirror = "";
    private OkHttpClient client;
    private List<String> customMirrors = null;
    private boolean useCdn = true;
    private MirrorUpdateReceiver mirrorReceiver;
    
    public interface OnUpdateCheckListener {
        void onCheckStart();
        void onCheckSuccess(List<ReleaseInfo> releases, boolean hasNewer, boolean forceUpdate);
        void onCheckFailed(String error);
        void onNoUpdate(String currentVersion);
    }
    
    public static class ReleaseInfo {
        public String tagName;          // Tag 名称，如 "1-1.0.0-1"
        public String versionName;      // 版本名，如 "1.0.0"
        public String versionCode;      // 版本代码，如 "1"
        public String forceFlag;        // 强制更新标志，如 "1" 或 "0"
        public boolean forceUpdate;     // 是否强制更新
        public String releaseDate;      // 发布日期
        public String releaseNotes;     // 更新日志
        public String downloadUrl;      // APK 下载链接
        public String apkSize;          // APK 大小
        public boolean isPreRelease;    // 是否为预发布版
        public boolean isNewer;         // 是否比当前版本新
        
        public String getFormattedDate() {
            if (releaseDate == null || releaseDate.isEmpty()) return "";
            try {
                return releaseDate.substring(0, 10);
            } catch (Exception e) {
                return releaseDate;
            }
        }
        
        public String getDisplayName() {
            return "v" + versionName + " (代码: " + versionCode + ")";
        }
        
        public boolean isNewerThan(int currentCode) {
            if (versionCode == null) return false;
            try {
                int newCode = Integer.parseInt(versionCode);
                return newCode > currentCode;
            } catch (Exception e) {
                return false;
            }
        }
    }
    
    private class MirrorUpdateReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if ("com.UIN.Tool.UPDATE_MIRRORS".equals(intent.getAction())) {
                ArrayList<String> mirrors = intent.getStringArrayListExtra("mirrors");
                if (mirrors != null && !mirrors.isEmpty()) {
                    customMirrors = mirrors;
                    LogUtils.i(TAG, "已更新镜像站列表，共 " + mirrors.size() + " 个");
                }
                useCdn = intent.getBooleanExtra("use_cdn", true);
                testMirrorsAndCheck();
            }
        }
    }
    
    public UpdateChecker(Context context) {
        this.context = context.getApplicationContext();
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.client = getUnsafeOkHttpClient();
        disableSSLCertificateChecking();
        loadMirrorSettings();
        registerMirrorReceiver();
        LogUtils.i(TAG, "UpdateChecker 初始化完成");
    }
    
    private void loadMirrorSettings() {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String enabledMirrorsStr = prefs.getString(KEY_ENABLED_MIRRORS, "");
        useCdn = prefs.getBoolean(KEY_USE_CDN, true);
        
        if (!enabledMirrorsStr.isEmpty()) {
            String[] urls = enabledMirrorsStr.split(",");
            customMirrors = new ArrayList<>();
            for (String url : urls) {
                if (!url.isEmpty()) {
                    customMirrors.add(url);
                }
            }
            if (!customMirrors.isEmpty()) {
                LogUtils.i(TAG, "从配置加载镜像站，共 " + customMirrors.size() + " 个");
            }
        }
    }
    
    private void registerMirrorReceiver() {
        mirrorReceiver = new MirrorUpdateReceiver();
        IntentFilter filter = new IntentFilter("com.UIN.Tool.UPDATE_MIRRORS");
        LocalBroadcastManager.getInstance(context).registerReceiver(mirrorReceiver, filter);
    }
    
    public void unregisterReceiver() {
        if (mirrorReceiver != null) {
            LocalBroadcastManager.getInstance(context).unregisterReceiver(mirrorReceiver);
        }
    }
    
    private List<String> getMirrorsToTest() {
        if (customMirrors != null && !customMirrors.isEmpty()) {
            return customMirrors;
        }
        List<String> mirrors = new ArrayList<>();
        for (String mirror : DEFAULT_MIRRORS) {
            mirrors.add(mirror);
        }
        return mirrors;
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
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());

            final HostnameVerifier hostnameVerifier = (hostname, session) -> true;

            return new OkHttpClient.Builder()
                    .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                    .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                    .sslSocketFactory(sslContext.getSocketFactory(), (X509TrustManager) trustAllCerts[0])
                    .hostnameVerifier(hostnameVerifier)
                    .build();
        } catch (Exception e) {
            LogUtils.e(TAG, "创建OkHttpClient失败", e);
            return new OkHttpClient.Builder()
                    .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                    .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                    .build();
        }
    }
    
    private void disableSSLCertificateChecking() {
        try {
            TrustManager[] trustAllCerts = new TrustManager[]{
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
            
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.getSocketFactory());
            
            HostnameVerifier allHostsValid = new HostnameVerifier() {
                @Override
                public boolean verify(String hostname, SSLSession session) {
                    return true;
                }
            };
            HttpsURLConnection.setDefaultHostnameVerifier(allHostsValid);
            
            LogUtils.i(TAG, "SSL 证书检查已禁用");
        } catch (Exception e) {
            LogUtils.e(TAG, "禁用 SSL 证书检查失败", e);
        }
    }
    
    public void setOnUpdateCheckListener(OnUpdateCheckListener listener) {
        this.listener = listener;
    }
    
    public void checkUpdate() {
        LogUtils.enter(TAG, "checkUpdate");
        if (listener != null) {
            listener.onCheckStart();
        }
        testMirrorsAndCheck();
    }
    
    private void testMirrorsAndCheck() {
        LogUtils.i(TAG, "开始测试镜像站...");
        
        new Thread(new Runnable() {
            @Override
            public void run() {
                String workingUrl = null;
                List<String> mirrors = getMirrorsToTest();
                
                LogUtils.d(TAG, "测试直连: " + GITHUB_API);
                if (testUrl(GITHUB_API)) {
                    workingUrl = GITHUB_API;
                    LogUtils.success(TAG, "直连可用");
                }
                
                if (workingUrl == null) {
                    for (String mirror : mirrors) {
                        String testUrl = getMirrorUrl(mirror, GITHUB_API);
                        LogUtils.d(TAG, "测试镜像: " + testUrl);
                        if (testUrl(testUrl)) {
                            workingUrl = testUrl;
                            currentMirror = mirror;
                            LogUtils.success(TAG, "找到可用镜像: " + mirror);
                            break;
                        }
                    }
                }
                
                if (workingUrl == null) {
                    LogUtils.e(TAG, "所有镜像均不可用");
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (listener != null) {
                                listener.onCheckFailed("网络连接失败，请检查网络后重试");
                            }
                        }
                    });
                    return;
                }
                
                doCheckUpdate(workingUrl);
            }
        }).start();
    }
    
    private String getMirrorUrl(String mirror, String originalUrl) {
        if (mirror == null || mirror.isEmpty()) {
            return originalUrl;
        }
        if (mirror.contains("ghproxy")) {
            return mirror + "/" + originalUrl;
        } else if (mirror.contains("fastgit")) {
            return originalUrl.replace("https://api.github.com", mirror + "/api.github.com");
        } else if (mirror.contains("moeyy")) {
            return mirror + "/" + originalUrl;
        } else {
            return mirror + "/" + originalUrl;
        }
    }
    
    private String getDownloadMirrorUrl(String mirror, String originalUrl) {
        if (mirror == null || mirror.isEmpty() || !useCdn) {
            return originalUrl;
        }
        if (mirror.contains("ghproxy")) {
            return mirror + "/" + originalUrl;
        }
        return originalUrl;
    }
    
    private boolean testUrl(String urlString) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(urlString);
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(8000);
            connection.setReadTimeout(8000);
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "application/vnd.github.v3+json");
            connection.setRequestProperty("User-Agent", "UIN-Tool-Android");
            int responseCode = connection.getResponseCode();
            LogUtils.d(TAG, "测试 " + urlString + " -> 响应码: " + responseCode);
            return responseCode == 200;
        } catch (Exception e) {
            LogUtils.d(TAG, "测试失败: " + urlString + " - " + e.getMessage());
            return false;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
    
    private void doCheckUpdate(String apiUrl) {
        LogUtils.i(TAG, "开始检查更新, API URL: " + apiUrl);
        
        try {
            String fullUrl = apiUrl + "?per_page=30";
            LogUtils.d(TAG, "请求 URL: " + fullUrl);
            
            Request request = new Request.Builder()
                    .url(fullUrl)
                    .header("Accept", "application/vnd.github.v3+json")
                    .header("User-Agent", "UIN-Tool-Android")
                    .build();
            
            try (Response response = client.newCall(request).execute()) {
                int responseCode = response.code();
                LogUtils.param(TAG, "响应码", responseCode);
                
                if (responseCode != 200) {
                    final String error = "GitHub API 响应错误: " + responseCode;
                    LogUtils.e(TAG, error);
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (listener != null) listener.onCheckFailed(error);
                        }
                    });
                    return;
                }
                
                String responseBody = response.body().string();
                LogUtils.d(TAG, "响应内容长度: " + responseBody.length());
                
                final List<ReleaseInfo> releases = parseReleasesInfo(responseBody);
                final int currentVersionCode = getCurrentVersionCode();
                final String currentVersionName = getCurrentVersionName();
                
                LogUtils.param(TAG, "当前版本", currentVersionName + " (代码: " + currentVersionCode + ")");
                LogUtils.param(TAG, "找到 Release 数量", releases.size());
                
                boolean hasNewer = false;
                boolean forceUpdate = false;
                ReleaseInfo latestRelease = null;
                
                for (ReleaseInfo info : releases) {
                    info.isNewer = info.isNewerThan(currentVersionCode);
                    if (info.isNewer) {
                        hasNewer = true;
                        forceUpdate = info.forceUpdate;
                        latestRelease = info;
                        LogUtils.d(TAG, "发现新版本: " + info.versionName + " (代码: " + info.versionCode + ", 强制更新: " + forceUpdate + ")");
                        if (currentMirror != null && !currentMirror.isEmpty() && useCdn) {
                            String originalUrl = info.downloadUrl;
                            if (originalUrl != null && originalUrl.startsWith("https://github.com")) {
                                info.downloadUrl = getDownloadMirrorUrl(currentMirror, originalUrl);
                                LogUtils.d(TAG, "使用镜像下载: " + info.downloadUrl);
                            }
                        }
                        break;
                    }
                }
                
                final boolean finalHasNewer = hasNewer;
                final boolean finalForceUpdate = forceUpdate;
                
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (listener != null) {
                            if (!releases.isEmpty()) {
                                LogUtils.success(TAG, "检查更新成功，共 " + releases.size() + " 个版本");
                                listener.onCheckSuccess(releases, finalHasNewer, finalForceUpdate);
                            } else {
                                LogUtils.i(TAG, "没有找到任何 Release");
                                listener.onNoUpdate(currentVersionName);
                            }
                        }
                    }
                });
            }
            
        } catch (final Exception e) {
            LogUtils.e(TAG, "检查更新失败: " + e.getMessage(), e);
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (listener != null) {
                        listener.onCheckFailed("请求失败: " + e.getMessage());
                    }
                }
            });
        }
    }
    
    /**
     * 解析 Releases 信息
     * Tag 格式: {versionCode}-{versionName}-{forceFlag}
     * 例如: 1-1.0.0-1 (强制更新), 1-1.0.0-0 (不强制), 1-1.0.0 (默认不强制)
     */
    private List<ReleaseInfo> parseReleasesInfo(String json) {
        List<ReleaseInfo> releases = new ArrayList<>();
        try {
            LogUtils.d(TAG, "开始解析 Releases JSON");
            JSONArray releasesArray = new JSONArray(json);
            LogUtils.param(TAG, "JSON 数组长度", releasesArray.length());
            
            for (int i = 0; i < releasesArray.length(); i++) {
                JSONObject release = releasesArray.getJSONObject(i);
                
                if (release.optBoolean("draft", false)) {
                    LogUtils.d(TAG, "跳过草稿版本");
                    continue;
                }
                
                ReleaseInfo info = new ReleaseInfo();
                info.tagName = release.optString("tag_name", "");
                info.releaseDate = release.optString("published_at", "");
                info.releaseNotes = release.optString("body", "");
                info.isPreRelease = release.optBoolean("prerelease", false);
                
                LogUtils.d(TAG, "解析版本: " + info.tagName);
                
                // 解析 tag_name (格式: versionCode-versionName-forceFlag)
                // 例如: 1-1.0.0-1, 1-1.0.0-0, 1-1.0.0
                String[] parts = info.tagName.split("-", 3);
                if (parts.length >= 2) {
                    info.versionCode = parts[0];
                    info.versionName = parts[1];
                    if (parts.length >= 3) {
                        info.forceFlag = parts[2];
                        info.forceUpdate = "1".equals(info.forceFlag);
                    } else {
                        info.forceFlag = "0";
                        info.forceUpdate = false;
                    }
                } else {
                    info.versionName = info.tagName;
                    info.versionCode = String.valueOf(getVersionCodeFromName(info.tagName));
                    info.forceFlag = "0";
                    info.forceUpdate = false;
                }
                
                LogUtils.d(TAG, "版本信息: versionCode=" + info.versionCode + 
                        ", versionName=" + info.versionName + 
                        ", forceUpdate=" + info.forceUpdate);
                
                JSONArray assets = release.optJSONArray("assets");
                if (assets != null && assets.length() > 0) {
                    LogUtils.d(TAG, "找到 " + assets.length() + " 个附件");
                    for (int j = 0; j < assets.length(); j++) {
                        JSONObject asset = assets.getJSONObject(j);
                        String name = asset.optString("name", "");
                        if (name.endsWith(".apk")) {
                            info.downloadUrl = asset.optString("browser_download_url", "");
                            long size = asset.optLong("size", 0);
                            info.apkSize = formatSize(size);
                            LogUtils.d(TAG, "找到 APK: " + name + ", 大小: " + info.apkSize);
                            break;
                        }
                    }
                }
                
                if (info.downloadUrl == null || info.downloadUrl.isEmpty()) {
                    info.downloadUrl = "https://github.com/Undefined-Invalid-Null/UIN-Tool/releases/tag/" + info.tagName;
                    info.apkSize = "未知";
                    LogUtils.d(TAG, "未找到 APK，使用仓库地址");
                }
                
                releases.add(info);
            }
            
            // 按版本代码降序排序（最新的在前）
            Collections.sort(releases, new Comparator<ReleaseInfo>() {
                @Override
                public int compare(ReleaseInfo o1, ReleaseInfo o2) {
                    try {
                        int code1 = Integer.parseInt(o1.versionCode);
                        int code2 = Integer.parseInt(o2.versionCode);
                        return Integer.compare(code2, code1);
                    } catch (Exception e) {
                        return 0;
                    }
                }
            });
            
            LogUtils.i(TAG, "解析完成，共 " + releases.size() + " 个 Release");
            
        } catch (Exception e) {
            LogUtils.e(TAG, "解析 Releases 信息失败", e);
        }
        return releases;
    }
    
    /**
     * 检查是否是强制更新（用于启动时判断）
     */
    public boolean isForceUpdateRequired() {
        try {
            String apiUrl = GITHUB_API + "?per_page=1";
            String mirroredUrl = getMirrorUrl(currentMirror, apiUrl);
            
            Request request = new Request.Builder()
                    .url(mirroredUrl)
                    .header("Accept", "application/vnd.github.v3+json")
                    .header("User-Agent", "UIN-Tool-Android")
                    .build();
            
            try (Response response = client.newCall(request).execute()) {
                if (response.isSuccessful()) {
                    String responseBody = response.body().string();
                    JSONArray releases = new JSONArray(responseBody);
                    if (releases.length() > 0) {
                        JSONObject release = releases.getJSONObject(0);
                        String tagName = release.optString("tag_name", "");
                        String[] parts = tagName.split("-", 3);
                        
                        int currentVersionCode = getCurrentVersionCode();
                        int newVersionCode = 0;
                        try {
                            newVersionCode = Integer.parseInt(parts[0]);
                        } catch (Exception e) {
                            return false;
                        }
                        
                        boolean hasNewer = newVersionCode > currentVersionCode;
                        boolean forceUpdate = false;
                        if (parts.length >= 3) {
                            forceUpdate = "1".equals(parts[2]);
                        }
                        
                        // 检查是否已被忽略
                        if (forceUpdate && hasNewer) {
                            SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
                            String ignoredVersion = prefs.getString(KEY_FORCE_UPDATE_IGNORE, "");
                            if (tagName.equals(ignoredVersion)) {
                                return false;
                            }
                            return true;
                        }
                    }
                }
            }
        } catch (Exception e) {
            LogUtils.e(TAG, "检查强制更新失败", e);
        }
        return false;
    }
    
    /**
     * 忽略本次强制更新（仅用于测试，不建议用户使用）
     */
    public void ignoreForceUpdate(String tagName) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_FORCE_UPDATE_IGNORE, tagName).apply();
    }
    
    private int getVersionCodeFromName(String versionName) {
        try {
            String[] parts = versionName.split("\\.");
            int code = 0;
            for (int i = 0; i < parts.length; i++) {
                code = code * 100 + Integer.parseInt(parts[i]);
            }
            return code;
        } catch (Exception e) {
            return 1;
        }
    }
    
    private String getCurrentVersionName() {
        try {
            PackageManager pm = context.getPackageManager();
            PackageInfo pi = pm.getPackageInfo(context.getPackageName(), 0);
            return pi.versionName;
        } catch (Exception e) {
            return "1.0.0";
        }
    }
    
    private int getCurrentVersionCode() {
        try {
            PackageManager pm = context.getPackageManager();
            PackageInfo pi = pm.getPackageInfo(context.getPackageName(), 0);
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                return (int) pi.getLongVersionCode();
            } else {
                return pi.versionCode;
            }
        } catch (Exception e) {
            return 1;
        }
    }
    
    private String formatSize(long size) {
        if (size <= 0) return "未知";
        if (size < 1024) return size + " B";
        if (size < 1024 * 1024) return String.format("%.2f KB", size / 1024.0);
        if (size < 1024 * 1024 * 1024) return String.format("%.2f MB", size / (1024.0 * 1024.0));
        return String.format("%.2f GB", size / (1024.0 * 1024.0 * 1024.0));
    }
    
    public void openDownloadPage(String url) {
        if (url == null || url.isEmpty()) {
            url = GITHUB_REPO;
        }
        LogUtils.action(TAG, "打开下载页面", url);
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }
    
    public void openReleasePage() {
        LogUtils.action(TAG, "打开 Release 页面", GITHUB_REPO);
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_REPO));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }
}