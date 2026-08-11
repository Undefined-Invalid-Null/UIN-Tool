package com.UIN.Tool

import com.UIN.Tool.utils.Str
import android.content.Context
import androidx.multidex.MultiDex
import com.UIN.Tool.app.TermuxApplication
import com.UIN.Tool.core.di.ServiceLocator
import com.UIN.Tool.log.Logger
import com.UIN.Tool.utils.UIConfig
import com.UIN.Tool.constants.AppConstants as Constants
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
    }

    override fun attachBaseContext(base: Context) {
        MultiDex.install(base)
        super.attachBaseContext(base)
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        try {
            UIConfig.init(this)
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
            initTextMate()
            Logger.i(TAG, Str.get(R.string.textmate_initialized))
        } catch (e: Exception) {
            Logger.e(TAG, Str.get(R.string.textmate_initialization_failed), e)
        }

        Logger.i(TAG, Str.get(R.string.uin_tool_app_startup_complete))
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