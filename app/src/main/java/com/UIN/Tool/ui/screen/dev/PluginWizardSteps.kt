// app/src/main/java/com/UIN/Tool/ui/screen/dev/PluginWizardSteps.kt
package com.UIN.Tool.ui.screen.dev

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.UIN.Tool.ui.components.UIComponents
import com.UIN.Tool.utils.FileUtils
import com.UIN.Tool.utils.formatFileSize
import java.io.File

// ==================== 辅助函数 ====================

private fun loadBitmapFromFile(path: String): Bitmap? {
    return try {
        BitmapFactory.decodeFile(path)
    } catch (e: Exception) {
        null
    }
}

private fun handleIconSelection(
    context: android.content.Context,
    uri: Uri,
    onIconSelected: (String) -> Unit
) {
    try {
        val tempFile = File(context.cacheDir, "temp_icon_${System.currentTimeMillis()}.png")
        if (FileUtils.copyUriToFile(context, uri, tempFile)) {
            onIconSelected(tempFile.absolutePath)
        }
    } catch (e: Exception) {
        // 忽略异常
    }
}

private fun handleResourceSelection(
    context: android.content.Context,
    uri: Uri,
    onResourceAdded: (String) -> Unit
) {
    try {
        val fileName = uri.lastPathSegment?.substringAfterLast("/") ?: "resource"
        val tempFile = File(context.cacheDir, "temp_res_${System.currentTimeMillis()}_$fileName")
        if (FileUtils.copyUriToFile(context, uri, tempFile)) {
            onResourceAdded(tempFile.absolutePath)
        }
    } catch (e: Exception) {
        // 忽略异常
    }
}

// ==================== 步骤1：配置信息 ====================

