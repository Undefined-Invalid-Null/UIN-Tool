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
    
    fun renderTemplate(template: String, variables: Map<String, String>): String {
        var result = template
        variables.forEach { (key, value) ->
            result = result.replace("{{$key}}", value ?: "")
        }
        return result
    }
    
    fun generateJavaCode(
        context: Context,
        uiType: String,
        variables: Map<String, String>
    ): String {
        val templatePath = if (uiType == "web") "WebPlugin.java.tmpl" else "NativePlugin.java.tmpl"
        val template = loadTemplate(context, templatePath)
        return renderTemplate(template, variables)
    }

    /** 生成后端启动脚本 scripts/start.sh（依赖检测 + 启动，读 $PORT） */
    fun generateBackendStartScript(context: Context, variables: Map<String, String>): String {
        return renderTemplate(loadTemplate(context, "backend/start.sh.tmpl"), variables)
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
                files["web/index.html"] = renderTemplate(loadTemplate(context, "web/simple_index.html.tmpl"), variables)
                return files
            }
            // 空白版
            1 -> {
                files["web/index.html"] = renderTemplate(loadTemplate(context, "web/blank_index.html"), variables)
                return files
            }
            // 完整测试面板
            else -> {
                val indexTemplate = loadTemplate(context, "web/index.html")
                val cssTemplate = loadTemplate(context, "web/style.css")
                val jsTemplate = loadTemplate(context, "web/script.js")
                files["web/index.html"] = renderTemplate(indexTemplate, variables)
                files["web/style.css"] = renderTemplate(cssTemplate, variables)
                files["web/script.js"] = renderTemplate(jsTemplate, variables)
                return files
            }
        }
    }
    
    fun generatePluginJson(variables: Map<String, String>): String {
        return """
            {
                "pluginId": "${variables["pluginId"] ?: ""}",
                "version": ${variables["version"] ?: 1},
                "versionName": "${variables["versionName"] ?: "1.0.0"}",
                "minHostVersion": 1,
                "name": "${variables["name"] ?: ""}",
                "author": "${variables["author"] ?: ""}",
                "description": "${variables["description"] ?: ""}",
                "icon": "icon.png",
                "mainClass": "${variables["mainClass"] ?: ""}",
                "apiLevel": 21,
                "uiType": "${variables["uiType"] ?: "native"}",
                "entry": "${variables["entry"] ?: "web/index.html"}",
                "permissions": [],
                "dependencies": "${variables["dependencies"] ?: ""}"
            }
        """.trimIndent()
    }
}