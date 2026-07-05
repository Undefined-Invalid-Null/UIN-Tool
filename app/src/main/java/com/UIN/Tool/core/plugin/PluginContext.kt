package com.UIN.Tool.core.plugin

import android.content.Context
import android.content.ContextWrapper
import android.content.res.AssetManager
import android.content.res.Resources
import android.view.LayoutInflater
import java.io.File

/**
 * 插件上下文
 * 为插件提供独立的资源加载能力
 */
class PluginContext(
    base: Context,
    private val pluginDir: String
) : ContextWrapper(base) {
    
    private var pluginAssetManager: AssetManager? = null
    private var pluginResources: Resources? = null
    private var pluginInflater: LayoutInflater? = null
    
    init {
        initPluginResources()
    }
    
    private fun initPluginResources() {
        try {
            val assetManager = AssetManager::class.java.newInstance()
            val addAssetPath = AssetManager::class.java.getMethod("addAssetPath", String::class.java)
            
            // 添加插件目录
            addAssetPath.invoke(assetManager, pluginDir)
            
            // 添加资源目录
            val resDir = File(pluginDir, "res")
            if (resDir.exists()) {
                addAssetPath.invoke(assetManager, resDir.absolutePath)
            }
            
            this.pluginAssetManager = assetManager
            
            val superRes = super.getResources()
            this.pluginResources = Resources(assetManager, superRes.displayMetrics, superRes.configuration)
            this.pluginInflater = LayoutInflater.from(baseContext)
            
        } catch (e: Exception) {
            // 回退到宿主资源
            this.pluginAssetManager = super.getAssets()
            this.pluginResources = super.getResources()
            this.pluginInflater = LayoutInflater.from(baseContext)
        }
    }
    
    /**
     * 获取插件目录
     */
    fun getPluginDir(): String = pluginDir
    
    /**
     * 获取插件文件路径
     */
    fun getPluginFilePath(relativePath: String): String = "$pluginDir/$relativePath"
    
    /**
     * 检查插件文件是否存在
     */
    fun hasPluginFile(relativePath: String): Boolean = File(pluginDir, relativePath).exists()
    
    override fun getAssets(): AssetManager {
        return pluginAssetManager ?: super.getAssets()
    }
    
    override fun getResources(): Resources {
        return pluginResources ?: super.getResources()
    }
    
    override fun getSystemService(name: String): Any? {
        if (Context.LAYOUT_INFLATER_SERVICE == name) {
            return pluginInflater ?: super.getSystemService(name)
        }
        return super.getSystemService(name)
    }
}