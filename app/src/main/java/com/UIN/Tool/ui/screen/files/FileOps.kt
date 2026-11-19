// app/src/main/java/com/UIN/Tool/ui/screen/files/FileOps.kt
package com.UIN.Tool.ui.screen.files

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import com.UIN.Tool.R
import com.UIN.Tool.utils.Str
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

/** 文件操作类型：进度对话框标题、统计口径、完成 toast 都按它来（4.12）。 */
enum class OpKind {
    COPY,
    MOVE,
    DELETE,

    /**
     * f19：压缩原先借用 [COPY] 的标签，进度框标题就成了「正在复制 N 项…」——
     * 用户明明点的是「压缩」。进度框的总条数是**顶层条目数**（压缩包里有多少个源），
     * 与复制同一套口径，所以这里只是把「叫什么」改对，不动任何计数逻辑。
     */
    COMPRESS
}

/** 冲突处理三选（4.4 定稿）。 */
enum class ConflictPolicy { REPLACE, KEEP_BOTH, SKIP }

/**
 * 一个冲突项。对话框要把「复制侧」与「已存在侧」的名字/大小/时间**都**摆出来（4.4），
 * 所以这里不是只带路径的薄包装。
 */
data class ConflictItem(
    val sourcePath: String,
    val targetPath: String,
    /** 目录没有字节数，用 -1 表示「—」。 */
    val sourceSize: Long,
    val targetSize: Long,
    val sourceModified: Long,
    val targetModified: Long
) {
    val sourceName: String get() = sourcePath.substringAfterLast('/')

    val targetName: String get() = targetPath.substringAfterLast('/')

    /** 大小与修改时间都相同 → 对话框默认建议「已存在且相同，跳过？」（4.4）。 */
    val identical: Boolean
        get() = sourceSize >= 0 && sourceSize == targetSize && sourceModified == targetModified
}

/** 一次冲突询问的答复；[ConflictDecision] 为 null = 用户点了外面 = **整批取消**（4.4）。 */
data class ConflictDecision(
    val policy: ConflictPolicy,
    /** 对剩余全部应用：后续再撞冲突不再问。 */
    val applyToAll: Boolean
)

/**
 * 七层进度的数据源（4.12）。本类只负责**数据**，格式化与渲染在
 * `UnifiedProgressDialog` 里，两者分开才好做「字节模式 / 条目模式」两种统计口径。
 */
data class OpProgress(
    val kind: OpKind = OpKind.COPY,
    /** 顶层条目数 → 标题「正在复制 3 项…」。 */
    val totalCount: Int = 0,
    val doneCount: Int = 0,
    /** 总字节；-1 = 未知（不画字节进度条，退化为条目模式）。 */
    val totalBytes: Long = -1L,
    val doneBytes: Long = 0L,
    /** 当前正在处理的条目名（第 3 层）。 */
    val currentName: String = "",
    /** 源/目标路径（第 4 层）。 */
    val sourcePath: String = "",
    val targetPath: String = "",
    val finished: Boolean = false,
    val cancelled: Boolean = false,
    val successCount: Int = 0,
    val failedCount: Int = 0,
    /** 首个错误的说明，收尾 toast 里带上（4.4 第 4 条）。 */
    val firstError: String? = null
)

/** 一次操作的最终结果。 */
data class OpResult(
    val cancelled: Boolean,
    val successCount: Int,
    val failedCount: Int,
    val firstError: String?,
    /** 「跳过」的顶层条数。既不算成功也不算失败 —— 收尾 toast 单独说一句。 */
    val skippedCount: Int = 0
)

/**
 * 文件操作引擎（MT.md §4.4/§4.5）：**纯自研**，只依赖 JDK 与协程，不引三方库。
 *
 * - 全程 `Dispatchers.IO`；每写一块字节就 `ensureActive()` 一次，**点取消立刻停**；
 * - 取消后统一清理本次**新建**的半成品（原本就存在的文件不会被误删）——
 *   修正参考实现「只能隐藏不能中止」的硬伤（4.14）；
 * - 冲突只在**顶层条目**判定（`目标目录/同名` 已存在），目录内部合并冲突按替换处理，
 *   这与对话框「源名 → 目标名」的清单口径一致；
 * - 同卷移动先试 `renameTo`（原子、零拷贝），失败或跨卷才退化为 复制 → 长度校验 → 删源。
 */
