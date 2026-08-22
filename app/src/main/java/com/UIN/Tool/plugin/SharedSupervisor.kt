// plugin/SharedSupervisor.kt
package com.UIN.Tool.plugin

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.UIN.Tool.R
import com.UIN.Tool.constants.AppConstants
import com.UIN.Tool.log.Logger
import com.UIN.Tool.utils.Str
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * 实体 Termux 共享后端 supervisor。
 *
 * 背景：proot 容器每次 `proot-distro login` 都要重新初始化（link2symlink 索引、
 * 容器解析等），冷启动约 5s。共享方案让**一个**常驻 supervisor 在共享环境里跑
 * （proot 容器内 / Termux 本机），所有插件后端作为它的后台子进程运行，容器只
 * 初始化一次，后续插件启动只需在容器内 fork 一个子进程，省掉 proot 初始化开销。
 *
 * 通信协议（全部落在共享存储 <plugins根>/.uin/ 目录）：
 * - supervisor.sh  宿主首次写入的 supervisor 脚本，在共享环境内 `sh` 执行
 * - cmd/<key>.cmd  宿主投放的后端启动命令；supervisor 拾取后 `( sh file ) &` 后台执行
 * - pid/<key>      supervisor 记录该后端的子进程 PID
 * - stop/<key>    宿主投放的停止请求；supervisor 拾取后按 PID 递归杀进程树
 * - idle/<key>    该后端空闲回收分钟数（0 = 无限，不回收）
 * - alive          supervisor 每轮 touch，宿主据此判断 supervisor 是否存活
 * - host_alive     宿主周期 touch，supervisor 据此判断宿主进程是否存活
 * - shutdown       宿主退出时写入；supervisor 拾取后自行退出（容器随之退出）
 *
 * proot 模式启动命令：
 *   proot-distro login <container> --bind '<plugins根>:/plugins' -- sh -c 'sh /plugins/.uin/supervisor.sh /plugins'
 * 本机模式启动命令：
 *   sh '<plugins根>/.uin/supervisor.sh' '<plugins根>'
 *
 * 生命周期：宿主存活期间 supervisor 常驻（即使所有后端回收完）；宿主退出时写
 * shutdown + 停止写 host_alive，supervisor 在下一轮检测到后退出，容器随之关闭。
 */
object SharedSupervisor {

    private const val TAG = "SharedSupervisor"

    private const val CTRL_DIR = ".uin"
    private const val SUPERVISOR_SCRIPT = "supervisor.sh"
    private const val ALIVE_FILE = "alive"
    private const val HOST_ALIVE_FILE = "host_alive"
    private const val SHUTDOWN_FILE = "shutdown"
    private const val KEEP_ALIVE_FILE = "keep_alive"

    /** 宿主写 host_alive 的周期（毫秒），与插件管理器清理任务同节奏 */
    private const val HOST_ALIVE_INTERVAL_MS = 30_000L

    /** supervisor 存活判定阈值：alive mtime 超过该时长视为 supervisor 已死 */
    private const val SUPERVISOR_ALIVE_TIMEOUT_MS = 5_000L

    /** 宿主判定 supervisor 已死的阈值（5 分钟，减少冷启动频率） */
    private const val HOST_ALIVE_TIMEOUT_MS = 300_000L

    /** 等待 supervisor 就绪（alive 出现）的最长时间 */
    private const val SUPERVISOR_READY_TIMEOUT_MS = 20_000L

    /** 宿主进程是否已退出（退出后停止写 host_alive，让 supervisor 自退） */
    private val hostAliveHandler = Handler(Looper.getMainLooper())

    private val hostAliveTask = object : Runnable {
        override fun run() {
            touchHostAlive()
            hostAliveHandler.postDelayed(this, HOST_ALIVE_INTERVAL_MS)
        }
    }

    private var hostAliveStarted = false

    // ==================== 目录 ====================

    private fun ctrlDir(): File = File(AppConstants.PLUGIN_DIR, CTRL_DIR)

    private fun cmdDir(): File = File(ctrlDir(), "cmd")

    private fun pidDir(): File = File(ctrlDir(), "pid")

    private fun stopDir(): File = File(ctrlDir(), "stop")

    private fun idleDir(): File = File(ctrlDir(), "idle")

    private fun aliveFile(): File = File(ctrlDir(), ALIVE_FILE)

    private fun hostAliveFile(): File = File(ctrlDir(), HOST_ALIVE_FILE)

    private fun shutdownFile(): File = File(ctrlDir(), SHUTDOWN_FILE)

    private fun keepAliveFile(): File = File(ctrlDir(), KEEP_ALIVE_FILE)

