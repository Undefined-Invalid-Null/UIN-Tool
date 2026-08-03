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

private const val TAG = "DevScreen"

@Composable
fun DevScreen() {
    val context = LocalContext.current
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
                text = "创建 UIN Tool 插件，支持原生和 Web 两种前端，多种后端语言",
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
                        exportTemplates(context) { isExporting = false }
                        isExporting = true
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
                    Button(
                        onClick = {
                            selectedUiType = "native"
                            showCreatePluginDialog = false
                            navigateToWizard(context, "native", "")
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF1A3A4A),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("原生 UI (Android View)")
                    }
                    
                    // ✅ 纯 WebView（无后端）
                    Button(
                        onClick = {
                            selectedUiType = "web"
                            isWebViewOnly = true
                            showCreatePluginDialog = false
                            // 无后端，直接跳转
                            navigateToWizard(context, "web", "")
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF455A64),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Web UI (纯前端, 无后端)")
                    }
                    
                    // WebView + 后端
                    Button(
                        onClick = {
                            selectedUiType = "web"
                            isWebViewOnly = false
                            showCreatePluginDialog = false
                            showBackendDialog = true
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF37474F),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Web UI + 后端")
                    }
                    
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
                        Button(
                            onClick = {
                                selectedBackend = key
                                showBackendDialog = false
                                navigateToWizard(context, "web", key)
                            },
                            modifier = Modifier.fillMaxWidth().height(44.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = when (key) {
                                    "python" -> Color(0xFF1A3A4A)
                                    "node" -> Color(0xFF2E7D32)
                                    "php" -> Color(0xFFE65100)
                                    "other" -> Color(0xFF6A1B9A)
                                    else -> Color(0xFF455A64)
                                },
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(label)
                        }
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
// 导出模板
// ============================================================
private fun exportTemplates(
    context: android.content.Context,
    onComplete: () -> Unit
) {
    try {
        val templateDir = File(Constants.WORK_DIR, "templates")
        if (!templateDir.exists()) {
            templateDir.mkdirs()
        }

        val assetFiles = listOf(
            "templates/native_template.tpk" to "native_plugin_template.tpk",
            "templates/web_template.tpk" to "web_plugin_template.tpk",
            "templates/python_template.tpk" to "python_plugin_template.tpk",
            "templates/NativeTestPlugin.tpk" to "NativeTestPlugin.tpk",
            "template.tpk" to "plugin_template.tpk",
            "docs/README.md" to "docs/README.md",
            "docs/Help.md" to "docs/Help.md",
            "docs/About.md" to "docs/About.md",
            "docs/CONTRIBUTORS.md" to "docs/CONTRIBUTORS.md",
            "docs/CHANGELOG.md" to "docs/CHANGELOG.md"
        )

        var successCount = 0
        var failCount = 0

        for ((assetPath, fileName) in assetFiles) {
            try {
                val destFile = File(templateDir, fileName)
                destFile.parentFile?.mkdirs()
                context.assets.open(assetPath).use { input ->
                    java.io.FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                }
                successCount++
                AppLog.d(TAG, "导出模板文件: $fileName")
            } catch (e: Exception) {
                AppLog.w(TAG, "复制文件失败: $assetPath - ${e.message}")
                failCount++
            }
        }

        val readmeFile = File(templateDir, "README.txt")
        readmeFile.writeText(
            """
            ============================================================
            UIN Tool 插件模板
            ============================================================
            
            导出时间: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}
            
            文件说明:
            ------------------------------------------------------------
            ├── native_plugin_template.tpk  原生插件模板
            ├── web_plugin_template.tpk     Web 插件模板  
            ├── python_plugin_template.tpk  Python 后端插件模板
            ├── NativeTestPlugin.tpk        测试插件示例
            ├── plugin_template.tpk         通用插件模板
            └── docs/                       文档目录
                ├── README.md              项目介绍
                ├── Help.md                使用帮助
                ├── About.md               关于应用
                ├── CONTRIBUTORS.md        贡献者名单
                └── CHANGELOG.md           更新日志
            
            使用方法:
            ------------------------------------------------------------
            1. 在「管理」「插件管理」中选择「导入插件」
            2. 选择 .tpk 文件
            3. 插件将自动安装到 UIN Tool 中
            4. 在「工具」页面可以运行已安装的插件
            
            开发指南:
            ------------------------------------------------------------
            详见 docs/ 目录下的文档
            
            ============================================================
            """.trimIndent()
        )

        val message = if (failCount == 0) {
            "所有模板已导出到:\n${templateDir.absolutePath}"
        } else {
            "导出完成 (成功 $successCount 个, 失败 $failCount 个)\n${templateDir.absolutePath}"
        }

        AppToast.showLong(context, message)
        AppLog.success(TAG, "模板导出完成: ${templateDir.absolutePath} (成功 $successCount, 失败 $failCount)")

    } catch (e: Exception) {
        AppLog.e(TAG, "导出模板失败", e)
        AppToast.error(context, "导出失败: ${e.message}")
    } finally {
        onComplete()
    }
}