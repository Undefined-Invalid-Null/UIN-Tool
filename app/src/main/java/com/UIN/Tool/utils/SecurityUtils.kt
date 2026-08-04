package com.UIN.Tool.utils

import com.UIN.Tool.R
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
            Logger.w(TAG, Str.get(R.string.signature_verification_ignored_2))
            return true
        }

        val fileHash = calculateFileHash(file) ?: return false
        val expectedHash = preferenceManager.getPluginSignature(file.name)

        return if (expectedHash.isNullOrEmpty()) {
            preferenceManager.savePluginSignature(file.name, fileHash)
            Logger.i(TAG, Str.get(R.string.first_import_recording_signature_fil, file.name))
            true
        } else {
            val verified = expectedHash == fileHash
            if (!verified) {
                Logger.e(TAG, Str.get(R.string.signature_verification_failed_file_m, file.name))
            } else {
                Logger.success(TAG, Str.get(R.string.signature_verification_passed_file_n, file.name))
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
            Logger.e(TAG, Str.get(R.string.failed_to_compute_hash_file_absolute, file.absolutePath), e)
            null
        }
    }

    fun savePluginSignature(pluginId: String, file: File, preferenceManager: PreferenceManager) {
        val hash = calculateFileHash(file)
        if (hash != null) {
            preferenceManager.savePluginSignature(pluginId, hash)
            Logger.i(TAG, Str.get(R.string.saving_plugin_signature_pluginid, pluginId))
        }
    }

    fun isValidSha256(hash: String): Boolean {
        return hash.matches(Regex("^[a-fA-F0-9]{64}$"))
    }
}