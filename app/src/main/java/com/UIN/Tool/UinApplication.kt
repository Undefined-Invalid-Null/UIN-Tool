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
import org.json.JSONObject
import android.content.res.AssetManager
import com.UIN.Tool.ui.screen.dev.editor.TextMateThemes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.ByteArrayInputStream
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

        /**
         * 6.2.0 批次33：scopeName → 该语法在 assets 里的路径。
         *
         * 用于编辑器「设语言」时按需重新解析语法文件，重建一份**干净的 Grammar 实例**
         * （见 CodeEditorScreen.refreshGrammar）——tm4e 的 `ObjectCloner.deepClone` 不真正
         * 克隆普通对象，多语法共享同一份 raw，规则 id 会被别的实例占掉导致
         * `No rule with index 1` 完全无高亮。启动时 `loadGrammars()` 填充。
         */
        val grammarAssetPaths: MutableMap<String, String> =
            java.util.concurrent.ConcurrentHashMap()

        /**
         * 6.2.0 批次66（用户：「txtmate 里的文件没全用上，别硬编码，必要时单独建一个 json
         * 记录」）—— `assets/textmate/languages.json` 清单解析结果。
         *
         * 清单登记每个语法文件的 grammar 路径 / name / scopeName；带 `extensions` 的条目再产出
         * 「后缀 → scopeName」兜底表，供 `CodeEditorScreen.getLanguageScopeName()` 的 else 分支
         * 查询 —— 以后加语法只需补一条清单记录，不用再改 when 白名单（既有 when 分支优先，
         * 老后缀的行为完全不变）。启动时 `loadLanguageManifest()` 填充。
         */
        @Volatile
        var manifestScopeByExt: Map<String, String> = emptyMap()
            private set

        /** 清单里可作为手动语法项的 (显示名, scopeName)，按 scope 去重、已滤掉注入型语法。 */
        @Volatile
        private var manifestExtras: List<Pair<String, String>> = emptyList()

        /**
         * 语法选择器的动态附加项：清单登记、但不在 CodeEditorScreen 精选列表里的语言。
         * 清单还没读到（或读失败）时现场兜底解析一次，保证选择器总能列全。
         */
        fun extraLanguages(assets: AssetManager): List<Pair<String, String>> {
            val base = manifestExtras.takeIf { it.isNotEmpty() }
                ?: runCatching { parseLanguageManifest(assets).second }
                    .getOrElse {
                        Logger.w(TAG, "extraLanguages fallback failed: ${it.message}")
                        emptyList()
                    }
            // 批次66追加：用户「语法/主题文件管理」导入的私有语法也进选择器（按 scope 去重）
            val users = userGrammarList.map { it.name to it.scope }
            return (base + users).distinctBy { it.second }
        }

        /**
         * 解析 `textmate/languages.json` → (后缀表, 手动语法项)。
         * 纯读文件不抛异常；`extensions` 缺省表示该语法只做手动选择、不参与后缀推断。
         */
        private fun parseLanguageManifest(
            assets: AssetManager
        ): Pair<Map<String, String>, List<Pair<String, String>>> {
            val raw = assets.open("textmate/languages.json")
                .use { it.readBytes().toString(Charsets.UTF_8) }
            val arr = JSONObject(raw).getJSONArray("languages")
            val extMap = HashMap<String, String>()
            val extras = LinkedHashMap<String, String>() // scope → name，天然按 scope 去重
            for (i in 0 until arr.length()) {
                val entry = arr.optJSONObject(i) ?: continue
                val scope = entry.optString("scopeName")
                val name = entry.optString("name")
                if (scope.isEmpty()) continue
                val exts = entry.optJSONArray("extensions")
                if (exts != null) {
                    for (j in 0 until exts.length()) {
                        val ext = exts.optString(j).lowercase().removePrefix(".")
                        if (ext.isNotEmpty() && !extMap.containsKey(ext)) extMap[ext] = scope
                    }
                }
                // 注入型语法（markdown 数学块等）只嵌进宿主语言，不作为独立手动选项
                if (name.isNotEmpty() && !scope.contains(".math") && !extras.containsKey(scope)) {
                    extras[scope] = name
                }
            }
            return extMap to extras.map { (scope, name) -> name to scope }
        }

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

        // ==================== 批次66追加：用户导入的语法 / 主题 ====================

        /** 一条用户导入的语法：显示名、scopeName、文件绝对路径。 */
        data class UserGrammar(val name: String, val scope: String, val path: String)

        /** 用户导入的语法登记（选择器附加项与查重；删除时移除，注册表里的实例等重启失效）。 */
        private val userGrammarList =
            java.util.concurrent.CopyOnWriteArrayList<UserGrammar>()

        /**
         * 已注册主题的两个查重键：文件名 stem（= ThemeModel 的 name）与主题 JSON 的 `name`
         * 字段（= sora `loadTheme` 的实际去重键）。导入前用它判断是否会与现有主题撞名 ——
         * 撞 JSON name 时 sora 会走「已存在 → setTheme(旧主题)」分支，既加不进来还会把
         * 当前主题切走，所以必须在导入侧拦掉。
         */
        private val loadedThemeStems: MutableSet<String> =
            java.util.concurrent.ConcurrentHashMap.newKeySet()
        private val loadedThemeRawNames: MutableSet<String> =
            java.util.concurrent.ConcurrentHashMap.newKeySet()

        fun userGrammars(): List<UserGrammar> = userGrammarList.toList()

        fun themeStems(): Set<String> = loadedThemeStems.toSet()

        fun themeRawNames(): Set<String> = loadedThemeRawNames.toSet()

        /** 记录一个已注册主题（内置与用户导入共用），供导入查重。 */
        fun trackLoadedTheme(stem: String, cleanedJson: String) {
            loadedThemeStems.add(stem)
            val raw = runCatching { org.json.JSONObject(cleanedJson).optString("name") }
                .getOrDefault("")
            if (raw.isNotEmpty()) loadedThemeRawNames.add(raw)
        }

        /** 删除用户语法后摘掉登记（注册表中的 Grammar 实例仍存活到下次启动）。 */
        fun forgetUserGrammar(scope: String) {
            userGrammarList.removeAll { it.scope == scope }
            grammarAssetPaths.remove(scope)
        }

        private fun addOrReplaceUserGrammar(g: UserGrammar) {
            userGrammarList.removeAll { it.scope == g.scope }
            userGrammarList.add(g)
        }
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
        // 批次37：把 logcat 默认落盘到同一日志目录（后台守护线程，不阻塞启动），
        // 让库直接写 logcat 的异常（如 sora 的 AsyncAnalysis）也能进文件、可导出。
        StartupTask("logcatRecorderInit") { com.UIN.Tool.log.LogcatRecorder.start() },
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

        // 2~3. 加载语法 + 主题；finally 里无条件置位就绪标志，保证「挂起等就绪再打开」
        // 的 ACTION_VIEW 不会因某个加载抛异常而永久卡在「正在打开…」。
        // 6.2.0 批次66：先读 languages.json 清单（后缀→scope 兜底表 + 选择器附加项），
        // 与语法/主题同一批就绪，内部自捕异常不会影响后续加载。
        loadLanguageManifest()
        try {
            loadGrammars()
            loadThemes()
        } finally {
            // 6.2.0 批次28：语法与主题全部就绪（或失败）后才置位，让启动瞬间打开的文件
            // 在两者齐备时一次性带高亮打开（避免先 EmptyLanguage 再补设的空窗）。
            com.UIN.Tool.ui.screen.dev.editor.TextMateInitState.grammarsReady.value = true
        }
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
                // 6.2.0 批次33：assets.list() 只返回名字，分不出文件与目录，于是 textmate 根
                // 目录里的散落文件（languages.json、各种 *.json）会被当成语法目录去列
                // syntaxes/，每启动打一条「⚠️ 目录 X/syntaxes 为空」。语法目录名一律不含点，
                // 故「含点」即判定为文件直接跳过。实测该规则不影响任何真实语法目录。
                if (langDir.contains('.')) continue

                val syntaxesPath = "$basePath/$langDir/syntaxes"
                val files = assets.list(syntaxesPath)
                if (files == null || files.isEmpty()) {
                    Logger.w(TAG, Str.get(R.string.directory_langdir_syntaxes_is_empty, langDir))
                    continue
                }

                // 该语言目录若带 language-configuration.json（括号对/自动缩进/换行规则），
                // 统一挂到目录下每个语法上；不存在时返回 null，库侧会自行跳过。
                val langConfigPath = "$basePath/$langDir/language-configuration.json".takeIf {
                    assets.list("$basePath/$langDir")?.contains("language-configuration.json") == true
                }

                // 6.2.0 批次9：遍历目录下**全部**语法文件。
                // 旧版只取 assets.list 返回的第一个文件，导致 typescript-basics 目录里的
                // TypeScriptReact.tmLanguage.json（声明 source.tsx）从未被注册，
                // .tsx 文件因此拿不到语法高亮并静默沿用上一个文件的语言。
                for (fileName in files) {
                    if (!isValidGrammarFile(fileName)) continue
                    // 注入语法不能作为独立语言注册
                    if (fileName.contains("injection", ignoreCase = true)) continue

                    val fullPath = "$syntaxesPath/$fileName"
                    val bytes = try {
                        assets.open(fullPath).use { it.readBytes() }
                    } catch (e: Exception) {
                        Logger.e(TAG, Str.get(R.string.failed_to_load_grammar_langdir, fullPath), e)
                        failedCount++
                        continue
                    }
                    if (bytes.isEmpty()) {
                        failedCount++
                        continue
                    }

                    // 资产损坏检测：历史上有语法文件内容是 "404: Not Found"（LFS 拉取失败留下的占位）
                    val head = bytes.decodeToString(0, minOf(bytes.size, 64))
                    if (head.contains("404: Not Found")) {
                        Logger.w(TAG, Str.get(R.string.skip_broken_grammar_asset, fullPath))
                        failedCount++
                        continue
                    }

                    // 优先使用语法文件自身声明的 scopeName（权威来源），推断仅作兜底
                    val scopeName = readScopeName(bytes) ?: getScopeNameFromFile(langDir, fileName)
                    if (scopeName == null) {
                        Logger.w(TAG, Str.get(R.string.could_not_infer_scope_langdir_gramma, langDir, fileName))
                        failedCount++
                        continue
                    }
                    if (scopeName.contains(".injection.")) continue

                    warnMalformedCaptures(scopeName, fileName, bytes)

                    try {
                        // 6.2.0 批次12 修复「代码高亮一直不生效」的真正根因：
                        //
                        // GrammarRegistry.loadGrammar() 用 GrammarDefinition.getName()
                        // 作为**去重键**（见其 grammarFileName2ScopeName map）：
                        //   if (grammarFileName2ScopeName.containsKey(languageName) && ...) return 已有;
                        // 旧代码让同目录下的所有语法文件都返回目录名（如 "typescript-basics"），
                        // 于是同一目录里**只有第一个文件能注册成功**，其余全部被静默跳过 ——
                        // source.tsx、source.js.jsx、source.python 等因此从来没能注册上，
                        // TextMateLanguage.create(scope) 找不到语法 → 回退 text.plain → 完全没有高亮。
                        //
                        // 现改为用**语法文件的完整路径**做 name（全局唯一），并把同目录的
                        // language-configuration.json（如有的话）接上，顺带获得括号匹配/自动缩进。
                        val grammarDef = object : GrammarDefinition {
                            override fun getName(): String = fullPath

                            override fun getLanguageConfiguration(): String? = langConfigPath

                            override fun getScopeName(): String = scopeName

                            override fun getGrammar(): IGrammarSource {
                                return IGrammarSource.fromInputStream(
                                    bytes.inputStream(), fullPath, null
                                )
                            }
                        }

                        val grammar = grammarRegistry.loadGrammar(grammarDef)
                        if (grammar == null) {
                            Logger.w(TAG, Str.get(R.string.failed_to_load_grammar_langdir, fullPath))
                            failedCount++
                            continue
                        }
                        loadedCount++
                        // 6.2.0 批次33：记下 scope → 资产路径，供编辑器按需重建干净 Grammar 实例。
                        grammarAssetPaths[scopeName] = fullPath
                        Logger.d(TAG, Str.get(R.string.loaded_grammar_langdir_scopename_gra, langDir, scopeName, fileName))
                    } catch (e: Exception) {
                        Logger.e(TAG, Str.get(R.string.failed_to_load_grammar_langdir, fullPath), e)
                        failedCount++
                    }
                }
            }

            // 批次66追加：用户导入的私有语法（filesDir/textmate/<dir>/syntaxes）与内置同批就绪
            loadedCount += loadUserGrammars()

            Logger.i(TAG, Str.get(R.string.grammar_load_complete_success_loaded, loadedCount, failedCount))
        } catch (e: Exception) {
            Logger.e(TAG, Str.get(R.string.grammar_load_failed), e)
        }
    }

    /**
     * 从语法文件内容中读取其**自身声明**的 scopeName。
     * 支持 JSON 语法（VS Code 常见）与 plist XML 语法（.tmLanguage）。
     * 读不出来返回 null，由调用方回退到按文件名/目录名推断。
     */
    /**
     * 6.2.0 批次38：语法资产自检 —— `captures` 里出现**裸字符串**条目时提前点名。
     *
     * 危害链条（实测于 xml.tmLanguage.json）：
     * `RawCaptures extends HashMap<IRawRule>`，`get(captureId)` 隐式强转 → 值是 String
     * 就抛 `ClassCastException`；该异常发生在 `RuleFactory.getCompiledRuleId` 的 lambda
     * 内部，而 `Grammar.registerRule` 是「先 `++_lastRuleId` → `setId()` 盖章 → 构造 →
     * `put`」，构造抛异常时 **put 被跳过**，于是 `_ruleId2desc` 里留下 id 洞、
     * `_lastRuleId` 却已自增。下次 tokenize 重试时 `desc.getId()` 已非空，直接返回这个
     * 从未注册过的 id → `getRule()` 抛 `IndexOutOfBoundsException` → 该语言**完全无高亮**。
     *
     * 这类畸形在编辑器里只会表现为「这个语言没高亮」，没有任何报错弹窗，故在加载期扫一遍
     * 并写进启动日志。仅检查 JSON 语法（plist 走另一条解析路径）。
     */
    private fun warnMalformedCaptures(scopeName: String, fileName: String, bytes: ByteArray) {
        if (!fileName.endsWith(".json", ignoreCase = true)) return
        val json = try {
            org.json.JSONObject(bytes.decodeToString())
        } catch (e: Exception) {
            return // 非法 JSON 由后续 loadGrammar 自行报错，这里不重复
        }
        val hits = ArrayList<String>()
        fun walk(node: Any?, path: String) {
            when (node) {
                is org.json.JSONObject -> {
                    val names = node.names() ?: return
                    for (i in 0 until names.length()) {
                        val key = names.getString(i)
                        val child = node.get(key)
                        if (key in CAPTURE_KEYS && child is org.json.JSONObject) {
                            val capNames = child.names() ?: continue
                            for (j in 0 until capNames.length()) {
                                val capId = capNames.getString(j)
                                if (child.get(capId) !is org.json.JSONObject) {
                                    hits.add("$path/$key.$capId")
                                }
                            }
                        } else {
                            walk(child, "$path/$key")
                        }
                    }
                }
                is org.json.JSONArray -> {
                    for (i in 0 until node.length()) {
                        walk(node.get(i), "$path[$i]")
                    }
                }
            }
        }
        walk(json, "")
        if (hits.isNotEmpty()) {
            Logger.w(
                TAG,
                "语法资产 $scopeName ($fileName) 的 captures 含裸字符串条目，tm4e 编译时会抛 ClassCastException 并使该语言完全无高亮，请修正: ${hits.take(5)}"
            )
        }
    }

    private val CAPTURE_KEYS = setOf("captures", "beginCaptures", "endCaptures", "whileCaptures")

    private fun readScopeName(bytes: ByteArray): String? {
        val text = try {
            bytes.decodeToString()
        } catch (e: Exception) {
            return null
        }
        return when {
            text.trimStart().startsWith("{") -> try {
                org.json.JSONObject(text).optString("scopeName").takeIf { it.isNotEmpty() }
            } catch (e: Exception) {
                null
            }
            text.contains("<plist") || text.contains("<?xml") -> {
                Regex("""<key>\s*scopeName\s*</key>\s*<string>\s*([^<]+?)\s*</string>""")
                    .find(text)
                    ?.groupValues?.get(1)
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
            }
            else -> null
        }
    }

    /**
     * 判断是否为有效的语法文件
     */
    private fun isValidGrammarFile(fileName: String): Boolean {
        val lowerName = fileName.lowercase()
        // 注意：lowerName 已转小写，后缀字面量必须也用小写比对。
        // 旧代码比对混合大小写 ".tmLanguage"，导致所有 plist 语法（如 Kotlin.tmLanguage）
        // 被误判为非法而跳过 —— kotlin 因此从未注册、.kt 没有高亮。
        return lowerName.endsWith(".json") ||
                lowerName.endsWith(".tmlanguage") ||
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
            "markdown-math" to "text.html.markdown.math",
            "objective-c" to "source.objc",
            "perl" to "source.perl",
            "php" to "source.php",
            "powershell" to "source.powershell",
            "prompt-basics" to "source.prompt",
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
            "typescript-basics" to "source.tsx",
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
                    // 6.2.0 批次26：VSCode 主题多为 JSONC（// 注释 + 尾逗号），严格 Gson 会抛
                    // MalformedJsonException（9 个主题因此加载失败、每次启动刷 117 条告警）。
                    // 这里先把注释与尾逗号清掉转成严格 JSON 再交给 sora 解析。
                    val raw = assets.open(fullPath).use { it.readBytes().toString(Charsets.UTF_8) }
                    val cleaned = TextMateThemes.sanitizeJsonc(raw)
                    val inputStream = ByteArrayInputStream(cleaned.toByteArray(Charsets.UTF_8))

                    val themeSource = IThemeSource.fromInputStream(
                        inputStream,
                        fullPath,
                        null
                    )
                    val themeModel = ThemeModel(themeSource, themeName)
                    // 6.2.0 批次66：深浅色改为动态检测（读主题自身的 type / 背景亮度），
                    // 不再查代码里硬编码的深色名单 —— 漏登记的主题（如 nord）也能判对。
                    themeModel.isDark = TextMateThemes.detect(cleaned, themeName)

                    themeRegistry.loadTheme(themeModel, false)
                    // 批次66追加：登记 stem 与 JSON name，供「语法/主题文件管理」导入查重
                    trackLoadedTheme(themeName, cleaned)
                    loadedCount++
                    Logger.d(TAG, Str.get(R.string.theme_loaded_themename, themeName))
                } catch (e: Exception) {
                    Logger.w(TAG, Str.get(R.string.theme_load_failed_filename_e_message, fileName, e.message))
                }
            }
            
            // 批次66追加：用户导入的私有主题 —— 在内置之后加载，全部就绪再定默认主题。
            // （导入侧已按 stem / JSON name 查重，这里不会再撞 sora 的去重分支）
            loadedCount += loadUserThemes()

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

    /**
     * 6.2.0 批次66：读取 `assets/textmate/languages.json` 清单，产出「后缀 → scopeName」
     * 兜底表与选择器附加项（见 companion 的 parseLanguageManifest）。自捕异常：清单只是
     * 兜底增强，读失败不应影响语法/主题加载。
     */
    private fun loadLanguageManifest() {
        runCatching {
            val (extMap, extras) = parseLanguageManifest(assets)
            manifestScopeByExt = extMap
            manifestExtras = extras
            Logger.d(
                TAG,
                Str.get(R.string.lang_manifest_loaded_entries_rules, extras.size, extMap.size)
            )
        }.onFailure {
            Logger.w(TAG, Str.get(R.string.lang_manifest_load_failed, it.message))
        }
    }

    // ==================== 批次66追加：私有目录里的语法 / 主题 ====================

    /**
     * 把一个**用户导入的私有主题**注册进 ThemeRegistry（启动加载与运行时导入共用）。
     *
     * 与内置主题的区别：文件在 `filesDir/textmate/themes`，可被用户删除；
     * 查重键同 sora（stem 与 JSON `name`），任一撞名即拒绝，避免 sora 的
     * 「已存在 → setTheme(旧主题)」副作用把当前主题切走。
     *
     * @return 是否成功注册（文件已存在但本次未注册 = 此前已注册过，改动要重启才生效）
     */
    fun registerUserTheme(name: String, file: File): Boolean {
        if (!file.isFile) return false
        if (name in loadedThemeStems) return false
        return try {
            val raw = file.readText(Charsets.UTF_8)
            val cleaned = TextMateThemes.sanitizeJsonc(raw)
            val jsonName = runCatching { org.json.JSONObject(cleaned).optString("name") }
                .getOrDefault("")
            if (jsonName.isNotEmpty() && jsonName in loadedThemeRawNames) return false
            val input = ByteArrayInputStream(cleaned.toByteArray(Charsets.UTF_8))
            val themeSource = IThemeSource.fromInputStream(input, file.absolutePath, null)
            val model = ThemeModel(themeSource, name)
            model.isDark = TextMateThemes.detect(cleaned, name)
            ThemeRegistry.getInstance().loadTheme(model, false)
            trackLoadedTheme(name, cleaned)
            Logger.i(TAG, Str.get(R.string.theme_loaded_themename, name))
            true
        } catch (e: Exception) {
            Logger.w(
                TAG,
                Str.get(R.string.theme_load_failed_filename_e_message, file.name, e.message)
            )
            false
        }
    }

    /**
     * 把一个**用户导入的私有语法**注册进 GrammarRegistry（启动加载与运行时导入共用）。
     *
     * 与内置语法的差异：name 用绝对文件路径（全局唯一，sora 按 name 去重）、
     * 不挂 language-configuration（它由 FileProvider 按 assets 路径解析，私有路径不可用）、
     * scope 必须由文件自身声明（导入时已校验）。
     *
     * @return 注册成功的 scopeName；失败返回 null
     */
    fun registerUserGrammar(file: File): String? {
        return try {
            if (!file.isFile) return null
            val bytes = file.readBytes()
            if (bytes.isEmpty()) return null
            // 资产损坏检测：历史上有语法文件内容是 "404: Not Found"（LFS 占位）
            if (bytes.decodeToString(0, minOf(bytes.size, 64)).contains("404: Not Found")) return null
            val scopeName = readScopeName(bytes) ?: return null
            if (scopeName.contains(".injection.")) return null
            val path = file.absolutePath
            // 同一路径已注册（重复导入/重启加载已建过）→ 保留现有实例，登记照旧
            if (grammarAssetPaths[scopeName] == path &&
                GrammarRegistry.getInstance().findGrammar(scopeName) != null
            ) {
                addOrReplaceUserGrammar(UserGrammar(file.nameWithoutExtension, scopeName, path))
                return scopeName
            }
            val grammarDef = object : GrammarDefinition {
                override fun getName(): String = path
                override fun getLanguageConfiguration(): String? = null
                override fun getScopeName(): String = scopeName
                override fun getGrammar(): IGrammarSource =
                    IGrammarSource.fromInputStream(bytes.inputStream(), path, null)
            }
            GrammarRegistry.getInstance().loadGrammar(grammarDef) ?: return null
            grammarAssetPaths[scopeName] = path
            addOrReplaceUserGrammar(UserGrammar(file.nameWithoutExtension, scopeName, path))
            Logger.i(
                TAG,
                Str.get(R.string.user_grammar_loaded_name_scope, file.nameWithoutExtension, scopeName)
            )
            scopeName
        } catch (e: Exception) {
            Logger.e(
                TAG,
                Str.get(R.string.failed_to_load_grammar_langdir, file.absolutePath),
                e
            )
            null
        }
    }

    /** 批次66追加：启动时加载 `filesDir/textmate/` 下用户导入的全部语法。 */
    private fun loadUserGrammars(): Int {
        val root = File(filesDir, "textmate")
        val dirs = root.listFiles { f -> f.isDirectory && f.name != "themes" } ?: return 0
        var count = 0
        for (dir in dirs) {
            val files = File(dir, "syntaxes").listFiles { f -> f.isFile } ?: continue
            for (f in files) {
                if (!isValidGrammarFile(f.name)) continue
                if (f.name.contains("injection", ignoreCase = true)) continue
                if (registerUserGrammar(f) != null) count++
            }
        }
        return count
    }

    /** 批次66追加：启动时加载 `filesDir/textmate/themes/` 下用户导入的全部主题。 */
    private fun loadUserThemes(): Int {
        val dir = File(filesDir, "textmate/themes")
        val files = dir.listFiles { f -> f.isFile && f.name.endsWith(".json") } ?: return 0
        var count = 0
        for (f in files.sortedBy { it.name }) {
            if (registerUserTheme(f.name.removeSuffix(".json"), f)) count++
        }
        return count
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