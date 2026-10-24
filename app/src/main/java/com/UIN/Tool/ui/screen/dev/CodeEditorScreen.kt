// app/src/main/java/com/UIN/Tool/ui/screen/dev/CodeEditorScreen.kt
package com.UIN.Tool.ui.screen.dev

import android.content.Context
import android.graphics.Color as AndroidColor
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
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
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
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
import com.UIN.Tool.ui.screen.dev.editor.FileAccess
import com.UIN.Tool.ui.screen.dev.editor.FileNode
import com.UIN.Tool.ui.screen.dev.editor.HexColorHints
import com.UIN.Tool.ui.screen.dev.editor.MatchHighlights
import com.UIN.Tool.ui.screen.dev.editor.TextMateInitState
import com.UIN.Tool.ui.screen.dev.editor.WholeSearch
import com.UIN.Tool.ui.theme.AppColors
import com.UIN.Tool.ui.theme.AppDimens
import com.UIN.Tool.ui.theme.dialogBackgroundOf
import com.UIN.Tool.ui.theme.pageGradientBrush
import com.UIN.Tool.utils.AppLog
import com.UIN.Tool.utils.AppToast
import com.UIN.Tool.utils.Str
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.event.EditorKeyEvent
import io.github.rosemoe.sora.event.KeyBindingEvent
import io.github.rosemoe.sora.event.SelectionChangeEvent
import io.github.rosemoe.sora.event.SubscriptionReceipt
import io.github.rosemoe.sora.lang.EmptyLanguage
import io.github.rosemoe.sora.lang.styling.HighlightTextContainer
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.langs.textmate.registry.model.ThemeModel
import io.github.rosemoe.sora.text.LineSeparator
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.DirectAccessProps
import io.github.rosemoe.sora.widget.SelectionMovement
import io.github.rosemoe.sora.widget.component.EditorAutoCompletion
import io.github.rosemoe.sora.widget.component.EditorTextActionWindow
import io.github.rosemoe.sora.widget.component.Magnifier
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import io.github.rosemoe.sora.widget.style.LineInfoPanelPositionMode
import java.io.FileInputStream
import java.io.InputStreamReader
import java.time.Duration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.eclipse.tm4e.core.grammar.IStateStack
import org.eclipse.tm4e.core.internal.grammar.raw.RawGrammarReader
import org.eclipse.tm4e.core.registry.IGrammarSource
import org.eclipse.tm4e.core.registry.IThemeSource

private const val TAG = "CodeEditorScreen"
private const val DEBUG_TAG = "EditorDebug"

// 6.2.0 批次43：分页导航 —— 每页固定 2000 行，「跳转到页」的粗粒度导航。
// 批次46：与窗口模式（WindowedDocument）同源 —— 必须用 FileAccess.WINDOW_PAGE_LINES，
// 否则超大文件的「n/N」页码会与实际载入窗口错位。
private const val PAGE_LINES = FileAccess.WINDOW_PAGE_LINES

// 批次54：超过此长度的文档，按键不再逐键复制全文（Content.toString 每键一份 → OOM）。
// 200 万字符 ≈ 4MB UTF-16 —— 小文件逐键同步保证 tab.content/搜索快照/保存内容实时。
private const val LARGE_EDIT_TEXT_CHARS = 2_000_000

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
 *
 * @param forcePlain 大文件省耗：内容超阈值且用户未手动指定语法时置 true，
 *                   直接用 EmptyLanguage 跳过语法解析（分析是大文件卡顿的主因）。
 */
private fun setEditorLanguage(
    editor: CodeEditor?,
    fileName: String,
    overrideScope: String? = null,
    forcePlain: Boolean = false
) {
    if (editor == null) return
    if (forcePlain) {
        AppLog.d(DEBUG_TAG, "setEditorLanguage: large-file mode, forcing EmptyLanguage for $fileName")
        editor.setEditorLanguage(EmptyLanguage())
        return
    }
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

/** 大文件判定：内容字符数超过阈值即视为「大文件」，配合 largeFileMode 省耗。 */
private fun isLargeContent(tab: EditorTab?): Boolean =
    tab != null && (
        // 批次46：窗口模式天然是超大文件的降级打开 —— 无论本页多少字符都按大文件省耗
        // （关语法/缩进线等），否则一个 10GB 文件的第 1 页看起来「只有 2000 行」白白重耗。
        tab.windowed || tab.content.length > FileAccess.LARGE_FILE_OPTIMIZE_CHARS
        )

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
 *
 * 6.2.0 追加：**必须给纵向滑块装一个 Drawable**，否则拖动滚动条时「行:列」位置面板
 * 会变成一块纯色、文字看不见。详见 [installScrollbarThumbDrawable]。
 */
private fun applyColorSchemeTweaks(editor: CodeEditor) {
    val scheme = editor.colorScheme ?: return
    val thumb = scheme.getColor(EditorColorScheme.LINE_NUMBER_CURRENT)
    val thumbPressed = scheme.getColor(EditorColorScheme.LINE_NUMBER)
    scheme.setColor(EditorColorScheme.SCROLL_BAR_THUMB, thumb)
    scheme.setColor(EditorColorScheme.SCROLL_BAR_THUMB_PRESSED, thumbPressed)
    scheme.setColor(
        EditorColorScheme.SCROLL_BAR_TRACK,
        scheme.getColor(EditorColorScheme.WHOLE_BACKGROUND)
    )
    // 行号分隔线：TextMate 主题把 LINE_DIVIDER 置为 TRANSPARENT，导致「行号分隔线」开关
    // 拨动后完全看不见。这里用行号色的半透明版重设，保证任何主题下都有一条可见的竖线。
    val divider = (scheme.getColor(EditorColorScheme.LINE_NUMBER) and 0x00FFFFFF) or 0x55000000
    scheme.setColor(EditorColorScheme.LINE_DIVIDER, divider)
    // 括号匹配描边：
    //  ① 宽度 = textBorderWidth，已在 applyRuntimeSettings 里从 4dp 收到 1.5dp（变细）；
    //  ② 颜色**不能**置 0 —— EditorRenderer.drawRowBackgroundRectWithBorder 在
    //     `if (borderColor == 0) return` 时会连描边一起跳过，只剩 sora 默认
    //     HIGHLIGHTED_DELIMITERS_BACKGROUND = 0x1D000000（11% 黑，几乎看不见），
    //     表现就是用户反馈的「括号匹配无效果」。
    // 这里把描边色还原成看得清的颜色：优先主题给的括号前景色，否则用当前行号色（强调色）。
    val delimBorder = scheme.getColor(EditorColorScheme.HIGHLIGHTED_DELIMITERS_FOREGROUND)
        .takeIf { it != 0 } ?: thumb
    scheme.setColor(EditorColorScheme.HIGHLIGHTED_DELIMITERS_BORDER, delimBorder)
    editor.setEdgeEffectColor(scheme.getColor(EditorColorScheme.CURRENT_LINE))
    installScrollbarThumbDrawable(editor, thumb, thumbPressed)
    editor.invalidate()
}

/**
 * 给纵向滚动条滑块装一个 [StateListDrawable]。
 *
 * **为什么必须装**（sora 0.24.6 上游 bug，`EditorRenderer.drawScrollBarVertical`）：
 * ```java
 * getVerticalScrollBarRect(tmpRect, ...);          // tmpRect = 滑块矩形
 * if (holdVerticalScrollBar()) {
 *     drawLineInfoPanel(canvas, ...);              // ← 内部把 tmpRect 改写成「面板矩形」
 * }
 * if (verticalScrollbarThumbDrawable != null) {
 *     setBounds(verticalScrollBarRect); ...        // ← 用的是先前保存的矩形，正确
 * } else {
 *     drawColor(canvas, THUMB_PRESSED, tmpRect);   // ← 用的是被污染的 tmpRect，错误
 * }
 * ```
 * `tmpRect` 是字段级共享对象，`drawLineInfoPanel()` 会把它改写成位置面板的矩形。
 * 没装 Drawable 时 sora 用这个已被污染的 `tmpRect` 画滑块 →
 * **一块与面板同尺寸的纯色矩形直接盖在刚画好的面板（含文字）上**。
 * 装了 Drawable 就走 `!= null` 分支，用的是调用前保存的 `verticalScrollBarRect`，
 * 宽度只有 `SCROLLBAR_WIDTH_DIP`（8dp），且面板右缘在 `width-30dp`，两者不重叠。
 *
 * 横向滚动条没有这条调用路径（无位置面板），不受影响。
 */
private fun installScrollbarThumbDrawable(editor: CodeEditor, normal: Int, pressed: Int) {
    val dp = editor.dpUnit
    fun pill(color: Int) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = 3f * dp
        setColor(color)
    }
    // sora 用 {state_pressed, state_enabled} / {state_enabled} 两组状态切换按下态。
    // 横竖滑块都要装：横向不装就退回默认方角实心块，与竖向圆角药丸不统一。
    // 各自新建独立实例 —— 同一个 Drawable 不能同时挂到两处。
    fun thumb() = StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_pressed, android.R.attr.state_enabled), pill(pressed))
        addState(intArrayOf(android.R.attr.state_enabled), pill(normal))
    }
    editor.setVerticalScrollbarThumbDrawable(thumb())
    editor.setHorizontalScrollbarThumbDrawable(thumb())
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

