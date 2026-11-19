// app/src/main/java/com/UIN/Tool/ui/screen/files/FileSidebar.kt
package com.UIN.Tool.ui.screen.files

import android.os.Environment
import android.os.StatFs
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import com.UIN.Tool.utils.UIConfig
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.UIN.Tool.R
import com.UIN.Tool.utils.Str

/**
 * 文件页**左侧侧边栏**（用户反馈①）。
 *
 * ## 侧边栏与底部面板是两回事
 *
 * 用户反馈①明确点过名：「侧边栏是侧边栏，底部最近位置/书签是另一个，不要搞混了」。
 * 所以两块内容**不重叠**：
 *
 * - **侧边栏（本文件）** = 「能去的地方 / 能开的工具」—— 存储卷、编辑器、终端、设置、远程管理。
 *   它是**导航目的地**，按分组分节，点一下就跳过去并自动收起。
 * - **底部上滑面板** = 「我去过哪儿 / 我存了哪儿」—— 书签与历史。那是位置记录，不是功能入口。
 *
 * 早先这里曾把书签、常用位置一起塞进侧栏，结果两段长长的列表把真正的功能入口
 * 挤到屏幕外（见下方历史注释），现在按反馈把它们彻底挪到底部面板。
 *
 * ## 为什么不是 `ModalNavigationDrawer`
 *
 * 旧版是 `ModalNavigationDrawer`：一条从左边缘滑出的竖向面板，压在整个列表上，
 * 双窗格时恰好把**左半屏**盖住 —— 用户点完条目还要等它滑回去才知道跳没跳成功。
 * 现在由调用方渲染成「遮罩 + 固定 280dp 面板」的覆盖层，点遮罩或点任意条目即收起，
 * 行为一致但高度、宽度、动画都握在自己手里。
 *
 * ## 为什么用 `clickable` 而不是 `combinedClickable`
 *
 * 旧版每一行都带长按（`onLongClick = {}` 空实现）。抽屉内容在**可滚动容器**里，
 * 长按与滚动手势要靠 Compose 的手势竞赛决定谁赢，输了的那一方表现为「点了没反应」——
 * 这正是用户报告的「常用位置点了没跳转」。这些行本来就没有长按语义，
 * 直接用 [clickable] 少一道竞赛，点击必然落到导航上。
 */
@Composable
fun FileNavDrawer(
    onPick: (path: String) -> Unit,
    onOpenEditor: () -> Unit,
    onOpenTerminal: () -> Unit,
    onOpenApkExtract: () -> Unit,
    /** f20：后台任务（被「隐藏」收起来、但还在跑的文件操作）。 */
    onOpenTasks: () -> Unit,
    /** 回收站：删除的文件先放这里，可以恢复。 */
    onOpenTrash: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenRemote: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 16.dp),
        // 新拟态下每行都有柔影，2dp 会糊成一片 —— 拉开到 8dp 让每张「面」分得开。
        verticalArrangement = Arrangement.spacedBy(
            if (UIConfig.isNeumorphismEnabled()) 8.dp else 2.dp
        )
    ) {
        Text(
            text = Str.get(R.string.files_sidebar_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)
        )

        // ---- 组一：存储 ----
        DrawerGroupHeader(Str.get(R.string.files_sidebar_group_storage))
        // 走 [StatFs]：一次系统调用取整卷的总量/可用量。不能拿 File.getTotalSpace()
        // 逐目录聚合（慢且不准）。读放在 remember 里 —— 每次展开都要新数字，
        // 但同一帧内不该重复调用。
        val storage = remember { primaryStorageInfo() }
        if (storage != null) {
            StorageCard(
                name = Str.get(R.string.files_sidebar_internal),
                path = Environment.getExternalStorageDirectory().absolutePath,
                used = storage.used,
                total = storage.total,
                onClick = { onPick(storage.path) }
            )
        }

        Spacer(Modifier.height(10.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        Spacer(Modifier.height(6.dp))

        // ---- 组二：打开（另开一页的编辑器 / 终端 / 提取安装包）----
        // s.txt 第 3 条（用户）：不再把下面所有入口都归进「文件工具」一个类。
        // 按用户实际要做的事分组：先「打开」，再「管理」。
        DrawerGroupHeader(Str.get(R.string.files_drawer_group_open))
        DrawerRow(
            icon = Icons.Default.Edit,
            label = Str.get(R.string.files_drawer_editor),
            onClick = onOpenEditor
        )
        DrawerRow(
            painter = painterResource(R.drawable.ic_terminal_prompt),
            label = Str.get(R.string.files_drawer_terminal),
            onClick = onOpenTerminal
        )
        // 提取安装包（b26/c18）：与编辑器、终端同属「一次性的工具页」，夹在
        // 工具段里；设置与远程管理仍是末尾那对「开关型页面」，不拆开
        DrawerRow(
            icon = Icons.Default.Android,
            label = Str.get(R.string.apk_extract_title),
            onClick = onOpenApkExtract
        )
        // ---- 组三：管理（任务、回收站、设置、远程）----
        Spacer(Modifier.height(10.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        Spacer(Modifier.height(6.dp))
        DrawerGroupHeader(Str.get(R.string.files_drawer_group_manage))

        // f20：后台任务列的是「已经被收起来、还在跑」的文件操作，属于「管理」
        // 而不是「打开」—— 点开是一个清单弹窗，不是新页面。
        DrawerRow(
            icon = Icons.Default.Schedule,
            label = Str.get(R.string.files_tasks_title),
            onClick = onOpenTasks
        )
        // 回收站：用户要求「删除的文件也回收」—— 入口就摆在工具段末尾，与后台任务
        // 并列（一个是「被收起来的操作」，一个是「被收起来的文件」，同属后悔药）。
        DrawerRow(
            icon = Icons.Default.Delete,
            label = Str.get(R.string.files_trash),
            onClick = onOpenTrash
        )
        // 远程管理：把本机的能力开给别人用的开关型页面
        DrawerRow(
            icon = Icons.Default.Storage,
            label = Str.get(R.string.remote_title),
            onClick = onOpenRemote
        )
        // t.txt 第 3 条（用户）：文件管理设置放到最下方 —— 它是「改配置」的入口，
        // 不该夹在任务 / 回收站 / 远程这些「干活的入口」中间。
        DrawerRow(
            icon = Icons.Default.Settings,
            label = Str.get(R.string.files_settings),
            onClick = onOpenSettings
        )

        Spacer(Modifier.height(8.dp))
    }
}

/** 侧边栏的分组标题：一行小字，把「存储 / 打开 / 管理」三段隔开。 */
@Composable
private fun DrawerGroupHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 8.dp, top = 6.dp, bottom = 2.dp)
    )
}

