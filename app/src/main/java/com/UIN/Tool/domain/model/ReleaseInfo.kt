package com.UIN.Tool.domain.model
import com.UIN.Tool.R
import com.UIN.Tool.utils.Str
import com.UIN.Tool.utils.formatFileSize

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
        return if (apkSize <= 0) {
            Str.get(R.string.unknown)
        } else {
            formatFileSize(apkSize)
        }
    }
}