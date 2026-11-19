// app/src/main/java/com/UIN/Tool/ui/screen/files/ArchiveIndex.kt
package com.UIN.Tool.ui.screen.files

import com.github.junrar.Archive
import com.github.junrar.exception.UnsupportedRarEncryptedException
import com.github.junrar.exception.WrongPasswordException
import org.apache.commons.compress.PasswordRequiredException
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.brotli.BrotliCompressorInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import org.apache.commons.compress.compressors.zstandard.ZstdCompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.io.PushbackInputStream
import java.io.RandomAccessFile

/**
 * 归档格式（MT.md §4.15 / §4.7）。
 *
 * [writable] 是**本版**的能力分级，不是库的理论上限 —— zip 可写、7z/tar 系只读。
 * 写能力给错比不给更危险：用户以为改了，其实没落盘。
 */
enum class ArchiveFormat(val writable: Boolean) {
    ZIP(writable = true),
    SEVEN_Z(writable = false),
    TAR(writable = false),
    /**
     * RAR（e16「rar 不解」）。**只读** —— 不是保守，是没有那条路：
     * RAR 是 RARLAB 的专有格式，开源侧只有解压、没有打包实现。
     */
    RAR(writable = false),
    /** 认得出但本版只当「不可浏览」——宁可明说不支持，也不要列个空目录骗人。 */
    UNKNOWN(writable = false);

    /**
     * 这个格式的条目头**自带 CRC32** 吗 —— 对比页「普通」档的判据来源。
     *
     * `TAR` 一并盖住 `.gz/.bz2/.xz/.zst` 这类「只压了一下的一整个文件」：
     * 识别成 [TAR] 不代表里面一定是打包，而那条路（`scanPlain`）同样没有条目级 CRC。
     *
     * 摆不摆那一档由它说了算（`modeOptionsFor`）：对 tar 摆一颗点了没变化的胶囊，
     * 比不摆更像坏了。
     */
    val hasEntryCrc: Boolean get() = when (this) {
        ZIP, SEVEN_Z, RAR -> true
        TAR, UNKNOWN -> false
    }

    /** 是否需要把整个流扫一遍才能列目录（无中央索引）。 */
    val needsFullScan: Boolean get() = this == SEVEN_Z || this == TAR
}

/**
 * 归档内的一个条目（4.15）。
 *
 * [path] 是**归档内路径**，用 `/` 分隔、无前导斜杠（与 zip/tar 的规范一致）；
 * 目录的 [path] 结尾也**不**带 `/` —— 统一靠 [isDir] 判别，免得路径比较时要记得先去尾斜杠。
 */
data class ArchiveEntry(
    val path: String,
    val isDir: Boolean,
    val size: Long,
    val modified: Long,
    /**
     * 条目正文的 CRC32 —— **包里记着的那一个**，不是现算的；没有就 null。
     *
     * 只有 zip / 7z / rar 的条目头带这个字段。**tar 与单文件压缩根本没有**：
     * tar 头只有一个校验头部自身的 checksum，正文一个校验和都不存。
     * 「这一条没记」与「这格式给不了」在调用方看来完全一样 —— 对比页只有两条都
     * 非空时才有的比，缺一边就自动退回比大小（见 [CompareMode]）。
     *
     * 三处扫描各按本格式的「没记」判据取值，**最后统一要求 > 0**：
     * 真值恰好是 0 的概率是 2^-32，与 CRC32 自身的碰撞概率同量级，为它留一条
     * 分支只会让三种格式的口径对不齐。
     *
     * 一趟扫索引**顺手就带出来了**，不额外花钱 —— 别改成 `archiveEntryCrc32` 那样
     * 为一条重开一次包，对比要 N 条就是 O(N^2)。
     */
    val crc: Long? = null
) {
    /** 最后一段；根条目不该问这个，调用方保证 [path] 非空。 */
    val name: String get() = path.substringAfterLast('/')

    /**
     * 压缩后大小：**故意不设这个字段**。
     *
     * zip 和 tar 拿得到，但 `SevenZArchiveEntry.getCompressedSize()` 在
     * commons-compress 1.28.0 里是**包级私有**（`javap` 可证），7z 的目录
     * 因此永远填不出真值 —— 与其让同一个字段在三种格式下含义不一
     * （真值 / 真值 / 编出来的 -1），不如整个不存。UI 也不需要它：
     * 全仓只有构造、没有任何读取。
     */
}

/**
 * 一个窗格**正处在某个归档内**的状态（4.15：每个窗格可各自处于不同归档内，两窗互不干扰）。
 *
 * [internalDir] 是归档内当前目录，根用空串表示 —— 与虚拟路径的拼接保持同一套约定，
 * 免得出现 `zip!//` 这种双斜杠。
 */
data class ArchiveState(
    val archivePath: String,
    val format: ArchiveFormat,
    /** 归档内当前目录（根 = 空串，其余无前导/尾随斜杠）。 */
    val internalDir: String
)

// ==================== 虚拟路径（4.15） ====================

/**
 * 虚拟路径：`<归档绝对路径>!/<内部路径>`。
 *
 * 分隔符用 `!/` 而不是单个 `!` —— 文件名里出现 `!` 很常见（`bang!.zip`），
 * 而 `!/` 同时出现在名字里的概率极低；真撞上了也只在**最后一个** `!/` 处切开。
 *
 * 解析一律以「最后一个 `!/`」为准：归档路径本身不可能含 `!/`（`/` 是路径分隔符），
 * 所以最后一个分隔符左边必然整段是归档路径。
 */
object VirtualPath {

    const val MARK = "!/"

    /** 该路径是否指向归档内部。 */
    fun isInArchive(path: String): Boolean = path.indexOf(MARK) >= 0

    /**
     * 拼一条虚拟路径；[internalDir] 为空即归档根。
     *
     * **[MARK] 本身已经带 `/`，所以这里不能再补一个** —— 早先多拼了一个 `/`，产出 `x.zip!//a/b`
     * 这种双斜杠，与 [ArchiveState] 注释里「免得出现 `zip!//` 这种双斜杠」的约定自相矛盾。
     * 它一直没暴露是因为 [split] 里那句 `removePrefix("/")` 恰好把它吃掉了（该容错保留，
     * 因为它同时让 [split] 兼容任意来源、不带引导斜杠的内部路径）。
     * 归档条目的 path 有 5 处由本函数拼出（列表条目、面包屑点击目标、上一层、
     * 进归档、冲突框源路径），所以拼错等于全链路都是 `!//`。
     */
    fun join(archivePath: String, internalDir: String): String =
        if (internalDir.isEmpty()) "$archivePath$MARK" else "$archivePath$MARK$internalDir"

    /**
     * 拆成 `(归档路径, 内部目录)`；非虚拟路径返回 null。
     *
     * 切在**最后一个** `!/` 上：`/a/b.zip!/x!/y` 里归档是 `/a/b.zip`，
     * 内部是 `x!/y`（内部条目名里带 `!` 时靠这条规则仍然解析得对）。
     */
    fun split(path: String): Pair<String, String>? {
        val idx = path.lastIndexOf(MARK)
        if (idx < 0) return null
        val archive = path.substring(0, idx)
        if (archive.isEmpty()) return null
        val internal = path.substring(idx + MARK.length).removePrefix("/")
        return archive to internal
    }

