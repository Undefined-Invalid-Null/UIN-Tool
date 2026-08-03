// ui/screen/dev/DevScreen.kt
package com.UIN.Tool.ui.screen.dev

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.UIN.Tool.app.TermuxActivity
import com.UIN.Tool.app.activities.SettingsActivity
import com.UIN.Tool.log.Logger
import com.UIN.Tool.ui.components.UIComponents
import com.UIN.Tool.ui.screen.docs.DocBrowserActivity
import com.UIN.Tool.utils.AppLog
import com.UIN.Tool.utils.AppToast
import com.UIN.Tool.utils.Constants
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
        UIComponents.TitleText("开发")

        // ============================================================
        // 终端卡片
        // ============================================================
        UIComponents.Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            UIComponents.SectionTitle("终端")
            Text(
                text = "启动完整的 Linux 终端环境\n基于 Termux 核心引擎",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                UIComponents.PrimaryButton(
                    text = "打开终端",
                    icon = Icons.Default.Terminal,
                    onClick = {
                        try {
                            val intent = Intent(context, TermuxActivity::class.java)
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(intent)
                            AppLog.i(TAG, "启动终端成功")
                        } catch (e: Exception) {
                            AppLog.e(TAG, "启动终端失败", e)
                            AppToast.error(context, "启动终端失败: ${e.message}")
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
                UIComponents.SecondaryButton(
                    text = "终端设置",
                    icon = Icons.Default.Settings,
                    onClick = {
                        try {
                            val intent = Intent(context, SettingsActivity::class.java)
                            context.startActivity(intent)
                            AppLog.i(TAG, "打开终端设置")
                        } catch (e: Exception) {
                            AppLog.e(TAG, "启动终端设置失败", e)
                            AppToast.error(context, "启动设置失败: ${e.message}")
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
            UIComponents.SectionTitle("插件开发工具")
            Text(
                text = "创建 UIN Tool 插件，支持原生、Web 和 CUI 终端三种前端，多种后端语言",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            UIComponents.PrimaryButton(
                text = "创建插件",
                icon = Icons.Default.Add,
                onClick = { showCreatePluginDialog = true },
                modifier = Modifier.fillMaxWidth()
            )

            UIComponents.SecondaryButton(
                text = if (isExporting) "导出中..." else "导出模板",
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
            UIComponents.SectionTitle("快速开始")
            Text(
                text = "1. 点击「创建插件」选择类型\n" +
                        "2. 填写插件基本信息\n" +
                        "3. 编写代码或选择二进制文件\n" +
                        "4. 导出 TPK 文件\n" +
                        "5. 在「管理」「插件管理」中导入运行",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            UIComponents.SecondaryButton(
                text = "查看开发文档",
                icon = Icons.Default.Info,
                onClick = {
                    try {
                        val intent = Intent(context, DocBrowserActivity::class.java)
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        AppToast.warning(context, "文档功能开发中")
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
            containerColor = Color.White,
            title = {
                Text(
                    "选择前端类型",
                    color = Color(0xFF1A1A1A)
                )
            },
            text = {
                Text(
                    "请选择插件的前端 UI 类型：",
                    color = Color(0xFF555555)
                )
            },
            confirmButton = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 原生 UI
                    UIComponents.PrimaryButton(
                        text = "原生 UI (Android View)",
                        onClick = {
                            selectedUiType = "native"
                            showCreatePluginDialog = false
                            navigateToWizard(context, "native", "")
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    // ✅ 纯 WebView（无后端）
                    UIComponents.PrimaryButton(
                        text = "Web UI (纯前端, 无后端)",
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
                        text = "Web UI + 后端",
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
                        text = "CUI 终端 (命令行界面)",
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
                            contentColor = Color(0xFF888888)
                        )
                    ) {
                        Text("取消")
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
            containerColor = Color.White,
            title = {
                Text(
                    "选择后端语言",
                    color = Color(0xFF1A1A1A)
                )
            },
            text = {
                Text(
                    "请选择 Web 插件的后端语言：",
                    color = Color(0xFF555555)
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
                        "binary" to "二进制文件",
                        "other" to "自定义（手动启动）"
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
                            contentColor = Color(0xFF888888)
                        )
                    ) {
                        Text("取消")
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
        AppLog.i(TAG, "启动插件向导: uiType=$uiType, backend=$backend")
    } catch (e: Exception) {
        AppLog.e(TAG, "跳转插件向导失败", e)
        AppToast.error(context, "功能开发中: ${e.message}")
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
            AppToast.warning(context, "assets/test_plugins 中没有 TPK 模板")
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
                AppLog.d(TAG, "导出模板文件: $name")
            } catch (e: Exception) {
                AppLog.w(TAG, "复制文件失败: $name - ${e.message}")
                failCount++
            }
        }

        val readmeFile = File(templateDir, "README.txt")
        readmeFile.writeText(buildTemplateReadme(exportedNames))

        val message = if (failCount == 0) {
            "已导出 $successCount 个插件模板到:\n${templateDir.absolutePath}"
        } else {
            "导出完成 (成功 $successCount 个, 失败 $failCount 个)\n${templateDir.absolutePath}"
        }

        AppToast.showLong(context, message)
        AppLog.success(TAG, "模板导出完成: ${templateDir.absolutePath} (成功 $successCount, 失败 $failCount)")

    } catch (e: Exception) {
        AppLog.e(TAG, "导出模板失败", e)
        AppToast.error(context, "导出失败: ${e.message}")
    }
}

// ============================================================
// 生成模板 README
// ============================================================
private fun buildTemplateReadme(files: List<String>): String {
    val descMap = mapOf(
        "com.example.cuitest.tpk" to "CUI 终端插件示例（全屏终端执行脚本）",
        "com.example.othertest.tpk" to "自定义后端插件示例（other 模式，pre-command 手动启动）",
        "com.example.termuxtest.tpk" to "Termux 后端插件示例（Python 后端）",
        "com.test.allapi.tpk" to "全接口测试插件",
        "com.test.storage.tpk" to "存储测试插件",
        "NativeTestPlugin.tpk" to "原生插件示例",
        "web_plugin_template.tpk" to "Web 插件模板（纯前端）"
    )
    val sb = StringBuilder()
    sb.appendLine("============================================================")
    sb.appendLine("UIN Tool 插件模板")
    sb.appendLine("============================================================")
    sb.appendLine()
    sb.appendLine("导出时间: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}")
    sb.appendLine()
    sb.appendLine("文件说明:")
    sb.appendLine("------------------------------------------------------------")
    files.forEachIndexed { index, name ->
        val prefix = if (index == files.lastIndex) "└── " else "├── "
        sb.appendLine("$prefix$name  ${descMap[name] ?: "插件模板"}")
    }
    sb.appendLine()
    sb.appendLine("使用方法:")
    sb.appendLine("------------------------------------------------------------")
    sb.appendLine("1. 在「管理」「插件管理」中选择「导入插件」")
    sb.appendLine("2. 选择 .tpk 文件")
    sb.appendLine("3. 插件将自动安装到 UIN Tool 中")
    sb.appendLine("4. 在「工具」页面可以运行已安装的插件")
    sb.appendLine()
    sb.appendLine("============================================================")
    return sb.toString().trimEnd()
}