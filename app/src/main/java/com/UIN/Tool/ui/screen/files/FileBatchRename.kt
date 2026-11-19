// app/src/main/java/com/UIN/Tool/ui/screen/files/FileBatchRename.kt
package com.UIN.Tool.ui.screen.files

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.UIN.Tool.R
import com.UIN.Tool.ui.components.unified.ButtonSize
import com.UIN.Tool.ui.components.unified.ButtonVariant
import com.UIN.Tool.ui.components.unified.UnifiedButton
import com.UIN.Tool.ui.components.unified.UnifiedDialog
import com.UIN.Tool.ui.components.unified.dialogAutoFocus
import com.UIN.Tool.ui.theme.AppDimens
import com.UIN.Tool.utils.Str
import java.io.File

/**
 * 批量重命名：一条模板把整批选中文件按同一套规则改名。
 *
 * ## 模板记号
 *
 * | 记号 | 含义 |
 * |---|---|
 * | `#p` | 主干名（不含扩展名） |
 * | `#e` | 扩展名（不含点）；没有扩展名时为空 |
 * | `#n` | 序号，从「起始序号」开始，按当前行递增 |
 * | `#z` | 同 `#n`，但按「序号位数」补零（`001`、`002`…） |
 * | `#f` | 完整文件名（含扩展名） |
 * | `##` | 一个字面 `#` |
 *
 * ## 为什么用 `#` 而不是 `%`
 *
 * `#` 与 Android 的资源格式化**完全不沾边**，而 `%` 会撞上两堵墙：
 * 一是 AAPT2 会把含 `%f`/`%n` 的资源当格式串校验，多个非位置占位符直接报编译错；
 * 二是 `Context.getString(id)` 走不走 `String.format` 取决于有没有传参，
 * 同一条文案在「带参」和「不带参」两条调用路径上会呈现出不同的字面量。
 * 换成 `#` 之后这两个问题都不存在，文案怎么调都不会被解释成格式串。
 *
 * ## 为什么是模板而不是「加前缀 / 加后缀 / 序号」三个固定模式
 *
 * 三种模式覆盖不了 `报告_#z_最终版` 这类组合需求，而模板一条就能表达；
 * 配上**实时预览**，用户不需要先在脑子里展开一遍再点确定 —— 看错了当场改。
 */
const val BATCH_RENAME_DEFAULT_TEMPLATE = "#p"

/** 预览最多显示这些行：再多对话框就滚个没完，剩下的靠「N 项」一行交代。 */
private const val BATCH_RENAME_PREVIEW_MAX = 8

/**
 * 对话框内容整体限高，超了就整块可滚。
 *
 * 加了「查找替换 + 正则 + 大小写」之后行数变多，而 [UnifiedDialog] 自己是
 * `wrapContentHeight`、没有高度兜底 —— 小屏上不设上限就是**两头都被裁掉且够不着**。
 */
private val BATCH_RENAME_MAX_HEIGHT = 440.dp

/**
 * 展开一条批量重命名结果。
 *
 * [seq] 是这一行的绝对序号（调用方已经把「起始值 + 下标」算好传进来），
 * [width] 只作用于 `#z`。
 *
 * 未知记号（`#x`）**原样保留**而不是吞掉 —— 用户敲错时能在预览里直接看到 `#x` 还在，
 * 比「凭空少一截」好定位得多。
 */
fun expandBatchName(template: String, fileName: String, seq: Int, width: Int): String {
    // `.gitignore` 这类点开头的文件不算「有扩展名」，否则主干会被削成空
    val dot = fileName.lastIndexOf('.')
    val hasExt = dot > 0
    val stem = if (hasExt) fileName.substring(0, dot) else fileName
    val ext = if (hasExt) fileName.substring(dot + 1) else ""

    val sb = StringBuilder(template.length + 8)
    var i = 0
    while (i < template.length) {
        val c = template[i]
        if (c != '#' || i + 1 >= template.length) {
            sb.append(c)
            i++
            continue
        }
        when (template[i + 1]) {
            'p' -> sb.append(stem)
            'e' -> sb.append(ext)
            'n' -> sb.append(seq)
            'z' -> sb.append(seq.toString().padStart(width.coerceIn(1, 12), '0'))
            'f' -> sb.append(fileName)
            '#' -> sb.append('#')
            else -> {
                // 未知记号：连 # 一起原样留下，预览里看得见
                sb.append('#').append(template[i + 1])
            }
        }
        i += 2
    }
    return sb.toString()
}

