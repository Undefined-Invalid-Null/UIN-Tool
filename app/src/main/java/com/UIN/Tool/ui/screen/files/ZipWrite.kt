// app/src/main/java/com/UIN/Tool/ui/screen/files/ZipWrite.kt
package com.UIN.Tool.ui.screen.files

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.CompressionMethod
import com.UIN.Tool.R
import com.UIN.Tool.utils.AppLog
import com.UIN.Tool.utils.Str
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap

/**
 * 「加入已有压缩包」的**更新方式三选**（用户反馈 e15 **P1-10**）。
 *
 * 这三档是**表态**，不是提问：选定了就不再逐项弹冲突框 —— 那个询问是给
 * 「用户还没表过态」的场景准备的，模式本身就是态，再问一遍等于问两遍。
 * 想要「保留两者」那种逐项决定的，走的是撞名三选那条路（反馈 e17）。
 */
enum class UpdateMode {
    /** 只把**包里没有**的加进去，已存在的一条都不动。 */
    ADD_NEW,

    /** 换掉「源比包里新」的那些；源没变的一条都不动 —— 否则「更新」会把整包重压一遍。 */
    UPDATE,

    /** 同名一律覆盖。 */
    REPLACE_ALL
}

/**
 * zip **写入**引擎（M4b · §4.15 写能力）。
 *
 * 与读侧的分工，是本模块最容易踩的坑：
 * - **读**（列目录、解出）走 commons-compress —— 见 [ArchiveIndex] / [ArchiveExtract]；
 * - **写**走 zip4j —— N1 引入它的理由就是 zip 的加密读写只有它能做，且 `ProgressMonitor`
 *   自带当前文件/百分比/取消，可直接喂 `UnifiedProgressDialog`。
 *
 * 两侧**编码必须钉死成同一个**（见 [open]），否则会出现「列得出来、改不回去」。
 * 4.7-C 要做的 GBK/Big5 回退也必须**同时**作用于两侧。
 *
 * ## 写前保护 + 失败回滚（用户反馈清单第 10 项）
 *
 * 五个写操作（[rename] / [remove] / [create] / [addFiles] / [replaceEntry]）**都不直接碰活归档**：
 * 一律先在同目录拷一份 `.<名字>.staging` 暂存件，zip4j 的全部改动都发生在暂存件上，
 * 成功后 rename 覆盖原文件（POSIX rename(2) 对已有目标是原子替换），失败/取消只删暂存件 ——
 * 任何一条失败路径下，原归档都**逐字节**保持操作前的样子，也不留 `.staging`/`.rollback` 垃圾。
 * 同一个归档的写操作按 canonical path 加 [Mutex] 互斥，两个窗格同时改一个包不会互相踩。
 * 细节见 [withStagedArchive]。
 *
 * 加密包的口径：[rename]/[remove] 是原样拷字节、加密状态原样保留，直接放行；
 * [create]/[addFiles]/[replaceEntry] 会往包里**追加**内容，而这里拿不到密码
 * （zip4j 不给 password 就只会写出明文条目），所以先 [requireNoEncryption] 拦一道并明说是密码问题。
 *
 * 全部函数都在 `Dispatchers.IO` 上跑，抛出的异常交给调用方（`runOp`/VM 的 try）翻成提示 ——
 * **不静默吞**。
 */
object ZipWrite {

    private const val TAG = "ZipWrite"

    /**
     * 有归档被**真的改过**了（commit 之后）—— e15 **P1-11** 的钩子。
     *
     * 挂在这儿而不是各个调用点，是因为六条写路径（改名 / 删除 / 新建 / 加条目 /
     * 追加 / 编辑器写回）**全部**收在 [withStagedArchive] 一个口子里 ——
     * 逐个调用点去报，迟早漏掉一条，而漏掉的那条往往正是用户最常走的那条。
     * 空操作（`commitIf()` 为假）不触发：没改东西却报「签名失效」，是在吓唬人。
     */
    var onArchiveWritten: ((archive: File) -> Unit)? = null

    /** 条目名编码：与 commons-compress `ZipFile` 的默认值一致，两边对同一批字节给出同一字符串。 */
    private val NAME_CHARSET = StandardCharsets.UTF_8

    private val EMPTY = ByteArray(0)

    /** 进度上报节流间隔（ms）。与 FileOps / ArchiveExtract 同值，免得三处刷新频率不一。 */
    private const val REPORT_INTERVAL_MS = 60L

    /**
     * 打开待写 zip。
     *
     * `setCharset` 必须显式设 —— zip4j 的默认值跟构造路径走，不显式钉死就等于把一致性
     * 押在「恰好相同」上；而读侧 commons-compress 默认就是 UTF-8。
     */
    private fun open(archive: File): ZipFile = ZipFile(archive).apply { setCharset(NAME_CHARSET) }

    /** 该条目（文件或目录，内部路径）是否已存在。 */
    suspend fun exists(archive: File, internalPath: String): Boolean =
        withContext(Dispatchers.IO) {
            open(archive).use { z -> z.getFileHeader(stripSlash(internalPath)) != null }
        }

