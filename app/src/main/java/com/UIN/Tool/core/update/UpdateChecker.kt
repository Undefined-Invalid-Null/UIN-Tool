package com.UIN.Tool.core.update

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.UIN.Tool.data.local.PreferenceManager
import com.UIN.Tool.domain.model.ReleaseInfo
import com.UIN.Tool.log.Logger
import com.UIN.Tool.utils.Constants
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
        private val DEFAULT_MIRRORS = listOf(
            "https://hub.fastgit.xyz",
            "https://github.moeyy.xyz",
            "https://ghproxy.net",
            "https://mirror.ghproxy.com",
            "https://gh.api.99988866.xyz",
            "https://gitclone.com"
        )
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(Constants.NETWORK_TIMEOUT, TimeUnit.SECONDS)
        .readTimeout(Constants.NETWORK_READ_TIMEOUT, TimeUnit.SECONDS)
        .writeTimeout(Constants.NETWORK_WRITE_TIMEOUT, TimeUnit.SECONDS)
        .cache(okhttp3.Cache(File(context.cacheDir, "okhttp_cache"), Constants.CACHE_SIZE))
        .build()

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

                Logger.param(TAG, "当前版本", "$currentVersionName (代码: $currentVersionCode)")

                // 测试镜像站
                val workingUrl = testMirrors()
                if (workingUrl == null) {
                    Logger.e(TAG, "所有镜像均不可用")
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        onUpdateListener?.onCheckFailed("网络连接失败，请检查网络后重试")
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

                for (release in releases) {
                    val versionCode = release.versionCode.toIntOrNull() ?: 0
                    if (versionCode > currentVersionCode) {
                        hasNewer = true
                        forceUpdate = release.forceUpdate

                        // 应用镜像到下载链接
                        currentMirror?.let { mirror ->
                            if (release.downloadUrl.startsWith("https://github.com")) {
                                release.downloadUrl = getMirrorDownloadUrl(mirror, release.downloadUrl)
                            }
                        }
                        break
                    }
                }

                Logger.param(TAG, "有新版本", hasNewer)
                Logger.param(TAG, "强制更新", forceUpdate)

                val finalReleases = releases
                val finalHasNewer = hasNewer
                val finalForceUpdate = forceUpdate

                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    onUpdateListener?.onCheckSuccess(finalReleases, finalHasNewer, finalForceUpdate)
                }

            } catch (e: Exception) {
                Logger.e(TAG, "检查更新失败", e)
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    onUpdateListener?.onCheckFailed(e.message ?: "检查更新失败")
                }
            } finally {
                Logger.exit(TAG, "checkUpdate", System.currentTimeMillis())
            }
        }.start()
    }

    private fun testMirrors(): String? {
        // 先尝试直连
        Logger.d(TAG, "测试直连: $GITHUB_API")
        if (testUrl(GITHUB_API)) {
            Logger.success(TAG, "直连可用")
            return GITHUB_API
        }

        // 获取启用的镜像
        val enabledMirrors = preferenceManager.getEnabledMirrors()
        val useCdn = preferenceManager.isUseCdn()

        val mirrorsToTest = if (enabledMirrors.isNotEmpty() && useCdn) {
            enabledMirrors
        } else {
            DEFAULT_MIRRORS
        }

        for (mirror in mirrorsToTest) {
            val testUrl = getMirrorUrl(mirror, GITHUB_API)
            Logger.d(TAG, "测试镜像: $testUrl")
            if (testUrl(testUrl)) {
                currentMirror = mirror
                Logger.success(TAG, "找到可用镜像: $mirror")
                return testUrl
            }
        }

        // 如果配置的镜像都不可用，尝试默认镜像
        if (enabledMirrors.isNotEmpty() && useCdn) {
            Logger.d(TAG, "配置镜像不可用，尝试默认镜像")
            for (mirror in DEFAULT_MIRRORS) {
                val testUrl = getMirrorUrl(mirror, GITHUB_API)
                if (testUrl(testUrl)) {
                    currentMirror = mirror
                    Logger.success(TAG, "找到可用默认镜像: $mirror")
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
                throw Exception("GitHub API错误: ${response.code}")
            }

            val body = response.body?.string() ?: return emptyList()
            return parseReleases(body)

        } catch (e: Exception) {
            Logger.e(TAG, "获取Releases失败", e)
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

                val assets = release.optJSONArray("assets")
                if (assets != null) {
                    for (j in 0 until assets.length()) {
                        val asset = assets.getJSONObject(j)
                        if (asset.getString("name").endsWith(".apk")) {
                            downloadUrl = asset.getString("browser_download_url")
                            apkSize = asset.getLong("size")
                            break
                        }
                    }
                }

                if (downloadUrl.isEmpty()) {
                    downloadUrl = "https://github.com/Undefined-Invalid-Null/UIN-Tool/releases/tag/$tagName"
                }
            }

            releases.add(info)
        }

        releases.sortByDescending { it.versionCode.toIntOrNull() ?: 0 }

        return releases
    }

    private fun getMirrorUrl(mirror: String, originalUrl: String): String {
        return when {
            mirror.contains("ghproxy") -> "$mirror/$originalUrl"
            mirror.contains("fastgit") -> originalUrl.replace("https://api.github.com", "$mirror/api.github.com")
            mirror.contains("moeyy") -> "$mirror/$originalUrl"
            else -> "$mirror/$originalUrl"
        }
    }

    private fun getMirrorDownloadUrl(mirror: String, originalUrl: String): String {
        return when {
            mirror.contains("ghproxy") -> "$mirror/$originalUrl"
            mirror.contains("fastgit") -> originalUrl.replace("https://github.com", "$mirror/github.com")
            else -> originalUrl
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
        Logger.i(TAG, "忽略版本: $versionName")
    }

    fun ignoreForceUpdate(tagName: String) {
        preferenceManager.setForceUpdateIgnored(tagName)
        Logger.i(TAG, "忽略强制更新: $tagName")
    }
}