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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.UIN.Tool.R
import com.UIN.Tool.core.di.ServiceLocator
import com.UIN.Tool.data.local.PreferenceManager
import com.UIN.Tool.domain.repository.IConfigRepository
import com.UIN.Tool.log.Logger
import com.UIN.Tool.plugin.PluginHostActivity
import com.UIN.Tool.plugin.PluginManager
import com.UIN.Tool.ui.components.UIComponents
import com.UIN.Tool.ui.screen.log.LogViewerActivity
import com.UIN.Tool.ui.screen.dev.DevScreen
import com.UIN.Tool.ui.screen.manage.ManageScreen
import com.UIN.Tool.ui.screen.repo.RepoScreen
import com.UIN.Tool.ui.screen.tools.ToolsScreen
import com.UIN.Tool.ui.theme.UINToolTheme
import com.UIN.Tool.utils.AppLog
import com.UIN.Tool.constants.AppConstants as Constants
import com.UIN.Tool.utils.CrashLogUtils
import com.UIN.Tool.utils.UIConfig
import java.io.File

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
                    startActivity(Intent(this, LogViewerActivity::class.java).apply {
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            window.statusBarColor = uiConfig.getPrimaryDarkColor()
            window.navigationBarColor = uiConfig.getSurfaceColor()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            setTaskDescription(
                ActivityManager.TaskDescription(
                    getString(R.string.app_name),
                    null,
                    uiConfig.getPrimaryColor() or 0xFF000000.toInt()
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
            window.statusBarColor = uiConfig.getPrimaryDarkColor()
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
        Str.get(R.string.developer) to R.drawable.ic_developer_mode,
        Str.get(R.string.tools) to R.drawable.ic_grid_view,
        Str.get(R.string.repository) to R.drawable.ic_repo,
        Str.get(R.string.manage) to R.drawable.ic_settings
    )

    LaunchedEffect(checkUpdate) {
        if (checkUpdate) {
            selectedTab = 3
        }
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

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = Color.Transparent,
                tonalElevation = 0.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(
                        elevation = 8.dp,
                        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                        clip = false
                    )
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.95f),
                                Color.White.copy(alpha = 0.85f)
                            )
                        ),
                        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                    )
                    .height(56.dp)
            ) {
                tabs.forEachIndexed { index, (label, icon) ->
                    val isSelected = selectedTab == index
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { selectedTab = index },
                        icon = {
                            Icon(
                                painter = painterResource(id = icon),
                                contentDescription = label,
                                modifier = Modifier.size(24.dp),
                                tint = if (isSelected) Color(0xFF1A3A4A) else Color(0xFF9AA6B2)
                            )
                        },
                        label = { 
                            Text(
                                label,
                                color = if (isSelected) Color(0xFF1A3A4A) else Color(0xFF9AA6B2),
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color(0xFF1A3A4A),
                            selectedTextColor = Color(0xFF1A3A4A),
                            unselectedIconColor = Color(0xFF9AA6B2),
                            unselectedTextColor = Color(0xFF9AA6B2),
                            indicatorColor = Color.Transparent
                        )
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            when (selectedTab) {
                0 -> DevScreen()
                1 -> ToolsScreen()
                2 -> RepoScreen()
                3 -> ManageScreen(checkUpdate = checkUpdate)
            }
        }
    }
}