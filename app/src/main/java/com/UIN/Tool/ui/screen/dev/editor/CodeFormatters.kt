// app/src/main/java/com/UIN/Tool/ui/screen/dev/editor/CodeFormatters.kt
package com.UIN.Tool.ui.screen.dev.editor

/**
 * 6.2.0 批次40：**真**格式化器。
 *
 * 背景（已在 sora 0.24.4 源码里核实）：`TextMateLanguage extends EmptyLanguage`
 * 且**不覆盖** `getFormatter()`，于是拿到 `EmptyLanguage.EmptyFormatter.INSTANCE` ——
 * 它的 `format()` / `setReceiver()` 都是**空方法体**。所以 `CodeEditor.formatCodeAsync()`
 * 虽然返回 true，实际什么都不发生：没有变化、没有错误、没有任何反馈。
 * 菜单里的「格式化」一直是假按钮。
 *
 * 这里**不实现 sora 的 `Formatter` 接口**，而是直接产出格式化后的字符串，
 * 由调用方走 `CodeEditor.onFormatSucceed()` 应用 —— 那是 sora 自己的应用路径
 * （`beginBatchEdit`+`endBatchEdit` = 一步撤销、`inputConnection` 失效、
 * 光标 `setSelectionAround` 钳制、滚动条复位），比自己 `setText` 好在不会丢撤销历史。
 *
 * 铁律：**只调整缩进与空白，绝不动任何值**。格式化把用户数据改坏，比不格式化严重得多。
 * 因此 JSON 走逐 token 保值重排（数值、字符串字面量原样搬运），
 * YAML 只做「证明安全」的行级清理，且块标量内的行一个字节都不碰。
 */
object CodeFormatters {

    /** 支持格式化的扩展名（其余类型菜单里直接置灰，避免再出现假按钮）。 */
    fun supports(fileName: String): Boolean = kindOf(fileName) != null

    /**
     * @return 格式化后的文本；`null` = 该类型不支持，或内容不合法（此时**不改动**原文档）。
     */
    fun formatText(fileName: String, source: String): String? = when (kindOf(fileName)) {
        Kind.JSON -> formatJson(source)
        Kind.YAML -> formatYaml(source)
        null -> null
    }

    private enum class Kind { JSON, YAML }

    private fun kindOf(fileName: String): Kind? = when (fileName.substringAfterLast('.', "").lowercase()) {
        "json", "json5", "jsonc" -> Kind.JSON
        "yaml", "yml" -> Kind.YAML
        else -> null
    }

    // ==================== JSON ====================

    private const val JSON_INDENT = "  "

