package com.UIN.Tool.utils

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
            Logger.e(TAG, "加载模板失败: $templatePath", e)
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
        
        if (templateType == 2) return files
        
        val indexTemplate = if (templateType == 1) {
            loadTemplate(context, "web/blank_index.html")
        } else {
            loadTemplate(context, "web/index.html")
        }
        files["web/index.html"] = renderTemplate(indexTemplate, variables)
        
        if (templateType == 0) {
            val cssTemplate = loadTemplate(context, "web/style.css")
            val jsTemplate = loadTemplate(context, "web/script.js")
            files["web/style.css"] = renderTemplate(cssTemplate, variables)
            files["web/script.js"] = renderTemplate(jsTemplate, variables)
        }
        
        return files
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