package com.UIN.Tool

import android.content.Context
import android.widget.Toast
import androidx.multidex.MultiDex
import com.UIN.Tool.app.TermuxApplication
import com.UIN.Tool.core.di.ServiceLocator
import com.UIN.Tool.log.Logger
import com.UIN.Tool.utils.Constants
import java.io.File

class UinApplication : TermuxApplication() {

    companion object {
        private const val TAG = "UinApplication"
        
        @JvmStatic
        private lateinit var instance: UinApplication
        
        @JvmStatic
        fun getInstance(): UinApplication {
            return instance
        }
        
        @JvmStatic
        fun getAppContext(): Context {
            return instance.applicationContext
        }
    }

    override fun attachBaseContext(base: Context) {
        // ★★★ 关键修复：在任何操作之前先安装 MultiDex ★★★
        // 注意：这里传入 base，而不是 this
        MultiDex.install(base)
        
        // 然后才能安全地调用父类的 attachBaseContext
        // 因为父类中可能使用了 Guava 库
        super.attachBaseContext(base)
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        // ServiceLocator 初始化
        try {
            ServiceLocator.init(this)
            Logger.i(TAG, "ServiceLocator 初始化成功")
        } catch (e: Exception) {
            Logger.e(TAG, "ServiceLocator 初始化失败", e)
        }

        // 工作目录初始化
        try {
            initWorkDirectory()
            Logger.i(TAG, "工作目录初始化成功")
        } catch (e: Exception) {
            Logger.e(TAG, "工作目录初始化失败", e)
        }

        Logger.i(TAG, "UIN Tool 应用启动完成")
    }

    /**
     * 初始化工作目录
     */
    private fun initWorkDirectory() {
        val workDir = File(Constants.WORK_DIR)
        if (!workDir.exists()) {
            workDir.mkdirs()
        }

        val subDirs = listOf(
            "home", "usr", "usr/bin", "usr/lib", "usr/include",
            "etc", "tmp", "var", "var/log", "var/run", "var/tmp",
            "plugins", "templates", "downloads", "cache", "logs"
        )

        for (subDir in subDirs) {
            val dir = File(workDir, subDir)
            if (!dir.exists()) {
                dir.mkdirs()
            }
        }

        workDir.setReadable(true, false)
        workDir.setWritable(true, false)
        workDir.setExecutable(true, false)

        Logger.i(TAG, "工作目录已创建: ${workDir.absolutePath}")
    }
}