// app/src/main/java/com/UIN/Tool/SplashActivity.kt
package com.UIN.Tool

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.UIN.Tool.R
import com.UIN.Tool.core.di.ServiceLocator
import com.UIN.Tool.core.update.UpdateChecker
import com.UIN.Tool.core.update.UpdateDownloader
import com.UIN.Tool.data.local.PreferenceManager
import com.UIN.Tool.domain.model.ReleaseInfo
import com.UIN.Tool.log.Logger
import com.UIN.Tool.ui.screen.onboarding.OnboardingActivity
import com.UIN.Tool.ui.theme.UINToolTheme
import com.UIN.Tool.utils.Constants
import com.UIN.Tool.utils.MarkdownRenderer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

class SplashActivity : ComponentActivity() {

    companion object {
        private const val TAG = "SplashActivity"
        private const val SPLASH_DELAY = 1500L
    }

    private lateinit var preferenceManager: PreferenceManager
    private var updateDownloader: UpdateDownloader? = null
    private var hasRequestedPermission = false
    
    // 权限请求状态
    private var showPermissionExplain by mutableStateOf(false)
    private var showPermissionDenied by mutableStateOf(false)

    // ==================== 存储权限请求 Launcher ====================
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val allGranted = results.values.all { it }
        if (allGranted) {
            Logger.success(TAG, "所有存储权限已授予")
            createWorkDirectory()
            // 权限授予后继续检查更新
            checkForUpdate()
        } else {
            val deniedPermissions = results.filter { !it.value }.keys
            Logger.w(TAG, "部分权限被拒绝: $deniedPermissions")
            
            val shouldShowRationale = deniedPermissions.any { permission ->
                shouldShowRequestPermissionRationale(permission)
            }
            
            if (shouldShowRationale) {
                showPermissionExplain = true
            } else {
                showPermissionDenied = true
            }
        }
    }

    // ==================== Android 11+ 管理所有文件权限 Launcher ====================
    private val manageStorageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                Logger.success(TAG, "管理所有文件权限已授予")
                createWorkDirectory()
                checkForUpdate()
            } else {
                Logger.w(TAG, "管理所有文件权限被拒绝")
                showPermissionDenied = true
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        preferenceManager = ServiceLocator.getPreferenceManager()

        setContent {
            UINToolTheme {
                SplashScreenWithUpdate(
                    isCheckingPermission = !hasStoragePermission(),
                    onPermissionRequest = { requestStoragePermission() },
                    showPermissionExplain = showPermissionExplain,
                    showPermissionDenied = showPermissionDenied,
                    onDismissPermissionExplain = { showPermissionExplain = false },
                    onDismissPermissionDenied = { showPermissionDenied = false },
                    onNavigate = { navigateToNext() },
                    onOpenBrowser = { url ->
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            startActivity(intent)
                        } catch (e: Exception) {
                            Logger.e(TAG, "打开浏览器失败", e)
                            Toast.makeText(this, "无法打开链接", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onStartDownload = { releaseInfo ->
                        startDownload(releaseInfo)
                    }
                )
            }
        }

        // 检查并请求存储权限
        if (!hasStoragePermission()) {
            requestStoragePermission()
        } else {
            createWorkDirectory()
            // 延迟检查更新
            Handler(Looper.getMainLooper()).postDelayed({
                checkForUpdate()
            }, SPLASH_DELAY)
        }
    }

    // ==================== 存储权限检查 ====================
    private fun hasStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }

    // ==================== 请求存储权限 ====================
    private fun requestStoragePermission() {
        if (hasRequestedPermission) return
        hasRequestedPermission = true

        Logger.i(TAG, "请求存储权限")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    intent.data = Uri.parse("package:$packageName")
                    manageStorageLauncher.launch(intent)
                } catch (e: Exception) {
                    Logger.e(TAG, "打开存储权限设置失败", e)
                    requestNormalStoragePermission()
                }
            }
        } else {
            requestNormalStoragePermission()
        }
    }

    private fun requestNormalStoragePermission() {
        val permissions = mutableListOf<String>()
        if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        if (permissions.isNotEmpty()) {
            requestPermissionLauncher.launch(permissions.toTypedArray())
        }
    }

    // ==================== 创建工作目录 ====================
    private fun createWorkDirectory() {
        try {
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

            dirs.forEach { path ->
                val dir = File(path)
                if (!dir.exists()) {
                    dir.mkdirs()
                    Logger.d(TAG, "创建目录: $path")
                }
            }

            // 初始化日志
            Logger.init(Constants.LOG_DIR)
            Logger.i(TAG, "══════════════════════════════════════════════════")
            Logger.i(TAG, "应用启动 - UIN Tool v${Constants.APP_VERSION}")
            Logger.i(TAG, "工作目录: ${Constants.WORK_DIR}")
            Logger.i(TAG, "══════════════════════════════════════════════════")

            preferenceManager.setWorkFolder(Constants.WORK_DIR)
            Logger.success(TAG, "工作目录创建成功")

        } catch (e: Exception) {
            Logger.e(TAG, "创建工作目录失败", e)
            Toast.makeText(this, "创建目录失败: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    // ==================== 版本忽略检查 ====================
    private fun isVersionIgnored(versionName: String): Boolean {
        if (versionName.isEmpty()) return false
        val prefs = preferenceManager.getPrefs()
        val ignoredVersion = prefs.getString(Constants.KEY_IGNORE_VERSION, "")
        return versionName == ignoredVersion
    }

    private fun setVersionIgnored(versionName: String) {
        if (versionName.isEmpty()) return
        val prefs = preferenceManager.getPrefs()
        prefs.edit().putString(Constants.KEY_IGNORE_VERSION, versionName).apply()
        Logger.i(TAG, "忽略版本: $versionName")
    }

    // ==================== 检查更新 ====================
    private fun checkForUpdate() {
        Logger.enter(TAG, "checkForUpdate")

        val updateChecker = UpdateChecker(this, preferenceManager)

        updateChecker.setOnUpdateListener(object : UpdateChecker.OnUpdateListener {
            override fun onCheckStart() {
                Logger.d(TAG, "开始检查更新")
            }

            override fun onCheckSuccess(releases: List<ReleaseInfo>, hasNewer: Boolean, forceUpdate: Boolean) {
                if (hasNewer && releases.isNotEmpty()) {
                    val latest = releases.first()

                    if (!forceUpdate && isVersionIgnored(latest.versionName)) {
                        Logger.i(TAG, "用户已忽略版本: ${latest.versionName}")
                        navigateToNext()
                        return
                    }

                    // 通过 Compose 显示更新对话框
                    showUpdateDialogCompose(latest, forceUpdate)
                } else {
                    navigateToNext()
                }
            }

            override fun onCheckFailed(error: String) {
                Logger.e(TAG, "检查更新失败: $error")
                navigateToNext()
            }

            override fun onNoUpdate(currentVersion: String) {
                Logger.i(TAG, "当前已是最新版本: $currentVersion")
                navigateToNext()
            }
        })

        updateChecker.checkUpdate()
    }

    // ==================== 显示更新对话框 (通过 Compose) ====================
    private fun showUpdateDialogCompose(releaseInfo: ReleaseInfo, forceUpdate: Boolean) {
        setContent {
            UINToolTheme {
                UpdateDialog(
                    releaseInfo = releaseInfo,
                    forceUpdate = forceUpdate,
                    onDismiss = {
                        if (!forceUpdate) navigateToNext()
                    },
                    onDownload = {
                        startDownload(releaseInfo)
                    },
                    onManualDownload = {
                        try {
                            val url = "https://github.com/Undefined-Invalid-Null/UIN-Tool/releases/tag/${releaseInfo.tagName}"
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            startActivity(intent)
                        } catch (e: Exception) {
                            Logger.e(TAG, "打开浏览器失败", e)
                            Toast.makeText(this, "无法打开链接", Toast.LENGTH_SHORT).show()
                        }
                        Handler(Looper.getMainLooper()).postDelayed({
                            navigateToNext()
                        }, 500)
                    },
                    onIgnore = {
                        setVersionIgnored(releaseInfo.versionName)
                        navigateToNext()
                    }
                )
            }
        }
    }

    // ==================== 下载更新 ====================
    private fun startDownload(releaseInfo: ReleaseInfo) {
        Logger.enter(TAG, "startDownload")

        if (releaseInfo.downloadUrl.isNullOrEmpty()) {
            Logger.e(TAG, "下载链接为空")
            Toast.makeText(this, "下载链接无效，请稍后重试", Toast.LENGTH_SHORT).show()
            return
        }

        updateDownloader = UpdateDownloader(this)
        updateDownloader?.setOnDownloadListener(object : UpdateDownloader.OnDownloadListener {
            override fun onStart() {
                Logger.d(TAG, "开始下载")
            }

            override fun onProgress(progress: Int, downloaded: Long, total: Long) {
                // 进度通过 Compose 状态更新
            }

            override fun onSuccess(file: File) {
                Logger.success(TAG, "下载成功")
                updateDownloader?.installApk(file)
            }

            override fun onFailed(error: String) {
                Logger.e(TAG, "下载失败: $error")
                Toast.makeText(this@SplashActivity, "下载失败: $error", Toast.LENGTH_LONG).show()
            }
        })

        updateDownloader?.startDownload(releaseInfo.downloadUrl, releaseInfo.versionName)
    }

    // ==================== 导航 ====================
    private fun navigateToNext() {
        try {
            val isFirstLaunch = preferenceManager.isFirstLaunch()
            val lastVersion = preferenceManager.getLastVersion()
            val currentVersion = Constants.APP_VERSION
            val isVersionUpdated = lastVersion != currentVersion && !isFirstLaunch

            preferenceManager.setFirstLaunch(false)
            preferenceManager.setLastVersion(currentVersion)

            val intent = if (isFirstLaunch || isVersionUpdated) {
                Intent(this, OnboardingActivity::class.java).apply {
                    putExtra("is_version_update", isVersionUpdated)
                    putExtra("version_name", currentVersion)
                }
            } else {
                Intent(this, MainActivity::class.java)
            }

            startActivity(intent)
            finish()

            Logger.exit(TAG, "navigateToNext", System.currentTimeMillis())
        } catch (e: Exception) {
            Logger.e(TAG, "导航失败", e)
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        updateDownloader?.cancelDownload()
        Logger.d(TAG, "onDestroy")
    }
}

// ==================== Compose UI ====================

@Composable
fun SplashScreenWithUpdate(
    isCheckingPermission: Boolean,
    onPermissionRequest: () -> Unit,
    showPermissionExplain: Boolean,
    showPermissionDenied: Boolean,
    onDismissPermissionExplain: () -> Unit,
    onDismissPermissionDenied: () -> Unit,
    onNavigate: () -> Unit,
    onOpenBrowser: (String) -> Unit,
    onStartDownload: (ReleaseInfo) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val preferenceManager = ServiceLocator.getPreferenceManager()

    // ==================== 状态 ====================
    var showUpdateDialog by remember { mutableStateOf(false) }
    var isChecking by remember { mutableStateOf(!isCheckingPermission) }
    var updateInfo by remember { mutableStateOf<ReleaseInfo?>(null) }
    var isForceUpdate by remember { mutableStateOf(false) }
    var showDownloadProgress by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableStateOf(0) }

    // ==================== 检查更新 ====================
    LaunchedEffect(isCheckingPermission) {
        if (!isCheckingPermission) {
            delay(1500)
            isChecking = true

            val updateChecker = UpdateChecker(context, preferenceManager)

            updateChecker.setOnUpdateListener(object : UpdateChecker.OnUpdateListener {
                override fun onCheckStart() {
                    Logger.d("SplashScreen", "开始检查更新")
                }

                override fun onCheckSuccess(releases: List<ReleaseInfo>, hasNewer: Boolean, forceUpdate: Boolean) {
                    isChecking = false

                    if (hasNewer && releases.isNotEmpty()) {
                        val latest = releases.first()

                        if (!forceUpdate && isVersionIgnored(preferenceManager, latest.versionName)) {
                            Logger.i("SplashScreen", "用户已忽略版本: ${latest.versionName}")
                            onNavigate()
                            return
                        }

                        updateInfo = latest
                        isForceUpdate = forceUpdate
                        showUpdateDialog = true
                    } else {
                        onNavigate()
                    }
                }

                override fun onCheckFailed(error: String) {
                    isChecking = false
                    Logger.e("SplashScreen", "检查更新失败: $error")
                    onNavigate()
                }

                override fun onNoUpdate(currentVersion: String) {
                    isChecking = false
                    Logger.i("SplashScreen", "当前已是最新版本: $currentVersion")
                    onNavigate()
                }
            })

            updateChecker.checkUpdate()
        }
    }

    // ==================== 权限请求对话框 ====================
    if (isCheckingPermission) {
        PermissionRequestDialog(
            onRequest = onPermissionRequest,
            onExit = {
                android.os.Process.killProcess(android.os.Process.myPid())
                System.exit(1)
            }
        )
        return
    }

    // ==================== 权限说明对话框 ====================
    if (showPermissionExplain) {
        PermissionExplainDialog(
            onRequest = onPermissionRequest,
            onDismiss = onDismissPermissionExplain
        )
        return
    }

    // ==================== 权限被拒绝对话框 ====================
    if (showPermissionDenied) {
        PermissionDeniedDialog(
            onGoSettings = {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                intent.data = Uri.parse("package:${context.packageName}")
                context.startActivity(intent)
                onDismissPermissionDenied()
            },
            onExit = {
                android.os.Process.killProcess(android.os.Process.myPid())
                System.exit(1)
            },
            onDismiss = onDismissPermissionDenied
        )
        return
    }

    // ==================== UI ====================
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        // ==================== 启动画面 ====================
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_launcher_foreground),
                contentDescription = "App Icon",
                modifier = Modifier.size(120.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "UIN Tool",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1A3A4A)
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "版本 ${Constants.APP_VERSION} (Build ${Constants.APP_VERSION_CODE})",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF9AA6B2)
            )

            if (isChecking) {
                Spacer(modifier = Modifier.height(24.dp))
                CircularProgressIndicator(
                    modifier = Modifier.size(32.dp),
                    color = Color(0xFF1A3A4A),
                    strokeWidth = 3.dp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "正在检查更新...",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF9AA6B2)
                )
            }
        }

        // ==================== 更新对话框 ====================
        if (showUpdateDialog && updateInfo != null) {
            UpdateDialog(
                releaseInfo = updateInfo!!,
                forceUpdate = isForceUpdate,
                onDismiss = {
                    if (!isForceUpdate) {
                        showUpdateDialog = false
                        onNavigate()
                    }
                },
                onDownload = {
                    showUpdateDialog = false
                    showDownloadProgress = true
                    onStartDownload(updateInfo!!)
                },
                onManualDownload = {
                    showUpdateDialog = false
                    onOpenBrowser("https://github.com/Undefined-Invalid-Null/UIN-Tool/releases/tag/${updateInfo!!.tagName}")
                    scope.launch {
                        delay(500)
                        onNavigate()
                    }
                },
                onIgnore = {
                    setVersionIgnored(preferenceManager, updateInfo!!.versionName)
                    showUpdateDialog = false
                    onNavigate()
                }
            )
        }

        // ==================== 下载进度对话框 ====================
        if (showDownloadProgress) {
            DownloadProgressDialog(
                progress = downloadProgress,
                onCancel = {
                    showDownloadProgress = false
                    onNavigate()
                }
            )
        }
    }
}

