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

    fun init(context: Context) {
        if (isInitialized) {
            Logger.w(TAG, Str.get(R.string.servicelocator_already_initialized_s))
            return
        }
        
        try {
            container = AppContainer(context.applicationContext)
            isInitialized = true
            Logger.success(TAG, Str.get(R.string.servicelocator_initialization_comple))
        } catch (e: Exception) {
            Logger.e(TAG, Str.get(R.string.servicelocator_initialization_failed), e)
            isInitialized = false
        }
    }

    fun getContainer(): AppContainer {
        checkInitialized()
        return container
    }

    // ==================== 核心服务 ====================
    fun getPluginManager() = getContainer().pluginManager
    
    fun getConfigRepository() = getContainer().configRepository
    
    fun getPluginRepository() = getContainer().pluginRepository

    // ==================== 更新服务 ====================
    fun getUpdateChecker() = getContainer().updateChecker
    
    fun getUpdateDownloader() = getContainer().updateDownloader

    // ==================== 编译服务 ====================
    fun getJavaToDexCompiler() = getContainer().javaToDexCompiler

    // ==================== 网络服务 ====================
    fun getGitHubApiService() = getContainer().gitHubApiService
    
    fun getMirrorManager() = getContainer().mirrorManager

    // ==================== 数据服务 ====================
    fun getPreferenceManager(): PreferenceManager = getContainer().preferenceManager

    // ==================== 辅助方法 ====================
    fun isInitialized(): Boolean = isInitialized
    
    private fun checkInitialized() {
        if (!isInitialized) {
            throw IllegalStateException(Str.get(R.string.servicelocator_not_initialized_call_))
        }
    }
}