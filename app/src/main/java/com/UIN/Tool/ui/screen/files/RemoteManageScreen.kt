// app/src/main/java/com/UIN/Tool/ui/screen/files/RemoteManageScreen.kt
package com.UIN.Tool.ui.screen.files

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.UIN.Tool.ui.theme.AppDimens
import com.UIN.Tool.R
import com.UIN.Tool.core.remote.RemoteServerService
import com.UIN.Tool.ui.components.unified.ButtonVariant
import com.UIN.Tool.ui.components.unified.ManageTopAppBar
import com.UIN.Tool.ui.components.unified.UnifiedButton
import com.UIN.Tool.ui.components.unified.UnifiedCard
import com.UIN.Tool.ui.components.unified.UnifiedCaptionText
import com.UIN.Tool.ui.components.unified.UnifiedSectionTitle
import com.UIN.Tool.ui.components.unified.UnifiedTextField
import com.UIN.Tool.ui.theme.AppColors
import com.UIN.Tool.utils.AppToast
import com.UIN.Tool.utils.Str
import java.io.File

/**
 * 远程管理页：FTP 与 HTTP 两个服务端的开关、地址、配置与连接日志。
 *
 * ## 生命周期
 *
 * 两个服务端都挂在 [RemoteManager] 单例上，**离开本页不会停** —— 起服务是为了让电脑那头
 * 连过来，人多半会切回文件页继续干活；页面一关就掐 socket，这功能等于残废。
 * 要停就回本页按「停止」，或者杀进程。
 *
 * ## 账号密码两个服务端共用
 *
 * 只有一套，不是两套：用户心智里「远程管理」是一个开关加一个口令，
 * 拆成 FTP 口令 / HTTP 口令只会让人不确定「我设的到底是哪一个」。
 * 改动即时下发到两个服务端，**新连接**立刻生效（已登录的会话保持原样，
 * 这也是 FTP 客户端的常规预期 —— 重连才换凭据）。
 *
 * ## 端口为什么不能默认 21 / 80
 *
 * 绑 80 与 21 都要 root，Android 上必然 `BindException`。2121 与 8080 是无特权端口里
 * 最有辨识度的两个，用户一眼就知道「这是 FTP / 这是 Web」。
 */