    /** 归档绝对路径；非虚拟路径返回 null。 */
    fun archiveOf(path: String): String? = split(path)?.first

    /** 归档内目录；非虚拟路径或在根上返回空串。 */
    fun internalOf(path: String): String? = split(path)?.second

    /** 归档内上一层；已在根则返回 null（调用方据此决定「退出归档」）。 */
    fun parentInternal(internalDir: String): String? =
        if (internalDir.isEmpty()) null
        else internalDir.substringBeforeLast('/', "")

    /**
     * 面包屑分段：归档名 + 逐层内部目录。
     *
     * 第一段是归档本身（不含 `!/`），返回的其余段是**归档内**路径，调用方直接
     * [join] 回去即可 —— 不让 UI 层自己去切字符串。
     */
    fun crumbs(path: String): List<Pair<String, String>>? {
        val (archive, internal) = split(path) ?: return null
        val out = mutableListOf(archive to archive)
        if (internal.isNotEmpty()) {
            var acc = ""
            internal.split('/').forEach { seg ->
                acc = if (acc.isEmpty()) seg else "$acc/$seg"
                out += seg to acc
            }
        }
        return out
    }
}

// ==================== 格式识别 ====================

/**
 * 认不出格式、或认出的格式一个条目都读不出来时抛这个。
 *
 * f11：原先 `UNKNOWN` 直接返回**空表** —— 用户看到的是一个空目录，没有任何提示，
 * 而空表还会被写进条目缓存，于是「点一次是空、之后永远是空」，直到文件或进程变化。
 * 现在把「认不出」变成一个**说得出口的错误**，由 `loadArchive` 翻成「压缩包已损坏 /
 * 无法识别」空态；空表也不再入缓存（空包每次重扫一次，代价可以忽略）。
 */
class UnsupportedArchiveException(detail: String) :
    java.io.IOException("unsupported archive: $detail")

/**
 * 识别归档格式（4.7-C：**魔数优先**，扩展名只是提示）。
 *
 * 扩展名会骗人（`photo.zip` 其实是 jpg），魔数不会。但魔数只认前几个字节，
 * 所以**先按魔数判**，认不出再回落扩展名 —— 顺序反了就会被改过名的文件带偏。
 *
 * f11：读头从 8 字节放宽到 **512 字节**。8 字节根本看不到 tar 的 `ustar`
 * （偏移 257），于是「无后缀 / 后缀写错的 tar」只能落到 `UNKNOWN` → 空目录；
 * 512 字节是一个 tar 块，正好够做 V7 老 tar 的**头部校验和**验证。
 */
fun detectArchiveFormat(file: File): ArchiveFormat {
    if (!file.isFile) return ArchiveFormat.UNKNOWN
    val head = readHead(file)
    detectByMagic(head)?.let { return it }
    return when (file.extension.lowercase()) {
        "zip", "jar", "apk", "aar", "epub", "docx", "xlsx", "pptx" -> ArchiveFormat.ZIP
        "7z" -> ArchiveFormat.SEVEN_Z
        "rar" -> ArchiveFormat.RAR
        // tar.gz / tar.xz / tar.zst 都是「tar + 外层压缩」，外层由调用方解开后再喂 tar；
        // `gz`/`bz2`/… 也可能是**只压了一下的一整个文件**，里头是 tar 还是单文件由
        // openTarStream 的嗅探决定，识别成 TAR 不代表里面一定是打包
        "tar", "gz", "tgz", "bz2", "xz", "zst", "br" -> ArchiveFormat.TAR
        "cpio", "ar" -> ArchiveFormat.UNKNOWN
        else -> ArchiveFormat.UNKNOWN
    }
}

/** 读文件头最多 [size] 字节；读不到给空数组（小文件/无权限都不该抛）。 */
private fun readHead(file: File, size: Int = 512): ByteArray = try {
    file.inputStream().buffered().use { ins ->
        val buf = ByteArray(size)
        var got = 0
        while (got < buf.size) {
            val n = ins.read(buf, got, buf.size - got)
            if (n < 0) break
            got += n
        }
        if (got == buf.size) buf else buf.copyOf(got)
    }
} catch (_: Exception) {
    ByteArray(0)
}

/** 头几个字节的十六进制，只给诊断信息用（`magic=504b0304`）。 */
private fun hexHead(m: ByteArray, n: Int = 8): String =
    m.take(n).joinToString("") { "%02x".format(it.toInt() and 0xFF) }

private fun detectByMagic(m: ByteArray): ArchiveFormat? {
    if (m.size < 4) return null
    // zip 族：PK\x03\x04 / PK\x05\x06（空包）/ PK\x07\x08（分卷）
    if (m[0] == 'P'.code.toByte() && m[1] == 'K'.code.toByte() &&
        (m[2] == 3.toByte() || m[2] == 5.toByte() || m[2] == 7.toByte())
    ) return ArchiveFormat.ZIP
    // 7z: 37 7A BC AF 27 1C
    if (m[0] == 0x37.toByte() && m[1] == 0x7A.toByte() && m[2] == 0xBC.toByte() &&
        m[3] == 0xAF.toByte()
    ) return ArchiveFormat.SEVEN_Z
    // gzip: 1F 8B
    if (m[0] == 0x1F.toByte() && m[1] == 0x8B.toByte()) return ArchiveFormat.TAR
    // xz: FD 37 7A 58 5A 00
    if (m[0] == 0xFD.toByte() && m[1] == 0x37.toByte() && m[2] == 0x7A.toByte() &&
        m[3] == 0x58.toByte()
    ) return ArchiveFormat.TAR
    // bzip2: 42 5A 68
    if (m[0] == 0x42.toByte() && m[1] == 0x5A.toByte() && m[2] == 0x68.toByte()) {
        return ArchiveFormat.TAR
    }
    // zstd: 28 B5 2F FD —— 没这条的话，改过名的 `.tar.zst` 只能靠扩展名兜底
    if (m[0] == 0x28.toByte() && m[1] == 0xB5.toByte() && m[2] == 0x2F.toByte() &&
        m[3] == 0xFD.toByte()
    ) return ArchiveFormat.TAR
    // RAR：`Rar!` + 1A 07，第 7 字节才分 RAR4(00) / RAR5(01) —— 前 6 字节两者相同
    if (m.size >= 6 &&
        m[0] == 0x52.toByte() && m[1] == 0x61.toByte() && m[2] == 0x72.toByte() &&
        m[3] == 0x21.toByte() && m[4] == 0x1A.toByte() && m[5] == 0x07.toByte()
    ) return ArchiveFormat.RAR
    // brotli 没有魔数（只能靠扩展名）；tar 的 `ustar` 在偏移 257 —— 只有读到
    // 512 字节才看得见（f11：原先读 8 字节，于是「无后缀/后缀写错的 tar」
    // 一律 UNKNOWN → 空目录）。这里顺带认 V7 老 tar（没有 ustar，只有头部校验和）。
    if (tarLooksValid(m)) return ArchiveFormat.TAR
    return null
}

