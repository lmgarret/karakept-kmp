package com.karakept.app.ui.screens.main

import io.mockk.every
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
 * Regression tests for READER-01: scroll position persistence in MainScreenModel.
 *
 * Guards against regressions of the scroll position restore bug fixed in Phase 03
 * (commit 5d4d2aa). savedScrollIndex and savedScrollOffset are hoisted in
 * MainScreenModel (Koin singleton) and must survive Nav3 push/pop.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ScrollPositionRegressionTest {
    private val testDispatcher = StandardTestDispatcher()
    private val harness = MainScreenModelHarness(testDispatcher)

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        every { harness.serverRepository.servers } returns flowOf(emptyList())
        every { harness.settingsRepository.offlineMode } returns flowOf(false)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `savedScrollIndex and savedScrollOffset default to zero`() = runTest(testDispatcher) {
        val model = harness.createModel()
        advanceUntilIdle()

        assertEquals(0, model.savedScrollIndex)
        assertEquals(0, model.savedScrollOffset)
    }

    @Test
    fun `saveScrollPosition persists index and offset`() = runTest(testDispatcher) {
        val model = harness.createModel()
        advanceUntilIdle()

        model.saveScrollPosition(5, 100)

        assertEquals(5, model.savedScrollIndex)
        assertEquals(100, model.savedScrollOffset)
    }

    @Test
    fun `saveScrollPosition overwrites previous values`() = runTest(testDispatcher) {
        val model = harness.createModel()
        advanceUntilIdle()

        model.saveScrollPosition(5, 100)
        model.saveScrollPosition(10, 200)

        assertEquals(10, model.savedScrollIndex)
        assertEquals(200, model.savedScrollOffset)
    }
}
