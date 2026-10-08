// app/src/main/java/com/UIN/Tool/SplashActivity.kt
package com.UIN.Tool

import com.UIN.Tool.utils.Str
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
import android.widget.Toast
import android.graphics.drawable.ColorDrawable
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.UIN.Tool.R
import com.UIN.Tool.core.di.ServiceLocator
import com.UIN.Tool.core.update.UpdateChecker
import com.UIN.Tool.core.update.UpdateDownloader
import com.UIN.Tool.data.local.PreferenceManager
import com.UIN.Tool.domain.model.ReleaseInfo
import com.UIN.Tool.log.Logger
import com.UIN.Tool.ui.components.UpdateDialog
import com.UIN.Tool.ui.screen.onboarding.OnboardingActivity
import com.UIN.Tool.ui.theme.UINToolTheme
import com.UIN.Tool.utils.AppLog
import com.UIN.Tool.utils.UIConfig
import com.UIN.Tool.constants.AppConstants as Constants
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import com.UIN.Tool.ui.theme.AppDimens
import com.UIN.Tool.ui.theme.dialogBackgroundOf
import com.UIN.Tool.ui.theme.pageGradientBrush

private const val SPLASH_DELAY = 700L
private const val UPDATE_CHECK_TIMEOUT = 5000L

/** B-33：日常启动的更新检查只允许等这么久，超时直接进主界面（开屏不能被网络拖住） */
private const val UPDATE_CHECK_TIMEOUT_FAST = 1500L

class SplashActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "SplashActivity"
    }

    private lateinit var preferenceManager: PreferenceManager
    private var updateDownloader: UpdateDownloader? = null
    @Volatile private var workDirectoryReady = false
    @Volatile private var workDirectoryInProgress = false
    private var isFirstLaunch = false
    
    private var showPermissionExplain by mutableStateOf(true)
    private var showPermissionDenied by mutableStateOf(false)
    private var hasNavigated = false

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val allGranted = results.values.all { it }
        if (allGranted) {
            AppLog.success(TAG, Str.get(R.string.all_storage_permissions_granted))
            createWorkDirectory()
            // 关闭权限弹窗后由 LaunchedEffect(hasStoragePermission) 统一触发导航，避免双重导航
            showPermissionExplain = false
        } else {
            val deniedPermissions = results.filter { !it.value }.keys
            AppLog.w(TAG, Str.get(R.string.some_permissions_denied_deniedpermis, deniedPermissions))
            
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

    private val manageStorageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        checkPermissionAfterReturn()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 开屏窗口背景跟随应用主题模式（而非系统），避免首帧白屏/灰屏闪一下
        if (UIConfig.isInitialized()) {
            val uiConfig = UIConfig.getInstance()
            val dark = uiConfig.shouldUseDarkTheme()
            try {
                val colorString = if (dark) {
                    uiConfig.getColorStringDark("background")
                } else {
                    uiConfig.getColorString("background")
                }
                window.setBackgroundDrawable(ColorDrawable(android.graphics.Color.parseColor(colorString)))
            } catch (e: Exception) {
                AppLog.e(TAG, "set splash window background failed", e)
            }
        }

        preferenceManager = ServiceLocator.getPreferenceManager()
        isFirstLaunch = preferenceManager.isFirstLaunch()
        // 在 setContent 前根据实际权限状态初始化，避免权限弹窗首帧闪现
        showPermissionExplain = !hasStoragePermission()

        setContent {
            UINToolTheme {
                SplashScreenWithUpdate(
                    showPermissionExplain = showPermissionExplain,
                    showPermissionDenied = showPermissionDenied,
                    onRequestPermission = { requestStoragePermission() },
                    onDismissPermissionExplain = { showPermissionExplain = false },
                    onDismissPermissionDenied = { showPermissionDenied = false },
                    onNavigate = { navigateToNext() },
                    onOpenBrowser = { url ->
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            startActivity(intent)
                        } catch (e: Exception) {
                            AppLog.e(TAG, Str.get(R.string.failed_to_open_browser), e)
                            Toast.makeText(this, Str.get(R.string.unable_to_open_link), Toast.LENGTH_SHORT).show()
                        }
                    },
                    onStartDownload = { releaseInfo ->
                        startDownload(releaseInfo)
                    },
                    isFirstLaunch = isFirstLaunch,
                    hasStoragePermission = hasStoragePermission()
                )
            }
        }

        if (hasStoragePermission()) {
            createWorkDirectory()
        }
    }

    /**
     * B-28/G-17：权限复检。
     * 1) 已授权 → 收起所有权限弹窗，交给 LaunchedEffect 继续导航；
     * 2) 未授权且当前没有任何可操作弹窗（典型场景：从系统设置返回却未授权）
     *    → 重新显示权限说明，避免永久停留在无按钮的开屏页。
     */
    override fun onResume() {
        super.onResume()
        if (hasStoragePermission()) {
            showPermissionExplain = false
            showPermissionDenied = false
            if (!workDirectoryReady) createWorkDirectory()
        } else if (!showPermissionExplain && !showPermissionDenied) {
            showPermissionExplain = true
        }
    }

    private fun checkPermissionAfterReturn() {
        if (hasStoragePermission()) {
            AppLog.success(TAG, Str.get(R.string.permission_granted))
            showPermissionExplain = false
            showPermissionDenied = false
            createWorkDirectory()
            // 状态变更后由 LaunchedEffect(hasStoragePermission) 统一触发导航
        } else {
            AppLog.w(TAG, Str.get(R.string.permission_still_not_granted))
            showPermissionExplain = false
            showPermissionDenied = true
        }
    }

    private fun hasStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestStoragePermission() {
        AppLog.i(TAG, Str.get(R.string.request_storage_permission))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    intent.data = Uri.parse("package:$packageName")
                    manageStorageLauncher.launch(intent)
                } catch (e: Exception) {
                    AppLog.e(TAG, Str.get(R.string.failed_to_open_storage_permission), e)
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

    private fun createWorkDirectory() {
        // B-35：批量 mkdirs + Logger.init 是首启最慢路径，移出主线程；
        // workDirectoryReady/inProgress 两道闸避免重复进入（onResume 可能连触）
        if (workDirectoryReady || workDirectoryInProgress) return
        workDirectoryInProgress = true
        lifecycleScope.launch(Dispatchers.IO) {
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
                        AppLog.d(TAG, Str.get(R.string.creating_directory_path, path))
                    }
                }

                // 拍板2.0（下60）方向回调：下58 曾迁应用私有目录，此处反向兜底——
                // 私有侧若有迁移残留（跑过迁移版本的构建）搬回共享工作目录；
                // 从未跑过迁移的设备私有侧不存在 → 零开销。幂等：同名已存在即作废旧件
                //（仅同日日志可能与 Logger.init 新建件碰撞），迁空后删除私有子目录。
                listOf("logs", "backups", "tpk").forEach { name ->
                    val privateDir = getExternalFilesDir(null)?.let { File(it, name) }
                    if (privateDir == null || !privateDir.isDirectory) return@forEach
                    val target = File(Constants.WORK_DIR, name)
                    target.mkdirs()
                    var moved = 0
                    privateDir.listFiles()?.forEach { src ->
                        if (!src.isFile) return@forEach
                        val dst = File(target, src.name)
                        when {
                            dst.exists() -> src.delete()
                            src.renameTo(dst) -> moved++
                            else -> runCatching { src.copyTo(dst); src.delete() }
                                .onSuccess { moved++ }
                        }
                    }
                    privateDir.delete()
                    if (moved > 0) {
                        AppLog.i(TAG, Str.get(R.string.storage_migration_moved, name, moved))
                    }
                }

                Logger.init(Constants.LOG_DIR)
                AppLog.i(TAG, "══════════════════════════════════════════════════")
                AppLog.i(TAG, Str.get(R.string.app_launch_uin_tool_v_constants_app_, Constants.APP_VERSION))
                AppLog.i(TAG, Str.get(R.string.work_directory_constants_work_dir, Constants.WORK_DIR))
                AppLog.i(TAG, "══════════════════════════════════════════════════")

                preferenceManager.setWorkFolder(Constants.WORK_DIR)
                AppLog.success(TAG, Str.get(R.string.work_directory_created))
                workDirectoryReady = true

            } catch (e: Exception) {
                AppLog.e(TAG, Str.get(R.string.failed_to_create_work_directory), e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@SplashActivity, Str.get(R.string.failed_to_create_directory_e_message, e.message), Toast.LENGTH_LONG).show()
                }
            } finally {
                workDirectoryInProgress = false
            }
        }
    }

    private fun startDownload(releaseInfo: ReleaseInfo) {
        AppLog.enter(TAG, "startDownload")

        if (releaseInfo.downloadUrl.isNullOrEmpty()) {
            AppLog.e(TAG, Str.get(R.string.download_link_is_empty))
            Toast.makeText(this, Str.get(R.string.invalid_download_link_please_try_again), Toast.LENGTH_SHORT).show()
            return
        }

        // B-21：统一用 ServiceLocator 单例（避免每次下载 new 一个 OkHttp/下载线程体系）
        updateDownloader = ServiceLocator.getUpdateDownloader()
        updateDownloader?.setOnDownloadListener(object : UpdateDownloader.OnDownloadListener {
            override fun onStart() {
                AppLog.d(TAG, Str.get(R.string.start_download))
            }

            override fun onProgress(progress: Int, downloaded: Long, total: Long) {}

            override fun onSuccess(file: File) {
                AppLog.success(TAG, Str.get(R.string.download_successful))
                updateDownloader?.installApk(file)
            }

            override fun onFailed(error: String) {
                AppLog.e(TAG, Str.get(R.string.download_failed_error_2, error))
                // B-31：onFailed 在下载线程回调，原生 Toast 在 Android 11+ 非主线程直接抛异常
                // （被 UinApplication 全局处理器吞掉 → 下载失败无任何提示）
                com.UIN.Tool.utils.AppToast.error(this@SplashActivity, Str.get(R.string.download_failed_error_2, error))
            }
        })

        updateDownloader?.startDownload(releaseInfo.downloadUrl, releaseInfo.versionName, releaseInfo.sha256)
    }

    private fun navigateToNext() {
        if (hasNavigated) return
        hasNavigated = true
        try {
            val lastVersion = preferenceManager.getLastVersion()
            val currentVersion = Constants.APP_VERSION
            val isVersionUpdated = lastVersion != currentVersion && !isFirstLaunch

            if (isFirstLaunch) {
                preferenceManager.setFirstLaunch(false)
            }
            preferenceManager.setLastVersion(currentVersion)

            val intent = if (isFirstLaunch || isVersionUpdated) {
                Intent(this, OnboardingActivity::class.java).apply {
                    putExtra("is_version_update", isVersionUpdated)
                    putExtra("version_name", currentVersion)
                    putExtra("release_notes", preferenceManager.getLastChangelog())
                }
            } else {
                Intent(this, MainActivity::class.java)
            }

            startActivity(intent)
            finish()

            AppLog.exit(TAG, "navigateToNext", System.currentTimeMillis())
        } catch (e: Exception) {
            AppLog.e(TAG, Str.get(R.string.navigation_failed), e)
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        updateDownloader?.cancelDownload()
        updateDownloader?.clearDownloadListener()
        updateDownloader = null
        AppLog.d(TAG, "onDestroy")
    }
}