    /**
     * 逐 token 保值的 JSON 重排。
     *
     * 只重算**缩进/冒号后空格**，其余字符原样搬运，因此数值精度、键顺序、
     * 转义写法、JSON5 的单引号与尾逗号全部不受影响（用 `org.json` 解析再序列化
     * 会把大整数变 Double、丢掉重复键，反而会改坏数据）。
     *
     * 同时做**结构校验**：括号不配对、字符串未闭合、顶层出现多个值 → 一律返回 null，
     * 绝不输出半个文件。
     *
     * 注释按不透明 token 原样保留，`.jsonc` 也能格式化。
     *
     * @return 格式化结果；结构不合法时返回 null。
     */
    private fun formatJson(src: String): String? {
        val out = StringBuilder(src.length + src.length / 8)
        val brackets = ArrayList<Char>()          // 未闭合的括号栈，用于配对校验
        val hasContent = ArrayList<Boolean>()     // 与 brackets 等长：该层是否已吐过实质内容
        var pendingNewline = false                // 待写入的换行（首个元素前/逗号后）
        /**
         * 行注释之后必须**硬换行**：行注释会吃掉整行，若下一个 token 跟在它后面，
         * 会被一起注释掉（例如 `// 配置项{...` —— 整个对象都被注释，直接毁文件）。
         * 空容器 `{}` 则相反，闭括号**不能**换行，故与 pendingNewline 分开记。
         */
        var hardBreak = false
        /** 最近一个行注释在 [out] 中的起始下标 —— 用于把紧随其后的逗号挪到注释前面。 */
        var commentStart = -1
        var topLevelDone = false                  // 顶层是否已有值
        var i = 0
        val n = src.length

        fun indent(level: Int) {
            repeat(level) { out.append(JSON_INDENT) }
        }

        fun newline(level: Int) {
            out.append('\n')
            indent(level)
        }

        fun flushPending() {
            if (pendingNewline || hardBreak) {
                newline(brackets.size)
                pendingNewline = false
            }
            hardBreak = false
        }

        // 块注释结束后紧跟的 token 前补一个空格，避免「注释收尾符 + 引号/数字」黏连成一坨。
        // 只看最后一个字符是不是斜杠，能走到这里说明刚刚写完一个块注释。
        fun prepareForToken() {
            flushPending()
            if (out.isNotEmpty() && out[out.length - 1] == '/') out.append(' ')
        }

        // 注释前补一个空格，避免「数字紧贴行注释」——合法，但读起来像被注释掉了。
        fun spaceBeforeComment() {
            if (out.isEmpty()) return
            val last = out[out.length - 1]
            if (last != '\n' && last != ' ' && last != '\t') out.append(' ')
        }

        /** 标记「写入了一个值」，同时校验顶层不允许多值。 */
        fun markValue(): Boolean {
            if (brackets.isEmpty()) {
                if (topLevelDone) return false
                topLevelDone = true
            } else {
                hasContent[hasContent.size - 1] = true
            }
            return true
        }

        while (i < n) {
            val c = src[i]
            when {
                c.isWhitespace() -> i++    // 原有空白一律丢弃，缩进由本函数重新生成

                c == '"' || c == '\'' -> {
                    val end = scanJsonString(src, i) ?: return null
                    prepareForToken()
                    out.append(src, i, end)
                    if (!markValue()) return null
                    i = end
                }

                // 注释（JSONC / JSON5）：当不透明 token 原样保留
                c == '/' && i + 1 < n && src[i + 1] == '/' -> {
                    var j = i
                    while (j < n && src[j] != '\n') j++
                    flushPending()
                    commentStart = out.length
                    spaceBeforeComment()
                    out.append(src, i, j)
                    hardBreak = true
                    i = j
                }

                c == '/' && i + 1 < n && src[i + 1] == '*' -> {
                    val end = src.indexOf("*/", i + 2)
                    if (end < 0) return null
                    flushPending()
                    spaceBeforeComment()
                    out.append(src, i, end + 2)
                    i = end + 2
                }

                c == '{' || c == '[' -> {
                    prepareForToken()
                    out.append(c)
                    // 先记父层有内容，再入栈
                    if (brackets.isNotEmpty()) {
                        hasContent[hasContent.size - 1] = true
                    } else if (topLevelDone) {
                        return null
                    } else {
                        topLevelDone = true
                    }
                    brackets.add(c)
                    hasContent.add(false)
                    pendingNewline = true
                    i++
                }

                c == '}' || c == ']' -> {
                    if (brackets.isEmpty()) return null
                    val open = brackets.removeAt(brackets.size - 1)
                    val hadContent = hasContent.removeAt(hasContent.size - 1)
                    if ((open == '{') != (c == '}')) return null
                    // 空容器保持 `{}` / `[]` 在同一行；但行注释后必须换行，否则闭括号被吞
                    if (hadContent || hardBreak) newline(brackets.size)
                    out.append(c)
                    pendingNewline = false
                    hardBreak = false
                    i++
                }

                c == ',' -> {
                    if (brackets.isEmpty()) return null   // 顶层逗号 → 不是合法 JSON
                    if (hardBreak && commentStart >= 0) {
                        // 逗号紧跟行注释：注释占满了一行，逗号挪到注释**前面**，
                        // 否则会变成孤零零占一行的 `,`（`1// c\n,`）。
                        out.insert(commentStart, ',')
                        hardBreak = false
                        commentStart = -1
                        pendingNewline = true
                        i++
                    } else {
                        flushPending()
                        out.append(',')
                        pendingNewline = true
                        commentStart = -1
                        i++
                    }
                }

                c == ':' -> {
                    if (brackets.isEmpty()) return null
                    flushPending()
                    out.append(": ")
                    pendingNewline = false
                    i++
                }

                else -> {
                    // 数字 / true / false / null / JSON5 裸键：读到结构符或空白为止，原样搬运
                    var j = i
                    while (j < n) {
                        val ch = src[j]
                        if (ch.isWhitespace() || ch == ',' || ch == ':' ||
                            ch == '{' || ch == '}' || ch == '[' || ch == ']'
                        ) break
                        j++
                    }
                    if (j == i) return null   // 结构符都已单独分支，到这里说明有非法字符
                    prepareForToken()
                    out.append(src, i, j)
                    commentStart = -1
                    if (!markValue()) return null
                    i = j
                }
            }
        }

        // 走到这里 brackets 必为空：顶层逗号/冒号已在分支里直接判 null，
        // 容器闭合会清掉 pendingNewline，故无需再兜一次。
        if (brackets.isNotEmpty()) return null   // 括号未闭合
        return out.toString()
    }

