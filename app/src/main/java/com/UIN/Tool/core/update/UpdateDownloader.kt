package com.UIN.Tool.core.update

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import com.UIN.Tool.MainActivity
import com.UIN.Tool.log.Logger
import com.UIN.Tool.utils.Constants
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

class UpdateDownloader(
    private val context: Context
) {

    companion object {
        private const val TAG = "UpdateDownloader"
        private const val CHANNEL_ID = "update_download_channel"
        private const val NOTIFICATION_ID = 1001
    }

    private var onDownloadListener: OnDownloadListener? = null
    private var isDownloading = false
    private var downloadThread: Thread? = null

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    interface OnDownloadListener {
        fun onStart()
        fun onProgress(progress: Int, downloaded: Long, total: Long)
        fun onSuccess(file: File)
        fun onFailed(error: String)
    }

    init {
        createNotificationChannel()
    }

    fun setOnDownloadListener(listener: OnDownloadListener) {
        this.onDownloadListener = listener
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "更新下载",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "应用更新下载进度"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun startDownload(downloadUrl: String, versionName: String) {
        if (isDownloading) {
            Logger.w(TAG, "下载正在进行中")
            return
        }

        isDownloading = true
        onDownloadListener?.onStart()

        // 创建下载目录
        val downloadDir = File(Constants.DOWNLOAD_DIR)
        if (!downloadDir.exists()) {
            downloadDir.mkdirs()
        }

        // 清理版本号前缀
        val cleanVersion = versionName.replace(Regex("^[vV]"), "")
        val fileName = "UIN_Tool_v${cleanVersion}.apk"
        val file = File(downloadDir, fileName)

        if (file.exists()) {
            file.delete()
        }

        Logger.i(TAG, "开始下载: $downloadUrl")
        Logger.param(TAG, "保存路径", file.absolutePath)

        // 显示通知
        showNotification(versionName)

        downloadThread = Thread {
            try {
                val request = Request.Builder()
                    .url(downloadUrl)
                    .header("User-Agent", "UIN-Tool-Android")
                    .build()

                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    throw Exception("下载失败: ${response.code}")
                }

                val body = response.body ?: throw Exception("响应体为空")
                val contentLength = body.contentLength()

                FileOutputStream(file).use { fos ->
                    body.byteStream().use { inputStream ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        var downloaded = 0L
                        var lastProgress = -1

                        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                            // 检查是否被取消
                            if (!isDownloading) {
                                fos.close()
                                file.delete()
                                throw Exception("下载已取消")
                            }

                            fos.write(buffer, 0, bytesRead)
                            downloaded += bytesRead

                            val progress = if (contentLength > 0) {
                                (downloaded * 100 / contentLength).toInt()
                            } else 0

                            if (progress != lastProgress) {
                                lastProgress = progress
                                updateNotification(progress, downloaded, contentLength)
                                onDownloadListener?.onProgress(progress, downloaded, contentLength)
                                Logger.d(TAG, "下载进度: $progress%")
                            }
                        }
                    }
                }

                Logger.success(TAG, "下载完成: ${file.absolutePath}")

                notificationManager.cancel(NOTIFICATION_ID)
                isDownloading = false
                onDownloadListener?.onSuccess(file)

            } catch (e: Exception) {
                Logger.e(TAG, "下载失败", e)
                notificationManager.cancel(NOTIFICATION_ID)
                isDownloading = false
                onDownloadListener?.onFailed(e.message ?: "下载失败")
            }
        }

        downloadThread?.start()
    }

    fun cancelDownload() {
        isDownloading = false
        downloadThread?.interrupt()
        notificationManager.cancel(NOTIFICATION_ID)
        Logger.i(TAG, "下载已取消")
    }

    fun installApk(file: File) {
        if (!file.exists()) {
            Logger.e(TAG, "APK文件不存在: ${file.absolutePath}")
            return
        }

        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(intent)
            Logger.success(TAG, "启动安装: ${file.name}")
        } catch (e: Exception) {
            Logger.e(TAG, "启动安装失败", e)
        }
    }

    private fun showNotification(versionName: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("正在下载 UIN Tool $versionName")
            .setContentText("下载中... 0%")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setProgress(100, 0, false)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun updateNotification(progress: Int, downloaded: Long, total: Long) {
        val downloadedStr = formatSize(downloaded)
        val totalStr = formatSize(total)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("正在下载更新")
            .setContentText("下载中... $progress% ($downloadedStr/$totalStr)")
            .setProgress(100, progress, false)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun formatSize(size: Long): String {
        return when {
            size <= 0 -> "0 B"
            size < 1024 -> "$size B"
            size < 1024 * 1024 -> String.format("%.2f KB", size / 1024.0)
            else -> String.format("%.2f MB", size / (1024.0 * 1024.0))
        }
    }
}