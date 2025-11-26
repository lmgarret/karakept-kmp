package com.karakept.app.ui.screens

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.karakept.app.data.model.LayoutType
import com.karakept.app.data.model.Server
import com.karakept.app.data.model.ViewerMode
import com.karakept.app.data.repository.ServerRepository
import com.karakept.app.data.repository.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsScreenModel(
    private val settingsRepository: SettingsRepository,
    private val serverRepository: ServerRepository
) : ScreenModel {
    val layoutType: StateFlow<LayoutType> = settingsRepository.layoutType.stateIn(
        scope = screenModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = LayoutType.CARD
    )

    // Viewer mode flow
    val viewerMode: StateFlow<ViewerMode> = settingsRepository.viewerMode.stateIn(
        scope = screenModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ViewerMode.READER
    )

    val servers: StateFlow<List<Server>> = serverRepository.servers.stateIn(
        scope = screenModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val activeServerId: StateFlow<String?> = settingsRepository.activeServerId.stateIn(
        scope = screenModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    fun setLayoutType(layoutType: LayoutType) {
        screenModelScope.launch {
            settingsRepository.setLayoutType(layoutType)
        }
    }

    fun setViewerMode(mode: ViewerMode) {
        screenModelScope.launch {
            settingsRepository.setViewerMode(mode)
        }
    }

    fun setActiveServer(id: String) {
        screenModelScope.launch {
            settingsRepository.setActiveServerId(id)
        }
    }
}
