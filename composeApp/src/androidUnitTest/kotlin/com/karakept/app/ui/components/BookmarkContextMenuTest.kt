package com.karakept.app.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.karakept.api.model.KarakeepList
import com.karakept.app.data.local.entity.BookmarkEntity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Compose UI tests for the desktop [BookmarkContextMenu].
 *
 * Regression coverage for #231: "Move to List" and "Edit Tags" must open the
 * shared pickers and emit a real value, rather than firing a no-op action.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class BookmarkContextMenuTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val bookmark = BookmarkEntity(
        localId = 1,
        remoteId = 1,
        originalRemoteId = "remote-1",
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
    fun `Move to List opens picker and emits selected list id`() {
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

        composeTestRule.onNodeWithText("Move to List").performClick()

        // The list picker should now be visible with the available lists.
        composeTestRule.onNodeWithText("Read Later").assertIsDisplayed()
        composeTestRule.onNodeWithText("Read Later").performClick()

        val action = emitted
        assertTrue(action is BookmarkAction.MoveToList, "Expected MoveToList, got $action")
        assertEquals("list-1", (action as BookmarkAction.MoveToList).listId)
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
