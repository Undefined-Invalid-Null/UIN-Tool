package com.UIN.Tool.ui.screen.permission

import com.UIN.Tool.R
import com.UIN.Tool.utils.Str
import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.UIN.Tool.core.di.ServiceLocator
import com.UIN.Tool.domain.model.PluginInfo
import com.UIN.Tool.plugin.PluginPermissionManager
import com.UIN.Tool.ui.components.unified.*
import com.UIN.Tool.utils.AppToast
import com.UIN.Tool.utils.PermissionUtils
import com.UIN.Tool.ui.theme.AppDimens
import com.UIN.Tool.ui.theme.AppColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "PluginPermissionScreen"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PluginPermissionScreen(initialPluginId: String? = null) {
    val context = LocalContext.current
    val activity = context as? Activity
    val pluginManager = ServiceLocator.getPluginManager()
    val scope = rememberCoroutineScope()

    var plugins by remember { mutableStateOf<List<PluginInfo>>(emptyList()) }
    // 转屏 / 进程重建后还要停在原来的插件详情页（旧实现总选第一个，丢状态看不出来）。
    var selectedPluginId by rememberSaveable { mutableStateOf<String?>(null) }
    // 是「从列表点进来」还是「深链直接进来」：只有前者，返回键才先回列表。
    var enteredFromList by rememberSaveable { mutableStateOf(false) }
    var pluginPermissions by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    var isLoading by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }
    var lastRefreshTime by remember { mutableStateOf<String?>(null) }
    var pluginSearchText by rememberSaveable { mutableStateOf("") }
    var showPermissionInfo by remember { mutableStateOf(false) }
    val pullRefreshState = rememberPullToRefreshState()

    // 列表每行的「已授权 n/m」：放在 IO 线程统一算一次。
    // 直接在行内算会在每次重组（搜索框每敲一个字）重复 N×M 次权限查询，
    // 其中特殊权限走的是 binder IPC，列表一长就会掉帧。
    var permissionRevision by remember { mutableIntStateOf(0) }
    val grantedCounts by produceState(emptyMap<String, Int>(), plugins, permissionRevision) {
        value = withContext(Dispatchers.IO) {
            plugins.associate { plugin ->
                plugin.pluginId to plugin.permissions.count {
                    PluginPermissionManager.checkPluginPermission(context, plugin.pluginId, it)
                }
            }
        }
    }

    fun loadPluginPermissions(pluginId: String) {
        val perms = PluginPermissionManager.getPluginDeclaredPermissions(context, pluginId)
        val status = perms.associateWith { permission ->
            PluginPermissionManager.checkPluginPermission(context, pluginId, permission)
        }
        pluginPermissions = status
        // 详情页勾选后，列表里的「已授权 n/m」也要跟着变。
        permissionRevision++
    }

    fun refreshPermissions() {
        // loadPluginPermissions 自己会 permissionRevision++，这里不要再加一次。
        selectedPluginId?.let { loadPluginPermissions(it) }
    }

    val settingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        refreshPermissions()
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        refreshPermissions()
        AppToast.info(
            context,
            if (granted) Str.get(R.string.permission_granted) else Str.get(R.string.permission_denied)
        )
    }

    // ✅ 批量授权用 RequestMultiplePermissions 驱动，结果回调即时刷新列表，
    //    替代原先 ActivityCompat.requestPermissions(1001) 无回调、UI 停在旧状态的问题
    val multiPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        isLoading = false
        refreshPermissions()
    }

    /**
     * 请求所有缺失权限
     */
    fun requestAllPermissions() {
        selectedPluginId?.let { pluginId ->
            val activity = context as? Activity
            if (activity == null) {
                AppToast.error(context, Str.get(R.string.failed_to_get_activity))
                return
            }

            isLoading = true

            // 批量授权前先解除全部封禁（撤销），否则被封禁权限仍被视为缺失
            PluginPermissionManager.unblockAllDeclaredPermissions(context, pluginId)

            val missing = PluginPermissionManager.getMissingPermissions(context, pluginId)
            if (missing.isEmpty()) {
                isLoading = false
                refreshPermissions()
                AppToast.info(context, Str.get(R.string.all_permissions_granted))
                return
            }

            // 分离普通权限和特殊权限
            val normal = missing.filter { !PluginPermissionManager.isSpecialPermission(it) }
            val special = missing.filter { PluginPermissionManager.isSpecialPermission(it) }

            // 请求普通权限（走 launcher，回调里统一刷新）
            if (normal.isNotEmpty()) {
                multiPermissionLauncher.launch(normal.toTypedArray())
            } else {
                isLoading = false
                refreshPermissions()
            }

            // 引导特殊权限
            if (special.isNotEmpty()) {
                PluginPermissionManager.openAppSettings(activity)
                AppToast.info(context, Str.get(R.string.enable_special_permissions_manually_in))
            }
        }
    }

    /**
     * 切换单个权限：支持授权与撤销（封禁）。
     * - 已生效（granted=true）→ 撤销：写入插件层封禁，插件即失去使用能力；
     * - 未生效且已封禁 → 解除封禁（并按需发起真实运行时请求）；
     * - 未生效且未封禁 → 走原授权流程。
     */
    fun togglePermission(pluginId: String, permission: String) {
        val activity = context as? Activity
        if (activity == null) {
            AppToast.error(context, Str.get(R.string.failed_to_get_activity))
            return
        }
        val currentlyGranted = PluginPermissionManager.checkPluginPermission(context, pluginId, permission)
        if (currentlyGranted) {
            PluginPermissionManager.setPermissionBlocked(context, pluginId, permission, true)
            AppToast.info(context, Str.get(R.string.permission_revoked_permission, permission))
            refreshPermissions()
            return
        }
        // 已封禁 → 先解除封禁
        if (PluginPermissionManager.isPermissionBlocked(context, pluginId, permission)) {
            PluginPermissionManager.setPermissionBlocked(context, pluginId, permission, false)
            AppToast.info(context, Str.get(R.string.permission_unblocked_permission, permission))
            refreshPermissions()
            return
        }
        // 伪权限无需运行时授权，仅声明即可 → 解除封禁后即为已授权
        if (PluginPermissionManager.isPseudoPermission(permission)) {
            refreshPermissions()
            return
        }
        if (PermissionUtils.isSpecialPermission(permission)) {
            PermissionUtils.requestSpecialPermission(
                activity,
                permission,
                settingsLauncher
            )
            return
        }
        permissionLauncher.launch(permission)
    }

    /**
     * 批量撤销：一键封禁该插件声明的全部权限
     */
    fun revokeAllPermissions() {
        val pluginId = selectedPluginId ?: return
        PluginPermissionManager.blockAllDeclaredPermissions(context, pluginId)
        AppToast.info(context, Str.get(R.string.all_permissions_revoked))
        refreshPermissions()
    }

    LaunchedEffect(Unit) {
        // 首次扫描放到 IO：PluginManager 的去抖窗口一过就会**同步**遍历插件目录、
        // 逐个读 plugin.json，放在主线程会掉帧。
        withContext(Dispatchers.IO) { pluginManager.refreshPlugins() }
    }

    // 插件列表跟着 StateFlow 走：refreshPlugins 命中去抖时是 500ms 后才更新，
    // 一次性快照会停在旧列表上（刷新后看不到新装 / 新卸的插件）。
    val allPlugins by pluginManager.plugins.collectAsState()
    LaunchedEffect(allPlugins) {
        // 权限管理页仅管理 Web 插件（有/无后端均可）；原生/CUI 插件不受权限管理页管控
        plugins = allPlugins.filter { it.uiType == "web" }
    }

    // y.txt 第 1 条（用户）：进来先看**插件列表**；只有调用方点名了某个插件
    // （深链 / 从插件页跳转）才直接落到它的权限页。
    //
    // 只认一次：用户从详情页退回列表后再下拉刷新，不能又把他弹回那个插件。
    var initialTargetApplied by remember { mutableStateOf(false) }
    LaunchedEffect(plugins) {
        if (!initialTargetApplied) {
            val target = initialPluginId?.takeIf { id -> plugins.any { it.pluginId == id } }
            if (target != null) {
                initialTargetApplied = true
                selectedPluginId = target
                loadPluginPermissions(target)
            }
        }
    }

    // 详情页开着的时候插件被卸载：退回列表，而不是停在一张「请选择插件」的空详情上。
    // `plugins.isNotEmpty()` 是给进程重建留的余地 —— 恢复出来的 selectedPluginId
    // 要等插件列表加载完再判断。
    LaunchedEffect(plugins, selectedPluginId) {
        if (plugins.isNotEmpty() && selectedPluginId != null &&
            plugins.none { it.pluginId == selectedPluginId }
        ) {
            selectedPluginId = null
        }
    }

    val selectedPlugin = plugins.find { it.pluginId == selectedPluginId }
    val inDetail = selectedPluginId != null
    // 只有「从列表点进来」才把返回键先给列表；深链直接进来的详情页，返回即退出。
    val canReturnToList = inDetail && enteredFromList

    // 详情页里按返回键先回插件列表，而不是直接退出整个页面（经典两级页面）。
    BackHandler(enabled = canReturnToList) {
        // 退回列表：loadPluginPermissions 已经在每次勾选时刷新过计数，这里不必再加。
        selectedPluginId = null
    }

    Scaffold(
        containerColor = AppColors.pageBackground(),
        topBar = {
            ManageTopAppBar(
                // 详情页标题就是插件名：一眼看出「现在管的是谁」。
                titleText = selectedPlugin?.name ?: Str.get(R.string.plugin_permissions),
                onBack = {
                    if (canReturnToList) {
                        selectedPluginId = null
                    } else {
                        activity?.finish()
                    }
                },
                actions = {
                    // v.txt 第 3 条（用户）：权限说明不再写死在页面里 ——
                    // 点这个信息图标打开 assets/help/plugin_permissions.json 的内容。
                    // w.txt 第 3 条（用户）：图标改用灰色（次要文字色），不再跟主题主色抢眼。
                    UnifiedIconButton(
                        icon = Icons.Default.Info,
                        onClick = { showPermissionInfo = true },
                        contentDescription = Str.get(R.string.info),
                        tint = AppColors.textSecondary()
                    )
                }
            )
        }
    ) { paddingValues ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                scope.launch {
                    isRefreshing = true
                    // 列表也要跟着刷新：插件可能刚装上 / 刚卸载，只刷权限会留下旧行。
                    // 去抖窗口内那次是 500ms 后才真正扫盘，所以这里等一小会儿再收指示器，
                    // 免得「下拉一下什么都没变」。
                    withContext(Dispatchers.IO) { pluginManager.refreshPlugins() }
                    delay(600)
                    refreshPermissions()
                    AppToast.info(context, Str.get(R.string.permission_status_refreshed))
                    lastRefreshTime = currentTimeString()
                    isRefreshing = false
                }
            },
            state = pullRefreshState,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            indicator = {
                PullRefreshIndicator(
                    isRefreshing = isRefreshing,
                    state = pullRefreshState,
                    modifier = Modifier.align(Alignment.TopCenter)
                )
            }
        ) {
            if (inDetail) {
                PermissionList(
                    plugin = selectedPlugin,
                    permissions = pluginPermissions,
                    isLoading = isLoading,
                    lastRefreshTime = lastRefreshTime,
                    onToggle = { permission ->
                        val pid = selectedPluginId
                        if (!isLoading && pid != null) {
                            togglePermission(pid, permission)
                        }
                    },
                    onGrantAll = { requestAllPermissions() },
                    onRevokeAll = { revokeAllPermissions() }
                )
            } else {
                PluginPickerList(
                    plugins = plugins,
                    grantedCounts = grantedCounts,
                    searchText = pluginSearchText,
                    onSearchChange = { pluginSearchText = it },
                    lastRefreshTime = lastRefreshTime,
                    onPick = { plugin ->
                        selectedPluginId = plugin.pluginId
                        enteredFromList = true
                        loadPluginPermissions(plugin.pluginId)
                    }
                )
            }
        }
    }

    if (showPermissionInfo) {
        PluginPermissionInfoDialog(onDismiss = { showPermissionInfo = false })
    }
}

