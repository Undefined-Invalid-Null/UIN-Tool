package com.UIN.Tool.domain.repository

import kotlinx.coroutines.flow.StateFlow

interface ConfigRepository {
    fun getWorkFolder(): StateFlow<String>
    suspend fun setWorkFolder(path: String)
    fun getViewMode(): StateFlow<String>
    suspend fun setViewMode(mode: String)
    fun getThemeConfig(): StateFlow<Map<String, String>>
    suspend fun updateThemeConfig(config: Map<String, String>)
    suspend fun resetThemeConfig()
    suspend fun setIconTintEnabled(enabled: Boolean)
    fun isIconTintEnabled(): Boolean
}