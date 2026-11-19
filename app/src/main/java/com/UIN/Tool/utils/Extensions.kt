package com.UIN.Tool.utils

import com.UIN.Tool.R
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import java.io.File

/**
 * 格式化文件大小。
 *
 * 只有这一处实现 —— 文件页进度条/冲突清单/属性框、下载进度、仓库插件体积共用同一套口径，
 * 免得同一个字节数在三个界面显示成三种样子。负数一律给 `—`（目录没有字节数）。
 */
fun formatFileSize(size: Long): String {
    return when {
        size < 0L -> "—"
        size < 1024 -> "$size B"
        size < 1024 * 1024 -> String.format("%.2f KB", size / 1024.0)
        size < 1024L * 1024 * 1024 -> String.format("%.2f MB", size / (1024.0 * 1024.0))
        else -> String.format("%.2f GB", size / (1024.0 * 1024.0 * 1024.0))
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
 * 用其他应用打开文件/目录（FileProvider 签发**一次性只读** URI）。
 *
 * - MIME 按扩展名推断，推不出时退回**通配类型**；**目录**保持 `resource/folder`（交给文件管理器浏览）；
 * - 失败**不再静默吞掉**：路径不在白名单、没有能处理的应用，都给用户一句提示。
 *   白名单范围见 `res/xml/file_paths.xml`（N13：覆盖 `/storage/` 下所有存储卷）。
 */
fun Context.openFile(file: File) {
    try {
        val uri = FileProvider.getUriForFile(
            this,
            "${packageName}.fileprovider",
            file
        )
        val mime = if (file.isDirectory) "resource/folder"
        else MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(file.extension.lowercase()) ?: "*/*"
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, file.name))
    } catch (e: Exception) {
        AppLog.w("Extensions", "open file failed: ${e.message}")
        AppToast.warning(this, Str.get(R.string.files_open_failed))
    }
}

/**
 * 分享文本
 */
fun Context.shareText(text: String, title: String = Str.get(R.string.share)) {
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
 * 分享文件（可多选），与 [openFile] 共用同一套 FileProvider 白名单。
 *
 * 一个走 `ACTION_SEND`、多个走 `ACTION_SEND_MULTIPLE` —— 只给 SEND 却塞多个流，
 * 一半接收方只会取第一条，用户以为分享成功了却漏了文件。
 * 多选时 MIME 一律走通配串（就是代码里那个星号斜杠星号，写在这里会让本注释提前闭合）：
 * 类型各不相同，给一个具体值会让多数目标根本不出现。
 */
fun Context.shareFiles(files: List<File>) {
    if (files.isEmpty()) return
    try {
        val uris = files.map {
            FileProvider.getUriForFile(this, "${packageName}.fileprovider", it)
        }
        val single = files.size == 1
        val intent = Intent(if (single) Intent.ACTION_SEND else Intent.ACTION_SEND_MULTIPLE)
            .apply {
                type = if (single) {
                    MimeTypeMap.getSingleton()
                        .getMimeTypeFromExtension(files[0].extension.lowercase()) ?: "*/*"
                } else "*/*"
                if (single) {
                    putExtra(Intent.EXTRA_STREAM, uris[0])
                } else {
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
                }
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        startActivity(Intent.createChooser(intent, files[0].name))
    } catch (e: Exception) {
        AppLog.w("Extensions", "share files failed: ${e.message}")
        AppToast.warning(this, Str.get(R.string.files_open_failed))
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