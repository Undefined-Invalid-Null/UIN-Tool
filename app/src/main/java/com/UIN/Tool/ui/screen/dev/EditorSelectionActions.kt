// app/src/main/java/com/UIN/Tool/ui/screen/dev/EditorSelectionActions.kt
package com.UIN.Tool.ui.screen.dev

import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.selection.SelectionContainer
import com.UIN.Tool.R
import com.UIN.Tool.ui.components.unified.UnifiedDialog
import com.UIN.Tool.ui.components.unified.UnifiedDialogTextButton
import com.UIN.Tool.ui.components.unified.UnifiedListDialog
import com.UIN.Tool.ui.theme.AppColors
import com.UIN.Tool.ui.theme.AppDimens
import com.UIN.Tool.utils.AppToast
import com.UIN.Tool.utils.Str
import io.github.rosemoe.sora.R as SoraR
import io.github.rosemoe.sora.event.SelectionChangeEvent
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.component.EditorTextActionWindow
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import kotlin.math.min

// ============================================================
// 批次63（用户「增加代码编辑器选中菜单的功能」）：
// 选中菜单 = sora 的浮动选中窗（剪切/复制/粘贴那个），新增两枚按钮：
//   ① 打开链接 —— 从选区自动识别 URL，单个直接调浏览器、多个弹选择框；
//   ② 翻译 —— 弹「选择翻译方式」（必应翻译＝bing-translate-api 移植 / 系统翻译＝
//              列出可处理文本的应用），必应译文在结果弹窗里可复制。
// ============================================================

// ==================== 链接识别 / 打开 ====================

/** `http(s)://…` 直链（到空白或引号等 URL 结束符为止）。 */
private val URL_PATTERN = Regex("https?://[^\\s\"'<>\\[\\]{}()]+", RegexOption.IGNORE_CASE)

/** 无协议的裸域名（`example.com/a?b=1`），识别后补 `https://`。 */
private val BARE_DOMAIN =
    Regex("^[a-z0-9]+([-.][a-z0-9]+)+(:\\d{1,5})?(/[^\\s]*)?$", RegexOption.IGNORE_CASE)

/**
 * 从选中文本里抽取要交给浏览器的链接。顺序：
 * 1. 所有 `http(s)://` 直链（剥掉句尾标点 —— `…com.` 的句号不属于 URL）；
 * 2. 一个都没有时，若整段选区就是一个裸域名，补 `https://` 当作链接。
 *
 * 返回去重后的列表（顺序保留），空列表 = 没识别到。
 */
fun findLinks(text: String): List<String> {
    val out = LinkedHashSet<String>()
    URL_PATTERN.findAll(text).forEach { m ->
        out.add(m.value.trimEnd('.', ',', ';', ':', '!', '?', ')', ']', '}', '"', '\''))
    }
    if (out.isEmpty()) {
        val t = text.trim()
        if (BARE_DOMAIN.matches(t)) out.add("https://$t")
    }
    return out.toList()
}

/**
 * 调系统浏览器打开 [url]。**只放行 http/https** —— `intent://`、`javascript:`、`file://`
 * 之类一律拒绝（与插件 JS 桥 openUrl 同一条白名单口径，见 PluginJSInterface C-15）。
 */
fun openLink(context: Context, url: String): Boolean {
    val scheme = url.substringBefore(':').lowercase()
    if (scheme != "http" && scheme != "https") {
        AppToast.show(context, Str.get(R.string.unable_to_open_link))
        return false
    }
    return try {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        true
    } catch (e: Exception) {
        AppToast.show(context, Str.get(R.string.unable_to_open_link))
        false
    }
}

/**
 * 链接识别结果的统一分派（选中窗按钮与顶栏菜单共用）：
 * 0 个 → 提示没识别到；1 个 → 直接调浏览器打开；多个 → 交给 [onPick] 弹选择框。
 */
