package com.karakept.app.ui.screens.main

import io.mockk.coEvery
import io.mockk.coVerify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

/** Overlapping stretches reported while scrolling must not ask for the same row twice. */
@OptIn(ExperimentalCoroutinesApi::class)
class MainScreenModelProgressPrefetchTest {
    private val testDispatcher = StandardTestDispatcher()
    private val harness = MainScreenModelHarness(testDispatcher)

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun rowsAlreadyOnTheWireAreNotAskedForAgain() = runTest(testDispatcher) {
        val firstPull = CompletableDeferred<Unit>()
        coEvery {
            harness.bookmarkRepository.pullReadingProgressForVisible(any(), listOf("a", "b"))
        } coAnswers { firstPull.await() }
        val model = harness.createModel()
        advanceUntilIdle()

        model.onBookmarksVisible(listOf("a", "b"))
        advanceUntilIdle()
        model.onBookmarksVisible(listOf("b", "c"))
        advanceUntilIdle()

        coVerify(exactly = 1) {
            harness.bookmarkRepository.pullReadingProgressForVisible("server-1", listOf("c"))
        }

        // Once the answer lands the rows are the repository's to judge fresh or stale again.
        firstPull.complete(Unit)
        advanceUntilIdle()
        model.onBookmarksVisible(listOf("a", "b"))
        advanceUntilIdle()

        coVerify(exactly = 2) {
            harness.bookmarkRepository.pullReadingProgressForVisible("server-1", listOf("a", "b"))
        }
    }
}
