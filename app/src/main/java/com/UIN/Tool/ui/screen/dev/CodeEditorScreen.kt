// app/src/main/java/com/UIN/Tool/ui/screen/dev/CodeEditorScreen.kt
package com.UIN.Tool.ui.screen.dev

import android.content.Context
import android.graphics.Color as AndroidColor
import android.graphics.Typeface
import android.view.KeyEvent
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import com.UIN.Tool.R
import com.UIN.Tool.UinApplication
import com.UIN.Tool.ui.components.unified.UnifiedButton
import com.UIN.Tool.ui.components.unified.UnifiedConfirmDialog
import com.UIN.Tool.ui.components.unified.UnifiedDialog
import com.UIN.Tool.ui.components.unified.UnifiedDialogTextButton
import com.UIN.Tool.ui.components.unified.UnifiedEmptyState
import com.UIN.Tool.ui.components.unified.UnifiedIconButton
import com.UIN.Tool.ui.components.unified.UnifiedTextField
import com.UIN.Tool.ui.components.unified.ButtonSize
import com.UIN.Tool.ui.components.unified.ButtonVariant
import com.UIN.Tool.ui.screen.dev.editor.CodeFormatters
import com.UIN.Tool.ui.screen.dev.editor.EditorSettings
import com.UIN.Tool.ui.screen.dev.editor.EditorTab
import com.UIN.Tool.ui.screen.dev.editor.EditorViewModel
import com.UIN.Tool.ui.screen.dev.editor.FileNode
import com.UIN.Tool.ui.screen.dev.editor.HexColorHints
import com.UIN.Tool.ui.screen.dev.editor.TextMateInitState
import com.UIN.Tool.ui.theme.AppColors
import com.UIN.Tool.ui.theme.AppDimens
import com.UIN.Tool.ui.theme.dialogBackgroundOf
import com.UIN.Tool.ui.theme.pageGradientBrush
import com.UIN.Tool.utils.AppLog
import com.UIN.Tool.utils.Str
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.event.EditorKeyEvent
import io.github.rosemoe.sora.event.KeyBindingEvent
import io.github.rosemoe.sora.event.PublishSearchResultEvent
import io.github.rosemoe.sora.event.SelectionChangeEvent
import io.github.rosemoe.sora.event.SubscriptionReceipt
import io.github.rosemoe.sora.lang.EmptyLanguage
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.langs.textmate.registry.model.ThemeModel
import io.github.rosemoe.sora.text.LineSeparator
import io.github.rosemoe.sora.util.regex.RegexBackrefGrammar
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.DirectAccessProps
import io.github.rosemoe.sora.widget.EditorSearcher
import io.github.rosemoe.sora.widget.SelectionMovement
import io.github.rosemoe.sora.widget.component.EditorAutoCompletion
import io.github.rosemoe.sora.widget.component.EditorTextActionWindow
import io.github.rosemoe.sora.widget.component.Magnifier
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import io.github.rosemoe.sora.widget.style.LineInfoPanelPosition
import io.github.rosemoe.sora.widget.style.LineInfoPanelPositionMode
import java.time.Duration
import kotlinx.coroutines.launch
import org.eclipse.tm4e.core.grammar.IStateStack
import org.eclipse.tm4e.core.internal.grammar.raw.RawGrammarReader
import org.eclipse.tm4e.core.registry.IGrammarSource
import org.eclipse.tm4e.core.registry.IThemeSource

private const val TAG = "CodeEditorScreen"
private const val DEBUG_TAG = "EditorDebug"

// ==================== 主题列表 ====================
private val THEMES = listOf(
    "dark-plus", "dracula", "dracula-soft",
    "github-dark", "github-dark-dimmed", "github-light",
    "light-plus", "material-theme", "material-theme-darker",
    "material-theme-lighter", "material-theme-ocean", "material-theme-palenight",
    "min-dark", "min-light", "monokai", "nord",
    "one-dark-pro", "poimandres",
    "rose-pine", "rose-pine-dawn", "rose-pine-moon",
    "slack-dark", "slack-ochin",
    "solarized-dark", "solarized-light",
    "vitesse-black", "vitesse-dark", "vitesse-light"
)

private val DARK_THEMES = setOf(
    "dark-plus", "dracula", "dracula-soft",
    "github-dark", "github-dark-dimmed",
    "material-theme", "material-theme-darker",
    "material-theme-ocean", "material-theme-palenight",
    "min-dark", "monokai", "nord",
    "one-dark-pro", "poimandres",
    "rose-pine", "rose-pine-moon",
    "slack-dark", "slack-ochin",
    "solarized-dark",
    "vitesse-black", "vitesse-dark"
)

private val LIGHT_THEMES = setOf(
    "github-light", "light-plus", "material-theme-lighter",
    "min-light", "rose-pine-dawn", "solarized-light", "vitesse-light"
)

// ==================== 语法高亮列表（6.2.0 批次16）====================
// 用户可在右上角手动为当前文件挑选高亮语法，不再只依赖后缀名推断。
// scope 为 null 表示「自动（按后缀名）」；"text.plain" 在 setEditorLanguage 里会兜底成
// EmptyLanguage（纯文本、无高亮）。
private val LANGUAGES: List<Pair<String, String?>> = listOf(
    "Auto (by extension)" to null,
    "Plain Text" to "text.plain",
    "Java" to "source.java",
    "Kotlin" to "source.kotlin",
    "XML" to "text.xml",
    "HTML" to "text.html.basic",
    "CSS" to "source.css",
    "SCSS" to "source.css.scss",
    "LESS" to "source.css.less",
    "INI / Properties" to "source.ini",
    "Batch" to "source.batchfile",
    "VB.NET" to "source.asp.vb.net",
    "Log" to "text.log",
    "Pug" to "text.pug",
    "Razor" to "text.html.cshtml",
    "JavaScript" to "source.js",
    "JSX" to "source.js.jsx",
    "TypeScript" to "source.ts",
    "TSX" to "source.tsx",
    "JSON" to "source.json",
    "Python" to "source.python",
    "Markdown" to "text.html.markdown",
    "C" to "source.c",
    "C++" to "source.cpp",
    "C#" to "source.cs",
    "Objective-C" to "source.objc",
    "Objective-C++" to "source.objcpp",
    "Go" to "source.go",
    "Rust" to "source.rust",
    "PHP" to "source.php",
    "Ruby" to "source.ruby",
    "Swift" to "source.swift",
    "Shell" to "source.shell",
    "SQL" to "source.sql",
    "PowerShell" to "source.powershell",
    "YAML" to "source.yaml",
    "TOML" to "source.toml",
    "Dart" to "source.dart",
    "Lua" to "source.lua",
    "R" to "source.r",
    "Scala" to "source.scala",
    "Perl" to "source.perl",
    "Haskell" to "source.haskell",
    "Elixir" to "source.elixir",
    "Erlang" to "source.erlang",
    "Clojure" to "source.clojure",
    "Groovy" to "source.groovy",
    "Dockerfile" to "source.dockerfile",
    "Makefile" to "source.makefile",
    "F#" to "source.fsharp",
    "Diff" to "source.diff",
    "dotenv" to "source.dotenv",
    "ShaderLab" to "source.shaderlab"
)

private const val DARK_DEFAULT = "one-dark-pro"
private const val LIGHT_DEFAULT = "github-light"

// ==================== 文件图标 ====================
private fun getFileIcon(fileName: String): ImageVector = when {
    fileName.endsWith(".java") -> Icons.Default.Code
    fileName.endsWith(".kt") || fileName.endsWith(".kts") -> Icons.Default.Code
    fileName.endsWith(".xml") -> Icons.Default.Description
    fileName.endsWith(".html") || fileName.endsWith(".htm") -> Icons.Default.Language
    fileName.endsWith(".css") || fileName.endsWith(".scss") || fileName.endsWith(".less") ->
        Icons.Default.Palette
    fileName.endsWith(".js") || fileName.endsWith(".jsx") ||
        fileName.endsWith(".ts") || fileName.endsWith(".tsx") -> Icons.Default.Javascript
    fileName.endsWith(".json") || fileName.endsWith(".yml") ||
        fileName.endsWith(".yaml") || fileName.endsWith(".toml") -> Icons.Default.DataArray
    fileName.endsWith(".py") -> Icons.Default.Code
    fileName.endsWith(".md") || fileName.endsWith(".markdown") -> Icons.Default.Article
    fileName.endsWith(".txt") || fileName.endsWith(".log") -> Icons.Default.Description
    fileName.endsWith(".gradle") || fileName.endsWith(".pro") ||
        fileName.endsWith(".properties") || fileName.endsWith(".ini") -> Icons.Default.Settings
    fileName.endsWith(".sh") || fileName.endsWith(".bash") || fileName.endsWith(".bat") ->
        Icons.Default.Terminal
    fileName.endsWith(".sql") -> Icons.Default.Storage
    fileName.endsWith(".png") || fileName.endsWith(".jpg") ||
        fileName.endsWith(".jpeg") || fileName.endsWith(".gif") ||
        fileName.endsWith(".webp") -> Icons.Default.Image
    else -> Icons.Default.InsertDriveFile
}

// ==================== 语言 Scope ====================
private fun getLanguageScopeName(fileName: String): String {
    val extension = fileName.substringAfterLast('.', "").lowercase()
    return when (extension) {
        "java" -> "source.java"
        "kt", "kts" -> "source.kotlin"
        "xml" -> "text.xml"
        "html", "htm", "xhtml" -> "text.html.basic"
        "css" -> "source.css"
        // 6.2.0 批次12：以下几项原先写的是「看起来对」但语法文件**实际声明的 scope** 不同，
        // create() 找不到语法直接抛异常 → 一直没有高亮。已逐个与 assets 里的 scopeName 核对。
        "scss" -> "source.css.scss"
        "less" -> "source.css.less"
        "properties" -> "source.ini"
        "bat", "cmd" -> "source.batchfile"
        "vb" -> "source.asp.vb.net"
        "log" -> "text.log"
        "pug", "jade" -> "text.pug"
        "razor" -> "text.html.cshtml"
        "js", "mjs", "cjs" -> "source.js"
        // 6.2.0 批次9：.jsx 使用 JavaScriptReact 语法（source.js.jsx）
        "jsx" -> "source.js.jsx"
        "json", "json5", "jsonc" -> "source.json"
        "py", "pyw" -> "source.python"
        "md", "markdown", "rst" -> "text.html.markdown"
        "ts" -> "source.ts"
        // 6.2.0 批次9：.tsx 使用 typescript-basics 中 TypeScriptReact 语法声明的真实 scope
        "tsx" -> "source.tsx"
        "cpp", "cc", "cxx" -> "source.cpp"
        "c" -> "source.c"
        "h", "hpp", "hh" -> "source.cpp"
        // 6.2.0 批次12：Objective-C / Objective-C++ 各自是独立语法，之前全归到 source.cpp
        "m" -> "source.objc"
        "mm" -> "source.objcpp"
        "cs" -> "source.cs"
        "go" -> "source.go"
        "rs" -> "source.rust"
        "php" -> "source.php"
        "rb" -> "source.ruby"
        "swift" -> "source.swift"
        "sh", "bash", "zsh", "fish" -> "source.shell"
        "sql" -> "source.sql"
        "ps1" -> "source.powershell"
        "yaml", "yml" -> "source.yaml"
        "toml" -> "source.toml"
        "dart" -> "source.dart"
        "lua" -> "source.lua"
        "r" -> "source.r"
        "scala" -> "source.scala"
        "pl", "pm" -> "source.perl"
        "hs" -> "source.haskell"
        "ex", "exs" -> "source.elixir"
        "erl" -> "source.erlang"
        "clj" -> "source.clojure"
        "groovy" -> "source.groovy"
        "dockerfile" -> "source.dockerfile"
        "makefile", "mk" -> "source.makefile"
        "ini" -> "source.ini"
        "fs", "fsx" -> "source.fsharp"
        "diff", "patch" -> "source.diff"
        "dotenv", "env" -> "source.dotenv"
        "hlsl", "glsl" -> "source.hlsl"
        "julia" -> "source.julia"
        "latex", "tex" -> "text.tex.latex"
        "shader", "shaderlab" -> "source.shaderlab"
        // 6.2.0 批次29：以下语法文件其实早已随包内置，但没有扩展名映射，.coffee/.hbs
        // 因此一直落到 text.plain（有语法却没高亮）。
        "coffee", "coffeescript" -> "source.coffee"
        "hbs", "handlebars" -> "text.html.handlebars"
        else -> "text.plain"
    }
}

// ==================== 关键词（6.2.0 修复：按语言注入，不再全量塞 Java 关键词） ====================
private val JAVA_KEYWORDS = arrayOf(
    "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char",
    "class", "const", "continue", "default", "do", "double", "else", "enum",
    "extends", "final", "finally", "float", "for", "goto", "if", "implements",
    "import", "instanceof", "int", "interface", "long", "native", "new", "null",
    "package", "private", "protected", "public", "return", "short", "static",
    "strictfp", "super", "switch", "synchronized", "this", "throw", "throws",
    "transient", "try", "void", "volatile", "while", "true", "false"
)

private val KOTLIN_KEYWORDS = arrayOf(
    "as", "break", "class", "continue", "do", "else", "false", "for", "fun",
    "if", "in", "interface", "is", "null", "object", "package", "return",
    "super", "this", "throw", "true", "try", "typealias", "typeof", "val",
    "var", "when", "while", "by", "catch", "constructor", "delegate",
    "dynamic", "field", "file", "finally", "get", "import", "init", "param",
    "property", "receiver", "set", "setparam", "where", "actual", "abstract",
    "annotation", "companion", "const", "crossinline", "data", "enum",
    "expect", "external", "final", "infix", "inline", "inner", "internal",
    "lateinit", "noinline", "open", "operator", "out", "override", "private",
    "protected", "public", "reified", "sealed", "suspend", "tailrec", "vararg"
)

private val JS_KEYWORDS = arrayOf(
    "async", "await", "break", "case", "catch", "class", "const", "continue",
    "debugger", "default", "delete", "do", "else", "enum", "export", "extends",
    "false", "finally", "for", "function", "if", "import", "in", "instanceof",
    "let", "new", "null", "return", "static", "super", "switch", "this",
    "throw", "true", "try", "typeof", "var", "void", "while", "with", "yield",
    "interface", "implements", "package", "private", "protected", "public"
)

private val C_KEYWORDS = arrayOf(
    "auto", "break", "case", "char", "const", "continue", "default", "do",
    "double", "else", "enum", "extern", "float", "for", "goto", "if", "inline",
    "int", "long", "register", "restrict", "return", "short", "signed", "sizeof",
    "static", "struct", "switch", "typedef", "union", "unsigned", "void",
    "volatile", "while", "bool", "true", "false", "nullptr", "class", "template"
)

private val PYTHON_KEYWORDS = arrayOf(
    "and", "as", "assert", "async", "await", "break", "class", "continue",
    "def", "del", "elif", "else", "except", "False", "finally", "for", "from",
    "global", "if", "import", "in", "is", "lambda", "None", "nonlocal", "not",
    "or", "pass", "raise", "return", "True", "try", "while", "with", "yield"
)

private val SQL_KEYWORDS = arrayOf(
    "SELECT", "FROM", "WHERE", "INSERT", "INTO", "VALUES", "UPDATE", "SET",
    "DELETE", "CREATE", "TABLE", "DROP", "ALTER", "INDEX", "JOIN", "LEFT",
    "RIGHT", "INNER", "OUTER", "ON", "GROUP", "BY", "ORDER", "HAVING",
    "LIMIT", "OFFSET", "AND", "OR", "NOT", "NULL", "AS", "DISTINCT",
    "PRIMARY", "KEY", "FOREIGN", "REFERENCES", "DEFAULT", "UNIQUE"
)

// ---- 6.2.0 批次39（SE 审计 T3-23）：配置类文件的补全源 ----
// 此前 keywordsFor 对 xml/json/yaml/gradle/html/ini 全部返回 emptyArray()，
// 而 TextMateLanguage.requireAutoComplete 只走 IdentifierAutoComplete 关键词表 +
// 分析器产出的 syncIdentifiers（TextMate 正则分析不收集标识符，恒为空），
// 也就是说**本 App 最常编辑的那几类配置文件，补全一直是死的**。
// 这里补上词表（不新造 CompletionAdapter：见下）。

