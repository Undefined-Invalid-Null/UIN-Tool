// ui/screen/dev/PluginWizardViewModel.kt
package com.UIN.Tool.ui.screen.dev

import com.UIN.Tool.R
import com.UIN.Tool.utils.Str
import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.UIN.Tool.log.Logger
import com.UIN.Tool.utils.AppLog
import com.UIN.Tool.constants.AppConstants as Constants
import com.UIN.Tool.utils.TemplateUtils
import org.json.JSONObject
import java.io.File

private const val TAG = "PluginWizardViewModel"

class PluginWizardViewModel(
    private val context: Context,
    val uiType: String,
    val backendType: String = ""
) : ViewModel() {

    // ==================== 插件信息 ====================
    var pluginId = mutableStateOf("com.example.myplugin")
    var pluginName = mutableStateOf(Str.get(R.string.my_plugins))
    var pluginAuthor = mutableStateOf(Str.get(R.string.developer_2))
    var pluginDescription = mutableStateOf(Str.get(R.string.this_is_an_example_plugin))
    var pluginVersion = mutableStateOf("1")
    var pluginVersionName = mutableStateOf("1.0.0")
    var mainClass = mutableStateOf("com.example.MainPlugin")
    var entryPath = mutableStateOf("web/index.html")
    var webTemplateType = mutableStateOf(0)

    // ==================== 后端运行配置 ====================
    var backendRuntime = mutableStateOf("termux")
    var backendPreCommand = mutableStateOf("")
    var backendStartCommand = mutableStateOf("")
    var backendTimeout = mutableStateOf("30")
    var backendHealthCheck = mutableStateOf("/health")

    // ==================== 外部内容接收（openWith） ====================
    var openWithEnabled = mutableStateOf(false)
    var openWithLabel = mutableStateOf("")
    var openWithMimeTypes = mutableStateOf("")
    var openWithAcceptText = mutableStateOf(true)
    var openWithAcceptUrl = mutableStateOf(true)
    var openWithAcceptFile = mutableStateOf(true)

    // ==================== 插件说明 ====================
    var pluginNotice = mutableStateOf("")

    // ==================== 扩展配置 ====================
    var permissions = mutableStateOf<List<String>>(emptyList())
    var minHostVersion = mutableStateOf("1")
    var category = mutableStateOf("")
    var updateUrl = mutableStateOf("")

    // ==================== 文件管理 ====================
    var fileList = mutableStateOf<List<String>>(emptyList())
    var fileContents = mutableStateOf<Map<String, String>>(emptyMap())

    // ==================== 图标和资源 ====================
    var iconPath = mutableStateOf("")
    var resourcePaths = mutableStateOf<List<String>>(emptyList())

    // ==================== 二进制文件 ====================
    var binaryFilePath = mutableStateOf("")

    // ==================== 编译状态 ====================
    var isCompiling = mutableStateOf(false)
    var compileMessage = mutableStateOf("")
    var compileProgress = mutableStateOf(0)
    var tpkFile = mutableStateOf<File?>(null)
    var projectDir = mutableStateOf<File?>(null)

    init {
        // 二进制后端：前端入口固定为 web/index.html；二进制由打包阶段复制到 backend/myapp
        if (uiType == "web" && backendType == "binary") {
            entryPath.value = "web/index.html"
        }
        initDefaultFiles()
    }

    fun initDefaultFiles() {
        if (fileList.value.isEmpty() || fileContents.value.isEmpty()) {
            if (uiType == "native") {
                generateNativeCode()
            } else if (uiType == "cui") {
                generateCuiFiles()
            } else {
                generateWebTemplates()
                if (backendType.isNotEmpty()) {
                    generateBackendFiles()
                }
            }
        }
    }

    fun generateNativeCode() {
        val className = mainClass.value.substringAfterLast('.')
        val packageName = mainClass.value.substringBeforeLast('.')
        val packagePath = packageName.replace('.', '/')
        val javaFilePath = "src/$packagePath/$className.java"

        val vars = mapOf(
            "PACKAGE_NAME" to packageName,
            "CLASS_NAME" to className,
            "PLUGIN_NAME" to pluginName.value,
            "PLUGIN_ID" to pluginId.value,
            "PLUGIN_VERSION" to pluginVersion.value,
            "PLUGIN_VERSION_NAME" to pluginVersionName.value,
            "PLUGIN_AUTHOR" to pluginAuthor.value,
            "PLUGIN_DESCRIPTION" to pluginDescription.value
        )

        try {
            val javaCode = TemplateUtils.generateJavaCode(context, "native", vars)
            if (javaCode.isNotEmpty()) {
                fileList.value = listOf(javaFilePath)
                fileContents.value = mapOf(javaFilePath to javaCode)
            }
        } catch (e: Exception) {
            AppLog.e(TAG, Str.get(R.string.failed_to_generate_java_code), e)
        }
    }

    fun generateWebTemplates() {
        try {
            val vars = mapOf(
                "PLUGIN_NAME" to pluginName.value,
                "PLUGIN_ID" to pluginId.value,
                "PLUGIN_VERSION" to pluginVersion.value,
                "PLUGIN_VERSION_NAME" to pluginVersionName.value,
                "PLUGIN_AUTHOR" to pluginAuthor.value,
                "PLUGIN_DESCRIPTION" to pluginDescription.value
            )
            val files = TemplateUtils.generateWebTemplates(
                context,
                vars,
                if (uiType == "web" && backendType.isNotEmpty()) 2 else webTemplateType.value
            )

            fileList.value = files.keys.toList()
            fileContents.value = files

        } catch (e: Exception) {
            AppLog.e(TAG, Str.get(R.string.failed_to_generate_web_template), e)
        }
    }

    private fun generateCuiFiles() {
        try {
            val script = """
                #!/usr/bin/env python3
                # -*- coding: utf-8 -*-
                # ${Str.get(R.string.cui_plugin_script_comment)}
                import os

                print("=" * 48)
                print(Str.get(R.string.cui_plugin_terminal_started))
                print("=" * 48)
                print(Str.get(R.string.plugin_id_2) + os.environ.get("PLUGIN_ID", "?"))
                print(Str.get(R.string.plugin_dir) + os.getcwd())
                print("-" * 48)
                print(Str.get(R.string.type_exit_or_ctrl_d_to_end_the_sessi))

                import code
                code.interact(banner="", local=locals())
            """.trimIndent()

            fileList.value = listOf("scripts/script.py")
            fileContents.value = mapOf("scripts/script.py" to script)

            if (backendPreCommand.value.isBlank()) {
                backendPreCommand.value = "python3 scripts/script.py"
            }
        } catch (e: Exception) {
            AppLog.e(TAG, Str.get(R.string.failed_to_generate_cui_script), e)
        }
    }

    private fun generateBackendFiles() {
        if (uiType != "web" || backendType.isEmpty()) {
            return
        }

        val files = fileContents.value.toMutableMap()
        val fileNames = fileList.value.toMutableList()

        val vars = mapOf(
            "PLUGIN_NAME" to pluginName.value,
            "PLUGIN_ID" to pluginId.value,
            "PLUGIN_VERSION" to pluginVersion.value,
            "PLUGIN_VERSION_NAME" to pluginVersionName.value,
            "PLUGIN_AUTHOR" to pluginAuthor.value
        )

        // 二进制后端：启动脚本直接运行用户选择的二进制 backend/myapp
        if (backendType == "binary") {
            val startScript = TemplateUtils.generateBinaryBackendStartScript(context, vars)
            files["scripts/start.sh"] = startScript
            if ("scripts/start.sh" !in fileNames) fileNames.add("scripts/start.sh")

            entryPath.value = "web/index.html"
            if (backendStartCommand.value.isBlank()) {
                backendStartCommand.value = "sh scripts/start.sh"
            }

            fileList.value = fileNames
            fileContents.value = files
            return
        }

        // 新式后端：生成启动脚本 + 后端服务示例（不再按语言生成）
        val startScript = TemplateUtils.generateBackendStartScript(context, vars)
        val serverScript = TemplateUtils.generateBackendServer(context, vars)

        files["scripts/start.sh"] = startScript
        files["scripts/backend/server.py"] = serverScript

        for (path in listOf("scripts/start.sh", "scripts/backend/server.py")) {
            if (path !in fileNames) fileNames.add(path)
        }

        entryPath.value = "web/index.html"
        if (backendStartCommand.value.isBlank()) {
            backendStartCommand.value = "sh scripts/start.sh"
        }

        fileList.value = fileNames
        fileContents.value = files
    }

    fun updateFiles(files: List<String>, contents: Map<String, String>) {
        fileList.value = files
        fileContents.value = contents
    }

    /** 更新主类名并重新生成原生入口文件，避免代码编辑器始终停留在默认的 MainPlugin.java。 */
    fun setMainClass(value: String) {
        mainClass.value = value
        if (uiType == "native" && value.isNotBlank()) {
            val className = value.substringAfterLast('.')
            val packageName = value.substringBeforeLast('.')
            val packagePath = packageName.replace('.', '/')
            val expected = "src/$packagePath/$className.java"
            // 仅当当前文件列表不包含与主类名对应的入口文件时才重新生成（避免覆盖用户已编辑内容）
            if (fileList.value.none { it == expected }) {
                generateNativeCode()
            }
        }
    }

    suspend fun generateProjectFiles(workDir: File): Boolean {
        return try {
            workDir.mkdirs()

            val jsonContent = generatePluginJsonContent()
            val jsonFile = File(workDir, "plugin.json")
            jsonFile.writeText(jsonContent)

            fileContents.value.forEach { (path, content) ->
                val file = File(workDir, path)
                file.parentFile?.mkdirs()
                file.writeText(content)
            }

            if (iconPath.value.isNotEmpty() && File(iconPath.value).exists()) {
                val iconFile = File(workDir, "icon.png")
                File(iconPath.value).copyTo(iconFile, overwrite = true)
            }

            if (resourcePaths.value.isNotEmpty()) {
                val resDir = File(workDir, "res")
                resDir.mkdirs()
                resourcePaths.value.forEach { resPath ->
                    val srcRes = File(resPath)
                    if (srcRes.exists()) {
                        val dstRes = File(resDir, srcRes.name)
                        srcRes.copyTo(dstRes, overwrite = true)
                    }
                }
            }

            generateReadme(workDir)

            AppLog.success(TAG, Str.get(R.string.project_files_generated_workdir_abso, workDir.absolutePath))
            true

        } catch (e: Exception) {
            AppLog.e(TAG, Str.get(R.string.failed_to_generate_project_files_2), e)
            false
        }
    }

    private fun generatePluginJsonContent(): String {
        val json = JSONObject().apply {
            put("pluginId", pluginId.value)
            put("version", pluginVersion.value.toIntOrNull() ?: 1)
            put("versionName", pluginVersionName.value)
            put("minHostVersion", minHostVersion.value.toIntOrNull() ?: 1)
            put("name", pluginName.value)
            put("author", pluginAuthor.value)
            put("description", pluginDescription.value)
            put("icon", "icon.png")
            put("mainClass", if (uiType == "native") mainClass.value else "")
            put("uiType", uiType)
            put("entry", if (uiType == "web") entryPath.value else "")
            put("permissions", permissions.value.joinToString(","))
            put("notice", pluginNotice.value)
            put("category", category.value)
            put("updateUrl", updateUrl.value)

            if (uiType == "web" && backendType.isNotEmpty()) {
                // 新式后端：统一 other 模式 + 必填启动命令，运行环境由用户在软件内全局设定
                put("backend", "other")
                put("backendStartCommand", backendStartCommand.value.trim().ifBlank { "sh scripts/start.sh" })
                put("backendStartEntry", "scripts/start.sh")
                put("backendAutoStart", true)
                put("backendTimeout", backendTimeout.value.toIntOrNull() ?: 30)
                put("backendHealthCheck", backendHealthCheck.value.ifBlank { "/health" })
            }

            // 外部内容接收（openWith）
            if (openWithEnabled.value) {
                put("openWith", JSONObject().apply {
                    put("enabled", true)
                    put("label", openWithLabel.value)
                    put("mimeTypes", openWithMimeTypes.value)
                    put("acceptText", openWithAcceptText.value)
                    put("acceptUrl", openWithAcceptUrl.value)
                    put("acceptFile", openWithAcceptFile.value)
                })
            }

            // CUI 插件：无后端，打开终端时执行 pre-command 进入脚本
            if (uiType == "cui") {
                put("backendRuntime", backendRuntime.value.ifEmpty { "termux" })
                put("backendPreCommand", backendPreCommand.value.ifBlank { "python3 scripts/script.py" })
            }
        }
        return json.toString()
    }

    private fun generateReadme(workDir: File) {
        try {
            val uiTypeLabel = when (uiType) {
                "web" -> "WebView"
                "cui" -> Str.get(R.string.cui_terminal)
                else -> Str.get(R.string.native_code)
            }

            val directoryTree: String
            val devGuide: String
            val buildSteps: String
            val packageFiles: String

            when {
                // 原生插件：Java 源码 + 编译打包
                uiType == "native" -> {
                    val mainClassPath = mainClass.value.replace('.', '/') + ".java"
                    directoryTree = "├── src/                 # Java 源码目录\n    └── $mainClassPath"
                    devGuide = """## 开发说明

1. 编辑 `src/` 下的 Java 源码，实现 `PluginInterface` 接口。
2. `onCreateView` 返回插件主界面（原生 View）。
3. 调用宿主能力（日志、存储、HTTP 等）需先在该插件的权限管理页授权。
4. 打包前先在「管理 -> 运行日志」确认无编译错误。"""
                    buildSteps = """### 环境要求

- JDK 8 或更高版本
- Android SDK（包含 d8 工具）
- host-sdk.jar（从 UIN Tool 导出模板时附带）

### 编译命令

```bash
# 1. 编译 Java 源码
javac -source 8 -target 8 -cp host-sdk.jar -d . $(find src -name '*.java')

# 2. 打包为 JAR
jar cvf plugin.jar $(find . -name '*.class')

# 3. 转换为 DEX
d8 --release --lib android.jar --min-api 21 --output . plugin.jar

# 4. 重命名为 plugin.dex
mv classes.dex plugin.dex
```"""
                    packageFiles = "├── plugin.dex\n├── plugin.json\n└── icon.png"
                }

                // CUI 插件：终端脚本，无编译
                uiType == "cui" -> {
                    directoryTree = "├── scripts/             # 终端脚本目录\n    └── script.py"
                    devGuide = """## 开发说明

1. 编辑 `scripts/script.py`，宿主持环境变量 `PLUGIN_ID`、`PLUGIN_DIR`。
2. 插件在终端中运行，脚本内可用 `print` 输出，退出 `exit` 或 `Ctrl+D` 结束。
3. 运行环境由用户在软件的全局设定中选择（Termux 或 proot 容器）。"""
                    buildSteps = """### 无需编译

CUI 插件为脚本型插件，无需 Java 编译，直接打包即可。"""
                    packageFiles = "├── scripts/script.py\n├── plugin.json\n└── icon.png"
                }

                // Web 插件 + 后端：前端页面 + 后端启动脚本
                uiType == "web" && backendType.isNotEmpty() -> {
                    directoryTree = "├── web/                 # 前端页面目录（index.html 等）\n├── scripts/             # 后端启动脚本与示例\n    ├── start.sh\n    └── backend/server.py"
                    devGuide = """## 开发说明

1. 编辑 `web/index.html` 编写前端页面，通过 `UINPlugin` 调用宿主能力。
2. 后端由 `scripts/start.sh` 启动（读取宿主导入的 `PORT` 环境变量），
   `scripts/backend/server.py` 为示例服务（含 `/health` 健康检查）。
3. 前端调用后端统一走 `UINPlugin.callBackendApi(path, method, body)`，无需关心端口。
4. 调用宿主能力（剪贴板、存储、HTTP 等）需先在该插件的权限管理页授权。"""
                    buildSteps = """### 无需编译

前端 + 脚本后端无需 Java 编译，直接打包即可。"""
                    packageFiles = "├── web/                 # 前端页面\n├── scripts/             # 后端启动脚本与示例\n├── plugin.json\n└── icon.png"
                }

                // Web 插件（无后端）：纯前端页面
                else -> {
                    directoryTree = "├── web/                 # 前端页面目录（index.html 等）"
                    devGuide = """## 开发说明

1. 编辑 `web/index.html` 编写前端页面，通过 `UINPlugin` 调用宿主能力。
2. 调用宿主能力（剪贴板、存储、HTTP 等）需先在该插件的权限管理页授权。
3. 无需后端时，前端直接调用 `UINPlugin` 的 JS 桥方法即可。"""
                    buildSteps = """### 无需编译

纯前端插件无需 Java 编译，直接打包即可。"""
                    packageFiles = "├── web/                 # 前端页面\n├── plugin.json\n└── icon.png"
                }
            }

            val vars = mapOf(
                "PLUGIN_NAME" to pluginName.value,
                "PLUGIN_ID" to pluginId.value,
                "PLUGIN_VERSION" to pluginVersion.value,
                "PLUGIN_VERSION_NAME" to pluginVersionName.value,
                "PLUGIN_AUTHOR" to pluginAuthor.value,
                "PLUGIN_DESCRIPTION" to pluginDescription.value,
                "UI_TYPE" to uiTypeLabel,
                "DIRECTORY_TREE" to directoryTree,
                "DEV_GUIDE" to devGuide,
                "BUILD_STEPS" to buildSteps,
                "PACKAGE_FILES" to packageFiles
            )
            val readme = TemplateUtils.generateReadme(context, vars)
            File(workDir, "README.md").writeText(readme)
        } catch (e: Exception) {
            AppLog.e(TAG, Str.get(R.string.failed_to_generate_readme), e)
        }
    }
}