/**
 * 内容像不像一个 tar 块。
 *
 * 两种都认：
 * - **ustar/GNU**：偏移 257 处 `ustar`（GNU 那串是 `ustar  \0`，前 5 字节一样）；
 * - **V7（1988 年前的老包，没有 ustar）**：偏移 148 的八进制**头部校验和**。
 *   校验和把 148..155 这 8 字节按空格计，其余按无符号字节求和；少数实现按有符号
 *   字节求和，两种都算一遍 —— 只认一种会把另一批老包继续判成「认不出」。
 *
 * 全零块（空 tar / 结束块）不算：那不是「一个条目」，认成 tar 只会得到空表。
 */
private fun tarLooksValid(b: ByteArray): Boolean {
    if (b.size < 512) return false
    var allZero = true
    for (v in b) {
        if (v.toInt() != 0) {
            allZero = false
            break
        }
    }
    if (allZero) return false
    if (b[257] == 'u'.code.toByte() && b[258] == 's'.code.toByte() &&
        b[259] == 't'.code.toByte() && b[260] == 'a'.code.toByte() &&
        b[261] == 'r'.code.toByte()
    ) return true
    // 校验和字段：八进制、以 NUL 或空格结尾/填充
    val field = String(b, 148, 8, Charsets.US_ASCII).trim('\u0000', ' ')
    val stored = field.toLongOrNull(8) ?: return false
    var unsigned = 0L
    var signed = 0L
    for (i in 0 until 512) {
        if (i in 148..155) {
            unsigned += 32L
            signed += 32L
        } else {
            unsigned += (b[i].toLong() and 0xFF)
            signed += b[i].toLong()
        }
    }
    return stored == unsigned || stored == signed
}

/**
 * 只看扩展名，**不做任何 I/O** —— 供主线程在点击时判断「这该不该当目录进」。
 *
 * 这里故意不查魔数：`listFiles` 之后目录里可能有上千个文件，每个都读 8 字节磁盘
 * 就是上千次系统调用，主线程必掉帧。真正认格式是在 `loadArchive` 里（IO 线程）做的，
 * 所以这里放宽点、误判成归档也只会多走一次 IO，不会走错分支。
 */
fun looksLikeArchive(name: String): Boolean = "archive" in FileTypes.builtIn(name)

/**
 * **能整包解出**的扩展名 —— 与 [detectArchiveFormat] 的扩展名分支一一对应，
 * 只是把 `cpio`/`ar` 那两个「认得出、但本版不支持」的划到外面。
 *
 * 供上下文菜单门控用（用户反馈⑪：解压入口在长按菜单里）。菜单是主线程渲染的，
 * 不能像 [detectArchiveFormat] 那样读魔数；而按 [looksLikeArchive] 给的话，
 * `x.cpio` 会摆出一个点了必然报错的「解压」—— 正是 4.11「不该让用户试了才知道」。
 *
 * m.txt 第 3 条起两者同源：`assets/config/file_types.json` 里带 `archive` 内置能力的类型
 * 就是「归档阅读器接得住」的那批，`cpio`/`ar` 已在数据里单独归到 `archive_unsupported`
 * （无内置能力），所以这里与 [looksLikeArchive] 等价，不再维护第二份后缀清单。
 */
fun extractableName(name: String): Boolean = looksLikeArchive(name)

/**
 * 把归档内路径规范化，**逃逸出目标目录的一律返回 null**（zip-slip / 目录穿越防护）。
 *
 * 归档里的名字是**别人给的**：`../../etc/hosts`、`a/../../evil` 都是网上下载的包里
 * 真实存在的攻击写法。落到 `File(targetDir, raw)` 上就会写到目标目录外面去 ——
 * 而「解压到下载目录」恰恰是本应用最常见的用法，所以这道闸必须在**引擎里**过，
 * 而不是指望每个调用方都记得先查一遍。
 *
 * 规则：
 * - **NUL 截断**：`abc\0def` → `abc`。文件系统根本不收带 NUL 的名字（真去建会抛
 *   invalid file path），出现它多是打包工具把 C 字符串的结尾一起抄了进来；
 * - **反斜杠当分隔符**：`dir\file.txt` → `dir/file.txt`。Windows 系打包工具会拿 `\` 分层，
 *   不换的话它在 Android 上就是「一个带反斜杠的文件名」—— FAT 卡上直接建不出来，
 *   目录结构也永远展不开（这正是清单第 13 条）；
 * - `.` 与空段直接丢（`a//b`、`./a` 都是同一个东西）；
 * - `..` 往回退一段；**退无可退**（`../x`、`a/../../x`）= 要往外跑 → 整条返回 null；
 * - 前导 `/` 当相对路径处理（`/etc/hosts` → `etc/hosts`），zip 里带前导斜杠很常见，
 *   且绝大多数是良性命名习惯；
 * - 空路径（退成了 `.`）也返回 null。
 *
 * 返回值是**规范化后**的路径，调用方要用它替换原路径再往下传 —— 只判不换的话，
 * 同一份数据在别处（例如 `e.path` 与 `r.internalRoot` 的配对）又会拿原始串去比，闸等于白过。
 */
fun safeInternalPath(path: String): String? {
    val clean = path
        .substringBefore('\u0000')
        .replace('\\', '/')
    if (clean.isEmpty()) return null
    val out = ArrayList<String>()
    for (seg in clean.split('/')) {
        when (seg) {
            "", "." -> Unit
            ".." -> {
                if (out.isEmpty()) return null
                out.removeAt(out.size - 1)
            }

            else -> out.add(seg)
        }
    }
    if (out.isEmpty()) return null
    return out.joinToString("/")
}

// ==================== 目录列表 ====================

// ==================== 条目缓存（用户反馈 d9：包内每进一层就重新索引） ====================

/** 缓存的包数上限：两窗格各开一个归档 + 正在解的一个，留一格余量。 */
private const val ENTRY_CACHE_MAX_PACKS = 4

/**
 * 缓存的条数上限。
 *
 * 超过这个量就**不缓存**：一个上百万条的 tar 解出来的列表本身就可能上百 MB，
 * 让它常驻比每次重扫更伤 —— 那种包宁可每次扫，内存只在扫描期间占用。
 */
private const val ENTRY_CACHE_MAX_ITEMS = 200_000

/**
 * 按「绝对路径 + 大小 + 修改时间」认包（access-order = 真，命中多的留在里面）。
 *
 * 打包 / 解出 / 写回调的是文件本身，落盘后大小或 mtime 必变 → 键一变就自然失效，
 * 写路径上不必挨个通知。**只存条目元数据，不存解出来的正文。**
 */
private val entryCache =
    object : LinkedHashMap<String, List<ArchiveEntry>>(8, 0.75f, true) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<String, List<ArchiveEntry>>
        ): Boolean = size > ENTRY_CACHE_MAX_PACKS
    }

