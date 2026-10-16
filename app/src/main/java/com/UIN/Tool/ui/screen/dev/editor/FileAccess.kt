// app/src/main/java/com/UIN/Tool/ui/screen/dev/editor/FileAccess.kt
package com.UIN.Tool.ui.screen.dev.editor

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.UIN.Tool.utils.AppLog
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * 编辑器的文件访问层。
 * D2 口径（用户拍板）：默认申请文件访问权限，获批后直接用 java.io.File 读写；
 * 外部 ACTION_VIEW 传来的 content:// URI 用 ContentResolver 兜底。
 */
object FileAccess {

    private const val TAG = "EditorFileAccess"

    /**
     * 老式 provider 的文件路径列名。
     * 注意：`OpenableColumns` 只定义了 `DISPLAY_NAME` 与 `SIZE`，没有 `DATA`，
     * 路径列属于 MediaStore 的 `_data`，故此处用列名字面量。
     */
    private const val COLUMN_DATA = "_data"

    /** 文本类扩展名白名单 —— 决定能否作为文本打开，也用于语言识别。 */
    val TEXT_EXTENSIONS: Set<String> = setOf(
        "txt", "text", "log", "md", "markdown", "rst", "adoc",
        "java", "kt", "kts", "scala", "groovy", "gradle",
        "xml", "html", "htm", "xhtml", "css", "scss", "less",
        "js", "mjs", "cjs", "jsx", "ts", "tsx", "json", "json5", "jsonc",
        "py", "pyw", "rb", "php", "pl", "pm", "lua", "r", "dart",
        "go", "rs", "c", "cc", "cpp", "cxx", "h", "hpp", "hh", "m", "mm",
        "cs", "swift", "sh", "bash", "zsh", "fish", "ps1", "bat", "cmd",
        "sql", "yaml", "yml", "toml", "ini", "cfg", "conf", "properties",
        "gitignore", "gitattributes", "editorconfig", "dockerfile", "makefile",
        "csv", "tsv", "env", "pro", "mk", "cmake", "patch", "diff",
        "clj", "cljs", "ex", "exs", "erl", "hs", "fs", "fsx", "vb", "asm",
        "hlsl", "glsl", "shader", "pug", "jade", "ejs", "hbs", "twig", "vue"
    )

    /**
     * 6.2.0 批次40：单个文件的读取**硬上限**。
     *
     * 此前只有 `LARGE_FILE_BYTES`（2MB，超了转只读），再大就照读不误，
     * 撑爆了走 `OutOfMemoryError` → `readText` 返回 null → 用户看到的是
     * 「**读取失败: xxx**」，与真正的权限/IO 失败混为一谈，完全指不出真实原因。
     *
     * 8MB 的依据：解码成 UTF-16 已是 16MB，再进 Content 的行结构、撤销栈、
     * 渲染缓存要翻几倍，低内存机器上必然 OOM。宁可早一点用准确文案拦住。
     */
    const val HARD_LIMIT_BYTES: Long = 8L * 1024 * 1024

    /**
     * 读取结果 —— 把「文件太大，这台机器装不下」和「真的读不动了」分开。
     * 光靠 `String?` 无法区分，调用方只能一律报「读取失败」。
     */
    sealed interface ReadResult {
        class Ok(val text: String) : ReadResult
        class TooLarge(val bytes: Long) : ReadResult
        object Unreadable : ReadResult
    }

    /** 无扩展名但按名字识别为文本的文件。 */
    private val TEXT_BASENAMES = setOf(
        "makefile", "dockerfile", "rakefile", "gemfile", "vagrantfile",
        "license", "readme", "changelog", "notice", "authors", "contributing"
    )

    fun isTextFile(fileName: String): Boolean {
        val lower = fileName.lowercase()
        val ext = lower.substringAfterLast('.', "")
        if (ext.isNotEmpty() && ext in TEXT_EXTENSIONS) return true
        if (ext.isEmpty() || ext == lower) return lower in TEXT_BASENAMES
        return false
    }

    /**
     * 6.2.0 批次26：路径是否落在 Android 11+ 的系统保护目录（其他应用的 Android/data、
     * Android/obb）。这些目录即便授予「所有文件访问权限」也读不到（EACCES 硬拦截）。
     * 用于给出「请改用系统文件选择器」的明确提示，而不是含糊的读取失败。
     */
    fun isProtectedPath(path: String): Boolean {
        val p = path.replace('\\', '/')
        return p.contains("/Android/data/", ignoreCase = true) ||
            p.endsWith("/Android/data", ignoreCase = true) ||
            p.contains("/Android/obb/", ignoreCase = true) ||
            p.endsWith("/Android/obb", ignoreCase = true)
    }

