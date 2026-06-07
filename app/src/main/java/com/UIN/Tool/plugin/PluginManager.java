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

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.lang.reflect.Method;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 插件管理器
 * 负责插件的安装、卸载、加载、生命周期管理等
 */
public class PluginManager {
    
    private static final String TAG = "PluginManager";
    private static PluginManager instance;
    
    private Context appContext;
    private String workFolder;
    
    // 插件信息缓存
    private Map<String, PluginInfo> loadedPlugins = new HashMap<>();
    
    // 原生插件相关
    private Map<String, DexClassLoader> classLoaders = new HashMap<>();
    private Map<String, PluginInterface> pluginInstances = new HashMap<>();
    
    // Web 插件相关
    private Map<String, WebView> webViewCache = new HashMap<>();
    private Map<String, String> pluginBaseUrls = new HashMap<>();
    
    private Handler mainHandler;
    
    // 签名验证配置
    private static final boolean ENFORCE_SIGNATURE_CHECK = true;
    private static boolean ignoreSignatureWarning = false;
    
    // 支持的插件文件扩展名
    private static final String[] SUPPORTED_EXTENSIONS = {".tpk", ".zip"};
    
    /**
     * 私有构造函数
     */
    private PluginManager(Context context) {
        this.appContext = context.getApplicationContext();
        this.mainHandler = new Handler(Looper.getMainLooper());
        refreshWorkFolder();
        LogUtils.i(TAG, "PluginManager 初始化完成");
    }
    
    /**
     * 获取单例实例
     */
    public static PluginManager getInstance(Context context) {
        if (instance == null) {
            instance = new PluginManager(context);
        }
        return instance;
    }
    
    /**
     * 设置是否忽略签名验证（仅用于开发调试）
     */
    public static void setIgnoreSignatureWarning(boolean ignore) {
        ignoreSignatureWarning = ignore;
        LogUtils.i(TAG, "忽略签名验证: " + ignore);
    }
    
    /**
     * 获取是否忽略签名验证
     */
    public static boolean isIgnoreSignatureWarning() {
        return ignoreSignatureWarning;
    }
    
    /**
     * 显示 Toast 提示
     */
    private void showToast(String message, int duration) {
        mainHandler.post(() -> Toast.makeText(appContext, message, duration).show());
    }
    
    /**
     * 获取应用版本号
     */
    private String getAppVersion() {
        try {
            PackageManager pm = appContext.getPackageManager();
            PackageInfo pi = pm.getPackageInfo(appContext.getPackageName(), 0);
            return pi.versionName;
        } catch (Exception e) {
            LogUtils.e(TAG, "获取版本失败", e);
            return "1.0";
        }
    }
    
