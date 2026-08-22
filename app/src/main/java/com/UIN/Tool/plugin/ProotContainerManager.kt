// plugin/ProotContainerManager.kt
package com.UIN.Tool.plugin

import com.UIN.Tool.R
import com.UIN.Tool.utils.Str
import android.content.Context
import android.content.res.AssetManager
import com.UIN.Tool.log.Logger
import com.UIN.Tool.shared.shell.command.ExecutionCommand
import com.UIN.Tool.shared.shell.command.runner.app.AppShell
import com.UIN.Tool.shared.termux.shell.command.environment.TermuxShellEnvironment
import java.io.File
import java.io.FileOutputStream

/**
 * proot 容器运行时管理器
 *
 * 负责 Termux 环境初始化（复用 bootstrap）与 Alpine 共享容器的离线恢复（proot-distro restore）
 * 与检测，以及容器/宿主内命令执行。
 */
object ProotContainerManager {

    private const val TAG = "ProotContainerManager"

    /** Termux 前缀目录，例如 /data/data/com.UIN.Tool/files/usr */
    val PREFIX: String = "/data/data/com.UIN.Tool/files/usr"

    /** proot-distro 数据目录 */
    private val PROOT_DISTRO_DIR = "$PREFIX/var/lib/proot-distro"

    /** Alpine 容器 rootfs 目录 */
    private val ALPINE_ROOTFS_DIR = "$PROOT_DISTRO_DIR/containers/alpine/rootfs"

    /** Alpine 容器 rootfs 内的常规文件探测路径（避开符号链接：容器内 /bin/pwd 等绝对链接在宿主上解析会失效） */
    private val ALPINE_BUSYBOX = "$ALPINE_ROOTFS_DIR/bin/busybox"
    private val ALPINE_RELEASE = "$ALPINE_ROOTFS_DIR/etc/alpine-release"

    /** proot-distro 可执行文件路径 */
    private val PROOT_DISTRO_BIN = "$PREFIX/bin/proot-distro"

    /** Termux bash 路径 */
    val BASH = "$PREFIX/bin/bash"

    /** 离线 Alpine 备份资源文件名（proot-distro backup 生成的备份，复制到可写目录时的统一命名） */
    private const val ALPINE_ASSET = "alpine.tar.xz"

    /** 是否已安装 Termux 基础环境 */
    fun isTermuxReady(): Boolean = File(PREFIX, "bin").exists()