    // ==================== 权限 ====================

    /** 是否已具备直接读写外部存储的能力。 */
    fun hasStorageAccess(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.READ_EXTERNAL_STORAGE
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    }

    /** Android 11+ 需要跳系统设置授予「所有文件访问」；旧版返回 null（走运行时申请）。 */
    fun manageAllFilesIntent(): Intent? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = Uri.parse("package:${contextPackageNameHint}")
            }
        } else null
    }

    /** 由调用方注入包名，避免本对象持有 Context。 */
    @Volatile
    private var contextPackageNameHint: String = ""

    fun initPackageName(context: Context) {
        contextPackageNameHint = context.packageName
    }

    // ==================== 直接文件 IO ====================

    fun readText(file: File): ReadResult {
        if (!file.isFile || !file.canRead()) {
            AppLog.w(TAG, "readText: unreadable ${file.absolutePath}")
            return ReadResult.Unreadable
        }
        // 先量大小再读：不要等 OOM 才发现文件太大。
        val size = file.length()
        if (size > HARD_LIMIT_BYTES) {
            AppLog.w(TAG, "readText: over hard limit ${file.absolutePath} ($size bytes)")
            return ReadResult.TooLarge(size)
        }
        return try {
            ReadResult.Ok(decodeText(file.readBytes()))
        } catch (e: IOException) {
            AppLog.e(TAG, "readText failed: ${file.absolutePath}", e)
            ReadResult.Unreadable
        } catch (e: OutOfMemoryError) {
            // 两次调用之间文件长大、或本机堆更小 —— 本质仍是「太大」。
            AppLog.e(TAG, "readText OOM (file too large): ${file.absolutePath}")
            ReadResult.TooLarge(file.length())
        }
    }

    fun writeText(file: File, content: String): Boolean {
        return try {
            val parent = file.parentFile
            if (parent != null && !parent.exists()) parent.mkdirs()
            FileOutputStream(file).use { it.write(content.toByteArray(Charsets.UTF_8)) }
            true
        } catch (e: IOException) {
            AppLog.e(TAG, "writeText failed: ${file.absolutePath}", e)
            false
        } catch (e: OutOfMemoryError) {
            AppLog.e(TAG, "writeText OOM: ${file.absolutePath}")
            false
        }
    }

    /** 按探测到的编码把字节解成文本。Charset.decode 只接受 ByteBuffer，故走 String 构造。 */
    private fun decodeText(bytes: ByteArray): String = String(bytes, detectCharset(bytes))

    /** 按 BOM / 高位字节粗判编码；默认 UTF-8。 */
    private fun detectCharset(bytes: ByteArray): java.nio.charset.Charset {
        if (bytes.size >= 3 &&
            bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()
        ) return Charsets.UTF_8
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte())
            return java.nio.charset.Charset.forName("UTF-16LE")
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte())
            return java.nio.charset.Charset.forName("UTF-16BE")
        return Charsets.UTF_8
    }

    // ==================== content:// 兜底 ====================

    fun readUri(context: Context, uri: Uri): String? {
        // 6.2.0 批次25：加详细日志 + FileDescriptor 兜底，便于定位 android/data/ 读取失败原因。
        AppLog.d(TAG, "readUri scheme=${uri.scheme} uri=$uri")
        // 方式一：openInputStream（大多数 content:// provider 走这里）。
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val bytes = input.readBytes()
                AppLog.d(TAG, "readUri openInputStream ok bytes=${bytes.size}")
                return decodeText(bytes)
            }
            AppLog.w(TAG, "readUri openInputStream returned null stream")
        } catch (e: Exception) {
            AppLog.e(TAG, "readUri openInputStream failed: $uri", e)
        } catch (e: OutOfMemoryError) {
            // catch(Exception) 拦不住 Error，不补这一条会直接把进程带崩。
            AppLog.e(TAG, "readUri openInputStream OOM (file too large): $uri")
            return null
        }
        // 方式二：openFileDescriptor（个别 provider 只支持 fd，不支持流）。
        try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                java.io.FileInputStream(pfd.fileDescriptor).use { fis ->
                    val bytes = fis.readBytes()
                    AppLog.d(TAG, "readUri openFileDescriptor ok bytes=${bytes.size}")
                    return decodeText(bytes)
                }
            }
            AppLog.w(TAG, "readUri openFileDescriptor returned null")
        } catch (e: Exception) {
            AppLog.e(TAG, "readUri openFileDescriptor failed: $uri", e)
        } catch (e: OutOfMemoryError) {
            AppLog.e(TAG, "readUri openFileDescriptor OOM (file too large): $uri")
            return null
        }
        AppLog.e(TAG, "readUri all methods failed: $uri")
        return null
    }

    fun writeUri(context: Context, uri: Uri, content: String): Boolean {
        return try {
            context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
                out.write(content.toByteArray(Charsets.UTF_8))
                true
            } ?: false
        } catch (e: Exception) {
            AppLog.e(TAG, "writeUri failed: $uri", e)
            false
        }
    }

    /**
     * 6.2.0 批次21：另存为 —— 在用户经 SAF 选择的目录（treeUri）下新建同名文档并写入。
     * 走 DocumentsContract + ContentResolver，**无需存储权限**（SAF 授予了该目录的访问权）。
     * 成功返回新文档 Uri；失败（含同名冲突等）返回 null。
     */
    fun saveAsToTree(context: Context, treeUri: Uri, fileName: String, content: String): Uri? {
        return try {
            val doc = DocumentsContract.createDocument(
                context.contentResolver, treeUri, "text/plain", fileName
            ) ?: return null
            if (writeUri(context, doc, content)) {
                doc
            } else {
                try { DocumentsContract.deleteDocument(context.contentResolver, doc) } catch (_: Exception) {}
                null
            }
        } catch (e: Exception) {
            AppLog.e(TAG, "saveAsToTree failed", e)
            null
        }
    }

    /**
     * 尝试把 content:// 解析成真实绝对路径，以便走直接 IO（用户要的「直接改文件」）。
     * 解析不出时返回 null，调用方回落 ContentResolver。
     */
    fun resolveRealPath(context: Context, uri: Uri): String? {
        when (uri.scheme) {
            "file" -> return uri.path
            "content" -> Unit
            else -> return uri.path
        }

        // DocumentsContract（文件管理器常用）：primary:xxx → /storage/emulated/0/xxx
        try {
            if (DocumentsContract.isDocumentUri(context, uri)) {
                val docId = DocumentsContract.getDocumentId(uri)
                when {
                    docId.startsWith("primary:", ignoreCase = true) -> {
                        val rel = docId.substring("primary:".length)
                        return File(Environment.getExternalStorageDirectory(), rel).absolutePath
                    }
                    docId.matches(Regex("^([0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}):(.*)$")) -> {
                        val (uuid, rel) = docId.split(":", limit = 2)
                        val base = File("/storage")
                            .listFiles()?.firstOrNull { it.name.startsWith(uuid) }
                        if (base != null) return File(base, rel).absolutePath
                    }
                }
            }
        } catch (e: Exception) {
            AppLog.w(TAG, "resolveRealPath via DocumentsContract failed: $uri")
        }

        // DATA 列（老式 provider）
        try {
            context.contentResolver.query(uri, arrayOf(COLUMN_DATA), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(COLUMN_DATA)
                        if (idx >= 0) {
                            val path = cursor.getString(idx)
                            if (!path.isNullOrEmpty() && File(path).exists()) return path
                        }
                    }
                }
        } catch (e: Exception) {
            AppLog.w(TAG, "resolveRealPath via DATA column failed: $uri")
        }

        return null
    }

    // ==================== 目录遍历 ====================

    /** 列出目录下的子项：目录在前，均按名称排序。 */
    fun listChildren(dir: File): List<File> {
        val children = dir.listFiles() ?: return emptyList()
        return children
            .filter { !it.name.startsWith(".") }
            .sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
    }

    /** 打开项目时要隐藏的重噪声目录。 */
    private val NOISE_DIRS = setOf(
        ".git", ".svn", ".hg", ".gradle", ".idea", ".dart_tool", ".cxx",
        "build", "bin", "gen", "out", "node_modules", "__pycache__"
    )

    fun listProjectChildren(dir: File): List<File> {
        return listChildren(dir).filter { !(it.isDirectory && it.name in NOISE_DIRS) }
    }

    fun formatSize(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
        bytes < 1024L * 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024))
        else -> "%.1f GB".format(bytes / (1024.0 * 1024 * 1024))
    }
}
