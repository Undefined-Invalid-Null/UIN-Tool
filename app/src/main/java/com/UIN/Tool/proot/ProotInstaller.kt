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

    fun prootBinaryDir(context: Context): File =
        File(context.filesDir, PROOT_DIR)

    fun prootBinaryPath(context: Context): File =
        File(prootBinaryDir(context), PROOT_BIN)

    fun isInstalled(context: Context): Boolean {
        val bin = prootBinaryPath(context)
        return bin.exists() && bin.length() > 0
    }

    fun ensureInstalled(context: Context): Result<File> {
        val bin = prootBinaryPath(context)
        if (bin.exists() && bin.length() > 0) {
            Logger.i(TAG, "ProotInstaller: 已存在，跳过安装: ${bin.absolutePath} (${bin.length()} bytes)")
            chmodExecutable(bin)
            return Result.success(bin)
        }
        Logger.i(TAG, "ProotInstaller: 开始安装 proot 二进制...")
        return try {
            val dir = prootBinaryDir(context)
            dir.mkdirs()
            Logger.i(TAG, "ProotInstaller: 目标目录: ${dir.absolutePath} (exists=${dir.exists()})")
            val abi = getPrimaryAbi()
            Logger.i(TAG, "ProotInstaller: 检测到 ABI: $abi (SUPPORTED_ABIS=${android.os.Build.SUPPORTED_ABIS.joinToString()})")
            val assetName = findProotAsset(context)
                ?: return Result.failure(IllegalStateException("No proot binary asset found for this device"))
            Logger.i(TAG, "ProotInstaller: 正在从 assets 复制: $assetName → ${bin.absolutePath}")
            context.assets.open(assetName).use { input ->
                val size = input.available()
                Logger.i(TAG, "ProotInstaller: asset 大小: $size bytes")
                FileOutputStream(bin).use { output ->
                    input.copyTo(output)
                }
            }
            Logger.i(TAG, "ProotInstaller: 复制完成，文件大小: ${bin.length()} bytes")
            chmodExecutable(bin)
            Logger.i(TAG, "ProotInstaller: 安装完成: ${bin.absolutePath}")
            Result.success(bin)
        } catch (e: Exception) {
            Logger.e(TAG, "ProotInstaller: 安装失败: ${e.message}", e)
            Result.failure(e)
        }
    }

    private fun findProotAsset(context: Context): String? {
        val abi = getPrimaryAbi()
        val candidates = listOf(
            "$PROOT_DIR/${PROOT_ASSET_PREFIX}$abi",
            "$PROOT_DIR/$PROOT_BIN",
            "${PROOT_ASSET_PREFIX}$abi",
            PROOT_ASSET_PREFIX.removeSuffix("-"),
            "proot"
        )
        for (name in candidates) {
            try {
                context.assets.open(name).use {
                    Logger.i(TAG, "ProotInstaller: 找到 asset: $name")
                    return name
                }
            } catch (e: Exception) {
                Logger.d(TAG, "ProotInstaller: asset '$name' 不存在: ${e.message}")
            }
        }
        Logger.e(TAG, "ProotInstaller: 未找到任何 proot asset，已尝试: $candidates")
        return null
    }

    private fun getPrimaryAbi(): String {
        val abis = android.os.Build.SUPPORTED_ABIS
        return when {
            abis.any { it.contains("arm64") || it.contains("aarch64") } -> "aarch64"
            abis.any { it.contains("arm") } -> "arm"
            abis.any { it.contains("x86_64") || it.contains("amd64") } -> "x86_64"
            abis.any { it.contains("x86") || it.contains("i686") || it.contains("i386") } -> "i686"
            else -> "aarch64"
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
