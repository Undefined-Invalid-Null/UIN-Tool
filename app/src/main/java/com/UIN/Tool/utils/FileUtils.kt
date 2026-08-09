package com.UIN.Tool.utils

import android.content.Context
import android.net.Uri
import com.UIN.Tool.UinApplication
import com.UIN.Tool.data.local.FileManager
import java.io.File

/**
 * 文件工具统一委托层。
 * 所有实现均已收敛至 [FileManager]，此处仅保持公开 API 不变并转发。
 */
object FileUtils {

    private val fileManager: FileManager by lazy {
        FileManager(UinApplication.getInstance())
    }

    fun copyUriToFile(context: Context, uri: Uri, destFile: File): Boolean {
        return FileManager(context).copyUriToFile(uri, destFile)
    }

    fun readFileToString(file: File): String? {
        return fileManager.readFileToString(file)
    }

    fun writeStringToFile(file: File, content: String): Boolean {
        return fileManager.writeStringToFile(file, content)
    }

    fun deleteRecursively(file: File): Boolean {
        return fileManager.deleteRecursively(file)
    }

    fun copyDirectory(src: File, dst: File): Boolean {
        return fileManager.copyDirectory(src, dst)
    }

    fun zipDirectory(srcDir: File, destZip: File): Boolean {
        return fileManager.zipDirectory(srcDir, destZip)
    }

    fun unzipFile(zipFile: File, destDir: File): Boolean {
        return fileManager.unzipFile(zipFile, destDir)
    }
}
