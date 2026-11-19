package com.UIN.Tool.utils

import com.UIN.Tool.R
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.node.AbstractVisitor
import org.commonmark.node.Code
import org.commonmark.node.HardLineBreak
import org.commonmark.node.Heading
import org.commonmark.node.Node
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.Text
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.AttributeProvider
import org.commonmark.renderer.html.AttributeProviderContext
import org.commonmark.renderer.html.AttributeProviderFactory
import org.commonmark.renderer.html.HtmlRenderer

/**
 * Markdown 渲染器
 * 完全复用 Java 版本的逻辑，转换为 Kotlin
 */
object MarkdownRenderer {

    // E-15：解析规模上限（行内正则 + 逐行扫描近似 O(n²)，超大文档会卡死渲染线程）
    private const val MAX_PARSE_CHARS = 512 * 1024

    /**
     * 将 Markdown 转换为 HTML。
     *
     * 保留「正文内嵌目录」的老行为给帮助页 / 更新日志用；Markdown 阅读页改用
     * [render] 并把目录交给标题栏侧边栏（l.txt 第 1 条）。
     */
    fun toHtml(markdown: String): String = render(markdown, includeToc = true).html

    /** Markdown 渲染结果：HTML + 标题目录（供阅读页侧边栏跳转）。 */
    data class Rendered(
        val html: String,
        val toc: List<TocItem>
    )

    /** 一个可跳转的标题（[id] 已注入正文对应 `<h*>` 的 id）。 */
    data class TocItem(
        val level: Int,
        val id: String,
        val title: String
    )

    /**
     * l.txt 第 1 条：渲染 Markdown，目录**不再塞进正文**。
     *
     * @param includeToc true 时正文顶部仍带可折叠目录（帮助页等旧入口）；阅读页传 false，
     *        目录只经返回值 [Rendered.toc] 交给侧边栏。
     */
    fun render(markdown: String, includeToc: Boolean = false): Rendered {
        if (markdown.isEmpty()) {
            return Rendered(getEmptyHtml(), emptyList())
        }

        var truncated = false
        val source = if (markdown.length > MAX_PARSE_CHARS) {
            var cut = markdown.lastIndexOf('\n', MAX_PARSE_CHARS)
            if (cut <= 0) cut = MAX_PARSE_CHARS
            truncated = true
            markdown.substring(0, cut)
        } else {
            markdown
        }

        val html = StringBuilder()

        // HTML 头部
        html.append("<!DOCTYPE html>\n")
        html.append("<html>\n")
        html.append("<head>\n")
        html.append("    <meta charset='UTF-8'>\n")
        html.append("    <meta name='viewport' content='width=device-width, initial-scale=1.0, minimum-scale=1.0, maximum-scale=5.0, user-scalable=yes'>\n")
        html.append("    <title>Document</title>\n")
        html.append("    <style>\n")
        html.append(getCss())
        html.append("    </style>\n")
        html.append("    <script>\n")
        html.append(getJavaScript())
        html.append("    </script>\n")
        html.append("</head>\n")
        html.append("<body>\n")
        html.append("<div class='container'>\n")

        // 解析 Markdown
        val result = parseMarkdownWithToc(source)
        val tocHtml = generateTocHtml(result.tocItems)

        // 添加目录（仅 includeToc；阅读页走侧边栏）
        if (includeToc && result.tocItems.isNotEmpty()) {
            html.append("<div class='toc-container'>\n")
            html.append(Str.get(R.string.div_class_toc_header_onclick_togglet))
            html.append("    <div class='toc-content' id='tocContent'>\n")
            html.append("        <ul class='toc-list'>\n")
            html.append(tocHtml)
            html.append("        </ul>\n")
            html.append("    </div>\n")
            html.append("</div>\n")
        }

        html.append(result.content)

        if (truncated) {
            html.append("<p class='truncated'>")
            html.append(Str.get(R.string.markdown_truncated_notice))
            html.append("</p>\n")
        }

        html.append("</div>\n")
        html.append("</body>\n")
        html.append("</html>")

        return Rendered(html.toString(), result.tocItems)
    }