/** 不是真文件（不存在 / 是目录）就没有可缓存的东西 —— 让它照旧走失败分支。 */
private fun archiveCacheKey(file: File): String? =
    if (file.isFile) "${file.absolutePath}|${file.length()}|${file.lastModified()}" else null

/**
 * 读出**归档内全部条目**（只读元数据，不解出正文）。
 *
 * 被两处复用：列目录时按路径前缀挑直接子项；解出/压缩前先量出目标条目的总字节，
 * 好让进度条第 5 层有分母 —— 没有分母就只能显示计数，用户看不出还剩多少。
 *
 * **包内导航只扫一次**（用户反馈 d9）：进 `a/`、进 `a/b/`、退回来再进 `c/`，
 * 变的只是前缀，包本身一个字节都没动，却要重开一遍包 —— zip 得重读中央目录并
 * 把每个名字重新判一遍编码，7z 得把整包头重读一遍，tar 干脆把整个流再解压扫完。
 * 在一个 2GB 的 tar 里连点五层就是五遍全量扫描。这里按「包 + 大小 + mtime」缓存
 * 全量条目，命中就直接给；**命中的连进度都不报**（没有「扫」这个动作，
 * UI 那边的 loading 会自己落回去，不该闪一下「索引中…」）。
 *
 * @param onProgress 扫描进度（条目数）；未命中时才回调，7z/tar 全量扫按节流间隔回调
 * @throws java.io.IOException 归档损坏 / 打不开
 */
fun readArchiveEntries(
    file: File,
    onProgress: (count: Int) -> Unit = {}
): List<ArchiveEntry> {
    val key = archiveCacheKey(file)
    if (key != null) {
        synchronized(entryCache) { entryCache[key] }?.let { return it }
    }

    val format = detectArchiveFormat(file)
    // f11：认不出不再「静默空」。空目录是**合法结果**（空 zip 就是空的），
    // 而认不出是**失败** —— 两者混在一起，用户只会看到「这个包读不出，也不说为什么」。
    if (format == ArchiveFormat.UNKNOWN) {
        throw UnsupportedArchiveException(
            "magic=${hexHead(readHead(file))} ext=${file.extension.ifEmpty { "(none)" }}"
        )
    }

    var failure: Exception? = null
    var fresh: List<ArchiveEntry> = emptyList()
    try {
        fresh = scanAs(format, file, onProgress)
    } catch (e: Exception) {
        failure = e
    }

    // f11 P0-3：多读器回落。**只在主读器抛异常或读出空表时**才走这条 ——
    // 正常包一次都不多扫（大 tar 全扫很贵，不能变成常态）。
    // 典型受益：中央目录畸形的 zip、改过后缀的 7z/rar、被外层压缩的 tar。
    if (fresh.isEmpty()) {
        val alt = scanFallbacks(file, format, onProgress)
        if (!alt.isNullOrEmpty()) {
            fresh = alt
            failure = null
        }
    }

    if (failure != null && fresh.isEmpty()) throw failure

    // 空表**永不入缓存**（f11）：一次失败曾经被冻结成「这个包是空的」，
    // 之后同一进程里怎么点都是空。扫描一次空包的成本可以忽略，宁可重扫。
    if (key != null && fresh.isNotEmpty() && fresh.size <= ENTRY_CACHE_MAX_ITEMS) {
        synchronized(entryCache) { entryCache[key] = fresh }
    }
    return fresh
}

/** 按格式选读器。集中一处，[readArchiveEntries] 的回落逻辑才不用重复 `when`。 */
private fun scanAs(format: ArchiveFormat, file: File, onProgress: (Int) -> Unit): List<ArchiveEntry> =
    when (format) {
        ArchiveFormat.ZIP -> scanZip(file, onProgress)
        ArchiveFormat.SEVEN_Z -> scanSevenZ(file, onProgress)
        ArchiveFormat.TAR -> scanTar(file, onProgress)
        ArchiveFormat.RAR -> scanRar(file, onProgress)
        ArchiveFormat.UNKNOWN -> emptyList()
    }

/**
 * 拿不准的包，按**内容**把其余读器试一遍，取第一个非空结果。
 *
 * 候选不是「全部都试」：每种格式都要有内容证据（zip/7z/rar 的魔数、tar 的块校验和），
 * 否则拿一个普通二进制去喂 tar 解析器，可能解析出**一堆像模像样的鬼名字**。
 */
private fun scanFallbacks(
    file: File,
    tried: ArchiveFormat,
    onProgress: (Int) -> Unit
): List<ArchiveEntry>? {
    val head = readHead(file)
    val candidates = ArrayList<ArchiveFormat>(3)
    if (tried != ArchiveFormat.ZIP &&
        head.size >= 4 && head[0] == 'P'.code.toByte() && head[1] == 'K'.code.toByte() &&
        (head[2] == 3.toByte() || head[2] == 5.toByte() || head[2] == 7.toByte())
    ) candidates += ArchiveFormat.ZIP
    if (tried != ArchiveFormat.SEVEN_Z && head.size >= 4 &&
        head[0] == 0x37.toByte() && head[1] == 0x7A.toByte() &&
        head[2] == 0xBC.toByte() && head[3] == 0xAF.toByte()
    ) candidates += ArchiveFormat.SEVEN_Z
    if (tried != ArchiveFormat.RAR && head.size >= 6 &&
        head[0] == 'R'.code.toByte() && head[1] == 'a'.code.toByte() &&
        head[2] == 'r'.code.toByte() && head[3] == '!'.code.toByte() &&
        head[4] == 0x1A.toByte() && head[5] == 0x07.toByte()
    ) candidates += ArchiveFormat.RAR
    if (tried != ArchiveFormat.TAR && tarLooksValid(head)) candidates += ArchiveFormat.TAR
    for (fmt in candidates) {
        val got = runCatching { scanAs(fmt, file, onProgress) }.getOrNull()
        if (!got.isNullOrEmpty()) return got
    }
    return null
}

/**
 * 丢掉某个包的条目缓存（f11）。
 *
 * 写路径本来靠「大小 + mtime」自然失效，但同一毫秒内改完且大小恰好不变时键不变 ——
 * 用户会看到「加进去了却不在列表里」。写成功后显式清一次，比赌时钟粒度可靠。
 */
internal fun invalidateArchiveEntries(file: File) {
    val prefix = file.absolutePath + "|"
    synchronized(entryCache) {
        entryCache.keys.removeAll { it.startsWith(prefix) }
    }
}

/** 取某个包已经记下的密码（没有就 null）。包内搬运要拿它解源条目。 */
internal fun cachedArchivePassword(file: File): String? =
    synchronized(archivePasswords) { archivePasswords[file.absolutePath] }