private fun applyRuntimeSettings(editor: CodeEditor, s: EditorSettings, largeActive: Boolean = false) {
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
    // 大文件省耗：粘性滚动要在滚动时反复解析块结构，关掉省一大截 CPU。
    editor.props.stickyScroll = s.stickyScroll && !largeActive
    editor.setScalable(s.pinchScalable)
    editor.props.autoIndent = true
    editor.getComponent(EditorAutoCompletion::class.java)?.isEnabled = s.autoComplete

    // ---- 6.2.0 批次32：补齐此前从未启用的 sora 编辑器能力 ----

    // 撤销 / 重做。注：0.24.4 的 initialize() 已默认 setUndoEnabled(true)，此处幂等保留，
    // 以防未来版本默认值变更；原文「sora 默认是关闭的」的说法已过时。
    editor.setUndoEnabled(true)

    // 当前块的缩进参考线（比「高亮当前行」更能看清嵌套层级）。
    // 大文件省耗：缩进线同样依赖块结构计算，一并关掉。
    editor.setBlockLineEnabled(s.blockLine && !largeActive)
    editor.setBlockLineWidth(1f)

    // 浮动「行:列」位置面板：**拖动右侧滚动条时**才出现的那个行号气泡，控制它停靠到哪。
    // 6.2.0 修「改位置没反应」，两处原因：
    //  1) 早先只调 setDisplayLnPanel，从未把 lnPanelPosition 应用到编辑器；
    //  2) 下方曾有第二处重复赋值，把 左/右 之外的档位又切回 FOLLOW —— 而 FOLLOW 模式
    //     永远贴在右侧滚动条旁（上/下/中 视觉上几乎没区别），所以点了像没生效。
    // 修法：全程只用 FIXED，EditorRenderer.drawLineInfoPanel() 的 FIXED 分支按位掩码
    // 认 LEFT(1)/TOP(2)/RIGHT(4)/BOTTOM(8)/CENTER(15)，五档都会真正挪到对应屏幕方位。
    editor.setDisplayLnPanel(s.lnPanel)
    editor.setLnPanelPositionMode(LineInfoPanelPositionMode.FIXED)
    editor.setLnPanelPosition(s.lnPanelPosition)

    // 选中文字后自动跟手显示的「剪切 / 复制 / 粘贴」悬浮窗（内置组件，只需打开）。
    editor.getComponent(EditorTextActionWindow::class.java)?.isEnabled = s.textActionPopup

    // 滚动时选区吸附屏幕边缘（长文档里拖选不会「看不见选到哪了」）。
    editor.setStickyTextSelection(true)

    // 连字：配合 Fira Code / JetBrains Mono 等字体渲染 -> ⇥ != 等合字。
    editor.setLigatureEnabled(s.ligature)

    // 文本**描边宽度**（不是内边距）：sora 只把它当 stroke 宽度用在
    //  ① 括号匹配框 ② 选区/高亮区域的边框上。
    // 6.2.0：原先 4dp，括号匹配框粗得喧宾夺主；收到 1.5dp（sora 默认 1dp）后
    // 边框依然可见但明显变细 —— 这是「改细」而不是「改成没有」。
    editor.setTextBorderWidth(dp(1.5f))
    // 细滚动条 + 软键盘常显（便于随时输入代码）。
    editor.setScrollBarEnabled(true)
    editor.setVerticalScrollBarEnabled(true)
    editor.setSoftKeyboardEnabled(true)
    editor.setVerticalExtraSpaceFactor(0.2f)

    // 光标：略宽于默认、平移有动画、可选闪烁。
    editor.setCursorWidth(dp(2f))
    editor.setCursorAnimationEnabled(true)
    if (s.cursorBlink) editor.setCursorBlinkPeriod(530) else editor.setCursorBlinkPeriod(0)

    // 行号分隔线 —— 行号列与正文之间那条竖线。0 = 不画。
    // （此前只有字段和这行应用逻辑，没有 UI 开关；6.2.0 补上「行号分隔线」开关。）
    editor.setDividerWidth(if (s.lineDivider) dp(2f) else 0f)
    editor.setDividerMargin(dp(6f))

    // ---- 6.2.0 批次34：第二轮功能审计补齐的 sora 能力 ----

    // 排版：行距倍数与字宽（横向缩放），均需在字号/字体变更后重设。
    editor.setLineSpacingMultiplier(s.lineSpacing)
    editor.setTextScaleX(s.textScaleX)

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
    /** 6.2.0 大文件省耗：最近一次是否用纯文本（EmptyLanguage）。变化时需重设语言。 */
    var attachedForcePlain: Boolean? = null
    /** 6.2.0 批次46：最近一次已应用的内容版本号 —— 窗口模式翻页时同标签换文本，需据此重设。 */
    var attachedContentRev: Int = 0
    /**
     * 批次46/47：翻页/跳转到行/全文搜索跳转后**待落地的光标**（0 基 行:列；
     * length>0 时连选中一起落地 —— 全文搜索结果跳转要高亮匹配文本）。
     * sora 的 setText 是异步落地的，目标行只有在新文本 setText 之后定位才可靠，
     * 由 EditorCanvas 在换页 setText 后消费。
     */
    data class PendingSel(val line: Int, val col: Int, val length: Int = 0)
    var pendingSelection: PendingSel? = null
    /** 6.2.0 批次31：十六进制颜色背景。setEditorLanguage 会清掉背景，需在重设语言后手动 refresh。 */
    var hexHints: HexColorHints? = null
    /** 6.2.0 批次57（用户）：搜索结果编辑器内高亮。与 hex 共用 setHighlightTexts 通道，成对补写。 */
    var matchHighlights: MatchHighlights? = null
    /**
     * 6.2.0 大文件省耗：最近一次应用给 [hexHints] 的扫描行数上限。
     * 省耗开 = [HexColorHints.MAX_LINES]（1 万行），省耗关 = [Int.MAX_VALUE]（全量扫描）。
     * 变化时要 refresh，否则旧的空容器不会重建、色块不会回来。
     */
    var attachedHexCap: Int? = null
    /** 批次54：参数可空 —— null = 大文档脏信号（不带文本，防止每键全文复制 OOM）。 */
    var onContentChange: ((String?) -> Unit)? = null
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

    /**
     * 6.2.0 批次57：`setEditorLanguage` 内部会 `setHighlightTexts(null)` 清空整个背景容器 ——
     * 两个背景来源（hex 色块 / 搜索高亮）成对补写。先后无关：后写的会把先写的按颜色类型
     * （hex=ConstColor、搜索=EditorColor）带载回来，见 MatchHighlights KDoc。
     */
    fun restoreDecorations() {
        hexHints?.refresh()
        matchHighlights?.reapply()
    }

    private var contentListener: SubscriptionReceipt<ContentChangeEvent>? = null
    private var selectionListener: SubscriptionReceipt<SelectionChangeEvent>? = null
    private var keyBindingListener: SubscriptionReceipt<KeyBindingEvent>? = null

    fun attach(view: CodeEditor) {
        if (editor === view) return
        editor = view
        contentListener = view.subscribeEvent(ContentChangeEvent::class.java) { event, _ ->
            if (event.action == ContentChangeEvent.ACTION_SET_NEW_TEXT) return@subscribeEvent
            // 批次54 修 2026-10-21 22:26 OOM：每个按键都把整档 toString 全文（30MB 档
            // ≈ 57MB 字符数组/键），堆 531/537MB 顶穿。大文档只回传 null = 脏信号；
            // 最新文本由保存/搜索/替换/切走前的 contentRefresher 统一回灌。
            val text = view.text
            if (text.length <= LARGE_EDIT_TEXT_CHARS) onContentChange?.invoke(text.toString())
            else onContentChange?.invoke(null)
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
        matchHighlights?.detach()
        matchHighlights = null
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
        attachedForcePlain = null
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
                    // 该选项只会按深浅色在两个主题间切换，语义含糊。默认固定 github-dark-dimmed。
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
// 编码选择器（6.2.0）
// ============================================================
/**
 * 底部状态栏「编码」按钮弹出的选择器。
 *
 * 首项「自动检测」会按文件字节判定 UTF-8 / GBK 等（见 [FileAccess.detectCharset]）；
 * 其余为手动指定，选定后立即按该编码**重新读取**当前文档（见
 * [com.UIN.Tool.ui.screen.dev.editor.EditorViewModel.setTabCharset]）。
 */
@Composable
fun EncodingSelectionDialog(
    currentCharset: String,
    detectedCharset: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 480.dp)
                .height(460.dp)
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
                        text = Str.get(R.string.editor_encoding),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    UnifiedIconButton(
                        icon = Icons.Default.Close,
                        onClick = onDismiss,
                        contentDescription = Str.get(R.string.cancel)
                    )
                }
                // 当前判定结果 / 正在使用的编码 —— 让「按什么读的」这件事始终可见。
                Text(
                    text = Str.get(R.string.editor_encoding_hint, detectedCharset),
                    style = MaterialTheme.typography.labelSmall,
                    color = AppColors.textSecondary(),
                    modifier = Modifier.padding(
                        horizontal = AppDimens.spacingLarge,
                        vertical = AppDimens.spacingSmall
                    )
                )
                HorizontalDivider()
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(AppDimens.spacingSmall)
                ) {
                    items(FileAccess.CHARSET_OPTIONS) { option ->
                        ThemeRow(
                            name = if (option == FileAccess.CHARSET_AUTO) {
                                Str.get(R.string.editor_encoding_auto)
                            } else option,
                            selected = option == currentCharset,
                            isDark = null
                        ) { onSelect(option) }
                    }
                }
            }
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
    // 6.2.0 批次42：文件名过长时的显示方式（编辑器设置「文件树多行显示」）。
    multiLine: Boolean = false,
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
    var propertiesTarget by remember { mutableStateOf<java.io.File?>(null) }
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
                        multiLine = multiLine,
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
                        onDelete = { deleteTarget = node.file },
                        onProperties = { propertiesTarget = node.file }
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

    // 6.2.0：文件/文件夹属性。批次43 抽成共用 [FilePropertiesDialog]（文件树 ⋮、
    // 长按标签页共用），弹窗内新增行数/字节/字符数流式统计，行可点复制。
    propertiesTarget?.let { file ->
        FilePropertiesDialog(file = file, onDismiss = { propertiesTarget = null })
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

/**
 * 属性对话框里的一行「标签 : 值」。值超长自动换行，方便看完整路径。
 * 批次43：整行可点击（带涟漪）→ 复制该值到剪贴板；「—」占位值没有复制意义，禁用。
 */
@Composable
private fun PropertyRow(label: String, value: String) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .clickable(
                enabled = value.isNotBlank() && value != "—",
                onClick = {
                    clipboard.setText(AnnotatedString(value))
                    AppToast.info(context, Str.get(R.string.copied_to_clipboard))
                }
            )
            .padding(vertical = 2.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = AppColors.textSecondary(),
            modifier = Modifier.width(88.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
    }
}

// ============================================================
// 属性弹窗（批次43：文件树 ⋮ 与长按标签共用；行数/字节/字符数 IO 流式统计）
// ============================================================
@Composable
private fun FilePropertiesDialog(file: java.io.File, onDismiss: () -> Unit) {
    val isDir = file.isDirectory
    val typeValue = if (isDir) Str.get(R.string.folder)
    else file.extension.ifEmpty { Str.get(R.string.file) }
    val sizeValue = if (isDir) "—" else FileAccess.formatSize(file.length())
    val timeValue = if (file.lastModified() > 0L) {
        java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
            .format(java.util.Date(file.lastModified()))
    } else "—"
    var linesValue by remember { mutableStateOf("…") }
    var bytesValue by remember { mutableStateOf("…") }
    var charsValue by remember { mutableStateOf("…") }
    LaunchedEffect(file) {
        if (isDir) {
            linesValue = "—"
            bytesValue = "—"
            charsValue = "—"
        } else {
            bytesValue = file.length().toString()
            val (l, c) = withContext(Dispatchers.IO) { countLinesAndChars(file) }
            linesValue = l
            charsValue = c
        }
    }
    UnifiedDialog(
        onDismissRequest = onDismiss,
        title = Str.get(R.string.file_properties),
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(AppDimens.spacingSmall)) {
                PropertyRow(Str.get(R.string.name), file.name)
                PropertyRow(Str.get(R.string.location), file.absolutePath)
                PropertyRow(Str.get(R.string.file_type), typeValue)
                PropertyRow(Str.get(R.string.container_info_size), sizeValue)
                PropertyRow(Str.get(R.string.editor_prop_lines), linesValue)
                PropertyRow(Str.get(R.string.editor_prop_bytes), bytesValue)
                PropertyRow(Str.get(R.string.editor_prop_chars), charsValue)
                PropertyRow(Str.get(R.string.editor_modified_time), timeValue)
            }
        },
        confirmButton = {
            UnifiedButton(
                text = Str.get(R.string.close),
                onClick = onDismiss,
                modifier = Modifier.weight(1f)
            )
        }
    )
}

/**
 * 批次43：流式数行数/字符数 —— 永不整读进内存。
 *  - 只开一次头（≤64KB）判定编码（[FileAccess.detectCharsetFromHead]），随后从同一条流解码；
 *  - >128MB 直接报「—」（数完要好几秒，弹窗上不值得卡）；字节数始终精确（File.length O(1)）；
 *  - 行数口径与 sora 一致：空文档 1 行，n 个 `\n` → n+1 行。
 */
private fun countLinesAndChars(file: java.io.File): Pair<String, String> = try {
    val maxCountBytes = 128L * 1024 * 1024
    val total = file.length()
    if (total > maxCountBytes) {
        "—" to "—"
    } else {
        FileInputStream(file).use { fis ->
            val head = ByteArray(minOf(total, 65_536L).toInt().coerceAtLeast(1))
            val n = fis.read(head)
            if (n < 0) {
                "1" to "0"
            } else {
                val charset = FileAccess.detectCharsetFromHead(
                    if (n == head.size) head else head.copyOf(n)
                )
                InputStreamReader(fis, charset).use { r ->
                    var chars = 0L
                    var lines = 0L
                    val buf = CharArray(8192)
                    while (true) {
                        val k = r.read(buf)
                        if (k < 0) break
                        chars += k
                        for (i in 0 until k) if (buf[i] == '\n') lines++
                    }
                    (if (chars == 0L) 1L else lines + 1).toString() to chars.toString()
                }
            }
        }
    }
} catch (_: Exception) {
    "—" to "—"
}

