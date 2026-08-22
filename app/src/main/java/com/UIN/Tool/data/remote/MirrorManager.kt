package com.UIN.Tool.data.remote

import com.UIN.Tool.R
import com.UIN.Tool.utils.Str
import com.UIN.Tool.domain.model.MirrorItem
import com.UIN.Tool.log.Logger
import com.UIN.Tool.constants.AppConstants as Constants
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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
        // ✅ 并发探测所有镜像（原来逐个串行最多 5s×N），整体耗时从 N×5s 降到约 5s
        return coroutineScope {
            mirrors.map { mirror ->
                async { mirror.copy(reachable = testMirrorReachable(mirror.url)) }
            }.awaitAll()
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
        val safeMirror = forceHttps(mirror)
        return when {
            safeMirror.contains("ghproxy") -> "$safeMirror/$originalUrl"
            safeMirror.contains("fastgit") -> originalUrl.replace(
                "https://api.github.com",
                "$safeMirror/api.github.com"
            )
            safeMirror.contains("moeyy") -> "$safeMirror/$originalUrl"
            else -> "$safeMirror/$originalUrl"
        }
    }

    fun getDownloadMirrorUrl(mirror: String, originalUrl: String, useCdn: Boolean): String {
        if (!useCdn || mirror.isEmpty()) return originalUrl
        val safeMirror = forceHttps(mirror)
        return when {
            safeMirror.contains("ghproxy") -> "$safeMirror/$originalUrl"
            safeMirror.contains("fastgit") -> originalUrl.replace(
                "https://github.com",
                "$safeMirror/github.com"
            )
            else -> originalUrl
        }
    }

    /** 镜像地址强制使用 https：http 直接升级，禁止明文传输。 */
    private fun forceHttps(url: String): String {
        if (url.isBlank()) return url
        return if (url.startsWith("http://")) "https://" + url.removePrefix("http://") else url
    }

    /** 解析时统一为 https：http 升级，无协议时补全 https。 */
    private fun normalizeScheme(url: String): String {
        val trimmed = url.trim()
        return when {
            trimmed.startsWith("http://") -> "https://" + trimmed.removePrefix("http://")
            trimmed.startsWith("https://") -> trimmed
            else -> "https://$trimmed"
        }
    }

    fun getDefaultMirrors(): List<MirrorItem> {
        return Constants.DEFAULT_MIRRORS.map { url ->
            MirrorItem(
                name = url.substringAfter("//").substringBefore("."),
                url = url,
                isDefault = true,
                remark = when {
                    url.contains("fastgit") -> Str.get(R.string.fast_domestic_mirror)
                    url.contains("ghproxy") -> Str.get(R.string.proxy_acceleration)
                    url.contains("moeyy") -> Str.get(R.string.domestic_mirror)
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
                    url = normalizeScheme(url),
                    remark = remark,
                    isDefault = false
                )
            }
            // 如果只有URL
            if (line.contains("http")) {
                val url = line.trim()
                return MirrorItem(
                    name = url.substringAfter("//").substringBefore("."),
                    url = normalizeScheme(url),
                    isDefault = false
                )
            }
        } catch (e: Exception) {
            Logger.e(TAG, Str.get(R.string.failed_to_parse_mirror_line_line, line), e)
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