    /**
     * 归档内**同目录唯一**的落点名：`a/b.txt` → `a/b (1).txt` → `a/b (2).txt`…
     *
     * 与 `FileOps.uniqueSibling` 同一套序号规则，但比对的是**条目名**而不是 `File.exists()` ——
     * 拿 `File("x.zip!/a/b.txt").exists()` 去判永远是 false，序号根本不会往上走。
     * 目录按「同名目录」比（带尾斜杠的那条算同一个）。
     */
    suspend fun uniqueEntryName(archive: File, internalPath: String): String =
        withContext(Dispatchers.IO) {
            val wanted = stripSlash(internalPath)
            // 先把「已占用的名字集合」读出来，再做纯字符串运算 —— 别把整段塞进 use 的 lambda：
            // 那里所有出口都是 return@use，末尾又是 while(true)（类型 Nothing），
            // Kotlin 会把 lambda 的返回类型推断成 Any，函数声明的 String 对不上。
            val taken = open(archive).use { z ->
                z.fileHeaders.mapTo(HashSet<String>()) { stripSlash(it.fileName) }
            }
            if (wanted !in taken) return@withContext wanted
            val slash = wanted.lastIndexOf('/')
            val dir = if (slash < 0) "" else wanted.substring(0, slash + 1)
            val base = if (slash < 0) wanted else wanted.substring(slash + 1)
            val dot = base.lastIndexOf('.')
            // 隐藏文件（`.gitignore`）与无扩展名都要算得对：没有点、或点在首字符 → 整段当主干
            val stem: String
            val ext: String
            if (dot > 0) {
                stem = base.substring(0, dot)
                ext = base.substring(dot)
            } else {
                stem = base
                ext = ""
            }
            // 找第一个没被占用的 `stem (n).ext`。
            // 不写成 `while (true) { if (c !in taken) return@withContext c; i++ }` ——
            // while 是**语句**，放在 lambda 末尾时整块类型是 Unit，
            // 而函数声明返回 String，编译器直接报「expected String, actual Unit」。
            // 写成「循环到没被占用为止，最后把候选交出去」则末尾表达式天然是 String。
            var i = 1
            var candidate = "$dir$stem ($i)$ext"
            while (candidate in taken) {
                i++
                candidate = "$dir$stem ($i)$ext"
            }
            candidate
        }

    /**
     * 重命名条目。**目标名已存在时不覆盖**（返回 false），与 4.5「重名不自动吃掉同名文件」一致 ——
     * 由调用方先把建议名算好、回填进对话框，用户确认后才动。
     */
    suspend fun rename(archive: File, oldInternal: String, newInternal: String): Boolean =
        withContext(Dispatchers.IO) {
            val from = stripSlash(oldInternal)
            val to = stripSlash(newInternal)
            // 没真改东西就别回写整包（commitIf=false 时暂存件直接丢弃）
            var changed = false
            withStagedArchive(archive, commitIf = { changed }) { work ->
                open(work).use { z ->
                    // 目录条目在本包里是带尾斜杠写的（`dir/`），而调用方传进来的
                    // 内部路径没有尾斜杠 —— 只按 `from` 查会得到 null，于是「改文件夹名」
                    // 永远弹重名/失败。两种形态都认。
                    val header = z.getFileHeader(from) ?: z.getFileHeader("$from/")
                        ?: throw IOException("entry not found: $from")
                    if (from == to) return@use true
                    // 同名文件或同名目录都算撞名，不覆盖
                    if (z.getFileHeader(to) != null || z.getFileHeader("$to/") != null) {
                        return@use false
                    }
                    // zip4j 的 renameFile **按前缀匹配**：callee 内部拿 header 的完整名字当 key，
                    // 把 `startsWith(key)` 的条目全部改名。对文件来说 `report.txt` 会连带
                    // 把同目录的 `report.txt.bak` 改成 `summary.txt.bak` —— 静默改掉无关条目。
                    // 目录正常（key 以 `/` 结尾）时子条目正是要一起改的，不算碰撞。
                    val key = header.fileName
                    val dirPrefix = if (key.endsWith("/")) key else "$key/"
                    val collision = z.fileHeaders.any { h ->
                        val n = h.fileName
                        n != key && n.startsWith(key) &&
                            !(header.isDirectory && n.startsWith(dirPrefix))
                    }
                    if (collision) {
                        // 目录 / 加密条目不做「复制+删」回退：前者数据量大，后者会掉加密。
                        // 宁可报重名让用户先处理，也不静默改掉别的条目。
                        if (header.isDirectory || header.isEncrypted) return@use false
                        // 安全回退：内容复制出来 → 用新名字追加 → 按**精确条目名**删旧的。
                        val tmp = File(
                            work.absoluteFile.parentFile,
                            ".uin_rename_${System.nanoTime()}"
                        )
                        try {
                            z.getInputStream(header).use { ins ->
                                tmp.outputStream().use { outs -> ins.copyTo(outs, 64 * 1024) }
                            }
                            val p = ZipParameters().apply {
                                setFileNameInZip(to)
                                setCompressionMethod(
                                    header.compressionMethod ?: CompressionMethod.DEFLATE
                                )
                                if (header.lastModifiedTimeEpoch > 0) {
                                    setLastModifiedFileTime(header.lastModifiedTimeEpoch)
                                }
                            }
                            tmp.inputStream().use { z.addStream(it, p) }
                            z.removeFile(key)
                        } finally {
                            runCatching { tmp.delete() }
                        }
                        changed = true
                        return@use true
                    }
                    // 无前缀碰撞时才交给库；目录的子树由它按 `dir/` 前缀一起改。
                    z.renameFile(header, to)
                    changed = true
                    true
                }
            }
        }