    /**
     * 无界面后台安装 bootstrap（Application 层调用，不弹进度框）。
     * 完全复用 TermuxInstaller 的安装逻辑，仅去掉 ProgressDialog 和 ErrorDialog。
     * 成功后 isTermuxReady() == true。
     */
    fun installBootstrapHeadless(context: Context): Boolean {
        try {
            if (isTermuxReady()) {
                Logger.i(TAG, "installBootstrapHeadless: already ready, skipping")
                return true
            }
            Logger.i(TAG, "installBootstrapHeadless: starting")

            val filesDir = context.filesDir
            val stagingDir = File(filesDir, "usr-staging")
            val prefixDir = File(PREFIX)

            // ① 确保 files 目录可访问（与 TermuxApplication.onCreate 一致）
            val error = com.UIN.Tool.shared.termux.file.TermuxFileUtils.isTermuxFilesDirectoryAccessible(context, true, true)
            if (error != null) {
                Logger.e(TAG, "installBootstrapHeadless: files dir not accessible: $error")
                return false
            }
            Logger.i(TAG, "installBootstrapHeadless: files dir OK")

            // ② 清理旧目录
            com.UIN.Tool.shared.file.FileUtils.deleteFile("termux prefix staging directory", stagingDir.absolutePath, true)
            com.UIN.Tool.shared.file.FileUtils.deleteFile("termux prefix directory", prefixDir.absolutePath, true)
            Logger.i(TAG, "installBootstrapHeadless: old dirs cleaned")

            // ③ 创建 staging 目录（prefix 不创建，rename 会自动创建）
            com.UIN.Tool.shared.termux.file.TermuxFileUtils.isTermuxPrefixStagingDirectoryAccessible(true, true)
            Logger.i(TAG, "installBootstrapHeadless: staging dir ready")

            // ④ 确定架构
            val arch = when (android.os.Build.CPU_ABI) {
                "arm64-v8a" -> "aarch64"
                "armeabi-v7a", "armeabi" -> "arm"
                "x86_64" -> "x86_64"
                "x86" -> "i686"
                else -> "aarch64"
            }
            Logger.i(TAG, "installBootstrapHeadless: arch=$arch")

            // ⑤ 复制 assets（与原版 TermuxInstaller 完全一致）
            val decompressedName = "bootstrap-$arch.tar"
            val compressedName = "$decompressedName.xz"
            val xzName = "xz-$arch/xz"
            val liblzmaName = "xz-$arch/liblzma.so.5"

            val decompressedFile = File(filesDir, decompressedName)
            val compressedFile = File(filesDir, compressedName)
            val xzFile = File(filesDir, "xz")
            val liblzmaFile = File(filesDir, "liblzma.so.5")

            // 原版用 filenames[] + filePaths[] 对应复制
            val assetNames = arrayOf(compressedName, xzName, liblzmaName)
            val destFiles = arrayOf(compressedFile, xzFile, liblzmaFile)

            for (i in assetNames.indices) {
                Logger.i(TAG, "installBootstrapHeadless: copying ${assetNames[i]}")
                context.assets.open(assetNames[i]).use { input ->
                    java.io.FileOutputStream(destFiles[i]).use { output ->
                        val buf = ByteArray(8096)
                        var n: Int
                        while (input.read(buf).also { n = it } != -1) {
                            output.write(buf, 0, n)
                        }
                        output.flush()
                    }
                }
                Logger.i(TAG, "installBootstrapHeadless: copied ${destFiles[i].name} (${destFiles[i].length()} bytes)")
            }

            // ⑥ 解压（与原版命令完全一致）
            Logger.i(TAG, "installBootstrapHeadless: chmod +x xz")
            runEarlyCommand(context, "/system/bin/chmod +x ${xzFile.absolutePath}")

            Logger.i(TAG, "installBootstrapHeadless: xz -d")
            runEarlyCommand(context, "${xzFile.absolutePath} -d ${compressedFile.absolutePath}")
            Logger.i(TAG, "installBootstrapHeadless: decompressed exists=${decompressedFile.exists()}, size=${if (decompressedFile.exists()) decompressedFile.length() else 0}")

            Logger.i(TAG, "installBootstrapHeadless: tar -xf")
            runEarlyCommand(context, "/system/bin/tar -xf ${decompressedFile.absolutePath} -C ${stagingDir.absolutePath}")

            // 原版用一条 rm 删三个文件
            Logger.i(TAG, "installBootstrapHeadless: rm temp files")
            runEarlyCommand(context, "/system/bin/rm ${xzFile.absolutePath} ${liblzmaFile.absolutePath} ${decompressedFile.absolutePath}")

            // 检查 staging 内容
            val stagingContents = stagingDir.listFiles()?.map { it.name } ?: emptyList()
            Logger.i(TAG, "installBootstrapHeadless: staging contents=$stagingContents")
            if (stagingContents.isEmpty()) {
                Logger.e(TAG, "installBootstrapHeadless: staging is EMPTY after extraction!")
                return false
            }

            // ⑦ staging → prefix（用 shell mv，Java renameTo 在 Android 上对目录不可靠）
            Logger.i(TAG, "installBootstrapHeadless: staging -> prefix")
            runEarlyCommand(context, "/system/bin/rm -rf ${prefixDir.absolutePath}")
            runEarlyCommand(context, "/system/bin/mv ${stagingDir.absolutePath} ${prefixDir.absolutePath}")
            if (!isTermuxReady()) {
                Logger.e(TAG, "installBootstrapHeadless: prefix/bin not found after mv")
                return false
            }

            Logger.success(TAG, "installBootstrapHeadless: bootstrap installed OK")
            return true
        } catch (e: Exception) {
            Logger.e(TAG, "installBootstrapHeadless failed: ${e.message}", e)
            return false
        }
    }

    /** 是否已安装 Alpine 共享容器 */
    fun isAlpineInstalled(): Boolean {
        if (!File(ALPINE_ROOTFS_DIR).isDirectory) return false
        // 探测普通文件（busybox / alpine-release），避免符号链接在宿主上解析失效
        return File(ALPINE_BUSYBOX).exists() || File(ALPINE_RELEASE).exists()
    }

    /** 检测容器内是否存在指定命令，例如 "python3" */
    fun commandExistsInContainer(command: String): Boolean {
        val rootfsBin = "$ALPINE_ROOTFS_DIR/usr/bin/$command"
        return File(rootfsBin).exists()
    }

    /**
     * 在 assets 根目录查找 Alpine 离线备份资源。
     *
     * 部分 IDE（如 AndroidIDE）打包时会改写扩展名（如 alpine.tar.xz -> alpine.tar），
     * 因此不依赖固定文件名，而是按前缀 "alpine" 枚举查找。
     */
    private fun findAlpineAsset(context: Context): String? {
        return try {
            context.assets.list("")
                ?.firstOrNull { it.lowercase().startsWith("alpine") }
        } catch (e: Exception) {
            Logger.e(TAG, Str.get(R.string.failed_to_enumerate_assets_e_message, e.message), e)
            null
        }
    }

