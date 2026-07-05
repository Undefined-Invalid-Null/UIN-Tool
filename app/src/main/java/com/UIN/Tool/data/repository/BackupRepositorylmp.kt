package com.UIN.Tool.data.repository

import com.UIN.Tool.plugin.PluginManager
import com.UIN.Tool.data.local.FileManager
import com.UIN.Tool.domain.model.BackupInfo
import com.UIN.Tool.domain.repository.BackupRepository
import com.UIN.Tool.log.Logger
import com.UIN.Tool.utils.Constants
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
) : BackupRepository {

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
                Logger.i(TAG, "加载 ${backups.size} 个备份文件")
            } catch (e: Exception) {
                Logger.e(TAG, "加载备份列表失败", e)
            }
        }
    }

    override suspend fun createBackup(progress: (String) -> Unit): BackupInfo? {
        return withContext(Dispatchers.IO) {
            try {
                progress("准备备份...")

                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val plugins = pluginManager.plugins.value
                val backupFile = File(
                    Constants.BACKUP_DIR,
                    "UIN_Tool_Backup_${plugins.size}_$timestamp.zip"
                )
                backupFile.parentFile?.mkdirs()
                if (backupFile.exists()) backupFile.delete()

                progress("正在备份插件 (${plugins.size} 个)...")
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
                    Logger.success(TAG, "备份创建成功: ${backupFile.name}")
                    progress("备份完成！")
                    return@withContext backupInfo
                } else {
                    Logger.e(TAG, "备份创建失败")
                    return@withContext null
                }
            } catch (e: Exception) {
                Logger.e(TAG, "创建备份失败", e)
                return@withContext null
            }
        }
    }

    override suspend fun restoreBackup(backup: BackupInfo, progress: (String) -> Unit): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                progress("准备恢复...")

                val tempDir = File(Constants.TEMP_DIR, "restore_${System.currentTimeMillis()}")
                tempDir.mkdirs()

                progress("解压备份文件...")
                val unzipResult = fileManager.unzipFile(backup.file, tempDir)
                if (!unzipResult) {
                    Logger.e(TAG, "解压备份失败")
                    fileManager.deleteRecursively(tempDir)
                    return@withContext false
                }

                progress("恢复插件...")
                val pluginsBackup = File(tempDir, "plugins")
                if (pluginsBackup.exists()) {
                    val pluginDir = File(Constants.PLUGIN_DIR)
                    if (pluginDir.exists()) {
                        fileManager.deleteRecursively(pluginDir)
                    }
                    pluginDir.mkdirs()
                    fileManager.copyDirectory(pluginsBackup, pluginDir)
                }

                progress("刷新插件列表...")
                pluginManager.refreshPlugins()

                progress("清理临时文件...")
                fileManager.deleteRecursively(tempDir)

                // 刷新备份列表
                loadBackups()

                Logger.success(TAG, "备份恢复成功")
                progress("恢复完成！")
                return@withContext true
            } catch (e: Exception) {
                Logger.e(TAG, "恢复备份失败", e)
                return@withContext false
            }
        }
    }

    override suspend fun deleteBackup(backup: BackupInfo): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                if (backup.file.delete()) {
                    _backups.value = _backups.value.filter { it.file != backup.file }
                    Logger.success(TAG, "删除备份: ${backup.name}")
                    return@withContext true
                }
                return@withContext false
            } catch (e: Exception) {
                Logger.e(TAG, "删除备份失败", e)
                return@withContext false
            }
        }
    }
}