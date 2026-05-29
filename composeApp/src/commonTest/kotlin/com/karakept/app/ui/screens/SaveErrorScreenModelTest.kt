package com.karakept.app.ui.screens

import com.karakept.app.data.local.entity.BookmarkEntity
import com.karakept.app.data.repository.BookmarkRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class SaveErrorScreenModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private val bookmarkRepository = mockk<BookmarkRepository>()
    private lateinit var screenModel: SaveErrorScreenModel

    private val url = "https://example.com/article"

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        screenModel = SaveErrorScreenModel(bookmarkRepository)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun retry_success_emitsSuccessStateWithBookmarkId() = runTest(testDispatcher) {
        val bookmark = mockk<BookmarkEntity>()
        every { bookmark.localId } returns 42L
        coEvery { bookmarkRepository.createBookmark(url) } returns Result.success(bookmark)

        screenModel.retry(url)
        runCurrent()

        val state = screenModel.retryStates.value[url]
        assertIs<SaveRetryState.Success>(state)
        assertEquals(42L, state.bookmarkId)
    }

    @Test
    fun retry_failure_emitsFailedStateWithMessage() = runTest(testDispatcher) {
        coEvery { bookmarkRepository.createBookmark(url) } returns
            Result.failure(Exception("network down"))

        screenModel.retry(url)
        runCurrent()

        val state = screenModel.retryStates.value[url]
        assertIs<SaveRetryState.Failed>(state)
        assertEquals("network down", state.message)
    }

    @Test
    fun retry_setsRetryingStateBeforeCompletion() = runTest(testDispatcher) {
        val bookmark = mockk<BookmarkEntity>()
        every { bookmark.localId } returns 1L
        coEvery { bookmarkRepository.createBookmark(url) } returns Result.success(bookmark)

        screenModel.retry(url)
        // Coroutine launched on the test dispatcher hasn't run yet.
        assertIs<SaveRetryState.Retrying>(screenModel.retryStates.value[url])

        runCurrent()
        assertIs<SaveRetryState.Success>(screenModel.retryStates.value[url])
    }

    @Test
    fun retry_tracksStatePerUrlIndependently() = runTest(testDispatcher) {
        val otherUrl = "https://example.com/other"
        val bookmark = mockk<BookmarkEntity>()
        every { bookmark.localId } returns 7L
        coEvery { bookmarkRepository.createBookmark(url) } returns Result.success(bookmark)
        coEvery { bookmarkRepository.createBookmark(otherUrl) } returns
            Result.failure(Exception("boom"))

        screenModel.retry(url)
        screenModel.retry(otherUrl)
        runCurrent()

        assertIs<SaveRetryState.Success>(screenModel.retryStates.value[url])
        assertIs<SaveRetryState.Failed>(screenModel.retryStates.value[otherUrl])
    }
}