fun dispatchOpenLink(context: Context, text: String, onPick: (List<String>) -> Unit) {
    val links = findLinks(text)
    when {
        links.isEmpty() ->
            AppToast.show(context, Str.get(R.string.editor_select_action_no_link))
        links.size == 1 -> openLink(context, links.first())
        else -> onPick(links)
    }
}

// ==================== 系统翻译（ACTION_PROCESS_TEXT） ====================

/** 一个能处理「处理文本」意图的翻译应用（系统翻译入口）。 */
data class SystemTranslateApp(val label: String, val packageName: String, val activityName: String)

/**
 * 列出设备上可用的系统翻译应用：`ACTION_PROCESS_TEXT` + `text/plain` 是 Android 的
 * 标准「选中文字 → 交给别的应用处理」通道，谷歌翻译等都会注册它。
 * 过滤掉自己（本应用不注册该 intent，纯兜底）。
 */
fun querySystemTranslateApps(context: Context): List<SystemTranslateApp> {
    val probe = Intent(Intent.ACTION_PROCESS_TEXT).apply { type = "text/plain" }
    val pm = context.packageManager
    val list: List<ResolveInfo> = runCatching { pm.queryIntentActivities(probe, 0) }
        .getOrDefault(emptyList())
    return list
        .filter { it.activityInfo?.packageName != context.packageName }
        .map {
            SystemTranslateApp(
                label = it.loadLabel(pm).toString(),
                packageName = it.activityInfo?.packageName ?: "",
                activityName = it.activityInfo?.name ?: ""
            )
        }
        .filter { it.packageName.isNotEmpty() && it.activityName.isNotEmpty() }
        .distinctBy { it.packageName + "/" + it.activityName }
        .sortedBy { it.label.lowercase() }
}

/** 把选中文本交给所选系统翻译应用（READONLY，只译不改）。 */
private fun launchSystemTranslate(context: Context, app: SystemTranslateApp, text: String) {
    val intent = Intent(Intent.ACTION_PROCESS_TEXT).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_PROCESS_TEXT, text)
        putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, true)
        setClassName(app.packageName, app.activityName)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent) }
        .onFailure {
            AppToast.show(context, Str.get(R.string.unable_to_open_link))
        }
}

// ==================== 选中菜单组件（sora 子类） ====================

/**
 * 给 sora 内置选中窗追加「打开链接」「翻译」两枚按钮。
 *
 * 实现要点：
 * - sora 的按钮字段是 `private final`，但布局结构固定（`panel_root > panel_hv > 行`），
 *   照着已有的「复制」按钮克隆两枚塞进行尾即可；`updateBtnState()` 每次显示都会重新
 *   measure，所以新增按钮会被自动计入宽度；
 * - 宽度上限 230dp 是父类私有的（5×45dp=225dp 刚好），新增两枚会被裁 —— 在
 *   [displayWindow] 里按实测内容宽放宽（上限 420dp，超宽由 HorizontalScrollView 兜底）；
 * - 图标颜色由父类 `applyColorScheme()` 的 `applyColorFilter` 叠加主题色，这里跟随调用；
 * - 没有选区时两枚按钮隐藏；选区里没识别到 URL 时**连「打开链接」一起藏**（父类
 *   `onSelectionChange` 是 protected，正好覆盖）。
 *
 * @param onPickLink 选区里识别到 ≥2 个链接时回调（1 个直接开浏览器、0 个提示），
 *                   由界面层弹选择框
 * @param onTranslate 点「翻译」回调选中文本，由界面层弹翻译流程
 */