/**
 * 只列**标签名**，不列带命名空间的属性（如 `android:layout_width`）。
 *
 * 原因是 sora 的前缀计算用 `MyCharacter.isJavaIdentifierPart` 回扫，遇到 `:` 就断开：
 * 输入 `android:l` 时算出的 prefix 是 `l`、prefixLength=1，
 * 若词条写成 `android:layout_width` 就会补成 `android:android:layout_width`。
 * 想正确支持带冒号的属性必须自造前缀长度，风险高于收益，故只做标签。
 */
private val XML_KEYWORDS = arrayOf(
    // AndroidManifest.xml
    "manifest", "uses-sdk", "uses-permission", "application", "activity",
    "activity-alias", "service", "receiver", "provider", "meta-data",
    "uses-feature", "supports-screens", "permission", "permission-group",
    "intent-filter", "action", "category", "data", "exported", "alias",
    "queries", "package",
    // res/layout
    "LinearLayout", "RelativeLayout", "FrameLayout", "ConstraintLayout",
    "ScrollView", "HorizontalScrollView", "ListView", "GridView",
    "TextView", "EditText", "Button", "ImageView", "ImageButton",
    "CheckBox", "RadioButton", "RadioGroup", "Switch", "Spinner",
    "ProgressBar", "SeekBar", "WebView", "Fragment", "View", "ViewGroup",
    "Space", "include", "merge", "requestFocus", "item", "string",
    "resources", "style", "color", "dimen", "array", "plurals",
    "declare-styleable", "attr", "shape", "selector", "layer-list",
    "inset", "padding", "size", "solid", "stroke", "corners",
    // 其它常见配置
    "project", "root", "plugin", "policy", "property", "bean",
)

/** HTML / XHTML 标签。 */
private val HTML_KEYWORDS = arrayOf(
    "html", "head", "body", "meta", "link", "script", "style", "title",
    "div", "span", "p", "a", "ul", "ol", "li", "table", "thead", "tbody",
    "tr", "td", "th", "form", "input", "button", "select", "option",
    "textarea", "img", "video", "audio", "source", "canvas", "header",
    "footer", "nav", "main", "section", "article", "aside", "h1", "h2",
    "h3", "blockquote", "pre", "code", "em", "strong", "br", "hr",
)

/**
 * CSS / SCSS / LESS 常用属性与关键字。
 *
 * 注意 sora 的前缀是在光标处**往回扫「Java 标识符字符」**得到的，所以
 *  - 输入 `backgro` 时前缀就是 `backgro` → 能匹配到 `background-color`（表项本身可以带连字符）；
 *  - 但输入 `background-c` 时前缀在 `-` 处就断了 → 只剩 `background` → 会列出所有 `background*`。
 * 这是 sora 补全的既定行为，不为它加子类适配器（`DefaultCompletionItemAdapter` 是 final）。
 * `:hover` / `@media` 同理：冒号、at 符号处会截断前缀，故表里只放去掉前缀的核心词。
 */
private val CSS_KEYWORDS = arrayOf(
    "align-content", "align-items", "align-self", "all", "animation", "appearance",
    "background", "background-color", "background-image", "background-position",
    "background-repeat", "background-size", "backdrop-filter", "border", "border-color",
    "border-collapse", "border-radius", "border-spacing", "border-style", "border-width",
    "bottom", "box-shadow", "box-sizing", "caret-color", "clear", "clip", "color",
    "column-gap", "columns", "content", "cursor", "direction", "display", "filter",
    "flex", "flex-basis", "flex-direction", "flex-flow", "flex-grow", "flex-shrink",
    "flex-wrap", "float", "font", "font-family", "font-size", "font-style",
    "font-variant", "font-weight", "gap", "grid", "grid-area", "grid-column",
    "grid-row", "grid-template", "grid-template-areas", "grid-template-columns",
    "grid-template-rows", "height", "inset", "justify-content", "justify-items",
    "justify-self", "left", "letter-spacing", "line-height", "list-style", "margin",
    "margin-bottom", "margin-left", "margin-right", "margin-top", "max-height",
    "max-width", "min-height", "min-width", "object-fit", "object-position", "opacity",
    "order", "outline", "outline-color", "outline-offset", "outline-style",
    "outline-width", "overflow", "overflow-x", "overflow-y", "padding",
    "padding-bottom", "padding-left", "padding-right", "padding-top", "perspective",
    "place-content", "place-items", "place-self", "pointer-events", "position",
    "resize", "right", "row-gap", "scroll-behavior", "tab-size", "table-layout",
    "text-align", "text-decoration", "text-indent", "text-overflow", "text-shadow",
    "text-transform", "top", "transform", "transform-origin", "transition",
    "transition-delay", "transition-duration", "transition-property",
    "transition-timing-function", "unicode-bidi", "user-select", "vertical-align",
    "visibility", "white-space", "width", "will-change", "word-break", "word-spacing",
    "word-wrap", "writing-mode", "z-index",
    // at-rule 名 / 伪类 / 全局值（均去掉 @ 与 : 前缀，原因见上）
    "import", "media", "supports", "keyframes", "font-face", "root",
    "hover", "focus", "focus-visible", "active", "visited", "disabled", "checked",
    "before", "after", "first-child", "last-child", "nth-child", "only-child",
    "not", "and", "or", "important", "inherit", "initial", "revert", "unset",
)

/** Gradle 构建脚本（.gradle）——`.kts` 走 KOTLIN_KEYWORDS，这里只补 Groovy DSL。 */
private val GRADLE_KEYWORDS = arrayOf(
    "plugins", "apply", "repositories", "dependencies", "buildscript",
    "classpath", "google", "mavenCentral", "maven", "flatDir", "implementation",
    "api", "compileOnly", "runtimeOnly", "testImplementation", "annotationProcessor",
    "android", "defaultConfig", "buildTypes", "compileSdk", "minSdk",
    "targetSdk", "versionCode", "versionName", "namespace", "compileOptions",
    "kotlinOptions", "buildFeatures", "viewBinding", "packaging", "lint",
    "signingConfigs", "flavorDimensions", "productFlavors", "release", "debug",
    "task", "tasks", "doLast", "doFirst", "from", "into", "dependsOn",
    "group", "version", "ext", "subprojects", "allprojects",
)

/**
 * json / yaml / toml / ini / properties / conf 共用的配置键。
 *
 * 这些文件没有「语法关键字」，能补的只有常见的键名；配合 IdentifierAutoComplete
 * 的大小写不敏感前缀匹配，输入 `ver` 就能出 `version`。
 */
private val CONFIG_KEYWORDS = arrayOf(
    "name", "version", "versionCode", "versionName", "description", "type",
    "author", "license", "repository", "homepage", "keywords", "main",
    "scripts", "dependencies", "devDependencies", "peerDependencies",
    "optionalDependencies", "config", "settings", "options", "properties",
    "debug", "release", "enable", "disable", "true", "false", "null",
    "id", "path", "url", "group", "artifact", "scope", "value", "key",
    "target", "source", "timeout", "retry", "enabled", "default",
)

/** 6.2.0 修复：只对对应语言注入关键词，避免 Java 关键词污染其他语言的补全。 */
private fun keywordsFor(fileName: String): Array<String> {
    val ext = fileName.substringAfterLast('.', "").lowercase()
    return when (ext) {
        "java" -> JAVA_KEYWORDS
        "kt", "kts" -> KOTLIN_KEYWORDS
        "js", "jsx", "mjs", "cjs", "ts", "tsx" -> JS_KEYWORDS
        "c", "h", "cpp", "cc", "cxx", "hpp", "hh", "m", "mm", "cs", "go",
        "rs", "swift", "dart", "groovy", "scala" -> C_KEYWORDS
        "py", "pyw" -> PYTHON_KEYWORDS
        "sql" -> SQL_KEYWORDS
        "xml" -> XML_KEYWORDS
        "html", "htm", "xhtml" -> HTML_KEYWORDS
        "css", "scss", "sass", "less" -> CSS_KEYWORDS
        "gradle" -> GRADLE_KEYWORDS
        "json", "json5", "jsonc", "yaml", "yml", "toml", "ini",
        "cfg", "conf", "properties" -> CONFIG_KEYWORDS
        else -> emptyArray()
    }
}

// ==================== 回退配色 ====================
/**
 * 6.2.0 Bug2/5/6 的共同根因（已在 sora 源码中核实）：
 *
 *  TextMate 语法高亮产出的是 **>=255 的自定义色号**，要靠
 *  `TextMateColorScheme.getColor(type)` 去 tm4e 的主题里解析；
 *  一旦 applyTheme 失败、回退到本文件里的普通 EditorColorScheme，
 *  `getColor(>=255)` 在配色表里查不到 → SparseIntArray 返回 0 → **全透明**。
 *  于是正文一片空白，而全选时 sora 改用 TEXT_SELECTED 重绘选中文字，
 *  字才「突然出现」——即用户看到的「页面空白，仅全选才能看见字符」。
 *  同理 TEXT_ACTION_WINDOW_BACKGROUND / _ICON_COLOR 也变透明，
 *  导致全选/剪切/复制/粘贴浮层没有背景、图标看不清。
 *
 * 现改为：
 *  1) 只覆盖我们关心的少数色，其余沿用库 applyDefault() 的默认值，不再一律 TRANSPARENT
 *  2) >=255 的 TextMate 色号退化为正文色，杜绝透明
 *  3) TEXT_SELECTED 显式置 0 —— 选中只改背景色，不改文字颜色（用户要求）
 */
private fun createFallbackScheme(dark: Boolean): EditorColorScheme {
    val bg = if (dark) AndroidColor.parseColor("#1E1E1E") else AndroidColor.WHITE
    val fg = if (dark) AndroidColor.parseColor("#D4D4D4") else AndroidColor.BLACK
    return object : EditorColorScheme() {
        init {
            setColor(EditorColorScheme.WHOLE_BACKGROUND, bg)
            setColor(
                EditorColorScheme.LINE_NUMBER_BACKGROUND,
                if (dark) AndroidColor.parseColor("#252525") else AndroidColor.parseColor("#F0F0F0")
            )
            setColor(EditorColorScheme.TEXT_NORMAL, fg)
            setColor(
                EditorColorScheme.LINE_NUMBER,
                if (dark) AndroidColor.parseColor("#858585") else AndroidColor.parseColor("#888888")
            )
            setColor(
                EditorColorScheme.CURRENT_LINE,
                if (dark) AndroidColor.parseColor("#2A2A2A") else AndroidColor.parseColor("#EEEEEE")
            )
            setColor(
                EditorColorScheme.LINE_DIVIDER,
                if (dark) AndroidColor.parseColor("#333333") else AndroidColor.parseColor("#E0E0E0")
            )
            // 选中：只画背景，不改文字颜色
            setColor(EditorColorScheme.TEXT_SELECTED, 0)
            // 文本操作浮层（全选/剪切/复制/粘贴）：给不透明背景与图标色
            setColor(
                EditorColorScheme.TEXT_ACTION_WINDOW_BACKGROUND,
                if (dark) AndroidColor.parseColor("#2B2B2B") else AndroidColor.WHITE
            )
            setColor(
                EditorColorScheme.TEXT_ACTION_WINDOW_ICON_COLOR,
                if (dark) AndroidColor.parseColor("#EEEEEE") else AndroidColor.parseColor("#444444")
            )
        }

        override fun getColor(color: Int): Int {
            // TextMate 自定义色号在普通配色表中不存在 → 退化为正文色，避免透明
            return if (color >= 255) getColor(EditorColorScheme.TEXT_NORMAL)
            else super.getColor(color)
        }
    }
}

private fun getLightColorScheme(): EditorColorScheme = createFallbackScheme(false)

private fun getDarkColorScheme(): EditorColorScheme = createFallbackScheme(true)

// ==================== 语言设置 ====================
/**
 * 6.2.0 批次33：用**全新解析**的语法源重建该 scope 的 Grammar 实例，绕开 tm4e 的共享 raw 污染。
 *
 * 根因（已用日志实测确认）：`Grammar` 构造器里有 `this._grammar = initGrammar(grammar, null)`，
 * 而 `initGrammar` 第一步 `ObjectCloner.deepClone(grammar)` —— 该方法末分支是 `return obj`，
 * 普通对象**从不真正克隆**。于是同一 scope 的 raw 被 registry 与所有 include 它的语法共享：
 * 谁先编译谁就把 `IRawRule.id` 写死在共享对象上，后编译的实例 `getCompiledRuleId` 见
 * `id != null` 直接返回、**不再 registerRule**，自己那份 `_ruleId2desc` 里就查不到该 id。
 *
 * 实测 a.xml：`rootId=1 selfId=1`，却 `mapSize=39 < lastRuleId=44`，
 * `getRule(1)` 抛 `No rule with index 1 found` → 分析线程被杀 → `styles=null` → 完全无高亮。
 * 同一次启动里 `text.html.basic` 正常（mapSize==lastRuleId==2433），只有被 include 过的
 * scope（xml / markdown）中招 —— 印证是跨语法共享导致，而非语法本身损坏。
 *
 * 修法：先写入全新解析的 raw，再逐出 `_grammars[scope]` 缓存，随后 `findGrammar()` 触发重建
 * —— 新实例所见的 `IRawRule` 全是 id=null 的干净对象，编译时会把自己用到的规则全部
 * re-register 进自己的 map。**每次设语言都执行**：即便中途又被别的语法污染，切回来即自愈。
 *
 * 全程 try/catch：失败则继续用旧实例，只退回原状、不影响基本功能。
 *
 * @return 是否成功重建
 */
private fun refreshGrammar(context: Context, scopeName: String): Boolean {
    val path = UinApplication.grammarAssetPaths[scopeName] ?: return false
    return try {
        val bytes = context.assets.open(path).use { it.readBytes() }
        val freshRaw = RawGrammarReader.readGrammar(
            IGrammarSource.fromInputStream(bytes.inputStream(), path, null)
        )
        val gr = GrammarRegistry.getInstance()
        fun fieldOf(o: Any, n: String) =
            o.javaClass.getDeclaredField(n).apply { isAccessible = true }

        val registry = fieldOf(gr, "registry").get(gr)
        val sync = fieldOf(registry, "_syncRegistry").get(registry)
        @Suppress("UNCHECKED_CAST")
        val rawMap = fieldOf(sync, "_rawGrammars").get(sync) as MutableMap<String, Any>
        @Suppress("UNCHECKED_CAST")
        val grammarMap = fieldOf(sync, "_grammars").get(sync) as MutableMap<String, Any>
        // 顺序很重要：先换 raw 再逐出缓存，否则重建会读到旧的污染对象。
        rawMap[scopeName] = freshRaw
        grammarMap.remove(scopeName)
        val rebuilt = gr.findGrammar(scopeName)
        AppLog.d(DEBUG_TAG, "grammar-refresh: scope=$scopeName rebuilt=${rebuilt != null}")
        rebuilt != null
    } catch (ex: Throwable) {
        AppLog.w(DEBUG_TAG, "grammar-refresh failed scope=$scopeName: ${ex.javaClass.name}: ${ex.message}")
        false
    }
}

/**
 * 6.2.0 修复两处旧缺陷：
 *  1) 不再无条件注入 Java 关键词
 *  2) scope 未注册时回退到 text.plain，而不是静默沿用上一个文件的语言
 */
