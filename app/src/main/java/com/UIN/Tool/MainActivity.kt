// app/src/main/java/com/UIN/Tool/MainActivity.kt
package com.UIN.Tool

import com.UIN.Tool.utils.Str
import android.app.ActivityManager
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer

import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selectableGroup
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.UIN.Tool.R
import com.UIN.Tool.core.di.ServiceLocator
import com.UIN.Tool.data.local.PreferenceManager
import com.UIN.Tool.log.Logger
import com.UIN.Tool.plugin.PluginHostActivity
import com.UIN.Tool.plugin.PluginManager
import com.UIN.Tool.ui.components.unified.*
import com.UIN.Tool.ui.components.UpdateDialog
import com.UIN.Tool.ui.components.unified.UnifiedAlertDialog
import com.UIN.Tool.ui.components.unified.UnifiedDialogTextButton
import com.UIN.Tool.ui.screen.log.LogViewerActivity
import com.UIN.Tool.ui.screen.dev.DevScreen
import com.UIN.Tool.ui.screen.files.FileDeepLink
import com.UIN.Tool.ui.screen.files.FileDeepLinkBus
import com.UIN.Tool.ui.screen.files.FilesScreen
import com.UIN.Tool.ui.screen.manage.ManageScreen
import com.UIN.Tool.ui.screen.repo.RepoScreen
import com.UIN.Tool.ui.screen.tools.ToolsScreen
import com.UIN.Tool.ui.theme.UINToolTheme
import com.UIN.Tool.ui.theme.LaunchScreenBackground
import androidx.lifecycle.viewmodel.compose.viewModel
import com.UIN.Tool.ui.viewmodel.UpdateViewModel
import com.UIN.Tool.ui.theme.dynamicColor
import com.UIN.Tool.ui.theme.AppColors
import com.UIN.Tool.utils.AppLog
import com.UIN.Tool.constants.AppConstants as Constants
import com.UIN.Tool.utils.CrashLogUtils
import com.UIN.Tool.utils.formatFileSize
import com.UIN.Tool.utils.UIConfig
import java.io.File
import com.UIN.Tool.utils.AppToast

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
        private const val EXTRA_SELECTED_TAB = "selected_tab"

        /**
         * m.txt 第 12 条：这条 intent 来自「后台保活」常驻通知。
         *
         * 点通知只该把用户带回**上次停留的页面** —— 不能走 launcher 那条「回到默认
         * 启动页」的逻辑（默认页就是插件页，用户会以为应用自己跳走了）。MainActivity
         * 见到这个 extra 就只收掉插件浮层，不动当前 tab。
         */
        const val EXTRA_FROM_KEEP_ALIVE = "from_keep_alive"
        private const val EXTRA_CHECK_UPDATE = "check_update"
        private const val EXTRA_PLUGIN_ID = "plugin_id"
        private const val EXTRA_OPEN_PLUGIN = "open_plugin"
    }

    private val pluginManager: PluginManager by lazy { ServiceLocator.getPluginManager() }
    
    private lateinit var preferenceManager: PreferenceManager
    // 6.3.0 M1.5：tab 从「下标」改为「稳定 id」（dev/tools/files/repo/manage）——
    // 侧栏「导航」可调底部顺序，改的是排序不是 id，所以深链/快捷方式/恢复现场都按 id 走。
    // 这里先给默认值，onCreate 里再按设置里的「默认启动页」覆写。
    private var selectedTab by mutableStateOf(UIConfig.NAV_DEFAULT_TAB)

    /** 每次外部 intent 导航（onNewIntent）自增，驱动已有组合响应，避免重建 setContent */
    private var tabSignal by mutableStateOf(0)
    
    // ✅ 使用 mutableStateOf 管理退出对话框状态
    private var showExitDialog by mutableStateOf(false)

    private val manageStorageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (hasManageStoragePermission()) {
            AppLog.success(TAG, Str.get(R.string.storage_permission_granted))
            checkAndSetWorkFolder()
        } else {
            AppLog.w(TAG, Str.get(R.string.storage_permission_denied))
            AppToast.showLong(this, Str.get(R.string.storage_permission_is_required_for))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val hasCrashed = CrashLogUtils.shouldNavigateToLogs(this)
        if (hasCrashed) {
            // 留痕：这行没出现 = 崩溃标记没写上或已被清掉；出现了却仍没进日志页
            // = 500ms 后那次 startActivity 失败（它的 catch 里本来就记着）
            AppLog.i(TAG, "crash flag present -> will open log page")
            // 6.2.0 批次42：闪退后原本跳「开发工具」——那页内嵌的运行日志区块早在批次37
            // 就已移除，崩溃内容其实在日志页面（LogViewerScreen）。改跳 LogViewerActivity。
            // 标记不在跳转前提前清除：留给日志页读出后显示「发生异常」提示条再自行清除
            // （LogViewerScreen 的 LaunchedEffect 会清）；跳转失败时在 catch 里兜底清，
            // 避免标记残留导致每次启动都往日志页跳。
            // B-15：原 Handler.postDelayed 未在 onDestroy 移除，Activity 已销毁仍会 startActivity；
            // 改 lifecycleScope（随销毁自动取消）并补 isDestroyed/isFinishing 双保险
            lifecycleScope.launch {
                delay(500)
                if (isDestroyed || isFinishing) return@launch
                try {
                    startActivity(Intent(this@MainActivity, LogViewerActivity::class.java).apply {
                        putExtra("auto_open", true)
                        // SINGLE_TOP：崩溃处理器可能已经把日志页顶在最上面了（f9），
                        // 这里再来一次必须复用那个实例，不能叠出第二个日志页
                        addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    })
                } catch (e: Exception) {
                    CrashLogUtils.clearNavigateFlag(this@MainActivity)
                    AppLog.e(TAG, Str.get(R.string.failed_to_open_log_page), e)
                }
            }
        }

        preferenceManager = PreferenceManager(this)

        // B-17：UIConfig.init 在 UinApplication 里失败只记日志就被吞了，
        // 这里直接 getInstance() 会在 onResume/onCreate 抛 IllegalStateException（启动即崩且无归因）
        try {
            UIConfig.init(this)
        } catch (e: Exception) {
            AppLog.e(TAG, Str.get(R.string.uiconfig_initialized), e)
        }
        uiConfigOrNull()?.let { applyTheme(it) }

        // 主界面首帧之前保持与开屏一致的静态画面（图标 + 名称 + 版本），
        // 这样从 SplashActivity 直接跳过来时，等待 Compose 首帧期间也不是白屏。
        // u.txt 第 4 条：配置变更（深浅色 / 语言）导致的重建不再重放开屏 ——
        // 只铺一层当前主题背景色，避免「黑屏一瞬」的观感。
        if (savedInstanceState == null) {
            LaunchScreenBackground.apply(this)
        } else {
            LaunchScreenBackground.applyPlainBackground(this)
        }

        Logger.init(Constants.LOG_DIR)
        AppLog.i(TAG, "══════════════════════════════════════════════════")
        AppLog.i(TAG, Str.get(R.string.app_launch))
        AppLog.param(TAG, Str.get(R.string.version), getVersionCode().toString())
        AppLog.param(TAG, Str.get(R.string.android_version), "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        AppLog.param(TAG, Str.get(R.string.device), "${Build.MANUFACTURER} ${Build.MODEL}")

        val pluginId = intent.getStringExtra(EXTRA_PLUGIN_ID)
        val openPlugin = intent.getBooleanExtra(EXTRA_OPEN_PLUGIN, false)
        
        AppLog.d(TAG, "onCreate - pluginId: $pluginId, openPlugin: $openPlugin")
        
        val isFromLauncher = intent.action == Intent.ACTION_MAIN &&
                             intent.hasCategory(Intent.CATEGORY_LAUNCHER)
        
        if (isFromLauncher) {
            AppLog.i(TAG, Str.get(R.string.launched_from_desktop_icon_closing_all))
            closeAllPluginActivities()
        }
        
        if (!pluginId.isNullOrEmpty() && openPlugin) {
            AppLog.i(TAG, Str.get(R.string.opening_plugin_from_shortcut_plugini, pluginId))
            openPluginDirectly(pluginId)
            return
        }

        // 6.3.0 M1.5：先按设置里的「默认启动页」，随后 shortcut extra / 深链再逐层覆盖
        // m.txt 第 12 条：保活通知冷启动（Activity 已被销毁、没有 saved state 可恢复）时，
        // 直接用持久化的「上次停留页」，否则会落到默认启动页（默认是插件页）。
        val fromKeepAlive = intent.getBooleanExtra(EXTRA_FROM_KEEP_ALIVE, false)
        selectedTab = if (fromKeepAlive) readLastTab(this) ?: readDefaultTab() else readDefaultTab()
        if (fromKeepAlive) AppLog.i(TAG, "onCreate: from keep-alive, restore last tab=$selectedTab")
        handleShortcutIntent(intent)
        // B-19：深链覆盖 shortcut extra 指定的 tab
        val deepLinked = applyDeepLink(intent)

        // f1「后台保活回来落在插件页、而不是上次停留页」：进程被杀后重建，MainContent 里
        // rememberSaveable 存着「上次停留页」，但**首帧**那次 LaunchedEffect 会拿 initialTab
        // 把它覆盖掉（= 设置里的默认启动页）。只有 intent 确实点了名（快捷方式 / 深链 /
        // 检查更新）才把 tabSignal 置成非零、允许首帧覆盖；否则恢复值必须赢。
        if (deepLinked ||
            intent.getBooleanExtra(EXTRA_CHECK_UPDATE, false) ||
            resolveTabId(intent.getStringExtra(EXTRA_SELECTED_TAB)) != null
        ) {
            tabSignal = 1
        }

        setContent {
            UINToolTheme {
                // F-16：统一长任务宿主挂根部，浮于 MainContent 之上（模态遮罩）
                Box(modifier = Modifier.fillMaxSize()) {
                    MainContent(
                        initialTab = selectedTab,
                        tabSignal = tabSignal,
                        checkUpdate = intent.getBooleanExtra(EXTRA_CHECK_UPDATE, false),
                        showExitDialog = showExitDialog,
                        onBackRequest = {
                            AppLog.d(TAG, "back pressed")
                            showExitDialog = true
                        },
                        onExitConfirm = { 
                            showExitDialog = false
                            com.UIN.Tool.plugin.SharedSupervisor.onHostExit()
                            finishAffinity()
                        },
                        onExitDismiss = { 
                            showExitDialog = false
                        }
                    )
                    com.UIN.Tool.ui.components.LongTaskHost()
                }
            }

            // 配置变更时即时刷新状态栏/导航栏/任务描述颜色
            LaunchedEffect(Unit) {
                UIConfig.configVersion.collect {
                    if (UIConfig.isInitialized()) {
                        applyTheme(UIConfig.getInstance())
                        // E-09（方案B）：主题设置变化即重挂夜间模式，AppCompat 会
                        // 自动重建已启动的界面（XML/Compose 双侧同源切换）
                        UinApplication.getInstance().applyNightMode()
                    }
                }
            }
        }

        // B-14：原 Handler.postDelayed 后直接 AlertDialog.show()，销毁后弹窗 WindowLeaked
        lifecycleScope.launch {
            delay(1000)
            if (isDestroyed || isFinishing) return@launch
            checkPermissions()
        }

        // 实体 Termux 模式：后台预热共享 supervisor（容器只初始化一次，后续插件启动更快）
        com.UIN.Tool.plugin.SharedSupervisor.prewarm(applicationContext)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        AppLog.d(TAG, "onNewIntent")

        // m.txt 第 12 条：后台保活通知 → 只回到上次停留页，不按默认启动页重置。
        // 必须放在 launcher 分支之前：这条 intent 也带 MAIN/LAUNCHER（为了在冷启动时
        // 直接拉起 MainActivity 而不是 Splash），不先拦就会被下面改成默认页。
        if (intent.getBooleanExtra(EXTRA_FROM_KEEP_ALIVE, false)) {
            AppLog.i(TAG, "onNewIntent: from keep-alive, keep current tab")
            closeAllPluginActivities()
            showExitDialog = false
            return
        }

        val pluginId = intent.getStringExtra(EXTRA_PLUGIN_ID)
        val openPlugin = intent.getBooleanExtra(EXTRA_OPEN_PLUGIN, false)
        
        val isFromLauncher = intent.action == Intent.ACTION_MAIN &&
                             intent.hasCategory(Intent.CATEGORY_LAUNCHER)
        
        if (isFromLauncher) {
            AppLog.i(TAG, Str.get(R.string.onnewintent_launched_from_desktop_icon))
            closeAllPluginActivities()

            val hasExplicitTarget = !pluginId.isNullOrEmpty() ||
                intent.getStringExtra(EXTRA_SELECTED_TAB) != null ||
                intent.getBooleanExtra(EXTRA_CHECK_UPDATE, false) ||
                intent.data != null

            // l.txt 第 4 条：桌面图标 / 通知「打开应用」= 一次**按设置启动**。
            // 原先这里保持当前页，用户改完「默认启动页」从桌面点回来仍是旧页，看起来
            // 就是「改了没生效」。现在无明确目标的 launcher 意图统一回到默认启动页。
            if (!hasExplicitTarget) {
                showExitDialog = false
                selectedTab = readDefaultTab()
                AppLog.i(TAG, "onNewIntent: launcher without target, go to default tab")
                // tabSignal 驱动已有组合切页；MainContent 的首帧恢复逻辑不会覆盖它。
                tabSignal++
                return
            }
        }
        
        if (!pluginId.isNullOrEmpty() && openPlugin) {
            AppLog.i(TAG, Str.get(R.string.onnewintent_opening_plugin_from_shortcut, pluginId))
            openPluginDirectly(pluginId)
            return
        }
        
        handleShortcutIntent(intent)
        selectedTab = getTabFromIntent(intent)
        applyDeepLink(intent)
        showExitDialog = false
        // 通过 tabSignal 驱动已有组合响应导航，不再重建 setContent（避免丢失组合内状态）
        tabSignal++
    }

    /**
     * B-16：原先方法体为空，但两处日志都写着「关闭所有插件页面」——实际什么都没关。
     * PluginHostActivity 用 documentLaunchMode=always 各自成 task，这里按 baseIntent/topActivity
     * 的组件名把插件宿主任务逐个 finishAndRemoveTask。
     */
    private fun closeAllPluginActivities() {
        try {
            val am = getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager
            var closed = 0
            for (task in am.appTasks) {
                // compileSdk 37：AppTask.taskInfo 变成可空（SDK 35 起 getTaskInfo 标了 @Nullable）
                val base = task.taskInfo?.baseIntent?.component?.className
                val top = task.taskInfo?.topActivity?.className
                if (base?.contains("PluginHostActivity") == true || top?.contains("PluginHostActivity") == true) {
                    task.finishAndRemoveTask()
                    closed++
                }
            }
            if (closed > 0) AppLog.i(TAG, "closed $closed plugin host task(s)")
        } catch (e: Exception) {
            AppLog.e(TAG, "closeAllPluginActivities failed", e)
        }
    }

    /** B-17：UIConfig 未初始化（Application 段 init 失败被吞）时返回 null，调用方降级跳过主题应用 */
    private fun uiConfigOrNull(): UIConfig? =
        if (UIConfig.isInitialized()) UIConfig.getInstance() else null

    private fun openPluginDirectly(pluginId: String) {
        AppLog.i(TAG, Str.get(R.string.opening_plugin_directly_pluginid, pluginId))
        try {
            val info = pluginManager.getPluginInfo(pluginId)
            
            if (info == null) {
                AppLog.w(TAG, Str.get(R.string.plugin_does_not_exist_pluginid_navig, pluginId))
                finish()
                startActivity(Intent(this, MainActivity::class.java))
                return
            }
            
            AppLog.i(TAG, Str.get(R.string.plugin_exists_info_name, info.name))
            
            val intent = Intent(this, PluginHostActivity::class.java).apply {
                putExtra(PluginHostActivity.EXTRA_PLUGIN_ID, pluginId)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
            AppLog.success(TAG, Str.get(R.string.plugin_started_successfully_info_nam, info.name))
            
            finish()
            
        } catch (e: Exception) {
            AppLog.e(TAG, Str.get(R.string.failed_to_start_plugin_e_message, e.message), e)
            finish()
            startActivity(Intent(this, MainActivity::class.java))
        }
    }

    private fun applyTheme(uiConfig: UIConfig) {
        val dark = uiConfig.shouldUseDarkTheme(uiConfig.isSystemDark())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            window.statusBarColor = uiConfig.getColorForMode("status_bar", dark)
            window.navigationBarColor = uiConfig.getColorForMode("navigation_bar", dark)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val taskColor = uiConfig.getColorForMode("primary", dark) or 0xFF000000.toInt()
            setTaskDescription(
                ActivityManager.TaskDescription(
                    getString(R.string.app_name),
                    null,
                    taskColor
                )
            )
        }

        // o.txt 第 15 条：主题色可能刚被改过 —— 快捷方式图标跟着换色。
        // 内部按「颜色没变且条目齐全」直接返回，不会每次都发一次动态快捷方式。
        com.UIN.Tool.utils.ThemeShortcuts.sync(this)
    }

    /**
     * B-19：Manifest 声明了 uin://tools 与 uin://check_update 两个深链，但代码从未读 intent.data，
     * 浏览器点开等于没反应。这里按 host 路由到对应 tab（深链优先级高于 shortcut extra）。
     * 返回 true 表示本次 intent 是深链。
     *
     * 6.3.0 M5 §4.16：追加第三个 host `files` —— 它比前两个多带参数，
     * 切完 tab 还要**跳目录 + 高亮文件**，所以额外投一份请求到 [FileDeepLinkBus]。
     */
    private fun applyDeepLink(intent: Intent): Boolean {
        val data = intent.data ?: return false
        if (data.scheme != "uin") return false
        return when (data.host) {
            "tools" -> {
                selectedTab = "tools"
                AppLog.i(TAG, "deep link uin://tools -> tab=tools")
                true
            }
            "check_update" -> {
                selectedTab = "manage"
                AppLog.i(TAG, "deep link uin://check_update -> tab=manage(check update)")
                true
            }
            "files" -> {
                selectedTab = "files"
                // 4.16：path=目录、file=文件绝对路径。缺 path 就用 file 的父目录兜底，
                // 两个都缺等价 uin://files（只切 tab）。这里的拆分是**唯一**入口：
                // 下游 FilesScreen 只认「目录 + 可选文件」，不再各自解 URI。
                val rawPath = data.getQueryParameter("path").orEmpty()
                val rawFile = data.getQueryParameter("file").orEmpty()
                // i.txt 第 6 条：`archive=<包路径>` = 直接挂载该归档并浏览（APK 信息页用）。
                val rawArchive = data.getQueryParameter("archive").orEmpty()
                val dir = if (rawPath.isNotEmpty()) {
                    File(rawPath).absolutePath
                } else {
                    rawFile.substringBeforeLast('/', "")
                }
                FileDeepLinkBus.post(
                    FileDeepLink(
                        dirPath = dir,
                        filePath = rawFile.ifEmpty { null },
                        mountArchive = rawArchive.ifEmpty { null }
                    )
                )
                AppLog.i(TAG, "deep link uin://files -> tab=files path=$dir file=$rawFile")
                true
            }
            else -> {
                AppLog.w(TAG, "unknown deep link: $data")
                false
            }
        }
    }

    private fun handleShortcutIntent(intent: Intent) {
        if (intent == null) return
        
        // B-18：原直接打印 intent.extras（可能含文件 URI/插件参数等），日志会落共享存储；
        // 改为只打印参与路由的白名单字段
        AppLog.d(TAG, "handleShortcutIntent - data=${intent.data} action=${intent.action}")
        
        val selectedTab = intent.getStringExtra(EXTRA_SELECTED_TAB)
        val checkUpdate = intent.getBooleanExtra(EXTRA_CHECK_UPDATE, false)
        
        AppLog.d(TAG, "selectedTab: $selectedTab, checkUpdate: $checkUpdate")
        
        if (checkUpdate) {
            this.selectedTab = "manage"
        } else {
            // 6.3.0 M1.5：没带 tab extra 就保持原值（onCreate 已按「默认启动页」初始化）；
            // 非法 id 同样忽略——不再像旧版那样硬回落到插件页
            resolveTabId(selectedTab)?.let { this.selectedTab = it }
        }
    }

    /** intent 里的 tab extra → 合法 id；缺失或非法返回 null，由调用方决定是否覆盖当前值 */
    private fun resolveTabId(id: String?): String? =
        if (id != null && UIConfig.NAV_TAB_IDS.contains(id)) id else null

    /** 本次 intent 生效的 tab：shortcut/深链指定优先，否则用设置里的「默认启动页」 */
    private fun getTabFromIntent(intent: Intent): String {
        val selectedTab = intent.getStringExtra(EXTRA_SELECTED_TAB)
        val checkUpdate = intent.getBooleanExtra(EXTRA_CHECK_UPDATE, false)
        return if (checkUpdate) "manage" else resolveTabId(selectedTab) ?: readDefaultTab()
    }

    /** 设置未初始化（极早期或异常路径）时回落到决策 2 的默认值：插件页 */
    private fun readDefaultTab(): String =
        uiConfigOrNull()?.getDefaultTab() ?: UIConfig.NAV_DEFAULT_TAB

    private fun getVersionCode(): Int {
        return try {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode.toInt()
            } else {
                packageInfo.versionCode
            }
        } catch (e: Exception) {
            AppLog.e(TAG, Str.get(R.string.failed_to_get_version_code), e)
            1
        }
    }

    private fun checkPermissions() {
        AppLog.enter(TAG, "checkPermissions")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!hasManageStoragePermission()) {
                AppLog.w(TAG, Str.get(R.string.all_files_access_permission_required))
                showPermissionDialog()
            } else {
                AppLog.success(TAG, Str.get(R.string.all_files_access_permission_granted))
                checkAndSetWorkFolder()
            }
        } else {
            if (ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                AppLog.w(TAG, Str.get(R.string.storage_permission_required))
                showPermissionDialog()
            } else {
                AppLog.success(TAG, Str.get(R.string.storage_permission_granted))
                checkAndSetWorkFolder()
            }
        }

        AppLog.exit(TAG, "checkPermissions", System.currentTimeMillis())
    }

    private fun hasManageStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }
    }

    private fun showPermissionDialog() {
        if (isDestroyed || isFinishing) return
        AppLog.d(TAG, Str.get(R.string.showing_permission_request_dialog))
        AlertDialog.Builder(this)
            .setTitle(Str.get(R.string.storage_permission_required))
            .setMessage(Str.get(R.string.uin_tool_needs_access_to_all_files_to))
            .setPositiveButton(Str.get(R.string.grant)) { _, _ ->
                AppLog.action(TAG, Str.get(R.string.user_tapped), Str.get(R.string.grant))
                requestManageStoragePermission()
            }
            .setNegativeButton(Str.get(R.string.exit)) { _, _ ->
                AppLog.action(TAG, Str.get(R.string.user_tapped), Str.get(R.string.exit))
                finishAffinity()
            }
            .setCancelable(false)
            .show()
    }

    private fun requestManageStoragePermission() {
        AppLog.i(TAG, Str.get(R.string.request_storage_permission))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
            intent.data = Uri.parse("package:$packageName")
            manageStorageLauncher.launch(intent)
        } else {
            requestPermissions(
                arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE),
                100
            )
        }
    }

    private fun checkAndSetWorkFolder() {
        AppLog.enter(TAG, "checkAndSetWorkFolder")
        try {
            var workFolder = preferenceManager.getWorkFolder()

            AppLog.param(TAG, "workFolder", workFolder)

            if (workFolder.isEmpty()) {
                val defaultFolder = File(Constants.WORK_DIR)
                if (!defaultFolder.exists()) {
                    defaultFolder.mkdirs()
                    AppLog.i(TAG, Str.get(R.string.creating_work_directory_defaultfolde, defaultFolder.absolutePath))
                }
                preferenceManager.setWorkFolder(defaultFolder.absolutePath)
                AppLog.success(TAG, Str.get(R.string.setting_default_work_directory_defau, defaultFolder.absolutePath))
                AppToast.showLong(this, Str.get(R.string.work_directory_defaultfolder_absolut, defaultFolder.absolutePath))
            } else {
                val folder = File(workFolder)
                if (!folder.exists()) {
                    folder.mkdirs()
                    AppLog.i(TAG, Str.get(R.string.creating_work_directory_folder_absol, folder.absolutePath))
                }
                AppLog.i(TAG, Str.get(R.string.using_existing_work_directory_workfo, workFolder))
            }

            pluginManager.refreshPlugins()

        } catch (e: Exception) {
            AppLog.e(TAG, Str.get(R.string.failed_to_create_work_directory_e_me, e.message), e)
            CrashLogUtils.logException(this, e, "MainActivity")
            AppToast.show(this, Str.get(R.string.failed_to_create_work_directory_e_me, e.message))
        }
        AppLog.exit(TAG, "checkAndSetWorkFolder", System.currentTimeMillis())
    }

    override fun onResume() {
        super.onResume()
        AppLog.d(TAG, "onResume")

        // B-17：init 失败被吞时这里原先必抛 IllegalStateException
        uiConfigOrNull()?.let { uiConfig ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val dark = uiConfig.shouldUseDarkTheme(uiConfig.isSystemDark())
                window.statusBarColor = uiConfig.getColorForMode("status_bar", dark)
                window.navigationBarColor = uiConfig.getColorForMode("navigation_bar", dark)
            }
        }

        pluginManager.refreshPlugins()
        // C-03：登记前台宿主，供插件权限弹窗解析 Activity（PluginManager 只持有 applicationContext）
        pluginManager.onHostActivityResumed(this)
    }

    override fun onPause() {
        super.onPause()
        AppLog.d(TAG, "onPause")
    }

    override fun onDestroy() {
        super.onDestroy()
        // B-13/C-21：只有真正退出（而非旋转/系统重建）才通知后端宿主退出，
        // 否则配置变更会误杀 supervisor 与全部插件后端。
        if (isFinishing && !isChangingConfigurations) {
            com.UIN.Tool.plugin.SharedSupervisor.onHostExit()
            AppLog.i(TAG, Str.get(R.string.app_exit))
        }
        pluginManager.onHostActivityDestroyed(this)
    }

    @Deprecated("Using Activity Result API")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        AppLog.d(TAG, "onRequestPermissionsResult, requestCode: $requestCode")
        // C-03：插件声明权限的真实回执（PluginPermissionDetail / PluginManage 在本 Activity 内发起）
        if (pluginManager.dispatchPermissionResult(requestCode, grantResults)) {
            return
        }
        if (requestCode == 100) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                AppLog.success(TAG, Str.get(R.string.storage_permission_granted))
                checkAndSetWorkFolder()
            } else {
                AppLog.w(TAG, Str.get(R.string.storage_permission_denied))
                AppToast.show(this, Str.get(R.string.storage_permission_is_required_for))
                finishAffinity()
            }
        }
    }
}