    /** 复制离线 Alpine 备份资源到可写目录，返回目标路径 */
    private fun copyAlpineAsset(context: Context): String? {
        return try {
            val assetName = findAlpineAsset(context) ?: return null
            // 统一命名为 alpine.tar.xz，兼容旧版 proot-distro 按扩展名识别压缩格式
            val dest = File(context.filesDir, ALPINE_ASSET)
            context.assets.open(assetName).use { input ->
                FileOutputStream(dest).use { output ->
                    input.copyTo(output)
                }
            }
            dest.absolutePath
        } catch (e: Exception) {
            Logger.e(TAG, Str.get(R.string.failed_to_copy_offline_alpine_backup, e.message), e)
            null
        }
    }

    /**
     * 执行命令结果
     */
    data class ExecResult(val exitCode: Int, val stdout: String, val stderr: String)

    /**
     * 在 Termux 环境中同步执行命令（bash -lc "..."）。
     *
     * @param context 上下文
     * @param command 要执行的命令（bash -lc 形式，可包含管道/环境变量等）
     * @param workDir 工作目录，可为 null
     */
    fun runInTermuxSync(context: Context, command: String, workDir: String? = null): ExecResult {
        val execCommand = ExecutionCommand(-1, BASH, arrayOf("-lc", command),
            null, workDir ?: "/", ExecutionCommand.Runner.APP_SHELL.getName(), false)
        execCommand.backgroundCustomLogLevel = 0
        return try {
            val appShell = AppShell.execute(context, execCommand, null, TermuxShellEnvironment(), null, true)
            val resultData = execCommand.resultData
            ExecResult(
                exitCode = resultData.exitCode ?: -1,
                stdout = resultData.stdout.toString(),
                stderr = resultData.stderr.toString()
            )
        } catch (e: Exception) {
            Logger.e(TAG, Str.get(R.string.termux_command_failed_e_message, e.message), e)
            ExecResult(-1, "", e.message ?: "")
        }
    }

    /**
     * 检测 Termux 环境，若未初始化则进行初始化。
     *
     * 初始化完成后通过 [whenDone] 回调（在 UI 线程执行）。如果 Termux 已就绪则立即回调。
     *
     * @param activity 用于展示进度弹窗的 Activity
     * @param status 阶段状态回调（在主线程执行，用于 UI 提示当前在做什么）
     * @param whenDone 初始化完成后回调（成功或失败重试后都会进入）
     */
    fun ensureTermux(activity: android.app.Activity, status: ((String) -> Unit)? = null, whenDone: () -> Unit) {
        if (isTermuxReady()) {
            whenDone()
            return
        }
        status?.invoke(Str.get(R.string.initializing_termux_base_environment))
        Logger.i(TAG, Str.get(R.string.termux_not_ready_initializing_bootst))
        com.UIN.Tool.app.TermuxInstaller.setupBootstrapIfNeeded(activity) {
            whenDone()
        }
    }

    /**
     * 检测 Alpine 容器，若未安装则从离线备份恢复。
     *
     * 恢复使用 `proot-distro restore` 命令，alpine.tar.xz 需为 proot-distro backup 生成的备份
     * （备份中可预装 Python 等依赖环境）。恢复完成后通过 [onResult] 回调（在主线程执行，true 成功 / false 失败）。
     *
     * @param context 上下文
     * @param status 阶段状态回调（在主线程执行，用于 UI 提示当前在做什么）
     * @param onResult 恢复结果回调
     */
    @Volatile
    private var _isAlpineInstalling = false
    @Volatile
    private var _alpineInstallResult: Boolean? = null