    /**
     * 删除条目（[internals] 里可以是目录）。
     *
     * 目录要**先把它下面的条目全部展开**再一起删：zip4j 只有 `removeFile`/`removeFiles`、
     * 没有 `removeFolder`，而每删一条就把整个包重写一遍 —— 一个 200 项的文件夹逐条删要重写 200 次。
     */
    suspend fun remove(archive: File, internals: List<String>): Boolean =
        withContext(Dispatchers.IO) {
            var changed = false
            withStagedArchive(archive, commitIf = { changed }) { work ->
                open(work).use { z ->
                    val all = z.fileHeaders.map { it.fileName }
                    val targets = LinkedHashSet<String>()
                    for (raw in internals) {
                        val name = stripSlash(raw)
                        if (name.isEmpty()) continue
                        if (name in all) targets += name
                        val prefix = "$name/"
                        for (n in all) if (n.startsWith(prefix)) targets += n
                    }
                    if (targets.isEmpty()) return@use false
                    z.removeFiles(targets.toList())
                    changed = true
                    true
                }
            }
        }

    /**
     * 新建条目：`isDir` 时落一条**以 `/` 结尾**的空目录条目，否则落一个空文件。
     *
     * 目录名带尾斜杠是 zip 的通用约定，读侧 commons-compress 的 `ZipArchiveEntry.isDirectory()`
     * 正是按「名字以 `/` 结尾」判的 —— 所以这么写出去，读回来必然还是个目录。
     *
     * @return false = 同名已存在（调用方先 [uniqueEntryName]）
     */
    suspend fun create(archive: File, internalPath: String, isDir: Boolean): Boolean =
        withContext(Dispatchers.IO) {
            val name = stripSlash(internalPath)
            // 注意标签：这一句在 use **之前**，只能从 withContext 的 lambda 返回。
            if (name.isEmpty()) return@withContext false
            var changed = false
            withStagedArchive(archive, commitIf = { changed }) { work ->
                requireNoEncryption(work, archive, "create an entry")
                open(work).use { z ->
                    if (z.getFileHeader(name) != null) return@use false
                    val params = ZipParameters().apply {
                        setFileNameInZip(if (isDir) "$name/" else name)
                        setCompressionMethod(CompressionMethod.DEFLATE)
                        setOverrideExistingFilesInZip(false)
                    }
                    z.addStream(ByteArrayInputStream(EMPTY), params)
                    changed = true
                    true
                }
            }
        }

