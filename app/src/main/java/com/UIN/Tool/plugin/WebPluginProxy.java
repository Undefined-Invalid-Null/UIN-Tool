package com.UIN.Tool.plugin;

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.UIN.Tool.utils.LogUtils;

import java.io.File;

/**
 * Web 插件代理类 - 实现 PluginInterface 接口，内部使用 WebView 渲染
 */
public class WebPluginProxy implements PluginInterface {
    
    private static final String TAG = "WebPluginProxy";
    
    private Context context;
    private String pluginId;
    private String pluginDir;
    private WebView webView;
    private PluginInfo pluginInfo;
    private String entryFile;
    private boolean isWebViewReady = false;
    
    public WebPluginProxy(String pluginId, String pluginDir, PluginInfo info) {
        this.pluginId = pluginId;
        this.pluginDir = pluginDir;
        this.pluginInfo = info;
        
        // 获取入口文件，默认为 web/index.html
        this.entryFile = info.entry != null ? info.entry : "web/index.html";
    }
    
    @Override
    public View onCreateView(Context context, ViewGroup container, Bundle savedInstanceState) {
        this.context = context;
        
        // 创建 WebView
        webView = new WebView(context);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setAllowFileAccess(true);
        webView.getSettings().setAllowFileAccessFromFileURLs(true);
        webView.getSettings().setAllowUniversalAccessFromFileURLs(true);
        
        // 设置 WebViewClient
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                isWebViewReady = true;
                LogUtils.success(TAG, "WebView 加载完成: " + pluginInfo.name);
            }
            
            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                LogUtils.e(TAG, "WebView 加载错误: " + description);
                // 显示错误页面
                view.loadData("<html><body><h3>插件加载失败</h3><p>" + description + "</p></body></html>", 
                        "text/html", "UTF-8");
            }
        });
        
        // 设置 JS 接口
        WebPluginProxy proxy = this;
        PluginWebInterface jsInterface = new PluginWebInterface(context, pluginId, proxy);
        webView.addJavascriptInterface(jsInterface, "UINPlugin");
        
        // 加载本地 HTML 文件
        String indexPath = pluginDir + File.separator + entryFile;
        File indexFile = new File(indexPath);
        
        if (indexFile.exists()) {
            webView.loadUrl("file://" + indexPath);
            LogUtils.i(TAG, "加载 Web 插件: " + indexPath);
        } else {
            // 文件不存在，显示默认页面
            String defaultHtml = getDefaultHtml();
            webView.loadDataWithBaseURL("file://" + pluginDir + "/web/", defaultHtml, "text/html", "UTF-8", null);
            LogUtils.w(TAG, "入口文件不存在，使用默认页面: " + indexPath);
        }
        
        return webView;
    }
    
    /**
     * JS 调用插件方法
     */
    public void onJsCall(String method, String params) {
        // 可以根据需要扩展插件自己的方法
        switch (method) {
            case "getPluginInfo":
                sendEvent("pluginInfo", pluginInfo.toJson());
                break;
            default:
                LogUtils.d(TAG, "未处理的方法: " + method);
        }
    }
    
    /**
     * 向 Web 端发送事件
     */
    public void sendEvent(String eventName, String data) {
        if (webView != null && isWebViewReady) {
            String js = String.format(
                "if (window.dispatchEvent && window.dispatchEvent instanceof Function) {" +
                "  window.dispatchEvent(new CustomEvent('%s', { detail: %s }));" +
                "} else if (window.on%s) {" +
                "  window.on%s(%s);" +
                "}",
                eventName, 
                data != null ? data : "null",
                eventName,
                eventName,
                data != null ? data : "null"
            );
            webView.evaluateJavascript(js, null);
        }
    }
    
    @Override
    public void onResume() {
        if (webView != null) {
            webView.onResume();
            webView.resumeTimers();
        }
        sendEvent("resume", "{}");
    }
    
    @Override
    public void onPause() {
        if (webView != null) {
            webView.onPause();
            webView.pauseTimers();
        }
        sendEvent("pause", "{}");
    }
    
    @Override
    public void onDestroy() {
        sendEvent("destroy", "{}");
        
        if (webView != null) {
            webView.loadUrl("about:blank");
            webView.clearHistory();
            webView.clearCache(true);
            webView.clearFormData();
            webView.destroy();
            webView = null;
        }
    }
    
    @Override
    public boolean onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
            return true;
        }
        return false;
    }
    
    @Override
    public Bundle onSaveInstanceState() {
        Bundle bundle = new Bundle();
        if (webView != null) {
            webView.saveState(bundle);
        }
        return bundle;
    }
    
    /**
     * 默认的 HTML 内容
     */
    private String getDefaultHtml() {
        return "<!DOCTYPE html>\n" +
               "<html>\n" +
               "<head>\n" +
               "    <meta charset=\"UTF-8\">\n" +
               "    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n" +
               "    <title>" + pluginInfo.name + "</title>\n" +
               "    <style>\n" +
               "        body { font-family: sans-serif; padding: 20px; text-align: center; }\n" +
               "        button { background: #37474F; color: white; border: none; padding: 12px 24px; border-radius: 8px; margin: 10px; }\n" +
               "        .info { background: #f0f0f0; padding: 16px; border-radius: 12px; margin-top: 20px; text-align: left; }\n" +
               "    </style>\n" +
               "</head>\n" +
               "<body>\n" +
               "    <h1>" + pluginInfo.name + "</h1>\n" +
               "    <p>" + (pluginInfo.description != null ? pluginInfo.description : "") + "</p>\n" +
               "    <button onclick=\"UINPlugin.callHost('toast', 'Hello from Web Plugin!')\">点我</button>\n" +
               "    <button onclick=\"UINPlugin.callHost('finish', '')\">关闭</button>\n" +
               "    <div class=\"info\">\n" +
               "        <strong>插件信息</strong><br>\n" +
               "        版本: " + pluginInfo.versionName + "<br>\n" +
               "        作者: " + (pluginInfo.author != null ? pluginInfo.author : "未知") + "<br>\n" +
               "        ID: " + pluginId + "\n" +
               "    </div>\n" +
               "    <script>\n" +
               "        // 监听插件事件\n" +
               "        window.addEventListener('resume', function(e) { console.log('插件恢复', e.detail); });\n" +
               "        window.addEventListener('pause', function(e) { console.log('插件暂停', e.detail); });\n" +
               "    </script>\n" +
               "</body>\n" +
               "</html>";
    }
}