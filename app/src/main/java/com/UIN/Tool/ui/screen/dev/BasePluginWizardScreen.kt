// app/src/main/java/com/UIN/Tool/ui/screen/dev/BasePluginWizardScreen.kt
package com.UIN.Tool.ui.screen.dev

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.UIN.Tool.R
import com.UIN.Tool.core.compiler.JavaToDexCompiler
import com.UIN.Tool.log.Logger
import com.UIN.Tool.ui.components.UIComponents
import com.UIN.Tool.utils.Constants
import com.UIN.Tool.utils.FileUtils
import kotlinx.coroutines.launch
import java.io.File

private const val TAG = "BasePluginWizardScreen"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BasePluginWizardScreen(
    onFinish: () -> Unit,
    uiType: String = "native"
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val viewModel = remember {
        PluginWizardViewModel(context, uiType)
    }

    var currentStep by remember { mutableStateOf(0) }
    val totalSteps = 5

    // ==================== 导入 Web 项目 ====================
    val webImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null && viewModel.webTemplateType.value == 2) {
            try {
                val tempFile = File(context.cacheDir, "web_import_${System.currentTimeMillis()}.zip")
                if (!FileUtils.copyUriToFile(context, uri, tempFile)) {
                    Toast.makeText(context, "无法读取文件", Toast.LENGTH_SHORT).show()
                    return@rememberLauncherForActivityResult
                }

                val extractDir = File(context.cacheDir, "web_extract_${System.currentTimeMillis()}")
                extractDir.mkdirs()

                if (FileUtils.unzipFile(tempFile, extractDir)) {
                    val webDir = findWebDir(extractDir)
                    if (webDir != null) {
                        val files = mutableMapOf<String, String>()
                        webDir.listFiles()?.forEach { file ->
                            if (file.isFile) {
                                val relativePath = file.name
                                files["web/$relativePath"] = file.readText()
                            }
                        }
                        if (files.isNotEmpty()) {
                            viewModel.updateFiles(files.keys.toList(), files)
                            Toast.makeText(context, "Web项目导入成功，共 ${files.size} 个文件", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(context, "Web项目为空", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(context, "未找到有效的Web项目", Toast.LENGTH_LONG).show()
                    }
                } else {
                    Toast.makeText(context, "解压ZIP文件失败", Toast.LENGTH_SHORT).show()
                }

                FileUtils.deleteRecursively(extractDir)
                tempFile.delete()

            } catch (e: Exception) {
                Toast.makeText(context, "导入失败: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // ==================== 代码编辑器 Launcher ====================
    val codeEditorLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val data = result.data
            val updatedFiles = data?.getStringArrayListExtra("file_list") ?: emptyList()
            val updatedContents = data?.getSerializableExtra("file_contents") as? HashMap<String, String> ?: emptyMap()
            viewModel.updateFiles(updatedFiles, updatedContents)
            Toast.makeText(context, "代码已更新，共 ${updatedFiles.size} 个文件", Toast.LENGTH_SHORT).show()
        }
    }

    // ==================== 打开代码编辑器 ====================
    fun openCodeEditor() {
        viewModel.initDefaultFiles()

        val intent = android.content.Intent(context, CodeEditorActivity::class.java)
        intent.putExtra("file_list", ArrayList(viewModel.fileList.value))
        intent.putExtra("file_contents", HashMap(viewModel.fileContents.value))
        intent.putExtra("ui_type", uiType)
        intent.putExtra("main_class", viewModel.mainClass.value)
        intent.putExtra("plugin_name", viewModel.pluginName.value)
        intent.putExtra("plugin_id", viewModel.pluginId.value)

        codeEditorLauncher.launch(intent)
    }

    // ==================== 编译打包 ====================
    fun startCompileAndPackage() {
        scope.launch {
            try {
                viewModel.isCompiling.value = true
                viewModel.compileMessage.value = "准备编译环境..."
                viewModel.compileProgress.value = 0

                val safePluginId = viewModel.pluginId.value
                    .replace("/", "_")
                    .replace("\\", "_")
                    .replace(":", "_")
                
                val workDir = File(Constants.PLUGIN_DIR, safePluginId)
                val outputTpk = File(Constants.TPK_DIR, "$safePluginId.tpk")
                
                // 确保目录存在
                workDir.mkdirs()
                outputTpk.parentFile?.mkdirs()

                // 先保存所有项目文件
                viewModel.compileMessage.value = "生成项目文件..."
                viewModel.compileProgress.value = 10
                
                val saveSuccess = viewModel.generateProjectFiles(workDir)
                if (!saveSuccess) {
                    viewModel.compileMessage.value = "❌ 生成项目文件失败"
                    viewModel.isCompiling.value = false
                    Toast.makeText(context, "生成项目文件失败", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                viewModel.compileMessage.value = "项目文件已生成"
                viewModel.compileProgress.value = 30

                // 如果是原生插件，执行编译打包
                if (uiType == "native") {
                    viewModel.compileMessage.value = "开始编译 Java 代码..."
                    viewModel.compileProgress.value = 40

                    val compiler = JavaToDexCompiler(context)
                    
                    // 设置进度监听
                    compiler.setOnProgressListener { message ->
                        viewModel.compileMessage.value = message
                        when {
                            message.contains("编译") -> viewModel.compileProgress.value = 50
                            message.contains("JAR") -> viewModel.compileProgress.value = 65
                            message.contains("DEX") -> viewModel.compileProgress.value = 75
                            message.contains("打包") -> viewModel.compileProgress.value = 85
                        }
                    }

                    compiler.setOnCompleteListener { resultFile ->
                        viewModel.compileMessage.value = "✅ 编译打包完成!"
                        viewModel.compileProgress.value = 100
                        viewModel.isCompiling.value = false
                        viewModel.tpkFile.value = outputTpk
                        
                        Toast.makeText(
                            context,
                            "✅ 编译打包成功!\n${outputTpk.absolutePath}",
                            Toast.LENGTH_LONG
                        ).show()
                        
                        onFinish()
                    }

                    compiler.setOnErrorListener { error ->
                        viewModel.compileMessage.value = "❌ 编译失败: $error"
                        viewModel.compileProgress.value = 0
                        viewModel.isCompiling.value = false
                        Toast.makeText(context, "编译失败: $error", Toast.LENGTH_LONG).show()
                    }

                    // 执行编译打包
                    val srcDir = File(workDir, "src")
                    val mainClass = viewModel.mainClass.value
                    
                    compiler.compileAndPackage(
                        javaSrcDir = srcDir,
                        projectDir = workDir,
                        outputTpk = outputTpk,
                        uiType = uiType,
                        mainClass = mainClass
                    )

                } else {
                    // Web 插件：直接打包 TPK（不需要编译）
                    viewModel.compileMessage.value = "打包 Web 插件..."
                    viewModel.compileProgress.value = 60

                    val compiler = JavaToDexCompiler(context)
                    
                    compiler.setOnProgressListener { message ->
                        viewModel.compileMessage.value = message
                        when {
                            message.contains("打包") -> viewModel.compileProgress.value = 80
                        }
                    }

                    compiler.setOnCompleteListener { resultFile ->
                        viewModel.compileMessage.value = "✅ Web 插件打包完成!"
                        viewModel.compileProgress.value = 100
                        viewModel.isCompiling.value = false
                        viewModel.tpkFile.value = outputTpk
                        
                        Toast.makeText(
                            context,
                            "✅ Web 插件打包成功!\n${outputTpk.absolutePath}",
                            Toast.LENGTH_LONG
                        ).show()
                        
                        onFinish()
                    }

                    compiler.setOnErrorListener { error ->
                        viewModel.compileMessage.value = "❌ 打包失败: $error"
                        viewModel.compileProgress.value = 0
                        viewModel.isCompiling.value = false
                        Toast.makeText(context, "打包失败: $error", Toast.LENGTH_LONG).show()
                    }

                    // ✅ Web 插件直接打包（调用公共方法）
                    compiler.packageTpk(
                        projectDir = workDir,
                        outputTpk = outputTpk,
                        uiType = uiType,
                        hasDex = false
                    )
                }

            } catch (e: Exception) {
                Logger.e(TAG, "编译打包异常", e)
                viewModel.compileMessage.value = "❌ 异常: ${e.message}"
                viewModel.isCompiling.value = false
                Toast.makeText(context, "编译打包异常: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // ==================== 步骤标题 ====================
    fun getStepTitle(): String {
        return when (currentStep) {
            0 -> "配置插件信息"
            1 -> "设置插件图标"
            2 -> if (uiType == "native") "编写插件代码" else "配置Web插件"
            3 -> "添加资源文件"
            else -> "生成项目文件"
        }
    }

    fun getStepDesc(): String {
        return when (currentStep) {
            0 -> "填写插件的基本配置信息"
            1 -> "选择一个 PNG 图片作为图标（可选）"
            2 -> if (uiType == "native") "实现 PluginInterface 接口" else "选择Web插件模板类型"
            3 -> "可选的图片、音频等资源文件"
            else -> "生成项目结构并打包为 TPK"
        }
    }

    // ==================== 验证当前步骤 ====================
    fun validateCurrentStep(): Boolean {
        return when (currentStep) {
            0 -> {
                if (viewModel.pluginId.value.isEmpty() || viewModel.pluginName.value.isEmpty()) {
                    Toast.makeText(context, "请填写插件ID和名称", Toast.LENGTH_SHORT).show()
                    return false
                }
                if (!viewModel.pluginId.value.matches(Regex("^[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+$"))) {
                    Toast.makeText(context, "插件ID格式不正确，应为域名倒序格式", Toast.LENGTH_SHORT).show()
                    return false
                }
                true
            }
            2 -> {
                if (uiType == "native" && viewModel.mainClass.value.isEmpty()) {
                    Toast.makeText(context, "请填写主类名", Toast.LENGTH_SHORT).show()
                    return false
                }
                if (uiType == "native" && !viewModel.mainClass.value.contains(".")) {
                    Toast.makeText(context, "主类名必须包含包名，如 com.example.MainPlugin", Toast.LENGTH_SHORT).show()
                    return false
                }
                true
            }
            else -> true
        }
    }

    // ==================== UI ====================
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (uiType == "native") "创建原生插件" else "创建Web插件") },
                navigationIcon = {
                    UIComponents.IconButton(
                        icon = Icons.Default.ArrowBack,
                        onClick = onFinish
                    )
                },
                actions = {
                    if (currentStep == 2) {
                        UIComponents.IconButton(
                            icon = Icons.Default.Edit,
                            onClick = { openCodeEditor() }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        bottomBar = {
            NavigationBar(
                modifier = Modifier.fillMaxWidth(),
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (currentStep > 0) {
                        UIComponents.SecondaryButton(
                            text = "上一步",
                            onClick = { if (currentStep > 0) currentStep-- },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    if (currentStep < totalSteps - 1) {
                        UIComponents.PrimaryButton(
                            text = "下一步",
                            onClick = {
                                if (validateCurrentStep()) {
                                    if (currentStep < totalSteps - 1) currentStep++
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        UIComponents.PrimaryButton(
                            text = if (viewModel.isCompiling.value) "处理中..." else "完成",
                            onClick = {
                                if (validateCurrentStep()) {
                                    startCompileAndPackage()
                                }
                            },
                            modifier = Modifier.weight(1f),
                            enabled = !viewModel.isCompiling.value,
                            loading = viewModel.isCompiling.value
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 步骤指示器
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                repeat(totalSteps) { index ->
                    Box(
                        modifier = Modifier
                            .size(if (index == currentStep) 12.dp else 8.dp)
                            .background(
                                if (index <= currentStep)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.surfaceVariant,
                                RoundedCornerShape(50)
                            )
                    )
                    if (index < totalSteps - 1) {
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                }
            }

            // 步骤标题
            UIComponents.TitleText(
                getStepTitle(),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            UIComponents.BodyText(
                getStepDesc(),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 步骤内容
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
            ) {
                when (currentStep) {
                    0 -> PluginConfigStep(
                        pluginId = viewModel.pluginId.value,
                        onPluginIdChange = { viewModel.pluginId.value = it },
                        pluginName = viewModel.pluginName.value,
                        onPluginNameChange = { viewModel.pluginName.value = it },
                        pluginAuthor = viewModel.pluginAuthor.value,
                        onPluginAuthorChange = { viewModel.pluginAuthor.value = it },
                        pluginDescription = viewModel.pluginDescription.value,
                        onPluginDescriptionChange = { viewModel.pluginDescription.value = it },
                        pluginVersion = viewModel.pluginVersion.value,
                        onPluginVersionChange = { viewModel.pluginVersion.value = it },
                        pluginVersionName = viewModel.pluginVersionName.value,
                        onPluginVersionNameChange = { viewModel.pluginVersionName.value = it },
                        mainClass = viewModel.mainClass.value,
                        onMainClassChange = { viewModel.mainClass.value = it },
                        entryPath = viewModel.entryPath.value,
                        onEntryPathChange = { viewModel.entryPath.value = it },
                        uiType = uiType
                    )
                    1 -> PluginIconStep(
                        iconPath = viewModel.iconPath.value,
                        onIconSelected = { viewModel.iconPath.value = it }
                    )
                    2 -> if (uiType == "native") {
                        NativeCodeStep(
                            onOpenEditor = { openCodeEditor() },
                            fileCount = viewModel.fileList.value.size
                        )
                    } else {
                        WebConfigStep(
                            templateType = viewModel.webTemplateType.value,
                            onTemplateTypeChange = { viewModel.webTemplateType.value = it },
                            onImportWebProject = { webImportLauncher.launch("application/zip") }
                        )
                    }
                    3 -> ResourcesStep(
                        resourcePaths = viewModel.resourcePaths.value,
                        onResourceAdded = { viewModel.resourcePaths.value = viewModel.resourcePaths.value + it },
                        onResourceRemoved = { index ->
                            viewModel.resourcePaths.value = viewModel.resourcePaths.value
                                .filterIndexed { i, _ -> i != index }
                        }
                    )
                    4 -> PackageStep(
                        isCompiling = viewModel.isCompiling.value,
                        compileMessage = viewModel.compileMessage.value,
                        compileProgress = viewModel.compileProgress.value,
                        tpkFile = viewModel.tpkFile.value
                    )
                }
            }
        }
    }
}

// ==================== 查找 Web 目录 ====================
private fun findWebDir(dir: File): File? {
    val webDir = File(dir, "web")
    if (webDir.exists() && webDir.isDirectory) {
        return webDir
    }
    dir.listFiles()?.forEach { file ->
        if (file.isDirectory) {
            val subWeb = File(file, "web")
            if (subWeb.exists() && subWeb.isDirectory) {
                return subWeb
            }
        }
    }
    return null
}