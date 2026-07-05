package com.UIN.Tool.data.local

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.UIN.Tool.log.Logger
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class FileManager(private val context: Context) {

    private val TAG = "FileManager"

    fun copyUriToFile(uri: Uri, destFile: File): Boolean {
        return try {
            val resolver: ContentResolver = context.contentResolver
            resolver.openInputStream(uri)?.use { inputStream ->
                FileOutputStream(destFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            true
        } catch (e: Exception) {
            Logger.e(TAG, "复制Uri到文件失败", e)
            false
        }
    }

    fun readFileToString(file: File): String? {
        return try {
            if (!file.exists()) return null
            file.readText()
        } catch (e: Exception) {
            Logger.e(TAG, "读取文件失败: ${file.absolutePath}", e)
            null
        }
    }

    fun writeStringToFile(file: File, content: String): Boolean {
        return try {
            file.parentFile?.mkdirs()
            file.writeText(content)
            true
        } catch (e: Exception) {
            Logger.e(TAG, "写入文件失败: ${file.absolutePath}", e)
            false
        }
    }

    fun deleteRecursively(file: File): Boolean {
        return try {
            when {
                !file.exists() -> true
                file.isDirectory -> {
                    file.listFiles()?.forEach { deleteRecursively(it) }
                    file.delete()
                }
                else -> file.delete()
            }
        } catch (e: Exception) {
            Logger.e(TAG, "删除失败: ${file.absolutePath}", e)
            false
        }
    }

    fun copyDirectory(src: File, dst: File): Boolean {
        return try {
            if (!src.exists()) return false
            if (!dst.exists()) dst.mkdirs()

            src.listFiles()?.forEach { file ->
                val destFile = File(dst, file.name)
                if (file.isDirectory) {
                    copyDirectory(file, destFile)
                } else {
                    file.copyTo(destFile, overwrite = true)
                }
            }
            true
        } catch (e: Exception) {
            Logger.e(TAG, "复制目录失败", e)
            false
        }
    }

    fun zipDirectory(srcDir: File, destZip: File): Boolean {
        return try {
            destZip.parentFile?.mkdirs()
            if (destZip.exists()) destZip.delete()

            ZipOutputStream(FileOutputStream(destZip)).use { zos ->
                zipFileRecursive(srcDir, srcDir, zos)
            }
            true
        } catch (e: Exception) {
            Logger.e(TAG, "压缩目录失败", e)
            false
        }
    }

    private fun zipFileRecursive(rootDir: File, file: File, zos: ZipOutputStream) {
        if (file.isDirectory) {
            file.listFiles()?.forEach { child ->
                zipFileRecursive(rootDir, child, zos)
            }
        } else {
            try {
                val entryName = file.absolutePath
                    .removePrefix(rootDir.absolutePath)
                    .removePrefix("/")
                zos.putNextEntry(ZipEntry(entryName))
                FileInputStream(file).use { fis ->
                    fis.copyTo(zos)
                }
                zos.closeEntry()
            } catch (e: Exception) {
                Logger.e(TAG, "添加文件到ZIP失败: ${file.absolutePath}", e)
            }
        }
    }

    fun unzipFile(zipFile: File, destDir: File): Boolean {
        return try {
            destDir.mkdirs()
            ZipInputStream(FileInputStream(zipFile)).use { zis ->
                var entry: ZipEntry? = zis.nextEntry
                while (entry != null) {
                    val targetFile = File(destDir, entry.name)
                    if (entry.isDirectory) {
                        targetFile.mkdirs()
                    } else {
                        targetFile.parentFile?.mkdirs()
                        FileOutputStream(targetFile).use { fos ->
                            zis.copyTo(fos)
                        }
                    }
                    entry = zis.nextEntry
                }
            }
            true
        } catch (e: Exception) {
            Logger.e(TAG, "解压失败: ${zipFile.absolutePath}", e)
            false
        }
    }

    fun getFileSize(file: File): Long {
        return if (file.exists() && file.isFile) {
            file.length()
        } else if (file.exists() && file.isDirectory) {
            file.listFiles()?.sumOf { getFileSize(it) } ?: 0
        } else {
            0
        }
    }

    fun createDirectory(path: String): Boolean {
        return try {
            val dir = File(path)
            if (!dir.exists()) {
                dir.mkdirs()
            }
            true
        } catch (e: Exception) {
            Logger.e(TAG, "创建目录失败: $path", e)
            false
        }
    }
}