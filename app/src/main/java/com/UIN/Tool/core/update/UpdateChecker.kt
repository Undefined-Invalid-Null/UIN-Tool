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
    private val preferenceManager: PreferenceManager,
    // B-54：OkHttpClient 由 AppContainer 注入（原先生了第二个 client 且与 AppContainer 指向
    // 同一个 cacheDir/okhttp_cache，双 Cache 实例同目录会抛 CACHE DIRECTORY IS ALREADY IN USE）
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(Constants.NETWORK_TIMEOUT, TimeUnit.SECONDS)
        .readTimeout(Constants.NETWORK_READ_TIMEOUT, TimeUnit.SECONDS)
        .writeTimeout(Constants.NETWORK_WRITE_TIMEOUT, TimeUnit.SECONDS)
        .cache(okhttp3.Cache(File(context.cacheDir, "update_okhttp_cache"), Constants.CACHE_SIZE))
        .build()
) {

    companion object {
        private const val TAG = "UpdateChecker"
        private const val GITHUB_API = "https://api.github.com/repos/Undefined-Invalid-Null/UIN-Tool/releases"
    }

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

    /** B-58：Activity/页面销毁后调用，避免检查结束后仍回调已销毁的界面 */
    fun clearUpdateListener() {
        this.onUpdateListener = null
    }

    fun checkUpdate() {
        // B-58：检查开始时快照 listener，期间被 clearUpdateListener() 清掉就不再回调
        val listener = onUpdateListener
        listener?.onCheckStart()
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
                        listener?.onCheckFailed(Str.get(R.string.network_connection_failed_check_your))
                    }
                    return@Thread
                }

                // 获取Releases
                val releases = fetchReleases(workingUrl)
                if (releases.isEmpty()) {
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        listener?.onNoUpdate(currentVersionName)
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
                    listener?.onCheckSuccess(finalReleases, finalHasNewer, finalForceUpdate)
                }

            } catch (e: Exception) {
                Logger.e(TAG, Str.get(R.string.update_check_failed), e)
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    listener?.onCheckFailed(e.message ?: Str.get(R.string.update_check_failed))
                }
            } finally {
                Logger.exit(TAG, "checkUpdate", System.currentTimeMillis())
            }
        }.start()
    }

    private fun testMirrors(): String? {
        // B-57：原先直连 + 各镜像串行 HEAD 探测，每步 8s，最坏 8×N 秒卡在开屏；
        // 改为「同一批候选并发竞速，第一个成功即返回」，单轮耗时 ≈ 单次超时。
        data class Probe(val url: String, val mirror: String?)

        fun race(candidates: List<Probe>): Probe? {
            if (candidates.isEmpty()) return null
            val pool = java.util.concurrent.Executors.newFixedThreadPool(candidates.size.coerceAtMost(4))
            try {
                return pool.invokeAny(candidates.map { c ->
                    java.util.concurrent.Callable {
                        if (testUrl(c.url)) c
                        else throw java.util.concurrent.ExecutionException(java.io.IOException("probe failed: ${c.url}"))
                    }
                })
            } catch (_: Exception) {
                return null
            } finally {
                pool.shutdownNow()
            }
        }

        // 第一轮：直连 + 用户启用的镜像（保持原有优先级：直连仍在候选里）
        val enabledMirrors = preferenceManager.getEnabledMirrors()
        val useCdn = preferenceManager.isUseCdn()

        val wave1 = mutableListOf(Probe(GITHUB_API, null))
        if (enabledMirrors.isNotEmpty() && useCdn) {
            enabledMirrors.forEach { m -> wave1.add(Probe(mirrorManager.getMirrorUrl(m, GITHUB_API), m)) }
        }
        Logger.d(TAG, Str.get(R.string.testing_direct_connection_github_api, GITHUB_API))
        race(wave1)?.let { winner ->
            currentMirror = winner.mirror
            if (winner.mirror == null) {
                Logger.success(TAG, Str.get(R.string.direct_connection_available))
            } else {
                Logger.success(TAG, Str.get(R.string.found_usable_mirror_mirror, winner.mirror))
            }
            return winner.url
        }

        // 第二轮：默认镜像兜底（仅当配置镜像/直连全挂时）
        val wave2 = Constants.DEFAULT_MIRRORS.map { m -> Probe(mirrorManager.getMirrorUrl(m, GITHUB_API), m) }
        race(wave2)?.let { winner ->
            currentMirror = winner.mirror
            Logger.success(TAG, Str.get(R.string.found_usable_default_mirror_mirror, winner.mirror ?: ""))
            return winner.url
        }

        Logger.e(TAG, Str.get(R.string.all_mirrors_are_unavailable))
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
        return try {
            val url = "$apiUrl?per_page=30"
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "UIN-Tool-Android")
                .build()

            // B-56：response 必须关闭，原非 2xx 分支直接 throw → 连接永不归还连接池
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw Exception(Str.get(R.string.github_api_error_response_code, response.code))
                }
                val body = response.body?.string() ?: return@use emptyList()
                parseReleases(body)
            }

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

        // B-59：双字段排序——两侧都有 versionCode 时比 code；任一侧缺失退回三元组比较。
        // 两套刻度绝不混到同一数轴（原「code + 1e9 基线上移」hack 会把无 code 的
        // 老 tag 挤进有 code 的序列，导致「最新版本」误判）。
        releases.sortWith { a, b ->
            val ac = a.versionCode.toIntOrNull()
            val bc = b.versionCode.toIntOrNull()
            if (ac != null && bc != null) {
                bc.compareTo(ac)
            } else {
                val byName = compareVersionTriplets(b.versionName, a.versionName)
                if (byName != 0) byName else (bc ?: -1).compareTo(ac ?: -1)
            }
        }

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

    /**
     * B-24：release 页面地址（手动下载入口用）——走本次检查探测到的镜像，
     * 不再由调用方硬编码 https://github.com/... 绕过镜像；未命中镜像时返回原地址。
     */
    fun getReleasePageUrl(tagName: String): String {
        val original = "https://github.com/Undefined-Invalid-Null/UIN-Tool/releases/tag/$tagName"
        val mirror = currentMirror ?: return original
        return try {
            mirrorManager.getDownloadMirrorUrl(mirror, original, true)
        } catch (e: Exception) {
            Logger.e(TAG, "getReleasePageUrl mirror fallback: ${e.message}", e)
            original
        }
    }

    fun ignoreForceUpdate(tagName: String) {
        preferenceManager.setForceUpdateIgnored(tagName)
        Logger.i(TAG, Str.get(R.string.ignoring_force_update_tagname, tagName))
    }
}