package com.UIN.Tool.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/**
 * 格式化文件大小
 */
fun formatFileSize(size: Long): String {
    return when {
        size < 1024 -> "$size B"
        size < 1024 * 1024 -> String.format("%.2f KB", size / 1024.0)
        else -> String.format("%.2f MB", size / (1024.0 * 1024.0))
    }
}

/**
 * 安全获取列表元素
 */
fun <T> List<T>.safeGet(index: Int): T? {
    return if (index in indices) this[index] else null
}

/**
 * 验证是否为有效的插件ID格式
 */
fun String.isValidPluginId(): Boolean {
    return matches(Regex("^[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+$"))
}

/**
 * 验证是否为有效的十六进制颜色值
 */
fun String.isValidHexColor(): Boolean {
    return matches(Regex("^#[0-9A-Fa-f]{6}$")) || matches(Regex("^#[0-9A-Fa-f]{8}$"))
}

/**
 * 截断字符串并添加省略号
 */
fun String.truncate(maxLength: Int): String {
    return if (this.length > maxLength) {
        this.substring(0, maxLength) + "..."
    } else {
        this
    }
}

/**
 * 打开文件（使用 FileProvider）
 */
fun Context.openFile(file: File) {
    try {
        val uri = FileProvider.getUriForFile(
            this,
            "${packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "resource/folder")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "选择文件管理器"))
    } catch (e: Exception) {
        // 忽略
    }
}

/**
 * 分享文本
 */
fun Context.shareText(text: String, title: String = "分享") {
    try {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(Intent.createChooser(intent, title))
    } catch (e: Exception) {
        // 忽略
    }
}

/**
 * 打开URL
 */
fun Context.openUrl(url: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    } catch (e: Exception) {
        // 忽略
    }
}