// ==================== Compose UI ====================

@Composable
fun SplashScreenWithUpdate(
    showPermissionExplain: Boolean,
    showPermissionDenied: Boolean,
    onRequestPermission: () -> Unit,
    onDismissPermissionExplain: () -> Unit,
    onDismissPermissionDenied: () -> Unit,
    onNavigate: () -> Unit,
    onOpenBrowser: (String) -> Unit,
    onStartDownload: (ReleaseInfo) -> Unit,
    isFirstLaunch: Boolean,
    hasStoragePermission: Boolean
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val preferenceManager = ServiceLocator.getPreferenceManager()
    val colorScheme = MaterialTheme.colorScheme

    var showUpdateDialog by remember { mutableStateOf(false) }
    var updateInfo by remember { mutableStateOf<ReleaseInfo?>(null) }
    var isForceUpdate by remember { mutableStateOf(false) }
    var showDownloadProgress by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableStateOf(0) }

    // B-58：checker 是单例，离开开屏必须清掉监听，否则回跳/销毁后仍回调本组合
    DisposableEffect(Unit) {
        onDispose { ServiceLocator.getUpdateChecker().clearUpdateListener() }
    }

    LaunchedEffect(hasStoragePermission, isFirstLaunch) {
        if (hasStoragePermission) {
            delay(SPLASH_DELAY)

            // B-33：原实现只在 isFirstLaunch 检查更新 —— 首次安装时根本不存在新版本，
            // 「开屏更新弹窗」几乎必然形同虚设；改为每次开屏都查，但等待窗口区分：
            // 首启/进引导页可多等（5s），日常启动最多 1.5s，超时直接进主界面
            // （MainActivity 的每日静默检查会兜底，不会因此漏掉更新提示）。
            val updateChecker = ServiceLocator.getUpdateChecker()
            var isCompleted = false
            val timeout = if (isFirstLaunch) UPDATE_CHECK_TIMEOUT else UPDATE_CHECK_TIMEOUT_FAST

            // B-32：原 Handler.postDelayed 超时未随生命周期取消，组合销毁后仍回调 onNavigate()；
            // 改成同一 LaunchedEffect 内的协程，离开组合即自动取消
            launch {
                delay(timeout)
                if (!isCompleted) {
                    isCompleted = true
                    AppLog.w("SplashScreen", Str.get(R.string.update_check_timed_out_update_check_, timeout))
                    onNavigate()
                }
            }

            updateChecker.setOnUpdateListener(object : UpdateChecker.OnUpdateListener {
                override fun onCheckStart() {
                    AppLog.d("SplashScreen", Str.get(R.string.start_update_check))
                }

                override fun onCheckSuccess(releases: List<ReleaseInfo>, hasNewer: Boolean, forceUpdate: Boolean) {
                    if (isCompleted) return
                    isCompleted = true
                    // B-33：开屏已联网检查过 → 记为当日已检查，MainActivity 的每日静默检查不再重复请求
                    preferenceManager.setLastUpdateCheckDay(java.time.LocalDate.now().toEpochDay())

                    if (hasNewer && releases.isNotEmpty()) {
                        val latest = releases.first()

                        if (!forceUpdate && isVersionIgnored(preferenceManager, latest.versionName)) {
                            AppLog.i("SplashScreen", Str.get(R.string.user_ignored_version_latest_versionn, latest.versionName))
                            onNavigate()
                            return
                        }

                        // 持久化最新变更日志，供开屏（onboarding）展示 Markdown 更新说明
                        preferenceManager.setLastChangelog(latest.releaseNotes ?: "")

                        updateInfo = latest
                        isForceUpdate = forceUpdate
                        showUpdateDialog = true
                    } else {
                        onNavigate()
                    }
                }

                override fun onCheckFailed(error: String) {
                    if (isCompleted) return
                    isCompleted = true
                    AppLog.e("SplashScreen", Str.get(R.string.update_check_failed_error_2, error))
                    onNavigate()
                }

                override fun onNoUpdate(currentVersion: String) {
                    if (isCompleted) return
                    isCompleted = true
                    preferenceManager.setLastUpdateCheckDay(java.time.LocalDate.now().toEpochDay())
                    AppLog.i("SplashScreen", Str.get(R.string.you_are_on_the_latest_version_curren, currentVersion))
                    onNavigate()
                }
            })

            updateChecker.checkUpdate()
        }
    }

    if (showPermissionExplain) {
        PermissionExplainDialog(
            onRequest = onRequestPermission,
            onDismiss = onDismissPermissionExplain
        )
        return
    }

    if (showPermissionDenied) {
        PermissionDeniedDialog(
            onGoSettings = {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                intent.data = Uri.parse("package:${context.packageName}")
                context.startActivity(intent)
                onDismissPermissionDenied()
            },
            onExit = {
                // B-34：killProcess+System.exit(1) 是硬杀，改 finishAffinity 走正常生命周期
                (context as? android.app.Activity)?.finishAffinity()
            },
            onDismiss = onDismissPermissionDenied
        )
        return
    }

    // ==================== 启动画面（适配深色模式，深色用深灰色背景） ====================
    val iconAlpha = remember { Animatable(0f) }
    val iconScale = remember { Animatable(0.7f) }
    LaunchedEffect(Unit) {
        launch { iconScale.animateTo(1f, tween(durationMillis = 450)) }
        iconAlpha.animateTo(1f, tween(durationMillis = 450))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(
                pageGradientBrush()?.let { brush ->
                    Modifier.background(brush)
                } ?: Modifier.background(MaterialTheme.colorScheme.background)
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_splash_foreground),
                contentDescription = Str.get(R.string.app_icon_desc),
                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .size(120.dp)
                    .graphicsLayer {
                        alpha = iconAlpha.value
                        scaleX = iconScale.value
                        scaleY = iconScale.value
                    }
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = Str.get(R.string.app_name),
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = Str.get(R.string.version_constants_app_version_build_, Constants.APP_VERSION, Constants.APP_VERSION_CODE),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
        }

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
                    onOpenBrowser(ServiceLocator.getUpdateChecker().getReleasePageUrl(updateInfo!!.tagName))
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