    private fun getCss(): String {
        // E-14：全部颜色随深/浅主题切换（原为浅色硬编码 → 深色模式下黑字压深底读不清）
        val dark = UIConfig.shouldUseDarkTheme()
        val text = if (dark) "#E3E3E3" else "#333"
        val textSecondary = if (dark) "#BDBDBD" else "#555"
        val heading1 = if (dark) "#B0BEC5" else "#37474F"
        val heading2 = if (dark) "#CFD8DC" else "#455A64"
        val heading34 = if (dark) "#CFD8DC" else "#546E7A"
        val heading56 = if (dark) "#B0BEC5" else "#78909C"
        val headingBorder = if (dark) "#3A3F44" else "#E0E0E0"
        val surface = if (dark) "#26282B" else "#FFFFFF"
        val surfaceVariant = if (dark) "#2F3134" else "#F8F9FA"
        val surfaceVariantHover = if (dark) "#3A3C40" else "#E9ECEF"
        val listHoverBg = if (dark) "#33363A" else "#F0F0F0"
        val link = if (dark) "#82B1FF" else "#37474F"
        val linkBorder = if (dark) "#3A3F44" else "#CFD8DC"
        val inlineCodeBg = if (dark) "#2B2E31" else "#F4F4F4"
        val quoteBg = if (dark) "#26282B" else "#F5F5F5"
        val tableBorder = if (dark) "#3A3F44" else "#DDDDDD"
        val thBg = if (dark) "#2F3134" else "#F0F0F0"
        val rowBg = if (dark) "#232527" else "#F9F9F9"
        val strongColor = if (dark) "#ECEFF1" else "#2C3E50"
        return """
            * { margin: 0; padding: 0; box-sizing: border-box; }
            /* o.txt 第 6 条：正文宽度必须严格等于可视宽度 —— 只要根元素允许横向滚动，
               右滑就会滑出一段没有任何内容的空白。宽内容（代码块 / 表格）各自有
               overflow-x: auto，自己内部横滚，不受这里影响。 */
            html, body { width: 100%; max-width: 100%; overflow-x: hidden; }
            body { 
                font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Helvetica, Arial, sans-serif; 
                line-height: 1.6; 
                color: $text; 
                background: transparent; 
                padding: 16px; 
                margin: 0;
                -webkit-tap-highlight-color: transparent;
            }
            .container { 
                max-width: 900px; 
                margin: 0 auto; 
                background: transparent; 
                padding: 0; 
                box-shadow: none;
                border: none;
            }
            .toc-container { 
                background: $surface; 
                border-radius: 12px; 
                margin: 16px 0 24px; 
                box-shadow: 0 1px 3px rgba(0,0,0,0.1); 
                overflow: hidden;
            }
            .toc-header { 
                padding: 12px 16px; 
                background: $surfaceVariant; 
                font-weight: 600; 
                font-size: 16px; 
                cursor: pointer; 
                user-select: none;
                display: flex;
                justify-content: space-between;
                align-items: center;
            }
            .toc-header:hover { background: $surfaceVariantHover; }
            .toc-toggle { font-size: 12px; transition: transform 0.2s; }
            .toc-toggle.collapsed { transform: rotate(-90deg); }
            .toc-content { padding: 8px 0 12px 0; }
            .toc-content.collapsed { display: none; }
            .toc-list { list-style: none; padding-left: 0; margin: 0; }
            .toc-list li { margin: 4px 0; }
            .toc-list a { 
                display: block; 
                padding: 6px 16px; 
                color: $textSecondary; 
                text-decoration: none; 
                font-size: 14px; 
                border-left: 3px solid transparent; 
                transition: all 0.2s;
            }
            .toc-list a:hover { 
                background: $listHoverBg; 
                color: $link; 
                border-left-color: $link; 
            }
            .code-block-wrapper { 
                position: relative; 
                margin: 16px 0; 
                border-radius: 8px; 
                overflow: hidden;
            }
            .copy-btn { 
                position: absolute; 
                top: 8px; 
                right: 8px; 
                background: rgba(45,45,45,0.8); 
                border: none; 
                color: #ccc; 
                padding: 4px 10px; 
                border-radius: 6px; 
                font-size: 12px; 
                cursor: pointer; 
                backdrop-filter: blur(4px);
                transition: all 0.2s;
                font-family: monospace;
                z-index: 10;
            }
            .copy-btn:hover { 
                background: rgba(45,45,45,0.9); 
                color: white; 
            }
            .copy-btn.copied { 
                background: #4caf50; 
                color: white; 
            }
            h1, h2, h3, h4, h5, h6 { 
                scroll-margin-top: 70px; 
            }
            h1 { font-size: 28px; margin: 20px 0 12px; padding-bottom: 8px; border-bottom: 2px solid $heading1; color: $heading1; }
            h2 { font-size: 24px; margin: 18px 0 10px; padding-bottom: 6px; border-bottom: 1px solid $headingBorder; color: $heading2; }
            h3 { font-size: 20px; margin: 16px 0 10px; color: $heading34; }
            h4 { font-size: 18px; margin: 14px 0 8px; color: $heading34; }
            h5 { font-size: 16px; margin: 12px 0 6px; color: $heading56; }
            h6 { font-size: 14px; margin: 10px 0 4px; color: $heading56; }
            p { margin: 12px 0; line-height: 1.7; }
            a { color: $link; text-decoration: none; border-bottom: 1px solid $linkBorder; }
            code { 
                background: $inlineCodeBg; 
                padding: 2px 6px; 
                border-radius: 4px; 
                font-family: 'SF Mono', Monaco, 'Cascadia Code', 'Roboto Mono', Consolas, monospace; 
                font-size: 13px; 
                color: #c7254e; 
            }
            pre { 
                background: #2d2d2d; 
                padding: 16px; 
                margin: 0; 
                overflow-x: auto; 
                -webkit-overflow-scrolling: touch;
            }
            pre code { 
                background: transparent; 
                color: #f8f8f2; 
                padding: 0; 
                font-size: 13px; 
                line-height: 1.5; 
            }
            ul, ol { margin: 12px 0; padding-left: 28px; }
            li { margin: 6px 0; }
            blockquote { 
                margin: 16px 0; 
                padding: 12px 20px; 
                background: $quoteBg; 
                border-left: 4px solid $heading1; 
                color: $textSecondary; 
            }
            table { 
                width: 100%; 
                border-collapse: collapse; 
                margin: 16px 0; 
                font-size: 14px; 
                display: block;
                overflow-x: auto;
                -webkit-overflow-scrolling: touch;
            }
            th, td { 
                border: 1px solid $tableBorder; 
                padding: 10px 12px; 
                text-align: left; 
            }
            th { background: $thBg; font-weight: 600; }
            tr:nth-child(even) { background: $rowBg; }
            hr { border: none; border-top: 1px solid $headingBorder; margin: 24px 0; }
            img { 
                max-width: 100%; 
                height: auto; 
                border-radius: 8px; 
                display: block;
                margin: 12px 0;
            }
            strong { font-weight: 700; color: $strongColor; }
            em { font-style: italic; }
            del { text-decoration: line-through; color: #999; }
            .table-wrapper { overflow-x: auto; margin: 16px 0; -webkit-overflow-scrolling: touch; }
            @media (max-width: 600px) { 
                body { padding: 12px; } 
                .container { padding: 0; } 
                h1 { font-size: 24px; } 
                h2 { font-size: 20px; } 
                h3 { font-size: 18px; } 
                th, td { padding: 6px 8px; } 
            }
            html { scroll-behavior: smooth; }
            /* 用户反馈 d11：文档滑条加粗到 16px 并圆角 —— 系统默认那根细滑条在手机上点不中。
               轨道透明，只留拇指；拇指长度由内容比例决定（CSS 管不了），
               所以「长」靠下面 WebView 关掉自动淡出来保证它一直在。 */
            ::-webkit-scrollbar { width: 16px; height: 16px; }
            ::-webkit-scrollbar-track { background: transparent; }
            ::-webkit-scrollbar-thumb {
                background: rgba(128, 128, 128, 0.55);
                background-clip: padding-box;
                border: 4px solid transparent;
                border-radius: 999px;
            }
            ::-webkit-scrollbar-thumb:hover {
                background: rgba(128, 128, 128, 0.85);
                background-clip: padding-box;
            }
            ::-webkit-scrollbar-corner { background: transparent; }
        """.trimIndent()
    }