/** 抽屉里的一行：图标 + 文字，整行可点。 */
@Composable
private fun DrawerRow(
    label: String,
    onClick: () -> Unit,
    icon: ImageVector? = null,
    painter: Painter? = null
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            // l.txt 第 15 条：侧边栏导航行在新拟态下各自浮起；关闭时保持透明圆角。
            .filesNeuSurface(
                shape = RoundedCornerShape(10.dp),
                cornerRadius = 10.dp,
                fallback = Color.Transparent
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 12.dp)
    ) {
        // Icon 没有「两个参数都可空」的重载 —— 终端用的是自带 drawable（Material 图标集里
        // 没有终端字形），其余用矢量图标，所以这里必须分两支走。
        val tint = MaterialTheme.colorScheme.primary
        when {
            painter != null -> Icon(
                painter = painter,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(22.dp)
            )
            icon != null -> Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(Modifier.width(14.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** [StatFs] 读出来的容量信息。[path] 是这条卡片对应的卷根。 */
private data class StorageInfo(val path: String, val used: Long, val total: Long)

/**
 * 读主存储卷的容量。返回 null = 读不到（极个别 ROM/权限受限时不给数字），
 * 调用方据此**整块不显示**而不是显示一行 `0 B / 0 B`。
 */
private fun primaryStorageInfo(): StorageInfo? = try {
    val dir = Environment.getExternalStorageDirectory()
    val stat = StatFs(dir.absolutePath)
    // availableBlocks vs freeBlocks：前者是**普通应用真正能写进去的**量，
    // 后者包含普通应用无权使用的保留块。用后者会显示「还有很多」然后写失败。
    val total = stat.blockCountLong * stat.blockSizeLong
    val avail = stat.availableBlocksLong * stat.blockSizeLong
    StorageInfo(path = dir.absolutePath, used = total - avail, total = total)
} catch (_: Exception) {
    null
}

// ==================== 组件 ====================

/**
 * 存储卡卡片：名称 + 用量条 + 「已用 / 总量 · 百分比」。
 *
 * 进度条**不给 indeterminate 形态**：容量是确定值，用不确定进度条会让用户以为还在读。
 * 颜色也不做「快满了变红」的语义 —— 那需要一个阈值，而阈值是因人而异的：
 * 一个从不装东西的人和一个囤 4K 视频的人，「快满了」的标准完全不同。
 */
@Composable
private fun StorageCard(
    name: String,
    path: String,
    used: Long,
    total: Long,
    onClick: () -> Unit
) {
    val frac = if (total > 0) (used.toFloat() / total).coerceIn(0f, 1f) else 0f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // l.txt 第 15 条：容量卡片同样按新拟态浮起。
            .filesNeuSurface(
                shape = RoundedCornerShape(10.dp),
                cornerRadius = 10.dp,
                fallback = Color.Transparent
            )
            // 纯 clickable：与 [DrawerRow] 同理，抽屉里不需要长按语义
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 存储卡图标用一个色块代替图标字形：Material 图标集里没有「SD 卡/内置存储」
        // 的通用字形，硬凑一个会让用户以为是在区分内外卡，而这里只有一个卷。
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = name.take(1),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(5.dp))
            LinearProgressIndicator(
                progress = { frac },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = Str.get(
                    R.string.files_sidebar_storage_used,
                    FileOps.formatSize(used),
                    FileOps.formatSize(total)
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}
