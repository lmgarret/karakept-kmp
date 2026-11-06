package com.karakept.app.ui.screens.main

import com.karakept.app.data.model.ListSettings
import com.karakept.app.data.model.Server
import com.karakept.app.domain.action.TagFilterRequests
import io.mockk.coEvery
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
import kotlin.test.assertEquals

/**
 * The reader hands a tapped tag back to the bookmark list through [TagFilterRequests].
 *
 * It used to reach for the list's `MainScreenModel` directly, which built a *second* model —
 * outside any `ViewModelStore`, so `onCleared()` never ran and every repository collector it
 * started in `init` kept refreshing the window for the rest of the process. One leaked per
 * bookmark opened, which is what the #333 logs caught: seventeen models answering one sync.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TagFilterRequestTest {
    private val testDispatcher = StandardTestDispatcher()
    private val harness = MainScreenModelHarness(testDispatcher)

    private lateinit var tagFilterRequests: TagFilterRequests

    private val fakeServer = Server(
        id = "server-1",
        url = "https://example.com",
        apiKey = "test-key",
        label = "Test"
    )

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        tagFilterRequests = TagFilterRequests()

        every { harness.serverRepository.servers } returns flowOf(listOf(fakeServer))
        every { harness.settingsRepository.getListSettings(any()) } returns flowOf(ListSettings())
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
    fun requestFromAnotherScreen_appliesTheTagFilterToTheListOnScreen() = runTest(testDispatcher) {
        val model = harness.createModel(tagFilterRequests)
        advanceUntilIdle()

        tagFilterRequests.request("kotlin", sourceBookmarkId = 42L)
        advanceUntilIdle()

        assertEquals(listOf("kotlin"), model.currentFilter.value.tags)
        // The source bookmark travels with the request so the list can scroll back to it.
        assertEquals(42L, model.consumeTagFilterSource())
    }

    @Test
    fun noRequest_leavesTheFilterAlone() = runTest(testDispatcher) {
        val model = harness.createModel(tagFilterRequests)
        advanceUntilIdle()

        assertEquals(emptyList(), model.currentFilter.value.tags)
    }
}
