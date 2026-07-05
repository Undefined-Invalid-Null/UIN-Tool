package com.UIN.Tool.core.plugin

import android.content.Context
import android.webkit.JsResult
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.widget.Toast
import com.UIN.Tool.log.Logger

class PluginWebChromeClient(
    private val context: Context,
    private val pluginId: String
) : WebChromeClient() {
    
    private val TAG = "PluginWebChromeClient"
    
    override fun onProgressChanged(view: WebView, newProgress: Int) {
        super.onProgressChanged(view, newProgress)
        if (newProgress == 100) {
            Logger.success(TAG, "插件加载完成: $pluginId")
        }
    }
    
    override fun onReceivedTitle(view: WebView, title: String) {
        super.onReceivedTitle(view, title)
        Logger.i(TAG, "[$pluginId] 标题: $title")
    }
    
    override fun onJsAlert(view: WebView, url: String, message: String, result: JsResult): Boolean {
        Logger.i(TAG, "JS Alert: $message")
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        result.confirm()
        return true
    }
    
    override fun onJsConfirm(view: WebView, url: String, message: String, result: JsResult): Boolean {
        Logger.i(TAG, "JS Confirm: $message")
        android.app.AlertDialog.Builder(context)
            .setTitle("确认")
            .setMessage(message)
            .setPositiveButton("确定") { _, _ -> result.confirm() }
            .setNegativeButton("取消") { _, _ -> result.cancel() }
            .show()
        return true
    }
    
    override fun onConsoleMessage(consoleMessage: android.webkit.ConsoleMessage): Boolean {
        Logger.d("WebView[$pluginId]", 
            "${consoleMessage.message()} (line ${consoleMessage.lineNumber()})")
        return true
    }
}