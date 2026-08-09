package com.UIN.Tool.domain.model

import com.UIN.Tool.utils.formatFileSize
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
        return formatFileSize(size)
    }
}