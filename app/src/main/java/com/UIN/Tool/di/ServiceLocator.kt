// app/src/main/java/com/UIN/Tool/core/di/ServiceLocator.kt
package com.UIN.Tool.core.di

import android.content.Context
import com.UIN.Tool.data.local.PreferenceManager

object ServiceLocator {
    private lateinit var container: AppContainer

    fun init(context: Context) {
        container = AppContainer(context.applicationContext)
    }

    fun getContainer(): AppContainer = container

    // ==================== 核心服务 ====================
    fun getPluginManager() = container.pluginManager
    fun getConfigRepository() = container.configRepository
    fun getPluginRepository() = container.pluginRepository

    // ==================== 更新服务 ====================
    fun getUpdateChecker() = container.updateChecker
    fun getUpdateDownloader() = container.updateDownloader

    // ==================== 编译服务 ====================
    fun getJavaToDexCompiler() = container.javaToDexCompiler

    // ==================== 网络服务 ====================
    fun getGitHubApiService() = container.gitHubApiService
    fun getMirrorManager() = container.mirrorManager

    // ==================== 数据服务 ====================
    fun getPreferenceManager(): PreferenceManager = container.preferenceManager
}