    /** 从引号位置扫到闭合，返回闭合后下标；未闭合返回 null。 */
    private fun scanJsonString(src: String, start: Int): Int? {
        val quote = src[start]
        var j = start + 1
        while (j < src.length) {
            when (src[j]) {
                '\\' -> j += 2
                quote -> return j + 1
                else -> j++
            }
        }
        return null
    }

    // ==================== YAML ====================

    /**
     * YAML 是**缩进敏感**的，乱动就可能改坏数据，所以只做三件**证明安全**的事：
     *
     *  1. 正在块标量（`|` / `>`）里的行 **原样照抄，一个字节都不碰**；
     *  2. 其余行：行首 tab 换成空格（YAML 明文禁止用 tab 作缩进）、去掉行尾空白；
     *  3. 折叠连续空行，保证文件以单个换行结尾。
     *
     * **不重排缩进层级** —— 那需要真正的 YAML 解析器（多行标量、锚点、流式集合
     * 都会让朴素的缩进重算出错），风险远大于收益。
     *
     * 另外还跟踪跨行引号：多行双引号标量内部的行也必须原样保留，
     * 否则行尾空白清理会改到字符串内容里去。
     *
     * @return 格式化结果（YAML 清理永不失败）。
     */
    private fun formatYaml(src: String): String {
        val lines = src.split('\n')
        // 末尾换行单独处理：split 会让「以换行结尾」多出一个空串
        val hadTrailingNewline = lines.isNotEmpty() && lines.last().isEmpty()
        val body = if (hadTrailingNewline) lines.subList(0, lines.size - 1) else lines

        val out = ArrayList<String>(body.size)
        var blankRun = 0
        var blockBaseIndent = -1   // >=0 表示正处于块标量内，值为引入行的缩进
        var openQuote: Char = ' '  // ' ' = 无；否则为未闭合的引号

        for (raw in body) {
            val line = removeCr(raw)

            // ---- 状态 1：块标量内部，逐字节原样保留 ----
            if (blockBaseIndent >= 0) {
                val trimmed = line.trimStart(' ', '\t')
                if (trimmed.isEmpty()) {
                    // 块标量里的空行也是内容，不能折叠
                    out.add(raw)
                    continue
                }
                if (leadingSpaces(line) > blockBaseIndent) {
                    out.add(raw)
                    continue
                }
                blockBaseIndent = -1   // 缩进回落 → 块标量结束
            }

            // ---- 状态 2：跨行引号内部，原样保留 ----
            if (openQuote != ' ') {
                openQuote = scanYamlQuotes(line, openQuote, from = 0)
                out.add(raw)
                continue
            }

            // ---- 正常行：只动缩进 tab 与行尾空白 ----
            val leading = line.takeWhile { it == ' ' || it == '\t' }
            val rest = line.substring(leading.length)
            var cleaned = expandLeadingTabs(leading) + rest.trimEnd(' ', '\t')

            // 先探引号是否跨行：跨行则本行不改（宁可少格式化，不可改坏字符串内容）
            val quoteAfter = scanYamlQuotes(cleaned, ' ', from = leading.length)
            if (quoteAfter != ' ') {
                out.add(raw)
                openQuote = quoteAfter
                continue
            }
            // 清理会波及多行标量，同样原样保留
            if (startsBlockScalar(cleaned)) {
                out.add(raw)
                blockBaseIndent = leadingSpaces(line)
                blankRun = 0
                continue
            }

            if (cleaned.isEmpty()) {
                blankRun++
                if (blankRun > 1) continue   // 折叠连续空行
                out.add(cleaned)
                continue
            }
            blankRun = 0
            out.add(cleaned)
        }

        // 仍在块标量 / 跨行引号里收尾 → 末尾空行属于内容，不做归一化。
        // 但 hadTrailingNewline 已经被前面剥掉了，必须原样还回去：
        // 块标量的结尾换行本身就是标量的值（尤其 `|+` 这种保留换行的指示符）。
        if (blockBaseIndent >= 0 || openQuote != ' ') {
            val text = out.joinToString("\n")
            return if (hadTrailingNewline && text.isNotEmpty()) text + "\n" else text
        }

        val tail = out.dropWhile { it.isEmpty() }.dropLastWhile { it.isEmpty() }
        if (tail.isEmpty()) return ""
        return (tail + "").joinToString("\n")   // 保证以单个换行结尾
    }

