package com.karakept.app.data.repository

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.karakept.app.data.model.AccentColor
import com.karakept.app.data.model.AutoExportInterval
import com.karakept.app.data.model.BackupSettings
import com.karakept.app.data.model.BookmarkLayout
import com.karakept.app.data.model.CheckboxState
import com.karakept.app.data.model.CustomSwipeActionConfig
import com.karakept.app.data.model.DateDisplayMode
import com.karakept.app.data.model.DefaultListType
import com.karakept.app.data.model.LayoutType
import com.karakept.app.data.model.LinkOpenMode
import com.karakept.app.data.model.ListSettings
import com.karakept.app.data.model.ListSyncConfig
import com.karakept.app.data.model.PageTurnDirection
import com.karakept.app.data.model.PageTurnKeyBindings
import com.karakept.app.data.model.ReaderFontFamily
import com.karakept.app.data.model.ReaderTypography
import com.karakept.app.data.model.RowActionMode
import com.karakept.app.data.model.SwipeAction
import com.karakept.app.data.model.SyncStrategy
import com.karakept.app.data.model.ThemeMode
import com.karakept.app.data.model.ViewerMode
import com.karakept.app.utils.BackupCrypto
import kotlinx.coroutines.flow.first
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

/** All set* mutation methods, update helpers, backup API, and layout CRUD for SettingsRepository. */

internal suspend fun SettingsRepository.updateThemeSettings(transform: StoredThemeSettings.() -> StoredThemeSettings) {
    dataStore.edit { prefs ->
        prefs[THEME_SETTINGS_KEY] = settingsJson.encodeToString(prefs.readThemeSettings().transform())
    }
}

internal suspend fun SettingsRepository.updateDisplaySettings(transform: StoredDisplaySettings.() -> StoredDisplaySettings) {
    dataStore.edit { prefs ->
        prefs[DISPLAY_SETTINGS_KEY] = settingsJson.encodeToString(prefs.readDisplaySettings().transform())
    }
}

internal suspend fun SettingsRepository.updateReaderSettings(transform: StoredReaderSettings.() -> StoredReaderSettings) {
    dataStore.edit { prefs ->
        prefs[READER_SETTINGS_KEY] = settingsJson.encodeToString(prefs.readReaderSettings().transform())
    }
}

internal suspend fun SettingsRepository.updateSwipeSettings(transform: StoredSwipeSettings.() -> StoredSwipeSettings) {
    dataStore.edit { prefs ->
        prefs[SWIPE_SETTINGS_KEY] = settingsJson.encodeToString(prefs.readSwipeSettings().transform())
    }
}

internal suspend fun SettingsRepository.updateSyncSettings(transform: StoredSyncSettings.() -> StoredSyncSettings) {
    dataStore.edit { prefs ->
        prefs[SYNC_SETTINGS_KEY] = settingsJson.encodeToString(prefs.readSyncSettings().transform())
    }
}

internal suspend fun SettingsRepository.updateAppSettings(transform: StoredAppSettings.() -> StoredAppSettings) {
    dataStore.edit { prefs ->
        prefs[APP_SETTINGS_KEY] = settingsJson.encodeToString(prefs.readAppSettings().transform())
    }
}

internal suspend fun SettingsRepository.updateEinkSettings(transform: StoredEinkSettings.() -> StoredEinkSettings) {
    dataStore.edit { prefs ->
        prefs[EINK_SETTINGS_KEY] = settingsJson.encodeToString(prefs.readEinkSettings().transform())
    }
}

/**
 * Returns a one-shot snapshot of all backed-up settings as a flat [BackupSettings].
 * Reads all category blobs plus the per-list-settings key in a single DataStore snapshot.
 * Used by [com.karakept.app.data.repository.BackupRepository].
 */