class SelectionActionWindow(
    editor: CodeEditor,
    private val onPickLink: (List<String>) -> Unit,
    private val onTranslate: (String) -> Unit
) : EditorTextActionWindow(editor) {

    /**
     * 编辑器引用 —— 两道坑叠在一起，**都**决定了不能写成主构造器 `private val editor`：
     * ① 父类 `EditorPopupWindow` 已有 `public CodeEditor getEditor()`，同名属性会生成
     *    同一 JVM 签名 → K2 报 `Accidental override`（实机构建踩过，故另取名）；
     * ② 父类构造器里就会回调 [applyColorScheme]（虚方法 → 走到本类覆盖），而 Kotlin
     *    构造属性要等 super() 返回之后才赋值，那一刻读它必 NPE。
     * 因此：可空字段 [host] 在 init 首行回填、经 [hostEditor] 读取；[applyColorScheme]
     * 里判空跳过（父类构造期调到时尚未绑定，本来也没按钮要上色）。
     */
    private var host: CodeEditor? = null
    private var linkBtn: ImageButton? = null
    private var translateBtn: ImageButton? = null

    /**
     * 6.2.0 批次65（用户「悬浮菜单会浮在所有界面上，甚至打开设置页也会显示」）：
     * **覆盖层拉黑开关** —— 设置页 / 侧滑抽屉这类同窗口覆盖层开着时置 true，
     * 本弹窗拒绝显示（[displayWindow] 直接收起）；由界面层在覆盖层开合时写。
     */
    @Volatile
    var overlayBlocked: Boolean = false

    /**
     * 编辑器视口变化监听：开关符号栏、搜索面板展开收起、覆盖层进出都会改变编辑器的
     * 高度/位置，而父类只在滚动时重算坐标（`FEATURE_DISMISS_WHEN_OBSCURING_CURSOR`
     * 未启用、没挂布局监听）—— 弹窗留在**旧窗口坐标**上，于是悬浮到新挤进来的控件
     * （底部符号栏 / 分页栏）上面。这里补挂监听：开着就按新边界重挂，拉黑就收起。
     * 声明必须在 init 之前（Kotlin 属性按声明序初始化）；组件被 `replaceComponent`
     * 替换时父类会 `setEnabled(false)`，在 [setEnabled] 里摘除，不留悬挂监听。
     */
    private val layoutListener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
        onEditorBoundsChanged()
    }
    private var layoutWatchAttached = false

    private fun onEditorBoundsChanged() {
        val ed = host ?: return
        if (!isEnabled) return  // 已被替换的旧组件：不许把自己再弹出来
        if (overlayBlocked) {
            dismiss()
            return
        }
        if (!isShowing) return
        if (ed.cursor?.isSelected != true) {
            dismiss()
            return
        }
        // 布局还在连续变化（展开/收起动画进行中），等稳定后再按新边界重挂。
        ed.post {
            if (isEnabled && !overlayBlocked && isShowing && ed.cursor?.isSelected == true) {
                displayWindow()
            }
        }
    }

    private val hostEditor: CodeEditor
        get() = host ?: error("SelectionActionWindow: editor not attached yet")

    init {
        host = editor
        val root = getView()
        val template = root.findViewById<ImageButton>(SoraR.id.panel_btn_copy)
        val row = template.parent as ViewGroup
        linkBtn = makeButton(
            template, R.drawable.ic_selection_link,
            hostEditor.context.getString(R.string.editor_open_link)
        ).also { row.addView(it) }
        translateBtn = makeButton(
            template, R.drawable.ic_selection_translate,
            hostEditor.context.getString(R.string.editor_translate)
        ).also { row.addView(it) }
        // 构造期父类已经调过一次 applyColorScheme（那时还没按钮），这里补一次上色。
        applyColorScheme()
        // 批次65：挂视口监听（此时 host 已就位）。
        editor.addOnLayoutChangeListener(layoutListener)
        layoutWatchAttached = true
    }

    /** 组件被替换 / 编辑器释放时父类走这里 —— 顺手摘掉布局监听，防止旧实例滞留回调。 */
    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        val ed = host ?: return
        if (enabled && !layoutWatchAttached) {
            ed.addOnLayoutChangeListener(layoutListener)
            layoutWatchAttached = true
        } else if (!enabled && layoutWatchAttached) {
            ed.removeOnLayoutChangeListener(layoutListener)
            layoutWatchAttached = false
        }
    }

    private fun makeButton(template: ImageButton, icon: Int, desc: String): ImageButton =
        ImageButton(hostEditor.context).apply {
            layoutParams = ViewGroup.LayoutParams(
                template.layoutParams.width,
                template.layoutParams.height
            )
            scaleType = template.scaleType
            setImageResource(icon)
            background = template.background?.constantState?.newDrawable()?.mutate()
            contentDescription = desc
            setOnClickListener(this@SelectionActionWindow)
        }

    /** 跟随主题给新按钮上色（父类给内置按钮上完色后回调到这里）。 */
    override fun applyColorScheme() {
        super.applyColorScheme()
        val ed = host ?: return  // 父类构造期先调到这里，子类还没绑定，直接跳过
        val color = ed.colorScheme.getColor(EditorColorScheme.TEXT_ACTION_WINDOW_ICON_COLOR)
        linkBtn?.let { applyColorFilter(it, color) }
        translateBtn?.let { applyColorFilter(it, color) }
    }

    /**
     * 按选区内容显隐（批次63 追加要求「**没识别到链接就直接不显示**打开链接图标」）：
     * 无选区 → 两枚都藏；有选区但里面没有 URL → 只藏「打开链接」，翻译照常出现。
     * 开头的判空同 [applyColorScheme]：父类构造期若先回调到这里，子类还没绑定编辑器，
     * 跳过即可。
     */
    override fun onSelectionChange(event: SelectionChangeEvent) {
        super.onSelectionChange(event)
        if (host == null) return
        val sel = selectedText()
        // 用 isNullOrBlank 的契约分支拿非空 sel（else 分支里编译器才认定 sel != null）。
        val links = if (sel.isNullOrBlank()) emptyList() else findLinks(sel)
        linkBtn?.visibility = if (links.isEmpty()) View.GONE else View.VISIBLE
        translateBtn?.visibility = if (sel.isNullOrBlank()) View.GONE else View.VISIBLE
    }

    override fun displayWindow() {
        // 批次65：设置页 / 抽屉等同窗口覆盖层开着时拒绝弹出（父类事件回调也会走到这里，
        // 只在界面层 dismiss 一次挡不住后续的 displayWindow 请求）。
        if (overlayBlocked) {
            dismiss()
            return
        }
        super.displayWindow()
        // 父类把窗宽封顶 230dp（本为 5×45dp=225dp 设计），新增两枚会被裁掉；
        // super 已按内容实测宽居中定位，这里把窗宽提到实测宽（上限 420dp）即可对齐。
        val full = getView().measuredWidth
        if (full > 0) {
            setSize(min(full, (hostEditor.getDpUnit() * 420).toInt()), getHeight())
        }
    }

    override fun onClick(view: View) {
        when (view) {
            linkBtn -> {
                handleOpenLink()
                dismiss()
                return
            }
            translateBtn -> {
                handleTranslate()
                dismiss()
                return
            }
            else -> super.onClick(view)
        }
    }

    private fun selectedText(): String? {
        val c = hostEditor.cursor ?: return null
        if (!c.isSelected) return null
        return hostEditor.text.subSequence(c.left, c.right).toString()
    }

    private fun handleOpenLink() {
        val sel = selectedText()
        if (sel.isNullOrBlank()) {
            AppToast.show(hostEditor.context, Str.get(R.string.editor_select_action_no_selection))
            return
        }
        dispatchOpenLink(hostEditor.context, sel, onPickLink)
    }

    private fun handleTranslate() {
        val sel = selectedText()
        if (sel.isNullOrBlank()) {
            AppToast.show(hostEditor.context, Str.get(R.string.editor_select_action_no_selection))
            return
        }
        onTranslate(sel)
    }
}

