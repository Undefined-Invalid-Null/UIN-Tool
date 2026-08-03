// ui/screen/dev/BasePluginWizardScreen.kt
package com.UIN.Tool.ui.screen.dev

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.UIN.Tool.core.compiler.JavaToDexCompiler
import com.UIN.Tool.log.Logger
import com.UIN.Tool.ui.components.UIComponents
import com.UIN.Tool.utils.AppLog
import com.UIN.Tool.utils.AppToast
import com.UIN.Tool.utils.Constants
import kotlinx.coroutines.launch
import java.io.File

private const val TAG = "BasePluginWizardScreen"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BasePluginWizardScreen(
    onFinish: () -> Unit,
    uiType: String = "native",
    backendType: String = ""
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val viewModel = remember {
        PluginWizardViewModel(context, uiType, backendType)
    }

    var currentStep by remember { mutableStateOf(0) }

    // 编辑 JSON 对话框状态
    var showJsonEditor by remember { mutableStateOf(false) }
    var jsonDraft by remember { mutableStateOf("") }
    
    val hasBackend = backendType.isNotEmpty()
    
    val totalSteps = if (uiType == "native") {
        5
    } else if (uiType == "web" && backendType == "binary") {
        4
    } else if (uiType == "web" && backendType.isEmpty()) {
        4
    } else if (uiType == "cui") {
        4
    } else {
        5
    }

    val binaryFilePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            handleBinaryFileSelection(context, uri) { filePath ->
                viewModel.binaryFilePath.value = filePath
                AppToast.success(context, "二进制文件已选择")
            }
        }
    }

    val webImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            handleWebProjectImport(context, uri) { files, contents ->
                viewModel.updateFiles(files, contents)
                AppToast.success(context, "Web项目导入成功，共 ${files.size} 个文件")
            }
        }
    }

    val codeEditorLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val data = result.data
            val updatedFiles = data?.getStringArrayListExtra("file_list") ?: emptyList()
            val updatedContents = data?.getSerializableExtra("file_contents") as? HashMap<String, String> ?: emptyMap()
            viewModel.updateFiles(updatedFiles, updatedContents)
            AppToast.info(context, "代码已更新，共 ${updatedFiles.size} 个文件")
        }
    }

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

    fun buildPluginJson(): String {
        return try {
            val json = org.json.JSONObject().apply {
                put("pluginId", viewModel.pluginId.value)
                put("version", viewModel.pluginVersion.value.toIntOrNull() ?: 1)
                put("versionName", viewModel.pluginVersionName.value)
                put("minHostVersion", 1)
                put("name", viewModel.pluginName.value)
                put("author", viewModel.pluginAuthor.value)
                put("description", viewModel.pluginDescription.value)
                put("icon", "icon.png")
                put("mainClass", if (uiType == "native") viewModel.mainClass.value else "")
                put("apiLevel", 21)
                put("uiType", uiType)
                put("entry", if (uiType == "web") viewModel.entryPath.value else "")
                put("permissions", "")
                put("dependencies", "")
                put("notice", viewModel.pluginNotice.value)
                if (uiType == "cui") {
                    put("backendRuntime", viewModel.backendRuntime.value.ifEmpty { "termux" })
                    put("backendPreCommand", viewModel.backendPreCommand.value.ifBlank { "python3 scripts/script.py" })
                }
            }
            json.toString(2)
        } catch (e: Exception) {
            "{}"
        }
    }

    fun applyPluginJson(text: String): Boolean {
        return try {
            val json = org.json.JSONObject(text)
            viewModel.pluginId.value = json.optString("pluginId", viewModel.pluginId.value)
            viewModel.pluginName.value = json.optString("name", viewModel.pluginName.value)
            viewModel.pluginAuthor.value = json.optString("author", viewModel.pluginAuthor.value)
            viewModel.pluginDescription.value = json.optString("description", viewModel.pluginDescription.value)
            viewModel.pluginVersion.value = json.optInt("version", viewModel.pluginVersion.value.toIntOrNull() ?: 1).toString()
            viewModel.pluginVersionName.value = json.optString("versionName", viewModel.pluginVersionName.value)
            viewModel.mainClass.value = json.optString("mainClass", viewModel.mainClass.value)
            viewModel.entryPath.value = json.optString("entry", viewModel.entryPath.value)
            viewModel.pluginNotice.value = json.optString("notice", viewModel.pluginNotice.value)
            viewModel.backendRuntime.value = json.optString("backendRuntime", viewModel.backendRuntime.value)
            viewModel.backendPreCommand.value = json.optString("backendPreCommand", viewModel.backendPreCommand.value)
            true
        } catch (e: Exception) {
            AppToast.error(context, "JSON 解析失败: ${e.message}")
            false
        }
    }

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

                workDir.mkdirs()
                outputTpk.parentFile?.mkdirs()

                viewModel.compileMessage.value = "生成项目文件..."
                viewModel.compileProgress.value = 10

                val saveSuccess = viewModel.generateProjectFiles(workDir)
                if (!saveSuccess) {
                    viewModel.compileMessage.value = "❌ 生成项目文件失败"
                    viewModel.isCompiling.value = false
                    AppToast.error(context, "生成项目文件失败")
                    return@launch
                }

                viewModel.compileMessage.value = "项目文件已生成"
                viewModel.compileProgress.value = 30

                if (uiType == "web" && backendType == "binary") {
                    val binaryPath = viewModel.binaryFilePath.value
                    if (binaryPath.isNotEmpty()) {
                        val srcFile = File(binaryPath)
                        val destFile = File(workDir, "backend/myapp")
                        destFile.parentFile?.mkdirs()
                        srcFile.copyTo(destFile, overwrite = true)
                        destFile.setExecutable(true)
                        AppLog.d(TAG, "二进制文件已复制: ${destFile.absolutePath}")
                    }
                }

                if (uiType == "native") {
                    viewModel.compileMessage.value = "开始编译 Java 代码..."
                    viewModel.compileProgress.value = 40

                    val compiler = JavaToDexCompiler(context)

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
                        AppToast.success(context, "✅ 编译打包成功!\n${outputTpk.absolutePath}")
                        onFinish()
                    }

                    compiler.setOnErrorListener { error ->
                        viewModel.compileMessage.value = "❌ 编译失败: $error"
                        viewModel.compileProgress.value = 0
                        viewModel.isCompiling.value = false
                        AppToast.error(context, "编译失败: $error")
                    }

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
                    viewModel.compileMessage.value = "打包插件..."
                    viewModel.compileProgress.value = 60

                    val compiler = JavaToDexCompiler(context)

                    compiler.setOnProgressListener { message ->
                        viewModel.compileMessage.value = message
                        when {
                            message.contains("打包") -> viewModel.compileProgress.value = 80
                        }
                    }

                    compiler.setOnCompleteListener { resultFile ->
                        viewModel.compileMessage.value = "✅ 打包完成!"
                        viewModel.compileProgress.value = 100
                        viewModel.isCompiling.value = false
                        viewModel.tpkFile.value = outputTpk
                        AppToast.success(context, "✅ 打包成功!\n${outputTpk.absolutePath}")
                        onFinish()
                    }

                    compiler.setOnErrorListener { error ->
                        viewModel.compileMessage.value = "❌ 打包失败: $error"
                        viewModel.compileProgress.value = 0
                        viewModel.isCompiling.value = false
                        AppToast.error(context, "打包失败: $error")
                    }

                    compiler.packageTpk(
                        projectDir = workDir,
                        outputTpk = outputTpk,
                        uiType = uiType,
                        hasDex = false
                    )
                }

            } catch (e: Exception) {
                AppLog.e(TAG, "编译打包异常", e)
                viewModel.compileMessage.value = "❌ 异常: ${e.message}"
                viewModel.isCompiling.value = false
                AppToast.error(context, "编译打包异常: ${e.message}")
            }
        }
    }

    fun getStepTitle(): String {
        return when (currentStep) {
            0 -> "配置插件信息"
            1 -> "设置插件图标"
            2 -> when {
                uiType == "web" && backendType == "binary" -> "选择二进制文件"
                uiType == "web" -> "Web 代码编辑"
                uiType == "cui" -> "编辑终端脚本"
                else -> "编写插件代码"
            }
            3 -> when {
                uiType == "web" && (backendType == "binary" || backendType.isEmpty()) -> "生成项目文件"
                uiType == "cui" -> "生成项目文件"
                else -> "添加资源文件"
            }
            4 -> "生成项目文件"
            else -> "生成项目文件"
        }
    }

    fun getStepDesc(): String {
        return when (currentStep) {
            0 -> "填写插件的基本配置信息"
            1 -> "选择一个 PNG 图片作为图标（可选）"
            2 -> when {
                uiType == "web" && backendType == "binary" -> "选择编译好的可执行二进制文件"
                uiType == "web" -> "编辑 HTML/CSS/JS 或导入已有项目"
                uiType == "cui" -> "编辑终端启动脚本，插件打开后会在终端中运行"
                else -> "实现 PluginInterface 接口"
            }
            3 -> when {
                uiType == "web" && (backendType == "binary" || backendType.isEmpty()) -> "生成项目文件并打包为 TPK"
                uiType == "cui" -> "生成项目文件并打包为 TPK"
                else -> "可选的图片、音频等资源文件"
            }
            4 -> "生成项目结构并打包为 TPK"
            else -> "生成项目文件"
        }
    }

    fun validateCurrentStep(): Boolean {
        return when (currentStep) {
            0 -> {
                if (viewModel.pluginId.value.isEmpty() || viewModel.pluginName.value.isEmpty()) {
                    AppToast.warning(context, "请填写插件ID和名称")
                    return false
                }
                if (!viewModel.pluginId.value.matches(Regex("^[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+$"))) {
                    AppToast.warning(context, "插件ID格式不正确，应为域名倒序格式")
                    return false
                }
                true
            }
            2 -> {
                if (uiType == "web" && backendType == "binary") {
                    if (viewModel.binaryFilePath.value.isEmpty()) {
                        AppToast.warning(context, "请选择二进制文件")
                        return false
                    }
                    true
                } else if (uiType == "native") {
                    if (viewModel.mainClass.value.isEmpty()) {
                        AppToast.warning(context, "请填写主类名")
                        return false
                    }
                    if (!viewModel.mainClass.value.contains(".")) {
                        AppToast.warning(context, "主类名必须包含包名，如 com.example.MainPlugin")
                        return false
                    }
                    true
                } else {
                    true
                }
            }
            else -> true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when {
                            uiType == "native" -> "创建原生插件"
                            backendType == "binary" -> "创建二进制后端插件"
                            uiType == "web" && backendType.isEmpty() -> "创建 Web 插件"
                            uiType == "cui" -> "创建 CUI 插件"
                            else -> "创建 Web + 后端插件"
                        }
                    )
                },
                navigationIcon = {
                    UIComponents.IconButton(
                        icon = Icons.Default.ArrowBack,
                        onClick = onFinish
                    )
                },
                actions = {
                    if (currentStep == 0) {
                        UIComponents.IconButton(
                            icon = Icons.Default.Code,
                            onClick = {
                                jsonDraft = buildPluginJson()
                                showJsonEditor = true
                            }
                        )
                    }
                    if (currentStep == 2 && backendType != "binary") {
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

            UIComponents.TitleText(
                getStepTitle(),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            UIComponents.BodyText(
                getStepDesc(),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

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
                        pluginNotice = viewModel.pluginNotice.value,
                        onPluginNoticeChange = { viewModel.pluginNotice.value = it },
                        uiType = uiType,
                        backendType = backendType,
                        backendRuntime = viewModel.backendRuntime.value,
                        onBackendRuntimeChange = { viewModel.backendRuntime.value = it },
                        backendPreCommand = viewModel.backendPreCommand.value,
                        onBackendPreCommandChange = { viewModel.backendPreCommand.value = it }
                    )
                    1 -> PluginIconStep(
                        iconPath = viewModel.iconPath.value,
                        onIconSelected = { viewModel.iconPath.value = it }
                    )
                    2 -> {
                        if (uiType == "web" && backendType == "binary") {
                            BinaryFileSelectionStep(
                                filePath = viewModel.binaryFilePath.value,
                                onFileSelected = { viewModel.binaryFilePath.value = it },
                                onFilePicker = { binaryFilePickerLauncher.launch("*/*") }
                            )
                        } else if (uiType == "native") {
                            NativeCodeStep(
                                onOpenEditor = { openCodeEditor() },
                                fileCount = viewModel.fileList.value.size
                            )
                        } else if (uiType == "cui") {
                            CuiCodeStep(
                                fileCount = viewModel.fileList.value.size,
                                onOpenEditor = { openCodeEditor() }
                            )
                        } else {
                            WebCodeStep(
                                fileCount = viewModel.fileList.value.size,
                                onOpenEditor = { openCodeEditor() },
                                onImportWebProject = { webImportLauncher.launch("application/zip") }
                            )
                        }
                    }
                    3 -> {
                        if (uiType == "web" && (backendType == "binary" || backendType.isEmpty())) {
                            PackageStep(
                                isCompiling = viewModel.isCompiling.value,
                                compileMessage = viewModel.compileMessage.value,
                                compileProgress = viewModel.compileProgress.value,
                                tpkFile = viewModel.tpkFile.value
                            )
                        } else if (uiType == "cui") {
                            PackageStep(
                                isCompiling = viewModel.isCompiling.value,
                                compileMessage = viewModel.compileMessage.value,
                                compileProgress = viewModel.compileProgress.value,
                                tpkFile = viewModel.tpkFile.value
                            )
                        } else {
                            ResourcesStep(
                                resourcePaths = viewModel.resourcePaths.value,
                                onResourceAdded = { viewModel.resourcePaths.value = viewModel.resourcePaths.value + it },
                                onResourceRemoved = { index ->
                                    viewModel.resourcePaths.value = viewModel.resourcePaths.value
                                        .filterIndexed { i, _ -> i != index }
                                }
                            )
                        }
                    }
                    4 -> {
                        PackageStep(
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

    // ============================================================
    // 编辑 plugin.json 对话框
    // ============================================================
    if (showJsonEditor) {
        AlertDialog(
            onDismissRequest = { showJsonEditor = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("编辑 plugin.json") },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = jsonDraft,
                        onValueChange = { jsonDraft = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(420.dp),
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            fontSize = 13.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "保存后将同步到表单各字段，字段包括 pluginId/name/version/entry/backendPreCommand 等。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (applyPluginJson(jsonDraft)) {
                            showJsonEditor = false
                            AppToast.success(context, "JSON 已应用")
                        }
                    }
                ) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { showJsonEditor = false }) { Text("取消") }
            }
        )
    }
}