package com.UIN.Tool.plugin;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.JsResult;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import com.UIN.Tool.R;
import com.UIN.Tool.utils.FileUtils;
import com.UIN.Tool.utils.LogUtils;
import com.UIN.Tool.utils.PreferencesUtils;
import com.UIN.Tool.widget.UINWidgetProvider;
import com.UIN.Tool.widget.Widget1x1Provider;

import dalvik.system.DexClassLoader;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class PluginManager {
    private static PluginManager instance;
    private Context appContext;
    private String workFolder;
    private Map<String, PluginInfo> loadedPlugins = new HashMap<>();
    private Map<String, DexClassLoader> classLoaders = new HashMap<>();
    private Map<String, PluginInterface> pluginInstances = new HashMap<>();
    private Map<String, WebView> webViewCache = new HashMap<>();
    private Map<String, String> pluginBaseUrls = new HashMap<>();
    private Handler mainHandler;

    private static final boolean ENFORCE_SIGNATURE_CHECK = true;
    private static boolean ignoreSignatureWarning = false;

    private PluginManager(Context context) {
        this.appContext = context.getApplicationContext();
        this.mainHandler = new Handler(Looper.getMainLooper());
        refreshWorkFolder();
        LogUtils.i("PluginManager", "PluginManager 初始化完成");
    }

    public static PluginManager getInstance(Context context) {
        if (instance == null) {
            instance = new PluginManager(context);
        }
        return instance;
    }

    public static void setIgnoreSignatureWarning(boolean ignore) {
        ignoreSignatureWarning = ignore;
        LogUtils.i("PluginManager", "忽略签名验证: " + ignore);
    }

    public static boolean isIgnoreSignatureWarning() {
        return ignoreSignatureWarning;
    }

    private void showToast(String message, int duration) {
        mainHandler.post(() -> Toast.makeText(appContext, message, duration).show());
    }

    private String getAppVersion() {
        try {
            PackageManager pm = appContext.getPackageManager();
            PackageInfo pi = pm.getPackageInfo(appContext.getPackageName(), 0);
            return pi.versionName;
        } catch (Exception e) {
            LogUtils.e("PluginManager", "获取版本失败", e);
            return "1.0";
        }
    }

    private boolean verifyFileSignature(File file) {
        if (ignoreSignatureWarning) {
            LogUtils.w("PluginManager", "签名验证已忽略，跳过签名检查");
            return true;
        }
        
        if (!ENFORCE_SIGNATURE_CHECK) {
            return true;
        }
        
        try {
            String fileHash = getFileHash(file);
            if (fileHash == null) {
                LogUtils.e("PluginManager", "无法计算文件哈希");
                return false;
            }
            
            String expectedHash = PreferencesUtils.getPluginSignature(appContext, file.getName());
            if (expectedHash == null || expectedHash.isEmpty()) {
                PreferencesUtils.savePluginSignature(appContext, file.getName(), fileHash);
                LogUtils.i("PluginManager", "首次导入，记录签名");
                return true;
            }
            
            if (!expectedHash.equals(fileHash)) {
                LogUtils.e("PluginManager", "签名验证失败！文件可能被篡改");
                return false;
            }
            
            LogUtils.success("PluginManager", "签名验证通过");
            return true;
            
        } catch (Exception e) {
            LogUtils.e("PluginManager", "签名验证异常: " + e.getMessage(), e);
            return false;
        }
    }
    
    private String getFileHash(File file) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            FileInputStream fis = new FileInputStream(file);
            byte[] buffer = new byte[8192];
            int len;
            while ((len = fis.read(buffer)) > 0) {
                md.update(buffer, 0, len);
            }
            fis.close();
            byte[] hash = md.digest();
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                hexString.append(String.format("%02x", b));
            }
            return hexString.toString();
        } catch (Exception e) {
            LogUtils.e("PluginManager", "计算哈希失败: " + e.getMessage(), e);
            return null;
        }
    }

    public void refreshWorkFolder() {
        String newWorkFolder = PreferencesUtils.getWorkFolder(appContext);
        if (newWorkFolder == null || newWorkFolder.isEmpty()) {
            newWorkFolder = "/storage/emulated/0/UIN_Tool";
            PreferencesUtils.setWorkFolder(appContext, newWorkFolder);
        }
        this.workFolder = newWorkFolder;
        File workDir = new File(workFolder);
        if (!workDir.exists()) {
            workDir.mkdirs();
            LogUtils.i("PluginManager", "创建工作目录: " + workFolder);
        }
        loadedPlugins.clear();
        classLoaders.clear();
        pluginInstances.clear();
        webViewCache.clear();
        pluginBaseUrls.clear();
        LogUtils.i("PluginManager", "工作目录: " + workFolder);
    }

    public void refreshWorkFolder(Context context) {
        String newWorkFolder = PreferencesUtils.getWorkFolder(context);
        if (newWorkFolder == null || newWorkFolder.isEmpty()) {
            newWorkFolder = "/storage/emulated/0/UIN_Tool";
            PreferencesUtils.setWorkFolder(context, newWorkFolder);
        }
        this.workFolder = newWorkFolder;
        File workDir = new File(workFolder);
        if (!workDir.exists()) {
            workDir.mkdirs();
        }
        loadedPlugins.clear();
        classLoaders.clear();
        pluginInstances.clear();
        webViewCache.clear();
        pluginBaseUrls.clear();
        LogUtils.i("PluginManager", "刷新工作目录: " + workFolder);
    }

    public String getWorkFolder() {
        return workFolder;
    }

    private File getPluginDir(String pluginId) {
        if (pluginId == null || pluginId.isEmpty()) return null;
        String safeId = pluginId.replace("/", "_").replace("\\", "_").replace(":", "_").replace("*", "_");
        return new File(workFolder, safeId);
    }

    public File getPluginDirFile(String pluginId) {
        return getPluginDir(pluginId);
    }

    public boolean isPluginInstalled(String pluginId) {
        File pluginDir = getPluginDir(pluginId);
        return pluginDir != null && pluginDir.exists() && new File(pluginDir, "plugin.json").exists();
    }

    public PluginInfo installPlugin(String tpkFilePath, String originalFileName) {
        long startTime = System.currentTimeMillis();
        LogUtils.enter("PluginManager", "installPlugin");
        LogUtils.param("PluginManager", "文件路径", tpkFilePath);
        LogUtils.param("PluginManager", "原始文件名", originalFileName);
        
        try {
            File tpkFile = new File(tpkFilePath);
            if (!tpkFile.exists()) {
                showToast("文件不存在", Toast.LENGTH_SHORT);
                LogUtils.e("PluginManager", "文件不存在: " + tpkFilePath);
                return null;
            }

            if (!verifyFileSignature(tpkFile)) {
                showToast("插件签名验证失败，文件可能被篡改", Toast.LENGTH_LONG);
                LogUtils.e("PluginManager", "插件签名验证失败: " + originalFileName);
                return null;
            }

            File tempDir = new File(appContext.getCacheDir(), "temp_plugin_" + System.currentTimeMillis());
            boolean unzipSuccess = unzipWithValidation(tpkFile, tempDir);
            
            if (!unzipSuccess) {
                FileUtils.deleteDir(tempDir);
                showToast("文件不是有效的插件包，解压失败", Toast.LENGTH_SHORT);
                LogUtils.e("PluginManager", "解压失败: " + originalFileName);
                return null;
            }

            File jsonFile = new File(tempDir, "plugin.json");
            if (!jsonFile.exists()) {
                FileUtils.deleteDir(tempDir);
                showToast("插件包缺少 plugin.json 文件", Toast.LENGTH_SHORT);
                LogUtils.e("PluginManager", "缺少 plugin.json: " + originalFileName);
                return null;
            }

            String jsonContent = FileUtils.readFileToString(jsonFile);
            PluginInfo info = PluginInfo.fromJson(jsonContent);
            if (info == null || info.pluginId == null || info.pluginId.isEmpty()) {
                FileUtils.deleteDir(tempDir);
                showToast("plugin.json 格式错误", Toast.LENGTH_SHORT);
                LogUtils.e("PluginManager", "plugin.json 格式错误: " + originalFileName);
                return null;
            }

            int hostVersion = Build.VERSION.SDK_INT;
            if (info.minHostVersion > hostVersion) {
                FileUtils.deleteDir(tempDir);
                showToast("插件需要更高版本的 Android 系统", Toast.LENGTH_SHORT);
                LogUtils.e("PluginManager", "Android 版本过低: " + info.minHostVersion + " > " + hostVersion);
                return null;
            }

            if (!"web".equals(info.uiType)) {
                File dexFile = new File(tempDir, "plugin.dex");
                if (!dexFile.exists()) {
                    FileUtils.deleteDir(tempDir);
                    showToast("原生插件缺少 plugin.dex 文件", Toast.LENGTH_SHORT);
                    LogUtils.e("PluginManager", "缺少 plugin.dex: " + originalFileName);
                    return null;
                }
                
                if (!verifyFileSignature(dexFile)) {
                    FileUtils.deleteDir(tempDir);
                    showToast("插件 DEX 签名验证失败", Toast.LENGTH_LONG);
                    LogUtils.e("PluginManager", "DEX 签名验证失败: " + originalFileName);
                    return null;
                }
            }

            File pluginDir = getPluginDir(info.pluginId);
            if (pluginDir == null) {
                FileUtils.deleteDir(tempDir);
                showToast("插件ID无效", Toast.LENGTH_SHORT);
                LogUtils.e("PluginManager", "插件ID无效: " + info.pluginId);
                return null;
            }
            
            if (pluginDir.exists()) {
                FileUtils.deleteDir(pluginDir);
                LogUtils.i("PluginManager", "删除旧插件: " + info.pluginId);
            }
            pluginDir.mkdirs();

            copyDirWithoutSourceName(tempDir, pluginDir);
            FileUtils.deleteDir(tempDir);

            loadedPlugins.put(info.pluginId, info);
            LogUtils.success("PluginManager", "导入成功: " + info.name + " (" + info.pluginId + ")");
            showToast("导入成功：" + info.name, Toast.LENGTH_SHORT);
            
            notifyWidgetsRefresh();
            
            LogUtils.exit("PluginManager", "installPlugin", startTime);
            return info;

        } catch (Exception e) {
            LogUtils.e("PluginManager", "导入异常: " + e.getMessage(), e);
            showToast("导入失败：" + e.getMessage(), Toast.LENGTH_SHORT);
            LogUtils.exit("PluginManager", "installPlugin", startTime);
            return null;
        }
    }

    private void copyDirWithoutSourceName(File src, File dst) {
        if (!src.exists()) return;
        if (src.isDirectory()) {
            if (!dst.exists()) dst.mkdirs();
            File[] children = src.listFiles();
            if (children != null) {
                for (File child : children) {
                    if (child.getName().equals(".source_name")) continue;
                    copyDirWithoutSourceName(child, new File(dst, child.getName()));
                }
            }
        } else {
            try {
                FileInputStream in = new FileInputStream(src);
                FileOutputStream out = new FileOutputStream(dst);
                byte[] buffer = new byte[8192];
                int len;
                while ((len = in.read(buffer)) > 0) {
                    out.write(buffer, 0, len);
                }
                in.close();
                out.close();
            } catch (Exception e) {
                LogUtils.e("PluginManager", "复制文件失败: " + e.getMessage(), e);
            }
        }
    }

    private boolean unzipWithValidation(File zipFile, File destDir) {
        try {
            if (!destDir.exists()) destDir.mkdirs();

            ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile));
            ZipEntry entry;
            byte[] buffer = new byte[8192];
            boolean hasValidEntry = false;

            while ((entry = zis.getNextEntry()) != null) {
                hasValidEntry = true;
                File targetFile = new File(destDir, entry.getName());

                if (entry.isDirectory()) {
                    targetFile.mkdirs();
                } else {
                    targetFile.getParentFile().mkdirs();
                    FileOutputStream fos = new FileOutputStream(targetFile);
                    int len;
                    while ((len = zis.read(buffer)) > 0) {
                        fos.write(buffer, 0, len);
                    }
                    fos.close();
                }
                zis.closeEntry();
            }
            zis.close();
            
            return hasValidEntry;
        } catch (Exception e) {
            LogUtils.e("PluginManager", "解压失败: " + e.getMessage(), e);
            return false;
        }
    }

    public View getPluginView(String pluginId, Context context, ViewGroup container) {
        LogUtils.action("PluginManager", "获取插件视图", pluginId);
        long startTime = System.currentTimeMillis();
        
        PluginInfo info = getPluginInfo(pluginId);
        if (info == null) {
            LogUtils.e("PluginManager", "插件不存在: " + pluginId);
            return null;
        }
        
        LogUtils.param("PluginManager", "插件名称", info.name);
        LogUtils.param("PluginManager", "UI类型", info.uiType);
        
        View result;
        if ("web".equals(info.uiType)) {
            result = createWebViewForPlugin(pluginId, context, info);
        } else {
            result = loadNativePluginView(pluginId, context, container, info);
        }
        
        LogUtils.exit("PluginManager", "getPluginView", startTime);
        return result;
    }
    
    @SuppressLint("SetJavaScriptEnabled")
    private WebView createWebViewForPlugin(String pluginId, Context context, PluginInfo info) {
        long startTime = System.currentTimeMillis();
        LogUtils.enter("PluginManager", "createWebViewForPlugin");
        LogUtils.param("PluginManager", "插件ID", pluginId);
        LogUtils.param("PluginManager", "插件名称", info.name);
        
        try {
            File pluginDir = getPluginDir(pluginId);
            if (pluginDir == null) return null;
            
            String baseUrl = "file://" + pluginDir.getAbsolutePath() + "/";
            pluginBaseUrls.put(pluginId, baseUrl);
            
            WebView cachedWebView = webViewCache.get(pluginId);
            if (cachedWebView != null && cachedWebView.getParent() == null) {
                String url = baseUrl + info.entry;
                cachedWebView.loadUrl(url);
                LogUtils.i("PluginManager", "复用 WebView: " + info.name);
                return cachedWebView;
            }
            
            WebView webView = new WebView(context);
            WebSettings settings = webView.getSettings();
            
            settings.setJavaScriptEnabled(true);
            settings.setJavaScriptCanOpenWindowsAutomatically(true);
            settings.setDomStorageEnabled(true);
            settings.setDatabaseEnabled(true);
            settings.setCacheMode(WebSettings.LOAD_DEFAULT);
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
            }
            
            settings.setBuiltInZoomControls(true);
            settings.setDisplayZoomControls(false);
            settings.setSupportZoom(true);
            settings.setLoadWithOverviewMode(true);
            settings.setUseWideViewPort(true);
            settings.setAllowFileAccess(true);
            settings.setAllowFileAccessFromFileURLs(true);
            settings.setAllowUniversalAccessFromFileURLs(true);
            settings.setAllowContentAccess(true);
            settings.setDefaultFontSize(16);
            settings.setLoadsImagesAutomatically(true);
            settings.setBlockNetworkImage(false);
            settings.setBlockNetworkLoads(false);
            settings.setMediaPlaybackRequiresUserGesture(false);
            settings.setDefaultTextEncodingName("UTF-8");
            settings.setSupportMultipleWindows(false);
            
            String databasePath = context.getDir("database", Context.MODE_PRIVATE).getPath();
            settings.setDatabasePath(databasePath);
            settings.setGeolocationEnabled(true);
            settings.setGeolocationDatabasePath(databasePath);
            settings.setSaveFormData(true);
            
            String userAgent = settings.getUserAgentString();
            settings.setUserAgentString(userAgent + " UINTool/" + getAppVersion());
            
            CookieManager.getInstance().setAcceptCookie(true);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);
            }
            
            webView.setWebChromeClient(new WebChromeClient() {
                @Override
                public void onProgressChanged(WebView view, int newProgress) {
                    super.onProgressChanged(view, newProgress);
                    if (newProgress == 100) {
                        LogUtils.success("PluginManager", "Web 插件加载完成: " + info.name);
                    }
                }
                
                @Override
                public void onReceivedTitle(WebView view, String title) {
                    super.onReceivedTitle(view, title);
                    LogUtils.i("PluginManager", "页面标题: " + title);
                }
                
                @Override
                public boolean onConsoleMessage(android.webkit.ConsoleMessage consoleMessage) {
                    LogUtils.d("WebView[" + pluginId + "]", 
                        consoleMessage.message() + " (line " + consoleMessage.lineNumber() + ")");
                    return true;
                }
                
                @Override
                public boolean onJsAlert(WebView view, String url, String message, JsResult result) {
                    new android.app.AlertDialog.Builder(context)
                        .setTitle("提示")
                        .setMessage(message)
                        .setPositiveButton("确定", (dialog, which) -> result.confirm())
                        .setCancelable(false)
                        .show();
                    result.confirm();
                    return true;
                }
                
                @Override
                public boolean onJsConfirm(WebView view, String url, String message, JsResult result) {
                    new android.app.AlertDialog.Builder(context)
                        .setTitle("确认")
                        .setMessage(message)
                        .setPositiveButton("确定", (dialog, which) -> result.confirm())
                        .setNegativeButton("取消", (dialog, which) -> result.cancel())
                        .show();
                    return true;
                }
            });
            
            webView.setWebViewClient(new WebViewClient() {
                @Override
                public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                    String url = request.getUrl().toString();
                    if (url.startsWith("http://") || url.startsWith("https://")) {
                        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        context.startActivity(intent);
                        return true;
                    }
                    view.loadUrl(url);
                    return true;
                }
                
                @Override
                public void onPageFinished(WebView view, String url) {
                    super.onPageFinished(view, url);
                    LogUtils.success("PluginManager", "页面加载完成: " + url);
                }
                
                @Override
                public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                    LogUtils.e("PluginManager", "WebView 加载错误: " + description);
                }
            });
            
            PluginJSInterface jsInterface = new PluginJSInterface(appContext, pluginId, info);
            webView.addJavascriptInterface(jsInterface, "UINPlugin");
            
            String entryPath = info.entry != null && !info.entry.isEmpty() ? info.entry : "web/index.html";
            String url = baseUrl + entryPath;
            webView.loadUrl(url);
            
            webViewCache.put(pluginId, webView);
            
            LogUtils.success("PluginManager", "创建 WebView: " + info.name + " -> " + url);
            LogUtils.exit("PluginManager", "createWebViewForPlugin", startTime);
            return webView;
            
        } catch (Exception e) {
            LogUtils.e("PluginManager", "创建 WebView 失败: " + e.getMessage(), e);
            LogUtils.exit("PluginManager", "createWebViewForPlugin", startTime);
            return null;
        }
    }
    
    private View loadNativePluginView(String pluginId, Context context, ViewGroup container, PluginInfo info) {
        long startTime = System.currentTimeMillis();
        LogUtils.enter("PluginManager", "loadNativePluginView");
        LogUtils.param("PluginManager", "插件ID", pluginId);
        LogUtils.param("PluginManager", "插件名称", info.name);
        
        try {
            DexClassLoader classLoader = classLoaders.get(pluginId);
            if (classLoader == null) {
                File pluginDir = getPluginDir(pluginId);
                if (pluginDir == null) return null;
                File dexFile = new File(pluginDir, "plugin.dex");
                if (!dexFile.exists()) return null;

                File optDir = new File(appContext.getCodeCacheDir(), "opt/" + pluginId);
                optDir.mkdirs();

                classLoader = new DexClassLoader(
                        dexFile.getAbsolutePath(),
                        optDir.getAbsolutePath(),
                        null,
                        appContext.getClassLoader()
                );
                classLoaders.put(pluginId, classLoader);
                LogUtils.i("PluginManager", "加载原生插件: " + info.name);
            }

            Class<?> clazz = classLoader.loadClass(info.mainClass);
            PluginInterface plugin = (PluginInterface) clazz.newInstance();
            pluginInstances.put(pluginId, plugin);

            File pluginDir = getPluginDir(pluginId);
            if (pluginDir == null) return null;
            PluginContext pluginContext = new PluginContext(context, pluginDir.getAbsolutePath());

            View result = plugin.onCreateView(pluginContext, container, null);
            LogUtils.success("PluginManager", "原生插件加载成功: " + info.name);
            LogUtils.exit("PluginManager", "loadNativePluginView", startTime);
            return result;

        } catch (Exception e) {
            LogUtils.e("PluginManager", "加载原生插件异常: " + e.getMessage(), e);
            LogUtils.exit("PluginManager", "loadNativePluginView", startTime);
            return null;
        }
    }

    public void openPlugin(String pluginId, Context context) {
        LogUtils.action("PluginManager", "打开插件", pluginId);
        Intent intent = new Intent(context, PluginHostActivity.class);
        intent.putExtra(PluginHostActivity.EXTRA_PLUGIN_ID, pluginId);
        context.startActivity(intent);
    }

    public void onPluginResume(String pluginId) {
        LogUtils.d("PluginManager", "插件恢复: " + pluginId);
        PluginInterface plugin = pluginInstances.get(pluginId);
        if (plugin != null) plugin.onResume();
        
        WebView webView = webViewCache.get(pluginId);
        if (webView != null) {
            webView.evaluateJavascript("if(window.dispatchEvent) window.dispatchEvent(new Event('resume'));", null);
            webView.onResume();
        }
    }

    public void onPluginPause(String pluginId) {
        LogUtils.d("PluginManager", "插件暂停: " + pluginId);
        PluginInterface plugin = pluginInstances.get(pluginId);
        if (plugin != null) plugin.onPause();
        
        WebView webView = webViewCache.get(pluginId);
        if (webView != null) {
            webView.evaluateJavascript("if(window.dispatchEvent) window.dispatchEvent(new Event('pause'));", null);
            webView.onPause();
        }
    }

    public void onPluginDestroy(String pluginId) {
        LogUtils.d("PluginManager", "插件销毁: " + pluginId);
        PluginInterface plugin = pluginInstances.get(pluginId);
        if (plugin != null) plugin.onDestroy();
        pluginInstances.remove(pluginId);
        
        WebView webView = webViewCache.remove(pluginId);
        if (webView != null) {
            webView.evaluateJavascript("if(window.dispatchEvent) window.dispatchEvent(new Event('destroy'));", null);
            webView.loadUrl("about:blank");
            webView.clearHistory();
            webView.clearCache(true);
            webView.destroy();
        }
        pluginBaseUrls.remove(pluginId);
    }

    public boolean onPluginBackPressed(String pluginId) {
        PluginInterface plugin = pluginInstances.get(pluginId);
        if (plugin != null) return plugin.onBackPressed();
        
        WebView webView = webViewCache.get(pluginId);
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
            return true;
        }
        return false;
    }

    public List<PluginInfo> getInstalledPlugins() {
        List<PluginInfo> list = new ArrayList<>();
        File pluginsDir = new File(workFolder);
        if (!pluginsDir.exists() || !pluginsDir.isDirectory()) {
            pluginsDir.mkdirs();
            return list;
        }

        File[] files = pluginsDir.listFiles();
        if (files == null) return list;

        for (File pluginDir : files) {
            if (pluginDir.isDirectory() && !pluginDir.getName().startsWith(".")) {
                File jsonFile = new File(pluginDir, "plugin.json");
                if (jsonFile.exists()) {
                    String json = FileUtils.readFileToString(jsonFile);
                    PluginInfo info = PluginInfo.fromJson(json);
                    if (info != null && info.pluginId != null) {
                        list.add(info);
                        loadedPlugins.put(info.pluginId, info);
                    } else {
                        LogUtils.w("PluginManager", "无效的插件目录: " + pluginDir.getName());
                    }
                }
            }
        }
        LogUtils.i("PluginManager", "获取已安装插件列表，共 " + list.size() + " 个");
        return list;
    }
    
    public List<PluginInfo> searchPlugins(String keyword) {
        List<PluginInfo> result = new ArrayList<>();
        if (keyword == null || keyword.trim().isEmpty()) {
            return getInstalledPlugins();
        }
        
        String lowerKeyword = keyword.toLowerCase().trim();
        for (PluginInfo info : getInstalledPlugins()) {
            if (info.name.toLowerCase().contains(lowerKeyword) ||
                info.pluginId.toLowerCase().contains(lowerKeyword) ||
                (info.description != null && info.description.toLowerCase().contains(lowerKeyword)) ||
                (info.author != null && info.author.toLowerCase().contains(lowerKeyword))) {
                result.add(info);
            }
        }
        LogUtils.i("PluginManager", "搜索插件, 关键词: " + keyword + ", 结果: " + result.size() + " 个");
        return result;
    }

    public List<PluginInfo> getPluginsByCategory(String category) {
        List<PluginInfo> result = new ArrayList<>();
        String uncategorized = appContext.getString(R.string.uncategorized);
        for (PluginInfo info : getInstalledPlugins()) {
            String pluginCategory = (info.category == null || info.category.isEmpty()) ? uncategorized : info.category;
            if (category.equals(pluginCategory)) {
                result.add(info);
            }
        }
        return result;
    }

    public List<String> getAllCategories() {
        List<String> categories = new ArrayList<>();
        String allCategory = appContext.getString(R.string.all_category);
        String uncategorized = appContext.getString(R.string.uncategorized);
        
        categories.add(allCategory);
        categories.add(uncategorized);
        
        for (PluginInfo info : getInstalledPlugins()) {
            String category = (info.category == null || info.category.isEmpty()) ? uncategorized : info.category;
            if (!categories.contains(category)) {
                categories.add(category);
            }
        }
        return categories;
    }

    public List<String> getAllCategoriesWithSystem() {
        return getAllCategories();
    }

    public boolean addCategory(String categoryName) {
        if (categoryName == null || categoryName.trim().isEmpty()) {
            LogUtils.w("PluginManager", "分类名称不能为空");
            return false;
        }
        
        String trimmed = categoryName.trim();
        List<String> existing = getAllCategories();
        
        if (existing.contains(trimmed)) {
            LogUtils.w("PluginManager", "分类已存在: " + trimmed);
            return false;
        }
        
        LogUtils.action("PluginManager", "添加分类", trimmed);
        return true;
    }

    public boolean deleteCategory(String categoryName) {
        if (categoryName == null || categoryName.isEmpty()) {
            return false;
        }
        
        String allCategory = appContext.getString(R.string.all_category);
        String uncategorized = appContext.getString(R.string.uncategorized);
        
        if (categoryName.equals(allCategory) || categoryName.equals(uncategorized)) {
            LogUtils.w("PluginManager", "不能删除系统分类: " + categoryName);
            return false;
        }
        
        LogUtils.action("PluginManager", "删除分类", categoryName);
        
        List<PluginInfo> pluginsInCategory = getPluginsByCategory(categoryName);
        boolean success = true;
        
        for (PluginInfo info : pluginsInCategory) {
            if (!updatePluginCategory(info.pluginId, uncategorized)) {
                success = false;
                LogUtils.e("PluginManager", "移动插件失败: " + info.name);
            }
        }
        
        if (success) {
            LogUtils.success("PluginManager", "分类删除成功: " + categoryName);
        }
        
        return success;
    }

    public boolean updatePluginCategory(String pluginId, String newCategory) {
        LogUtils.action("PluginManager", "更新插件分类", pluginId + " -> " + newCategory);
        try {
            File pluginDir = getPluginDir(pluginId);
            if (pluginDir == null) return false;
            
            File jsonFile = new File(pluginDir, "plugin.json");
            if (!jsonFile.exists()) return false;
            
            String json = FileUtils.readFileToString(jsonFile);
            PluginInfo info = PluginInfo.fromJson(json);
            if (info == null) return false;
            
            info.category = newCategory;
            
            FileOutputStream fos = new FileOutputStream(jsonFile);
            fos.write(info.toJson().getBytes());
            fos.close();
            
            if (loadedPlugins.containsKey(pluginId)) {
                loadedPlugins.get(pluginId).category = newCategory;
            }
            
            LogUtils.success("PluginManager", "插件分类更新成功: " + pluginId + " -> " + newCategory);
            return true;
        } catch (Exception e) {
            LogUtils.e("PluginManager", "更新分类失败: " + e.getMessage(), e);
            return false;
        }
    }

    private PluginInfo getInstalledPluginInfo(String pluginId) {
        File pluginDir = getPluginDir(pluginId);
        if (pluginDir == null) return null;
        File jsonFile = new File(pluginDir, "plugin.json");
        if (jsonFile.exists()) {
            String json = FileUtils.readFileToString(jsonFile);
            return PluginInfo.fromJson(json);
        }
        return null;
    }

    public boolean uninstallPlugin(String pluginId) {
        LogUtils.action("PluginManager", "卸载插件", pluginId);
        File pluginDir = getPluginDir(pluginId);
        File optDir = new File(appContext.getCodeCacheDir(), "opt/" + pluginId);
        if (pluginDir != null) FileUtils.deleteDir(pluginDir);
        FileUtils.deleteDir(optDir);
        
        WebView webView = webViewCache.remove(pluginId);
        if (webView != null) {
            webView.destroy();
        }
        
        loadedPlugins.remove(pluginId);
        classLoaders.remove(pluginId);
        pluginInstances.remove(pluginId);
        pluginBaseUrls.remove(pluginId);
        PreferencesUtils.removePluginSignature(appContext, pluginId + ".tpk");
        
        notifyWidgetsRefresh();
        
        LogUtils.success("PluginManager", "插件卸载成功: " + pluginId);
        showToast("已卸载插件", Toast.LENGTH_SHORT);
        return true;
    }

    public PluginInfo getPluginInfo(String pluginId) {
        if (loadedPlugins.containsKey(pluginId)) {
            return loadedPlugins.get(pluginId);
        }
        return getInstalledPluginInfo(pluginId);
    }
    
    public void clearWebViewCache() {
        LogUtils.action("PluginManager", "清理 WebView 缓存", "");
        for (WebView webView : webViewCache.values()) {
            if (webView != null) {
                webView.destroy();
            }
        }
        webViewCache.clear();
        LogUtils.i("PluginManager", "WebView 缓存已清理");
    }

    public void refreshAndNotifyWidgets(Context context) {
        refreshWorkFolder(context);
        notifyWidgetsRefresh();
        LogUtils.i("PluginManager", "Refreshed and notified widgets");
    }

    public void onPluginsChanged(Context context) {
        refreshAndNotifyWidgets(context);
    }

    private void notifyWidgetsRefresh() {
        try {
            UINWidgetProvider.sendIntentToRefreshAllWidgets(appContext);
            Widget1x1Provider.sendRefreshIntent(appContext);
            LogUtils.i("PluginManager", "Widgets refresh notification sent");
        } catch (Exception e) {
            LogUtils.e("PluginManager", "Failed to notify widgets: " + e.getMessage(), e);
        }
    }
}