// ==================== 权限请求对话框 ====================

@Composable
fun PermissionRequestDialog(
    onRequest: () -> Unit,
    onExit: () -> Unit
) {
    Dialog(
        onDismissRequest = { },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .wrapContentHeight()
                .clip(RoundedCornerShape(24.dp)),
            colors = CardDefaults.cardColors(
                containerColor = Color.White
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 图标
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .background(
                            Color(0xFFE3F2FD),
                            RoundedCornerShape(36.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "📁", fontSize = 36.sp)
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "需要存储权限",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1A1A1A)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "UIN Tool 需要存储权限来正常运行：",
                    fontSize = 14.sp,
                    color = Color(0xFF666666),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Color(0xFFF5F7FA),
                            RoundedCornerShape(12.dp)
                        )
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    PermissionItem("📦", "导入/导出插件文件 (.tpk)")
                    PermissionItem("💾", "备份和恢复插件数据")
                    PermissionItem("📝", "保存运行日志到文件")
                    PermissionItem("📥", "下载应用更新")
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = onExit,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFEEEEEE),
                            contentColor = Color(0xFF666666)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("退出", fontSize = 15.sp)
                    }

                    Button(
                        onClick = onRequest,
                        modifier = Modifier
                            .weight(2f)
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF1A3A4A),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("授予权限", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "您的数据安全，我们不会访问个人文件",
                    fontSize = 11.sp,
                    color = Color(0xFF999999)
                )
            }
        }
    }
}

