package com.karakept.app.data.repository

import androidx.compose.ui.graphics.Color
import androidx.datastore.preferences.core.mutablePreferencesOf
import com.karakept.app.data.model.AccentColor
import com.karakept.app.data.model.AutoExportInterval
import com.karakept.app.data.model.BackupSettings
import com.karakept.app.data.model.CustomSwipeActionConfig
import com.karakept.app.data.model.CustomSwipeActionType
import com.karakept.app.data.model.LayoutType
import com.karakept.app.data.model.ReaderFontFamily
import com.karakept.app.data.model.SwipeAction
import com.karakept.app.data.model.SyncStrategy
import com.karakept.app.data.model.ThemeMode
import com.karakept.app.data.model.ViewerMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Flow-level tests for [SettingsRepository] using [FakeDataStore].
 *
 * Covers default values (FLOW-01), write-read roundtrips (FLOW-02),
 * deduplication (FLOW-03), atomic reset (FLOW-04), complex types (FLOW-05),
 * corrupt data fallback (FLOW-06), and backup paths (FLOW-07/08).
 */
class SettingsRepositoryFlowTest {

    private lateinit var fakeDataStore: FakeDataStore
    private lateinit var repo: SettingsRepository

    @BeforeTest
    fun setup() {
        fakeDataStore = FakeDataStore()
        repo = SettingsRepository(fakeDataStore)
    }

    // ── FLOW-01: Default values from empty DataStore ──────────────────────────

    @Test
    fun themeMode_defaultsToSystem() = runTest {
        assertEquals(ThemeMode.SYSTEM, repo.themeMode.first())
    }

    @Test
    fun accentColor_defaultsToPurple() = runTest {
        assertEquals(AccentColor.PURPLE, repo.accentColor.first())
    }

    @Test
    fun layoutType_defaultsToList() = runTest {
        assertEquals(LayoutType.LIST, repo.layoutType.first())
    }

    @Test
    fun hideArticleThumbnails_defaultsToTrue() = runTest {
        assertEquals(true, repo.hideArticleThumbnails.first())
    }

    @Test
    fun showReadingTimeBadge_defaultsToTrue() = runTest {
        assertEquals(true, repo.showReadingTimeBadge.first())
    }

    @Test
    fun showTags_defaultsToTrue() = runTest {
        assertEquals(true, repo.showTags.first())
    }

    @Test
    fun dimReadBookmarks_defaultsToTrue() = runTest {
        assertEquals(true, repo.dimReadBookmarks.first())
    }

    @Test
    fun viewerMode_defaultsToReader() = runTest {
        assertEquals(ViewerMode.READER, repo.viewerMode.first())
    }

    @Test
    fun htmlTextColor_defaultsToNull() = runTest {
        assertNull(repo.htmlTextColor.first())
    }

    @Test
    fun htmlFontSize_defaultsTo16() = runTest {
        assertEquals(16, repo.htmlFontSize.first())
    }

    @Test
    fun htmlFontFamily_defaultsToSystem() = runTest {
        assertEquals(ReaderFontFamily.SYSTEM, repo.htmlFontFamily.first())
    }

    @Test
    fun readingSpeedWpm_defaultsTo238() = runTest {
        assertEquals(238, repo.readingSpeedWpm.first())
    }

    @Test
    fun swipeLeftAction_defaultsToMarkRead() = runTest {
        assertEquals(SwipeAction.MARK_READ, repo.swipeLeftAction.first())
    }

    @Test
    fun swipeRightAction_defaultsToArchive() = runTest {
        assertEquals(SwipeAction.ARCHIVE, repo.swipeRightAction.first())
    }

    @Test
    fun contentSyncStrategy_defaultsToPerBookmark() = runTest {
        assertEquals(SyncStrategy.PER_BOOKMARK, repo.contentSyncStrategy.first())
    }

    @Test
    fun notificationsEnabled_defaultsToTrue() = runTest {
        assertEquals(true, repo.notificationsEnabled.first())
    }

    @Test
    fun offlineMode_defaultsToFalse() = runTest {
        assertEquals(false, repo.offlineMode.first())
    }

    @Test
    fun onboardingCompleted_defaultsToFalse() = runTest {
        assertEquals(false, repo.onboardingCompleted.first())
    }

    @Test
    fun autoExportInterval_defaultsToNever() = runTest {
        assertEquals(AutoExportInterval.NEVER, repo.autoExportInterval.first())
    }

    // ── FLOW-02: Write-read roundtrip tests ───────────────────────────────────

    // Theme category
    @Test
    fun setThemeMode_dark_roundTrips() = runTest {
        repo.setThemeMode(ThemeMode.DARK)
        assertEquals(ThemeMode.DARK, repo.themeMode.first())
    }

