package com.karakept.app.ui.screens

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.karakept.app.data.repository.ServerRepository
import com.karakept.app.data.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * ScreenModel for [OidcLoginScreen].
 *
 * Handles saving the server (with the API key obtained from the OIDC web session)
 * and optionally completing the onboarding flow.
 */
class OidcLoginScreenModel(
    private val settingsRepository: SettingsRepository,
    private val serverRepository: ServerRepository,
) : ScreenModel {

    fun addServer(
        url: String,
        apiKey: String,
        isOnboarding: Boolean,
        onSuccess: () -> Unit,
    ) {
        screenModelScope.launch {
            val trimmedUrl = url.trim()
            serverRepository.addServer(trimmedUrl, apiKey.trim(), trimmedUrl)

            val allServers = serverRepository.servers.first()
            val activeServerId = settingsRepository.activeServerId.first()
            if (activeServerId == null && allServers.size == 1) {
                settingsRepository.setActiveServerId(allServers.first().id)
            }

            if (isOnboarding) {
                settingsRepository.setOnboardingCompleted(true)
            }

            onSuccess()
        }
    }
}
