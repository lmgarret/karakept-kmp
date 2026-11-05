package com.karakept.app.ui.screens.main

import com.karakept.app.testing.testBookmark
import com.karakept.api.model.KarakeepList
import com.karakept.app.data.model.Server
import com.karakept.app.data.model.SwipeAction
import io.mockk.coVerify
import io.mockk.every
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

/**
 * Regression tests for LIST-02: Smart lists do not update after quick actions that
 * change a bookmark's list membership.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class List02RegressionTest {
    private val testDispatcher = StandardTestDispatcher()
    private val harness = MainScreenModelHarness(testDispatcher)

    private val fakeServer = Server(
        id = "server-1",
        url = "https://example.com",
        apiKey = "test-key",
        label = "Test"
    )

    private val smartList1 = KarakeepList(id = "smart-1", name = "Unread", type = KarakeepList.Type.SMART)
    private val smartList2 = KarakeepList(id = "smart-2", name = "Recent", type = KarakeepList.Type.SMART)
    private val manualList = KarakeepList(id = "manual-1", name = "Read Later", type = KarakeepList.Type.MANUAL)

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        every { harness.serverRepository.servers } returns flowOf(listOf(fakeServer))
        every { harness.settingsRepository.offlineMode } returns flowOf(false)
        every { harness.listRepository.lists } returns MutableStateFlow(listOf(smartList1, smartList2, manualList))
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `moveBookmarkToList calls moveToList on actions repository`() = runTest(testDispatcher) {
        val bookmark = testBookmark(1L)
        val model = harness.createModel()
        advanceUntilIdle()

        model.moveBookmarkToList(bookmark, "manual-1")
        advanceUntilIdle()

        coVerify { harness.bookmarkActionsRepository.moveToList(bookmark.remoteId, bookmark.serverId, "manual-1", any()) }
    }

    @Test
    fun `removeBookmarkFromList calls removeFromList on actions repository`() = runTest(testDispatcher) {
        val bookmark = testBookmark(2L, listIds = "manual-1")
        val model = harness.createModel()
        advanceUntilIdle()

        model.removeBookmarkFromList(bookmark, "manual-1")
        advanceUntilIdle()

        coVerify { harness.bookmarkActionsRepository.removeFromList(bookmark.remoteId, bookmark.serverId, "manual-1") }
    }

    @Test
    fun `moveBookmarkToList works when no SMART lists exist`() = runTest(testDispatcher) {
        every { harness.listRepository.lists } returns MutableStateFlow(listOf(manualList))

        val bookmark = testBookmark(3L)
        val model = harness.createModel()
        advanceUntilIdle()

        model.moveBookmarkToList(bookmark, "manual-1")
        advanceUntilIdle()

        coVerify { harness.bookmarkActionsRepository.moveToList(bookmark.remoteId, bookmark.serverId, "manual-1", any()) }
    }

    @Test
    fun `executeScrollAction with ADD_TO_LIST delegates to moveBookmarkToList`() = runTest(testDispatcher) {
        val bookmark = testBookmark(4L)
        val config = com.karakept.app.data.model.CustomSwipeActionConfig(
            id = "cfg-1",
            type = com.karakept.app.data.model.CustomSwipeActionType.ADD_TO_LIST,
            listId = "manual-1"
        )
        val model = harness.createModel()
        advanceUntilIdle()

        model.executeScrollAction(bookmark, SwipeAction.ADD_TO_LIST, config)
        advanceUntilIdle()

        coVerify { harness.bookmarkActionsRepository.moveToList(bookmark.remoteId, bookmark.serverId, "manual-1", any()) }
    }
}