// ==================== 对话框 ====================

/** 多个链接时的选择框（一个链接直接开浏览器，不走这里）。 */
@Composable
fun SelectionLinkDialog(links: List<String>, onDismiss: () -> Unit) {
    val context = LocalContext.current
    UnifiedListDialog(
        title = Str.get(R.string.editor_open_link),
        items = links,
        onItemClick = { i ->
            openLink(context, links[i])
            onDismiss()
        },
        onDismiss = onDismiss
    )
}

private enum class TranslateStep { PROVIDER, ONLINE }

/**
 * 翻译流程入口：先选**翻译方式**（在线通道 + 系统翻译应用），在线路线再选目标语言、
 * 弹结果（可复制）；系统路线直接把文本交给所选应用。
 *
 * 批次66追加：在线通道清单由 [onlineChannels]（= 「翻译接口管理」的启停 + 自定义实例）
 * 给出，通道规格 `OnlineTranslate` 定义在 TranslateApiManage.kt，这里只做状态流转。
 */
@Composable
fun TranslateActionDialog(text: String, onDismiss: () -> Unit) {
    var step by remember { mutableStateOf(TranslateStep.PROVIDER) }
    var spec by remember { mutableStateOf<OnlineTranslate?>(null) }
    when (step) {
        TranslateStep.PROVIDER -> TranslateProviderDialog(
            text = text,
            onSelect = { s ->
                spec = s
                step = TranslateStep.ONLINE
            },
            onDismiss = onDismiss
        )
        TranslateStep.ONLINE -> spec?.let { s ->
            OnlineTranslateDialog(text = text, spec = s, onDismiss = onDismiss)
        }
    }
}