/**
 * 列出 [internalDir] 的**直接子项**（4.15）。
 *
 * 三种格式的读法差异很大，但对外只有一句承诺：**给的是直接子项，且不把整个归档读进内存**。
 *
 * - **zip** 走 zip4j 的中央目录索引 —— 只读索引不解压，大包也是瞬时的；
 * - **7z** 有索引但要建（构造 `SevenZFile` 时顺序读完全部头）→ 调用方给「索引中…」提示；
 * - **tar 系**没有索引，只能全量扫一遍流；扫的过程**不缓存条目内容**，只留路径与元数据。
 *
 * 隐含目录会被补出来：zip 里条目常只写 `a/b/c.txt` 而没有 `a/`、`a/b/` 这两条，
 * 直接按条目列表渲染会让 `a` 根本不出现。这里按路径前缀聚合补全。
 *
 * @param onProgress 扫描进度（条目数）；7z/tar 全量扫时按节流间隔回调，供「索引中…」用
 * @throws java.io.IOException 归档损坏 / 打不开
 */
fun listArchiveChildren(
    file: File,
    internalDir: String,
    onProgress: (count: Int) -> Unit = {}
): List<ArchiveEntry> = directChildren(readArchiveEntries(file, onProgress), internalDir)

/**
 * 按**包内路径**取单条条目（属性框用，e15 **P1-7**）。
 *
 * 走 [readArchiveEntries] 的**条目缓存** —— 刚列完目录就开属性框，不会重扫一遍整包。
 * 比对前先把给来的路径过一遍 [safeInternalPath]：条目表里存的是规范形，
 * 调用方那一截却可能带尾斜杠或 `./`，字面比较会永远匹配不上。
 */
internal fun findArchiveEntry(archive: File, internalPath: String): ArchiveEntry? {
    val want = safeInternalPath(internalPath) ?: return null
    return readArchiveEntries(archive).firstOrNull { it.path == want }
}

/**
 * 单条条目的 **CRC32** —— 归档**自己记着**的那一份校验值（zip 的中央目录、7z 的头索引
 * 里就摆着），**一个字节都不用解压**。这就是「校验区」要给用户看的那个数（e15 **P1-7**）。
 *
 * @return `null` = **这个格式压根没有这一项**（tar、单文件 gz/bz2/xz、未知格式）。
 *   调用方据此**整行不摆** —— 摆一个「—」会被读成「读失败了」，而事实是本来就没有。
 *   另外，包里没记（-1）也转成 `null`：显示成 `ffffffff` 只会误导。
 */
internal fun archiveEntryCrc32(archive: File, internalPath: String): Long? {
    val want = safeInternalPath(internalPath) ?: return null
    val crc: Long? = when (detectArchiveFormat(archive)) {
        ArchiveFormat.ZIP -> runCatching {
            var hit: Long? = null
            ArchiveEncoding.openZipArchive(archive).use { zip ->
                val en = zip.entries
                while (en.hasMoreElements()) {
                    val e = en.nextElement()
                    if (safeInternalPath(e.name) == want) {
                        hit = e.crc
                        break
                    }
                }
            }
            hit
        }.getOrNull()

        ArchiveFormat.SEVEN_Z -> runCatching {
            var hit: Long? = null
            openSevenZ(archive).use { z ->
                for (e in z.entries) {
                    if (safeInternalPath(e.name) == want) {
                        // 包里没记 CRC 时 getCrc() 给的是 0（不是 -1），拿 hasCrc 分辨，
                        // 否则「没有这一项」会被显示成 00000000
                        if (e.hasCrc) hit = e.crc.toLong() and 0xFFFFFFFFL
                        break
                    }
                }
            }
            hit
        }.getOrNull()

        ArchiveFormat.RAR -> runCatching {
            var hit: Long? = null
            openRar(archive).use { rar ->
                for (fh in rar.getFileHeaders()) {
                    if (safeInternalPath(fh.getFileName() ?: "") == want) {
                        val c = fh.getFileCRC()
                        // -1 = 包里压根没记这一项；显示成 ffffffff 只会误导（同 7z 那条）
                        if (c != -1) hit = c.toLong() and 0xFFFFFFFFL
                        break
                    }
                }
            }
            hit
        }.getOrNull()

        else -> null
    }
    return crc?.takeIf { it >= 0L }
}

/**
 * 从**全量条目**里挑出 [internalDir] 的直接子项，并补出隐含目录。
 *
 * 目录行一并给出**聚合**：`size` = 后代文件字节之和，`modified` = 后代里最新的一条
 * （用户反馈 e15 **P2-18**）。原先目录恒 0 / `-`，包里一个 2GB 的子文件夹和一个
 * 空文件夹长得一模一样 —— 想找东西只能挨个点开。
 *
 * 聚合**只算到这一层**：这些数是给当前这一屏的行看的，往下的分支等真进去时
 * 再按那一层重算。一趟 `for` 里顺手累加，不额外开一轮全量遍历（大包几十万条）。
 */
private fun directChildren(all: List<ArchiveEntry>, internalDir: String): List<ArchiveEntry> {
    val prefix = if (internalDir.isEmpty()) "" else "$internalDir/"
    val seen = LinkedHashMap<String, ArchiveEntry>()
    val sum = HashMap<String, Long>()
    val newest = HashMap<String, Long>()
    for (e in all) {
        if (prefix.isNotEmpty() && !e.path.startsWith(prefix)) continue
        val rest = e.path.removePrefix(prefix)
        if (rest.isEmpty()) continue
        val slash = rest.indexOf('/')
        if (slash < 0) {
            // 直接子项（文件，或显式目录条目）
            if (e.isDir) seen.putIfAbsent(e.path, e)
            else seen[e.path] = e
        } else {
            // 更深层的条目 → 首段是个**隐含目录**，只补一次
            // （注意是位置传参：path, isDir, size, modified —— ArchiveEntry 已无 compressedSize）
            val dirPath = prefix + rest.substring(0, slash)
            seen.putIfAbsent(
                dirPath,
                ArchiveEntry(dirPath, true, 0L, e.modified)
            )
            // 累加到「相对当前这一层的首段」—— `a/b/c.txt` 归到 `a`，
            // 正是页面上那一行要代表的范围。
            if (!e.isDir && e.size > 0L) sum[dirPath] = (sum[dirPath] ?: 0L) + e.size
            if (e.modified > 0L) {
                val cur = newest[dirPath]
                if (cur == null || e.modified > cur) newest[dirPath] = e.modified
            }
        }
    }
    if (sum.isEmpty() && newest.isEmpty()) return seen.values.toList()
    // 文件行原样；目录行回填。**显式**目录条目同样走这里 —— 它那条早被 putIfAbsent
    // 放进 seen 了，聚合值是它后代算出来的，只能在这一轮统一盖上去。
    return seen.values.map { e ->
        if (!e.isDir) e
        else e.copy(
            size = sum[e.path] ?: e.size,
            // max 而不是覆盖：显式目录条目自带的 mtime 可能比后代新（空目录、
            // 只含 0 长度项的目录），直接盖会把已知的最新时间改小。
            modified = maxOf(e.modified, newest[e.path] ?: 0L)
        )
    }
}

