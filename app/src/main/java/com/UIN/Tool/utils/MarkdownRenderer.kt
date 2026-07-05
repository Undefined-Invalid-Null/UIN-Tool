package com.UIN.Tool.utils

import android.text.TextUtils
import java.util.regex.Pattern

/**
 * Markdown 渲染器
 * 完全复用 Java 版本的逻辑，转换为 Kotlin
 */
object MarkdownRenderer {

    /**
     * 将 Markdown 转换为 HTML
     */
    fun toHtml(markdown: String): String {
        if (markdown.isEmpty()) {
            return getEmptyHtml()
        }

        val html = StringBuilder()

        // HTML 头部
        html.append("<!DOCTYPE html>\n")
        html.append("<html>\n")
        html.append("<head>\n")
        html.append("    <meta charset='UTF-8'>\n")
        html.append("    <meta name='viewport' content='width=device-width, initial-scale=1.0, maximum-scale=5.0, user-scalable=yes'>\n")
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
        val result = parseMarkdownWithToc(markdown)
        val tocHtml = generateTocHtml(result.tocItems)

        // 添加目录
        if (result.tocItems.isNotEmpty()) {
            html.append("<div class='toc-container'>\n")
            html.append("    <div class='toc-header' onclick='toggleToc()'>📑 目录 <span class='toc-toggle'>▼</span></div>\n")
            html.append("    <div class='toc-content' id='tocContent'>\n")
            html.append("        <ul class='toc-list'>\n")
            html.append(tocHtml)
            html.append("        </ul>\n")
            html.append("    </div>\n")
            html.append("</div>\n")
        }

        html.append(result.content)

        html.append("</div>\n")
        html.append("</body>\n")
        html.append("</html>")

        return html.toString()
    }