/**
 * y.txt 第 1 条（用户）：第一级 —— **插件列表**（经典列表页，一行一个插件）。
 *
 * 图标 + 名称 + 版本 + 「已授权 n/m」+ 右箭头：不点进去也能看出哪个插件还缺权限。
 * 搜索框在列表正上方，按名称 / 插件 ID 过滤。
 */
@Composable
private fun PluginPickerList(
    plugins: List<PluginInfo>,
    grantedCounts: Map<String, Int>,
    searchText: String,
    onSearchChange: (String) -> Unit,
    lastRefreshTime: String?,
    onPick: (PluginInfo) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        LastUpdatedCaption(
            time = lastRefreshTime,
            modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
        )

        // 空态也放进 LazyColumn：PullToRefreshBox 靠子级 scrollable 的 nestedScroll
        // 收手势，不可滚动的 Column/Box 会让「插件全被卸载」时彻底拉不动刷新。
        if (plugins.isEmpty()) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                item {
                    UnifiedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.Extension,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            UnifiedBodyText(Str.get(R.string.no_web_plugins_manageable))
                            UnifiedCaptionText(Str.get(R.string.only_web_plugins_can_be_managed))
                        }
                    }
                }
            }
            return@Column
        }

        UnifiedTextField(
            value = searchText,
            onValueChange = onSearchChange,
            placeholder = Str.get(R.string.search_plugin_hint),
            leadingIcon = Icons.Default.Search,
            showClearButton = true,
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))

        val filtered = plugins.filter {
            searchText.isBlank() ||
                it.name.contains(searchText, ignoreCase = true) ||
                it.pluginId.contains(searchText, ignoreCase = true)
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (filtered.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillParentMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        UnifiedBodyText(Str.get(R.string.no_matching_plugins))
                    }
                }
            } else {
                // 同一个 pluginId 装了两份（插件目录去重只在 manager 里做）时，
                // 只拿 pluginId 当 key 会让 LazyColumn 直接抛「key already used」。
                items(filtered, key = { "${it.pluginId}#${it.name}" }) { plugin ->
                    PluginListRow(
                        plugin = plugin,
                        granted = grantedCounts[plugin.pluginId] ?: 0,
                        onClick = { onPick(plugin) }
                    )
                }
            }
        }
    }
}

