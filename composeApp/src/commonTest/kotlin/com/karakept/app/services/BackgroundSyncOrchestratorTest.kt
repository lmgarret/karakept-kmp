package com.karakept.app.services

import com.karakept.app.data.model.Server
import com.karakept.app.data.repository.BookmarkRepository
import com.karakept.app.data.repository.ServerRepository
import com.karakept.app.data.repository.SettingsRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class BackgroundSyncOrchestratorTest {

    private val settingsRepository = mockk<SettingsRepository>(relaxed = true)
    private val bookmarkRepository = mockk<BookmarkRepository>(relaxed = true)
    private val serverRepository = mockk<ServerRepository>(relaxed = true)
    private val notificationProvider = mockk<NotificationProvider>(relaxed = true)

    private val fakeServer = Server(id = "s1", url = "https://example.com", apiKey = "key", label = "Test")

    private fun createOrchestrator() = BackgroundSyncOrchestrator(
        settingsRepository, bookmarkRepository, serverRepository, notificationProvider
    )

    private fun setupDefaults(
        syncEnabled: Boolean = true,
        notificationsEnabled: Boolean = true,
        digestEnabled: Boolean = true,
        canSend: Boolean = true,
        servers: List<Server> = listOf(fakeServer),
        activeServerId: String = "s1"
    ) {
        every { settingsRepository.backgroundSyncEnabled } returns flowOf(syncEnabled)
        every { settingsRepository.notificationsEnabled } returns flowOf(notificationsEnabled)
        every { settingsRepository.backgroundSyncDigestNotification } returns flowOf(digestEnabled)
        every { settingsRepository.activeServerId } returns flowOf(activeServerId)
        every { serverRepository.servers } returns flowOf(servers)
        every { notificationProvider.canSendNotifications() } returns canSend
    }

    @Test
    fun runSync_returnsSkipped_whenSyncDisabled() = runTest {
        setupDefaults(syncEnabled = false)
        assertEquals(SyncResult.SKIPPED, createOrchestrator().runSync())
    }

    @Test
    fun runSync_returnsSkipped_whenNoServers() = runTest {
        setupDefaults(servers = emptyList())
        assertEquals(SyncResult.SKIPPED, createOrchestrator().runSync())
    }

    @Test
    fun runSync_returnsSuccess_afterSync() = runTest {
        setupDefaults()
        coEvery { bookmarkRepository.syncBookmarks(any()) } returns 0
        assertEquals(SyncResult.SUCCESS, createOrchestrator().runSync())
    }

    @Test
    fun runSync_returnsError_whenSyncThrows() = runTest {
        setupDefaults()
        coEvery { bookmarkRepository.syncBookmarks(any()) } throws RuntimeException("network error")
        assertEquals(SyncResult.ERROR, createOrchestrator().runSync())
    }

    @Test
    fun runSync_sendsDigestNotification_whenEnabled() = runTest {
        setupDefaults()
        coEvery { bookmarkRepository.syncBookmarks(any()) } returns 3
        coEvery { bookmarkRepository.getListsNeedingNotification(any()) } returns emptyList()

        createOrchestrator().runSync()

        verify { notificationProvider.sendNotification("Bookmarks synced", "3 new bookmarks synced", NotificationType.DIGEST) }
    }

    @Test
    fun runSync_skipsDigest_whenDigestDisabled() = runTest {
        setupDefaults(digestEnabled = false)
        coEvery { bookmarkRepository.syncBookmarks(any()) } returns 3
        coEvery { bookmarkRepository.getListsNeedingNotification(any()) } returns emptyList()

        createOrchestrator().runSync()

        verify(exactly = 0) { notificationProvider.sendNotification(any(), any(), NotificationType.DIGEST) }
    }

    @Test
    fun runSync_skipsAllNotifications_whenNotificationsDisabled() = runTest {
        setupDefaults(notificationsEnabled = false)
        coEvery { bookmarkRepository.syncBookmarks(any()) } returns 5

        createOrchestrator().runSync()

        verify(exactly = 0) { notificationProvider.sendNotification(any(), any(), any()) }
    }

    @Test
    fun runSync_skipsAllNotifications_whenProviderCannotSend() = runTest {
        setupDefaults(canSend = false)
        coEvery { bookmarkRepository.syncBookmarks(any()) } returns 5

        createOrchestrator().runSync()

        verify(exactly = 0) { notificationProvider.sendNotification(any(), any(), any()) }
    }

    @Test
    fun runSync_sendsListNotification_whenListsNeedNotification() = runTest {
        setupDefaults()
        coEvery { bookmarkRepository.syncBookmarks(any()) } returns 3
        coEvery { bookmarkRepository.getListsNeedingNotification("s1") } returns listOf(
            Triple("list1", "Reading", 2),
            Triple("list2", "Work", 1)
        )

        createOrchestrator().runSync()

        verify {
            notificationProvider.sendNotification(
                "List updates", "2 new in Reading, 1 new in Work", NotificationType.LIST_UPDATE
            )
        }
    }

    @Test
    fun runSync_skipsListNotification_whenNewCountIsZero() = runTest {
        setupDefaults()
        coEvery { bookmarkRepository.syncBookmarks(any()) } returns 0

        createOrchestrator().runSync()

        verify(exactly = 0) { notificationProvider.sendNotification(any(), any(), NotificationType.LIST_UPDATE) }
        // Should NOT even call getListsNeedingNotification
        coVerify(exactly = 0) { bookmarkRepository.getListsNeedingNotification(any()) }
    }

    @Test
    fun runSync_usesActiveServer() = runTest {
        val server2 = Server(id = "s2", url = "https://other.com", apiKey = "k2", label = "Other")
        setupDefaults(servers = listOf(fakeServer, server2), activeServerId = "s2")
        coEvery { bookmarkRepository.syncBookmarks(any()) } returns 0

        createOrchestrator().runSync()

        coVerify { bookmarkRepository.syncBookmarks(server2) }
    }

    @Test
    fun runSync_fallsBackToFirstServer_whenActiveNotFound() = runTest {
        setupDefaults(activeServerId = "nonexistent")
        coEvery { bookmarkRepository.syncBookmarks(any()) } returns 0

        createOrchestrator().runSync()

        coVerify { bookmarkRepository.syncBookmarks(fakeServer) }
    }

    // ---- Message formatting tests ----

    @Test
    fun formatDigestMessage_withNewBookmarks_showsCount() {
        val (title, message) = BackgroundSyncOrchestrator.formatDigestMessage(5)
        assertEquals("Bookmarks synced", title)
        assertEquals("5 new bookmarks synced", message)
    }

    @Test
    fun formatDigestMessage_singleBookmark_noPlural() {
        val (_, message) = BackgroundSyncOrchestrator.formatDigestMessage(1)
        assertEquals("1 new bookmark synced", message)
    }

    @Test
    fun formatDigestMessage_zeroBookmarks_showsUpToDate() {
        val (_, message) = BackgroundSyncOrchestrator.formatDigestMessage(0)
        assertEquals("Bookmarks are up to date", message)
    }

    @Test
    fun formatListMessage_multipleList_joinedWithComma() {
        val (title, message) = BackgroundSyncOrchestrator.formatListMessage(
            listOf("Reading" to 3, "Work" to 1)
        )
        assertEquals("List updates", title)
        assertEquals("3 new in Reading, 1 new in Work", message)
    }
}
