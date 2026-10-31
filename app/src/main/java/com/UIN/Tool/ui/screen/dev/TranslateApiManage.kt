package com.UIN.Tool.ui.screen.dev

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.UIN.Tool.R
import com.UIN.Tool.data.local.TranslateApiStore
import com.UIN.Tool.data.remote.BingTranslate
import com.UIN.Tool.data.remote.GoogleTranslate
import com.UIN.Tool.data.remote.LibreTranslate
import com.UIN.Tool.data.remote.MyMemoryTranslate
import com.UIN.Tool.ui.components.unified.UnifiedConfirmDialog
import com.UIN.Tool.ui.components.unified.UnifiedDialog
import com.UIN.Tool.ui.components.unified.UnifiedDialogTextButton
import com.UIN.Tool.ui.components.unified.UnifiedTextField
import com.UIN.Tool.ui.theme.AppColors
import com.UIN.Tool.ui.theme.AppDimens
import com.UIN.Tool.utils.AppToast
import com.UIN.Tool.utils.Str
import kotlinx.coroutines.launch

/**
 * 6.2.0 批次66追加：**翻译接口管理**（用户取舍：启停内置 + 自定义接口）。
 *
 * - 三个内置免费匿名通道（必应 / Google / MyMemory）各带开关，关掉后**不再出现在**
 *   翻译方式列表里（[onlineChannels] 按 [TranslateApiStore] 过滤）；
 * - 自定义通道 = 任意 **LibreTranslate 兼容**实例（[LibreTranslate] 按官方协议请求），
 *   记录名称 / 地址 / 可选密钥，存 [TranslateApiStore]；本机网络没有可直连的公开实例，
 *   所以提供「测试」按钮让用户就地验证自己填的地址。
 */

/** 在线翻译通道的统一描述：目标语言表 + 取译文的挂起函数 + 异常 → 用户可读消息。 */
internal data class OnlineTranslate(
    val langs: List<Pair<String, String>>,
    val fetch: suspend (text: String, to: String) -> String,
    val mapError: (Throwable) -> String
)

/** 翻译方式列表里的一行：标题（通道名）+ 副标题（说明 / 地址）+ 结果页规格。 */
internal data class TranslateChannel(
    val title: String,
    val subtitle: String,
    val spec: OnlineTranslate
)

private fun genericError(e: Throwable): String =
    Str.get(R.string.editor_translate_failed, e.message ?: "?")

/**
 * 装配当前可用的在线通道列表（[TranslateApiStore] 决定启停与自定义项），
 * 翻译方式对话框每次打开时现算，管理页改完立即反映。
 */
internal fun onlineChannels(context: android.content.Context): List<TranslateChannel> =
    buildList {
        if (TranslateApiStore.isEnabled(context, TranslateApiStore.KEY_BING)) {
            add(
                TranslateChannel(
                    title = Str.get(R.string.editor_translate_bing),
                    subtitle = Str.get(R.string.editor_translate_bing_hint),
                    spec = OnlineTranslate(
                        langs = BingTranslate.TARGET_LANGS,
                        fetch = { t, to -> BingTranslate.translate(t, to).translation },
                        mapError = { e ->
                            (e as? BingTranslate.TranslateException)
                                ?.let { Str.get(it.res, *it.args) }
                                ?: genericError(e)
                        }
                    )
                )
            )
        }
        // Google gtx 免费匿名通道（需 HTTP/1.1，见 GoogleTranslate 类注释）
        if (TranslateApiStore.isEnabled(context, TranslateApiStore.KEY_GOOGLE)) {
            add(
                TranslateChannel(
                    title = Str.get(R.string.editor_translate_google),
                    subtitle = Str.get(R.string.editor_translate_google_hint),
                    spec = OnlineTranslate(
                        langs = GoogleTranslate.TARGET_LANGS,
                        fetch = { t, to -> GoogleTranslate.translate(t, to) },
                        mapError = ::genericError
                    )
                )
            )
        }
        // MyMemory 免费匿名通道（每日 5000 字符，见 MyMemoryTranslate 类注释）
        if (TranslateApiStore.isEnabled(context, TranslateApiStore.KEY_MYMEMORY)) {
            add(
                TranslateChannel(
                    title = Str.get(R.string.editor_translate_mymemory),
                    subtitle = Str.get(R.string.editor_translate_mymemory_hint),
                    spec = OnlineTranslate(
                        langs = MyMemoryTranslate.TARGET_LANGS,
                        fetch = { t, to -> MyMemoryTranslate.translate(t, to) },
                        mapError = ::genericError
                    )
                )
            )
        }
        // 用户自定义的 LibreTranslate 兼容实例
        TranslateApiStore.customs(context).forEach { api ->
            add(
                TranslateChannel(
                    title = api.name,
                    subtitle = api.url,
                    spec = OnlineTranslate(
                        langs = LibreTranslate.TARGET_LANGS,
                        fetch = { t, to -> LibreTranslate.translate(api.url, api.apiKey, t, to) },
                        mapError = ::genericError
                    )
                )
            )
        }
    }