// ==================== 权限说明对话框 ====================

@Composable
fun PermissionExplainDialog(
    onRequest: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .wrapContentHeight()
                .clip(RoundedCornerShape(24.dp)),
            colors = CardDefaults.cardColors(
                containerColor = Color.White
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = "ℹ️", fontSize = 48.sp)

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "权限说明",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1A1A1A)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "UIN Tool 需要存储权限来：\n\n" +
                        "• 📦 导入/导出插件文件 (.tpk)\n" +
                        "• 💾 备份和恢复插件数据\n" +
                        "• 📝 保存运行日志\n" +
                        "• 📥 下载应用更新\n\n" +
                        "请授予存储权限以正常使用所有功能",
                    fontSize = 14.sp,
                    color = Color(0xFF444444),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFEEEEEE),
                            contentColor = Color(0xFF666666)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("退出", fontSize = 15.sp)
                    }

                    Button(
                        onClick = onRequest,
                        modifier = Modifier
                            .weight(2f)
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF1A3A4A),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("授予权限", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

// ==================== 权限被拒绝对话框 ====================

@Composable
fun PermissionDeniedDialog(
    onGoSettings: () -> Unit,
    onExit: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .wrapContentHeight()
                .clip(RoundedCornerShape(24.dp)),
            colors = CardDefaults.cardColors(
                containerColor = Color.White
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = "⚠️", fontSize = 48.sp)

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "权限被拒绝",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFD32F2F)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "您拒绝了存储权限，部分功能将无法正常使用。\n\n" +
                        "您可以在系统设置中手动开启权限：\n" +
                        "设置 → 应用 → UIN Tool → 权限 → 存储",
                    fontSize = 14.sp,
                    color = Color(0xFF444444),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = onExit,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFEEEEEE),
                            contentColor = Color(0xFF666666)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("退出", fontSize = 15.sp)
                    }

                    Button(
                        onClick = onGoSettings,
                        modifier = Modifier
                            .weight(2f)
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF1A3A4A),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("去设置", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

@Composable
fun PermissionItem(icon: String, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = icon,
            fontSize = 16.sp,
            modifier = Modifier.width(28.dp)
        )
        Text(
            text = text,
            fontSize = 13.sp,
            color = Color(0xFF333333)
        )
    }
}