    private fun getJavaScript(): String {
        return """
            function toggleToc() {
                var content = document.getElementById('tocContent');
                var toggle = document.querySelector('.toc-toggle');
                if (content) {
                    content.classList.toggle('collapsed');
                    toggle.classList.toggle('collapsed');
                }
            }
            
            function copyCode(btn) {
                var wrapper = btn.parentElement;
                var code = wrapper.querySelector('code');
                var text = code.innerText;
                
                if (navigator.clipboard && navigator.clipboard.writeText) {
                    navigator.clipboard.writeText(text).then(function() {
                        showCopySuccess(btn);
                    }).catch(function(err) {
                        console.error('${Str.get(R.string.clipboard_api_failed_js)}', err);
                        fallbackCopy(text, btn);
                    });
                } else {
                    fallbackCopy(text, btn);
                }
            }
            
            function fallbackCopy(text, btn) {
                var textarea = document.createElement('textarea');
                textarea.value = text;
                textarea.style.position = 'fixed';
                textarea.style.top = '-9999px';
                textarea.style.left = '-9999px';
                document.body.appendChild(textarea);
                textarea.select();
                textarea.setSelectionRange(0, textarea.value.length);
                try {
                    document.execCommand('copy');
                    showCopySuccess(btn);
                } catch(err) {
                    console.error('${Str.get(R.string.copy_failed_js)}', err);
                    btn.textContent = '${Str.get(R.string.failed_js)}';
                    setTimeout(function() {
                        btn.textContent = '${Str.get(R.string.copy_js)}';
                    }, 2000);
                }
                document.body.removeChild(textarea);
            }
            
            function showCopySuccess(btn) {
                btn.textContent = '${Str.get(R.string.copied_js)}';
                btn.classList.add('copied');
                setTimeout(function() {
                    btn.textContent = '${Str.get(R.string.copy_js)}';
                    btn.classList.remove('copied');
                }, 2000);
            }
            
            document.addEventListener('DOMContentLoaded', function() {
                var pres = document.querySelectorAll('pre');
                pres.forEach(function(pre) {
                    if (pre.parentElement && !pre.parentElement.classList.contains('code-block-wrapper')) {
                        var wrapper = document.createElement('div');
                        wrapper.className = 'code-block-wrapper';
                        pre.parentNode.insertBefore(wrapper, pre);
                        wrapper.appendChild(pre);
                        var btn = document.createElement('button');
                        btn.className = 'copy-btn';
                        btn.textContent = '${Str.get(R.string.copy_js)}';
                        btn.onclick = function() { copyCode(this); };
                        wrapper.appendChild(btn);
                    }
                });
                
                // 表格横滚：commonmark 出的是裸 <table>，包一层才吃得到 .table-wrapper 的 overflow-x。
                // 与上面 <pre> 同一套「缺 wrapper 才包」的判断，重复渲染不会套两层。
                var tables = document.querySelectorAll('table');
                tables.forEach(function(tbl) {
                    if (tbl.parentElement && !tbl.parentElement.classList.contains('table-wrapper')) {
                        var tw = document.createElement('div');
                        tw.className = 'table-wrapper';
                        tbl.parentNode.insertBefore(tw, tbl);
                        tw.appendChild(tbl);
                    }
                });
                
                var headers = document.querySelectorAll('h1, h2, h3, h4, h5, h6');
                headers.forEach(function(header) {
                    if (!header.id) {
                        // 兜底：正常标题 id 由 AttributeProvider 注入，这里只补漏网的。规则必须与
                        // Kotlin 侧 slugify() 同源（GitHub 口径）。本函数是 Kotlin **原始字符串**，
                        // 反斜杠只写一个 —— 原先写成 \\w，JS 实际拿到「字面反斜杠 + w」，
                        // 这条兜底从一开始就是坏的（只是渲染器总设 id 才没暴露）。
                        var id = header.innerText.toLowerCase()
                            .replace(/[^\w\u4e00-\u9fa5\s-]+/g, '')
                            .replace(/\s/g, '-')
                            .replace(/^-|-$/g, '');
                        header.id = id;
                    }
                });
                
                if (window.location.hash) {
                    var targetId = window.location.hash.substring(1);
                    var target = document.getElementById(targetId);
                    if (target) {
                        setTimeout(function() {
                            target.scrollIntoView({ behavior: 'smooth', block: 'start' });
                        }, 100);
                    }
                }
            });
        """.trimIndent()
    }

