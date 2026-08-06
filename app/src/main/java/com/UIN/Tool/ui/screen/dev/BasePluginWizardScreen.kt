// ui/screen/dev/BasePluginWizardScreen.kt
package com.UIN.Tool.ui.screen.dev

import com.UIN.Tool.R
import com.UIN.Tool.utils.Str
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
import com.UIN.Tool.constants.AppConstants as Constants
import kotlinx.coroutines.launch
import java.io.File
import com.UIN.Tool.ui.theme.AppColors
import com.UIN.Tool.ui.theme.AppDimens

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
                AppToast.success(context, Str.get(R.string.binary_file_selected))
            }
        }
    }

    val webImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            handleWebProjectImport(context, uri) { files, contents ->
                viewModel.updateFiles(files, contents)
                AppToast.success(context, Str.get(R.string.web_project_imported_files_size_file, files.size))
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
            AppToast.info(context, Str.get(R.string.code_updated_updatedfiles_size_file_, updatedFiles.size))
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
                if (uiType == "web" && backendType.isNotEmpty()) {
                    put("backend", "other")
                    put("backendStartCommand", viewModel.backendStartCommand.value.trim().ifBlank { "sh scripts/start.sh" })
                    put("backendStartEntry", "scripts/start.sh")
                    put("backendAutoStart", true)
                    put("backendTimeout", 30)
                    put("backendHealthCheck", "/health")
                }
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
            viewModel.backendStartCommand.value = json.optString("backendStartCommand", viewModel.backendStartCommand.value)
            true
        } catch (e: Exception) {
            AppToast.error(context, Str.get(R.string.json_parse_failed_e_message, e.message))
            false
        }
    }

    fun startCompileAndPackage() {
        scope.launch {
            try {
                viewModel.isCompiling.value = true
                viewModel.compileMessage.value = Str.get(R.string.preparing_build_environment)
                viewModel.compileProgress.value = 0

                val safePluginId = viewModel.pluginId.value
                    .replace("/", "_")
                    .replace("\\", "_")
                    .replace(":", "_")

                val workDir = File(Constants.PLUGIN_DIR, safePluginId)
                val outputTpk = File(Constants.TPK_DIR, "$safePluginId.tpk")

                workDir.mkdirs()
                outputTpk.parentFile?.mkdirs()

                viewModel.compileMessage.value = Str.get(R.string.generating_project_files)
                viewModel.compileProgress.value = 10

                val saveSuccess = viewModel.generateProjectFiles(workDir)
                if (!saveSuccess) {
                    viewModel.compileMessage.value = Str.get(R.string.failed_to_generate_project_files)
                    viewModel.isCompiling.value = false
                    AppToast.error(context, Str.get(R.string.failed_to_generate_project_files_2))
                    return@launch
                }

                viewModel.compileMessage.value = Str.get(R.string.project_files_generated)
                viewModel.compileProgress.value = 30

                if (uiType == "web" && backendType == "binary") {
                    val binaryPath = viewModel.binaryFilePath.value
                    if (binaryPath.isNotEmpty()) {
                        val srcFile = File(binaryPath)
                        val destFile = File(workDir, "backend/myapp")
                        destFile.parentFile?.mkdirs()
                        srcFile.copyTo(destFile, overwrite = true)
                        destFile.setExecutable(true)
                        AppLog.d(TAG, Str.get(R.string.binary_file_copied_destfile_absolute, destFile.absolutePath))
                    }
                }

                if (uiType == "native") {
                    viewModel.compileMessage.value = Str.get(R.string.compiling_java_code)
                    viewModel.compileProgress.value = 40

                    val compiler = JavaToDexCompiler(context)

                    compiler.setOnProgressListener { message ->
                        viewModel.compileMessage.value = message
                        when {
                            message.contains(Str.get(R.string.compile)) -> viewModel.compileProgress.value = 50
                            message.contains("JAR") -> viewModel.compileProgress.value = 65
                            message.contains("DEX") -> viewModel.compileProgress.value = 75
                            message.contains(Str.get(R.string.package_label)) -> viewModel.compileProgress.value = 85
                        }
                    }

                    compiler.setOnCompleteListener { resultFile ->
                        viewModel.compileMessage.value = Str.get(R.string.compile_and_package_complete)
                        viewModel.compileProgress.value = 100
                        viewModel.isCompiling.value = false
                        viewModel.tpkFile.value = outputTpk
                        AppToast.success(context, Str.get(R.string.compile_and_package_successful_n_out, outputTpk.absolutePath))
                        onFinish()
                    }

                    compiler.setOnErrorListener { error ->
                        viewModel.compileMessage.value = Str.get(R.string.compilation_failed_error, error)
                        viewModel.compileProgress.value = 0
                        viewModel.isCompiling.value = false
                        AppToast.error(context, Str.get(R.string.compilation_failed_error_2, error))
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
                    viewModel.compileMessage.value = Str.get(R.string.packaging_plugin)
                    viewModel.compileProgress.value = 60

                    val compiler = JavaToDexCompiler(context)

                    compiler.setOnProgressListener { message ->
                        viewModel.compileMessage.value = message
                        when {
                            message.contains(Str.get(R.string.package_label)) -> viewModel.compileProgress.value = 80
                        }
                    }

                    compiler.setOnCompleteListener { resultFile ->
                        viewModel.compileMessage.value = Str.get(R.string.packaging_complete)
                        viewModel.compileProgress.value = 100
                        viewModel.isCompiling.value = false
                        viewModel.tpkFile.value = outputTpk
                        AppToast.success(context, Str.get(R.string.packaging_successful_n_outputtpk_abs, outputTpk.absolutePath))
                        onFinish()
                    }

                    compiler.setOnErrorListener { error ->
                        viewModel.compileMessage.value = Str.get(R.string.packaging_failed_error, error)
                        viewModel.compileProgress.value = 0
                        viewModel.isCompiling.value = false
                        AppToast.error(context, Str.get(R.string.packaging_failed_error_2, error))
                    }

                    compiler.packageTpk(
                        projectDir = workDir,
                        outputTpk = outputTpk,
                        uiType = uiType,
                        hasDex = false
                    )
                }

            } catch (e: Exception) {
                AppLog.e(TAG, Str.get(R.string.compile_package_exception), e)
                viewModel.compileMessage.value = Str.get(R.string.exception_e_message, e.message)
                viewModel.isCompiling.value = false
                AppToast.error(context, Str.get(R.string.compile_package_exception_e_message, e.message))
            }
        }
    }

    fun getStepTitle(): String {
        return when (currentStep) {
            0 -> Str.get(R.string.configure_plugin_info)
            1 -> Str.get(R.string.set_plugin_icon)
            2 -> when {
                uiType == "web" && backendType == "binary" -> Str.get(R.string.select_binary_file)
                uiType == "web" -> Str.get(R.string.web_code_editor)
                uiType == "cui" -> Str.get(R.string.edit_terminal_script)
                else -> Str.get(R.string.write_plugin_code)
            }
            3 -> when {
                uiType == "web" && (backendType == "binary" || backendType.isEmpty()) -> Str.get(R.string.generate_project_files)
                uiType == "cui" -> Str.get(R.string.generate_project_files)
                else -> Str.get(R.string.add_resource_files)
            }
            4 -> Str.get(R.string.generate_project_files)
            else -> Str.get(R.string.generate_project_files)
        }
    }

    fun getStepDesc(): String {
        return when (currentStep) {
            0 -> Str.get(R.string.fill_in_the_plugin_basic_configurati)
            1 -> Str.get(R.string.choose_a_png_image_as_the_icon_optio)
            2 -> when {
                uiType == "web" && backendType == "binary" -> Str.get(R.string.select_a_compiled_executable_binary_)
                uiType == "web" -> Str.get(R.string.edit_html_css_js_or_import_an_existi)
                uiType == "cui" -> Str.get(R.string.edit_the_terminal_launch_script_that)
                else -> Str.get(R.string.implement_the_plugininterface)
            }
            3 -> when {
                uiType == "web" && (backendType == "binary" || backendType.isEmpty()) -> Str.get(R.string.generate_project_files_and_package_a)
                uiType == "cui" -> Str.get(R.string.generate_project_files_and_package_a)
                else -> Str.get(R.string.optional_resources_like_images_and_a)
            }
            4 -> Str.get(R.string.generate_the_project_structure_and_p)
            else -> Str.get(R.string.generate_project_files)
        }
    }

    fun validateCurrentStep(): Boolean {
        return when (currentStep) {
            0 -> {
                if (viewModel.pluginId.value.isEmpty() || viewModel.pluginName.value.isEmpty()) {
                    AppToast.warning(context, Str.get(R.string.please_fill_in_the_plugin_id_and_nam))
                    return false
                }
                if (!viewModel.pluginId.value.matches(Regex("^[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+$"))) {
                    AppToast.warning(context, Str.get(R.string.invalid_plugin_id_it_should_be_a_rev))
                    return false
                }
                true
            }
            2 -> {
                if (uiType == "web" && backendType == "binary") {
                    if (viewModel.binaryFilePath.value.isEmpty()) {
                        AppToast.warning(context, Str.get(R.string.please_select_a_binary_file))
                        return false
                    }
                    true
                } else if (uiType == "native") {
                    if (viewModel.mainClass.value.isEmpty()) {
                        AppToast.warning(context, Str.get(R.string.please_fill_in_the_main_class_name))
                        return false
                    }
                    if (!viewModel.mainClass.value.contains(".")) {
                        AppToast.warning(context, Str.get(R.string.the_main_class_name_must_include_the))
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
                            uiType == "native" -> Str.get(R.string.create_native_plugin)
                            backendType == "binary" -> Str.get(R.string.create_binary_backend_plugin)
                            uiType == "web" && backendType.isEmpty() -> Str.get(R.string.create_web_plugin)
                            uiType == "cui" -> Str.get(R.string.create_cui_plugin)
                            else -> Str.get(R.string.create_web_backend_plugin)
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
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                    actionIconContentColor = MaterialTheme.colorScheme.onBackground
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
                            text = Str.get(R.string.previous),
                            onClick = { if (currentStep > 0) currentStep-- },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    if (currentStep < totalSteps - 1) {
                        UIComponents.PrimaryButton(
                            text = Str.get(R.string.next),
                            onClick = {
                                if (validateCurrentStep()) {
                                    if (currentStep < totalSteps - 1) currentStep++
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        UIComponents.PrimaryButton(
                            text = if (viewModel.isCompiling.value) Str.get(R.string.working) else Str.get(R.string.finish),
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
                        backendPreCommand = viewModel.backendPreCommand.value,
                        onBackendPreCommandChange = { viewModel.backendPreCommand.value = it },
                        backendStartCommand = viewModel.backendStartCommand.value,
                        onBackendStartCommandChange = { viewModel.backendStartCommand.value = it }
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
            containerColor = if (AppColors.glassEnabled())
                AppColors.glassBackground()
            else
                MaterialTheme.colorScheme.surface,
            title = { Text(Str.get(R.string.edit_plugin_json)) },
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
                            fontSize = AppDimens.bodyTextSize.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        Str.get(R.string.saved_values_are_synced_to_the_form_),
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
                            AppToast.success(context, Str.get(R.string.json_applied))
                        }
                    }
                ) { Text(Str.get(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = { showJsonEditor = false }) { Text(Str.get(R.string.cancel)) }
            }
        )
    }
}