// app/src/main/java/com/UIN/Tool/ui/screen/files/FileSearch.kt
package com.UIN.Tool.ui.screen.files

import android.os.SystemClock
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 搜索条件（§4.8）。
 *
 * 与 PaneState.filter 的区别是**本质的**，不是程度的：
 * - `filter` 是**活动窗格内、即时、只看当前目录**的输入即过滤，作用于已加载的条目；
 * - 本类是**递归搜索**，要亲自走盘、可选读文件内容、结果落常驻面板。
 * 把两者合成一个会让「过滤框输入后列表空了」和「搜索框输入后开始扫盘」互相打架。
 */
data class SearchOptions(
    val query: String = "",
    /** 包含子文件夹（§4.8 明列的复选框）。 */
    val recursive: Boolean = true,
    val caseSensitive: Boolean = false,
    /** 正则。**由调用方保证表达式合法** —— UI 层负责编译失败时提示并保持上一次可用条件。 */
    val regex: Boolean = false,
    /** 匹配文件内容（而非仅名字）。 */
    val contentMatch: Boolean = false,
    /** 体积范围（字节）。`-1` = 不限。 */
    val minSize: Long = -1L,
    val maxSize: Long = -1L
) {
    /** 条件是否空到「搜了必然全中」—— UI 据此禁用搜索按钮，免得用户误触就开始扫盘。 */
    val isBlank: Boolean
        get() = query.isBlank() && !contentMatch && minSize < 0 && maxSize < 0
}

/** 一条搜索结果。[path] 是真实绝对路径。 */
data class SearchResult(
    val path: String,
    val name: String,
    val isDir: Boolean,
    val size: Long,
    val modified: Long,
    /** 命中内容时这一行所在的行号（1 起），便于用户判断是不是自己要找的那段。 */
    val hitLine: Int = -1,
    /** 命中内容时那一行的原文（已截断）。 */
    val hitText: String = ""
)

/** 搜索状态。 */
sealed interface SearchState {
    data object Idle : SearchState
    data class Running(val scanned: Int, val hits: Int) : SearchState
    data class Done(val hits: Int, val truncated: Boolean) : SearchState
    /**
     * n.txt 第 15 条：用户**中途停止**。与 [Done] 分开，因为「找到 N 项就这些了」
     * 与「扫到一半我喊停」是两种完全不同的结论 —— 前者可以据此判断「没有更多」，
     * 后者不行。已经找到的结果照常保留并展示。
     */
    data class Stopped(val hits: Int) : SearchState
}

/**
 * 递归搜索（§4.8）。
 *
 * ## 三个必须说清的取舍
 *
 * 1. **内容匹配读整个文件**，只是分块流式读、**内存有界**：
 *    - 按 64KB 读、按 `\n` 切行，峰值内存 = 一行（超长行截到 [MAX_LINE_CHARS]）；
 *    - **二进制直接跳过**（前 8KB 出现 NUL）：对几个 GB 的视频/安装包搜文本没有意义，
 *      强行读只会让搜索卡住；名字匹配不受影响；
 *    - 每 64KB `ensureActive()`，点「停止」能立刻收手。
 * 2. **结果条数有上限**。扫一个含十万文件的目录树，若全部塞进 `StateFlow`，
 *    每来一条就重组一次列表，UI 会直接卡死。到 [RESULT_LIMIT] 就停并标记 `truncated`。
 * 3. **单条失败不中断整批**。某个子目录没权限就读不了，那是常态；为此中止整个搜索
 *    会让「有 99% 可读」的场景一个结果都拿不到。
 */
object FileSearch {

    /** 结果条数上限的默认值（可在设置里改，0 = 不限；i.txt 第 10 条）。 */
    const val RESULT_LIMIT = FilePrefs.SEARCH_LIMIT_DEFAULT

    /** 单行参与匹配的最大字符数。无换行的超大二进制只保留前段，避免一行撑爆内存。 */
    private const val MAX_LINE_CHARS = 1 shl 20

    /** 命中行原文最多留这么多字符（结果行只显示一行，多了也放不下）。 */
    private const val SNIPPET_CHARS = 160

    /** 只在开头这么多字符里查 NUL，判定是不是二进制。 */
    private const val BINARY_PROBE_CHARS = 8192