    private data class ParseResult(
        val content: String,
        val tocItems: List<TocItem>
    )

    /**
     * 解析 Markdown → HTML + 目录项。
     *
     * 解析交给 **commonmark-java**（CommonMark 规范实现，本项目早就在依赖里），
     * 不再逐行手写 —— 手写版的 `inList` 只是个布尔量，**列表永远只有一层**
     * （文档里 308 处缩进子项会被拍平），段落续行、引用块、转义也各有边界情况。
     * 这里只保留手写版才需要做的三件事：
     *   1. 给标题分配 id（GitHub 规则 + 重复去重），供顶部目录与正文手写目录 `(#...)` 跳转；
     *   2. 收集目录项；
     *   3. 交给 HtmlRenderer 出 HTML。
     * 复制按钮与表格横滚由页面 JS 自动补（`<pre>` / `<table>` 缺 wrapper 时包一层）。
     */
    private fun parseMarkdownWithToc(markdown: String): ParseResult {
        val extensions = listOf(TablesExtension.create())
        val document = Parser.builder().extensions(extensions).build().parse(markdown)

        // 第一遍：收标题、算 id。slug 用标题的**纯文本**口径（去掉 `**`、反引号等行内标记），
        // 与文档手写目录按 GitHub 规则生成的锚点一致。
        val tocItems = mutableListOf<TocItem>()
        val idByNode = HashMap<Node, String>()
        val seen = HashSet<String>()
        document.accept(object : AbstractVisitor() {
            override fun visit(heading: Heading) {
                val text = plainTextOf(heading)
                val id = slugify(text, seen)
                idByNode[heading] = id
                tocItems.add(TocItem(heading.level, id, text))
                super.visit(heading)
            }
        })

        // 第二遍：渲染，标题 id 由 AttributeProvider 注入。
        // （页面 JS 只在 `!header.id` 时才兜底，所以这里设了就不会被覆盖。）
        val body = HtmlRenderer.builder()
            .extensions(extensions)
            .attributeProviderFactory(object : AttributeProviderFactory {
                override fun create(context: AttributeProviderContext): AttributeProvider =
                    object : AttributeProvider {
                        override fun setAttributes(
                            node: Node,
                            tagName: String,
                            attributes: MutableMap<String, String>
                        ) {
                            if (node is Heading) idByNode[node]?.let { attributes["id"] = it }
                        }
                    }
            })
            .build()
            .render(document)

        return ParseResult(body, tocItems)
    }

