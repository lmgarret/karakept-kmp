package com.karakept.app.ui.screens

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.karakept.app.data.repository.ServerRepository
import com.karakept.app.data.repository.SettingsRepository
import com.karakept.app.data.repository.setActiveServerId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class LoginScreenModel(
    private val serverRepository: ServerRepository,
    private val settingsRepository: SettingsRepository,
    private val remoteDataSource: com.karakept.app.data.remote.RemoteDataSource
) : ScreenModel {

    fun addServer(url: String, apiKey: String, onSuccess: () -> Unit) {
        screenModelScope.launch {
            val trimmedUrl = url.trim()
            serverRepository.addServer(trimmedUrl, apiKey.trim(), trimmedUrl)

            // Auto-select this server if it's the only one
            val allServers = serverRepository.servers.first()
            val activeServerId = settingsRepository.activeServerId.first()
            if (activeServerId == null && allServers.size == 1) {
                settingsRepository.setActiveServerId(allServers.first().id)
            }

            onSuccess()
        }
    }

    fun testConnection(url: String, apiKey: String, onResult: (Boolean) -> Unit) {
        screenModelScope.launch {
            val success = remoteDataSource.testConnection(url.trim(), apiKey.trim())
            onResult(success)
        }
    }
}
