// plugin/RealTermuxRuntime.kt
package com.UIN.Tool.plugin

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.UIN.Tool.R
import com.UIN.Tool.log.Logger
import com.UIN.Tool.shared.termux.TermuxConstants.TERMUX_APP.TERMUX_SERVICE
import com.UIN.Tool.utils.PermissionUtils
import com.UIN.Tool.utils.Str
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * 实体 Termux（com.termux）运行时。
 *
 * 通过 Termux 的 RUN_COMMAND intent 调用 com.termux.app.RunCommandService：
 * - 无法直接杀掉 com.termux 启动的进程（跨应用沙箱），停止只能靠约定好的 HTTP /stop 端点。
 * - 环境变量无法透传（无 EXTRA_ENVIRONMENT），必须内联进 `sh -lc` 字符串。
 * - 插件位于共享存储 /storage/emulated/0/UIN_Tool/plugins，需 termux-setup-storage 授权后才能读取。
 */
object RealTermuxRuntime {

    private const val TAG = "RealTermuxRuntime"

    /** 探测请求自增序号，保证并发探测的 PendingIntent requestCode 与回调不冲突 */
    private val requestIdCounter = AtomicInteger(0)

    /** 上次探测成功的时间戳（毫秒），用于短缓存避免每次启动重复探测往返 */
    private val lastProbeOkAt = AtomicLong(0)

    /** 探测成功缓存有效期：期间内重复探测直接视为就绪（权限/初始化短期内不会变化） */
    private const val PROBE_CACHE_MS = 60_000L

    /** 上次预热时间戳（毫秒），节流避免每次启动宿主都重复预热 */
    private val lastPrewarmAt = AtomicLong(0)

    /** 预热节流：同一时刻内只预热一次，避免短时间内反复拉起容器 */
    private const val PREWARM_COOLDOWN_MS = 30_000L

    val SH_PATH: String = "${BackendConfig.REAL_TERMUX_PREFIX}/bin/sh"
    val BASH_PATH: String = "${BackendConfig.REAL_TERMUX_PREFIX}/bin/bash"
    val PROOT_DISTRO_PATH: String = "${BackendConfig.REAL_TERMUX_PREFIX}/bin/proot-distro"

    // ==================== RUN_COMMAND intent 键 ====================

    private const val ACTION_RUN_COMMAND = "com.termux.RUN_COMMAND"
    private const val EXTRA_COMMAND_PATH = "com.termux.RUN_COMMAND_PATH"
    private const val EXTRA_ARGUMENTS = "com.termux.RUN_COMMAND_ARGUMENTS"
    private const val EXTRA_WORKDIR = "com.termux.RUN_COMMAND_WORKDIR"
    private const val EXTRA_BACKGROUND = "com.termux.RUN_COMMAND_BACKGROUND"
    private const val EXTRA_SHELL_NAME = "com.termux.RUN_COMMAND_SHELL_NAME"
    private const val EXTRA_COMMAND_LABEL = "com.termux.RUN_COMMAND_COMMAND_LABEL"
    private const val EXTRA_PENDING_INTENT = "com.termux.RUN_COMMAND_PENDING_INTENT"
    private const val EXTRA_SESSION_ACTION = "com.termux.RUN_COMMAND_SESSION_ACTION"

    /**
     * 前台会话只建会话、不自动打开 Activity（值对应
     * TERMUX_SERVICE.VALUE_EXTRA_SESSION_ACTION_SWITCH_TO_NEW_SESSION_AND_DONT_OPEN_ACTIVITY）。
     * 由本应用前台 Activity 直接拉起 com.termux 的 TermuxActivity，避免依赖
     * com.termux 服务端“显示在其他应用上层”的自动弹出（Android 10+/14+ 上并不可靠）。
     */
    const val SESSION_ACTION_DONT_OPEN_ACTIVITY = "2"

    private const val RUN_COMMAND_SERVICE_NAME = "com.termux.app.RunCommandService"
    private const val TERMUX_ACTIVITY_NAME = "com.termux.app.TermuxActivity"

    /**
     * 实体 Termux RUN_COMMAND 权限。
     * 由 com.termux 以 dangerous 级别声明（见 termux-app AndroidManifest.xml），
     * 第三方应用需在本应用 Manifest 中声明 <uses-permission> 并在
     * App Info -> 权限 -> 其他权限（Additional permissions）中手动授予，
     * 否则启动 com.termux.app.RunCommandService 会抛
     * "Not allowed to start service ... without permission com.termux.permission.RUN_COMMAND"。
     */
    const val RUN_COMMAND_PERMISSION = "com.termux.permission.RUN_COMMAND"