    /** 取节点纯文本：标题里 `**粗体**`、`` `代码` `` 的标记要去掉，才能与浏览器 innerText 对齐。 */
    private fun plainTextOf(node: Node): String {
        val sb = StringBuilder()
        var child: Node? = node.firstChild
        while (child != null) {
            val cur = child
            when (cur) {
                is Text -> sb.append(cur.literal)
                is Code -> sb.append(cur.literal)
                is SoftLineBreak, is HardLineBreak -> sb.append(' ')
                else -> sb.append(plainTextOf(cur))
            }
            child = cur.next
        }
        return sb.toString()
    }

    /**
     * GitHub 兼容 slug + 重复去重：小写 → 删掉「非单词/非中文/非空白/非连字符」→
     * **每个**空白各转一个连字符 → 去首尾连字符；撞了补 `-1`、`-2`。
     *
     * 手写版是「非单词**连续段**压成一个连字符」，`1.1 核心功能` 会得到 `1-1-核心功能`，
     * 而文档手写目录按 GitHub 规则写的是 `#11-核心功能` —— 正文那份目录整片点不动。
     * 顶部自动目录因为 href 与 id 同源，始终自洽，所以这个错一直看不出来。
     */
    private fun slugify(text: String, seen: MutableSet<String>): String {
        var id = text.lowercase()
            .replace(Regex("[^\\w\\u4e00-\\u9fa5\\s-]+"), "")
            .replace(Regex("\\s"), "-")
            .replace(Regex("^-|-$"), "")
        if (!seen.add(id)) {
            var n = 1
            while (!seen.add("$id-$n")) n++
            id = "$id-$n"
        }
        return id
    }

