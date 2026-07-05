// app/src/main/java/com/UIN/Tool/ui/screen/dev/DevScreen.kt
package com.UIN.Tool.ui.screen.dev

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.UIN.Tool.log.Logger
import com.UIN.Tool.ui.components.UIComponents
import com.UIN.Tool.utils.Constants
import java.io.File

@Composable
fun DevScreen() {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        UIComponents.TitleText("开发")

        // 快速创建插件卡片
        UIComponents.Card {
            UIComponents.SectionTitle("插件开发工具")

            UIComponents.PrimaryButton(
                text = "创建原生插件",
                icon = Icons.Default.Add,
                onClick = {
                    try {
                        val intent = Intent(context, BasePluginWizardActivity::class.java)
                        intent.putExtra("ui_type", "native")
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        Logger.e("DevScreen", "跳转原生插件向导失败", e)
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
                text = "创建Web插件",
                icon = Icons.Default.Add,
                onClick = {
                    try {
                        val intent = Intent(context, BasePluginWizardActivity::class.java)
                        intent.putExtra("ui_type", "web")
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        Logger.e("DevScreen", "跳转Web插件向导失败", e)
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
                text = "导出模板",
                icon = Icons.Default.FileDownload,
                onClick = {
                    exportTemplates(context)
                },
                modifier = Modifier.fillMaxWidth()
            )
        }

        // 快速开始卡片
        UIComponents.Card {
            UIComponents.SectionTitle("快速开始")
            UIComponents.BodyText(
                "1. 选择「创建新插件」\n" +
                "2. 选择插件类型（原生/Web）\n" +
                "3. 填写插件信息\n" +
                "4. 编写代码\n" +
                "5. 导出 TPK 文件\n" +
                "6. 在「管理」中导入运行"
            )
        }
    }
}

private fun exportTemplates(context: android.content.Context) {
    try {
        val templateDir = File(Constants.WORK_DIR, "templates")
        if (!templateDir.exists()) {
            templateDir.mkdirs()
        }

        val assetFiles = listOf(
            "templates/native_template.tpk" to "native_plugin_template.tpk",
            "templates/web_template.tpk" to "web_plugin_template.tpk",
            "docs/README.md" to "docs/README.md",
            "docs/Help.md" to "docs/Help.md",
            "docs/About.md" to "docs/About.md",
            "docs/CONTRIBUTORS.md" to "docs/CONTRIBUTORS.md",
            "docs/CHANGELOG.md" to "docs/CHANGELOG.md"
        )

        for ((assetPath, fileName) in assetFiles) {
            try {
                val destFile = File(templateDir, fileName)
                destFile.parentFile?.mkdirs()
                context.assets.open(assetPath).use { input ->
                    java.io.FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                }
            } catch (e: Exception) {
                Logger.w("DevScreen", "复制文件失败: $assetPath")
            }
        }

        Toast.makeText(
            context,
            "模板已导出到: ${templateDir.absolutePath}",
            Toast.LENGTH_LONG
        ).show()
        Logger.success("DevScreen", "模板导出成功: ${templateDir.absolutePath}")

    } catch (e: Exception) {
        Logger.e("DevScreen", "导出模板失败", e)
        Toast.makeText(
            context,
            "导出失败: ${e.message}",
            Toast.LENGTH_SHORT
        ).show()
    }
}