    /** 探测回调 Action，与 RealTermuxProbeReceiver 配对 */
    const val PROBE_ACTION = "com.UIN.Tool.REAL_TERMUX_PROBE_RESULT"

    /** 探测请求唯一标识 extra，用于回调按请求区分，避免并发探测互相覆盖 */
    const val EXTRA_PROBE_REQUEST_ID = "com.UIN.Tool.REAL_TERMUX_PROBE_REQUEST_ID"

    /**
     * 通过 RUN_COMMAND 在 Termux 自身 UID 下执行 pkill，杀掉该插件启动的后端进程。
     *
     * 宿主跨应用沙箱无法直接 kill com.termux 启动的进程，但 RUN_COMMAND 在
     * Termux 自己的 UID 下执行，等同于“Termux 自己杀自己”，可以命中后端进程树。
     *
     * 匹配方式：启动命令 `sh -lc "export ... PLUGIN_ID=<id> ..."` 的 cmdline 内含
     * `PLUGIN_ID=<id>`（proot 模式外层为 proot-distro login 进程，同样含该串）。
     * 用 `[P]LUGIN_ID` 正则技巧排除本次 kill 命令自身（自身 cmdline 含字面 `[P]LUGIN_ID`
     * 而非 `PLUGIN_ID`），再取进程组 PGID 用 `kill -9 -- -PGID` 连带杀掉子进程
     * （`start.sh` 里 `exec python3` 后 python 仍是该组子进程）。
     *
     * @param pluginId 插件 ID（按字面匹配，正则点号已转义避免误杀兄弟插件）
     */
    fun killBackend(context: Context, pluginId: String): Boolean {
        if (!isRunCommandPermissionGranted(context)) {
            Logger.e(TAG, Str.get(R.string.real_termux_run_command_permission_denied))
            return false
        }
        val escaped = pluginId.replace(".", "\\.")
        val killCmd =
            "P=\$(pgrep -f '[P]LUGIN_ID=$escaped' | head -n1); " +
                "if [ -n \"\$P\" ]; then " +
                "G=\$(ps -o pgid= -p \$P | tr -d ' '); " +
                "[ -n \"\$G\" ] && kill -9 -- -\$G 2>/dev/null; " +
                "kill -9 \$P 2>/dev/null; " +
                "fi; true"
        try {
            val intent = buildRunCommandIntent(
                commandPath = BASH_PATH,
                arguments = arrayOf("-lc", killCmd),
                workDir = "/",
                shellName = "uin-kill",
                commandLabel = "kill-backend",
                background = true
            )
            return startRunCommand(context, intent)
        } catch (e: Exception) {
            Logger.e(TAG, "failed to send kill command for $pluginId: ${e.message}", e)
            return false
        }
    }

    // ==================== 状态检测 ====================