    /**
     * 把**真实文件**加进 zip（§4.15「复制进归档」/ §4.7）。
     *
     * 落点是 [internalDir]（归档内目录，根用空串），目录会递归展开、并**先落一条以 `/`
     * 结尾的目录条目** —— 否则解出来只有一堆孤立文件，路径全靠条目名里的斜杠暗示。
     *
     * ## 为什么用 `addStream` 逐条追加，而不是 `addFiles` 整批
     *
     * 两个理由，都是查过 2.11.6 的 jar 得来的，不是印象：
     * ① **`addStream` 是追加**，不是整包重写 —— `AddStreamToZipTask` 拿
     *    `SplitOutputStream(file, offset)` 在既有包的末尾接着写，所以逐条调用不会变成
     *    O(条目数 × 包大小)。
     * ② **`addFiles` 表达不了我们要的命名**：它自己按文件路径推条目名，只能靠
     *    `rootFolderNameInZip` 加一层统一前缀，无法对单个条目改名 —— 而撞名时的
     *    「保留两者」必须逐条改名（`name (1).ext`）。
     *
     * ## 撞名只问顶层
     *
     * 冲突清单按 [sources] 的**顶层项**问（与真实目录复制同一套 UX）：复制一个含上千文件的
     * 目录时，若把每个嵌套文件都列进冲突框，用户根本没法看。顶层选了「保留两者」，整个子树
     * 都跟着换名；选了「替换」，子树内同名的一并覆盖。
     *
     * @return null = 用户点外面取消整批
     */
    suspend fun addFiles(
        archive: File,
        internalDir: String,
        sources: List<File>,
        /** 压缩级别，与压缩框同一套 `FilePrefs.LEVEL_*`（用户反馈 e15 **P1-10**「级别」）。 */
        level: String = FilePrefs.LEVEL_DEFAULT,
        /**
         * 更新模式（用户反馈 e15 **P1-10**「更新模式三选」）。
         *
         * `null` = 保持原样：撞名时逐项问三选（反馈 e17「追加进已有包」走这条）；
         * 非 null = 用户已明确选定，**不再问**，按模式机械判定。
         */
        updateMode: UpdateMode? = null,
        /**
         * 进度框标题用哪种动词。往**已存在的包**里加条目既可能是「复制进来」，
         * 也可能是压缩框的「加入已有压缩包」—— 只有调用方知道该说哪句。
         * g17：原先这里写死 COPY，压缩框追加时标题一直显示「正在复制」。
         */
        kind: OpKind = OpKind.COPY,
        onProgress: (OpProgress) -> Unit,
        onConflict: suspend (List<ConflictItem>) -> ConflictDecision?
    ): OpResult? = withContext(Dispatchers.IO) {
        if (sources.isEmpty()) return@withContext OpResult(false, 0, 0, null)

        val dirPrefix = stripSlash(internalDir).let { if (it.isEmpty()) "" else "$it/" }

        // 1) 展开成「源文件 → 归档内条目名」。目录本身也占一条（带尾斜杠）。
        data class Item(val src: File, val entry: String, val isDir: Boolean)

        val items = ArrayList<Item>()
        fun walk(src: File, entryBase: String) {
            if (src.isDirectory) {
                items += Item(src, entryBase, isDir = true)
                val kids = src.listFiles() ?: return
                for (k in kids) walk(k, "$entryBase/${k.name}")
            } else {
                items += Item(src, entryBase, isDir = false)
            }
        }
        for (s in sources) {
            if (s.name.isEmpty()) continue
            walk(s, dirPrefix + s.name)
        }
        if (items.isEmpty()) return@withContext OpResult(false, 0, 0, null)

        val totalBytes = items.filter { !it.isDir }.sumOf { it.src.length() }.coerceAtLeast(0L)
        val topNames = sources.map { it.name }

        var success = 0
        var failed = 0
        var skipped = 0
        /**
         * 实际写进包的**条目**数（目录条目也算一条）—— 只用来决定暂存件要不要提交，
         * 与对外的 `success`/`failed` 不是一个量纲：
         * 对外按**顶层源**计数（进度框的 totalCount 也是 sources.size），
         * 否则「移动 1 个文件夹」会报成「已复制 37 项」，删除源的判定也无从下手。
         */
        var written = 0
        var firstError: String? = null
        var doneBytes = 0L
        var lastReport = 0L
        var cancelled = false
        var remembered: ConflictPolicy? = null

        fun report(doneCount: Int, current: String, finished: Boolean = false) {
            val now = System.currentTimeMillis()
            if (!finished && now - lastReport < REPORT_INTERVAL_MS) return
            lastReport = now
            onProgress(
                OpProgress(
                    kind = kind,
                    totalCount = sources.size,
                    doneCount = doneCount,
                    totalBytes = totalBytes,
                    doneBytes = doneBytes,
                    currentName = if (finished) "" else current,
                    targetPath = archive.path,
                    finished = finished,
                    successCount = success,
                    failedCount = failed,
                    firstError = firstError
                )
            )
        }

        // g17：先报一条 0/N —— 暂存件准备、撞名询问、首个大文件写入都可能耗上一阵，
        // 不先推一条，进度框要等到第一项完成才出现（看起来像点了没反应）。
        report(0, sources.firstOrNull()?.name.orEmpty())

        val mode = updateMode

        // 2) 顶层撞名问一次（懒问 + 「对剩余全部应用」）。
        // 返回 null = 用户取消。zip 是**暂存件**上那个已打开的包（见下方 withStagedArchive）。
        suspend fun policyForTop(zip: ZipFile, index: Int): ConflictPolicy? {
            remembered?.let { return it }

            // 更新模式（P1-10）：用户**已经表过态**，模式本身就是态 ——
            // 再弹一次冲突框等于问两遍。没撞名的照旧直接放行。
            if (mode != null) {
                val name = sources[index].name
                if (topNames[index].isEmpty() || !entryExists(zip, dirPrefix + name)) {
                    return ConflictPolicy.REPLACE
                }
                return when (mode) {
                    // 只添加新的：已存在的整棵跳过。目录也返回 SKIP，但下面 `skipTop`
                    // 对目录恒为 false，所以子项仍会走到 —— 靠逐条的「已存在就跳过」
                    // 把目录里**新增**的那几条补进去。
                    UpdateMode.ADD_NEW -> ConflictPolicy.SKIP
                    // 同名一律覆盖
                    UpdateMode.REPLACE_ALL -> ConflictPolicy.REPLACE
                    // 更新：只换「源比包里新的」。源没变就别动 —— 否则「更新」等于
                    // 把整个包重写一遍，改完发现一个字节都没变。
                    UpdateMode.UPDATE -> {
                        val src = sources[index]
                        if (src.isDirectory) {
                            ConflictPolicy.SKIP
                        } else {
                            val header = runCatching {
                                zip.getFileHeader(stripSlash(dirPrefix + name))
                            }.getOrNull()
                            if (header != null && src.lastModified() > header.lastModifiedTimeEpoch) {
                                ConflictPolicy.REPLACE
                            } else {
                                ConflictPolicy.SKIP
                            }
                        }
                    }
                }
            }
            val batch = ArrayList<ConflictItem>()
            for (j in index until sources.size) {
                val name = sources[j].name
                if (!topNames[j].isEmpty() && entryExists(zip, dirPrefix + name)) {
                    batch.add(
                        ConflictItem(
                            sourcePath = sources[j].path,
                            targetPath = "${archive.path}!/${dirPrefix}$name",
                            sourceSize = if (sources[j].isFile) sources[j].length() else -1L,
                            targetSize = -1L,
                            sourceModified = sources[j].lastModified(),
                            targetModified = 0L
                        )
                    )
                }
            }
            if (batch.isEmpty()) return ConflictPolicy.REPLACE
            val decision = onConflict(batch) ?: return null
            if (decision.applyToAll) remembered = decision.policy
            return decision.policy
        }

        // 3) 全部改动都在**同目录暂存件**上做，成功才 rename 覆盖原包（见 [withStagedArchive]）。
        //    commitIf = written > 0：一条都没加进去（整批失败 / 一进来就取消）时原包一个字节都不动。
        withStagedArchive(archive, commitIf = { written > 0 }) { work ->
            // 往加密包里追加 = 新条目掉加密（zip4j 只认 password，这里没有）—— 动手前先拒
            requireNoEncryption(work, archive, "add files")
            try {
                // 整个批次共用一个已打开的 zip —— 每条都重开等于每条都重建一次中央目录。
                val zip = open(work)
                try {
                    for ((index, src) in sources.withIndex()) {
                        val policy = policyForTop(zip, index) ?: run { cancelled = true; break }
                        val base = dirPrefix + src.name
                        // 「保留两者」要把顶层名改掉，子树跟着它走
                        val renamedBase = if (policy == ConflictPolicy.KEEP_BOTH) {
                            uniqueEntryNameLocked(archive, zip, base)
                        } else {
                            base
                        }
                        val skipTop = policy == ConflictPolicy.SKIP &&
                            entryExists(zip, base) &&
                            // 用户在三选里明确选了「跳过」：整棵子树都不动。
                            // 更新模式（ADD_NEW/UPDATE）对目录也返回 SKIP，但那是
                            // 「合并进去、只补新增/只换更新的」—— 必须继续下钻。
                            (mode == null || !src.isDirectory)

                        // 顶层源的结局：有任一子项失败 = 失败；有任一子项被跳过 = 跳过
                        // （跳过既不成功也不失败 —— 源必须原样留着，尤其「移动进包」时）。
                        var sourceFailed = false
                        var sourceSkipped = false

                        var idx = 0
                        while (idx < items.size) {
                            val it = items[idx]
                            // 只处理属于当前顶层的那一段
                            if (it.entry != base && !it.entry.startsWith("$base/")) {
                                idx++
                                continue
                            }
                            idx++
                            currentCoroutineContext().ensureActive()

                            val entryName = when {
                                it.entry == base -> renamedBase
                                else -> renamedBase + it.entry.removePrefix(base)
                            }

                            // 顶层跳过 = 整棵子树都不加
                            if (skipTop) {
                                sourceSkipped = true
                                continue
                            }

                            try {
                                if (it.isDir) {
                                    // 目录条目带尾斜杠，读侧 isDirectory() 正是按这个判的
                                    if (zip.getFileHeader(stripSlash(entryName)) == null) {
                                        val p = ZipParameters().apply {
                                            setFileNameInZip("$entryName/")
                                            setCompressionMethod(CompressionMethod.STORE)
                                            setOverrideExistingFilesInZip(false)
                                        }
                                        zip.addStream(ByteArrayInputStream(EMPTY), p)
                                    }
                                    written++
                                } else {
                                    // 更新模式的**逐条**判定 —— 顶层是目录时才会走到
                                    // 这里（顶层是文件时 policyForTop 已定了整棵的去留）。
                                    val header = runCatching {
                                        zip.getFileHeader(stripSlash(entryName))
                                    }.getOrNull()
                                    if (header != null && mode != null) {
                                        val skip = when (mode) {
                                            // 包里已有 → 一条都不动
                                            UpdateMode.ADD_NEW -> true
                                            // 源没比包里新 → 不动（否则「更新」= 整包重压）
                                            UpdateMode.UPDATE -> it.src.lastModified() <= header.lastModifiedTimeEpoch
                                            UpdateMode.REPLACE_ALL -> false
                                        }
                                        if (skip) {
                                            sourceSkipped = true
                                            continue
                                        }
                                    }
                                    it.src.inputStream().buffered().use { ins ->
                                        val p = ZipParameters().apply {
                                            setFileNameInZip(entryName)
                                            setCompressionMethod(CompressionMethod.DEFLATE)
                                            // 级别（P1-10）：这里原先**压根没设**，等于恒用
                                            // zip4j 默认 —— 用户在框里选了「最快」也看不出差别。
                                            setCompressionLevel(ArchiveCompress.zipLevelOf(level))
                                            setOverrideExistingFilesInZip(policy == ConflictPolicy.REPLACE)
                                            if (it.src.lastModified() > 0) {
                                                setLastModifiedFileTime(it.src.lastModified())
                                            }
                                        }
                                        zip.addStream(ins, p)
                                    }
                                    doneBytes += it.src.length()
                                    written++
                                }
                            } catch (e: Exception) {
                                if (e is kotlinx.coroutines.CancellationException) throw e
                                sourceFailed = true
                                if (firstError == null) firstError = e.message
                            }
                            report(index, it.src.name)
                        }
                        // 收口一个顶层源的结局。全跳过 -> 既不算成功也不算失败：
                        // 「移动进包」时源必须原地保留（用户选的就是「不动」）。
                        when {
                            sourceFailed -> failed++
                            sourceSkipped -> skipped++
                            else -> success++
                        }
                    }
                } finally {
                    zip.close()
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                // 打不开/包损坏 —— 整批失败，错误文案交给上层。**不是用户取消**：
                // 原来这里报 cancelled=true，进度框直接走「已取消」分支，用户看不到失败原因。
                return@withStagedArchive OpResult(false, success, failed.coerceAtLeast(1), e.message, skipped)
            }

            report(sources.size, "", finished = true)
            OpResult(cancelled, success, failed, firstError, skipped)
        }
    }

    /**
     * 与 [uniqueEntryName] 同样的「撞名就加 ` (n)`」，但**复用调用方已打开的 [zip]**。
     *
     * 单独留一份是为了避免在批量加文件时反复重开包：每开一次都要重建中央目录。
     */
    private fun uniqueEntryNameLocked(archive: File, zip: ZipFile, entry: String): String {
        val name = stripSlash(entry)
        val dot = name.lastIndexOf('/')
        val dir = if (dot >= 0) name.substring(0, dot + 1) else ""
        val file = if (dot >= 0) name.substring(dot + 1) else name
        val extDot = file.lastIndexOf('.')
        val stem = if (extDot > 0) file.substring(0, extDot) else file
        val ext = if (extDot > 0) file.substring(extDot) else ""
        var n = 1
        while (zip.getFileHeader("$dir$stem ($n)$ext") != null) n++
        return "$dir$stem ($n)$ext"
    }

    /**
     * 用 [data] **覆盖**一个已有条目（编辑器写回归档，4.15「保存写回」）。
     *
     * `setOverrideExistingFilesInZip(true)` 不是可有可无的开关 —— zip4j 的
     * `AddStreamToZipTask` 继承 `AbstractAddFileToZipTask`，它靠这个标志决定要不要先
     * `removeFileIfExists`；不设就是「加不进去」或者变成同名两条。
     *
     * @param modified 写回后希望保留的修改时间；<=0 表示不设（用 zip4j 默认）
     * @throws IOException 条目不存在 / 包不可写
     */
    suspend fun replaceEntry(
        archive: File,
        internalPath: String,
        data: InputStream,
        modified: Long
    ) = withContext(Dispatchers.IO) {
        val name = stripSlash(internalPath)
        // 与其余四个写操作**同一个口径**：也走暂存件。编辑器保存写回是这条链上最怕
        // 半路失败的一环 —— 直接往活包上写，写到一半断电就是整包坏了，而用户此刻
        // 恰恰正以为「保存成功」。失败时原包逐字节原样，临时副本仍在，改动不丢。
        withStagedArchive(archive, commitIf = { true }) { work ->
            open(work).use { z ->
                if (z.getFileHeader(name) == null) {
                    throw IOException("entry not found: $name")
                }
                val params = ZipParameters().apply {
                    setFileNameInZip(name)
                    setCompressionMethod(CompressionMethod.DEFLATE)
                    setOverrideExistingFilesInZip(true)
                    if (modified > 0) setLastModifiedFileTime(modified)
                }
                z.addStream(data, params)
            }
        }
    }

    /**
     * [archive] 里的 [internalPath] 与磁盘上的 [candidate] **逐字节是否完全一致**。
     *
     * 用户反馈 e15 **P1-6**「写回归档跳过未改动内容」：`replaceEntry` 的代价是
     * **把整包复制一份再原子换回去** —— 为一个没变的字节搬 500MB 既白费，
     * 还平白多一次断电窗口。所以写回前先问这一句。
     *
     * 两段式：**先比长度**（真改动大多改了长度，一次都不用解压就短路），
     * 长度对得上才逐块比。代价是「改动了但长度没变」时多解压一遍 ——
     * 拿它换掉一次整包复制，怎么算都划算。
     *
     * 任何一步出错都返回 false（= 当作有改动，照常写回）：这是一层**优化**，
     * 不该因为读不动包就拦住一次本来能成功的写回。
     *
     * @return true = 内容完全一致，可以一动不动
     */
    suspend fun entryEquals(archive: File, internalPath: String, candidate: File): Boolean =
        withContext(Dispatchers.IO) {
            // 把 buf 填满（或读到头）——两边都按同样的节奏读，才好按块比
            fun fill(inp: InputStream, buf: ByteArray): Int {
                var off = 0
                while (off < buf.size) {
                    val n = inp.read(buf, off, buf.size - off)
                    if (n < 0) break
                    off += n
                }
                return off
            }

            try {
                if (!candidate.isFile) return@withContext false
                val name = stripSlash(internalPath)
                open(archive).use { z ->
                    val header = z.getFileHeader(name) ?: return@withContext false
                    val declared = header.uncompressedSize
                    if (declared >= 0 && declared != candidate.length()) return@withContext false
                    z.getInputStream(header).use { a ->
                        candidate.inputStream().use { b ->
                            val ba = ByteArray(64 * 1024)
                            val bb = ByteArray(64 * 1024)
                            while (true) {
                                val na = fill(a, ba)
                                val nb = fill(b, bb)
                                if (na != nb) return@withContext false
                                if (na == 0) return@withContext true
                                var i = 0
                                while (i < na) {
                                    if (ba[i] != bb[i]) return@withContext false
                                    i++
                                }
                            }
                        }
                    }
                }
                false
            } catch (_: Exception) {
                false
            }
        }

    /** 读单个条目的全部字节（编辑器要先解成临时副本才好改；§4.15）。 */
    suspend fun readEntry(archive: File, internalPath: String): ByteArray =
        withContext(Dispatchers.IO) {
            val name = stripSlash(internalPath)
            open(archive).use { z ->
                val header = z.getFileHeader(name)
                    ?: throw IOException("entry not found: $name")
                z.getInputStream(header).use { it.readBytes() }
            }
        }

    /**
     * 把条目**流式**落到磁盘文件（编辑器「另存归档内条目为副本」的主路径）。
     *
     * 与 [readEntry] 的区别只在内存：那个整条读进 `ByteArray`，500MB 的条目直接 OOM。
     * 编辑器打开归档内文本前要先落一份真实副本，条目大小完全不受控，必须流式。
     *
     * 写临时名 + 成功后改名：中途抛异常（条目不存在 / 归档损坏）不会留半截文件，
     * 否则下次打开会把半截内容当成原文件编辑。
     */
    suspend fun copyEntryTo(archive: File, internalPath: String, out: File) =
        withContext(Dispatchers.IO) {
            val name = stripSlash(internalPath)
            val tmp = File(out.parentFile, out.name + ".part")
            open(archive).use { z ->
                val header = z.getFileHeader(name)
                    ?: throw IOException("entry not found: $name")
                z.getInputStream(header).use { input ->
                    tmp.outputStream().use { outp ->
                        input.copyTo(outp, DEFAULT_BUFFER_SIZE)
                    }
                }
            }
            // 覆盖已存在的旧副本：renameTo 在同目录下对已有目标是可行的，先删更稳。
            if (out.exists() && !out.delete()) throw IOException("cannot replace: $out")
            if (!tmp.renameTo(out)) throw IOException("cannot rename: $tmp -> $out")
        }

    // ==================== 内部 ====================

    /** 同一个归档的写操作互斥；键是 canonical path，两个窗格改同一个包不会互相踩。 */
    private val LOCKS = ConcurrentHashMap<String, Mutex>()

    /**
     * **写前暂存 + 失败回滚**（类注释「写前保护 + 失败回滚」的落点）。
     *
     * 一句话：五个写操作没有一个直接碰活归档 —— 先在**同目录**拷一份 `.名字.staging`，
     * 全部改动都落在暂存件上，成功才把它换到原位置；任何一条失败路径（异常 / 用户取消 /
     * 拷贝中断 / 磁盘满）都只删暂存件，原包**逐字节**保持操作前的样子，也**不留垃圾**。
     *
     * 为什么必须同目录：rename(2) 只在**同一个文件系统**内才是原子替换。换到别的卷上，
     * `renameTo` 会直接失败，或者（更糟）退化成「先删原包再拷」—— 中途断电就是整个包没了。
     *
     * 提交分两步，第二步是兜底（[commitStaged]）：
     * 1. `staging.renameTo(archive)` —— POSIX 下对已有目标就是原子替换，最理想；
     * 2. 部分文件系统（FAT/sdcardfs，即外置 SD 卡）拒绝 rename 到已存在目标 —— 退成
     *    「原包让位成 `.名字.rollback` → 暂存件顶上 → 扫尾」，且**任何一步失败都把原包放回去**。
     *    顺序换成「先删原包」的话，两步之间失败 = 数据没了，这正是 `.rollback` 存在的意义。
     *
     * @param commitIf 块跑完**之后**再问一次「这次到底改了东西没有」。没改（重命名成了原名、
     *   删除的条目压根不在包里、整批一条都没加进去）就直接丢掉暂存件、一个字节都不动 ——
     *   否则一个 2GB 的包会因为一次空操作被整个重写一遍。
     * @param work 传给块的**暂存件**路径：调用方所有 zip4j 操作都打在它身上，不是原包。
     */
    private suspend fun <T> withStagedArchive(
        archive: File,
        commitIf: () -> Boolean,
        block: suspend (File) -> T
    ): T {
        if (!archive.isFile) throw IOException("archive not found: $archive")
        val dir = archive.parentFile ?: throw IOException("no parent dir: $archive")
        val key = try {
            archive.canonicalPath
        } catch (e: Exception) {
            archive.absolutePath
        }
        val mutex = LOCKS.computeIfAbsent(key) { Mutex() }
        return mutex.withLock {
            val staging = File(dir, ".${archive.name}.staging")
            // 上次写到一半进程被杀会留下脏暂存件；先清掉再开新的，否则改的是旧内容
            if (staging.exists() && !staging.delete()) {
                throw IOException("cannot clear stale staging: $staging")
            }
            try {
                copyForEdit(archive, staging)
                val result = block(staging)
                if (commitIf()) {
                    // 反馈 e18：写回前留一份可回滚副本（设置里可关）。**必须在 commit 之前** —
                    // commit 之后原包已经换成新内容了，这会儿再拷就只是新包的复制品。
                    makeBackup(archive)
                    commitStaged(staging, archive)
                    // 内容已经换过了才报；暂存件没 commit 就报，是在说谎
                    onArchiveWritten?.invoke(archive)
                } else {
                    runCatching { staging.delete() }
                }
                result
            } catch (t: Throwable) {
                // 取消也走这里（CancellationException 是 Throwable 的子类）—— 半成品必须清干净，
                // 否则下次写会撞上一个脏暂存件；`runCatching` 只兜「删不掉」，不吞原异常
                runCatching { staging.delete() }
                throw t
            }
        }
    }

    /** 逐字节拷出暂存件。取消判在每 64KB 之间（一次拷贝不做任何改动，所以只拷不改）。 */
    private suspend fun copyForEdit(src: File, dst: File) {
        try {
            src.inputStream().use { input ->
                FileOutputStream(dst).use { output ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                    }
                }
            }
        } catch (t: Throwable) {
            runCatching { dst.delete() }
            throw t
        }
    }