@Composable
private fun FileNodeRow(
    node: FileNode,
    depth: Int,
    isActive: Boolean,
    isSelectedDir: Boolean = false,
    // 6.2.0 批次42：文件名多行显示（编辑器设置）—— false 单行裁切，true 换行自适应行高。
    multiLine: Boolean = false,
    onToggle: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onProperties: () -> Unit
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
            // 6.2.0 批次42：长按行 = 点行尾 ⋮，弹出同一菜单（锚定仍在 ⋮ 按钮处）。
            .combinedClickable(onClick = onToggle, onLongClick = { menuOpen = true })
            .padding(start = (8 + depth * 14).dp, end = AppDimens.spacingSmall)
            // 多行显示时行高随内容生长（min 40dp 保持单行时观感），关闭则固定 40dp 单行裁切。
            .then(
                if (multiLine) Modifier.heightIn(min = 40.dp)
                else Modifier.height(40.dp)
            ),
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
        // 6.2.0 批次42：修「文件名过长时行尾 ⋮ 显示不出来」。
        // Text 不加 weight(1f) 时按**整行宽度**测量 —— 省略号要到整行宽度才触发，
        // 名字一长就把后面的 ⋮ 顶出可视区。加 weight 后文本只按剩余空间测量、
        // 超长即出省略号；原「Spacer(weight(1f)) 把 ⋮ 推到行尾」的作用由该 weight
        // 一并承担（Text 填满剩余空间，⋮ 恒贴行尾，短名也不受影响）。
        Text(
            text = node.name,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
            maxLines = if (multiLine) Int.MAX_VALUE else 1,
            overflow = if (multiLine) TextOverflow.Clip else TextOverflow.Ellipsis,
            color = if (isActive || isSelectedDir) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface
        )
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
                    },
                    onProperties = {
                        menuOpen = false
                        onProperties()
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
    onDelete: () -> Unit,
    onProperties: () -> Unit
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
        // 6.2.0：查看属性（名称/路径/类型/大小/修改时间）——非破坏性，只读信息。
        DropdownMenuItem(
            text = { Text(Str.get(R.string.editor_properties), style = MaterialTheme.typography.bodyMedium) },
            onClick = onProperties
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
    onClose: (String) -> Unit,
    // 6.2.0 批次43：长按标签 → 完整属性弹窗（文件树行的长按仍是 ⋮ 菜单）。
    onLongPress: (EditorTab) -> Unit
) {
    val scroll = rememberScrollState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scroll)
            // 6.2.0 批次14：chrome 由 cardBackground(不透明 surface) 改为 pageBackground ——
            // 渐变开启时透出全局渐变、玻璃开启时用玻璃底，与 TopAppBar/应用整体统一。
            .background(AppColors.pageBackground())
            // 6.2.0 批次43：38 → 44dp，略低于符号栏 —— 点 × 时不再误触到相邻标签。
            .height(44.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        tabs.forEach { tab ->
            val active = tab.id == activeId
            Row(
                modifier = Modifier
                    .fillMaxHeight()
                    .background(
                        if (active) AppColors.cardBackground() else Color.Transparent,
                        RoundedCornerShape(AppDimens.radiusSmall)
                    )
                    // 6.2.0：标签点击去掉水波纹——标签本身已有底色区分选中态，
                    // 涟漪在窄条上扩散反而糊住文件名。批次43：点击/长按合成 combinedClickable
                    // （indication=null 与原行为一致），长按打开属性。
                    .combinedClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onSelect(tab.id) },
                        onLongClick = { onLongPress(tab) }
                    )
                    // 批次48：内边距加宽 —— 图标/文字/关闭键之间留够空隙，点选不蹭到 × 。
                    .padding(horizontal = AppDimens.spacingMedium),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = getFileIcon(tab.name),
                    contentDescription = null,
                    // 批次48：16 → 20dp，与加高后的行匹配，占满更多容器高度。
                    modifier = Modifier.size(AppDimens.iconMedium),
                    tint = if (active) MaterialTheme.colorScheme.primary else AppColors.textSecondary()
                )
                Spacer(modifier = Modifier.width(AppDimens.spacingSmall))
                Text(
                    text = tab.name + if (tab.isModified) " •" else "",
                    // 批次48：labelMedium → labelLarge（字重更实）。
                    // 批次49：label* 档位被 Type.kt 统一钉在 caption.sp(12sp)，光换档不放大字号，
                    // 仍是「文字小、边框大」。这里内联提到 bodyTextSize(14sp) —— 只放大标签页，
                    // 不动全局 labelLarge（文件树标题等另一处共用档位保持原样）。
                    style = MaterialTheme.typography.labelLarge,
                    fontSize = AppDimens.bodyTextSize.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (active) MaterialTheme.colorScheme.primary else AppColors.textSecondary(),
                    // 批次48：120 → 160dp —— 名字可见更长，避免两个标签的 × 挤在一起被误点。
                    modifier = Modifier.widthIn(max = 160.dp)
                )
                Spacer(modifier = Modifier.width(AppDimens.spacingSmall))
                // 批次48：× 从「16dp 图标 = 16dp 热区」放大成「36×44dp 可点区域 + 20dp 图标」——
                // 热区贴满容器高度、宽度独立于图标大小，手指按下去按得准、也不易蹭到相邻标签的 ×。
                Box(
                    modifier = Modifier
                        .width(36.dp)
                        .fillMaxHeight()
                        .clickable { onClose(tab.id) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = Str.get(R.string.editor_close_tab),
                        modifier = Modifier.size(AppDimens.iconMedium),
                        tint = AppColors.textSecondary()
                    )
                }
            }
        }
    }
    HorizontalDivider()
}

// ============================================================
// 信息栏（批次43：标签栏下方 —— 行/字节/字符/选中，全部数字直读；
// 行:列与选中原先挤在底部状态栏，现移上来，状态栏腾给分页/编码/设置）
// ============================================================
@Composable
private fun EditorInfoBar(
    line: Int,
    totalLines: Int,
    /** 磁盘字节数；uri/未落盘标签为 null → 显示「—」。 */
    bytes: Long?,
    chars: Int,
    selection: Int,
    /** 批次46：分页窗口模式 —— 行号是绝对值；批次53（用户反馈「窗口模式是什么」）
     *  撤掉术语徽标，改在字符数上标注「本页」，一眼看懂数字口径。 */
    windowed: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AppColors.pageBackground())
            .padding(horizontal = AppDimens.spacingMedium, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(AppDimens.spacingMedium),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = Str.get(R.string.editor_info_line_total, line, totalLines),
            style = MaterialTheme.typography.labelSmall,
            color = AppColors.textSecondary(),
            maxLines = 1
        )
        // bytes=null（uri/未落盘）不能进 %1$d 格式化 —— String 塞 %d 会直接崩，单独走「—」。
        Text(
            text = if (bytes != null) Str.get(R.string.editor_info_bytes, bytes) else "—",
            style = MaterialTheme.typography.labelSmall,
            color = AppColors.textSecondary(),
            maxLines = 1
        )
        Text(
            text = if (windowed) {
                // 分页打开时此栏数字是**本页**字符数 —— 直接标出来，免得被当成全文。
                Str.get(R.string.editor_info_chars_page, chars)
            } else {
                Str.get(R.string.editor_info_chars, chars)
            },
            style = MaterialTheme.typography.labelSmall,
            color = AppColors.textSecondary(),
            maxLines = 1
        )
        if (selection > 0) {
            Text(
                text = Str.get(R.string.editor_info_selected, selection),
                style = MaterialTheme.typography.labelSmall,
                color = AppColors.textSecondary(),
                maxLines = 1
            )
        }
    }
    HorizontalDivider()
}