    @Test
    fun setAccentColor_blue_roundTrips() = runTest {
        repo.setAccentColor(AccentColor.BLUE)
        assertEquals(AccentColor.BLUE, repo.accentColor.first())
    }

    // Display category
    @Test
    fun setLayoutType_card_roundTrips() = runTest {
        repo.setLayoutType(LayoutType.CARD)
        assertEquals(LayoutType.CARD, repo.layoutType.first())
    }

    @Test
    fun setHideArticleThumbnails_false_roundTrips() = runTest {
        repo.setHideArticleThumbnails(false)
        assertEquals(false, repo.hideArticleThumbnails.first())
    }

    @Test
    fun setShowTags_false_roundTrips() = runTest {
        repo.setShowTags(false)
        assertEquals(false, repo.showTags.first())
    }

    @Test
    fun setDimReadBookmarks_false_roundTrips() = runTest {
        repo.setDimReadBookmarks(false)
        assertEquals(false, repo.dimReadBookmarks.first())
    }

    // Reader category
    @Test
    fun setViewerMode_web_roundTrips() = runTest {
        repo.setViewerMode(ViewerMode.WEB)
        assertEquals(ViewerMode.WEB, repo.viewerMode.first())
    }

    @Test
    fun setHtmlFontSize_24_roundTrips() = runTest {
        repo.setHtmlFontSize(24)
        assertEquals(24, repo.htmlFontSize.first())
    }

    @Test
    fun setHtmlFontFamily_notoSans_roundTrips() = runTest {
        repo.setHtmlFontFamily(ReaderFontFamily.NOTO_SANS)
        assertEquals(ReaderFontFamily.NOTO_SANS, repo.htmlFontFamily.first())
    }

    @Test
    fun setReadingSpeedWpm_300_roundTrips() = runTest {
        repo.setReadingSpeedWpm(300)
        assertEquals(300, repo.readingSpeedWpm.first())
    }

    @Test
    fun setReadingSpeedWpm_belowMinimum_coercesTo100() = runTest {
        repo.setReadingSpeedWpm(50)
        assertEquals(100, repo.readingSpeedWpm.first())
    }

    @Test
    fun setReadingSpeedWpm_aboveMaximum_coercesTo500() = runTest {
        repo.setReadingSpeedWpm(999)
        assertEquals(500, repo.readingSpeedWpm.first())
    }

    // Swipe category
    @Test
    fun setSwipeLeftAction_archive_roundTrips() = runTest {
        repo.setSwipeLeftAction(SwipeAction.ARCHIVE)
        assertEquals(SwipeAction.ARCHIVE, repo.swipeLeftAction.first())
    }

    @Test
    fun setSwipeRightAction_favourite_roundTrips() = runTest {
        repo.setSwipeRightAction(SwipeAction.FAVOURITE)
        assertEquals(SwipeAction.FAVOURITE, repo.swipeRightAction.first())
    }

    // Sync category
    @Test
    fun setContentSyncStrategy_all_roundTrips() = runTest {
        repo.setContentSyncStrategy(SyncStrategy.ALL)
        assertEquals(SyncStrategy.ALL, repo.contentSyncStrategy.first())
    }

    // App category
    @Test
    fun setOfflineMode_true_roundTrips() = runTest {
        repo.setOfflineMode(true)
        assertEquals(true, repo.offlineMode.first())
    }

    @Test
    fun setAutoExportInterval_weekly_roundTrips() = runTest {
        repo.setAutoExportInterval(AutoExportInterval.WEEKLY)
        assertEquals(AutoExportInterval.WEEKLY, repo.autoExportInterval.first())
    }

    @Test
    fun setNotificationsEnabled_false_roundTrips() = runTest {
        repo.setNotificationsEnabled(false)
        assertEquals(false, repo.notificationsEnabled.first())
    }

    // ── FLOW-03: distinctUntilChanged deduplication ───────────────────────────

