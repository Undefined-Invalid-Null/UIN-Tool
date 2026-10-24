// app/src/main/java/com/UIN/Tool/MainActivity.kt
package com.UIN.Tool

import com.UIN.Tool.utils.Str
import android.app.ActivityManager
import android.app.AlertDialog
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
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
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
import com.UIN.Tool.ui.screen.manage.ManageScreen
import com.UIN.Tool.ui.screen.repo.RepoScreen
import com.UIN.Tool.ui.screen.tools.ToolsScreen
import com.UIN.Tool.ui.theme.UINToolTheme
import androidx.lifecycle.viewmodel.compose.viewModel
import com.UIN.Tool.ui.viewmodel.UpdateViewModel
import com.UIN.Tool.ui.theme.dynamicColor
import com.UIN.Tool.ui.theme.AppColors
import com.UIN.Tool.ui.theme.AppDimens
import com.UIN.Tool.ui.components.unified.NeuDefaults
import com.UIN.Tool.ui.components.unified.neuRaised
import com.UIN.Tool.utils.AppLog
import com.UIN.Tool.constants.AppConstants as Constants
import com.UIN.Tool.utils.CrashLogUtils
import com.UIN.Tool.utils.formatFileSize
import com.UIN.Tool.utils.UIConfig
import java.io.File

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
        private const val EXTRA_SELECTED_TAB = "selected_tab"
        private const val EXTRA_CHECK_UPDATE = "check_update"
        private const val EXTRA_PLUGIN_ID = "plugin_id"
        private const val EXTRA_OPEN_PLUGIN = "open_plugin"
    }

    private val pluginManager: PluginManager by lazy { ServiceLocator.getPluginManager() }
    
    private lateinit var preferenceManager: PreferenceManager
    private var selectedTab by mutableStateOf(1)

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
            android.widget.Toast.makeText(this, Str.get(R.string.storage_permission_is_required_for), android.widget.Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val hasCrashed = CrashLogUtils.shouldNavigateToLogs(this)
        if (hasCrashed) {
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

        handleShortcutIntent(intent)
        // B-19：深链覆盖 shortcut extra 指定的 tab
        applyDeepLink(intent)

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
        
        val pluginId = intent.getStringExtra(EXTRA_PLUGIN_ID)
        val openPlugin = intent.getBooleanExtra(EXTRA_OPEN_PLUGIN, false)
        
        val isFromLauncher = intent.action == Intent.ACTION_MAIN &&
                             intent.hasCategory(Intent.CATEGORY_LAUNCHER)
        
        if (isFromLauncher) {
            AppLog.i(TAG, Str.get(R.string.onnewintent_launched_from_desktop_icon))
            closeAllPluginActivities()
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
                val base = task.taskInfo.baseIntent?.component?.className
                val top = task.taskInfo.topActivity?.className
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
    }

    /**
     * B-19：Manifest 声明了 uin://tools 与 uin://check_update 两个深链，但代码从未读 intent.data，
     * 浏览器点开等于没反应。这里按 host 路由到对应 tab（深链优先级高于 shortcut extra）。
     * 返回 true 表示本次 intent 是深链。
     */
    private fun applyDeepLink(intent: Intent): Boolean {
        val data = intent.data ?: return false
        if (data.scheme != "uin") return false
        return when (data.host) {
            "tools" -> {
                selectedTab = 1
                AppLog.i(TAG, "deep link uin://tools -> tab=tools")
                true
            }
            "check_update" -> {
                selectedTab = 3
                AppLog.i(TAG, "deep link uin://check_update -> tab=manage(check update)")
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
            this.selectedTab = 3
        } else {
            this.selectedTab = when (selectedTab) {
                "dev" -> 0
                "tools" -> 1
                "repo" -> 2
                "manage" -> 3
                else -> 1
            }
        }
    }

    private fun getTabFromIntent(intent: Intent): Int {
        val selectedTab = intent.getStringExtra(EXTRA_SELECTED_TAB)
        val checkUpdate = intent.getBooleanExtra(EXTRA_CHECK_UPDATE, false)
        return if (checkUpdate) {
            3
        } else {
            when (selectedTab) {
                "dev" -> 0
                "tools" -> 1
                "repo" -> 2
                "manage" -> 3
                else -> 1
            }
        }
    }

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
                android.widget.Toast.makeText(this, Str.get(R.string.work_directory_defaultfolder_absolut, defaultFolder.absolutePath), android.widget.Toast.LENGTH_LONG).show()
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
            android.widget.Toast.makeText(this, Str.get(R.string.failed_to_create_work_directory_e_me, e.message), android.widget.Toast.LENGTH_SHORT).show()
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
                android.widget.Toast.makeText(this, Str.get(R.string.storage_permission_is_required_for), android.widget.Toast.LENGTH_SHORT).show()
                finishAffinity()
            }
        }
    }
}

