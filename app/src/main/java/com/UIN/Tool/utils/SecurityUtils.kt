package com.UIN.Tool.utils

import com.UIN.Tool.plugin.PluginManager
import com.UIN.Tool.data.local.PreferenceManager
import com.UIN.Tool.log.Logger
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

object SecurityUtils {

    private const val TAG = "SecurityUtils"

    fun verifyFileSignature(file: File, preferenceManager: PreferenceManager): Boolean {
        if (PluginManager.isIgnoreSignatureWarning()) {
            Logger.w(TAG, "签名验证已忽略")
            return true
        }

        val fileHash = calculateFileHash(file) ?: return false
        val expectedHash = preferenceManager.getPluginSignature(file.name)

        return if (expectedHash.isNullOrEmpty()) {
            preferenceManager.savePluginSignature(file.name, fileHash)
            Logger.i(TAG, "首次导入，记录签名: ${file.name}")
            true
        } else {
            val verified = expectedHash == fileHash
            if (!verified) {
                Logger.e(TAG, "签名验证失败！文件可能被篡改: ${file.name}")
            } else {
                Logger.success(TAG, "签名验证通过: ${file.name}")
            }
            verified
        }
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
            Logger.e(TAG, "计算哈希失败: ${file.absolutePath}", e)
            null
        }
    }

    fun savePluginSignature(pluginId: String, file: File, preferenceManager: PreferenceManager) {
        val hash = calculateFileHash(file)
        if (hash != null) {
            preferenceManager.savePluginSignature(pluginId, hash)
            Logger.i(TAG, "保存插件签名: $pluginId")
        }
    }

    fun isValidSha256(hash: String): Boolean {
        return hash.matches(Regex("^[a-fA-F0-9]{64}$"))
    }
}