/**
 * zip：读中央目录索引，不解压（`ZipFile` 打开即定位到中央目录，与 zip4j 的随机访问同一性能档）。
 *
 * **改用 commons-compress 而非 zip4j，是为了和「解出」共用同一个解码器**：
 * 4.15 最后一条要求归档内条目能复制到另一窗格，而提取走 `ZipFile.getInputStream`。
 * 若列目录用 zip4j、提取用 commons-compress，遇到 GBK/Big5 名字就会出现
 * 「列表里看得见、复制却一条都对不上」—— 两个解码器对同一批字节给出不同字符串。
 * 4.15 本来就点名了「commons-compress `ZipFile` 作按 GBK/Big5 解条目名的那条路」，
 * 这里只是把它提前成了**唯一**那条；zip4j 留给 M4b 的 zip 写入与 M4d 的加密。
 *
 * 条目名编码的回退已收敛进 [ArchiveEncoding]（UTF-8 → GBK → Big5 自动判定），
 * **列目录与提取共用同一个入口**，避免「列得出来、取不出来」。
 */
private fun scanZip(file: File, onProgress: (Int) -> Unit): List<ArchiveEntry> {
    val out = ArrayList<ArchiveEntry>()
    // 编码判定走唯一入口 `ArchiveEncoding.openZipArchive` —— **不许在这里直接
    // ZipFile.builder()**：列目录与提取必须用同一套条目名编码，否则同一个包会
    // 「列得出来、取不出来」。详见 ArchiveEncoding 的 KDoc。
    ArchiveEncoding.openZipArchive(file).use { zip ->
        val en = zip.entries
        var i = 0
        while (en.hasMoreElements()) {
            val e = en.nextElement()
            // 规范化（`./a`、`a//b`、`/a` → `a`、`a/b`、`a`；`../a` 逃逸的整条不列）——
            // 不换的话浏览器会按 `.` 分出一个假文件夹。**zip 这里刻意不换**：包内改名 /
            // 删除 / 写回全靠与包里原样名精确比对，换成规范形就找不着条目了。
            val name = safeInternalPath(e.name)
            if (name != null) {
                out += ArchiveEntry(
                    path = name,
                    isDir = e.isDirectory,
                    // 未知长度时 getSize() 给 -1，直接当字节数会算出负进度
                    size = if (e.isDirectory) 0L else e.size.coerceAtLeast(0L),
                    modified = runCatching { e.lastModifiedTime.toMillis() }.getOrDefault(0L),
                    // ZipEntry 没记时给的是 **null**（不是 -1）；再按统一口径把 0 也当没记
                    crc = e.crc?.takeIf { it > 0L }
                )
            }
            if (++i and 0x3FF == 0) onProgress(out.size)
        }
    }
    onProgress(out.size)
    return out
}

/**
 * 用户为某个包输过的密码（e15 **P1-12**）。按**绝对路径**缓存，只活在内存里 ——
 * 密码是用户输的东西，不进设置、更不进导出。
 *
 * 收敛到这一个口子的理由，和 zip 条目名编码那条一模一样：列目录、属性 CRC、搜索、
 * 解压、复制出、包内打开文件**全都要读同一个包**，各传各的迟早跑出
 * 「列得出来、解不出来」两种行为。
 */
private val archivePasswords = HashMap<String, String>()

/** 记下用户为 [file] 输过的密码。空串按「没输」处理 —— 那不是「密码是空的」。 */
internal fun rememberArchivePassword(file: File, password: String?) {
    val key = file.absolutePath
    synchronized(archivePasswords) {
        if (password.isNullOrEmpty()) archivePasswords.remove(key)
        else archivePasswords[key] = password
    }
}

/**
 * 打开 7z 的**唯一**入口（e15 **P1-12**）。
 *
 * [password] 没给就用缓存 —— 「包内打开文件」那条路压根没有地方让用户输密码，
 * 只能靠这里把之前输过的接上。给了就用给的。
 *
 * 这一条**不许**再出现第二处 `SevenZFile.builder()`：密码只要有一处漏传，
 * 表现就是「这个包在这儿打得开、在那儿打不开」，查起来最费劲。
 */
internal fun openSevenZ(file: File, password: String? = null): SevenZFile {
    val builder = SevenZFile.builder().setFile(file)
    val pw = password?.takeIf { it.isNotEmpty() }
        ?: synchronized(archivePasswords) { archivePasswords[file.absolutePath] }
    if (!pw.isNullOrEmpty()) builder.setPassword(pw)
    return builder.get()
}

/**
 * 这个异常是不是「要密码」（e15 **P1-12**）。
 *
 * 库有可能把 `PasswordRequiredException` 包在别的异常里，按类型直接 catch 会漏 ——
 * 沿 cause 链问一遍。带跳数上限：`cause == self` 的异常会让 while 死循环。
 */
internal fun isPasswordRequired(e: Throwable?): Boolean {
    var t = e
    var hops = 0
    while (t != null && hops < 16) {
        if (t is PasswordRequiredException) return true
        t = if (t.cause === t) null else t.cause
        hops++
    }
    return false
}

/**
 * 打开 RAR 的**唯一**入口（e16「rar 不解」）。
 *
 * 与 [openSevenZ] 一样只许有这一处：密码只要有一处漏传，表现就是「这个包在这儿打得开、
 * 在那儿打不开」，查起来最费劲。没显式给就用缓存那份 —— 「包内打开文件」那条路
 * 压根没有地方让用户再输一次。
 *
 * 两种异常就地翻译成 [PasswordRequiredException]，好让上层只认这**一个**信号：
 *
 * - **RAR5 头加密却没给密码** → junrar 抛 `WrongPasswordException("Missing password …")`。
 *   不转的话会一路掉进「已损坏」，而包其实好好的 —— 正是 e15 P1-12 里 7z 那条毛病；
 * - **RAR4 头加密** junrar 不给准话（主头本身没加密，坏在后面那条文件头的解密上），
 *   所以开包前先自己看主头的 `MHD_PASSWORD` 位。这一位**没密码也读得到**。
 *
 * 密码给错也走同一个翻译：上层 `submitArchivePassword` 会再补一句「密码错误」，
 * 这里只负责别把它报成「已损坏」。
 */
internal fun openRar(file: File, password: String? = null): Archive {
    val pw = password?.takeIf { it.isNotEmpty() }
        ?: synchronized(archivePasswords) { archivePasswords[file.absolutePath] }
    if (pw.isNullOrEmpty() && rar4HeadersEncrypted(file) == true) {
        throw PasswordRequiredException("rar headers encrypted")
    }
    return try {
        if (pw.isNullOrEmpty()) Archive(file) else Archive(file, pw)
    } catch (e: WrongPasswordException) {
        throw PasswordRequiredException(e.message ?: "rar password required")
    } catch (e: UnsupportedRarEncryptedException) {
        throw PasswordRequiredException(e.message ?: "rar encrypted")
    }
}

/**
 * RAR4 的「加密文件名」位（`MHD_PASSWORD` = 0x0080）：标在**主头**上，而主头本身不加密，
 * 所以没密码也读得到这一位。
 *
 * @return `true` = 头被加密；`false` = 没加密；`null` = 不是 RAR4（RAR5 由 junrar 自己报）
 *   或读不出来。三者**不许混**，`null` 必须走「让 junrar 试」那条路。
 *
 * 自己读而不是全靠 junrar：RAR4 头加密时它只会说「头坏了」，那正是
 * 「包明明是好的、却报已损坏」的来源。
 */
