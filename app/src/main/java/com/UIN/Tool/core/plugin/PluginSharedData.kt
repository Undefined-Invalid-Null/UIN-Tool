package com.UIN.Tool.core.plugin

import android.content.Context
import com.UIN.Tool.log.Logger
import com.UIN.Tool.utils.Constants
import org.json.JSONObject
import java.io.File
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * 插件共享数据
 * 所有插件均可读写，用于数据交换
 */
object PluginSharedData {

    private const val TAG = "PluginSharedData"
    private const val SHARED_PREFS_NAME = "plugin_shared_data"
    private const val SHARED_FILE_DIR = "shared_data"

    private lateinit var context: Context
    private var initialized = false
    private val lock = ReentrantReadWriteLock()

    fun init(context: Context) {
        if (initialized) return
        this.context = context.applicationContext
        initialized = true
        Logger.d(TAG, "共享数据已初始化")
    }

    private fun getPrefs(): android.content.SharedPreferences {
        checkInitialized()
        return context.getSharedPreferences(SHARED_PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun getSharedDir(): File {
        checkInitialized()
        val dir = File(Constants.WORK_DIR, SHARED_FILE_DIR)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun checkInitialized() {
        if (!initialized) {
            throw IllegalStateException("PluginSharedData 未初始化，请先调用 init()")
        }
    }

    // ==================== KV 存储 ====================

    fun putString(key: String, value: String) {
        lock.write { getPrefs().edit().putString(key, value).apply() }
        Logger.d(TAG, "putString: $key")
    }

    fun getString(key: String, defaultValue: String = ""): String {
        return lock.read { getPrefs().getString(key, defaultValue) ?: defaultValue }
    }

    fun putInt(key: String, value: Int) {
        lock.write { getPrefs().edit().putInt(key, value).apply() }
    }

    fun getInt(key: String, defaultValue: Int = 0): Int {
        return lock.read { getPrefs().getInt(key, defaultValue) }
    }

    fun putBoolean(key: String, value: Boolean) {
        lock.write { getPrefs().edit().putBoolean(key, value).apply() }
    }

    fun getBoolean(key: String, defaultValue: Boolean = false): Boolean {
        return lock.read { getPrefs().getBoolean(key, defaultValue) }
    }

    fun putLong(key: String, value: Long) {
        lock.write { getPrefs().edit().putLong(key, value).apply() }
    }

    fun getLong(key: String, defaultValue: Long = 0L): Long {
        return lock.read { getPrefs().getLong(key, defaultValue) }
    }

    fun putFloat(key: String, value: Float) {
        lock.write { getPrefs().edit().putFloat(key, value).apply() }
    }

    fun getFloat(key: String, defaultValue: Float = 0f): Float {
        return lock.read { getPrefs().getFloat(key, defaultValue) }
    }

    fun putJSON(key: String, json: JSONObject) {
        putString(key, json.toString())
    }

    fun getJSON(key: String): JSONObject? {
        val str = getString(key, "")
        return try {
            if (str.isNotEmpty()) JSONObject(str) else null
        } catch (e: Exception) {
            Logger.e(TAG, "解析JSON失败: $key", e)
            null
        }
    }

    fun remove(key: String) {
        lock.write { getPrefs().edit().remove(key).apply() }
    }

    fun clear() {
        lock.write { getPrefs().edit().clear().apply() }
    }

    fun contains(key: String): Boolean {
        return lock.read { getPrefs().contains(key) }
    }

    fun getAll(): Map<String, Any> {
        return lock.read {
            @Suppress("UNCHECKED_CAST")
            getPrefs().all as Map<String, Any>
        }
    }

    // ==================== 文件存储 ====================

    private fun getSafeFile(fileName: String): File? {
        if (fileName.contains("..") || fileName.contains("/../") || fileName.startsWith("/")) {
            Logger.w(TAG, "非法文件路径: $fileName")
            return null
        }
        val baseDir = getSharedDir()
        val file = File(baseDir, fileName)
        return try {
            if (file.canonicalPath.startsWith(baseDir.canonicalPath)) file else null
        } catch (e: Exception) {
            null
        }
    }

    fun writeFile(fileName: String, content: String): Boolean {
        val file = getSafeFile(fileName) ?: return false
        return try {
            file.parentFile?.mkdirs()
            file.writeText(content)
            true
        } catch (e: Exception) {
            Logger.e(TAG, "写入共享文件失败: $fileName", e)
            false
        }
    }

    fun writeFileBytes(fileName: String, data: ByteArray): Boolean {
        val file = getSafeFile(fileName) ?: return false
        return try {
            file.parentFile?.mkdirs()
            file.writeBytes(data)
            true
        } catch (e: Exception) {
            Logger.e(TAG, "写入共享文件失败: $fileName", e)
            false
        }
    }

    fun readFile(fileName: String): String? {
        val file = getSafeFile(fileName) ?: return null
        return try {
            if (file.exists()) file.readText() else null
        } catch (e: Exception) {
            Logger.e(TAG, "读取共享文件失败: $fileName", e)
            null
        }
    }

    fun readFileBytes(fileName: String): ByteArray? {
        val file = getSafeFile(fileName) ?: return null
        return try {
            if (file.exists()) file.readBytes() else null
        } catch (e: Exception) {
            null
        }
    }

    fun deleteFile(fileName: String): Boolean {
        val file = getSafeFile(fileName) ?: return false
        return try {
            if (file.exists()) file.delete() else true
        } catch (e: Exception) {
            false
        }
    }

    fun fileExists(fileName: String): Boolean {
        val file = getSafeFile(fileName) ?: return false
        return file.exists()
    }

    fun listFiles(): List<String> {
        return try {
            getSharedDir().listFiles()?.map { it.name }?.sorted() ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
}