suspend fun SettingsRepository.currentSettings(): BackupSettings {
    val prefs = dataStore.data.first()
    val theme = prefs.readThemeSettings()
    val display = prefs.readDisplaySettings()
    val reader = prefs.readReaderSettings()
    val swipe = prefs.readSwipeSettings()
    val sync = prefs.readSyncSettings()
    val app = prefs.readAppSettings()
    val eink = prefs.readEinkSettings()
    val listSettingsMap: Map<String, ListSettings> = runCatching {
        settingsJson.decodeFromString<Map<String, ListSettings>>(prefs[PER_LIST_SETTINGS_KEY] ?: "{}")
    }.getOrDefault(emptyMap())
    return BackupSettings(
        themeMode = theme.themeMode,
        accentColor = theme.accentColor,
        layoutType = display.layoutType,
        hideArticleThumbnails = display.hideArticleThumbnails,
        showReadingTimeBadge = display.showReadingTimeBadge,
        showTags = display.showTags,
        dimReadBookmarks = display.dimReadBookmarks,
        showScrollCursor = display.showScrollCursor,
        viewerMode = reader.viewerMode,
        htmlTextColor = reader.htmlTextColor,
        htmlBackgroundColor = reader.htmlBackgroundColor,
        htmlFontSize = reader.htmlFontSize,
        htmlFontFamily = reader.htmlFontFamily,
        readingSpeedWpm = reader.readingSpeedWpm,
        trackReadingProgress = reader.trackReadingProgress,
        resetProgressOnMarkUnread = reader.resetProgressOnMarkUnread,
        linkOpenMode = reader.linkOpenMode,
        preferFullPageHtml = reader.preferFullPageHtml,
        readerLineHeightScale = reader.readerLineHeightScale,
        readerHorizontalMarginDp = reader.readerHorizontalMarginDp,
        readerMaxWidthDp = reader.readerMaxWidthDp,
        showReaderHeroImage = reader.showReaderHeroImage,
        einkModeEnabled = eink.einkModeEnabled,
        einkDisableAnimations = eink.disableAnimations,
        einkHighContrast = eink.highContrast,
        einkInstantPageScroll = eink.instantPageScroll,
        pageTurnKeysEnabled = eink.hardwareKeysEnabled,
        pageTurnUseVolumeKeys = eink.useVolumeKeys,
        pageTurnInvertVolumeKeys = eink.invertVolumeKeys,
        pageTurnPreviousKeyCode = eink.previousPageKeyCode,
        pageTurnNextKeyCode = eink.nextPageKeyCode,
        pageTurnOverlapPercent = eink.pageTurnOverlapPercent,
        swipeLeftAction = swipe.swipeLeftAction,
        swipeRightAction = swipe.swipeRightAction,
        customSwipeConfigsJson = swipe.customSwipeConfigsJson,
        swipeLeftConfigId = swipe.swipeLeftConfigId,
        swipeRightConfigId = swipe.swipeRightConfigId,
        rowActionMode = swipe.rowActionMode,
        contentSyncStrategy = sync.contentSyncStrategy,
        contentSyncTargetLists = sync.contentSyncTargetLists,
        contentSyncWithChildren = sync.contentSyncWithChildren,
        notificationsEnabled = app.notificationsEnabled,
        offlineMode = app.offlineMode,
        onboardingCompleted = app.onboardingCompleted,
        autoExportInterval = app.autoExportInterval,
        backupExportDirectory = app.backupExportDirectory,
        perListSettings = listSettingsMap,
        defaultListType = prefs[DEFAULT_LIST_TYPE_KEY] ?: DefaultListType.ALL_BOOKMARKS.name,
        defaultListId = prefs[DEFAULT_LIST_ID_KEY],
        customLayoutsJson = prefs[LAYOUTS_KEY] ?: "[]",
        defaultLayoutId = prefs[DEFAULT_LAYOUT_ID_KEY],
        backupPinHash = app.backupPinHash
    )
}

/**
 * Atomically replaces all backed-up settings with [s].
 * All six category blobs are written in a single DataStore transaction.
 * Used by [BackupRepository].
 */