/**
 * 翻译接口管理页（编辑器设置 → 两个管理入口之一）：
 * 内置通道开关 + 自定义接口列表（增 / 删 / 测试）。
 */
@Composable
fun TranslateApiManageDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // 任一项变更后自增，重算开关状态与自定义列表
    var refresh by remember { mutableIntStateOf(0) }
    val bing = remember(refresh) { TranslateApiStore.isEnabled(context, TranslateApiStore.KEY_BING) }
    val google = remember(refresh) { TranslateApiStore.isEnabled(context, TranslateApiStore.KEY_GOOGLE) }
    val mymemory = remember(refresh) {
        TranslateApiStore.isEnabled(context, TranslateApiStore.KEY_MYMEMORY)
    }
    val customs = remember(refresh) { TranslateApiStore.customs(context) }

    var showAdd by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<TranslateApiStore.CustomApi?>(null) }

    UnifiedDialog(
        onDismissRequest = onDismiss,
        title = Str.get(R.string.editor_translate_api_manage),
        content = {
            Column(
                modifier = Modifier
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                SectionLabel(Str.get(R.string.editor_translate_api_builtin))
                Spacer(modifier = Modifier.height(AppDimens.spacingSmall))
                ApiSwitchRow(
                    title = Str.get(R.string.editor_translate_bing),
                    subtitle = Str.get(R.string.editor_translate_bing_hint),
                    checked = bing
                ) {
                    TranslateApiStore.setEnabled(context, TranslateApiStore.KEY_BING, it)
                    refresh++
                }
                Spacer(modifier = Modifier.height(AppDimens.spacingSmall))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(AppDimens.spacingSmall))
                ApiSwitchRow(
                    title = Str.get(R.string.editor_translate_google),
                    subtitle = Str.get(R.string.editor_translate_google_hint),
                    checked = google
                ) {
                    TranslateApiStore.setEnabled(context, TranslateApiStore.KEY_GOOGLE, it)
                    refresh++
                }
                Spacer(modifier = Modifier.height(AppDimens.spacingSmall))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(AppDimens.spacingSmall))
                ApiSwitchRow(
                    title = Str.get(R.string.editor_translate_mymemory),
                    subtitle = Str.get(R.string.editor_translate_mymemory_hint),
                    checked = mymemory
                ) {
                    TranslateApiStore.setEnabled(context, TranslateApiStore.KEY_MYMEMORY, it)
                    refresh++
                }

                Spacer(modifier = Modifier.height(AppDimens.spacingMedium))
                SectionLabel(Str.get(R.string.editor_translate_api_custom))
                Spacer(modifier = Modifier.height(AppDimens.spacingSmall))
                if (customs.isEmpty()) {
                    Text(
                        text = Str.get(R.string.editor_translate_api_custom_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = AppColors.textSecondary()
                    )
                } else {
                    customs.forEachIndexed { i, api ->
                        if (i > 0) {
                            Spacer(modifier = Modifier.height(AppDimens.spacingSmall))
                            HorizontalDivider()
                            Spacer(modifier = Modifier.height(AppDimens.spacingSmall))
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = api.name,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = api.url,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AppColors.textSecondary()
                                )
                            }
                            // 就地连通测试（能否访问取决于用户填的实例地址）
                            TextButton(onClick = {
                                scope.launch {
                                    runCatching {
                                        LibreTranslate.test(api.url, api.apiKey)
                                    }.onSuccess {
                                        AppToast.success(
                                            context,
                                            Str.get(R.string.editor_translate_api_test_ok, it)
                                        )
                                    }.onFailure { e ->
                                        AppToast.error(
                                            context,
                                            Str.get(
                                                R.string.editor_translate_api_test_fail,
                                                e.message ?: "?"
                                            )
                                        )
                                    }
                                }
                            }) { Text(Str.get(R.string.test)) }
                            IconButton(onClick = { pendingDelete = api }) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = Str.get(R.string.delete),
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(AppDimens.spacingSmall))
                Text(
                    text = Str.get(R.string.editor_translate_api_switch_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = AppColors.textSecondary()
                )
            }
        },
        confirmButton = {
            UnifiedDialogTextButton(onClick = { showAdd = true }) {
                Text(Str.get(R.string.editor_translate_api_add))
            }
        },
        dismissButton = {
            UnifiedDialogTextButton(onClick = onDismiss) { Text(Str.get(R.string.close)) }
        }
    )

    if (showAdd) {
        AddCustomApiDialog(
            onSaved = {
                showAdd = false
                refresh++
            },
            onDismiss = { showAdd = false }
        )
    }

    pendingDelete?.let { api ->
        UnifiedConfirmDialog(
            title = Str.get(R.string.delete),
            message = Str.get(R.string.editor_translate_api_delete_msg, api.name),
            isDestructive = true,
            confirmText = Str.get(R.string.delete),
            dismissText = Str.get(R.string.cancel),
            onConfirm = {
                TranslateApiStore.removeCustom(context, api.id)
                pendingDelete = null
                refresh++
            },
            onDismiss = { pendingDelete = null }
        )
    }
}

