package com.UIN.Tool.domain.repository

import com.UIN.Tool.domain.model.PluginInfo
import kotlinx.coroutines.flow.StateFlow

interface PluginRepository {
    fun getPlugins(): StateFlow<List<PluginInfo>>
    suspend fun getPlugin(pluginId: String): PluginInfo?
    suspend fun installPlugin(filePath: String): PluginInfo?
    suspend fun uninstallPlugin(pluginId: String): Boolean
    suspend fun refreshPlugins()
    fun searchPlugins(keyword: String): List<PluginInfo>
    fun getPluginsByCategory(category: String): List<PluginInfo>
    fun getAllCategories(): List<String>
    suspend fun updatePluginCategory(pluginId: String, category: String): Boolean
}