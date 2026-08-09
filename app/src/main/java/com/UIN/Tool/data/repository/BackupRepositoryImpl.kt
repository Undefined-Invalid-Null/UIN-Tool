package com.UIN.Tool.data.repository

import com.UIN.Tool.R
import com.UIN.Tool.utils.Str
import com.UIN.Tool.plugin.PluginManager
import com.UIN.Tool.data.local.FileManager
import com.UIN.Tool.domain.model.BackupInfo
import com.UIN.Tool.domain.repository.IBackupRepository
import com.UIN.Tool.log.Logger
import com.UIN.Tool.constants.AppConstants as Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class BackupRepositoryImpl(
    private val pluginManager: PluginManager,
    private val fileManager: FileManager
) : IBackupRepository {

    companion object {
        private const val TAG = "BackupRepositoryImpl"
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
                        // 从备份文件名解析插件数量
                        val pluginCount = try {
                            file.name.substringAfter("_").substringBefore(".").toIntOrNull() ?: 0
                        } catch (e: Exception) {
                            0
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
                Logger.i(TAG, Str.get(R.string.loading_backups_size_backup_file_s, backups.size))
            } catch (e: Exception) {
                Logger.e(TAG, Str.get(R.string.failed_to_load_backup_list), e)
            }
        }
    }

    override suspend fun createBackup(progress: (String) -> Unit): BackupInfo? {
        return withContext(Dispatchers.IO) {
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

                progress(Str.get(R.string.backing_up_plugins_plugins_size, plugins.size))
                val result = fileManager.zipDirectory(File(Constants.PLUGIN_DIR), backupFile)

                if (result) {
                    val backupInfo = BackupInfo(
                        file = backupFile,
                        name = backupFile.name,
                        size = backupFile.length(),
                        date = backupFile.lastModified(),
                        pluginCount = plugins.size
                    )
                    _backups.value = (_backups.value + backupInfo).sortedByDescending { it.date }
                    Logger.success(TAG, Str.get(R.string.backup_created_backupfile_name, backupFile.name))
                    progress(Str.get(R.string.backup_complete))
                    return@withContext backupInfo
                } else {
                    Logger.e(TAG, Str.get(R.string.backup_creation_failed))
                    return@withContext null
                }
            } catch (e: Exception) {
                Logger.e(TAG, Str.get(R.string.failed_to_create_backup), e)
                return@withContext null
            }
        }
    }

    override suspend fun restoreBackup(backup: BackupInfo, progress: (String) -> Unit): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                progress(Str.get(R.string.preparing_restore))

                val tempDir = File(Constants.TEMP_DIR, "restore_${System.currentTimeMillis()}")
                tempDir.mkdirs()

                progress(Str.get(R.string.extracting_backup_file))
                val unzipResult = fileManager.unzipFile(backup.file, tempDir)
                if (!unzipResult) {
                    Logger.e(TAG, Str.get(R.string.failed_to_extract_backup))
                    fileManager.deleteRecursively(tempDir)
                    return@withContext false
                }

                progress(Str.get(R.string.restoring_plugins_2))
                val pluginsBackup = File(tempDir, "plugins")
                if (pluginsBackup.exists()) {
                    val pluginDir = File(Constants.PLUGIN_DIR)
                    if (pluginDir.exists()) {
                        fileManager.deleteRecursively(pluginDir)
                    }
                    pluginDir.mkdirs()
                    fileManager.copyDirectory(pluginsBackup, pluginDir)
                }

                progress(Str.get(R.string.refreshing_plugin_list))
                pluginManager.refreshPlugins()

                progress(Str.get(R.string.cleaning_up_temporary_files))
                fileManager.deleteRecursively(tempDir)

                // 刷新备份列表
                loadBackups()

                Logger.success(TAG, Str.get(R.string.backup_restore_successful))
                progress(Str.get(R.string.restore_complete))
                return@withContext true
            } catch (e: Exception) {
                Logger.e(TAG, Str.get(R.string.failed_to_restore_backup), e)
                return@withContext false
            }
        }
    }

    override suspend fun deleteBackup(backup: BackupInfo): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                if (backup.file.delete()) {
                    _backups.value = _backups.value.filter { it.file != backup.file }
                    Logger.success(TAG, Str.get(R.string.deleting_backup_backup_name, backup.name))
                    return@withContext true
                }
                return@withContext false
            } catch (e: Exception) {
                Logger.e(TAG, Str.get(R.string.failed_to_delete_backup), e)
                return@withContext false
            }
        }
    }
}