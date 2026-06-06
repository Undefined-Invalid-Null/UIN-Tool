// app/src/main/java/com/UIN/Tool/ui/common/IconHelper.java
package com.UIN.Tool.ui.common;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.widget.ImageView;

import com.UIN.Tool.R;
import com.UIN.Tool.plugin.PluginInfo;
import com.UIN.Tool.plugin.PluginManager;

import java.io.File;

/**
 * 图标加载辅助类
 * 统一处理插件图标的加载逻辑，消除代码重复
 */
public class IconHelper {

    /**
     * 加载插件图标
     * @param imageView 目标 ImageView
     * @param plugin 插件信息
     */
    public static void loadPluginIcon(ImageView imageView, PluginInfo plugin) {
        if (imageView == null || plugin == null) {
            setDefaultIcon(imageView);
            return;
        }

        Context context = imageView.getContext();
        PluginManager pluginManager = PluginManager.getInstance(context);
        File pluginDir = pluginManager.getPluginDirFile(plugin.pluginId);

        if (pluginDir != null && pluginDir.exists()) {
            String iconPath = plugin.icon != null && !plugin.icon.isEmpty() ? plugin.icon : "icon.png";
            File iconFile = new File(pluginDir, iconPath);

            if (iconFile.exists()) {
                try {
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inSampleSize = 2;
                    Bitmap bitmap = BitmapFactory.decodeFile(iconFile.getAbsolutePath(), options);
                    if (bitmap != null) {
                        imageView.setImageBitmap(bitmap);
                        return;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        setDefaultIcon(imageView);
    }

    /**
     * 设置默认图标
     */
    private static void setDefaultIcon(ImageView imageView) {
        if (imageView != null) {
            imageView.setImageResource(R.drawable.ic_extension);
        }
    }

    /**
     * 加载插件图标并缩放
     */
    public static void loadPluginIconScaled(ImageView imageView, PluginInfo plugin, int targetSize) {
        if (imageView == null || plugin == null) {
            setDefaultIcon(imageView);
            return;
        }

        Context context = imageView.getContext();
        PluginManager pluginManager = PluginManager.getInstance(context);
        File pluginDir = pluginManager.getPluginDirFile(plugin.pluginId);

        if (pluginDir != null && pluginDir.exists()) {
            String iconPath = plugin.icon != null && !plugin.icon.isEmpty() ? plugin.icon : "icon.png";
            File iconFile = new File(pluginDir, iconPath);

            if (iconFile.exists()) {
                try {
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inJustDecodeBounds = true;
                    BitmapFactory.decodeFile(iconFile.getAbsolutePath(), options);

                    int scale = 1;
                    while (options.outWidth / scale > targetSize && options.outHeight / scale > targetSize) {
                        scale *= 2;
                    }
                    options.inSampleSize = scale;
                    options.inJustDecodeBounds = false;

                    Bitmap bitmap = BitmapFactory.decodeFile(iconFile.getAbsolutePath(), options);
                    if (bitmap != null) {
                        imageView.setImageBitmap(bitmap);
                        return;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        setDefaultIcon(imageView);
    }
}