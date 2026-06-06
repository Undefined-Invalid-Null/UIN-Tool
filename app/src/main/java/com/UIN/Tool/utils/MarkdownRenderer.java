package com.UIN.Tool.utils;

import android.text.TextUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Markdown 渲染器
 * 支持标题、列表、代码块、表格、链接、图片、粗体、斜体、引用等
 * 支持目录锚点跳转和代码复制（通过 Android JavaScript 接口）
 */
public class MarkdownRenderer {

    /**
     * 将 Markdown 转换为 HTML
     */
    public static String toHtml(String markdown) {
        if (TextUtils.isEmpty(markdown)) {
            return getEmptyHtml();
        }
        
        StringBuilder html = new StringBuilder();
        
        // HTML 头部
        html.append("<!DOCTYPE html>\n");
        html.append("<html>\n");
        html.append("<head>\n");
        html.append("    <meta charset='UTF-8'>\n");
        html.append("    <meta name='viewport' content='width=device-width, initial-scale=1.0, maximum-scale=5.0, user-scalable=yes'>\n");
        html.append("    <title>Document</title>\n");
        html.append("    <style>\n");
        html.append(getCss());
        html.append("    </style>\n");
        html.append("    <script>\n");
        html.append(getJavaScript());
        html.append("    </script>\n");
        html.append("</head>\n");
        html.append("<body>\n");
        html.append("<div class='container'>\n");

        // 解析 Markdown，同时收集标题用于目录
        ParseResult result = parseMarkdownWithToc(markdown);
        String tocHtml = generateTocHtml(result.tocItems);
        
        // 添加目录（如果存在）
        if (!result.tocItems.isEmpty()) {
            html.append("<div class='toc-container'>\n");
            html.append("    <div class='toc-header' onclick='toggleToc()'>📑 目录 <span class='toc-toggle'>▼</span></div>\n");
            html.append("    <div class='toc-content' id='tocContent'>\n");
            html.append("        <ul class='toc-list'>\n");
            html.append(tocHtml);
            html.append("        </ul>\n");
            html.append("    </div>\n");
            html.append("</div>\n");
        }
        
        html.append(result.content);

        html.append("</div>\n");
        html.append("</body>\n");
        html.append("</html>");
        
        return html.toString();
    }

