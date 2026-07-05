// ✅ 包名与 Java 版本完全一致：com.UIN.Tool.plugin
package com.UIN.Tool.plugin

import android.content.Context
import android.content.ContextWrapper
import android.content.res.AssetManager
import android.content.res.Resources
import android.view.LayoutInflater
import java.io.File

/**
 * 插件上下文 - 完全复用 Java 版本
 * Java 版本: PluginContext.java
 * 包名: com.UIN.Tool.plugin
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

    fun getPluginDir(): String = pluginDir

    fun getPluginFilePath(relativePath: String): String = "$pluginDir/$relativePath"

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