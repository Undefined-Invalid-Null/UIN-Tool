package com.UIN.Tool.utils

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object FileUtils {
    
    fun copyUriToFile(context: Context, uri: Uri, destFile: File): Boolean {
        return try {
            val resolver: ContentResolver = context.contentResolver
            resolver.openInputStream(uri)?.use { inputStream ->
                FileOutputStream(destFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            true
        } catch (e: Exception) { false }
    }
    
    fun readFileToString(file: File): String? {
        return try {
            if (!file.exists()) return null
            file.readText()
        } catch (e: Exception) { null }
    }
    
    fun writeStringToFile(file: File, content: String): Boolean {
        return try {
            file.parentFile?.mkdirs()
            file.writeText(content)
            true
        } catch (e: Exception) { false }
    }
    
    fun deleteRecursively(file: File): Boolean {
        return when {
            !file.exists() -> true
            file.isDirectory -> {
                file.listFiles()?.forEach { deleteRecursively(it) }
                file.delete()
            }
            else -> file.delete()
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
        } catch (e: Exception) { false }
    }
    
    /**
     * 压缩目录为ZIP文件
     */
    fun zipDirectory(srcDir: File, destZip: File): Boolean {
        return try {
            destZip.parentFile?.mkdirs()
            if (destZip.exists()) destZip.delete()
            
            ZipOutputStream(FileOutputStream(destZip)).use { zos ->
                zipFileRecursive(srcDir, srcDir, zos)
            }
            true
        } catch (e: Exception) { false }
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
                e.printStackTrace()
            }
        }
    }
    
    /**
     * 解压ZIP文件
     */
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
        } catch (e: Exception) { false }
    }
}