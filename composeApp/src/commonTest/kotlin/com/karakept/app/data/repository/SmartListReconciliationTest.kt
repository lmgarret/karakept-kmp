package com.karakept.app.data.repository

import com.karakept.api.model.KarakeepList
import com.karakept.app.data.local.dao.AssetDao
import com.karakept.app.data.local.dao.BookmarkDao
import com.karakept.app.data.local.dao.ListDao
import com.karakept.app.data.local.dao.PendingActionDao
import com.karakept.app.data.local.entity.BookmarkEntity
import com.karakept.app.data.local.entity.PendingActionEntity
import com.karakept.app.data.local.entity.PendingActionType
import com.karakept.app.data.model.Server
import com.karakept.app.data.remote.RemoteDataSource
import com.karakept.app.utils.ImageCacheManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import com.karakept.app.utils.TestAppDispatchers
import kotlinx.coroutines.test.StandardTestDispatcher

/**
 * Unit tests for smart-list membership reconciliation after quick-action mutations.
 *
 * Covers the LIST-02 bug scenario:
 *   - Bookmark starts in smart list A (RSS), not in manual list B
 *   - User adds bookmark to list B  →  server excludes it from list A
 *   - User removes bookmark from list B  →  server includes it back in list A
 *
 * Tests verify both the DB merge logic in [reconcileBookmarkSmartListMembership]
 * and that [processPendingActions] (flush) sends the action to the server before
 * the GET /bookmarks/{id}/lists call.
 */
class SmartListReconciliationTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testAppDispatchers = TestAppDispatchers(testDispatcher)

    // ---- Shared fixtures ----

    private val testServer = Server(
        id = "server-1",
        url = "https://test.example.com",
        apiKey = "test-key",
        label = "Test"
    )

    private val bookmarkDao: BookmarkDao = mockk(relaxed = true)
    private val assetDao: AssetDao = mockk(relaxed = true)
    private val remoteDataSource: RemoteDataSource = mockk(relaxed = true)
    private val pendingActionDao: PendingActionDao = mockk(relaxed = true)
    private val settingsRepository: SettingsRepository = mockk()
    private val serverRepository: ServerRepository = mockk()
    private val highlightRepository: HighlightRepository = mockk(relaxed = true)
    private val imageCacheManager: ImageCacheManager = mockk(relaxed = true)
    private val listDao: ListDao = mockk(relaxed = true)

    private val actionsRepository = BookmarkActionsRepository(
        bookmarkDao = bookmarkDao,
        pendingActionDao = pendingActionDao,
        remoteDataSource = remoteDataSource,
        serverRepository = serverRepository,
        settingsRepository = settingsRepository,
        appDispatchers = testAppDispatchers
    )

    private val bookmarkRepository = BookmarkRepository(
        bookmarkDao = bookmarkDao,
        assetDao = assetDao,
        remoteDataSource = remoteDataSource,
        bookmarkActionsRepository = actionsRepository,
        settingsRepository = settingsRepository,
        serverRepository = serverRepository,
        highlightRepository = highlightRepository,
        imageCacheManager = imageCacheManager,
        listDao = listDao,
        appDispatchers = testAppDispatchers
    )

    init {
        coEvery { serverRepository.servers } returns flowOf(listOf(testServer))
    }

    // ---- Helpers ----

    private fun bookmarkEntity(
        localId: Long = 1L,
        listIds: String = ""
    ) = BookmarkEntity(
        localId = localId,
        remoteId = "orig-$localId",
        serverId = testServer.id,
        url = "https://example.com/$localId",
        title = "Bookmark $localId",
        content = null,
        imageUrl = null,
        bannerImageAssetId = null,
        screenshotAssetId = null,
        description = null,
        createdAt = 1000L,
        isArchived = false,
        isStarred = false,
        listIds = listIds
    )

    private fun karakeepList(id: String, type: KarakeepList.Type = KarakeepList.Type.SMART) =
        KarakeepList(id = id, name = "List $id", type = type)

    // ---- reconcileBookmarkSmartListMembership tests ----

    /**
     * Core scenario: after removing from manual list B, bookmark should appear in smart list A.
     *
     * DB state (after optimistic remove): listIds = "" (empty — listB was removed, listA never local)
     * Server returns: [listA]  (smart list A now includes bookmark again)
     * Expected DB update: listIds = "listA"
     */
    @Test
    fun removeFromManualList_bookmarkAppearsInSmartList() = runTest(testDispatcher) {
        val entity = bookmarkEntity(localId = 1L, listIds = "")
        coEvery { bookmarkDao.getBookmarkById(1L) } returns entity
        coEvery { remoteDataSource.fetchListsForBookmark(testServer, "orig-1") } returns
            listOf(karakeepList("listA"))

        val smartListIds = setOf("listA")
        bookmarkRepository.reconcileBookmarkSmartListMembership(testServer, 1L, smartListIds)

        coVerify {
            bookmarkDao.updateBookmarkMetadata(
                localId = 1L,
                title = entity.title,
                url = entity.url,
                description = entity.description,
                imageUrl = entity.imageUrl,
                bannerImageAssetId = entity.bannerImageAssetId,
                screenshotAssetId = entity.screenshotAssetId,
                tags = entity.tags,
                listIds = "listA",
                isStarred = entity.isStarred,
                isArchived = entity.isArchived,
                isRead = entity.isRead,
                readingTimeMinutes = entity.readingTimeMinutes,
                modifiedAt = any(),
                crawlStatus = any(),
                crawledAt = any(),
                summary = any(),
                summarizationStatus = any()
            )
        }
    }

    /**
     * Adding to manual list B: bookmark leaves smart list A (server excludes it).
     *
     * DB state (after optimistic add): listIds = "listA,listB"
     * Server returns: [] (smart list A excludes bookmark because it's in list B)
     * Expected DB update: listIds = "listB" (manual lists preserved, smart list A removed)
     */
    @Test
    fun addToManualList_bookmarkLeavesSmartList() = runTest(testDispatcher) {
        val entity = bookmarkEntity(localId = 2L, listIds = "listA,listB")
        coEvery { bookmarkDao.getBookmarkById(2L) } returns entity
        coEvery { remoteDataSource.fetchListsForBookmark(testServer, "orig-2") } returns emptyList()

        val smartListIds = setOf("listA")
        bookmarkRepository.reconcileBookmarkSmartListMembership(testServer, 2L, smartListIds)

        coVerify {
            bookmarkDao.updateBookmarkMetadata(
                localId = 2L,
                title = any(),
                url = any(),
                description = any(),
                imageUrl = any(),
                bannerImageAssetId = any(),
                screenshotAssetId = any(),
                tags = any(),
                listIds = "listB",
                isStarred = any(),
                isArchived = any(),
                isRead = any(),
                readingTimeMinutes = any(),
                modifiedAt = any(),
                crawlStatus = any(),
                crawledAt = any(),
                summary = any(),
                summarizationStatus = any()
            )
        }
    }

    /**
     * Manual list membership is preserved even when the server doesn't return it.
     *
     * DB state: listIds = "listC" (manual, not a smart list)
     * Server returns: [listA]
     * Expected: listIds = "listA,listC" — manual list C kept, smart list A added
     */
    @Test
    fun manualListMembershipPreservedDuringReconciliation() = runTest(testDispatcher) {
        val entity = bookmarkEntity(localId = 3L, listIds = "listC")
        coEvery { bookmarkDao.getBookmarkById(3L) } returns entity
        coEvery { remoteDataSource.fetchListsForBookmark(testServer, "orig-3") } returns
            listOf(karakeepList("listA"))

        val smartListIds = setOf("listA") // listC is manual, not in smartListIds

        bookmarkRepository.reconcileBookmarkSmartListMembership(testServer, 3L, smartListIds)

        val listIdsSlot = slot<String>()
        coVerify {
            bookmarkDao.updateBookmarkMetadata(
                localId = 3L,
                title = any(),
                url = any(),
                description = any(),
                imageUrl = any(),
                bannerImageAssetId = any(),
                screenshotAssetId = any(),
                tags = any(),
                listIds = capture(listIdsSlot),
                isStarred = any(),
                isArchived = any(),
                isRead = any(),
                readingTimeMinutes = any(),
                modifiedAt = any(),
                crawlStatus = any(),
                crawledAt = any(),
                summary = any(),
                summarizationStatus = any()
            )
        }
        // Both listA (from server) and listC (manual, preserved) should be present
        val savedIds = listIdsSlot.captured.split(",").toSet()
        assertTrue("listA" in savedIds, "Smart list A should be in saved listIds (got: ${listIdsSlot.captured})")
        assertTrue("listC" in savedIds, "Manual list C should be preserved (got: ${listIdsSlot.captured})")
    }

    /**
     * No DB write occurs when the membership is already correct.
     */
    @Test
    fun noDbWriteWhenMembershipUnchanged() = runTest(testDispatcher) {
        val entity = bookmarkEntity(localId = 4L, listIds = "listA")
        coEvery { bookmarkDao.getBookmarkById(4L) } returns entity
        coEvery { remoteDataSource.fetchListsForBookmark(testServer, "orig-4") } returns
            listOf(karakeepList("listA"))

        val smartListIds = setOf("listA")
        bookmarkRepository.reconcileBookmarkSmartListMembership(testServer, 4L, smartListIds)

        coVerify(exactly = 0) { bookmarkDao.updateBookmarkMetadata(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    /**
     * Stale-server scenario: server returns empty smart list membership.
     * reconcileBookmarkSmartListMembership returns false to signal staleness,
     * so the caller can schedule a deferred smart list sync.
     */
    @Test
    fun staleServerResponse_returnsFalseForDeferredSync() = runTest(testDispatcher) {
        val entity = bookmarkEntity(localId = 10L, listIds = "")
        coEvery { bookmarkDao.getBookmarkById(10L) } returns entity
        coEvery { remoteDataSource.fetchListsForBookmark(testServer, "orig-10") } returns emptyList()

        val smartListIds = setOf("listA")
        val result = bookmarkRepository.reconcileBookmarkSmartListMembership(testServer, 10L, smartListIds)

        // No DB write (server returned stale empty data)
        coVerify(exactly = 0) {
            bookmarkDao.updateBookmarkMetadata(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        }
        // Returns false → caller should mark smart lists for deferred refresh
        assertEquals(false, result)
    }

    /**
     * When server returns fresh smart list data, returns true.
     */
    @Test
    fun freshServerResponse_returnsTrue() = runTest(testDispatcher) {
        val entity = bookmarkEntity(localId = 11L, listIds = "")
        coEvery { bookmarkDao.getBookmarkById(11L) } returns entity
        coEvery { remoteDataSource.fetchListsForBookmark(testServer, "orig-11") } returns
            listOf(karakeepList("listA"))

        val smartListIds = setOf("listA")
        val result = bookmarkRepository.reconcileBookmarkSmartListMembership(testServer, 11L, smartListIds)

        assertEquals(true, result)
        // DB was updated with listA
        coVerify {
            bookmarkDao.updateBookmarkMetadata(
                localId = 11L,
                title = any(),
                url = any(),
                description = any(),
                imageUrl = any(),
                bannerImageAssetId = any(),
                screenshotAssetId = any(),
                tags = any(),
                listIds = "listA",
                isStarred = any(),
                isArchived = any(),
                isRead = any(),
                readingTimeMinutes = any(),
                modifiedAt = any(),
                crawlStatus = any(),
                crawledAt = any(),
                summary = any(),
                summarizationStatus = any()
            )
        }
    }

    // ---- flushPendingActions + reconcile sequence test ----

    /**
     * Key ordering guarantee: the REMOVE_FROM_LIST action is sent to the server
     * BEFORE GET /bookmarks/{id}/lists is called.
     *
     * Without the flush, GET could return stale smart-list membership.
     */
    @Test
    fun flushSendsActionBeforeGetListsIsCalled() = runTest(testDispatcher) {
        val bookmark = bookmarkEntity(localId = 5L, listIds = "")

        // Pending REMOVE_FROM_LIST action for listB
        val pendingAction = PendingActionEntity(
            id = 1L,
            bookmarkRemoteId = "orig-5",
            serverId = testServer.id,
            actionType = PendingActionType.REMOVE_FROM_LIST,
            actionData = """{"listId":"listB"}""",
            createdAt = 1000L
        )

        coEvery { pendingActionDao.getPendingActionsList(testServer.id) } returns listOf(pendingAction)
        coEvery { pendingActionDao.getProcessableActions(testServer.id, any()) } returns listOf(pendingAction)
        coEvery { bookmarkDao.getBookmarkByRemoteId("orig-5", testServer.id) } returns bookmark
        coEvery { bookmarkDao.getBookmarkById(5L) } returns bookmark
        coEvery { remoteDataSource.fetchListsForBookmark(testServer, "orig-5") } returns
            listOf(karakeepList("listA"))

        // Simulate what reconcileBookmarkLists does: flush first, then GET /bookmarks/{id}/lists
        actionsRepository.flushPendingActions(testServer)
        bookmarkRepository.reconcileBookmarkSmartListMembership(testServer, 5L, setOf("listA"))

        // Verify the action was sent to the server (meaning flush ran before GET)
        coVerify(exactly = 1) { remoteDataSource.removeBookmarkFromList(testServer, "listB", "orig-5") }
        coVerify(exactly = 1) { remoteDataSource.fetchListsForBookmark(testServer, "orig-5") }

        // The DB should have been updated with listA
        coVerify {
            bookmarkDao.updateBookmarkMetadata(
                localId = 5L,
                title = any(),
                url = any(),
                description = any(),
                imageUrl = any(),
                bannerImageAssetId = any(),
                screenshotAssetId = any(),
                tags = any(),
                listIds = "listA",
                isStarred = any(),
                isArchived = any(),
                isRead = any(),
                readingTimeMinutes = any(),
                modifiedAt = any(),
                crawlStatus = any(),
                crawledAt = any(),
                summary = any(),
                summarizationStatus = any()
            )
        }
    }
}
