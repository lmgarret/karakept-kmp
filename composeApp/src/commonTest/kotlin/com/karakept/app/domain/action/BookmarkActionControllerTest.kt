package com.karakept.app.domain.action

import com.karakept.app.data.local.dao.BookmarkDao
import com.karakept.app.data.local.dao.PendingActionDao
import com.karakept.app.data.local.entity.BookmarkEntity
import com.karakept.app.data.repository.BaseRepositoryTest
import com.karakept.app.data.repository.BookmarkActionsRepository
import com.karakept.app.data.repository.SettingsRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Unit tests for [BookmarkActionController].
 *
 * Verifies that each action type dispatches to the correct repository method,
 * snackbar messages are correct, and error handling returns BookmarkActionResult.Error.
 */
class BookmarkActionControllerTest : BaseRepositoryTest() {

    private val bookmarkActionsRepository = mockk<BookmarkActionsRepository>(relaxed = true)
    private val bookmarkDao = mockk<BookmarkDao>(relaxed = true)
    private val pendingActionDao = mockk<PendingActionDao>(relaxed = true)
    private val snackbarManager = mockk<ActionSnackbarManager>(relaxed = true)
    private val settingsRepository = mockk<SettingsRepository>(relaxed = true)

    private val controller = BookmarkActionController(
        bookmarkActionsRepository = bookmarkActionsRepository,
        bookmarkDao = bookmarkDao,
        pendingActionDao = pendingActionDao,
        snackbarManager = snackbarManager,
        settingsRepository = settingsRepository
    )

    override fun setup() {
        super.setup()
        // Default stubs needed by executeAction internals
        coEvery { pendingActionDao.getPendingActionsList(any()) } returns emptyList()
        coEvery { settingsRepository.resetProgressOnMarkUnread } returns flowOf(false)
    }

    // ──────────────────────────────────────────────────────────
    // Action dispatch tests
    // ──────────────────────────────────────────────────────────

    @Test
    fun archiveAction_callsRepositoryArchive_returnsSuccess() = runTest {
        val bookmark = makeBookmark()
        val result = controller.executeAction(BookmarkActionEvent.Archive(bookmark))

        assertIs<BookmarkActionResult.Success>(result)
        assertEquals("Bookmark archived", result.message)
        coVerify { bookmarkActionsRepository.archiveBookmark(bookmark.remoteId, bookmark.serverId) }
    }

    @Test
    fun unarchiveAction_callsRepositoryUnarchive_returnsSuccess() = runTest {
        val bookmark = makeBookmark()
        val result = controller.executeAction(BookmarkActionEvent.Unarchive(bookmark))

        assertIs<BookmarkActionResult.Success>(result)
        assertEquals("Bookmark unarchived", result.message)
        coVerify { bookmarkActionsRepository.unarchiveBookmark(bookmark.remoteId, bookmark.serverId) }
    }

    @Test
    fun markReadAction_callsRepositoryMarkRead_returnsSuccess() = runTest {
        val bookmark = makeBookmark()
        val result = controller.executeAction(BookmarkActionEvent.MarkRead(bookmark))

        assertIs<BookmarkActionResult.Success>(result)
        assertEquals("Marked as read", result.message)
        coVerify { bookmarkActionsRepository.markAsRead(bookmark.remoteId, bookmark.serverId) }
    }

    @Test
    fun markUnreadAction_callsRepositoryMarkUnread_returnsSuccess() = runTest {
        val bookmark = makeBookmark()
        val result = controller.executeAction(BookmarkActionEvent.MarkUnread(bookmark))

        assertIs<BookmarkActionResult.Success>(result)
        assertEquals("Marked as unread", result.message)
        coVerify {
            bookmarkActionsRepository.markAsUnread(
                bookmark.remoteId,
                bookmark.serverId,
                resetProgress = false
            )
        }
    }

    @Test
    fun markUnreadAction_withResetProgressTrue_passesResetFlag() = runTest {
        coEvery { settingsRepository.resetProgressOnMarkUnread } returns flowOf(true)
        val bookmark = makeBookmark()

        controller.executeAction(BookmarkActionEvent.MarkUnread(bookmark))

        coVerify {
            bookmarkActionsRepository.markAsUnread(
                bookmark.remoteId,
                bookmark.serverId,
                resetProgress = true
            )
        }
    }

    @Test
    fun toggleFavoriteAction_starredBookmark_callsToggleWithCurrentState() = runTest {
        val bookmark = makeBookmark(isStarred = true)
        val result = controller.executeAction(BookmarkActionEvent.ToggleFavorite(bookmark))

        assertIs<BookmarkActionResult.Success>(result)
        assertEquals("Removed from favorites", result.message)
        coVerify {
            bookmarkActionsRepository.toggleFavourite(bookmark.remoteId, bookmark.serverId, true)
        }
    }