@Composable
fun PluginConfigStep(
    pluginId: String,
    onPluginIdChange: (String) -> Unit,
    pluginName: String,
    onPluginNameChange: (String) -> Unit,
    pluginAuthor: String,
    onPluginAuthorChange: (String) -> Unit,
    pluginDescription: String,
    onPluginDescriptionChange: (String) -> Unit,
    pluginVersion: String,
    onPluginVersionChange: (String) -> Unit,
    pluginVersionName: String,
    onPluginVersionNameChange: (String) -> Unit,
    mainClass: String,
    onMainClassChange: (String) -> Unit,
    entryPath: String,
    onEntryPathChange: (String) -> Unit,
    uiType: String
) {
    Column {
        UIComponents.TextInput(
            value = pluginId,
            onValueChange = onPluginIdChange,
            label = "插件ID",
            placeholder = "com.example.myplugin",
            modifier = Modifier.fillMaxWidth(),
            isError = pluginId.isNotEmpty() && !pluginId.matches(Regex("^[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+$")),
            supportingText = if (pluginId.isNotEmpty() && !pluginId.matches(Regex("^[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+$"))) {
                "⚠️ 格式应为域名倒序，如 com.example.myplugin"
            } else null
        )
        Spacer(modifier = Modifier.height(8.dp))

        UIComponents.TextInput(
            value = pluginName,
            onValueChange = onPluginNameChange,
            label = "插件名称",
            placeholder = "我的插件",
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))

        UIComponents.TextInput(
            value = pluginAuthor,
            onValueChange = onPluginAuthorChange,
            label = "作者",
            placeholder = "开发者",
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))

        UIComponents.TextInput(
            value = pluginDescription,
            onValueChange = onPluginDescriptionChange,
            label = "描述",
            placeholder = "这是一个示例插件",
            modifier = Modifier.fillMaxWidth(),
            singleLine = false
        )
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            UIComponents.TextInput(
                value = pluginVersion,
                onValueChange = onPluginVersionChange,
                label = "版本号",
                placeholder = "1",
                modifier = Modifier.weight(1f)
            )
            UIComponents.TextInput(
                value = pluginVersionName,
                onValueChange = onPluginVersionNameChange,
                label = "版本名",
                placeholder = "1.0.0",
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))

        if (uiType == "native") {
            UIComponents.TextInput(
                value = mainClass,
                onValueChange = onMainClassChange,
                label = "主类名",
                placeholder = "com.example.MainPlugin",
                modifier = Modifier.fillMaxWidth(),
                isError = mainClass.isNotEmpty() && !mainClass.contains("."),
                supportingText = if (mainClass.isNotEmpty() && !mainClass.contains(".")) {
                    "⚠️ 必须包含包名，如 com.example.MainPlugin"
                } else null
            )
        } else {
            UIComponents.TextInput(
                value = entryPath,
                onValueChange = onEntryPathChange,
                label = "入口文件",
                placeholder = "web/index.html",
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

// ==================== 步骤2：图标选择 ====================

@Composable
fun PluginIconStep(
    iconPath: String,
    onIconSelected: (String) -> Unit
) {
    val context = LocalContext.current

    val iconPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            handleIconSelection(context, uri, onIconSelected)
        }
    }

    val bitmap = if (iconPath.isNotEmpty() && File(iconPath).exists()) {
        loadBitmapFromFile(iconPath)
    } else {
        null
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .background(
                    MaterialTheme.colorScheme.surfaceVariant,
                    RoundedCornerShape(16.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "图标预览",
                    modifier = Modifier.size(80.dp)
                )
            } else {
                Icon(
                    Icons.Default.Image,
                    contentDescription = "图标预览",
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            UIComponents.PrimaryButton(
                text = if (iconPath.isNotEmpty()) "更换图标" else "选择图标",
                icon = Icons.Default.FileUpload,
                onClick = { iconPickerLauncher.launch("image/*") }
            )

            if (iconPath.isNotEmpty()) {
                UIComponents.SecondaryButton(
                    text = "移除图标",
                    onClick = { onIconSelected("") }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        UIComponents.CaptionText(
            "建议使用 128x128 像素的 PNG 图片"
        )
    }
}

// ==================== 步骤3：原生代码 ====================

@Composable
fun NativeCodeStep(
    onOpenEditor: () -> Unit,
    fileCount: Int
) {
    Column {
        UIComponents.Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            UIComponents.BodyText(
                """
                    📌 原生插件开发提示

                    • 必须实现 PluginInterface 接口的所有方法
                    • UI 必须通过 Java 代码动态创建，不能使用 XML
                    • 示例代码已包含基础的 LinearLayout + Button
                    • 可以添加任何原生 Android 控件
                    • 修改后需要重新编译生成 DEX

                    当前已生成 ${if (fileCount > 0) fileCount else 0} 个代码文件
                """.trimIndent(),
                modifier = Modifier.padding(8.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        UIComponents.PrimaryButton(
            text = "打开代码编辑器 (${fileCount} 个文件)",
            icon = Icons.Default.Edit,
            onClick = onOpenEditor,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

// ==================== 步骤3：Web配置 ====================

@Composable
fun WebConfigStep(
    templateType: Int,
    onTemplateTypeChange: (Int) -> Unit,
    onImportWebProject: () -> Unit
) {
    Column {
        UIComponents.Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            UIComponents.BodyText(
                """
                    📌 Web 插件说明

                    • Web 插件不需要编写 Java 代码
                    • UI 界面请编辑 web/index.html、web/style.css、web/script.js
                    • JavaScript 可通过 UINPlugin.callHost() 调用原生功能
                    • 修改 HTML/CSS/JS 后无需重新编译
                    • 可以直接导入已有的 HTML/CSS/JS 项目
                """.trimIndent(),
                modifier = Modifier.padding(8.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        UIComponents.BodyText("选择模板类型：")
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            UIComponents.Chip(
                label = "完整模板",
                selected = templateType == 0,
                onClick = { onTemplateTypeChange(0) },
                modifier = Modifier.weight(1f)
            )
            UIComponents.Chip(
                label = "空白模板",
                selected = templateType == 1,
                onClick = { onTemplateTypeChange(1) },
                modifier = Modifier.weight(1f)
            )
            UIComponents.Chip(
                label = "导入项目",
                selected = templateType == 2,
                onClick = { onTemplateTypeChange(2) },
                modifier = Modifier.weight(1f)
            )
            UIComponents.Chip(
                label = "跳过",
                selected = templateType == 3,
                onClick = { onTemplateTypeChange(3) },
                modifier = Modifier.weight(1f)
            )
        }

        if (templateType == 2) {
            Spacer(modifier = Modifier.height(8.dp))
            UIComponents.PrimaryButton(
                text = "选择 ZIP 文件导入",
                icon = Icons.Default.FileUpload,
                onClick = onImportWebProject,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

// ==================== 步骤4：资源文件 ====================

@Composable
fun ResourcesStep(
    resourcePaths: List<String>,
    onResourceAdded: (String) -> Unit,
    onResourceRemoved: (Int) -> Unit
) {
    val context = LocalContext.current

    val resourcePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            handleResourceSelection(context, uri, onResourceAdded)
        }
    }

    Column {
        UIComponents.BodyText("添加资源文件（可选）")
        Spacer(modifier = Modifier.height(8.dp))
        UIComponents.CaptionText("添加图片、音频等资源文件")
        Spacer(modifier = Modifier.height(16.dp))

        if (resourcePaths.isEmpty()) {
            UIComponents.Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Folder,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        UIComponents.CaptionText("暂无资源文件")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(resourcePaths.indices.toList()) { index ->
                    val path = resourcePaths[index]
                    UIComponents.Card(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    Icons.Default.InsertDriveFile,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                UIComponents.BodyText(File(path).name)
                            }
                            UIComponents.IconButton(
                                icon = Icons.Default.Close,
                                onClick = { onResourceRemoved(index) },
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        UIComponents.PrimaryButton(
            text = "添加资源文件",
            icon = Icons.Default.Add,
            onClick = { resourcePickerLauncher.launch("*/*") },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

// ==================== 步骤5：打包 ====================

@Composable
fun PackageStep(
    isCompiling: Boolean,
    compileMessage: String,
    compileProgress: Int,
    tpkFile: File?
) {
    Column {
        UIComponents.TitleText("生成项目文件")
        Spacer(modifier = Modifier.height(8.dp))

        UIComponents.Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (isCompiling) 280.dp else 240.dp)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(16.dp)
                ) {
                    if (isCompiling) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(48.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        UIComponents.LinearProgressIndicator(progress = compileProgress / 100f)
                        Spacer(modifier = Modifier.height(8.dp))
                        UIComponents.BodyText(compileMessage)
                        UIComponents.CaptionText("$compileProgress%")
                    } else if (tpkFile != null && tpkFile.exists()) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        UIComponents.TitleText("✅ 打包成功！")
                        Spacer(modifier = Modifier.height(8.dp))
                        UIComponents.CaptionText(tpkFile.absolutePath)
                        UIComponents.CaptionText("大小: ${formatFileSize(tpkFile.length())}")
                    } else {
                        Icon(
                            Icons.Default.Build,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        UIComponents.BodyText("点击「完成」按钮开始生成项目文件")
                        if (compileMessage.isNotEmpty() && !compileMessage.contains("成功")) {
                            Spacer(modifier = Modifier.height(8.dp))
                            UIComponents.CaptionText(
                                compileMessage,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }
}