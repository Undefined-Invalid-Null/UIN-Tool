// ui/screen/dev/DevScreen.kt
package com.UIN.Tool.ui.screen.dev

import com.UIN.Tool.R
import com.UIN.Tool.utils.Str
import android.content.Intent
// ui/screen/dev/DevScreen.kt - 添加缺失的 import
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.UIN.Tool.app.TermuxActivity
import com.UIN.Tool.app.activities.SettingsActivity
import com.UIN.Tool.log.Logger
import com.UIN.Tool.ui.components.UIComponents
import com.UIN.Tool.ui.screen.docs.DocBrowserActivity
import com.UIN.Tool.ui.theme.AppColors
import com.UIN.Tool.utils.AppLog
import com.UIN.Tool.utils.AppToast
import com.UIN.Tool.constants.AppConstants as Constants
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "DevScreen"

@Composable
fun DevScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    var isExporting by remember { mutableStateOf(false) }

    // 创建插件对话框状态
    var showCreatePluginDialog by remember { mutableStateOf(false) }
    var selectedUiType by remember { mutableStateOf("") }
    var showBackendDialog by remember { mutableStateOf(false) }
    var selectedBackend by remember { mutableStateOf("") }
    var isWebViewOnly by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        UIComponents.TitleText(Str.get(R.string.developer))

        // ============================================================
        // 终端卡片
        // ============================================================
        UIComponents.Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            UIComponents.SectionTitle(Str.get(R.string.terminal))
            Text(
                text = Str.get(R.string.a_full_linux_terminal_environment_nb),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                UIComponents.PrimaryButton(
                    text = Str.get(R.string.open_terminal),
                    icon = Icons.Default.Terminal,
                    onClick = {
                        try {
                            val intent = Intent(context, TermuxActivity::class.java)
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(intent)
                            AppLog.i(TAG, Str.get(R.string.terminal_started))
                        } catch (e: Exception) {
                            AppLog.e(TAG, Str.get(R.string.failed_to_start_terminal), e)
                            AppToast.error(context, Str.get(R.string.failed_to_start_terminal_e_message, e.message))
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
                UIComponents.SecondaryButton(
                    text = Str.get(R.string.terminal_settings),
                    icon = Icons.Default.Settings,
                    onClick = {
                        try {
                            val intent = Intent(context, SettingsActivity::class.java)
                            context.startActivity(intent)
                            AppLog.i(TAG, Str.get(R.string.open_terminal_settings))
                        } catch (e: Exception) {
                            AppLog.e(TAG, Str.get(R.string.failed_to_open_terminal_settings), e)
                            AppToast.error(context, Str.get(R.string.failed_to_open_settings_e_message, e.message))
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // ============================================================
        // 插件开发工具卡片
        // ============================================================
        UIComponents.Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            UIComponents.SectionTitle(Str.get(R.string.plugin_development_tools))
            Text(
                text = Str.get(R.string.create_uin_tool_plugins_with_native_),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            UIComponents.PrimaryButton(
                text = Str.get(R.string.create_plugin),
                icon = Icons.Default.Add,
                onClick = { showCreatePluginDialog = true },
                modifier = Modifier.fillMaxWidth()
            )

            UIComponents.SecondaryButton(
                text = if (isExporting) Str.get(R.string.exporting) else Str.get(R.string.export_template),
                icon = Icons.Default.FileDownload,
                onClick = {
                    if (!isExporting) {
                        isExporting = true
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                exportTemplates(context)
                            }
                            isExporting = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
        }

        // ============================================================
        // 快速开始卡片
        // ============================================================
        UIComponents.Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            UIComponents.SectionTitle(Str.get(R.string.quick_start))
            Text(
                text = Str.get(R.string.dev_create_plugin_steps),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            UIComponents.SecondaryButton(
                text = Str.get(R.string.view_development_docs),
                icon = Icons.Default.Info,
                onClick = {
                    try {
                        val intent = Intent(context, DocBrowserActivity::class.java)
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        AppToast.warning(context, Str.get(R.string.docs_feature_under_development))
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(80.dp))
    }

    // ============================================================
    // 创建插件对话框（白色背景）
    // ============================================================
    if (showCreatePluginDialog) {
        AlertDialog(
            onDismissRequest = { showCreatePluginDialog = false },
            containerColor = if (AppColors.glassEnabled())
                AppColors.glassBackground()
            else
                MaterialTheme.colorScheme.surface,
            title = {
                Text(
                    Str.get(R.string.choose_frontend_type),
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Text(
                    Str.get(R.string.choose_the_plugin_frontend_ui_type),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 原生 UI
                    UIComponents.PrimaryButton(
                        text = Str.get(R.string.native_ui_android_view),
                        onClick = {
                            selectedUiType = "native"
                            showCreatePluginDialog = false
                            navigateToWizard(context, "native", "")
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    // ✅ 纯 WebView（无后端）
                    UIComponents.PrimaryButton(
                        text = Str.get(R.string.web_ui_frontend_only_no_backend),
                        onClick = {
                            selectedUiType = "web"
                            isWebViewOnly = true
                            showCreatePluginDialog = false
                            // 无后端，直接跳转
                            navigateToWizard(context, "web", "")
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    // WebView + 后端
                    UIComponents.PrimaryButton(
                        text = Str.get(R.string.web_ui_backend),
                        onClick = {
                            selectedUiType = "web"
                            isWebViewOnly = false
                            showCreatePluginDialog = false
                            showBackendDialog = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    // ✅ CUI 终端
                    UIComponents.PrimaryButton(
                        text = Str.get(R.string.cui_terminal_command_line),
                        onClick = {
                            selectedUiType = "cui"
                            isWebViewOnly = false
                            showCreatePluginDialog = false
                            navigateToWizard(context, "cui", "")
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    TextButton(
                        onClick = { showCreatePluginDialog = false },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Text(Str.get(R.string.cancel))
                    }
                }
            },
            dismissButton = null
        )
    }

    // ============================================================
    // 后端选择对话框（白色背景）
    // ============================================================
    if (showBackendDialog) {
        AlertDialog(
            onDismissRequest = { showBackendDialog = false },
            containerColor = if (AppColors.glassEnabled())
                AppColors.glassBackground()
            else
                MaterialTheme.colorScheme.surface,
            title = {
                Text(
                    Str.get(R.string.choose_backend_language),
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Text(
                    Str.get(R.string.choose_the_web_plugin_backend_langua),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val backends = listOf(
                        "python" to "Python",
                        "node" to "Node.js",
                        "php" to "PHP",
                        "binary" to Str.get(R.string.binary_file),
                        "other" to Str.get(R.string.custom_manual_start)
                    )
                    backends.forEach { (key, label) ->
                        UIComponents.PrimaryButton(
                            text = label,
                            onClick = {
                                selectedBackend = key
                                showBackendDialog = false
                                navigateToWizard(context, "web", key)
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    TextButton(
                        onClick = { showBackendDialog = false },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Text(Str.get(R.string.cancel))
                    }
                }
            },
            dismissButton = null
        )
    }
}

// ============================================================
// 导航到向导页面
// ============================================================
private fun navigateToWizard(context: android.content.Context, uiType: String, backend: String) {
    try {
        val intent = Intent(context, BasePluginWizardActivity::class.java).apply {
            putExtra("ui_type", uiType)
            putExtra("backend_type", backend)
        }
        context.startActivity(intent)
        AppLog.i(TAG, Str.get(R.string.launching_plugin_wizard_uitype_uityp, uiType, backend))
    } catch (e: Exception) {
        AppLog.e(TAG, Str.get(R.string.failed_to_open_plugin_wizard), e)
        AppToast.error(context, Str.get(R.string.feature_under_development_e_message, e.message))
    }
}

// ============================================================
// 导出模板（从 assets/test_plugins 复制打包好的插件作为模板）
// ============================================================
private fun exportTemplates(
    context: android.content.Context
) {
    try {
        val assetDir = "test_plugins"
        val tpkNames = context.assets.list(assetDir)
            ?.filter { it.endsWith(Constants.PLUGIN_EXTENSION) }
            ?.sorted()
            ?: emptyList()

        if (tpkNames.isEmpty()) {
            AppToast.warning(context, Str.get(R.string.no_tpk_templates_in_assets_test_plug))
            return
        }

        val templateDir = File(Constants.WORK_DIR, "templates")
        if (!templateDir.exists()) {
            templateDir.mkdirs()
        }

        var successCount = 0
        var failCount = 0
        val exportedNames = mutableListOf<String>()

        for (name in tpkNames) {
            try {
                val destFile = File(templateDir, name)
                context.assets.open("$assetDir/$name").use { input ->
                    java.io.FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                }
                successCount++
                exportedNames.add(name)
                AppLog.d(TAG, Str.get(R.string.exporting_template_file_name, name))
            } catch (e: Exception) {
                AppLog.w(TAG, Str.get(R.string.failed_to_copy_file_name_e_message, name, e.message))
                failCount++
            }
        }

        val readmeFile = File(templateDir, "README.txt")
        readmeFile.writeText(buildTemplateReadme(exportedNames))

        val message = if (failCount == 0) {
            Str.get(R.string.exported_successcount_plugin_templat, successCount, templateDir.absolutePath)
        } else {
            Str.get(R.string.export_done_successcount_succeeded_f, successCount, failCount, templateDir.absolutePath)
        }

        AppToast.showLong(context, message)
        AppLog.success(TAG, Str.get(R.string.template_export_done_templatedir_abs, templateDir.absolutePath, successCount, failCount))

    } catch (e: Exception) {
        AppLog.e(TAG, Str.get(R.string.failed_to_export_template), e)
        AppToast.error(context, Str.get(R.string.export_failed_e_message, e.message))
    }
}

// ============================================================
// 生成模板 README
// ============================================================
private fun buildTemplateReadme(files: List<String>): String {
    val descMap = mapOf(
        "com.example.cuitest.tpk" to Str.get(R.string.cui_terminal_plugin_example_fullscre),
        "com.example.othertest.tpk" to Str.get(R.string.custom_backend_plugin_example_other_),
        "com.example.termuxtest.tpk" to Str.get(R.string.termux_backend_plugin_example_python),
        "com.test.allapi.tpk" to Str.get(R.string.full_api_test_plugin),
        "com.test.storage.tpk" to Str.get(R.string.storage_test_plugin),
        "NativeTestPlugin.tpk" to Str.get(R.string.native_plugin_example),
        "web_plugin_template.tpk" to Str.get(R.string.web_plugin_template_frontend_only)
    )
    val sb = StringBuilder()
    sb.appendLine("============================================================")
    sb.appendLine(Str.get(R.string.uin_tool_plugin_template))
    sb.appendLine("============================================================")
    sb.appendLine()
    sb.appendLine(Str.get(R.string.export_time, java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())))
    sb.appendLine()
    sb.appendLine(Str.get(R.string.file_description))
    sb.appendLine("------------------------------------------------------------")
    files.forEachIndexed { index, name ->
        val prefix = if (index == files.lastIndex) "└── " else "├── "
        sb.appendLine("$prefix$name  ${descMap[name] ?: Str.get(R.string.plugin_template)}")
    }
    sb.appendLine()
    sb.appendLine(Str.get(R.string.usage))
    sb.appendLine("------------------------------------------------------------")
    sb.appendLine(Str.get(R.string.step1_in_manage_plugins_tap_import_plugi))
    sb.appendLine(Str.get(R.string.step2_select_a_tpk_file))
    sb.appendLine(Str.get(R.string.step3_the_plugin_will_be_installed_into_))
    sb.appendLine(Str.get(R.string.step4_run_installed_plugins_on_the_tools))
    sb.appendLine()
    sb.appendLine("============================================================")
    return sb.toString().trimEnd()
}