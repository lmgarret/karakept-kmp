package com.karakept.app.ui.components.bookmark

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.karakept.api.model.KarakeepList
import com.karakept.app.data.model.Bookmark
import com.karakept.app.domain.ListMembershipChange
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Compose UI tests for the desktop [BookmarkContextMenu].
 *
 * Regression coverage for #231: "Edit Lists" and "Edit Tags" must open the
 * shared pickers and emit a real value, rather than firing a no-op action.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class BookmarkContextMenuTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val bookmark = Bookmark(
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

    private val lists = listOf(
        KarakeepList(id = "list-1", name = "Read Later"),
        KarakeepList(id = "list-2", name = "Archive")
    )

    @Test
    fun `Edit Lists opens the membership sheet and emits the change`() {
        var emitted: BookmarkAction? = null
        composeTestRule.setContent {
            MaterialTheme {
                BookmarkContextMenu(
                    expanded = true,
                    bookmark = bookmark,
                    availableLists = lists,
                    onAction = { emitted = it },
                    onDismiss = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Edit Lists").performClick()

        composeTestRule.onNodeWithText("Read Later").assertIsDisplayed()
        composeTestRule.onNodeWithText("Read Later").performClick()
        composeTestRule.onNodeWithText("Done").performClick()

        val action = emitted
        assertTrue(action is BookmarkAction.UpdateLists, "Expected UpdateLists, got $action")
        assertEquals(ListMembershipChange(added = setOf("list-1")), action.change)
    }

    @Test
    fun `Edit Tags opens tag editor dialog`() {
        composeTestRule.setContent {
            MaterialTheme {
                BookmarkContextMenu(
                    expanded = true,
                    bookmark = bookmark,
                    availableTags = listOf("kotlin", "compose"),
                    onAction = {},
                    onDismiss = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Edit Tags").performClick()

        // The tag editor dialog uses "Edit Tags" as its title and a Save button.
        composeTestRule.onNodeWithText("Save").assertIsDisplayed()
    }
}
