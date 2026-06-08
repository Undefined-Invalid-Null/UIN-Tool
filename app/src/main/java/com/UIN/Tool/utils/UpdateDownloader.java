package com.UIN.Tool.utils;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;
import androidx.core.content.FileProvider;

import com.UIN.Tool.MainActivity;
import com.UIN.Tool.R;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.cert.X509Certificate;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

public class UpdateDownloader {
    
    private static final String TAG = "UpdateDownloader";
    private static final String CHANNEL_ID = "update_download_channel";
    private static final String CHANNEL_NAME = "更新下载";
    private static final int NOTIFICATION_ID = 1001;
    
    private Context context;
    private NotificationManager notificationManager;
    private NotificationCompat.Builder notificationBuilder;
    private Handler mainHandler;
    private OnDownloadListener listener;
    private boolean isDownloading = false;
    
    public interface OnDownloadListener {
        void onStart();
        void onProgress(int progress, long downloaded, long total);
        void onSuccess(File file);
        void onFailed(String error);
    }
    
    public UpdateDownloader(Context context) {
        this.context = context.getApplicationContext();
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        createNotificationChannel();
        disableSSLCertificateChecking();
        LogUtils.i(TAG, "UpdateDownloader 初始化完成");
    }
    
    private void disableSSLCertificateChecking() {
        try {
            TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManager() {
                    @Override
                    public void checkClientTrusted(X509Certificate[] chain, String authType) {}
                    @Override
                    public void checkServerTrusted(X509Certificate[] chain, String authType) {}
                    @Override
                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[]{};
                    }
                }
            };
            
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.getSocketFactory());
            
            HostnameVerifier allHostsValid = (hostname, session) -> true;
            HttpsURLConnection.setDefaultHostnameVerifier(allHostsValid);
            
            LogUtils.i(TAG, "SSL 证书检查已禁用");
        } catch (Exception e) {
            LogUtils.e(TAG, "禁用 SSL 检查失败", e);
        }
    }
    
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("应用更新下载进度");
            notificationManager.createNotificationChannel(channel);
        }
    }
    
    public void setOnDownloadListener(OnDownloadListener listener) {
        this.listener = listener;
    }
    
    public boolean isDownloading() {
        return isDownloading;
    }
    
    public void cancelDownload() {
        isDownloading = false;
        if (notificationBuilder != null) {
            notificationBuilder.setProgress(0, 0, false);
            notificationBuilder.setContentText("下载已取消");
            notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build());
        }
        mainHandler.postDelayed(() -> notificationManager.cancel(NOTIFICATION_ID), 2000);
    }
    
    public void startDownload(String downloadUrl, String versionName) {
        if (isDownloading) {
            showToast("正在下载中，请稍后...");
            return;
        }
        
        isDownloading = true;
        
        if (listener != null) {
            listener.onStart();
        }
        
        createNotification(versionName);
        
        new Thread(new DownloadRunnable(downloadUrl, versionName)).start();
    }
    
    private void createNotification(String versionName) {
        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        
        notificationBuilder = new NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("正在下载 UIN Tool " + versionName)
            .setContentText("下载中... 0%")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW);
        
        notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build());
    }
    
    private void updateNotification(int progress, long downloaded, long total) {
        if (notificationBuilder == null) return;
        
        String downloadedStr = formatSize(downloaded);
        String totalStr = formatSize(total);
        
        notificationBuilder
            .setProgress(100, progress, false)
            .setContentText("下载中... " + progress + "% (" + downloadedStr + "/" + totalStr + ")");
        
        notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build());
    }
    
    /**
     * 安装 APK 文件
     */
    public void installApk(File apkFile) {
        if (apkFile == null || !apkFile.exists()) {
            LogUtils.e(TAG, "APK 文件不存在");
            showToast("安装包不存在");
            return;
        }
        
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            Uri apkUri;
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                apkUri = FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", apkFile);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } else {
                apkUri = Uri.fromFile(apkFile);
            }
            
            intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            
            LogUtils.success(TAG, "启动安装: " + apkFile.getAbsolutePath());
        } catch (Exception e) {
            LogUtils.e(TAG, "启动安装失败", e);
            showToast("无法启动安装: " + e.getMessage());
        }
    }
    
    private String formatSize(long size) {
        if (size <= 0) return "0 B";
        if (size < 1024) return size + " B";
        if (size < 1024 * 1024) return String.format("%.2f KB", size / 1024.0);
        return String.format("%.2f MB", size / (1024.0 * 1024.0));
    }
    
    private void showToast(final String message) {
        mainHandler.post(() -> Toast.makeText(context, message, Toast.LENGTH_SHORT).show());
    }
    
    private class DownloadRunnable implements Runnable {
        private String downloadUrl;
        private String versionName;
        
        DownloadRunnable(String downloadUrl, String versionName) {
            this.downloadUrl = downloadUrl;
            this.versionName = versionName;
        }
        
        @Override
        public void run() {
            HttpURLConnection connection = null;
            InputStream inputStream = null;
            FileOutputStream outputStream = null;
            
            try {
                File downloadDir = new File(Environment.getExternalStorageDirectory(), "UIN_Tool/downloads");
                if (!downloadDir.exists()) {
                    downloadDir.mkdirs();
                }
                
                String cleanVersion = versionName;
                if (cleanVersion.startsWith("v") || cleanVersion.startsWith("V")) {
                    cleanVersion = cleanVersion.substring(1);
                }
                File apkFile = new File(downloadDir, "UIN_Tool_v" + cleanVersion + ".apk");
                
                if (apkFile.exists()) {
                    apkFile.delete();
                }
                
                LogUtils.i(TAG, "开始下载: " + downloadUrl);
                LogUtils.i(TAG, "保存路径: " + apkFile.getAbsolutePath());
                
                URL url = new URL(downloadUrl);
                connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(30000);
                connection.setReadTimeout(30000);
                connection.setRequestMethod("GET");
                connection.setRequestProperty("User-Agent", "UIN-Tool-Android");
                
                int responseCode = connection.getResponseCode();
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    throw new Exception("服务器响应错误: " + responseCode);
                }
                
                long totalSize = connection.getContentLength();
                LogUtils.param(TAG, "文件大小", formatSize(totalSize));
                
                inputStream = connection.getInputStream();
                outputStream = new FileOutputStream(apkFile);
                
                byte[] buffer = new byte[8192];
                long downloadedSize = 0;
                int bytesRead;
                int lastProgress = -1;
                
                while ((bytesRead = inputStream.read(buffer)) != -1 && isDownloading) {
                    outputStream.write(buffer, 0, bytesRead);
                    downloadedSize += bytesRead;
                    
                    if (totalSize > 0) {
                        final int progress = (int) (downloadedSize * 100 / totalSize);
                        
                        if (progress != lastProgress) {
                            lastProgress = progress;
                            
                            final long finalDownloaded = downloadedSize;
                            final long finalTotal = totalSize;
                            
                            mainHandler.post(() -> {
                                if (listener != null) {
                                    listener.onProgress(progress, finalDownloaded, finalTotal);
                                }
                                updateNotification(progress, finalDownloaded, finalTotal);
                            });
                            
                            LogUtils.d(TAG, "下载进度: " + progress + "% (" + formatSize(downloadedSize) + "/" + formatSize(totalSize) + ")");
                        }
                    }
                }
                
                outputStream.flush();
                
                if (!isDownloading) {
                    apkFile.delete();
                    mainHandler.post(() -> {
                        if (listener != null) {
                            listener.onFailed("下载已取消");
                        }
                    });
                    return;
                }
                
                LogUtils.success(TAG, "下载完成: " + apkFile.getAbsolutePath());
                
                mainHandler.post(() -> {
                    if (listener != null) {
                        listener.onSuccess(apkFile);
                    }
                    notificationManager.cancel(NOTIFICATION_ID);
                });
                
            } catch (final Exception e) {
                LogUtils.e(TAG, "下载失败", e);
                mainHandler.post(() -> {
                    if (listener != null) {
                        listener.onFailed(e.getMessage());
                    }
                    notificationManager.cancel(NOTIFICATION_ID);
                });
            } finally {
                isDownloading = false;
                try {
                    if (inputStream != null) inputStream.close();
                    if (outputStream != null) outputStream.close();
                    if (connection != null) connection.disconnect();
                } catch (Exception e) {
                    LogUtils.e(TAG, "关闭流失败", e);
                }
            }
        }
    }
}