/** 添加自定义 LibreTranslate 兼容接口：名称 / 地址 / 可选密钥 + 连通测试。 */
@Composable
private fun AddCustomApiDialog(onSaved: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }

    // 地址与名称的基本校验；null 表示通过
    fun validate(): Boolean {
        error = when {
            name.isBlank() -> Str.get(R.string.editor_translate_api_name_required)
            !url.startsWith("http://") && !url.startsWith("https://") ->
                Str.get(R.string.editor_translate_api_invalid_url)
            else -> null
        }
        return error == null
    }

    UnifiedDialog(
        onDismissRequest = onDismiss,
        title = Str.get(R.string.editor_translate_api_add),
        content = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                UnifiedTextField(
                    value = name,
                    onValueChange = { name = it; error = null },
                    label = Str.get(R.string.name),
                    placeholder = "My LibreTranslate",
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(AppDimens.spacingSmall))
                UnifiedTextField(
                    value = url,
                    onValueChange = { url = it; error = null },
                    label = Str.get(R.string.editor_translate_api_url),
                    placeholder = "https://lt.example.com",
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(AppDimens.spacingSmall))
                UnifiedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it; error = null },
                    label = Str.get(R.string.editor_translate_api_key),
                    modifier = Modifier.fillMaxWidth()
                )
                error?.let {
                    Spacer(modifier = Modifier.height(AppDimens.spacingSmall))
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Spacer(modifier = Modifier.height(AppDimens.spacingSmall))
                TextButton(
                    enabled = !testing,
                    onClick = {
                        if (validate()) {
                            scope.launch {
                                testing = true
                                runCatching {
                                    LibreTranslate.test(url.trim().trimEnd('/'), apiKey.trim())
                                }.onSuccess {
                                    AppToast.success(
                                        context,
                                        Str.get(R.string.editor_translate_api_test_ok, it)
                                    )
                                }.onFailure { e ->
                                    AppToast.error(
                                        context,
                                        Str.get(
                                            R.string.editor_translate_api_test_fail,
                                            e.message ?: "?"
                                        )
                                    )
                                }
                                testing = false
                            }
                        }
                    }
                ) { Text(Str.get(R.string.test)) }
                Text(
                    text = Str.get(R.string.editor_translate_api_add_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = AppColors.textSecondary()
                )
            }
        },
        confirmButton = {
            UnifiedDialogTextButton(onClick = {
                if (validate()) {
                    TranslateApiStore.addCustom(context, name, url, apiKey)
                    AppToast.success(context, Str.get(R.string.editor_translate_api_added))
                    onSaved()
                }
            }) { Text(Str.get(R.string.save)) }
        },
        dismissButton = {
            UnifiedDialogTextButton(onClick = onDismiss) { Text(Str.get(R.string.cancel)) }
        }
    )
}

/** 分组小标题。 */
@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = AppColors.textSecondary()
    )
}

/** 内置通道行：左标题/说明，右开关。 */
@Composable
private fun ApiSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = AppColors.textSecondary()
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
