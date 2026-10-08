package com.UIN.Tool.proot

import android.content.Context
import android.system.Os
import com.UIN.Tool.log.Logger
import java.io.File
import java.io.FileOutputStream

object ProotInstaller {

    private const val TAG = "ProotInstaller"
    private const val PROOT_DIR = "proot"
    private const val PROOT_BIN = "proot"
    private const val PROOT_ASSET_PREFIX = "proot-"

    /**
     * D-01：小于该大小视为「安装中断留下的残缺二进制」。
     * 内置 proot asset 实测 229~289 KB，被截断的文件无法执行（ENOEXEC），
     * 原先只要 `length() > 0` 就认为已安装 → 永久损坏且不再自愈。
     */
    private const val PROOT_MIN_VALID_SIZE = 64L * 1024L

    fun prootBinaryDir(context: Context): File =
        File(context.filesDir, PROOT_DIR)

    fun prootBinaryPath(context: Context): File =
        File(prootBinaryDir(context), PROOT_BIN)

    fun isInstalled(context: Context): Boolean {
        val bin = prootBinaryPath(context)
        return bin.exists() && bin.length() >= PROOT_MIN_VALID_SIZE
    }

    fun ensureInstalled(context: Context): Result<File> {
        val bin = prootBinaryPath(context)
        if (bin.exists() && bin.length() >= PROOT_MIN_VALID_SIZE) {
            Logger.i(TAG, "ProotInstaller: already exists, skip install: ${bin.absolutePath} (${bin.length()} bytes)")
            chmodExecutable(bin)
            return Result.success(bin)
        }
        if (bin.exists()) {
            Logger.w(TAG, "ProotInstaller: truncated binary detected (${bin.length()} bytes < $PROOT_MIN_VALID_SIZE), reinstalling")
        }
        Logger.i(TAG, "ProotInstaller: installing proot binary...")
        val dir = prootBinaryDir(context)
        val tmp = File(dir, "$PROOT_BIN.tmp")
        return try {
            dir.mkdirs()
            Logger.i(TAG, "ProotInstaller: target dir: ${dir.absolutePath} (exists=${dir.exists()})")
            val abi = getPrimaryAbi()
            Logger.i(TAG, "ProotInstaller: detected ABI: $abi (SUPPORTED_ABIS=${android.os.Build.SUPPORTED_ABIS.joinToString()})")
            val assetName = findProotAsset(context)
                ?: return Result.failure(IllegalStateException("No proot binary asset found for this device"))
            Logger.i(TAG, "ProotInstaller: copying from assets: $assetName -> ${bin.absolutePath}")
            // D-01：先写 .tmp，校验大小后再原子改名，杜绝「半截 proot 却被判定已安装」
            tmp.delete()
            val copied = context.assets.open(assetName).use { input ->
                FileOutputStream(tmp).use { output -> input.copyTo(output) }
            }
            if (copied < PROOT_MIN_VALID_SIZE) {
                tmp.delete()
                return Result.failure(java.io.IOException("proot asset content invalid: copied=$copied bytes"))
            }
            if (bin.exists() && !bin.delete()) {
                tmp.delete()
                return Result.failure(java.io.IOException("failed to replace truncated proot binary"))
            }
            if (!tmp.renameTo(bin)) {
                tmp.delete()
                return Result.failure(java.io.IOException("proot binary atomic rename failed"))
            }
            Logger.i(TAG, "ProotInstaller: copy done, size: ${bin.length()} bytes")
            chmodExecutable(bin)
            Logger.i(TAG, "ProotInstaller: installed: ${bin.absolutePath}")
            Result.success(bin)
        } catch (e: Exception) {
            Logger.e(TAG, "ProotInstaller: install failed: ${e.message}", e)
            tmp.delete()
            if (bin.exists() && bin.length() < PROOT_MIN_VALID_SIZE) bin.delete()
            Result.failure(e)
        }
    }

    private fun findProotAsset(context: Context): String? {
        val abi = getPrimaryAbi()
        // D-39：abi 未知时不拼带 abi 的候选名，避免匹配到别的架构的二进制
        val candidates = buildList {
            if (abi != null) {
                add("$PROOT_DIR/${PROOT_ASSET_PREFIX}$abi")
                add("${PROOT_ASSET_PREFIX}$abi")
            }
            add("$PROOT_DIR/$PROOT_BIN")
            add(PROOT_ASSET_PREFIX.removeSuffix("-"))
            add("proot")
        }
        for (name in candidates) {
            try {
                context.assets.open(name).use {
                    Logger.i(TAG, "ProotInstaller: found asset: $name")
                    return name
                }
            } catch (e: Exception) {
                Logger.d(TAG, "ProotInstaller: asset '$name' missing: ${e.message}")
            }
        }
        Logger.e(TAG, "ProotInstaller: no proot asset found, tried: $candidates")
        return null
    }

    /** D-39：不支持的 ABI 不再兜底成 aarch64（会在 x86/riscv 设备上装错二进制、必然跑不起来） */
    private fun getPrimaryAbi(): String? {
        val abis = android.os.Build.SUPPORTED_ABIS
        return when {
            abis.any { it.contains("arm64") || it.contains("aarch64") } -> "aarch64"
            abis.any { it.contains("arm") } -> "arm"
            abis.any { it.contains("x86_64") || it.contains("amd64") } -> "x86_64"
            abis.any { it.contains("x86") || it.contains("i686") || it.contains("i386") } -> "i686"
            else -> {
                Logger.w(TAG, "ProotInstaller: unsupported ABI: ${abis.joinToString()}")
                null
            }
        }
    }

    private fun chmodExecutable(file: File) {
        try {
            Os.chmod(file.absolutePath, 448) // 0700
        } catch (e: Exception) {
            Logger.w(TAG, "chmod failed for ${file.absolutePath}: ${e.message}")
        }
    }
}
