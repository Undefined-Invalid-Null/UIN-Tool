package com.UIN.Tool.proot

import android.content.Context
import com.UIN.Tool.log.Logger
import java.io.File

object ProotRuntime {

    private const val TAG = "ProotRuntime"

    // ==================== 共享常量 ====================
    // D-33：基础挂载集不再无条件包含宿主共享存储（见 bindMountsFor）
    private val BIND_MOUNTS_BASE = listOf("/dev", "/proc", "/sys")
    private const val SHARED_STORAGE = "/storage/emulated/0"

    /** D-33：最小挂载集 + 用户可开关的共享存储。原先无条件 bind 共享存储且
     *  `--change-id=0:0` 让容器内进程（含插件后端）以 root 直接读写全部共享存储。 */
    private fun bindMountsFor(context: Context): List<String> {
        val shared = try {
            context.getSharedPreferences(
                com.UIN.Tool.constants.AppConstants.PREF_NAME,
                Context.MODE_PRIVATE
            ).getBoolean(com.UIN.Tool.constants.AppConstants.KEY_PROOT_BIND_SHARED_STORAGE, true)
        } catch (e: Exception) {
            true
        }
        return if (shared) BIND_MOUNTS_BASE + SHARED_STORAGE else BIND_MOUNTS_BASE
    }
    private val BIND_MOUNTS_EXTRA = listOf(
        "/dev/urandom:/dev/random",
        "/proc/self/fd:/dev/fd",
        "/proc/self/fd/0:/dev/stdin",
        "/proc/self/fd/1:/dev/stdout",
        "/proc/self/fd/2:/dev/stderr"
    )
    // D-17：去掉 --link2symlink。原先「每次命令都删掉 .l2s + 全盘 find 清理指向它的符号链接」
    // 与「继续传 --link2symlink」两套逻辑打架（等价于关掉该特性还白付 IO）。
    // rootfs 位于应用私有 ext4/f2fs，link() 硬链接原生可用，不需要 proot 的仿真。
    private val PROOT_FLAGS = listOf(
        "-L",
        "--kill-on-exit",
        "--sysvipc",
        "--change-id=0:0"
    )
    private val DEFAULT_KERNEL_RELEASE = "\\Linux\\uin-tool\\6.17.0-UIN-Tool\\#1 SMP PREEMPT_DYNAMIC\\aarch64\\localdomain\\-1\\"
    private val DEFAULT_ENV = mapOf(
        "HOME" to "/root",
        "USER" to "root",
        "SHELL" to "/bin/bash",
        "PATH" to "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
        "TERM" to "xterm-256color",
        "LANG" to "C.UTF-8"
    )

    private fun buildBaseArgs(
        prootBin: java.io.File,
        rootfsPath: java.io.File,
        bindMounts: List<String>,
        workDir: String
    ): List<String> {
        val args = mutableListOf<String>()
        args.add(prootBin.absolutePath)
        args.addAll(PROOT_FLAGS)
        args.addAll(listOf("--kernel-release=$DEFAULT_KERNEL_RELEASE"))
        // Use canonicalPath to resolve symlinks (e.g., /data/data/ → /data/user/0/)
        // This is critical for detranslate_path() to work correctly with link2symlink
        args.addAll(listOf("-r", rootfsPath.canonicalPath))
        for (mount in bindMounts) {
            args.addAll(listOf("-b", mount))
        }
        args.addAll(listOf("-w", workDir))
        return args
    }

    fun buildCommand(
        context: Context,
        config: ContainerConfig,
        command: String
    ): List<String> {
        val prootBin = ProotInstaller.prootBinaryPath(context)
        // PROOT_TMP_DIR must point to a host directory that exists for proot startup
        val hostTmpDir = File(context.cacheDir, "proot_tmp").apply { mkdirs() }

        // Ensure critical rootfs directories exist
        ensureRootfsDirs(context, config)

        // D-17：不再创建 .l2s（--link2symlink 已移除）
        // Use canonicalPath to resolve symlinks for consistent path matching
        val canonicalRootfs = config.rootfsPath.canonicalFile

        // Setup fake sysdata for /proc and /sys
        val containerDir = canonicalRootfs.parentFile ?: canonicalRootfs
        val sysdataDir = FakeSysdata.setup(context, containerDir)
        val fakeSysdataMounts = sysdataDir?.let { FakeSysdata.getBindMounts(it) } ?: emptyList()

        // Create /dev/shm directory
        val shmDir = File(containerDir, "shm").apply { mkdirs() }

        val shellPath = RootfsManager.findShell(canonicalRootfs) ?: "/bin/sh"

        val bindMounts = bindMountsFor(context) + BIND_MOUNTS_EXTRA + fakeSysdataMounts +
            "${shmDir.absolutePath}:/dev/shm" +
            config.extraBindMounts.map { "${it.first}:${it.second}" }

        // D-17：PROOT_L2S_DIR 随 --link2symlink 一并移除，只保留 PROOT_TMP_DIR
        val envWithL2s = DEFAULT_ENV.toMutableMap()
        envWithL2s["PROOT_TMP_DIR"] = hostTmpDir.absolutePath

        return buildBaseArgs(prootBin, canonicalRootfs, bindMounts, config.workingDir) + listOf(shellPath, "-lc", command)
    }