private fun rar4HeadersEncrypted(file: File): Boolean? {
    return try {
        RandomAccessFile(file, "r").use { raf ->
            val mark = ByteArray(7)
            if (raf.read(mark) != 7) return@use null
            // `Rar!\x1a\x07` + 版本字节：00 = RAR4，01/02 = RAR5
            if (mark[0] != 0x52.toByte() || mark[1] != 0x61.toByte() ||
                mark[2] != 0x72.toByte() || mark[3] != 0x21.toByte() ||
                mark[4] != 0x1A.toByte() || mark[5] != 0x07.toByte()
            ) return@use null
            if (mark[6].toInt() != 0) return@use null
            // 主头块：HEAD_CRC(2) HEAD_TYPE(1)=0x73 HEAD_FLAGS(2, 小端) HEAD_SIZE(2)
            val hdr = ByteArray(7)
            if (raf.read(hdr) != 7) return@use null
            if (hdr[2].toInt() != 0x73) return@use null
            val flags = (hdr[3].toInt() and 0xFF) or ((hdr[4].toInt() and 0xFF) shl 8)
            (flags and 0x0080) != 0
        }
    } catch (_: Exception) {
        null
    }
}

/**
 * RAR：junrar 在**构造时就把整条头链读完了**，没有 7z/tar 那种「全量扫流」——
 * [onProgress] 仍按条目数间隔回调，只为与其它格式的进度口径一致。
 *
 * 规范化与逃逸防护走同一个 [safeInternalPath]（7z/tar 都是在这儿换手的），
 * `../a` 这种逃逸的一条都不列。
 */
private fun scanRar(file: File, onProgress: (Int) -> Unit): List<ArchiveEntry> {
    val out = ArrayList<ArchiveEntry>()
    openRar(file).use { rar ->
        var i = 0
        for (fh in rar.getFileHeaders()) {
            // 目录在 RAR 里常以 `/` 或 `\` 结尾，先削掉再交给 safeInternalPath ——
            // 削完是空的（根目录那条）就整条跳过，免得列一个叫 "" 的假行
            val name = fh.getFileName()?.trimEnd('/', '\\') ?: continue
            if (name.isNotEmpty()) {
                safeInternalPath(name)?.let { norm ->
                    out += ArchiveEntry(
                        path = norm,
                        isDir = fh.isDirectory,
                        size = if (fh.isDirectory) 0L else fh.getUnpSize(),
                        modified = fh.getMTime()?.time ?: 0L,
                        // junrar 给的是 int。**先无符号化再筛**：直接 `> 0` 会把高位置 1
                        // 的那半数合法 CRC 当负数丢掉；-1 是「没记」，0 按统一口径也当没记
                        crc = (fh.getFileCRC().toLong() and 0xFFFFFFFFL)
                            .takeIf { it != 0L && it != 0xFFFFFFFFL }
                    )
                }
            }
            if (++i and 0x3FF == 0) onProgress(out.size)
        }
    }
    onProgress(out.size)
    return out
}

/** 7z：构造时顺序读完整个头索引；solid 包也只读头，不碰正文。 */
private fun scanSevenZ(file: File, onProgress: (Int) -> Unit): List<ArchiveEntry> {
    val out = ArrayList<ArchiveEntry>()
    openSevenZ(file).use { seven ->
        val entries = seven.entries
        // 不做 ensureCapacity：getEntries() 的声明类型是 Iterable（不是 List），拿不到 size；
        // 为了预分配去 count() 一遍又等于多扫一次，比 ArrayList 的几何扩容还贵。
        var i = 0
        for (e in entries) {
            // 规范化条目名：`./a`、`a//b` → `a`、`a/b`，`../a` 这种逃逸的整条不列（7z 是
            // 只读格式，没有「要跟包里原样名精确比对才能改」的包袱，可以放心换）
            val name = safeInternalPath(e.name)
            if (name != null) {
                out += ArchiveEntry(
                    path = name,
                    isDir = e.isDirectory,
                    size = e.size,
                    modified = if (e.hasLastModifiedDate) e.lastModifiedDate.time else 0L,
                    // 7z 的 CRC 是可选的，`hasCrc` 才说有没有 —— 不问它就可能拿一个
                    // 默认 0 去跟真值比出一个假的「不同」
                    crc = if (e.hasCrc) e.crcValue.takeIf { v -> v > 0L } else null
                )
            }
            if (++i and 0x3FF == 0) onProgress(out.size)
        }
    }
    onProgress(out.size)
    return out
}

/**
 * tar 系（含 .gz/.xz/.bz2/.zst 外层）：**纯流式全量扫描**，一条条读出来就丢内容引用，
 * 只留元数据 —— 512MB heap 下，几百 MB 的 tar 也只占路径字符串那点内存。
 *
 * [openTarStream] 返回 null 表示「剥掉外层之后里面**不是** tar」—— 那是 `a.log.gz`
 * 这类**只压了一下的一整个文件**，不是打包，整个包就一条内容（用户反馈 b10）。
 */
private fun scanTar(file: File, onProgress: (Int) -> Unit): List<ArchiveEntry> {
    val inner = openTarStream(file) ?: return scanPlain(file, onProgress)
    val out = ArrayList<ArchiveEntry>()
    TarArchiveInputStream(inner).use { tar ->
        var i = 0
        while (true) {
            val entry: TarArchiveEntry = tar.nextTarEntry ?: break
            // 规范化条目名：`tar cf x.tar .` 打出来的**全是 `./` 开头**，不换就会在浏览器里
            // 分出一个叫 `.` 的假文件夹。tar 只读，没有原样名比对的包袱（用户反馈 b10 清单）
            val name = safeInternalPath(entry.name)
            if (name != null) {
                out += ArchiveEntry(
                    path = name,
                    isDir = entry.isDirectory,
                    size = entry.size,
                    modified = entry.modTime?.time ?: 0L
                )
            }
            if (++i and 0x3FF == 0) onProgress(out.size)
        }
    }
    onProgress(out.size)
    return out
}

/** 单文件压缩：整包就是**一条**内容，名字 = 文件名去掉外层压缩后缀（`a.log.gz` → `a.log`）。 */
private fun scanPlain(file: File, onProgress: (Int) -> Unit): List<ArchiveEntry> {
    val entry = ArchiveEntry(
        path = plainInnerName(file),
        isDir = false,
        size = plainUncompressedSize(file),
        modified = file.lastModified()
    )
    onProgress(1)
    return listOf(entry)
}

/**
 * 单文件压缩还原出来的条目名：剥掉**外层压缩后缀**（`a.log.gz` → `a.log`）。
 *
 * `.tar.gz` 不会走到这里（里面是 tar，[scanTar] 直接接了），所以后缀表里没有 `.tar`。
 * 剥空了（文件本来就叫 `.gz`）就保留原名 —— 名字别致也远好过一个空条目名。
 */