    /**
     * 验证文件签名
     */
    private boolean verifyFileSignature(File file) {
        if (ignoreSignatureWarning) {
            LogUtils.w(TAG, "签名验证已忽略，跳过签名检查");
            return true;
        }
        
        if (!ENFORCE_SIGNATURE_CHECK) {
            return true;
        }
        
        try {
            String fileHash = getFileHash(file);
            if (fileHash == null) {
                LogUtils.e(TAG, "无法计算文件哈希");
                return false;
            }
            
            String expectedHash = PreferencesUtils.getPluginSignature(appContext, file.getName());
            if (expectedHash == null || expectedHash.isEmpty()) {
                PreferencesUtils.savePluginSignature(appContext, file.getName(), fileHash);
                LogUtils.i(TAG, "首次导入，记录签名");
                return true;
            }
            
            if (!expectedHash.equals(fileHash)) {
                LogUtils.e(TAG, "签名验证失败！文件可能被篡改");
                return false;
            }
            
            LogUtils.success(TAG, "签名验证通过");
            return true;
            
        } catch (Exception e) {
            LogUtils.e(TAG, "签名验证异常: " + e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * 计算文件 SHA-256 哈希值
     */
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
            LogUtils.e(TAG, "计算哈希失败: " + e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * 刷新工作目录
     */
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
            LogUtils.i(TAG, "创建工作目录: " + workFolder);
        }
        
        // 清空缓存
        loadedPlugins.clear();
        classLoaders.clear();
        pluginInstances.clear();
        webViewCache.clear();
        pluginBaseUrls.clear();
        
        LogUtils.i(TAG, "工作目录: " + workFolder);
    }
    
    /**
     * 刷新工作目录（带 Context 参数）
     */
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
        
        LogUtils.i(TAG, "刷新工作目录: " + workFolder);
    }
    
    /**
     * 获取工作目录
     */
    public String getWorkFolder() {
        return workFolder;
    }
    
    /**
     * 获取插件目录
     */
    private File getPluginDir(String pluginId) {
        if (pluginId == null || pluginId.isEmpty()) return null;
        String safeId = pluginId.replace("/", "_").replace("\\", "_").replace(":", "_").replace("*", "_");
        return new File(workFolder, safeId);
    }
    
    /**
     * 获取插件目录文件
     */
    public File getPluginDirFile(String pluginId) {
        return getPluginDir(pluginId);
    }
    
    /**
     * 检查插件是否已安装
     */
    public boolean isPluginInstalled(String pluginId) {
        File pluginDir = getPluginDir(pluginId);
        return pluginDir != null && pluginDir.exists() && new File(pluginDir, "plugin.json").exists();
    }
    
    /**
     * 安装插件
     * @param tpkFilePath TPK 文件路径
     * @param originalFileName 原始文件名
     * @return 安装的插件信息，失败返回 null
     */
    public PluginInfo installPlugin(String tpkFilePath, String originalFileName) {
        long startTime = System.currentTimeMillis();
        LogUtils.enter(TAG, "installPlugin");
        LogUtils.param(TAG, "文件路径", tpkFilePath);
        LogUtils.param(TAG, "原始文件名", originalFileName);
        
        try {
            File tpkFile = new File(tpkFilePath);
            if (!tpkFile.exists()) {
                showToast("文件不存在", Toast.LENGTH_SHORT);
                LogUtils.e(TAG, "文件不存在: " + tpkFilePath);
                return null;
            }

            // 验证签名
            if (!verifyFileSignature(tpkFile)) {
                showToast("插件签名验证失败，文件可能被篡改", Toast.LENGTH_LONG);
                LogUtils.e(TAG, "插件签名验证失败: " + originalFileName);
                return null;
            }

            // 解压到临时目录
            File tempDir = new File(appContext.getCacheDir(), "temp_plugin_" + System.currentTimeMillis());
            boolean unzipSuccess = unzipWithValidation(tpkFile, tempDir);
            
            if (!unzipSuccess) {
                FileUtils.deleteDir(tempDir);
                showToast("文件不是有效的插件包，解压失败", Toast.LENGTH_SHORT);
                LogUtils.e(TAG, "解压失败: " + originalFileName);
                return null;
            }

            // 读取 plugin.json
            File jsonFile = new File(tempDir, "plugin.json");
            if (!jsonFile.exists()) {
                FileUtils.deleteDir(tempDir);
                showToast("插件包缺少 plugin.json 文件", Toast.LENGTH_SHORT);
                LogUtils.e(TAG, "缺少 plugin.json: " + originalFileName);
                return null;
            }

            String jsonContent = FileUtils.readFileToString(jsonFile);
            PluginInfo info = PluginInfo.fromJson(jsonContent);
            if (info == null || info.pluginId == null || info.pluginId.isEmpty()) {
                FileUtils.deleteDir(tempDir);
                showToast("plugin.json 格式错误", Toast.LENGTH_SHORT);
                LogUtils.e(TAG, "plugin.json 格式错误: " + originalFileName);
                return null;
            }

            // 检查 Android 版本兼容性
            int hostVersion = Build.VERSION.SDK_INT;
            if (info.minHostVersion > hostVersion) {
                FileUtils.deleteDir(tempDir);
                showToast("插件需要更高版本的 Android 系统", Toast.LENGTH_SHORT);
                LogUtils.e(TAG, "Android 版本过低: " + info.minHostVersion + " > " + hostVersion);
                return null;
            }

            // 验证原生插件的 DEX 文件
            if (!"web".equals(info.uiType)) {
                File dexFile = new File(tempDir, "plugin.dex");
                if (!dexFile.exists()) {
                    FileUtils.deleteDir(tempDir);
                    showToast("原生插件缺少 plugin.dex 文件", Toast.LENGTH_SHORT);
                    LogUtils.e(TAG, "缺少 plugin.dex: " + originalFileName);
                    return null;
                }
                
                if (!verifyFileSignature(dexFile)) {
                    FileUtils.deleteDir(tempDir);
                    showToast("插件 DEX 签名验证失败", Toast.LENGTH_LONG);
                    LogUtils.e(TAG, "DEX 签名验证失败: " + originalFileName);
                    return null;
                }
            }

            // 复制到正式目录
            File pluginDir = getPluginDir(info.pluginId);
            if (pluginDir == null) {
                FileUtils.deleteDir(tempDir);
                showToast("插件ID无效", Toast.LENGTH_SHORT);
                LogUtils.e(TAG, "插件ID无效: " + info.pluginId);
                return null;
            }
            
            if (pluginDir.exists()) {
                FileUtils.deleteDir(pluginDir);
                LogUtils.i(TAG, "删除旧插件: " + info.pluginId);
            }
            pluginDir.mkdirs();

            copyDirWithoutSourceName(tempDir, pluginDir);
            FileUtils.deleteDir(tempDir);

            loadedPlugins.put(info.pluginId, info);
            LogUtils.success(TAG, "导入成功: " + info.name + " (" + info.pluginId + ")");
            showToast("导入成功：" + info.name, Toast.LENGTH_SHORT);
            
            notifyWidgetsRefresh();
            
            LogUtils.exit(TAG, "installPlugin", startTime);
            return info;

        } catch (Exception e) {
            LogUtils.e(TAG, "导入异常: " + e.getMessage(), e);
            showToast("导入失败：" + e.getMessage(), Toast.LENGTH_SHORT);
            LogUtils.exit(TAG, "installPlugin", startTime);
            return null;
        }
    }
    
    /**
     * 复制目录（不复制 .source_name 文件）
     */
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
                LogUtils.e(TAG, "复制文件失败: " + e.getMessage(), e);
            }
        }
    }
    
    /**
     * 解压并验证 ZIP 文件
     */
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

                // 安全检查：防止路径遍历攻击
                String canonicalPath = targetFile.getCanonicalPath();
                if (!canonicalPath.startsWith(destDir.getCanonicalPath())) {
                    LogUtils.w(TAG, "跳过恶意路径: " + entry.getName());
                    continue;
                }

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
            LogUtils.e(TAG, "解压失败: " + e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * 获取插件视图
     */
    public View getPluginView(String pluginId, Context context, ViewGroup container) {
        LogUtils.action(TAG, "获取插件视图", pluginId);
        long startTime = System.currentTimeMillis();
        
        PluginInfo info = getPluginInfo(pluginId);
        if (info == null) {
            LogUtils.e(TAG, "插件不存在: " + pluginId);
            return null;
        }
        
        LogUtils.param(TAG, "插件名称", info.name);
        LogUtils.param(TAG, "UI类型", info.uiType);
        
        View result;
        if ("web".equals(info.uiType)) {
            result = createWebViewForPlugin(pluginId, context, info);
        } else {
            result = loadNativePluginView(pluginId, context, container, info);
        }
        
        LogUtils.exit(TAG, "getPluginView", startTime);
        return result;
    }
    
    /**
     * 为 Web 插件创建 WebView
     */
    @SuppressLint("SetJavaScriptEnabled")
    private WebView createWebViewForPlugin(String pluginId, Context context, PluginInfo info) {
        long startTime = System.currentTimeMillis();
        LogUtils.enter(TAG, "createWebViewForPlugin");
        LogUtils.param(TAG, "插件ID", pluginId);
        LogUtils.param(TAG, "插件名称", info.name);
        
        try {
            File pluginDir = getPluginDir(pluginId);
            if (pluginDir == null) return null;
            
            String baseUrl = "file://" + pluginDir.getAbsolutePath() + "/";
            pluginBaseUrls.put(pluginId, baseUrl);
            
            // 检查缓存的 WebView
            WebView cachedWebView = webViewCache.get(pluginId);
            if (cachedWebView != null && cachedWebView.getParent() == null) {
                String url = baseUrl + info.entry;
                cachedWebView.loadUrl(url);
                LogUtils.i(TAG, "复用 WebView: " + info.name);
                return cachedWebView;
            }
            
            WebView webView = new WebView(context);
            WebSettings settings = webView.getSettings();
            
            // 配置 WebView
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
            
            // 数据库路径
            String databasePath = context.getDir("database", Context.MODE_PRIVATE).getPath();
            settings.setDatabasePath(databasePath);
            settings.setGeolocationEnabled(true);
            settings.setGeolocationDatabasePath(databasePath);
            settings.setSaveFormData(true);
            
            // 设置 User-Agent
            String userAgent = settings.getUserAgentString();
            settings.setUserAgentString(userAgent + " UINTool/" + getAppVersion());
            
            // Cookie 管理
            CookieManager.getInstance().setAcceptCookie(true);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);
            }
            
            // 设置 WebChromeClient
            webView.setWebChromeClient(new WebChromeClient() {
                @Override
                public void onProgressChanged(WebView view, int newProgress) {
                    super.onProgressChanged(view, newProgress);
                    if (newProgress == 100) {
                        LogUtils.success(TAG, "Web 插件加载完成: " + info.name);
                    }
                }
                
                @Override
                public void onReceivedTitle(WebView view, String title) {
                    super.onReceivedTitle(view, title);
                    LogUtils.i(TAG, "页面标题: " + title);
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
            
            // 设置 WebViewClient
            webView.setWebViewClient(new WebViewClient() {
                @Override
                public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                    String url = request.getUrl().toString();
                    // 外部链接用浏览器打开
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
                    LogUtils.success(TAG, "页面加载完成: " + url);
                }
                
                @Override
                public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                    LogUtils.e(TAG, "WebView 加载错误: " + description);
                }
            });
            
            // 添加 JavaScript 接口
            PluginJSInterface jsInterface = new PluginJSInterface(context, pluginId, info);
            webView.addJavascriptInterface(jsInterface, "UINPlugin");
            
            // 加载入口文件
            String entryPath = info.entry != null && !info.entry.isEmpty() ? info.entry : "web/index.html";
            String url = baseUrl + entryPath;
            webView.loadUrl(url);
            
            webViewCache.put(pluginId, webView);
            
            LogUtils.success(TAG, "创建 WebView: " + info.name + " -> " + url);
            LogUtils.exit(TAG, "createWebViewForPlugin", startTime);
            return webView;
            
        } catch (Exception e) {
            LogUtils.e(TAG, "创建 WebView 失败: " + e.getMessage(), e);
            LogUtils.exit(TAG, "createWebViewForPlugin", startTime);
            return null;
        }
    }
    
    /**
     * 加载原生插件视图
     */
    private View loadNativePluginView(String pluginId, Context context, ViewGroup container, PluginInfo info) {
        long startTime = System.currentTimeMillis();
        LogUtils.enter(TAG, "loadNativePluginView");
        LogUtils.param(TAG, "插件ID", pluginId);
        LogUtils.param(TAG, "插件名称", info.name);
        
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
                LogUtils.i(TAG, "加载原生插件: " + info.name);
            }

            Class<?> clazz = classLoader.loadClass(info.mainClass);
            PluginInterface plugin = (PluginInterface) clazz.newInstance();
            pluginInstances.put(pluginId, plugin);

            File pluginDir = getPluginDir(pluginId);
            if (pluginDir == null) return null;
            PluginContext pluginContext = new PluginContext(context, pluginDir.getAbsolutePath());

            View result = plugin.onCreateView(pluginContext, container, null);
            LogUtils.success(TAG, "原生插件加载成功: " + info.name);
            LogUtils.exit(TAG, "loadNativePluginView", startTime);
            return result;

        } catch (Exception e) {
            LogUtils.e(TAG, "加载原生插件异常: " + e.getMessage(), e);
            LogUtils.exit(TAG, "loadNativePluginView", startTime);
            return null;
        }
    }
    
    /**
     * 打开插件
     */
    public void openPlugin(String pluginId, Context context) {
        LogUtils.action(TAG, "打开插件", pluginId);
        Intent intent = new Intent(context, PluginHostActivity.class);
        intent.putExtra(PluginHostActivity.EXTRA_PLUGIN_ID, pluginId);
        context.startActivity(intent);
    }
    
    /**
     * 获取插件实例
     */
    public PluginInterface getPluginInstance(String pluginId) {
        return pluginInstances.get(pluginId);
    }
    
    /**
     * 插件恢复生命周期
     */
    public void onPluginResume(String pluginId) {
        LogUtils.d(TAG, "插件恢复: " + pluginId);
        PluginInterface plugin = pluginInstances.get(pluginId);
        if (plugin != null) plugin.onResume();
        
        WebView webView = webViewCache.get(pluginId);
        if (webView != null) {
            webView.evaluateJavascript("if(window.dispatchEvent) window.dispatchEvent(new Event('resume'));", null);
            webView.onResume();
        }
    }
    
    /**
     * 插件暂停生命周期
     */
    public void onPluginPause(String pluginId) {
        LogUtils.d(TAG, "插件暂停: " + pluginId);
        PluginInterface plugin = pluginInstances.get(pluginId);
        if (plugin != null) plugin.onPause();
        
        WebView webView = webViewCache.get(pluginId);
        if (webView != null) {
            webView.evaluateJavascript("if(window.dispatchEvent) window.dispatchEvent(new Event('pause'));", null);
            webView.onPause();
        }
    }
    
    /**
     * 插件销毁生命周期
     */
    public void onPluginDestroy(String pluginId) {
        LogUtils.d(TAG, "插件销毁: " + pluginId);
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
    
    /**
     * 插件返回键处理
     */
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
    
    /**
     * 获取已安装插件列表
     */
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
                    if (info != null && info.pluginId != null && !info.pluginId.isEmpty()) {
                        // 确保插件 ID 与目录名一致
                        if (!info.pluginId.equals(pluginDir.getName())) {
                            info.pluginId = pluginDir.getName();
                        }
                        list.add(info);
                        loadedPlugins.put(info.pluginId, info);
                    } else {
                        LogUtils.w(TAG, "无效的插件目录: " + pluginDir.getName());
                    }
                }
            }
        }
        LogUtils.i(TAG, "获取已安装插件列表，共 " + list.size() + " 个");
        return list;
    }
    
    /**
     * 搜索插件
     */
    public List<PluginInfo> searchPlugins(String keyword) {
        List<PluginInfo> result = new ArrayList<>();
        if (keyword == null || keyword.trim().isEmpty()) {
            return getInstalledPlugins();
        }
        
        String lowerKeyword = keyword.toLowerCase().trim();
        for (PluginInfo info : getInstalledPlugins()) {
            if ((info.name != null && info.name.toLowerCase().contains(lowerKeyword)) ||
                (info.pluginId != null && info.pluginId.toLowerCase().contains(lowerKeyword)) ||
                (info.description != null && info.description.toLowerCase().contains(lowerKeyword)) ||
                (info.author != null && info.author.toLowerCase().contains(lowerKeyword))) {
                result.add(info);
            }
        }
        LogUtils.i(TAG, "搜索插件, 关键词: " + keyword + ", 结果: " + result.size() + " 个");
        return result;
    }
    
    /**
     * 根据分类获取插件
     */
    public List<PluginInfo> getPluginsByCategory(String category) {
        List<PluginInfo> result = new ArrayList<>();
        for (PluginInfo info : getInstalledPlugins()) {
            String pluginCategory = (info.category == null || info.category.isEmpty()) ? "未分类" : info.category;
            if (category.equals(pluginCategory)) {
                result.add(info);
            }
        }
        return result;
    }
    
    /**
     * 获取所有分类
     */
    public List<String> getAllCategories() {
        List<String> categories = new ArrayList<>();
        String allCategory = "全部";
        String uncategorized = "未分类";
        
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
    
    /**
     * 获取所有分类（包含系统分类）
     */
    public List<String> getAllCategoriesWithSystem() {
        return getAllCategories();
    }
    
    /**
     * 添加分类
     */
    public boolean addCategory(String categoryName) {
        if (categoryName == null || categoryName.trim().isEmpty()) {
            LogUtils.w(TAG, "分类名称不能为空");
            return false;
        }
        
        String trimmed = categoryName.trim();
        List<String> existing = getAllCategories();
        
        if (existing.contains(trimmed)) {
            LogUtils.w(TAG, "分类已存在: " + trimmed);
            return false;
        }
        
        LogUtils.action(TAG, "添加分类", trimmed);
        // 分类只是逻辑概念，不需要实际存储
        return true;
    }
    
    /**
     * 删除分类
     */
    public boolean deleteCategory(String categoryName) {
        if (categoryName == null || categoryName.isEmpty()) {
            return false;
        }
        
        String allCategory = "全部";
        String uncategorized = "未分类";
        
        if (categoryName.equals(allCategory) || categoryName.equals(uncategorized)) {
            LogUtils.w(TAG, "不能删除系统分类: " + categoryName);
            return false;
        }
        
        LogUtils.action(TAG, "删除分类", categoryName);
        
        List<PluginInfo> pluginsInCategory = getPluginsByCategory(categoryName);
        boolean success = true;
        
        for (PluginInfo info : pluginsInCategory) {
            if (!updatePluginCategory(info.pluginId, uncategorized)) {
                success = false;
                LogUtils.e(TAG, "移动插件失败: " + info.name);
            }
        }
        
        if (success) {
            LogUtils.success(TAG, "分类删除成功: " + categoryName);
        }
        
        return success;
    }
    
    /**
     * 更新插件分类
     */
    public boolean updatePluginCategory(String pluginId, String newCategory) {
        LogUtils.action(TAG, "更新插件分类", pluginId + " -> " + newCategory);
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
            
            LogUtils.success(TAG, "插件分类更新成功: " + pluginId + " -> " + newCategory);
            return true;
        } catch (Exception e) {
            LogUtils.e(TAG, "更新分类失败: " + e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * 获取已安装的插件信息（从文件读取）
     */
    private PluginInfo getInstalledPluginInfo(String pluginId) {
        File pluginDir = getPluginDir(pluginId);
        if (pluginDir == null) return null;
        File jsonFile = new File(pluginDir, "plugin.json");
        if (jsonFile.exists()) {
            String json = FileUtils.readFileToString(jsonFile);
            PluginInfo info = PluginInfo.fromJson(json);
            if (info != null && info.pluginId == null) {
                info.pluginId = pluginId;
            }
            return info;
        }
        return null;
    }
    
    /**
     * 获取插件信息
     */
    public PluginInfo getPluginInfo(String pluginId) {
        if (loadedPlugins.containsKey(pluginId)) {
            return loadedPlugins.get(pluginId);
        }
        return getInstalledPluginInfo(pluginId);
    }
    
    /**
     * 卸载插件
     */
    public boolean uninstallPlugin(String pluginId) {
        LogUtils.action(TAG, "卸载插件", pluginId);
        File pluginDir = getPluginDir(pluginId);
        File optDir = new File(appContext.getCodeCacheDir(), "opt/" + pluginId);
        if (pluginDir != null) FileUtils.deleteDir(pluginDir);
        FileUtils.deleteDir(optDir);
        
        // 清理 WebView 缓存
        WebView webView = webViewCache.remove(pluginId);
        if (webView != null) {
            webView.destroy();
        }
        
        // 清理缓存
        loadedPlugins.remove(pluginId);
        classLoaders.remove(pluginId);
        pluginInstances.remove(pluginId);
        pluginBaseUrls.remove(pluginId);
        
        // 删除签名记录
        PreferencesUtils.removePluginSignature(appContext, pluginId + ".tpk");
        PreferencesUtils.removePluginSignature(appContext, pluginId);
        
        notifyWidgetsRefresh();
        
        LogUtils.success(TAG, "插件卸载成功: " + pluginId);
        showToast("已卸载插件", Toast.LENGTH_SHORT);
        return true;
    }
    
    /**
     * 清理 WebView 缓存
     */
    public void clearWebViewCache() {
        LogUtils.action(TAG, "清理 WebView 缓存", "");
        for (WebView webView : webViewCache.values()) {
            if (webView != null) {
                webView.destroy();
            }
        }
        webViewCache.clear();
        LogUtils.i(TAG, "WebView 缓存已清理");
    }
    
    /**
     * 刷新并通知小部件更新
     */
    public void refreshAndNotifyWidgets(Context context) {
        refreshWorkFolder(context);
        notifyWidgetsRefresh();
        LogUtils.i(TAG, "刷新并通知小部件更新");
    }
    
    /**
     * 插件变更时调用
     */
    public void onPluginsChanged(Context context) {
        refreshAndNotifyWidgets(context);
    }
    
    /**
     * 通知小部件刷新
     */
    private void notifyWidgetsRefresh() {
        try {
            // 使用反射调用，避免循环依赖
            Class<?> widgetClass = Class.forName("com.UIN.Tool.widget.UINWidgetProvider");
            Method sendIntentMethod = widgetClass.getMethod("sendIntentToRefreshAllWidgets", Context.class);
            sendIntentMethod.invoke(null, appContext);
            
            Class<?> widget1x1Class = Class.forName("com.UIN.Tool.widget.Widget1x1Provider");
            Method sendRefreshIntentMethod = widget1x1Class.getMethod("sendRefreshIntent", Context.class);
            sendRefreshIntentMethod.invoke(null, appContext);
            
            LogUtils.i(TAG, "小部件刷新通知已发送");
        } catch (Exception e) {
            LogUtils.e(TAG, "通知小部件刷新失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 从文件安装插件
     */
    public PluginInfo installPluginFromFile(File file) {
        if (file == null || !file.exists()) {
            LogUtils.e(TAG, "文件不存在");
            return null;
        }
        
        String fileName = file.getName();
        boolean isValidExtension = false;
        for (String ext : SUPPORTED_EXTENSIONS) {
            if (fileName.toLowerCase().endsWith(ext)) {
                isValidExtension = true;
                break;
            }
        }
        
        if (!isValidExtension) {
            LogUtils.e(TAG, "不支持的文件类型: " + fileName);
            showToast("不支持的文件类型，请选择 .tpk 或 .zip 文件", Toast.LENGTH_SHORT);
            return null;
        }
        
        return installPlugin(file.getAbsolutePath(), fileName);
    }
    
    /**
     * 批量安装插件
     */
    public List<PluginInfo> installPluginsBatch(List<File> files) {
        List<PluginInfo> installed = new ArrayList<>();
        List<String> failed = new ArrayList<>();
        
        for (File file : files) {
            PluginInfo info = installPluginFromFile(file);
            if (info != null) {
                installed.add(info);
            } else {
                failed.add(file.getName());
            }
        }
        
        if (!failed.isEmpty()) {
            LogUtils.w(TAG, "批量安装失败: " + failed.size() + " 个文件");
            showToast("成功安装 " + installed.size() + " 个，失败 " + failed.size() + " 个", Toast.LENGTH_LONG);
        } else {
            showToast("成功安装 " + installed.size() + " 个插件", Toast.LENGTH_SHORT);
        }
        
        return installed;
    }
    
    /**
     * 导出插件为 TPK 文件
     */
    public boolean exportPlugin(String pluginId, File destFile) {
        LogUtils.action(TAG, "导出插件", pluginId);
        
        try {
            File pluginDir = getPluginDir(pluginId);
            if (pluginDir == null || !pluginDir.exists()) {
                LogUtils.e(TAG, "插件目录不存在");
                return false;
            }
            
            // 打包为 ZIP
            java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(new FileOutputStream(destFile));
            zipDirectory(pluginDir, pluginDir, zos);
            zos.close();
            
            LogUtils.success(TAG, "插件导出成功: " + destFile.getAbsolutePath());
            showToast("插件导出成功", Toast.LENGTH_SHORT);
            return true;
            
        } catch (Exception e) {
            LogUtils.e(TAG, "导出插件失败: " + e.getMessage(), e);
            showToast("导出失败: " + e.getMessage(), Toast.LENGTH_SHORT);
            return false;
        }
    }
    
    /**
     * 递归打包目录
     */
    private void zipDirectory(File rootDir, File file, java.util.zip.ZipOutputStream zos) throws Exception {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    zipDirectory(rootDir, child, zos);
                }
            }
        } else {
            String entryName = file.getAbsolutePath().substring(rootDir.getAbsolutePath().length() + 1);
            java.util.zip.ZipEntry entry = new java.util.zip.ZipEntry(entryName);
            zos.putNextEntry(entry);
            FileInputStream fis = new FileInputStream(file);
            byte[] buffer = new byte[8192];
            int len;
            while ((len = fis.read(buffer)) > 0) {
                zos.write(buffer, 0, len);
            }
            fis.close();
            zos.closeEntry();
        }
    }
    
    /**
     * 获取插件 WebView 实例
     */
    public WebView getPluginWebView(String pluginId) {
        return webViewCache.get(pluginId);
    }
    
    /**
     * 刷新单个插件的 WebView
     */
    public void refreshPluginWebView(String pluginId) {
        WebView webView = webViewCache.get(pluginId);
        if (webView != null) {
            PluginInfo info = getPluginInfo(pluginId);
            if (info != null && info.uiType.equals("web")) {
                File pluginDir = getPluginDir(pluginId);
                if (pluginDir != null) {
                    String baseUrl = "file://" + pluginDir.getAbsolutePath() + "/";
                    String url = baseUrl + info.entry;
                    webView.loadUrl(url);
                    LogUtils.i(TAG, "刷新 WebView: " + pluginId);
                }
            }
        }
    }
    
    /**
     * 获取所有已安装插件的 ID 列表
     */
    public List<String> getInstalledPluginIds() {
        List<String> ids = new ArrayList<>();
        for (PluginInfo info : getInstalledPlugins()) {
            ids.add(info.pluginId);
        }
        return ids;
    }
    
    /**
     * 检查插件是否有更新
     */
    public boolean hasPluginUpdate(String pluginId, int newVersion) {
        PluginInfo info = getPluginInfo(pluginId);
        if (info == null) return false;
        return newVersion > info.version;
    }
    
    /**
     * 更新插件（卸载后安装新版本）
     */
    public boolean updatePlugin(String pluginId, File newPluginFile) {
        LogUtils.action(TAG, "更新插件", pluginId);
        
        // 先卸载旧版本
        if (!uninstallPlugin(pluginId)) {
            LogUtils.e(TAG, "卸载旧版本失败");
            return false;
        }
        
        // 安装新版本
        PluginInfo info = installPluginFromFile(newPluginFile);
        if (info == null) {
            LogUtils.e(TAG, "安装新版本失败");
            return false;
        }
        
        LogUtils.success(TAG, "插件更新成功: " + pluginId);
        showToast("插件已更新到 " + info.versionName, Toast.LENGTH_SHORT);
        return true;
    }
}