// ==================== 查找替换 / 大小写（批量重命名的第二套改法） ====================

/** [caseConvertName] 的四档；序号即按钮循环顺序，同时决定取哪条文案。 */
private const val CASE_KEEP = 0
private const val CASE_UPPER = 1
private const val CASE_LOWER = 2
private const val CASE_CAPITALIZE = 3

/**
 * 查找替换。[regex] 为 true 时把 [find] 当正则、[replace] 里 `$1` 是捕获组。
 *
 * 找不到就原样返回 —— 批量场景里「这条不匹配」是常态，不是错误；
 * 坏正则也返回原样，但那样等于「点了没反应」，所以另外配了 [isBadRegex] 单独拦。
 */
private fun findReplaceName(name: String, find: String, replace: String, regex: Boolean): String {
    if (find.isEmpty()) return name
    if (!regex) return name.replace(find, replace)
    val r = runCatching { find.toRegex() }.getOrNull() ?: return name
    // 兜底：替换串里还有别的非法引用时（命名组之类）会抛 IllegalArgumentException，
    // 而这里跑在每次重算预览的路上 —— 闪退一次，用户敲的查找串就全没了
    return runCatching { r.replace(name, replace) }.getOrElse { name }
}

/** 替换串里的 `$1` 这类数字组引用（反斜杠转义过的不算）。 */
private val REGEX_GROUP_REF = Regex("""(?<!\\)\$(\d+)""")

/**
 * 这组查找 / 替换能用吗？只在开了正则、且真填了查找串时问。
 *
 * 两类错都要在**预览前**单独报出来，否则 [findReplaceName] 只会安静地返回原样名，
 * 四道闸一路放行、确定键可点，用户看到的却是「按了没有任何变化」：
 * 一是正则本身写错，二是 `$3` 却没有第三个捕获组（真替换时抛异常）。
 */
private fun isBadRegex(find: String, replace: String, regex: Boolean): Boolean {
    if (!regex || find.isEmpty()) return false
    val r = runCatching { find.toRegex() }.getOrNull() ?: return true
    // 捕获组数只有 `Matcher` 有：kotlin 的 `Regex` 不暴露，java 的 `Pattern` 也没有
    // （`Pattern.groupCount()` 根本不存在，javap 实测），所以经 `matcher()` 拐一次。
    val groups = r.toPattern().matcher("").groupCount()
    return REGEX_GROUP_REF.findAll(replace).any { m ->
        // 数字大到 Int 都装不下，也一样引不着
        m.groupValues[1].toIntOrNull()?.let { it > groups } ?: true
    }
}

/** 大小写这档叫什么（按钮文字里的 `%1$s`）。 */
private fun caseLabelRes(mode: Int): Int = when (mode) {
    CASE_UPPER -> R.string.batch_rename_case_upper
    CASE_LOWER -> R.string.batch_rename_case_lower
    CASE_CAPITALIZE -> R.string.batch_rename_case_title
    else -> R.string.batch_rename_case_original
}

/**
 * 大小写转换：**只动主干，扩展名原样** —— `IMG_001.jpg` 转大写不该变成 `.JPG`，
 * 那看着像后缀被改了，按扩展名关联的打开方式也会跟着疑神疑鬼。
 * `.gitignore` 这类点开头的文件按「无扩展名」处理，与 [expandBatchName] 同一套规则。
 */
private fun caseConvertName(name: String, mode: Int): String {
    if (mode == CASE_KEEP || name.isEmpty()) return name
    val dot = name.lastIndexOf('.')
    val hasExt = dot > 0
    val stem = if (hasExt) name.substring(0, dot) else name
    val ext = if (hasExt) name.substring(dot) else ""
    // 用不带 locale 的版本：土耳其语的 I/i 规则会把英文文件名改出意料之外的结果
    val converted = when (mode) {
        CASE_UPPER -> stem.uppercase()
        CASE_LOWER -> stem.lowercase()
        else -> stem.replaceFirstChar { ch ->
            if (ch.isLowerCase()) ch.titlecase() else ch.toString()
        }
    }
    return converted + ext
}