suspend fun SettingsRepository.restoreSettings(s: BackupSettings) {
    dataStore.edit { prefs ->
        prefs[THEME_SETTINGS_KEY] = settingsJson.encodeToString(
            StoredThemeSettings(themeMode = s.themeMode, accentColor = s.accentColor)
        )
        prefs[DISPLAY_SETTINGS_KEY] = settingsJson.encodeToString(
            StoredDisplaySettings(
                layoutType = s.layoutType,
                hideArticleThumbnails = s.hideArticleThumbnails,
                showReadingTimeBadge = s.showReadingTimeBadge,
                showTags = s.showTags,
                dimReadBookmarks = s.dimReadBookmarks,
                showScrollCursor = s.showScrollCursor
            )
        )
        prefs[READER_SETTINGS_KEY] = settingsJson.encodeToString(
            StoredReaderSettings(
                viewerMode = s.viewerMode,
                htmlTextColor = s.htmlTextColor,
                htmlBackgroundColor = s.htmlBackgroundColor,
                htmlFontSize = s.htmlFontSize,
                htmlFontFamily = s.htmlFontFamily,
                readingSpeedWpm = s.readingSpeedWpm,
                trackReadingProgress = s.trackReadingProgress,
                resetProgressOnMarkUnread = s.resetProgressOnMarkUnread,
                linkOpenMode = s.linkOpenMode,
                preferFullPageHtml = s.preferFullPageHtml,
                readerLineHeightScale = s.readerLineHeightScale,
                readerHorizontalMarginDp = s.readerHorizontalMarginDp,
                readerMaxWidthDp = s.readerMaxWidthDp,
                showReaderHeroImage = s.showReaderHeroImage
            )
        )
        prefs[EINK_SETTINGS_KEY] = settingsJson.encodeToString(
            StoredEinkSettings(
                einkModeEnabled = s.einkModeEnabled,
                disableAnimations = s.einkDisableAnimations,
                highContrast = s.einkHighContrast,
                instantPageScroll = s.einkInstantPageScroll,
                hardwareKeysEnabled = s.pageTurnKeysEnabled,
                useVolumeKeys = s.pageTurnUseVolumeKeys,
                invertVolumeKeys = s.pageTurnInvertVolumeKeys,
                previousPageKeyCode = s.pageTurnPreviousKeyCode,
                nextPageKeyCode = s.pageTurnNextKeyCode,
                pageTurnOverlapPercent = s.pageTurnOverlapPercent.coerceIn(
                    PageTurnKeyBindings.MIN_OVERLAP_PERCENT,
                    PageTurnKeyBindings.MAX_OVERLAP_PERCENT
                )
            )
        )
        prefs[SWIPE_SETTINGS_KEY] = settingsJson.encodeToString(
            StoredSwipeSettings(
                swipeLeftAction = s.swipeLeftAction,
                swipeRightAction = s.swipeRightAction,
                customSwipeConfigsJson = s.customSwipeConfigsJson,
                swipeLeftConfigId = s.swipeLeftConfigId,
                swipeRightConfigId = s.swipeRightConfigId,
                rowActionMode = s.rowActionMode
            )
        )
        prefs[SYNC_SETTINGS_KEY] = settingsJson.encodeToString(
            StoredSyncSettings(
                contentSyncStrategy = s.contentSyncStrategy,
                contentSyncTargetLists = s.contentSyncTargetLists,
                contentSyncWithChildren = s.contentSyncWithChildren
            )
        )
        prefs[APP_SETTINGS_KEY] = settingsJson.encodeToString(
            StoredAppSettings(
                notificationsEnabled = s.notificationsEnabled,
                offlineMode = s.offlineMode,
                onboardingCompleted = s.onboardingCompleted,
                autoExportInterval = s.autoExportInterval,
                backupExportDirectory = s.backupExportDirectory,
                backupPinHash = s.backupPinHash
            )
        )
        // Restore per-list settings
        prefs[PER_LIST_SETTINGS_KEY] = settingsJson.encodeToString<Map<String, ListSettings>>(s.perListSettings)
        // Restore default list
        prefs[DEFAULT_LIST_TYPE_KEY] = s.defaultListType
        if (s.defaultListId != null) {
            prefs[DEFAULT_LIST_ID_KEY] = s.defaultListId
        } else {
            prefs.remove(DEFAULT_LIST_ID_KEY)
        }
        // Restore custom layouts
        prefs[LAYOUTS_KEY] = s.customLayoutsJson
        if (s.defaultLayoutId != null) {
            prefs[DEFAULT_LAYOUT_ID_KEY] = s.defaultLayoutId
        } else {
            prefs.remove(DEFAULT_LAYOUT_ID_KEY)
        }
    }
}

