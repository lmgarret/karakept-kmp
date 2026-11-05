package com.karakept.app.ui.screens.main

import com.karakept.app.data.model.DefaultListType
import com.karakept.app.data.model.FilterConfig
import com.karakept.app.data.model.FilterStatus
import com.karakept.app.ui.screens.main.MainScreenModelHarness.Companion.bookmark
import io.mockk.every
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import kotlin.test.assertTrue

/**
 * Startup is one explicit sequence in [MainScreenModel]'s init: resolve the default filter, wait
 * for a server, then load. Independent coroutines used to race there and load the list twice, or
 * load it once with the unfiltered view before the default filter arrived. This pins both.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainScreenModelStartupTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var harness: MainScreenModelHarness

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        harness = MainScreenModelHarness(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `startup switches to a view exactly once`() = runTest(testDispatcher) {
        harness.publish((1L..20L).map(::bookmark))
        val model = harness.createModel()
        val job = launch { model.bookmarkWindow.collect {} }
        advanceUntilIdle()

        assertEquals(1, model.bookmarkListVersion.value, "one view switch, not one per racing coroutine")
        job.cancel()
    }

    @Test
    fun `the first page is read with the default filter, never the unfiltered view`() = runTest(testDispatcher) {
        every { harness.settingsRepository.defaultListType } returns flowOf(DefaultListType.FAVORITES)
        harness.publish((1L..20L).map(::bookmark))
        val model = harness.createModel()
        val job = launch { model.bookmarkWindow.collect {} }
        advanceUntilIdle()

        assertEquals(FilterConfig(status = FilterStatus.FAVORITES), model.currentFilter.value)
        assertTrue(harness.readFilters.isNotEmpty())
        assertTrue(
            harness.readFilters.all { it.status == FilterStatus.FAVORITES },
            "read for ${harness.readFilters.map { it.status }.distinct()}"
        )
        assertEquals(1, model.bookmarkListVersion.value)
        job.cancel()
    }
}
