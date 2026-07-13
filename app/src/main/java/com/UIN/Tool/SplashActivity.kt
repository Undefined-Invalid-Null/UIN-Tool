// app/src/main/java/com/UIN/Tool/SplashActivity.kt

package com.UIN.Tool

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.UIN.Tool.log.Logger
import com.UIN.Tool.ui.theme.UINToolTheme
import com.UIN.Tool.utils.Constants
import java.io.File

class SplashActivity : ComponentActivity() {

    companion object {
        private const val TAG = "SplashActivity"
        private const val SPLASH_DELAY = 1500L
    }

    private var hasRequestedPermission = false

    // 存储权限请求
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val allGranted = results.values.all { it }
        if (allGranted) {
            Logger.success(TAG, "所有存储权限已授予")
            createWorkDirectory()
            navigateToMain()
        } else {
            Logger.w(TAG, "部分权限被拒绝")
            Toast.makeText(this, "需要存储权限才能正常运行", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    // Android 11+ 管理所有文件权限
    private val manageStorageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                Logger.success(TAG, "管理所有文件权限已授予")
                createWorkDirectory()
                navigateToMain()
            } else {
                Logger.w(TAG, "管理所有文件权限被拒绝")
                Toast.makeText(this, "需要存储权限才能正常运行", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            UINToolTheme {
                SplashScreenContent()
            }
        }

        // 检查并请求权限
        if (!hasStoragePermission()) {
            requestStoragePermission()
        } else {
            createWorkDirectory()
            Handler(Looper.getMainLooper()).postDelayed({
                navigateToMain()
            }, SPLASH_DELAY)
        }
    }

    private fun hasStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestStoragePermission() {
        if (hasRequestedPermission) return
        hasRequestedPermission = true

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    intent.data = android.net.Uri.parse("package:$packageName")
                    manageStorageLauncher.launch(intent)
                } catch (e: Exception) {
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
        } else {
            navigateToMain()
        }
    }

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
                }
            }

            // 初始化日志
            Logger.init(Constants.LOG_DIR)
            Logger.i(TAG, "工作目录创建成功: ${Constants.WORK_DIR}")

        } catch (e: Exception) {
            Logger.e(TAG, "创建工作目录失败", e)
        }
    }

    private fun navigateToMain() {
        try {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
        } catch (e: Exception) {
            Logger.e(TAG, "导航失败", e)
            finish()
        }
    }
}

@Composable
fun SplashScreenContent() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White),
        contentAlignment = Alignment.Center
    ) {
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
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1A3A4A)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "版本 ${Constants.APP_VERSION}",
                fontSize = 14.sp,
                color = Color(0xFF9AA6B2)
            )

            Spacer(modifier = Modifier.height(16.dp))

            CircularProgressIndicator(
                modifier = Modifier.size(32.dp),
                color = Color(0xFF1A3A4A),
                strokeWidth = 3.dp
            )
        }
    }
}