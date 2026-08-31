package com.UIN.Tool

import com.UIN.Tool.utils.Str
import android.content.Context
import androidx.multidex.MultiDex
import com.UIN.Tool.app.TermuxApplication
import com.UIN.Tool.core.di.ServiceLocator
import com.UIN.Tool.log.Logger
import com.UIN.Tool.utils.UIConfig
import com.UIN.Tool.constants.AppConstants as Constants
import com.UIN.Tool.utils.CrashLogUtils
import com.UIN.Tool.plugin.SharedSupervisor
import io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.langs.textmate.registry.model.GrammarDefinition
import io.github.rosemoe.sora.langs.textmate.registry.model.ThemeModel
import io.github.rosemoe.sora.langs.textmate.registry.provider.AssetsFileResolver
import org.eclipse.tm4e.core.registry.IGrammarSource
import org.eclipse.tm4e.core.registry.IThemeSource
import java.io.File

class UinApplication : TermuxApplication() {

    companion object {
        private const val TAG = "UinApplication"

        @JvmStatic
        private lateinit var instance: UinApplication

        @JvmStatic
        fun getInstance(): UinApplication = instance

        @JvmStatic
        fun getAppContext(): Context = instance.applicationContext

        /** 后台环境安装进行中标志，避免与 PluginHostActivity 的安装流程冲突 */
        @Volatile
        private var _isEnvironmentInstalling = false

        @JvmStatic
        fun isEnvironmentInstalling(): Boolean = _isEnvironmentInstalling
    }

