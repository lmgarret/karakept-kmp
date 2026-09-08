package com.karakept.app.ui.screens.viewer

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.karakept.app.data.local.entity.BookmarkEntity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/**
 * Regression coverage for #278: the details panel must show the bookmark's full,
 * untruncated URL, open it in the browser on tap, and support copying it to the
 * clipboard.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class BookmarkDetailsPanelTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val longUrl =
        "https://example.com/a/very/long/path/that/would/otherwise/be/truncated/in/a/single/line/details/row?query=some-value&another=value"

    private val bookmark = BookmarkEntity(
        localId = 1,
        remoteId = 1,
        originalRemoteId = "remote-1",
        serverId = "server-1",
        url = longUrl,
        title = "Bookmark 1",
        content = null,
        imageUrl = null,
        bannerImageAssetId = null,
        screenshotAssetId = null,
        description = null,
        createdAt = 0L,
        isArchived = false,
        isStarred = false
    )

    @Test
    fun `full link is shown untruncated`() {
        composeTestRule.setContent {
            MaterialTheme {
                BookmarkDetailsPanel(
                    visible = true,
                    bookmark = bookmark,
                    onDismiss = {}
                )
            }
        }

        composeTestRule.onNodeWithText(longUrl).assertIsDisplayed()
    }

    @Test
    fun `tapping the link opens it in the browser`() {
        val openedUris = mutableListOf<String>()
        val fakeUriHandler = object : UriHandler {
            override fun openUri(uri: String) {
                openedUris += uri
            }
        }
        var openedCount = 0
        composeTestRule.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalUriHandler provides fakeUriHandler) {
                    BookmarkDetailsPanel(
                        visible = true,
                        bookmark = bookmark,
                        onOpenLink = { openedCount++ },
                        onDismiss = {}
                    )
                }
            }
        }

        composeTestRule.onNodeWithText(longUrl).performClick()

        assertEquals(listOf(longUrl), openedUris)
        assertEquals(1, openedCount)
    }

    @Test
    fun `copy button copies link and notifies caller`() {
        var copiedCount = 0
        composeTestRule.setContent {
            MaterialTheme {
                BookmarkDetailsPanel(
                    visible = true,
                    bookmark = bookmark,
                    onLinkCopied = { copiedCount++ },
                    onDismiss = {}
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Copy link").performClick()

        assertEquals(1, copiedCount)
    }
}
