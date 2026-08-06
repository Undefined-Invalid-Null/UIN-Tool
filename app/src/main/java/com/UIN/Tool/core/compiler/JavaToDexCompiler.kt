// app/src/main/java/com/UIN/Tool/core/compiler/JavaToDexCompiler.kt
package com.UIN.Tool.core.compiler

import com.UIN.Tool.R
import com.UIN.Tool.utils.Str
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.UIN.Tool.log.Logger
import java.io.File

/**
 * Java 源码编译为 DEX 文件
 * 
 * ⚠️ 注意：当前为占位实现，原生插件编译功能暂时禁用
 * 原因：ECJ 在 Android 上需要 tools.jar 提供 javax.lang.model 支持，
 * 但由于 Android 环境的类加载机制，该功能暂不可用。
 * 
 * TODO: 后续通过以下方式修复：
 * 1. 使用 Gradle 依赖方式引入 ECJ 和 tools.jar
 * 2. 或者使用 Janino 替代 ECJ（但 Janino 不支持 default 方法）
 * 3. 或者构建独立的编译服务（在 PC 端编译）
 */
class JavaToDexCompiler(
    private val context: Context
) {

    companion object {
        private const val TAG = "JavaToDexCompiler"
    }

    private var onProgress: ((String) -> Unit)? = null
    private var onComplete: ((File?) -> Unit)? = null
    private var onError: ((String) -> Unit)? = null

    private val mainHandler = Handler(Looper.getMainLooper())

    fun setOnProgressListener(listener: (String) -> Unit) {
        onProgress = listener
    }

    fun setOnCompleteListener(listener: (File?) -> Unit) {
        onComplete = listener
    }

    fun setOnErrorListener(listener: (String) -> Unit) {
        onError = listener
    }

    private fun updateProgress(message: String) {
        Logger.d(TAG, Str.get(R.string.progress_message, message))
        mainHandler.post {
            onProgress?.invoke(message)
        }
    }

    private fun showToast(message: String) {
        mainHandler.post {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    // ==================== 主入口（空实现） ====================

    suspend fun compileAndPackage(
        javaSrcDir: File,
        projectDir: File,
        outputTpk: File,
        uiType: String = "native",
        mainClass: String = ""
    ) {
        Logger.enter(TAG, "compileAndPackage")
        Logger.w(TAG, Str.get(R.string.native_plugin_compilation_temporaril))
        Logger.w(TAG, Str.get(R.string.source_dir_javasrcdir_absolutepath, javaSrcDir.absolutePath))
        Logger.w(TAG, Str.get(R.string.project_dir_projectdir_absolutepath, projectDir.absolutePath))
        Logger.w(TAG, Str.get(R.string.output_file_outputtpk_absolutepath, outputTpk.absolutePath))
        Logger.w(TAG, Str.get(R.string.ui_type_uitype, uiType))
        Logger.w(TAG, Str.get(R.string.main_class_mainclass, mainClass))

        updateProgress(Str.get(R.string.native_plugin_compilation_temporaril))

        // 如果是 Web 插件，直接打包
        if (uiType == "web") {
            Logger.i(TAG, Str.get(R.string.web_plugin_packaging_tpk_directly))
            packageTpk(projectDir, outputTpk, uiType, false)
            return
        }

        // 原生插件：返回错误
        val errorMsg = Str.get(R.string.native_plugin_compilation_is_current)
        Logger.e(TAG, errorMsg)
        mainHandler.post {
            onError?.invoke(errorMsg)
        }
        
        // 仍然尝试生成一个占位 TPK
        try {
            packageTpk(projectDir, outputTpk, uiType, false)
            Logger.w(TAG, Str.get(R.string.generated_placeholder_tpk_outputtpk_, outputTpk.absolutePath))
        } catch (e: Exception) {
            Logger.e(TAG, Str.get(R.string.failed_to_generate_placeholder_tpk), e)
        }
        
        Logger.exit(TAG, "compileAndPackage", System.currentTimeMillis())
    }

    // ==================== 打包 TPK ====================

    suspend fun packageTpk(
        projectDir: File,
        outputTpk: File,
        uiType: String = "native",
        hasDex: Boolean = false
    ): Boolean {
        Logger.enter(TAG, "packageTpk")
        
        return try {
            Logger.d(TAG, "projectDir: ${projectDir.absolutePath}")
            Logger.d(TAG, "outputTpk: ${outputTpk.absolutePath}")
            Logger.d(TAG, "uiType: $uiType")
            Logger.d(TAG, "hasDex: $hasDex")
            
            outputTpk.parentFile?.mkdirs()
            if (outputTpk.exists()) {
                outputTpk.delete()
                Logger.d(TAG, Str.get(R.string.removing_existing_tpk_file))
            }

            Logger.d(TAG, Str.get(R.string.packaging_tpk))

            java.util.zip.ZipOutputStream(java.io.FileOutputStream(outputTpk)).use { zos ->
                // 顶层特殊文件（已在下方显式处理，遍历整棵树时跳过，避免重复条目）
                val explicitTopEntries = setOf("plugin.json", "icon.png", "README.md")

                // 1. 添加 plugin.json
                val jsonFile = File(projectDir, "plugin.json")
                if (jsonFile.exists()) {
                    addFileToZip(zos, jsonFile, "plugin.json")
                    Logger.d(TAG, "  ✅ plugin.json (${jsonFile.length()} bytes)")
                } else {
                    Logger.w(TAG, Str.get(R.string.plugin_json_missing))
                }

                // 2. 添加 icon.png
                val iconFile = File(projectDir, "icon.png")
                if (iconFile.exists()) {
                    addFileToZip(zos, iconFile, "icon.png")
                    Logger.d(TAG, "  ✅ icon.png (${iconFile.length()} bytes)")
                }

                // 3. 原生插件：优先打包项目内已有的真实 plugin.dex；否则补充占位 DEX
                //    （占位实现，正式编译需 PC 端生成真实 DEX）
                if (uiType != "web" && uiType != "cui") {
                    val realDex = File(projectDir, "plugin.dex")
                    // DEX 文件以 0x64 0x65 0x78 0x0A（"dex\n"）开头为真实 DEX，占位文本则视为无效
                    val isRealDex = realDex.exists() && realDex.isFile && realDex.length() > 8 &&
                        runCatching {
                            val magic = ByteArray(8)
                            val n = realDex.inputStream().use { it.read(magic) }
                            n == 8 && magic[0] == 0x64.toByte() && magic[1] == 0x65.toByte() &&
                                magic[2] == 0x78.toByte() && magic[3] == 0x0A.toByte()
                        }.getOrDefault(false)
                    if (isRealDex) {
                        addFileToZip(zos, realDex, "plugin.dex")
                        Logger.d(TAG, "  ✅ plugin.dex (real, ${realDex.length()} bytes)")
                    } else {
                        zos.putNextEntry(java.util.zip.ZipEntry("plugin.dex"))
                        zos.write("// 原生插件编译功能暂时禁用".toByteArray())
                        zos.closeEntry()
                        Logger.w(TAG, Str.get(R.string.plugin_dex_placeholder))
                    }
                }

                // 4. 打包项目内全部内容（web/、scripts/、scripts/backend/、res/、src/、任意资源等）
                //    若 Web 插件缺少 web/index.html，则写入默认页面兜底
                if (uiType == "web") {
                    val webIndex = File(projectDir, "web/index.html")
                    if (!webIndex.exists()) {
                        val webDir = File(projectDir, "web")
                        webDir.mkdirs()
                        File(webDir, "index.html").writeText(getDefaultWebHtml())
                        Logger.d(TAG, Str.get(R.string.web_index_html_default))
                    }
                }

                addTreeToZip(zos, projectDir, "", explicitTopEntries)
                Logger.d(TAG, Str.get(R.string.all_project_content_packaged))

                // 5. 添加 README.md（显式追加，保证始终存在）
                val readmeFile = File(projectDir, "README.md")
                if (readmeFile.exists()) {
                    addFileToZip(zos, readmeFile, "README.md")
                    Logger.d(TAG, "  ✅ README.md (${readmeFile.length()} bytes)")
                }
            }

            Logger.success(TAG, Str.get(R.string.tpk_packaged_outputtpk_absolutepath_, outputTpk.absolutePath, outputTpk.length() / 1024))
            
            mainHandler.post {
                onComplete?.invoke(outputTpk)
            }
            
            true

        } catch (e: Exception) {
            Logger.e(TAG, Str.get(R.string.tpk_packaging_failed), e)
            mainHandler.post {
                onError?.invoke(e.message ?: Str.get(R.string.tpk_packaging_failed_2))
            }
            false
        } finally {
            Logger.exit(TAG, "packageTpk", System.currentTimeMillis())
        }
    }

    // ==================== 辅助方法 ====================

    private fun addFileToZip(
        zos: java.util.zip.ZipOutputStream,
        file: File,
        entryName: String
    ) {
        if (!file.exists()) return
        try {
            zos.putNextEntry(java.util.zip.ZipEntry(entryName))
            java.io.FileInputStream(file).use { fis ->
                fis.copyTo(zos)
            }
            zos.closeEntry()
        } catch (e: Exception) {
            Logger.e(TAG, Str.get(R.string.failed_to_add_file_entryname, entryName), e)
        }
    }

    private fun addDirToZip(
        zos: java.util.zip.ZipOutputStream,
        dir: File,
        basePath: String
    ) {
        if (!dir.exists() || !dir.isDirectory) return

        dir.listFiles()?.forEach { file ->
            if (file.name.startsWith(".")) return@forEach

            val entryName = basePath + file.name
            if (file.isDirectory) {
                zos.putNextEntry(java.util.zip.ZipEntry("$entryName/"))
                zos.closeEntry()
                addDirToZip(zos, file, "$entryName/")
            } else {
                addFileToZip(zos, file, entryName)
            }
        }
    }

    /**
     * 递归打包整个项目目录的所有内容（不遗漏任何子目录 / 文件）。
     *
     * 跳过隐藏文件（.xxx）与打包输出物（.tpk），并排除已显式处理的顶层条目。
     *
     * @param zos 目标 zip 输出流
     * @param dir 当前目录
     * @param basePath 当前目录在 zip 内的前缀（"" 表示顶层，其余以 "/" 结尾）
     * @param excludeTop 顶层需排除的文件名集合
     */
    private fun addTreeToZip(
        zos: java.util.zip.ZipOutputStream,
        dir: File,
        basePath: String,
        excludeTop: Set<String> = emptySet()
    ) {
        if (!dir.exists() || !dir.isDirectory) return

        dir.listFiles()?.sortedBy { it.name }?.forEach { file ->
            val name = file.name
            if (name.startsWith(".")) return@forEach
            if (basePath.isEmpty() && (name in excludeTop || name.endsWith(".tpk"))) return@forEach

            val entryName = basePath + name
            if (file.isDirectory) {
                zos.putNextEntry(java.util.zip.ZipEntry("$entryName/"))
                zos.closeEntry()
                addTreeToZip(zos, file, "$entryName/", excludeTop)
            } else {
                addFileToZip(zos, file, entryName)
            }
        }
    }

    private fun getDefaultWebHtml(): String {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>${Str.get(R.string.web_plugin)}</title>
                <style>
                    body { font-family: sans-serif; padding: 20px; text-align: center; background: #f5f5f5; margin: 0; }
                    .card { max-width: 400px; margin: 40px auto; background: white; border-radius: 16px; padding: 32px; box-shadow: 0 4px 12px rgba(0,0,0,0.1); }
                    h1 { color: #37474F; margin-bottom: 16px; }
                    button { background: #37474F; color: white; border: none; padding: 12px 24px; border-radius: 8px; margin: 8px; cursor: pointer; }
                    button:hover { background: #263238; }
                </style>
            </head>
            <body>
                <div class="card">
                    <h1>${Str.get(R.string.web_plugin)}</h1>
                    <button onclick="UINPlugin.callHost('toast', 'Hello from Web Plugin!')">${Str.get(R.string.click_me)}</button>
                    <button onclick="UINPlugin.callHost('finish', '')">${Str.get(R.string.close)}</button>
                    <script>console.log('${Str.get(R.string.web_plugin_loaded)}');</script>
                </div>
            </body>
            </html>
        """.trimIndent()
    }

    suspend fun compileAndPackageSimple(
        projectDir: File,
        outputTpk: File,
        uiType: String = "native"
    ) {
        val srcDir = File(projectDir, "src")
        val mainClass = getMainClass(projectDir)
        
        compileAndPackage(
            javaSrcDir = srcDir,
            projectDir = projectDir,
            outputTpk = outputTpk,
            uiType = uiType,
            mainClass = mainClass
        )
    }

    private fun getMainClass(projectDir: File): String {
        val jsonFile = File(projectDir, "plugin.json")
        if (!jsonFile.exists()) return ""
        
        return try {
            val json = jsonFile.readText()
            val obj = org.json.JSONObject(json)
            obj.optString("mainClass", "")
        } catch (e: Exception) {
            ""
        }
    }
}