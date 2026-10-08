package com.UIN.Tool.utils

import com.UIN.Tool.R
import android.content.Context
import com.UIN.Tool.log.Logger

object TemplateUtils {
    
    private const val TAG = "TemplateUtils"
    private const val TEMPLATE_BASE_PATH = "plugin_templates/"
    
    fun loadTemplate(context: Context, templatePath: String): String {
        return try {
            context.assets.open("$TEMPLATE_BASE_PATH$templatePath").bufferedReader().use {
                it.readText()
            }
        } catch (e: Exception) {
            Logger.e(TAG, Str.get(R.string.failed_to_load_template_templatepath, templatePath), e)
            ""
        }
    }
    
    /**
     * C-34：模板渲染按目标上下文转义。
     * - [Mode.INLINE]（默认）：去掉换行/回车/控制字符 —— 变量出现在 shell/python/js
     *   注释或 HTML 文本里时，名称含 `\n` 就能起新语句/命令。
     * - [Mode.JAVA]：在 INLINE 基础上再按 Java 字符串字面量转义（`"`、`\`、控制符），
     *   避免 `setText("{{PLUGIN_NAME}}")` 生成编译不过或可注入的代码。
     * - [Mode.HTML]：再做 HTML 文本转义，防止名称里的 `</title>` 之类破坏结构。
     * - [Mode.RAW]：原样替换（仅 README 等纯文档场景）。
     */
    enum class Mode { RAW, INLINE, JAVA, HTML }

    fun renderTemplate(
        template: String,
        variables: Map<String, String>,
        mode: Mode = Mode.INLINE
    ): String {
        var result = template
        variables.forEach { (key, value) ->
            val v = when (mode) {
                Mode.RAW -> value ?: ""
                Mode.INLINE -> sanitizeInline(value ?: "")
                Mode.JAVA -> escapeJavaString(sanitizeInline(value ?: ""))
                Mode.HTML -> escapeHtml(sanitizeInline(value ?: ""))
            }
            result = result.replace("{{$key}}", v)
        }
        return result
    }

    /** C-34：去掉换行/回车/制表与控制字符（危险性最低、对各模板都安全的兜底） */
    fun sanitizeInline(value: String): String =
        value.replace("[\\r\\n\\t]".toRegex(), " ").replace("[\\p{Cntrl}]".toRegex(), "")

    /** C-34：Java 字符串字面量转义 */
    fun escapeJavaString(value: String): String = buildString {
        for (c in value) {
            when (c) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (c.code < 0x20) {
                    append(String.format("\\u%04x", c.code))
                } else {
                    append(c)
                }
            }
        }
    }

    /** C-34：HTML 文本转义 */
    fun escapeHtml(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

    /** C-34/F-10：包名/类名必须是合法 Java 标识符片段，非法字符替换为 _ */
    fun sanitizeJavaIdentifier(value: String, fallback: String): String {
        val cleaned = value.replace("[^A-Za-z0-9_.$]".toRegex(), "_")
        return if (cleaned.isEmpty() || !cleaned.first().isLetter() && cleaned.first() != '_') {
            if (cleaned.isEmpty()) fallback else "_" + cleaned
        } else cleaned
    }
    
    fun generateJavaCode(
        context: Context,
        uiType: String,
        variables: Map<String, String>
    ): String {
        val templatePath = if (uiType == "web") "WebPlugin.java.tmpl" else "NativePlugin.java.tmpl"
        val template = loadTemplate(context, templatePath)
        // C-34：类名/包名出现在声明位置，先按合法标识符清洗；其余按 Java 字面量转义
        val safeVars = variables.toMutableMap()
        safeVars["CLASS_NAME"] = sanitizeJavaIdentifier(variables["CLASS_NAME"] ?: "MainPlugin", "MainPlugin")
        safeVars["PACKAGE_NAME"] = sanitizeJavaIdentifier(variables["PACKAGE_NAME"] ?: "com.plugin", "com.plugin")
        return renderTemplate(template, safeVars, Mode.JAVA)
    }

    /** 生成后端启动脚本 scripts/start.sh（依赖检测 + 启动，读 $PORT） */
    fun generateBackendStartScript(context: Context, variables: Map<String, String>): String {
        return renderTemplate(loadTemplate(context, "backend/start.sh.tmpl"), variables)
    }

    /** 生成二进制后端启动脚本 scripts/start.sh（启动打包的 backend/myapp，读 $PORT） */
    fun generateBinaryBackendStartScript(context: Context, variables: Map<String, String>): String {
        return renderTemplate(loadTemplate(context, "backend/start_binary.sh.tmpl"), variables)
    }

    /** 生成后端服务示例 scripts/backend/server.py（读 $PORT + /health） */
    fun generateBackendServer(context: Context, variables: Map<String, String>): String {
        return renderTemplate(loadTemplate(context, "backend/server.py.tmpl"), variables)
    }
    
    fun generateReadme(
        context: Context,
        variables: Map<String, String>
    ): String {
        val template = loadTemplate(context, "README.md.tmpl")
        return renderTemplate(template, variables)
    }
    
    fun generateWebTemplates(
        context: Context,
        variables: Map<String, String>,
        templateType: Int
    ): Map<String, String> {
        val files = mutableMapOf<String, String>()

        when (templateType) {
            // 干净最小版：脚本已内联进 index.html，无需 web/script.js
            2 -> {
                files["web/index.html"] = renderTemplate(loadTemplate(context, "web/simple_index.html.tmpl"), variables, Mode.HTML)
                return files
            }
            // 空白版
            1 -> {
                files["web/index.html"] = renderTemplate(loadTemplate(context, "web/blank_index.html"), variables, Mode.HTML)
                return files
            }
            // 完整测试面板
            else -> {
                val indexTemplate = loadTemplate(context, "web/index.html")
                val cssTemplate = loadTemplate(context, "web/style.css")
                val jsTemplate = loadTemplate(context, "web/script.js")
                files["web/index.html"] = renderTemplate(indexTemplate, variables, Mode.HTML)
                files["web/style.css"] = renderTemplate(cssTemplate, variables)
                files["web/script.js"] = renderTemplate(jsTemplate, variables)
                return files
            }
        }
    }
    
}