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

/**
 * 应用内下载管理器
 * 支持后台下载、进度显示、通知栏提示
 */
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
    
    /**
     * 禁用 SSL 证书检查（解决部分设备的证书问题）
     */
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
            
            HostnameVerifier allHostsValid = new HostnameVerifier() {
                @Override
                public boolean verify(String hostname, SSLSession session) {
                    return true;
                }
            };
            HttpsURLConnection.setDefaultHostnameVerifier(allHostsValid);
            
            LogUtils.i(TAG, "SSL 证书检查已禁用");
        } catch (Exception e) {
            LogUtils.e(TAG, "禁用 SSL 检查失败", e);
        }
    }
    
    /**
     * 创建通知渠道
     */
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
    
    /**
     * 取消下载
     */
    public void cancelDownload() {
        isDownloading = false;
        if (notificationBuilder != null) {
            notificationBuilder.setProgress(0, 0, false);
            notificationBuilder.setContentText("下载已取消");
            notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build());
        }
        // 延迟关闭通知
        mainHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                notificationManager.cancel(NOTIFICATION_ID);
            }
        }, 2000);
    }
    
    /**
     * 开始下载
     */
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
    
    /**
     * 创建下载通知
     */
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
    
    /**
     * 更新下载进度通知
     */
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
     * 显示下载成功通知
     */
    private void showDownloadSuccessNotification(File file, String versionName) {
        try {
            Intent installIntent = getInstallIntent(file);
            PendingIntent installPendingIntent = PendingIntent.getActivity(
                context, 0, installIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            
            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle("下载完成")
                .setContentText("UIN Tool " + versionName + " 已下载完成")
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .addAction(android.R.drawable.ic_menu_save, "安装", installPendingIntent);
            
            notificationManager.notify(NOTIFICATION_ID + 1, builder.build());
        } catch (Exception e) {
            LogUtils.e(TAG, "显示下载成功通知失败", e);
        }
    }
    
    /**
     * 显示下载失败通知
     */
    private void showDownloadFailedNotification(String error) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("下载失败")
            .setContentText(error)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT);
        
        notificationManager.notify(NOTIFICATION_ID + 2, builder.build());
    }
    
    /**
     * 获取安装 Intent
     */
    private Intent getInstallIntent(File file) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        Uri apkUri;
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            apkUri = FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", file);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } else {
            apkUri = Uri.fromFile(file);
        }
        
        intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return intent;
    }
    
    /**
     * 格式化文件大小
     */
    private String formatSize(long size) {
        if (size <= 0) return "未知";
        if (size < 1024) return size + " B";
        if (size < 1024 * 1024) return String.format("%.2f KB", size / 1024.0);
        if (size < 1024 * 1024 * 1024) return String.format("%.2f MB", size / (1024.0 * 1024.0));
        return String.format("%.2f GB", size / (1024.0 * 1024.0 * 1024.0));
    }
    
    /**
     * 显示 Toast
     */
    private void showToast(final String message) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
            }
        });
    }
    
    /**
     * 显示安装对话框
     */
    private void showInstallDialog(final File apkFile, String versionName) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                // 检查 Activity 是否还在运行
                if (context instanceof android.app.Activity) {
                    android.app.Activity activity = (android.app.Activity) context;
                    if (activity.isFinishing() || activity.isDestroyed()) {
                        LogUtils.w(TAG, "Activity 已销毁，无法显示对话框，直接启动安装");
                        Intent installIntent = getInstallIntent(apkFile);
                        context.startActivity(installIntent);
                        return;
                    }
                }
                
                try {
                    android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(context);
                    builder.setTitle("下载完成");
                    builder.setMessage("UIN Tool " + versionName + " 已下载完成，是否立即安装？");
                    builder.setPositiveButton("安装", new android.content.DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(android.content.DialogInterface dialog, int which) {
                            Intent installIntent = getInstallIntent(apkFile);
                            context.startActivity(installIntent);
                        }
                    });
                    builder.setNegativeButton("稍后", null);
                    builder.setCancelable(true);
                    builder.show();
                } catch (Exception e) {
                    LogUtils.e(TAG, "显示安装对话框失败，直接启动安装", e);
                    Intent installIntent = getInstallIntent(apkFile);
                    context.startActivity(installIntent);
                }
            }
        });
    }
    
    /**
     * 下载任务
     */
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
                // 创建下载目录
                File downloadDir = new File(Environment.getExternalStorageDirectory(), "UIN_Tool/downloads");
                if (!downloadDir.exists()) {
                    downloadDir.mkdirs();
                }
                
                // 清理版本名（移除 v 前缀）
                String cleanVersion = versionName;
                if (cleanVersion.startsWith("v") || cleanVersion.startsWith("V")) {
                    cleanVersion = cleanVersion.substring(1);
                }
                File apkFile = new File(downloadDir, "UIN_Tool_v" + cleanVersion + ".apk");
                
                // 如果文件已存在，先删除
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
                        
                        // 每 1% 更新一次进度
                        if (progress != lastProgress) {
                            lastProgress = progress;
                            
                            final long finalDownloaded = downloadedSize;
                            final long finalTotal = totalSize;
                            
                            mainHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    if (listener != null) {
                                        listener.onProgress(progress, finalDownloaded, finalTotal);
                                    }
                                    updateNotification(progress, finalDownloaded, finalTotal);
                                }
                            });
                            
                            LogUtils.d(TAG, "下载进度: " + progress + "% (" + formatSize(downloadedSize) + "/" + formatSize(totalSize) + ")");
                        }
                    }
                }
                
                outputStream.flush();
                
                if (!isDownloading) {
                    apkFile.delete();
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (listener != null) {
                                listener.onFailed("下载已取消");
                            }
                        }
                    });
                    return;
                }
                
                LogUtils.success(TAG, "下载完成: " + apkFile.getAbsolutePath());
                
                showDownloadSuccessNotification(apkFile, versionName);
                
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (listener != null) {
                            listener.onSuccess(apkFile);
                        }
                        notificationManager.cancel(NOTIFICATION_ID);
                        showInstallDialog(apkFile, versionName);
                    }
                });
                
            } catch (final Exception e) {
                LogUtils.e(TAG, "下载失败", e);
                showDownloadFailedNotification(e.getMessage());
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (listener != null) {
                            listener.onFailed(e.getMessage());
                        }
                        notificationManager.cancel(NOTIFICATION_ID);
                    }
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