// ==================== MainContent ====================

/** m.txt 第 12 条：上次停留页的持久化位置（保活通知冷启动时据此恢复）。 */
private const val PREF_MAIN_STATE = "main_state"
private const val KEY_LAST_TAB = "last_tab"

/** 读「上次停留页」；文件不存在或值非法时返回 null，调用方回落到默认启动页。 */
private fun readLastTab(context: Context): String? =
    try {
        context.getSharedPreferences(PREF_MAIN_STATE, Context.MODE_PRIVATE)
            .getString(KEY_LAST_TAB, null)
            ?.takeIf { TAB_SPECS.containsKey(it) }
    } catch (e: Exception) {
        null
    }

/** 底部导航的一项：id 恒定不变，顺序由 UIConfig 的 nav.tabOrder 决定（M1.5） */
private data class TabSpec(
    val id: String,
    val labelRes: Int,
    val iconRes: Int
)

/** 5 个 tab 的全集（id → 文案与图标）；**顺序不在这里体现**，一律读配置 */
private val TAB_SPECS = mapOf(
    "dev" to TabSpec("dev", R.string.developer, R.drawable.ic_terminal_prompt),
    "tools" to TabSpec("tools", R.string.plugins, R.drawable.ic_grid_view),
    "files" to TabSpec("files", R.string.files, R.drawable.ic_folder),
    "repo" to TabSpec("repo", R.string.repository, R.drawable.ic_repo),
    "manage" to TabSpec("manage", R.string.manage, R.drawable.ic_settings)
)

