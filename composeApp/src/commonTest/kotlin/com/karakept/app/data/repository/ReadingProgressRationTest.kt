package com.karakept.app.data.repository

import com.karakept.app.data.local.dao.AssetDao
import com.karakept.app.data.local.dao.BookmarkDao
import com.karakept.app.data.local.dao.ListDao
import com.karakept.app.data.remote.RemoteDataSource
import com.karakept.app.utils.ImageCacheManager
import com.karakept.app.utils.TestAppDispatchers
import io.mockk.mockk
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The reading-progress ration is per server, so whichever pass asks first takes the slot and
 * every other pass in that burst goes without. Which pass wins decides where the counts stay
 * stale — and the one place they must not is the view the user is looking at.
 */
class ReadingProgressRationTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testAppDispatchers = TestAppDispatchers(testDispatcher)

    private fun repository() = BookmarkRepository(
        mockk<BookmarkDao>(relaxed = true),
        mockk<AssetDao>(relaxed = true),
        mockk<RemoteDataSource>(relaxed = true),
        mockk<BookmarkActionsRepository>(relaxed = true),
        mockk<SettingsRepository>(relaxed = true),
        mockk<ServerRepository>(relaxed = true),
        mockk<HighlightRepository>(relaxed = true),
        mockk<ImageCacheManager>(relaxed = true),
        mockk<ListDao>(relaxed = true),
        testAppDispatchers
    )

    @Test
    fun firstPassTakesTheSlot() = runTest(testDispatcher) {
        val repo = repository()
        assertTrue(repo.tryAcquireReadingProgressPull("server-1", force = false))
    }

    @Test
    fun aSecondPassInTheSameBurstIsRationed() = runTest(testDispatcher) {
        val repo = repository()
        repo.tryAcquireReadingProgressPull("server-1", force = false)
        assertFalse(
            repo.tryAcquireReadingProgressPull("server-1", force = false),
            "a fan-out of list syncs must not run a pass apiece"
        )
    }

    @Test
    fun theViewOnScreenIsNeverRationed() = runTest(testDispatcher) {
        // The list the user just opened would otherwise skip its own pull because another
        // list's pass took the slot a moment earlier, leaving the counts on screen stale
        // until scrolling fetched them row by row.
        val repo = repository()
        repo.tryAcquireReadingProgressPull("server-1", force = false)
        assertTrue(repo.tryAcquireReadingProgressPull("server-1", force = true))
    }

    @Test
    fun theRationIsPerServer() = runTest(testDispatcher) {
        val repo = repository()
        repo.tryAcquireReadingProgressPull("server-1", force = false)
        assertTrue(repo.tryAcquireReadingProgressPull("server-2", force = false))
    }

    @Test
    fun forcingStillMovesTheWindowForEveryoneElse() = runTest(testDispatcher) {
        // A forced pass has just covered the rotation, so the passes behind it still wait.
        val repo = repository()
        repo.tryAcquireReadingProgressPull("server-1", force = true)
        assertFalse(repo.tryAcquireReadingProgressPull("server-1", force = false))
    }
}
