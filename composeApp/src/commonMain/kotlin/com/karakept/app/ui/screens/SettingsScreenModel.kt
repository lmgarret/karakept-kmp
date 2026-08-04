package com.karakept.app.ui.screens

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import com.karakept.app.data.repository.setLayoutType
import com.karakept.app.data.repository.setViewerMode
import com.karakept.app.data.repository.setActiveServerId
import com.karakept.app.data.repository.setHideArticleThumbnails
import com.karakept.app.data.repository.setThemeMode
import com.karakept.app.data.repository.setAccentColor
import com.karakept.app.data.repository.setHtmlTextColor
import com.karakept.app.data.repository.setHtmlBackgroundColor
import com.karakept.app.data.repository.setHtmlFontSize
import com.karakept.app.data.repository.setHtmlFontFamily
import com.karakept.app.data.repository.setOfflineMode
import com.karakept.app.data.repository.setShowReadingTimeBadge
import com.karakept.app.data.repository.setShowTags
import com.karakept.app.data.repository.setReadingSpeedWpm
import com.karakept.app.data.repository.setSwipeLeftAction
import com.karakept.app.data.repository.setSwipeRightAction
import com.karakept.app.data.repository.setCustomSwipeActionConfigs
import com.karakept.app.data.repository.setSwipeLeftConfigId
import com.karakept.app.data.repository.setSwipeRightConfigId
import com.karakept.app.data.repository.setDimReadBookmarks
import com.karakept.app.data.repository.setShowScrollCursor
import com.karakept.app.data.repository.setTrackReadingProgress
import com.karakept.app.data.repository.setResetProgressOnMarkUnread
import com.karakept.app.data.repository.setShowTagsInViewer
import com.karakept.app.data.repository.setPreferFullPageHtml
import com.karakept.app.data.repository.setNotificationsEnabled
import com.karakept.app.data.repository.setLinkOpenMode
import com.karakept.app.data.repository.setContentSyncStrategy
import com.karakept.app.data.repository.setDefaultListType
import com.karakept.app.data.repository.setDefaultListId
import com.karakept.app.data.repository.setShowDateInList
import com.karakept.app.data.repository.setDateDisplayMode
import com.karakept.app.data.repository.toggleContentSyncTargetList
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
) : ViewModel() {
    val layoutType: StateFlow<LayoutType> = settingsRepository.layoutType.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = LayoutType.LIST
    )

    // Viewer mode flow
    val viewerMode: StateFlow<ViewerMode> = settingsRepository.viewerMode.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ViewerMode.READER
    )

    val servers: StateFlow<List<Server>> = serverRepository.servers.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val activeServerId: StateFlow<String?> = settingsRepository.activeServerId.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    val hideArticleThumbnails: StateFlow<Boolean> = settingsRepository.hideArticleThumbnails.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = true
    )

    val themeMode: StateFlow<ThemeMode> = settingsRepository.themeMode.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ThemeMode.SYSTEM
    )

    val accentColor: StateFlow<AccentColor> = settingsRepository.accentColor.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AccentColor.PURPLE
    )

    val htmlTextColor: StateFlow<Color?> = settingsRepository.htmlTextColor.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    val htmlBackgroundColor: StateFlow<Color?> = settingsRepository.htmlBackgroundColor.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    val htmlFontSize: StateFlow<Int> = settingsRepository.htmlFontSize.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 16
    )

    val htmlFontFamily: StateFlow<ReaderFontFamily> = settingsRepository.htmlFontFamily.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ReaderFontFamily.SYSTEM
    )
    
    val offlineMode: StateFlow<Boolean> = settingsRepository.offlineMode.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = false
    )

    val showReadingTimeBadge: StateFlow<Boolean> = settingsRepository.showReadingTimeBadge.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = true
    )

    val showTags: StateFlow<Boolean> = settingsRepository.showTags.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = true
    )

    val readingSpeedWpm: StateFlow<Int> = settingsRepository.readingSpeedWpm.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 250
    )

    val swipeLeftAction: StateFlow<com.karakept.app.data.model.SwipeAction> = settingsRepository.swipeLeftAction.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = com.karakept.app.data.model.SwipeAction.MARK_READ
    )

    val swipeRightAction: StateFlow<com.karakept.app.data.model.SwipeAction> = settingsRepository.swipeRightAction.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = com.karakept.app.data.model.SwipeAction.ARCHIVE
    )

    fun setLayoutType(layoutType: LayoutType) {
        viewModelScope.launch {
            settingsRepository.setLayoutType(layoutType)
        }
    }

    fun setViewerMode(mode: ViewerMode) {
        viewModelScope.launch {
            settingsRepository.setViewerMode(mode)
        }
    }

    fun setActiveServer(id: String) {
        viewModelScope.launch {
            settingsRepository.setActiveServerId(id)
        }
    }

    fun setHideArticleThumbnails(hide: Boolean) {
        viewModelScope.launch {
            settingsRepository.setHideArticleThumbnails(hide)
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            settingsRepository.setThemeMode(mode)
        }
    }

    fun setAccentColor(color: AccentColor) {
        viewModelScope.launch {
            settingsRepository.setAccentColor(color)
        }
    }

    fun setHtmlTextColor(color: Color?) {
        viewModelScope.launch {
            settingsRepository.setHtmlTextColor(color)
        }
    }

    fun setHtmlBackgroundColor(color: Color?) {
        viewModelScope.launch {
            settingsRepository.setHtmlBackgroundColor(color)
        }
    }

    fun setHtmlFontSize(size: Int) {
        viewModelScope.launch {
            settingsRepository.setHtmlFontSize(size)
        }
    }

    fun setHtmlFontFamily(family: ReaderFontFamily) {
        viewModelScope.launch {
            settingsRepository.setHtmlFontFamily(family)
        }
    }
    
    fun setOfflineMode(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setOfflineMode(enabled)
        }
    }

    /** Toggle the user's manual offline mode. */
    fun toggleOfflineMode() {
        viewModelScope.launch {
            settingsRepository.setOfflineMode(!settingsRepository.offlineMode.first())
        }
    }

    fun setShowReadingTimeBadge(show: Boolean) {
        viewModelScope.launch {
            settingsRepository.setShowReadingTimeBadge(show)
        }
    }

    fun setShowTags(show: Boolean) {
        viewModelScope.launch {
            settingsRepository.setShowTags(show)
        }
    }

    fun setReadingSpeedWpm(wpm: Int) {
        viewModelScope.launch {
            settingsRepository.setReadingSpeedWpm(wpm)
        }
    }

    fun setSwipeLeftAction(action: com.karakept.app.data.model.SwipeAction) {
        viewModelScope.launch {
            settingsRepository.setSwipeLeftAction(action)
        }
    }

    fun setSwipeRightAction(action: com.karakept.app.data.model.SwipeAction) {
        viewModelScope.launch {
            settingsRepository.setSwipeRightAction(action)
        }
    }

    val customSwipeActionConfigs: StateFlow<List<com.karakept.app.data.model.CustomSwipeActionConfig>> =
        settingsRepository.customSwipeActionConfigs.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val swipeLeftConfigId: StateFlow<String?> = settingsRepository.swipeLeftConfigId.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    val swipeRightConfigId: StateFlow<String?> = settingsRepository.swipeRightConfigId.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    fun addCustomSwipeActionConfig(config: com.karakept.app.data.model.CustomSwipeActionConfig) {
        viewModelScope.launch {
            val updated = customSwipeActionConfigs.value + config
            settingsRepository.setCustomSwipeActionConfigs(updated)
        }
    }

    fun updateCustomSwipeActionConfig(config: com.karakept.app.data.model.CustomSwipeActionConfig) {
        viewModelScope.launch {
            val updated = customSwipeActionConfigs.value.map {
                if (it.id == config.id) config else it
            }
            settingsRepository.setCustomSwipeActionConfigs(updated)
        }
    }

    fun removeCustomSwipeActionConfig(id: String) {
        viewModelScope.launch {
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
        viewModelScope.launch {
            settingsRepository.setSwipeLeftConfigId(id)
        }
    }

    fun setSwipeRightConfigId(id: String?) {
        viewModelScope.launch {
            settingsRepository.setSwipeRightConfigId(id)
        }
    }

    val dimReadBookmarks: StateFlow<Boolean> = settingsRepository.dimReadBookmarks.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = true
    )

    val trackReadingProgress: StateFlow<Boolean> = settingsRepository.trackReadingProgress.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = true
    )

    fun setDimReadBookmarks(dim: Boolean) {
        viewModelScope.launch {
            settingsRepository.setDimReadBookmarks(dim)
        }
    }

    val showScrollCursor: StateFlow<Boolean> = settingsRepository.showScrollCursor.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = true
    )

    fun setShowScrollCursor(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setShowScrollCursor(enabled)
        }
    }

    fun setTrackReadingProgress(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setTrackReadingProgress(enabled)
        }
    }

    val resetProgressOnMarkUnread: StateFlow<Boolean> = settingsRepository.resetProgressOnMarkUnread.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = true
    )

    fun setResetProgressOnMarkUnread(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setResetProgressOnMarkUnread(enabled)
        }
    }

    val showTagsInViewer: StateFlow<Boolean> = settingsRepository.showTagsInViewer.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = true
    )

    fun setShowTagsInViewer(show: Boolean) {
        viewModelScope.launch {
            settingsRepository.setShowTagsInViewer(show)
        }
    }

    val preferFullPageHtml: StateFlow<Boolean> = settingsRepository.preferFullPageHtml.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = false
    )

    fun setPreferFullPageHtml(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setPreferFullPageHtml(enabled)
        }
    }

    val notificationsEnabled: StateFlow<Boolean> = settingsRepository.notificationsEnabled.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = true
    )

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setNotificationsEnabled(enabled)
        }
    }

    val linkOpenMode: StateFlow<LinkOpenMode> = settingsRepository.linkOpenMode.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = LinkOpenMode.CUSTOM_TAB
    )

    fun setLinkOpenMode(mode: LinkOpenMode) {
        viewModelScope.launch {
            settingsRepository.setLinkOpenMode(mode)
        }
    }

    val backgroundSyncEnabled: StateFlow<Boolean> = settingsRepository.backgroundSyncEnabled.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = false
    )

    val backgroundSyncFrequencyMinutes: StateFlow<Int> = settingsRepository.backgroundSyncFrequencyMinutes.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 60
    )

    val backgroundSyncDigestNotification: StateFlow<Boolean> = settingsRepository.backgroundSyncDigestNotification.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = false
    )

    fun setBackgroundSyncEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setBackgroundSyncEnabled(enabled)
        }
    }

    fun setBackgroundSyncFrequencyMinutes(minutes: Int) {
        viewModelScope.launch {
            settingsRepository.setBackgroundSyncFrequencyMinutes(minutes)
        }
    }

    fun setBackgroundSyncDigestNotification(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setBackgroundSyncDigestNotification(enabled)
        }
    }

    val contentSyncStrategy: StateFlow<com.karakept.app.data.model.SyncStrategy> = settingsRepository.contentSyncStrategy.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = com.karakept.app.data.model.SyncStrategy.PER_BOOKMARK
    )

    fun setContentSyncStrategy(strategy: com.karakept.app.data.model.SyncStrategy) {
        viewModelScope.launch {
            settingsRepository.setContentSyncStrategy(strategy)
        }
    }

    val contentSyncTargetLists: StateFlow<Set<String>> = settingsRepository.contentSyncTargetLists.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptySet()
    )

    val defaultListType: StateFlow<DefaultListType> = settingsRepository.defaultListType.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DefaultListType.ALL_BOOKMARKS
    )

    val defaultListId: StateFlow<String?> = settingsRepository.defaultListId.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    fun setDefaultListType(type: DefaultListType) {
        viewModelScope.launch {
            settingsRepository.setDefaultListType(type)
        }
    }

    fun setDefaultListId(id: String?) {
        viewModelScope.launch {
            settingsRepository.setDefaultListId(id)
        }
    }

    val contentSyncConfig: StateFlow<com.karakept.app.data.model.ListSyncConfig> = settingsRepository.contentSyncConfig.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = com.karakept.app.data.model.ListSyncConfig(emptySet(), emptySet())
    )

    // Expose lists from repository
    val availableLists: StateFlow<List<KarakeepList>> = listRepository.lists


    fun fetchAvailableLists() {
        viewModelScope.launch {
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
        viewModelScope.launch {
            settingsRepository.toggleContentSyncTargetList(listId)
        }
    }

    val showDateInList: StateFlow<Boolean> = settingsRepository.showDateInList.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = true
    )

    val dateDisplayMode: StateFlow<DateDisplayMode> = settingsRepository.dateDisplayMode.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DateDisplayMode.ELAPSED
    )

    fun setShowDateInList(show: Boolean) {
        viewModelScope.launch {
            settingsRepository.setShowDateInList(show)
        }
    }

    fun setDateDisplayMode(mode: DateDisplayMode) {
        viewModelScope.launch {
            settingsRepository.setDateDisplayMode(mode)
        }
    }
}