    private static String getCss() {
        StringBuilder css = new StringBuilder();
        css.append("* { margin: 0; padding: 0; box-sizing: border-box; }\n");
        css.append("body { \n");
        css.append("    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Helvetica, Arial, sans-serif; \n");
        css.append("    line-height: 1.6; \n");
        css.append("    color: #333; \n");
        css.append("    background: #f5f5f5; \n");
        css.append("    padding: 16px; \n");
        css.append("    margin: 0;\n");
        css.append("    -webkit-tap-highlight-color: transparent;\n");
        css.append("}\n");
        css.append(".container { \n");
        css.append("    max-width: 900px; \n");
        css.append("    margin: 0 auto; \n");
        css.append("    background: transparent; \n");
        css.append("    padding: 0; \n");
        css.append("    box-shadow: none;\n");
        css.append("    border: none;\n");
        css.append("}\n");
        
        // 目录样式
        css.append(".toc-container { \n");
        css.append("    background: white; \n");
        css.append("    border-radius: 12px; \n");
        css.append("    margin: 16px 0 24px; \n");
        css.append("    box-shadow: 0 1px 3px rgba(0,0,0,0.1); \n");
        css.append("    overflow: hidden;\n");
        css.append("}\n");
        css.append(".toc-header { \n");
        css.append("    padding: 12px 16px; \n");
        css.append("    background: #f8f9fa; \n");
        css.append("    font-weight: 600; \n");
        css.append("    font-size: 16px; \n");
        css.append("    cursor: pointer; \n");
        css.append("    user-select: none;\n");
        css.append("    display: flex;\n");
        css.append("    justify-content: space-between;\n");
        css.append("    align-items: center;\n");
        css.append("}\n");
        css.append(".toc-header:hover { background: #e9ecef; }\n");
        css.append(".toc-toggle { font-size: 12px; transition: transform 0.2s; }\n");
        css.append(".toc-toggle.collapsed { transform: rotate(-90deg); }\n");
        css.append(".toc-content { padding: 8px 0 12px 0; }\n");
        css.append(".toc-content.collapsed { display: none; }\n");
        css.append(".toc-list { list-style: none; padding-left: 0; margin: 0; }\n");
        css.append(".toc-list li { margin: 4px 0; }\n");
        css.append(".toc-list a { \n");
        css.append("    display: block; \n");
        css.append("    padding: 6px 16px; \n");
        css.append("    color: #555; \n");
        css.append("    text-decoration: none; \n");
        css.append("    font-size: 14px; \n");
        css.append("    border-left: 3px solid transparent; \n");
        css.append("    transition: all 0.2s;\n");
        css.append("}\n");
        css.append(".toc-list a:hover { \n");
        css.append("    background: #f0f0f0; \n");
        css.append("    color: #37474F; \n");
        css.append("    border-left-color: #37474F; \n");
        css.append("}\n");
        
        // 代码块复制按钮样式
        css.append(".code-block-wrapper { \n");
        css.append("    position: relative; \n");
        css.append("    margin: 16px 0; \n");
        css.append("    border-radius: 8px; \n");
        css.append("    overflow: hidden;\n");
        css.append("}\n");
        css.append(".copy-btn { \n");
        css.append("    position: absolute; \n");
        css.append("    top: 8px; \n");
        css.append("    right: 8px; \n");
        css.append("    background: rgba(45,45,45,0.8); \n");
        css.append("    border: none; \n");
        css.append("    color: #ccc; \n");
        css.append("    padding: 4px 10px; \n");
        css.append("    border-radius: 6px; \n");
        css.append("    font-size: 12px; \n");
        css.append("    cursor: pointer; \n");
        css.append("    backdrop-filter: blur(4px);\n");
        css.append("    transition: all 0.2s;\n");
        css.append("    font-family: monospace;\n");
        css.append("    z-index: 10;\n");
        css.append("}\n");
        css.append(".copy-btn:hover { \n");
        css.append("    background: rgba(45,45,45,0.9); \n");
        css.append("    color: white; \n");
        css.append("}\n");
        css.append(".copy-btn.copied { \n");
        css.append("    background: #4caf50; \n");
        css.append("    color: white; \n");
        css.append("}\n");
        
        // 标题锚点样式
        css.append("h1, h2, h3, h4, h5, h6 { \n");
        css.append("    scroll-margin-top: 70px; \n");
        css.append("}\n");
        
        // 其他样式
        css.append("h1 { font-size: 28px; margin: 20px 0 12px; padding-bottom: 8px; border-bottom: 2px solid #37474F; color: #37474F; }\n");
        css.append("h2 { font-size: 24px; margin: 18px 0 10px; padding-bottom: 6px; border-bottom: 1px solid #e0e0e0; color: #455A64; }\n");
        css.append("h3 { font-size: 20px; margin: 16px 0 10px; color: #546E7A; }\n");
        css.append("h4 { font-size: 18px; margin: 14px 0 8px; color: #607D8B; }\n");
        css.append("h5 { font-size: 16px; margin: 12px 0 6px; color: #78909C; }\n");
        css.append("h6 { font-size: 14px; margin: 10px 0 4px; color: #90A4AE; }\n");
        css.append("p { margin: 12px 0; line-height: 1.7; }\n");
        css.append("a { color: #37474F; text-decoration: none; border-bottom: 1px solid #CFD8DC; }\n");
        css.append("code { \n");
        css.append("    background: #f4f4f4; \n");
        css.append("    padding: 2px 6px; \n");
        css.append("    border-radius: 4px; \n");
        css.append("    font-family: 'SF Mono', Monaco, 'Cascadia Code', 'Roboto Mono', Consolas, monospace; \n");
        css.append("    font-size: 13px; \n");
        css.append("    color: #c7254e; \n");
        css.append("}\n");
        css.append("pre { \n");
        css.append("    background: #2d2d2d; \n");
        css.append("    padding: 16px; \n");
        css.append("    margin: 0; \n");
        css.append("    overflow-x: auto; \n");
        css.append("    -webkit-overflow-scrolling: touch;\n");
        css.append("}\n");
        css.append("pre code { \n");
        css.append("    background: transparent; \n");
        css.append("    color: #f8f8f2; \n");
        css.append("    padding: 0; \n");
        css.append("    font-size: 13px; \n");
        css.append("    line-height: 1.5; \n");
        css.append("}\n");
        css.append("ul, ol { margin: 12px 0; padding-left: 28px; }\n");
        css.append("li { margin: 6px 0; }\n");
        css.append("blockquote { \n");
        css.append("    margin: 16px 0; \n");
        css.append("    padding: 12px 20px; \n");
        css.append("    background: #f5f5f5; \n");
        css.append("    border-left: 4px solid #37474F; \n");
        css.append("    color: #555; \n");
        css.append("}\n");
        css.append("table { \n");
        css.append("    width: 100%; \n");
        css.append("    border-collapse: collapse; \n");
        css.append("    margin: 16px 0; \n");
        css.append("    font-size: 14px; \n");
        css.append("    display: block;\n");
        css.append("    overflow-x: auto;\n");
        css.append("    -webkit-overflow-scrolling: touch;\n");
        css.append("}\n");
        css.append("th, td { \n");
        css.append("    border: 1px solid #ddd; \n");
        css.append("    padding: 10px 12px; \n");
        css.append("    text-align: left; \n");
        css.append("}\n");
        css.append("th { background: #f0f0f0; font-weight: 600; }\n");
        css.append("tr:nth-child(even) { background: #f9f9f9; }\n");
        css.append("hr { border: none; border-top: 1px solid #e0e0e0; margin: 24px 0; }\n");
        css.append("img { \n");
        css.append("    max-width: 100%; \n");
        css.append("    height: auto; \n");
        css.append("    border-radius: 8px; \n");
        css.append("    display: block;\n");
        css.append("    margin: 12px 0;\n");
        css.append("}\n");
        css.append("strong { font-weight: 700; color: #2c3e50; }\n");
        css.append("em { font-style: italic; }\n");
        css.append("del { text-decoration: line-through; color: #999; }\n");
        css.append(".table-wrapper { overflow-x: auto; margin: 16px 0; -webkit-overflow-scrolling: touch; }\n");
        css.append("@media (max-width: 600px) { \n");
        css.append("    body { padding: 12px; } \n");
        css.append("    .container { padding: 0; } \n");
        css.append("    h1 { font-size: 24px; } \n");
        css.append("    h2 { font-size: 20px; } \n");
        css.append("    h3 { font-size: 18px; } \n");
        css.append("    th, td { padding: 6px 8px; } \n");
        css.append("}\n");
        css.append("html { scroll-behavior: smooth; }\n");
        return css.toString();
    }

