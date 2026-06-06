package com.UIN.Tool.plugin;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.webkit.JsResult;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.widget.Toast;

import com.UIN.Tool.utils.LogUtils;

public class PluginWebChromeClient extends WebChromeClient {
    
    private final Context context;
    private final String pluginId;
    
    public PluginWebChromeClient(Context context, String pluginId) {
        this.context = context;
        this.pluginId = pluginId;
    }
    
    @Override
    public void onProgressChanged(WebView view, int newProgress) {
        super.onProgressChanged(view, newProgress);
        if (newProgress == 100) {
            LogUtils.success("PluginWebChromeClient", "插件加载完成: " + pluginId);
        }
    }
    
    @Override
    public void onReceivedTitle(WebView view, String title) {
        super.onReceivedTitle(view, title);
        LogUtils.i("PluginWebChromeClient", "[" + pluginId + "] 标题: " + title);
    }
    
    @Override
    public boolean onJsAlert(WebView view, String url, String message, JsResult result) {
        LogUtils.i("PluginWebChromeClient", "JS Alert: " + message);
        Toast.makeText(context, message, Toast.LENGTH_LONG).show();
        result.confirm();
        return true;
    }
    
    @Override
    public boolean onJsConfirm(WebView view, String url, String message, JsResult result) {
        LogUtils.i("PluginWebChromeClient", "JS Confirm: " + message);
        // 这里可以显示对话框
        result.confirm();
        return true;
    }
    
    @Override
    public boolean onConsoleMessage(android.webkit.ConsoleMessage consoleMessage) {
        LogUtils.d("WebView[" + pluginId + "]", 
            consoleMessage.message() + " (line " + consoleMessage.lineNumber() + ")");
        return true;
    }
}