package com.UIN.Tool.data.remote

import com.UIN.Tool.domain.model.MirrorItem
import com.UIN.Tool.log.Logger
import com.UIN.Tool.utils.Constants
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class MirrorManager(
    private val client: OkHttpClient
) {

    companion object {
        private const val TAG = "MirrorManager"
    }

    private val testClient = client.newBuilder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    suspend fun testMirrors(mirrors: List<MirrorItem>): List<MirrorItem> {
        return mirrors.map { mirror ->
            mirror.copy(reachable = testMirrorReachable(mirror.url))
        }
    }

    private suspend fun testMirrorReachable(url: String): Boolean {
        return try {
            val request = Request.Builder()
                .url("$url/")
                .head()
                .build()

            testClient.newCall(request).execute().use { response ->
                response.isSuccessful || response.code in 301..308
            }
        } catch (e: Exception) {
            false
        }
    }

    fun getMirrorUrl(mirror: String, originalUrl: String): String {
        return when {
            mirror.contains("ghproxy") -> "$mirror/$originalUrl"
            mirror.contains("fastgit") -> originalUrl.replace(
                "https://api.github.com",
                "$mirror/api.github.com"
            )
            mirror.contains("moeyy") -> "$mirror/$originalUrl"
            else -> "$mirror/$originalUrl"
        }
    }

    fun getDownloadMirrorUrl(mirror: String, originalUrl: String, useCdn: Boolean): String {
        if (!useCdn || mirror.isEmpty()) return originalUrl
        return when {
            mirror.contains("ghproxy") -> "$mirror/$originalUrl"
            mirror.contains("fastgit") -> originalUrl.replace(
                "https://github.com",
                "$mirror/github.com"
            )
            else -> originalUrl
        }
    }

    fun getDefaultMirrors(): List<MirrorItem> {
        return Constants.DEFAULT_MIRRORS.map { url ->
            MirrorItem(
                name = url.substringAfter("//").substringBefore("."),
                url = url,
                isDefault = true,
                remark = when {
                    url.contains("fastgit") -> "国内高速镜像"
                    url.contains("ghproxy") -> "代理加速"
                    url.contains("moeyy") -> "国内镜像"
                    else -> ""
                }
            )
        }
    }

    fun parseMirrorFromString(line: String): MirrorItem? {
        try {
            val parts = line.split("|", limit = 3)
            if (parts.size >= 2) {
                val name = parts[0].trim()
                val url = parts[1].trim()
                val remark = if (parts.size > 2) parts[2].trim() else ""
                return MirrorItem(
                    name = name,
                    url = if (url.startsWith("http://") || url.startsWith("https://")) url else "https://$url",
                    remark = remark,
                    isDefault = false
                )
            }
            // 如果只有URL
            if (line.contains("http")) {
                val url = line.trim()
                return MirrorItem(
                    name = url.substringAfter("//").substringBefore("."),
                    url = if (url.startsWith("http://") || url.startsWith("https://")) url else "https://$url",
                    isDefault = false
                )
            }
        } catch (e: Exception) {
            Logger.e(TAG, "解析镜像行失败: $line", e)
        }
        return null
    }

    fun formatMirrorToString(mirror: MirrorItem): String {
        return if (mirror.remark.isNotEmpty()) {
            "${mirror.name}|${mirror.url}|${mirror.remark}"
        } else {
            "${mirror.name}|${mirror.url}"
        }
    }
}