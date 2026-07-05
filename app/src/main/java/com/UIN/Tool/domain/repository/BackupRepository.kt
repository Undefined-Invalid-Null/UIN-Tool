package com.UIN.Tool.domain.repository

import com.UIN.Tool.domain.model.BackupInfo
import kotlinx.coroutines.flow.StateFlow

interface BackupRepository {
    fun getBackups(): StateFlow<List<BackupInfo>>
    suspend fun createBackup(progress: (String) -> Unit): BackupInfo?
    suspend fun restoreBackup(backup: BackupInfo, progress: (String) -> Unit): Boolean
    suspend fun deleteBackup(backup: BackupInfo): Boolean
    suspend fun loadBackups()
}