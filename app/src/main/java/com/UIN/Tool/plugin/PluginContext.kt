package com.UIN.Tool.plugin

import com.UIN.Tool.R
import com.UIN.Tool.utils.Str
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.content.res.AssetManager
import android.content.res.Resources
import android.os.StatFs
import android.view.LayoutInflater
import com.UIN.Tool.log.Logger
import com.UIN.Tool.constants.AppConstants as Constants
import org.json.JSONObject
import java.io.File
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

private const val TAG = "PluginContext"

/**
 * 插件上下文
 * 为插件提供独立的资源加载能力和数据存储能力
 */
class PluginContext(
    base: Context,
    private val pluginDir: String
) : ContextWrapper(base) {

    private var pluginAssetManager: AssetManager? = null
    private var pluginResources: Resources? = null
    private var pluginInflater: LayoutInflater? = null

    // ==================== 数据存储 ====================
    private val prefs: SharedPreferences by lazy {
        val pluginId = pluginDir.substringAfterLast("/")
        baseContext.getSharedPreferences("${Constants.PREF_PLUGIN_DATA_PREFIX}$pluginId", Context.MODE_PRIVATE)
    }

    private val lock = ReentrantReadWriteLock()
    private val fileLock = Any()

    // 迁移标记
    private var isMigrated = false

    init {
        initPluginResources()
        migrateOldData()
    }

    private fun initPluginResources() {
        try {
            val assetManager = AssetManager::class.java.newInstance()
            val addAssetPath = AssetManager::class.java.getMethod("addAssetPath", String::class.java)

            addAssetPath.invoke(assetManager, pluginDir)

            val resDir = File(pluginDir, "res")
            if (resDir.exists()) {
                addAssetPath.invoke(assetManager, resDir.absolutePath)
            }

            this.pluginAssetManager = assetManager

            val superRes = super.getResources()
            this.pluginResources = Resources(assetManager, superRes.displayMetrics, superRes.configuration)
            this.pluginInflater = LayoutInflater.from(baseContext)

        } catch (e: Exception) {
            this.pluginAssetManager = super.getAssets()
            this.pluginResources = super.getResources()
            this.pluginInflater = LayoutInflater.from(baseContext)
        }
    }

    /**
     * 迁移旧数据（从 web_plugin_ 迁移到 plugin_data_）
     */
    private fun migrateOldData() {
        if (isMigrated) return

        try {
            val pluginId = getPluginId()
            val oldPrefs = baseContext.getSharedPreferences("web_plugin_$pluginId", Context.MODE_PRIVATE)
            val oldData = oldPrefs.all

            if (oldData.isNotEmpty()) {
                Logger.i(TAG, Str.get(R.string.migrating_old_data_olddata_size_item, oldData.size, pluginId))
                oldData.forEach { (key, value) ->
                    when (value) {
                        is String -> putString(key, value)
                        is Boolean -> putBoolean(key, value)
                        is Int -> putInt(key, value)
                        is Float -> putFloat(key, value)
                        is Long -> putLong(key, value)
                        else -> { /* 忽略不支持的类型 */ }
                    }
                }
                oldPrefs.edit().clear().apply()
                Logger.success(TAG, Str.get(R.string.migration_complete_olddata_size_reco, oldData.size))
            }
            isMigrated = true
        } catch (e: Exception) {
            Logger.e(TAG, Str.get(R.string.old_data_migration_failed), e)
            isMigrated = true
        }
    }

    // ==================== 插件基本信息 ====================

    fun getPluginDir(): String = pluginDir

    fun getPluginId(): String = pluginDir.substringAfterLast("/")

    fun getPluginFilePath(relativePath: String): String = "$pluginDir/$relativePath"

    fun hasPluginFile(relativePath: String): Boolean = File(pluginDir, relativePath).exists()

    // ==================== 数据目录 ====================

    fun getPluginDataDir(): File = File(pluginDir, "data").also { if (!it.exists()) it.mkdirs() }

    fun getPluginCacheDir(): File = File(pluginDir, "cache").also { if (!it.exists()) it.mkdirs() }

    // ==================== KV 存储 API ====================

    fun putString(key: String, value: String) {
        lock.write { prefs.edit().putString(key, value).apply() }
        Logger.d(TAG, "putString: $key = ${value.take(50)}")
    }

    fun getString(key: String, defaultValue: String = ""): String {
        return lock.read { prefs.getString(key, defaultValue) ?: defaultValue }
    }

    fun putInt(key: String, value: Int) {
        lock.write { prefs.edit().putInt(key, value).apply() }
    }

    fun getInt(key: String, defaultValue: Int = 0): Int {
        return lock.read { prefs.getInt(key, defaultValue) }
    }

    fun putLong(key: String, value: Long) {
        lock.write { prefs.edit().putLong(key, value).apply() }
    }

    fun getLong(key: String, defaultValue: Long = 0L): Long {
        return lock.read { prefs.getLong(key, defaultValue) }
    }

    fun putBoolean(key: String, value: Boolean) {
        lock.write { prefs.edit().putBoolean(key, value).apply() }
    }

    fun getBoolean(key: String, defaultValue: Boolean = false): Boolean {
        return lock.read { prefs.getBoolean(key, defaultValue) }
    }

    fun putFloat(key: String, value: Float) {
        lock.write { prefs.edit().putFloat(key, value).apply() }
    }

    fun getFloat(key: String, defaultValue: Float = 0f): Float {
        return lock.read { prefs.getFloat(key, defaultValue) }
    }

    fun putJSON(key: String, json: JSONObject) {
        putString(key, json.toString())
    }

    fun getJSON(key: String): JSONObject? {
        val str = getString(key, "")
        return try {
            if (str.isNotEmpty()) JSONObject(str) else null
        } catch (e: Exception) {
            Logger.e(TAG, Str.get(R.string.failed_to_parse_json_key, key), e)
            null
        }
    }

    fun remove(key: String) {
        lock.write { prefs.edit().remove(key).apply() }
    }

    fun clearAll() {
        lock.write { prefs.edit().clear().apply() }
        Logger.d(TAG, Str.get(R.string.clearing_all_kv_data))
    }

    fun contains(key: String): Boolean = lock.read { prefs.contains(key) }

    fun getAllKeys(): List<String> = lock.read { prefs.all.keys.toList() }

    fun getAllEntries(): Map<String, Any> {
        return lock.read {
            @Suppress("UNCHECKED_CAST")
            prefs.all as Map<String, Any>
        }
    }

    // ==================== 文件存储 API ====================

    private fun getSafeFile(fileName: String): File? {
        if (fileName.contains("..") || fileName.contains("/../") || fileName.startsWith("/")) {
            Logger.w(TAG, Str.get(R.string.invalid_file_path_filename_2, fileName))
            return null
        }

        val baseDir = getPluginDataDir()
        val file = File(baseDir, fileName)

        return try {
            if (file.canonicalPath.startsWith(baseDir.canonicalPath)) {
                file
            } else {
                Logger.w(TAG, Str.get(R.string.path_escape_file_canonicalpath, file.canonicalPath))
                null
            }
        } catch (e: Exception) {
            Logger.e(TAG, Str.get(R.string.path_resolution_failed_e_message, e.message))
            null
        }
    }

    private fun hasEnoughSpace(requiredBytes: Long): Boolean {
        return try {
            val stat = StatFs(getPluginDataDir().absolutePath)
            val availableBytes = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR2) {
                stat.availableBytes
            } else {
                stat.availableBlocks.toLong() * stat.blockSize
            }
            availableBytes > requiredBytes + 1024 * 1024
        } catch (e: Exception) {
            true
        }
    }

    fun writeFile(fileName: String, content: String): Boolean {
        synchronized(fileLock) {
            val file = getSafeFile(fileName) ?: return false
            if (!hasEnoughSpace(content.length.toLong())) {
                Logger.e(TAG, Str.get(R.string.insufficient_disk_space))
                return false
            }
            return try {
                file.parentFile?.mkdirs()
                file.writeText(content)
                Logger.d(TAG, Str.get(R.string.writing_file_filename_content_length, fileName, content.length))
                true
            } catch (e: Exception) {
                Logger.e(TAG, Str.get(R.string.failed_to_write_file_filename, fileName), e)
                false
            }
        }
    }

    fun writeFileBytes(fileName: String, data: ByteArray): Boolean {
        synchronized(fileLock) {
            val file = getSafeFile(fileName) ?: return false
            if (!hasEnoughSpace(data.size.toLong())) {
                Logger.e(TAG, Str.get(R.string.insufficient_disk_space))
                return false
            }
            return try {
                file.parentFile?.mkdirs()
                file.writeBytes(data)
                Logger.d(TAG, Str.get(R.string.writing_file_filename_data_size_byte, fileName, data.size))
                true
            } catch (e: Exception) {
                Logger.e(TAG, Str.get(R.string.failed_to_write_file_filename, fileName), e)
                false
            }
        }
    }

    fun readFile(fileName: String): String? {
        synchronized(fileLock) {
            val file = getSafeFile(fileName) ?: return null
            return try {
                if (file.exists()) {
                    file.readText()
                } else {
                    Logger.w(TAG, Str.get(R.string.file_not_found_filename, fileName))
                    null
                }
            } catch (e: Exception) {
                Logger.e(TAG, Str.get(R.string.failed_to_read_file_filename, fileName), e)
                null
            }
        }
    }

    fun readFileBytes(fileName: String): ByteArray? {
        synchronized(fileLock) {
            val file = getSafeFile(fileName) ?: return null
            return try {
                if (file.exists()) {
                    file.readBytes()
                } else {
                    null
                }
            } catch (e: Exception) {
                Logger.e(TAG, Str.get(R.string.failed_to_read_file_filename, fileName), e)
                null
            }
        }
    }

    fun deletePluginFile(fileName: String): Boolean {
        synchronized(fileLock) {
            val file = getSafeFile(fileName) ?: return false
            return try {
                if (file.exists()) {
                    file.delete()
                } else {
                    true
                }
            } catch (e: Exception) {
                Logger.e(TAG, Str.get(R.string.failed_to_delete_file_filename, fileName), e)
                false
            }
        }
    }

    fun fileExists(fileName: String): Boolean {
        synchronized(fileLock) {
            val file = getSafeFile(fileName) ?: return false
            return file.exists()
        }
    }

    fun listPluginFiles(): List<String> {
        synchronized(fileLock) {
            return try {
                getPluginDataDir().listFiles()?.map { it.name }?.sorted() ?: emptyList()
            } catch (e: Exception) {
                Logger.e(TAG, Str.get(R.string.failed_to_list_files), e)
                emptyList()
            }
        }
    }

    fun getPluginFileSize(fileName: String): Long {
        synchronized(fileLock) {
            val file = getSafeFile(fileName) ?: return 0L
            return try {
                if (file.exists()) file.length() else 0L
            } catch (e: Exception) {
                0L
            }
        }
    }

    fun clearPluginCache() {
        try {
            getPluginCacheDir().deleteRecursively()
            getPluginCacheDir().mkdirs()
            Logger.d(TAG, Str.get(R.string.cache_cleared))
        } catch (e: Exception) {
            Logger.e(TAG, Str.get(R.string.failed_to_clear_cache), e)
        }
    }

    fun deleteAllPluginData() {
        try {
            clearAll()
            getPluginDataDir().deleteRecursively()
            getPluginCacheDir().deleteRecursively()
            Logger.d(TAG, Str.get(R.string.all_data_deleted))
        } catch (e: Exception) {
            Logger.e(TAG, Str.get(R.string.failed_to_delete_data), e)
        }
    }

    // ==================== 数据版本管理 ====================

    fun getDataVersion(): Int = getInt(Constants.KEY_PLUGIN_DATA_VERSION, 0)

    fun setDataVersion(version: Int) = putInt(Constants.KEY_PLUGIN_DATA_VERSION, version)

    fun isDataMigrated(): Boolean = getBoolean(Constants.KEY_PLUGIN_DATA_MIGRATED, false)

    fun markDataMigrated() = putBoolean(Constants.KEY_PLUGIN_DATA_MIGRATED, true)

    // ==================== 数据统计 ====================

    data class StorageStats(
        val kvCount: Int,
        val fileCount: Int,
        val totalFileSize: Long,
        val cacheSize: Long
    )

    fun getStorageStats(): StorageStats {
        synchronized(fileLock) {
            val files = getPluginDataDir().listFiles() ?: emptyArray()
            var totalSize = 0L
            for (file in files) {
                if (file.isFile) {
                    totalSize += file.length()
                } else if (file.isDirectory) {
                    file.walk().forEach { f ->
                        if (f.isFile) totalSize += f.length()
                    }
                }
            }
            var cacheSize = 0L
            getPluginCacheDir().walk().forEach { f ->
                if (f.isFile) cacheSize += f.length()
            }

            return StorageStats(
                kvCount = prefs.all.size,
                fileCount = files.size,
                totalFileSize = totalSize,
                cacheSize = cacheSize
            )
        }
    }

    // ==================== 权限状态管理 ====================

    @Deprecated("Deprecated: use per-permission check via PluginPermissionManager instead")
    fun getPermissionState(): Int {
        return getInt("permission_state", 0)
    }

    @Deprecated("Deprecated: use per-permission grant/block via PluginPermissionManager instead")
    fun setPermissionState(state: Int) {
        putInt("permission_state", state)
        putLong("permission_state_timestamp", System.currentTimeMillis())
        Logger.d(TAG, Str.get(R.string.permission_state_updated_state_plugi, state, getPluginId()))
    }

    @Deprecated("Deprecated: use per-permission check via PluginPermissionManager instead")
    fun shouldShowPermissionDialog(): Boolean {
        return getPermissionState() == 0
    }

    @Deprecated("Deprecated: use per-permission check via PluginPermissionManager instead")
    fun getPermissionStateDescription(): String {
        return when (getPermissionState()) {
            0 -> Str.get(R.string.not_granted)
            1 -> Str.get(R.string.granted)
            2 -> Str.get(R.string.denied)
            else -> Str.get(R.string.unknown)
        }
    }

    @Deprecated("Deprecated: use per-permission grant/block via PluginPermissionManager instead")
    fun clearPermissionState() {
        remove("permission_state")
        remove("permission_state_timestamp")
        Logger.d(TAG, Str.get(R.string.permission_state_cleared_plugin_getp, getPluginId()))
    }

    // ==================== 兼容旧方法 ====================

    @Deprecated("Use getPluginDataDir()", ReplaceWith("getPluginDataDir()"))
    override fun getDataDir(): File = getPluginDataDir()

    @Deprecated("Use getPluginCacheDir()", ReplaceWith("getPluginCacheDir()"))
    override fun getCacheDir(): File = getPluginCacheDir()

    @Deprecated("Use deletePluginFile()", ReplaceWith("deletePluginFile(fileName)"))
    override fun deleteFile(fileName: String): Boolean = deletePluginFile(fileName)

    @Deprecated("Use listPluginFiles()", ReplaceWith("listPluginFiles()"))
    fun listFiles(): List<String> = listPluginFiles()

    @Deprecated("Use getPluginFileSize()", ReplaceWith("getPluginFileSize(fileName)"))
    fun getFileSize(fileName: String): Long = getPluginFileSize(fileName)

    @Deprecated("Use clearPluginCache()", ReplaceWith("clearPluginCache()"))
    fun clearCache() = clearPluginCache()

    @Deprecated("Use deleteAllPluginData()", ReplaceWith("deleteAllPluginData()"))
    fun deleteAllData() = deleteAllPluginData()

    // ==================== 覆盖 Context 方法 ====================

    override fun getAssets(): AssetManager {
        return pluginAssetManager ?: super.getAssets()
    }

    override fun getResources(): Resources {
        return pluginResources ?: super.getResources()
    }

    override fun getSystemService(name: String): Any? {
        if (Context.LAYOUT_INFLATER_SERVICE == name) {
            return pluginInflater ?: super.getSystemService(name)
        }
        return super.getSystemService(name)
    }
}