suspend fun SettingsRepository.setThemeMode(mode: ThemeMode) =
    updateThemeSettings { copy(themeMode = mode.name) }

suspend fun SettingsRepository.setAccentColor(color: AccentColor) =
    updateThemeSettings { copy(accentColor = color.name) }

suspend fun SettingsRepository.setLayoutType(layoutType: LayoutType) =
    updateDisplaySettings { copy(layoutType = layoutType.name) }

suspend fun SettingsRepository.setHideArticleThumbnails(hide: Boolean) =
    updateDisplaySettings { copy(hideArticleThumbnails = hide) }

suspend fun SettingsRepository.setShowReadingTimeBadge(show: Boolean) =
    updateDisplaySettings { copy(showReadingTimeBadge = show) }

suspend fun SettingsRepository.setShowTags(show: Boolean) =
    updateDisplaySettings { copy(showTags = show) }

suspend fun SettingsRepository.setDimReadBookmarks(dim: Boolean) =
    updateDisplaySettings { copy(dimReadBookmarks = dim) }

suspend fun SettingsRepository.setShowScrollCursor(enabled: Boolean) =
    updateDisplaySettings { copy(showScrollCursor = enabled) }

suspend fun SettingsRepository.setViewerMode(mode: ViewerMode) =
    updateReaderSettings { copy(viewerMode = mode.name) }

suspend fun SettingsRepository.setHtmlTextColor(color: Color?) =
    updateReaderSettings { copy(htmlTextColor = color?.toArgb()) }

suspend fun SettingsRepository.setHtmlBackgroundColor(color: Color?) =
    updateReaderSettings { copy(htmlBackgroundColor = color?.toArgb()) }

suspend fun SettingsRepository.setHtmlFontSize(size: Int) =
    updateReaderSettings { copy(htmlFontSize = size) }

suspend fun SettingsRepository.setHtmlFontFamily(family: ReaderFontFamily) =
    updateReaderSettings { copy(htmlFontFamily = family.name) }

suspend fun SettingsRepository.setReadingSpeedWpm(wpm: Int) =
    updateReaderSettings { copy(readingSpeedWpm = wpm.coerceIn(100, 500)) }

suspend fun SettingsRepository.setTrackReadingProgress(enabled: Boolean) =
    updateReaderSettings { copy(trackReadingProgress = enabled) }

suspend fun SettingsRepository.setResetProgressOnMarkUnread(enabled: Boolean) =
    updateReaderSettings { copy(resetProgressOnMarkUnread = enabled) }

suspend fun SettingsRepository.setLinkOpenMode(mode: LinkOpenMode) =
    updateReaderSettings { copy(linkOpenMode = mode.name) }

suspend fun SettingsRepository.setShowTagsInViewer(show: Boolean) =
    updateReaderSettings { copy(showTagsInViewer = show) }

suspend fun SettingsRepository.setScrollToTopEnabled(enabled: Boolean) =
    updateReaderSettings { copy(scrollToTopEnabled = enabled) }

suspend fun SettingsRepository.setPreferFullPageHtml(enabled: Boolean) =
    updateReaderSettings { copy(preferFullPageHtml = enabled) }

suspend fun SettingsRepository.setReaderLineHeightScale(scale: Float) = updateReaderSettings {
    copy(
        readerLineHeightScale = scale.coerceIn(
            ReaderTypography.MIN_LINE_HEIGHT_SCALE,
            ReaderTypography.MAX_LINE_HEIGHT_SCALE
        )
    )
}

suspend fun SettingsRepository.setReaderHorizontalMarginDp(margin: Int) = updateReaderSettings {
    copy(
        readerHorizontalMarginDp = margin.coerceIn(
            ReaderTypography.MIN_HORIZONTAL_MARGIN_DP,
            ReaderTypography.MAX_HORIZONTAL_MARGIN_DP
        )
    )
}

