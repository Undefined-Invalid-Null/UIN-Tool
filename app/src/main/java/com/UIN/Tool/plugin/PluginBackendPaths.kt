// app/src/main/java/com/UIN/Tool/plugin/PluginBackendPaths.kt
package com.UIN.Tool.plugin

import android.content.Context
import com.UIN.Tool.constants.AppConstants
import java.io.File

/**
 * 宿主文件 → 插件后端可见路径。
 *
 * u.txt 第 1 条（用户）：打开插件时不要把文件复制进插件目录，而是让插件直接操作原路径；
 * 并且不要限制文件大小。
 *
 * 后端有三种运行形态，可见范围不同：
 *  - **内置 proot**（默认）：[com.UIN.Tool.proot.ProotRuntime] 默认把 `/storage/emulated/0`
 *    按**原路径** bind 进容器（容器管理页可关）。所以共享存储里的文件不需要复制，
 *    容器内路径就是宿主绝对路径 —— 插件改的就是原文件。
 *  - **实体 Termux 本机**：与宿主同 uid、同一套文件系统，任何路径都直通。
 *  - **实体 Termux + proot**：容器由 proot-distro 管理，挂载集不可控，仍走复制兜底。
 *
 * 只有 [containerPath] 返回 null 时，调用方才需要把文件复制进插件的 `.incoming/`。
 */
object PluginBackendPaths {

    /** 内置 proot 默认挂载的共享存储根（与 ProotRuntime.SHARED_STORAGE 同源）。 */
    private const val SHARED_ROOT = "/storage/emulated/0"

    /** 共享存储当前是否被 bind 进内置容器（容器管理页的开关，默认开）。 */
    fun isSharedStorageBound(context: Context): Boolean = try {
        context.getSharedPreferences(AppConstants.PREF_NAME, Context.MODE_PRIVATE)
            .getBoolean(AppConstants.KEY_PROOT_BIND_SHARED_STORAGE, true)
    } catch (e: Exception) {
        true
    }

    /**
     * 后端能直接读写的路径；返回 null 表示后端看不到这个文件，调用方必须复制。
     *
     * 返回值既是 `filePath`（宿主绝对路径）也是 `containerPath`（容器内路径）——
     * 内置 proot 是原路径挂载，两者本就相同。
     */
    fun containerPath(context: Context, file: File): String? {
        if (!file.isFile) return null
        val abs = runCatching { file.canonicalPath }.getOrDefault(file.absolutePath)

        // 实体 Termux 本机：与宿主共用文件系统，路径直通。
        if (BackendConfig.isRealTermux(context) && !BackendConfig.isProotEnv(context)) {
            return abs
        }
        // 实体 Termux + proot：挂载集由 proot-distro 决定，不赌，走复制。
        if (!BackendConfig.isBuiltin(context)) return null

        if (!isSharedStorageBound(context)) return null
        val root = runCatching { File(SHARED_ROOT).canonicalPath }.getOrDefault(SHARED_ROOT)
        return if (abs == root || abs.startsWith("$root/")) abs else null
    }
}