    private static String getJavaScript() {
        StringBuilder js = new StringBuilder();
        js.append("function toggleToc() {\n");
        js.append("    var content = document.getElementById('tocContent');\n");
        js.append("    var toggle = document.querySelector('.toc-toggle');\n");
        js.append("    if (content) {\n");
        js.append("        content.classList.toggle('collapsed');\n");
        js.append("        toggle.classList.toggle('collapsed');\n");
        js.append("    }\n");
        js.append("}\n");
        js.append("\n");
        js.append("// 复制代码函数 - 使用 Clipboard API\n");
        js.append("function copyCode(btn) {\n");
        js.append("    var wrapper = btn.parentElement;\n");
        js.append("    var code = wrapper.querySelector('code');\n");
        js.append("    var text = code.innerText;\n");
        js.append("    \n");
        js.append("    // 优先使用 Clipboard API\n");
        js.append("    if (navigator.clipboard && navigator.clipboard.writeText) {\n");
        js.append("        navigator.clipboard.writeText(text).then(function() {\n");
        js.append("            showCopySuccess(btn);\n");
        js.append("        }).catch(function(err) {\n");
        js.append("            console.error('Clipboard API 失败:', err);\n");
        js.append("            fallbackCopy(text, btn);\n");
        js.append("        });\n");
        js.append("    } else {\n");
        js.append("        fallbackCopy(text, btn);\n");
        js.append("    }\n");
        js.append("}\n");
        js.append("\n");
        js.append("// 备用复制方法\n");
        js.append("function fallbackCopy(text, btn) {\n");
        js.append("    var textarea = document.createElement('textarea');\n");
        js.append("    textarea.value = text;\n");
        js.append("    textarea.style.position = 'fixed';\n");
        js.append("    textarea.style.top = '-9999px';\n");
        js.append("    textarea.style.left = '-9999px';\n");
        js.append("    document.body.appendChild(textarea);\n");
        js.append("    textarea.select();\n");
        js.append("    textarea.setSelectionRange(0, textarea.value.length);\n");
        js.append("    try {\n");
        js.append("        document.execCommand('copy');\n");
        js.append("        showCopySuccess(btn);\n");
        js.append("    } catch(err) {\n");
        js.append("        console.error('复制失败:', err);\n");
        js.append("        btn.textContent = '❌ 失败';\n");
        js.append("        setTimeout(function() {\n");
        js.append("            btn.textContent = '📋 复制';\n");
        js.append("        }, 2000);\n");
        js.append("    }\n");
        js.append("    document.body.removeChild(textarea);\n");
        js.append("}\n");
        js.append("\n");
        js.append("function showCopySuccess(btn) {\n");
        js.append("    btn.textContent = '✓ 已复制';\n");
        js.append("    btn.classList.add('copied');\n");
        js.append("    setTimeout(function() {\n");
        js.append("        btn.textContent = '📋 复制';\n");
        js.append("        btn.classList.remove('copied');\n");
        js.append("    }, 2000);\n");
        js.append("}\n");
        js.append("\n");
        js.append("document.addEventListener('DOMContentLoaded', function() {\n");
        js.append("    // 为所有代码块添加复制按钮\n");
        js.append("    var pres = document.querySelectorAll('pre');\n");
        js.append("    pres.forEach(function(pre) {\n");
        js.append("        if (pre.parentElement && !pre.parentElement.classList.contains('code-block-wrapper')) {\n");
        js.append("            var wrapper = document.createElement('div');\n");
        js.append("            wrapper.className = 'code-block-wrapper';\n");
        js.append("            pre.parentNode.insertBefore(wrapper, pre);\n");
        js.append("            wrapper.appendChild(pre);\n");
        js.append("            var btn = document.createElement('button');\n");
        js.append("            btn.className = 'copy-btn';\n");
        js.append("            btn.textContent = '📋 复制';\n");
        js.append("            btn.onclick = function() { copyCode(this); };\n");
        js.append("            wrapper.appendChild(btn);\n");
        js.append("        }\n");
        js.append("    });\n");
        js.append("    \n");
        js.append("    // 为所有标题添加锚点链接\n");
        js.append("    var headers = document.querySelectorAll('h1, h2, h3, h4, h5, h6');\n");
        js.append("    headers.forEach(function(header) {\n");
        js.append("        if (!header.id) {\n");
        js.append("            var text = header.innerText;\n");
        js.append("            var id = text.toLowerCase().replace(/[^\\w\\u4e00-\\u9fa5]+/g, '-');\n");
        js.append("            header.id = id;\n");
        js.append("        }\n");
        js.append("    });\n");
        js.append("    \n");
        js.append("    // 处理锚点跳转（滚动到元素）\n");
        js.append("    if (window.location.hash) {\n");
        js.append("        var targetId = window.location.hash.substring(1);\n");
        js.append("        var target = document.getElementById(targetId);\n");
        js.append("        if (target) {\n");
        js.append("            setTimeout(function() {\n");
        js.append("                target.scrollIntoView({ behavior: 'smooth', block: 'start' });\n");
        js.append("            }, 100);\n");
        js.append("        }\n");
        js.append("    }\n");
        js.append("});\n");
        return js.toString();
    }