private fun setEditorLanguage(editor: CodeEditor?, fileName: String, overrideScope: String? = null) {
    if (editor == null) return
    // 6.2.0 批次16：用户手动指定的语法优先；null 时按后缀名自动推断。
    val scopeName = overrideScope ?: getLanguageScopeName(fileName)
    AppLog.d(DEBUG_TAG, "setEditorLanguage: scope=$scopeName file=$fileName override=$overrideScope")
    // 6.2.0 批次33：先重建该 scope 的干净语法实例，再 create 语言（见 refreshGrammar 注释）。
    refreshGrammar(editor.context, scopeName)
    // 注意：TextMateLanguage.create() 在语法未注册时是**抛异常**而不是返回 null，
    // 而且 "text.plain" 在我们的语法表里同样不存在（再抛一次）。
    // 原实现两个 create 都失败时 language=null 直接 return，编辑器会**沿用上一个文件的语言**，
    // 表现为「换了文件高亮还是旧的」。这里改用 EmptyLanguage 作为真正的兜底。
    val language = try {
        TextMateLanguage.create(scopeName, true)
    } catch (e: Exception) {
        AppLog.w(TAG, "create($scopeName) failed, ${e.message}")
        null
    }
    if (language == null) {
        AppLog.w(TAG, "no language available for $fileName, using EmptyLanguage")
        editor.setEditorLanguage(EmptyLanguage())
        return
    }
    language.isAutoCompleteEnabled = true
    // 6.2.0 批次39（SE 审计 T1-4）：**修缩进参考线错位** —— sora 的
    // `CodeEditor.setTabWidth` 只改渲染用的宽度，而块高亮/粘性滚动算缩进层级用的是
    // `Language.getTabSize()`（TextMateLanguage 默认恒为 4）。两者不一致时，tabWidth 调成
    // 2 或 8 后所有「当前块高亮 / 缩进参考线 / sticky-scroll 块边界」都会按 4 列错算。
    // 这里同步；`applyRuntimeSettings` 也会在设置变化时同步一次。
    language.setTabSize(editor.tabWidth)
    val keywords = keywordsFor(fileName)
    if (keywords.isNotEmpty()) language.setCompleterKeywords(keywords)
    editor.setEditorLanguage(language)
    editor.getComponent(EditorAutoCompletion::class.java)?.isEnabled = true
}

// ==================== 格式化 ====================

/**
 * 6.2.0 批次40：**真**格式化。
 *
 * 背景：`TextMateLanguage extends EmptyLanguage` 且不覆盖 `getFormatter()`，
 * 于是 `CodeEditor.formatCodeAsync()` 拿到的是 `EmptyLanguage.EmptyFormatter`——
 * 它的 `format()` / `setReceiver()` 都是空方法体。也就是说此前菜单里的「格式化」
 * 虽然点击无报错，实际**什么都没做**（假按钮）。
 *
 * 这里不走 `Formatter` 接口：自己在后台线程算，算完调 sora 官方的
 * `CodeEditor.onFormatSucceed()` 应用 —— 那条路径自带 `beginBatchEdit`/`endBatchEdit`
 * （整份替换合成**一步撤销**）、输入连接失效、光标 `setSelectionAround` 钳制、滚动复位。
 *
 * 应用前做**双重失效校验**，任何一条命中就丢弃结果：
 *  1. `e.text !== origin` —— 期间切了标签（`setText` 会换新的 Content 对象）；
 *  2. `documentVersion` 变了 —— 期间用户还在打字。
 * 绝不把旧内容盖到新内容上。
 *
 * @param onFailed 走 viewModel 的消息通道提示（只在**内容不合法**时调用，
 *                 「不支持该类型」由菜单置灰，不会走到这里）。
 */
private fun runFormat(
    controller: EditorController,
    fileName: String,
    onFailed: (String) -> Unit
) {
    val e = controller.editor ?: return
    if (e.isEditable != true) return
    if (!CodeFormatters.supports(fileName)) return
    if (!controller.beginFormat()) return   // 上一次还没算完

    // 正文必须在 UI 线程取：Content 内部有锁，后台读容易和编辑并发。
    val origin = e.text
    val originVersion = origin.documentVersion
    val source = origin.toString()

    val worker = Thread(
        {
            val result = try {
                CodeFormatters.formatText(fileName, source)
            } catch (t: Throwable) {
                AppLog.e(TAG, "format crashed: ${t.javaClass.name}", t)
                null
            }
            e.postInLifecycle {
                try {
                    when {
                        result == null ->
                            onFailed(Str.get(R.string.editor_format_invalid))
                        // 已经是最简形态 → 不动文档，也就不会有一步空撤销
                        result == source -> Unit
                        e.text !== origin || e.text.documentVersion != originVersion ->
                            AppLog.d(TAG, "format discarded: document changed meanwhile")
                        // cursorRange 传 null：sora 会用 setSelectionAround 钳到合法位置
                        else -> e.onFormatSucceed(result, null)
                    }
                } finally {
                    controller.endFormat()
                }
            }
        },
        "uin-format"
    )
    worker.isDaemon = true
    worker.start()
}

// ==================== 主题应用 ====================
/**
 * 6.2.0：主题加载失败时**先退到内置的默认主题**，实在不行才用普通回退配色。
 *
 * 原因：一旦直接降级成普通 EditorColorScheme，TextMate 语法的 >=255 色号会查不到
 * 而变透明（详见 createFallbackScheme 的注释）。one-dark-pro / github-light 是随包
 * 必带且 JSON 合法的两个主题，作为兜底成功率远高于直接用纯色配色。
 */
private fun applyTheme(editor: CodeEditor?, themeName: String) {
    if (editor == null) return
    val fallbackName = if (DARK_THEMES.contains(themeName)) DARK_DEFAULT else LIGHT_DEFAULT

    // 依次尝试：用户所选主题 → 内置兜底主题
    val names = if (themeName == fallbackName) listOf(themeName) else listOf(themeName, fallbackName)
    for (name in names) {
        if (tryApplyTheme(editor, name)) {
            applyColorSchemeTweaks(editor)
            AppLog.d(DEBUG_TAG, "theme applied: $name")
            return
        }
    }
    AppLog.w(TAG, "all themes failed, using plain fallback palette: $themeName")
    editor.colorScheme =
        if (DARK_THEMES.contains(themeName)) getDarkColorScheme() else getLightColorScheme()
    applyColorSchemeTweaks(editor)
    editor.invalidate()
}


/**
 * 6.2.0 批次39（SE 审计 T2-15）：按当前 [EditorColorScheme] 微调滚动条三色与回弹光晕。
 *
 * 必须**在 colorScheme 之后**套用：`applyTheme()` 会整体换成
 * `TextMateColorScheme.create(...)` 的新实例，若只在 `applyRuntimeSettings` 里设置，
 * 换主题后滚动条会退回默认色、过拉光晕也会回落到 context 的 colorPrimary。
 * 因此这里同时被 `applyRuntimeSettings` 与 `applyTheme` 调用。
 *
 * 取色策略：滑块用「当前行号色」（最亮）、按下态用「普通行号色」、轨道用「整页背景」，
 * 这样在任意明/暗主题下都比默认灰阶更贴合配色，也不会出现不可见的轨道。
 */
private fun applyColorSchemeTweaks(editor: CodeEditor) {
    val scheme = editor.colorScheme ?: return
    scheme.setColor(
        EditorColorScheme.SCROLL_BAR_THUMB,
        scheme.getColor(EditorColorScheme.LINE_NUMBER_CURRENT)
    )
    scheme.setColor(
        EditorColorScheme.SCROLL_BAR_THUMB_PRESSED,
        scheme.getColor(EditorColorScheme.LINE_NUMBER)
    )
    scheme.setColor(
        EditorColorScheme.SCROLL_BAR_TRACK,
        scheme.getColor(EditorColorScheme.WHOLE_BACKGROUND)
    )
    editor.setEdgeEffectColor(scheme.getColor(EditorColorScheme.CURRENT_LINE))
    editor.invalidate()
}

/** 尝试应用指定主题；成功返回 true。 */
private fun tryApplyTheme(editor: CodeEditor, themeName: String): Boolean {
    return try {
        val themeRegistry = ThemeRegistry.getInstance()
        if (!themeRegistry.setTheme(themeName)) {
            // 注册表里没有 → 从 assets 现场加载
            val path = "textmate/themes/$themeName.json"
            val inputStream = editor.context.assets.open(path)
            val themeSource = IThemeSource.fromInputStream(inputStream, path, null)
            val themeModel = ThemeModel(themeSource, themeName)
            themeModel.isDark = DARK_THEMES.contains(themeName)
            themeRegistry.loadTheme(themeModel, true)
        }
        editor.colorScheme = TextMateColorScheme.create(themeRegistry)
        editor.invalidate()
        true
    } catch (e: Exception) {
        AppLog.w(TAG, "apply theme failed: $themeName, ${e.message}")
        false
    }
}

/**
 * 6.2.0 批次30/31：语法高亮不显示的**断链诊断**。
 *
 * 第一次采样打印 colorScheme / language / styles / 首行 span；4.2s 后**再采样一次**以区分
 * 「分析慢（稍后才出样式）」vs「分析卡死/异常（永远 null）」。
 *
 * 另外（若给了 scope）会只读 dump 一次该 scope 的 Grammar 内部状态（[dumpGrammarFor]）。
 *
 * 分析是异步的，故延迟调用以拿到已填充的 styles。
 */
private fun logHighlightDiagnostics(
    editor: CodeEditor?,
    tag: String,
    scope: String? = null,
    delayMs: Long = 800L
) {
    val e = editor ?: return
    // 6.2.0 批次39（SE 审计 T1-1）：改用 postDelayedInLifecycle —— 普通 postDelayed 在
    // 编辑器 release() 后仍会执行，届时 styles/colorScheme 已被置空，诊断回调会命中已释放对象。
    e.postDelayedInLifecycle({
        AppLog.d(DEBUG_TAG, "highlight-diag $tag: ${buildStylesSummary(e)}")
        // 第二次采样（约 5s）：区分「慢」和「卡死/异常」。
        e.postDelayedInLifecycle({
            AppLog.d(DEBUG_TAG, "highlight-diag $tag(+5s): ${buildStylesSummary(e)}")
            if (!scope.isNullOrEmpty()) {
                // 分析线程已停摆（styles 仍为 null）才复现分词：此时没有后台线程在编译
                // 同一 Grammar，前后两次 dump 还能顺带验证「计数器是否仍在增长」——
                // 若增长即说明确有并发，若不增长而 mapSize < lastRuleId 则是编译期真缺口。
                if (e.styles == null) {
                    probeTokenization(e, tag, scope)
                } else {
                    dumpGrammarFor(e, "recheck$tag", scope)
                }
            }
        }, 4200L)
        if (!scope.isNullOrEmpty()) {
            dumpGrammarFor(e, tag, scope)
        }
    }, delayMs)
}

/** 汇总 colorScheme 类型 / language 类型 / styles 是否产出 / 首行 span 的色号与解析色。 */
private fun buildStylesSummary(e: CodeEditor): String {
    return try {
        val scheme = e.colorScheme
        val lang = e.editorLanguage
        val styles = e.styles
        val sb = StringBuilder()
        sb.append("scheme=").append(scheme.javaClass.simpleName)
        sb.append(" lang=").append(lang?.javaClass?.simpleName ?: "null")
        sb.append(" styles=").append(if (styles == null) "null" else "ok")
        if (styles != null) {
            val reader = styles.spans.read()
            try {
                val spans = reader.getSpansOnLine(0)
                sb.append(" line0spans=").append(spans.size)
                val detail = StringBuilder()
                val n = minOf(spans.size, 6)
                for (i in 0 until n) {
                    val sp = spans[i]
                    val fg = sp.foregroundColorId
                    val color = scheme.getColor(fg)
                    if (i > 0) detail.append(',')
                    detail.append('@').append(sp.column)
                    detail.append(" fg=").append(fg)
                    detail.append(" rgb=").append(Integer.toHexString(color))
                }
                if (spans.size > n) detail.append(",…")
                sb.append(" [").append(detail).append(']')
            } finally {
                reader.moveToLine(-1)
            }
        }
        sb.toString()
    } catch (ex: Exception) {
        "summary-failed: ${ex.message}"
    }
}

/**
 * 只读地 dump 一次该 scope 的 Grammar 内部状态（见 [dumpGrammarState]）。
 *
 * 6.2.0 批次33：原先这里还会在**后台线程**跑 tokenizeLine2 去复现 sora 后台的异常，
 * 但那会与 sora 的分析线程**并发编译同一 Grammar 实例** —— `registerRule` 里
 * `++_lastRuleId` 与 `put` 并不原子，两个线程可能算出同一个 RuleId 并互相覆盖，
 * 实测把 mapSize 打得比 lastRuleId 还小（39 vs 44），即**诊断本身在破坏被测对象**。
 * 故移除并发分词，只保留只读观测（rootId / lastRuleId / mapSize / selfId）——
 * 「styles=ok/null + line0spans」这一层已经足以判断高亮是否真的生效。
 */
private fun dumpGrammarFor(e: CodeEditor, tag: String, scope: String) {
    val grammar = try {
        GrammarRegistry.getInstance().findGrammar(scope)
    } catch (ex: Exception) {
        AppLog.w(DEBUG_TAG, "grammar-state $tag: findGrammar($scope) failed: ${ex.message}")
        return
    }
    if (grammar == null) {
        AppLog.w(DEBUG_TAG, "grammar-state $tag: grammar=null scope=$scope")
        return
    }
    dumpGrammarState(grammar, tag, scope)
}

/**
 * 6.2.0 批次35：把 sora 后台分析**真正吞掉的异常**打出来。
 *
 * sora 的 AsyncIncrementalAnalyzeManager 捕获分析异常后只写 logcat（tag `AsyncAnalysis`），
 * 并**直接结束分析线程** → `editor.styles` 永远为 null，AppLog 里却看不到失败原因。
 * 这里自己跑一遍同样的 `tokenizeLine2`，catch 后连堆栈一起落进 AppLog。
 *
 * 只在 `styles == null` 的 +5s 采样里调用（见 [logHighlightDiagnostics]）：那时后台分析
 * 已经死掉，才不会与它并发编译同一实例；跑之前与跑之后各 dump 一次内部状态 ——
 * 若两次之间 `_lastRuleId` 还在涨，就说明确有并发线程；若不涨而 `mapSize < lastRuleId`，
 * 则是编译期真有「自增了却没 put」的缺口（`RuleFactory` 里 `desc.setId()` 在 lambda 内
 * 先执行、规则构造抛异常时 `put` 会被跳过）。
 *
 * 在后台线程跑、最多 500 行，避免卡住 UI。
 */
private fun probeTokenization(e: CodeEditor, tag: String, scope: String) {
    val grammar = try {
        GrammarRegistry.getInstance().findGrammar(scope)
    } catch (ex: Exception) {
        AppLog.w(DEBUG_TAG, "tokenize-probe $tag: findGrammar($scope) failed: ${ex.message}")
        return
    }
    if (grammar == null) {
        AppLog.w(DEBUG_TAG, "tokenize-probe $tag: grammar=null scope=$scope")
        return
    }
    dumpGrammarState(grammar, "probe-before$tag", scope)

    val text = e.text
    val total = text.lineCount
    val limit = minOf(total, 500)
    val lines = ArrayList<String>(limit)
    for (i in 0 until limit) {
        lines.add(text.getLine(i).toString())
    }

    Thread {
        try {
            var state: IStateStack? = null
            for (i in lines.indices) {
                val res = grammar.tokenizeLine2(lines[i], state, Duration.ofSeconds(2))
                state = res.ruleStack
            }
            AppLog.d(DEBUG_TAG, "tokenize-probe $tag: OK scope=$scope lines=$limit/$total")
        } catch (ex: Throwable) {
            AppLog.e(
                DEBUG_TAG,
                "tokenize-probe $tag: FAILED scope=$scope ${ex.javaClass.name}: ${ex.message}",
                ex
            )
        } finally {
            dumpGrammarState(grammar, "probe-after$tag", scope)
        }
    }.start()
}

/**
 * 反射读取 tm4e `Grammar` 内部状态，验证「共享 raw 被别的实例占位」这一根因。
 *
 * 已核实 tm4e 的 `ObjectCloner.deepClone` 末分支 `return obj`（不真正克隆普通对象），因此
 * `initGrammar` 里 `repo.setSelf(...)`、规则的 `desc.setId(...)` 都写在**全局共享**的同一份
 * raw grammar 上。若某个 scope 被其它语法 `include`（如 markdown include text.xml），先编译的
 * 实例会把共享 self 的 id 占掉；后编译的实例 `getRule` 就查不到 → `No rule with index 1`。
 *
 * `selfId != null`（tokenize 前）即证明已被别的实例占位。
 */
private fun dumpGrammarState(grammar: Any, tag: String, scope: String) {
    try {
        val c = grammar.javaClass
        fun field(n: String) = c.getDeclaredField(n).apply { isAccessible = true }
        val rootId = field("_rootId").get(grammar)
        val lastRuleId = field("_lastRuleId").getInt(grammar)
        @Suppress("UNCHECKED_CAST")
        val map = field("_ruleId2desc").get(grammar) as Map<*, *>
        val raw = field("_grammar").get(grammar)
        val repo = raw?.javaClass?.getMethod("getRepository")?.invoke(raw)
        val self = repo?.javaClass?.getMethod("getSelf")?.invoke(repo)
        val selfId = self?.javaClass?.getMethod("getId")?.invoke(self)
        AppLog.d(
            DEBUG_TAG,
            "grammar-state $tag: scope=$scope rootId=$rootId lastRuleId=$lastRuleId " +
                "mapSize=${map.size} selfId=$selfId"
        )
    } catch (ex: Throwable) {
        AppLog.w(DEBUG_TAG, "grammar-state $tag failed: ${ex.javaClass.simpleName}: ${ex.message}")
    }
}

