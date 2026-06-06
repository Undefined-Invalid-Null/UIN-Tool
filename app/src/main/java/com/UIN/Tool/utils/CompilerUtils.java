package com.UIN.Tool.utils;

import android.content.Context;
import android.content.res.AssetManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public class CompilerUtils {

    /**
     * 从 assets 复制文件到缓存目录
     */
    public static File copyAssetToCache(Context context, String assetPath, String cacheFileName) {
        try {
            File cacheFile = new File(context.getCacheDir(), cacheFileName);
            if (cacheFile.exists()) {
                return cacheFile;
            }
            
            // 确保父目录存在
            File parentDir = cacheFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }
            
            AssetManager assetManager = context.getAssets();
            InputStream is = assetManager.open(assetPath);
            OutputStream os = new FileOutputStream(cacheFile);
            
            byte[] buffer = new byte[8192];
            int length;
            while ((length = is.read(buffer)) > 0) {
                os.write(buffer, 0, length);
            }
            
            os.close();
            is.close();
            
            return cacheFile;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }
    
    /**
     * 递归查找所有 .java 文件
     */
    public static void findJavaFiles(File dir, List<File> result) {
        if (dir == null || !dir.exists()) return;
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File file : files) {
            if (file.isDirectory()) {
                findJavaFiles(file, result);
            } else if (file.getName().endsWith(".java")) {
                result.add(file);
            }
        }
    }
    
    /**
     * 递归查找所有 .class 文件
     */
    public static void findClassFiles(File dir, List<File> result) {
        if (dir == null || !dir.exists()) return;
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File file : files) {
            if (file.isDirectory()) {
                findClassFiles(file, result);
            } else if (file.getName().endsWith(".class")) {
                result.add(file);
            }
        }
    }
    
    /**
     * 递归删除目录
     */
    public static boolean deleteDir(File dir) {
        if (dir == null || !dir.exists()) return false;
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    deleteDir(file);
                } else {
                    file.delete();
                }
            }
        }
        return dir.delete();
    }
    
    /**
     * 确保目录存在
     */
    public static void ensureDir(File dir) {
        if (dir != null && !dir.exists()) {
            dir.mkdirs();
        }
    }
}