    @Test
    fun toggleFavoriteAction_unstarredBookmark_returnsAddedMessage() = runTest {
        val bookmark = makeBookmark(isStarred = false)
        val result = controller.executeAction(BookmarkActionEvent.ToggleFavorite(bookmark))

        assertIs<BookmarkActionResult.Success>(result)
        assertEquals("Added to favorites", result.message)
        coVerify {
            bookmarkActionsRepository.toggleFavourite(bookmark.remoteId, bookmark.serverId, false)
        }
    }

    @Test
    fun deleteAction_callsRepositoryDelete_returnsSuccess() = runTest {
        val bookmark = makeBookmark()
        val result = controller.executeAction(BookmarkActionEvent.Delete(bookmark))

        assertIs<BookmarkActionResult.Success>(result)
        assertEquals("Bookmark deleted", result.message)
        coVerify {
            bookmarkActionsRepository.deleteBookmark(
                bookmark.localId,
                bookmark.remoteId,
                bookmark.serverId
            )
        }
    }

    @Test
    fun updateTagsAction_callsRepositoryUpdateTags() = runTest {
        val bookmark = makeBookmark()
        val newTags = listOf("tag1", "tag2")

        val result = controller.executeAction(BookmarkActionEvent.UpdateTags(bookmark, newTags))

        assertIs<BookmarkActionResult.Success>(result)
        assertEquals("Tags updated", result.message)
        coVerify {
            bookmarkActionsRepository.updateTags(bookmark.remoteId, bookmark.serverId, newTags, true)
        }
    }

    @Test
    fun moveToListAction_callsRepositoryMoveToList() = runTest {
        val bookmark = makeBookmark()

        val result = controller.executeAction(BookmarkActionEvent.MoveToList(bookmark, "list-1"))

        assertIs<BookmarkActionResult.Success>(result)
        assertEquals("Moved to list", result.message)
        coVerify {
            bookmarkActionsRepository.moveToList(bookmark.remoteId, bookmark.serverId, "list-1", true)
        }
    }

    @Test
    fun removeFromListAction_callsRepositoryRemoveFromList() = runTest {
        val bookmark = makeBookmark()

        val result = controller.executeAction(
            BookmarkActionEvent.RemoveFromList(bookmark, "list-1")
        )

        assertIs<BookmarkActionResult.Success>(result)
        assertEquals("Removed from list", result.message)
        coVerify {
            bookmarkActionsRepository.removeFromList(
                bookmark.remoteId,
                bookmark.serverId,
                "list-1",
                true
            )
        }
    }

    // ──────────────────────────────────────────────────────────
    // Error handling
    // ──────────────────────────────────────────────────────────

    @Test
    fun executeAction_repositoryThrows_returnsError() = runTest {
        val bookmark = makeBookmark()
        coEvery {
            bookmarkActionsRepository.archiveBookmark(any(), any())
        } throws RuntimeException("network fail")

        val result = controller.executeAction(BookmarkActionEvent.Archive(bookmark))

        assertIs<BookmarkActionResult.Error>(result)
        assertEquals("network fail", result.message)
    }

    // ──────────────────────────────────────────────────────────
    // Snackbar dispatch
    // ──────────────────────────────────────────────────────────

    @Test
    fun undoableAction_showsSnackbarWithUndo() = runTest {
        val bookmark = makeBookmark()
        // Archive has requiresUndo = true by default
        controller.executeAction(BookmarkActionEvent.Archive(bookmark))

        coVerify { snackbarManager.showSnackbarWithUndo(any(), any(), any()) }
        coVerify(exactly = 0) { snackbarManager.showSnackbar(any(), any()) }
    }

    @Test
    fun nonUndoableAction_showsPlainSnackbar() = runTest {
        val bookmark = makeBookmark()
        // UpdateTags has requiresUndo = false by default
        controller.executeAction(BookmarkActionEvent.UpdateTags(bookmark, listOf("tag")))

        coVerify { snackbarManager.showSnackbar(any(), any()) }
        coVerify(exactly = 0) { snackbarManager.showSnackbarWithUndo(any(), any(), any()) }
    }

    // ──────────────────────────────────────────────────────────
    // Undo cache
    // ──────────────────────────────────────────────────────────

    @Test
    fun clearUndoCache_doesNotThrow() {
        controller.clearUndoCache()
        // No exception = pass
    }

    // ──────────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────────

    private fun makeBookmark(
        remoteId: Long = 42L,
        serverId: String = "server1",
        tags: String = "",
        listIds: String = "",
        isRead: Boolean = false,
        isStarred: Boolean = false
    ) = BookmarkEntity(
        localId = 1L,
        remoteId = remoteId,
        originalRemoteId = "remote-$remoteId",
        serverId = serverId,
        title = "Test",
        url = "https://example.com",
        description = null,
        imageUrl = null,
        bannerImageAssetId = null,
        screenshotAssetId = null,
        tags = tags,
        listIds = listIds,
        isStarred = isStarred,
        isArchived = false,
        isRead = isRead,
        createdAt = 0L,
        readingTimeMinutes = 0,
        content = null
    )
}
