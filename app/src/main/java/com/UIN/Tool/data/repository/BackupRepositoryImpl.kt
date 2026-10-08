package com.UIN.Tool.data.repository

import com.UIN.Tool.R
import com.UIN.Tool.utils.Str
import com.UIN.Tool.plugin.PluginManager
import com.UIN.Tool.data.local.FileManager
import com.UIN.Tool.domain.model.BackupInfo
import com.UIN.Tool.domain.model.PluginInfo
import com.UIN.Tool.domain.repository.IBackupRepository
import com.UIN.Tool.log.Logger
import com.UIN.Tool.utils.isValidPluginId
import com.UIN.Tool.constants.AppConstants as Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class BackupRepositoryImpl(
    private val pluginManager: PluginManager,
    private val fileManager: FileManager
) : IBackupRepository {

    companion object {
        private const val TAG = "BackupRepositoryImpl"
        private const val BACKUP_MANIFEST = "backup_info.json"
        private const val BACKUP_PLUGINS_DIR = "plugins"
        private const val FORMAT_VERSION = 1
    }

    private val _backups = MutableStateFlow<List<BackupInfo>>(emptyList())
    override fun getBackups(): StateFlow<List<BackupInfo>> = _backups.asStateFlow()

    override suspend fun loadBackups() {
        withContext(Dispatchers.IO) {
            try {
                val backupDir = File(Constants.BACKUP_DIR)
                if (!backupDir.exists()) backupDir.mkdirs()

                val backups = backupDir.listFiles()
                    ?.filter { it.isFile && it.name.endsWith(".zip") }
                    ?.map { file ->
                        // 从备份清单解析插件数量，解析失败回退到文件名
                        val pluginCount = readBackupManifest(file)?.optInt("pluginCount", 0) ?: run {
                            try {
                                file.name.substringAfter("_").substringBefore(".").toIntOrNull() ?: 0
                            } catch (e: Exception) {
                                0
                            }
                        }
                        BackupInfo(
                            file = file,
                            name = file.name,
                            size = file.length(),
                            date = file.lastModified(),
                            pluginCount = pluginCount
                        )
                    }
                    ?.sortedByDescending { it.date }
                    ?: emptyList()

                _backups.value = backups
                Logger.i(TAG, "Loading ${backups.size} backup file(s)")
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to load backup list", e)
            }
        }
    }

    /** 读取 zip 内的备份清单（不展开整个包，只读 manifest）。 */
    private fun readBackupManifest(file: File): JSONObject? {
        return try {
            java.util.zip.ZipFile(file).use { zf ->
                val entry = zf.getEntry(BACKUP_MANIFEST) ?: return null
                val content = zf.getInputStream(entry).bufferedReader().use { it.readText() }
                JSONObject(content)
            }
        } catch (e: Exception) {
            Logger.w(TAG, "Failed to read backup manifest: ${e.message ?: ""}")
            null
        }
    }

    override suspend fun createBackup(progress: (String) -> Unit): BackupInfo? {
        return withContext(Dispatchers.IO) {
            var stageDir: File? = null
            try {
                progress(Str.get(R.string.preparing_backup))

                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val plugins = pluginManager.plugins.value
                val backupFile = File(
                    Constants.BACKUP_DIR,
                    "UIN_Tool_Backup_${plugins.size}_$timestamp.zip"
                )
                backupFile.parentFile?.mkdirs()
                if (backupFile.exists()) backupFile.delete()

                // 统一格式：stage/<plugins/...> + stage/backup_info.json 再整体压缩
                stageDir = File(Constants.TEMP_DIR, "backup_stage_${System.currentTimeMillis()}")
                val pluginsStage = File(stageDir, BACKUP_PLUGINS_DIR)
                pluginsStage.mkdirs()

                progress(Str.get(R.string.backing_up_plugins_plugins_size, plugins.size))
                val pluginDir = File(Constants.PLUGIN_DIR)
                if (pluginDir.exists() && !fileManager.copyDirectory(pluginDir, pluginsStage)) {
                    Logger.e(TAG, "Backup creation failed")
                    return@withContext null
                }

                // 写入清单，便于恢复前校验与插件数量展示
                val manifest = JSONObject().apply {
                    put("formatVersion", FORMAT_VERSION)
                    put("appVersion", Constants.APP_VERSION)
                    put("pluginCount", plugins.size)
                    put("createdAt", timestamp)
                }
                File(stageDir, BACKUP_MANIFEST).writeText(manifest.toString())

                val result = fileManager.zipDirectory(stageDir, backupFile)

                if (result) {
                    val backupInfo = BackupInfo(
                        file = backupFile,
                        name = backupFile.name,
                        size = backupFile.length(),
                        date = backupFile.lastModified(),
                        pluginCount = plugins.size
                    )
                    _backups.value = (_backups.value + backupInfo).sortedByDescending { it.date }
                    Logger.success(TAG, "Backup created: ${backupFile.name}")
                    progress(Str.get(R.string.backup_complete))
                    return@withContext backupInfo
                } else {
                    Logger.e(TAG, "Backup creation failed")
                    return@withContext null
                }
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to create backup", e)
                return@withContext null
            } finally {
                stageDir?.let { fileManager.deleteRecursively(it) }
            }
        }
    }

    override suspend fun restoreBackup(backup: BackupInfo, progress: (String) -> Unit): Boolean {
        return withContext(Dispatchers.IO) {
            var tempDir: File? = null
            var stageDir: File? = null
            var oldDir: File? = null
            // G-03：顶替是否已发生 —— 回滚条件靠它判断（原条件 stageDir!=null && oldDir!=null
            // 在成功置 null 之后恒为假，回滚实为死代码）
            var swapped = false
            try {
                progress(Str.get(R.string.preparing_restore))

                tempDir = File(Constants.TEMP_DIR, "restore_${System.currentTimeMillis()}")
                tempDir.mkdirs()

                progress(Str.get(R.string.extracting_backup_file))
                if (!fileManager.unzipFile(backup.file, tempDir)) {
                    Logger.e(TAG, "Failed to extract backup")
                    return@withContext false
                }

                // 统一解析：优先 <plugins/> 子目录（新格式），否则兼容旧格式（插件目录直接位于根）
                val pluginsBackup = File(tempDir, BACKUP_PLUGINS_DIR)
                val pluginsRoot = if (pluginsBackup.exists()) pluginsBackup else tempDir

                // 恢复前校验：插件目录名=pluginId 且 plugin.json 合法
                progress(Str.get(R.string.validating_backup_content))
                val validationError = validatePluginsDirectory(pluginsRoot)
                if (validationError != null) {
                    Logger.e(TAG, validationError)
                    return@withContext false
                }

                // 暂存到 stage，原子替换前先确认已完整复制
                progress(Str.get(R.string.restoring_plugins_2))
                stageDir = File(Constants.TEMP_DIR, "restore_stage_${System.currentTimeMillis()}")
                stageDir.mkdirs()
                if (!fileManager.copyDirectory(pluginsRoot, stageDir)) {
                    Logger.e(TAG, "Failed to stage backup content")
                    return@withContext false
                }

                // 原子替换：现目录改名为 .old，stage 顶替为现目录
                val pluginDir = File(Constants.PLUGIN_DIR)
                val oldName = "${Constants.PLUGIN_DIR}.old.${System.currentTimeMillis()}"
                oldDir = File(oldName)
                if (pluginDir.exists()) {
                    if (!pluginDir.renameTo(oldDir)) {
                        Logger.e(TAG, "Failed to atomically replace plugin directory")
                        oldDir = null
                        return@withContext false
                    }
                }
                if (!stageDir.renameTo(pluginDir)) {
                    // 顶替失败：回滚旧目录
                    Logger.e(TAG, "Failed to atomically replace plugin directory")
                    if (oldDir.exists()) oldDir.renameTo(pluginDir)
                    oldDir = null
                    return@withContext false
                }
                stageDir = null
                swapped = true
                // G-03：oldDir 保留到后续步骤全部成功后再清理，中途异常才有东西可回滚

                progress(Str.get(R.string.refreshing_plugin_list))
                pluginManager.refreshPlugins()

                progress(Str.get(R.string.cleaning_up_temporary_files))
                fileManager.deleteRecursively(tempDir)
                tempDir = null

                loadBackups()

                // 恢复全流程成功 → 才回收被顶替的旧目录
                oldDir?.let { fileManager.deleteRecursively(it) }
                oldDir = null

                Logger.success(TAG, "Backup restore successful")
                progress(Str.get(R.string.restore_complete))
                return@withContext true
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to restore backup", e)
                // G-03：状态机回滚 —— 已顶替且旧目录仍在时，删除新目录并把旧目录改名还原
                try {
                    if (swapped && oldDir != null && oldDir.exists()) {
                        val pluginDir = File(Constants.PLUGIN_DIR)
                        fileManager.deleteRecursively(pluginDir)
                        if (oldDir.renameTo(pluginDir)) {
                            Logger.i(TAG, "restore: rollback succeeded, previous plugin dir restored")
                            oldDir = null
                        } else {
                            Logger.e(TAG, "Failed to rollback restore")
                        }
                    }
                } catch (rollback: Exception) {
                    Logger.e(TAG, "Failed to rollback restore", rollback)
                }
                return@withContext false
            } finally {
                tempDir?.let { fileManager.deleteRecursively(it) }
                stageDir?.let { fileManager.deleteRecursively(it) }
            }
        }
    }

    /**
     * 校验插件备份目录：子目录名必须等于合法 pluginId，且含可解析的 plugin.json。
     * 返回 null 表示通过，否则返回错误信息。
     */
    private fun validatePluginsDirectory(root: File): String? {
        val entries = root.listFiles() ?: return Str.get(R.string.backup_contains_no_plugins)
        if (entries.isEmpty()) return Str.get(R.string.backup_contains_no_plugins)

        for (entry in entries) {
            if (!entry.isDirectory) continue
            val pluginId = entry.name
            if (!pluginId.isValidPluginId()) {
                Logger.e(TAG, "Invalid plugin ID format: $pluginId")
                return Str.get(R.string.backup_contains_invalid_plugin_id_pluginid, pluginId)
            }
            val configFile = File(entry, Constants.PLUGIN_CONFIG_FILE)
            if (!configFile.exists() || !configFile.isFile) {
                Logger.e(TAG, "Missing plugin.json")
                return Str.get(R.string.backup_plugin_missing_config_pluginid, pluginId)
            }
            val info = try {
                PluginInfo.fromJson(configFile.readText())
            } catch (e: Exception) {
                null
            }
            if (info == null || info.pluginId != pluginId) {
                return Str.get(R.string.backup_plugin_config_invalid_pluginid, pluginId)
            }
        }
        return null
    }

    override suspend fun deleteBackup(backup: BackupInfo): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                if (backup.file.delete()) {
                    _backups.value = _backups.value.filter { it.file != backup.file }
                    Logger.success(TAG, "Deleting backup: ${backup.name}")
                    return@withContext true
                }
                return@withContext false
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to delete backup", e)
                return@withContext false
            }
        }
    }
}
