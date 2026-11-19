package com.UIN.Tool.ui.screen.permission

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.UIN.Tool.R
import com.UIN.Tool.ui.components.unified.UnifiedBodyText
import com.UIN.Tool.ui.components.unified.UnifiedCaptionText
import com.UIN.Tool.ui.components.unified.UnifiedCard
import com.UIN.Tool.ui.components.unified.UnifiedIconButton
import com.UIN.Tool.ui.components.unified.UnifiedTitleText
import com.UIN.Tool.ui.theme.AppDimens
import com.UIN.Tool.utils.Str
import com.UIN.Tool.utils.UIConfig
import org.json.JSONObject
import java.util.Locale

/**
 * v.txt 第 3 条（用户）：插件权限页的「信息」弹窗。
 *
 * 文案全部来自 `assets/help/plugin_permissions.json`（中英双份），代码里不写权限说明。
 * 新增权限只改这个 JSON，不需要动 Kotlin。
 */
private const val HELP_ASSET = "help/plugin_permissions.json"

/** 读取并缓存帮助 JSON；读不到时返回 null（弹窗只显示一句兜底说明）。 */
private fun loadPermissionHelp(context: android.content.Context): JSONObject? = try {
    context.assets.open(HELP_ASSET).bufferedReader().use { JSONObject(it.readText()) }
} catch (e: Exception) {
    null
}

/** 当前语言对应的键：`system` 跟随系统，其余按 UIConfig 的选择。 */
private fun helpLang(): String {
    val configured = if (UIConfig.isInitialized()) UIConfig.getInstance().getLanguage() else "system"
    return when (configured) {
        "zh" -> "zh"
        "en" -> "en"
        else -> if (Locale.getDefault().language == "zh") "zh" else "en"
    }
}

private fun JSONObject.localized(lang: String): String =
    optString(lang).ifEmpty { optString("en") }

@Composable
fun PluginPermissionInfoDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val help = remember(context) { loadPermissionHelp(context) }
    val lang = helpLang()
    val title = help?.optJSONObject("title")?.localized(lang) ?: Str.get(R.string.plugin_permissions)
    val intro = help?.optJSONObject("intro")?.localized(lang).orEmpty()
    val groups = help?.optJSONArray("groups")
    val notes = help?.optJSONArray("notes")

    Dialog(onDismissRequest = onDismiss) {
        // w.txt 第 3 条（用户）：弹窗不能半透明 —— 显式给不透明底，玻璃 alpha 不参与。
        UnifiedCard(
            modifier = Modifier.fillMaxWidth(),
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.padding(AppDimens.spacingLarge)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    UnifiedTitleText(title)
                    UnifiedIconButton(
                        icon = Icons.Default.Close,
                        onClick = onDismiss,
                        contentDescription = Str.get(R.string.close)
                    )
                }
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 460.dp),
                    verticalArrangement = Arrangement.spacedBy(AppDimens.spacingSmall)
                ) {
                    if (intro.isNotEmpty()) {
                        item {
                            UnifiedBodyText(
                                intro,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (groups != null) {
                        for (g in 0 until groups.length()) {
                            val group = groups.optJSONObject(g) ?: continue
                            val groupTitle = group.optJSONObject("title")?.localized(lang).orEmpty()
                            item {
                                Spacer(modifier = Modifier.height(AppDimens.spacingSmall))
                                Text(
                                    text = groupTitle,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            val items = group.optJSONArray("items") ?: continue
                            for (i in 0 until items.length()) {
                                val item = items.optJSONObject(i) ?: continue
                                val name = item.optJSONObject("name")?.localized(lang).orEmpty()
                                val desc = item.optJSONObject("description")?.localized(lang).orEmpty()
                                val permission = item.optString("permission")
                                item {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        UnifiedBodyText(name)
                                        UnifiedCaptionText(desc)
                                        UnifiedCaptionText(
                                            permission,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                    if (notes != null && notes.length() > 0) {
                        item {
                            Spacer(modifier = Modifier.height(AppDimens.spacingSmall))
                            Text(
                                text = help?.optJSONObject("notesTitle")?.localized(lang)
                                    ?: Str.get(R.string.plugin_permissions),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        for (n in 0 until notes.length()) {
                            val note = notes.optJSONObject(n)?.localized(lang).orEmpty()
                            if (note.isNotEmpty()) {
                                item { UnifiedCaptionText(note) }
                            }
                        }
                    }
                    if (help == null) {
                        item {
                            UnifiedBodyText(
                                Str.get(R.string.permissions_required_by_the_plugin),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
