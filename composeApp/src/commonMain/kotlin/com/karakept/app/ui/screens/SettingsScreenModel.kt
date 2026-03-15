package com.karakept.app.ui.screens

import androidx.compose.ui.graphics.Color
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.karakept.app.data.model.AccentColor
import com.karakept.app.data.model.DateDisplayMode
import com.karakept.app.data.model.LayoutType
import com.karakept.app.data.model.LinkOpenMode
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

import com.karakept.api.model.KarakeepList
import com.karakept.app.data.model.DefaultListType
import com.karakept.app.data.remote.RemoteDataSource
import com.karakept.app.data.repository.ListRepository
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

    val isAutoOffline: StateFlow<Boolean> = settingsRepository.autoOfflineDetected.stateIn(
        scope = screenModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = false
    )

    val effectiveOfflineMode: StateFlow<Boolean> = settingsRepository.effectiveOfflineMode.stateIn(
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
            // Clear auto-detected offline state when manually going online
            if (!enabled) {
                settingsRepository.clearAutoOfflineDetected()
            }
        }
    }

    /**
     * Toggle offline mode. If currently offline (manual or auto), go online.
     * If online, go offline (manual).
     */
    fun toggleOfflineMode() {
        screenModelScope.launch {
            val currentEffectiveOffline = settingsRepository.effectiveOfflineMode.first()
            if (currentEffectiveOffline) {
                // Going online - clear both manual and auto offline
                settingsRepository.setOfflineMode(false)
                settingsRepository.clearAutoOfflineDetected()
            } else {
                // Going offline - set manual offline mode
                settingsRepository.setOfflineMode(true)
            }
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

    val customSwipeActionConfigs: StateFlow<List<com.karakept.app.data.model.CustomSwipeActionConfig>> =
        settingsRepository.customSwipeActionConfigs.stateIn(
            scope = screenModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val swipeLeftConfigId: StateFlow<String?> = settingsRepository.swipeLeftConfigId.stateIn(
        scope = screenModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    val swipeRightConfigId: StateFlow<String?> = settingsRepository.swipeRightConfigId.stateIn(
        scope = screenModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    fun addCustomSwipeActionConfig(config: com.karakept.app.data.model.CustomSwipeActionConfig) {
        screenModelScope.launch {
            val updated = customSwipeActionConfigs.value + config
            settingsRepository.setCustomSwipeActionConfigs(updated)
        }
    }

    fun updateCustomSwipeActionConfig(config: com.karakept.app.data.model.CustomSwipeActionConfig) {
        screenModelScope.launch {
            val updated = customSwipeActionConfigs.value.map {
                if (it.id == config.id) config else it
            }
            settingsRepository.setCustomSwipeActionConfigs(updated)
        }
    }

    fun removeCustomSwipeActionConfig(id: String) {
        screenModelScope.launch {
            val updated = customSwipeActionConfigs.value.filter { it.id != id }
            settingsRepository.setCustomSwipeActionConfigs(updated)
            // Clear config ID if it was assigned to a swipe direction
            if (swipeLeftConfigId.value == id) {
                settingsRepository.setSwipeLeftConfigId(null)
                settingsRepository.setSwipeLeftAction(com.karakept.app.data.model.SwipeAction.NONE)
            }
            if (swipeRightConfigId.value == id) {
                settingsRepository.setSwipeRightConfigId(null)
                settingsRepository.setSwipeRightAction(com.karakept.app.data.model.SwipeAction.NONE)
            }
        }
    }

    fun setSwipeLeftConfigId(id: String?) {
        screenModelScope.launch {
            settingsRepository.setSwipeLeftConfigId(id)
        }
    }

    fun setSwipeRightConfigId(id: String?) {
        screenModelScope.launch {
            settingsRepository.setSwipeRightConfigId(id)
        }
    }

    val dimReadBookmarks: StateFlow<Boolean> = settingsRepository.dimReadBookmarks.stateIn(
        scope = screenModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = true
    )

    val trackReadingProgress: StateFlow<Boolean> = settingsRepository.trackReadingProgress.stateIn(
        scope = screenModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = true
    )

    fun setDimReadBookmarks(dim: Boolean) {
        screenModelScope.launch {
            settingsRepository.setDimReadBookmarks(dim)
        }
    }

    fun setTrackReadingProgress(enabled: Boolean) {
        screenModelScope.launch {
            settingsRepository.setTrackReadingProgress(enabled)
        }
    }

    val resetProgressOnMarkUnread: StateFlow<Boolean> = settingsRepository.resetProgressOnMarkUnread.stateIn(
        scope = screenModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = true
    )

    fun setResetProgressOnMarkUnread(enabled: Boolean) {
        screenModelScope.launch {
            settingsRepository.setResetProgressOnMarkUnread(enabled)
        }
    }

    val showTagsInViewer: StateFlow<Boolean> = settingsRepository.showTagsInViewer.stateIn(
        scope = screenModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = true
    )

    fun setShowTagsInViewer(show: Boolean) {
        screenModelScope.launch {
            settingsRepository.setShowTagsInViewer(show)
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

    val linkOpenMode: StateFlow<LinkOpenMode> = settingsRepository.linkOpenMode.stateIn(
        scope = screenModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = LinkOpenMode.CUSTOM_TAB
    )

    fun setLinkOpenMode(mode: LinkOpenMode) {
        screenModelScope.launch {
            settingsRepository.setLinkOpenMode(mode)
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

    val defaultListType: StateFlow<DefaultListType> = settingsRepository.defaultListType.stateIn(
        scope = screenModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DefaultListType.ALL_BOOKMARKS
    )

    val defaultListId: StateFlow<String?> = settingsRepository.defaultListId.stateIn(
        scope = screenModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    fun setDefaultListType(type: DefaultListType) {
        screenModelScope.launch {
            settingsRepository.setDefaultListType(type)
        }
    }

    fun setDefaultListId(id: String?) {
        screenModelScope.launch {
            settingsRepository.setDefaultListId(id)
        }
    }

    val contentSyncConfig: StateFlow<com.karakept.app.data.model.ListSyncConfig> = settingsRepository.contentSyncConfig.stateIn(
        scope = screenModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = com.karakept.app.data.model.ListSyncConfig(emptySet(), emptySet())
    )

    // Expose lists from repository
    val availableLists: StateFlow<List<KarakeepList>> = listRepository.lists


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

    val showDateInList: StateFlow<Boolean> = settingsRepository.showDateInList.stateIn(
        scope = screenModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = true
    )

    val dateDisplayMode: StateFlow<DateDisplayMode> = settingsRepository.dateDisplayMode.stateIn(
        scope = screenModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DateDisplayMode.ELAPSED
    )

    fun setShowDateInList(show: Boolean) {
        screenModelScope.launch {
            settingsRepository.setShowDateInList(show)
        }
    }

    fun setDateDisplayMode(mode: DateDisplayMode) {
        screenModelScope.launch {
            settingsRepository.setDateDisplayMode(mode)
        }
    }
}