suspend fun SettingsRepository.setReaderMaxWidthDp(width: Int) = updateReaderSettings {
    copy(
        readerMaxWidthDp = width.coerceIn(
            ReaderTypography.MIN_MAX_WIDTH_DP,
            ReaderTypography.MAX_MAX_WIDTH_DP
        )
    )
}

suspend fun SettingsRepository.setShowReaderHeroImage(show: Boolean) =
    updateReaderSettings { copy(showReaderHeroImage = show) }

suspend fun SettingsRepository.resetReaderAppearance() = updateReaderSettings {
    copy(
        htmlTextColor = null,
        htmlBackgroundColor = null,
        htmlFontSize = 16,
        htmlFontFamily = ReaderFontFamily.SYSTEM.name,
        readerLineHeightScale = ReaderTypography.DEFAULT_LINE_HEIGHT_SCALE,
        readerHorizontalMarginDp = ReaderTypography.DEFAULT_HORIZONTAL_MARGIN_DP,
        readerMaxWidthDp = ReaderTypography.DEFAULT_MAX_WIDTH_DP
    )
}

suspend fun SettingsRepository.setEinkModeEnabled(enabled: Boolean) =
    updateEinkSettings { copy(einkModeEnabled = enabled) }

suspend fun SettingsRepository.setEinkDisableAnimations(disable: Boolean) =
    updateEinkSettings { copy(disableAnimations = disable) }

suspend fun SettingsRepository.setEinkHighContrast(enabled: Boolean) =
    updateEinkSettings { copy(highContrast = enabled) }

suspend fun SettingsRepository.setEinkInstantPageScroll(enabled: Boolean) =
    updateEinkSettings { copy(instantPageScroll = enabled) }

suspend fun SettingsRepository.setPageTurnKeysEnabled(enabled: Boolean) =
    updateEinkSettings { copy(hardwareKeysEnabled = enabled) }

suspend fun SettingsRepository.setPageTurnUseVolumeKeys(enabled: Boolean) =
    updateEinkSettings { copy(useVolumeKeys = enabled) }

suspend fun SettingsRepository.setPageTurnInvertVolumeKeys(inverted: Boolean) =
    updateEinkSettings { copy(invertVolumeKeys = inverted) }

/**
 * Binds a hardware key to a page-turn direction. Passing null clears the binding. A key code
 * already bound to the other direction is released first, so the two can never collide.
 */
suspend fun SettingsRepository.setPageTurnKeyCode(direction: PageTurnDirection, keyCode: Int?) =
    updateEinkSettings {
        when (direction) {
            PageTurnDirection.PREVIOUS -> copy(
                previousPageKeyCode = keyCode,
                nextPageKeyCode = if (keyCode != null && nextPageKeyCode == keyCode) null else nextPageKeyCode
            )
            PageTurnDirection.NEXT -> copy(
                nextPageKeyCode = keyCode,
                previousPageKeyCode = if (keyCode != null && previousPageKeyCode == keyCode) null else previousPageKeyCode
            )
        }
    }

suspend fun SettingsRepository.setPageTurnOverlapPercent(percent: Int) = updateEinkSettings {
    copy(
        pageTurnOverlapPercent = percent.coerceIn(
            PageTurnKeyBindings.MIN_OVERLAP_PERCENT,
            PageTurnKeyBindings.MAX_OVERLAP_PERCENT
        )
    )
}

suspend fun SettingsRepository.setSwipeLeftAction(action: SwipeAction) =
    updateSwipeSettings { copy(swipeLeftAction = action.name) }

suspend fun SettingsRepository.setSwipeRightAction(action: SwipeAction) =
    updateSwipeSettings { copy(swipeRightAction = action.name) }

suspend fun SettingsRepository.setCustomSwipeActionConfigs(configs: List<CustomSwipeActionConfig>) =
    updateSwipeSettings { copy(customSwipeConfigsJson = settingsJson.encodeToString(configs)) }

suspend fun SettingsRepository.setSwipeLeftConfigId(id: String?) =
    updateSwipeSettings { copy(swipeLeftConfigId = id) }

suspend fun SettingsRepository.setSwipeRightConfigId(id: String?) =
    updateSwipeSettings { copy(swipeRightConfigId = id) }

