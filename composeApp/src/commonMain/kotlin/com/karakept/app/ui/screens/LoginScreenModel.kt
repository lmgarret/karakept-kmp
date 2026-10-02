package com.karakept.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.karakept.app.data.repository.ServerRepository
import com.karakept.app.data.repository.ServerVersionRepository
import com.karakept.app.domain.ServerVersionCheck
import com.karakept.app.data.repository.SettingsRepository
import com.karakept.app.data.repository.setActiveServerId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class LoginScreenModel(
    private val serverRepository: ServerRepository,
    private val settingsRepository: SettingsRepository,
    private val remoteDataSource: com.karakept.app.data.remote.RemoteDataSource,
    private val serverVersionRepository: ServerVersionRepository
) : ViewModel() {

    fun addServer(url: String, apiKey: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
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

    /** Reports whether [url] accepts [apiKey] and, when it does, what version the server runs. */
    fun testConnection(url: String, apiKey: String, onResult: (Boolean, ServerVersionCheck?) -> Unit) {
        viewModelScope.launch {
            val success = remoteDataSource.testConnection(url.trim(), apiKey.trim())
            val version = if (success) serverVersionRepository.check(url.trim()) else null
            onResult(success, version)
        }
    }
}
