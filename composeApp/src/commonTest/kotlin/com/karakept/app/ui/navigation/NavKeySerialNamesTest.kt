package com.karakept.app.ui.navigation

import com.karakept.app.ui.screens.settings.AboutScreen
import com.karakept.app.ui.screens.viewer.BookmarkViewerScreen
import com.karakept.app.ui.screens.settings.DevLogsScreen
import com.karakept.app.ui.screens.highlights.HighlightsScreen
import com.karakept.app.ui.screens.onboarding.LoginScreen
import com.karakept.app.ui.screens.main.MainScreen
import com.karakept.app.ui.screens.onboarding.OnboardingScreen
import com.karakept.app.ui.screens.settings.ReaderAppearanceScreen
import com.karakept.app.ui.screens.share.SaveErrorScreen
import com.karakept.app.ui.screens.settings.SettingsScreen
import com.karakept.app.ui.screens.share.ShareBookmarkScreen
import com.karakept.app.ui.screens.share.ShareMultipleBookmarksScreen
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer

class NavKeySerialNamesTest {

    private inline fun <reified T> assertSerialName(simpleName: String) {
        val serializer: KSerializer<T> = serializer<T>()
        assertEquals("com.karakept.app.ui.screens.$simpleName", serializer.descriptor.serialName)
    }

    @Test
    fun movedKeysKeepTheirSerialName() {
        assertSerialName<AboutScreen>("AboutScreen")
        assertSerialName<BookmarkViewerScreen>("BookmarkViewerScreen")
        assertSerialName<DevLogsScreen>("DevLogsScreen")
        assertSerialName<HighlightsScreen>("HighlightsScreen")
        assertSerialName<LoginScreen>("LoginScreen")
        assertSerialName<MainScreen>("MainScreen")
        assertSerialName<OnboardingScreen>("OnboardingScreen")
        assertSerialName<ReaderAppearanceScreen>("ReaderAppearanceScreen")
        assertSerialName<SaveErrorScreen>("SaveErrorScreen")
        assertSerialName<SettingsScreen>("SettingsScreen")
        assertSerialName<ShareBookmarkScreen>("ShareBookmarkScreen")
        assertSerialName<ShareMultipleBookmarksScreen>("ShareMultipleBookmarksScreen")
    }
}
