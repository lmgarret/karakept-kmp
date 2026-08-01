package com.karakept.app.ui.screens

import com.karakept.api.model.KarakeepList
import com.karakept.app.data.local.entity.BookmarkEntity
import com.karakept.app.data.model.DefaultListType
import com.karakept.app.data.model.FilterStatus
import com.karakept.app.data.model.ListSettings
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
import com.karakept.app.domain.action.UndoCompletedEvent
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A list opened at startup must still show its bookmarks once the startup sync finishes.
 *
 * The startup sync refreshes the lists first, which can change the *effective* query for the
 * view already on screen: a list with `includeChildListBookmarks` only expands into several
 * list IDs once its children are known, and a multi-list filter is applied client-side on top
 * of paged DB rows. The in-place refresh that follows the sync must handle that the same way
 * the initial load does — by paging until it finds items — instead of publishing the empty
 * first page and blanking the list.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RefreshAfterSyncTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var serverRepository: ServerRepository
    private lateinit var bookmarkRepository: BookmarkRepository
    private lateinit var bookmarkActionsRepository: BookmarkActionsRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var listRepository: ListRepository
    private lateinit var bookmarkActionController: BookmarkActionController
    private lateinit var snackbarManager: ActionSnackbarManager
    private lateinit var highlightRepository: HighlightRepository

    private val fakeServer = Server(
        id = "server-1",
        url = "https://example.com",
        apiKey = "test-key",
        label = "Test"
    )

    private val homeList = KarakeepList(id = "list-a", name = "Home", type = KarakeepList.Type.MANUAL)
    private val childList =
        KarakeepList(id = "list-a-1", name = "Child", parentId = "list-a", type = KarakeepList.Type.MANUAL)

    /** The drawer's lists, empty until the sync refreshes them — as on a cold start. */
    private val listsFlow = MutableStateFlow<List<KarakeepList>>(emptyList())

    // 25 bookmarks; only the three oldest belong to the home list, so they sit on the second
    // page of the unfiltered query and the first page of the list query.
    private var allBookmarks = (1L..25L).map { id ->
        makeBookmark(id, listIds = if (id <= 3L) "list-a" else "")
    }

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        serverRepository = mockk(relaxed = true)
        bookmarkRepository = mockk(relaxed = true)
        bookmarkActionsRepository = mockk(relaxed = true)
        settingsRepository = mockk(relaxed = true)
        listRepository = mockk(relaxed = true)
        bookmarkActionController = mockk(relaxed = true)
        snackbarManager = mockk(relaxed = true)
        highlightRepository = mockk(relaxed = true)

        every { serverRepository.servers } returns flowOf(listOf(fakeServer))
        every { settingsRepository.allListSettings } returns flowOf(emptyMap())
        every { settingsRepository.getListSettings(any()) } returns
            flowOf(ListSettings(includeChildListBookmarks = true))
        every { settingsRepository.swipeLeftAction } returns flowOf(SwipeAction.MARK_READ)
        every { settingsRepository.swipeRightAction } returns flowOf(SwipeAction.ARCHIVE)
        every { settingsRepository.customSwipeActionConfigs } returns flowOf(emptyList())
        every { settingsRepository.swipeLeftConfigId } returns flowOf(null)
        every { settingsRepository.swipeRightConfigId } returns flowOf(null)
        every { settingsRepository.dimReadBookmarks } returns flowOf(true)
        every { settingsRepository.defaultLayoutId } returns flowOf(null)
        every { settingsRepository.customLayouts } returns flowOf(emptyList())
        every { settingsRepository.offlineMode } returns flowOf(false)
        every { settingsRepository.activeServerId } returns flowOf("server-1")
        every { settingsRepository.defaultListType } returns flowOf(DefaultListType.SPECIFIC_LIST)
        every { settingsRepository.defaultListId } returns flowOf("list-a")
        every { settingsRepository.lastActiveFilterStatus } returns flowOf(null)
        every { settingsRepository.lastActiveFilterListId } returns flowOf(null)
        every { listRepository.lists } returns listsFlow
        every { highlightRepository.getHighlightsCount(any()) } returns flowOf(0)
        every { bookmarkRepository.getBookmarks(any()) } returns flowOf(emptyList())
        every { bookmarkActionsRepository.bookmarkChangedEvents } returns MutableSharedFlow<Long>()
        every { bookmarkActionController.undoCompletedEvents } returns MutableSharedFlow<UndoCompletedEvent>()
        every { bookmarkRepository.syncReports } returns MutableSharedFlow()
        every { bookmarkRepository.backgroundSyncCompleted } returns MutableSharedFlow()
        every { bookmarkRepository.syncProgress } returns MutableStateFlow(
            com.karakept.app.data.model.SyncProgress.Idle
        )
        coEvery { bookmarkRepository.shouldAutoSync(any()) } returns true

        // Step 1 of the sync: the lists land, so the home list's children become known.
        coEvery { listRepository.refreshLists(any()) } answers {
            listsFlow.value = listOf(homeList, childList)
        }

        // Stands in for BookmarkRepository.buildPagedQuery: a list query filters on membership
        // and ignores the status, an unfiltered query applies the status.
        coEvery {
            bookmarkRepository.getBookmarksPaged(
                server = any(), status = any(), offset = any(), limit = any(),
                sort = any(), listId = any()
            )
        } answers {
            val status = arg<FilterStatus>(1)
            val offset = arg<Int>(2)
            val limit = arg<Int>(3)
            val listId = arg<String?>(5)
            allBookmarks
                .sortedByDescending { it.createdAt }
                .filter { bookmark ->
                    if (listId != null) {
                        listId in bookmark.listIds.split(",")
                    } else {
                        status != FilterStatus.ALL || !bookmark.isArchived
                    }
                }
                .drop(offset)
                .take(limit)
        }
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun makeBookmark(id: Long, listIds: String) = BookmarkEntity(
        localId = id,
        remoteId = id,
        originalRemoteId = "remote-$id",
        serverId = "server-1",
        url = "https://example.com/$id",
        title = "Bookmark $id",
        content = null,
        imageUrl = null,
        bannerImageAssetId = null,
        screenshotAssetId = null,
        description = null,
        createdAt = id,
        isArchived = false,
        isStarred = false,
        listIds = listIds
    )

    private fun createMainScreenModel() = MainScreenModel(
        serverRepository = serverRepository,
        bookmarkRepository = bookmarkRepository,
        bookmarkActionsRepository = bookmarkActionsRepository,
        settingsRepository = settingsRepository,
        listRepository = listRepository,
        bookmarkActionController = bookmarkActionController,
        snackbarManager = snackbarManager,
        highlightRepository = highlightRepository
    )

    @Test
    fun `home list keeps its bookmarks across the startup sync`() = runTest(testDispatcher) {
        val model = createMainScreenModel()
        backgroundScope.launch { model.bookmarks.collect { } }
        advanceUntilIdle()

        assertEquals(
            listOf(3L, 2L, 1L),
            model.bookmarks.value.map { it.remoteId },
            "the home list's bookmarks must survive the sync that follows startup"
        )
    }

    @Test
    fun `a list emptied on the server is shown as empty after the sync`() = runTest(testDispatcher) {
        // The sync drops every bookmark's membership in the home list, so the view really is
        // empty afterwards — the refresh must publish that, not page on until it finds rows.
        coEvery { listRepository.refreshLists(any()) } answers {
            listsFlow.value = listOf(homeList, childList)
            allBookmarks = allBookmarks.map { it.copy(listIds = "") }
        }

        val model = createMainScreenModel()
        backgroundScope.launch { model.bookmarks.collect { } }
        advanceUntilIdle()

        assertEquals(emptyList(), model.bookmarks.value.map { it.remoteId }, "bookmarks after sync")
    }
}
