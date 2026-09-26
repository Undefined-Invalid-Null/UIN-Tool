package com.UIN.Tool.plugin

import android.content.Context
import com.UIN.Tool.constants.AppConstants
import com.UIN.Tool.log.Logger
import com.UIN.Tool.proot.ContainerConfig
import com.UIN.Tool.proot.ProotInstaller
import com.UIN.Tool.proot.ProotRuntime
import com.UIN.Tool.proot.RootfsManager
import java.io.File

/**
 * proot 容器运行时管理器（v2）
 *
 * 直接使用 proot 二进制 + rootfs，不再依赖 Termux bootstrap 或 proot-distro。
 */
object ProotContainerManager {

    private const val TAG = "ProotContainerManager"

    /**
     * 检查 proot 二进制和默认容器是否就绪
     */
    fun isReady(context: Context): Boolean {
        return ProotInstaller.isInstalled(context) &&
               RootfsManager.isContainerReady(context, RootfsManager.getDefaultContainer(context))
    }

    /**
     * 确保 proot 二进制已安装
     */
    fun ensureProot(context: Context): Result<File> {
        return ProotInstaller.ensureInstalled(context)
    }

    /**
     * 确保默认容器已安装（从 asset 解压）
     */
    suspend fun ensureDefaultContainer(context: Context): Result<Unit> {
        val name = RootfsManager.getDefaultContainer(context)
        if (RootfsManager.isContainerReady(context, name)) {
            return Result.success(Unit)
        }

        val assetName = findRootfsAsset(context, name)
        return if (assetName != null) {
            RootfsManager.installFromAsset(context, name, assetName)
        } else {
            Result.failure(IllegalStateException("No rootfs asset found for container '$name'"))
        }
    }

    /**
     * 获取当前默认容器配置
     */
    fun getDefaultConfig(context: Context): ContainerConfig {
        val name = RootfsManager.getDefaultContainer(context)
        return ContainerConfig(
            name = name,
            rootfsPath = RootfsManager.rootfsDir(context, name)
        )
    }

    /**
     * 获取指定容器配置
     */
    fun getConfig(context: Context, containerName: String): ContainerConfig {
        return ContainerConfig(
            name = containerName,
            rootfsPath = RootfsManager.rootfsDir(context, containerName)
        )
    }

    /**
     * 在容器中执行命令
     */
    fun execCommand(
        context: Context,
        command: String,
        containerName: String? = null,
        workDir: String? = null,
        extraEnv: Map<String, String> = emptyMap()
    ): Process? {
        val name = containerName ?: RootfsManager.getDefaultContainer(context)
        val config = getConfig(context, name).copy(environmentVars = extraEnv)
        return ProotRuntime.executeCommand(context, config, command, workDir)
    }

    /**
     * 在容器中执行命令并获取输出
     */
    fun execCommandWithOutput(
        context: Context,
        command: String,
        containerName: String? = null
    ): ProotRuntime.CommandResult {
        val name = containerName ?: RootfsManager.getDefaultContainer(context)
        val config = getConfig(context, name)
        return ProotRuntime.executeCommandWithOutput(context, config, command)
    }

    /**
     * 构建后端启动命令
     */
    fun buildBackendCommand(
        context: Context,
        pluginDir: String,
        pluginId: String,
        port: Int,
        startCommand: String,
        containerName: String? = null
    ): List<String> {
        val name = containerName ?: RootfsManager.getDefaultContainer(context)
        val pluginMount = "${AppConstants.CONTAINER_PLUGINS_PREFIX}/$pluginId"
        val config = getConfig(context, name).copy(
            workingDir = pluginMount,
            extraBindMounts = listOf(pluginDir to pluginMount),
            environmentVars = mapOf(
                "HOME" to "/root",
                "PORT" to port.toString(),
                "PLUGIN_ID" to pluginId,
                "PLUGIN_DIR" to pluginMount,
                "WORK_DIR" to pluginMount,
                "PYTHONUNBUFFERED" to "1"
            )
        )
        val envExports = config.environmentVars.entries.joinToString("; ") { (k, v) -> "export $k=$v" }
        val fullCommand = "$envExports; cd $pluginMount && exec $startCommand"
        return ProotRuntime.buildCommand(context, config, fullCommand)
    }

    /**
     * 构建交互式 shell 命令
     */
    fun buildInteractiveShell(
        context: Context,
        containerName: String? = null,
        extraEnv: Map<String, String> = emptyMap()
    ): List<String> {
        val name = containerName ?: RootfsManager.getDefaultContainer(context)
        val config = getConfig(context, name)
        return ProotRuntime.buildInteractiveShell(context, config, extraEnv)
    }

    /**
     * 查找 assets 中的 rootfs 文件
     */
    fun findRootfsAsset(context: Context, containerName: String): String? {
        val candidates = listOf(
            "${containerName}-rootfs.tar.xz",
            "${containerName}.tar.xz",
            "${containerName}-rootfs.tar.gz",
            "${containerName}.tar.gz",
            "${containerName}-rootfs.tar.zst",
            "${containerName}.tar.zst",
            "rootfs-${containerName}.tar.xz",
            "rootfs-${containerName}.tar.gz",
            "rootfs-${containerName}.tar.zst"
        )
        for (name in candidates) {
            try {
                context.assets.open(name).use {
                    Logger.d(TAG, "Found rootfs asset: $name")
                    return name
                }
            } catch (_: Exception) {
                // try next
            }
        }
        // Fallback: scan assets for any file starting with container name
        try {
            val found = context.assets.list("")
                ?.firstOrNull { it.startsWith(containerName) && (it.endsWith(".tar.xz") || it.endsWith(".tar.gz") || it.endsWith(".tar.zst")) }
            if (found != null) {
                Logger.d(TAG, "Found rootfs asset (scan): $found")
                return found
            }
        } catch (_: Exception) {}
        return null
    }

    /**
     * 检查容器内是否存在指定命令
     */
    fun commandExistsInContainer(context: Context, command: String, containerName: String? = null): Boolean {
        val name = containerName ?: RootfsManager.getDefaultContainer(context)
        val rootfs = RootfsManager.rootfsDir(context, name)
        val bins = listOf(
            File(rootfs, "usr/bin/$command"),
            File(rootfs, "bin/$command"),
            File(rootfs, "usr/local/bin/$command")
        )
        return bins.any { it.exists() }
    }

    /**
     * 获取容器 rootfs 路径
     */
    fun getRootfsPath(context: Context, containerName: String? = null): String {
        val name = containerName ?: RootfsManager.getDefaultContainer(context)
        return RootfsManager.rootfsDir(context, name).absolutePath
    }

    /**
     * 获取 proot 二进制路径
     */
    fun getProotPath(context: Context): String {
        return ProotInstaller.prootBinaryPath(context).absolutePath
    }
}