object FileOps {

    /** 一次操作最多处理的条目数（复制/移动要预扫描建总量，超限直接拒绝，避免拖垮设备）。 */
    const val MAX_ENTRIES = 100_000

    private const val BUFFER = 64 * 1024
    private const val REPORT_INTERVAL_MS = 60L

    /** 条目数超限：交给调用方映射成「请分批」提示。 */
    class TooLargeException(val limit: Int) : IOException("entries over $limit")

    // ==================== 复制 / 移动 ====================

    /**
     * @param onConflict 撞名时询问。返回 null = 用户取消整批（对话框点外面）。
     *
     * 冲突是**逐个懒问**的：第一次撞名时把「当前这条 + 后面所有已知的同名条目」一次性列成
     * 清单问（对应 4.4「多项：可滚动清单」），勾了「对剩余全部应用」就记住策略、
     * 后续（含目录合并时撞的同名文件）不再问；没勾就继续逐条问。
     */
    suspend fun executeCopyOrMove(
        kind: OpKind,
        sources: List<File>,
        targetDir: File,
        onProgress: (OpProgress) -> Unit,
        onConflict: suspend (List<ConflictItem>) -> ConflictDecision?
    ): OpResult {
        require(kind == OpKind.COPY || kind == OpKind.MOVE) { "kind must be COPY or MOVE" }
        if (sources.isEmpty()) return OpResult(false, 0, 0, null)

        val created = mutableListOf<File>()
        return try {
            withContext(Dispatchers.IO) {
                val totalBytes = preScan(sources)

                var success = 0
                var failed = 0
                var skipped = 0
                var firstError: String? = null
                var doneBytes = 0L
                var lastReport = 0L
                /** 勾了「对剩余全部应用」后记住的策略；null = 每次撞名都要问。 */
                var remembered: ConflictPolicy? = null

                fun report(doneCount: Int, current: File, finished: Boolean = false) {
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
                            currentName = if (finished) "" else current.name,
                            // 第 4 层「源 / 目标」要并排两条：源 = 内容来处（首个来源的所在目录），
                            // 目标 = 去处。原先两处都填 targetDir.path，对话框里两条一模一样。
                            // 不能用 current.parent —— 收尾那次传进来的是 targetDir（见下方两处
                            // report(..., finished = true)），会把目标的父目录当成「源」。
                            sourcePath = sources.firstOrNull()?.parent ?: "",
                            targetPath = targetDir.path,
                            finished = finished,
                            successCount = success,
                            failedCount = failed,
                            firstError = firstError
                        )
                    )
                }

                /**
                 * 撞名处理。[index] 是当前这条在 [sources] 里的下标 ——
                 * 从它往后把**所有**已存在的目标都收进同一张清单，一次问完（4.4 多项形态）。
                 */
                suspend fun policyFor(index: Int): ConflictPolicy? {
                    remembered?.let { return it }
                    val batch = mutableListOf<ConflictItem>()
                    for (j in index until sources.size) {
                        val plain = File(targetDir, sources[j].name)
                        if (!plain.exists()) continue
                        batch.add(
                            ConflictItem(
                                sourcePath = sources[j].path,
                                targetPath = plain.path,
                                sourceSize = if (sources[j].isFile) sources[j].length() else -1L,
                                targetSize = if (plain.isFile) plain.length() else -1L,
                                sourceModified = sources[j].lastModified(),
                                targetModified = plain.lastModified()
                            )
                        )
                    }
                    if (batch.isEmpty()) return ConflictPolicy.REPLACE
                    val decision = onConflict(batch) ?: return null
                    if (decision.applyToAll) remembered = decision.policy
                    return decision.policy
                }

                for ((index, src) in sources.withIndex()) {
                    currentCoroutineContext().ensureActive()
                    val plain = File(targetDir, src.name)

                    // 同目录复制 = 自动改名 `name (1).ext`，不弹冲突框（4.4 第 5 条）：
                    // 目标就是它自己，问「要不要替换自己」毫无意义。
                    val sameDir = src.parentFile?.absolutePath == targetDir.absolutePath
                    val sameDirCopy = kind == OpKind.COPY && sameDir

                    // 「剪切 → 在本目录粘贴」是空操作。原先会走冲突框、选替换后
                    // copyFile 用 FileOutputStream 截断**正在读的同一个 inode**，
                    // 源文件先被清空、再被 delete —— 整份文件凭空消失。
                    // 直接当成功跳过，不问也不动。
                    if (kind == OpKind.MOVE && sameDir) {
                        success++
                        continue
                    }

                    // 目录复制/移动进自己的子目录：目标就在源树内部，递归时会把刚建出来的
                    // 目录再当成源的一部分，越复制越深直到路径超长/磁盘写满。直接判失败。
                    if (src.isDirectory && isSameOrDescendant(targetDir, src)) {
                        failed++
                        if (firstError == null) {
                            firstError = Str.get(R.string.files_op_into_itself)
                        }
                        continue
                    }

                    val policy = when {
                        sameDirCopy -> ConflictPolicy.KEEP_BOTH
                        plain.exists() -> policyFor(index)
                        else -> remembered
                    }
                    if (plain.exists() && !sameDirCopy && policy == null) {
                        // 用户在外面点了 = 整批取消
                        report(sources.size, targetDir, finished = true)
                        return@withContext OpResult(true, success, failed, firstError, skipped)
                    }

                    // 「跳过」是**整条不动**：同名的文件不覆盖、同名的目录也不合并 ——
                    // 目录若继续走 transferOne，里面的同名文件仍会被 nestedPolicy 覆盖掉，
                    // 等于把「跳过」当成了「合并」。目录连同整棵子树一起跳过。
                    if (policy == ConflictPolicy.SKIP && plain.exists() && !sameDirCopy) {
                        skipped++
                        continue
                    }

                    val dst = resolveDst(src, targetDir, policy)
                    report(index, src)
                    try {
                        transferOne(
                            src, dst,
                            move = kind == OpKind.MOVE,
                            created = created,
                            // 目录合并时撞到的同名文件：没记住策略就按替换（合并目录的常态语义）
                            nestedPolicy = { remembered ?: ConflictPolicy.REPLACE }
                        ) { n ->
                            doneBytes += n
                            report(index, src)
                        }
                        success++
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        failed++
                        if (firstError == null) firstError = e.message ?: e.javaClass.simpleName
                    }
                }

                report(sources.size, targetDir, finished = true)
                OpResult(false, success, failed, firstError, skipped)
            }
        } catch (e: CancellationException) {
            // 用户点了取消：清掉本次新建的半成品，再把「已取消」如实报出去。
            withContext(NonCancellable) {
                cleanup(created)
                onProgress(
                    OpProgress(
                        kind = kind,
                        totalCount = sources.size,
                        totalBytes = -1L,
                        finished = true,
                        cancelled = true
                    )
                )
            }
            OpResult(true, 0, 0, null)
        }
    }

    // ==================== 删除 ====================

    /**
     * @param expectedCount / [expectedBytes] 由确认对话框提前量好，直接当进度总量用（不再扫第二遍）。
     */
    suspend fun executeDelete(
        targets: List<File>,
        expectedCount: Int,
        expectedBytes: Long,
        onProgress: (OpProgress) -> Unit
    ): OpResult {
        if (targets.isEmpty()) return OpResult(false, 0, 0, null)
        // 用户完全可能在「统计中…」还没跑完时就点删除 —— 那时两个期望值还是 -1，
        // 直接当总量用，标题就会显示成「正在删除 -1 项…」（反馈 e28 的负数）。
        // 回退成顶层目标数、字节退回「未知」，宁可粗一点也不许出现负数。
        // g9：**一律按顶层目标报数**。原先进度/汇总用的是递归条目数 —— 勾 1 个文件夹
        // （里面有 100 个文件）会显示「正在删除 101 项」，和确认框、屏幕上勾选的
        // 「1 项」对不上。进度条只需要知道「第几项 / 共几项」，子树多少条与用户无关。
        val totalCount = targets.size
        val totalBytes = if (expectedBytes > 0) expectedBytes else -1L
        return try {
            withContext(Dispatchers.IO) {
                var done = 0             // 已处理完的顶层目标（成功 + 失败）
                var ok = 0               // 成功删掉的顶层目标数
                var failed = 0
                var doneBytes = 0L
                var lastReport = 0L
                var firstError: String? = null

                for (target in targets) {
                    currentCoroutineContext().ensureActive()
                    val now = System.currentTimeMillis()
                    if (now - lastReport >= REPORT_INTERVAL_MS) {
                        lastReport = now
                        onProgress(
                            OpProgress(
                                kind = OpKind.DELETE,
                                totalCount = totalCount,
                                doneCount = done,
                                totalBytes = totalBytes,
                                doneBytes = doneBytes,
                                currentName = target.name,
                                sourcePath = target.path,
                                successCount = ok,
                                failedCount = failed,
                                firstError = firstError
                            )
                        )
                    }
                    try {
                        // 体积仍然递归量（字节行要动），但**计数不递归**。
                        val (bytes, _) = deleteTree(target)
                        done++
                        ok++
                        doneBytes += bytes
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        done++
                        failed++
                        if (firstError == null) firstError = e.message ?: e.javaClass.simpleName
                    }
                }

                onProgress(
                    OpProgress(
                        kind = OpKind.DELETE,
                        totalCount = totalCount,
                        doneCount = done,
                        totalBytes = totalBytes,
                        doneBytes = doneBytes,
                        finished = true,
                        successCount = ok,
                        failedCount = failed,
                        firstError = firstError
                    )
                )
                OpResult(false, ok, failed, firstError)
            }
        } catch (e: CancellationException) {
            withContext(NonCancellable) {
                onProgress(
                    OpProgress(
                        kind = OpKind.DELETE,
                        totalCount = targets.size,
                        finished = true,
                        cancelled = true
                    )
                )
            }
            OpResult(true, 0, 0, null)
        }
    }

    // ==================== 量尺寸（删除确认框用） ====================

    /**
     * 递归统计体积与条目数（4.5：确认框要异步补出「8.26 MB · 3 项」）。
     * 只在 `Dispatchers.IO` 上调用；带 [MAX_ENTRIES] 上限，超了按已扫到的量报，不卡死。
     */
    suspend fun measure(items: List<File>): Pair<Long, Int> = withContext(Dispatchers.IO) {
        var bytes = 0L
        var entries = 0
        val stack = ArrayDeque<File>()
        items.forEach { stack.addLast(it) }
        while (stack.isNotEmpty() && entries < MAX_ENTRIES) {
            val f = stack.removeLast()
            entries++
            if (f.isDirectory) {
                f.listFiles()?.forEach { stack.addLast(it) }
            } else {
                bytes += f.length()
            }
        }
        bytes to entries
    }

    // ==================== 工具 ====================

    /** 保留两份时的建议名：`name (1).ext`（点开头的文件不当扩展名处理）。 */
    fun uniqueSibling(target: File): File {
        val parent = target.parentFile ?: return target
        val name = target.name
        val dot = name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var i = 1
        while (i < Int.MAX_VALUE) {
            val candidate = File(parent, "$base ($i)$ext")
            if (!candidate.exists()) return candidate
            i++
        }
        return target
    }

    /**
     * 大小文案。**只做转发** —— 真正的实现是 [com.UIN.Tool.utils.formatFileSize]，
     * 保证进度条、冲突清单、属性框、下载进度四处同一个字节数显示成同一个样子。
     */
    fun formatSize(bytes: Long): String = com.UIN.Tool.utils.formatFileSize(bytes)

    /**
     * [dir] 是否等于 [ancestor] 或位于它内部。
     *
     * 用 canonical 路径比较（`/sdcard/x/../x`、符号链接都归一化）；取不到时退回绝对路径。
     * 只用于「别把文件夹复制进自己」的拦截，代价是一次 stat，可忽略。
     */
    private fun isSameOrDescendant(dir: File, ancestor: File): Boolean {
        val d = runCatching { dir.canonicalPath }.getOrElse { dir.absolutePath }
        val a = runCatching { ancestor.canonicalPath }.getOrElse { ancestor.absolutePath }
        return d == a || d.startsWith(a + File.separator)
    }

    /** 目标落点：冲突且选了「保留两份」→ 改名；其余情况保持原名（替换在传输里就地覆盖）。 */
    private fun resolveDst(src: File, targetDir: File, policy: ConflictPolicy?): File {
        val plain = File(targetDir, src.name)
        return if (policy == ConflictPolicy.KEEP_BOTH) uniqueSibling(plain) else plain
    }

    // ==================== 内部实现 ====================

    /** 预扫描：只为拿到总字节（进度条第 5/6 层）。超限抛 [TooLargeException]。 */
    private suspend fun preScan(sources: List<File>): Long {
        var bytes = 0L
        var entries = 0
        val stack = ArrayDeque<File>()
        sources.forEach { stack.addLast(it) }
        while (stack.isNotEmpty()) {
            currentCoroutineContext().ensureActive()
            val f = stack.removeLast()
            if (++entries > MAX_ENTRIES) throw TooLargeException(MAX_ENTRIES)
            if (f.isDirectory) f.listFiles()?.forEach { stack.addLast(it) }
            else bytes += f.length()
        }
        return bytes
    }

    /**
     * 递归搬一条。`created` 只记录**本次新建**的路径，取消时据此清半成品 ——
     * 覆盖已存在文件时故意不记账，避免「取消反而把原来的文件删了」。
     *
     * @param nestedPolicy 目录合并时撞到同名文件的处理（顶层冲突已在 [executeCopyOrMove] 里问过）
     */
    private suspend fun transferOne(
        src: File,
        dst: File,
        move: Boolean,
        created: MutableList<File>,
        nestedPolicy: () -> ConflictPolicy,
        onBytes: (Long) -> Unit
    ) {
        currentCoroutineContext().ensureActive()

        if (src.isDirectory) {
            if (!dst.isDirectory) {
                if (dst.exists()) throw IOException("target exists and is not a directory: ${dst.path}")
                if (!dst.mkdirs()) throw IOException("cannot create directory: ${dst.path}")
                created.add(dst)
            }
            src.listFiles()?.forEach { child ->
                val childDst = File(dst, child.name)
                val effective = if (childDst.exists() && !child.isDirectory) {
                    when (nestedPolicy()) {
                        ConflictPolicy.SKIP -> return@forEach
                        ConflictPolicy.KEEP_BOTH -> FileOps.uniqueSibling(childDst)
                        ConflictPolicy.REPLACE -> childDst
                    }
                } else childDst
                transferOne(child, effective, move, created, nestedPolicy, onBytes)
            }
            // 空目录移动也要把源清掉；非空目录在上面递归里已逐个搬走
            if (move && src.exists() && !src.delete()) throw IOException("cannot remove: ${src.path}")
            return
        }

        // 同卷移动先试原子改名（零拷贝）。目标已存在时不试，交给覆盖分支。
        if (move && !dst.exists()) {
            val size = src.length()
            if (src.renameTo(dst)) {
                onBytes(size)
                return
            }
        }

        if (dst.exists() && dst.isDirectory) throw IOException("target is a directory: ${dst.path}")
        if (!dst.exists()) {
            dst.parentFile?.let { parent ->
                if (!parent.isDirectory && !parent.mkdirs()) {
                    throw IOException("cannot create directory: ${parent.path}")
                }
            }
            created.add(dst)
        }

        // 用户反馈 e15 P0-1：**覆盖前先把旧文件的复制一份进回收站** —— 覆盖即真删是没有
        // 后悔药的。放在记账之后、写入之前：原文件此刻还在原地（回收站存的是**副本**），
        // 所以既不需要恢复逻辑，也不动「取消只清本次新建」那条账。失败不拦覆盖。
        if (dst.exists()) RecycleBin.preserve(dst)

        copyFile(src, dst, onBytes)

        if (move) {
            // 跨卷：校验通过才删源，长度对不上就把半成品删掉、源保留
            if (dst.length() != src.length()) {
                dst.delete()
                throw IOException("verify failed: ${dst.path}")
            }
            if (!src.delete()) throw IOException("cannot remove: ${src.path}")
        }
    }

    private suspend fun copyFile(src: File, dst: File, onBytes: (Long) -> Unit) {
        val buf = ByteArray(BUFFER)
        FileInputStream(src).use { ins ->
            FileOutputStream(dst).use { outs ->
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val n = ins.read(buf)
                    if (n < 0) break
                    outs.write(buf, 0, n)
                    onBytes(n.toLong())
                }
                // 移动要靠校验后删源，先落盘再比长度才有意义
                outs.fd.sync()
            }
        }
        dst.setLastModified(src.lastModified())
    }

    /**
     * 删掉一棵子树，返回 **`字节数 to 条目数`** —— 删除进度的报数口径就靠它。
     *
     * 只按顶层目标报数是不行的：删一个含 51 项的目录，进度全程停在 0%，收尾 toast
     * 只会说「共 1 项」，跟确认框那句「51 项」对不上（反馈 e28）。
     *
     * 字节必须在 `delete()` **之前**量 —— 删完 `length()` 恒为 0。
     * 条目口径与 [measure] 一致：**含自身**，这样确认框与进度用的是同一个数。
     */
    private suspend fun deleteTree(f: File): Pair<Long, Int> {
        currentCoroutineContext().ensureActive()
        val isDir = f.isDirectory
        val ownBytes = if (isDir) 0L else f.length()
        var bytes = 0L
        var entries = 0
        if (isDir) {
            f.listFiles()?.forEach { child ->
                val (b, c) = deleteTree(child)
                bytes += b
                entries += c
            }
        }
        if (f.exists() && !f.delete()) throw IOException("cannot delete: ${f.path}")
        return (bytes + ownBytes) to (entries + 1)
    }

    /** 取消后的半成品清理：先文件后目录、倒序，避开「父目录还没空就删不掉」。 */
    private fun cleanup(created: List<File>) {
        for (f in created.asReversed()) {
            try {
                if (f.exists()) f.delete()
            } catch (_: Exception) {
                // 清理尽力而为，失败不影响「已取消」这个结论
            }
        }
    }

    // ==================== .bak 恢复（g11） ====================

    /** 恢复结果：[target] 是恢复后的文件；[backedUp] 非空表示原同名文件已改成这个名字。 */
    data class BakRestoreResult(val target: File, val backedUp: String?)

    /**
     * 把 `a.xxx.bak` 恢复成 `a.xxx`。
     *
     * 同名文件已存在时，先把待恢复件挪到临时名，再把原文件改成 **原 `.bak` 名**
     * （用户要的就是「原来的 a.xxx 变成 a.xxx.bak」），最后把临时件落成 `a.xxx`。
     * 任何一步失败都回滚，绝不出现「原文件被挪走、备份也没了」的中间态。
     *
     * 只做文件系统操作，不碰 UI —— 调用方负责提示与刷新。
     */
    fun restoreBak(src: File): BakRestoreResult {
        require(src.isFile) { "not a file: ${src.path}" }
        val name = src.name
        require(name.endsWith(".bak", ignoreCase = true) && name.length > 4) { "not a .bak: $name" }
        val dir = src.parentFile ?: throw java.io.IOException("no parent: ${src.path}")
        val target = File(dir, name.dropLast(4))
        if (target == src) throw java.io.IOException("target == source")

        if (!target.exists()) {
            if (!src.renameTo(target)) throw java.io.IOException("rename failed: $name")
            return BakRestoreResult(target, null)
        }

        // 临时名用 `.restoring` 后缀，不会撞上用户的任何普通文件
        val holding = File(dir, "$name.${System.currentTimeMillis()}.restoring")
        if (!src.renameTo(holding)) throw java.io.IOException("rename failed: $name")
        // 原文件让位，改用待恢复件腾出来的那个名字（= 原 `.bak` 名）
        val backup = File(dir, name)
        if (!target.renameTo(backup)) {
            holding.renameTo(src)
            throw java.io.IOException("backup rename failed: ${target.name}")
        }
        if (!holding.renameTo(target)) {
            backup.renameTo(target)
            holding.renameTo(src)
            throw java.io.IOException("restore rename failed: ${target.name}")
        }
        return BakRestoreResult(target, backup.name)
    }
}