// ==================== 运行期外观设置 ====================
// 6.2.0 批次39（SE 审计 T2-12）：原先只开前三个（行内前/中/后空白），
// 于是即便设置了 LF/CRLF/CR 也**看不到行尾符**。这里补齐另外 5 个 flag：
//  - FOR_EMPTY_LINE  空行的缩进空白也画出来（原来只画有字符的行）
//  - LINE_SEPARATOR  行尾符（配合 lineSeparator 设置排查 CRLF/LF 混用）
//  - TAB_SAME_AS_SPACE Tab 画成等宽点，避免与真实空格混淆
//  - WHITESPACE_IN_SELECTION 选区内空白高亮，方便看清选了什么
//  - SOFT_WRAP       自动换行处画折行标记（wordWrap 开启时才有意义）
private val WHITESPACE_FLAGS =
    CodeEditor.FLAG_DRAW_WHITESPACE_LEADING or
        CodeEditor.FLAG_DRAW_WHITESPACE_INNER or
        CodeEditor.FLAG_DRAW_WHITESPACE_TRAILING or
        CodeEditor.FLAG_DRAW_WHITESPACE_FOR_EMPTY_LINE or
        CodeEditor.FLAG_DRAW_LINE_SEPARATOR or
        CodeEditor.FLAG_DRAW_TAB_SAME_AS_SPACE or
        CodeEditor.FLAG_DRAW_WHITESPACE_IN_SELECTION or
        CodeEditor.FLAG_DRAW_SOFT_WRAP

private fun applyRuntimeSettings(editor: CodeEditor, s: EditorSettings) {
    // sora 的 setDividerWidth / setCursorWidth / setTextBorderWidth 只收 **px**，
    // 这里从编辑器自身取密度换算，避免在 Composable 外依赖 CompositionLocal。
    val density = editor.context.resources.displayMetrics.density
    fun dp(v: Float): Float = v * density

    editor.setTextSize(s.fontSize)
    editor.tabWidth = s.tabWidth
    // 6.2.0 批次39（SE 审计 T1-4）：CodeEditor.setTabWidth 只影响渲染，代码块计算用的是
    // language.getTabSize()（默认恒为 4）。不同步则 tabWidth 调成 2/8 时缩进参考线、
    // 当前块高亮、粘性滚动的块边界会错位。
    (editor.editorLanguage as? TextMateLanguage)?.setTabSize(s.tabWidth)
    editor.setWordwrap(s.wordWrap)
    editor.setLineNumberEnabled(s.showLineNumbers)
    editor.setHighlightCurrentLine(s.currentLine)
    editor.setHighlightBracketPair(s.bracketMatching)
    editor.setPinLineNumber(true)
    editor.setFirstLineNumberAlwaysVisible(true)
    // 6.2.0 批次39（SE 审计 T2-12）：此前只开 LEADING|INNER|TRAILING，已有的 LF/CRLF/CR
    // 设置根本「看不见」行尾符；这里补齐空行空白、行尾符、Tab 占位、选区内空白、软换行标记。
    editor.setNonPrintablePaintingFlags(if (s.showNonPrintable) WHITESPACE_FLAGS else 0)
    editor.props.stickyScroll = s.stickyScroll
    editor.setScalable(s.pinchScalable)
    editor.props.autoIndent = true
    editor.getComponent(EditorAutoCompletion::class.java)?.isEnabled = s.autoComplete

    // ---- 6.2.0 批次32：补齐此前从未启用的 sora 编辑器能力 ----

    // 撤销 / 重做。注：0.24.4 的 initialize() 已默认 setUndoEnabled(true)，此处幂等保留，
    // 以防未来版本默认值变更；原文「sora 默认是关闭的」的说法已过时。
    editor.setUndoEnabled(true)

    // 当前块的缩进参考线（比「高亮当前行」更能看清嵌套层级）。
    editor.setBlockLineEnabled(s.blockLine)
    editor.setBlockLineWidth(1f)

    // 浮动「行:列」位置面板：光标移动时在编辑区角落提示位置。
    editor.setDisplayLnPanel(s.lnPanel)

    // 选中文字后自动跟手显示的「剪切 / 复制 / 粘贴」悬浮窗（内置组件，只需打开）。
    editor.getComponent(EditorTextActionWindow::class.java)?.isEnabled = s.textActionPopup

    // 滚动时选区吸附屏幕边缘（长文档里拖选不会「看不见选到哪了」）。
    editor.setStickyTextSelection(true)

    // 连字：配合 Fira Code / JetBrains Mono 等字体渲染 -> ⇥ != 等合字。
    editor.setLigatureEnabled(s.ligature)

    // 文本区内边距 + 细滚动条 + 软键盘常显（便于随时输入代码）。
    editor.setTextBorderWidth(dp(4f))
    editor.setScrollBarEnabled(true)
    editor.setVerticalScrollBarEnabled(true)
    editor.setSoftKeyboardEnabled(true)
    editor.setVerticalExtraSpaceFactor(0.2f)

    // 光标：略宽于默认、平移有动画、可选闪烁。
    editor.setCursorWidth(dp(2f))
    editor.setCursorAnimationEnabled(true)
    if (s.cursorBlink) editor.setCursorBlinkPeriod(530) else editor.setCursorBlinkPeriod(0)

    // 行分隔线 —— settings.lineDivider 此前有字段、有 UI 开关，却从未应用到编辑器。
    editor.setDividerWidth(if (s.lineDivider) dp(2f) else 0f)
    editor.setDividerMargin(dp(6f))

    // ---- 6.2.0 批次34：第二轮功能审计补齐的 sora 能力 ----

    // 排版：行距倍数与字宽（横向缩放），均需在字号/字体变更后重设。
    editor.setLineSpacingMultiplier(s.lineSpacing)
    editor.setTextScaleX(s.textScaleX)

    // 浮动「行:列」面板的停靠位置（左 / 上 / 右 / 下 / 中）。
    editor.setLnPanelPosition(s.lnPanelPosition)
    // 6.2.0 批次39（SE 审计 T1-3）：**修 bug** —— 只设 setLnPanelPosition 是不够的。
    // 默认模式是 FOLLOW，而 EditorRenderer.drawLineInfoPanel() 的 FOLLOW 分支只认
    // TOP/BOTTOM/CENTER 三个精确值，选「左(1)」或「右(4)」会落进 else 分支、照样画在
    // 右侧滚动条旁 —— 设置面板里那两个选项此前**完全无效**。
    // 停靠到固定边（左/右）必须切 FIXED 模式。
    editor.setLnPanelPositionMode(
        if (s.lnPanelPosition == LineInfoPanelPosition.LEFT ||
            s.lnPanelPosition == LineInfoPanelPosition.RIGHT
        ) LineInfoPanelPositionMode.FIXED else LineInfoPanelPositionMode.FOLLOW
    )

    // 此后由输入法插入的新行使用哪种换行符；不影响文档中已有的行尾，
    // 也不接受 LineSeparator.NONE（sora 内部会抛异常）。
    editor.setLineSeparator(
        when (s.lineSeparator) {
            1 -> LineSeparator.CRLF
            2 -> LineSeparator.CR
            else -> LineSeparator.LF
        }
    )

    // 当前代码块高亮（sora 默认开，这里让设置项真正生效）。
    editor.setHighlightCurrentBlock(s.highlightBlock)

    // void / null 等函数占位字符的渲染（sora 默认开）。
    editor.setRenderFunctionCharacters(s.renderFunctionChars)

    // ---- 6.2.0 批次39（SE 审计 T2-11/15/16/17/18）：DirectAccessProps 调优包 + 组件细节 ----

    // 点行号整行选中（0.24.4 默认是 PLACE_SELECTION_HOME，只把光标挪到行首）。
    editor.props.actionWhenLineNumberClicked = DirectAccessProps.LN_ACTION_SELECT_LINE

    // 连按退格按 tab 宽度整段删缩进（-1 = 跟随 tabWidth；默认 1 = 每次只删 1 个空格，
    // 在缩进较深时需要连点很多次）。
    editor.props.deleteMultiSpaces = -1
    // 滚到头/尾时允许 Android 的 EdgeEffect 过拉光晕。sora 默认关闭，此前 setEdgeEffectColor
    // 即使设了也毫无可见效果（见下方 T2-15 的配色）。
    editor.props.overScrollEnabled = true
    // 注：这里**有意不碰** `disallowSuggestions`（为真会强制重启输入连接、拒绝组字中的
    // composing text，会打断中文/日文输入法），也保留 `useICULibToSelectWords` /
    // `enableRoundTextBackground` / `formatPastedText` / `clipboardTextLengthLimit`
    // 的 0.24.4 默认值（其中 clipboardTextLengthLimit 默认已是 512KB，往下调反而会截断复制）。

    // 6.2.0 批次39（SE 审计 T2-16）：放大镜（sora 默认一直开着，此前既无感知也无开关）。
    editor.getComponent(Magnifier::class.java)?.let { mag ->
        mag.isEnabled = s.showMagnifier
        if (s.showMagnifier) {
            mag.setScaleFactor(1.3f)
            // 强制只在编辑区内显示，避免小屏上放大镜被顶出可视区。
            mag.setWithinEditorForcibly(true)
        }
    }

    // 6.2.0 批次39（SE 审计 T2-17）：补全窗此前只开了 isEnabled，全部细节走默认值。
    editor.getComponent(EditorAutoCompletion::class.java)?.let { ac ->
        ac.setCompletionWndPositionMode(EditorAutoCompletion.WINDOW_POS_MODE_FOLLOW_CURSOR_ALWAYS)
        ac.setHighlightMatchedLabel(true)
        ac.setEnabledAnimation(true)
        ac.setMaxHeight(dp(220f).toInt())
    }

    // 6.2.0 批次39（SE 审计 T2-15）：必须在换完 colorScheme 之后再套一次（见 applyTheme）。
    applyColorSchemeTweaks(editor)
    // 字距：与已有的 setTextScaleX（字宽）互补，0 = 不调整。
    editor.setTextLetterSpacing(0f)

    if (s.fontFamily.isNotBlank()) {
        try {
            editor.setTypefaceText(Typeface.create(s.fontFamily, Typeface.NORMAL))
        } catch (e: Exception) {
            AppLog.w(TAG, "unknown font family: ${s.fontFamily}")
        }
    } else {
        editor.setTypefaceText(Typeface.DEFAULT)
    }
}

// ============================================================
// 编辑器 ↔ Compose 桥接：以回调替代旧版 40×50ms 轮询 hack
// ============================================================
class EditorController {
    /** 用 Compose 状态持有，确保 SearchPanel 等在编辑器创建后能拿到实例并重组合。 */
    var editor: CodeEditor? by mutableStateOf(null)
        private set
    var attachedTabId: String? = null
    var attachedTheme: String? = null
    /** 6.2.0 批次16：最近一次应用的语法 override，变化时重设语言。 */
    var attachedLangScope: String? = null
    /** 记录最近一次已应用的外观设置，避免每次按键触发的重组合都重设一遍。 */
    var attachedSettings: EditorSettings? = null
    var attachedEditable: Boolean? = null
    /** 6.2.0 批次26：是否已在「语法就绪后」补设过一次语言（消除启动竞态的 EmptyLanguage）。 */
    var attachedGrammarReady: Boolean = false
    /** 6.2.0 批次31：十六进制颜色背景。setEditorLanguage 会清掉背景，需在重设语言后手动 refresh。 */
    var hexHints: HexColorHints? = null
    var onContentChange: ((String) -> Unit)? = null
    var onCursorChange: ((line: Int, column: Int, selection: Int) -> Unit)? = null
    /**
     * 6.2.0 批次39（SE 审计 T2-10）：硬件键盘快捷键里**需要界面层**处理的动作。
     * save=保存当前标签 / search=打开搜索面板 / goto=跳转到行对话框。
     */
    var onKeyAction: ((String) -> Unit)? = null
    /**
     * 6.2.0 批次40：格式化是否在后台进行中。
     * sora 的 `isFormatting()` 只反映它自己的 `Formatter`（我们没用），
     * 所以自己记一个用于防重入。
     */
    var formatting: Boolean = false
        private set

    fun beginFormat(): Boolean {
        if (formatting) return false
        formatting = true
        return true
    }

    fun endFormat() {
        formatting = false
    }

    private var contentListener: SubscriptionReceipt<ContentChangeEvent>? = null
    private var selectionListener: SubscriptionReceipt<SelectionChangeEvent>? = null
    private var keyBindingListener: SubscriptionReceipt<KeyBindingEvent>? = null

    fun attach(view: CodeEditor) {
        if (editor === view) return
        editor = view
        contentListener = view.subscribeEvent(ContentChangeEvent::class.java) { event, _ ->
            if (event.action == ContentChangeEvent.ACTION_SET_NEW_TEXT) return@subscribeEvent
            onContentChange?.invoke(view.text.toString())
        }
        selectionListener = view.subscribeEvent(SelectionChangeEvent::class.java) { _, _ ->
            val c = view.cursor ?: return@subscribeEvent
            val sel = if (c.isSelected) kotlin.math.abs(c.right - c.left) else 0
            onCursorChange?.invoke(c.leftLine + 1, c.leftColumn + 1, sel)
        }
        // ---- 6.2.0 批次39（SE 审计 T2-10）：硬件键盘快捷键 ----
        // sora 的 EditorKeyEventHandler 只在「Shift|Ctrl|Alt + A-Z/方向键/Home/End」时派发
        // KeyBindingEvent，且它派发在内建处理**之前**；一旦 markAsConsumed() 就短路返回。
        // 因此这里**只截获内建 handler 里没有的组合**（Ctrl+S/F/G），以及把 Ctrl+Shift+Z
        // 纠正成 redo（内建的 Ctrl+Z 分支不看 Shift，会把 redo 也变回 undo）；
        // 其余（Ctrl+C/V/X/A/Z/Y/D…）一律原样放行，不与内建行为抢。
        // 只在 DOWN 分支处理，避免 UP 时再触发一次。
        keyBindingListener = view.subscribeEvent(KeyBindingEvent::class.java) { e, _ ->
            if (e.eventType != EditorKeyEvent.Type.DOWN || !e.isCtrlPressed) return@subscribeEvent
            when {
                !e.isShiftPressed && e.keyCode == KeyEvent.KEYCODE_S -> {
                    onKeyAction?.invoke("save"); e.markAsConsumed()
                }
                !e.isShiftPressed && e.keyCode == KeyEvent.KEYCODE_F -> {
                    onKeyAction?.invoke("search"); e.markAsConsumed()
                }
                !e.isShiftPressed && e.keyCode == KeyEvent.KEYCODE_G -> {
                    onKeyAction?.invoke("goto"); e.markAsConsumed()
                }
                e.isShiftPressed && e.keyCode == KeyEvent.KEYCODE_Z -> {
                    if (view.isEditable) view.redo()
                    e.markAsConsumed()
                }
                else -> {}
            }
        }
    }

    /**
     * 6.2.0 批次39（SE 审计 T1-1）：**修资源泄漏**。
     *
     * 此前只置空字段、从不调 `CodeEditor.release()`。而 sora 的
     * `onDetachedFromWindow()` 只会派发事件 + 停光标闪烁，**不销毁语言**；
     * 于是每次 AndroidView 因重组/切页被重建，上一个 `TextMateLanguage` 的
     * `AnalyzeManager` 后台线程和 `Formatter` 都会永久存活（切换文件越久泄漏越多）。
     *
     * `release()` 会依次 `analyzeManager.destroy()` → `formatter.destroy()` →
     * `language.destroy()`，并解绑 `ContentListener` / `colorScheme`。
     * 语言实例由 [setEditorLanguage] 每次 `TextMateLanguage.create()` 新建，不被共享，
     * 所以销毁是安全的。
     */
    fun detach() {
        // 先让附属组件解绑（HexColorHints 自己订阅了 ContentChangeEvent）。
        hexHints?.detach()
        hexHints = null
        editor?.let { ed ->
            if (!ed.isReleased) {
                runCatching { ed.release() }
                    .onFailure { AppLog.w(TAG, "editor release failed: ${it.message}") }
            }
        }
        editor = null
        attachedTabId = null
        attachedTheme = null
        attachedLangScope = null
        attachedSettings = null
        attachedEditable = null
        attachedGrammarReady = false
        formatting = false
        contentListener?.unsubscribe()
        selectionListener?.unsubscribe()
        keyBindingListener?.unsubscribe()
        contentListener = null
        selectionListener = null
        keyBindingListener = null
    }

