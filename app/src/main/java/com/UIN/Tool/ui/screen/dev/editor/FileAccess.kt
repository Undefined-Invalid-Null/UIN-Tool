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
import java.io.RandomAccessFile

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

    /**
     * 文本类扩展名白名单 —— 供语言识别等场景参考。
     * 批次53 起**打开判定**不再依赖它（改走 [BINARY_EXTENSIONS] 黑名单否决制，见 [isTextFile]）。
     */
    val TEXT_EXTENSIONS: Set<String> = setOf(
        "txt", "text", "log", "md", "markdown", "rst", "adoc",
        "java", "kt", "kts", "scala", "groovy", "gradle",
        "xml", "html", "htm", "xhtml", "css", "scss", "less", "svg",
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
     * 6.2.0 大文件省耗的**判定下限**：内容超过该字符数即视为「大文件」，
     * 在「大文件省耗」开关开启时关掉语法解析 / 粘性滚动 / 块缩进线，并把十六进制色
     * 提示的扫描压到 [com.UIN.Tool.ui.screen.dev.editor.HexColorHints.MAX_LINES] 行以内。
     *
     * 6.2.0 调整：50 万 → **200 万字符** —— 用户反馈 2MB 级文件「很小」，不该一打开就降级。
     * 整套省耗措施都随开关走，用户关掉即全部恢复。
     *
     * 注：**打开文件本身没有大小上限**（早先的 8MB 硬上限已取消）。真读不动时
     * `readText` 由 OOM 兜底返回 [ReadResult.TooLarge]，文案仍是「文件过大」。
     */
    const val LARGE_FILE_OPTIMIZE_CHARS: Int = 2_000_000

    /**
     * 读取结果 —— 把「文件太大，这台机器装不下」和「真的读不动了」分开。
     * 光靠 `String?` 无法区分，调用方只能一律报「读取失败」。
     */
    sealed interface ReadResult {
        /** 6.2.0：[charset] = 解码这份文本时**实际采用**的编码名（供状态栏显示、保存回写）。 */
        class Ok(val text: String, val charset: String) : ReadResult
        class TooLarge(val bytes: Long) : ReadResult
        object Unreadable : ReadResult
    }

    /**
     * 6.2.0 批次53：**已知二进制**扩展名黑名单 —— 明确不该按文本打开的类型
     * （图片/音视频/压缩与安装包/Office 与 PDF/字体/数据库/库文件/证书容器）。
     *
     * 与 [isTextFile] 的「黑名单否决制」配套：白名单外的**未知后缀、无后缀文件、
     * 点开头隐藏文件（.bashrc 等）一律按 txt 打开** —— 用户拍板「未知文件后缀
     * 不论什么时候都要支持」（文件树 / 恢复上次打开 / 向导收集 / 外部 ACTION_VIEW 同源）。
     * 故名单只收「无歧义的二进制」，不收 .dat 这类含义模糊的后缀（宁可按 txt 打开）。
     */
    private val BINARY_EXTENSIONS = setOf(
        // 图片
        "png", "jpg", "jpeg", "gif", "bmp", "webp", "ico", "tif", "tiff", "heic", "avif",
        // 音频 / 视频
        "mp3", "mp4", "m4a", "m4v", "aac", "ogg", "oga", "wav", "flac", "opus",
        "mkv", "avi", "mov", "wmv", "flv", "webm", "3gp", "amr", "mid", "midi",
        // 压缩包 / 安装包 / 库
        "zip", "tar", "gz", "tgz", "bz2", "xz", "lz4", "zst", "7z", "rar", "svgz",
        "apk", "aab", "xapk", "jar", "war", "dex", "so", "oat", "vdex",
        "exe", "dll", "dylib", "o", "a", "lib", "bin",
        // 文档（非纯文本存储）
        "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "odt", "ods", "odp",
        "epub", "mobi", "pages", "numbers", "key",
        // 字体 / 数据库 / 磁盘镜像 / 字节码 / 证书容器
        "ttf", "otf", "woff", "woff2", "eot",
        "db", "sqlite", "sqlite3", "iso", "img", "dmg", "wim",
        "pyc", "pyo", "class",
        "keystore", "jks", "bks", "p12", "pfx"
    )

    /**
     * 能否按文本打开 —— 批次53 起改为**黑名单否决制**：
     *  [BINARY_EXTENSIONS] 命中 → 拒开（真二进制，按 txt 打开只有乱码）；
     *  其余（含 [TEXT_EXTENSIONS] 白名单、未知后缀、无后缀文件）→ 一律放行按 txt 打开。
     *
     * 旧实现是白名单制（名单外全拒），用户点名「未知文件后缀以 txt 方式打开，
     * 不论什么时候都要支持」—— 四条打开入口（文件树、恢复上次、向导收集、外部 VIEW）
     * 全部经此函数，改一处即全部生效。
     */
    fun isTextFile(fileName: String): Boolean {
        val lower = fileName.lowercase()
        val ext = lower.substringAfterLast('.', "")
        return ext !in BINARY_EXTENSIONS
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

    /**
     * 6.2.0 批次46：窗口模式的每页行数。分页控件（PAGE_LINES）与
     * [WindowedDocument] 的页粒度**必须**同源，否则「n/N」页码与实际窗口对不上。
     */
    const val WINDOW_PAGE_LINES: Int = 2000

    /**
     * 批次56：分页的**字节上限**（与行数上限是「先到者断页」的或关系）——
     * 只按行数分页时，行极密（一屏几万行）或单行极长的病态文件一页会装进上百 MB。
     * 1MB 字节 ≈ 100 万 ASCII 字符（解出后 ≤2MB 字符数组），512MB 堆机安全。
     * 单行超过上限仍独占一页（页界必须落在行首，不能从行中间断开）。
     */
    const val WINDOW_PAGE_BYTES: Int = 1024 * 1024

    /** 批次46：窗口模式的打开结果 —— 索引 + 首页文本 + 实际采用的编码名。 */
    data class WindowedRead(
        val doc: WindowedDocument,
        val text: String,
        val charset: String
    )

    /**
     * 批次50：渐进式窗口打开的**头部快读**结果 —— 首页文本可能被截断（超长单行档），
     * 索引完成后由调用方用 [WindowedDocument.readPage] 的精确页文本替换。
     */
    data class HeadPage(
        val text: String,
        val charset: String,
        /** true = 文件头在 maxLines/maxBytes 内没读全首页（后台索引完成后会校正文本）。 */
        val truncated: Boolean
    )

    /**
     * 批次50：只读文件头（≤maxBytes）解出前 maxLines 行 —— 供窗口模式**先显示首页**，
     * 页索引随后由 [WindowedDocument.build] 后台补齐。行界口径与 build 一致
     * （LF/CR/CRLF 各算一个）。截断处总是退回最后一个完整行界之后（多字节字符不切断）。
     */
    fun readHeadPage(
        file: File,
        charsetName: String = CHARSET_AUTO,
        maxLines: Int = WINDOW_PAGE_LINES,
        maxBytes: Int = 4 * 1024 * 1024
    ): HeadPage? {
        if (!file.isFile || !file.canRead()) return null
        return try {
            RandomAccessFile(file, "r").use { raf ->
                val fileLen = raf.length()
                val toRead = minOf(fileLen, maxBytes.toLong()).toInt()
                if (toRead <= 0) return HeadPage("", CHARSET_AUTO, false)
                val buf = ByteArray(toRead)
                raf.readFully(buf)
                val charset = if (charsetName.equals(CHARSET_AUTO, ignoreCase = true)) {
                    detectCharset(if (toRead > 65536) buf.copyOfRange(0, 65536) else buf)
                } else charsetFor(charsetName)
                var text = String(buf, charset)
                val truncatedFile = fileLen > toRead
                // 切到第 maxLines 个行界之后（与窗口页宽对齐；CRLF 按一个行界）。
                var lines = 0
                var i = 0
                var cut = -1
                while (i < text.length) {
                    val c = text[i]
                    if (c == '\r') {
                        lines++
                        if (i + 1 < text.length && text[i + 1] == '\n') i++
                    } else if (c == '\n') lines++
                    i++
                    // 批次56：与 [WindowedDocument.build] 同款「先到者断页」—— 行数到 2000
                    // 或过 1MB 都在行界处截（首页文本以索引完成后的精确页校正为准）。
                    // 口径说明：这里的 i 是**解码后字符下标**，对多字节编码（如 UTF-8 中文
                    // 1 字 = 3 字节）晚于真实字节上限触发，最多多读 ~3×1MB 进堆 —— 预读页
                    // 本就 ≤4MB 总量护栏，仅影响预读页大小，不影响正确性。
                    if (lines >= maxLines || (i >= WINDOW_PAGE_BYTES && (c == '\r' || c == '\n'))) {
                        cut = i
                        break
                    }
                }
                val truncated = truncatedFile || cut < 0
                if (truncatedFile) {
                    // 头缓冲没装下首页：退到最后一个完整行界（多字节字符不切断）；
                    // 一个行界都没有（超长单行档）则原样给出，索引完成后校正。
                    val lastBreak = maxOf(
                        text.lastIndexOf('\n'),
                        text.lastIndexOf('\r')
                    )
                    if (cut < 0 && lastBreak >= 0) cut = lastBreak + 1
                }
                if (cut < 0) cut = text.length
                if (cut != text.length) text = text.substring(0, cut)
                HeadPage(text, charset.name(), truncated)
            }
        } catch (e: Exception) {
            AppLog.e(TAG, "readHeadPage failed: ${file.absolutePath}", e)
            null
        } catch (e: OutOfMemoryError) {
            AppLog.e(TAG, "readHeadPage OOM: ${file.absolutePath}")
            null
        }
    }

    /** 批次50：编码名 → Charset（索引后台重建用；非法名回落自动档的 UTF-8）。 */
    fun charsetForName(name: String): java.nio.charset.Charset = charsetFor(name)

    /**
     * 6.2.0 批次46：**分页窗口模式**打开（EmEditor 式磁盘溢写的 Android 缩配）。
     *
     * 内存预检失败（或整读到一半 OOM）的超大文件走这里：整文件**不进堆**，
     * 只流式扫一遍建页偏移索引（[WindowedDocument.build]），随后读出首页文本。
     * 编码判定与整读完全同源（BOM → 严格 UTF-8 → GBK 疑似 → ISO 兜底，或按用户所选强解）。
     *
     * 注意：本函数是**同步流式 IO**（GB 级需数秒），调用方务必放到后台线程。
     *
     * @return 窗口打开结果；编码不适合字节级扫描 / IO 失败返回 null（调用方回落「文件过大」）。
     */
    fun openWindowed(file: File, charsetName: String = CHARSET_AUTO): WindowedRead? {
        if (!file.isFile || !file.canRead()) return null
        return try {
            // 清理上次保存中断留下的临时文件（本函数不依赖它，仅防垃圾堆积）。
            File(file.parentFile, ".${file.name}.uin_tmp").delete()
            val head = ByteArray(minOf(file.length(), 65536L).toInt())
            RandomAccessFile(file, "r").use { raf -> if (head.isNotEmpty()) raf.readFully(head) }
            val charset = if (charsetName.equals(CHARSET_AUTO, ignoreCase = true)) {
                detectCharset(head)
            } else charsetFor(charsetName)
            val doc = WindowedDocument.build(file, charset, WINDOW_PAGE_LINES) ?: return null
            AppLog.d(
                TAG,
                "openWindowed: ${file.absolutePath} pages=${doc.totalPages} " +
                    "lines=${doc.totalLines} charset=${charset.name()}"
            )
            WindowedRead(doc, doc.readPage(0), charset.name())
        } catch (e: Exception) {
            AppLog.e(TAG, "openWindowed failed: ${file.absolutePath}", e)
            null
        } catch (e: OutOfMemoryError) {
            AppLog.e(TAG, "openWindowed OOM: ${file.absolutePath}")
            null
        }
    }

    /**
     * 6.2.0：读取文本。
     *
     * @param charsetName 编码选择：[CHARSET_AUTO]（默认）按 [detectCharset] 自动判定，
     *   否则按给定编码名强解（[CHARSET_AUTO]/非法名同样回落到自动判定）。
     * @return [ReadResult.Ok] 携带解码文本**与实际采用的编码名**（供状态栏显示/保存复用）。
     */
    fun readText(file: File, charsetName: String = CHARSET_AUTO): ReadResult {
        if (!file.isFile || !file.canRead()) {
            AppLog.w(TAG, "readText: unreadable ${file.absolutePath}")
            return ReadResult.Unreadable
        }
        // 6.2.0：不再按固定大小在读取前拒开；真读不动由下方 OOM 兜底报「文件过大」。
        //
        // 6.2.0 修「打开大文件后界面卡死」：靠 OOM 兜底的代价是 ART 反复
        // `Forcing collection of SoftReferences` / `blocking GC Alloc`（设备日志实测停摆 12s+），
        // 主线程被 GC 卡住，用户看到的就是整个界面无响应。故先做内存预检，装不下直接走
        // TooLarge 用明确文案收场，而不是「先卡死一阵子再报错」。
        if (!fitsHeapBeforeRead(file.length(), "readText")) {
            return ReadResult.TooLarge(file.length())
        }
        return try {
            val bytes = file.readBytes()
            ReadResult.Ok(decodeText(bytes, charsetName), detectName(bytes, charsetName))
        } catch (e: IOException) {
            AppLog.e(TAG, "readText failed: ${file.absolutePath}", e)
            ReadResult.Unreadable
        } catch (e: OutOfMemoryError) {
            // 内存装不下 —— 本质仍是「文件太大」，与权限/IO 失败区分开。
            AppLog.e(TAG, "readText OOM (file too large): ${file.absolutePath}")
            ReadResult.TooLarge(file.length())
        }
    }

    /**
     * @param charsetName 写出编码（[CHARSET_AUTO] 时按 UTF-8 写，与新建文件一致）。
     */
    fun writeText(file: File, content: String, charsetName: String = CHARSET_AUTO): Boolean {
        return try {
            val parent = file.parentFile
            if (parent != null && !parent.exists()) parent.mkdirs()
            FileOutputStream(file).use { it.write(content.toByteArray(charsetFor(charsetName))) }
            true
        } catch (e: IOException) {
            AppLog.e(TAG, "writeText failed: ${file.absolutePath}", e)
            false
        } catch (e: OutOfMemoryError) {
            AppLog.e(TAG, "writeText OOM: ${file.absolutePath}")
            false
        }
    }

    // ==================== 编码：检测 / 选择 / 解码 / 编码 ====================

    /** 自动检测（读时按内容判定，写时按 UTF-8）。 */
    const val CHARSET_AUTO = "auto"

    /**
     * 6.2.0：候选编码全集（按语言/场景分组，顺序即选择器展示顺序）。
     * 首项是 [CHARSET_AUTO]；其余本身就是语言无关的编码名，直接显示即可。
     */
    private val ALL_CHARSET_OPTIONS: List<String> = listOf(
        CHARSET_AUTO,
        // 中文
        "UTF-8", "GB18030", "GBK", "Big5", "Big5-HKSCS",
        // 日韩
        "Shift_JIS", "EUC-JP", "ISO-2022-JP", "EUC-KR",
        // Unicode 变体
        "UTF-16LE", "UTF-16BE", "UTF-16", "UTF-32LE", "UTF-32BE",
        // 单字节（西欧/俄文/泰文等）
        "ISO-8859-1", "ISO-8859-2", "ISO-8859-15", "windows-1250",
        "windows-1251", "windows-1252", "KOI8-R", "TIS-620", "US-ASCII"
    )

    /**
     * 6.2.0：可选编码名（只放数据，显示名由 UI 按资源本地化 —— 这里是代码层，不放中文）。
     *
     * 批次27 应用户要求扩充编码列表，并用 [java.nio.charset.Charset.isSupported] 按
     * **本机能力**过滤 —— 不同 Android 版本/ROM 的编码支持面不同（如 ISO-2022-JP 在多数
     * 设备上并不存在），不过滤就会出现「选完其实解不动、静默回退 UTF-8」的坑。
     */
    val CHARSET_OPTIONS: List<String> = ALL_CHARSET_OPTIONS.filter {
        it == CHARSET_AUTO ||
            runCatching { java.nio.charset.Charset.isSupported(it) }.getOrDefault(false)
    }

    /**
     * 解码用的 Charset。[CHARSET_AUTO] 或非法/平台不支持的名一律回落 UTF-8
     * （真正的自动判定在 [decodeText] 里做，这里只兜住 `Charset.forName` 抛异常）。
     */
    private fun charsetFor(name: String?): java.nio.charset.Charset {
        if (name.isNullOrBlank() || name.equals(CHARSET_AUTO, ignoreCase = true)) return Charsets.UTF_8
        return try {
            java.nio.charset.Charset.forName(name)
        } catch (e: Exception) {
            AppLog.w(TAG, "unknown charset: $name")
            Charsets.UTF_8
        }
    }

    /**
     * 6.2.0：按用户选择解码。[CHARSET_AUTO] 走 [detectCharset] 自动判定，否则强解。
     * 返回的文本**不**带回编码信息，需要编码名请用 [detectName]。
     */
    fun decodeText(bytes: ByteArray, charsetName: String = CHARSET_AUTO): String =
        if (charsetName.equals(CHARSET_AUTO, ignoreCase = true)) {
            String(bytes, detectCharset(bytes))
        } else {
            String(bytes, charsetFor(charsetName))
        }

    /**
     * 解码时**实际采用**的编码名 —— 自动档时等于判定结果，手动档时等于所选项。
     * 状态栏「编码」按钮就显示它，另存/覆盖也用它写回。
     */
    fun detectName(bytes: ByteArray, charsetName: String = CHARSET_AUTO): String =
        if (charsetName.equals(CHARSET_AUTO, ignoreCase = true)) {
            detectCharset(bytes).name()
        } else {
            charsetFor(charsetName).name()
        }

    /** 暴露给 UI：只判定、不解码（供编码选择器预览「检测结果」）。 */
    fun detectCharsetName(bytes: ByteArray): String = detectCharset(bytes).name()

    /**
     * 批次43：属性弹窗流式统计用 —— 只凭文件**开头样本**判定编码（BOM → 严格 UTF-8 →
     * GBK 疑似 → ISO 兜底），避免为数行数/字符数把整文件读进内存。样本约 64KB 即可
     * 命中绝大多数真实文件；判定失败也只是计数偏差，不影响文件本身。
     */
    fun detectCharsetFromHead(sample: ByteArray): java.nio.charset.Charset = detectCharset(sample)

    /**
     * 自动判定：BOM → 严格 UTF-8 → 疑似 GBK/GB18030 → ISO-8859-1。
     *
     * 6.2.0 新增。原先**无条件**返回 UTF-8，GBK 老文件读出来是 `锟斤拷` 之类乱码，
     * 且没有任何地方能手动改正。现按以下优先级判定：
     *  1. **BOM**（UTF-8 / UTF-16LE / UTF-16BE）——最可靠；
     *  2. **严格 UTF-8 校验**：能整段按 UTF-8 解出即 UTF-8（纯 ASCII 也走这里）；
     *  3. **GBK/GB18030 疑似**：高位字节都能按 GBK 双字节对吃掉，且不是稀疏的
     *     欧洲语言重音字符（见 [looksGbk]）——满足则按 GB18030（GBK 超集，Android 内置）；
     *  4. 兜底 ISO-8859-1：字节与字符 1:1，**永不抛错**，也便于用户改选后原样写回。
     *
     * 这只是启发式；拿不准的文件用底部状态栏的「编码」按钮手动指定即可。
     */
    private fun detectCharset(bytes: ByteArray): java.nio.charset.Charset {
        if (bytes.isEmpty()) return Charsets.UTF_8
        // 1) BOM
        if (bytes.size >= 3 &&
            bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()
        ) return Charsets.UTF_8
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte())
            return java.nio.charset.Charset.forName("UTF-16LE")
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte())
            return java.nio.charset.Charset.forName("UTF-16BE")
        // 2) 严格 UTF-8
        if (isValidUtf8(bytes)) return Charsets.UTF_8
        // 3) GBK / GB18030
        if (looksGbk(bytes)) return java.nio.charset.Charset.forName("GB18030")
        // 4) 兜底
        return java.nio.charset.Charset.forName("ISO-8859-1")
    }

    /** 全量严格 UTF-8 校验（RFC 3629：拒收过长序列、代理区、>U+10FFFF）。 */
    private fun isValidUtf8(bytes: ByteArray): Boolean {
        var i = 0
        while (i < bytes.size) {
            val b = bytes[i].toInt() and 0xFF
            when {
                b < 0x80 -> i++
                b in 0xC2..0xDF -> { if (i + 1 >= bytes.size || !isCont(bytes[i + 1])) return false; i += 2 }
                b == 0xE0 -> {
                    if (i + 2 >= bytes.size || (bytes[i + 1].toInt() and 0xFF) !in 0xA0..0xBF ||
                        !isCont(bytes[i + 2])
                    ) return false
                    i += 3
                }
                b in 0xE1..0xEF -> {
                    if (i + 2 >= bytes.size || !isCont(bytes[i + 1]) || !isCont(bytes[i + 2])) return false
                    i += 3
                }
                b == 0xF0 -> {
                    if (i + 3 >= bytes.size || (bytes[i + 1].toInt() and 0xFF) !in 0x90..0xBF ||
                        !isCont(bytes[i + 2]) || !isCont(bytes[i + 3])
                    ) return false
                    i += 4
                }
                b in 0xF1..0xF4 -> {
                    if (i + 3 >= bytes.size || !isCont(bytes[i + 1]) || !isCont(bytes[i + 2]) ||
                        !isCont(bytes[i + 3])
                    ) return false
                    i += 4
                }
                else -> return false
            }
        }
        return true
    }

    private fun isCont(b: Byte): Boolean = (b.toInt() and 0xC0) == 0x80

    /**
     * 疑似 GBK/GB18030 判据（只扫前 64KB 以免大文件白扫）：
     *  - 高位字节（>=0x80）**成对**且**每对**都落在 GBK 单区范围内（paired == high）；
     *  - 字节里至少有 2 个汉字的量（high >= 4）；
     *  - 不是「高位字节又少又稀」的欧洲语言文本：高位 >= 16 却只占 8% 以下时
     *    视为 ISO-8859-1（法语/德语的 é/è/à 每个占 1 字节，本判据会误判成 GBK）。
     *
     * 两条路都可能出错，所以 UI 里保留手动选择 —— 这条只负责把**绝大多数中文老文件**
     * 自动解对。
     */
    private fun looksGbk(bytes: ByteArray): Boolean {
        val scan = minOf(bytes.size, 65536)
        var i = 0
        var high = 0
        var paired = 0
        while (i < scan) {
            val b = bytes[i].toInt() and 0xFF
            if (b < 0x80) { i++; continue }
            high++
            if (b in 0x81..0xFE && i + 1 < scan) {
                val t = bytes[i + 1].toInt() and 0xFF
                if (t in 0x40..0x7E || t in 0x80..0xFE) {
                    paired++
                    i += 2
                    continue
                }
            }
            i++
        }
        if (high < 4 || paired != high) return false
        return !(high >= 16 && high * 100 < scan * 8)
    }

    /**
     * 6.2.0：**读取/解码前的内存预检**（不是固定大小硬上限，而是随剩余堆内存浮动的护栏）。
     *
     * 原实现只靠 `catch (OutOfMemoryError)` 兜底 —— 但真正 OOM 时 ART 已经进入
     * 「反复 blocking GC / 强制回收 SoftReferences」的状态（设备日志实测主线程停摆 12 秒以上），
     * 界面看起来就是**卡死**。这里按「峰值所需 + 余量」放行，装不下就别开始读，
     * 直接给「文件过大」的明确文案。
     *
     * 6.2.0 修「预检通过却仍读到一半 OOM」：峰值是 byte[]（1 倍）+ UTF-16 解码
     * char[]（2 倍）≈ 3 倍，但设备实测 31,390,381 字节文件申请 62,780,778 字节的
     * char[] 时距堆上限只差 6MB —— 理论 3 倍**零余量**，其它并发分配一挤就 OOM。
     * 故预检统一按 [needBeforeRead]（3.25 倍）要求。
     */
    private fun availableHeap(): Long {
        val rt = Runtime.getRuntime()
        return rt.maxMemory() - (rt.totalMemory() - rt.freeMemory())
    }

    /** 读取**前**（byte[] 尚未分配）的峰值需求：byte[] 1 倍 + char[] 2 倍 + 25% 余量。 */
    private fun needBeforeRead(bytes: Long): Long = bytes * 3 + bytes / 4

    /**
     * 读取**后**（byte[] 已在手上、已计入 available）的峰值需求：
     * 只差解码出的 char[]（UTF-16 最坏 2 倍）+ 25% 余量。
     */
    private fun needAfterBytes(bytes: Long): Long = bytes * 2 + bytes / 4

    private fun fitsHeap(bytes: Long, where: String): Boolean {
        if (bytes <= 0) return true
        val need = needAfterBytes(bytes)
        val available = availableHeap()
        if (need <= available) return true
        AppLog.w(TAG, "$where pre-guard: bytes=$bytes need=$need available=$available")
        return false
    }

    /**
     * 6.2.0 修「同一个文件一会儿能打开、一会儿报文件过大」（**只用于读之前**）：
     *
     * `freeMemory` 只是**已提交堆里空闲的部分** —— 刚关掉一个大文档后那段内存还没回收，
     * available 看起来很小，于是误判装不下。设备日志实测：同一 29.9MB 文件 25 秒前刚打开成功，
     * 关掉再开就被挡住（available=88MB，阈值=29MB）；而用「打开方式」进来时是冷启动、
     * 堆是空的，同一个文件又打得开 —— 表现成「树里打不开、外部打开却可以」。
     *
     * 打开文件本就是一次同步 IO，这里先强制回收再量一次，仍装不下才判定失败。
     * [fitsHeap]（读完字节后、解码前那道）不做这件事：那时字节还在手上，GC 也回收不掉。
     *
     * 6.2.0 批次41：**修「强制回收刚跑就测量、把本可打开的文件误判成文件过大」**。
     * ART 的 `System.gc()` 触发的是**并发** GC，常在收尾完成前就返回。设备日志实测
     * 2026-10-17 21:58:06：gc 后立刻测量比 GC 完成行早 0.4 秒、低估约 10MB；
     * 21:58:36 更低估约 48MB —— 31MB 文件连判两次「文件过大」，而 40 秒后堆真正回收完、
     * 同一个文件又打得开。故 gc 之后给收尾一点时间再量（每 80ms 复测一次、最多 320ms，
     * 仅在临界路径上付出），仍装不下才判定失败。
     */
    private fun fitsHeapBeforeRead(bytes: Long, where: String): Boolean {
        if (bytes <= 0) return true
        val need = needBeforeRead(bytes)
        if (need <= availableHeap()) return true
        System.gc()
        var after = availableHeap()
        var waited = 0
        while (need > after && waited < 320) {
            Thread.sleep(80)
            waited += 80
            after = availableHeap()
        }
        if (need <= after) {
            if (waited > 0) {
                AppLog.d(
                    TAG,
                    "$where pre-guard recovered after gc+wait ${waited}ms: need=$need available=$after"
                )
            }
            return true
        }
        AppLog.w(
            TAG,
            "$where pre-guard(after gc+wait ${waited}ms): bytes=$bytes need=$need available=$after"
        )
        return false
    }

    // ==================== content:// 兜底 ====================

    /** 6.2.0：content:// 读取结果 —— 文本 + 实际采用的编码名（状态栏显示/写回复用）。 */
    data class UriRead(val text: String, val charset: String)

    /** @param charsetName 见 [readText]：[CHARSET_AUTO] 自动判定，否则强解。 */
    fun readUri(context: Context, uri: Uri, charsetName: String = CHARSET_AUTO): UriRead? {
        // 6.2.0 批次25：加详细日志 + FileDescriptor 兜底，便于定位 android/data/ 读取失败原因。
        AppLog.d(TAG, "readUri scheme=${uri.scheme} uri=$uri")
        // 方式一：openInputStream（大多数 content:// provider 走这里）。
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val bytes = input.readBytes()
                AppLog.d(TAG, "readUri openInputStream ok bytes=${bytes.size}")
                // 6.2.0：content:// 拿不到预知大小，只能读完字节再预检，避免解码阶段二次 OOM 卡死。
                if (!fitsHeap(bytes.size.toLong(), "readUri stream")) return null
                return UriRead(decodeText(bytes, charsetName), detectName(bytes, charsetName))
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
                    if (!fitsHeap(bytes.size.toLong(), "readUri fd")) return null
                    return UriRead(decodeText(bytes, charsetName), detectName(bytes, charsetName))
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

    /** @param charsetName 见 [writeText]：[CHARSET_AUTO] 按 UTF-8 写。 */
    fun writeUri(
        context: Context,
        uri: Uri,
        content: String,
        charsetName: String = CHARSET_AUTO
    ): Boolean {
        return try {
            context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
                out.write(content.toByteArray(charsetFor(charsetName)))
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
            // 6.2.0 修「选完文件夹毫无反应 / 卡死」：OpenDocumentTree 返回的是 **tree URI**
            // （形如 .../tree/primary%3AMine），`isDocumentUri()` 对它恒为 false ——
            // 于是原代码永远走不到 docId 解析，只能落到注定失败的 `_data` 列查询，
            // 返回 null 后调用方直接 return，表现为「选完文件夹什么都没发生」。
            // 这里对 tree URI 直接取路径段 `/tree/<treeDocumentId>`，复用同一套 primary:/UUID: 映射。
            //
            // 为什么不调 getTreeDocumentId() / 不比 DocumentsContract.AUTHORITY：
            //  - `AUTHORITY` 在 android.provider.DocumentsContract 里**不是公开 API**，
            //    编译期报 Unresolved reference 'AUTHORITY'；
            //  - getTreeDocumentId() 在不同 API 版本上对「非 tree URI」的校验行为不一致，
            //    而这里只想认 tree URI，自己读路径段最稳、也无异常分支。
            val segs = uri.pathSegments
            val docId = when {
                DocumentsContract.isDocumentUri(context, uri) -> DocumentsContract.getDocumentId(uri)
                segs.size >= 2 && segs[0] == "tree" -> segs[1]
                else -> null
            }
            if (docId != null) {
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
