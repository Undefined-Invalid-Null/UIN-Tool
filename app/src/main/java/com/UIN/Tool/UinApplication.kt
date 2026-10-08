package com.UIN.Tool

import com.UIN.Tool.utils.Str
import android.content.Context
import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.UIN.Tool.core.di.ServiceLocator
import com.UIN.Tool.log.Logger
import com.UIN.Tool.utils.UIConfig
import com.UIN.Tool.constants.AppConstants as Constants
import com.UIN.Tool.utils.CrashLogUtils
import com.UIN.Tool.plugin.SharedSupervisor
import com.UIN.Tool.plugin.PluginManager
import io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.langs.textmate.registry.model.GrammarDefinition
import io.github.rosemoe.sora.langs.textmate.registry.model.ThemeModel
import io.github.rosemoe.sora.langs.textmate.registry.provider.AssetsFileResolver
import org.eclipse.tm4e.core.registry.IGrammarSource
import org.eclipse.tm4e.core.registry.IThemeSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

class UinApplication : Application() {

    companion object {
        private const val TAG = "UinApplication"

        @JvmStatic
        private lateinit var instance: UinApplication

        @JvmStatic
        fun getInstance(): UinApplication = instance

        @JvmStatic
        fun getAppContext(): Context = instance.applicationContext

        /** B-10：进程启动时的系统原始 locale（跟随系统时用，见 attachBaseContext） */
        @Volatile
        private var systemDefaultLocale: java.util.Locale? = null

        /**
         * B-08：应用级协程作用域（SupervisorJob + IO + 记日志的异常处理器）。
         * 替代原先「裸 Thread{} 里再 MainScope().launch{}」的写法——那种写法不可取消、
         * 没有异常处理器（未捕获异常会走全局 handler 把进程杀掉）。
         */
        val appScope: CoroutineScope = CoroutineScope(
            SupervisorJob() + Dispatchers.IO +
                CoroutineExceptionHandler { _, throwable ->
                    try {
                        Logger.e("UinApplication", "appScope task failed", throwable)
                    } catch (_: Exception) {
                    }
                }
        )

        // B-80：后台环境安装标志（_isEnvironmentInstalling）随 autoInstallEnvironment
        // 一并下沉到 core/bootstrap/EnvironmentBootstrap，避免 Application 继续持有
        // 环境编排状态；PluginHostActivity 改查 EnvironmentBootstrap.isEnvironmentInstalling()
    }

    /**
     * B-12：instance 提前到 attachBaseContext 注册——onCreate 之前（如 providers、
     * ContentProvider 初始化期）访问 getInstance() 不再抛 UninitializedPropertyAccessException。
     */
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(newBase)
        instance = this
        // B-10：在任何 applyLocale→Locale.setDefault 之前抓系统真实默认值 ——
        // 否则切过 zh/en 再切「跟随系统」时 getDefault() 拿到的是被污染的值
        systemDefaultLocale = java.util.Locale.getDefault()
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        // B-06：启动序列改为声明式任务列表 + 统一执行器（顺序、逐段隔离与原先完全一致），
        // 每段任务带 id 归因与耗时日志，后续增删任务只改列表。
        runStartupTasks(mainStartupTasks())

        appScope.launch {
            runStartupTasks(backgroundStartupTasks())
        }

