package com.UIN.Tool.bootstrap

import android.content.Context
import com.UIN.Tool.R
import com.UIN.Tool.log.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * B-80：后台环境引导（proot 二进制 + 默认 rootfs 容器安装）。
 *
 * 原实现是 [com.UIN.Tool.UinApplication] 直接编排安装与 Toast（God-class 的一段），
 * 现下沉到独立引导器：Application 只负责在 appScope(IO) 里调用一次
 * [autoInstall]，顺序、并发语义与日志文案保持不变。
 *
 * [isEnvironmentInstalling] 由 PluginHostActivity 轮询——安装进行中宿主跳过
 * 自己的安装流程，避免与本引导器并发写同一目录。
 */
object EnvironmentBootstrap {

    private const val TAG = "EnvironmentBootstrap"

    /** 后台环境安装进行中标志，避免与 PluginHostActivity 的安装流程冲突 */
    @Volatile
    @JvmStatic
    private var installing = false

    @JvmStatic
    fun isEnvironmentInstalling(): Boolean = installing

    /**
     * 后台自动安装 proot 二进制和默认 rootfs 容器。
     * 仅在内置 proot 模式下执行；Real Termux 模式跳过。
     * 安装前设置 [isEnvironmentInstalling] 标志，PluginHostActivity 检测到后跳过自身安装，避免冲突。
     */
    fun autoInstall(appScope: CoroutineScope, context: Context) {
        val ctx = context.applicationContext
        if (!com.UIN.Tool.plugin.BackendConfig.isBuiltin(ctx)) {
            Logger.i(TAG, "autoInstallEnvironment: not builtin mode, skip")
            return
        }

        appScope.launch {
            try {
                installing = true
                Logger.i(TAG, "autoInstallEnvironment: === start checking proot & rootfs ===")

                // 1. 确保 proot 二进制已安装
                Logger.i(TAG, "autoInstallEnvironment: [1/2] checking proot binary...")
                val prootResult = com.UIN.Tool.proot.ProotInstaller.ensureInstalled(ctx)
                if (prootResult.isFailure) {
                    Logger.e(TAG, "autoInstallEnvironment: proot install failed: ${prootResult.exceptionOrNull()?.message}")
                    installing = false
                    return@launch
                }
                Logger.i(TAG, "autoInstallEnvironment: [1/2] proot binary ready")

                // 容器就绪即可创建终端会话，不再阻塞
                installing = false

                // 2. 确保默认容器已安装（异步，不阻塞终端）
                val containerName = com.UIN.Tool.proot.RootfsManager.getDefaultContainer(ctx)
                Logger.i(TAG, "autoInstallEnvironment: [2/2] checking container '$containerName'...")
                if (!com.UIN.Tool.proot.RootfsManager.isContainerReady(ctx, containerName)) {
                    Logger.i(TAG, "autoInstallEnvironment: [2/2] container '$containerName' not ready, starting background install")
                    com.UIN.Tool.utils.AppToast.show(ctx, ctx.getString(R.string.container_installing_please_wait, containerName))
                    appScope.launch {
                        val result = com.UIN.Tool.plugin.ProotContainerManager.ensureDefaultContainer(ctx)
                        if (result.isSuccess) {
                            Logger.i(TAG, "autoInstallEnvironment: [2/2] container '$containerName' installed")
                            com.UIN.Tool.utils.AppToast.success(ctx, ctx.getString(R.string.container_install_completed, containerName))
                        } else {
                            Logger.e(TAG, "autoInstallEnvironment: [2/2] container install failed: ${result.exceptionOrNull()?.message}")
                        }
                    }
                } else {
                    Logger.i(TAG, "autoInstallEnvironment: [2/2] container '$containerName' ready")
                }
                Logger.i(TAG, "autoInstallEnvironment: === environment check done ===")
            } catch (e: Exception) {
                installing = false
                Logger.e(TAG, "autoInstallEnvironment failed: ${e.message}", e)
            }
        }
    }
}