@Composable
fun RemoteManageScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val ftp = RemoteManager.ftp
    val http = RemoteManager.http

    val ftpState by ftp.state.collectAsState()
    val httpState by http.state.collectAsState()

    // 只要有服务端在监听就挂前台通知（用户答复 4），两个都停了就撤。
    // 四个键一起算：先起 FTP 再起 HTTP 时 `anyRunning` 不会翻转，只盯一个键就漏掉第二个 ——
    // 通知里少一行地址，而那正是用户从通知里要看到的东西。
    // **不在页面销毁时停**：服务端是离开页面也该继续跑的，dispose 顺手掐掉前台身份
    // 就等于把「后台被回收」的那道保护又还了回去。
    LaunchedEffect(ftpState.running, ftpState.port, httpState.running, httpState.port) {
        if (ftpState.running || httpState.running) {
            RemoteServerService.start(context)
        } else {
            RemoteServerService.stop(context)
        }
    }
    val ftpLog by ftp.log.collectAsState()
    val httpLog by http.log.collectAsState()

    var ftpPort by rememberSaveable { mutableStateOf(FilePrefs.remoteFtpPort().toString()) }
    var httpPort by rememberSaveable { mutableStateOf(FilePrefs.remoteHttpPort().toString()) }
    var root by rememberSaveable { mutableStateOf(FilePrefs.remoteRoot()) }
    var user by rememberSaveable { mutableStateOf(FilePrefs.remoteUser()) }
    var pass by rememberSaveable { mutableStateOf(FilePrefs.remotePass()) }

    // 局域网地址只在进页面时取一次 —— 它来自网卡枚举，跟着重组反复算纯属浪费
    val ip = remember { lanIpv4() }

    /** 共享目录：留空就跟随文件页当前的根。 */
    fun resolvedRoot(): File = File(root.trim().ifEmpty { FilePrefs.rootPath() })

    fun copyAddress(addr: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("UIN_Tool", addr))
        AppToast.success(context, Str.get(R.string.copied_to_clipboard))
    }

    /** 把共享设置写回 prefs 并即时下发给两个服务端。 */
    fun applyShared() {
        FilePrefs.setRemoteRoot(root)
        FilePrefs.setRemoteUser(user)
        FilePrefs.setRemotePass(pass)
        val dir = resolvedRoot()
        ftp.root = dir
        http.root = dir
        ftp.username = user
        ftp.password = pass
        http.username = user
        http.password = pass
    }

    /** 启动前的两道闸：端口合法、目录存在。两道都过了才碰 socket。 */
    fun validatedPort(raw: String): Int? {
        val port = raw.trim().toIntOrNull()
        return if (port != null && port in 1..65535) port else null
    }

    fun startFtp() {
        val port = validatedPort(ftpPort)
        if (port == null) {
            AppToast.warning(context, Str.get(R.string.remote_invalid_port))
            return
        }
        if (!resolvedRoot().isDirectory) {
            AppToast.warning(context, Str.get(R.string.remote_root_missing))
            return
        }
        FilePrefs.setRemoteFtpPort(port)
        applyShared()
        ftp.start(port)
    }

    fun startHttp() {
        val port = validatedPort(httpPort)
        if (port == null) {
            AppToast.warning(context, Str.get(R.string.remote_invalid_port))
            return
        }
        if (!resolvedRoot().isDirectory) {
            AppToast.warning(context, Str.get(R.string.remote_root_missing))
            return
        }
        FilePrefs.setRemoteHttpPort(port)
        applyShared()
        http.start(port)
    }

    Scaffold(
        containerColor = AppColors.pageBackground(),
        topBar = {
            ManageTopAppBar(
                titleText = Str.get(R.string.remote_title),
                // 与文件页同处 MainActivity Scaffold 内，状态栏已由外层让出。
                windowInsets = WindowInsets(0, 0, 0, 0),
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
            // ==================== 共享设置 ====================
            UnifiedSectionTitle(Str.get(R.string.remote_root))
            UnifiedTextField(
                value = root,
                onValueChange = {
                    root = it
                    // 即时下发：目录改了，**下一个连接**就该看到新目录，不必先停服务
                    applyShared()
                },
                label = Str.get(R.string.remote_root),
                placeholder = FilePrefs.rootPath(),
                modifier = Modifier.fillMaxWidth()
            )
            UnifiedCaptionText(
                Str.get(R.string.remote_root_hint),
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
            )

            UnifiedTextField(
                value = user,
                onValueChange = {
                    user = it
                    applyShared()
                },
                label = Str.get(R.string.remote_account),
                modifier = Modifier.fillMaxWidth()
            )
            UnifiedTextField(
                value = pass,
                onValueChange = {
                    pass = it
                    applyShared()
                },
                label = Str.get(R.string.remote_password),
                isPassword = true,
                showClearButton = true,
                modifier = Modifier.fillMaxWidth()
            )
            if (user.isBlank() && pass.isBlank()) {
                // 不设口令是允许的（家里局域网图个方便），但必须让用户知道代价
                UnifiedCaptionText(
                    Str.get(R.string.remote_no_auth_warning),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }

            // ==================== FTP ====================
            UnifiedSectionTitle(Str.get(R.string.remote_ftp_title))
            UnifiedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(AppDimens.cardSpacing)
                ) {
                    StatusRow(running = ftpState.running)
                    UnifiedCaptionText(
                        Str.get(R.string.remote_ftp_hint),
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                    AddressRow(
                        address = displayAddress(
                            scheme = "ftp",
                            ip = ip,
                            running = ftpState.running,
                            runningPort = ftpState.port,
                            configuredPort = FilePrefs.DEFAULT_FTP_PORT,
                            fallback = ftpPort
                        ),
                        onCopy = {
                            displayAddress(
                                scheme = "ftp", ip = ip, running = ftpState.running,
                                runningPort = ftpState.port,
                                configuredPort = FilePrefs.DEFAULT_FTP_PORT,
                                fallback = ftpPort
                            )?.let { copyAddress(it) }
                        }
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        UnifiedTextField(
                            value = ftpPort,
                            onValueChange = { ftpPort = it },
                            label = Str.get(R.string.remote_port),
                            // 运行中改端口不会生效，反而让人以为改了 —— 直接锁住
                            enabled = !ftpState.running,
                            modifier = Modifier.weight(1f)
                        )
                        UnifiedButton(
                            text = Str.get(
                                if (ftpState.running) R.string.remote_stop else R.string.remote_start
                            ),
                            onClick = { if (ftpState.running) ftp.stop() else startFtp() },
                            variant = if (ftpState.running) ButtonVariant.Destructive
                            else ButtonVariant.Primary,
                            modifier = Modifier.align(Alignment.CenterVertically)
                        )
                    }
                    if (ftpState.error != null) {
                        UnifiedCaptionText(
                            Str.get(R.string.remote_start_failed, ftpState.error ?: ""),
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }
                }
            }

            // ==================== HTTP ====================
            UnifiedSectionTitle(Str.get(R.string.remote_http_title))
            UnifiedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(AppDimens.cardSpacing)
                ) {
                    StatusRow(running = httpState.running)
                    UnifiedCaptionText(
                        Str.get(R.string.remote_http_hint),
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                    AddressRow(
                        address = displayAddress(
                            scheme = "http",
                            ip = ip,
                            running = httpState.running,
                            runningPort = httpState.port,
                            configuredPort = FilePrefs.DEFAULT_HTTP_PORT,
                            fallback = httpPort
                        ),
                        onCopy = {
                            displayAddress(
                                scheme = "http", ip = ip, running = httpState.running,
                                runningPort = httpState.port,
                                configuredPort = FilePrefs.DEFAULT_HTTP_PORT,
                                fallback = httpPort
                            )?.let { copyAddress(it) }
                        }
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        UnifiedTextField(
                            value = httpPort,
                            onValueChange = { httpPort = it },
                            label = Str.get(R.string.remote_port),
                            enabled = !httpState.running,
                            modifier = Modifier.weight(1f)
                        )
                        UnifiedButton(
                            text = Str.get(
                                if (httpState.running) R.string.remote_stop else R.string.remote_start
                            ),
                            onClick = { if (httpState.running) http.stop() else startHttp() },
                            variant = if (httpState.running) ButtonVariant.Destructive
                            else ButtonVariant.Primary,
                            modifier = Modifier.align(Alignment.CenterVertically)
                        )
                    }
                    if (httpState.error != null) {
                        UnifiedCaptionText(
                            Str.get(R.string.remote_start_failed, httpState.error ?: ""),
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }
                }
            }

            // ==================== 连接日志 ====================
            UnifiedSectionTitle(Str.get(R.string.remote_log_title))
            UnifiedCard(modifier = Modifier.fillMaxWidth()) {
                val merged = remember(ftpLog, httpLog) {
                    // 行首补协议名：连接行本身只写了 IP，两个服务端混在一起分不清是谁
                    ftpLog.map { "FTP  | $it" } + httpLog.map { "HTTP | $it" }
                }
                if (merged.isEmpty()) {
                    UnifiedCaptionText(
                        Str.get(R.string.remote_log_empty),
                        modifier = Modifier.padding(12.dp)
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        merged.takeLast(LOG_VISIBLE).forEach { line ->
                            Text(
                                text = line,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 运行状态：一颗色点 + 文案。绿点在浅色深色主题下都够醒目，不依赖主题色。 */
@Composable
private fun StatusRow(running: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(9.dp)
                .background(
                    if (running) Color(0xFF22C55E) else Color(0xFF9CA3AF),
                    CircleShape
                )
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = Str.get(if (running) R.string.remote_running else R.string.remote_stopped),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * 地址行：可点选复制的等宽地址 + 一个复制按钮。
 *
 * 两处都给是因为**复制地址是这一页最高频的动作** —— 光有文字，用户得长按选中再点复制，
 * 在一串 `ftp://192.168.1.5:2121` 上很容易只选中一半。
 */
@Composable
private fun AddressRow(address: String?, onCopy: () -> Unit) {
    if (address == null) {
        UnifiedCaptionText(
            Str.get(R.string.remote_ip_unknown),
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        return
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = address,
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .clickable { onCopy() }
                .padding(vertical = 4.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        UnifiedButton(
            text = Str.get(R.string.remote_copy_address),
            onClick = onCopy,
            variant = ButtonVariant.Outlined
        )
    }
}

/**
 * 拼出展示用地址。
 *
 * 运行中报**实际**端口（用户填 0 让系统分配时，配置里的值和真端口对不上）；
 * 未启动时报**配置**端口，让人知道点下「启动」会开在哪儿。
 */
private fun displayAddress(
    scheme: String,
    ip: String?,
    running: Boolean,
    runningPort: Int,
    configuredPort: Int,
    fallback: String
): String? {
    if (ip == null) return null
    val port = if (running && runningPort > 0) runningPort
    else fallback.trim().toIntOrNull()?.takeIf { it in 1..65535 } ?: configuredPort
    return "$scheme://$ip:$port"
}

/** 日志最多显示这些行 —— 再多也只是让人没法滚动到有用的那几条。 */
private const val LOG_VISIBLE = 60