/** 第一步：选择翻译方式（弹窗选择可用的翻译）。 */
@Composable
private fun TranslateProviderDialog(
    text: String,
    onSelect: (OnlineTranslate) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val apps = remember(context) { querySystemTranslateApps(context) }
    // 批次66追加：在线通道按「翻译接口管理」的配置装配（关掉的内置项不出现，自定义项追加在后）
    val channels = remember(context) { onlineChannels(context) }
    UnifiedDialog(
        onDismissRequest = onDismiss,
        title = Str.get(R.string.editor_translate_pick_title),
        content = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                if (channels.isEmpty()) {
                    Text(
                        text = Str.get(R.string.editor_translate_all_off),
                        style = MaterialTheme.typography.bodyMedium,
                        color = AppColors.textSecondary()
                    )
                }
                channels.forEachIndexed { i, channel ->
                    if (i > 0) {
                        Spacer(modifier = Modifier.height(AppDimens.spacingSmall))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(AppDimens.spacingSmall))
                    }
                    ProviderRow(
                        title = channel.title,
                        subtitle = channel.subtitle
                    ) { onSelect(channel.spec) }
                }
                if (channels.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(AppDimens.spacingSmall))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(AppDimens.spacingSmall))
                }
                Text(
                    text = Str.get(R.string.editor_translate_system),
                    style = MaterialTheme.typography.labelMedium,
                    color = AppColors.textSecondary()
                )
                Spacer(modifier = Modifier.height(AppDimens.spacingSmall))
                if (apps.isEmpty()) {
                    Text(
                        text = Str.get(R.string.editor_translate_no_system),
                        style = MaterialTheme.typography.bodyMedium,
                        color = AppColors.textSecondary()
                    )
                } else {
                    apps.forEach { app ->
                        ProviderRow(title = app.label) {
                            launchSystemTranslate(context, app, text)
                            onDismiss()
                        }
                    }
                }
            }
        },
        dismissButton = {
            UnifiedDialogTextButton(onClick = onDismiss) { Text(Str.get(R.string.cancel)) }
        }
    )
}