    /**
     * u.txt 第 2 条（用户）：点搜索「有概率闪退」。
     *
     * 递归走盘原先没有深度上限 —— 目录里若有**指向祖先的符号链接**（解压 tar/zip
     * 时很常见），`walk` 会一路递归下去直到 StackOverflowError；而协程的
     * `catch (Exception)` 接不住 `Error`，于是进程直接闪退。加一道深度闸门：
     * 超过就当作普通目录收下、不再往下走。正常目录树远达不到这个深度。
     */
    private const val MAX_WALK_DEPTH = 64

    private val _state = MutableStateFlow<SearchState>(SearchState.Idle)
    val state: StateFlow<SearchState> = _state.asStateFlow()

    private val _results = MutableStateFlow<List<SearchResult>>(emptyList())
    val results: StateFlow<List<SearchResult>> = _results.asStateFlow()

    /** 一次性提示。**不是字符串** —— 交给 UI 层映射到 strings 资源，保证 zh/en 都对。 */
    sealed interface Notice {
        /** 条件为空，没东西可搜。 */
        data object Blank : Notice

        /** 正则表达式非法。 */
        data object BadRegex : Notice

        /** 扫描途中出错（多为没权限）。 */
        data class Error(val message: String?) : Notice

        /**
         * 搜索范围在**归档内**、而用户勾了「匹配文件内容」（反馈 e15 P0-2）。
         * 内容匹配读的是真实文件，包里的条目没有可读的字节流可走 ——
         * 宁可明说「只按名字搜」，也不要让用户以为「搜不到 = 里面没有」。
         */
        data object ArchiveNameOnly : Notice
    }

    private val _notices = MutableSharedFlow<Notice>(extraBufferCapacity = 4)
    val notices: SharedFlow<Notice> = _notices.asSharedFlow()

    private var job: Job? = null

    /**
     * n.txt 第 15 条：每次 [start] 自增。被取消的旧任务收尾时若发现代数已变，
     * 就**不许再写** `_state` / `_results` —— 否则「停止后立刻重新搜索」时，
     * 旧任务迟到的 Stopped 会把新一次搜索的 Running 和结果盖掉。
     */
    @Volatile
    private var searchGeneration = 0

