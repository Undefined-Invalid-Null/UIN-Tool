// app/src/main/java/com/UIN/Tool/MainActivity.kt
package com.UIN.Tool

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
import com.UIN.Tool.ui.log.LogViewerActivity
import com.UIN.Tool.ui.screen.dev.DevScreen
import com.UIN.Tool.ui.screen.manage.ManageScreen
import com.UIN.Tool.ui.screen.repo.RepoScreen
import com.UIN.Tool.ui.screen.tools.ToolsScreen
import com.UIN.Tool.ui.theme.UINToolTheme
import com.UIN.Tool.utils.Constants
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

    private val manageStorageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (hasManageStoragePermission()) {
            Logger.success(TAG, "存储权限已授予")
            checkAndSetWorkFolder()
        } else {
            Logger.w(TAG, "存储权限被拒绝")
            android.widget.Toast.makeText(this, "需要存储权限才能正常使用", android.widget.Toast.LENGTH_LONG).show()
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
                    Logger.e(TAG, "跳转日志页面失败", e)
                }
            }, 500)
        }

        preferenceManager = PreferenceManager(this)

        UIConfig.init(this)
        val uiConfig = UIConfig.getInstance()
        applyTheme(uiConfig)

        Logger.init(Constants.LOG_DIR)
        Logger.i(TAG, "══════════════════════════════════════════════════")
        Logger.i(TAG, "应用启动")
        Logger.param(TAG, "版本", getVersionCode().toString())
        Logger.param(TAG, "Android版本", "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        Logger.param(TAG, "设备", "${Build.MANUFACTURER} ${Build.MODEL}")

        val pluginId = intent.getStringExtra(EXTRA_PLUGIN_ID)
        val openPlugin = intent.getBooleanExtra(EXTRA_OPEN_PLUGIN, false)
        
        Logger.d(TAG, "onCreate - pluginId: $pluginId, openPlugin: $openPlugin")
        
        val isFromLauncher = intent.action == Intent.ACTION_MAIN && 
                             intent.hasCategory(Intent.CATEGORY_LAUNCHER)
        
        if (isFromLauncher) {
            Logger.i(TAG, "从桌面图标启动，关闭所有插件页面")
            closeAllPluginActivities()
        }
        
        if (!pluginId.isNullOrEmpty() && openPlugin) {
            Logger.i(TAG, "从快捷方式打开插件: $pluginId")
            openPluginDirectly(pluginId)
            return
        }

        handleShortcutIntent(intent)

        setContent {
            UINToolTheme {
                MainContent(
                    initialTab = selectedTab,
                    checkUpdate = intent.getBooleanExtra(EXTRA_CHECK_UPDATE, false)
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
        Logger.d(TAG, "onNewIntent")
        
        val pluginId = intent.getStringExtra(EXTRA_PLUGIN_ID)
        val openPlugin = intent.getBooleanExtra(EXTRA_OPEN_PLUGIN, false)
        
        val isFromLauncher = intent.action == Intent.ACTION_MAIN && 
                             intent.hasCategory(Intent.CATEGORY_LAUNCHER)
        
        if (isFromLauncher) {
            Logger.i(TAG, "onNewIntent: 从桌面图标启动，关闭所有插件页面")
            closeAllPluginActivities()
        }
        
        if (!pluginId.isNullOrEmpty() && openPlugin) {
            Logger.i(TAG, "onNewIntent: 从快捷方式打开插件: $pluginId")
            openPluginDirectly(pluginId)
            return
        }
        
        handleShortcutIntent(intent)
        selectedTab = getTabFromIntent(intent)
        
        setContent {
            UINToolTheme {
                MainContent(
                    initialTab = selectedTab,
                    checkUpdate = intent.getBooleanExtra(EXTRA_CHECK_UPDATE, false)
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
                    Logger.i(TAG, "找到 PluginHostActivity 任务，正在关闭...")
                    task.finishAndRemoveTask()
                    Logger.i(TAG, "PluginHostActivity 任务已关闭")
                }
            }
        } catch (e: Exception) {
            Logger.e(TAG, "关闭插件 Activity 失败: ${e.message}", e)
        }
    }

    private fun openPluginDirectly(pluginId: String) {
        Logger.i(TAG, "直接打开插件: $pluginId")
        try {
            val info = pluginManager.getPluginInfo(pluginId)
            
            if (info == null) {
                Logger.w(TAG, "插件不存在: $pluginId，跳转到主界面")
                finish()
                startActivity(Intent(this, MainActivity::class.java))
                return
            }
            
            Logger.i(TAG, "插件存在: ${info.name}")
            
            val intent = Intent(this, PluginHostActivity::class.java).apply {
                putExtra(PluginHostActivity.EXTRA_PLUGIN_ID, pluginId)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
            Logger.success(TAG, "插件启动成功: ${info.name}")
            
            finish()
            
        } catch (e: Exception) {
            Logger.e(TAG, "启动插件失败: ${e.message}", e)
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
        
        Logger.d(TAG, "handleShortcutIntent - Extras: ${intent.extras}")
        
        val selectedTab = intent.getStringExtra(EXTRA_SELECTED_TAB)
        val checkUpdate = intent.getBooleanExtra(EXTRA_CHECK_UPDATE, false)
        
        Logger.d(TAG, "selectedTab: $selectedTab, checkUpdate: $checkUpdate")
        
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
            Logger.e(TAG, "获取版本号失败", e)
            1
        }
    }

    private fun checkPermissions() {
        Logger.enter(TAG, "checkPermissions")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!hasManageStoragePermission()) {
                Logger.w(TAG, "需要管理所有文件权限")
                showPermissionDialog()
            } else {
                Logger.success(TAG, "管理所有文件权限已授予")
                checkAndSetWorkFolder()
            }
        } else {
            if (ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Logger.w(TAG, "需要存储权限")
                showPermissionDialog()
            } else {
                Logger.success(TAG, "存储权限已授予")
                checkAndSetWorkFolder()
            }
        }

        Logger.exit(TAG, "checkPermissions", System.currentTimeMillis())
    }

    private fun hasManageStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }
    }

    private fun showPermissionDialog() {
        Logger.d(TAG, "显示权限请求对话框")
        AlertDialog.Builder(this)
            .setTitle("需要存储权限")
            .setMessage("UIN Tool 需要管理所有文件的权限，以便导入/导出插件。\n\n请点击「授权」并允许权限。")
            .setPositiveButton("授权") { _, _ ->
                Logger.action(TAG, "用户点击", "授权")
                requestManageStoragePermission()
            }
            .setNegativeButton("退出") { _, _ ->
                Logger.action(TAG, "用户点击", "退出")
                finishAffinity()
            }
            .setCancelable(false)
            .show()
    }

    private fun requestManageStoragePermission() {
        Logger.i(TAG, "请求存储权限")
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
        Logger.enter(TAG, "checkAndSetWorkFolder")
        try {
            var workFolder = preferenceManager.getWorkFolder()

            Logger.param(TAG, "workFolder", workFolder)

            if (workFolder.isEmpty()) {
                val defaultFolder = File(Constants.WORK_DIR)
                if (!defaultFolder.exists()) {
                    defaultFolder.mkdirs()
                    Logger.i(TAG, "创建工作目录: ${defaultFolder.absolutePath}")
                }
                preferenceManager.setWorkFolder(defaultFolder.absolutePath)
                Logger.success(TAG, "设置默认工作目录: ${defaultFolder.absolutePath}")
                android.widget.Toast.makeText(this, "工作目录: ${defaultFolder.absolutePath}", android.widget.Toast.LENGTH_LONG).show()
            } else {
                val folder = File(workFolder)
                if (!folder.exists()) {
                    folder.mkdirs()
                    Logger.i(TAG, "创建工作目录: ${folder.absolutePath}")
                }
                Logger.i(TAG, "使用已有工作目录: $workFolder")
            }

            pluginManager.refreshPlugins()

        } catch (e: Exception) {
            Logger.e(TAG, "创建工作目录失败: ${e.message}", e)
            CrashLogUtils.logException(this, e, "MainActivity")
            android.widget.Toast.makeText(this, "创建工作目录失败: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
        }
        Logger.exit(TAG, "checkAndSetWorkFolder", System.currentTimeMillis())
    }

    override fun onResume() {
        super.onResume()
        Logger.d(TAG, "onResume")

        try {
            val am = getSystemService(ACTIVITY_SERVICE) as ActivityManager
            val tasks = am.appTasks
            for (task in tasks) {
                val info = task.taskInfo
                val topActivity = info.topActivity
                if (topActivity?.className?.contains("PluginHostActivity") == true) {
                    Logger.i(TAG, "检测到插件页面在前台，关闭插件页面")
                    task.finishAndRemoveTask()
                    val intent = Intent(this, MainActivity::class.java)
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    startActivity(intent)
                    return
                }
            }
        } catch (e: Exception) {
            Logger.e(TAG, "检查任务失败: ${e.message}", e)
        }

        val uiConfig = UIConfig.getInstance()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.statusBarColor = uiConfig.getPrimaryDarkColor()
        }

        pluginManager.refreshPlugins()
    }

    override fun onPause() {
        super.onPause()
        Logger.d(TAG, "onPause")
    }

    override fun onDestroy() {
        super.onDestroy()
        Logger.i(TAG, "应用退出")
    }

    override fun onBackPressed() {
        Logger.d(TAG, "onBackPressed")
        AlertDialog.Builder(this)
            .setTitle("退出应用")
            .setMessage("确定要退出 UIN Tool 吗？")
            .setPositiveButton("退出") { _, _ ->
                Logger.action(TAG, "用户选择", "退出应用")
                finishAffinity()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    @Deprecated("使用 Activity Result API")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        Logger.d(TAG, "onRequestPermissionsResult, requestCode: $requestCode")
        if (requestCode == 100) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Logger.success(TAG, "存储权限已授予")
                checkAndSetWorkFolder()
            } else {
                Logger.w(TAG, "存储权限被拒绝")
                android.widget.Toast.makeText(this, "需要存储权限才能正常使用", android.widget.Toast.LENGTH_SHORT).show()
                finishAffinity()
            }
        }
    }
}

// ==================== MainContent ====================

@Composable
fun MainContent(
    initialTab: Int = 1,
    checkUpdate: Boolean = false
) {
    var selectedTab by remember { mutableStateOf(initialTab) }

    val tabs = listOf(
        "开发" to R.drawable.ic_developer_mode,
        "工具" to R.drawable.ic_grid_view,
        "仓库" to R.drawable.ic_repo,
        "管理" to R.drawable.ic_settings
    )

    LaunchedEffect(checkUpdate) {
        if (checkUpdate) {
            selectedTab = 3
        }
    }

    Scaffold(
        bottomBar = {
            // 浮动底部导航栏 - 无边框，无左右下边距
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