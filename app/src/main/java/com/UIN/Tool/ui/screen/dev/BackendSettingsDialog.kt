// ui/screen/dev/BackendSettingsDialog.kt
package com.UIN.Tool.ui.screen.dev

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.UIN.Tool.R
import com.UIN.Tool.plugin.BackendConfig
import com.UIN.Tool.ui.components.UIComponents
import com.UIN.Tool.ui.theme.AppColors
import com.UIN.Tool.utils.AppToast
import com.UIN.Tool.utils.Str

@Composable
fun BackendSettingsDialog(onDismiss: () -> Unit) {
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

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = if (AppColors.glassEnabled())
            AppColors.glassBackground()
        else
            MaterialTheme.colorScheme.surface,
        title = {
            Text(Str.get(R.string.backend_runtime_settings), color = MaterialTheme.colorScheme.onSurface)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                UIComponents.BodyText(Str.get(R.string.backend_implementation))
                Spacer(modifier = Modifier.height(4.dp))
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
                    Spacer(modifier = Modifier.height(12.dp))
                    UIComponents.BodyText(Str.get(R.string.backend_environment))
                    Spacer(modifier = Modifier.height(4.dp))
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
                        Spacer(modifier = Modifier.height(8.dp))
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

                Spacer(modifier = Modifier.height(12.dp))
                UIComponents.BodyText(Str.get(R.string.idle_recycle_timeout_minutes))
                Spacer(modifier = Modifier.height(4.dp))
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
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                Str.get(R.string.reminder),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                Str.get(R.string.real_termux_requires_allow_external),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                "allow-external-apps=true",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                "termux-setup-storage",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                "termux-reload-settings",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    BackendConfig.setImplementation(context, impl)
                    if (isReal) {
                        BackendConfig.setEnvironment(context, env)
                        BackendConfig.setContainer(context, container.trim())
                    }
                    BackendConfig.setIdleTimeoutMinutes(context, idleMin)
                    AppToast.success(context, Str.get(R.string.backend_settings_saved))
                    onDismiss()
                }
            ) {
                Text(Str.get(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(Str.get(R.string.cancel))
            }
        }
    )
}
