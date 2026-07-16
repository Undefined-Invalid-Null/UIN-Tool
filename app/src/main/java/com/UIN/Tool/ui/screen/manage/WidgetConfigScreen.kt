// app/src/main/java/com/UIN/Tool/ui/screen/manage/WidgetConfigScreen.kt
package com.UIN.Tool.ui.screen.manage

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.UIN.Tool.R
import com.UIN.Tool.log.Logger
import com.UIN.Tool.ui.components.UIComponents
import com.UIN.Tool.ui.theme.AppColors
import com.UIN.Tool.ui.theme.AppDimens
import com.UIN.Tool.ui.theme.UINToolTheme
import com.UIN.Tool.utils.AppLog
import com.UIN.Tool.utils.AppToast
import com.UIN.Tool.widget.Widget1x1Provider
import com.UIN.Tool.widget.WidgetProvider

private const val TAG = "WidgetConfigScreen"

@OptIn(ExperimentalMaterial3Api::class)
class WidgetConfigActivity : ComponentActivity() {

    private lateinit var appWidgetManager: AppWidgetManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        appWidgetManager = AppWidgetManager.getInstance(this)

        setContent {
            UINToolTheme {
                WidgetConfigScreen(
                    onBack = { finish() },
                    onAddWidget = { pinWidget() },
                    onAddShortcut = { pinShortcut() }
                )
            }
        }
    }

    private fun pinWidget() {
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                AppToast.showLong(this, "Android 8.0+ 才支持此功能")
                return
            }

            val componentName = ComponentName(this, WidgetProvider::class.java)

            if (!appWidgetManager.isRequestPinAppWidgetSupported()) {
                AppToast.showLong(this, "当前启动器不支持固定小部件，请长按桌面手动添加")
                return
            }

            AppLog.i(TAG, "开始添加3x3小部件")
            appWidgetManager.requestPinAppWidget(componentName, null, null)

            AppToast.showLong(this, "请在桌面放置小部件")

        } catch (e: Exception) {
            AppLog.e(TAG, "添加小部件失败: ${e.message}", e)
            AppToast.error(this, "添加失败: ${e.message}")
        }
    }

    private fun pinShortcut() {
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                AppToast.showLong(this, "Android 8.0+ 才支持此功能")
                return
            }

            val componentName = ComponentName(this, Widget1x1Provider::class.java)

            if (!appWidgetManager.isRequestPinAppWidgetSupported()) {
                AppToast.showLong(this, "当前启动器不支持固定快捷方式，请长按桌面手动添加")
                return
            }

            AppLog.i(TAG, "开始添加快捷方式")
            appWidgetManager.requestPinAppWidget(componentName, null, null)

            AppToast.showLong(this, "请在桌面放置快捷方式")

        } catch (e: Exception) {
            AppLog.e(TAG, "添加快捷方式失败: ${e.message}", e)
            AppToast.error(this, "添加失败: ${e.message}")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetConfigScreen(
    onBack: () -> Unit,
    onAddWidget: () -> Unit,
    onAddShortcut: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "小部件",
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // ============================================================
            // 快捷方式卡片
            // ============================================================
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Shortcut,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "快捷方式",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "1x1 快捷方式，点击直接打开插件，快速访问常用功能",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 20.sp
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "• 占用桌面空间小，适合常用插件\n" +
                                   "• 点击即开，无需进入应用\n" +
                                   "• 可放置多个，每个指向不同插件",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 18.sp
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = onAddShortcut,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = "添加",
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("添加到桌面", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }

            // ============================================================
            // 3x3 小部件卡片
            // ============================================================
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Widgets,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "3x3 小部件",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "3x3 小部件，在桌面展示多个插件，一目了然",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 20.sp
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "• 同时显示 3 个插件，方便快速切换\n" +
                                   "• 自动轮播展示插件状态\n" +
                                   "• 点击插件卡片直接进入对应功能",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 18.sp
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = onAddWidget,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = "添加",
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("添加到桌面", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }

            // ============================================================
            // 底部说明
            // ============================================================
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 8.dp)
                        ) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "使用说明",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Text(
                            text = "1. 点击「添加到桌面」按钮，系统会提示选择放置位置\n" +
                                   "2. 在桌面拖动小部件可调整位置\n" +
                                   "3. 长按小部件可调整大小或移除\n" +
                                   "4. 快捷方式点击后直接打开插件，无需进入应用",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 20.sp
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}