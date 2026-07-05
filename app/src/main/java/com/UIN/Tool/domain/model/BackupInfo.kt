package com.UIN.Tool.domain.model

import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * 备份信息
 */
data class BackupInfo(
    val file: File,
    val name: String,
    val size: Long,
    val date: Long,
    val pluginCount: Int = 0
) {
    
    fun getFormattedDate(): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(date))
    }
    
    fun getFormattedSize(): String {
        return when {
            size < 1024 -> "$size B"
            size < 1024 * 1024 -> String.format("%.2f KB", size / 1024.0)
            else -> String.format("%.2f MB", size / (1024.0 * 1024.0))
        }
    }
}