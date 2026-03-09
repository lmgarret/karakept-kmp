package com.karakept.app.ui.screens

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.karakept.app.data.remote.RemoteDataSource
import com.karakept.app.data.repository.BackupRepository
import com.karakept.app.data.repository.ServerRepository
import com.karakept.app.data.repository.SettingsRepository
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

    fun importSettings(jsonContent: String, onResult: (Result<String>) -> Unit) {
        screenModelScope.launch {
            try {
                val summary = backupRepository.importFromJson(jsonContent)
                onResult(Result.success(summary))
            } catch (e: Exception) {
                onResult(Result.failure(e))
            }
        }
    }
}
