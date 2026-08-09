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
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer

import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.UIN.Tool.R
import com.UIN.Tool.core.di.ServiceLocator
import com.UIN.Tool.core.update.UpdateChecker
import com.UIN.Tool.core.update.UpdateDownloader
import com.UIN.Tool.data.local.PreferenceManager
import com.UIN.Tool.domain.model.ReleaseInfo
import com.UIN.Tool.domain.repository.IConfigRepository
import com.UIN.Tool.log.Logger
import com.UIN.Tool.plugin.PluginHostActivity
import com.UIN.Tool.plugin.PluginManager
import com.UIN.Tool.ui.components.UIComponents
import com.UIN.Tool.ui.components.UpdateDialog
import com.UIN.Tool.ui.components.unified.UnifiedAlertDialog
import com.UIN.Tool.ui.components.unified.UnifiedDialogTextButton
import com.UIN.Tool.ui.screen.dev.DevToolsActivity
import com.UIN.Tool.ui.screen.dev.DevScreen
import com.UIN.Tool.ui.screen.manage.ManageScreen
import com.UIN.Tool.ui.screen.repo.RepoScreen
import com.UIN.Tool.ui.screen.tools.ToolsScreen
import com.UIN.Tool.ui.theme.UINToolTheme
import com.UIN.Tool.ui.theme.dynamicColor
import com.UIN.Tool.ui.theme.AppColors
import com.UIN.Tool.ui.theme.AppDimens
import com.UIN.Tool.utils.AppLog
import com.UIN.Tool.utils.AppToast
import com.UIN.Tool.constants.AppConstants as Constants
import com.UIN.Tool.utils.CrashLogUtils
import com.UIN.Tool.utils.UIConfig
import java.io.File
import java.time.LocalDate