    /**
     * 写回前的 `.bak` 副本（反馈 e18：「新增 .bak 备份（设置里加开关）」）。
     *
     * - 落点 = 同目录 `<原名>.bak`，**覆盖上一次的**（只留最近一份，免得把磁盘吃光）。
     * - 必须在 `commitStaged` **之前**调用：那之后原包已经是新内容了。
     * - 备份失败**不阻断**写回 —— 此刻原包一个字节都还没动，没有数据损失，
     *   少一层保险总比把这次改动整个拦下来强。
     * - 取消必须原样抛出：吞了就等于「用户点了取消，却照样提交」。
     */
    private fun makeBackup(archive: File) {
        if (!FilePrefs.archiveBackup()) return
        val bak = File(archive.parentFile, "${archive.name}.bak")
        try {
            archive.copyTo(bak, overwrite = true)
        } catch (t: Throwable) {
            runCatching { bak.delete() }   // 拷到一半的残件比没有更误导
            if (t is kotlinx.coroutines.CancellationException) throw t
        }
    }

    /**
     * 把暂存件换到原位置。**顺序不可对调**，对调就没有回滚可言。
     *
     * 第 1 步是常态（同一文件系统内 rename 覆盖已有目标 = 原子替换）；只有它失败时才走第 2 步 ——
     * 外置 SD 卡上的 FAT、以及部分 sdcardfs 实现不接受「覆盖已有文件」的 rename。
     */
    private fun commitStaged(staging: File, archive: File) {
        if (!staging.exists()) throw IOException("staging disappeared: $staging")
        if (staging.renameTo(archive)) return

        val rollback = File(archive.parentFile, ".${archive.name}.rollback")
        if (rollback.exists() && !rollback.delete()) {
            throw IOException(Str.get(R.string.files_archive_commit_failed, rollback.name))
        }
        if (!archive.renameTo(rollback)) {
            // 原包还在自己那儿 —— 现状没变，只报失败、别动暂存件以外的东西
            throw IOException(Str.get(R.string.files_archive_commit_failed, archive.name))
        }
        if (!staging.renameTo(archive)) {
            // 顶不上去：先把原包放回去。放回去了 = 一切照旧，报失败即可
            if (rollback.renameTo(archive)) {
                throw IOException(Str.get(R.string.files_archive_commit_failed, archive.name))
            }
            // 极坏的一路：原包改不回去。**不能**把暂存件删了了事 ——
            // 它是这次唯一的完整副本，路径必须报给用户让他自己救
            throw IOException(Str.get(R.string.files_archive_commit_failed, rollback.path))
        }
        rollback.delete()
    }