/**
 * y.txt 第 1 条（用户）：第二级 —— **某个插件的权限清单**。
 *
 * 顶部是插件信息与「全部授权 / 全部撤销」，下面逐条勾选。返回键 / 标题栏返回
 * 都回到插件列表（在 [PluginPermissionScreen] 里处理），不会再一口气退出去。
 */
@Composable
private fun PermissionList(
    plugin: PluginInfo?,
    permissions: Map<String, Boolean>,
    isLoading: Boolean,
    lastRefreshTime: String?,
    onToggle: (String) -> Unit,
    onGrantAll: () -> Unit,
    onRevokeAll: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(AppDimens.cardSpacing)
    ) {
        item {
            LastUpdatedCaption(
                time = lastRefreshTime,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        if (plugin == null) {
            item {
                Box(
                    modifier = Modifier
                        .fillParentMaxWidth()
                        .padding(top = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    UnifiedBodyText(Str.get(R.string.please_select_a_plugin))
                }
            }
        } else {
            item {
                UnifiedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp)
                    ) {
                        UnifiedBodyText(Str.get(R.string.plugin_plugin_name, plugin.name))
                        UnifiedCaptionText(
                            Str.get(
                                R.string.id_plugin_pluginid_version_plugin_ve,
                                plugin.pluginId,
                                plugin.versionName
                            )
                        )
                        UnifiedCaptionText(
                            Str.get(
                                R.string.declared_permissions_plugin_permissi,
                                plugin.permissions.size
                            )
                        )
                    }
                }
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    UnifiedButton(
                        text = Str.get(R.string.grant_all_permissions),
                        icon = Icons.Default.Check,
                        onClick = onGrantAll,
                        modifier = Modifier.weight(1f),
                        enabled = !isLoading,
                        loading = isLoading
                    )
                    UnifiedButton(
                        text = Str.get(R.string.revoke_all_permissions),
                        icon = Icons.Default.Close,
                        onClick = onRevokeAll,
                        modifier = Modifier.weight(1f),
                        enabled = !isLoading,
                        variant = ButtonVariant.Destructive
                    )
                }
            }
            if (permissions.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillParentMaxWidth()
                            .padding(top = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.Security,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            UnifiedTitleText(Str.get(R.string.this_plugin_declares_no_permissions_2))
                            UnifiedCaptionText(Str.get(R.string.plugins_declare_their_required))
                        }
                    }
                }
            } else {
                items(permissions.entries.toList()) { (permission, granted) ->
                    UnifiedCard(
                        onClick = { onToggle(permission) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = PermissionUtils.getPermissionDisplayName(permission),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (granted) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    }
                                )
                                UnifiedCaptionText(permission)
                                if (PermissionUtils.isSpecialPermission(permission)) {
                                    UnifiedCaptionText(
                                        Str.get(R.string.special_permission_requires_enabling_2),
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                            Checkbox(
                                checked = granted,
                                onCheckedChange = { onToggle(permission) },
                                colors = CheckboxDefaults.colors(
                                    checkmarkColor = MaterialTheme.colorScheme.onPrimary,
                                    checkedColor = MaterialTheme.colorScheme.primary,
                                    uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                enabled = !isLoading
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 插件列表里的一行：图标 + 名称 + 「ID | 版本」+ 已授权数量 + 右箭头。
 *
 * [granted] 由外层在 IO 线程统一算好（见 [PluginPermissionScreen] 的 `grantedCounts`）——
 * 行内现算会在每次重组时重复 N×M 次权限查询，特殊权限那几条还是 binder IPC。
 */
@Composable
private fun PluginListRow(
    plugin: PluginInfo,
    granted: Int,
    onClick: () -> Unit
) {
    val total = plugin.permissions.size
    UnifiedCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PluginGridIcon(plugin, 40.dp)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = plugin.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                UnifiedCaptionText(
                    Str.get(
                        R.string.id_plugin_pluginid_version_plugin_ve,
                        plugin.pluginId,
                        plugin.versionName
                    )
                )
                if (total == 0) {
                    // 0 / 0 读起来像「还没加载」，直接说清楚：这个插件不需要权限。
                    UnifiedCaptionText(Str.get(R.string.this_plugin_declares_no_permissions_2))
                } else {
                    UnifiedCaptionText(
                        Str.get(R.string.permissions_granted_summary, granted, total),
                        color = if (granted == total) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            null
                        }
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = AppColors.textSecondary()
            )
        }
    }
}

/** 插件图标：读插件目录里的 icon，失败时退回首字母（与插件管理页同一套回退）。 */
@Composable
private fun PluginGridIcon(plugin: PluginInfo, size: androidx.compose.ui.unit.Dp) {
    var bitmap by remember(plugin.pluginId) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(plugin.pluginId) {
        withContext(Dispatchers.IO) {
            val dir = java.io.File(com.UIN.Tool.constants.AppConstants.PLUGIN_DIR, plugin.pluginId)
            val iconFile = java.io.File(dir, plugin.icon.ifEmpty { "icon.png" })
            if (iconFile.exists()) {
                try {
                    bitmap = android.graphics.BitmapFactory.decodeFile(iconFile.absolutePath)
                } catch (_: Exception) {
                }
            }
        }
    }
    val current = bitmap
    Box(modifier = Modifier.size(size), contentAlignment = Alignment.Center) {
        if (current != null) {
            androidx.compose.foundation.Image(
                bitmap = current.asImageBitmap(),
                contentDescription = plugin.name,
                modifier = Modifier
                    .size(size)
                    .clip(RoundedCornerShape(AppDimens.radiusSmall))
            )
        } else {
            Text(
                text = plugin.name.take(1).uppercase(),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
