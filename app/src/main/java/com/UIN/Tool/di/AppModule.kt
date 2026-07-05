package com.UIN.Tool.di

import android.content.Context
import com.UIN.Tool.core.compiler.JavaToDexCompiler
import com.UIN.Tool.plugin.PluginManager
import com.UIN.Tool.core.update.UpdateChecker
import com.UIN.Tool.core.update.UpdateDownloader
import com.UIN.Tool.data.local.FileManager
import com.UIN.Tool.data.local.PreferenceManager
import com.UIN.Tool.data.remote.GitHubApiService
import com.UIN.Tool.data.remote.MirrorManager
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.io.File
import java.util.concurrent.TimeUnit

object AppModule {
    
    private var okHttpClient: OkHttpClient? = null
    private var preferenceManager: PreferenceManager? = null
    private var fileManager: FileManager? = null
    private var pluginManager: PluginManager? = null
    private var gitHubApiService: GitHubApiService? = null
    private var mirrorManager: MirrorManager? = null
    
    fun provideOkHttpClient(context: Context): OkHttpClient {
        if (okHttpClient == null) {
            okHttpClient = OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .cache(okhttp3.Cache(File(context.cacheDir, "okhttp_cache"), 50 * 1024 * 1024))
                .addInterceptor(HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BASIC
                })
                .build()
        }
        return okHttpClient!!
    }
    
    fun providePreferenceManager(context: Context): PreferenceManager {
        if (preferenceManager == null) {
            preferenceManager = PreferenceManager(context)
        }
        return preferenceManager!!
    }
    
    fun provideFileManager(context: Context): FileManager {
        if (fileManager == null) {
            fileManager = FileManager(context)
        }
        return fileManager!!
    }
    
    fun providePluginManager(context: Context): PluginManager {
        if (pluginManager == null) {
            pluginManager = PluginManager.getInstance(context)
        }
        return pluginManager!!
    }
    
    fun provideGitHubApiService(context: Context): GitHubApiService {
        if (gitHubApiService == null) {
            gitHubApiService = GitHubApiService(provideOkHttpClient(context))
        }
        return gitHubApiService!!
    }
    
    fun provideMirrorManager(context: Context): MirrorManager {
        if (mirrorManager == null) {
            mirrorManager = MirrorManager(provideOkHttpClient(context))
        }
        return mirrorManager!!
    }
    
    fun provideUpdateChecker(context: Context): UpdateChecker {
        return UpdateChecker(context, providePreferenceManager(context))
    }
    
    fun provideUpdateDownloader(context: Context): UpdateDownloader {
        return UpdateDownloader(context)
    }
    
    fun provideJavaToDexCompiler(context: Context): JavaToDexCompiler {
        return JavaToDexCompiler(context)
    }
}