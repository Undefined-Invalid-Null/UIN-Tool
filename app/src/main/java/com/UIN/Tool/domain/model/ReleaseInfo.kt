package com.UIN.Tool.domain.model

/**
 * GitHub Release信息
 */
data class ReleaseInfo(
    var tagName: String = "",
    var versionCode: String = "1",
    var versionName: String = "1.0.0",
    var forceFlag: String = "0",
    var forceUpdate: Boolean = false,
    var releaseDate: String = "",
    var releaseNotes: String = "",
    var downloadUrl: String = "",
    var apkSize: Long = 0,
    var isPreRelease: Boolean = false
) {
    
    fun getFormattedDate(): String {
        return if (releaseDate.isNotEmpty()) {
            try {
                releaseDate.substring(0, 10)
            } catch (e: Exception) {
                releaseDate
            }
        } else ""
    }
    
    fun getFormattedSize(): String {
        return when {
            apkSize <= 0 -> "未知"
            apkSize < 1024 -> "${apkSize} B"
            apkSize < 1024 * 1024 -> String.format("%.2f KB", apkSize / 1024.0)
            else -> String.format("%.2f MB", apkSize / (1024.0 * 1024.0))
        }
    }
}