    /**
     * 往包里**追加**内容前拦一道加密（类注释「加密包的口径」）。
     *
     * zip4j 只认 password：不给 password，写进去的新条目就是明文 —— 一个包从此
     * 「一半加密一半明文」，比直接拒绝更难收拾。而这里拿不到密码（读写分离，读侧才可能问），
     * 所以**改名 / 删除照常放行，追加一律拒绝**并把原因说明白。
     *
     * 打不开就不在这里拦：让后面真正的 `open` 再抛一次，报错才带得上「包坏了」这个上下文。
     */
    private fun requireNoEncryption(work: File, archive: File, action: String) {
        val encrypted = try {
            open(work).use { z -> z.fileHeaders.any { it.isEncrypted } }
        } catch (_: Exception) {
            false
        }
        if (!encrypted) return
        AppLog.w(TAG, "refuse '$action' on encrypted archive: $archive")
        throw IOException(Str.get(R.string.files_archive_encrypted_edit))
    }

    /**
     * 归档里是否已有这个条目（含目录）。**只比条目名**，不碰文件系统 ——
     * 对 `x.zip!/a` 这样的虚拟路径 `File.exists()` 恒为 false，拿它判会永远放行。
     */
    private fun entryExists(zip: ZipFile, entry: String): Boolean = try {
        zip.getFileHeader(stripSlash(entry)) != null
    } catch (_: Exception) {
        false
    }

    /**
     * 去尾斜杠：zip4j 按**精确字符串**查条目，而我们的 [ArchiveEntry.path] 已经去过了尾斜杠
     * （`scanZip` 里 `removeSuffix("/")`），带尾斜杠去查会查不到。
     */
    private fun stripSlash(p: String): String = p.removeSuffix("/")
}
