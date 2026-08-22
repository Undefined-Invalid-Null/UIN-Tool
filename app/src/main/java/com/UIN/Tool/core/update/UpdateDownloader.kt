package com.UIN.Tool.core.update

import com.UIN.Tool.R
import com.UIN.Tool.utils.Str
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
import com.UIN.Tool.constants.AppConstants as Constants
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
                Str.get(R.string.update_download),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = Str.get(R.string.app_update_download_progress)
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun startDownload(downloadUrl: String, versionName: String, expectedSha256: String = "") {
        if (isDownloading) {
            Logger.w(TAG, Str.get(R.string.download_in_progress))
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

        Logger.i(TAG, Str.get(R.string.starting_download_downloadurl, downloadUrl))
        Logger.param(TAG, Str.get(R.string.save_path), file.absolutePath)
        if (expectedSha256.isNotEmpty()) {
            Logger.param(TAG, "expectedSha256", expectedSha256)
        } else {
            Logger.w(TAG, "no SHA-256 published in release, integrity check skipped")
        }

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
                    throw Exception(Str.get(R.string.download_failed_response_code, response.code))
                }

                val body = response.body ?: throw Exception(Str.get(R.string.response_body_is_empty))
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
                                throw Exception(Str.get(R.string.download_cancelled))
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
                                Logger.d(TAG, Str.get(R.string.download_progress_progress, progress))
                            }
                        }
                    }
                }

                // ✅ 下载完成：若发布方声明了 SHA-256，则校验文件哈希，不匹配即拒绝安装
                if (expectedSha256.isNotEmpty()) {
                    val actual = computeSha256(file)
                    if (actual.isEmpty() || !actual.equals(expectedSha256, ignoreCase = true)) {
                        Logger.e(TAG, Str.get(R.string.apk_sha256_mismatch, expectedSha256, actual))
                        file.delete()
                        throw Exception(Str.get(R.string.apk_sha256_mismatch, expectedSha256, actual))
                    }
                    Logger.success(TAG, "SHA-256 verified: $actual")
                }

                Logger.success(TAG, Str.get(R.string.download_complete_file_absolutepath, file.absolutePath))

                notificationManager.cancel(NOTIFICATION_ID)
                isDownloading = false
                onDownloadListener?.onSuccess(file)

            } catch (e: Exception) {
                Logger.e(TAG, Str.get(R.string.download_failed), e)
                // 失败/中断时删除半包，避免残留损坏 APK
                try {
                    if (file.exists()) file.delete()
                } catch (_: Exception) {
                }
                notificationManager.cancel(NOTIFICATION_ID)
                isDownloading = false
                onDownloadListener?.onFailed(e.message ?: Str.get(R.string.download_failed))
            }
        }

        downloadThread?.start()
    }

    /**
     * 计算文件 SHA-256（小写十六进制），失败返回空串。
     */
    private fun computeSha256(file: File): String {
        return try {
            java.security.MessageDigest.getInstance("SHA-256").let { digest ->
                file.inputStream().use { input ->
                    val buffer = ByteArray(8192)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        digest.update(buffer, 0, read)
                    }
                }
                digest.digest().joinToString("") { "%02x".format(it) }
            }
        } catch (e: Exception) {
            Logger.e(TAG, "computeSha256 failed: ${e.message}", e)
            ""
        }
    }

    fun cancelDownload() {
        isDownloading = false
        downloadThread?.interrupt()
        notificationManager.cancel(NOTIFICATION_ID)
        Logger.i(TAG, Str.get(R.string.download_cancelled))
    }

    fun installApk(file: File) {
        if (!file.exists()) {
            Logger.e(TAG, Str.get(R.string.apk_file_not_found_file_absolutepath, file.absolutePath))
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
            Logger.success(TAG, Str.get(R.string.starting_install_file_name, file.name))
        } catch (e: Exception) {
            Logger.e(TAG, Str.get(R.string.failed_to_start_install), e)
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
            .setContentTitle(Str.get(R.string.downloading_uin_tool_versionname, versionName))
            .setContentText(Str.get(R.string.downloading_0))
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
            .setContentTitle(Str.get(R.string.downloading_update))
            .setContentText(Str.get(R.string.downloading_progress_downloadedstr_t, progress, downloadedStr, totalStr))
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