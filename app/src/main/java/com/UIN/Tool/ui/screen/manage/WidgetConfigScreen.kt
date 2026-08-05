// app/src/main/java/com/UIN/Tool/ui/screen/manage/WidgetConfigScreen.kt
package com.UIN.Tool.ui.screen.manage

import com.UIN.Tool.utils.Str
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
                AppToast.showLong(this, Str.get(R.string.this_feature_requires_android_8_0))
                return
            }

            val componentName = ComponentName(this, WidgetProvider::class.java)

            if (!appWidgetManager.isRequestPinAppWidgetSupported()) {
                AppToast.showLong(this, Str.get(R.string.your_launcher_doesn_t_support_pinnin))
                return
            }

            AppLog.i(TAG, Str.get(R.string.adding_3x3_widget))
            appWidgetManager.requestPinAppWidget(componentName, null, null)

            AppToast.showLong(this, Str.get(R.string.place_the_widget_on_your_home_screen))

        } catch (e: Exception) {
            AppLog.e(TAG, Str.get(R.string.failed_to_add_widget_e_message, e.message), e)
            AppToast.error(this, Str.get(R.string.add_failed_e_message, e.message))
        }
    }

    private fun pinShortcut() {
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                AppToast.showLong(this, Str.get(R.string.this_feature_requires_android_8_0))
                return
            }

            val componentName = ComponentName(this, Widget1x1Provider::class.java)

            if (!appWidgetManager.isRequestPinAppWidgetSupported()) {
                AppToast.showLong(this, Str.get(R.string.your_launcher_doesn_t_support_pinnin_2))
                return
            }

            AppLog.i(TAG, Str.get(R.string.adding_shortcut))
            appWidgetManager.requestPinAppWidget(componentName, null, null)

            AppToast.showLong(this, Str.get(R.string.place_the_shortcut_on_your_home_scre))

        } catch (e: Exception) {
            AppLog.e(TAG, Str.get(R.string.failed_to_add_shortcut_e_message, e.message), e)
            AppToast.error(this, Str.get(R.string.add_failed_e_message, e.message))
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
            UIComponents.ManageTopAppBar(
                titleText = Str.get(R.string.widget),
                onBack = onBack
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
                    shape = RoundedCornerShape(AppDimens.cardCornerRadius),
                    colors = CardDefaults.cardColors(
                        containerColor = if (AppColors.glassEnabled())
                            AppColors.glassBackground()
                        else
                            MaterialTheme.colorScheme.surface
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
                                text = Str.get(R.string.shortcut),
                                fontSize = AppDimens.sectionTitleTextSize.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = Str.get(R.string.a_1x1_shortcut_that_opens_a_plugin_w),
                            fontSize = AppDimens.bodyTextSize.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 20.sp
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = Str.get(R.string.widget_1x1_desc),
                            fontSize = AppDimens.bodyTextSize.sp,
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
                            shape = RoundedCornerShape(AppDimens.buttonCornerRadius)
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = Str.get(R.string.add),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(Str.get(R.string.add_to_home_screen), fontSize = AppDimens.bodyTextSize.sp, fontWeight = FontWeight.Medium)
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
                    shape = RoundedCornerShape(AppDimens.cardCornerRadius),
                    colors = CardDefaults.cardColors(
                        containerColor = if (AppColors.glassEnabled())
                            AppColors.glassBackground()
                        else
                            MaterialTheme.colorScheme.surface
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
                                text = Str.get(R.string.widget_3x3_widget),
                                fontSize = AppDimens.sectionTitleTextSize.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = Str.get(R.string.a_3x3_widget_showing_multiple_plugin),
                            fontSize = AppDimens.bodyTextSize.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 20.sp
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = Str.get(R.string.widget_3x3_desc),
                            fontSize = AppDimens.bodyTextSize.sp,
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
                            shape = RoundedCornerShape(AppDimens.buttonCornerRadius)
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = Str.get(R.string.add),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(Str.get(R.string.add_to_home_screen), fontSize = AppDimens.bodyTextSize.sp, fontWeight = FontWeight.Medium)
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
                    shape = RoundedCornerShape(AppDimens.cardCornerRadius),
                    colors = CardDefaults.cardColors(
                        containerColor = if (AppColors.glassEnabled())
                            AppColors.glassBackground()
                        else
                            MaterialTheme.colorScheme.surfaceVariant
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
                                text = Str.get(R.string.how_to_use),
                                fontSize = AppDimens.bodyTextSize.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Text(
                            text = Str.get(R.string.widget_add_steps),
                            fontSize = AppDimens.bodyTextSize.sp,
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