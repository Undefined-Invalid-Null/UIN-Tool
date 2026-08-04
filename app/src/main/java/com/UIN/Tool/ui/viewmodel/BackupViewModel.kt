// app/src/main/java/com/UIN/Tool/ui/viewmodel/BackupViewModel.kt
package com.UIN.Tool.ui.viewmodel

import com.UIN.Tool.R
import com.UIN.Tool.utils.Str
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.UIN.Tool.core.di.ServiceLocator
import com.UIN.Tool.data.local.FileManager
import com.UIN.Tool.data.repository.BackupRepositoryImpl
import com.UIN.Tool.domain.model.BackupInfo
import com.UIN.Tool.log.Logger
import com.UIN.Tool.constants.AppConstants as Constants
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
                Logger.i(TAG, Str.get(R.string.loading_backups_value_size_backup_fi, _backups.value.size))
            } catch (e: Exception) {
                Logger.e(TAG, Str.get(R.string.failed_to_load_backups), e)
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
                    progressText = Str.get(R.string.creating_backup)
                )

                val backup = repository.createBackup { progress ->
                    _uiState.value = _uiState.value.copy(progressText = progress)
                }

                if (backup != null) {
                    _backups.value = repository.getBackups().value
                    Logger.success(TAG, Str.get(R.string.backup_created_backup_name, backup.name))
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        progressText = Str.get(R.string.backup_complete)
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = Str.get(R.string.backup_creation_failed)
                    )
                }
            } catch (e: Exception) {
                Logger.e(TAG, Str.get(R.string.failed_to_create_backup), e)
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
                    progressText = Str.get(R.string.restoring_backup)
                )

                val success = repository.restoreBackup(backup) { progress ->
                    _uiState.value = _uiState.value.copy(progressText = progress)
                }

                if (success) {
                    _backups.value = repository.getBackups().value
                    Logger.success(TAG, Str.get(R.string.backup_restore_successful))
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        progressText = Str.get(R.string.restore_complete)
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = Str.get(R.string.restore_failed)
                    )
                }
            } catch (e: Exception) {
                Logger.e(TAG, Str.get(R.string.failed_to_restore_backup), e)
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
                    Logger.success(TAG, Str.get(R.string.deleting_backup_backup_name, backup.name))
                }
            } catch (e: Exception) {
                Logger.e(TAG, Str.get(R.string.failed_to_delete_backup), e)
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