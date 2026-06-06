package com.UIN.Tool.utils;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class FileUtils {
    private static final String TAG = "FileUtils";

    /**
     * 从 Uri 复制文件到目标路径
     */
    public static boolean copyUriToFile(Context context, Uri sourceUri, File destFile) {
        try {
            ContentResolver resolver = context.getContentResolver();
            InputStream inputStream = resolver.openInputStream(sourceUri);
            if (inputStream == null) return false;

            FileOutputStream outputStream = new FileOutputStream(destFile);
            byte[] buffer = new byte[8192];
            int length;
            while ((length = inputStream.read(buffer)) > 0) {
                outputStream.write(buffer, 0, length);
            }
            outputStream.close();
            inputStream.close();
            return true;
        } catch (Exception e) {
            Log.e(TAG, "copyUriToFile error", e);
            return false;
        }
    }

    /**
     * 解压 ZIP 文件到目标目录
     */
    public static boolean unzip(File zipFile, File destDir) {
        try {
            if (!destDir.exists()) destDir.mkdirs();

            ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile));
            ZipEntry entry;
            byte[] buffer = new byte[8192];

            while ((entry = zis.getNextEntry()) != null) {
                File targetFile = new File(destDir, entry.getName());

                if (entry.isDirectory()) {
                    targetFile.mkdirs();
                } else {
                    targetFile.getParentFile().mkdirs();
                    FileOutputStream fos = new FileOutputStream(targetFile);
                    int len;
                    while ((len = zis.read(buffer)) > 0) {
                        fos.write(buffer, 0, len);
                    }
                    fos.close();
                }
                zis.closeEntry();
            }
            zis.close();
            return true;
        } catch (Exception e) {
            Log.e(TAG, "unzip error", e);
            return false;
        }
    }

    /**
     * 递归删除目录
     */
    public static boolean deleteDir(File dir) {
        if (dir == null || !dir.exists()) return false;
        if (dir.isDirectory()) {
            File[] children = dir.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteDir(child);
                }
            }
        }
        return dir.delete();
    }

    /**
     * 复制目录
     */
    public static boolean copyDir(File srcDir, File destDir) {
        try {
            if (!srcDir.exists()) return false;
            if (!destDir.exists()) destDir.mkdirs();

            for (File srcFile : srcDir.listFiles()) {
                File destFile = new File(destDir, srcFile.getName());
                if (srcFile.isDirectory()) {
                    copyDir(srcFile, destFile);
                } else {
                    FileInputStream fis = new FileInputStream(srcFile);
                    FileOutputStream fos = new FileOutputStream(destFile);
                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = fis.read(buffer)) > 0) {
                        fos.write(buffer, 0, len);
                    }
                    fis.close();
                    fos.close();
                }
            }
            return true;
        } catch (Exception e) {
            Log.e(TAG, "copyDir error", e);
            return false;
        }
    }

    /**
     * 读取文件内容为字符串
     */
    public static String readFileToString(File file) {
        try {
            FileInputStream fis = new FileInputStream(file);
            byte[] data = new byte[(int) file.length()];
            fis.read(data);
            fis.close();
            return new String(data, "UTF-8");
        } catch (Exception e) {
            return null;
        }
    }
}