    fun buildInteractiveShell(
        context: Context,
        config: ContainerConfig,
        extraEnv: Map<String, String> = emptyMap()
    ): List<String> {
        val prootBin = ProotInstaller.prootBinaryPath(context)

        // Ensure critical rootfs directories exist
        ensureRootfsDirs(context, config)

        // Use canonicalPath to resolve symlinks for consistent path matching
        val canonicalRootfs = config.rootfsPath.canonicalFile

        val containerDir = canonicalRootfs.parentFile ?: canonicalRootfs
        val sysdataDir = FakeSysdata.setup(context, containerDir)
        val fakeSysdataMounts = sysdataDir?.let { FakeSysdata.getBindMounts(it) } ?: emptyList()

        val shmDir = File(containerDir, "shm").apply { mkdirs() }

        val bindMounts = bindMountsFor(context) + BIND_MOUNTS_EXTRA + fakeSysdataMounts +
            "${shmDir.absolutePath}:/dev/shm" +
            config.extraBindMounts.map { "${it.first}:${it.second}" }

        val shellPath = RootfsManager.findShell(canonicalRootfs) ?: "/bin/sh"
        return buildBaseArgs(prootBin, canonicalRootfs, bindMounts, config.workingDir) + listOf(shellPath, "-l")
    }

    fun getDefaultEnv(): Map<String, String> = DEFAULT_ENV

    /**
     * Get default environment with PROOT_L2S_DIR set for the given rootfs.
     */
    fun getDefaultEnvWithL2s(rootfsPath: File): Map<String, String> {
        // D-17：--link2symlink 已移除，这里保留 API 兼容调用方，只返回默认 env
        return DEFAULT_ENV
    }

    /**
     * Ensure critical directories exist inside rootfs.
     * Docker rootfs images often lack these.
     */
    fun ensureRootfsDirs(context: Context, config: ContainerConfig) {
        val rootfs = config.rootfsPath.canonicalFile
        val dirs = listOf(
            "etc/apt/apt.conf.d",
            "etc/dpkg/dpkg.cfg.d",
            "tmp",
            "var/tmp",
            "var/lib/dpkg",
            "var/cache/apt",
            "var/lib/apt",
            "root"
        )
        for (dir in dirs) {
            val d = File(rootfs, dir)
            if (!d.exists()) d.mkdirs()
        }
        // Clean up broken link2symlink artifacts (leftover .l2s symlinks)
        cleanupBrokenL2sSymlinks(rootfs)
        // Set /tmp permissions
        Runtime.getRuntime().exec(arrayOf("chmod", "1777", File(rootfs, "tmp").absolutePath)).waitFor()
    }

    /**
     * Clean up broken link2symlink artifacts.
     * When rootfs is moved or app data path changes, l2s symlinks become stale.
     * Since rootfs is on internal storage (ext4/f2fs), we don't need --link2symlink,
     * so clean up all leftover .l2s symlinks to prevent dpkg stat failures.
     */
    private fun cleanupBrokenL2sSymlinks(rootfs: File) {
        try {
            val l2sDir = File(rootfs, ".l2s")
            // D-17：没有 .l2s（新容器/已清理过）就直接返回 ——
            // 原先无条件对整个 rootfs 跑一次 `find ... -delete`，每条 proot 命令都要全盘遍历。
            if (!l2sDir.exists()) return
            l2sDir.deleteRecursively()
            Logger.d(TAG, "Cleaned up .l2s directory")
            // Remove any stale symlinks pointing to .l2s (from previous --link2symlink usage)
            val process = Runtime.getRuntime().exec(
                arrayOf("find", rootfs.absolutePath, "-type", "l", "-lname", "*.l2s*", "-delete")
            )
            process.waitFor()
        } catch (e: Exception) {
            Logger.w(TAG, "Failed to cleanup l2s symlinks: ${e.message}")
        }
    }

