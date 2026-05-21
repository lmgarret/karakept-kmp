package com.karakept.app.data.repository

import com.karakept.app.data.local.dao.AssetDao
import com.karakept.app.data.local.dao.BookmarkDao
import com.karakept.app.data.local.dao.ListDao
import com.karakept.app.data.local.entity.BookmarkEntity
import com.karakept.app.data.model.Server
import com.karakept.app.data.model.SyncStrategy
import com.karakept.app.data.remote.RemoteDataSource
import com.karakept.app.utils.ImageCacheManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/**
 * Regression test: syncSingleBookmark must emit bookmarkChangedEvents
 * so the UI's accumulated list refreshes with updated asset IDs.
 */
class SyncSingleBookmarkNotifyTest : BaseRepositoryTest() {

    private val bookmarkDao = mockk<BookmarkDao>(relaxed = true)
    private val assetDao = mockk<AssetDao>(relaxed = true)
    private val remoteDataSource = mockk<RemoteDataSource>(relaxed = true)
    private val bookmarkActionsRepository = mockk<BookmarkActionsRepository>(relaxed = true)
    private val settingsRepository = mockk<SettingsRepository>(relaxed = true)
    private val serverRepository = mockk<ServerRepository>(relaxed = true)
    private val highlightRepository = mockk<HighlightRepository>(relaxed = true)
    private val imageCacheManager = mockk<ImageCacheManager>(relaxed = true)
    private val listDao = mockk<ListDao>(relaxed = true)

    private val repository = BookmarkRepository(
        bookmarkDao,
        assetDao,
        remoteDataSource,
        bookmarkActionsRepository,
        settingsRepository,
        serverRepository,
        highlightRepository,
        imageCacheManager,
        listDao
    )

    private val testServer = Server("s1", "http://localhost", "key", "Label")

    private fun makeBookmark(
        remoteId: Long = 42L,
        bannerImageAssetId: String? = null,
        screenshotAssetId: String? = null
    ) = BookmarkEntity(
        localId = 1L,
        remoteId = remoteId,
        originalRemoteId = "remote-$remoteId",
        serverId = testServer.id,
        title = "Test",
        url = "https://example.com",
        description = null,
        imageUrl = null,
        bannerImageAssetId = bannerImageAssetId,
        screenshotAssetId = screenshotAssetId,
        tags = "",
        listIds = "",
        isStarred = false,
        isArchived = false,
        isRead = false,
        createdAt = 0L,
        readingTimeMinutes = 0,
        content = ""
    )

    @Test
    fun syncSingleBookmark_emitsBookmarkChangedEvent() = runTest(testDispatcher) {
        val existing = makeBookmark(remoteId = 42L)

        coEvery { serverRepository.servers } returns flowOf(listOf(testServer))
        coEvery { bookmarkDao.getBookmarkByRemoteId(42L, testServer.id) } returns existing
        coEvery { settingsRepository.contentSyncStrategy } returns flowOf(SyncStrategy.ALL)
        every { settingsRepository.preferFullPageHtml } returns flowOf(false)

        val mockDto = mockk<com.karakept.api.model.Bookmark>(relaxed = true) {
            every { id } returns "remote-42"
            every { title } returns "Updated Title"
            every { content } returns mockk(relaxed = true) {
                every { type } returns com.karakept.api.model.BookmarkContent.Type.LINK
                every { url } returns "https://example.com"
                every { description } returns null
                every { imageUrl } returns null
                every { htmlContent } returns null
                every { text } returns null
                every { title } returns "Updated Title"
            }
            every { assets } returns emptyList()
            every { tags } returns emptyList()
            every { favourited } returns false
            every { archived } returns false
            every { note } returns null
        }
        coEvery { remoteDataSource.fetchBookmark(testServer, "remote-42") } returns mockDto

        repository.syncSingleBookmark(42L, testServer.id)

        coVerify { bookmarkActionsRepository.notifyBookmarkChanged(42L) }
    }
}
