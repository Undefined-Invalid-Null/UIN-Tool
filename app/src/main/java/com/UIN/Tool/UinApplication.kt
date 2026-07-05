// app/src/main/java/com/UIN/Tool/UinApplication.kt
package com.UIN.Tool

import android.app.Application
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.UIN.Tool.core.di.ServiceLocator
import com.UIN.Tool.log.Logger
import com.UIN.Tool.utils.Constants
import com.UIN.Tool.utils.CrashLogUtils
import com.UIN.Tool.utils.UIConfig
import com.UIN.Tool.widget.WidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

class UinApplication : Application() {

    companion object {
        private lateinit var instance: UinApplication

        fun getInstance(): UinApplication = instance

        /**
         * 获取应用上下文
         */
        fun getAppContext(): Context = instance.applicationContext
    }

    private val applicationScope = CoroutineScope(Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        instance = this

        // ==================== 1. 初始化日志系统（最早初始化） ====================
        Logger.init(Constants.LOG_DIR)
        Logger.i("UinApplication", "══════════════════════════════════════════════════")
        Logger.i("UinApplication", "应用启动 - UIN Tool v${Constants.APP_VERSION}")
        Logger.i("UinApplication", "══════════════════════════════════════════════════")

        // ==================== 2. 初始化依赖注入容器 ====================
        ServiceLocator.init(this)
        Logger.i("UinApplication", "依赖注入容器初始化完成")

        // ==================== 3. 初始化UI配置 ====================
        UIConfig.init(this)
        Logger.i("UinApplication", "UI配置初始化完成")

        // ==================== 4. 创建必要的目录 ====================
        createDirectories()

        // ==================== 5. 设置全局异常处理 ====================
        setupExceptionHandler()

        // ==================== 6. 清理过期日志 ====================
        cleanOldLogs()

        // ==================== 7. 初始化插件管理器（预加载） ====================
        applicationScope.launch {
            try {
                val pluginManager = ServiceLocator.getPluginManager()
                pluginManager.refreshPlugins()
                Logger.i("UinApplication", "插件管理器初始化完成，已加载 ${pluginManager.getPluginCount()} 个插件")
            } catch (e: Exception) {
                Logger.e("UinApplication", "插件管理器初始化失败", e)
            }
        }

        // ==================== 8. 注册应用生命周期回调 ====================
        registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            private var activityCount = 0
            private var isAppInForeground = false

            override fun onActivityCreated(activity: android.app.Activity, savedInstanceState: Bundle?) {
                // 不需要特殊处理
            }

            override fun onActivityStarted(activity: android.app.Activity) {
                activityCount++
                if (!isAppInForeground && activityCount == 1) {
                    isAppInForeground = true
                    Logger.d("UinApplication", "应用进入前台")
                    onAppForeground()
                }
            }

            override fun onActivityResumed(activity: android.app.Activity) {
                // 不需要特殊处理
            }

            override fun onActivityPaused(activity: android.app.Activity) {
                // 不需要特殊处理
            }

            override fun onActivityStopped(activity: android.app.Activity) {
                activityCount--
                if (isAppInForeground && activityCount == 0) {
                    isAppInForeground = false
                    Logger.d("UinApplication", "应用进入后台")
                    onAppBackground()
                }
            }

            override fun onActivitySaveInstanceState(activity: android.app.Activity, outState: Bundle) {
                // 不需要特殊处理
            }

            override fun onActivityDestroyed(activity: android.app.Activity) {
                // 不需要特殊处理
            }
        })

        // ==================== 9. 延迟刷新小部件 ====================
        Handler(Looper.getMainLooper()).postDelayed({
            try {
                WidgetProvider.forceRefreshAllWidgets(this)
                Logger.i("UinApplication", "应用启动后刷新小部件")
            } catch (e: Exception) {
                Logger.e("UinApplication", "刷新小部件失败", e)
            }
        }, 3000)

        Logger.success("UinApplication", "应用初始化完成")
    }

    /**
     * 创建必要的目录结构
     */
    private fun createDirectories() {
        val dirs = listOf(
            Constants.WORK_DIR,
            Constants.PLUGIN_DIR,
            Constants.LOG_DIR,
            Constants.BACKUP_DIR,
            Constants.DOWNLOAD_DIR,
            Constants.TEMP_DIR,
            Constants.CACHE_DIR,
            Constants.TPK_DIR
        )

        var successCount = 0
        var failCount = 0

        dirs.forEach { path ->
            try {
                val dir = File(path)
                if (!dir.exists()) {
                    val created = dir.mkdirs()
                    if (created) {
                        successCount++
                        Logger.d("UinApplication", "创建目录: $path")
                    } else {
                        failCount++
                        Logger.w("UinApplication", "创建目录失败: $path")
                    }
                } else {
                    successCount++
                }
            } catch (e: Exception) {
                failCount++
                Logger.e("UinApplication", "创建目录异常: $path", e)
            }
        }

        Logger.i("UinApplication", "目录创建完成: 成功 $successCount 个, 失败 $failCount 个")

        // 验证关键目录
        val pluginDir = File(Constants.PLUGIN_DIR)
        if (!pluginDir.exists() || !pluginDir.isDirectory) {
            Logger.e("UinApplication", "⚠️ 插件目录不可用: ${Constants.PLUGIN_DIR}")
            // 尝试重新创建
            try {
                pluginDir.mkdirs()
                if (pluginDir.exists()) {
                    Logger.success("UinApplication", "插件目录重新创建成功")
                }
            } catch (e: Exception) {
                Logger.e("UinApplication", "插件目录重新创建失败", e)
            }
        }

        val logDir = File(Constants.LOG_DIR)
        if (!logDir.exists() || !logDir.isDirectory) {
            Logger.e("UinApplication", "⚠️ 日志目录不可用: ${Constants.LOG_DIR}")
        }
    }

    /**
     * 设置全局未捕获异常处理器
     */
    private fun setupExceptionHandler() {
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            // ==================== 记录崩溃日志 ====================
            try {
                CrashLogUtils.logException(this, throwable, "UncaughtException")
                Logger.e("UinApplication", "未捕获异常: ${throwable.message}", throwable)

                // 打印堆栈到日志文件
                val stackTrace = throwable.stackTraceToString()
                Logger.d("UinApplication", "堆栈信息:\n$stackTrace")
            } catch (e: Exception) {
                // 如果日志记录本身失败，确保至少打印到控制台
                e.printStackTrace()
            }

            // ==================== 标记崩溃，用于下次启动跳转日志页面 ====================
            try {
                getSharedPreferences(Constants.PREF_CRASH, Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean(Constants.KEY_JUST_CRASHED, true)
                    .apply()
            } catch (e: Exception) {
                // 忽略
            }

            // ==================== 延迟后尝试打开日志页面或主界面 ====================
            try {
                Thread.sleep(500)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
            }

            // 尝试打开日志页面
            try {
                val intent = android.content.Intent(this, com.UIN.Tool.ui.log.LogViewerActivity::class.java).apply {
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    putExtra("auto_open", true)
                }
                startActivity(intent)
                Logger.i("UinApplication", "已启动日志查看器")
            } catch (e: Exception) {
                // 如果无法打开日志页面，尝试打开主界面
                try {
                    val intent = android.content.Intent(this, MainActivity::class.java).apply {
                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    }
                    startActivity(intent)
                    Logger.i("UinApplication", "已启动主界面")
                } catch (e2: Exception) {
                    // 完全失败，只能退出
                    e2.printStackTrace()
                }
            }

            // ==================== 延迟退出进程 ====================
            try {
                Thread.sleep(200)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
            }

            android.os.Process.killProcess(android.os.Process.myPid())
            System.exit(1)
        }

        Logger.i("UinApplication", "全局异常处理器已设置")
    }

    /**
     * 清理过期日志文件
     */
    private fun cleanOldLogs() {
        try {
            val logDir = File(Constants.LOG_DIR)
            if (!logDir.exists()) return

            val currentTime = System.currentTimeMillis()
            val retentionMillis = Constants.LOG_RETENTION_DAYS * 24L * 60L * 60L * 1000L
            var deletedCount = 0
            var totalCount = 0

            logDir.listFiles()?.forEach { file ->
                if (file.isFile && file.name.endsWith(".log")) {
                    totalCount++
                    val lastModified = file.lastModified()
                    if (currentTime - lastModified > retentionMillis) {
                        if (file.delete()) {
                            deletedCount++
                            Logger.d("UinApplication", "删除过期日志: ${file.name}")
                        }
                    }
                }
            }

            if (deletedCount > 0) {
                Logger.i("UinApplication", "清理了 $deletedCount/$totalCount 个过期日志文件")
            } else {
                Logger.d("UinApplication", "没有需要清理的过期日志文件 (共 $totalCount 个)")
            }
        } catch (e: Exception) {
            // 清理失败不影响应用启动
            Logger.w("UinApplication", "清理过期日志失败: ${e.message}")
        }
    }

    /**
     * 应用进入前台时的回调
     */
    private fun onAppForeground() {
        // 刷新插件列表
        applicationScope.launch {
            try {
                val pluginManager = ServiceLocator.getPluginManager()
                pluginManager.refreshPlugins()
                Logger.d("UinApplication", "应用前台刷新插件完成")
            } catch (e: Exception) {
                Logger.e("UinApplication", "应用前台刷新插件失败", e)
            }
        }
        
        // 刷新小部件
        try {
            WidgetProvider.forceRefreshAllWidgets(this)
            Logger.d("UinApplication", "应用前台刷新小部件完成")
        } catch (e: Exception) {
            Logger.e("UinApplication", "应用前台刷新小部件失败", e)
        }
    }

    /**
     * 应用进入后台时的回调
     */
    private fun onAppBackground() {
        // 清理临时资源
        applicationScope.launch {
            try {
                // 清理临时目录
                val tempDir = File(Constants.TEMP_DIR)
                if (tempDir.exists() && tempDir.isDirectory) {
                    val files = tempDir.listFiles()
                    var deletedCount = 0
                    files?.forEach { file ->
                        if (file.isFile && file.name.startsWith("temp_")) {
                            val lastModified = file.lastModified()
                            // 删除超过1小时的临时文件
                            if (System.currentTimeMillis() - lastModified > 3600000) {
                                if (file.delete()) {
                                    deletedCount++
                                }
                            }
                        }
                    }
                    if (deletedCount > 0) {
                        Logger.d("UinApplication", "后台清理了 $deletedCount 个临时文件")
                    }
                }
            } catch (e: Exception) {
                Logger.w("UinApplication", "后台清理临时文件失败: ${e.message}")
            }
        }
    }

    /**
     * 应用退出时的清理工作
     */
    override fun onTerminate() {
        super.onTerminate()
        Logger.i("UinApplication", "应用终止")

        // 清理WebView缓存
        try {
            com.UIN.Tool.plugin.PluginManager.clearWebViewCache()
            Logger.d("UinApplication", "WebView缓存已清理")
        } catch (e: Exception) {
            // 忽略
        }

        // 清理临时文件
        try {
            val tempDir = File(Constants.TEMP_DIR)
            if (tempDir.exists() && tempDir.isDirectory) {
                tempDir.listFiles()?.forEach { file ->
                    if (file.isFile && file.name.startsWith("temp_")) {
                        file.delete()
                    }
                }
            }
        } catch (e: Exception) {
            // 忽略
        }

        Logger.i("UinApplication", "应用终止完成")
    }

    /**
     * 获取应用版本名称
     */
    fun getVersionName(): String {
        return try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: Constants.APP_VERSION
        } catch (e: Exception) {
            Constants.APP_VERSION
        }
    }

    /**
     * 获取应用版本号
     */
    fun getVersionCode(): Int {
        return try {
            val info = packageManager.getPackageInfo(packageName, 0)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                info.longVersionCode.toInt()
            } else {
                info.versionCode
            }
        } catch (e: Exception) {
            Constants.APP_VERSION_CODE
        }
    }

    /**
     * 检查应用是否在前台
     */
    fun isAppInForeground(): Boolean {
        // 通过ActivityLifecycleCallbacks维护的状态来判断
        // 这里使用了一个简单的方法：检查是否有可见的Activity
        try {
            val am = getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            val tasks = am.appTasks
            if (tasks.isNotEmpty()) {
                val info = tasks[0].taskInfo
                val topActivity = info.topActivity
                return topActivity?.packageName == packageName
            }
        } catch (e: Exception) {
            // 忽略
        }
        return false
    }
}