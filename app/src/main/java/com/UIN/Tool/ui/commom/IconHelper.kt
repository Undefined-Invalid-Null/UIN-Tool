package com.UIN.Tool.ui.common

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.ImageView
import com.UIN.Tool.R
import com.UIN.Tool.plugin.PluginManager
import com.UIN.Tool.domain.model.PluginInfo
import com.UIN.Tool.utils.Constants
import java.io.File

object IconHelper {

    fun loadPluginIcon(imageView: ImageView?, plugin: PluginInfo?) {
        if (imageView == null || plugin == null) {
            setDefaultIcon(imageView)
            return
        }

        val context = imageView.context
        val pluginManager = PluginManager.getInstance(context)
        val pluginDir = File(Constants.PLUGIN_DIR, plugin.pluginId)

        if (pluginDir.exists()) {
            val iconPath = if (plugin.icon.isNotEmpty()) plugin.icon else "icon.png"
            val iconFile = File(pluginDir, iconPath)

            if (iconFile.exists()) {
                try {
                    val options = BitmapFactory.Options().apply { inSampleSize = 2 }
                    val bitmap = BitmapFactory.decodeFile(iconFile.absolutePath, options)
                    if (bitmap != null) {
                        imageView.setImageBitmap(bitmap)
                        return
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        setDefaultIcon(imageView)
    }

    private fun setDefaultIcon(imageView: ImageView?) {
        imageView?.setImageResource(R.drawable.ic_extension)
    }

    fun loadPluginIconScaled(
        imageView: ImageView?,
        plugin: PluginInfo?,
        targetSize: Int
    ) {
        if (imageView == null || plugin == null) {
            setDefaultIcon(imageView)
            return
        }

        val context = imageView.context
        val pluginManager = PluginManager.getInstance(context)
        val pluginDir = File(Constants.PLUGIN_DIR, plugin.pluginId)

        if (pluginDir.exists()) {
            val iconPath = if (plugin.icon.isNotEmpty()) plugin.icon else "icon.png"
            val iconFile = File(pluginDir, iconPath)

            if (iconFile.exists()) {
                try {
                    val options = BitmapFactory.Options().apply {
                        inJustDecodeBounds = true
                    }
                    BitmapFactory.decodeFile(iconFile.absolutePath, options)

                    var scale = 1
                    while (options.outWidth / scale > targetSize && options.outHeight / scale > targetSize) {
                        scale *= 2
                    }
                    options.inSampleSize = scale
                    options.inJustDecodeBounds = false

                    val bitmap = BitmapFactory.decodeFile(iconFile.absolutePath, options)
                    if (bitmap != null) {
                        imageView.setImageBitmap(bitmap)
                        return
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        setDefaultIcon(imageView)
    }

    fun getPluginIconBitmap(
        context: Context,
        plugin: PluginInfo?
    ): Bitmap? {
        if (plugin == null) return null

        val pluginManager = PluginManager.getInstance(context)
        val pluginDir = File(Constants.PLUGIN_DIR, plugin.pluginId)

        if (pluginDir.exists()) {
            val iconPath = if (plugin.icon.isNotEmpty()) plugin.icon else "icon.png"
            val iconFile = File(pluginDir, iconPath)

            if (iconFile.exists()) {
                try {
                    val options = BitmapFactory.Options().apply { inSampleSize = 2 }
                    return BitmapFactory.decodeFile(iconFile.absolutePath, options)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        return null
    }
}