    override fun attachBaseContext(base: Context) {
        MultiDex.install(base)
        super.attachBaseContext(base)
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        installGlobalCrashHandler()

        try {
            UIConfig.init(this)
            com.UIN.Tool.ui.common.StyleManager.init()
            applyLocale()
            Logger.i(TAG, Str.get(R.string.uiconfig_initialized))
        } catch (e: Exception) {
            Logger.e(TAG, "UIConfig init failed", e)
        }

        try {
            ServiceLocator.init(this)
            Logger.i(TAG, Str.get(R.string.servicelocator_initialized))
        } catch (e: Exception) {
            Logger.e(TAG, Str.get(R.string.servicelocator_initialization_failed), e)
        }

        try {
            initWorkDirectory()
            Logger.i(TAG, Str.get(R.string.work_directory_initialized))
        } catch (e: Exception) {
            Logger.e(TAG, Str.get(R.string.work_directory_initialization_failed), e)
        }

        try {
            com.UIN.Tool.cache.DiskCache.cleanup("icons")
            Logger.i(TAG, "Disk cache cleanup completed")
        } catch (e: Exception) {
            Logger.e(TAG, "Disk cache cleanup failed", e)
        }

        try {
            cleanupOnVersionUpgrade()
        } catch (e: Exception) {
            Logger.e(TAG, "cleanupOnVersionUpgrade failed", e)
        }

        try {
            SharedSupervisor.killStaleProcesses()
            Logger.i(TAG, "stale supervisor processes cleaned")
        } catch (e: Exception) {
            Logger.e(TAG, "killStaleProcesses failed", e)
        }

        try {
            clearOldDynamicShortcuts()
        } catch (e: Exception) {
            Logger.e(TAG, "clearOldDynamicShortcuts failed", e)
        }

        try {
            autoInstallEnvironment()
        } catch (e: Exception) {
            Logger.e(TAG, "autoInstallEnvironment failed", e)
        }

        try {
            initTextMate()
            Logger.i(TAG, Str.get(R.string.textmate_initialized))
        } catch (e: Exception) {
            Logger.e(TAG, Str.get(R.string.textmate_initialization_failed), e)
        }

        Logger.i(TAG, Str.get(R.string.uin_tool_app_startup_complete))
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
                        Logger.e(TAG, "后台线程崩溃已记录（不终止进程）: ${thread.name} - ${throwable.message}")
                    }
                } catch (e: Exception) {
                    previous?.uncaughtException(thread, throwable)
                }
            }
            Logger.i(TAG, Str.get(R.string.global_crash_handler_installed))
        } catch (e: Exception) {
            Logger.e(TAG, "installGlobalCrashHandler failed", e)
        }
    }

    fun applyLocale() {
        if (!UIConfig.isInitialized()) return
        val lang = UIConfig.getInstance().getLanguage()
        val locale = when (lang) {
            "zh" -> java.util.Locale.CHINESE
            "en" -> java.util.Locale.ENGLISH
            else -> java.util.Locale.getDefault()
        }
        java.util.Locale.setDefault(locale)
        val config = android.content.res.Configuration(resources.configuration)
        config.setLocales(android.os.LocaleList(locale))
        @Suppress("DEPRECATION")
        resources.updateConfiguration(config, resources.displayMetrics)
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

    /**
     * 后台自动检测并安装 Termux bootstrap 和 Alpine 容器。
     * 仅在内置 Termux 模式下执行；Real Termux 模式跳过。
     * 安装前设置 [isEnvironmentInstalling] 标志，PluginHostActivity 检测到后跳过自身安装，避免冲突。
     */
    private fun autoInstallEnvironment() {
        val ctx = applicationContext
        // 仅内置模式需要 bootstrap + alpine；Real Termux 模式由外部 Termux 管理
        if (!com.UIN.Tool.plugin.BackendConfig.isBuiltin(ctx)) return

        Thread {
            try {
                _isEnvironmentInstalling = true
                Logger.i(TAG, "autoInstallEnvironment: checking bootstrap & alpine")

                // 1. 确保 Termux bootstrap 就绪（复用 PluginHostActivity 的检测逻辑）
                if (!com.UIN.Tool.plugin.ProotContainerManager.isTermuxReady()) {
                    Logger.i(TAG, "autoInstallEnvironment: bootstrap not ready, installing")
                    com.UIN.Tool.plugin.ProotContainerManager.installBootstrapHeadless(ctx)
                    if (!com.UIN.Tool.plugin.ProotContainerManager.isTermuxReady()) {
                        Logger.e(TAG, "autoInstallEnvironment: bootstrap install failed")
                        _isEnvironmentInstalling = false
                        return@Thread
                    }
                    // 写入环境变量文件（与 TermuxInstaller 安装后一致）
                    try {
                        com.UIN.Tool.shared.termux.shell.command.environment.TermuxShellEnvironment.writeEnvironmentToFile(ctx)
                        Logger.i(TAG, "autoInstallEnvironment: environment file written")
                    } catch (e: Exception) {
                        Logger.e(TAG, "autoInstallEnvironment: writeEnvironmentToFile failed: ${e.message}")
                    }
                    Logger.success(TAG, "autoInstallEnvironment: bootstrap installed")
                }

                // bootstrap 就绪即可创建终端会话，不再阻塞
                _isEnvironmentInstalling = false

                // 2. 确保 Alpine 容器已安装（异步，不阻塞终端）
                if (!com.UIN.Tool.plugin.ProotContainerManager.isAlpineInstalled()) {
                    Logger.i(TAG, "autoInstallEnvironment: Alpine not installed, installing in background")
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        android.widget.Toast.makeText(ctx, ctx.getString(com.UIN.Tool.R.string.alpine_installing_do_not_exit), android.widget.Toast.LENGTH_LONG).show()
                    }
                    com.UIN.Tool.plugin.ProotContainerManager.ensureAlpine(ctx, null) { success ->
                        if (success) {
                            Logger.success(TAG, "autoInstallEnvironment: Alpine installed successfully")
                            android.os.Handler(android.os.Looper.getMainLooper()).post {
                                android.widget.Toast.makeText(ctx, ctx.getString(com.UIN.Tool.R.string.alpine_install_complete), android.widget.Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Logger.e(TAG, "autoInstallEnvironment: Alpine install failed")
                        }
                    }
                } else {
                    Logger.i(TAG, "autoInstallEnvironment: environment ready")
                }
            } catch (e: Exception) {
                _isEnvironmentInstalling = false
                Logger.e(TAG, "autoInstallEnvironment error: ${e.message}", e)
            }
        }.start()
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