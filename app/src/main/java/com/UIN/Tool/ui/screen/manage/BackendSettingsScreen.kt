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
import com.UIN.Tool.ui.components.unified.*
import com.UIN.Tool.ui.theme.AppColors
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
    var idleInput by remember {
        mutableStateOf(
            if (BackendConfig.isInfiniteIdleTimeout(context)) ""
            else BackendConfig.getIdleTimeoutMinutes(context).toString()
        )
    }
    var keepAlive by remember {
        mutableStateOf(BackendConfig.isKeepAliveEnabled(context))
    }

    val isReal = impl == BackendConfig.IMPL_REAL
    val setupCode = BackendConfig.buildRealTermuxSetupCode(context)
    val idlePresets = listOf(3, 5, 10, 15)

    fun onIdleInputChange(text: String) {
        idleInput = text.filter { it.isDigit() }.take(4)
        idleMin = idleInput.toIntOrNull() ?: BackendConfig.IDLE_TIMEOUT_INFINITE
    }

    fun onIdlePreset(minutes: Int) {
        idleMin = minutes
        idleInput = minutes.toString()
    }

    fun onIdleInfinite() {
        idleMin = BackendConfig.IDLE_TIMEOUT_INFINITE
        idleInput = ""
    }

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
        containerColor = AppColors.pageBackground(),
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
            UnifiedSectionTitle(Str.get(R.string.backend_implementation))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    BackendConfig.IMPL_BUILTIN to Str.get(R.string.builtin_termux),
                    BackendConfig.IMPL_REAL to Str.get(R.string.real_termux)
                ).forEach { (key, label) ->
                    UnifiedChip(
                        label = label,
                        selected = impl == key,
                        onClick = { impl = key },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            UnifiedCaptionText(
                if (isReal)
                    Str.get(R.string.real_termux_will_call_com_termux_t)
                else
                    Str.get(R.string.builtin_termux_is_a_simplified_li),
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
            )

            if (isReal) {
                UnifiedSectionTitle(Str.get(R.string.backend_environment))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        BackendConfig.ENV_TERMUX to Str.get(R.string.termux_local),
                        BackendConfig.ENV_PROOT to Str.get(R.string.proot_container)
                    ).forEach { (key, label) ->
                        UnifiedChip(
                            label = label,
                            selected = env == key,
                            onClick = { env = key },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                if (env == BackendConfig.ENV_PROOT) {
                    UnifiedTextField(
                        value = container,
                        onValueChange = { container = it },
                        label = Str.get(R.string.proot_container_name),
                        placeholder = "alpine",
                        modifier = Modifier.fillMaxWidth()
                    )
                    UnifiedCaptionText(
                        Str.get(R.string.run_proot_distro_list_in_termux_t),
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }

            UnifiedSectionTitle(Str.get(R.string.idle_recycle_timeout_minutes))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                idlePresets.forEach { minutes ->
                    UnifiedChip(
                        label = "$minutes ${Str.get(R.string.min_)}",
                        selected = idleMin == minutes,
                        onClick = { onIdlePreset(minutes) },
                        modifier = Modifier.weight(1f)
                    )
                }
                UnifiedChip(
                    label = Str.get(R.string.idle_infinite),
                    selected = idleMin == BackendConfig.IDLE_TIMEOUT_INFINITE,
                    onClick = { onIdleInfinite() },
                    modifier = Modifier.weight(1f)
                )
            }
            UnifiedTextField(
                value = idleInput,
                onValueChange = { onIdleInputChange(it) },
                label = Str.get(R.string.idle_custom_minutes),
                placeholder = "5",
                supportingText = Str.get(R.string.backend_will_be_stopped_after_being_i),
                modifier = Modifier.fillMaxWidth()
            )

            if (isReal) {
                // ==================== 后台保活 ====================
                UnifiedSectionTitle(Str.get(R.string.keep_alive_title))
                UnifiedCard(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    Str.get(R.string.keep_alive),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    Str.get(R.string.keep_alive_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            UnifiedSwitch(
                                checked = keepAlive,
                                onCheckedChange = {
                                    keepAlive = it
                                    com.UIN.Tool.plugin.SharedSupervisor.setKeepAlive(it)
                                    if (it) {
                                        requestIgnoreBatteryOptimizations(context)
                                    }
                                }
                            )
                        }
                        // 保活权限状态提示
                        val shizukuOk = remember {
                            com.UIN.Tool.utils.PermissionUtils.hasShizukuPermission()
                        }
                        val dhizukuOk = remember {
                            com.UIN.Tool.utils.PermissionUtils.hasDhizukuPermission(context)
                        }
                        Text(
                            buildString {
                                append(Str.get(R.string.keep_alive_privilege_status))
                                append(
                                    when {
                                        shizukuOk -> Str.get(R.string.keep_alive_privilege_shizuku)
                                        dhizukuOk -> Str.get(R.string.keep_alive_privilege_dhizuku)
                                        else -> Str.get(R.string.keep_alive_privilege_none)
                                    }
                                )
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (shizukuOk || dhizukuOk)
                                MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.error
                        )
                    }
                }

                // ==================== 实体 Termux 共享调度器说明 ====================
                UnifiedCard(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            Str.get(R.string.start_backend_supervisor),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            Str.get(R.string.shared_supervisor_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        // RUN_COMMAND 权限与调度器状态提示
                        val hasPerm = remember {
                            com.UIN.Tool.plugin.RealTermuxRuntime.isRunCommandPermissionGranted(context)
                        }
                        val alive = remember {
                            com.UIN.Tool.plugin.SharedSupervisor.isSupervisorAlive()
                        }
                        Text(
                            buildString {
                                append(Str.get(R.string.run_command_permission_status))
                                append(if (hasPerm) Str.get(R.string.permission_status_granted)
                                else Str.get(R.string.permission_status_denied))
                                append("  ")
                                append(Str.get(R.string.supervisor_status))
                                append(if (alive) Str.get(R.string.supervisor_status_running)
                                else Str.get(R.string.supervisor_status_stopped))
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (hasPerm && alive)
                                MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.error
                        )
                        if (!hasPerm) {
                            Text(
                                Str.get(R.string.real_termux_run_command_permission_grant_guide),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // ==================== 实体 Termux 初始化命令（可复制） ====================
                UnifiedCard(
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
                        UnifiedIconButton(
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

            UnifiedButton(
                text = Str.get(R.string.save),
                onClick = {
                    BackendConfig.setImplementation(context, impl)
                    if (isReal) {
                        BackendConfig.setEnvironment(context, env)
                        BackendConfig.setContainer(context, container.trim())
                        BackendConfig.setKeepAliveEnabled(context, keepAlive)
                    }
                    BackendConfig.setIdleTimeoutMinutes(context, idleMin)
                    // 保存后预热共享 supervisor（容器/会话在软件打开期间常驻）
                    com.UIN.Tool.plugin.SharedSupervisor.prewarm(context)
                    AppToast.success(context, Str.get(R.string.backend_settings_saved))
                    onBack()
                },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/** 请求忽略电池优化（需用户确认；部分厂商机型可能直接返回失败）。 */
private fun requestIgnoreBatteryOptimizations(context: android.content.Context) {
    try {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            val pm = context.getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
            if (!pm.isIgnoringBatteryOptimizations(context.packageName)) {
                val intent = android.content.Intent(
                    android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    android.net.Uri.parse("package:${context.packageName}")
                ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            }
        }
    } catch (_: Exception) {
    }
}
