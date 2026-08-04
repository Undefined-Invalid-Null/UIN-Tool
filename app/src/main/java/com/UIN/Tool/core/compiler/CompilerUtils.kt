package com.UIN.Tool.core.compiler

import com.UIN.Tool.R
import com.UIN.Tool.utils.Str
import android.content.Context
import com.UIN.Tool.log.Logger
import java.io.File
import java.io.FileOutputStream

object CompilerUtils {
    
    private const val TAG = "CompilerUtils"
    
    fun copyAssetToCache(context: Context, assetPath: String, cacheFileName: String): File? {
        return try {
            val cacheFile = File(context.cacheDir, cacheFileName)
            if (cacheFile.exists()) {
                return cacheFile
            }
            
            cacheFile.parentFile?.mkdirs()
            
            context.assets.open(assetPath).use { inputStream ->
                FileOutputStream(cacheFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            
            Logger.i(TAG, Str.get(R.string.copying_asset_to_cache_cachefile_abs, cacheFile.absolutePath))
            cacheFile
            
        } catch (e: Exception) {
            Logger.e(TAG, Str.get(R.string.failed_to_copy_asset_assetpath, assetPath), e)
            null
        }
    }
    
    fun findJavaFiles(dir: File): List<File> {
        val result = mutableListOf<File>()
        if (!dir.exists()) return result
        
        dir.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                result.addAll(findJavaFiles(file))
            } else if (file.name.endsWith(".java")) {
                result.add(file)
            }
        }
        
        return result
    }
    
    fun findClassFiles(dir: File): List<File> {
        val result = mutableListOf<File>()
        if (!dir.exists()) return result
        
        dir.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                result.addAll(findClassFiles(file))
            } else if (file.name.endsWith(".class")) {
                result.add(file)
            }
        }
        
        return result
    }
}