/**
 * 校验整批结果，返回出错时的字符串资源；通过则返回 `null`。
 *
 * 四道闸按「用户最可能先犯的错」排序：空名 → 非法字符 → 批内重名 → 撞磁盘上的既有文件。
 * 撞盘检查要把**本批自己要改的那些路径**排除掉 —— 原地改名（模板没变）时目标就是源，
 * 不排除的话会把自己的旧名当成冲突，一个字没改就报错。
 */
private fun validateBatchRename(paths: List<String>, names: List<String>): Int? = when {
    names.isEmpty() -> null
    names.any { it.isBlank() } -> R.string.batch_rename_empty
    names.any { it.contains('/') } -> R.string.batch_rename_slash
    // 批内重名：两个源文件映射到同一个新名，后一个必然覆盖失败
    names.toSet().size != names.size -> R.string.batch_rename_dup
    else -> {
        val parent = File(paths[0]).parentFile
        val sources = paths.map { File(it).path }.toHashSet()
        val taken = parent != null && names.any { n ->
            val dst = File(parent, n)
            dst.exists() && dst.path !in sources
        }
        if (taken) R.string.batch_rename_taken else null
    }
}

/**
 * 批量重命名框。
 *
 * [paths] 是**已选中**的源路径（调用方负责只在批量态弹出）。
 * [onRename] 收的是**算好的整批新名**（模板 / 查找替换 / 正则 / 大小写都在这里展开），
 * 真正改名在 ViewModel 里做 —— 对话框不碰文件系统，预览可以每敲一个字重算，而改名只在
 * 按下确定时发生一次；也正因为只有一套展开逻辑，预览显示的就是最终落盘的结果。
 */