    @Test
    fun themeMode_writeSameValueTwice_emitsOnce() = runTest {
        val emissions = mutableListOf<ThemeMode>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            repo.themeMode.toList(emissions)
        }
        runCurrent()
        repo.setThemeMode(ThemeMode.DARK)
        runCurrent()
        repo.setThemeMode(ThemeMode.DARK) // duplicate
        runCurrent()
        // Expect: SYSTEM (default) + DARK (one change) = 2 emissions, not 3
        assertEquals(2, emissions.size)
        assertEquals(ThemeMode.SYSTEM, emissions[0])
        assertEquals(ThemeMode.DARK, emissions[1])
        job.cancel()
    }

    @Test
    fun layoutType_writeSameValueTwice_emitsOnce() = runTest {
        val emissions = mutableListOf<LayoutType>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            repo.layoutType.toList(emissions)
        }
        runCurrent()
        repo.setLayoutType(LayoutType.CARD)
        runCurrent()
        repo.setLayoutType(LayoutType.CARD) // duplicate
        runCurrent()
        assertEquals(2, emissions.size)
        assertEquals(LayoutType.LIST, emissions[0])
        assertEquals(LayoutType.CARD, emissions[1])
        job.cancel()
    }

    @Test
    fun offlineMode_crossCategoryWrite_doesNotEmitThemeMode() = runTest {
        val emissions = mutableListOf<ThemeMode>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            repo.themeMode.toList(emissions)
        }
        runCurrent()
        // Write to a different category (app settings)
        repo.setOfflineMode(true)
        runCurrent()
        // themeMode should only have the initial default emission
        assertEquals(1, emissions.size)
        assertEquals(ThemeMode.SYSTEM, emissions[0])
        job.cancel()
    }

    // ── FLOW-04: resetReaderAppearance atomic reset ───────────────────────────

    @Test
    fun resetReaderAppearance_resetsMultipleFields() = runTest {
        // Set non-default values
        repo.setHtmlTextColor(Color(0xFFFF0000.toInt()))
        repo.setHtmlBackgroundColor(Color(0xFF00FF00.toInt()))
        repo.setHtmlFontSize(24)
        repo.setHtmlFontFamily(ReaderFontFamily.NOTO_SANS)

        // Reset
        repo.resetReaderAppearance()

        // Verify reset to defaults
        assertNull(repo.htmlTextColor.first())
        assertNull(repo.htmlBackgroundColor.first())
        assertEquals(16, repo.htmlFontSize.first())
        assertEquals(ReaderFontFamily.SYSTEM, repo.htmlFontFamily.first())
    }

    @Test
    fun resetReaderAppearance_preservesOtherReaderFields() = runTest {
        // Set values that should NOT be reset
        repo.setViewerMode(ViewerMode.WEB)
        repo.setReadingSpeedWpm(400)

        // Also set appearance values
        repo.setHtmlFontSize(24)

        // Reset appearance only
        repo.resetReaderAppearance()

        // These should be preserved
        assertEquals(ViewerMode.WEB, repo.viewerMode.first())
        assertEquals(400, repo.readingSpeedWpm.first())
    }

    // ── FLOW-05: Complex type round-trips ─────────────────────────────────────

    @Test
    fun setHtmlTextColor_nonNull_emitsCorrectColor() = runTest {
        val red = Color(0xFFFF0000.toInt())
        repo.setHtmlTextColor(red)
        assertEquals(red, repo.htmlTextColor.first())
    }

    @Test
    fun setHtmlTextColor_null_emitsNull() = runTest {
        // Set a color first
        repo.setHtmlTextColor(Color(0xFFFF0000.toInt()))
        assertEquals(Color(0xFFFF0000.toInt()), repo.htmlTextColor.first())

        // Then clear it
        repo.setHtmlTextColor(null)
        assertNull(repo.htmlTextColor.first())
    }

    @Test
    fun setCustomSwipeActionConfigs_roundTrips() = runTest {
        val configs = listOf(
            CustomSwipeActionConfig(
                id = "cfg-1",
                type = CustomSwipeActionType.ADD_TAG,
                tagName = "important"
            ),
            CustomSwipeActionConfig(
                id = "cfg-2",
                type = CustomSwipeActionType.ADD_TO_LIST,
                listId = "list-42",
                listName = "Read Later",
                customName = "Save for later"
            )
        )
        repo.setCustomSwipeActionConfigs(configs)
        assertEquals(configs, repo.customSwipeActionConfigs.first())
    }

    // ── FLOW-06: Corrupt data fallback ────────────────────────────────────────

    @Test
    fun themeMode_invalidEnumValue_fallsBackToDefault() = runTest {
        // Pre-seed with valid JSON but invalid enum value
        val seeded = FakeDataStore(
            mutablePreferencesOf(
                repo.THEME_SETTINGS_KEY to """{"themeMode":"INVALID_VALUE","accentColor":"PURPLE"}"""
            )
        )
        val seededRepo = SettingsRepository(seeded)
        assertEquals(ThemeMode.SYSTEM, seededRepo.themeMode.first())
    }

    @Test
    fun layoutType_invalidEnumValue_fallsBackToDefault() = runTest {
        val seeded = FakeDataStore(
            mutablePreferencesOf(
                repo.DISPLAY_SETTINGS_KEY to """{"layoutType":"NONEXISTENT","hideArticleThumbnails":true,"showReadingTimeBadge":true,"showTags":true,"dimReadBookmarks":true}"""
            )
        )
        val seededRepo = SettingsRepository(seeded)
        assertEquals(LayoutType.LIST, seededRepo.layoutType.first())
    }

    @Test
    fun viewerMode_invalidEnumValue_fallsBackToDefault() = runTest {
        val seeded = FakeDataStore(
            mutablePreferencesOf(
                repo.READER_SETTINGS_KEY to """{"viewerMode":"GARBAGE","htmlTextColor":null,"htmlBackgroundColor":null,"htmlFontSize":16,"htmlFontFamily":"SYSTEM","readingSpeedWpm":238,"trackReadingProgress":true,"resetProgressOnMarkUnread":true,"linkOpenMode":"CUSTOM_TAB","showTagsInViewer":true,"scrollToTopEnabled":true}"""
            )
        )
        val seededRepo = SettingsRepository(seeded)
        assertEquals(ViewerMode.READER, seededRepo.viewerMode.first())
    }

    @Test
    fun themeMode_completelyInvalidJson_fallsBackToDefault() = runTest {
        // Completely invalid JSON -- runCatching in readThemeSettings catches the exception
        val seeded = FakeDataStore(
            mutablePreferencesOf(
                repo.THEME_SETTINGS_KEY to "not json at all"
            )
        )
        val seededRepo = SettingsRepository(seeded)
        assertEquals(ThemeMode.SYSTEM, seededRepo.themeMode.first())
    }

    // ── FLOW-07: currentSettings returns all defaults ─────────────────────────

    @Test
    fun currentSettings_returnsAllDefaults_whenEmpty() = runTest {
        val settings = repo.currentSettings()
        assertEquals(ThemeMode.SYSTEM.name, settings.themeMode)
        assertEquals(AccentColor.PURPLE.name, settings.accentColor)
        assertEquals(LayoutType.LIST.name, settings.layoutType)
        assertEquals(ViewerMode.READER.name, settings.viewerMode)
        assertEquals(SwipeAction.MARK_READ.name, settings.swipeLeftAction)
        assertEquals(SwipeAction.ARCHIVE.name, settings.swipeRightAction)
        assertEquals(SyncStrategy.PER_BOOKMARK.name, settings.contentSyncStrategy)
        assertEquals(true, settings.notificationsEnabled)
        assertEquals(false, settings.offlineMode)
        assertEquals(false, settings.onboardingCompleted)
        assertEquals(AutoExportInterval.NEVER.name, settings.autoExportInterval)
    }

    // ── FLOW-08: restoreSettings / currentSettings roundtrip ──────────────────

    @Test
    fun restoreSettings_thenCurrentSettings_roundTrips() = runTest {
        val custom = BackupSettings(
            themeMode = ThemeMode.DARK.name,
            accentColor = AccentColor.BLUE.name,
            layoutType = LayoutType.CARD.name,
            offlineMode = true,
            htmlFontSize = 24,
            swipeLeftAction = SwipeAction.ARCHIVE.name,
            contentSyncStrategy = SyncStrategy.ALL.name,
            notificationsEnabled = false
        )
        repo.restoreSettings(custom)
        val restored = repo.currentSettings()

        assertEquals(ThemeMode.DARK.name, restored.themeMode)
        assertEquals(AccentColor.BLUE.name, restored.accentColor)
        assertEquals(LayoutType.CARD.name, restored.layoutType)
        assertEquals(true, restored.offlineMode)
        assertEquals(24, restored.htmlFontSize)
        assertEquals(SwipeAction.ARCHIVE.name, restored.swipeLeftAction)
        assertEquals(SyncStrategy.ALL.name, restored.contentSyncStrategy)
        assertEquals(false, restored.notificationsEnabled)
    }

    @Test
    fun restoreSettings_updatesFlows() = runTest {
        val custom = BackupSettings(
            themeMode = ThemeMode.DARK.name,
            layoutType = LayoutType.CARD.name,
            offlineMode = true
        )
        repo.restoreSettings(custom)

        assertEquals(ThemeMode.DARK, repo.themeMode.first())
        assertEquals(LayoutType.CARD, repo.layoutType.first())
        assertEquals(true, repo.offlineMode.first())
    }

    @Test
    fun restoreSettings_preservesNonBackupDefaults() = runTest {
        // Restore with explicit non-default values
        val custom = BackupSettings(themeMode = ThemeMode.DARK.name)
        repo.restoreSettings(custom)

        // Fields not explicitly set should still have their BackupSettings defaults
        assertEquals(LayoutType.LIST.name, repo.currentSettings().layoutType)
        assertTrue(repo.currentSettings().notificationsEnabled)
    }
}