    // ==================== supervisor 脚本 ====================

/**
     * 从 assets/supervisor/supervisor.sh 读取脚本模板，替换占位符。
     * @param root 共享环境内的 plugins 根目录（proot 内为 /plugins，本机为真实路径）
     */
    fun buildSupervisorScript(context: Context, root: String): String {
        val hostAliveTimeoutSec = HOST_ALIVE_TIMEOUT_MS / 1000
        return try {
            context.assets.open("supervisor/supervisor.sh").bufferedReader().use { it.readText() }
                .replace("%%HOST_ALIVE_TIMEOUT_SEC%%", hostAliveTimeoutSec.toString())
        } catch (e: Exception) {
            Logger.e(TAG, "failed to read supervisor.sh from assets: ${e.message}", e)
            ""
        }
    }

    /** 把 supervisor.sh 写入控制目录（幂等：内容一致则不重写）。 */
    private fun ensureScriptWritten(context: Context, root: String) {
        try {
            val dir = ctrlDir()
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, SUPERVISOR_SCRIPT)
            val content = buildSupervisorScript(context, root)
            if (content.isEmpty()) return
            if (file.exists() && file.readText() == content) return
            file.writeText(content)
            Logger.d(TAG, "supervisor.sh written (root=$root)")
        } catch (e: Exception) {
            Logger.e(TAG, "failed to write supervisor.sh: ${e.message}", e)
        }
    }

    // ==================== 存活检测 ====================

    /** 杀掉残留的旧 supervisor 及其所有子进程（App 启动时调用）。 */
    @JvmStatic
    fun killStaleProcesses() {
        try {
            // Android toybox ps: ps 包含进程名在最后一列，用 grep 匹配
            val proc = Runtime.getRuntime().exec(arrayOf("sh", "-c",
                "for p in \$(ps 2>/dev/null | grep 'supervisor.sh' | grep -v grep | awk '{print \$1}'); do kill -9 \$p 2>/dev/null; done;" +
                "for p in \$(ps 2>/dev/null | grep 'proot.*plugins' | grep -v grep | awk '{print \$1}'); do kill -9 \$p 2>/dev/null; done;" +
                "for p in \$(ps 2>/dev/null | grep 'sh -c export PORT' | grep -v grep | awk '{print \$1}'); do kill -9 \$p 2>/dev/null; done;" +
                "for p in \$(ps 2>/dev/null | grep 'python3 backend/server.py' | grep -v grep | awk '{print \$1}'); do kill -9 \$p 2>/dev/null; done"
            ))
            proc.waitFor()
            Thread.sleep(300)
        } catch (_: Exception) {}
    }

    /** 杀掉残留的旧 supervisor：写 shutdown 文件让其自行退出，再清 PID 残留。 */
    private fun killStaleSupervisor() {
        try {
            if (isSupervisorAlive()) return
            // 写 shutdown 文件让旧 supervisor 自行退出（它每秒检查一次）
            val shutdownFile = File(ctrlDir(), "shutdown")
            shutdownFile.writeText("stale")
            Thread.sleep(2000)
            // 清理残留的 alive 文件
            val alive = aliveFile()
            if (alive.exists()) alive.delete()
            killStaleProcesses()
        } catch (_: Exception) {}
    }

    fun isSupervisorAlive(): Boolean {
        return try {
            val alive = aliveFile()
            if (!alive.exists()) {
                Logger.d(TAG, "isSupervisorAlive: alive file not found")
                return false
            }
            val age = System.currentTimeMillis() - alive.lastModified()
            val recent = age <= SUPERVISOR_ALIVE_TIMEOUT_MS
            if (!recent) {
                Logger.d(TAG, "isSupervisorAlive: alive file stale, age=${age}ms, threshold=${SUPERVISOR_ALIVE_TIMEOUT_MS}ms")
            }
            recent
        } catch (e: Exception) {
            Logger.d(TAG, "isSupervisorAlive: exception=${e.message}")
            false
        }
    }

    /** supervisor 刚退出（alive 文件 mtime < 30s），Termux 会话还在，可跳过 probe */
    fun wasRecentlyAlive(): Boolean {
        return try {
            val alive = aliveFile()
            if (!alive.exists()) return false
            val age = System.currentTimeMillis() - alive.lastModified()
            age <= 30_000L
        } catch (_: Exception) {
            false
        }
    }

    // ==================== 宿主侧 host_alive ====================

    private fun touchHostAlive() {
        try {
            val dir = ctrlDir()
            if (!dir.exists()) dir.mkdirs()
            hostAliveFile().writeText(System.currentTimeMillis().toString())
        } catch (_: Exception) {
        }
    }

    /** 宿主进程存活期间周期写 host_alive（supervisor 据此判定宿主未退出）。 */
    fun startHostAliveTicker() {
        if (hostAliveStarted) return
        hostAliveStarted = true
        touchHostAlive()                          // 立即写一次，确保 supervisor 下轮检测到
        hostAliveHandler.removeCallbacks(hostAliveTask)
        hostAliveHandler.postDelayed(hostAliveTask, HOST_ALIVE_INTERVAL_MS)
        Logger.d(TAG, "host_alive ticker started")
    }

    /**
     * 宿主退出：停止写 host_alive。后台保活未开启时写 shutdown 标记让 supervisor 自退；
     * 保活开启时 supervisor 继续常驻（仅靠显式 shutdown 退出）。
     * supervisor 自身会检测 host_alive 超时并杀掉所有后端进程。
     */
    fun onHostExit() {
        hostAliveStarted = false
        hostAliveHandler.removeCallbacks(hostAliveTask)
        if (keepAliveFile().exists()) {
            Logger.i(TAG, "keep_alive on: supervisor stays alive after host exit")
            return
        }
        try {
            shutdownFile().writeText("1")
            Logger.i(TAG, "shutdown marker written")
        } catch (_: Exception) {
        }
    }

    /**
     * 后台保活开关：开启写 keep_alive 标记（supervisor 不再因宿主进程被杀而退出），
     * 关闭删除标记（恢复“宿主消失即自退”）。
     */
    fun setKeepAlive(enabled: Boolean) {
        try {
            val dir = ctrlDir()
            if (!dir.exists()) dir.mkdirs()
            val f = keepAliveFile()
            if (enabled) {
                f.writeText("1")
                Logger.i(TAG, "keep_alive marker written")
            } else {
                f.delete()
                Logger.i(TAG, "keep_alive marker removed")
            }
        } catch (_: Exception) {
        }
    }

    // ==================== 启动 / 就绪 ====================

    /**
     * 确保共享 supervisor 已运行。
     * - 若已存活（alive 新鲜）直接返回 true；
     * - 否则根据后端环境（proot / 本机）通过 RUN_COMMAND 拉起，并等待就绪。
     * 拉不起来（权限/安装问题）返回 false。
     */
    fun ensureSupervisor(context: Context): Boolean {
        val t0 = System.currentTimeMillis()
        if (isSupervisorAlive()) {
            startHostAliveTicker()
            perfLog("ensureSupervisor: already alive, ${System.currentTimeMillis() - t0}ms")
            return true
        }
        // 杀掉残留的旧 supervisor 进程（alive 文件过期但进程还在）
        killStaleSupervisor()
        val t1 = System.currentTimeMillis()
        if (!RealTermuxRuntime.isTermuxInstalled(context)) {
            Logger.e(TAG, Str.get(R.string.real_termux_not_installed))
            return false
        }
        if (!RealTermuxRuntime.isRunCommandPermissionGranted(context)) {
            Logger.e(TAG, Str.get(R.string.real_termux_run_command_permission_denied))
            return false
        }

        val root = if (BackendConfig.isProotEnv(context)) "/plugins" else AppConstants.PLUGIN_DIR
        ensureScriptWritten(context, root)
        val t2 = System.currentTimeMillis()
        perfLog("ensureSupervisor: not alive, writing script took ${t2 - t1}ms")

        val intent = if (BackendConfig.isProotEnv(context)) {
            val container = BackendConfig.getContainer(context)
            val bind = "${AppConstants.PLUGIN_DIR}:/plugins"
            val inner = "sh /plugins/.uin/$SUPERVISOR_SCRIPT /plugins"
            Logger.i(TAG, "proot mode: container=$container, bind=$bind")
            RealTermuxRuntime.buildRunCommandIntent(
                commandPath = RealTermuxRuntime.PROOT_DISTRO_PATH,
                arguments = arrayOf(
                    "login", container,
                    "--bind", bind,
                    "--", "sh", "-lc", inner
                ),
                workDir = AppConstants.PLUGIN_DIR,
                shellName = "uin-supervisor",
                commandLabel = Str.get(R.string.start_backend_supervisor),
                background = true
            )
        } else {
            val script = "${AppConstants.PLUGIN_DIR}/.uin/$SUPERVISOR_SCRIPT"
            RealTermuxRuntime.buildRunCommandIntent(
                commandPath = RealTermuxRuntime.BASH_PATH,
                arguments = arrayOf("-lc", "sh ${shQ(script)} ${shQ(AppConstants.PLUGIN_DIR)}"),
                workDir = AppConstants.PLUGIN_DIR,
                shellName = "uin-supervisor",
                commandLabel = Str.get(R.string.start_backend_supervisor),
                background = true
            )
        }

        val t3 = System.currentTimeMillis()
        if (!RealTermuxRuntime.startRunCommand(context, intent)) {
            Logger.e(TAG, Str.get(R.string.real_termux_run_command_permission_denied))
            return false
        }
        val t4 = System.currentTimeMillis()
        perfLog("startRunCommand: ${t4 - t3}ms")
        Logger.i(TAG, "startRunCommand sent, isProot=${BackendConfig.isProotEnv(context)}, script=${AppConstants.PLUGIN_DIR}/.uin/$SUPERVISOR_SCRIPT")

        // 等待 supervisor 就绪（alive 出现）
        val deadline = System.currentTimeMillis() + SUPERVISOR_READY_TIMEOUT_MS
        var attempts = 0
        while (System.currentTimeMillis() < deadline) {
            attempts++
            if (isSupervisorAlive()) {
                val t5 = System.currentTimeMillis()
                perfLog("supervisor alive detected after ${attempts} attempts, total: ${t5 - t0}ms")
                Logger.i(TAG, "supervisor alive detected, pid=${aliveFile().readText().trim()}")
                startHostAliveTicker()
                return true
            }
            if (attempts % 15 == 0) { // 每 3s 输出一次
                val alive = aliveFile()
                val exists = alive.exists()
                val content = if (exists) try { alive.readText().trim() } catch (_: Exception) { "err" } else { "no-file" }
                Logger.d(TAG, "waiting for supervisor: attempt=$attempts, alive=$content, elapsed=${System.currentTimeMillis() - t0}ms")
            }
            try {
                Thread.sleep(200)
            } catch (_: InterruptedException) {
                break
            }
        }
        perfLog("supervisor did not become ready in ${SUPERVISOR_READY_TIMEOUT_MS}ms (attempts=$attempts)")
        return false
    }

    // ==================== 后端控制 ====================

    /**
     * 投放后端启动命令。
     * @param key     后端键（插件 ID，或独立端口模式实例键）
     * @param script  后端启动命令体（含环境变量 export 与启动命令）
     * @param idleMin 空闲回收分钟数（0 = 无限不回收）
     */
    fun requestStart(key: String, script: String, idleMin: Int) {
        try {
            cmdDir().mkdirs()
            idleDir().mkdirs()
            File(cmdDir(), "$key.cmd").writeText(script)
            File(idleDir(), key).writeText(idleMin.toString())
            Logger.d(TAG, "start requested for $key (idle=$idleMin)")
        } catch (e: Exception) {
            Logger.e(TAG, "failed to request start for $key: ${e.message}", e)
        }
    }

    /** 投放后端停止请求（supervisor 按 PID 递归杀进程树）。 */
    fun requestStop(key: String) {
        try {
            stopDir().mkdirs()
            File(stopDir(), key).writeText("1")
            Logger.d(TAG, "stop requested for $key")
        } catch (e: Exception) {
            Logger.e(TAG, "failed to request stop for $key: ${e.message}", e)
        }
    }

    /** 直接清除某后端的 pid/idle 记录（端口已死时清理残留）。 */
    fun clearBackendRecord(key: String) {
        try {
            File(pidDir(), key).delete()
            File(idleDir(), key).delete()
        } catch (_: Exception) {
        }
    }

    /** 删除全部 pid/idle/stop 残留（supervisor 重启前清理）。 */
    fun clearAllBackendRecords() {
        try {
            pidDir().listFiles()?.forEach { it.delete() }
            idleDir().listFiles()?.forEach { it.delete() }
            stopDir().listFiles()?.forEach { it.delete() }
        } catch (_: Exception) {
        }
    }

    /**
     * 预热：宿主打开软件或后端配置变更时调用，后台拉起共享 supervisor 并保持。
     * 仅实体 Termux 模式下有效；不阻塞调用线程。
     */
    fun prewarm(context: Context) {
        if (!BackendConfig.isRealTermux(context)) return
        Thread {
            try {
                ensureSupervisor(context)
            } catch (e: Exception) {
                Logger.e(TAG, "prewarm failed: ${e.message}", e)
            }
        }.start()
    }

    private fun shQ(s: String): String = "'" + s.replace("'", "'\\''") + "'"

    /** 写性能日志到 logs/perf_<date>.log */
    private fun perfLog(msg: String) {
        try {
            val dir = File(AppConstants.LOG_DIR)
            if (!dir.exists()) dir.mkdirs()
            val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            val file = File(dir, "perf_$date.log")
            val ts = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
            FileWriter(file, true).use { it.appendLine("[$ts] $msg\n") }
        } catch (_: Exception) {
        }
    }
}