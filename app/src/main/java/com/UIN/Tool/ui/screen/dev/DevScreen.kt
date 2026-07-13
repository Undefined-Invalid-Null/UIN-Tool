// app/src/main/java/com/UIN/Tool/ui/screen/dev/DevScreen.kt

package com.UIN.Tool.ui.screen.dev

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.UIN.Tool.app.TermuxActivity
import com.UIN.Tool.app.activities.SettingsActivity
import com.UIN.Tool.log.Logger
import com.UIN.Tool.ui.components.UIComponents
import com.UIN.Tool.ui.screen.docs.DocBrowserActivity
import com.UIN.Tool.utils.Constants
import java.io.File

private const val TAG = "DevScreen"

@Composable
fun DevScreen() {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    var isExporting by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ============================================================
        // 标题
        // ============================================================
        UIComponents.TitleText("开发")

        // ============================================================
        // 终端卡片
        // ============================================================
        UIComponents.Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            UIComponents.SectionTitle("终端")

            Text(
                text = "启动完整的 Linux 终端环境\n" +
                        "基于 Termux 核心引擎\n" +
                        "支持 bash、zsh 等 Shell\n" +
                        "支持 apt 包管理\n" +
                        "支持 Python、Node.js 等运行时",
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
                            Logger.i(TAG, "启动终端成功")
                        } catch (e: Exception) {
                            Logger.e(TAG, "启动终端失败", e)
                            Toast.makeText(
                                context,
                                "启动终端失败: ${e.message}",
                                Toast.LENGTH_LONG
                            ).show()
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
                            Logger.i(TAG, "打开终端设置")
                        } catch (e: Exception) {
                            Logger.e(TAG, "启动终端设置失败", e)
                            Toast.makeText(context, "启动设置失败: ${e.message}", Toast.LENGTH_SHORT).show()
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
                text = "创建、开发和导出 UIN Tool 插件",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            UIComponents.PrimaryButton(
                text = "创建原生插件",
                icon = Icons.Default.Code,
                onClick = {
                    try {
                        val intent = Intent(context, BasePluginWizardActivity::class.java)
                        intent.putExtra("ui_type", "native")
                        context.startActivity(intent)
                        Logger.i(TAG, "启动原生插件向导")
                    } catch (e: Exception) {
                        Logger.e(TAG, "跳转原生插件向导失败", e)
                        Toast.makeText(
                            context,
                            "功能开发中: ${e.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            )

            UIComponents.PrimaryButton(
                text = "创建 Web 插件",
                icon = Icons.Default.Web,
                onClick = {
                    try {
                        val intent = Intent(context, BasePluginWizardActivity::class.java)
                        intent.putExtra("ui_type", "web")
                        context.startActivity(intent)
                        Logger.i(TAG, "启动Web插件向导")
                    } catch (e: Exception) {
                        Logger.e(TAG, "跳转Web插件向导失败", e)
                        Toast.makeText(
                            context,
                            "功能开发中: ${e.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
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
                modifier = Modifier.fillMaxWidth()
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
                text = "1. 选择「创建原生插件」或「创建 Web 插件」\n" +
                        "2. 填写插件基本信息\n" +
                        "3. 编写插件代码 (Kotlin/Java 或 HTML/CSS/JS)\n" +
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
                        Toast.makeText(context, "文档功能开发中", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }

        // ============================================================
        // 已移除「工具和设置」卡片
        //    这些功能已移至「管理」页面
        // ============================================================

        // ============================================================
        // 底部留白
        // ============================================================
        Spacer(modifier = Modifier.height(80.dp))
    }
}

/**
 * 导出模板文件到工作目录
 */
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
                Logger.d(TAG, "导出模板文件: $fileName")
            } catch (e: Exception) {
                Logger.w(TAG, "复制文件失败: $assetPath - ${e.message}")
                failCount++
            }
        }

        // 创建模板说明文件
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

        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        Logger.success(TAG, "模板导出完成: ${templateDir.absolutePath} (成功 $successCount, 失败 $failCount)")

    } catch (e: Exception) {
        Logger.e(TAG, "导出模板失败", e)
        Toast.makeText(context, "导出失败: ${e.message}", Toast.LENGTH_SHORT).show()
    } finally {
        onComplete()
    }
}