class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
        private const val EXTRA_SELECTED_TAB = "selected_tab"
        private const val EXTRA_CHECK_UPDATE = "check_update"
        private const val EXTRA_PLUGIN_ID = "plugin_id"
        private const val EXTRA_OPEN_PLUGIN = "open_plugin"
    }

    private val pluginManager: PluginManager by lazy { ServiceLocator.getPluginManager() }
    private val configRepository: IConfigRepository by lazy { ServiceLocator.getConfigRepository() }
    
    private lateinit var preferenceManager: PreferenceManager
    private var selectedTab = 1
    
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
            android.widget.Toast.makeText(this, Str.get(R.string.storage_permission_is_required_for_n), android.widget.Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val hasCrashed = CrashLogUtils.shouldNavigateToLogs(this)
        if (hasCrashed) {
            CrashLogUtils.clearNavigateFlag(this)
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                try {
                    startActivity(Intent(this, DevToolsActivity::class.java).apply {
                        putExtra("auto_open", true)
                    })
                } catch (e: Exception) {
                    AppLog.e(TAG, Str.get(R.string.failed_to_open_log_page), e)
                }
            }, 500)
        }

        preferenceManager = PreferenceManager(this)

        UIConfig.init(this)
        val uiConfig = UIConfig.getInstance()
        applyTheme(uiConfig)

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
            AppLog.i(TAG, Str.get(R.string.launched_from_desktop_icon_closing_a))
            closeAllPluginActivities()
        }
        
        if (!pluginId.isNullOrEmpty() && openPlugin) {
            AppLog.i(TAG, Str.get(R.string.opening_plugin_from_shortcut_plugini, pluginId))
            openPluginDirectly(pluginId)
            return
        }

        handleShortcutIntent(intent)

        setContent {
            UINToolTheme {
                MainContent(
                    initialTab = selectedTab,
                    checkUpdate = intent.getBooleanExtra(EXTRA_CHECK_UPDATE, false),
                    showExitDialog = showExitDialog,
                    onExitConfirm = { 
                        showExitDialog = false
                        finishAffinity()
                    },
                    onExitDismiss = { 
                        showExitDialog = false
                    }
                )
            }

            // 配置变更时即时刷新状态栏/导航栏/任务描述颜色
            LaunchedEffect(Unit) {
                UIConfig.configVersion.collect {
                    if (UIConfig.isInitialized()) {
                        applyTheme(UIConfig.getInstance())
                    }
                }
            }
        }

        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            checkPermissions()
        }, 1000)
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
            AppLog.i(TAG, Str.get(R.string.onnewintent_launched_from_desktop_ic))
            closeAllPluginActivities()
        }
        
        if (!pluginId.isNullOrEmpty() && openPlugin) {
            AppLog.i(TAG, Str.get(R.string.onnewintent_opening_plugin_from_shor, pluginId))
            openPluginDirectly(pluginId)
            return
        }
        
        handleShortcutIntent(intent)
        selectedTab = getTabFromIntent(intent)
        showExitDialog = false
        
        setContent {
            UINToolTheme {
                MainContent(
                    initialTab = selectedTab,
                    checkUpdate = intent.getBooleanExtra(EXTRA_CHECK_UPDATE, false),
                    showExitDialog = showExitDialog,
                    onExitConfirm = { 
                        showExitDialog = false
                        finishAffinity()
                    },
                    onExitDismiss = { 
                        showExitDialog = false
                    }
                )
            }
        }
    }

    private fun closeAllPluginActivities() {
        try {
            val activityManager = getSystemService(ACTIVITY_SERVICE) as ActivityManager
            val tasks = activityManager.appTasks
            
            for (task in tasks) {
                val info = task.taskInfo
                val topActivity = info.topActivity
                if (topActivity?.className?.contains("PluginHostActivity") == true) {
                    AppLog.i(TAG, Str.get(R.string.found_pluginhostactivity_task_closin))
                    task.finishAndRemoveTask()
                    AppLog.i(TAG, Str.get(R.string.pluginhostactivity_task_closed))
                }
            }
        } catch (e: Exception) {
            AppLog.e(TAG, Str.get(R.string.failed_to_close_plugin_activity_e_me, e.message), e)
        }
    }

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

    private fun handleShortcutIntent(intent: Intent) {
        if (intent == null) return
        
        AppLog.d(TAG, "handleShortcutIntent - Extras: ${intent.extras}")
        
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
        AppLog.d(TAG, Str.get(R.string.showing_permission_request_dialog))
        AlertDialog.Builder(this)
            .setTitle(Str.get(R.string.storage_permission_required))
            .setMessage(Str.get(R.string.uin_tool_needs_access_to_all_files_t))
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

        try {
            val am = getSystemService(ACTIVITY_SERVICE) as ActivityManager
            val tasks = am.appTasks
            for (task in tasks) {
                val info = task.taskInfo
                val topActivity = info.topActivity
                if (topActivity?.className?.contains("PluginHostActivity") == true) {
                    AppLog.i(TAG, Str.get(R.string.plugin_page_is_in_the_foreground_clo))
                    task.finishAndRemoveTask()
                    val intent = Intent(this, MainActivity::class.java)
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    startActivity(intent)
                    return
                }
            }
        } catch (e: Exception) {
            AppLog.e(TAG, Str.get(R.string.failed_to_check_task_e_message, e.message), e)
        }

        val uiConfig = UIConfig.getInstance()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val dark = uiConfig.shouldUseDarkTheme(uiConfig.isSystemDark())
            window.statusBarColor = uiConfig.getColorForMode("status_bar", dark)
            window.navigationBarColor = uiConfig.getColorForMode("navigation_bar", dark)
        }

        pluginManager.refreshPlugins()
    }

    override fun onPause() {
        super.onPause()
        AppLog.d(TAG, "onPause")
    }

    override fun onDestroy() {
        super.onDestroy()
        AppLog.i(TAG, Str.get(R.string.app_exit))
    }

    override fun onBackPressed() {
        AppLog.d(TAG, "onBackPressed")
        // ✅ 直接修改状态，不需要重新 setContent
        showExitDialog = true
    }

    @Deprecated("Using Activity Result API")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        AppLog.d(TAG, "onRequestPermissionsResult, requestCode: $requestCode")
        if (requestCode == 100) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                AppLog.success(TAG, Str.get(R.string.storage_permission_granted))
                checkAndSetWorkFolder()
            } else {
                AppLog.w(TAG, Str.get(R.string.storage_permission_denied))
                android.widget.Toast.makeText(this, Str.get(R.string.storage_permission_is_required_for_n), android.widget.Toast.LENGTH_SHORT).show()
                finishAffinity()
            }
        }
    }
}

// ==================== MainContent ====================

private const val MAIN_CONTENT_TAG = "MainContent"

