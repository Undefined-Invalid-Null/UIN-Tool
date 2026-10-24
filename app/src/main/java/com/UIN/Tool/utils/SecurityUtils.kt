package com.UIN.Tool.utils

import com.UIN.Tool.R
import com.UIN.Tool.data.local.PreferenceManager
import com.UIN.Tool.log.Logger
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

object SecurityUtils {

    private const val TAG = "SecurityUtils"

    /**
     * 按 pluginId 校验插件包「签名」（SHA-256 文件哈希钉住），记录格式 = `版本:哈希`。
     *
     * 批次53 修「**插件永远无法更新**」：旧实现把首装文件哈希按 pluginId 永久钉死，而开发者
     * 重打包必然改变文件字节 → 同 pluginId 的任何新版本都被判「文件可能被篡改」拒装（安装流程
     * 的覆盖升级、data/ 备份恢复其实都是好的，全被这道关卡拦死）。现按版本区分语义：
     * - 无记录（首次安装）→ 记 `版本:哈希` 放行；
     * - **同版本** → 哈希必须一致（保留防篡改本意：同版本文件被偷换时拒装）；
     * - **版本不同**（升级/降级）→ 按更新处理，重记 `版本:哈希` 放行；
     * - 旧格式裸哈希（6.2.0 之前的记录，无版本前缀）→ 哈希一致放行；不一致也按更新迁移放行
     *   （否则存量用户会被首装哈希永久锁死，无法升级任何已装插件）。
     */
    fun verifyFileSignature(
        pluginId: String,
        version: Int,
        file: File,
        preferenceManager: PreferenceManager
    ): Boolean {
        val fileHash = calculateFileHash(file) ?: return false
        val stored = preferenceManager.getPluginSignature(pluginId)

        if (stored.isNullOrEmpty()) {
            preferenceManager.savePluginSignature(pluginId, "$version:$fileHash")
            Logger.i(TAG, Str.get(R.string.first_import_recording_signature_fil, pluginId))
            return true
        }

        val sep = stored.indexOf(':')
        if (sep < 0) {
            // 旧格式：裸哈希，无版本可比对 —— 一律迁移为「版本:哈希」新格式。
            if (!stored.equals(fileHash, ignoreCase = true)) {
                Logger.i(TAG, Str.get(R.string.saving_plugin_signature_pluginid, pluginId))
            }
            preferenceManager.savePluginSignature(pluginId, "$version:$fileHash")
            return true
        }

        val storedVersion = stored.substring(0, sep).toIntOrNull()
        val storedHash = stored.substring(sep + 1)
        if (storedVersion != null && storedVersion == version) {
            // 同版本：字节必须一致 —— 这才是本机制真正的防篡改点。
            val verified = storedHash.equals(fileHash, ignoreCase = true)
            if (!verified) {
                Logger.e(TAG, Str.get(R.string.signature_verification_failed_file_may, pluginId))
            } else {
                Logger.success(TAG, Str.get(R.string.signature_verification_passed_file_n, pluginId))
            }
            return verified
        }
        // 版本不同 = 开发者更新（或用户主动降级）→ 重记哈希放行。
        preferenceManager.savePluginSignature(pluginId, "$version:$fileHash")
        Logger.i(TAG, Str.get(R.string.saving_plugin_signature_pluginid, pluginId))
        return true
    }

    fun calculateFileHash(file: File): String? {
        return try {
            val md = MessageDigest.getInstance("SHA-256")
            FileInputStream(file).use { fis ->
                val buffer = ByteArray(8192)
                var len: Int
                while (fis.read(buffer).also { len = it } > 0) {
                    md.update(buffer, 0, len)
                }
            }
            md.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            Logger.e(TAG, Str.get(R.string.failed_to_compute_hash_file_absolute, file.absolutePath), e)
            null
        }
    }

    fun savePluginSignature(
        pluginId: String,
        version: Int,
        file: File,
        preferenceManager: PreferenceManager
    ) {
        val hash = calculateFileHash(file)
        if (hash != null) {
            preferenceManager.savePluginSignature(pluginId, "$version:$hash")
            Logger.i(TAG, Str.get(R.string.saving_plugin_signature_pluginid, pluginId))
        }
    }

    fun isValidSha256(hash: String): Boolean {
        return hash.matches(Regex("^[a-fA-F0-9]{64}$"))
    }
}