    private static class ParseResult {
        String content;
        List<TocItem> tocItems;
        
        ParseResult(String content, List<TocItem> tocItems) {
            this.content = content;
            this.tocItems = tocItems;
        }
    }
    
    private static class TocItem {
        int level;
        String id;
        String title;
        
        TocItem(int level, String id, String title) {
            this.level = level;
            this.id = id;
            this.title = title;
        }
    }
    
    private static ParseResult parseMarkdownWithToc(String markdown) {
        StringBuilder result = new StringBuilder();
        List<TocItem> tocItems = new ArrayList<>();
        String[] lines = markdown.split("\n");
        
        boolean inCodeBlock = false;
        boolean inList = false;
        boolean inOrderedList = false;
        boolean inBlockquote = false;
        StringBuilder codeBlock = new StringBuilder();
        StringBuilder blockquoteContent = new StringBuilder();
        String currentLanguage = "";
        
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String trimmedLine = line.trim();
            
            // 代码块
            if (trimmedLine.startsWith("```")) {
                if (!inCodeBlock) {
                    if (inList) {
                        result.append(inOrderedList ? "</ol>\n" : "</ul>\n");
                        inList = false;
                    }
                    if (inBlockquote) {
                        result.append("</blockquote>\n");
                        inBlockquote = false;
                    }
                    inCodeBlock = true;
                    currentLanguage = trimmedLine.substring(3).trim();
                    codeBlock = new StringBuilder();
                } else {
                    inCodeBlock = false;
                    result.append("<div class='code-block-wrapper'><pre><code");
                    if (!currentLanguage.isEmpty()) {
                        result.append(" class='language-").append(escapeHtml(currentLanguage)).append("'");
                    }
                    result.append(">");
                    result.append(escapeHtml(codeBlock.toString()));
                    result.append("</code></pre><button class='copy-btn' onclick='copyCode(this)'>📋 复制</button></div>\n");
                }
                continue;
            }
            
            if (inCodeBlock) {
                codeBlock.append(line).append("\n");
                continue;
            }
            
            // 表格
            if (trimmedLine.contains("|") && trimmedLine.startsWith("|")) {
                if (inList) {
                    result.append(inOrderedList ? "</ol>\n" : "</ul>\n");
                    inList = false;
                }
                if (inBlockquote) {
                    result.append("</blockquote>\n");
                    inBlockquote = false;
                }
                String tableHtml = parseTable(lines, i);
                if (tableHtml != null) {
                    result.append(tableHtml);
                    while (i + 1 < lines.length && lines[i + 1].contains("|")) {
                        i++;
                    }
                    continue;
                }
            }
            
            // 标题
            int level = 0;
            String titleText = "";
            String headerTag = "";
            
            if (trimmedLine.startsWith("# ")) {
                level = 1;
                titleText = trimmedLine.substring(2);
                headerTag = "h1";
            } else if (trimmedLine.startsWith("## ")) {
                level = 2;
                titleText = trimmedLine.substring(3);
                headerTag = "h2";
            } else if (trimmedLine.startsWith("### ")) {
                level = 3;
                titleText = trimmedLine.substring(4);
                headerTag = "h3";
            } else if (trimmedLine.startsWith("#### ")) {
                level = 4;
                titleText = trimmedLine.substring(5);
                headerTag = "h4";
            } else if (trimmedLine.startsWith("##### ")) {
                level = 5;
                titleText = trimmedLine.substring(6);
                headerTag = "h5";
            } else if (trimmedLine.startsWith("###### ")) {
                level = 6;
                titleText = trimmedLine.substring(7);
                headerTag = "h6";
            }
            
            if (level > 0) {
                if (inList) {
                    result.append(inOrderedList ? "</ol>\n" : "</ul>\n");
                    inList = false;
                }
                if (inBlockquote) {
                    result.append("</blockquote>\n");
                    inBlockquote = false;
                }
                String id = titleText.toLowerCase()
                        .replaceAll("[^\\w\\u4e00-\\u9fa5]+", "-")
                        .replaceAll("^-|-$", "");
                tocItems.add(new TocItem(level, id, titleText));
                result.append("<").append(headerTag).append(" id='").append(id).append("'>");
                result.append(parseInline(escapeHtml(titleText)));
                result.append("</").append(headerTag).append(">\n");
                continue;
            }
            
            // 引用
            if (trimmedLine.startsWith(">")) {
                if (inList) {
                    result.append(inOrderedList ? "</ol>\n" : "</ul>\n");
                    inList = false;
                }
                if (!inBlockquote) {
                    inBlockquote = true;
                    blockquoteContent = new StringBuilder();
                }
                String quoteLine = trimmedLine.substring(1).trim();
                if (quoteLine.isEmpty() && blockquoteContent.length() > 0) {
                    result.append("<blockquote>").append(parseInline(escapeHtml(blockquoteContent.toString()))).append("</blockquote>\n");
                    inBlockquote = false;
                } else {
                    blockquoteContent.append(quoteLine).append("\n");
                }
                continue;
            }
            
            if (inBlockquote && !trimmedLine.startsWith(">")) {
                result.append("<blockquote>").append(parseInline(escapeHtml(blockquoteContent.toString()))).append("</blockquote>\n");
                inBlockquote = false;
            }
            
            // 分隔线
            if (trimmedLine.matches("^[-*_]{3,}$")) {
                if (inList) { result.append(inOrderedList ? "</ol>\n" : "</ul>\n"); inList = false; }
                if (inBlockquote) { result.append("</blockquote>\n"); inBlockquote = false; }
                result.append("<hr>\n");
                continue;
            }
            
            // 无序列表
            if (trimmedLine.matches("^[-*+]\\s+.*")) {
                if (!inList || inOrderedList) {
                    if (inList) result.append(inOrderedList ? "</ol>\n" : "</ul>\n");
                    result.append("<ul>\n");
                    inList = true;
                    inOrderedList = false;
                }
                String content = trimmedLine.substring(1).trim();
                if (content.startsWith("[ ] ")) {
                    String taskContent = content.substring(4);
                    result.append("<li class='task-list-item'><input type='checkbox' disabled> ").append(parseInline(escapeHtml(taskContent))).append("</li>\n");
                } else if (content.startsWith("[x] ") || content.startsWith("[X] ")) {
                    String taskContent = content.substring(4);
                    result.append("<li class='task-list-item'><input type='checkbox' checked disabled> ").append(parseInline(escapeHtml(taskContent))).append("</li>\n");
                } else {
                    result.append("<li>").append(parseInline(escapeHtml(content))).append("</li>\n");
                }
                continue;
            }
            
            // 有序列表
            if (trimmedLine.matches("^\\d+\\.\\s+.*")) {
                if (!inList || !inOrderedList) {
                    if (inList) result.append(inOrderedList ? "</ol>\n" : "</ul>\n");
                    result.append("<ol>\n");
                    inList = true;
                    inOrderedList = true;
                }
                String content = trimmedLine.substring(trimmedLine.indexOf('.') + 1).trim();
                result.append("<li>").append(parseInline(escapeHtml(content))).append("</li>\n");
                continue;
            }
            
            // 空行
            if (trimmedLine.isEmpty()) {
                if (inList) {
                    result.append(inOrderedList ? "</ol>\n" : "</ul>\n");
                    inList = false;
                }
                continue;
            }
            
            // 普通段落
            if (inList) {
                result.append(inOrderedList ? "</ol>\n" : "</ul>\n");
                inList = false;
            }
            
            StringBuilder paragraph = new StringBuilder(line);
            while (i + 1 < lines.length && !lines[i + 1].trim().isEmpty() &&
                   !lines[i + 1].trim().startsWith("#") &&
                   !lines[i + 1].trim().startsWith("```") &&
                   !lines[i + 1].trim().matches("^[-*+>\\d].*") &&
                   !lines[i + 1].trim().matches("^\\|.*\\|$")) {
                i++;
                paragraph.append("\n").append(lines[i]);
            }
            
            result.append("<p>").append(parseInline(escapeHtml(paragraph.toString()))).append("</p>\n");
        }
        