    /**
     * 起一次搜索。**已有搜索在跑时先取消旧的** —— 两个搜索同时往同一个 `_results`
     * 里写，用户会看到结果莫名其妙地混着两次的内容。
     *
     * [root] 是**路径字符串**而不是 `File`：归档内的当前位置是虚拟路径
     * （`x.zip!/dir`），拿它去问 `File.isDirectory()` 恒为 false，于是走到
     * `consider(root)`、对着一个不存在的文件匹配 —— 这正是「归档内搜索完全不可用」
     * 的根因（反馈 e15 P0-2）。这里显式分流：虚拟路径按**条目表**枚举，真实路径照旧。
     */
    fun start(scope: CoroutineScope, root: String, options: SearchOptions) {
        if (options.isBlank) {
            _notices.tryEmit(Notice.Blank)
            return
        }
        // 正则在这里编译一次：非法表达式应当**当场**报，而不是扫到一半才炸
        val pattern = if (options.regex) {
            try {
                Regex(options.query)
            } catch (e: Exception) {
                _notices.tryEmit(Notice.BadRegex)
                return
            }
        } else null

        val split = VirtualPath.split(root)
        // 包内没有可读的字节流可走（见 Notice.ArchiveNameOnly）—— 先说一声，
        // 免得用户对着「0 结果」以为里面真没有
        if (split != null && options.contentMatch) _notices.tryEmit(Notice.ArchiveNameOnly)

        job?.cancel()
        val generation = ++searchGeneration
        _results.value = emptyList()
        _state.value = SearchState.Running(scanned = 0, hits = 0)
        // 上限在**开始时**读一次：搜索跑起来之后用户再改设置，不该让同一次搜索
        // 中途换口径（已经吐出来的结果也不会收回）。
        val limit = FilePrefs.searchResultLimit()
        job = scope.launch(Dispatchers.IO) {
            val out = ArrayList<SearchResult>()
            var scanned = 0
            var truncated = false
            // h.txt 第 12 条：进度不能只在「命中的第 200 项」才上报 —— 命中判定失败时
            // 函数提前 return，`scanned % 200` 那段根本走不到，界面于是一直停在
            // 「已扫描 0 项」，直到扫描结束才整批弹出来。改成按**时间**节流上报。
            var lastReportAt = 0L
            var lastReportedHits = 0

            fun reportProgress() {
                // 旧任务不许再写状态（见 searchGeneration 的说明）。
                if (generation != searchGeneration) return
                val now = SystemClock.elapsedRealtime()
                if (now - lastReportAt < 120L) return
                lastReportAt = now
                _state.value = SearchState.Running(scanned = scanned, hits = out.size)
                if (out.size != lastReportedHits) {
                    lastReportedHits = out.size
                    _results.value = out.toList()
                }
            }

            /**
             * 命中判定 + 落结果。参数一律是**值**而不是 `File` —— 归档内条目没有
             * 真实 `File`（`File("x.zip!/a.txt")` 上问 `isFile()` 恒为 false）。
             *
             * @param content 内容来源；null = 拿不到字节（归档内条目），内容匹配跳过
             */
            suspend fun emit(
                path: String,
                name: String,
                isDir: Boolean,
                size: Long,
                modified: Long,
                content: (() -> java.io.InputStream?)? = null
            ) {
                currentCoroutineContext().ensureActive()
                scanned++
                // 先报进度，再判定命中：命中与否都要让用户看到「还在扫」。
                reportProgress()
                // p.txt 第 9 条（用户）：搜索命中要**同时看名称和完整路径**。
                val hit = matchesNameOrContent(
                    name, path, isDir, size, content, options, pattern
                )
                    ?: return
                out += SearchResult(
                    path = path,
                    name = name,
                    isDir = isDir,
                    size = size,
                    modified = modified,
                    hitLine = hit.line,
                    hitText = hit.text
                )
                if (limit > 0 && out.size >= limit) {
                    truncated = true
                    throw DoneSignal()
                }
                // 命中后立刻让结果可见（同样受 120ms 节流，避免内容匹配狂刷列表）
                reportProgress()
            }

            suspend fun consider(f: File) = emit(
                path = f.absolutePath,
                name = f.name,
                isDir = f.isDirectory,
                size = if (f.isFile) f.length() else 0L,
                modified = f.lastModified(),
                content = if (f.isFile) ({ f.inputStream() }) else null
            )

            suspend fun walk(dir: File, depth: Int) {
                currentCoroutineContext().ensureActive()
                // u.txt 第 2 条：深度闸门 —— 符号链接成环时不能无限递归（否则闪退）。
                if (depth >= MAX_WALK_DEPTH) return
                val kids = dir.listFiles() ?: return
                for (k in kids) {
                    currentCoroutineContext().ensureActive()
                    if (k.isDirectory) {
                        // 目录本身也参与匹配：用户搜「Download」应当能搜到那个目录
                        consider(k)
                        if (options.recursive) walk(k, depth + 1)
                    } else {
                        consider(k)
                    }
                }
            }

            /**
             * 归档内按**条目表**枚举（反馈 e15 P0-2）。
             *
             * `listArchiveChildren` 每次只给直接子项、且 `readArchiveEntries` 有缓存，
             * 所以递归时不必自己把全量条目读一遍。条目 `path` 是**包内相对路径**，
             * 结果里必须 [VirtualPath.join] 成虚拟路径 —— 搜索结果要能直接点过去。
             */
            suspend fun walkArchive(archivePath: String, internalDir: String, depth: Int) {
                currentCoroutineContext().ensureActive()
                if (depth >= MAX_WALK_DEPTH) return
                val children = listArchiveChildren(File(archivePath), internalDir)
                for (e in children) {
                    currentCoroutineContext().ensureActive()
                    emit(
                        path = VirtualPath.join(archivePath, e.path),
                        name = e.name,
                        isDir = e.isDir,
                        size = e.size,
                        modified = e.modified,
                        content = null
                    )
                    if (e.isDir && options.recursive) walkArchive(archivePath, e.path, depth + 1)
                }
            }

            try {
                if (split != null) walkArchive(split.first, split.second, 0)
                else {
                    val dir = File(root)
                    if (dir.isDirectory) walk(dir, 0)
                    else consider(dir)
                }
            } catch (_: DoneSignal) {
                // 到达上限，正常收尾
            } catch (_: kotlinx.coroutines.CancellationException) {
                // n.txt 第 15 条：停止时**保留已经找到的结果** —— 把最后一份 out
                // 交出去（节流发布最多丢 120ms，不能把攒了这一会儿的结果一起收回），
                // 状态改成 Stopped 而非 Idle，面板才能说清「是停了，不是没搜到」。
                if (generation == searchGeneration) {
                    _results.value = out.toList()
                    _state.value = SearchState.Stopped(hits = out.size)
                }
                return@launch
            } catch (e: Exception) {
                _notices.tryEmit(Notice.Error(e.message))
            } catch (e: StackOverflowError) {
                // u.txt 第 2 条（用户）：点搜索「有概率闪退」的最后一道防线。
                // 递归走盘已有深度闸门，但归档枚举 / 目录树里若还有别的成环路径，
                // 溢出的是 **Error 而不是 Exception** —— 上面那个 catch 接不住，
                // 协程会把它交给默认未捕获处理器，进程直接退出。这里收下它：
                // 已经找到的结果照常交出去，只补一条「扫描出错」的提示。
                _notices.tryEmit(Notice.Error(e.message))
            }

            if (generation != searchGeneration) return@launch
            _results.value = out.toList()
            _state.value = SearchState.Done(hits = out.size, truncated = truncated)
        }
    }