    private fun getCss(): String {
        return """
            * { margin: 0; padding: 0; box-sizing: border-box; }
            body { 
                font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Helvetica, Arial, sans-serif; 
                line-height: 1.6; 
                color: #333; 
                background: #f5f5f5; 
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
                background: white; 
                border-radius: 12px; 
                margin: 16px 0 24px; 
                box-shadow: 0 1px 3px rgba(0,0,0,0.1); 
                overflow: hidden;
            }
            .toc-header { 
                padding: 12px 16px; 
                background: #f8f9fa; 
                font-weight: 600; 
                font-size: 16px; 
                cursor: pointer; 
                user-select: none;
                display: flex;
                justify-content: space-between;
                align-items: center;
            }
            .toc-header:hover { background: #e9ecef; }
            .toc-toggle { font-size: 12px; transition: transform 0.2s; }
            .toc-toggle.collapsed { transform: rotate(-90deg); }
            .toc-content { padding: 8px 0 12px 0; }
            .toc-content.collapsed { display: none; }
            .toc-list { list-style: none; padding-left: 0; margin: 0; }
            .toc-list li { margin: 4px 0; }
            .toc-list a { 
                display: block; 
                padding: 6px 16px; 
                color: #555; 
                text-decoration: none; 
                font-size: 14px; 
                border-left: 3px solid transparent; 
                transition: all 0.2s;
            }
            .toc-list a:hover { 
                background: #f0f0f0; 
                color: #37474F; 
                border-left-color: #37474F; 
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
            h1 { font-size: 28px; margin: 20px 0 12px; padding-bottom: 8px; border-bottom: 2px solid #37474F; color: #37474F; }
            h2 { font-size: 24px; margin: 18px 0 10px; padding-bottom: 6px; border-bottom: 1px solid #e0e0e0; color: #455A64; }
            h3 { font-size: 20px; margin: 16px 0 10px; color: #546E7A; }
            h4 { font-size: 18px; margin: 14px 0 8px; color: #607D8B; }
            h5 { font-size: 16px; margin: 12px 0 6px; color: #78909C; }
            h6 { font-size: 14px; margin: 10px 0 4px; color: #90A4AE; }
            p { margin: 12px 0; line-height: 1.7; }
            a { color: #37474F; text-decoration: none; border-bottom: 1px solid #CFD8DC; }
            code { 
                background: #f4f4f4; 
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
                background: #f5f5f5; 
                border-left: 4px solid #37474F; 
                color: #555; 
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
                border: 1px solid #ddd; 
                padding: 10px 12px; 
                text-align: left; 
            }
            th { background: #f0f0f0; font-weight: 600; }
            tr:nth-child(even) { background: #f9f9f9; }
            hr { border: none; border-top: 1px solid #e0e0e0; margin: 24px 0; }
            img { 
                max-width: 100%; 
                height: auto; 
                border-radius: 8px; 
                display: block;
                margin: 12px 0;
            }
            strong { font-weight: 700; color: #2c3e50; }
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
                        console.error('Clipboard API 失败:', err);
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
                    console.error('复制失败:', err);
                    btn.textContent = '❌ 失败';
                    setTimeout(function() {
                        btn.textContent = '📋 复制';
                    }, 2000);
                }
                document.body.removeChild(textarea);
            }
            
            function showCopySuccess(btn) {
                btn.textContent = '✓ 已复制';
                btn.classList.add('copied');
                setTimeout(function() {
                    btn.textContent = '📋 复制';
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
                        btn.textContent = '📋 复制';
                        btn.onclick = function() { copyCode(this); };
                        wrapper.appendChild(btn);
                    }
                });
                
                var headers = document.querySelectorAll('h1, h2, h3, h4, h5, h6');
                headers.forEach(function(header) {
                    if (!header.id) {
                        var text = header.innerText;
                        var id = text.toLowerCase().replace(/[^\\w\\u4e00-\\u9fa5]+/g, '-');
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

    private data class TocItem(
        val level: Int,
        val id: String,
        val title: String
    )

    private fun parseMarkdownWithToc(markdown: String): ParseResult {
        val result = StringBuilder()
        val tocItems = mutableListOf<TocItem>()
        val lines = markdown.split("\n")

        var inCodeBlock = false
        var inList = false
        var inOrderedList = false
        var inBlockquote = false
        val codeBlock = StringBuilder()
        val blockquoteContent = StringBuilder()
        var currentLanguage = ""

        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            val trimmedLine = line.trim()

            // 代码块
            if (trimmedLine.startsWith("```")) {
                if (!inCodeBlock) {
                    if (inList) {
                        result.append(if (inOrderedList) "</ol>\n" else "</ul>\n")
                        inList = false
                    }
                    if (inBlockquote) {
                        result.append("</blockquote>\n")
                        inBlockquote = false
                    }
                    inCodeBlock = true
                    currentLanguage = trimmedLine.substring(3).trim()
                    codeBlock.clear()
                } else {
                    inCodeBlock = false
                    result.append("<div class='code-block-wrapper'><pre><code")
                    if (currentLanguage.isNotEmpty()) {
                        result.append(" class='language-").append(escapeHtml(currentLanguage)).append("'")
                    }
                    result.append(">")
                    result.append(escapeHtml(codeBlock.toString()))
                    result.append("</code></pre><button class='copy-btn' onclick='copyCode(this)'>📋 复制</button></div>\n")
                }
                i++
                continue
            }

            if (inCodeBlock) {
                codeBlock.append(line).append("\n")
                i++
                continue
            }

            // 表格
            if (trimmedLine.contains("|") && trimmedLine.startsWith("|")) {
                if (inList) {
                    result.append(if (inOrderedList) "</ol>\n" else "</ul>\n")
                    inList = false
                }
                if (inBlockquote) {
                    result.append("</blockquote>\n")
                    inBlockquote = false
                }
                val tableHtml = parseTable(lines, i)
                if (tableHtml != null) {
                    result.append(tableHtml)
                    while (i + 1 < lines.size && lines[i + 1].contains("|")) {
                        i++
                    }
                    i++
                    continue
                }
            }

            // 标题
            var level = 0
            var titleText = ""
            var headerTag = ""

            when {
                trimmedLine.startsWith("# ") -> {
                    level = 1
                    titleText = trimmedLine.substring(2)
                    headerTag = "h1"
                }
                trimmedLine.startsWith("## ") -> {
                    level = 2
                    titleText = trimmedLine.substring(3)
                    headerTag = "h2"
                }
                trimmedLine.startsWith("### ") -> {
                    level = 3
                    titleText = trimmedLine.substring(4)
                    headerTag = "h3"
                }
                trimmedLine.startsWith("#### ") -> {
                    level = 4
                    titleText = trimmedLine.substring(5)
                    headerTag = "h4"
                }
                trimmedLine.startsWith("##### ") -> {
                    level = 5
                    titleText = trimmedLine.substring(6)
                    headerTag = "h5"
                }
                trimmedLine.startsWith("###### ") -> {
                    level = 6
                    titleText = trimmedLine.substring(7)
                    headerTag = "h6"
                }
            }

            if (level > 0) {
                if (inList) {
                    result.append(if (inOrderedList) "</ol>\n" else "</ul>\n")
                    inList = false
                }
                if (inBlockquote) {
                    result.append("</blockquote>\n")
                    inBlockquote = false
                }
                val id = titleText.lowercase()
                    .replace(Regex("[^\\w\\u4e00-\\u9fa5]+"), "-")
                    .replace(Regex("^-|-$"), "")
                tocItems.add(TocItem(level, id, titleText))
                result.append("<").append(headerTag).append(" id='").append(id).append("'>")
                result.append(parseInline(escapeHtml(titleText)))
                result.append("</").append(headerTag).append(">\n")
                i++
                continue
            }

            // 引用
            if (trimmedLine.startsWith(">")) {
                if (inList) {
                    result.append(if (inOrderedList) "</ol>\n" else "</ul>\n")
                    inList = false
                }
                if (!inBlockquote) {
                    inBlockquote = true
                    blockquoteContent.clear()
                }
                val quoteLine = trimmedLine.substring(1).trim()
                if (quoteLine.isEmpty() && blockquoteContent.isNotEmpty()) {
                    result.append("<blockquote>").append(parseInline(escapeHtml(blockquoteContent.toString()))).append("</blockquote>\n")
                    inBlockquote = false
                } else {
                    blockquoteContent.append(quoteLine).append("\n")
                }
                i++
                continue
            }

            if (inBlockquote && !trimmedLine.startsWith(">")) {
                result.append("<blockquote>").append(parseInline(escapeHtml(blockquoteContent.toString()))).append("</blockquote>\n")
                inBlockquote = false
            }

            // 分隔线
            if (trimmedLine.matches(Regex("^[-*_]{3,}$"))) {
                if (inList) {
                    result.append(if (inOrderedList) "</ol>\n" else "</ul>\n")
                    inList = false
                }
                if (inBlockquote) {
                    result.append("</blockquote>\n")
                    inBlockquote = false
                }
                result.append("<hr>\n")
                i++
                continue
            }

            // 无序列表
            if (trimmedLine.matches(Regex("^[-*+]\\s+.*"))) {
                if (!inList || inOrderedList) {
                    if (inList) result.append(if (inOrderedList) "</ol>\n" else "</ul>\n")
                    result.append("<ul>\n")
                    inList = true
                    inOrderedList = false
                }
                val content = trimmedLine.substring(1).trim()
                if (content.startsWith("[ ] ")) {
                    val taskContent = content.substring(4)
                    result.append("<li class='task-list-item'><input type='checkbox' disabled> ").append(parseInline(escapeHtml(taskContent))).append("</li>\n")
                } else if (content.startsWith("[x] ") || content.startsWith("[X] ")) {
                    val taskContent = content.substring(4)
                    result.append("<li class='task-list-item'><input type='checkbox' checked disabled> ").append(parseInline(escapeHtml(taskContent))).append("</li>\n")
                } else {
                    result.append("<li>").append(parseInline(escapeHtml(content))).append("</li>\n")
                }
                i++
                continue
            }

            // 有序列表
            if (trimmedLine.matches(Regex("^\\d+\\.\\s+.*"))) {
                if (!inList || !inOrderedList) {
                    if (inList) result.append(if (inOrderedList) "</ol>\n" else "</ul>\n")
                    result.append("<ol>\n")
                    inList = true
                    inOrderedList = true
                }
                val content = trimmedLine.substring(trimmedLine.indexOf('.') + 1).trim()
                result.append("<li>").append(parseInline(escapeHtml(content))).append("</li>\n")
                i++
                continue
            }

            // 空行
            if (trimmedLine.isEmpty()) {
                if (inList) {
                    result.append(if (inOrderedList) "</ol>\n" else "</ul>\n")
                    inList = false
                }
                i++
                continue
            }

            // 普通段落
            if (inList) {
                result.append(if (inOrderedList) "</ol>\n" else "</ul>\n")
                inList = false
            }

            val paragraph = StringBuilder(line)
            while (i + 1 < lines.size && lines[i + 1].trim().isNotEmpty() &&
                !lines[i + 1].trim().startsWith("#") &&
                !lines[i + 1].trim().startsWith("```") &&
                !lines[i + 1].trim().matches(Regex("^[-*+>\\d].*")) &&
                !lines[i + 1].trim().matches(Regex("^\\|.*\\|$"))) {
                i++
                paragraph.append("\n").append(lines[i])
            }

            result.append("<p>").append(parseInline(escapeHtml(paragraph.toString()))).append("</p>\n")
            i++
        }

        if (inCodeBlock) {
            result.append("<div class='code-block-wrapper'><pre><code>").append(escapeHtml(codeBlock.toString())).append("</code></pre><button class='copy-btn' onclick='copyCode(this)'>📋 复制</button></div>\n")
        }
        if (inList) {
            result.append(if (inOrderedList) "</ol>\n" else "</ul>\n")
        }
        if (inBlockquote) {
            result.append("<blockquote>").append(parseInline(escapeHtml(blockquoteContent.toString()))).append("</blockquote>\n")
        }

        return ParseResult(result.toString(), tocItems)
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

    private fun parseTable(lines: List<String>, startIndex: Int): String? {
        try {
            val rows = mutableListOf<Array<String>>()
            var i = startIndex

            while (i < lines.size && lines[i].trim().contains("|")) {
                var line = lines[i].trim()
                if (line.startsWith("|")) line = line.substring(1)
                if (line.endsWith("|")) line = line.substring(0, line.length - 1)
                val cells = line.split("|").map { it.trim() }.toTypedArray()
                rows.add(cells)
                i++
            }

            if (rows.size < 2) return null

            val hasSeparator = if (rows.size > 1) {
                rows[1].any { it.matches(Regex("^[-:]+[-:]*$")) }
            } else false

            val table = StringBuilder()
            table.append("<div class='table-wrapper'>\n")
            table.append("<table>\n")

            table.append("<thead>\n<tr>\n")
            val headers = rows[0]
            for (j in headers.indices) {
                var align = ""
                if (hasSeparator && rows.size > 1 && j < rows[1].size) {
                    val sep = rows[1][j]
                    align = when {
                        sep.startsWith(":") && sep.endsWith(":") -> " center"
                        sep.endsWith(":") -> " right"
                        sep.startsWith(":") -> " left"
                        else -> ""
                    }
                }
                table.append("<th style='text-align:").append(if (align.isEmpty()) "left" else align.trim()).append("'>")
                table.append(parseInline(escapeHtml(headers[j])))
                table.append("</th>\n")
            }
            table.append("</tr></thead>\n")

            table.append("<tbody>\n")
            val startRow = if (hasSeparator) 2 else 1
            for (r in startRow until rows.size) {
                table.append("<tr>\n")
                val cells = rows[r]
                for (j in cells.indices) {
                    table.append("<td>").append(parseInline(escapeHtml(cells[j]))).append("</td>\n")
                }
                table.append("</tr>\n")
            }
            table.append("</tbody>\n")
            table.append("</table>\n")
            table.append("</div>\n")

            return table.toString()
        } catch (e: Exception) {
            return null
        }
    }

    private fun parseInline(text: String): String {
        if (text.isEmpty()) return text

        var result = text

        // 粗体 **text** 或 __text__
        result = result.replace(Regex("\\*\\*([^*]+)\\*\\*"), "<strong>$1</strong>")
        result = result.replace(Regex("__([^_]+)__"), "<strong>$1</strong>")

        // 斜体 *text* 或 _text_
        result = result.replace(Regex("\\*([^*]+)\\*"), "<em>$1</em>")
        result = result.replace(Regex("_([^_]+)_"), "<em>$1</em>")

        // 删除线 ~~text~~
        result = result.replace(Regex("~~([^~]+)~~"), "<del>$1</del>")

        // 行内代码 `code`
        val codePattern = Pattern.compile("`([^`]+)`")
        var matcher = codePattern.matcher(result)
        val sb = StringBuffer()
        while (matcher.find()) {
            matcher.appendReplacement(sb, "<code>" + matcher.group(1) + "</code>")
        }
        matcher.appendTail(sb)
        result = sb.toString()

        // 图片 ![alt](url)
        val imgPattern = Pattern.compile("!\\[([^\\]]*)\\]\\(([^)]+)\\)")
        matcher = imgPattern.matcher(result)
        val sb2 = StringBuffer()
        while (matcher.find()) {
            val alt = matcher.group(1)
            val src = matcher.group(2)
            matcher.appendReplacement(sb2, "<img src='$src' alt='$alt'>")
        }
        matcher.appendTail(sb2)
        result = sb2.toString()

        // 链接 [text](url)
        val linkPattern = Pattern.compile("\\[([^\\]]+)\\]\\(([^)]+)\\)")
        matcher = linkPattern.matcher(result)
        val sb3 = StringBuffer()
        while (matcher.find()) {
            val text2 = matcher.group(1)
            val url = matcher.group(2)
            matcher.appendReplacement(sb3, "<a href='$url'>$text2</a>")
        }
        matcher.appendTail(sb3)
        result = sb3.toString()

        return result
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
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset='UTF-8'>
                <meta name='viewport' content='width=device-width, initial-scale=1.0, maximum-scale=5.0, user-scalable=yes'>
                <style>
                    body { 
                        font-family: sans-serif; 
                        padding: 20px; 
                        text-align: center; 
                        background: #f5f5f5; 
                        margin: 0;
                    }
                    .card { 
                        max-width: 400px; 
                        margin: 0 auto; 
                        background: white; 
                        border-radius: 16px; 
                        padding: 32px; 
                        box-shadow: 0 4px 12px rgba(0,0,0,0.1); 
                    }
                    h1 { color: #e74c3c; margin-bottom: 16px; }
                    p { color: #666; margin-bottom: 24px; }
                </style>
            </head>
            <body>
                <div class='card'>
                    <h1>📄 空文档</h1>
                    <p>文档内容为空</p>
                </div>
            </body>
            </html>
        """.trimIndent()
    }
}