        if (inCodeBlock) {
            result.append("<div class='code-block-wrapper'><pre><code>").append(escapeHtml(codeBlock.toString())).append("</code></pre><button class='copy-btn' onclick='copyCode(this)'>📋 复制</button></div>\n");
        }
        if (inList) {
            result.append(inOrderedList ? "</ol>\n" : "</ul>\n");
        }
        if (inBlockquote) {
            result.append("<blockquote>").append(parseInline(escapeHtml(blockquoteContent.toString()))).append("</blockquote>\n");
        }
        
        return new ParseResult(result.toString(), tocItems);
    }

    private static String generateTocHtml(List<TocItem> tocItems) {
        if (tocItems.isEmpty()) return "";
        
        StringBuilder html = new StringBuilder();
        int lastLevel = 1;
        
        for (TocItem item : tocItems) {
            while (item.level > lastLevel) {
                html.append("<ul>\n");
                lastLevel++;
            }
            while (item.level < lastLevel) {
                html.append("</ul>\n");
                lastLevel--;
            }
            html.append("<li>");
            html.append("<a href='#").append(item.id).append("'>").append(escapeHtml(item.title)).append("</a>");
            html.append("</li>\n");
        }
        
        while (lastLevel > 1) {
            html.append("</ul>\n");
            lastLevel--;
        }
        
        return html.toString();
    }

    private static String parseTable(String[] lines, int startIndex) {
        try {
            List<String[]> rows = new ArrayList<>();
            int i = startIndex;
            
            while (i < lines.length && lines[i].trim().contains("|")) {
                String line = lines[i].trim();
                if (line.startsWith("|")) line = line.substring(1);
                if (line.endsWith("|")) line = line.substring(0, line.length() - 1);
                String[] cells = line.split("\\|");
                for (int j = 0; j < cells.length; j++) {
                    cells[j] = cells[j].trim();
                }
                rows.add(cells);
                i++;
            }
            
            if (rows.size() < 2) return null;
            
            boolean hasSeparator = false;
            if (rows.size() > 1) {
                String[] secondRow = rows.get(1);
                for (String cell : secondRow) {
                    if (cell.matches("^[-:]+[-:]*$")) {
                        hasSeparator = true;
                        break;
                    }
                }
            }
            
            StringBuilder table = new StringBuilder();
            table.append("<div class='table-wrapper'>\n");
            table.append("<table>\n");
            
            table.append("<thead>\n<tr>\n");
            String[] headers = rows.get(0);
            for (int j = 0; j < headers.length; j++) {
                String align = "";
                if (hasSeparator && rows.size() > 1 && j < rows.get(1).length) {
                    String sep = rows.get(1)[j];
                    if (sep.startsWith(":") && sep.endsWith(":")) align = " center";
                    else if (sep.endsWith(":")) align = " right";
                    else if (sep.startsWith(":")) align = " left";
                }
                table.append("<th style='text-align:").append(align.isEmpty() ? "left" : align.trim()).append("'>");
                table.append(parseInline(escapeHtml(headers[j])));
                table.append("</th>\n");
            }
            table.append("</td></thead>\n");
            
            table.append("<tbody>\n");
            int startRow = hasSeparator ? 2 : 1;
            for (int r = startRow; r < rows.size(); r++) {
                table.append("<tr>\n");
                String[] cells = rows.get(r);
                for (int j = 0; j < cells.length; j++) {
                    table.append("<td>").append(parseInline(escapeHtml(cells[j]))).append("</td>\n");
                }
                table.append("</tr>\n");
            }
            table.append("</tbody>\n");
            table.append("</table>\n");
            table.append("</div>\n");
            
            return table.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private static String parseInline(String text) {
        if (TextUtils.isEmpty(text)) return text;
        
        String result = text;
        
        // 处理粗体 **text** 或 __text__
        result = result.replaceAll("\\*\\*([^*]+)\\*\\*", "<strong>$1</strong>");
        result = result.replaceAll("__([^_]+)__", "<strong>$1</strong>");
        
        // 处理斜体 *text* 或 _text_
        result = result.replaceAll("\\*([^*]+)\\*", "<em>$1</em>");
        result = result.replaceAll("_([^_]+)_", "<em>$1</em>");
        
        // 处理删除线 ~~text~~
        result = result.replaceAll("~~([^~]+)~~", "<del>$1</del>");
        
        // 处理行内代码 `code`
        Pattern codePattern = Pattern.compile("`([^`]+)`");
        Matcher codeMatcher = codePattern.matcher(result);
        StringBuffer sb = new StringBuffer();
        while (codeMatcher.find()) {
            codeMatcher.appendReplacement(sb, "<code>" + codeMatcher.group(1) + "</code>");
        }
        codeMatcher.appendTail(sb);
        result = sb.toString();
        
        // 处理图片 ![alt](url)
        Pattern imgPattern = Pattern.compile("!\\[([^\\]]*)\\]\\(([^)]+)\\)");
        Matcher imgMatcher = imgPattern.matcher(result);
        sb = new StringBuffer();
        while (imgMatcher.find()) {
            String alt = imgMatcher.group(1);
            String src = imgMatcher.group(2);
            imgMatcher.appendReplacement(sb, "<img src='" + src + "' alt='" + alt + "'>");
        }
        imgMatcher.appendTail(sb);
        result = sb.toString();
        
        // 处理链接 [text](url)
        Pattern linkPattern = Pattern.compile("\\[([^\\]]+)\\]\\(([^)]+)\\)");
        Matcher linkMatcher = linkPattern.matcher(result);
        sb = new StringBuffer();
        while (linkMatcher.find()) {
            String text2 = linkMatcher.group(1);
            String url = linkMatcher.group(2);
            linkMatcher.appendReplacement(sb, "<a href='" + url + "'>" + text2 + "</a>");
        }
        linkMatcher.appendTail(sb);
        result = sb.toString();
        
        return result;
    }

    private static String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&#39;");
    }

    private static String getEmptyHtml() {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n");
        html.append("<html>\n");
        html.append("<head>\n");
        html.append("    <meta charset='UTF-8'>\n");
        html.append("    <meta name='viewport' content='width=device-width, initial-scale=1.0, maximum-scale=5.0, user-scalable=yes'>\n");
        html.append("    <style>\n");
        html.append("        body { \n");
        html.append("            font-family: sans-serif; \n");
        html.append("            padding: 20px; \n");
        html.append("            text-align: center; \n");
        html.append("            background: #f5f5f5; \n");
        html.append("            margin: 0;\n");
        html.append("        }\n");
        html.append("        .card { \n");
        html.append("            max-width: 400px; \n");
        html.append("            margin: 0 auto; \n");
        html.append("            background: white; \n");
        html.append("            border-radius: 16px; \n");
        html.append("            padding: 32px; \n");
        html.append("            box-shadow: 0 4px 12px rgba(0,0,0,0.1); \n");
        html.append("        }\n");
        html.append("        h1 { color: #e74c3c; margin-bottom: 16px; }\n");
        html.append("        p { color: #666; margin-bottom: 24px; }\n");
        html.append("    </style>\n");
        html.append("</head>\n");
        html.append("<body>\n");
        html.append("    <div class='card'>\n");
        html.append("        <h1>📄 空文档</h1>\n");
        html.append("        <p>文档内容为空</p>\n");
        html.append("    </div>\n");
        html.append("</body>\n");
        html.append("</html>\n");
        return html.toString();
    }
}