internal fun plainInnerName(file: File): String {
    val lowered = file.name.lowercase()
    for (suffix in PLAIN_SUFFIXES) {
        if (lowered.endsWith(suffix) && lowered.length > suffix.length) {
            return file.name.dropLast(suffix.length)
        }
    }
    return file.name
}

private val PLAIN_SUFFIXES = listOf(".gz", ".gzip", ".bz2", ".xz", ".zst", ".br")

/**
 * 单文件压缩的未压缩大小。**只有 gzip 把这个数记在包里**（文件尾 4 字节，小端、mod 2^32）；
 * bz2/xz/zst/br 想知道就得把整条流解一遍 —— 为列表里一个数字扫完整个包不值当。
 *
 * 量不出来就给 0，进度随之退化成按条目数走（本仓对「总量未知」的既有口径），**不谎报**。
 */
private fun plainUncompressedSize(file: File): Long {
    if (detectOuter(file) != OuterCompression.GZIP) return 0L
    return try {
        val len = file.length()
        if (len < 4L) 0L else RandomAccessFile(file, "r").use { r ->
            r.seek(len - 4L)
            r.readInt().toLong() and 0xFFFFFFFFL
        }
    } catch (_: Exception) {
        0L
    }
}

/**
 * tar 外层的压缩类型；识别**只看魔数**，与扩展名无关（`archive.tar.gz` 改名成 `.bin`
 * 也照样认得出）。brotli 是唯一例外 —— 它是**流式格式、没有魔数**，只能看扩展名。
 *
 * `.tar.bz2` / `.tar.zst` 早已能**压**出来（见 [ArchiveCompress]），却因为这里没认
 * 而读不回去 —— 能写不能读比压根不支持更像 bug（用户反馈 b10）。
 */
private enum class OuterCompression { NONE, GZIP, XZ, BZIP2, ZSTD, BROTLI }

/** 单文件压缩的外层后缀，用于把 `a.log.gz` 还原成条目名 `a.log`。 */
private const val BROTLI_EXT = "br"

/**
 * 剥掉外层压缩后的流；**不判断**里面是不是 tar（判断见 [openTarStream]）。
 *
 * `internal` 而非 `private`：**列目录与解出必须走同一条流构造**，否则一边读的是 `.tar` 原文、
 * 一边读的是 `.tar.gz` 解压流，同一个包会「列得出来、取不出来」。
 */
internal fun openPlainStream(file: File): InputStream = unwrap(file, detectOuter(file))

/** 按已判好的 [kind] 剥外层；[openPlainStream] 与 [openTarStream] 共用这一段，别各写各的。 */
private fun unwrap(file: File, kind: OuterCompression): InputStream {
    val raw: InputStream = BufferedInputStream(file.inputStream())
    return when (kind) {
        OuterCompression.GZIP -> GzipCompressorInputStream(raw, true)
        OuterCompression.XZ -> XZCompressorInputStream(raw, true)
        OuterCompression.BZIP2 -> BZip2CompressorInputStream(raw)
        OuterCompression.ZSTD -> ZstdCompressorInputStream(raw)
        OuterCompression.BROTLI -> BrotliCompressorInputStream(raw)
        OuterCompression.NONE -> raw
    }
}

/**
 * 打开「tar 系」文件，**先剥外层、再看里面到底是不是 tar**。
 *
 * @return 非 null = 可直接喂 `TarArchiveInputStream`；null = 里面不是 tar，而是
 *   **一整个被压过的文件**（`a.log.gz`），调用方按单文件处理，名字用 [plainInnerName]。
 *
 * 为什么用 ustar 魔数而不是「试着按 tar 读一遍」：喂给 tar 解析器的一段普通文本有概率
 * 解析出**乱七八糟还像模像样**的条目，用户看到的是一堆鬼名字；而 ustar/GNU 两种 tar
 * 在偏移 257 处都写着 `ustar`，认不出就当单文件，错也错得可解释。
 * **纯 `.tar`（没有外层）不嗅探** —— 那条路上现有行为一个字节都不改，V7 格式的老包
 * （1988 年前、没有 ustar 魔数）也就不会被误判成单文件。
 */
internal fun openTarStream(file: File): InputStream? {
    val kind = detectOuter(file)
    val inner = unwrap(file, kind)
    if (kind == OuterCompression.NONE) return inner
    return sniffTar(inner)
}

/**
 * 读出前 512 字节、**原样塞回去**，再看偏移 257 处是不是 `ustar`。
 *
 * 不用 `mark/reset`：压出来的流（gzip/xz/…）大多不支持 mark，或支持但缓冲区不够 512；
 * 用 [PushbackInputStream] 明确把字节推回去，与底层实现无关。
 *
 * 认不出时**由本函数关掉流**再返回 null，调用方不必再收拾 —— 免得每个调用点
 * 都要记住「返回 null 的情况下我已经持有半条流」。
 */
private fun sniffTar(inner: InputStream): InputStream? {
    val buf = ByteArray(512)
    var got = 0
    while (got < buf.size) {
        val n = inner.read(buf, got, buf.size - got)
        if (n < 0) break
        got += n
    }
    val isTar = got >= 262 &&
        buf[257] == 'u'.code.toByte() &&
        buf[258] == 's'.code.toByte() &&
        buf[259] == 't'.code.toByte() &&
        buf[260] == 'a'.code.toByte() &&
        buf[261] == 'r'.code.toByte()
    val out = PushbackInputStream(inner, 512)
    if (got > 0) out.unread(buf, 0, got)
    if (isTar) return out
    runCatching { out.close() }
    return null
}

private fun detectOuter(file: File): OuterCompression = try {
    file.inputStream().use { ins ->
        val h = ByteArray(6)
        var n = 0
        while (n < h.size) {
            val r = ins.read(h, n, h.size - n)
            if (r < 0) break
            n += r
        }
        when {
            n >= 2 && h[0] == 0x1F.toByte() && h[1] == 0x8B.toByte() -> OuterCompression.GZIP
            n >= 6 && h[0] == 0xFD.toByte() && h[1] == 0x37.toByte() &&
                h[2] == 0x7A.toByte() && h[3] == 0x58.toByte() -> OuterCompression.XZ
            // bzip2: "BZh"
            n >= 3 && h[0] == 0x42.toByte() && h[1] == 0x5A.toByte() &&
                h[2] == 0x68.toByte() -> OuterCompression.BZIP2
            // zstd: 28 B5 2F FD
            n >= 4 && h[0] == 0x28.toByte() && h[1] == 0xB5.toByte() &&
                h[2] == 0x2F.toByte() && h[3] == 0xFD.toByte() -> OuterCompression.ZSTD
            // brotli 没有魔数，只能看扩展名（见 [OuterCompression]）
            file.extension.equals(BROTLI_EXT, ignoreCase = true) -> OuterCompression.BROTLI
            else -> OuterCompression.NONE
        }
    }
} catch (_: Exception) {
    OuterCompression.NONE
}
