package com.karakept.app.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.karakept.app.data.local.entity.BookmarkEntity
import com.karakept.app.data.repository.AiCapabilities
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/**
 * Compose UI tests for the AI actions in [BookmarkContextMenu].
 *
 * Both are hidden unless the server has said it will run them: summarizing needs an inference
 * client configured, and re-running AI tagging is an admin-only route. Offering an action the
 * server will only refuse is worse than not offering it at all, so the gating is the contract.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class BookmarkMenuAiActionsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val bookmark = BookmarkEntity(
        localId = 1,
        remoteId = "remote-1",
        serverId = "server-1",
        url = "https://example.com/1",
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

    private fun setMenu(
        capabilities: AiCapabilities,
        onAction: (BookmarkAction) -> Unit = {}
    ) {
        composeTestRule.setContent {
            MaterialTheme {
                BookmarkContextMenu(
                    expanded = true,
                    bookmark = bookmark,
                    aiCapabilities = capabilities,
                    onAction = onAction,
                    onDismiss = {}
                )
            }
        }
    }

    @Test
    fun `a server with an inference client offers summarize but not re-tagging`() {
        setMenu(AiCapabilities(canSummarize = true, isAdmin = false))

        composeTestRule.onNodeWithText("Generate summary").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("Re-run AI tagging").assertCountEquals(0)
    }

    @Test
    fun `an admin key also offers re-tagging`() {
        setMenu(AiCapabilities(canSummarize = true, isAdmin = true))

        composeTestRule.onNodeWithText("Generate summary").assertIsDisplayed()
        composeTestRule.onNodeWithText("Re-run AI tagging").assertIsDisplayed()
    }

    @Test
    fun `a server with no model configured offers neither`() {
        setMenu(AiCapabilities(canSummarize = false, isAdmin = false))

        composeTestRule.onAllNodesWithText("Generate summary").assertCountEquals(0)
        composeTestRule.onAllNodesWithText("Re-run AI tagging").assertCountEquals(0)
    }

    @Test
    fun `summarize emits its action`() {
        var emitted: BookmarkAction? = null
        setMenu(AiCapabilities(canSummarize = true, isAdmin = true)) { emitted = it }

        composeTestRule.onNodeWithText("Generate summary").performClick()

        assertEquals(BookmarkAction.Summarize, emitted)
    }

    @Test
    fun `re-tag emits its action`() {
        var emitted: BookmarkAction? = null
        setMenu(AiCapabilities(canSummarize = true, isAdmin = true)) { emitted = it }

        composeTestRule.onNodeWithText("Re-run AI tagging").performClick()

        assertEquals(BookmarkAction.RetagWithAi, emitted)
    }
}
