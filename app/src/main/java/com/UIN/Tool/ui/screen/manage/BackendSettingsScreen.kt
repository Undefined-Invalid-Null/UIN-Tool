// ui/screen/manage/BackendSettingsScreen.kt
package com.UIN.Tool.ui.screen.manage

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.UIN.Tool.R
import com.UIN.Tool.plugin.BackendConfig
import com.UIN.Tool.ui.components.UIComponents
import com.UIN.Tool.utils.AppToast
import com.UIN.Tool.utils.Str

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackendSettingsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current

    var impl by remember {
        mutableStateOf(BackendConfig.getImplementation(context))
    }
    var env by remember {
        mutableStateOf(BackendConfig.getEnvironment(context))
    }
    var container by remember {
        mutableStateOf(BackendConfig.getContainer(context))
    }
    var idleMin by remember {
        mutableStateOf(BackendConfig.getIdleTimeoutMinutes(context))
    }

    val isReal = impl == BackendConfig.IMPL_REAL
    val setupCode = BackendConfig.buildRealTermuxSetupCode(context)

    fun copySetupCode() {
        try {
            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                as android.content.ClipboardManager
            clipboard.setPrimaryClip(
                android.content.ClipData.newPlainText("UIN_Tool", setupCode)
            )
            AppToast.success(context, Str.get(R.string.copied_to_clipboard))
        } catch (e: Exception) {
            AppToast.error(context, Str.get(R.string.copy_failed))
        }
    }

    Scaffold(
        topBar = {
            UIComponents.ManageTopAppBar(
                titleText = Str.get(R.string.backend_runtime_settings),
                onBack = onBack
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            UIComponents.SectionTitle(Str.get(R.string.backend_implementation))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    BackendConfig.IMPL_BUILTIN to Str.get(R.string.builtin_termux),
                    BackendConfig.IMPL_REAL to Str.get(R.string.real_termux)
                ).forEach { (key, label) ->
                    FilterChip(
                        selected = impl == key,
                        onClick = { impl = key },
                        label = { Text(label) },
                        modifier = Modifier.weight(1f),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                }
            }
            UIComponents.CaptionText(
                if (isReal)
                    Str.get(R.string.real_termux_will_call_com_termux_t)
                else
                    Str.get(R.string.builtin_termux_is_a_simplified_li),
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
            )

            if (isReal) {
                UIComponents.SectionTitle(Str.get(R.string.backend_environment))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        BackendConfig.ENV_TERMUX to Str.get(R.string.termux_local),
                        BackendConfig.ENV_PROOT to Str.get(R.string.proot_container)
                    ).forEach { (key, label) ->
                        FilterChip(
                            selected = env == key,
                            onClick = { env = key },
                            label = { Text(label) },
                            modifier = Modifier.weight(1f),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        )
                    }
                }
                if (env == BackendConfig.ENV_PROOT) {
                    UIComponents.TextInput(
                        value = container,
                        onValueChange = { container = it },
                        label = Str.get(R.string.proot_container_name),
                        placeholder = "alpine",
                        modifier = Modifier.fillMaxWidth()
                    )
                    UIComponents.CaptionText(
                        Str.get(R.string.run_proot_distro_list_in_termux_t),
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }

            UIComponents.SectionTitle(Str.get(R.string.idle_recycle_timeout_minutes))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(3, 5, 10, 15).forEach { minutes ->
                    FilterChip(
                        selected = idleMin == minutes,
                        onClick = { idleMin = minutes },
                        label = { Text("$minutes ${Str.get(R.string.min_)}") },
                        modifier = Modifier.weight(1f),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                }
            }
            UIComponents.CaptionText(
                Str.get(R.string.backend_will_be_stopped_after_being_i),
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
            )

            if (isReal) {
                // ==================== 实体 Termux 初始化命令（可复制） ====================
                UIComponents.Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                Str.get(R.string.backend_init_command),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                Str.get(R.string.backend_init_command_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        UIComponents.IconButton(
                            icon = Icons.Default.ContentCopy,
                            onClick = { copySetupCode() },
                            contentDescription = Str.get(R.string.copy_command)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text(
                            setupCode,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            UIComponents.PrimaryButton(
                text = Str.get(R.string.save),
                onClick = {
                    BackendConfig.setImplementation(context, impl)
                    if (isReal) {
                        BackendConfig.setEnvironment(context, env)
                        BackendConfig.setContainer(context, container.trim())
                    }
                    BackendConfig.setIdleTimeoutMinutes(context, idleMin)
                    AppToast.success(context, Str.get(R.string.backend_settings_saved))
                    onBack()
                },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
