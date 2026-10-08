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

            // D-08：execute() 是阻塞调用，原先在调用方协程（多为 Main）上执行会抛
            // NetworkOnMainThreadException 并被吞掉，表现为「所有镜像都不可达」。
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                testClient.newCall(request).execute().use { response ->
                    response.isSuccessful || response.code in 301..308
                }
            }
        } catch (e: Exception) {
            false
        }
    }

    /** D-32：镜像能力位 —— 子串猜模板会把 gitclone 这类「仅 git 克隆」镜像当成通用代理。 */
    private enum class MirrorStyle { HOST_REPLACEMENT, PATH_PREFIX, GIT_ONLY, UNKNOWN }

    private fun mirrorStyle(safeMirror: String): MirrorStyle = when {
        safeMirror.contains("fastgit") -> MirrorStyle.HOST_REPLACEMENT
        // 只有 git clone 能力的镜像（无 API/下载代理），不能拼 `$mirror/$url`
        safeMirror.contains("gitclone") -> MirrorStyle.GIT_ONLY
        safeMirror.contains("ghproxy") || safeMirror.contains("gh-proxy") ||
            safeMirror.contains("moeyy") || safeMirror.contains("kgithub") ||
            safeMirror.contains("gitmirror") || safeMirror.contains("gh.llkk") ||
            safeMirror.contains("ghps.cc") || safeMirror.contains("mirror.ghproxy") -> MirrorStyle.PATH_PREFIX
        else -> MirrorStyle.UNKNOWN
    }

    fun getMirrorUrl(mirror: String, originalUrl: String): String {
        if (mirror.isBlank()) return originalUrl
        val safeMirror = forceHttps(mirror)
        return when (mirrorStyle(safeMirror)) {
            MirrorStyle.HOST_REPLACEMENT -> originalUrl.replace(
                "https://api.github.com",
                "$safeMirror/api.github.com"
            )
            // D-32：只有 git clone 能力的镜像不代理 API（原先子串模板一律 `"$mirror/$url"`，
            // gitclone 拼出来的地址必然 404）。未知镜像保持原有的路径前缀约定，
            // 但**只对下载生效**，API 调用回退原地址，避免把「根路径可达」当「可代理 API」。
            MirrorStyle.GIT_ONLY -> originalUrl
            MirrorStyle.UNKNOWN ->
                if (originalUrl.startsWith("https://api.github.com")) originalUrl
                else "$safeMirror/$originalUrl"
            MirrorStyle.PATH_PREFIX -> "$safeMirror/$originalUrl"
        }
    }

    fun getDownloadMirrorUrl(mirror: String, originalUrl: String, useCdn: Boolean): String {
        if (!useCdn || mirror.isEmpty()) return originalUrl
        val safeMirror = forceHttps(mirror)
        return when (mirrorStyle(safeMirror)) {
            MirrorStyle.PATH_PREFIX -> "$safeMirror/$originalUrl"
            MirrorStyle.HOST_REPLACEMENT -> originalUrl.replace(
                "https://github.com",
                "$safeMirror/github.com"
            )
            MirrorStyle.GIT_ONLY -> originalUrl
            // 未知镜像沿用路径前缀约定（与历史行为一致）
            MirrorStyle.UNKNOWN -> "$safeMirror/$originalUrl"
        }
    }

    /** 镜像地址强制使用 https：http 直接升级，禁止明文传输。
     *  D-32③：本机/局域网镜像（localhost、192.168/10/172.16-31、IPv4/v6 字面量）保留 http，
     *  否则强升 https 必然连不上。 */
    private fun forceHttps(url: String): String {
        if (url.isBlank()) return url
        if (isLocalMirror(url)) return url
        return if (url.startsWith("http://")) "https://" + url.removePrefix("http://") else url
    }

    private fun isLocalMirror(url: String): Boolean {
        val host = try {
            java.net.URI(url).host
        } catch (e: Exception) {
            null
        } ?: return false
        if (host.equals("localhost", ignoreCase = true)) return true
        if (host.contains(':')) return true // IPv6 字面量
        val parts = host.split('.')
        if (parts.size != 4 || parts.any { it.toIntOrNull() == null }) return false
        val p0 = parts[0].toInt()
        val p1 = parts[1].toInt()
        return p0 == 10 || (p0 == 192 && p1 == 168) || (p0 == 172 && p1 in 16..31) || p0 == 127
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
            Logger.e(TAG, "Failed to parse mirror line: $line", e)
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