suspend fun SettingsRepository.setRowActionMode(mode: RowActionMode) =
    updateSwipeSettings { copy(rowActionMode = mode.name) }

suspend fun SettingsRepository.setContentSyncStrategy(strategy: SyncStrategy) =
    updateSyncSettings { copy(contentSyncStrategy = strategy.name) }

suspend fun SettingsRepository.setContentSyncTargetLists(listIds: Set<String>) =
    updateSyncSettings { copy(contentSyncTargetLists = listIds) }

suspend fun SettingsRepository.toggleContentSyncTargetList(listId: String) = updateSyncSettings {
    val current = contentSyncTargetLists.toMutableSet()
    if (current.contains(listId)) current.remove(listId) else current.add(listId)
    copy(contentSyncTargetLists = current)
}

suspend fun SettingsRepository.updateListSyncState(listId: String, newState: CheckboxState) = updateSyncSettings {
    val currentSelected = contentSyncTargetLists.toMutableSet()
    val currentWithChildren = contentSyncWithChildren.toMutableSet()
    when (newState) {
        CheckboxState.UNCHECKED -> {
            currentSelected.remove(listId)
            currentWithChildren.remove(listId)
        }
        CheckboxState.CHECKED_PARENT_ONLY -> {
            currentSelected.add(listId)
            currentWithChildren.remove(listId)
        }
        CheckboxState.CHECKED_WITH_CHILDREN -> {
            currentSelected.add(listId)
            currentWithChildren.add(listId)
        }
    }
    copy(contentSyncTargetLists = currentSelected, contentSyncWithChildren = currentWithChildren)
}

suspend fun SettingsRepository.setContentSyncConfig(config: ListSyncConfig) = updateSyncSettings {
    copy(
        contentSyncTargetLists = config.selectedLists,
        contentSyncWithChildren = config.withChildrenMode
    )
}

suspend fun SettingsRepository.setNotificationsEnabled(enabled: Boolean) =
    updateAppSettings { copy(notificationsEnabled = enabled) }

suspend fun SettingsRepository.setOfflineMode(enabled: Boolean) =
    updateAppSettings { copy(offlineMode = enabled) }

suspend fun SettingsRepository.setOnboardingCompleted(completed: Boolean) =
    updateAppSettings { copy(onboardingCompleted = completed) }

suspend fun SettingsRepository.setAutoExportInterval(interval: AutoExportInterval) =
    updateAppSettings { copy(autoExportInterval = interval.name) }

suspend fun SettingsRepository.setBackupExportDirectory(path: String?) =
    updateAppSettings { copy(backupExportDirectory = path) }

/**
 * Sets (or clears) the backup PIN.
 * Writes the actual PIN to the backup PIN key (for scheduled exports) and
 * stores its PBKDF2 hash in [StoredAppSettings.backupPinHash] (for verification and backup).
 */
suspend fun SettingsRepository.setBackupPin(pin: String?) {
    dataStore.edit { prefs ->
        if (pin != null) {
            prefs[BACKUP_PIN_KEY] = pin
        } else {
            prefs.remove(BACKUP_PIN_KEY)
        }
    }
    updateAppSettings {
        copy(backupPinHash = if (pin != null) BackupCrypto.hashPin(pin) else null)
    }
}

suspend fun SettingsRepository.setActiveServerId(id: String) {
    dataStore.edit { it[ACTIVE_SERVER_ID_KEY] = id }
}

suspend fun SettingsRepository.setDrawerWidthDp(width: Float) {
    dataStore.edit { it[DRAWER_WIDTH_DP_KEY] = width }
}

suspend fun SettingsRepository.setListColumnFraction(fraction: Float) {
    dataStore.edit { it[LIST_COLUMN_FRACTION_KEY] = fraction }
}

suspend fun SettingsRepository.setWindowState(width: Float, height: Float, x: Float, y: Float, maximized: Boolean) {
    dataStore.edit { prefs ->
        prefs[WINDOW_WIDTH_KEY] = width
        prefs[WINDOW_HEIGHT_KEY] = height
        prefs[WINDOW_X_KEY] = x
        prefs[WINDOW_Y_KEY] = y
        prefs[WINDOW_MAXIMIZED_KEY] = maximized
    }
}