@Composable
fun MainContent(
    initialTab: Int = 1,
    checkUpdate: Boolean = false,
    showExitDialog: Boolean = false,
    onExitConfirm: () -> Unit = {},
    onExitDismiss: () -> Unit = {}
) {
    var selectedTab by remember { mutableStateOf(initialTab) }

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

    // ==================== 静默更新检测（每天一次） ====================
    val context = LocalContext.current
    val preferenceManager = ServiceLocator.getPreferenceManager()
    var silentUpdate by remember { mutableStateOf<ReleaseInfo?>(null) }
    var silentForce by remember { mutableStateOf(false) }
    var silentProgress by remember { mutableStateOf(false) }
    var silentProgressPct by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        try {
            // 仅当今天尚未静默检测过才执行，避免重复弹窗
            val today = LocalDate.now().toEpochDay()
            if (preferenceManager.getLastUpdateCheckDay() == today) return@LaunchedEffect
            preferenceManager.setLastUpdateCheckDay(today)

            val checker = UpdateChecker(context, preferenceManager)
            checker.setOnUpdateListener(object : UpdateChecker.OnUpdateListener {
                override fun onCheckStart() {}

                override fun onCheckSuccess(releases: List<ReleaseInfo>, hasNewer: Boolean, forceUpdate: Boolean) {
                    if (!hasNewer || releases.isEmpty()) return
                    val latest = releases.first()
                    if (!forceUpdate && latest.versionName == preferenceManager.getIgnoredVersion()) return
                    preferenceManager.setLastChangelog(latest.releaseNotes ?: "")
                    silentUpdate = latest
                    silentForce = forceUpdate
                }

                override fun onCheckFailed(error: String) {}

                override fun onNoUpdate(currentVersion: String) {}
            })
            checker.checkUpdate()
        } catch (e: Exception) {
            AppLog.e(MAIN_CONTENT_TAG, Str.get(R.string.update_check_failed), e)
        }
    }

    // 静默检测到新版本时显示更新弹窗
    val silentTarget = silentUpdate
    if (silentTarget != null) {
        UpdateDialog(
            releaseInfo = silentTarget,
            forceUpdate = silentForce,
            onDismiss = { if (!silentForce) silentUpdate = null },
            onDownload = {
                silentUpdate = null
                silentProgress = true
                val downloader = UpdateDownloader(context)
                downloader.setOnDownloadListener(object : UpdateDownloader.OnDownloadListener {
                    override fun onStart() {}

                    override fun onProgress(progress: Int, downloaded: Long, total: Long) {
                        silentProgressPct = progress
                    }

                    override fun onSuccess(file: java.io.File) {
                        silentProgress = false
                        downloader.installApk(file)
                    }

                    override fun onFailed(error: String) {
                        silentProgress = false
                        AppToast.error(context, Str.get(R.string.download_failed_error, error))
                    }
                })
                downloader.startDownload(silentTarget.downloadUrl, silentTarget.versionName)
            },
            onManualDownload = {
                silentUpdate = null
                try {
                    val url = "https://github.com/Undefined-Invalid-Null/UIN-Tool/releases/tag/${silentTarget.tagName}"
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                } catch (e: Exception) {
                    AppLog.e(MAIN_CONTENT_TAG, Str.get(R.string.failed_to_open_browser), e)
                }
            },
            onIgnore = {
                preferenceManager.setIgnoredVersion(silentTarget.versionName)
                silentUpdate = null
            }
        )
    }

    // 静默下载进度提示
    if (silentProgress) {
        UnifiedAlertDialog(
            onDismissRequest = { },
            title = { Text(Str.get(R.string.downloading_update)) },
            text = {
                Column {
                    LinearProgressIndicator(
                        progress = { silentProgressPct / 100f },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "${silentProgressPct}%",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            },
            confirmButton = {
                UnifiedDialogTextButton(onClick = { silentProgress = false }) {
                    Text(Str.get(R.string.cancel))
                }
            }
        )
    }

    // ✅ 退出对话框 - 使用 UIComponents.ConfirmDialog
    if (showExitDialog) {
        UIComponents.ConfirmDialog(
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
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .shadow(AppDimens.cardElevation, navShape)
                .background(AppColors.cardBackground(), navShape)
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
                        .semantics { role = Role.Tab }
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = { selectedTab = index }
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