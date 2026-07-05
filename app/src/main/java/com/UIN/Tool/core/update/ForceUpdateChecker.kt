package com.UIN.Tool.core.update

import android.content.Context
import com.UIN.Tool.data.local.PreferenceManager
import com.UIN.Tool.log.Logger
import com.UIN.Tool.utils.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.io.IOException
import java.util.concurrent.TimeUnit

class ForceUpdateChecker(
    private val context: Context,
    private val preferenceManager: PreferenceManager
) {

    companion object {
        private const val TAG = "ForceUpdateChecker"
        private const val GITHUB_API = "https://api.github.com/repos/Undefined-Invalid-Null/UIN-Tool/releases"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    interface ForceUpdateCallback {
        fun onResult(forceUpdate: Boolean)
        fun onError(error: String)
    }

    fun checkForceUpdate(callback: ForceUpdateCallback) {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            try {
                Logger.enter(TAG, "checkForceUpdate")

                val currentVersionCode = getCurrentVersionCode()
                Logger.param(TAG, "当前版本代码", currentVersionCode)

                // 在后台线程执行网络请求
                Thread {
                    try {
                        val request = Request.Builder()
                            .url("$GITHUB_API?per_page=1")
                            .header("Accept", "application/vnd.github.v3+json")
                            .header("User-Agent", "UIN-Tool-Android")
                            .build()

                        val response = client.newCall(request).execute()
                        if (!response.isSuccessful) {
                            Logger.e(TAG, "API请求失败: ${response.code}")
                            android.os.Handler(android.os.Looper.getMainLooper()).post {
                                callback.onResult(false)
                            }
                            return@Thread
                        }

                        val body = response.body?.string()
                        if (body.isNullOrEmpty()) {
                            android.os.Handler(android.os.Looper.getMainLooper()).post {
                                callback.onResult(false)
                            }
                            return@Thread
                        }

                        val releases = JSONArray(body)
                        if (releases.length() == 0) {
                            android.os.Handler(android.os.Looper.getMainLooper()).post {
                                callback.onResult(false)
                            }
                            return@Thread
                        }

                        val latest = releases.getJSONObject(0)
                        val tagName = latest.optString("tag_name", "")
                        val parts = tagName.split("-", limit = 3)

                        val newVersionCode = parts.getOrNull(0)?.toIntOrNull() ?: 0
                        val forceFlag = parts.getOrNull(2) ?: "0"

                        val hasNewer = newVersionCode > currentVersionCode
                        val isForceUpdate = forceFlag == "1"

                        Logger.param(TAG, "最新版本代码", newVersionCode)
                        Logger.param(TAG, "强制更新标志", forceFlag)
                        Logger.param(TAG, "需要强制更新", isForceUpdate)

                        if (hasNewer && isForceUpdate) {
                            val ignoredTag = preferenceManager.getForceUpdateIgnored()
                            if (tagName == ignoredTag) {
                                Logger.i(TAG, "强制更新已被忽略: $tagName")
                                android.os.Handler(android.os.Looper.getMainLooper()).post {
                                    callback.onResult(false)
                                }
                                return@Thread
                            }
                            android.os.Handler(android.os.Looper.getMainLooper()).post {
                                callback.onResult(true)
                            }
                        } else {
                            android.os.Handler(android.os.Looper.getMainLooper()).post {
                                callback.onResult(false)
                            }
                        }

                    } catch (e: IOException) {
                        Logger.e(TAG, "网络请求失败", e)
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            callback.onError("网络请求失败: ${e.message}")
                        }
                    } catch (e: Exception) {
                        Logger.e(TAG, "检查强制更新失败", e)
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            callback.onError(e.message ?: "检查失败")
                        }
                    }
                }.start()

            } catch (e: Exception) {
                Logger.e(TAG, "检查强制更新失败", e)
                callback.onError(e.message ?: "检查失败")
            } finally {
                Logger.exit(TAG, "checkForceUpdate", System.currentTimeMillis())
            }
        }
    }

    suspend fun checkForceUpdate(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Logger.enter(TAG, "checkForceUpdate")

                val currentVersionCode = getCurrentVersionCode()

                val request = Request.Builder()
                    .url("$GITHUB_API?per_page=1")
                    .header("Accept", "application/vnd.github.v3+json")
                    .header("User-Agent", "UIN-Tool-Android")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Logger.e(TAG, "API请求失败: ${response.code}")
                        return@withContext false
                    }

                    val body = response.body?.string() ?: return@withContext false
                    val releases = JSONArray(body)

                    if (releases.length() == 0) {
                        return@withContext false
                    }

                    val latest = releases.getJSONObject(0)
                    val tagName = latest.optString("tag_name", "")
                    val parts = tagName.split("-", limit = 3)

                    val newVersionCode = parts.getOrNull(0)?.toIntOrNull() ?: 0
                    val forceFlag = parts.getOrNull(2) ?: "0"

                    val hasNewer = newVersionCode > currentVersionCode
                    val isForceUpdate = forceFlag == "1"

                    Logger.param(TAG, "当前版本", currentVersionCode)
                    Logger.param(TAG, "最新版本", newVersionCode)
                    Logger.param(TAG, "强制更新", isForceUpdate)

                    if (hasNewer && isForceUpdate) {
                        val ignoredTag = preferenceManager.getForceUpdateIgnored()
                        if (tagName == ignoredTag) {
                            Logger.i(TAG, "强制更新已被忽略: $tagName")
                            return@withContext false
                        }
                        return@withContext true
                    }
                }

                false

            } catch (e: Exception) {
                Logger.e(TAG, "检查强制更新失败", e)
                false
            } finally {
                Logger.exit(TAG, "checkForceUpdate", System.currentTimeMillis())
            }
        }
    }

    private fun getCurrentVersionCode(): Int {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                packageInfo.longVersionCode.toInt()
            } else {
                packageInfo.versionCode
            }
        } catch (e: Exception) {
            1
        }
    }
}