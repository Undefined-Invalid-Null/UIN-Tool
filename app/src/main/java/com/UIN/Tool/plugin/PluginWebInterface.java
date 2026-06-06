package com.UIN.Tool.plugin;

import android.app.Activity;
import android.content.Context;
import android.webkit.JavascriptInterface;
import android.widget.Toast;

import com.UIN.Tool.utils.LogUtils;

import org.json.JSONObject;

/**
 * WebView 与插件通信的 JavaScript 接口
 */
public class PluginWebInterface {
    
    private final Context context;
    private final String pluginId;
    private final WebPluginProxy proxy;
    
    public PluginWebInterface(Context context, String pluginId, WebPluginProxy proxy) {
        this.context = context;
        this.pluginId = pluginId;
        this.proxy = proxy;
    }
    
    /**
     * 调用插件的 Java 方法
     * @param method 方法名
     * @param params JSON 格式参数
     */
    @JavascriptInterface
    public void callPlugin(String method, String params) {
        LogUtils.i("PluginWebInterface", "JS 调用插件: " + method + ", 参数: " + params);
        if (proxy != null) {
            proxy.onJsCall(method, params);
        }
    }
    
    /**
     * 调用宿主工具方法
     */
    @JavascriptInterface
    public void callHost(String action, String data) {
        LogUtils.i("PluginWebInterface", "JS 调用宿主: " + action + ", 数据: " + data);
        
        switch (action) {
            case "toast":
                showToast(data);
                break;
            case "finish":
                if (context instanceof Activity) {
                    ((Activity) context).finish();
                }
                break;
            case "log":
                LogUtils.i("PluginWeb", data);
                break;
            default:
                LogUtils.w("PluginWebInterface", "未知的宿主调用: " + action);
        }
    }
    
    /**
     * 发送事件到 Web 端
     */
    @JavascriptInterface
    public void sendEvent(String eventName, String data) {
        LogUtils.d("PluginWebInterface", "发送事件: " + eventName);
    }
    
    private void showToast(String message) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
    }
}