        Logger.i(TAG, Str.get(R.string.uin_tool_app_startup_complete))
    }

    /**
     * B-06：启动任务描述 —— id 用于日志归因；单段异常只记日志，不中断后续任务。
     */
    private data class StartupTask(val id: String, val run: () -> Unit)

    private fun runStartupTasks(tasks: List<StartupTask>) {
        for (task in tasks) {
            val t0 = System.currentTimeMillis()
            try {
                task.run()
                Logger.i(TAG, "startup task ${task.id} ok (${System.currentTimeMillis() - t0}ms)")
            } catch (e: Exception) {
                Logger.e(TAG, "startup task ${task.id} failed (${System.currentTimeMillis() - t0}ms)", e)
            }
        }
    }

    /** 主线程任务：崩溃兜底必须最先装，日志次之，其后才依赖 Logger 的组件初始化 */
    private fun mainStartupTasks(): List<StartupTask> = listOf(
        StartupTask("globalCrashHandler") { installGlobalCrashHandler() },
        // B-07：日志初始化提前到启动最前，避免 Application 段（崩溃处理/UIConfig/ServiceLocator）
        // 的日志因 Logger 尚未初始化而丢失。Logger.init 幂等；权限未就绪导致的失败
        // 会留在 isInitialized=false，由后续（SplashActivity.createWorkDirectory）重试。
        StartupTask("loggerInit") { Logger.init(Constants.LOG_DIR) },
        StartupTask("uiConfigInit") {
            UIConfig.init(this)
            com.UIN.Tool.ui.common.StyleManager.init()
            applyLocale()
            applyNightMode()
            Logger.i(TAG, Str.get(R.string.uiconfig_initialized))
        },
        StartupTask("serviceLocatorInit") {
            ServiceLocator.init(this)
            Logger.i(TAG, Str.get(R.string.servicelocator_initialized))
        }
    )

    /**
     * B-01/B-02/B-03/B-04/B-05/B-08：全是磁盘/资产遍历型初始化，原先串在主线程
     * onCreate 里（建目录、清 .uin、扫 icons 缓存、遍历 assets/textmate 语法与主题、
     * 清桌面快捷方式），是冷启动最贵的一段；统一挪到 appScope(IO) 后台执行，
     * 保留原有先后顺序与逐段 try/catch 归因。
     */
    private fun backgroundStartupTasks(): List<StartupTask> = listOf(
        StartupTask("initWorkDirectory") {
            initWorkDirectory()
            Logger.i(TAG, Str.get(R.string.work_directory_initialized))
        },
        StartupTask("diskCacheCleanup") {
            com.UIN.Tool.cache.DiskCache.cleanup("icons")
            Logger.i(TAG, "Disk cache cleanup completed")
        },
        StartupTask("cleanupOnVersionUpgrade") { cleanupOnVersionUpgrade() },
        StartupTask("clearOldDynamicShortcuts") { clearOldDynamicShortcuts() },
        // B-02：TextMate 语法/主题加载（遍历 assets，最贵的一段）改后台；
        // 用户进终端编辑器时语法早已加载完毕
        StartupTask("initTextMate") {
            initTextMate()
            Logger.i(TAG, Str.get(R.string.textmate_initialized))
        },
        // B-01：killStaleProcesses 含 Runtime.exec + waitFor，保持
        // 「先清理残留进程 → 再检查安装环境」的先后顺序
        StartupTask("killStaleProcesses") { SharedSupervisor.killStaleProcesses() },
        // B-80：编排与 Toast 全部在 EnvironmentBootstrap，Application 只发起
        StartupTask("environmentAutoInstall") {
            com.UIN.Tool.bootstrap.EnvironmentBootstrap.autoInstall(appScope, this@UinApplication)
        },
        // G-28：小部件周期刷新调度（读 UIConfig 开关，幂等 KEEP/cancel；WorkManager 自动初始化）
        StartupTask("widgetRefreshSchedule") {
            com.UIN.Tool.widget.WidgetRefreshScheduler.apply(this)
        }
    )

    /**
     * C-12：内存吃紧时释放插件「保留会话」WebView。
     * 这些 WebView 的宿主 Activity 均已销毁（retain 时已从视图树 remove），
     * 可安全 destroy；否则数十 MB WebView 会常驻到进程退出。
     */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        try {
            if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) {
                PluginManager.clearRetainedWebViews()
                Logger.i(TAG, "onTrimMemory($level): retained plugin WebViews released")
            }
        } catch (e: Exception) {
            Logger.e(TAG, "onTrimMemory failed", e)
        }
    }

    /**
     * 全局未捕获异常处理：把任何线程（含原生插件后台线程）的崩溃写入日志目录，
     * 避免「闪退到桌面但日志里没有任何记录」。仅主线程崩溃交给系统默认处理（进程终止），
     * 非主线程崩溃仅记录，不让单个插件后台线程把整个宿主进程带走。
     */
    private fun installGlobalCrashHandler() {
        try {
            val previous = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                try {
                    val isMain = thread === android.os.Looper.getMainLooper().thread
                    if (isMain) {
                        // 主线程崩溃：进程随即终止。标记「下次启动直达日志页」（commit 同步落盘），
                        // 再交给系统默认处理走闪退流程。
                        CrashLogUtils.logExceptionAndNavigate(this, throwable, "Uncaught-${thread.name}")
                        previous?.uncaughtException(thread, throwable)
                            ?: android.os.Process.killProcess(android.os.Process.myPid())
                    } else {
                        CrashLogUtils.logException(this, throwable, "Uncaught-${thread.name}")
                        Logger.e(TAG, "background thread crash recorded (process kept): ${thread.name} - ${throwable.message}")
                    }
                } catch (e: Exception) {
                    // B-09：记录环节自身失败也不能静默——先落 logcat 兜底，再交回默认处理器，
                    // 避免「崩溃既没写进日志文件也没进 logcat」
                    android.util.Log.e(TAG, "crash handler failed while recording", e)
                    android.util.Log.e(TAG, "original crash on ${thread.name}", throwable)
                    previous?.uncaughtException(thread, throwable)
                }
            }
            Logger.i(TAG, Str.get(R.string.global_crash_handler_installed))
        } catch (e: Exception) {
            Logger.e(TAG, "installGlobalCrashHandler failed", e)
        }
    }

    /**
     * E-09（方案B）：把主题偏好交给 AppCompat 统一执行 —— 显式深/浅覆盖系统 uiMode、
     * 跟随系统则透传；XML 资源（values-night）与窗口配置自此与 Compose 同源，
     * 且 setDefaultNightMode 会自动重建已启动的 Activity（改主题全局即时生效）。
     */
    fun applyNightMode() {
        if (!UIConfig.isInitialized()) return
        try {
            val mode = when (UIConfig.getInstance().getThemeMode()) {
                UIConfig.THEME_MODE_DARK -> AppCompatDelegate.MODE_NIGHT_YES
                UIConfig.THEME_MODE_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
            if (AppCompatDelegate.getDefaultNightMode() != mode) {
                AppCompatDelegate.setDefaultNightMode(mode)
            }
        } catch (e: Exception) {
            Logger.e(TAG, "applyNightMode failed", e)
        }
    }

    fun applyLocale() {
        if (!UIConfig.isInitialized()) return
        try {
            val lang = UIConfig.getInstance().getLanguage()
            val locale = when (lang) {
                "zh" -> java.util.Locale.CHINESE
                "en" -> java.util.Locale.ENGLISH
                else -> systemDefaultLocale ?: java.util.Locale.getDefault()
            }
            java.util.Locale.setDefault(locale)

            // B-10（方案A）：三套 locale 机制收敛到本函数 ——
            // ① AppCompat delegate：27 个 Activity 的 attachBaseContext/XML 上下文统一由它处理，
            //    并接入系统「应用语言」设置（应用内设置为准，系统改动下次 applyLocale 覆盖回来）；
            // ② app resources：Str.get 走 app context，保留原 updateConfiguration 通道保证文案即时切换。
            val tag = when (lang) {
                "zh" -> "zh"
                "en" -> "en"
                else -> ""
            }
            androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(
                androidx.core.os.LocaleListCompat.forLanguageTags(tag)
            )

            val config = android.content.res.Configuration(resources.configuration)
            config.setLocales(android.os.LocaleList(locale))
            @Suppress("DEPRECATION")
            resources.updateConfiguration(config, resources.displayMetrics)
        } catch (e: Exception) {
            Logger.e(TAG, "applyLocale failed", e)
        }
    }

    /**
     * 清除旧版动态插件快捷方式（已改为 shortcuts.xml 静态快捷方式）。
     */
    private fun clearOldDynamicShortcuts() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N_MR1) {
            try {
                val sm = getSystemService(android.content.pm.ShortcutManager::class.java) ?: return
                val oldIds = sm.dynamicShortcuts
                    .filter { it.id.startsWith("plugin_") }
                    .map { it.id }
                if (oldIds.isNotEmpty()) {
                    sm.removeDynamicShortcuts(oldIds)
                    Logger.i(TAG, "Cleared ${oldIds.size} old dynamic plugin shortcuts")
                }
            } catch (e: Exception) {
                Logger.e(TAG, "clearOldDynamicShortcuts error: ${e.message}", e)
            }
        }
    }

    private fun initTextMate() {
        // 1. 添加 Assets 文件提供者
        FileProviderRegistry.getInstance().addFileProvider(
            AssetsFileResolver(assets)
        )

        // 2. 动态加载语法
        loadGrammars()

        // 3. 加载主题
        loadThemes()
    }

    /**
     * 动态加载所有语法文件
     * 遍历 assets/textmate/ 目录下的所有语言文件夹
     */
    private fun loadGrammars() {
        try {
            val grammarRegistry = GrammarRegistry.getInstance()
            val basePath = "textmate"
            
            // 获取 assets/textmate 下的所有子目录
            val languageDirs = assets.list(basePath) ?: run {
                Logger.w(TAG, Str.get(R.string.assets_textmate_directory_not_found))
                return
            }
            
            var loadedCount = 0
            var failedCount = 0
            
            Logger.d(TAG, Str.get(R.string.found_languagedirs_size_language_dir, languageDirs.size))
            
            for (langDir in languageDirs) {
                // 跳过 themes 目录
                if (langDir == "themes") continue
                
                val syntaxesPath = "$basePath/$langDir/syntaxes"
                val files = assets.list(syntaxesPath)
                if (files == null || files.isEmpty()) {
                    Logger.w(TAG, Str.get(R.string.directory_langdir_syntaxes_is_empty, langDir))
                    continue
                }
                
                // 查找语法文件
                var grammarFile: String? = null
                for (fileName in files) {
                    if (isValidGrammarFile(fileName)) {
                        grammarFile = fileName
                        break
                    }
                }
                
                if (grammarFile == null) {
                    Logger.w(TAG, Str.get(R.string.no_grammar_file_found_langdir, langDir))
                    failedCount++
                    continue
                }
                
                val fullPath = "$syntaxesPath/$grammarFile"
                val scopeName = getScopeNameFromFile(langDir, grammarFile)
                if (scopeName == null) {
                    Logger.w(TAG, Str.get(R.string.could_not_infer_scope_langdir_gramma, langDir, grammarFile))
                    failedCount++
                    continue
                }
                
                try {
                    val inputStream = assets.open(fullPath)
                    
                    // 创建 GrammarDefinition
                    val grammarDef = object : GrammarDefinition {
                        override fun getName(): String = langDir
                        
                        override fun getLanguageConfiguration(): String? = null
                        
                        override fun getScopeName(): String = scopeName
                        
                        override fun getGrammar(): IGrammarSource {
                            return IGrammarSource.fromInputStream(inputStream, fullPath, null)
                        }
                    }
                    
                    grammarRegistry.loadGrammar(grammarDef)
                    loadedCount++
                    Logger.d(TAG, Str.get(R.string.loaded_grammar_langdir_scopename_gra, langDir, scopeName, grammarFile))
                } catch (e: Exception) {
                    Logger.e(TAG, Str.get(R.string.failed_to_load_grammar_langdir, langDir), e)
                    failedCount++
                }
            }
            
            Logger.i(TAG, Str.get(R.string.grammar_load_complete_success_loaded, loadedCount, failedCount))
        } catch (e: Exception) {
            Logger.e(TAG, Str.get(R.string.grammar_load_failed), e)
        }
    }

    /**
     * 判断是否为有效的语法文件
     */
    private fun isValidGrammarFile(fileName: String): Boolean {
        val lowerName = fileName.lowercase()
        return lowerName.endsWith(".json") ||
                lowerName.endsWith(".tmLanguage") ||
                lowerName.endsWith(".plist")
    }

    /**
     * 从文件名或目录名推断 Scope Name
     */
    private fun getScopeNameFromFile(langDir: String, fileName: String): String? {
        // 1. 移除扩展名
        val baseName = fileName.substringBeforeLast('.')
            .replace(Regex("\\.tmLanguage$"), "")
            .replace(Regex("\\.tmLang$"), "")
        
        // 2. 特殊文件名映射（处理大小写不一致）
        val fileNameToScope = mapOf(
            "JavaScript" to "source.js",
            "JSON" to "source.json",
            "TypeScript" to "source.ts",
            "Kotlin" to "source.kotlin",
            "CSharp" to "source.cs",
            "FSharp" to "source.fsharp",
            "ShellScript" to "source.shell",
            "Objective-C" to "source.objc",
            "Handlebars" to "text.html.handlebars",
            "LaTeX" to "text.tex.latex",
            "Bibtex" to "text.tex.bibtex",
            "TeX" to "text.tex",
            "Python" to "source.python",
            "batchfile" to "source.batch",
            "coffeescript" to "source.coffee",
            "c" to "source.c",
            "cpp" to "source.cpp",
            "cuda-cpp" to "source.cuda-cpp",
            "dart" to "source.dart",
            "docker" to "source.dockerfile",
            "dotenv" to "source.dotenv",
            "go" to "source.go",
            "groovy" to "source.groovy",
            "hlsl" to "source.hlsl",
            "html" to "text.html.basic",
            "julia" to "source.julia",
            "less" to "source.less",
            "lua" to "source.lua",
            "make" to "source.makefile",
            "markdown" to "text.html.markdown",
            "perl" to "source.perl",
            "php" to "source.php",
            "powershell" to "source.powershell",
            "pug" to "source.pug",
            "r" to "source.r",
            "ruby" to "source.ruby",
            "rust" to "source.rust",
            "scss" to "source.scss",
            "shaderlab" to "source.shaderlab",
            "shell-unix-bash" to "source.shell",
            "sql" to "source.sql",
            "swift" to "source.swift",
            "xml" to "text.xml",
            "yaml" to "source.yaml"
        )
        fileNameToScope[baseName]?.let { return it }
        
        // 3. 使用目录名映射
        val dirToScope = mapOf(
            "bat" to "source.batch",
            "clojure" to "source.clojure",
            "coffeescript" to "source.coffee",
            "cpp" to "source.cpp",
            "csharp" to "source.cs",
            "css" to "source.css",
            "dart" to "source.dart",
            "diff" to "source.diff",
            "docker" to "source.dockerfile",
            "dockerfile" to "source.dockerfile",
            "dotenv" to "source.dotenv",
            "elixir" to "source.elixir",
            "erlang" to "source.erlang",
            "fsharp" to "source.fsharp",
            "go" to "source.go",
            "groovy" to "source.groovy",
            "handlebars" to "text.html.handlebars",
            "haskell" to "source.haskell",
            "hlsl" to "source.hlsl",
            "html" to "text.html.basic",
            "ini" to "source.ini",
            "java" to "source.java",
            "javascript" to "source.js",
            "json" to "source.json",
            "julia" to "source.julia",
            "kotlin" to "source.kotlin",
            "latex" to "text.tex.latex",
            "less" to "source.less",
            "log" to "source.log",
            "lua" to "source.lua",
            "make" to "source.makefile",
            "makefile" to "source.makefile",
            "markdown" to "text.html.markdown",
            "markdown-basics" to "text.html.markdown",
            "markdown-math" to "text.html.markdown.math",
            "objective-c" to "source.objc",
            "perl" to "source.perl",
            "php" to "source.php",
            "powershell" to "source.powershell",
            "prompt-basics" to "source.prompt",
            "properties" to "source.properties",
            "pug" to "source.pug",
            "python" to "source.python",
            "r" to "source.r",
            "razor" to "source.razor",
            "restructuredtext" to "source.rst",
            "ruby" to "source.ruby",
            "rust" to "source.rust",
            "scala" to "source.scala",
            "scss" to "source.scss",
            "search-result" to "source.search-result",
            "shaderlab" to "source.shaderlab",
            "shell" to "source.shell",
            "shellscript" to "source.shell",
            "sql" to "source.sql",
            "swift" to "source.swift",
            "toml" to "source.toml",
            "typescript" to "source.ts",
            "typescript-basics" to "source.ts",
            "vb" to "source.vb",
            "xml" to "text.xml",
            "yaml" to "source.yaml"
        )
        return dirToScope[langDir.lowercase()]
    }

    /**
     * 加载主题
     */
    private fun loadThemes() {
        try {
            val themeRegistry = ThemeRegistry.getInstance()
            val themesPath = "textmate/themes"
            
            val themeFiles = assets.list(themesPath) ?: return
            
            var loadedCount = 0
            
            for (fileName in themeFiles) {
                if (!fileName.endsWith(".json")) continue
                
                try {
                    val themeName = fileName.removeSuffix(".json")
                    val fullPath = "$themesPath/$fileName"
                    val inputStream = assets.open(fullPath)
                    
                    val themeSource = IThemeSource.fromInputStream(
                        inputStream,
                        fullPath,
                        null
                    )
                    val themeModel = ThemeModel(themeSource, themeName)
                    themeModel.isDark = isDarkTheme(themeName)
                    
                    themeRegistry.loadTheme(themeModel, false)
                    loadedCount++
                    Logger.d(TAG, Str.get(R.string.theme_loaded_themename, themeName))
                } catch (e: Exception) {
                    Logger.w(TAG, Str.get(R.string.theme_load_failed_filename_e_message, fileName, e.message))
                }
            }
            
            // 设置默认主题
            if (themeRegistry.setTheme("one-dark-pro")) {
                Logger.i(TAG, Str.get(R.string.default_theme_one_dark_pro))
            } else {
                Logger.w(TAG, Str.get(R.string.failed_to_set_default_theme))
            }
            
            Logger.i(TAG, Str.get(R.string.theme_load_complete_loadedcount_them, loadedCount))
        } catch (e: Exception) {
            Logger.e(TAG, Str.get(R.string.theme_load_failed), e)
        }
    }

    private fun isDarkTheme(themeName: String): Boolean {
        val darkThemes = setOf(
            "dark-plus", "dracula", "dracula-soft",
            "github-dark", "github-dark-dimmed",
            "material-theme", "material-theme-darker",
            "material-theme-ocean", "material-theme-palenight",
            "min-dark", "monokai", "nord",
            "one-dark-pro", "poimandres",
            "rose-pine", "rose-pine-moon",
            "slack-dark", "slack-ochin",
            "solarized-dark",
            "vitesse-black", "vitesse-dark",
            "abyss-color-theme", "dimmed-monokai-color-theme",
            "dark_modern", "dark_plus", "dark_vs", "hc_black"
        )
        return darkThemes.contains(themeName)
    }

    private fun cleanupOnVersionUpgrade() {
        val prefs = getSharedPreferences("uin_prefs", Context.MODE_PRIVATE)
        val lastVersion = prefs.getInt("last_version_code", 0)
        val currentVersion = try {
            packageManager.getPackageInfo(packageName, 0).longVersionCode.toInt()
        } catch (_: Exception) { 0 }
        if (lastVersion != 0 && lastVersion != currentVersion) {
            Logger.i(TAG, "version upgraded: $lastVersion -> $currentVersion, cleaning .uin dirs")
            val pluginsDir = File(Constants.PLUGIN_DIR)
            pluginsDir.listFiles()?.filter { it.isDirectory }?.forEach { pluginDir ->
                val uinDir = File(pluginDir, ".uin")
                if (uinDir.exists()) {
                    uinDir.deleteRecursively()
                    Logger.d(TAG, "deleted ${uinDir.absolutePath}")
                }
            }
            // 也清理共享 .uin（宿主根目录下）
            val sharedUin = File(pluginsDir, ".uin")
            if (sharedUin.exists()) {
                sharedUin.deleteRecursively()
                Logger.d(TAG, "deleted shared ${sharedUin.absolutePath}")
            }
        }
        prefs.edit().putInt("last_version_code", currentVersion).apply()
    }

    private fun initWorkDirectory() {
        val workDir = File(Constants.WORK_DIR)
        if (!workDir.exists()) {
            workDir.mkdirs()
        }

        val subDirs = listOf(
            "plugins", "templates", "downloads", "cache", "logs"
        )

        for (subDir in subDirs) {
            val dir = File(workDir, subDir)
            if (!dir.exists()) {
                dir.mkdirs()
            }
        }

        // 在共享存储工作目录创建 .nomedia，阻止 Android 媒体库扫描其中的文件（如插件资源、下载文件）
        val noMediaFile = File(workDir, ".nomedia")
        if (!noMediaFile.exists()) {
            try {
                noMediaFile.createNewFile()
            } catch (_: Exception) {
                Logger.w(TAG, "Failed to create .nomedia in ${workDir.absolutePath}")
            }
        }

        // 清理历史遗留的空 Termux 目录（usr/var/etc 等不属于本软件，仅删除空目录）。
        // 先删最深子目录，再删父目录，避免父目录因内含空子目录而被跳过。
        val staleTermuxDirs = listOf(
            "usr/bin", "usr/lib", "usr/include", "usr",
            "var/log", "var/run", "var/tmp", "var",
            "home", "etc", "tmp"
        )
        for (staleDir in staleTermuxDirs) {
            val dir = File(workDir, staleDir)
            if (dir.isDirectory && dir.listFiles()?.isEmpty() == true) {
                dir.delete()
            }
        }

        workDir.setReadable(true, false)
        workDir.setWritable(true, false)
        workDir.setExecutable(true, false)

        Logger.i(TAG, Str.get(R.string.work_directory_created_workdir_absol, workDir.absolutePath))
    }
}