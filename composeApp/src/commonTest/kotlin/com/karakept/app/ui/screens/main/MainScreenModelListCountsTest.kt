package com.karakept.app.ui.screens.main

import com.karakept.app.testing.testBookmark
import com.karakept.api.model.KarakeepList
import com.karakept.app.data.model.ListSettings
import com.karakept.app.data.model.Server
import io.mockk.coEvery
import io.mockk.every
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
 * Regression tests for #315: the drawer's list count must only fold in a nested list's
 * bookmarks when that list's `includeChildListBookmarks` setting is enabled.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainScreenModelListCountsTest {
    private val testDispatcher = StandardTestDispatcher()
    private val harness = MainScreenModelHarness(testDispatcher)

    private val fakeServer = Server(
        id = "server-1",
        url = "https://example.com",
        apiKey = "test-key",
        label = "Test"
    )

    private val parentList = KarakeepList(id = "list-a", name = "Parent", type = KarakeepList.Type.MANUAL)
    private val childList =
        KarakeepList(id = "list-b", name = "Child", parentId = "list-a", type = KarakeepList.Type.MANUAL)

    // 10 unread bookmarks in the parent list, 5 unread in the nested child list.
    private val allBookmarks =
        (1L..10L).map { testBookmark(it, listIds = "list-a", isRead = false) } +
            (11L..15L).map { testBookmark(it, listIds = "list-b", isRead = false) }

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        every { harness.serverRepository.servers } returns flowOf(listOf(fakeServer))
        every { harness.settingsRepository.getListSettings(any()) } returns flowOf(ListSettings())
        every { harness.listRepository.lists } returns MutableStateFlow(listOf(parentList, childList))
        every { harness.bookmarkRepository.getBookmarks(any()) } returns flowOf(allBookmarks)
        bookmarkRepositoryCountsOf(harness.bookmarkRepository, allBookmarks)
        every { harness.bookmarkRepository.syncProgress } returns MutableStateFlow(
            com.karakept.app.data.model.SyncProgress.Idle
        )
        coEvery {
            harness.bookmarkRepository.getBookmarksPaged(
                server = any(), status = any(), after = any(), limit = any(),
                sort = any(), listId = any()
            )
        } returns emptyList()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `parent list count excludes nested list when includeChildListBookmarks is disabled`() =
        runTest(testDispatcher) {
            every { harness.settingsRepository.allListSettings } returns flowOf(
                mapOf("list-a" to ListSettings(includeChildListBookmarks = false))
            )

            val model = harness.createModel()
            val job = launch { model.listCounts.collect {} }
            advanceUntilIdle()

            assertEquals(10, model.listCounts.value["list-a"])
            assertEquals(5, model.listCounts.value["list-b"])
            job.cancel()
        }

    @Test
    fun `parent list count includes nested list when includeChildListBookmarks is enabled`() =
        runTest(testDispatcher) {
            every { harness.settingsRepository.allListSettings } returns flowOf(
                mapOf("list-a" to ListSettings(includeChildListBookmarks = true))
            )

            val model = harness.createModel()
            val job = launch { model.listCounts.collect {} }
            advanceUntilIdle()

            assertEquals(15, model.listCounts.value["list-a"])
            assertEquals(5, model.listCounts.value["list-b"])
            job.cancel()
        }
}