suspend fun SettingsRepository.setDefaultListType(type: DefaultListType) {
    dataStore.edit { it[DEFAULT_LIST_TYPE_KEY] = type.name }
}

suspend fun SettingsRepository.setDefaultListId(id: String?) {
    dataStore.edit { prefs ->
        if (id != null) {
            prefs[DEFAULT_LIST_ID_KEY] = id
        } else {
            prefs.remove(DEFAULT_LIST_ID_KEY)
        }
    }
}

suspend fun SettingsRepository.setLastAutoExportTime(timestamp: Long) {
    dataStore.edit { it[LAST_AUTO_EXPORT_TIME_KEY] = timestamp }
}

suspend fun SettingsRepository.setShowDateInList(show: Boolean) {
    dataStore.edit { it[SHOW_DATE_IN_LIST_KEY] = show }
}

suspend fun SettingsRepository.setDateDisplayMode(mode: DateDisplayMode) {
    dataStore.edit { it[DATE_DISPLAY_MODE_KEY] = mode.name }
}

suspend fun SettingsRepository.setListSettings(listId: String, settings: ListSettings) {
    dataStore.edit { prefs ->
        val current: MutableMap<String, ListSettings> = runCatching {
            settingsJson.decodeFromString<Map<String, ListSettings>>(
                prefs[PER_LIST_SETTINGS_KEY] ?: "{}"
            ).toMutableMap()
        }.getOrDefault(mutableMapOf())
        current[listId] = settings
        prefs[PER_LIST_SETTINGS_KEY] =
            settingsJson.encodeToString<Map<String, ListSettings>>(current)
    }
}

suspend fun SettingsRepository.saveLayout(layout: BookmarkLayout) {
    dataStore.edit { prefs ->
        val current: MutableList<BookmarkLayout> = runCatching {
            settingsJson.decodeFromString<List<BookmarkLayout>>(
                prefs[LAYOUTS_KEY] ?: "[]"
            ).toMutableList()
        }.getOrDefault(mutableListOf())
        val index = current.indexOfFirst { it.id == layout.id }
        if (index >= 0) {
            current[index] = layout
        } else {
            current.add(layout)
        }
        prefs[LAYOUTS_KEY] = settingsJson.encodeToString<List<BookmarkLayout>>(current)
    }
}

suspend fun SettingsRepository.deleteLayout(id: String) {
    dataStore.edit { prefs ->
        val current: MutableList<BookmarkLayout> = runCatching {
            settingsJson.decodeFromString<List<BookmarkLayout>>(
                prefs[LAYOUTS_KEY] ?: "[]"
            ).toMutableList()
        }.getOrDefault(mutableListOf())
        current.removeAll { it.id == id }
        prefs[LAYOUTS_KEY] = settingsJson.encodeToString<List<BookmarkLayout>>(current)
        // If deleted layout was the default, clear default
        if (prefs[DEFAULT_LAYOUT_ID_KEY] == id) {
            prefs.remove(DEFAULT_LAYOUT_ID_KEY)
        }
    }
}

suspend fun SettingsRepository.setDefaultLayoutId(id: String?) {
    dataStore.edit { prefs ->
        if (id != null) {
            prefs[DEFAULT_LAYOUT_ID_KEY] = id
        } else {
            prefs.remove(DEFAULT_LAYOUT_ID_KEY)
        }
    }
}

suspend fun SettingsRepository.setListLayoutId(listId: String, layoutId: String?) {
    dataStore.edit { prefs ->
        val current: MutableMap<String, ListSettings> = runCatching {
            settingsJson.decodeFromString<Map<String, ListSettings>>(
                prefs[PER_LIST_SETTINGS_KEY] ?: "{}"
            ).toMutableMap()
        }.getOrDefault(mutableMapOf())
        val existing = current[listId] ?: ListSettings()
        current[listId] = existing.copy(layoutId = layoutId)
        prefs[PER_LIST_SETTINGS_KEY] =
            settingsJson.encodeToString<Map<String, ListSettings>>(current)
    }
}