    fun currentText(): String = editor?.text?.toString() ?: ""
}

// ============================================================
// 主题选择对话框
// ============================================================
@Composable
fun ThemeSelectionDialog(
    currentTheme: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        // 6.2.0 批次17：与新建/重命名/删除弹窗统一 —— 改用与主应用同款渐变背景
        // （dialogBackgroundOf），原先 UnifiedCard(solid) 是纯色、风格割裂。
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 480.dp)
                .height(520.dp)
                .then(Modifier.dialogBackgroundOf(RoundedCornerShape(AppDimens.dialogCornerRadius))),
            shape = RoundedCornerShape(AppDimens.dialogCornerRadius),
            color = Color.Transparent,
            shadowElevation = 0.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(AppDimens.spacingLarge),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = Str.get(R.string.editor_theme),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    UnifiedIconButton(
                        icon = Icons.Default.Close,
                        onClick = onDismiss,
                        contentDescription = Str.get(R.string.cancel)
                    )
                }
                HorizontalDivider()
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(AppDimens.spacingSmall)
                ) {
                    // 6.2.0：移除「跟随系统」项 —— 系统并未定义代码编辑器主题，
                    // 该选项只会按深浅色在两个主题间切换，语义含糊。默认固定 dracula。
                    items(THEMES) { theme ->
                        ThemeRow(
                            name = theme,
                            selected = theme == currentTheme,
                            isDark = theme in DARK_THEMES
                        ) { onSelect(theme) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ThemeRow(
    name: String,
    selected: Boolean,
    isDark: Boolean?,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = AppDimens.spacingLarge, vertical = AppDimens.spacingMedium),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (isDark != null) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .background(
                            if (isDark) Color(0xFF1E1E1E) else Color(0xFFF5F5F5),
                            RoundedCornerShape(4.dp)
                        )
                )
                Spacer(modifier = Modifier.width(AppDimens.spacingMedium))
            }
            Text(text = name, style = MaterialTheme.typography.bodyMedium)
        }
        if (selected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

// ============================================================
// 语法高亮选择器（6.2.0 批次16）
// ============================================================
@Composable
fun SyntaxSelectionDialog(
    currentScope: String?,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        // 6.2.0 批次17：与主题选择器/新建/重命名等弹窗统一 —— 同款渐变背景。
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 480.dp)
                .height(520.dp)
                .then(Modifier.dialogBackgroundOf(RoundedCornerShape(AppDimens.dialogCornerRadius))),
            shape = RoundedCornerShape(AppDimens.dialogCornerRadius),
            color = Color.Transparent,
            shadowElevation = 0.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(AppDimens.spacingLarge),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = Str.get(R.string.editor_syntax),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    UnifiedIconButton(
                        icon = Icons.Default.Close,
                        onClick = onDismiss,
                        contentDescription = Str.get(R.string.cancel)
                    )
                }
                HorizontalDivider()
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(AppDimens.spacingSmall)
                ) {
                    items(LANGUAGES) { (label, scope) ->
                        SyntaxRow(
                            // scope == null 表示「自动（按后缀名）」，用本地化文案
                            label = if (scope == null) Str.get(R.string.editor_syntax_auto) else label,
                            selected = scope == currentScope
                        ) { onSelect(scope) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SyntaxRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = AppDimens.spacingLarge, vertical = AppDimens.spacingMedium),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        if (selected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

// ============================================================
// 文件树
// ============================================================
@Composable
fun FileTreePanel(
    viewModel: EditorViewModel,
    activePath: String?,
    onOpenFile: (java.io.File) -> Unit,
    onOpenFolder: () -> Unit,
    modifier: Modifier = Modifier
) {
    val root by viewModel.projectRoot.collectAsState()
    val expanded by viewModel.expandedDirs.collectAsState()
    val selectedDir by viewModel.selectedDir.collectAsState()
    val treeVersion by viewModel.treeVersion.collectAsState()
    // 6.2.0 批次16：treeVersion 作为 remember 键 —— 任何文件系统变更（重命名/删除/新建）
    // 都会 +1，强制重算 buildTree()，修复旧版「重命名后树不刷新」的 bug。
    val nodes = remember(root, expanded, treeVersion) { viewModel.buildTree() }
    val flat = remember(nodes) { flattenNodes(nodes) }
    val listState = rememberLazyListState()
    // 6.2.0 批次23：外部打开文件后把祖先目录展开、树根设为内部存储，这里再把视口滚到该文件，
    // 用户点开目录树即可直接看到当前打开的文件被定位高亮。
    LaunchedEffect(activePath, flat) {
        val idx = flat.indexOfFirst { it.first.path == activePath }
        if (idx >= 0) listState.animateScrollToItem(idx)
    }
    var renameTarget by remember { mutableStateOf<java.io.File?>(null) }
    var deleteTarget by remember { mutableStateOf<java.io.File?>(null) }
    var newName by remember { mutableStateOf("") }
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    var showNewFileDialog by remember { mutableStateOf(false) }
    var newFileName by remember { mutableStateOf("") }

    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppDimens.spacingMedium, vertical = AppDimens.spacingSmall),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = Str.get(R.string.editor_file_tree),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = AppColors.textSecondary()
            )
            Row {
                // 6.2.0 批次17：打开文件夹入口移到侧栏头部（顶栏 FolderOpen 已移除）。
                UnifiedIconButton(
                    icon = Icons.Default.FolderOpen,
                    onClick = onOpenFolder,
                    contentDescription = Str.get(R.string.editor_open_folder)
                )
                UnifiedIconButton(
                    icon = Icons.Default.CreateNewFolder,
                    onClick = {
                        newFolderName = ""
                        showNewFolderDialog = true
                    },
                    contentDescription = Str.get(R.string.editor_new_folder)
                )
                // 6.2.0 批次19：新建文件弹窗不再预填默认文件名，输入框留空让用户自己填。
                UnifiedIconButton(
                    icon = Icons.Default.PostAdd,
                    onClick = {
                        newFileName = ""
                        showNewFileDialog = true
                    },
                    contentDescription = Str.get(R.string.new_file)
                )
            }
        }
        HorizontalDivider()
        if (root == null) {
            UnifiedEmptyState(
                title = Str.get(R.string.editor_no_project),
                description = Str.get(R.string.editor_no_project_hint),
                icon = Icons.Default.FolderOpen,
                actionText = Str.get(R.string.editor_open_folder),
                onAction = onOpenFolder,
                modifier = Modifier.padding(AppDimens.spacingLarge)
            )
        } else {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                items(flat, key = { it.first.path }) { (node, depth) ->
                    FileNodeRow(
                        node = node,
                        depth = depth,
                        isActive = node.path == activePath,
                        isSelectedDir = node is FileNode.DirNode && node.path == selectedDir?.absolutePath,
                        onToggle = {
                            if (node is FileNode.DirNode) {
                                viewModel.toggleDir(node.path)
                                // 点目录同时标记为「选中」，后续新建文件/文件夹默认落到这里
                                viewModel.selectDir(node.file)
                            } else {
                                onOpenFile(node.file)
                            }
                        },
                        onRename = {
                            renameTarget = node.file
                            newName = node.name
                        },
                        onDelete = { deleteTarget = node.file }
                    )
                }
            }
        }
    }

    // 6.2.0 批次18：改用 [UnifiedDialog]（现已修复为真正的 Dialog 窗口，光标定位正确），
    // 并自动聚焦输入框。与主应用弹窗同款渐变背景。
    renameTarget?.let { file ->
        val focusRequester = remember { FocusRequester() }
        LaunchedEffect(Unit) { focusRequester.requestFocus() }
        UnifiedDialog(
            onDismissRequest = { renameTarget = null },
            title = Str.get(R.string.rename_file),
            content = {
                UnifiedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    singleLine = true,
                    modifier = Modifier.focusRequester(focusRequester)
                )
            },
            confirmButton = {
                UnifiedButton(
                    text = Str.get(R.string.rename),
                    onClick = {
                        if (viewModel.renameFile(file, newName)) renameTarget = null
                    },
                    modifier = Modifier.weight(1f)
                )
            },
            dismissButton = {
                UnifiedButton(
                    text = Str.get(R.string.cancel),
                    onClick = { renameTarget = null },
                    variant = ButtonVariant.Outlined,
                    modifier = Modifier.weight(1f)
                )
            }
        )
    }

    deleteTarget?.let { file ->
        UnifiedConfirmDialog(
            title = Str.get(R.string.editor_delete_confirm_title),
            message = Str.get(R.string.editor_delete_confirm_message, file.name),
            isDestructive = true,
            onConfirm = {
                viewModel.deleteFile(file)
                deleteTarget = null
            },
            onDismiss = { deleteTarget = null }
        )
    }

    // 6.2.0 批次18：改用 [UnifiedDialog]（已修复为 Dialog 窗口，光标定位正确）+ 自动聚焦。
    // 支持 `a/b` 这类名字。
    if (showNewFolderDialog) {
        val focusRequester = remember { FocusRequester() }
        LaunchedEffect(Unit) { focusRequester.requestFocus() }
        UnifiedDialog(
            onDismissRequest = { showNewFolderDialog = false },
            title = Str.get(R.string.editor_new_folder),
            content = {
                UnifiedTextField(
                    value = newFolderName,
                    onValueChange = { newFolderName = it },
                    singleLine = true,
                    placeholder = Str.get(R.string.editor_name_hint),
                    modifier = Modifier.focusRequester(focusRequester)
                )
            },
            confirmButton = {
                UnifiedButton(
                    text = Str.get(R.string.editor_create),
                    onClick = {
                        if (viewModel.newFolder(newFolderName) != null) showNewFolderDialog = false
                    },
                    modifier = Modifier.weight(1f)
                )
            },
            dismissButton = {
                UnifiedButton(
                    text = Str.get(R.string.cancel),
                    onClick = { showNewFolderDialog = false },
                    variant = ButtonVariant.Outlined,
                    modifier = Modifier.weight(1f)
                )
            }
        )
    }

    // 6.2.0 批次19：新建文件弹窗不预填默认文件名（留空），只留灰色提示；名称为空则不创建。
    if (showNewFileDialog) {
        val focusRequester = remember { FocusRequester() }
        LaunchedEffect(Unit) { focusRequester.requestFocus() }
        UnifiedDialog(
            onDismissRequest = { showNewFileDialog = false },
            title = Str.get(R.string.new_file),
            content = {
                UnifiedTextField(
                    value = newFileName,
                    onValueChange = { newFileName = it },
                    singleLine = true,
                    placeholder = Str.get(R.string.editor_file_name_hint),
                    modifier = Modifier.focusRequester(focusRequester)
                )
            },
            confirmButton = {
                UnifiedButton(
                    text = Str.get(R.string.editor_create),
                    onClick = {
                        val name = newFileName.trim()
                        if (name.isNotEmpty() && viewModel.newFile(name) != null) showNewFileDialog = false
                    },
                    modifier = Modifier.weight(1f)
                )
            },
            dismissButton = {
                UnifiedButton(
                    text = Str.get(R.string.cancel),
                    onClick = { showNewFileDialog = false },
                    variant = ButtonVariant.Outlined,
                    modifier = Modifier.weight(1f)
                )
            }
        )
    }
}

private fun flattenNodes(
    nodes: List<FileNode>,
    depth: Int = 0
): List<Pair<FileNode, Int>> = nodes.flatMap { node ->
    val self = listOf(node to depth)
    if (node is FileNode.DirNode && node.isExpanded) {
        self + flattenNodes(node.children, depth + 1)
    } else {
        self
    }
}

@Composable
private fun FileNodeRow(
    node: FileNode,
    depth: Int,
    isActive: Boolean,
    isSelectedDir: Boolean = false,
    onToggle: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    // 高亮层级：打开的文件 > 选中的目录（新建落点）> 普通
    val rowBg = when {
        isActive -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        isSelectedDir -> MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)
        else -> Color.Transparent
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(rowBg)
            .clickable(onClick = onToggle)
            .padding(start = (8 + depth * 14).dp, end = AppDimens.spacingSmall)
            .height(40.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (node is FileNode.DirNode) {
            Icon(
                imageVector = if (node.isExpanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(AppDimens.iconSmall),
                tint = AppColors.textSecondary()
            )
        } else {
            Spacer(modifier = Modifier.size(AppDimens.iconSmall))
        }
        Spacer(modifier = Modifier.width(AppDimens.spacingSmall))
        Icon(
            imageVector = if (node is FileNode.DirNode) {
                if (node.isExpanded) Icons.Default.FolderOpen else Icons.Default.Folder
            } else getFileIcon(node.name),
            contentDescription = null,
            modifier = Modifier.size(AppDimens.iconSmall),
            tint = if (node is FileNode.DirNode) MaterialTheme.colorScheme.primary
            else AppColors.textSecondary()
        )
        Spacer(modifier = Modifier.width(AppDimens.spacingSmall))
        Text(
            text = node.name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = if (isActive || isSelectedDir) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.weight(1f))
        // 6.2.0 批次12 修复「文件夹无法重命名/删除」：目录也渲染 ⋮ 菜单。
        // 批次14：菜单由「居中 Dialog（近似全屏）」改为锚定在 ⋮ 按钮旁的 DropdownMenu 小弹层。
        Box {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = null,
                modifier = Modifier
                    .size(AppDimens.iconSmall)
                    .clickable { menuOpen = true },
                tint = AppColors.textSecondary()
            )
            // DropdownMenu 在组合处自动锚定到父 Box（即 ⋮ 按钮），点击外部自动关闭
            if (menuOpen) {
                FileNodeMenu(
                    onDismiss = { menuOpen = false },
                    onRename = {
                        menuOpen = false
                        onRename()
                    },
                    onDelete = {
                        menuOpen = false
                        onDelete()
                    }
                )
            }
        }
    }
}

@Composable
private fun FileNodeMenu(
    onDismiss: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    // 6.2.0 批次14：由「居中 Dialog（近似全屏）」改为锚定在 ⋮ 按钮旁的小弹层。
    // DropdownMenu 自动定位到调用处的父 Box（即 ⋮ 图标），宽度收窄、点击外部自动关闭。
    // 注意：Material3 的 DropdownMenu 首参是 expanded（外部已用 if (menuOpen) 包裹，此处恒 true）。
    DropdownMenu(
        expanded = true,
        onDismissRequest = onDismiss,
        modifier = Modifier
            .width(170.dp)
            .background(AppColors.dialogBackground())
    ) {
        DropdownMenuItem(
            text = { Text(Str.get(R.string.rename), style = MaterialTheme.typography.bodyMedium) },
            onClick = onRename
        )
        DropdownMenuItem(
            text = {
                Text(
                    Str.get(R.string.editor_delete),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            },
            onClick = onDelete
        )
    }
}

/**
 * 屏幕最左侧的窄感应条（默认 28dp）。
 *
 * ModalNavigationDrawer 内置的滑动手势**作用于整个内容区**，会把编辑器里
 * 横向滚动代码的拖拽一并抢走。为彻底避免冲突：关闭抽屉时禁用内置手势（代码滚动恢复正常），
 * 改由本感应条在**最左侧窄条内**检测右滑来打开抽屉；抽屉打开后再启用内置手势以便左滑关闭。
 */
@Composable
private fun DrawerEdgeOpener(
    enabled: Boolean,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    edgeWidth: Dp = 28.dp
) {
    if (!enabled) return
    // 注意：这里不能用 .align()（那是 BoxScope 扩展，本函数不在 BoxScope 内）。
    // 由调用方在 Box 里传入带 align 的 modifier 来定位到左缘。
    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(edgeWidth)
            .pointerInput(Unit) {
                var total = 0f
                var fired = false
                detectHorizontalDragGestures(
                    onDragStart = { total = 0f; fired = false },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        if (!fired) {
                            total += dragAmount
                            // 向右累计超过 touchSlop×2 才判定为「打开抽屉」，
                            // 避免轻微抖动误触；向左拖动不计入（那是代码滚动方向）。
                            if (total > viewConfiguration.touchSlop * 2f) {
                                fired = true
                                onOpen()
                            }
                        }
                    }
                )
            }
    )
}