@Composable
fun FileBatchRenameDialog(
    paths: List<String>,
    onRename: (names: List<String>) -> Unit,
    onDismiss: () -> Unit
) {
    var template by rememberSaveable { mutableStateOf(BATCH_RENAME_DEFAULT_TEMPLATE) }
    var startText by rememberSaveable { mutableStateOf("1") }
    var widthText by rememberSaveable { mutableStateOf("3") }
    // 第二套改法：查找替换 + 正则 + 大小写。几组输入**互斥显示**而不叠着堆 ——
    // 叠起来就是四五个输入框一起杵在那儿，对话框高到出屏
    var replaceMode by rememberSaveable { mutableStateOf(false) }
    var findText by rememberSaveable { mutableStateOf("") }
    var replaceText by rememberSaveable { mutableStateOf("") }
    var useRegex by rememberSaveable { mutableStateOf(false) }
    var caseMode by rememberSaveable { mutableStateOf(CASE_KEEP) }

    val startNum = startText.trim().toIntOrNull() ?: 1
    val widthNum = (widthText.trim().toIntOrNull() ?: 3).coerceIn(1, 12)

    val names = remember(
        template, paths, startNum, widthNum,
        replaceMode, findText, replaceText, useRegex, caseMode
    ) {
        paths.mapIndexed { i, p ->
            val src = File(p).name
            val renamed = if (replaceMode) {
                findReplaceName(src, findText, replaceText, useRegex)
            } else {
                expandBatchName(template, src, startNum + i, widthNum)
            }
            caseConvertName(renamed, caseMode)
        }
    }
    // 坏正则排在最前面：它会让上面每个名字都等于原样，四道闸一路放行、确定键可点，
    // 用户只看到「按了没反应」
    val errorRes = remember(names, paths, replaceMode, findText, replaceText, useRegex) {
        if (replaceMode && isBadRegex(findText, replaceText, useRegex)) R.string.batch_rename_bad_regex
        else validateBatchRename(paths, names)
    }

    UnifiedDialog(
        onDismissRequest = onDismiss,
        title = Str.get(R.string.batch_rename_title),
        content = {
            Column(
                // 整块限高可滚：改法加到两套之后行数变多，而 UnifiedDialog 自己是
                // wrapContentHeight、没有高度兜底 —— 小屏不设上限就是两头裁掉且够不着
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = BATCH_RENAME_MAX_HEIGHT)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(AppDimens.spacingSmall)
            ) {
                // 两种改法二选一，只显示当前这套的输入，另一套整个收起来
                Row(horizontalArrangement = Arrangement.spacedBy(AppDimens.spacingSmall)) {
                    UnifiedButton(
                        text = Str.get(R.string.batch_rename_mode_template),
                        onClick = { replaceMode = false },
                        modifier = Modifier.weight(1f),
                        size = ButtonSize.Small,
                        variant = if (replaceMode) ButtonVariant.Text else ButtonVariant.Primary
                    )
                    UnifiedButton(
                        text = Str.get(R.string.batch_rename_mode_find_replace),
                        onClick = { replaceMode = true },
                        modifier = Modifier.weight(1f),
                        size = ButtonSize.Small,
                        variant = if (replaceMode) ButtonVariant.Primary else ButtonVariant.Text
                    )
                }

                if (replaceMode) {
                    Row(horizontalArrangement = Arrangement.spacedBy(AppDimens.spacingSmall)) {
                        OutlinedTextField(
                            value = findText,
                            onValueChange = { findText = it },
                            label = { Text(Str.get(R.string.batch_rename_find)) },
                            singleLine = true,
                            // j.txt 第 4 条：本模式下这格是最上面的输入框 —— 自动聚焦。
                            modifier = Modifier.weight(1f).dialogAutoFocus()
                        )
                        OutlinedTextField(
                            value = replaceText,
                            onValueChange = { replaceText = it },
                            label = { Text(Str.get(R.string.batch_rename_replace)) },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    DialogCheckRow(
                        label = Str.get(R.string.batch_rename_regex),
                        checked = useRegex
                    ) { useRegex = it }
                    if (useRegex) {
                        Text(
                            text = Str.get(R.string.batch_rename_regex_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    OutlinedTextField(
                        value = template,
                        onValueChange = { template = it },
                        label = { Text(Str.get(R.string.batch_rename_template)) },
                        singleLine = true,
                        // j.txt 第 4 条：模板模式下这格是最上面的输入框 —— 自动聚焦。
                        modifier = Modifier.fillMaxWidth().dialogAutoFocus()
                    )
                    Text(
                        text = Str.get(R.string.batch_rename_template_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(AppDimens.spacingSmall)) {
                        OutlinedTextField(
                            value = startText,
                            onValueChange = { startText = it },
                            label = { Text(Str.get(R.string.batch_rename_start)) },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = widthText,
                            onValueChange = { widthText = it },
                            label = { Text(Str.get(R.string.batch_rename_width)) },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // 大小写：两种改法都能再跟一步转换。按一下换一档，按钮上的字就是当前档，
                // 不用额外摆四个单选项把对话框再撑高一截
                UnifiedButton(
                    text = Str.get(R.string.batch_rename_case, Str.get(caseLabelRes(caseMode))),
                    onClick = { caseMode = (caseMode + 1) % (CASE_CAPITALIZE + 1) },
                    modifier = Modifier.fillMaxWidth(),
                    size = ButtonSize.Small,
                    variant = ButtonVariant.Outlined
                )

                Text(
                    text = Str.get(R.string.batch_rename_preview),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                // 预览要限高 + 可滚：选中 500 个文件时不能把整个对话框撑出屏幕
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 168.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    paths.take(BATCH_RENAME_PREVIEW_MAX).forEachIndexed { i, p ->
                        BatchPreviewRow(oldName = File(p).name, newName = names[i])
                    }
                    if (paths.size > BATCH_RENAME_PREVIEW_MAX) {
                        Text(
                            text = "+${paths.size - BATCH_RENAME_PREVIEW_MAX}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (errorRes != null) {
                    Text(
                        text = Str.get(errorRes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            UnifiedButton(
                text = Str.get(R.string.rename),
                // 交算好的整批新名 —— 预览里看到的就是落盘的那份
                onClick = { onRename(names) },
                // 校验不过就禁用：这里不能靠「点了没反应」传达错误，错误文案就在上方摆着
                enabled = errorRes == null,
                variant = ButtonVariant.Primary
            )
        },
        dismissButton = {
            UnifiedButton(
                text = Str.get(R.string.cancel),
                onClick = onDismiss,
                variant = ButtonVariant.Text
            )
        }
    )
}

/** 预览一行：旧名 → 新名。没变的那行用灰色，变了的用主色，一眼看出哪些会被改。 */
@Composable
private fun BatchPreviewRow(oldName: String, newName: String) {
    val changed = oldName != newName
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = oldName,
            style = MaterialTheme.typography.bodySmall,
            color = muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = " → ",
            style = MaterialTheme.typography.bodySmall,
            color = muted
        )
        Text(
            text = newName,
            style = MaterialTheme.typography.bodySmall,
            color = if (changed) MaterialTheme.colorScheme.primary else muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}