// ==================== 更新对话框 ====================

@Composable
fun UpdateDialog(
    releaseInfo: ReleaseInfo,
    forceUpdate: Boolean,
    onDismiss: () -> Unit,
    onDownload: () -> Unit,
    onManualDownload: () -> Unit,
    onIgnore: () -> Unit
) {
    val context = LocalContext.current

    Dialog(
        onDismissRequest = { if (!forceUpdate) onDismiss() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight()
                .clip(RoundedCornerShape(20.dp)),
            colors = CardDefaults.cardColors(
                containerColor = Color.White
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Text(
                    text = if (forceUpdate) "⚠️ 强制更新" else "📦 发现新版本",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (forceUpdate) Color(0xFFD32F2F) else Color(0xFF1A3A4A)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "版本 ${releaseInfo.versionName}",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF1A1A1A)
                )

                Text(
                    text = "版本代码: ${releaseInfo.versionCode} | 大小: ${releaseInfo.getFormattedSize()} | 发布: ${releaseInfo.getFormattedDate()}",
                    fontSize = 12.sp,
                    color = Color(0xFF666666)
                )

                Spacer(modifier = Modifier.height(12.dp))

                if (!releaseInfo.releaseNotes.isNullOrEmpty()) {
                    Text(
                        text = "📝 更新日志",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF1A1A1A),
                        modifier = Modifier.padding(bottom = 4.dp)
                    )

                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                settings.apply {
                                    javaScriptEnabled = true
                                    domStorageEnabled = true
                                    loadWithOverviewMode = true
                                    useWideViewPort = true
                                    setSupportZoom(true)
                                    builtInZoomControls = true
                                    displayZoomControls = false
                                    defaultTextEncodingName = "UTF-8"
                                    cacheMode = WebSettings.LOAD_NO_CACHE
                                }

                                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                                    setLayerType(WebView.LAYER_TYPE_SOFTWARE, null)
                                }

                                webViewClient = object : WebViewClient() {
                                    override fun onPageFinished(view: WebView, url: String) {
                                        super.onPageFinished(view, url)
                                        Logger.d("UpdateDialog", "WebView 加载完成")
                                    }
                                }

                                val html = MarkdownRenderer.toHtml(releaseInfo.releaseNotes)

                                val styledHtml = """
                                    <html>
                                    <head>
                                        <meta name='viewport' content='width=device-width, initial-scale=1.0, maximum-scale=5.0, user-scalable=yes'>
                                        <style>
                                            body {
                                                padding: 8px;
                                                margin: 0;
                                                font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
                                                font-size: 13px;
                                                color: #333;
                                                line-height: 1.6;
                                                background: transparent;
                                            }
                                            h1 { font-size: 18px; margin: 8px 0; }
                                            h2 { font-size: 16px; margin: 6px 0; }
                                            h3 { font-size: 14px; margin: 4px 0; }
                                            p { margin: 4px 0; }
                                            ul, ol { margin: 4px 0; padding-left: 20px; }
                                            li { margin: 2px 0; }
                                            code { background: #f4f4f4; padding: 2px 4px; border-radius: 4px; font-size: 12px; }
                                            pre { background: #2d2d2d; padding: 8px; border-radius: 8px; overflow-x: auto; }
                                            pre code { background: transparent; color: #f8f8f2; }
                                            blockquote { margin: 8px 0; padding: 4px 12px; border-left: 3px solid #37474F; background: #f5f5f5; }
                                            a { color: #37474F; text-decoration: none; }
                                            table { width: 100%; border-collapse: collapse; margin: 8px 0; font-size: 12px; }
                                            th, td { border: 1px solid #ddd; padding: 4px 6px; text-align: left; }
                                            th { background: #f0f0f0; }
                                            img { max-width: 100%; height: auto; border-radius: 4px; }
                                        </style>
                                    </head>
                                    <body>
                                        $html
                                    </body>
                                    </html>
                                """.trimIndent()

                                loadDataWithBaseURL("file:///android_asset/", styledHtml, "text/html", "UTF-8", null)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 80.dp, max = 250.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFF5F5F5))
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onDownload,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF1A3A4A),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("📥 下载更新", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onManualDownload,
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFEEEEEE),
                                contentColor = Color(0xFF333333)
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("🌐 手动下载", fontSize = 13.sp)
                        }

                        if (!forceUpdate) {
                            Button(
                                onClick = onIgnore,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(40.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFFFEBEE),
                                    contentColor = Color(0xFFD32F2F)
                                ),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("暂不更新", fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==================== 下载进度对话框 ====================

@Composable
fun DownloadProgressDialog(
    progress: Int,
    onCancel: () -> Unit
) {
    Dialog(
        onDismissRequest = { },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .wrapContentHeight()
                .clip(RoundedCornerShape(20.dp)),
            colors = CardDefaults.cardColors(
                containerColor = Color.White
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "⏳ 正在下载更新",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1A3A4A)
                )

                Spacer(modifier = Modifier.height(16.dp))

                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = Color(0xFF1A3A4A),
                    trackColor = Color(0xFFE0E0E0)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "$progress%",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF1A3A4A)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onCancel,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFEEEEEE),
                        contentColor = Color(0xFF666666)
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("取消下载", fontSize = 14.sp)
                }
            }
        }
    }
}

// ==================== 工具函数 ====================

private fun isVersionIgnored(preferenceManager: PreferenceManager, versionName: String): Boolean {
    if (versionName.isEmpty()) return false
    val prefs = preferenceManager.getPrefs()
    val ignoredVersion = prefs.getString(Constants.KEY_IGNORE_VERSION, "")
    return versionName == ignoredVersion
}

private fun setVersionIgnored(preferenceManager: PreferenceManager, versionName: String) {
    if (versionName.isEmpty()) return
    val prefs = preferenceManager.getPrefs()
    prefs.edit().putString(Constants.KEY_IGNORE_VERSION, versionName).apply()
    Logger.i("SplashScreen", "忽略版本: $versionName")
}