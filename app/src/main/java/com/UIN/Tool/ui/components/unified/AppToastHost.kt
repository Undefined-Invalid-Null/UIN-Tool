// app/src/main/java/com/UIN/Tool/ui/components/unified/AppToastHost.kt
package com.UIN.Tool.ui.components.unified

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.UIN.Tool.utils.AppToast

/**
 * m.txt 第 13 条：弹窗 / 整页 Dialog 自己的 AppToast 宿主。
 *
 * 原生 [androidx.compose.ui.window.Dialog] 是独立窗口，主界面 Theme 根节点的
 * SnackbarHost 会被它整个盖住，于是 AppToast 只能退回系统 Toast —— 用户看到的
 * 就是两种不同的提示样式。这里在弹窗窗口内部再挂一个同款 SnackbarHost，并向
 * [AppToast] 登记；弹窗存在期间所有提示都优先走这个宿主。
 *
 * 关闭时务必成对注销，避免已销毁的宿主留在栈里接走提示（那样提示就再也不显示）。
 */
@Composable
fun AppToastHost(modifier: Modifier = Modifier) {
    val host = remember { SnackbarHostState() }
    DisposableEffect(host) {
        AppToast.pushHost(host)
        onDispose { AppToast.popHost(host) }
    }
    SnackbarHost(hostState = host, modifier = modifier) { data ->
        AppToastSnackbar(data)
    }
}

/**
 * n.txt 第 8 条：**AppToast 只有一种长相**。
 *
 * 原先主界面（[com.UIN.Tool.ui.theme] 的根 SnackbarHost）与弹窗宿主各自写了一份
 * `Snackbar(...)`，两份配色只能靠人工同步；而一旦没有宿主退回系统 Toast，
 * 底色又完全交给 ROM —— 于是「同一条 AppToast」会出现三种底色。
 * 现在两个宿主共用这个 composable，系统 Toast 的外形也在
 * [AppToast] 里按同一组配色自绘。
 */
@Composable
fun AppToastSnackbar(
    data: SnackbarData,
    modifier: Modifier = Modifier
) {
    Snackbar(
        snackbarData = data,
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurface,
        actionColor = MaterialTheme.colorScheme.primary,
        dismissActionContentColor = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