// ==================== 权限说明对话框 ====================

@Composable
fun PermissionExplainDialog(
    onRequest: () -> Unit,
    onDismiss: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight()
                .then(Modifier.dialogBackgroundOf(RoundedCornerShape(AppDimens.cardCornerRadius))),
            shape = RoundedCornerShape(AppDimens.cardCornerRadius),
            border = null,
            colors = CardDefaults.cardColors(
                containerColor = Color.Transparent
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(
                            colorScheme.primaryContainer.copy(alpha = 0.3f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Folder,
                        contentDescription = null,
                        modifier = Modifier.size(36.dp),
                        tint = colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = Str.get(R.string.storage_permission_required),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = Str.get(R.string.uin_tool_needs_storage_permission_for),
                    fontSize = 14.sp,
                    color = colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            colorScheme.surfaceVariant,
                            RoundedCornerShape(AppDimens.radiusMedium)
                        )
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    PermissionItem(
                        icon = Icons.Outlined.FileCopy,
                        text = Str.get(R.string.import_export_plugin_files)
                    )
                    PermissionItem(
                        icon = Icons.Outlined.Backup,
                        text = Str.get(R.string.back_up_and_restore_plugin_data)
                    )
                    PermissionItem(
                        icon = Icons.Outlined.Description,
                        text = Str.get(R.string.save_runtime_logs_to_a_file)
                    )
                    PermissionItem(
                        icon = Icons.Outlined.Download,
                        text = Str.get(R.string.download_app_updates)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = Str.get(R.string.after_tapping_grant_allow_storage),
                    fontSize = 12.sp,
                    color = colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = {
                            // B-34：同上，改 finishAffinity
                            (context as? android.app.Activity)?.finishAffinity()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colorScheme.surfaceVariant,
                            contentColor = colorScheme.onSurfaceVariant
                        ),
                        shape = RoundedCornerShape(AppDimens.buttonCornerRadius)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(Str.get(R.string.exit), fontSize = 15.sp)
                    }

                    Button(
                        onClick = onRequest,
                        modifier = Modifier
                            .weight(2f)
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colorScheme.primary,
                            contentColor = colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(AppDimens.buttonCornerRadius)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(Str.get(R.string.grant_2), fontSize = 15.sp, fontWeight = FontWeight.Medium)
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
    val colorScheme = MaterialTheme.colorScheme
    
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
                .then(Modifier.dialogBackgroundOf(RoundedCornerShape(AppDimens.cardCornerRadius))),
            shape = RoundedCornerShape(AppDimens.cardCornerRadius),
            border = null,
            colors = CardDefaults.cardColors(
                containerColor = Color.Transparent
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(
                            colorScheme.errorContainer
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Warning,
                        contentDescription = null,
                        modifier = Modifier.size(36.dp),
                        tint = colorScheme.onErrorContainer
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = Str.get(R.string.permission_denied),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.error
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = Str.get(R.string.you_denied_storage_permission_some),
                    fontSize = 14.sp,
                    color = colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = Str.get(R.string.you_can_enable_the_permission_manually),
                    fontSize = 13.sp,
                    color = colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            colorScheme.surfaceVariant,
                            RoundedCornerShape(AppDimens.radiusMedium)
                        )
                        .padding(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = Str.get(R.string.settings_apps_uin_tool_permissions),
                            fontSize = 13.sp,
                            color = colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
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
                            containerColor = colorScheme.surfaceVariant,
                            contentColor = colorScheme.onSurfaceVariant
                        ),
                        shape = RoundedCornerShape(AppDimens.buttonCornerRadius)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(Str.get(R.string.exit), fontSize = 15.sp)
                    }

                    Button(
                        onClick = onGoSettings,
                        modifier = Modifier
                            .weight(2f)
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colorScheme.primary,
                            contentColor = colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(AppDimens.buttonCornerRadius)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(Str.get(R.string.go_to_settings), fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

// ==================== 权限项组件 ====================

@Composable
fun PermissionItem(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    val colorScheme = MaterialTheme.colorScheme
    
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = colorScheme.primary
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = text,
            fontSize = 13.sp,
            color = colorScheme.onSurface
        )
    }
}

// ==================== 更新对话框（已移至 ui/components/UpdateContent.kt） ====================

// ==================== 下载进度对话框 ====================

@Composable
fun DownloadProgressDialog(
    progress: Int,
    onCancel: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    
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
                .then(Modifier.dialogBackgroundOf(RoundedCornerShape(AppDimens.cardCornerRadius))),
            shape = RoundedCornerShape(AppDimens.cardCornerRadius),
            border = null,
            colors = CardDefaults.cardColors(
                containerColor = Color.Transparent
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Download,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = Str.get(R.string.downloading_update),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(AppDimens.radiusSmall)),
                    color = colorScheme.primary,
                    trackColor = colorScheme.surfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "$progress%",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = colorScheme.primary
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onCancel,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colorScheme.surfaceVariant,
                        contentColor = colorScheme.onSurfaceVariant
                    ),
                    shape = RoundedCornerShape(AppDimens.buttonCornerRadius)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(Str.get(R.string.cancel_download), fontSize = 14.sp)
                }
            }
        }
    }
}

// ==================== 工具函数 ====================

// B-36：忽略版本的读写只保留 PreferenceManager 一套（原先直读 SharedPreferences + KEY_IGNORE_VERSION，
// 与 PreferenceManager.get/setIgnoredVersion 并存两套实现）
private fun isVersionIgnored(preferenceManager: PreferenceManager, versionName: String): Boolean {
    if (versionName.isEmpty()) return false
    return versionName == preferenceManager.getIgnoredVersion()
}

private fun setVersionIgnored(preferenceManager: PreferenceManager, versionName: String) {
    if (versionName.isEmpty()) return
    preferenceManager.setIgnoredVersion(versionName)
    AppLog.i("SplashScreen", Str.get(R.string.ignore_version_versionname, versionName))
}