// ==================== MainContent ====================

@Composable
fun MainContent(
    initialTab: Int = 1,
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

    val tabs = listOf(
        Str.get(R.string.developer) to R.drawable.ic_terminal_prompt,
        Str.get(R.string.plugins) to R.drawable.ic_grid_view,
        Str.get(R.string.repository) to R.drawable.ic_repo,
        Str.get(R.string.manage) to R.drawable.ic_settings
    )

    LaunchedEffect(checkUpdate) {
        if (checkUpdate) {
            selectedTab = 3
        }
    }

    // 外部 intent 导航（onNewIntent）通过 tabSignal 驱动切换，保持组合内状态不丢失
    LaunchedEffect(tabSignal) {
        selectedTab = initialTab
    }

    // ==================== 静默更新检测（每天一次） ====================
    // B-79：检查/下载/弹窗状态全部下沉 UpdateViewModel，组合只负责渲染；
    // 旋转或重建时进行中的检查与下载不再丢失（原 remember { mutableStateOf } 会被重置）。
    val context = LocalContext.current
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
    BackHandler {
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
                        val direction = if (targetState > initialState) 1 else -1
                        (slideInHorizontally(tween(300)) { it * direction } + fadeIn(tween(300)))
                            .togetherWith(
                                slideOutHorizontally(tween(300)) { -it * direction } + fadeOut(tween(300))
                            )
                    },
                    label = "main_tab_content"
                ) { tab ->
                    when (tab) {
                        0 -> DevScreen()
                        1 -> ToolsScreen()
                        2 -> RepoScreen()
                        3 -> ManageScreen(checkUpdate = checkUpdate)
                    }
                }
            }
        }

        // 悬浮式底部导航：风格完全遵照卡片（不透明 surface 背景、卡片圆角与阴影），
        // 比玻璃态卡片更白更实，点击动效（按压缩放 + 选中放大）保持不变
        val selectedColor = dynamicColor("nav_selected", MaterialTheme.colorScheme.primary)
        val unselectedColor = dynamicColor("nav_unselected", MaterialTheme.colorScheme.onSurfaceVariant)
        val navShape = RoundedCornerShape(AppDimens.cardCornerRadius)
        val neuNav = UIConfig.isNeumorphismEnabled()
        val isDarkNav = UIConfig.shouldUseDarkTheme()

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .then(if (neuNav) Modifier.neuRaised(navShape, isDarkNav, NeuDefaults.Intensity.LIGHT, cornerRadius = AppDimens.cardCornerRadius, backgroundColor = Color.Transparent) else Modifier.shadow(AppDimens.cardElevation, navShape))
                .clip(navShape)
                .background(AppColors.cardBackground(), navShape)
                // F-17/E-07：把 4 个 tab 声明成一个可选组，读屏可播报「第 n 项，共 4 项」
                .semantics { selectableGroup() }
                .height(AppDimens.bottomNavHeight)
        ) {
            tabs.forEachIndexed { index, (label, icon) ->
                val isSelected = selectedTab == index
                val interactionSource = remember { MutableInteractionSource() }
                val isPressed by interactionSource.collectIsPressedAsState()
                val tabScale by animateFloatAsState(
                    targetValue = if (isPressed) 0.88f else 1f,
                    animationSpec = tween(durationMillis = 150),
                    label = "nav_tab_scale"
                )
                val iconSize by animateDpAsState(
                    targetValue = if (isSelected) 30.dp else 24.dp,
                    animationSpec = tween(durationMillis = 200),
                    label = "nav_icon_size"
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .graphicsLayer {
                            scaleX = tabScale
                            scaleY = tabScale
                        }
                        // F-17：原 clickable + 手动 role 无法表达「已选中」，改 selectable——
                        // 选中态进入语义树，读屏会朗读「已选择」，且按 Tab 角色归组
                        .selectable(
                            selected = isSelected,
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = { selectedTab = index },
                            role = Role.Tab
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        painter = painterResource(id = icon),
                        contentDescription = label,
                        modifier = Modifier.size(iconSize),
                        tint = if (isSelected) selectedColor else unselectedColor
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        label,
                        color = if (isSelected) selectedColor else unselectedColor,
                        fontSize = 10.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}