    private fun generateTocHtml(tocItems: List<TocItem>): String {
        if (tocItems.isEmpty()) return ""

        val html = StringBuilder()
        var lastLevel = 1

        for (item in tocItems) {
            while (item.level > lastLevel) {
                html.append("<ul>\n")
                lastLevel++
            }
            while (item.level < lastLevel) {
                html.append("</ul>\n")
                lastLevel--
            }
            html.append("<li>")
            html.append("<a href='#").append(item.id).append("'>").append(escapeHtml(item.title)).append("</a>")
            html.append("</li>\n")
        }

        while (lastLevel > 1) {
            html.append("</ul>\n")
            lastLevel--
        }

        return html.toString()
    }

    private fun escapeHtml(text: String): String {
        if (text.isEmpty()) return ""
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }

    private fun getEmptyHtml(): String {
        // E-14：空文档页同样随主题切换
        val dark = UIConfig.shouldUseDarkTheme()
        val bg = if (dark) "#1E2124" else "#F5F5F5"
        val cardBg = if (dark) "#26282B" else "#FFFFFF"
        val body = if (dark) "#E3E3E3" else "#666666"
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset='UTF-8'>
                <meta name='viewport' content='width=device-width, initial-scale=1.0, minimum-scale=1.0, maximum-scale=5.0, user-scalable=yes'>
                <style>
                    body { 
                        font-family: sans-serif; 
                        padding: 20px; 
                        text-align: center; 
                        background: $bg; 
                        margin: 0;
                    }
                    .card { 
                        max-width: 400px; 
                        margin: 0 auto; 
                        background: $cardBg; 
                        border-radius: 16px; 
                        padding: 32px; 
                        box-shadow: 0 4px 12px rgba(0,0,0,0.1); 
                    }
                    h1 { color: #e74c3c; margin-bottom: 16px; }
                    p { color: $body; margin-bottom: 24px; }
                </style>
            </head>
            <body>
                <div class='card'>
                    <h1>${Str.get(R.string.empty_doc_h1)}</h1>
                    <p>${Str.get(R.string.empty_doc_p)}</p>
                </div>
            </body>
            </html>
        """.trimIndent()
    }
}