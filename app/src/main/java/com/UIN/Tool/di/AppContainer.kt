// app/src/main/java/com/UIN/Tool/core/di/AppContainer.kt
package com.UIN.Tool.core.di

import android.content.Context
import com.UIN.Tool.data.local.PreferenceManager
import com.UIN.Tool.data.remote.GitHubApiService
import com.UIN.Tool.data.remote.MirrorManager
import com.UIN.Tool.data.repository.ConfigRepositoryImpl
import com.UIN.Tool.data.repository.PluginRepositoryImpl
import com.UIN.Tool.domain.repository.IConfigRepository
import com.UIN.Tool.domain.repository.IPluginRepository
import com.UIN.Tool.plugin.PluginManager
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

class AppContainer(private val context: Context) {

    // ---------- 网络层 ----------
    val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .cache(okhttp3.Cache(File(context.cacheDir, "okhttp_cache"), 50 * 1024 * 1024))
            .build()
    }

    // ---------- 数据层 ----------
    val preferenceManager: PreferenceManager by lazy { PreferenceManager(context) }
    val gitHubApiService: GitHubApiService by lazy { GitHubApiService(okHttpClient) }
    val mirrorManager: MirrorManager by lazy { MirrorManager(okHttpClient) }

    // ---------- 仓库 ----------
    val configRepository: IConfigRepository by lazy {
        ConfigRepositoryImpl(preferenceManager)
    }

    val pluginRepository: IPluginRepository by lazy {
        PluginRepositoryImpl(PluginManager.getInstance(context))
    }

    // ---------- 管理器 ----------
    val pluginManager: PluginManager by lazy {
        PluginManager.getInstance(context)
    }

    // ---------- 更新服务 ----------
    val updateChecker by lazy {
        com.UIN.Tool.core.update.UpdateChecker(context, preferenceManager)
    }

    val updateDownloader by lazy {
        com.UIN.Tool.core.update.UpdateDownloader(context)
    }

    // ---------- 编译服务 ----------
    val javaToDexCompiler by lazy {
        com.UIN.Tool.core.compiler.JavaToDexCompiler(context)
    }
}