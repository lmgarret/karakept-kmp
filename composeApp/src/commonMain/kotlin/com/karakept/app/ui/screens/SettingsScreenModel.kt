package com.karakept.app.ui.screens

import androidx.compose.ui.graphics.Color
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.karakept.app.data.model.AccentColor
import com.karakept.app.data.model.LayoutType
import com.karakept.app.data.model.ReaderFontFamily
import com.karakept.app.data.model.Server
import com.karakept.app.data.model.ThemeMode
import com.karakept.app.data.model.ViewerMode
import com.karakept.app.data.repository.ServerRepository
import com.karakept.app.data.repository.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

import cafe.adriel.voyager.navigator.Navigator
import com.karakept.app.data.remote.RemoteDataSource
import com.karakept.app.data.repository.ListRepository
import com.karakept.app.data.remote.model.ListDto
import kotlinx.coroutines.flow.first

class SettingsScreenModel(
    private val settingsRepository: SettingsRepository,
    private val serverRepository: ServerRepository,
    private val remoteDataSource: RemoteDataSource,
    private val listRepository: ListRepository
) : ScreenModel {
    val layoutType: StateFlow<LayoutType> = settingsRepository.layoutType.stateIn(
        scope = screenModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = LayoutType.LIST
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

    val hideArticleThumbnails: StateFlow<Boolean> = settingsRepository.hideArticleThumbnails.stateIn(
        scope = screenModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = true
    )

    val themeMode: StateFlow<ThemeMode> = settingsRepository.themeMode.stateIn(
        scope = screenModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ThemeMode.SYSTEM
    )

    val accentColor: StateFlow<AccentColor> = settingsRepository.accentColor.stateIn(
        scope = screenModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AccentColor.PURPLE
    )

    val htmlTextColor: StateFlow<Color?> = settingsRepository.htmlTextColor.stateIn(
        scope = screenModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    val htmlBackgroundColor: StateFlow<Color?> = settingsRepository.htmlBackgroundColor.stateIn(
        scope = screenModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    val htmlFontSize: StateFlow<Int> = settingsRepository.htmlFontSize.stateIn(
        scope = screenModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 16
    )

    val htmlFontFamily: StateFlow<ReaderFontFamily> = settingsRepository.htmlFontFamily.stateIn(
        scope = screenModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ReaderFontFamily.SYSTEM
    )
    
    val offlineMode: StateFlow<Boolean> = settingsRepository.offlineMode.stateIn(
        scope = screenModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = false
    )

    val showReadingTimeBadge: StateFlow<Boolean> = settingsRepository.showReadingTimeBadge.stateIn(
        scope = screenModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = true
    )

    val showTags: StateFlow<Boolean> = settingsRepository.showTags.stateIn(
        scope = screenModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = true
    )

    val readingSpeedWpm: StateFlow<Int> = settingsRepository.readingSpeedWpm.stateIn(
        scope = screenModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 250
    )

    val swipeLeftAction: StateFlow<com.karakept.app.data.model.SwipeAction> = settingsRepository.swipeLeftAction.stateIn(
        scope = screenModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = com.karakept.app.data.model.SwipeAction.MARK_READ
    )

    val swipeRightAction: StateFlow<com.karakept.app.data.model.SwipeAction> = settingsRepository.swipeRightAction.stateIn(
        scope = screenModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = com.karakept.app.data.model.SwipeAction.ARCHIVE
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

    fun setHideArticleThumbnails(hide: Boolean) {
        screenModelScope.launch {
            settingsRepository.setHideArticleThumbnails(hide)
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        screenModelScope.launch {
            settingsRepository.setThemeMode(mode)
        }
    }

    fun setAccentColor(color: AccentColor) {
        screenModelScope.launch {
            settingsRepository.setAccentColor(color)
        }
    }

    fun setHtmlTextColor(color: Color?) {
        screenModelScope.launch {
            settingsRepository.setHtmlTextColor(color)
        }
    }

    fun setHtmlBackgroundColor(color: Color?) {
        screenModelScope.launch {
            settingsRepository.setHtmlBackgroundColor(color)
        }
    }

    fun setHtmlFontSize(size: Int) {
        screenModelScope.launch {
            settingsRepository.setHtmlFontSize(size)
        }
    }

    fun setHtmlFontFamily(family: ReaderFontFamily) {
        screenModelScope.launch {
            settingsRepository.setHtmlFontFamily(family)
        }
    }
    
    fun setOfflineMode(enabled: Boolean) {
        screenModelScope.launch {
            settingsRepository.setOfflineMode(enabled)
        }
    }

    fun setShowReadingTimeBadge(show: Boolean) {
        screenModelScope.launch {
            settingsRepository.setShowReadingTimeBadge(show)
        }
    }

    fun setShowTags(show: Boolean) {
        screenModelScope.launch {
            settingsRepository.setShowTags(show)
        }
    }

    fun setReadingSpeedWpm(wpm: Int) {
        screenModelScope.launch {
            settingsRepository.setReadingSpeedWpm(wpm)
        }
    }

    fun setSwipeLeftAction(action: com.karakept.app.data.model.SwipeAction) {
        screenModelScope.launch {
            settingsRepository.setSwipeLeftAction(action)
        }
    }

    fun setSwipeRightAction(action: com.karakept.app.data.model.SwipeAction) {
        screenModelScope.launch {
            settingsRepository.setSwipeRightAction(action)
        }
    }

    val dimReadBookmarks: StateFlow<Boolean> = settingsRepository.dimReadBookmarks.stateIn(
        scope = screenModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = true
    )

    val autoMarkReadOnScroll: StateFlow<Boolean> = settingsRepository.autoMarkReadOnScroll.stateIn(
        scope = screenModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = false
    )

    fun setDimReadBookmarks(dim: Boolean) {
        screenModelScope.launch {
            settingsRepository.setDimReadBookmarks(dim)
        }
    }

    fun setAutoMarkReadOnScroll(autoMark: Boolean) {
        screenModelScope.launch {
            settingsRepository.setAutoMarkReadOnScroll(autoMark)
        }
    }

    val notificationsEnabled: StateFlow<Boolean> = settingsRepository.notificationsEnabled.stateIn(
        scope = screenModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = true
    )

    fun setNotificationsEnabled(enabled: Boolean) {
        screenModelScope.launch {
            settingsRepository.setNotificationsEnabled(enabled)
        }
    }

    val contentSyncStrategy: StateFlow<com.karakept.app.data.model.SyncStrategy> = settingsRepository.contentSyncStrategy.stateIn(
        scope = screenModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = com.karakept.app.data.model.SyncStrategy.PER_BOOKMARK
    )

    fun setContentSyncStrategy(strategy: com.karakept.app.data.model.SyncStrategy) {
        screenModelScope.launch {
            settingsRepository.setContentSyncStrategy(strategy)
        }
    }

    val contentSyncTargetLists: StateFlow<Set<String>> = settingsRepository.contentSyncTargetLists.stateIn(
        scope = screenModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptySet()
    )

    val contentSyncConfig: StateFlow<com.karakept.app.data.model.ListSyncConfig> = settingsRepository.contentSyncConfig.stateIn(
        scope = screenModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = com.karakept.app.data.model.ListSyncConfig(emptySet(), emptySet())
    )

    // Expose lists from repository
    val availableLists: StateFlow<List<ListDto>> = listRepository.lists


    fun fetchAvailableLists() {
        screenModelScope.launch {
             // Use explicit type to avoid ambiguity
             val allServers = serverRepository.servers.first()
             var targetServerId = activeServerId.value
             
             if (targetServerId == null && allServers.isNotEmpty()) {
                 val defaultServer = allServers.first()
                 targetServerId = defaultServer.id
                 settingsRepository.setActiveServerId(defaultServer.id)
             }
             
             targetServerId?.let { id ->
                 val server = allServers.find { it.id == id }
                 if (server != null) {
                     listRepository.refreshLists(server)
                 }
             }
        }
    }
    
    fun toggleContentSyncTargetList(listId: String) {
        screenModelScope.launch {
            settingsRepository.toggleContentSyncTargetList(listId)
        }
    }
}
