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
import com.UIN.Tool.ui.screen.files.FileTypeRegistry
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
     * 能否按文本打开 —— **黑名单否决制**，名单唯一来源是 `file_types.json` 顶层
     * `binaryExtensions`（见 [FileTypeRegistry.isBinaryExtension]）。
     *
     * 命中 → 拒开（真二进制，按 txt 打开只有乱码）；其余（未知后缀、无后缀文件、
     * 点开头隐藏文件）→ 一律放行按 txt 打开 —— 用户拍板「未知文件后缀不论什么时候
     * 都要支持」。四条打开入口（文件树、恢复上次、向导收集、外部 VIEW）全经此函数。
     *
     * q.txt 第 4 条：这里**不再维护任何后缀字面量** —— 后缀、图标、颜色、文本/二进制
     * 判定全部集中在 `file_types.json`，新增格式只改那一个文件。
     */
    fun isTextFile(fileName: String): Boolean {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return !FileTypeRegistry.isBinaryExtension(ext)
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
    fun writeText(
        file: File,
        content: String,
        charsetName: String = CHARSET_AUTO,
        /** f18：覆盖原文件前把它留成 `<原名>.bak`。 */
        backup: Boolean = false
    ): Boolean {
        return try {
            // 先编码再动文件：编码阶段 OOM / 抛错时原文件必须还在原地
            val bytes = content.toByteArray(charsetFor(charsetName))
            writeBytes(file, bytes, backup)
        } catch (e: OutOfMemoryError) {
            AppLog.e(TAG, "writeText OOM: ${file.absolutePath}")
            false
        } catch (e: Exception) {
            AppLog.e(TAG, "writeText encode failed: ${file.absolutePath}", e)
            false
        }
    }

    /**
     * k.txt 第 10 条：以**原始字节**原子写回（AXML 重新编码后的二进制）。
     *
     * 与 [writeText] 共用同一条「同目录临时文件 → 原子改名」路径：写满/写到一半
     * 失败时目标文件从未被截断，原文件或 `.bak` 完好。
     */
    fun writeBytes(
        file: File,
        bytes: ByteArray,
        /** f18：覆盖原文件前把它留成 `<原名>.bak`。 */
        backup: Boolean = false
    ): Boolean {
        return try {
            val parent = file.parentFile
            if (parent != null && !parent.exists()) parent.mkdirs()
            // f18：走改名而不是拷贝 —— 保存路径上多拷一份大文件等于把保存翻倍，
            // 而改名不花时间。写失败要把原文件放回去，绝不留下「文件没了只剩 .bak」。
            val bak = if (backup && file.isFile) {
                val b = File(file.parentFile, "${file.name}.bak")
                runCatching { b.delete() }
                if (runCatching { file.renameTo(b) }.getOrDefault(false)) b else null
            } else null
            // 存储写满 / 写到一半失败时，直接 FileOutputStream(file) 已经把**原文件
            // 截断**了 —— 下面 catch 里那句 `!file.exists()` 永远不成立，.bak 也不会
            // 换回来，用户只剩一个半截文件。改成「先写同目录临时文件，再原子改名」：
            // 失败时目标路径根本没人碰过，原文件（或 .bak）完好。
            val dir = file.absoluteFile.parentFile
            val tmp = dir?.let { File(it, ".${file.name}.uinsave") }
            try {
                if (tmp == null) throw IOException("no parent dir: ${file.path}")
                runCatching { tmp.delete() }
                FileOutputStream(tmp).use { out ->
                    out.write(bytes)
                    out.fd.sync()
                }
                if (!tmp.renameTo(file)) throw IOException("rename failed: ${file.path}")
            } catch (t: Throwable) {
                if (tmp != null) runCatching { tmp.delete() }
                // 只有「旧文件已改名成 .bak」时才需要换回来；没改名时目标从未被触碰。
                if (bak != null && bak.exists()) runCatching { bak.renameTo(file) }
                throw t
            }
            true
        } catch (e: IOException) {
            AppLog.e(TAG, "writeBytes failed: ${file.absolutePath}", e)
            false
        } catch (e: OutOfMemoryError) {
            AppLog.e(TAG, "writeBytes OOM: ${file.absolutePath}")
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
     *  1. **BOM**（UTF-8 / UTF-16LE / UTF-16BE）——最可靠；1.5) 无 BOM 的 UTF-16（看 NUL 分布）；
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
        // 1.5) 无 BOM 的 UTF-16（见 detectUtf16NoBom）：必须在「严格 UTF-8」之前 ——
        //      NUL 是合法 UTF-8 字节，先走 UTF-8 就永远轮不到它，整屏断字。
        detectUtf16NoBom(bytes)?.let { return it }
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
     *  - 高位字节（>=0x80）**成对**且几乎每对都落在 GBK 单区范围内（允许 1 个落单，
     *    兼容被截断的日志尾巴）；GB18030 四字节序列也认；
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
        var high = 0        // 高位**序列**数（2 字节汉字记 1，4 字节序列也记 1）
        var highBytes = 0   // 高位**字节**数 —— 密度判断必须用它
        var paired = 0
        while (i < scan) {
            val b = bytes[i].toInt() and 0xFF
            if (b < 0x80) { i++; continue }
            high++
            highBytes++
            // f15：配对要看**整份字节**而不是看 64KB 扫描窗口 —— 原先写 `i + 1 < scan`，
            // 汉字刚好跨在 64KB 分界上时后半个字节被当成「落单的高位字节」，
            // 于是 much 常见的 GBK 大文件被误判成 ISO-8859-1（满屏乱码）。
            // 分界只在文件真的到此结束时才算数。
            if (b in 0x81..0xFE && i + 1 < bytes.size) {
                val t = bytes[i + 1].toInt() and 0xFF
                if (t in 0x40..0x7E || t in 0x80..0xFE) {
                    paired++
                    highBytes++   // 尾字节也是高位字节
                    i += 2
                    continue
                }
                // GB18030 四字节序列（第二字节是数字）：罕见汉字/emoji 走这条，
                // 原先一律算「落单」→ 含这类字符的文件整份判错编码。整体仍只记一次配对。
                if (t in 0x30..0x39 && i + 3 < bytes.size &&
                    (bytes[i + 2].toInt() and 0xFF) in 0x81..0xFE &&
                    (bytes[i + 3].toInt() and 0xFF) in 0x30..0x39
                ) {
                    paired++
                    highBytes++   // 第三字节也是高位（第二、四字节是数字）
                    i += 4
                    continue
                }
            }
            i++
        }
        if (high < 4 || paired < high - 1) return false
        // g14：密度用**高位字节数**。原先用 `high`（序列数）—— 一个汉字 2 字节却只记 1，
        // 比例凭空砍半；中文 CSV 这类「汉字只占 5~6% 字符、但字节占比其实超过 8%」的
        // 文件会因此被判成欧洲语言，落进 ISO-8859-1 兜底，整屏乱码。
        return !(highBytes >= 16 && highBytes * 100 < scan * 8)
    }

    /**
     * 无 BOM 的 UTF-16 判定。
     *
     * f15：UTF-16 的文件头不一定带 BOM（Windows 上不少工具导出时不写），
     * 这类文件原先会被 `isValidUtf8` 当成 UTF-8（NUL 是合法字节），
     * 解码出来整屏 `\u0000` 与断字 —— 看着就是「编码检测坏了」。
     * 判据很稳：ASCII 为主的 UTF-16 文本，**固定一侧的字节几乎全是 0**，
     * 而 UTF-8/GBK 文本极少出现 NUL。
     */
    private fun detectUtf16NoBom(bytes: ByteArray): java.nio.charset.Charset? {
        val n = minOf(bytes.size, 4096) and 0xFFFE // 取偶数
        if (n < 32) return null
        var zeroEven = 0
        var zeroOdd = 0
        var i = 0
        while (i < n) {
            if (bytes[i].toInt() == 0) zeroEven++
            if (bytes[i + 1].toInt() == 0) zeroOdd++
            i += 2
        }
        val pairs = n / 2
        // 七成以上的一侧是 NUL，另一侧基本不是 —— 就是 UTF-16 的字节序特征
        if (zeroEven * 10 >= pairs * 7 && zeroOdd * 10 < pairs * 3) return Charsets.UTF_16BE
        if (zeroOdd * 10 >= pairs * 7 && zeroEven * 10 < pairs * 3) return Charsets.UTF_16LE
        return null
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
     * k.txt 第 10 条：AXML 的 `content://` 写回 —— 编辑器保存的是文本，落盘前先
     * 重新编码成二进制，因此不能走 [writeUri] 的字符串路径。
     */
    fun writeUriBytes(context: Context, uri: Uri, bytes: ByteArray): Boolean {
        return try {
            context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
                out.write(bytes)
                true
            } ?: false
        } catch (e: Exception) {
            AppLog.e(TAG, "writeUriBytes failed: $uri", e)
            false
        }
    }

    /** 读取 content:// 的全部字节（AXML 体积通常很小；仍走堆预检）。 */
    fun readUriBytes(context: Context, uri: Uri): ByteArray? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val bytes = input.readBytes()
                if (!fitsHeap(bytes.size.toLong(), "readUriBytes")) null else bytes
            }
        } catch (e: Exception) {
            AppLog.e(TAG, "readUriBytes failed: $uri", e)
            null
        } catch (e: OutOfMemoryError) {
            AppLog.e(TAG, "readUriBytes OOM: $uri")
            null
        }
    }

    /** content:// 的前 4 字节是否 AXML 文件头（外部应用用 UIN Tool 打开 AXML 的入口）。 */
    fun isAxmlUri(context: Context, uri: Uri): Boolean {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val head = ByteArray(4)
                input.read(head) == 4 &&
                    (head[0].toInt() and 0xFF) == 0x03 &&
                    (head[1].toInt() and 0xFF) == 0x00 &&
                    (head[2].toInt() and 0xFF) == 0x08 &&
                    (head[3].toInt() and 0xFF) == 0x00
            } ?: false
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 6.2.0 批次21：另存为 —— 在用户经 SAF 选择的目录（treeUri）下新建同名文档并写入。
     * 走 DocumentsContract + ContentResolver，**无需存储权限**（SAF 授予了该目录的访问权）。
     * 成功返回新文档 Uri；失败（含同名冲突等）返回 null。
     */
    fun saveAsToTree(context: Context, treeUri: Uri, fileName: String, content: String): Uri? {
        return saveAsToTree(
            context, treeUri, fileName,
            content.toByteArray(charsetFor(CHARSET_AUTO)),
            "text/plain"
        )
    }

    /** 字节版另存为（k.txt 第 10 条：AXML 另存必须继续保持二进制）。 */
    fun saveAsToTree(
        context: Context,
        treeUri: Uri,
        fileName: String,
        bytes: ByteArray,
        mime: String = "application/octet-stream"
    ): Uri? {
        // m.txt 第 7 条：内置选择器选的目录返回 `file://`，不是 SAF tree URI ——
        // DocumentsContract 接不住它（会抛 IllegalArgumentException），直接按真实目录写。
        // 返回同一套 file:// URI：后续保存走 ContentResolver 的 file scheme 分支（支持写），
        // resolveRealPath 也能立刻还原成路径。
        if (treeUri.scheme == "file") {
            val dir = treeUri.path?.let(::File) ?: return null
            if (!dir.isDirectory) return null
            return try {
                val target = File(dir, fileName)
                target.outputStream().use { it.write(bytes) }
                Uri.fromFile(target)
            } catch (e: Exception) {
                AppLog.e(TAG, "saveAsToLocalDir failed: $treeUri", e)
                null
            }
        }
        return try {
            val doc = DocumentsContract.createDocument(
                context.contentResolver, treeUri, mime, fileName
            ) ?: return null
            if (writeUriBytes(context, doc, bytes)) {
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
            // m.txt 第 7 条：内置选择器返回的 content://<包名>.inapppicker/f/<绝对路径>/<文件名>，
            // 路径就写在 URI 里 —— 不必再走 DocumentsContract/_data 列那两条查表路。
            "content" -> if (uri.authority?.endsWith(".inapppicker") == true) {
                val segs = uri.pathSegments
                if (segs.size >= 2 && segs[0] == "f") return segs[1]
            }
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
