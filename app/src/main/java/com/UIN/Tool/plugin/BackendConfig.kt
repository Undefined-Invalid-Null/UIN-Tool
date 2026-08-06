// plugin/BackendConfig.kt
package com.UIN.Tool.plugin

import android.content.Context
import android.content.SharedPreferences

/**
 * 全局后端运行配置。
 *
 * 用户在插件管理页设置，对所有插件生效：
 * - 后端实现：内置简化版 Termux（默认，强制 proot Alpine 容器） / 实体 Termux（com.termux）
 * - 实体 Termux 的后端环境：Termux 本机 / Proot 容器（需填容器名）
 * - 空闲自动回收时长（分钟，默认 5）
 */
object BackendConfig {

    const val IMPL_BUILTIN = "builtin"
    const val IMPL_REAL = "real"

    const val ENV_TERMUX = "termux"
    const val ENV_PROOT = "proot"

    const val CONTAINER_DEFAULT = "alpine"
    const val IDLE_TIMEOUT_DEFAULT_MIN = 5

    /** 内置简化版 Termux 的固定容器名（从 assets 离线恢复） */
    const val BUILTIN_CONTAINER = "alpine"

    private const val PREFS = "uin_backend_prefs"
    private const val KEY_IMPL = "backend_impl"
    private const val KEY_ENV = "backend_env"
    private const val KEY_CONTAINER = "backend_container"
    private const val KEY_IDLE_TIMEOUT = "backend_idle_timeout_min"

    /** 实体 Termux 包名 */
    const val REAL_TERMUX_PACKAGE = "com.termux"

    /** 实体 Termux 的 RunCommandService 类名 */
    const val REAL_TERMUX_RUN_COMMAND_SERVICE = "$REAL_TERMUX_PACKAGE.app.RunCommandService"

    /** 实体 Termux 前缀目录 */
    const val REAL_TERMUX_PREFIX = "/data/data/$REAL_TERMUX_PACKAGE/files/usr"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ==================== 后端实现 ====================

    fun getImplementation(context: Context): String =
        prefs(context).getString(KEY_IMPL, IMPL_BUILTIN) ?: IMPL_BUILTIN

    fun setImplementation(context: Context, impl: String) {
        prefs(context).edit().putString(KEY_IMPL, impl).apply()
    }

    fun isBuiltin(context: Context): Boolean = getImplementation(context) == IMPL_BUILTIN

    fun isRealTermux(context: Context): Boolean = getImplementation(context) == IMPL_REAL

    // ==================== 实体 Termux 环境 ====================

    fun getEnvironment(context: Context): String =
        prefs(context).getString(KEY_ENV, ENV_TERMUX) ?: ENV_TERMUX

    fun setEnvironment(context: Context, env: String) {
        prefs(context).edit().putString(KEY_ENV, env).apply()
    }

    fun getContainer(context: Context): String =
        prefs(context).getString(KEY_CONTAINER, CONTAINER_DEFAULT)?.ifBlank { CONTAINER_DEFAULT }
            ?: CONTAINER_DEFAULT

    fun setContainer(context: Context, container: String) {
        prefs(context).edit().putString(KEY_CONTAINER, container).apply()
    }

    /**
     * 内置简化版 Termux 强制使用 proot Alpine 容器，返回其容器名。
     */
    fun getBuiltinContainer(): String = BUILTIN_CONTAINER

    /**
     * 根据全局配置判断当前后端是否运行在 proot 容器中。
     * 内置版强制 proot；实体版由用户选择。
     */
    fun isProotEnv(context: Context): Boolean {
        return if (isBuiltin(context)) true
        else getEnvironment(context) == ENV_PROOT
    }

    /**
     * 组装可在实体 Termux 里直接粘贴执行的一行初始化命令。
     * 开发页/后端运行设置页与 PluginHostActivity 引导共用同一实现。
     */
    fun buildRealTermuxSetupCode(context: Context): String {
        val prootPart = if (getEnvironment(context) == ENV_PROOT)
            "proot-distro install ${getContainer(context)}"
        else ""
        return buildString {
            append("mkdir -p ~/.termux; ")
            append("grep -q '^allow-external-apps=true' ~/.termux/termux.properties 2>/dev/null || echo 'allow-external-apps=true' >> ~/.termux/termux.properties; ")
            append("termux-setup-storage; ")
            append("termux-reload-settings 2>/dev/null || true")
            if (prootPart.isNotEmpty()) append("; $prootPart")
        }.trimEnd()
    }

    // ==================== 空闲回收 ====================

    fun getIdleTimeoutMinutes(context: Context): Int =
        prefs(context).getInt(KEY_IDLE_TIMEOUT, IDLE_TIMEOUT_DEFAULT_MIN)

    fun setIdleTimeoutMinutes(context: Context, minutes: Int) {
        prefs(context).edit().putInt(KEY_IDLE_TIMEOUT, minutes.coerceIn(1, 120)).apply()
    }
}