/** 读导航顺序；设置未初始化或配置异常时回落默认顺序（开发→插件→文件→仓库→管理） */
private fun readTabOrder(): List<String> =
    try {
        if (UIConfig.isInitialized()) UIConfig.getInstance().getTabOrder() else UIConfig.NAV_TAB_IDS
    } catch (_: Exception) {
        UIConfig.NAV_TAB_IDS
    }

/**
 * 从文件页全屏退回时的兜底目标 tab。
 *
 * 不能是 `files` 本身 —— 用户反馈①要求「按返回键退回主页面」，退回去还落在文件页等于原地打转；
 * 也不能抛异常中断组合，所以整段 try 起来。
 */
private fun defaultReturnTab(): String = try {
    val t = if (UIConfig.isInitialized()) {
        UIConfig.getInstance().getDefaultTab()
    } else {
        UIConfig.NAV_DEFAULT_TAB
    }
    if (t == "files") "dev" else t
} catch (_: Exception) {
    "dev"
}

@Composable
fun MainContent(
    // 6.3.0 M1.5：从下标改成稳定 id（dev/tools/files/repo/manage）；String 同样可被 rememberSaveable 保存
    initialTab: String = UIConfig.NAV_DEFAULT_TAB,
    tabSignal: Int = 0,
    checkUpdate: Boolean = false,
    showExitDialog: Boolean = false,
    onExitConfirm: () -> Unit = {},
    onExitDismiss: () -> Unit = {},
    // B-27：返回键处理随组合走，替代 Activity 上已废弃的 onBackPressed 重写
    onBackRequest: () -> Unit = {}
) {
    // B-26：用 rememberSaveable——旋转/进程重建后保留用户所在 tab，
    // 原 remember 会退回 intent 里的 initialTab（外部导航参数），一旋转就被重置
    var selectedTab by rememberSaveable { mutableStateOf(initialTab) }

    // 用户反馈①：「文件」是**独立全屏页**，进它的瞬间收起底部导航，免得那条悬浮栏
    // 压在列表上；返回键先退全屏（回到进文件页之前的 tab），才轮到退出确认。
    // returnTab 记「从哪来」，filesExpanded 记「现在是不是全屏态」—— 两件事分开存，
    // 因为 tabSignal（外部深链）可能在任意时刻把 selectedTab 拨到 files。
    var filesExpanded by rememberSaveable { mutableStateOf(initialTab == "files") }
    var returnTab by rememberSaveable {
        mutableStateOf(if (initialTab == "files") defaultReturnTab() else initialTab)
    }
    val filesActive = filesExpanded && selectedTab == "files"

    fun enterFiles() {
        // 已经在文件页就别把 returnTab 覆盖成 "files"，否则返回键会原地打转
        if (selectedTab != "files") returnTab = selectedTab
        // 重大缺陷：这里原先只置 filesExpanded，**没有把 selectedTab 拨到 "files"**。
        // 而 AnimatedContent(targetState = selectedTab) 是按 selectedTab 决定渲染哪一页的 ——
        // 结果就是「文件」按钮按下去有按压动画、页面却停在原 tab（用户反馈：点文件没反应）。
        // filesExpanded 管的是「全屏态」（收底栏 + 返回键），selectedTab 管的是「显示哪页」，
        // 两件事必须一起置位才成立。
        selectedTab = "files"
        filesExpanded = true
    }

    fun exitFiles() {
        filesExpanded = false
        selectedTab = returnTab.takeIf { it != "files" } ?: defaultReturnTab()
    }

    // 6.3.0 M1.5：顺序来自侧栏「导航 → 导航顺序」，id 与图标固定、只排序不隐藏；
    // 观察 configVersion，设置里调完顺序回到本页即刻生效
    val cfgVersion by UIConfig.configVersion.collectAsState()
    val tabs = remember(cfgVersion) { readTabOrder().mapNotNull { TAB_SPECS[it] } }

    LaunchedEffect(checkUpdate) {
        if (checkUpdate) {
            selectedTab = "manage"
        }
    }

    // 外部 intent 导航（onNewIntent）通过 tabSignal 驱动切换，保持组合内状态不丢失。
    // f1：这个效果**首帧也会跑一次** —— 无条件覆盖会把 rememberSaveable 恢复出来的
    // 「上次停留页」顶成 initialTab（默认启动页），于是后台保活回来永远落在默认页。
    // tabSignal 只在 onNewIntent 或 onCreate 显式点了名时才 > 0，用它把
    // 「外部导航要拨页」与「首帧 / 旋转 / 进程重建」分开：后两者不许覆盖。
    LaunchedEffect(tabSignal) {
        if (tabSignal > 0) {
            selectedTab = initialTab
            // 深链 uin://files 直落文件页时，全屏态要跟上，否则底栏会压在文件列表上
            if (initialTab == "files") filesExpanded = true
        }
    }

    // ==================== 静默更新检测（每天一次） ====================
    // B-79：检查/下载/弹窗状态全部下沉 UpdateViewModel，组合只负责渲染；
    // 旋转或重建时进行中的检查与下载不再丢失（原 remember { mutableStateOf } 会被重置）。
    val context = LocalContext.current
    // m.txt 第 12 条：把当前页持久化（key 与 [readLastTab] 对齐）。任务被清/进程被杀后，
    // 点保活通知冷启动仍能回到离开时那一页；正常冷启动仍按设置里的默认启动页。
    LaunchedEffect(selectedTab) {
        runCatching {
            context.getSharedPreferences(PREF_MAIN_STATE, Context.MODE_PRIVATE)
                .edit().putString(KEY_LAST_TAB, selectedTab).apply()
        }
    }
    val updateViewModel: UpdateViewModel = viewModel()
    val updateState by updateViewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        updateViewModel.checkSilentlyIfNeeded()
    }

    // 静默检测到新版本时显示更新弹窗
    val silentTarget = updateState.pending
    if (silentTarget != null) {
        UpdateDialog(
            releaseInfo = silentTarget,
            forceUpdate = updateState.force,
            onDismiss = { updateViewModel.dismissPending() },
            onDownload = { updateViewModel.startDownload(context) },
            onManualDownload = { updateViewModel.openReleasePage(context) },
            onIgnore = { updateViewModel.ignoreCurrent() }
        )
    }

    // 静默下载进度提示
    if (updateState.downloading) {
        UnifiedAlertDialog(
            onDismissRequest = { },
            title = { Text(Str.get(R.string.downloading_update)) },
            text = {
                Column {
                    LinearProgressIndicator(
                        progress = { updateState.progressPct / 100f },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = Str.get(
                            R.string.downloading_release_versionname_prog,
                            updateState.downloadingVersion,
                            updateState.progressPct.toString(),
                            formatFileSize(updateState.downloadedBytes),
                            formatFileSize(updateState.totalBytes)
                        ),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            },
            confirmButton = {
                UnifiedDialogTextButton(onClick = {
                    // B-23：原只把进度弹窗关掉，后台继续下载、下完还会拉起安装
                    updateViewModel.cancelDownload()
                }) {
                    Text(Str.get(R.string.cancel))
                }
            }
        )
    }

    // B-27：改用 Compose BackHandler（与组合同生命周期），不再重写 deprecated onBackPressed
    // 用户反馈①：文件页全屏时，返回键只负责「退回主页面」，不再一路走到退出确认。
    // 两个 handler 用 enabled 互斥，谁都不必关心注册顺序。
    BackHandler(enabled = filesActive) {
        exitFiles()
    }
    BackHandler(enabled = !filesActive) {
        onBackRequest()
    }

    // ✅ 退出对话框 - 使用 UnifiedConfirmDialog
    if (showExitDialog) {
        UnifiedConfirmDialog(
            title = Str.get(R.string.exit_app),
            message = Str.get(R.string.exit_uin_tool),
            confirmText = Str.get(R.string.exit),
            dismissText = Str.get(R.string.cancel),
            onConfirm = onExitConfirm,
            onDismiss = onExitDismiss,
            isDestructive = true
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = AppColors.pageBackground()
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                AnimatedContent(
                    targetState = selectedTab,
                    transitionSpec = {
                        // 6.3.0 M1.5：id 之间没有大小关系，改用「在当前排序里的位置」判断左右方向
                        val fromIndex = tabs.indexOfFirst { it.id == initialState }
                        val toIndex = tabs.indexOfFirst { it.id == targetState }
                        val direction = if (toIndex >= fromIndex) 1 else -1
                        (slideInHorizontally(tween(300)) { it * direction } + fadeIn(tween(300)))
                            .togetherWith(
                                slideOutHorizontally(tween(300)) { -it * direction } + fadeOut(tween(300))
                            )
                    },
                    label = "main_tab_content"
                ) { tab ->
                    when (tab) {
                        "dev" -> DevScreen()
                        "tools" -> ToolsScreen()
                        "files" -> FilesScreen(
                            onExit = { exitFiles() }
                        )
                        "repo" -> RepoScreen()
                        "manage" -> ManageScreen(checkUpdate = checkUpdate)
                        // 兜底：非法 id 理论上已被 resolveTabId 挡住，这里保证不白屏
                        else -> ToolsScreen()
                    }
                }
            }
        }

        // ==================== 底部导航（n.txt 第 16 条：整条重写）====================
        // 视觉：悬浮胶囊 + 每项自己的高亮胶囊；选中项图标放大、文字加粗。
        // o.txt 第 2 条去掉了原先底部那颗主色小圆点 —— 与高亮胶囊表达的是同一件事，
        // 叠在一起只会让底栏显得脏。按下时整项轻微收缩，松手用弹簧回位。
        val selectedColor = dynamicColor("nav_selected", MaterialTheme.colorScheme.primary)
        val unselectedColor = dynamicColor("nav_unselected", MaterialTheme.colorScheme.onSurfaceVariant)
        // p.txt 第 1 条（用户）：底栏**发灰、透明度高**。根因是玻璃模式给它铺了半透明底，
        // 底下滚动的列表透上来就成了灰雾。这里固定用不透明的主题 surface —— 底栏是
        // 常驻控件，不是浮在内容上的卡片，本来就不该跟着玻璃开关走。
        val navSurface = MaterialTheme.colorScheme.surface
        // 胶囊形（高度的一半）比卡片圆角更像「独立的一条」，也是这次重写的主要形状变化。
        val navHeight = 68.dp
        val navShape = RoundedCornerShape(navHeight / 2)
        // o.txt 第 2 条：内部圆角与外层同心 —— 外半径减去容器内边距，避免内外弧线岔开。
        val navInnerShape = RoundedCornerShape(navHeight / 2 - 6.dp)

        // 用户反馈①：文件页全屏时**整条底栏不参与组合**，而不是 alpha=0 ——
        // 后者仍在触摸区内，手指够到列表底部会被导航键吃掉一次点击。
        // p.txt 第 1 条：**不使用 Material 阴影** —— 底栏只靠不透明底色与内容分层。
        // q.txt 第 1 条（用户）：新拟态开关打开时，底栏**仍要有新拟态的凸起** ——
        // 上一版为了去掉「发灰的半透明」把阴影一并砍了，结果新拟态模式下底栏像一块
        // 贴死的色板。现在按开关分流：新拟态走 neuRaised（表面用 neuSurfaceFill，
        // 与页面同材质），普通模式维持不透明 surface + 零阴影。
        val neuNav = UIConfig.isNeumorphismEnabled()
        val navFill = if (neuNav) neuSurfaceFill() else navSurface
        if (!filesActive) Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 14.dp, vertical = 10.dp)
                // 阴影必须画在 clip 外层，否则会被圆角裁掉。
                .then(
                    if (neuNav) Modifier.neuRaised(
                        shape = navShape,
                        isDark = UIConfig.shouldUseDarkTheme(),
                        intensity = currentNeuIntensity(),
                        cornerRadius = navHeight / 2,
                        backgroundColor = navFill
                    ) else Modifier
                )
                .clip(navShape)
                .background(if (neuNav) Color.Transparent else navSurface, navShape)
                .semantics { selectableGroup() }
                .height(navHeight)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                tabs.forEach { spec ->
                    BottomNavItem(
                        spec = spec,
                        selected = selectedTab == spec.id,
                        selectedColor = selectedColor,
                        unselectedColor = unselectedColor,
                        itemShape = navInnerShape,
                        itemCornerRadius = navHeight / 2 - 6.dp,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            if (spec.id == "files") enterFiles() else selectedTab = spec.id
                        }
                    )
                }
            }
        }
    }
}


