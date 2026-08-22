package com.UIN.Tool.core.update

import com.UIN.Tool.R
import com.UIN.Tool.utils.Str
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.UIN.Tool.data.local.PreferenceManager
import com.UIN.Tool.data.remote.MirrorManager
import com.UIN.Tool.domain.model.ReleaseInfo
import com.UIN.Tool.log.Logger
import com.UIN.Tool.constants.AppConstants as Constants
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

class UpdateChecker(
    private val context: Context,
    private val preferenceManager: PreferenceManager
) {

    companion object {
        private const val TAG = "UpdateChecker"
        private const val GITHUB_API = "https://api.github.com/repos/Undefined-Invalid-Null/UIN-Tool/releases"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(Constants.NETWORK_TIMEOUT, TimeUnit.SECONDS)
        .readTimeout(Constants.NETWORK_READ_TIMEOUT, TimeUnit.SECONDS)
        .writeTimeout(Constants.NETWORK_WRITE_TIMEOUT, TimeUnit.SECONDS)
        .cache(okhttp3.Cache(File(context.cacheDir, "okhttp_cache"), Constants.CACHE_SIZE))
        .build()

    private val mirrorManager = MirrorManager(client)

    private var currentMirror: String? = null
    private var onUpdateListener: OnUpdateListener? = null

    interface OnUpdateListener {
        fun onCheckStart()
        fun onCheckSuccess(releases: List<ReleaseInfo>, hasNewer: Boolean, forceUpdate: Boolean)
        fun onCheckFailed(error: String)
        fun onNoUpdate(currentVersion: String)
    }

    fun setOnUpdateListener(listener: OnUpdateListener) {
        this.onUpdateListener = listener
    }

    fun checkUpdate() {
        onUpdateListener?.onCheckStart()
        Logger.enter(TAG, "checkUpdate")

        Thread {
            try {
                val currentVersionCode = getCurrentVersionCode()
                val currentVersionName = getCurrentVersionName()

                Logger.param(TAG, Str.get(R.string.current_version), Str.get(R.string.currentversionname_code_currentversi, currentVersionName, currentVersionCode))

                // 测试镜像站
                val workingUrl = testMirrors()
                if (workingUrl == null) {
                    Logger.e(TAG, Str.get(R.string.all_mirrors_are_unavailable))
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        onUpdateListener?.onCheckFailed(Str.get(R.string.network_connection_failed_check_your))
                    }
                    return@Thread
                }

                // 获取Releases
                val releases = fetchReleases(workingUrl)
                if (releases.isEmpty()) {
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        onUpdateListener?.onNoUpdate(currentVersionName)
                    }
                    return@Thread
                }

                // 检查是否有新版本
                var hasNewer = false
                var forceUpdate = false
                val ignoredForceTag = preferenceManager.getForceUpdateIgnored()

                for (release in releases) {
                    if (isReleaseNewer(release, currentVersionCode, currentVersionName)) {
                        hasNewer = true
                        forceUpdate = release.forceUpdate
                        // 用户曾忽略强更：忽略的是 tagName，命中则降级为非强更
                        if (forceUpdate && ignoredForceTag.isNotEmpty() && release.tagName == ignoredForceTag) {
                            forceUpdate = false
                            Logger.i(TAG, Str.get(R.string.force_update_ignored_tagname, ignoredForceTag))
                        }

                        // 应用镜像到下载链接
                        currentMirror?.let { mirror ->
                            if (release.downloadUrl.startsWith("https://github.com")) {
                                release.downloadUrl = mirrorManager.getDownloadMirrorUrl(mirror, release.downloadUrl, true)
                            }
                        }
                        break
                    }
                }

                Logger.param(TAG, Str.get(R.string.new_version_available), hasNewer)
                Logger.param(TAG, Str.get(R.string.mandatory_update), forceUpdate)

                val finalReleases = releases
                val finalHasNewer = hasNewer
                val finalForceUpdate = forceUpdate

                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    onUpdateListener?.onCheckSuccess(finalReleases, finalHasNewer, finalForceUpdate)
                }

            } catch (e: Exception) {
                Logger.e(TAG, Str.get(R.string.update_check_failed), e)
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    onUpdateListener?.onCheckFailed(e.message ?: Str.get(R.string.update_check_failed))
                }
            } finally {
                Logger.exit(TAG, "checkUpdate", System.currentTimeMillis())
            }
        }.start()
    }

    private fun testMirrors(): String? {
        // 先尝试直连
        Logger.d(TAG, Str.get(R.string.testing_direct_connection_github_api, GITHUB_API))
        if (testUrl(GITHUB_API)) {
            Logger.success(TAG, Str.get(R.string.direct_connection_available))
            return GITHUB_API
        }

        // 获取启用的镜像
        val enabledMirrors = preferenceManager.getEnabledMirrors()
        val useCdn = preferenceManager.isUseCdn()

        val mirrorsToTest = if (enabledMirrors.isNotEmpty() && useCdn) {
            enabledMirrors
        } else {
            Constants.DEFAULT_MIRRORS
        }

        for (mirror in mirrorsToTest) {
            val testUrl = mirrorManager.getMirrorUrl(mirror, GITHUB_API)
            Logger.d(TAG, Str.get(R.string.testing_mirror_testurl, testUrl))
            if (testUrl(testUrl)) {
                currentMirror = mirror
                Logger.success(TAG, Str.get(R.string.found_usable_mirror_mirror, mirror))
                return testUrl
            }
        }

        // 如果配置的镜像都不可用，尝试默认镜像
        if (enabledMirrors.isNotEmpty() && useCdn) {
            Logger.d(TAG, Str.get(R.string.configured_mirror_unavailable_trying))
            for (mirror in Constants.DEFAULT_MIRRORS) {
                val testUrl = mirrorManager.getMirrorUrl(mirror, GITHUB_API)
                if (testUrl(testUrl)) {
                    currentMirror = mirror
                    Logger.success(TAG, Str.get(R.string.found_usable_default_mirror_mirror, mirror))
                    return testUrl
                }
            }
        }

        return null
    }

    private fun testUrl(urlString: String): Boolean {
        return try {
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            connection.requestMethod = "HEAD"
            connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
            connection.setRequestProperty("User-Agent", "UIN-Tool-Android")
            val responseCode = connection.responseCode
            connection.disconnect()
            responseCode == 200 || responseCode == 302 || responseCode == 301
        } catch (e: Exception) {
            false
        }
    }

    private fun fetchReleases(apiUrl: String): List<ReleaseInfo> {
        try {
            val url = "$apiUrl?per_page=30"
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "UIN-Tool-Android")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                throw Exception(Str.get(R.string.github_api_error_response_code, response.code))
            }

            val body = response.body?.string() ?: return emptyList()
            return parseReleases(body)

        } catch (e: Exception) {
            Logger.e(TAG, Str.get(R.string.failed_to_fetch_releases), e)
            throw e
        }
    }

    private fun parseReleases(json: String): List<ReleaseInfo> {
        val releases = mutableListOf<ReleaseInfo>()
        val jsonArray = JSONArray(json)

        for (i in 0 until jsonArray.length()) {
            val release = jsonArray.getJSONObject(i)

            if (release.optBoolean("draft", false)) {
                continue
            }

            val tagName = release.optString("tag_name", "")
            val parts = tagName.split("-", limit = 3)

            val info = ReleaseInfo().apply {
                this.tagName = tagName
                releaseDate = release.optString("published_at", "")
                releaseNotes = release.optString("body", "")
                isPreRelease = release.optBoolean("prerelease", false)

                versionCode = parts.getOrNull(0) ?: "1"
                versionName = parts.getOrNull(1) ?: versionCode
                forceFlag = parts.getOrNull(2) ?: "0"
                forceUpdate = forceFlag == "1"

                // 解析发布正文中声明的 SHA-256（形如 "SHA256: <64hex>" / "sha256=<64hex>" / "<64hex>" 独立行）
                sha256 = extractSha256(releaseNotes)

                val assets = release.optJSONArray("assets")
                if (assets != null) {
                    for (j in 0 until assets.length()) {
                        val asset = assets.getJSONObject(j)
                        val name = asset.getString("name")
                        if (name.endsWith(".apk")) {
                            downloadUrl = asset.getString("browser_download_url")
                            apkSize = asset.getLong("size")
                        }
                    }
                }

                if (downloadUrl.isEmpty()) {
                    downloadUrl = "https://github.com/Undefined-Invalid-Null/UIN-Tool/releases/tag/$tagName"
                }
            }

            releases.add(info)
        }

        releases.sortWith(compareByDescending { it.toComparableVersion() })

        // SHA-256 只需对最新一个 release 拉取即可（下载对话框固定用 releases.first()），
        // 逐条拉取会让每次更新检查都产生 N 次网络请求，导致「点击检查更新」迟迟不弹窗。
        // 正文已声明哈希的 release 保留正文哈希，无需额外请求。
        if (releases.isNotEmpty() && releases.first().sha256.isEmpty()) {
            val first = releases.first()
            if (first.downloadUrl.endsWith(".apk")) {
                first.sha256 = fetchSha256Asset(first.downloadUrl)
            }
        }

        return releases
    }

    /**
     * 判断 release 是否比当前版本新。
     * 优先用 versionCode（整型）比较；versionCode 缺失或非数字（如 tag 只有版本名）
     * 时回退到版本号三元组比较，避免"无前缀 tag 一律为 0 → 漏算新版"。
     */
    private fun isReleaseNewer(release: ReleaseInfo, currentCode: Int, currentName: String): Boolean {
        val releaseCode = release.versionCode.toIntOrNull()
        if (releaseCode != null) {
            return releaseCode > currentCode
        }
        return compareVersionTriplets(release.versionName, currentName) > 0
    }

    /**
     * 提取可比较的版本数值。
     * 注意两套刻度不能混排：带 versionCode 的 tag（如 `20-5.4.0-1`）code 是单调小整数，
     * 而旧版无 code 的 tag（如 `V2.6.0`）按三元组能拼出百万级大数。
     * 若直接返回原始值，`V2.6.0`(≈2,006,000) 会排在 `20-5.4.0-1`(code=20) 之上，
     * 导致「最新版本」误判为 v2.6.0。修复：带 code 的 release 统一加 1e9 基线上移，
     * 保证任何旧 tag 的三元组（<1e9）都排在其下。
     */
    private fun ReleaseInfo.toComparableVersion(): Long {
        val code = versionCode.toIntOrNull()
        if (code != null) return code.toLong() + 1_000_000_000L
        val parts = versionName
            .trim()
            .trimStart('v', 'V')
            .split('.')
            .map { it.toIntOrNull() ?: 0 }
        // 三元组拼成 大数：major*1000000 + minor*1000 + patch
        return (parts.getOrNull(0) ?: 0) * 1_000_000L +
            (parts.getOrNull(1) ?: 0) * 1_000L +
            (parts.getOrNull(2) ?: 0)
    }

    /** 版本号三元组比较：a > b 返回正数，a < b 返回负数，相等返回 0 */
    private fun compareVersionTriplets(a: String, b: String): Int {
        val pa = a.trim().trimStart('v', 'V').split('.').map { it.toIntOrNull() ?: 0 }
        val pb = b.trim().trimStart('v', 'V').split('.').map { it.toIntOrNull() ?: 0 }
        val len = maxOf(pa.size, pb.size)
        for (i in 0 until len) {
            val x = pa.getOrNull(i) ?: 0
            val y = pb.getOrNull(i) ?: 0
            if (x != y) return x.compareTo(y)
        }
        return 0
    }

    /**
     * 从 release 正文提取 64 位十六进制 SHA-256（小写归一）。
     * 支持格式：`SHA256: <hex>`、`sha256=<hex>`、`SHA-256 <hex>`、独立 `hex` 行。
     */
    private fun extractSha256(body: String): String {
        if (body.isBlank()) return ""
        val hexRegex = Regex("[0-9a-fA-F]{64}")
        val match = hexRegex.find(body) ?: return ""
        return match.value.lowercase()
    }

    /**
     * 拉取 `.sha256` 资产内容（纯文本，形如 "<hex>  filename" 或仅 "<hex>"）。
     * 仅在获取 release 列表时对最新几个 release 调用，失败静默返回空。
     */
    private fun fetchSha256Asset(apkUrl: String): String {
        return try {
            val shaUrl = apkUrl + ".sha256"
            val request = Request.Builder()
                .url(shaUrl)
                .header("User-Agent", "UIN-Tool-Android")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return ""
                val text = response.body?.string() ?: return ""
                extractSha256(text)
            }
        } catch (e: Exception) {
            Logger.d(TAG, "fetchSha256Asset failed: ${e.message}")
            ""
        }
    }

    private fun getCurrentVersionCode(): Int {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode.toInt()
            } else {
                packageInfo.versionCode
            }
        } catch (e: Exception) {
            1
        }
    }

    private fun getCurrentVersionName(): String {
        return try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0.0"
        } catch (e: Exception) {
            "1.0.0"
        }
    }

    fun ignoreVersion(versionName: String) {
        preferenceManager.setIgnoredVersion(versionName)
        Logger.i(TAG, Str.get(R.string.ignore_version_versionname, versionName))
    }

    fun ignoreForceUpdate(tagName: String) {
        preferenceManager.setForceUpdateIgnored(tagName)
        Logger.i(TAG, Str.get(R.string.ignoring_force_update_tagname, tagName))
    }
}