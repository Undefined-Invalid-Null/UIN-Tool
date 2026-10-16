// app/src/main/java/com/UIN/Tool/ui/screen/dev/DevToolsScreen.kt
package com.UIN.Tool.ui.screen.dev

import com.UIN.Tool.R
import com.UIN.Tool.utils.Str
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.UIN.Tool.core.di.ServiceLocator
import com.UIN.Tool.ui.components.unified.*
import com.UIN.Tool.ui.theme.AppColors
import com.UIN.Tool.utils.AppLog
import com.UIN.Tool.utils.AppToast

private const val TAG = "DevToolsScreen"

/**
 * 开发工具页。
 *
 * 6.2.0 批次37：移除原先内嵌的「运行日志」区块（含其 TopBar 导出/清空、下拉刷新、
 * 崩溃提示、日志列表）。它与专门的运行日志页（`LogViewerScreen`，入口在「管理」页）
 * 是同一份逻辑的旧拷贝，两边各自维护、行为还不一致；日志相关能力（logcat 来源切换、
 * 导出、清空）现统一收敛到 `LogViewerScreen`。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevToolsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val pluginManager = ServiceLocator.getPluginManager()
    var nativeMultiInstance by remember {
        mutableStateOf(pluginManager.isNativeMultiInstanceEnabled())
    }
    var retainSession by remember {
        mutableStateOf(pluginManager.isSharedSessionRetainEnabled())
    }
    var ignoreSignature by remember {
        mutableStateOf(pluginManager.isDevIgnoreSignatureEnabled())
    }

    fun saveDeveloperOptions() {
        pluginManager.setNativeMultiInstanceEnabled(nativeMultiInstance)
        pluginManager.setSharedSessionRetainEnabled(retainSession)
        pluginManager.setDevIgnoreSignatureEnabled(ignoreSignature)
        AppToast.info(context, Str.get(R.string.dev_options_saved))
        AppLog.i(TAG, "developer options saved")
    }

    Scaffold(
        containerColor = AppColors.pageBackground(),
        topBar = {
            ManageTopAppBar(
                titleText = Str.get(R.string.dev_tools),
                onBack = onBack
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(8.dp)
        ) {
            UnifiedCard(modifier = Modifier.fillMaxWidth()) {
                UnifiedSectionTitle(Str.get(R.string.developer_options))
                Spacer(modifier = Modifier.height(4.dp))

                // 原生插件多开（实验性）
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = nativeMultiInstance,
                        onCheckedChange = { nativeMultiInstance = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = MaterialTheme.colorScheme.primary,
                            uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                    Text(
                        text = Str.get(R.string.dev_native_plugin_multi_instance),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = Str.get(R.string.dev_native_plugin_multi_instance_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))

                // 关闭时保留会话（共享端口模式）
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = retainSession,
                        onCheckedChange = { retainSession = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = MaterialTheme.colorScheme.primary,
                            uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                    Text(
                        text = Str.get(R.string.dev_shared_session_retain),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = Str.get(R.string.dev_shared_session_retain_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))

                // 忽略签名验证（仅开发用）
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = ignoreSignature,
                        onCheckedChange = { ignoreSignature = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = MaterialTheme.colorScheme.primary,
                            uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                    Text(
                        text = Str.get(R.string.dev_ignore_signature_verification),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = Str.get(R.string.dev_ignore_signature_verification_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                UnifiedButton(
                    text = Str.get(R.string.save),
                    onClick = { saveDeveloperOptions() },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