/**
 * n.txt 第 16 条：底部导航的单项。
 *
 * o.txt 第 2 条：选中的视觉层次收成两层 —— 主色淡底胶囊（面）+ 图标放大与文字加粗
 * （形）；原先底部那颗小圆点已去掉。两层都走 200~300ms 的动画，切换 tab 时是连续
 * 过渡而不是硬切；按下用弹簧收缩，松手回弹，手感更接近实体按键。
 *
 * `Modifier.weight` 必须由调用方（RowScope）传入 —— 组件内部不是 RowScope。
 */
@Composable
private fun BottomNavItem(
    spec: TabSpec,
    selected: Boolean,
    selectedColor: Color,
    unselectedColor: Color,
    itemShape: Shape,
    itemCornerRadius: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val label = Str.get(spec.labelRes)
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.9f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "nav_press"
    )
    val iconScale by animateFloatAsState(
        targetValue = if (selected) 1.12f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "nav_icon"
    )
    val pillColor by animateColorAsState(
        targetValue = if (selected) selectedColor.copy(alpha = 0.12f) else Color.Transparent,
        animationSpec = tween(durationMillis = 220),
        label = "nav_pill"
    )
    val tint by animateColorAsState(
        targetValue = if (selected) selectedColor else unselectedColor,
        animationSpec = tween(durationMillis = 220),
        label = "nav_tint"
    )

    Box(
        modifier = modifier
            .fillMaxHeight()
            .padding(horizontal = 2.dp)
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .clip(itemShape)
            .background(pillColor, itemShape)
            // q.txt 第 1 条（用户）：新拟态下选中项做成**内凹** —— 与底栏的凸起形成
            // 一凸一凹的层次，不再只靠一层淡色胶囊。未选中项保持平面。
            .then(
                if (selected && UIConfig.isNeumorphismEnabled()) Modifier.neuInset(
                    shape = itemShape,
                    isDark = UIConfig.shouldUseDarkTheme(),
                    intensity = currentNeuIntensity(),
                    cornerRadius = itemCornerRadius
                ) else Modifier
            )
            // F-17：selectable 让读屏能播报「已选中」，并按 Tab 角色归组。
            .selectable(
                selected = selected,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                role = Role.Tab
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                painter = painterResource(id = spec.iconRes),
                contentDescription = null,
                modifier = Modifier
                    .size(24.dp)
                    .graphicsLayer {
                        scaleX = iconScale
                        scaleY = iconScale
                    },
                tint = tint
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = label,
                color = tint,
                fontSize = 10.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1
            )
        }
    }
}
