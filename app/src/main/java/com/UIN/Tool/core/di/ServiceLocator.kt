// app/src/main/java/com/UIN/Tool/core/di/ServiceLocator.kt

package com.UIN.Tool.core.di

import com.UIN.Tool.R
import com.UIN.Tool.utils.Str
import android.content.Context
import com.UIN.Tool.data.local.PreferenceManager
import com.UIN.Tool.log.Logger

object ServiceLocator {
    private const val TAG = "ServiceLocator"
    
    private var isInitialized = false
    private lateinit var container: AppContainer
    // B-53：记录最近一次初始化失败原因与应用上下文，失败后还有重试与归因路径
    private var initError: Throwable? = null
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
        if (isInitialized) {
            Logger.w(TAG, Str.get(R.string.servicelocator_already_initialized))
            return
        }

        try {
            container = AppContainer(context.applicationContext)
            isInitialized = true
            initError = null
            Logger.success(TAG, Str.get(R.string.servicelocator_initialization_complete))
        } catch (e: Exception) {
            Logger.e(TAG, Str.get(R.string.servicelocator_initialization_failed), e)
            isInitialized = false
            initError = e
        }
    }

    fun getContainer(): AppContainer {
        checkInitialized()
        return container
    }

    // ==================== 核心服务 ====================
    fun getPluginManager() = getContainer().pluginManager
    
    fun getPluginRepository() = getContainer().pluginRepository

    // ==================== 更新服务 ====================
    fun getUpdateChecker() = getContainer().updateChecker
    
    fun getUpdateDownloader() = getContainer().updateDownloader

    // ==================== 网络服务 ====================
    fun getGitHubApiService() = getContainer().gitHubApiService
    
    fun getMirrorManager() = getContainer().mirrorManager

    // D-29：共享 OkHttpClient（连接池 + 缓存单实例），避免各处自建第二套网络栈
    fun getOkHttpClient(): okhttp3.OkHttpClient = getContainer().okHttpClient

    // ==================== 数据服务 ====================
    fun getPreferenceManager(): PreferenceManager = getContainer().preferenceManager

    // ==================== 辅助方法 ====================
    fun isInitialized(): Boolean = isInitialized
    
    private fun checkInitialized() {
        if (isInitialized) return
        // B-53：原先失败被吞、后续每次 getXxx() 都抛同一句无归因的异常 → 启动即崩且看不出原因。
        // 这里先用缓存的应用上下文自动重试一次，仍失败则把原始异常作为 cause 带出去。
        appContext?.let { ctx -> runCatching { init(ctx) } }
        if (!isInitialized) {
            val cause = initError
            val detail = cause?.message?.let { " ($it)" } ?: ""
            throw IllegalStateException(Str.get(R.string.servicelocator_not_initialized_call_init) + detail, cause)
        }
    }
}