// ============================================================
// 标签栏
// ============================================================
@Composable
private fun EditorTabRow(
    tabs: List<EditorTab>,
    activeId: String?,
    onSelect: (String) -> Unit,
    onClose: (String) -> Unit
) {
    val scroll = rememberScrollState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scroll)
            // 6.2.0 批次14：chrome 由 cardBackground(不透明 surface) 改为 pageBackground ——
            // 渐变开启时透出全局渐变、玻璃开启时用玻璃底，与 TopAppBar/应用整体统一。
            .background(AppColors.pageBackground())
            .height(38.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        tabs.forEach { tab ->
            val active = tab.id == activeId
            Row(
                modifier = Modifier
                    .background(
                        if (active) AppColors.cardBackground() else Color.Transparent,
                        RoundedCornerShape(AppDimens.radiusSmall)
                    )
                    .clickable { onSelect(tab.id) }
                    .padding(horizontal = AppDimens.spacingSmall),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = getFileIcon(tab.name),
                    contentDescription = null,
                    modifier = Modifier.size(AppDimens.iconSmall),
                    tint = if (active) MaterialTheme.colorScheme.primary else AppColors.textSecondary()
                )
                Spacer(modifier = Modifier.width(AppDimens.spacingXSmall))
                Text(
                    text = tab.name + if (tab.isModified) " •" else "",
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (active) MaterialTheme.colorScheme.primary else AppColors.textSecondary(),
                    modifier = Modifier.widthIn(max = 120.dp)
                )
                Spacer(modifier = Modifier.width(AppDimens.spacingXSmall))
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = Str.get(R.string.editor_close_tab),
                    modifier = Modifier
                        .size(AppDimens.iconXSmall)
                        .clickable { onClose(tab.id) },
                    tint = AppColors.textSecondary()
                )
            }
        }
    }
    HorizontalDivider()
}

// ============================================================
// 状态栏
// ============================================================
@Composable
private fun EditorStatusBar(
    line: Int,
    column: Int,
    selection: Int,
    fileName: String,
    readOnly: Boolean,
    matchInfo: String?,
    onToggleWrap: () -> Unit,
    onOpenSettings: () -> Unit
) {
    HorizontalDivider()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // 6.2.0 批次14：与整体统一，渐变/玻璃时透出，不再用不透明 surface
            .background(AppColors.pageBackground())
            .padding(horizontal = AppDimens.spacingMedium, vertical = AppDimens.spacingXSmall),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = Str.get(R.string.editor_line_col, line, column),
            style = MaterialTheme.typography.labelSmall,
            color = AppColors.textSecondary()
        )
        if (selection > 0) {
            Spacer(modifier = Modifier.width(AppDimens.spacingMedium))
            Text(
                text = Str.get(R.string.editor_selection_len, selection),
                style = MaterialTheme.typography.labelSmall,
                color = AppColors.textSecondary()
            )
        }
        if (readOnly) {
            Spacer(modifier = Modifier.width(AppDimens.spacingMedium))
            Text(
                text = Str.get(R.string.editor_readonly_badge),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        if (matchInfo != null) {
            Text(
                text = matchInfo,
                style = MaterialTheme.typography.labelSmall,
                color = AppColors.textSecondary()
            )
            Spacer(modifier = Modifier.width(AppDimens.spacingMedium))
        }
        Text(
            text = fileName,
            style = MaterialTheme.typography.labelSmall,
            color = AppColors.textSecondary(),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 140.dp)
        )
        Spacer(modifier = Modifier.width(AppDimens.spacingSmall))
        UnifiedIconButton(
            icon = Icons.Default.WrapText,
            onClick = onToggleWrap,
            tint = AppColors.textSecondary(),
            contentDescription = Str.get(R.string.editor_wordwrap)
        )
        UnifiedIconButton(
            icon = Icons.Default.Settings,
            onClick = onOpenSettings,
            tint = AppColors.textSecondary(),
            contentDescription = Str.get(R.string.editor_settings)
        )
    }
}

// ============================================================
// 查找 / 替换
// ============================================================
@Composable
private fun SearchPanel(
    editor: CodeEditor?,
    onClose: () -> Unit,
    onMatchInfo: (String?) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var replacement by remember { mutableStateOf("") }
    var caseSensitive by remember { mutableStateOf(false) }
    var wholeWord by remember { mutableStateOf(false) }
    var useRegex by remember { mutableStateOf(false) }
    var showReplace by remember { mutableStateOf(false) }

    // ---- 6.2.0 批次39（SE 审计 T1-2）：**修「匹配计数一直不准」** ----
    // sora 的 EditorSearcher.search() 明确写着「结果不是立即可用的：我们在线程里搜索」，
    // 完成后才派发 PublishSearchResultEvent（stopSearch() 也会派发一次）。
    // 原实现是在 search() 返回后**立刻** reportMatches，读到的永远是 0（或上一次的旧值），
    // 于是搜索框下方几乎总显示「无匹配」，且随下一次按键才偶尔变正确。
    // 这里改为订阅结果事件，由 searcher 事后回调。
    // 匹配索引是异步的，DisposableEffect 与 editor 生命周期绑定，切页/重建时自动解绑。
    val matchInfo = onMatchInfo
    DisposableEffect(editor) {
        val receipt = editor?.subscribeEvent(PublishSearchResultEvent::class.java) { _, _ ->
            reportMatches(editor, matchInfo)
        }
        onDispose { receipt?.unsubscribe() }
    }

    fun runSearch() {
        val e = editor ?: return
        if (query.isEmpty()) {
            e.getSearcher().stopSearch()
            onMatchInfo(null)
            return
        }
        try {
            // 6.2.0 批次18：移除 beginSearchMode() —— 它只负责弹出 sora 内建的
            // 搜索 ActionMode（android.widget.SearchView，hint「欲搜索文本」），
            // 会和我们上面自己的 SearchPanel（hint「查找」）重复成两个搜索框。
            // 真正的检索由下面 getSearcher().search() 直接完成，无需它。
            val options = when {
                // 6.2.0 批次39（SE 审计 T1-8）：带上 RegexBackrefGrammar.DEFAULT。
                // 不传时 sora 会显式跳过反向引用处理，替换文本里的 $1 $2 会被**原样写进文档**。
                useRegex -> EditorSearcher.SearchOptions(
                    EditorSearcher.SearchOptions.TYPE_REGULAR_EXPRESSION,
                    !caseSensitive,
                    RegexBackrefGrammar.DEFAULT
                )
                wholeWord -> EditorSearcher.SearchOptions(
                    EditorSearcher.SearchOptions.TYPE_WHOLE_WORD, !caseSensitive
                )
                else -> EditorSearcher.SearchOptions(!caseSensitive, false)
            }
            // 6.2.0 批次39（SE 审计 T1-2）：显式声明「到末尾回卷到开头」——
            // 这本是 0.24.4 的默认值，这里写死以防依赖隐式默认导致翻页行为随版本漂移。
            e.getSearcher().setCyclicJumping(true)
            e.getSearcher().search(query, options)
            // 注意：此处**不能**调 reportMatches —— 结果还没算出来，
            // 由上面订阅的 PublishSearchResultEvent 在计算完成后更新。
        } catch (ex: Exception) {
            AppLog.e(TAG, "search failed", ex)
            onMatchInfo(Str.get(R.string.editor_search_invalid))
        }
    }

    // 6.2.0 批次20：上一个/下一个 —— 未输入内容（或上次检索失败）时 searcher 未进入检索状态，
    // 直接 gotoNext/gotoPrevious 会抛 IllegalStateException 闪退。先判空再 try 兜底。
    fun gotoPrev() {
        if (query.isEmpty()) return
        try { editor?.getSearcher()?.gotoPrevious() } catch (_: IllegalStateException) {}
    }
    fun gotoNext() {
        if (query.isEmpty()) return
        try { editor?.getSearcher()?.gotoNext() } catch (_: IllegalStateException) {}
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            // 6.2.0 批次14：与整体统一，渐变/玻璃时透出
            .background(AppColors.pageBackground())
            .padding(AppDimens.spacingSmall)
    ) {
        // 6.2.0 批次19：搜索框独占一行；功能按键移到下一行；新增上一个/下一个。
        UnifiedTextField(
            value = query,
            onValueChange = {
                query = it
                runSearch()
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = Str.get(R.string.editor_find),
            leadingIcon = Icons.Default.Search
        )
        Spacer(modifier = Modifier.height(AppDimens.spacingSmall))
        // 功能按键行：上一个(左箭头) / 下一个(右箭头) / 匹配大小写 / 全词 / 正则 / 展开替换 / 关闭
        Row(verticalAlignment = Alignment.CenterVertically) {
            UnifiedIconButton(
                icon = Icons.Default.KeyboardArrowLeft,
                onClick = { gotoPrev() },
                contentDescription = Str.get(R.string.editor_search_prev)
            )
            UnifiedIconButton(
                icon = Icons.Default.KeyboardArrowRight,
                onClick = { gotoNext() },
                contentDescription = Str.get(R.string.editor_search_next)
            )
            Spacer(modifier = Modifier.width(AppDimens.spacingSmall))
            FilterChipToggle(Str.get(R.string.editor_match_case), caseSensitive) { caseSensitive = it; runSearch() }
            FilterChipToggle(Str.get(R.string.editor_whole_word), wholeWord) { wholeWord = it; runSearch() }
            FilterChipToggle(Str.get(R.string.editor_regex), useRegex) { useRegex = it; runSearch() }
            Spacer(modifier = Modifier.weight(1f))
            UnifiedIconButton(
                icon = if (showReplace) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                onClick = { showReplace = !showReplace },
                contentDescription = Str.get(R.string.editor_replace)
            )
            UnifiedIconButton(
                icon = Icons.Default.Close,
                onClick = {
                    editor?.getSearcher()?.stopSearch()
                    onMatchInfo(null)
                    onClose()
                },
                contentDescription = Str.get(R.string.cancel)
            )
        }
        // 替换区：展开后出现替换输入框 + 替换/全部替换按钮
        if (showReplace) {
            Spacer(modifier = Modifier.height(AppDimens.spacingSmall))
            UnifiedTextField(
                value = replacement,
                onValueChange = { replacement = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = Str.get(R.string.editor_replace),
                leadingIcon = Icons.Default.FindReplace
            )
            Spacer(modifier = Modifier.height(AppDimens.spacingSmall))
            Row(verticalAlignment = Alignment.CenterVertically) {
                UnifiedButton(
                    text = Str.get(R.string.editor_replace),
                    size = ButtonSize.Small,
                    onClick = {
                        // replaceCurrentMatch 是同步的，且不派发结果事件，这里直接刷新。
                        editor?.getSearcher()?.replaceCurrentMatch(replacement)
                        reportMatches(editor, onMatchInfo)
                    },
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(AppDimens.spacingSmall))
                UnifiedButton(
                    text = Str.get(R.string.editor_replace_all),
                    size = ButtonSize.Small,
                    onClick = {
                        // replaceAll 在新线程里跑，完成后才派发 PublishSearchResultEvent，
                        // 这里**不能**立刻 reportMatches（会读到替换前的旧计数）。
                        // 计数由上面订阅的 DisposableEffect 负责刷新。
                        editor?.getSearcher()?.replaceAll(replacement)
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

private fun reportMatches(editor: CodeEditor?, onMatchInfo: (String?) -> Unit) {
    val e = editor ?: return
    val searcher = e.getSearcher()
    // 6.2.0 批次39（SE 审计 T1-2）：stopSearch() 也会派发 PublishSearchResultEvent，
    // 而那时 currentPattern 已被置空 —— 直接读 getMatchedPositionCount() 会先过
    // checkState() 抛 IllegalStateException。先用 hasQuery() 判空。
    if (!searcher.hasQuery()) {
        onMatchInfo(null)
        return
    }
    // 该函数只在「结果已就绪」时被调用（结果事件回调 / 同步的 replaceCurrentMatch），
    // 所以 total == 0 是真·无匹配，可以直接给反馈。
    val total = searcher.getMatchedPositionCount()
    if (total <= 0) {
        onMatchInfo(Str.get(R.string.editor_no_matches))
        return
    }
    val idx = searcher.getCurrentMatchedPositionIndex()
    // -1 表示光标不落在任何匹配上（尚未跳到结果）。
    val current = if (idx < 0) 1 else idx + 1
    onMatchInfo(Str.get(R.string.editor_matches, current, total))
}

@Composable
private fun FilterChipToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .padding(horizontal = AppDimens.spacingXSmall)
            .background(
                if (checked) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                else Color.Transparent,
                RoundedCornerShape(AppDimens.radiusSmall)
            )
            .clickable { onChange(!checked) }
            .padding(horizontal = AppDimens.spacingSmall, vertical = AppDimens.spacingXSmall),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (checked) MaterialTheme.colorScheme.primary else AppColors.textSecondary()
        )
    }
}

// ============================================================
// 6.2.0 批次32：编辑器操作菜单 + 跳转到行
// ============================================================

/**
 * 顶栏「更多操作」下拉菜单。
 *
 * 这些都是 sora `CodeEditor` 的内置能力，此前**一个都没接**：
 * - 剪切/复制/粘贴/全选：只能靠软键盘长按或系统剪贴板；
 * - 复制/剪切整行、选中整行、增加·减少缩进、格式化代码：完全没有入口。
 *
 * 6.2.0 批次39（SE 审计 T1-5）：**修 bug** —— 6.2.0 的撤销/重做项此前不看
 * [CodeEditor.canUndo] / [CodeEditor.canRedo]，没得撤时依然是可点的，
 * 点了没反应却没有任何反馈。现在按实际可撤销/可重做状态置灰。
 * （另外原文注释说「setUndoEnabled 默认 false」已过时：0.24.4 的 initialize() 默认就是 true。）
 *
 * 菜单用 `DropdownMenu` 锚定在 ⋮ 按钮旁，与文件树里 `FileNodeMenu` 的写法保持一致。
 */
@Composable
private fun EditorActionMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    editor: CodeEditor?,
    fileName: String,
    onGoto: () -> Unit,
    onFormat: () -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss
    ) {
        val e = editor
        val readOnly = e?.isEditable != true

        // 局部函数也必须标 @Composable，否则无法在其中调用 DropdownMenuItem / Icon 等组合函数。
        @Composable
        fun item(icon: ImageVector, label: String, enabled: Boolean = true, action: () -> Unit) {
            DropdownMenuItem(
                text = { Text(label) },
                leadingIcon = { Icon(icon, contentDescription = null) },
                enabled = enabled && !readOnly,
                onClick = action
            )
        }

        item(Icons.Default.Undo, Str.get(R.string.editor_undo), enabled = e?.canUndo() == true) { e?.undo() }
        item(Icons.Default.Redo, Str.get(R.string.editor_redo), enabled = e?.canRedo() == true) { e?.redo() }
        HorizontalDivider()
        item(Icons.Default.ContentCut, Str.get(R.string.editor_cut)) { e?.cutText() }
        item(Icons.Default.ContentCopy, Str.get(R.string.editor_copy)) { e?.copyText() }
        item(Icons.Default.ContentPaste, Str.get(R.string.editor_paste)) { e?.pasteText() }
        item(Icons.Default.SelectAll, Str.get(R.string.editor_select_all)) { e?.selectAll() }
        HorizontalDivider()
        // 6.2.0 批次39（SE 审计 T2-13）：整行类操作补齐 —— 此前只有「重复行」，
        // 剪切整行 / 选中当前行都没有入口（软键盘长按只能选词）。
        // 注：不加「复制整行」——`copyText()` 无参版内部就是 `copyText(true)`
        //     （有选区复制选区、无选区复制整行），与上面的「复制」完全重复。
        item(Icons.Default.PlaylistAdd, Str.get(R.string.editor_duplicate_line)) { e?.duplicateLine() }
        item(Icons.Default.ContentCut, Str.get(R.string.editor_cut_line)) { e?.cutLine() }
        // 选中整行：先移到行首再扩展到行尾（比手动拖选快）。
        item(Icons.Default.FormatAlignJustify, Str.get(R.string.editor_select_line)) {
            e?.moveSelection(SelectionMovement.LINE_START)
            e?.extendSelection(SelectionMovement.LINE_END)
        }
        item(Icons.Default.FormatIndentIncrease, Str.get(R.string.editor_indent)) { e?.indentLines(true) }
        item(Icons.Default.FormatIndentDecrease, Str.get(R.string.editor_unindent)) { e?.unindentSelection() }
        // 6.2.0 批次40：「格式化」此前是**假按钮**（sora 的 TextMate 语言不提供 Formatter，
        // formatCodeAsync 拿到的是空实现，点了什么都不会发生）。现在由 app 自己实现
        // （见 CodeFormatters.kt），并按扩展名置灰 —— json / json5 / jsonc / yaml / yml
        // 可用，其余类型直接禁用，不再出现「点了没反应」。
        // XML 有意不做：平台自带的 javax.xml Transformer 是 pretty-printer 而非保值重排
        //（会统一属性引号、剥掉标签间空白文本节点、可能丢 DOCTYPE 内部子集），
        // 与「只动空白、绝不动值」这条铁律冲突，手写分词器又等于造轮子。
        item(
            Icons.Default.FormatAlignLeft,
            Str.get(R.string.editor_format),
            enabled = e != null && CodeFormatters.supports(fileName)
        ) {
            onFormat()
        }
        HorizontalDivider()
        // 6.2.0 批次39（SE 审计 T2-13）：长配置文件里快速跳到文首/文末。
        item(Icons.Default.VerticalAlignTop, Str.get(R.string.editor_goto_doc_start), enabled = e != null) {
            e?.moveSelection(SelectionMovement.TEXT_START)
        }
        item(Icons.Default.VerticalAlignBottom, Str.get(R.string.editor_goto_doc_end), enabled = e != null) {
            e?.moveSelection(SelectionMovement.TEXT_END)
        }
        // 跳转到行只读也能用（只是移动光标）。
        DropdownMenuItem(
            text = { Text(Str.get(R.string.editor_goto_line)) },
            leadingIcon = { Icon(Icons.Default.Tag, contentDescription = null) },
            enabled = e != null,
            onClick = onGoto
        )
    }
}

/**
 * 「跳转到行」对话框。sora 的 `jumpToLine(line)` 是 0-based，这里按用户习惯接受 1-based 输入。
 */
@Composable
private fun GotoLineDialog(
    editor: CodeEditor?,
    onDismiss: () -> Unit
) {
    var input by remember { mutableStateOf("") }
    UnifiedDialog(
        onDismissRequest = onDismiss,
        title = Str.get(R.string.editor_goto_line),
        content = {
            UnifiedTextField(
                value = input,
                onValueChange = { v -> input = v.filter(Char::isDigit) },
                label = Str.get(R.string.editor_goto_line),
                singleLine = true
            )
        },
        confirmButton = {
            UnifiedDialogTextButton(onClick = {
                val line = input.toIntOrNull()
                if (line != null && editor != null) {
                    // 1-based 输入 → 0-based 行号，并夹到合法范围，避免越界。
                    val target = (line - 1).coerceIn(0, maxOf(0, editor.text.lineCount - 1))
                    editor.jumpToLine(target)
                    editor.ensureSelectionVisible()
                    editor.requestFocus()
                }
                onDismiss()
            }) { Text(Str.get(R.string.ok_2)) }
        },
        dismissButton = {
            UnifiedDialogTextButton(onClick = onDismiss) { Text(Str.get(R.string.cancel)) }
        }
    )
}

// ============================================================
// 设置面板
// ============================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsSheet(
    settings: EditorSettings,
    onChange: (EditorSettings) -> Unit,
    onOpenThemePicker: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        // 6.2.0 批次16：容器自身透明，改由下方 Box 铺同款渐变背景（sheet 的圆角 shape 会裁剪内容）。
        // 去掉默认拖拽条 —— 它位于渐变 Box 之上，透明时会透出遮罩形成一道空隙；
        // 下滑关闭仍由 sheetState 手势处理，不受影响。
        containerColor = Color.Transparent,
        dragHandle = null
    ) {
        // 与主页面同款渐变铺底；渐变关闭时回落到纯色卡片底，观感与旧版一致。
        val brush = pageGradientBrush()
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (brush != null) Modifier.background(brush)
                    else Modifier.background(AppColors.cardBackground())
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    // 6.2.0 批次35：ModalBottomSheet 的内容默认**不**可滚动，设置项一旦超出
                    // 一屏就被裁掉、且上滑看不到下面的项。verticalScroll 必须加在这里，
                    // 且放在 padding 之前，这样上下内边距也跟着一起滚。
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = AppDimens.spacingLarge)
                    .padding(top = AppDimens.spacingLarge)
                    .padding(bottom = AppDimens.spacingXLarge)
            ) {
                Text(
                    text = Str.get(R.string.editor_settings),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(AppDimens.spacingLarge))

            // 6.2.0：主题固定为具体主题（默认 dracula），不再有「跟随系统」。
            // 显示当前生效主题；老配置残留空串时兜底显示 dracula。
            SettingRow(Str.get(R.string.editor_theme)) {
                TextButton(onClick = onOpenThemePicker) {
                    Text(text = settings.theme.ifEmpty { "dracula" })
                }
            }
            SliderRow(
                label = Str.get(R.string.editor_font_size),
                value = settings.fontSize,
                range = 10f..24f,
                valueLabel = settings.fontSize.toInt().toString()
            ) { onChange(settings.copy(fontSize = it)) }
            SegmentedRow(
                label = Str.get(R.string.editor_tab_width),
                options = listOf("2" to 2, "4" to 4, "8" to 8),
                selected = settings.tabWidth
            ) { onChange(settings.copy(tabWidth = it)) }
            SwitchRow(Str.get(R.string.editor_wordwrap), settings.wordWrap) {
                onChange(settings.copy(wordWrap = it))
            }
            SwitchRow(Str.get(R.string.editor_line_number), settings.showLineNumbers) {
                onChange(settings.copy(showLineNumbers = it))
            }
            SwitchRow(Str.get(R.string.editor_current_line), settings.currentLine) {
                onChange(settings.copy(currentLine = it))
            }
            SwitchRow(Str.get(R.string.editor_bracket_match), settings.bracketMatching) {
                onChange(settings.copy(bracketMatching = it))
            }
            SwitchRow(Str.get(R.string.editor_non_printable), settings.showNonPrintable) {
                onChange(settings.copy(showNonPrintable = it))
            }
            SwitchRow(Str.get(R.string.editor_sticky_scroll), settings.stickyScroll) {
                onChange(settings.copy(stickyScroll = it))
            }
            SwitchRow(Str.get(R.string.editor_pinch_zoom), settings.pinchScalable) {
                onChange(settings.copy(pinchScalable = it))
            }
            SwitchRow(Str.get(R.string.editor_auto_complete), settings.autoComplete) {
                onChange(settings.copy(autoComplete = it))
            }
            // 6.2.0 批次39（SE 审计 T1-9）：删掉「自动保存」开关 —— 它从 6.0 起就只有 UI，
            // 全工程零消费者，是个骗人的假开关。
            // 6.2.0 批次39（SE 审计 T2-16）：换成真正生效的「放大镜」开关。
            SwitchRow(Str.get(R.string.editor_show_magnifier), settings.showMagnifier) {
                onChange(settings.copy(showMagnifier = it))
            }
            // 6.2.0 批次32：新增的编辑器能力开关（撤销/跳转/格式化等在顶栏 ⋮ 菜单里）。
            SwitchRow(Str.get(R.string.editor_block_line), settings.blockLine) {
                onChange(settings.copy(blockLine = it))
            }
            SwitchRow(Str.get(R.string.editor_ln_panel), settings.lnPanel) {
                onChange(settings.copy(lnPanel = it))
            }
            SwitchRow(Str.get(R.string.editor_ligature), settings.ligature) {
                onChange(settings.copy(ligature = it))
            }
            SwitchRow(Str.get(R.string.editor_cursor_blink), settings.cursorBlink) {
                onChange(settings.copy(cursorBlink = it))
            }
            SwitchRow(Str.get(R.string.editor_selection_popup), settings.textActionPopup) {
                onChange(settings.copy(textActionPopup = it))
            }
            // ---- 6.2.0 批次34：第二轮功能审计新增 ----
            SliderRow(
                label = Str.get(R.string.editor_line_spacing),
                value = settings.lineSpacing,
                range = 0.8f..2f,
                // 滑杆是连续的，这里按 0.1 步进取整，避免标签出现一长串小数。
                valueLabel = settings.lineSpacing.toString()
            ) { onChange(settings.copy(lineSpacing = (Math.round(it * 10f) / 10f))) }
            SliderRow(
                label = Str.get(R.string.editor_text_scale_x),
                value = settings.textScaleX,
                range = 0.8f..1.3f,
                valueLabel = settings.textScaleX.toString()
            ) { onChange(settings.copy(textScaleX = (Math.round(it * 100f) / 100f))) }
            SegmentedRow(
                label = Str.get(R.string.editor_ln_panel_pos),
                options = listOf(
                    Str.get(R.string.editor_ln_pos_left) to 1,
                    Str.get(R.string.editor_ln_pos_top) to 2,
                    Str.get(R.string.editor_ln_pos_right) to 4,
                    Str.get(R.string.editor_ln_pos_bottom) to 8,
                    Str.get(R.string.editor_ln_pos_center) to 15
                ),
                selected = settings.lnPanelPosition
            ) { onChange(settings.copy(lnPanelPosition = it)) }
            SegmentedRow(
                label = Str.get(R.string.editor_line_separator),
                // 换行符是技术缩写（LF / CRLF / CR），中英文都保持原样，不进字符串表。
                options = listOf("LF" to 0, "CRLF" to 1, "CR" to 2),
                selected = settings.lineSeparator
            ) { onChange(settings.copy(lineSeparator = it)) }
            SwitchRow(Str.get(R.string.editor_highlight_block), settings.highlightBlock) {
                onChange(settings.copy(highlightBlock = it))
            }
            SwitchRow(Str.get(R.string.editor_render_fn_chars), settings.renderFunctionChars) {
                onChange(settings.copy(renderFunctionChars = it))
            }
            Spacer(modifier = Modifier.height(AppDimens.spacingLarge))
            }
        }
    }
}

