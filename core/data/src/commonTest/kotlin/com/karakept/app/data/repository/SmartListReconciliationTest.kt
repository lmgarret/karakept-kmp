package com.karakept.app.data.repository

import com.karakept.api.model.KarakeepList
import com.karakept.app.data.cache.ImageCacheManager
import com.karakept.app.data.local.dao.AssetDao
import com.karakept.app.data.local.dao.BookmarkDao
import com.karakept.app.data.local.dao.ListDao
import com.karakept.app.data.local.dao.PendingActionDao
import com.karakept.app.data.local.entity.ListEntity
import com.karakept.app.data.model.Server
import com.karakept.app.data.model.SyncStrategy
import com.karakept.app.data.remote.RemoteDataSource
import com.karakept.app.testing.testBookmarkEntity
import com.karakept.app.utils.TestAppDispatchers
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest

/**
 * Smart-list membership is never read from `GET /bookmarks/{id}/lists`.
 *
 * Karakeep answers that route from its `bookmarksInLists` join table (`List.forBookmark`), which
 * only manual lists write to: a smart list is a saved query, evaluated when its own bookmarks are
 * asked for (`SmartList.getBookmarkIds`), and holds no rows there. So the route reports a
 * bookmark's manual lists and never its smart ones — reading it as the whole answer is what used
 * to drop a bookmark out of the smart list it was being looked at in. Only a sync of the smart
 * list itself (`GET /lists/{id}/bookmarks`) says what is in it.
 */
class SmartListReconciliationTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testAppDispatchers = TestAppDispatchers(testDispatcher)

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
    private val settingsRepository: SettingsRepository = mockk(relaxed = true)
    private val serverRepository: ServerRepository = mockk()
    private val highlightRepository: HighlightRepository = mockk(relaxed = true)
    private val imageCacheManager: ImageCacheManager = mockk(relaxed = true)
    private val listDao: ListDao = mockk(relaxed = true)

    private val bookmarkRepository = BookmarkRepository(
        bookmarkDao = bookmarkDao,
        assetDao = assetDao,
        remoteDataSource = remoteDataSource,
        bookmarkActionsRepository = BookmarkActionsRepository(
            bookmarkDao = bookmarkDao,
            pendingActionDao = pendingActionDao,
            remoteDataSource = remoteDataSource,
            serverRepository = serverRepository,
            settingsRepository = settingsRepository,
            appDispatchers = testAppDispatchers
        ),
        settingsRepository = settingsRepository,
        serverRepository = serverRepository,
        highlightRepository = highlightRepository,
        imageCacheManager = imageCacheManager,
        listDao = listDao,
        appDispatchers = testAppDispatchers
    )

    init {
        every { serverRepository.servers } returns flowOf(listOf(testServer))
        every { settingsRepository.contentSyncStrategy } returns flowOf(SyncStrategy.NEVER)
        every { settingsRepository.preferFullPageHtml } returns flowOf(false)
        coEvery { remoteDataSource.fetchBookmark(testServer, any(), any()) } returns
            com.karakept.api.model.Bookmark(id = "orig-1", title = "Bookmark")
        coEvery { listDao.getListsForServerOnce(testServer.id) } returns listOf(
            listEntity("smartA", type = "smart"),
            listEntity("manualB"),
            listEntity("manualC"),
        )
    }

    private fun listEntity(id: String, type: String = "manual") = ListEntity(
        remoteId = id,
        serverId = testServer.id,
        name = "List $id",
        icon = null,
        parentId = null,
        description = null,
        type = type,
        query = null,
        updatedAt = 0L
    )

    private fun manualList(id: String) = KarakeepList(id = id, name = "List $id", type = KarakeepList.Type.MANUAL)

    private suspend fun refreshAndCaptureListIds(localListIds: String, serverLists: List<KarakeepList>): Set<String> {
        val entity = testBookmarkEntity(localId = 1L, remoteId = "orig-1", serverId = testServer.id, listIds = localListIds)
        coEvery { bookmarkDao.getBookmarkByRemoteId("orig-1", testServer.id) } returns entity
        coEvery { remoteDataSource.fetchListsForBookmark(testServer, "orig-1") } returns serverLists

        bookmarkRepository.syncSingleBookmark("orig-1", testServer.id)

        val listIds = slot<String>()
        coVerify {
            bookmarkDao.updateBookmarkMetadata(
                localId = 1L, title = any(), url = any(), description = any(), imageUrl = any(),
                bannerImageAssetId = any(), screenshotAssetId = any(), tags = any(),
                listIds = capture(listIds), isStarred = any(), isArchived = any(), isRead = any(),
                readingTimeMinutes = any(), modifiedAt = any(), crawlStatus = any(), crawledAt = any(),
                summary = any(), summarizationStatus = any()
            )
        }
        return listIds.captured.split(",").filter { it.isNotEmpty() }.toSet()
    }

    @Test
    fun `refreshing a bookmark keeps the smart lists it is in`() = runTest(testDispatcher) {
        // What Karakeep returns for a bookmark in smartA and manualB: manualB alone.
        val saved = refreshAndCaptureListIds("smartA,manualB", listOf(manualList("manualB")))

        assertEquals(setOf("smartA", "manualB"), saved)
    }

    @Test
    fun `refreshing a bookmark takes its manual lists from the server`() = runTest(testDispatcher) {
        val saved = refreshAndCaptureListIds("smartA,manualB", listOf(manualList("manualC")))

        assertEquals(setOf("smartA", "manualC"), saved)
    }

    @Test
    fun `refreshing a bookmark never adds a smart list it was not in`() = runTest(testDispatcher) {
        val saved = refreshAndCaptureListIds("manualB", listOf(manualList("manualB")))

        assertEquals(setOf("manualB"), saved)
    }

    @Test
    fun `a failed lists request keeps every membership`() = runTest(testDispatcher) {
        val entity = testBookmarkEntity(localId = 1L, remoteId = "orig-1", serverId = testServer.id, listIds = "smartA,manualB")
        coEvery { bookmarkDao.getBookmarkByRemoteId("orig-1", testServer.id) } returns entity
        coEvery { remoteDataSource.fetchListsForBookmark(testServer, "orig-1") } throws RuntimeException("offline")

        bookmarkRepository.syncSingleBookmark("orig-1", testServer.id)

        coVerify {
            bookmarkDao.updateBookmarkMetadata(
                localId = 1L, title = any(), url = any(), description = any(), imageUrl = any(),
                bannerImageAssetId = any(), screenshotAssetId = any(), tags = any(),
                listIds = "smartA,manualB", isStarred = any(), isArchived = any(), isRead = any(),
                readingTimeMinutes = any(), modifiedAt = any(), crawlStatus = any(), crawledAt = any(),
                summary = any(), summarizationStatus = any()
            )
        }
    }
}