    /** 去掉行尾 `\r` —— Content 会在应用时按其 lineSeparator 重新生成行尾。 */
    private fun removeCr(line: String): String = if (line.endsWith('\r')) line.dropLast(1) else line

    private fun leadingSpaces(line: String): Int {
        var i = 0
        while (i < line.length && (line[i] == ' ' || line[i] == '\t')) i++
        return i
    }

    /** YAML 禁止用 tab 作缩进，行首 tab 按 2 空格展开（只碰行首，行内不动）。 */
    private fun expandLeadingTabs(leading: String): String {
        if (leading.indexOf('\t') < 0) return leading
        var spaces = 0
        for (ch in leading) spaces += if (ch == '\t') 2 else 1
        return " ".repeat(spaces)
    }

    /**
     * 块标量起始：`|` / `>` 出现在行尾（可带 `+`/`-`、缩进指示、`# 注释`）。
     *
     * 用「出现在行尾」而非精确的语法解析 —— 判断**宁可过宽**：
     * 误判成块标量只会让那几行不被清理（少格式化，无害），
     * 漏判才会在块标量里乱改（会改坏数据）。
     */
    private val BLOCK_SCALAR = Regex("""[|>][+-]?\d*\s*(#.*)?$""")

    private fun startsBlockScalar(line: String): Boolean {
        val t = line.trim()
        if (t.isEmpty()) return false
        // 明显不是标量指示的行（例如 `command: echo a > b`）提前排除
        return BLOCK_SCALAR.containsMatchIn(t) && !t.contains("\"") && !t.contains("'")
    }

    /**
     * 从 [from] 扫描引号状态。YAML 规则：双引号里 `\` 转义；
     * 单引号里 `''` 是转义、单个 `'` 结束；`#` 前有空格（或行首）即进入注释，停止扫描。
     *
     * @return 行尾的引号状态（`' '` 表示已全部闭合）。
     */
    private fun scanYamlQuotes(line: String, initial: Char, from: Int): Char {
        var q = if (initial == ' ') ' ' else initial
        var i = from
        while (i < line.length) {
            val c = line[i]
            if (q == ' ') {
                if (c == '"' || c == '\'') {
                    q = c
                } else if (c == '#' && (i == 0 || line[i - 1] == ' ' || line[i - 1] == '\t')) {
                    break
                }
            } else if (q == '"') {
                if (c == '\\') {
                    i++   // 跳过被转义的下一个字符
                } else if (c == '"') {
                    q = ' '
                }
            } else {
                // 单引号：`''` 是转义 → 跳过它并**留在标量内**；单个 `'` 才闭合。
                // 原实现两个分支都把 q 置空，导致 `it''s` 被当成标量结束，
                // 后面的行会被误判为「已脱离引号」而参与行尾清理 → 改坏字符串内容。
                if (c == '\'') {
                    if (i + 1 < line.length && line[i + 1] == '\'') {
                        i++
                    } else {
                        q = ' '
                    }
                }
            }
            i++
        }
        return q
    }
}
