package com.karakept.app.ui.navigation

import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.savedstate.serialization.SavedStateConfiguration
import com.karakept.app.ui.screens.AboutScreen
import com.karakept.app.ui.screens.BookmarkViewerScreen
import com.karakept.app.ui.screens.HighlightsScreen
import com.karakept.app.ui.screens.LoginScreen
import com.karakept.app.ui.screens.MainScreen
import com.karakept.app.ui.screens.OnboardingScreen
import com.karakept.app.ui.screens.ReaderAppearanceScreen
import com.karakept.app.ui.screens.SaveErrorScreen
import com.karakept.app.ui.screens.SettingsScreen
import com.karakept.app.ui.screens.ShareBookmarkScreen
import com.karakept.app.ui.screens.ShareMultipleBookmarksScreen
import com.karakept.app.ui.screens.settings.AppearanceSettingsScreen
import com.karakept.app.ui.screens.settings.BackgroundSyncSettingsScreen
import com.karakept.app.ui.screens.settings.BackupRestoreScreen
import com.karakept.app.ui.screens.settings.BookmarkListSettingsScreen
import com.karakept.app.ui.screens.settings.BookmarkViewSettingsScreen
import com.karakept.app.ui.screens.settings.CustomSwipeActionsScreen
import com.karakept.app.ui.screens.settings.DefaultDisplaySettingsScreen
import com.karakept.app.ui.screens.settings.LayoutEditorScreen
import com.karakept.app.ui.screens.settings.LayoutsScreen
import com.karakept.app.ui.screens.settings.ListManagementScreen
import com.karakept.app.ui.screens.settings.NotificationSettingsScreen
import com.karakept.app.ui.screens.settings.PerListSettingsScreen
import com.karakept.app.ui.screens.settings.ServerSettingsScreen
import com.karakept.app.ui.screens.settings.SyncDataSettingsScreen
import com.karakept.app.ui.screens.settings.ThemeSettingsScreen
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

/**
 * Polymorphic registry of every [NavKey] in the app. Required by [navSavedStateConfiguration]
 * so `rememberNavBackStack` can serialize the back stack across config change / process death.
 * Add new screens here when introducing a new [NavKey].
 */
val navKeySerializersModule: SerializersModule = SerializersModule {
    polymorphic(NavKey::class) {
        subclass(MainScreen::class)
        subclass(LoginScreen::class)
        subclass(OnboardingScreen::class)
        subclass(BookmarkViewerScreen::class)
        subclass(ReaderAppearanceScreen::class)
        subclass(SaveErrorScreen::class)
        subclass(SettingsScreen::class)
        subclass(HighlightsScreen::class)
        subclass(AboutScreen::class)
        subclass(ShareBookmarkScreen::class)
        subclass(ShareMultipleBookmarksScreen::class)
        subclass(ServerSettingsScreen::class)
        subclass(ThemeSettingsScreen::class)
        subclass(AppearanceSettingsScreen::class)
        subclass(DefaultDisplaySettingsScreen::class)
        subclass(BookmarkViewSettingsScreen::class)
        subclass(BookmarkListSettingsScreen::class)
        subclass(NotificationSettingsScreen::class)
        subclass(BackgroundSyncSettingsScreen::class)
        subclass(SyncDataSettingsScreen::class)
        subclass(BackupRestoreScreen::class)
        subclass(CustomSwipeActionsScreen::class)
        subclass(ListManagementScreen::class)
        subclass(LayoutsScreen::class)
        subclass(LayoutEditorScreen::class)
        subclass(PerListSettingsScreen::class)
    }
}

val navSavedStateConfiguration: SavedStateConfiguration =
    SavedStateConfiguration { serializersModule = navKeySerializersModule }

/**
 * Maps each [NavKey] to its screen content. Used by both navigation hosts (main app and the
 * Android share activity). Each entry simply delegates to the key's own `Content()` composable.
 */
fun appEntryProvider(): (NavKey) -> NavEntry<NavKey> = entryProvider {
    entry<MainScreen> { it.Content() }
    entry<LoginScreen> { it.Content() }
    entry<OnboardingScreen> { it.Content() }
    entry<BookmarkViewerScreen> { it.Content() }
    entry<ReaderAppearanceScreen> { it.Content() }
    entry<SaveErrorScreen> { it.Content() }
    entry<SettingsScreen> { it.Content() }
    entry<HighlightsScreen> { it.Content() }
    entry<AboutScreen> { it.Content() }
    entry<ShareBookmarkScreen> { it.Content() }
    entry<ShareMultipleBookmarksScreen> { it.Content() }
    entry<ServerSettingsScreen> { it.Content() }
    entry<ThemeSettingsScreen> { it.Content() }
    entry<AppearanceSettingsScreen> { it.Content() }
    entry<DefaultDisplaySettingsScreen> { it.Content() }
    entry<BookmarkViewSettingsScreen> { it.Content() }
    entry<BookmarkListSettingsScreen> { it.Content() }
    entry<NotificationSettingsScreen> { it.Content() }
    entry<BackgroundSyncSettingsScreen> { it.Content() }
    entry<SyncDataSettingsScreen> { it.Content() }
    entry<BackupRestoreScreen> { it.Content() }
    entry<CustomSwipeActionsScreen> { it.Content() }
    entry<ListManagementScreen> { it.Content() }
    entry<LayoutsScreen> { it.Content() }
    entry<LayoutEditorScreen> { it.Content() }
    entry<PerListSettingsScreen> { it.Content() }
}