@Composable
private fun SettingRow(label: String, content: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = AppDimens.spacingSmall),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        content()
    }
}

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    valueLabel: String,
    onChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = valueLabel,
                style = MaterialTheme.typography.labelMedium,
                color = AppColors.textSecondary()
            )
        }
        androidx.compose.material3.Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range
        )
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = AppDimens.spacingXSmall),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/**
 * 6.2.0 批次35：标签在上、选项在下的一行式分段选择器。
 *
 * **不能**沿用 [SettingRow]（`Arrangement.SpaceBetween`）来放选项：Row 对每个子项都给足
 * 整行宽度约束，选项行一旦比「剩余空间」宽就会被贴右摆放，直接压到左侧标签上 ——
 * 这正是设置页里「部分设置项的 UI 控件重叠」的成因（5 个选项的行号面板位置最明显）。
 * 改成上下堆叠后，标签独占一行，永远不会有重叠。
 *
 * 选项行再套一层 `horizontalScroll` 作兜底：极窄屏幕上选项超出宽度时可横滑，而不是被裁。
 */
@Composable
private fun SegmentedRow(
    label: String,
    options: List<Pair<String, Int>>,
    selected: Int,
    onSelect: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = AppDimens.spacingSmall)
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
        ) {
            options.forEach { (text, value) ->
                TextButton(onClick = { onSelect(value) }) {
                    Text(
                        text = text,
                        color = if (selected == value) MaterialTheme.colorScheme.primary
                        else AppColors.textSecondary(),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}

// ============================================================
// 主屏
// ============================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CodeEditorScreen(
    viewModel: EditorViewModel,
    onExit: () -> Unit,
    onRequestPermission: () -> Unit,
    onOpenFolder: () -> Unit
) {
    val context = LocalContext.current
    val tabs by viewModel.tabs.collectAsState()
    val activeTabId by viewModel.activeTabId.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val readOnly by viewModel.readOnly.collectAsState()
    val projectRoot by viewModel.projectRoot.collectAsState()
    // 6.2.0 批次27：语法+主题后台加载完毕后翻转，EditorCanvas 据此补设主题与语言（消除启动竞态）。
    val grammarsReady by TextMateInitState.grammarsReady.collectAsState()
    // 6.2.0 批次27：最近一次打开失败的原因（如 Android/data 系统保护目录），空状态用它替换「未打开文件」。
    val lastOpenError by viewModel.lastOpenError.collectAsState()

    val controller = remember { EditorController() }
    var cursorLine by remember { mutableStateOf(1) }
    var cursorColumn by remember { mutableStateOf(1) }
    var selectionLen by remember { mutableStateOf(0) }
    var matchInfo by remember { mutableStateOf<String?>(null) }

    var showThemeDialog by remember { mutableStateOf(false) }
    var showSyntaxDialog by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    // 6.2.0 批次32：顶栏「更多操作」菜单 + 跳转到行对话框。
    var showActionMenu by remember { mutableStateOf(false) }
    var showGotoDialog by remember { mutableStateOf(false) }
    /**
     * 6.2.0 批次12：文件树由「内联 Row + 手写展开/收起」改为 **ModalNavigationDrawer**。
     *
     * 旧实现的两个问题：
     *  1. 展开/收起只是把一个内联 Column 从布局里移除，没有任何过渡动画，观感生硬；
     *  2. 只能点按钮，无法像常规侧边栏那样从屏幕左缘**滑动**打开。
     *
     * ModalNavigationDrawer 自带滑动手势 + 平移/淡入动画 + 点击遮罩关闭，
     * 且与项目里 ToolsScreen / PluginManageScreen 的既有写法保持一致。
     * 抽屉状态用 drawerState 承载，不再需要 treeUserClosed 这个手写布尔。
     */
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val drawerScope = rememberCoroutineScope()
    var showExitConfirm by remember { mutableStateOf(false) }
    var pendingClose by remember { mutableStateOf<String?>(null) }

    val activeTab = tabs.firstOrNull { it.id == activeTabId }
    // 6.2.0：默认主题固定 dracula，不再跟随系统深浅色（系统未定义编辑器主题）。
    // 兜底 dracula 只为兼容老用户配置里可能残留的空串。
    val effectiveTheme = settings.theme.ifEmpty { "dracula" }

    // 光标 / 内容回调接线（避免轮询）
    LaunchedEffect(controller) {
        controller.onCursorChange = { l, c, sel ->
            cursorLine = l
            cursorColumn = c
            selectionLen = sel
        }
        // 6.2.0 批次39（SE 审计 T2-10）：接上硬件键盘的 Ctrl+S / Ctrl+F / Ctrl+G。
        controller.onKeyAction = { action ->
            when (action) {
                "save" -> viewModel.saveActive()
                "search" -> showSearch = true
                "goto" -> showGotoDialog = true
            }
        }
    }
    LaunchedEffect(activeTabId, controller) {
        controller.onContentChange = { text ->
            activeTabId?.let { viewModel.updateContent(it, text) }
        }
    }

    BackHandler {
        // 抽屉开着时，返回键先关抽屉，而不是直接退出页面
        if (drawerState.isOpen) {
            drawerScope.launch { drawerState.close() }
        } else if (viewModel.hasUnsaved()) {
            showExitConfirm = true
        } else {
            onExit()
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        // 没有打开任何目录时禁用滑动手势，避免误触拉出空抽屉
        // 6.2.0：ModalNavigationDrawer 内置滑动手势作用于**整个内容区**，会把编辑器里
        // 横向滚动代码的拖拽一并抢走。改为：
        //  - 关闭时禁用内置手势（gesturesEnabled=false）→ 代码横向滚动恢复正常；
        //  - 由内容区最左侧的 DrawerEdgeOpener 窄条（28dp）检测右滑打开抽屉；
        //  - 抽屉打开后启用内置手势 → 抽屉上任意位置左滑即可关闭。
        gesturesEnabled = drawerState.isOpen && projectRoot != null,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(280.dp),
                // 6.2.0 批次16：抽屉容器透明，改由下方 Box 铺同款渐变背景（drawer 的 shape 会裁剪内容）。
                drawerContainerColor = Color.Transparent
            ) {
                // 与主页面同款渐变铺底；渐变关闭时回落纯色卡片底，观感与旧版一致。
                val brush = pageGradientBrush()
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(280.dp)
                        .then(
                            if (brush != null) Modifier.background(brush)
                            else Modifier.background(AppColors.cardBackground())
                        )
                ) {
                    FileTreePanel(
                        viewModel = viewModel,
                        activePath = activeTab?.file?.absolutePath,
                        onOpenFile = { viewModel.openFile(it) },
                        onOpenFolder = onOpenFolder,
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(280.dp)
                    )
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = activeTab?.name ?: Str.get(R.string.title_code_editor),
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    navigationIcon = {
                        // 6.2.0 批次24：只有建了树根（能定位/已打开文件夹）才显示目录树开关；
                        // 从外部打开却无法定位的文件（content:// 或无存储权限）直接显示文件本身，
                        // 不显示目录树、也不显示目录树开关图标。
                        if (projectRoot != null) {
                            UnifiedIconButton(
                                icon = Icons.Default.Menu,
                                onClick = {
                                    drawerScope.launch {
                                        if (drawerState.isOpen) drawerState.close()
                                        else drawerState.open()
                                    }
                                },
                                contentDescription = Str.get(R.string.editor_file_tree)
                            )
                        }
                    },
                    actions = {
                        if (!readOnly) {
                            UnifiedIconButton(
                                icon = Icons.Default.Save,
                                onClick = { viewModel.saveActive() },
                                contentDescription = Str.get(R.string.editor_save)
                            )
                        }
                        UnifiedIconButton(
                            icon = Icons.Default.Search,
                            onClick = { showSearch = !showSearch },
                            contentDescription = Str.get(R.string.editor_find)
                        )
                        // 6.2.0 批次16：右上角原「主题」图标改为「语法」图标。
                        // 主题只能在设置面板里选；这里改为手动为当前文件挑选高亮语法。
                        UnifiedIconButton(
                            icon = Icons.Default.DataObject,
                            onClick = { showSyntaxDialog = true },
                            contentDescription = Str.get(R.string.editor_syntax)
                        )
                        // 6.2.0 批次32：「更多操作」菜单 —— 撤销/重做/剪贴板/缩进/格式化/跳转到行。
                        // DropdownMenu 必须锚定在一个父 Box 上，才能贴着 ⋮ 按钮弹出。
                        Box {
                            UnifiedIconButton(
                                icon = Icons.Default.MoreVert,
                                onClick = { showActionMenu = true },
                                contentDescription = Str.get(R.string.editor_more)
                            )
                            EditorActionMenu(
                                expanded = showActionMenu,
                                onDismiss = { showActionMenu = false },
                                editor = controller.editor,
                                fileName = activeTab?.name ?: "",
                                onGoto = {
                                    showActionMenu = false
                                    showGotoDialog = true
                                },
                                onFormat = {
                                    showActionMenu = false
                                    runFormat(controller, activeTab?.name ?: "") { msg ->
                                        viewModel.emit(msg)
                                    }
                                }
                            )
                        }
                        // 6.2.0 批次17：移除右上角「打开文件夹」图标 —— 有文件时才用得上、
                        // 与文件树重复。打开入口改由「空状态按钮」+「侧栏文件树头部」提供。
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = AppColors.pageBackground(),
                        titleContentColor = MaterialTheme.colorScheme.onBackground,
                        navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                        actionIconContentColor = MaterialTheme.colorScheme.onBackground
                    )
                )
            },
            bottomBar = {
                EditorStatusBar(
                    line = cursorLine,
                    column = cursorColumn,
                    selection = selectionLen,
                    fileName = activeTab?.name ?: "",
                    readOnly = readOnly,
                    matchInfo = matchInfo,
                    onToggleWrap = { viewModel.updateSettings { it.copy(wordWrap = !it.wordWrap) } },
                    onOpenSettings = { showSettings = true }
                )
            },
            containerColor = AppColors.pageBackground()
        ) { padding ->
            // 文件树已移入 ModalNavigationDrawer，这里只保留编辑区
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .imePaddingCompat()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    if (tabs.isNotEmpty()) {
                        EditorTabRow(
                            tabs = tabs,
                            activeId = activeTabId,
                            onSelect = { viewModel.setActive(it) },
                            onClose = { id ->
                                val tab = tabs.firstOrNull { it.id == id }
                                if (tab != null && tab.isModified) pendingClose = id
                                else viewModel.closeTab(id, force = true)
                            }
                        )
                    }
                    // 6.2.0 批次19：搜索面板用「抽屉式」竖向展开/收起动画（从顶部滑入，更美观）；
                    // 只靠关闭按钮退出，不支持手势滑动。
                    AnimatedVisibility(
                        visible = showSearch,
                        enter = expandVertically(
                            expandFrom = Alignment.Top,
                            animationSpec = tween(220, easing = FastOutSlowInEasing)
                        ) + fadeIn(animationSpec = tween(220)),
                        exit = shrinkVertically(
                            shrinkTowards = Alignment.Top,
                            animationSpec = tween(180, easing = FastOutSlowInEasing)
                        ) + fadeOut(animationSpec = tween(180))
                    ) {
                        SearchPanel(
                            editor = controller.editor,
                            onClose = { showSearch = false },
                            onMatchInfo = { matchInfo = it }
                        )
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        if (activeTab == null) {
                            // 6.2.0 批次27：最近一次打开失败（如 Android/data 系统保护目录）时，
                            // 用具体原因替换「未打开文件」，失败原因常驻可见而非一闪而过的 toast。
                            val openError = lastOpenError
                            UnifiedEmptyState(
                                title = if (openError != null) {
                                    Str.get(R.string.editor_open_failed_title)
                                } else {
                                    Str.get(R.string.editor_empty_title)
                                },
                                description = openError ?: Str.get(R.string.editor_empty_hint),
                                icon = Icons.Default.Description,
                                // 6.2.0 批次40：这里**不该**再叫用户「打开文件夹」——
                                // 启动时就申请了存储权限，目录树本来就有内容（见
                                // EditorViewModel.ensureDefaultRoot）。按钮改成展开目录树，
                                // 让用户直接挑文件。真要换根目录，侧栏头部一直有 FolderOpen。
                                actionText = Str.get(R.string.editor_open_from_tree),
                                onAction = { drawerScope.launch { drawerState.open() } },
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(AppDimens.spacingXLarge)
                            )
                        } else {
                            // 6.2.0 批次24：打开的文件一律直接显示内容，不再因「无存储权限」
                            // 替换成「授予权限」引导页 —— content:// 打开的文件本就无需存储权限，
                            // 只读也能看。原先 readOnly && !hasStorageAccess 时整块被换成权限页，
                            // 用户看到的是「未打开文件/需要权限」而非文件本身。
                            EditorCanvas(
                                controller = controller,
                                tab = activeTab,
                                settings = settings,
                                effectiveTheme = effectiveTheme,
                                readOnly = readOnly,
                                grammarsReady = grammarsReady
                            )
                        }
                        // 6.2.0：抽屉左缘窄感应条（28dp）。ModalNavigationDrawer 内置手势已在关闭时禁用，
                        // 仅在此窄条内右滑打开抽屉；放在编辑区 Box 内（屏幕左缘）且位于 EditorCanvas 之上，
                        // 既不会遮挡顶栏菜单键，也能精确捕捉最左侧的滑动，其余区域代码横向滚动不受影响。
                        DrawerEdgeOpener(
                            enabled = projectRoot != null && !drawerState.isOpen,
                            onOpen = { drawerScope.launch { drawerState.open() } },
                            modifier = Modifier.align(Alignment.CenterStart)
                        )
                    }
                    if (settings.symbolBar && !readOnly) {
                        SymbolBar(
                            onInsert = { sym ->
                                controller.editor?.let { e ->
                                    // 6.2.0 批次39（SE 审计 T1-6）：**修缺陷** —— 原先直接
                                    // `e.text.insert(...)` 绕过编辑器入口，于是符号栏点 `(` 不会
                                    // 自动补 `)`、点 `"` 不会成对补全、也不会走自动缩进与
                                    // QuickQuoteHandler。改用 commitText 走与软键盘完全一致的链路。
                                    // 点按符号栏通常是在点击后立刻想在编辑器里继续改，先请求焦点。
                                    e.requestFocus()
                                    e.commitText(sym)
                                }
                            }
                        )
                    }
                }
            }
        }
    } // ModalNavigationDrawer

    if (showThemeDialog) {
        ThemeSelectionDialog(
            currentTheme = settings.theme,
            onSelect = { theme ->
                viewModel.updateSettings { it.copy(theme = theme) }
                showThemeDialog = false
            },
            onDismiss = { showThemeDialog = false }
        )
    }

    // 6.2.0 批次16：右上角语法选择器 —— 手动为当前文件挑选高亮语法，覆盖后缀名推断。
    if (showSyntaxDialog) {
        SyntaxSelectionDialog(
            currentScope = activeTab?.languageScope,
            onSelect = { scope ->
                viewModel.setActiveLanguageScope(scope)
                showSyntaxDialog = false
            },
            onDismiss = { showSyntaxDialog = false }
        )
    }

    // 6.2.0 批次32：顶栏「更多操作 → 跳转到行」。
    if (showGotoDialog) {
        GotoLineDialog(
            editor = controller.editor,
            onDismiss = { showGotoDialog = false }
        )
    }

    if (showSettings) {
        SettingsSheet(
            settings = settings,
            onChange = { viewModel.updateSettings { _ -> it } },
            onOpenThemePicker = {
                showSettings = false
                showThemeDialog = true
            },
            onDismiss = { showSettings = false }
        )
    }

    pendingClose?.let { id ->
        UnifiedConfirmDialog(
            title = Str.get(R.string.editor_unsaved_title),
            message = Str.get(R.string.editor_unsaved_message, tabs.firstOrNull { it.id == id }?.name ?: ""),
            isDestructive = false,
            confirmText = Str.get(R.string.editor_save),
            dismissText = Str.get(R.string.editor_discard),
            onConfirm = {
                tabs.firstOrNull { it.id == id }?.let { viewModel.saveTab(it) }
                viewModel.closeTab(id, force = true)
                pendingClose = null
            },
            onDismiss = {
                viewModel.closeTab(id, force = true)
                pendingClose = null
            }
        )
    }

    if (showExitConfirm) {
        UnifiedConfirmDialog(
            title = Str.get(R.string.editor_unsaved_title),
            message = Str.get(R.string.editor_unsaved_message, activeTab?.name ?: ""),
            isDestructive = false,
            confirmText = Str.get(R.string.editor_save),
            dismissText = Str.get(R.string.editor_discard),
            onConfirm = {
                viewModel.saveActive()
                showExitConfirm = false
                onExit()
            },
            onDismiss = {
                showExitConfirm = false
                onExit()
            }
        )
    }
}