    /**
     * n.txt 第 15 条：停止搜索但**保留结果**。
     *
     * 旧实现把状态直接改回 [SearchState.Idle]，结果既没有「已停止」的说明，正在
     * 扫描的协程还会在取消时把最后一小段（节流窗口内）丢掉。现在只取消任务：
     * 状态由协程的取消分支统一收尾为 [SearchState.Stopped] 并发布最后的结果；
     * 任务已结束（Done/Stopped）时这里什么都不动，停止按钮本来也不该出现。
     */
    fun cancel() {
        val running = _state.value is SearchState.Running
        job?.cancel()
        job = null
        if (running) {
            // 协程可能已经在取消路上；先给一个立即生效的状态，避免按钮闪回「搜索」。
            _state.value = SearchState.Stopped(hits = _results.value.size)
        }
    }

    fun clear() {
        job?.cancel()
        job = null
        searchGeneration++
        _results.value = emptyList()
        _state.value = SearchState.Idle
    }

    /** 到达结果上限的内部信号；不当作错误。 */
    private class DoneSignal : Exception()

    // ==================== 匹配 ====================

    /**
     * 一次命中的说明。
     *
     * [line] = -1 表示**按名字**命中（没有内容行号）；否则是内容命中的行号（1 起），
     * [text] 是那一行的前 [SNIPPET_CHARS] 个字符，供结果行展示。
     */
    private class ContentHit(val line: Int, val text: String)

    /** 名字命中，没有内容行号。 */
    private val NAME_HIT = ContentHit(-1, "")

    /**
     * 名字或内容是否命中。
     *
     * 体积范围**先于**内容匹配判定 —— 范围是纯元数据判断（一次 `length()`），
     * 而内容匹配要真读盘。先把绝大多数排除掉，才不会为了一个体积不符的大文件白读一遍。
     *
     * 参数一律是**值**而不是 `File`：归档内条目没有真实 `File`
     * （`File("x.zip!/a.txt")` 上问 `isFile()` 恒为 false），拿它去问只会得到
     * 「不是文件 → 不匹配内容 → 什么都搜不到」（反馈 e15 P0-2）。
     *
     * @param content 内容来源；null = 拿不到字节（归档内条目），内容匹配直接跳过
     */
    private suspend fun matchesNameOrContent(
        name: String,
        path: String,
        isDir: Boolean,
        size: Long,
        content: (() -> java.io.InputStream?)?,
        o: SearchOptions,
        pattern: Regex?
    ): ContentHit? {
        if (!matchesSize(isDir, size, o)) return null
        if (matchesText(name, o, pattern)) return NAME_HIT
        // 路径命中与名称命中同权：搜「Download」应能搜到该目录下的文件，
        // 而不是只搜到名叫 Download 的那一个目录。
        if (matchesText(path, o, pattern)) return NAME_HIT
        if (o.contentMatch && content != null) return matchesContent(content, o, pattern)
        return null
    }