// ============================================================
// 状态栏（批次43 重排：行:列/选中 → 信息栏，自动换行图标 → 删除（设置页仍有开关）；
// 批次44 再调：分页单独在左下，编码 + 设置靠右、设置固定最右）
// ============================================================
@Composable
private fun EditorStatusBar(
    readOnly: Boolean,
    /** 6.2.0：当前文档**实际使用**的编码名（UTF-8 / GB18030 …），兼作编码按钮的标签。 */
    charset: String,
    onPickCharset: () -> Unit,
    /** 批次43：分页导航 —— 当前页（1 起）/总页数；总页数 ≤1 时不显示分页控件。 */
    currentPage: Int,
    totalPages: Int,
    onJumpPage: (Int) -> Unit,
    /** 批次50：窗口渐进式打开的索引进度（0..100）；非 null 时左下角显示「扫描 N%」。 */
    scanPercent: Int? = null,
    /** 批次51：窗口保存进度（0..100）；非 null 时左下角显示「保存中 N%」（优先于扫描）。 */
    savePercent: Int? = null,
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
        // 批次44（用户指定）：分页单独留**左下**；编码与设置靠**右**，设置固定最右。
        // 右组不参与压缩（match/编码都封顶省略），窄屏 ⚙ 永不被挤掉。
        // 批次50：索引扫描期间显示进度（此时 totalPages=0，分页控件本就不出现）。
        // 批次51：保存中显示「保存中 N%」——GB 级写盘数十秒，无反馈会被连点/误判卡死。
        when {
            savePercent != null -> Text(
                text = Str.get(R.string.editor_window_saving, savePercent),
                style = MaterialTheme.typography.labelSmall,
                color = AppColors.textSecondary(),
                maxLines = 1
            )
            scanPercent != null -> Text(
                text = Str.get(R.string.editor_window_scanning, scanPercent),
                style = MaterialTheme.typography.labelSmall,
                color = AppColors.textSecondary(),
                maxLines = 1
            )
            else -> PageSwitcher(currentPage = currentPage, totalPages = totalPages, onJumpPage = onJumpPage)
        }
        Spacer(modifier = Modifier.weight(1f))
        Row(
            horizontalArrangement = Arrangement.spacedBy(AppDimens.spacingXSmall),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 复查：原「matchInfo」sora 页内计数（x/y，72dp）已随「搜索一律整档」删除 ——
            // 计数统一显示在搜索面板状态行（全文匹配 N 处）。
            if (readOnly) {
                Text(
                    text = Str.get(R.string.editor_readonly_badge),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            TextButton(
                onClick = onPickCharset,
                contentPadding = PaddingValues(
                    horizontal = AppDimens.spacingSmall,
                    vertical = 0.dp
                ),
                modifier = Modifier.heightIn(min = 44.dp)
            ) {
                Text(
                    text = charset,
                    style = MaterialTheme.typography.labelSmall,
                    color = AppColors.textSecondary(),
                    maxLines = 1,
                    // 批次45：**不再 90dp 截断** —— 完整显示编码类型（如「自动检测·GB18030」）；
                    // 省略号仅作极窄屏最后兜底，常规宽度下永不触发。
                    overflow = TextOverflow.Ellipsis
                )
            }
            UnifiedIconButton(
                icon = Icons.Default.Settings,
                onClick = onOpenSettings,
                tint = AppColors.textSecondary(),
                contentDescription = Str.get(R.string.editor_settings)
            )
        }
    }
}

// ============================================================
// 分页切换（批次43：左下角 `n/N`，点击带涟漪出下拉框；
// 框顶是整数输入（只许数字，必须落在 1..N），下方是页码列表）
// ============================================================
@Composable
private fun PageSwitcher(
    currentPage: Int,
    totalPages: Int,
    onJumpPage: (Int) -> Unit
) {
    // 单页文档没有分页概念 —— 不占底部栏宽度。
    if (totalPages <= 1) return
    var showDialog by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf("") }
    Text(
        text = "$currentPage/$totalPages",
        style = MaterialTheme.typography.labelSmall,
        color = AppColors.textSecondary(),
        maxLines = 1,
        // 批次43：**要涟漪**（普通 clickable，默认 indication），不再沿用标签栏的 indication=null。
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .clickable {
                input = currentPage.toString()
                showDialog = true
            }
            .padding(horizontal = AppDimens.spacingSmall, vertical = 4.dp)
    )
    // 批次51（用户复测「输入框水滴形指针跑到顶部」）：根因不在输入组件，而在弹层是
    // **DropdownMenu = Popup 窗口** —— UnifiedDialogs.kt 批次18 原话：「Popup 里的输入框
    // 获焦时，软键盘光标手柄会被渲染到弹窗左上角而非输入框内（Popup 与 IME 的窗口定位冲突）」。
    // 批次45 只换了输入组件、没离开 Popup，故一直没根治。改用与 GotoLineDialog 同款的
    // **UnifiedDialog（真 Dialog 窗口）**，光标手柄定位正常。
    if (showDialog) {
        val n = input.toIntOrNull()
        // 空输入不报错（等键入）；非空但超出 1..N 才标红 —— 确认时也不执行跳转。
        val invalid = input.isNotEmpty() && (n == null || n < 1 || n > totalPages)
        fun jump() {
            if (!invalid && n != null) {
                onJumpPage(n)
                showDialog = false
            }
        }
        UnifiedDialog(
            onDismissRequest = { showDialog = false },
            title = Str.get(R.string.editor_page_input_hint, totalPages),
            content = {
                UnifiedTextField(
                    value = input,
                    onValueChange = { s ->
                        // 只许数字、封顶 7 位；中文输入法直接拦下。
                        if (s.all { it.isDigit() } && s.length <= 7) input = s
                    },
                    singleLine = true,
                    label = Str.get(R.string.editor_page_input_hint, totalPages),
                    error = if (invalid) Str.get(R.string.editor_page_input_error, totalPages) else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    keyboardActions = KeyboardActions(onDone = { jump() }),
                    modifier = Modifier.fillMaxWidth()
                )
                HorizontalDivider()
                // 批次44 修「点分页即崩」：弹层用**固有尺寸(intrinsics)**测量内容，LazyColumn 是
                // SubcomposeLayout、不提供 intrinsics → IllegalStateException（改普通 Column + 限高滚动）。
                // 批次50 修「点分页卡死」：GB 级文件 totalPages 上万（24.txt = 16858 页），
                // repeat 全量组合直接卡死主线程 —— 只列**当前页 ±20**，首/尾页单列一项，
                // 远距离跳转走上方输入框（本来就是为粗粒度跳页设计的）。
                Column(
                    modifier = Modifier
                        .heightIn(max = 220.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    val start = maxOf(1, currentPage - 20)
                    val end = minOf(totalPages, currentPage + 20)
                    if (start > 1) {
                        Text(
                            text = Str.get(R.string.editor_page_item, 1),
                            style = MaterialTheme.typography.labelSmall,
                            color = AppColors.textSecondary(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onJumpPage(1)
                                    showDialog = false
                                }
                                .padding(
                                    horizontal = AppDimens.spacingMedium,
                                    vertical = AppDimens.spacingSmall
                                )
                        )
                    }
                    for (p in start..end) {
                        DropdownMenuItem(
                            text = { Text(Str.get(R.string.editor_page_item, p)) },
                            onClick = {
                                onJumpPage(p)
                                showDialog = false
                            }
                        )
                    }
                    if (end < totalPages) {
                        Text(
                            text = Str.get(R.string.editor_page_item, totalPages),
                            style = MaterialTheme.typography.labelSmall,
                            color = AppColors.textSecondary(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onJumpPage(totalPages)
                                    showDialog = false
                                }
                                .padding(
                                    horizontal = AppDimens.spacingMedium,
                                    vertical = AppDimens.spacingSmall
                                )
                        )
                    }
                }
            },
            confirmButton = {
                UnifiedDialogTextButton(onClick = { jump() }) { Text(Str.get(R.string.ok_2)) }
            },
            dismissButton = {
                UnifiedDialogTextButton(onClick = { showDialog = false }) {
                    Text(Str.get(R.string.cancel))
                }
            }
        )
    }
}

// ============================================================
// 查找 / 替换
// ============================================================

/**
 * 6.2.0 批次57②（用户反馈）：搜索面板状态**提升到 Screen 级**。
 *
 * 面板在 AnimatedVisibility 收起时整树卸载，面板内 remember 全部归零 —— 重开面板会以
 * 空查询触发「空输入即清结果」，把已搜完的结果与编辑器高亮一起误清（用户：只有面板
 * 关闭图标 X 才应清除）。查询/选项/当前命中游标提到这里，收起重开原样保留；清空只由
 * X（onClose → cancelWholeSearch）显式执行。字段用 MutableState 承载，面板内仍以
 * `by state.query` 委托，原有读写点一行不用改。
 */
private class SearchPanelState {
    val query = mutableStateOf("")
    val replacement = mutableStateOf("")
    val caseSensitive = mutableStateOf(false)
    val wholeWord = mutableStateOf(false)
    val useRegex = mutableStateOf(false)
    val matchIdx = mutableStateOf(-1)

    /** 防抖签名（tab|查询|选项）—— 重开面板同签名且结果仍在就跳过重扫（GB 级重扫数十秒）。 */
    var lastSignature: String = ""
}

@Composable
private fun SearchPanel(
    onClose: () -> Unit,
    // ---- 批次53（用户反馈）：**搜索/替换一律整档**，删掉「全文」开关与 sora 页内双轨 ----
    // 此前小文件走 sora 页内、大文件要手动开「全文」，两套状态互相抢（还引出「整读大文件
    // 不支持替换」「请打开全文」这类只有我们自己懂的提示）。现在唯一路径 = WholeSearch
    // 整档扫描（小文件为单页内存扫描，秒回；大文件流式分页），上一个/下一个在整档结果间
    // 移动，替换按文档形态由 VM 内部分路：窗口流式回写 / 非窗口内存整写。
    /** 复查：当前标签 id —— 进搜索防抖效果的键：切标签后同一查询自动对新标签重搜。 */
    tabKey: String = "",
    /** true = 非窗口（完整加载）文档 → 替换走内存整写：确认文案与内存提示按此切换。 */
    inMemoryReplace: Boolean = false,
    whole: EditorViewModel.WholeSearchUi? = null,
    wholeReplace: EditorViewModel.WholeReplaceUi? = null,
    onWholeSearch: (String, Boolean, Boolean, Boolean) -> Unit = { _, _, _, _ -> },
    onCancelWhole: () -> Unit = {},
    onWholeReplace: (String, Boolean, Boolean, Boolean, String) -> Unit = { _, _, _, _, _ -> },
    onJumpMatch: (WholeSearch.WholeMatch) -> Unit = {},
    // ---- 批次53：全量结果页 —— 面板只列前500条，完整列表走对话框（批次57 起搜索即全量收齐） ----
    full: EditorViewModel.WholeFullUi? = null,
    /** 全量列表的行内预览缓存（列表下标 → 预览；空时先显示占位、可见后懒加载）。 */
    fullPreviews: Map<Int, String> = emptyMap(),
    onLoadPreviews: (Int, Int) -> Unit = { _, _ -> },
    // 批次57②：面板状态由 Screen 持有（见 SearchPanelState）—— 收起重开不丢查询/选项。
    state: SearchPanelState
) {
    var query by state.query
    var replacement by state.replacement
    var caseSensitive by state.caseSensitive
    var wholeWord by state.wholeWord
    var useRegex by state.useRegex
    var showReplace by remember { mutableStateOf(false) }
    // 全文替换确认弹窗；matchIdx = 整档结果里的当前命中游标（上一个/下一个在结果间移动，
    // 新一轮搜索（换词/换选项）时归零重新导航。
    var showWholeReplaceConfirm by remember { mutableStateOf(false) }
    var matchIdx by state.matchIdx

    // ---- 批次53：整档搜索唯一入口（输入 450ms 防抖）—— 空输入即清结果，输入即扫。
    // （批次39 的 sora 异步计数订阅、批次47 的「全文」开关 LaunchedEffect 均已随
    // 「搜索一律整档」移除 —— 不再存在第二套搜索状态。）
    // 复查：键里带 tabKey —— 切到另一标签后用同一查询对新标签自动重搜（VM 的 whole
    // 状态是全局单例，调用点又按标签过滤展示，不重搜会一直空态）。
    LaunchedEffect(tabKey, query, caseSensitive, wholeWord, useRegex) {
        // 批次57②：重开面板（状态已提升、签名与结果都还在）同签名且结果仍有效
        //（仍是本标签本查询的结果、非过期/非进行中/非报错）就直接原样展示，不重扫 ——
        // GB 级重扫要数十秒，且重扫过程会把已有结果与高亮闪空一轮。
        val sig = "$tabKey\u0000$query\u0000$caseSensitive\u0000$wholeWord\u0000$useRegex"
        if (query.isNotEmpty()) {
            val w = whole
            if (sig == state.lastSignature && w != null && w.query == query &&
                !w.running && !w.error && !w.stale
            ) {
                return@LaunchedEffect
            }
            matchIdx = -1
            state.lastSignature = sig
            delay(450)
            onWholeSearch(query, caseSensitive, wholeWord, useRegex)
        } else {
            matchIdx = -1
            state.lastSignature = ""
            onWholeSearch("", false, false, false)
        }
    }

    // 批次53：**移除**「替换完成后自动重跑全文搜索」—— GB 级替换后紧跟一次全文重扫要再等
    // 数十秒，面板被旧结果+新进度反复刷屏，用户要的只是「替换成功」。现在替换完成直接
    // 显示成功并给手动「重新搜索」按钮（见下方状态行）。
    var showFull by remember { mutableStateOf(false) }
    // 全量结果页开着时新一轮搜索启动/出错/被清空 → 自动收起（旧结果继续挂着会误导）。
    LaunchedEffect(showFull, whole) {
        if (showFull && (whole == null || whole.running || whole.error)) showFull = false
    }

    // ---- 上一个/下一个：批次53 起在**整档结果**间移动（sora 页内翻页随双轨一并移除）----
    // 首次点「下一个」跳第一条、点「上一个」跳最后一条；到边界停住（不回卷 —— 回卷在
    // 整档语义下容易让人以为结果变少了）。
    fun jumpMatch(step: Int) {
        val ms = whole?.matches ?: return
        if (ms.isEmpty()) return
        val from = if (matchIdx < 0) (if (step > 0) -1 else ms.size) else matchIdx
        val idx = (from + step).coerceIn(0, ms.size - 1)
        matchIdx = idx
        onJumpMatch(ms[idx])
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
            onValueChange = { query = it },
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
                onClick = { jumpMatch(-1) },
                contentDescription = Str.get(R.string.editor_search_prev)
            )
            UnifiedIconButton(
                icon = Icons.Default.KeyboardArrowRight,
                onClick = { jumpMatch(1) },
                contentDescription = Str.get(R.string.editor_search_next)
            )
            Spacer(modifier = Modifier.width(AppDimens.spacingSmall))
            // 选项改动由上面的防抖 LaunchedEffect 自动触发新搜索，无需手动调用。
            FilterChipToggle(Str.get(R.string.editor_match_case), caseSensitive) { caseSensitive = it }
            FilterChipToggle(Str.get(R.string.editor_whole_word), wholeWord) { wholeWord = it }
            FilterChipToggle(Str.get(R.string.editor_regex), useRegex) { useRegex = it }
            Spacer(modifier = Modifier.weight(1f))
            UnifiedIconButton(
                icon = if (showReplace) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                onClick = { showReplace = !showReplace },
                contentDescription = Str.get(R.string.editor_replace)
            )
            UnifiedIconButton(
                icon = Icons.Default.Close,
                onClick = {
                    onCancelWhole()
                    onClose()
                },
                contentDescription = Str.get(R.string.cancel)
            )
        }
        // ---- 批次56：重搜/「全部结果」的可用性（提到状态行前 —— 按钮并入状态行右侧）----
        val wAct = whole
        val wRepAct = wholeReplace
        val hasResults = wAct != null && !wAct.error && !wAct.running && wAct.total > 0
        val canRefresh = query.isNotEmpty() && (
            (wRepAct?.done == true && wRepAct.error == null) ||
                wAct?.stale == true ||
                (wAct == null && wRepAct?.running != true && wRepAct?.done != true)
            )
        // ---- 批次53：状态行（进度/计数/错误）—— 搜索一律整档，无开关、无模式名词 ----
        Spacer(modifier = Modifier.height(AppDimens.spacingSmall))
        Row(verticalAlignment = Alignment.CenterVertically) {
            val w = whole
            val wReplace = wholeReplace
            if (w?.running == true) {
                // 扫描中把状态位让出来、给「取消」让路 —— 长任务必须可打断。
                Text(
                    text = Str.get(R.string.editor_search_whole_running, w.progress),
                    style = MaterialTheme.typography.labelSmall,
                    color = AppColors.textSecondary(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onCancelWhole) {
                    Text(
                        text = Str.get(R.string.cancel),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            } else {
                val status = when {
                    wReplace?.running == true ->
                        Str.get(R.string.editor_replace_whole_running, wReplace.progress)
                    wReplace?.error != null -> wReplace.error
                    wReplace?.done == true -> {
                        // 非窗口替换没有「页」概念 —— 用不带页数的完成文案，
                        // 避免出现「涉及 0 页」这种费解表述。
                        val base = if (wReplace.inMemory) {
                            Str.get(R.string.editor_replace_whole_done_mem, wReplace.replaced)
                        } else {
                            Str.get(
                                R.string.editor_replace_whole_done,
                                wReplace.replaced, wReplace.pages
                            )
                        }
                        if (wReplace.boundary > 0) {
                            base + Str.get(R.string.editor_replace_whole_boundary, wReplace.boundary)
                        } else base
                    }
                    w == null -> ""
                    w.error -> Str.get(R.string.editor_search_invalid)
                    // 复查：文档被编辑后结果过期 —— 明说并给重搜按钮（跳转侧另有夹界兜底）。
                    w.stale -> Str.get(R.string.editor_search_whole_stale)
                    w.total == 0 -> Str.get(R.string.editor_search_whole_none)
                    // 批次57（用户）：去掉「仅列前 500 条」文案 —— 面板已不内嵌结果列表，
                    // 浏览一律走「全部结果」对话框，截断提示只剩误导；截断态照常显示总命中数。
                    else -> Str.get(R.string.editor_search_whole_done, w.total)
                }
                Text(
                    text = status,
                    style = MaterialTheme.typography.labelSmall,
                    color = AppColors.textSecondary(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                // 批次56（用户指定）：重搜/「全部结果」并入状态行右侧 —— 不再单占一行
                //（面板更矮）；面板内结果列表已移除，「全部结果」是浏览与选择匹配的唯一入口。
                if (canRefresh) {
                    TextButton(onClick = {
                        matchIdx = -1
                        onWholeSearch(query, caseSensitive, wholeWord, useRegex)
                    }) {
                        Text(
                            text = Str.get(R.string.editor_search_whole_refresh),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
                if (hasResults) {
                    TextButton(onClick = { showFull = true }) {
                        Text(
                            text = Str.get(R.string.editor_search_full_btn),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }
        // 批次56：原「动作按钮独立一行」与「面板内结果列表（200.dp）」已移除 ——
        // 重搜/「全部结果」并入上方状态行右侧；匹配的选择与浏览只走「全部结果」弹窗
        //（用户指定：去掉预览框，避免面板占空间）。
        // 替换区：展开后出现替换输入框 + 「全部替换（全文）」。
        // 批次53（用户反馈）：**所有文件统一同一个按钮** —— VM 内部按文档形态分路
        //（窗口 → 磁盘回写流式改写；非窗口 → 内存单遍整写），不再出现「整读大文件
        // 不支持替换」「请打开全文」这类分叉提示；sora 页内「替换/全部替换」随双轨移除
        //（整档语义下只有「当前匹配」没有意义，且其 SearchRunnable 复制整档、大文件 OOM）。
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
            UnifiedButton(
                text = Str.get(R.string.editor_replace_all_whole),
                size = ButtonSize.Small,
                onClick = { showWholeReplaceConfirm = true },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
    // 批次47：全文替换确认 —— 改写可能耗时数十秒，且保存前不落盘，必须显式确认。
    // 批次53：非窗口（完整加载）文档走内存整写，文案讲人话 —— 一次性重写全文、
    // 撤销会重置、大文件可能慢/占内存，不再提「写回 spool」这种内部机制。
    if (showWholeReplaceConfirm) {
        UnifiedConfirmDialog(
            title = Str.get(R.string.editor_replace_all_whole_confirm_title),
            message = if (inMemoryReplace) {
                Str.get(R.string.editor_replace_all_whole_confirm_mem)
            } else {
                Str.get(R.string.editor_replace_all_whole_confirm)
            },
            onConfirm = {
                showWholeReplaceConfirm = false
                onWholeReplace(query, caseSensitive, wholeWord, useRegex, replacement)
            },
            onDismiss = { showWholeReplaceConfirm = false },
            isDestructive = true
        )
    }

    // ---- 批次53：全量结果页 —— 面板只列前500条，完整列表在此对话框里看 ----
    // 紧凑项不带预览（百万级列表不可能预生成），可见范围经 snapshotFlow 驱动按需懒加载；
    // 点击行跳转并收起（复用面板同款 onJumpMatch 定位）。批次57（用户）：搜索一趟全量收齐，
    //「加载更多」按钮与其 loading/error 展示一并移除。
    if (showFull) {
        val f = full
        UnifiedDialog(
            onDismissRequest = { showFull = false },
            title = Str.get(R.string.editor_search_full_title),
            // 批次57（用户反馈「占满全屏 + 结果被裁切」）：原 55%/480dp 封顶只约束列表，而
            // 滚动条 fillMaxHeight 把列表行撑到屏高上限 —— 标题/状态/按钮再叠上去必然顶出
            // 屏幕（横屏尤甚），于是「全屏 + 底部被裁」。改为对话框**总高**封顶
            //（min(600dp, 75% 屏高)），内部列表行 weight 吃剩余：长列表在封顶内滚动，
            // 短列表不撑高，标题/状态/按钮恒在屏内。
            modifier = Modifier.heightIn(
                max = minOf(600.dp, (LocalConfiguration.current.screenHeightDp * 0.75f).dp)
            ),
            content = {
                Text(
                    text = Str.get(
                        R.string.editor_search_full_loaded,
                        f?.matches?.size ?: 0,
                        f?.total ?: 0
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = AppColors.textSecondary()
                )
                Spacer(modifier = Modifier.height(2.dp))
                val listState = rememberLazyListState()
                // 批次57：滚动条高度跟随列表**实测高度**（onSizeChanged）—— 原 fillMaxHeight 在
                // wrap 高度的 Row 里把行撑到约束上限，正是「弹窗贴满全屏」的元凶；列表也不再
                // 自设 55%/480dp 上限，高度统一由对话框总高封顶 + 本行 weight 决定（单一来源）。
                val barDensity = LocalDensity.current
                var listH by remember { mutableStateOf(0.dp) }
                // 行吃对话框封顶后的剩余高度（fill=false → 长内容在行内滚动、短内容不撑高）。
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .onSizeChanged { listH = with(barDensity) { it.height.toDp() } }
                ) {
                    val rows = f?.matches ?: emptyList()
                    itemsIndexed(rows) { i, m ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onJumpMatch(m)
                                    showFull = false
                                }
                                .padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${m.absLine}:${m.column + 1}",
                                style = MaterialTheme.typography.labelSmall,
                                color = AppColors.textSecondary(),
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1,
                                modifier = Modifier.width(74.dp)
                            )
                            Text(
                                text = fullPreviews[i] ?: m.preview,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
                // 右侧滚动条：随列表位置指示；批次54 起支持点按/拖动跳转（热区 20dp）。
                // 批次57：高度 = 列表实测高度（原 fillMaxHeight 会把 wrap 高度的 Row 撑满）。
                SimpleVerticalScrollbar(
                    state = listState,
                    modifier = Modifier.height(listH)
                )
                }
                // 可见范围驱动按需预览（前后各留 2 行缓冲，滚动即预取）。
                LaunchedEffect(listState, f?.matches?.size) {
                    snapshotFlow { listState.layoutInfo.visibleItemsInfo }.collect { vis ->
                        if (vis.isNotEmpty()) {
                            onLoadPreviews(vis.first().index - 2, (vis.last().index + 4) - (vis.first().index - 2))
                        }
                    }
                }
            },
            confirmButton = {
                UnifiedDialogTextButton(onClick = { showFull = false }) { Text(Str.get(R.string.ok_2)) }
            }
        )
    }
}

// 批次53：原私有函数 reportMatches（sora 页内匹配计数）已随「搜索一律整档」移除 ——
// 面板计数统一来自 WholeSearch 总数，不再有第二套异步计数要对账。

// ============================================================
// 批次53（用户反馈）：简易垂直滚动条 —— 全量结果页可能上万行，
// 此前列表无任何滚动位置指示，翻到哪儿全靠记忆。
// 批次54（用户反馈「滑动条无法点击滑动」）：补上交互 —— 点按/拖动
// 按比例跳转列表；热区加宽到 20dp（拇指视觉宽 8dp），拖动中拇指跟手。
// ============================================================
@Composable
private fun SimpleVerticalScrollbar(
    state: LazyListState,
    modifier: Modifier = Modifier
) {
    val thumbColor = AppColors.textSecondary()
    val trackColor = thumbColor.copy(alpha = 0.12f)
    val activeColor = thumbColor.copy(alpha = 0.5f)
    val scope = rememberCoroutineScope()
    // 拖动中的手指纵坐标（节点坐标系）；null = 非拖动态，拇指按列表实际位置画。
    var dragY by remember { mutableStateOf<Float?>(null) }

    /** y（节点坐标）→ 列表比例 → 目标下标；仅按下标跳转（LazyList 原生能力）。 */
    fun jumpBy(y: Float, height: Float) {
        if (height <= 0f) return
        val total = state.layoutInfo.totalItemsCount
        if (total <= 0) return
        val index = ((y / height).coerceIn(0f, 1f) * (total - 1)).toInt()
        scope.launch { state.scrollToItem(index) }
    }

    Box(
        modifier = modifier
            .width(20.dp) // 热区 20dp —— 8dp 拇指宽度手指点不准
            .pointerInput(state) {
                detectTapGestures { offset ->
                    jumpBy(offset.y, size.height.toFloat())
                }
            }
            .pointerInput(state) {
                detectDragGestures(
                    onDragStart = { dragY = it.y },
                    onDrag = { change, _ ->
                        change.consume()
                        dragY = change.position.y
                        jumpBy(change.position.y, size.height.toFloat())
                    },
                    onDragEnd = { dragY = null },
                    onDragCancel = { dragY = null }
                )
            },
        contentAlignment = Alignment.CenterEnd
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
        ) {
            val info = state.layoutInfo
            val total = info.totalItemsCount
            val visible = info.visibleItemsInfo
            if (total <= 1 || visible.isEmpty() || size.height <= 0f || size.width <= 0f) {
                return@Canvas
            }
            // 以「已滚过的条目 + 首条被裁掉的比例」估算视口占比与位置：
            // 首条 offset 负值 = 上边缘被裁（部分可见），换算成小数条目。
            val first = visible.first()
            val itemAvg = visible.map { it.size }.average().toFloat().coerceAtLeast(1f)
            val partial = (first.offset.coerceAtMost(0).toFloat() / itemAvg).coerceAtLeast(-1f)
            val firstPos = first.index - partial          // 视口顶部所在条目（小数）
            val shown = visible.size + partial            // 视口内可见条目数（小数）
            val thumbRatio = (shown / total).coerceIn(0.03f, 1f)
            val thumbH = size.height * thumbRatio
            val maxTravel = size.height - thumbH
            val scrollable = (total - shown).coerceAtLeast(1f)
            // 批次54：拖动中拇指跟手（夹在轨道内），否则按列表实际位置画。
            val fraction = dragY?.let { y ->
                if (maxTravel > 0f) ((y - thumbH / 2f) / maxTravel).coerceIn(0f, 1f) else 0f
            } ?: (firstPos / scrollable).coerceIn(0f, 1f)
            val thumbTop = fraction * maxTravel
            val w = size.width
            val barW = w * 0.4f // 热区 20dp → 拇指视觉宽 8dp
            val barX = w - barW // 右对齐（列表在左侧）
            drawRoundRect(
                color = trackColor,
                topLeft = Offset(barX, 0f),
                size = Size(barW, size.height),
                cornerRadius = CornerRadius(barW / 2f)
            )
            drawRoundRect(
                color = activeColor,
                topLeft = Offset(barX, thumbTop),
                size = Size(barW, thumbH),
                cornerRadius = CornerRadius(barW / 2f)
            )
        }
    }
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
    onDismiss: () -> Unit,
    /**
     * 6.2.0 批次46：窗口模式 —— 输入按**全文绝对行号**校验与跳转（编辑器里的行号是
     * 页内相对的，这个上限与信息栏的 n/N 一致）。null = 整档模式，按编辑器自身行数。
     */
    windowedMaxLine: Int? = null,
    onWindowedGoto: ((absLine: Int) -> Unit)? = null
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
                if (line != null && windowedMaxLine != null && onWindowedGoto != null) {
                    // 窗口模式：夹到 1..总行数 后交由界面层切页 + 页内定位。
                    // 批次55 修崩溃：渐进式打开扫描期 totalLines==0，coerceIn(1, 0) 会抛
                    // IllegalArgumentException —— 索引未就绪时不做跳转（调用侧已兜一层）。
                    if (windowedMaxLine >= 1) onWindowedGoto(line.coerceIn(1, windowedMaxLine))
                } else if (line != null && editor != null) {
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
@Composable
private fun SettingsSheet(
    settings: EditorSettings,
    onChange: (EditorSettings) -> Unit,
    onOpenThemePicker: () -> Unit,
    onDismiss: () -> Unit
) {
    // 6.2.0：设置由「底部 ModalBottomSheet」改为**全屏设置页**。
    // 底部弹层里 27 项装不下，标题还随内容一起滚走；改成顶部固定栏（返回 + 标题）+ 正文滚动。
    // 本页与主内容是同一组合作用域下的兄弟节点，根作用域兄弟会重叠摆放，故 fillMaxSize 即覆盖全屏；
    // 额外吃掉点击（onNoOp）避免未被处理的点击穿透到下层编辑器。
    val brush = pageGradientBrush()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (brush != null) Modifier.background(brush)
                else Modifier.background(AppColors.cardBackground())
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { /* 阻止点击穿透到背景编辑器 */ }
            )
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 顶栏：固定不随内容滚动，保证「返回」永远可见。
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppDimens.spacingXSmall, vertical = AppDimens.spacingXSmall),
                verticalAlignment = Alignment.CenterVertically
            ) {
                UnifiedIconButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    onClick = onDismiss,
                    contentDescription = Str.get(R.string.back)
                )
                Spacer(modifier = Modifier.width(AppDimens.spacingSmall))
                Text(
                    text = Str.get(R.string.editor_settings),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            HorizontalDivider()
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    // 6.2.0 批次35：正文必须可滚动，设置项一旦超出一屏不能被裁掉。
                    // 放在 padding 之前，这样上下内边距也跟着一起滚。
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = AppDimens.spacingLarge)
                    .padding(top = AppDimens.spacingLarge)
                    .padding(bottom = AppDimens.spacingXLarge)
            ) {

            // 6.2.0 批次54（用户指定）：默认主题 github-dark-dimmed（原 dracula）；
            // 主题选择器保留，用户随时可切换。老配置残留空串时兜底同默认。
            SettingRow(Str.get(R.string.editor_theme)) {
                TextButton(onClick = onOpenThemePicker) {
                    Text(text = settings.theme.ifEmpty { "github-dark-dimmed" })
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
            // 6.2.0：行号分隔线 —— 字段与 applyRuntimeSettings 里的应用逻辑
            // （editor.setDividerWidth）早就在，但一直没配 UI 开关，属于「有实现无入口」。
            // 现在补上；关闭时宽度置 0，行号列与正文之间那条竖线消失。
            SwitchRow(Str.get(R.string.editor_line_divider), settings.lineDivider) {
                onChange(settings.copy(lineDivider = it))
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
            // 6.2.0：符号栏 —— 底部快捷符号条（( ) { } [ ] ; " ' = 等）。
            // 渲染分支（settings.symbolBar）早已存在，但默认 false 且没有开关，
            // 功能等于不可达；这里补上开关，默认仍保持关闭，不改变既有用户的观感。
            SwitchRow(Str.get(R.string.editor_symbol_bar), settings.symbolBar) {
                onChange(settings.copy(symbolBar = it))
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
                selected = settings.lnPanelPosition,
                description = Str.get(R.string.editor_ln_panel_pos_hint)
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
            // ---- 6.2.0：大文件省耗 —— 内容超阈值时关掉语法解析/粘性滚动/缩进线 ----
            // 用户手动选了语法仍会保留高亮（那等于「我明确要它」），故逃生门始终有效。
            SwitchRow(Str.get(R.string.editor_large_file_mode), settings.largeFileMode) {
                onChange(settings.copy(largeFileMode = it))
            }
            // 6.2.0 批次42：文件树名称多行显示 —— 关（默认）单行裁切，保证长文件名不把
            // 行尾 ⋮ 挤出屏幕；开则名称换多行、行高自适应。
            SwitchRow(Str.get(R.string.editor_tree_multi_line), settings.fileTreeMultiLine) {
                onChange(settings.copy(fileTreeMultiLine = it))
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
    // 注意参数顺序：**onSelect 必须是最后一个**，这样既有的 `SegmentedRow(...) { ... }`
    // 尾随 lambda 才会绑定到它。若把非函数型的 description 放最后，尾随 lambda 会被
    // 交给 description，报 "no value passed for parameter 'onSelect'"。
    description: String? = null,
    onSelect: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = AppDimens.spacingSmall)
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        description?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelSmall,
                color = AppColors.textSecondary()
            )
        }
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
    // 批次57②（用户指定）：插件向导草稿模式只显示草稿目录下的签 —— 用户没关的设备
    // 文件等隐藏而非关闭；向导结束（编辑器关闭）后会话恢复，原标签原样回来。
    val rawTabs by viewModel.tabs.collectAsState()
    val wizPrefix = viewModel.wizardDraftPrefix.collectAsState().value
    val tabs = wizPrefix?.let { p ->
        rawTabs.filter { it.file?.absolutePath?.startsWith(p) == true }
    } ?: rawTabs
    val activeTabId by viewModel.activeTabId.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val readOnly by viewModel.readOnly.collectAsState()
    // 批次55：按新编码重建分页索引中（只读互斥的可观察信号）—— 与 scanningActive 同理，
    // 重建期页偏移/文件字节在换代，编辑/翻页/替换/保存全都要挡。
    val rebuildingSet by viewModel.rebuildingCharset.collectAsState()
    val projectRoot by viewModel.projectRoot.collectAsState()
    // 6.2.0 批次27：语法+主题后台加载完毕后翻转，EditorCanvas 据此补设主题与语言（消除启动竞态）。
    val grammarsReady by TextMateInitState.grammarsReady.collectAsState()
    // 6.2.0 批次27：最近一次打开失败的原因（如 Android/data 系统保护目录），空状态用它替换「未打开文件」。
    val lastOpenError by viewModel.lastOpenError.collectAsState()
    // 6.2.0 批次47：全文搜索/替换状态（窗口模式）。
    val wholeSearch by viewModel.wholeSearch.collectAsState()
    val wholeReplaceUi by viewModel.wholeReplace.collectAsState()
    // 批次53：全量结果页（完整列表）与其懒加载预览缓存。
    val wholeFull by viewModel.wholeFull.collectAsState()
    val wholeFullPreviews by viewModel.wholePreviews.collectAsState()
    // 批次50：窗口渐进式打开的索引进度（tabId → 0..100），底部左下角显示。
    val windowScan by viewModel.windowScanProgress.collectAsState()
    // 批次51：保存进度（写盘 + 索引重建全程），底部左下角「保存中 N%」。
    val windowSave by viewModel.windowSaveProgress.collectAsState()

    val controller = remember { EditorController() }
    var cursorLine by remember { mutableStateOf(1) }
    var cursorColumn by remember { mutableStateOf(1) }
    var selectionLen by remember { mutableStateOf(0) }
    // 6.2.0 批次43：信息栏的总行数（光标/内容回调里重读 O(1) lineCount）。
    var totalLines by remember { mutableStateOf(0) }
    // 批次43：撤销/重做可用性 —— 撤销栈变化没有专用事件，凡光标动/内容变就 bump 一次，
    // 让顶栏按钮的 remember 键跟着重算。
    var historyTick by remember { mutableStateOf(0) }
    // 批次43：长按标签页 → 属性弹窗的目标文件（uri/未落盘标签 file=null → 不弹）。
    var tabPropertiesTarget by remember { mutableStateOf<java.io.File?>(null) }

    var showThemeDialog by remember { mutableStateOf(false) }
    var showSyntaxDialog by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    // 批次56（用户指定）：设置页滑入/滑出动画 —— mount 标志与可见性分离；
    // tick 每次「打开」+1，保证「关到一半立刻再开」也能重放入场动画。
    var settingsVisible by remember { mutableStateOf(false) }
    var settingsTick by remember { mutableStateOf(0) }
    // 批次56：顶栏「全部」下拉 → 关闭全部文件前的丢弃确认。
    var showCloseAllConfirm by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    // 批次57②（用户反馈）：搜索面板状态提升到 Screen —— 面板收起时整树卸载，面板内
    // remember 全丢，重开会以空查询触发「空输入即清结果」把已搜结果与高亮一起误清。
    // 提升后收起重开原样保留；清空只由面板关闭图标 X 显式执行（见 SearchPanel 调用点）。
    val searchState = remember { SearchPanelState() }
    // 6.2.0 批次32：顶栏「更多操作」菜单 + 跳转到行对话框。
    var showActionMenu by remember { mutableStateOf(false) }
    var showGotoDialog by remember { mutableStateOf(false) }
    // 6.2.0：底部状态栏「编码」选择器（Unicode/GBK 自动检测 + 手动指定）。
    var showCharsetPicker by remember { mutableStateOf(false) }
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
    // 批次50：索引扫描期间**强制只读** —— 页偏移/首页基线都还没就绪，此时编辑的内容
    // 容易与「索引完成后的首页校正」打架（用户文本 vs 精确页文本）。扫描完自动恢复可编辑。
    val scanningActive = activeTab?.let { windowScan[it.id] != null } == true
    // 复查：整档替换进行中也强制只读 —— 替换结束会用 spool/内存新文本整体重设内容，
    // 此间的编辑会被静默覆盖丢失。批次53：替换改为切标签不取消（后台跑完），只读必须
    // 按标签收窄 —— 否则 A 标签替换期间 B 标签也被误锁只读。
    val replacingActive = wholeReplaceUi?.running == true &&
        viewModel.wholeReplaceOwner == activeTab?.id
    // 批次55：重建互斥只针对活动标签（与 replacingActive 同款收窄）。
    val rebuildingActive = activeTab?.let { it.id in rebuildingSet } == true
    val readOnlyEff = readOnly || scanningActive || replacingActive || rebuildingActive
    // 6.2.0 批次54（用户指定）：默认主题 github-dark-dimmed，不再跟随系统深浅色
    // （系统未定义编辑器主题）。兜底只为兼容老配置里可能残留的空串。
    val effectiveTheme = settings.theme.ifEmpty { "github-dark-dimmed" }

    // 6.2.0 批次43：分页导航状态 —— 当前页由光标行推导（每页 [PAGE_LINES] 行，1 起）。
    // 批次46：窗口模式以**磁盘索引**为准 —— 光标行在窗口里是页内相对行号，推不出真实页码。
    val win = activeTab?.window
    val totalPages = win?.totalPages
        ?: if (totalLines <= 0) 1 else (totalLines + PAGE_LINES - 1) / PAGE_LINES
    val currentPage = win?.page
        ?: (((cursorLine - 1) / PAGE_LINES).coerceIn(0, totalPages - 1) + 1)
    // 撤销/重做可用性：键 = historyTick（回调里 bump）+ activeTabId（切标签换编辑器实例）。
    val canUndoOn = remember(historyTick, activeTabId) { controller.editor?.canUndo() == true }
    val canRedoOn = remember(historyTick, activeTabId) { controller.editor?.canRedo() == true }

    // 光标 / 内容回调接线（避免轮询）
    LaunchedEffect(controller) {
        controller.onCursorChange = { l, c, sel ->
            cursorLine = l
            cursorColumn = c
            selectionLen = sel
            // 批次43：光标一动就顺带刷新总行数（增删行后信息栏不滞后）+ 撤销栈状态。
            totalLines = controller.editor?.lineCount ?: totalLines
            historyTick++
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
    LaunchedEffect(activeTabId, activeTab?.contentRev, controller) {
        controller.onContentChange = { text ->
            val id = activeTabId
            val owner = controller.attachedTabId
            // 批次55：事件归属按编辑器**所挂标签**校验 —— 切签瞬间旧编辑器的迟到事件若按
            //「当下 activeTabId」记账，会把 A 的文本写进 B 的 content（串档）。owner 尚未
            // 挂签（null）时保持原行为；切走前 activateTab 已回灌过内容，丢迟到事件不丢改动。
            if (id != null && (owner == null || owner == id)) {
                if (text != null) viewModel.updateContent(id, text)
                // 批次54：大文档只置脏（内容不逐键复制，防 OOM），文本在消费入口回灌。
                else viewModel.markContentEdited(id)
            }
            // 批次43：编辑即 bump —— 撤销/重做可用性与总行数随之刷新。
            totalLines = controller.editor?.lineCount ?: totalLines
            historyTick++
        }
        // 批次54：大文档脏标签的文本回灌供给 —— 编辑只发生在活动标签（保存/搜索/替换/
        // 切走/翻页前都会经 VM 的 refreshStaleContent 回调到这里，取编辑器最新文本）。
        // 守卫命中即回灌、不再看长度：脏标记本身就来自超大文本；文本若已缩到很小，
        // 拷贝一份顺带把脏标记清掉，避免残留空转。
        viewModel.contentRefresher = { id ->
            val e = controller.editor
            if (e != null && id == activeTabId) {
                viewModel.updateContent(id, e.text.toString())
            }
        }
        // 批次43：切标签时 setText/首次选区是异步落地的 —— 立即取一次，400ms 后再补一次，
        // 顺带 bump historyTick（此时新编辑器的撤销栈才建好）。
        // 批次46：窗口模式翻页同理（key 里加 contentRev，换页后撤销栈重置为新页）。
        totalLines = controller.editor?.lineCount ?: 0
        delay(400)
        totalLines = controller.editor?.lineCount ?: totalLines
        historyTick++
    }

    BackHandler {
        // 6.2.0 修「设置页里按系统返回键直接退出编辑器」：返回键必须**由内向外**逐层收起
        // 当前打开的覆盖层，全部关完才轮到「关抽屉 → 未保存确认 → 退出」。
        // 原实现只有 抽屉/未保存/退出 三档，设置页与搜索面板一按返回就掉进 onExit()。
        // （UnifiedDialog / goto / 退出确认基于 Dialog 窗口，返回键由其自身窗口处理，无需在此收。）
        when {
            // 批次56：设置页关闭改走滑出动画（点返回按钮与系统返回一致），动画完才卸载。
            showSettings -> {
                settingsVisible = false
                val tick = settingsTick
                drawerScope.launch {
                    delay(280)
                    if (settingsTick == tick && !settingsVisible) showSettings = false
                }
            }
            showSearch -> showSearch = false
            showCharsetPicker -> showCharsetPicker = false
            showActionMenu -> showActionMenu = false
            drawerState.isOpen -> drawerScope.launch { drawerState.close() }
            viewModel.hasUnsaved() -> showExitConfirm = true
            else -> onExit()
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
                        multiLine = settings.fileTreeMultiLine,
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
                        // 批次45：标题整个移除 —— 右侧动作图标一多，标题槽被挤到零宽，
                        // 「代码编辑器」省略成纯文本的「…」（用户反馈的“最左边三个横点、
                        // 点不动”就是它）。文件名已在标签栏，顶栏不再显示任何标题。
                        Spacer(modifier = Modifier)
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
                        // 6.2.0 批次44：撤销/重做放到**动作区最左**（其他图标左侧），
                        // 收紧到 40dp（其余仍 48dp）。可用性由 historyTick（光标/内容回调
                        // bump）+ activeTabId 触发重算；不可用置灰但仍可点（与 ⋮ 菜单一致）。
                        // 批次50：readOnlyEff 含「索引扫描中」—— 扫描期连保存/撤销都不给。
                        if (!readOnlyEff) {
                            UnifiedIconButton(
                                icon = Icons.Default.Undo,
                                onClick = { if (canUndoOn) controller.editor?.undo() },
                                tint = if (canUndoOn) null
                                else AppColors.textSecondary().copy(alpha = 0.45f),
                                contentDescription = Str.get(R.string.editor_undo),
                                modifier = Modifier.size(40.dp)
                            )
                            UnifiedIconButton(
                                icon = Icons.Default.Redo,
                                onClick = { if (canRedoOn) controller.editor?.redo() },
                                tint = if (canRedoOn) null
                                else AppColors.textSecondary().copy(alpha = 0.45f),
                                contentDescription = Str.get(R.string.editor_redo),
                                modifier = Modifier.size(40.dp)
                            )
                        }
                        if (!readOnlyEff) {
                            // 短按=保存到原位置；长按=系统「另存为」（可另指定文件名 + 位置）。
                            UnifiedIconButton(
                                icon = Icons.Default.Save,
                                onClick = { viewModel.saveActive() },
                                onLongClick = { viewModel.requestSaveAsDoc() },
                                contentDescription = Str.get(R.string.editor_save),
                                // 批次45：与撤销/重做统一 40dp —— 图标间距同步收紧（24 → 16dp 视觉空隙）。
                                modifier = Modifier.size(40.dp)
                            )
                        }
                        // 批次56：顶部「全部」图标 —— 下拉：保存全部文件 / 关闭全部文件。
                        Box {
                            var allMenu by remember { mutableStateOf(false) }
                            UnifiedIconButton(
                                icon = Icons.Default.DoneAll,
                                onClick = { allMenu = true },
                                contentDescription = Str.get(R.string.editor_all),
                                modifier = Modifier.size(40.dp)
                            )
                            DropdownMenu(
                                expanded = allMenu,
                                onDismissRequest = { allMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(Str.get(R.string.editor_save_all)) },
                                    leadingIcon = { Icon(Icons.Default.Save, null) },
                                    onClick = {
                                        allMenu = false
                                        viewModel.saveAllDirty()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(Str.get(R.string.editor_close_all)) },
                                    leadingIcon = { Icon(Icons.Default.Close, null) },
                                    onClick = {
                                        allMenu = false
                                        if (viewModel.hasUnsaved()) showCloseAllConfirm = true
                                        else viewModel.closeAllTabs()
                                    }
                                )
                            }
                        }
                        UnifiedIconButton(
                            icon = Icons.Default.Search,
                            onClick = { showSearch = !showSearch },
                            contentDescription = Str.get(R.string.editor_find),
                            modifier = Modifier.size(40.dp)
                        )
                        // 6.2.0 批次16：右上角原「主题」图标改为「语法」图标。
                        // 主题只能在设置面板里选；这里改为手动为当前文件挑选高亮语法。
                        UnifiedIconButton(
                            icon = Icons.Default.DataObject,
                            onClick = { showSyntaxDialog = true },
                            contentDescription = Str.get(R.string.editor_syntax),
                            modifier = Modifier.size(40.dp)
                        )
                        // 6.2.0 批次32：「更多操作」菜单 —— 撤销/重做/剪贴板/缩进/格式化/跳转到行。
                        // DropdownMenu 必须锚定在一个父 Box 上，才能贴着 ⋮ 按钮弹出。
                        Box {
                            UnifiedIconButton(
                                icon = Icons.Default.MoreVert,
                                onClick = { showActionMenu = true },
                                contentDescription = Str.get(R.string.editor_more),
                                modifier = Modifier.size(40.dp)
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
                    readOnly = readOnlyEff,
                    // 编码标签：自动档显示「自动检测·实际编码」，选手动档显示所选编码。
                    // 6.2.0：底部**不再重复显示文件名**（顶栏/标签栏已有）。
                    charset = activeTab?.let { t ->
                        if (t.charset == FileAccess.CHARSET_AUTO) {
                            "${Str.get(R.string.editor_encoding_auto)}·${t.effectiveCharset}"
                        } else t.effectiveCharset
                    } ?: Str.get(R.string.editor_encoding_auto),
                    onPickCharset = { showCharsetPicker = true },
                    // 批次43：跳到页 → 光标落到页首行并滚到位（currentPage 由光标行推导，
                    // setSelection 触发光标回调后自动更新 n/N）。
                    // 批次46：窗口模式 = **真换页**（flush spool → 读新页 → contentRev 触发
                    // setText），编辑器内容跟着换；整档模式保持原「滚动定位」。
                    currentPage = currentPage,
                    totalPages = totalPages,
                    // 批次50：索引扫描中 → 左下角显示「扫描 N%」（此时 totalPages=0 本就不显示分页）。
                    scanPercent = activeTab?.let { t -> windowScan[t.id] },
                    savePercent = activeTab?.let { t -> windowSave[t.id] },
                    onJumpPage = { p ->
                        val t = activeTab
                        if (t?.window != null) {
                            viewModel.jumpWindowPage(t.id, p)
                        } else controller.editor?.let { e ->
                            val line = ((p - 1) * PAGE_LINES)
                                .coerceIn(0, (e.lineCount - 1).coerceAtLeast(0))
                            // 批次54：与「跳转到行」对话框同一条已验证路径 —— jumpToLine
                            // 落光标到页首行，底部 n/N 随光标回调同步到目标页。
                            e.jumpToLine(line)
                            e.ensureSelectionVisible()
                        }
                    },
                    onOpenSettings = { settingsTick++; showSettings = true }
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
                            },
                            // 批次43：长按标签 → 完整属性弹窗；uri/未落盘标签没有本地文件，不弹。
                            onLongPress = { tab -> tab.file?.let { tabPropertiesTarget = it } }
                        )
                        // 批次43：标签栏下方信息栏 —— n/N行 · n字节 · n字符 · 选中n个。
                        // 批次46：窗口模式行号换算为**绝对行**（页码 × 页宽 + 页内行），
                        // 总行数取磁盘索引；带「窗口模式」徽标提示语义差异。
                        EditorInfoBar(
                            // 批次56：窗口档绝对行按真实行基址（分页行数不等后
                            //「页号×页宽」会错位）；非窗口无基址概念，页内行即绝对行。
                            line = win?.let { w ->
                                (viewModel.absLineBase(activeTab?.id ?: "", w.page - 1)
                                    ?: ((w.page - 1) * PAGE_LINES)) + cursorLine
                            } ?: cursorLine,
                            totalLines = win?.totalLines ?: totalLines,
                            bytes = activeTab?.file?.length(),
                            chars = activeTab?.content?.length ?: 0,
                            selection = selectionLen,
                            windowed = win != null
                        )
                    }
                    // 6.2.0 批次57（用户）：**搜索结果在编辑器内高亮** —— 当前标签的全部命中
                    // 涂主题 findMatch 色（见 MatchHighlights）。键里刻意不含 contentRev：
                    // 编辑期间不重算，由 sora 的 updateOnInsertion/Deletion 把已画条目随内容
                    // 自动平移（不回跳）；「内容已修改」横幅照常提示过期，重搜/换页/切标签
                    // 才换新批次。editor 入键是因为 factory 重建编辑器后要立即补画。
                    // 批次57②复查（用户反馈「编辑后高亮偏移/旁边空格被涂」）：键只取
                    // **matches 列表引用**，不取整个 ui 对象 —— 「内容已修改」的 stale 标记
                    // 与进度更新都是 whole 对象的 copy（matches 引用不变），键整个对象会让
                    // 每次编辑都触发一次用**过期行/列**的重画：高亮被拽回编辑前位置（偏移）、
                    // 刚输入的字符被算进旧区间（旁边的空格被涂）。列表引用只在「新一轮搜索
                    // 出结果 / 结果被清空」时变化（startWholeSearch 以 emptyList() 单例起头），
                    // 编辑期间一律靠 sora 的 afterInsert/afterDelete 自动平移。
                    LaunchedEffect(
                        controller.editor,
                        wholeSearch?.matches,
                        wholeFull?.matches,
                        activeTab?.id,
                        win?.page,
                        viewModel.wholeSearchOwner
                    ) {
                        val mh = controller.matchHighlights ?: return@LaunchedEffect
                        val owner = viewModel.wholeSearchOwner
                        // 结果是全局单例：只属于其发起标签，切走即清（与面板同一门槛）。
                        if (owner == null || owner != activeTab?.id) {
                            mh.clear()
                            return@LaunchedEffect
                        }
                        // 全量列表优先（批次57 起搜索一趟收齐）；未就绪时退回面板前 500 条。
                        val src = wholeFull?.matches?.takeIf { it.isNotEmpty() }
                            ?: wholeSearch?.matches
                            ?: emptyList()
                        val page = win?.page
                        val items = ArrayList<HighlightTextContainer.HighlightText>()
                        for (m in src) {
                            // 窗口档只画当前页（编辑器里只有这一页的文本）；行号口径与跳转一致。
                            val line = if (page != null) {
                                if (m.page != page) continue
                                m.inPageLine
                            } else {
                                m.absLine - 1
                            }
                            // 0 长度匹配只是定位点、无区间可涂；越界列由 sora 绘制时自行夹住。
                            if (line < 0 || m.length <= 0) continue
                            items.add(MatchHighlights.matchItem(line, m.column, m.length))
                            if (items.size >= MatchHighlights.MAX_ITEMS) break
                        }
                        if (items.isEmpty()) mh.clear() else mh.apply(items)
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
                            state = searchState,
                            // 批次57②（用户指定）：只有面板关闭图标 X 才清除结果与高亮
                            //（cancelWholeSearch 清空 whole/wholeFull → 高亮 effect 随之
                            // 清画）；顶栏搜索图标/返回键只是收起，结果与高亮原样保留。
                            onClose = {
                                showSearch = false
                                viewModel.cancelWholeSearch()
                            },
                            // ---- 批次53：搜索/替换一律整档（「全文」开关已删）；
                            // 非窗口文档的替换走 VM 内存整写，确认文案按此切换。
                            inMemoryReplace = win == null,
                            // 复查：whole 状态是全局单例 —— 只展示**当前标签**的结果
                            //（切标签后旧结果不得再显示/跳转，防错位与越界）；tabKey
                            // 让防抖效果在切标签后用同一查询对新标签重搜。
                            tabKey = activeTab?.id ?: "",
                            whole = wholeSearch?.takeIf { viewModel.wholeSearchOwner == activeTab?.id },
                            wholeReplace = wholeReplaceUi?.takeIf { viewModel.wholeReplaceOwner == activeTab?.id },
                            // 批次53：全量结果页接线（完整列表 + 懒加载预览；批次57 起搜索即全量收齐）。
                            full = wholeFull?.takeIf { viewModel.wholeSearchOwner == activeTab?.id },
                            fullPreviews = wholeFullPreviews,
                            onLoadPreviews = { from, count ->
                                viewModel.requestWholePreviews(from, count)
                            },
                            onWholeSearch = { q, c, w, r ->
                                activeTab?.let { t ->
                                    viewModel.startWholeSearch(t.id, q, c, w, r)
                                }
                            },
                            onCancelWhole = { viewModel.cancelWholeSearch() },
                            onWholeReplace = { q, c, w, r, rep ->
                                activeTab?.let { t ->
                                    viewModel.startWholeReplace(t.id, q, c, w, r, rep)
                                }
                            },
                            onJumpMatch = { m ->
                                activeTab?.let { t ->
                                    // 批次49：非窗口大文件（单页整读扫描）—— 无需换页，
                                    // absLine 即全文行号（1 起），直接在编辑器里选中定位。
                                    // 复查：结果可能因编辑过期 —— 行/列全部夹到当前文档
                                    // 边界再定位（越界 setSelectionRegion 会直接崩）。
                                    if (t.window == null) {
                                        controller.editor?.let { e ->
                                            val maxLine = (e.lineCount - 1).coerceAtLeast(0)
                                            val line0 = (m.absLine - 1).coerceIn(0, maxLine)
                                            val maxCol = e.text.getLine(line0).length
                                            val col0 = m.column.coerceIn(0, maxCol)
                                            val endCol = (col0 + m.length).coerceIn(col0, maxCol)
                                            if (endCol > col0) {
                                                e.setSelectionRegion(line0, col0, line0, endCol)
                                            } else {
                                                e.setSelection(line0, col0)
                                            }
                                            e.ensurePositionVisible(line0, col0, true)
                                            e.requestFocus()
                                        }
                                        return@let
                                    }
                                    if (t.window?.page == m.page) {
                                        // 同页命中：无需换页，直接选中高亮匹配文本（同样夹界）。
                                        controller.editor?.let { e ->
                                            val maxLine = (e.lineCount - 1).coerceAtLeast(0)
                                            val line0 = m.inPageLine.coerceIn(0, maxLine)
                                            val maxCol = e.text.getLine(line0).length
                                            val col0 = m.column.coerceIn(0, maxCol)
                                            val endCol = (col0 + m.length).coerceIn(col0, maxCol)
                                            if (endCol > col0) {
                                                e.setSelectionRegion(line0, col0, line0, endCol)
                                            } else {
                                                e.setSelection(line0, col0)
                                            }
                                            e.ensurePositionVisible(line0, col0, true)
                                            e.requestFocus()
                                        }
                                    } else {
                                        // 跨页命中：换页落地后再选中（消费点已夹界，见 EditorCanvas）。
                                        controller.pendingSelection =
                                            EditorController.PendingSel(m.inPageLine, m.column, m.length)
                                        if (!viewModel.jumpWindowPage(t.id, m.page)) {
                                            controller.pendingSelection = null
                                        }
                                    }
                                }
                            }
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
                                // 批次50：扫描期只读（setEditable(false) 拦键盘/输入法）。
                                readOnly = readOnlyEff,
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
                    if (settings.symbolBar && !readOnlyEff) {
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
            onDismiss = { showGotoDialog = false },
            // 批次46：窗口标签按绝对行号跳 —— 目标页不在当前页时先切页、
            // 新文本落地后再定位（pendingSelection 由 EditorCanvas 消费）。
            windowedMaxLine = win?.totalLines?.takeIf { it > 0 },
            onWindowedGoto = activeTab?.let { t ->
                { absLine: Int ->
                    val w = t.window
                    if (w != null) {
                        // 批次56：行→页按前缀和二分定位 —— 分页行数不等后，
                        //「除以页宽」会把目标页算错、页内行跟着错位。
                        val page = viewModel.pageForAbsLine(t.id, absLine)
                            ?: ((absLine - 1) / PAGE_LINES + 1)
                        val base = viewModel.absLineBase(t.id, page - 1)
                            ?: ((page - 1) * PAGE_LINES)
                        val within = (absLine - 1 - base).coerceAtLeast(0)  // 0 基页内行
                        if (page == w.page) {
                            controller.editor?.let { e ->
                                e.jumpToLine(within)
                                e.ensureSelectionVisible()
                                e.requestFocus()
                            }
                        } else {
                            controller.pendingSelection = EditorController.PendingSel(within, 0)
                            if (!viewModel.jumpWindowPage(t.id, page)) {
                                controller.pendingSelection = null
                            }
                        }
                    }
                }
            }
        )
    }

    // 6.2.0：底部状态栏「编码」按钮 → 编码选择器。
    if (showCharsetPicker) {
        EncodingSelectionDialog(
            currentCharset = activeTab?.charset ?: FileAccess.CHARSET_AUTO,
            detectedCharset = activeTab?.effectiveCharset ?: "UTF-8",
            onSelect = { name ->
                activeTab?.let { viewModel.setTabCharset(it.id, name) }
                showCharsetPicker = false
            },
            onDismiss = { showCharsetPicker = false }
        )
    }

    if (showSettings) {
        // 批次56（用户指定）：设置页**滑入/滑出**动画 —— 进出都走水平滑动 + 淡入淡出；
        // 滑出动画放完才卸载（否则一闪即逝）。点「返回」与系统返回键走同一收尾。
        val settingsScope = rememberCoroutineScope()
        LaunchedEffect(settingsTick) { settingsVisible = true }
        val dismissSettings: () -> Unit = {
            settingsVisible = false
            val tick = settingsTick
            settingsScope.launch {
                delay(280)
                if (settingsTick == tick && !settingsVisible) showSettings = false
            }
        }
        AnimatedVisibility(
            visible = settingsVisible,
            enter = slideInHorizontally(animationSpec = tween(280)) { it } +
                fadeIn(animationSpec = tween(280)),
            exit = slideOutHorizontally(animationSpec = tween(280)) { it } +
                fadeOut(animationSpec = tween(280))
        ) {
            SettingsSheet(
                settings = settings,
                onChange = { viewModel.updateSettings { _ -> it } },
                onOpenThemePicker = {
                    settingsVisible = false
                    showSettings = false
                    showThemeDialog = true
                },
                onDismiss = dismissSettings
            )
        }
    }

    // 6.2.0 批次43：长按标签页 → 完整属性（与文件树 ⋮「属性」共用 FilePropertiesDialog）。
    tabPropertiesTarget?.let { file ->
        FilePropertiesDialog(file = file, onDismiss = { tabPropertiesTarget = null })
    }

    if (showCloseAllConfirm) {
        UnifiedConfirmDialog(
            title = Str.get(R.string.editor_unsaved_title),
            message = Str.get(R.string.editor_close_all_confirm),
            isDestructive = true,
            confirmText = Str.get(R.string.editor_close_all),
            dismissText = Str.get(R.string.cancel),
            onConfirm = {
                showCloseAllConfirm = false
                viewModel.closeAllTabs()
            },
            onDismiss = { showCloseAllConfirm = false }
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
                // 批次55：保存成功才关标签；窗口档/SAF 的异步保存由 VM 推迟到完成回调兑现。
                viewModel.saveAndClose(id)
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
                // 批次55：保存**全部**脏标签（原 saveActive 只存活动标签，其余静默丢失）。
                viewModel.saveAllDirty()
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
    // 大文件省耗：内容超阈值 + 用户开启开关 → 关掉语法解析/粘性滚动/缩进线。
    // 用户手动选了语法（languageScope != null）视为「明确要高亮」，不强制纯文本。
    val largeActive = settings.largeFileMode && isLargeContent(tab)
    val forcePlain = largeActive && tab.languageScope == null
    // 大文件省耗（随开关，可整体关闭）：十六进制色提示的扫描行数上限。
    val hexCap = if (settings.largeFileMode) HexColorHints.MAX_LINES else Int.MAX_VALUE
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
                // 批次54（用户反馈「搜索后无法编辑文本，点搜索结果/关面板才恢复」）：
                // Compose 搜索框持焦时 sora 不会在触摸里抢焦点，点编辑器拿不回输入焦点，
                // 软键盘仍进搜索框 —— 触摸即还焦；返回 false 不消费，事件照常进 sora。
                setOnTouchListener { v, _ ->
                    if (!v.isFocused) v.requestFocus()
                    false
                }
                controller.attach(this)
                applyRuntimeSettings(this, settings, largeActive)
                // sora 官方示例明确要求：**先**应用 TextMateColorScheme，**再**设置语言
                // （见 demo MainActivity：「Before using Textmate Language, TextmateColorScheme should be applied」）。
                applyTheme(this, effectiveTheme)
                setEditorLanguage(this, tab.name, tab.languageScope, forcePlain)
                controller.attachedTabId = tab.id
                controller.attachedContentRev = tab.contentRev
                controller.attachedTheme = effectiveTheme
                controller.attachedLangScope = tab.languageScope
                controller.attachedSettings = settings
                controller.attachedEditable = !readOnly
                controller.attachedGrammarReady = grammarsReady
                controller.attachedForcePlain = forcePlain
                setText(tab.content)
                post { rerunAnalysis(); invalidate() }
                // 6.2.0 批次30/31：十六进制颜色**背景**（色值文本本身涂成对应色）。必须在 setText
                // 之后创建，这样 attach 的首次扫描就能对已有内容产出背景。
                controller.matchHighlights = MatchHighlights(this)
                controller.hexHints = HexColorHints(this).also {
                    it.maxLines = hexCap
                    it.attach()
                }
                controller.attachedHexCap = hexCap
                logHighlightDiagnostics(this, "factory", tab.languageScope ?: getLanguageScopeName(tab.name))
            }
        },
        update = { editor ->
            // 仅在设置真正变化时应用，避免每次按键重组合都触发 relayout
            if (controller.attachedSettings != settings) {
                applyRuntimeSettings(editor, settings, largeActive)
                controller.attachedSettings = settings
            }
            if (controller.attachedHexCap != hexCap) {
                // 6.2.0 大文件省耗：开关切换 → 重设扫描上限并立即补扫/清空。
                controller.attachedHexCap = hexCap
                controller.hexHints?.let { it.maxLines = hexCap; it.refresh() }
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
                controller.attachedContentRev = tab.contentRev
                controller.attachedLangScope = tab.languageScope
                controller.attachedForcePlain = forcePlain
                setEditorLanguage(editor, tab.name, tab.languageScope, forcePlain)
                if (tab.content.length > FileAccess.LARGE_FILE_OPTIMIZE_CHARS) {
                    // 6.2.0 批次41：切回大文档前显式等一次回收 —— 栈里可能还躺着上一份
                    // 换下未久的大文档（200MB+ 的行结构），不等它回收就重建会双份驻留，
                    // 直接把堆顶穿（2026-10-17 21:59:52 实测：小/大标签来回切换后
                    // OutOfMemoryError 崩溃）。只在切到超 200 万字符的大文档时付这 ~60ms。
                    System.gc()
                    Thread.sleep(60)
                }
                editor.setText(tab.content)
                editor.post { editor.rerunAnalysis(); editor.invalidate() }
                // setEditorLanguage 清了背景；setText 虽派发 ContentChangeEvent 会延迟重建，这里立即补。
                controller.restoreDecorations()
                controller.pendingSelection?.let { sel ->
                    // 复查：setText 落地后内容可能已变（换页/切标签）—— 行/列夹到新文本
                    // 边界再定位（越界 setSelectionRegion 会直接崩）。
                    val maxLine = (editor.lineCount - 1).coerceAtLeast(0)
                    val line0 = sel.line.coerceIn(0, maxLine)
                    val maxCol = editor.text.getLine(line0).length
                    val col0 = sel.col.coerceIn(0, maxCol)
                    if (sel.length > 0 && col0 + sel.length <= maxCol) {
                        // 0.24.6 无 4 参 setSelection —— 选区正门是 setSelectionRegion。
                        editor.setSelectionRegion(line0, col0, line0, col0 + sel.length)
                    } else {
                        editor.setSelection(line0, col0)
                    }
                    editor.ensurePositionVisible(line0, col0, true)
                    controller.pendingSelection = null
                }
            } else if (controller.attachedContentRev != tab.contentRev) {
                // 6.2.0 批次46：**窗口模式翻页** —— 同标签页内容整体换页，按版本号重设文本。
                // setText 会把光标/历史栈重置到新文本（撤销也只作用于本页，见 Help 18.4）。
                controller.attachedContentRev = tab.contentRev
                editor.setText(tab.content)
                editor.setSelection(0, 0)
                editor.ensurePositionVisible(0, 0, true)
                controller.pendingSelection?.let { sel ->
                    // 复查：setText 落地后内容可能已变（换页/切标签）—— 行/列夹到新文本
                    // 边界再定位（越界 setSelectionRegion 会直接崩）。
                    val maxLine = (editor.lineCount - 1).coerceAtLeast(0)
                    val line0 = sel.line.coerceIn(0, maxLine)
                    val maxCol = editor.text.getLine(line0).length
                    val col0 = sel.col.coerceIn(0, maxCol)
                    if (sel.length > 0 && col0 + sel.length <= maxCol) {
                        // 0.24.6 无 4 参 setSelection —— 选区正门是 setSelectionRegion。
                        editor.setSelectionRegion(line0, col0, line0, col0 + sel.length)
                    } else {
                        editor.setSelection(line0, col0)
                    }
                    editor.ensurePositionVisible(line0, col0, true)
                    controller.pendingSelection = null
                }
                editor.post { editor.rerunAnalysis(); editor.invalidate() }
                controller.restoreDecorations()
            } else if (controller.attachedLangScope != tab.languageScope) {
                // 6.2.0 批次16：同一标签页内手动切换语法（右上角语法选择器）时，
                // tab.id 没变，需单独检测 languageScope 变化并重设语言。
                controller.attachedLangScope = tab.languageScope
                controller.attachedForcePlain = forcePlain
                setEditorLanguage(editor, tab.name, tab.languageScope, forcePlain)
                editor.post { editor.rerunAnalysis(); editor.invalidate() }
                controller.restoreDecorations()
            } else if (controller.attachedForcePlain != forcePlain) {
                // 6.2.0 大文件省耗：开关切换或内容跨过阈值时，forcePlain 变了要重设语言
                // （在纯文本与真实语法间切换）。前两个分支已覆盖「换了文件/换了语法」的情形。
                controller.attachedForcePlain = forcePlain
                setEditorLanguage(editor, tab.name, tab.languageScope, forcePlain)
                editor.post { editor.rerunAnalysis(); editor.invalidate() }
                controller.restoreDecorations()
            }
            // 6.2.0 批次27：语法与主题在后台加载（1.8~4s）。启动瞬间 ACTION_VIEW 打开的文件
            // 会因语法未注册回退成 EmptyLanguage（无高亮），且 factory 里 applyTheme 时主题
            // 尚未加载、colorScheme 是回退值。这里在两者就绪后补设一次主题 + 语言，高亮才可见。
            if (grammarsReady && !controller.attachedGrammarReady) {
                controller.attachedGrammarReady = true
                applyTheme(editor, effectiveTheme)
                setEditorLanguage(editor, tab.name, tab.languageScope, forcePlain)
                editor.post { editor.rerunAnalysis(); editor.invalidate() }
                controller.restoreDecorations()
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
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 6.2.0：符号条加高 + 触控区统一为 44×44dp。
        // 原来是 Text 直接吃 padding（约 30dp 宽），手指容易点偏到隔壁符号；
        // 现在每个符号都装进等宽等高 Box，点哪都是一整个独立热区。
        COMMON_SYMBOLS.forEach { sym ->
            Box(
                modifier = Modifier
                    .width(44.dp)
                    .height(44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onInsert(sym) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = sym,
                    style = MaterialTheme.typography.bodyLarge,
                    fontFamily = FontFamily.Monospace,
                    color = AppColors.textSecondary()
                )
            }
        }
    }
}