// 软键盘适配：manifest 中 windowSoftInputMode=adjustResize，配合 imePadding 避免键盘遮挡编辑区
private fun Modifier.imePaddingCompat(): Modifier = this.imePadding()

// ============================================================
// 编辑器画布
// ============================================================
@Composable
private fun EditorCanvas(
    controller: EditorController,
    tab: EditorTab,
    settings: EditorSettings,
    effectiveTheme: String,
    readOnly: Boolean,
    grammarsReady: Boolean
) {
    AndroidView(
        // 6.2.0 批次39（SE 审计 T2-14）：AndroidView 在 Compose 里是一块「语义黑盒」，
        // TalkBack 读到的只有 CodeEditor 这个普通 View 的默认信息（几乎等于没有）。
        // 这里补一条带文件名的 contentDescription，让无障碍服务能识别当前正在编辑哪个文件。
        modifier = Modifier
            .fillMaxSize()
            .semantics { contentDescription = Str.get(R.string.editor_a11y_description, tab.name) },
        factory = { ctx ->
            CodeEditor(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                isFocusable = true
                isFocusableInTouchMode = true
                controller.attach(this)
                applyRuntimeSettings(this, settings)
                // sora 官方示例明确要求：**先**应用 TextMateColorScheme，**再**设置语言
                // （见 demo MainActivity：「Before using Textmate Language, TextmateColorScheme should be applied」）。
                applyTheme(this, effectiveTheme)
                setEditorLanguage(this, tab.name, tab.languageScope)
                controller.attachedTabId = tab.id
                controller.attachedTheme = effectiveTheme
                controller.attachedLangScope = tab.languageScope
                controller.attachedSettings = settings
                controller.attachedEditable = !readOnly
                controller.attachedGrammarReady = grammarsReady
                setText(tab.content)
                post { rerunAnalysis(); invalidate() }
                // 6.2.0 批次30/31：十六进制颜色**背景**（色值文本本身涂成对应色）。必须在 setText
                // 之后创建，这样 attach 的首次扫描就能对已有内容产出背景。
                controller.hexHints = HexColorHints(this).also { it.attach() }
                logHighlightDiagnostics(this, "factory", tab.languageScope ?: getLanguageScopeName(tab.name))
            }
        },
        update = { editor ->
            // 仅在设置真正变化时应用，避免每次按键重组合都触发 relayout
            if (controller.attachedSettings != settings) {
                applyRuntimeSettings(editor, settings)
                controller.attachedSettings = settings
            }
            if (controller.attachedEditable != !readOnly) {
                editor.setEditable(!readOnly)
                controller.attachedEditable = !readOnly
            }
            if (controller.attachedTheme != effectiveTheme) {
                applyTheme(editor, effectiveTheme)
                controller.attachedTheme = effectiveTheme
            }
            if (controller.attachedTabId != tab.id) {
                controller.attachedTabId = tab.id
                controller.attachedLangScope = tab.languageScope
                setEditorLanguage(editor, tab.name, tab.languageScope)
                editor.setText(tab.content)
                editor.post { editor.rerunAnalysis(); editor.invalidate() }
                // setEditorLanguage 清了背景；setText 虽派发 ContentChangeEvent 会延迟重建，这里立即补。
                controller.hexHints?.refresh()
            } else if (controller.attachedLangScope != tab.languageScope) {
                // 6.2.0 批次16：同一标签页内手动切换语法（右上角语法选择器）时，
                // tab.id 没变，需单独检测 languageScope 变化并重设语言。
                controller.attachedLangScope = tab.languageScope
                setEditorLanguage(editor, tab.name, tab.languageScope)
                editor.post { editor.rerunAnalysis(); editor.invalidate() }
                controller.hexHints?.refresh()
            }
            // 6.2.0 批次27：语法与主题在后台加载（1.8~4s）。启动瞬间 ACTION_VIEW 打开的文件
            // 会因语法未注册回退成 EmptyLanguage（无高亮），且 factory 里 applyTheme 时主题
            // 尚未加载、colorScheme 是回退值。这里在两者就绪后补设一次主题 + 语言，高亮才可见。
            if (grammarsReady && !controller.attachedGrammarReady) {
                controller.attachedGrammarReady = true
                applyTheme(editor, effectiveTheme)
                setEditorLanguage(editor, tab.name, tab.languageScope)
                editor.post { editor.rerunAnalysis(); editor.invalidate() }
                controller.hexHints?.refresh()
                logHighlightDiagnostics(editor, "reapply", tab.languageScope ?: getLanguageScopeName(tab.name))
            }
        },
        onRelease = { controller.detach() }
    )
}

// ============================================================
// 符号栏（P2：库未提供现成集成，用 Compose 实现，避免依赖不明确的 API）
// ============================================================
private val COMMON_SYMBOLS = listOf(
    "{", "}", "(", ")", "[", "]", ";", ":", ",", "=", "+", "-", "*", "/",
    "<", ">", "&", "|", "!", "?", "'", "\"", ".", "_", "@", "#", "$", "%"
)

@Composable
private fun SymbolBar(onInsert: (String) -> Unit) {
    val scroll = rememberScrollState()
    HorizontalDivider()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scroll)
            // 6.2.0 批次14：与整体统一，渐变/玻璃时透出
            .background(AppColors.pageBackground())
            .padding(vertical = AppDimens.spacingXSmall),
        horizontalArrangement = Arrangement.Start
    ) {
        COMMON_SYMBOLS.forEach { sym ->
            Text(
                text = sym,
                style = MaterialTheme.typography.labelLarge,
                fontFamily = FontFamily.Monospace,
                color = AppColors.textSecondary(),
                modifier = Modifier
                    .padding(horizontal = AppDimens.spacingSmall)
                    .clickable { onInsert(sym) }
            )
        }
    }
}