/** 第二步：在线翻译 —— 选目标语言 → 按 [spec] 联网取译文 → 结果可复制。 */
@Composable
private fun OnlineTranslateDialog(
    text: String,
    spec: OnlineTranslate,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    // 批次66追加（用户最终取舍「默认翻译到软件所处的语言」）：默认目标 = **应用界面语言**
    // —— 界面是中文就默认简体中文、是英文就默认英文，其余同理（推翻最初的「中文界面
    // 默认译英文」）。各通道写法不一（必应 zh-Hans、Google/MyMemory/自定义 zh-CN），按
    // 「界面语言精确匹配 → 同前缀 → 简体中文」逐级兜底；并把默认项排到语言条最左，
    // 打开就能看见选中的到底是谁。
    val uiLang = java.util.Locale.getDefault().language
    val zhPrefer = listOf("zh-CN", "zh-Hans", "zh-CHS", "zh-SG")
    val defaultLang = remember(spec, uiLang) {
        val want = if (uiLang == "zh") zhPrefer else listOf(uiLang)
        want.firstNotNullOfOrNull { w ->
            spec.langs.firstOrNull { it.first.equals(w, ignoreCase = true) }?.first
        } ?: spec.langs.firstOrNull { it.first.startsWith(uiLang, ignoreCase = true) }?.first
            ?: zhPrefer.firstNotNullOfOrNull { w ->
                spec.langs.firstOrNull { it.first.equals(w, ignoreCase = true) }?.first
            } ?: spec.langs.firstOrNull { it.first.startsWith("zh", ignoreCase = true) }?.first
            ?: spec.langs.first().first
    }
    val shownLangs = remember(spec, defaultLang) {
        listOf(spec.langs.first { it.first == defaultLang }) +
            spec.langs.filter { it.first != defaultLang }
    }
    var lang by remember(spec, defaultLang) { mutableStateOf(defaultLang) }
    var result by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var retryTick by remember { mutableStateOf(0) }

    LaunchedEffect(spec, lang, retryTick) {
        loading = true
        error = null
        result = null
        runCatching { spec.fetch(text, lang) }
            .onSuccess { result = it }
            .onFailure { e -> error = spec.mapError(e) }
        loading = false
    }

    UnifiedDialog(
        onDismissRequest = onDismiss,
        title = Str.get(R.string.editor_translate),
        content = {
            Column(
                modifier = Modifier
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = Str.get(R.string.editor_translate_source),
                    style = MaterialTheme.typography.labelSmall,
                    color = AppColors.textSecondary()
                )
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(AppDimens.spacingMedium))
                Text(
                    text = Str.get(R.string.editor_translate_to),
                    style = MaterialTheme.typography.labelMedium,
                    color = AppColors.textSecondary()
                )
                Spacer(modifier = Modifier.height(AppDimens.spacingSmall))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(AppDimens.spacingSmall)) {
                    // 默认的简体中文排在首位（shownLangs 已把默认项提到最前）
                    items(shownLangs.size) { i ->
                        val (code, name) = shownLangs[i]
                        LangChip(name = name, selected = code == lang) { lang = code }
                    }
                }
                Spacer(modifier = Modifier.height(AppDimens.spacingMedium))
                when {
                    loading -> Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(AppDimens.spacingSmall)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp))
                        Text(
                            text = Str.get(R.string.editor_translate_running),
                            style = MaterialTheme.typography.bodyMedium,
                            color = AppColors.textSecondary()
                        )
                    }
                    error != null -> Column {
                        Text(
                            text = error ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(AppDimens.spacingSmall))
                        Text(
                            text = Str.get(R.string.retry),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clickable { retryTick++ }
                                .padding(vertical = AppDimens.spacingSmall)
                        )
                    }
                    result != null -> SelectionContainer {
                        Text(
                            text = result ?: "",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        },
        confirmButton = {
            val out = result
            if (out != null) {
                UnifiedDialogTextButton(onClick = {
                    clipboard.setText(AnnotatedString(out))
                    AppToast.show(context, Str.get(R.string.copied_to_clipboard))
                }) { Text(Str.get(R.string.copy)) }
            }
        },
        dismissButton = {
            UnifiedDialogTextButton(onClick = onDismiss) { Text(Str.get(R.string.close)) }
        }
    )
}

/** 翻译方式列表里的一行（标题 + 可选副标题）。 */
@Composable
private fun ProviderRow(title: String, subtitle: String? = null, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = AppDimens.spacingSmall),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = AppColors.textSecondary()
                )
            }
        }
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = AppColors.textSecondary()
        )
    }
}

/** 目标语言选择 chip。 */
@Composable
private fun LangChip(name: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(percent = 50),
        color = if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(
                horizontal = AppDimens.spacingMedium,
                vertical = AppDimens.spacingSmall
            )
        )
    }
}