    fun executeCommand(
        context: Context,
        config: ContainerConfig,
        command: String,
        workDir: String? = null,
        mergeErrorStream: Boolean = true
    ): Process? {
        return try {
            val args = buildCommand(context, config, command)
            val canonicalRootfs = config.rootfsPath.canonicalFile

            // D-18：容器内路径不能当宿主 cwd（原先 pb.directory(容器路径) 在宿主上多半不存在/不是目录），
            // 只有真实存在的宿主目录才设 cwd；容器内工作目录由 proot 的 --cwd/命令本身负责。
            val hostWorkDir = workDir?.let { File(canonicalRootfs, it.removePrefix("/")) }
                ?.takeIf { it.isDirectory }

            // D-17：不再创建 .l2s（--link2symlink 已移除）
            val pb = ProcessBuilder(args)
            hostWorkDir?.let { pb.directory(it) }
            pb.redirectErrorStream(mergeErrorStream)

            // Set environment variables including PROOT_L2S_DIR
            val env = pb.environment()
            env.putAll(DEFAULT_ENV)
            // D-18：config.environmentVars 原先被完全忽略（execCommand 的 extraEnv 静默失效）
            config.environmentVars.forEach { (k, v) -> env[k] = v }
            // buildCommand 里算好的 envWithL2s 从未被使用 → 补上 PROOT_TMP_DIR
            env["PROOT_TMP_DIR"] = File(context.cacheDir, "proot_tmp").apply { mkdirs() }.absolutePath

            pb.start()
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to execute proot command: $command", e)
            null
        }
    }

    /** D-16：单条命令最长执行时间 */
    private const val COMMAND_TIMEOUT_MS = 60_000L
    /** D-16：输出读取上限，防止循环日志把内存打爆 */
    private const val COMMAND_OUTPUT_MAX_BYTES = 1L * 1024 * 1024

    fun executeCommandWithOutput(
        context: Context,
        config: ContainerConfig,
        command: String
    ): CommandResult {
        return try {
            // D-16：stderr 不再并入 stdout（原先 redirectErrorStream(true) 让 error 恒为空串）
            val process = executeCommand(context, config, command, mergeErrorStream = false)
                ?: return CommandResult(false, "", "Failed to start process")

            val outBuf = StringBuilder()
            val errBuf = StringBuilder()
            val outThread = Thread { readCapped(process.inputStream, outBuf) }.apply {
                name = "uin-proot-out"; isDaemon = true; start()
            }
            val errThread = Thread { readCapped(process.errorStream, errBuf) }.apply {
                name = "uin-proot-err"; isDaemon = true; start()
            }

            val finished = process.waitFor(COMMAND_TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS)
            if (!finished) {
                // D-16：原先 waitFor() 无参，子进程卡住就把调用线程永久挂住
                process.destroyForcibly()
                CommandResult(false, outBuf.toString(), "command timed out (${COMMAND_TIMEOUT_MS}ms)", -1)
            } else {
                outThread.join(1000); errThread.join(1000)
                val exitCode = process.exitValue()
                CommandResult(exitCode == 0, outBuf.toString(), errBuf.toString(), exitCode)
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Command execution failed", e)
            CommandResult(false, "", e.message ?: "Unknown error")
        }
    }

    /** D-16：流式读取并按 [COMMAND_OUTPUT_MAX_BYTES] 截断，避免全量进内存 */
    private fun readCapped(input: java.io.InputStream, into: StringBuilder) {
        try {
            input.bufferedReader().use { r ->
                val buf = CharArray(4096)
                var total = 0
                while (true) {
                    val n = r.read(buf)
                    if (n < 0) break
                    total += n
                    if (total > COMMAND_OUTPUT_MAX_BYTES) {
                        into.append("\n…[output truncated]")
                        break
                    }
                    into.append(buf, 0, n)
                }
            }
        } catch (_: Exception) {
        }
    }

    data class CommandResult(
        val success: Boolean,
        val output: String,
        val error: String,
        val exitCode: Int = if (success) 0 else -1
    )
}
