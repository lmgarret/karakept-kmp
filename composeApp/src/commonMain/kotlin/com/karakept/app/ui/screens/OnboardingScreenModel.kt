package com.karakept.app.ui.screens

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.karakept.app.data.remote.RemoteDataSource
import com.karakept.app.data.repository.BackupRepository
import com.karakept.app.data.repository.ServerRepository
import com.karakept.app.data.repository.SettingsRepository
import com.karakept.app.data.repository.setOnboardingCompleted
import com.karakept.app.data.repository.setActiveServerId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class OnboardingScreenModel(
    private val settingsRepository: SettingsRepository,
    private val serverRepository: ServerRepository,
    private val remoteDataSource: RemoteDataSource,
    private val backupRepository: BackupRepository
) : ScreenModel {

    fun completeOnboarding(onDone: () -> Unit) {
        screenModelScope.launch {
            settingsRepository.setOnboardingCompleted(true)
            onDone()
        }
    }

    fun addServer(url: String, apiKey: String, onSuccess: () -> Unit) {
        screenModelScope.launch {
            val trimmedUrl = url.trim()
            serverRepository.addServer(trimmedUrl, apiKey.trim(), trimmedUrl)

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

    /**
     * @param onResult delivers a [Result] containing the import summary message and a flag
     *   indicating whether server connections were restored. When servers are restored the
     *   caller can skip the server-connection step.
     */
    fun importSettings(
        jsonContent: String,
        pin: String,
        onResult: (Result<Pair<String, Boolean>>) -> Unit
    ) {
        screenModelScope.launch {
            try {
                val summary = backupRepository.importFromJson(jsonContent, pin)
                val serversRestored = serverRepository.servers.first().isNotEmpty()
                onResult(Result.success(Pair(summary, serversRestored)))
            } catch (e: Exception) {
                onResult(Result.failure(e))
            }
        }
    }
}
