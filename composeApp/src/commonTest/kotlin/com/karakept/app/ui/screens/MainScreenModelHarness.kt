package com.karakept.app.ui.screens

import com.karakept.app.data.local.entity.BookmarkEntity
import com.karakept.app.data.model.DefaultListType
import com.karakept.app.data.model.FilterConfig
import com.karakept.app.data.model.Server
import com.karakept.app.data.model.SwipeAction
import com.karakept.app.data.repository.BookmarkActionsRepository
import com.karakept.app.data.repository.BookmarkRepository
import com.karakept.app.data.repository.HighlightRepository
import com.karakept.app.data.repository.ListRepository
import com.karakept.app.data.repository.ServerRepository
import com.karakept.app.data.repository.SettingsRepository
import com.karakept.app.domain.action.ActionSnackbarManager
import com.karakept.app.domain.action.BookmarkActionController
import com.karakept.app.utils.TestAppDispatchers
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * The mocks a [MainScreenModel] needs to exist, with the bookmark view backed by a list the test
 * can change.
 *
 * The model reads its rows by asking the database for the view's size and then for the pages
 * under the viewport, so a test that wants to describe a view describes it once — in [rows] — and
 * the harness answers both questions from it, the way the real queries do. Setting [rows] is
 * therefore the same thing as a write landing in the table: the count re-emits and the pages on
 * screen are read again.
 */
class MainScreenModelHarness(private val testDispatcher: CoroutineDispatcher) {

    val serverRepository: ServerRepository = mockk(relaxed = true)
    val bookmarkRepository: BookmarkRepository = mockk(relaxed = true)
    val bookmarkActionsRepository: BookmarkActionsRepository = mockk(relaxed = true)
    val settingsRepository: SettingsRepository = mockk(relaxed = true)
    val listRepository: ListRepository = mockk(relaxed = true)
    val bookmarkActionController: BookmarkActionController = mockk(relaxed = true)
    val snackbarManager: ActionSnackbarManager = mockk(relaxed = true)
    val highlightRepository: HighlightRepository = mockk(relaxed = true)

    val server = Server(
        id = "server-1",
        url = "https://example.com",
        apiKey = "test-key",
        label = "Test"
    )

    /** The whole view, in order. Changing it is a write landing in the table. */
    var rows: List<BookmarkEntity>
        get() = viewRows.value
        set(value) {
            viewRows.value = value
        }

    private val viewRows = MutableStateFlow<List<BookmarkEntity>>(emptyList())

    /** Every (offset, limit) a page was read at, in order. */
    val pageReads = mutableListOf<Pair<Int, Int>>()

    /** Every filter a page was read for, in order. */
    val readFilters = mutableListOf<FilterConfig>()

    init {
        every { serverRepository.servers } returns flowOf(listOf(server))
        every { settingsRepository.allListSettings } returns flowOf(emptyMap())
        every { settingsRepository.swipeLeftAction } returns flowOf(SwipeAction.MARK_READ)
        every { settingsRepository.swipeRightAction } returns flowOf(SwipeAction.ARCHIVE)
        every { settingsRepository.customSwipeActionConfigs } returns flowOf(emptyList())
        every { settingsRepository.swipeLeftConfigId } returns flowOf(null)
        every { settingsRepository.swipeRightConfigId } returns flowOf(null)
        every { settingsRepository.dimReadBookmarks } returns flowOf(true)
        every { settingsRepository.defaultLayoutId } returns flowOf(null)
        every { settingsRepository.customLayouts } returns flowOf(emptyList())
        every { settingsRepository.offlineMode } returns flowOf(true)
        every { settingsRepository.activeServerId } returns flowOf("server-1")
        every { settingsRepository.defaultListType } returns flowOf(DefaultListType.ALL_BOOKMARKS)
        every { settingsRepository.defaultListId } returns flowOf(null)
        every { settingsRepository.lastActiveFilterStatus } returns flowOf(null)
        every { settingsRepository.lastActiveFilterListId } returns flowOf(null)
        every { listRepository.lists } returns MutableStateFlow(emptyList())
        every { highlightRepository.getHighlightsCount(any()) } returns flowOf(0)
        every { bookmarkRepository.getOfflineBookmarkCount(any()) } returns flowOf(0)
        every { bookmarkRepository.getBookmarks(any()) } returns flowOf(emptyList())
        every { bookmarkActionsRepository.bookmarkChangedEvents } returns MutableSharedFlow()
        every { bookmarkActionsRepository.aiCapabilities } returns MutableStateFlow(emptyMap())
        every { bookmarkActionController.undoCompletedEvents } returns MutableSharedFlow()
        every { bookmarkRepository.syncReports } returns MutableSharedFlow()
        every { bookmarkRepository.backgroundSyncCompleted } returns MutableSharedFlow()

        // The view's size and its pages, answered from the same list — one predicate behind both,
        // as in the real queries.
        //
        // Derived from the rows rather than held separately, so a write that changes a row
        // without changing how many there are still re-emits. That is what Room does: its flows
        // re-run on invalidation, not on the value changing, and the list leans on it — a
        // bookmark marked read has to reach the screen, and the count is the same either way.
        every { bookmarkRepository.countBookmarksForViewFlow(any(), any()) } returns
            viewRows.map { it.size }
        coEvery { bookmarkRepository.getBookmarkPage(any(), any(), any(), any()) } answers {
            val filter = secondArg<FilterConfig>()
            val offset = thirdArg<Int>()
            val limit = arg<Int>(3)
            pageReads += offset to limit
            readFilters += filter
            val all = viewRows.value
            if (offset >= all.size) emptyList()
            else all.subList(offset, minOf(offset + limit, all.size))
        }

    }

    /** Publishes [newRows] as the view, as a database write would. */
    fun publish(newRows: List<BookmarkEntity>) {
        viewRows.value = newRows
    }

    fun clearReads() {
        pageReads.clear()
        readFilters.clear()
    }

    /** Offsets read since the last [clearReads], de-duplicated and in order. */
    fun offsetsRead(): List<Int> = pageReads.map { it.first }.distinct().sorted()

    fun createModel() = MainScreenModel(
        serverRepository = serverRepository,
        bookmarkRepository = bookmarkRepository,
        bookmarkActionsRepository = bookmarkActionsRepository,
        settingsRepository = settingsRepository,
        listRepository = listRepository,
        bookmarkActionController = bookmarkActionController,
        snackbarManager = snackbarManager,
        highlightRepository = highlightRepository,
        appDispatchers = TestAppDispatchers(testDispatcher)
    )

    companion object {
        fun bookmark(
            id: Long,
            remoteId: String = "remote-$id",
            isRead: Boolean = false,
            isArchived: Boolean = false,
            tags: String = "",
            listIds: String = ""
        ) = BookmarkEntity(
            localId = id,
            remoteId = remoteId,
            serverId = "server-1",
            url = "https://example.com/$id",
            title = "Bookmark $id",
            content = null,
            imageUrl = null,
            bannerImageAssetId = null,
            screenshotAssetId = null,
            description = null,
            createdAt = id,
            isArchived = isArchived,
            isStarred = false,
            isRead = isRead,
            tags = tags,
            listIds = listIds
        )
    }
}