    fun ensureAlpine(context: Context, status: ((String) -> Unit)? = null, onResult: (Boolean) -> Unit) {
        if (isAlpineInstalled()) {
            Logger.i(TAG, Str.get(R.string.alpine_container_ready))
            onResult(true)
            return
        }

        if (_isAlpineInstalling) {
            Logger.i(TAG, "ensureAlpine: another install in progress, waiting...")
            Thread {
                while (_isAlpineInstalling) { try { Thread.sleep(300) } catch (_: InterruptedException) {} }
                val result = _alpineInstallResult ?: false
                Logger.i(TAG, "ensureAlpine: waited, result=$result")
                postMain(onResult, result)
            }.start()
            return
        }

        synchronized(this) {
            if (_isAlpineInstalling) {
                Thread {
                    while (_isAlpineInstalling) { try { Thread.sleep(300) } catch (_: InterruptedException) {} }
                    postMain(onResult, _alpineInstallResult ?: false)
                }.start()
                return
            }
            _isAlpineInstalling = true
            _alpineInstallResult = null
        }

        Logger.i(TAG, Str.get(R.string.alpine_not_installed_starting_offlin))
        Thread {
            try {
                // ① 先检查离线安装包是否存在，避免在缺失时仍执行耗时的 proot-distro 安装
                val assetName = findAlpineAsset(context)
                if (assetName == null) {
                    postStatus(status, Str.get(R.string.no_offline_package_found_assets_alpi))
                    _alpineInstallResult = false; _isAlpineInstalling = false
                    postMain(onResult, false)
                    return@Thread
                }
                Logger.i(TAG, Str.get(R.string.found_alpine_offline_resource_assetn, assetName))

                // ② proot-distro 默认已随内置 Termux 预装：只做存在性检查，
                //    不再执行 `pkg install proot-distro -y`（联网安装是首装变慢的主要原因）。
                //    若缺失，仅提示后继续（restore 步骤会以清晰错误结束）。
                if (!File(PROOT_DISTRO_BIN).exists()) {
                    Logger.w(TAG, Str.get(R.string.proot_distro_not_installed_installin))
                    postStatus(status, Str.get(R.string.proot_distro_not_installed_installin))
                } else {
                    Logger.i(TAG, Str.get(R.string.proot_distro_installed))
                }

                // ③ 复制离线备份
                postStatus(status, Str.get(R.string.copying_offline_package))
                val assetPath = copyAlpineAsset(context)
                if (assetPath == null) {
                    Logger.e(TAG, Str.get(R.string.alpine_backup_resource_unavailable))
                    postStatus(status, Str.get(R.string.failed_to_copy_offline_package))
                    _alpineInstallResult = false; _isAlpineInstalling = false
                    postMain(onResult, false)
                    return@Thread
                }

                // 若残留不完整的 rootfs，先清理，避免 restore 因目录已存在而失败
                if (!isAlpineInstalled() && File(ALPINE_ROOTFS_DIR).exists()) {
                    Logger.w(TAG, Str.get(R.string.incomplete_alpine_rootfs_found_clean))
                    runInTermuxSync(context, "rm -rf '$ALPINE_ROOTFS_DIR'")
                }

                // ④ 容器名由备份内容自动识别（备份需为 proot-distro backup alpine 生成）
                postStatus(status, Str.get(R.string.restoring_shared_alpine_container_fi))
                val cmd = "$PROOT_DISTRO_BIN restore $assetPath"
                Logger.i(TAG, Str.get(R.string.running_offline_restore_cmd, cmd))
                val result = runInTermuxSync(context, cmd)
                Logger.i(TAG, Str.get(R.string.proot_distro_restore_exit_code_resul, result.exitCode))
                if (result.stdout.isNotBlank()) Logger.d(TAG, "restore stdout: ${result.stdout.trim()}")
                if (result.stderr.isNotBlank()) Logger.d(TAG, "restore stderr: ${result.stderr.trim()}")

                val success = result.exitCode == 0 && isAlpineInstalled()
                if (success) {
                    Logger.success(TAG, Str.get(R.string.alpine_container_restored))
                } else {
                    Logger.e(TAG, Str.get(R.string.alpine_container_restore_failed_resu, result.stderr.trim()))
                }
                _alpineInstallResult = success; _isAlpineInstalling = false
                postMain(onResult, success)
            } catch (e: Exception) {
                Logger.e(TAG, Str.get(R.string.alpine_container_restore_error_e_mes, e.message), e)
                postStatus(status, Str.get(R.string.alpine_container_restore_error_e_mes, e.message))
                _alpineInstallResult = false; _isAlpineInstalling = false
                postMain(onResult, false)
            }
        }.start()
    }

    private fun postStatus(status: ((String) -> Unit)?, message: String) {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            status?.invoke(message)
        }
    }

    private fun postMain(onResult: (Boolean) -> Unit, value: Boolean) {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            onResult(value)
        }
    }

    /**
     * 早期命令执行（bootstrap 安装用）。
     */
    private fun runEarlyCommand(context: Context, cmd: String) {
        try {
            val execCommand = com.UIN.Tool.shared.shell.command.ExecutionCommand(
                -1, "/system/bin/sh", null, "$cmd\n", "/",
                com.UIN.Tool.shared.shell.command.ExecutionCommand.Runner.APP_SHELL.getName(), true
            )
            execCommand.commandLabel = "ProotContainerManager Early Command"
            execCommand.backgroundCustomLogLevel = 0
            com.UIN.Tool.shared.shell.command.runner.app.AppShell.execute(
                context, execCommand, null,
                com.UIN.Tool.shared.termux.shell.command.environment.TermuxShellEnvironment(),
                null, true
            )
        } catch (e: Exception) {
            Logger.e(TAG, "runEarlyCommand failed: $cmd - ${e.message}", e)
        }
    }

    /**
     * 获取 Alpine 容器 rootfs 绝对路径
     */
    fun getAlpineRootFsPath(): String = ALPINE_ROOTFS_DIR
}