    private fun matchesSize(isDir: Boolean, size: Long, o: SearchOptions): Boolean {
        if (o.minSize < 0 && o.maxSize < 0) return true
        // 目录的体积无意义（多为 4096），只有真文件才卡体积
        if (isDir) return o.minSize < 0 && o.maxSize < 0
        if (o.minSize >= 0 && size < o.minSize) return false
        if (o.maxSize >= 0 && size > o.maxSize) return false
        return true
    }

    private fun matchesText(text: String, o: SearchOptions, pattern: Regex?): Boolean {
        if (o.query.isBlank()) return true
        return if (pattern != null) {
            pattern.containsMatchIn(text)
        } else {
            text.contains(o.query, ignoreCase = !o.caseSensitive)
        }
    }

    /**
     * 内容匹配：分块流式读、按行匹配，命中即返回行号与该行原文。
     *
     * 不整读进内存：内容匹配的对象可能是几个 GB 的日志；也不再用「只读前 8MB」的截断 ——
     * 那会让「搜不到」变成一句无法验证的话。峰值内存钉在一行（[MAX_LINE_CHARS]）上。
     *
     * 两个刻意的跳过：
     * - 前 [BINARY_PROBE_CHARS] 个字符里出现 NUL → 当二进制，不做内容匹配（名字匹配已在上层完成）；
     * - 单行超过 [MAX_LINE_CHARS] → 只拿前段参与匹配，但不因此中断整个文件。
     *
     * @return 命中行信息；null = 没命中或读不了（没权限/已删除），不中断整批
     */
    private suspend fun matchesContent(
        source: () -> java.io.InputStream?,
        o: SearchOptions,
        pattern: Regex?
    ): ContentHit? {
        return try {
            val input = source() ?: return null
            input.bufferedReader().use { reader ->
                val chunk = CharArray(64 * 1024)
                var firstChunk = true
                var lineNo = 0
                var line = StringBuilder()
                // 当前行是否已经超过 MAX_LINE_CHARS：超了之后只保留前段，
                // 不再往后拼 —— 否则截断后的前缀 + 新块尾部会拼成一段实际不存在的文本
                var lineOverflow = false
                while (true) {
                    // 每块检查一次取消：一个 1GB 的文本文件也能立刻停下
                    currentCoroutineContext().ensureActive()
                    val n = reader.read(chunk)
                    if (n < 0) break
                    if (firstChunk) {
                        firstChunk = false
                        val probe = minOf(n, BINARY_PROBE_CHARS)
                        for (i in 0 until probe) {
                            if (chunk[i] == '\u0000') return null
                        }
                    }
                    var start = 0
                    var i = 0
                    while (i < n) {
                        if (chunk[i] != '\n') {
                            i++
                            continue
                        }
                        lineNo++
                        if (!lineOverflow) line.append(chunk, start, i - start)
                        // 与旧的 readLine() 对齐：行尾的 \r 不算正文
                        val text = line.toString().trimEnd('\r')
                        if (matchesText(text, o, pattern)) {
                            return ContentHit(lineNo, text.take(SNIPPET_CHARS))
                        }
                        line = StringBuilder()
                        lineOverflow = false
                        start = i + 1
                        i++
                    }
                    if (start < n && !lineOverflow) {
                        line.append(chunk, start, n - start)
                        if (line.length > MAX_LINE_CHARS) {
                            line.setLength(MAX_LINE_CHARS)
                            lineOverflow = true
                        }
                    }
                }
                // 文件末尾没有换行：最后一段也算一行
                lineNo++
                val text = line.toString().trimEnd('\r')
                if (text.isNotEmpty() && matchesText(text, o, pattern)) {
                    ContentHit(lineNo, text.take(SNIPPET_CHARS))
                } else {
                    null
                }
            }
        } catch (_: IOException) {
            // 读不了（没权限/已删除/是二进制流）→ 视为不命中，不中断整批
            null
        }
    }
}
