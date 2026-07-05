// app/src/main/java/com/UIN/Tool/ui/viewmodel/BackupViewModel.kt
package com.UIN.Tool.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.UIN.Tool.core.di.ServiceLocator
import com.UIN.Tool.data.local.FileManager
import com.UIN.Tool.data.repository.BackupRepositoryImpl
import com.UIN.Tool.domain.model.BackupInfo
import com.UIN.Tool.log.Logger
import com.UIN.Tool.utils.Constants
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class BackupViewModel(
    private val context: Context
) : ViewModel() {

    companion object {
        private const val TAG = "BackupViewModel"
    }

    private val fileManager = FileManager(context)
    private val pluginManager = ServiceLocator.getPluginManager()
    private val repository = BackupRepositoryImpl(pluginManager, fileManager)

    private val _backups = MutableStateFlow<List<BackupInfo>>(emptyList())
    val backups: StateFlow<List<BackupInfo>> = _backups.asStateFlow()

    private val _uiState = MutableStateFlow(BackupUiState())
    val uiState: StateFlow<BackupUiState> = _uiState.asStateFlow()

    init {
        loadBackups()
    }

    fun loadBackups() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true)
                repository.loadBackups()
                _backups.value = repository.getBackups().value
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    backupCount = _backups.value.size
                )
                Logger.i(TAG, "加载 ${_backups.value.size} 个备份文件")
            } catch (e: Exception) {
                Logger.e(TAG, "加载备份失败", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message
                )
            }
        }
    }

    fun createBackup() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(
                    isLoading = true,
                    progressText = "正在创建备份..."
                )

                val backup = repository.createBackup { progress ->
                    _uiState.value = _uiState.value.copy(progressText = progress)
                }

                if (backup != null) {
                    _backups.value = repository.getBackups().value
                    Logger.success(TAG, "备份创建成功: ${backup.name}")
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        progressText = "备份完成！"
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "备份创建失败"
                    )
                }
            } catch (e: Exception) {
                Logger.e(TAG, "创建备份失败", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message
                )
            }
        }
    }

    fun restoreBackup(backup: BackupInfo) {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(
                    isLoading = true,
                    progressText = "正在恢复备份..."
                )

                val success = repository.restoreBackup(backup) { progress ->
                    _uiState.value = _uiState.value.copy(progressText = progress)
                }

                if (success) {
                    _backups.value = repository.getBackups().value
                    Logger.success(TAG, "备份恢复成功")
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        progressText = "恢复完成！"
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "恢复失败"
                    )
                }
            } catch (e: Exception) {
                Logger.e(TAG, "恢复备份失败", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message
                )
            }
        }
    }

    fun deleteBackup(backup: BackupInfo) {
        viewModelScope.launch {
            try {
                val success = repository.deleteBackup(backup)
                if (success) {
                    _backups.value = repository.getBackups().value
                    Logger.success(TAG, "删除备份: ${backup.name}")
                }
            } catch (e: Exception) {
                Logger.e(TAG, "删除备份失败", e)
            }
        }
    }

    data class BackupUiState(
        val isLoading: Boolean = false,
        val backupCount: Int = 0,
        val progressText: String = "",
        val error: String? = null
    )
}