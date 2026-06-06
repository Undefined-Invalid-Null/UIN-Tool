package com.UIN.Tool.utils;

import android.content.Context;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

/**
 * 模板工具类
 * 用于加载和渲染各种模板文件
 */
public class TemplateUtils {
    
    private static final String TEMPLATE_BASE_PATH = "plugin_templates/";
    
    // Web 模板类型常量
    public static final int WEB_TEMPLATE_FULL = 0;   // 完整模板
    public static final int WEB_TEMPLATE_BLANK = 1;  // 空白模板
    public static final int WEB_TEMPLATE_SKIP = 2;   // 跳过生成
    
    /**
     * 加载模板文件
     * @param context 上下文
     * @param templatePath 模板路径（相对于 assets/plugin_templates/）
     * @return 模板内容字符串
     */
    public static String loadTemplate(Context context, String templatePath) throws Exception {
        InputStream is = context.getAssets().open(TEMPLATE_BASE_PATH + templatePath);
        BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line).append("\n");
        }
        reader.close();
        is.close();
        return sb.toString();
    }
    
    /**
     * 渲染模板，替换占位符
     * @param template 模板内容
     * @param variables 变量映射表，key 为占位符名称（不含 {{}}），value 为替换值
     * @return 渲染后的字符串
     */
    public static String renderTemplate(String template, Map<String, String> variables) {
        String result = template;
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            String key = "{{" + entry.getKey() + "}}";
            String value = entry.getValue();
            if (value == null) value = "";
            result = result.replace(key, value);
        }
        return result;
    }
    
    /**
     * 加载并渲染模板
     * @param context 上下文
     * @param templatePath 模板路径
     * @param variables 变量映射表
     * @return 渲染后的字符串
     */
    public static String loadAndRender(Context context, String templatePath, Map<String, String> variables) throws Exception {
        String template = loadTemplate(context, templatePath);
        return renderTemplate(template, variables);
    }
    
    /**
     * 生成 Web 插件的所有模板文件
     * @param context 上下文
     * @param vars 变量映射表
     * @param templateType 模板类型: 0=完整模板, 1=空白模板, 2=跳过
     * @return 文件名到文件内容的映射
     */
    public static Map<String, String> generateWebTemplates(Context context, Map<String, String> vars, int templateType) throws Exception {
        Map<String, String> files = new HashMap<>();
        
        // 跳过模式：返回空 Map
        if (templateType == WEB_TEMPLATE_SKIP) {
            return files;
        }
        
        // 根据模板类型选择 index.html
        String indexTemplate;
        if (templateType == WEB_TEMPLATE_BLANK) {
            // 空白模板：使用 blank_index.html
            indexTemplate = loadAndRender(context, "web/blank_index.html", vars);
        } else {
            // 完整模板：使用 index.html
            indexTemplate = loadAndRender(context, "web/index.html", vars);
        }
        files.put("web/index.html", indexTemplate);
        
        // 完整模板还需要 CSS 和 JS
        if (templateType == WEB_TEMPLATE_FULL) {
            String styleCss = loadAndRender(context, "web/style.css", vars);
            String scriptJs = loadAndRender(context, "web/script.js", vars);
            files.put("web/style.css", styleCss);
            files.put("web/script.js", scriptJs);
        }
        
        return files;
    }
    
    /**
     * 生成 Java 代码
     * @param context 上下文
     * @param uiType UI类型: "native" 或 "web"
     * @param vars 变量映射表
     * @return Java 代码字符串
     */
    public static String generateJavaCode(Context context, String uiType, Map<String, String> vars) throws Exception {
        String templatePath;
        if ("web".equals(uiType)) {
            templatePath = "WebPlugin.java.tmpl";
        } else {
            templatePath = "NativePlugin.java.tmpl";
        }
        return loadAndRender(context, templatePath, vars);
    }
    
    /**
     * 生成 README.md 文档
     * @param context 上下文
     * @param vars 变量映射表
     * @return README 内容
     */
    public static String generateReadme(Context context, Map<String, String> vars) throws Exception {
        String template = loadTemplate(context, "README.md.tmpl");
        
        // 根据 UI 类型设置不同的内容
        String uiType = vars.get("UI_TYPE");
        if ("WebView".equals(uiType) || "web".equals(uiType)) {
            String webOption = vars.getOrDefault("WEB_OPTION", "完整模板");
            vars.put("WEB_SECTION", 
                "├── web/             # Web 资源目录\n" +
                "│   ├── index.html   # 主页面\n" +
                ("完整模板".equals(webOption) ? 
                "│   ├── style.css     # 样式文件\n" +
                "│   └── script.js     # JavaScript 文件\n" : 
                "│   └── (自行添加文件)\n"));
            vars.put("EXTRA_FILES", "和 `web/` 文件夹");
            vars.put("DEVELOPMENT_GUIDE", 
                "### WebView 插件开发\n\n" +
                "#### 已生成的资源\n" +
                ("完整模板".equals(webOption) ?
                "- **完整模板**：已生成 `index.html`、`style.css`、`script.js`，可直接修改\n" :
                "- **空白模板**：已生成 `index.html` 基础框架，需自行添加样式和脚本\n") +
                ("跳过生成".equals(webOption) ?
                "- **跳过模式**：`web/` 文件夹为空，需自行创建所有文件\n" : "") +
                "\n#### JavaScript API\n\n" +
                "在 HTML/JS 中可通过 `UINPlugin` 对象调用宿主功能：\n\n" +
                "```javascript\n" +
                "// 显示提示\n" +
                "UINPlugin.callHost('toast', '消息内容');\n\n" +
                "// 关闭插件\n" +
                "UINPlugin.callHost('finish', '');\n\n" +
                "// 输出日志\n" +
                "UINPlugin.callHost('log', '调试信息');\n\n" +
                "// 调用插件方法（需在 Java 端实现）\n" +
                "UINPlugin.callPlugin('methodName', JSON.stringify(params));\n" +
                "```\n\n" +
                "#### 生命周期事件\n\n" +
                "```javascript\n" +
                "window.addEventListener('resume', () => { console.log('插件恢复'); });\n" +
                "window.addEventListener('pause', () => { console.log('插件暂停'); });\n" +
                "window.addEventListener('destroy', () => { console.log('插件销毁'); });\n" +
                "```\n\n" +
                "#### 文件说明\n\n" +
                "| 文件 | 说明 |\n" +
                "|------|------|\n" +
                "| `index.html` | 主页面，必选 |\n" +
                "| `style.css` | 样式文件，可选 |\n" +
                "| `script.js` | JavaScript 逻辑，可选 |\n\n" +
                "**提示**：修改 HTML/CSS/JS 后无需重新编译，直接重新打开插件即可生效。\n");
        } else {
            vars.put("WEB_SECTION", "");
            vars.put("EXTRA_FILES", "和 `res/` 文件夹");
            vars.put("DEVELOPMENT_GUIDE",
                "### 原生插件开发\n\n" +
                "1. 编辑 `src/` 目录下的 Java 文件\n" +
                "2. 实现 `PluginInterface` 接口的所有方法\n" +
                "3. UI 必须通过 Java 代码动态创建，不能使用 XML 布局\n" +
                "4. 示例代码已自动生成，可以直接修改\n\n" +
                "#### 生命周期方法\n\n" +
                "| 方法 | 说明 |\n" +
                "|------|------|\n" +
                "| `onCreateView` | 创建插件视图，必须实现 |\n" +
                "| `onResume` | 插件恢复时调用 |\n" +
                "| `onPause` | 插件暂停时调用 |\n" +
                "| `onDestroy` | 插件销毁时调用 |\n" +
                "| `onBackPressed` | 返回键按下时调用 |\n\n" +
                "#### 注意事项\n\n" +
                "- 不要使用 Android XML 布局文件\n" +
                "- 所有 UI 控件必须通过 Java 代码动态创建\n" +
                "- 修改代码后需要重新编译生成 DEX\n");
        }
        
        return renderTemplate(template, vars);
    }
    
    /**
     * 生成默认的 plugin.json 内容
     * @param vars 变量映射表
     * @return JSON 字符串
     */
    public static String generatePluginJson(Map<String, String> vars) {
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("    \"pluginId\": \"").append(escapeJson(vars.get("pluginId"))).append("\",\n");
        json.append("    \"version\": ").append(vars.getOrDefault("version", "1")).append(",\n");
        json.append("    \"versionName\": \"").append(escapeJson(vars.get("versionName"))).append("\",\n");
        json.append("    \"minHostVersion\": 1,\n");
        json.append("    \"name\": \"").append(escapeJson(vars.get("name"))).append("\",\n");
        json.append("    \"author\": \"").append(escapeJson(vars.get("author"))).append("\",\n");
        json.append("    \"description\": \"").append(escapeJson(vars.get("description"))).append("\",\n");
        json.append("    \"icon\": \"icon.png\",\n");
        json.append("    \"mainClass\": \"").append(escapeJson(vars.get("mainClass"))).append("\",\n");
        json.append("    \"apiLevel\": 21,\n");
        json.append("    \"uiType\": \"").append(escapeJson(vars.get("uiType"))).append("\",\n");
        json.append("    \"entry\": \"").append(escapeJson(vars.get("entry"))).append("\"\n");
        json.append("}");
        return json.toString();
    }
    
    /**
     * 转义 JSON 字符串中的特殊字符
     */
    private static String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }
}