    /** 是否已安装 com.termux */
    fun isTermuxInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo(
                BackendConfig.REAL_TERMUX_PACKAGE,
                0
            ) != null
        } catch (_: Exception) {
            false
        }
    }

    /** 本应用是否已获得 com.termux.permission.RUN_COMMAND（需用户在 App Info 中授予）。 */
    fun isRunCommandPermissionGranted(context: Context): Boolean =
        PermissionUtils.hasPermission(context, RUN_COMMAND_PERMISSION)

    /**
     * 探测实体 Termux 是否就绪：allow-external-apps 是否开启、Termux 是否已初始化（bootstrap）。
     *
     * 无法跨沙箱读取 /data/data/com.termux，只能通过 RUN_COMMAND 发 `echo ok`，
     * 凭 PendingIntent 回调里的 errmsg 判断：
     * - 未开启 allow-external-apps：errmsg 含 "allow-external-apps"
     * - Termux 未初始化：errmsg 为 bootstrap/usr/bin 相关错误
     * - 正常：exitCode 0
     *
     * @param timeoutMs 探测超时（毫秒），超时视为未知（不做阻断）
     */
    fun probe(context: Context, timeoutMs: Long = 1500): ProbeResult {
        if (!isTermuxInstalled(context)) return ProbeResult(false, Str.get(R.string.real_termux_not_installed))
        if (!isRunCommandPermissionGranted(context)) {
            return ProbeResult(
                false,
                Str.get(R.string.real_termux_run_command_permission_denied),
                requiresRunCommandPermission = true
            )
        }

        // 短缓存：刚探测成功过就跳过往返，直接视为就绪（权限/初始化短期内不会变化）
        val now = System.currentTimeMillis()
        if (lastProbeOkAt.get() != 0L && now - lastProbeOkAt.get() < PROBE_CACHE_MS) {
            return ProbeResult(true)
        }

        val latch = CountDownLatch(1)
        val result = AtomicReference<ProbeResult?>(null)
        val requestId = requestIdCounter.incrementAndGet()

        RealTermuxProbeReceiver.pendingCallbacks[requestId] = object : ProbeCallback {
            override fun onResult(probe: ProbeResult) {
                result.set(probe)
                latch.countDown()
            }
        }

        try {
            val pi = PendingIntent.getBroadcast(
                context,
                requestId,
                Intent(PROBE_ACTION).setPackage(context.packageName).putExtra(EXTRA_PROBE_REQUEST_ID, requestId),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val intent = buildRunCommandIntent(
                commandPath = SH_PATH,
                arguments = arrayOf("-lc", "echo ok"),
                workDir = "/",
                shellName = "uin-probe",
                commandLabel = "probe",
                pendingIntent = pi
            )
            startRunCommand(context, intent)
        } catch (e: Exception) {
            Logger.e(TAG, Str.get(R.string.failed_to_start_real_termux_probe_e, e.message), e)
            RealTermuxProbeReceiver.pendingCallbacks.remove(requestId)
            return ProbeResult(false, e.message ?: "")
        }

        val received = try {
            latch.await(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (_: InterruptedException) {
            false
        }
        RealTermuxProbeReceiver.pendingCallbacks.remove(requestId)

        val probe = result.get()
        val final = probe ?: ProbeResult(true, "") // 超时无回调 → 未知，不阻断
        if (final.ok) lastProbeOkAt.set(System.currentTimeMillis())
        return final
    }

    /**
     * 探测 Termux 中 proot-distro 和指定容器是否可用。
     */
    fun probeProotDistro(context: Context, container: String, timeoutMs: Long = 30_000): ProbeResult {
        if (!isTermuxInstalled(context)) return ProbeResult(false, "Termux not installed")
        if (!isRunCommandPermissionGranted(context)) return ProbeResult(false, "Run-command permission denied")

        // 快速检查：proot-distro 命令是否存在（超时视为存在，不阻断）
        Logger.d(TAG, "probeProotDistro: checking 'which proot-distro'")
        val whichResult = sendProbeCommand(context, arrayOf("which", "proot-distro"), 5_000)
        Logger.d(TAG, "probeProotDistro: which result=$whichResult")
        // 超时无回调 → 假定可用（与原始 probe 一致，不阻断启动）
        if (whichResult != null && whichResult.ok == false) return ProbeResult(false, "proot-distro not installed in Termux (run: pkg install proot-distro)")

        // 慢检查：容器 login 是否可用（冷启动可能需要较长时间）
        Logger.d(TAG, "probeProotDistro: checking 'proot-distro login $container'")
        val loginResult = sendProbeCommand(context, arrayOf("proot-distro", "login", container, "--", "echo", "ok"), timeoutMs)
        Logger.d(TAG, "probeProotDistro: login result=$loginResult")
        // 超时 → 报错（login 超时说明容器可能未安装）
        return loginResult
            ?: ProbeResult(false, "probe timeout (container '$container' may not be installed, run: proot-distro install $container)")
    }

    private fun sendProbeCommand(context: Context, command: Array<String>, timeoutMs: Long = 5_000): ProbeResult? {
        val latch = CountDownLatch(1)
        val result = AtomicReference<ProbeResult?>(null)
        val requestId = requestIdCounter.incrementAndGet()
        val cmdStr = command.joinToString(" ")
        Logger.d(TAG, "sendProbeCommand: '$cmdStr' requestId=$requestId timeout=${timeoutMs}ms")

        RealTermuxProbeReceiver.pendingCallbacks[requestId] = object : ProbeCallback {
            override fun onResult(probe: ProbeResult) {
                Logger.d(TAG, "sendProbeCommand callback: requestId=$requestId ok=${probe.ok} error=${probe.error}")
                result.set(probe)
                latch.countDown()
            }
        }

        try {
            val pi = PendingIntent.getBroadcast(
                context, requestId,
                Intent(PROBE_ACTION).setPackage(context.packageName).putExtra(EXTRA_PROBE_REQUEST_ID, requestId),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val intent = buildRunCommandIntent(
                commandPath = SH_PATH,
                arguments = arrayOf("-lc", cmdStr),
                workDir = "/",
                shellName = "uin-probe",
                commandLabel = "probe",
                pendingIntent = pi
            )
            val sent = startRunCommand(context, intent)
            Logger.d(TAG, "sendProbeCommand: startRunCommand sent=$sent")
        } catch (e: Exception) {
            Logger.e(TAG, "sendProbeCommand exception: ${e.message}")
            RealTermuxProbeReceiver.pendingCallbacks.remove(requestId)
            return ProbeResult(false, e.message ?: "probe exception")
        }

        val received = try { latch.await(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS) } catch (_: InterruptedException) { false }
        RealTermuxProbeReceiver.pendingCallbacks.remove(requestId)
        if (!received) Logger.d(TAG, "sendProbeCommand: TIMEOUT after ${timeoutMs}ms for '$cmdStr'")
        return result.get()
    }

    // ==================== RUN_COMMAND 构建 ====================

    fun buildRunCommandIntent(
        commandPath: String,
        arguments: Array<String>,
        workDir: String,
        shellName: String,
        commandLabel: String,
        pendingIntent: PendingIntent? = null,
        background: Boolean = true,
        sessionAction: String? = null
    ): Intent {
        return Intent(ACTION_RUN_COMMAND).apply {
            putExtra(EXTRA_COMMAND_PATH, commandPath)
            putExtra(EXTRA_ARGUMENTS, arguments)
            putExtra(EXTRA_WORKDIR, workDir)
            putExtra(EXTRA_BACKGROUND, background)
            putExtra(EXTRA_SHELL_NAME, shellName)
            putExtra(EXTRA_COMMAND_LABEL, commandLabel)
            if (pendingIntent != null) {
                putExtra(EXTRA_PENDING_INTENT, pendingIntent)
            }
            if (sessionAction != null) {
                putExtra(EXTRA_SESSION_ACTION, sessionAction)
            }
        }
    }

    /** 拉起 com.termux 的全屏终端 Activity（前台调用不受后台启动限制约束）。 */
    fun termuxActivityIntent(): Intent =
        Intent().setClassName(BackendConfig.REAL_TERMUX_PACKAGE, TERMUX_ACTIVITY_NAME)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun startRunCommand(context: Context, intent: Intent): Boolean {
        if (!isRunCommandPermissionGranted(context)) {
            Logger.e(TAG, Str.get(R.string.real_termux_run_command_permission_denied))
            return false
        }
        intent.setClassName(BackendConfig.REAL_TERMUX_PACKAGE, RUN_COMMAND_SERVICE_NAME)
        try {
            context.startService(intent)
        } catch (_: SecurityException) {
            context.startForegroundService(intent)
        }
        return true
    }

    /** 探测结果 */
    data class ProbeResult(
        val ok: Boolean,
        val error: String = "",
        val requiresRunCommandPermission: Boolean = false
    ) {
        val requiresAllowExternalApps: Boolean
            get() = error.contains("allow-external-apps", ignoreCase = true)
    }
}

/** 探测回调接口（object 持有，避免静态类循环引用） */
interface ProbeCallback {
    fun onResult(result: RealTermuxRuntime.ProbeResult)
}

/**
 * 接收实体 Termux RUN_COMMAND 的 PendingIntent 回调。
 * 同时用于启动结果（errmsg）上报。
 */
class RealTermuxProbeReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "RealTermuxProbeReceiver"

        /**
         * 按请求 ID 存放待处理回调，支持并发探测互不覆盖。
         * 回调对象在 onResult 中取回后由本类移除；发起方超时/失败也会移除。
         */
        val pendingCallbacks = ConcurrentHashMap<Int, ProbeCallback>()
    }

    override fun onReceive(context: Context, intent: Intent) {
        val resultBundle: Bundle? = intent.getBundleExtra(TERMUX_SERVICE.EXTRA_PLUGIN_RESULT_BUNDLE)
        val exitCode = resultBundle?.getInt(TERMUX_SERVICE.EXTRA_PLUGIN_RESULT_BUNDLE_EXIT_CODE, -1) ?: -1
        val errmsg = resultBundle?.getString(TERMUX_SERVICE.EXTRA_PLUGIN_RESULT_BUNDLE_ERRMSG) ?: ""

        val requestId = intent.getIntExtra(RealTermuxRuntime.EXTRA_PROBE_REQUEST_ID, -1)
        val callback = pendingCallbacks.remove(requestId)

        if (errmsg.contains("allow-external-apps", ignoreCase = true)) {
            callback?.onResult(RealTermuxRuntime.ProbeResult(false, Str.get(R.string.allow_external_apps_not_enabled)))
            return
        }

        if (exitCode == 0) {
            callback?.onResult(RealTermuxRuntime.ProbeResult(true))
        } else {
            callback?.onResult(
                RealTermuxRuntime.ProbeResult(
                    false,
                    errmsg.ifBlank { Str.get(R.string.real_termux_not_initialized) }
                )
            )
        }
        Logger.d(TAG, "probe result exitCode=$exitCode errmsg=$errmsg")
    }
}
