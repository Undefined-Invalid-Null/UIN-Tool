package com.UIN.Tool.proot

import android.content.Context
import com.UIN.Tool.log.Logger
import java.io.File

object ProotRuntime {

    private const val TAG = "ProotRuntime"

    // ==================== 共享常量 ====================
    private val BIND_MOUNTS_BASE = listOf("/dev", "/proc", "/sys", "/storage/emulated/0")
    private val BIND_MOUNTS_EXTRA = listOf(
        "/dev/urandom:/dev/random",
        "/proc/self/fd:/dev/fd",
        "/proc/self/fd/0:/dev/stdin",
        "/proc/self/fd/1:/dev/stdout",
        "/proc/self/fd/2:/dev/stderr"
    )
    private val PROOT_FLAGS = listOf(
        "-L",
        "--kill-on-exit",
        "--link2symlink",
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

        // Create .l2s directory for link2symlink backing files (proot-distro compatible)
        // Use canonicalPath to resolve symlinks for consistent path matching
        val canonicalRootfs = config.rootfsPath.canonicalFile
        val l2sDir = File(canonicalRootfs, ".l2s")
        if (!l2sDir.exists()) l2sDir.mkdirs()

        // Setup fake sysdata for /proc and /sys
        val containerDir = canonicalRootfs.parentFile ?: canonicalRootfs
        val sysdataDir = FakeSysdata.setup(context, containerDir)
        val fakeSysdataMounts = sysdataDir?.let { FakeSysdata.getBindMounts(it) } ?: emptyList()

        // Create /dev/shm directory
        val shmDir = File(containerDir, "shm").apply { mkdirs() }

        val shellPath = RootfsManager.findShell(canonicalRootfs) ?: "/bin/sh"

        val bindMounts = BIND_MOUNTS_BASE + BIND_MOUNTS_EXTRA + fakeSysdataMounts +
            "${shmDir.absolutePath}:/dev/shm" +
            config.extraBindMounts.map { "${it.first}:${it.second}" }

        // Add PROOT_L2S_DIR and PROOT_TMP_DIR to env
        val envWithL2s = DEFAULT_ENV.toMutableMap()
        envWithL2s["PROOT_L2S_DIR"] = l2sDir.absolutePath
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
        val l2sDir = File(canonicalRootfs, ".l2s")
        if (!l2sDir.exists()) l2sDir.mkdirs()

        val containerDir = canonicalRootfs.parentFile ?: canonicalRootfs
        val sysdataDir = FakeSysdata.setup(context, containerDir)
        val fakeSysdataMounts = sysdataDir?.let { FakeSysdata.getBindMounts(it) } ?: emptyList()

        val shmDir = File(containerDir, "shm").apply { mkdirs() }

        val bindMounts = BIND_MOUNTS_BASE + BIND_MOUNTS_EXTRA + fakeSysdataMounts +
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
        val canonicalRootfs = rootfsPath.canonicalFile
        val l2sDir = File(canonicalRootfs, ".l2s")
        if (!l2sDir.exists()) l2sDir.mkdirs()
        return DEFAULT_ENV + ("PROOT_L2S_DIR" to l2sDir.absolutePath)
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
            if (l2sDir.exists()) {
                l2sDir.deleteRecursively()
                Logger.d(TAG, "Cleaned up .l2s directory")
            }
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
        workDir: String? = null
    ): Process? {
        return try {
            val args = buildCommand(context, config, command)
            val canonicalRootfs = config.rootfsPath.canonicalFile
            val effectiveWorkDir = workDir?.let { File(canonicalRootfs, it.removePrefix("/")) }
                ?: canonicalRootfs

            // Create .l2s directory for link2symlink backing files
            val l2sDir = File(canonicalRootfs, ".l2s")
            if (!l2sDir.exists()) l2sDir.mkdirs()

            val pb = ProcessBuilder(args)
            pb.directory(effectiveWorkDir)
            pb.redirectErrorStream(true)

            // Set environment variables including PROOT_L2S_DIR
            val env = pb.environment()
            env.putAll(DEFAULT_ENV)
            env["PROOT_L2S_DIR"] = l2sDir.absolutePath

            pb.start()
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to execute proot command: $command", e)
            null
        }
    }

    fun executeCommandWithOutput(
        context: Context,
        config: ContainerConfig,
        command: String
    ): CommandResult {
        return try {
            val process = executeCommand(context, config, command)
                ?: return CommandResult(false, "", "Failed to start process")

            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()
            CommandResult(exitCode == 0, output, "", exitCode)
        } catch (e: Exception) {
            Logger.e(TAG, "Command execution failed", e)
            CommandResult(false, "", e.message ?: "Unknown error")
        }
    }

    data class CommandResult(
        val success: Boolean,
        val output: String,
        val error: String,
        val exitCode: Int = if (success) 0 else -1
    )
}
