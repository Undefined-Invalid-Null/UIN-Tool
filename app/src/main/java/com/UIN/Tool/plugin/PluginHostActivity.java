package com.UIN.Tool.plugin;

import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.UIN.Tool.utils.LogUtils;

import java.io.File;

public class PluginHostActivity extends AppCompatActivity {
    public static final String EXTRA_PLUGIN_ID = "plugin_id";

    private FrameLayout container;
    private String currentPluginId;
    private WebPluginProxy webProxy;
    private WebView webView;
    private PluginManager pluginManager;
    private String currentTitle = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        container = new FrameLayout(this);
        container.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));
        setContentView(container);

        currentPluginId = getIntent().getStringExtra(EXTRA_PLUGIN_ID);
        if (currentPluginId == null) {
            finish();
            return;
        }

        pluginManager = PluginManager.getInstance(this);
        PluginInfo pluginInfo = pluginManager.getPluginInfo(currentPluginId);
        
        if (pluginInfo == null) {
            LogUtils.e("PluginHostActivity", "插件不存在: " + currentPluginId);
            Toast.makeText(this, "插件不存在: " + currentPluginId, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // 设置默认标题
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(pluginInfo.name);
            currentTitle = pluginInfo.name;
        }

        // 根据 UI 类型选择加载方式
        if ("web".equals(pluginInfo.uiType)) {
            loadWebPlugin(pluginManager, pluginInfo);
        } else {
            loadNativePlugin(pluginManager);
        }
    }
    
    public void setPluginTitle(String title) {
        currentTitle = title;
        if (getSupportActionBar() != null) {
            runOnUiThread(() -> {
                if (getSupportActionBar() != null) {
                    getSupportActionBar().setTitle(title);
                }
            });
        }
    }
    
    private void loadNativePlugin(PluginManager pluginManager) {
        View pluginView = pluginManager.getPluginView(currentPluginId, this, container);
        if (pluginView != null) {
            container.addView(pluginView);
        } else {
            LogUtils.e("PluginHostActivity", "加载原生插件失败");
            Toast.makeText(this, "加载原生插件失败", Toast.LENGTH_SHORT).show();
            finish();
        }
    }
    
    private void loadWebPlugin(PluginManager pluginManager, PluginInfo pluginInfo) {
        File pluginDir = pluginManager.getPluginDirFile(currentPluginId);
        if (pluginDir == null || !pluginDir.exists()) {
            LogUtils.e("PluginHostActivity", "插件目录不存在");
            Toast.makeText(this, "插件目录不存在", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        
        createWebView(pluginDir, pluginInfo);
    }
    
    private void createWebView(File pluginDir, PluginInfo pluginInfo) {
        webView = new WebView(this);
        WebSettings settings = webView.getSettings();
        
        settings.setJavaScriptEnabled(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }
        
        settings.setAllowFileAccess(true);
        settings.setAllowFileAccessFromFileURLs(true);
        settings.setAllowUniversalAccessFromFileURLs(true);
        settings.setDefaultTextEncodingName("UTF-8");
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onJsAlert(WebView view, String url, String message, android.webkit.JsResult result) {
                new android.app.AlertDialog.Builder(PluginHostActivity.this)
                    .setTitle("提示")
                    .setMessage(message)
                    .setPositiveButton("确定", (dialog, which) -> result.confirm())
                    .setCancelable(false)
                    .show();
                result.confirm();
                return true;
            }
            
            @Override
            public boolean onJsConfirm(WebView view, String url, String message, android.webkit.JsResult result) {
                new android.app.AlertDialog.Builder(PluginHostActivity.this)
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
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                LogUtils.success("PluginHostActivity", "WebView 页面加载完成: " + url);
                view.evaluateJavascript("console.log('UINPlugin available:', typeof UINPlugin !== 'undefined');", null);
                
                // 恢复标题
                if (currentTitle != null && !currentTitle.isEmpty() && getSupportActionBar() != null) {
                    getSupportActionBar().setTitle(currentTitle);
                }
            }
            
            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                LogUtils.e("PluginHostActivity", "WebView 错误: " + description);
                Toast.makeText(PluginHostActivity.this, "加载失败: " + description, Toast.LENGTH_SHORT).show();
            }
        });
        
        PluginJSInterface jsInterface = new PluginJSInterface(this, currentPluginId, pluginInfo);
        webView.addJavascriptInterface(jsInterface, "UINPlugin");
        
        String indexPath = pluginDir.getAbsolutePath() + "/web/index.html";
        File indexFile = new File(indexPath);
        
        if (indexFile.exists()) {
            webView.loadUrl("file://" + indexPath);
            LogUtils.i("PluginHostActivity", "加载 Web 插件: " + indexPath);
        } else {
            String errorHtml = "<html><body style='text-align:center;padding:50px;font-family:sans-serif'>" +
                    "<h2>插件加载失败</h2>" +
                    "<p>找不到 web/index.html 文件</p>" +
                    "<p style='font-size:12px;color:#999'>路径: " + indexPath + "</p>" +
                    "</body></html>";
            webView.loadDataWithBaseURL("file://" + pluginDir.getAbsolutePath() + "/", errorHtml, "text/html", "UTF-8", null);
            LogUtils.e("PluginHostActivity", "入口文件不存在: " + indexPath);
        }
        
        container.addView(webView);
        LogUtils.success("PluginHostActivity", "Web 插件已加载: " + pluginInfo.name);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webProxy != null) {
            webProxy.onResume();
        } else if (webView != null) {
            webView.onResume();
            webView.resumeTimers();
            webView.evaluateJavascript("if(window.dispatchEvent) window.dispatchEvent(new Event('resume'));", null);
        } else {
            PluginManager.getInstance(this).onPluginResume(currentPluginId);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (webProxy != null) {
            webProxy.onPause();
        } else if (webView != null) {
            webView.onPause();
            webView.pauseTimers();
            webView.evaluateJavascript("if(window.dispatchEvent) window.dispatchEvent(new Event('pause'));", null);
        } else {
            PluginManager.getInstance(this).onPluginPause(currentPluginId);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (webProxy != null) {
            webProxy.onDestroy();
            webProxy = null;
        } else if (webView != null) {
            webView.loadUrl("about:blank");
            webView.clearHistory();
            webView.clearCache(true);
            webView.destroy();
            webView = null;
        } else {
            PluginManager.getInstance(this).onPluginDestroy(currentPluginId);
        }
    }

    @Override
    public void onBackPressed() {
        boolean handled = false;
        if (webProxy != null) {
            handled = webProxy.onBackPressed();
        } else if (webView != null && webView.canGoBack()) {
            webView.goBack();
            handled = true;
        } else {
            handled = PluginManager.getInstance(this).onPluginBackPressed(currentPluginId);
        }
        if (!handled) {
            super.onBackPressed();
        }
    }
}