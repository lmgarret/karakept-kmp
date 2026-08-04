package com.karakept.app.data.repository

import com.karakept.app.data.local.dao.AssetDao
import com.karakept.app.data.local.dao.BookmarkDao
import com.karakept.app.data.local.dao.ListDao
import com.karakept.app.data.local.dao.PendingActionDao
import com.karakept.app.data.remote.RemoteDataSource
import com.karakept.app.utils.ImageCacheManager
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for the auto-sync throttle (#276): the startup sync must not re-fire every time
 * the main ScreenModel is recreated (e.g. returning from the reader).
 */
class AutoSyncThrottleTest {

    private fun repository() = BookmarkRepository(
        mockk<BookmarkDao>(relaxed = true),
        mockk<AssetDao>(relaxed = true),
        mockk<RemoteDataSource>(relaxed = true),
        mockk<BookmarkActionsRepository>(relaxed = true),
        mockk<SettingsRepository>(relaxed = true),
        mockk<ServerRepository>(relaxed = true),
        mockk<HighlightRepository>(relaxed = true),
        mockk<ImageCacheManager>(relaxed = true),
        mockk<ListDao>(relaxed = true)
    )

    @Test
    fun shouldAutoSync_trueBeforeAnySync() = runTest {
        val repo = repository()
        assertTrue(repo.shouldAutoSync("server-1"), "First sync should always be allowed")
    }

    @Test
    fun shouldAutoSync_falseImmediatelyAfterCompletion() = runTest {
        val repo = repository()
        repo.markAutoSyncCompleted("server-1")
        assertFalse(
            repo.shouldAutoSync("server-1"),
            "A sync within the throttle window must be suppressed"
        )
    }

    @Test
    fun shouldAutoSync_trueAfterWindowElapses() = runTest {
        val repo = repository()
        repo.markAutoSyncCompleted("server-1")
        // Zero interval → the window has already elapsed
        assertTrue(
            repo.shouldAutoSync("server-1", minIntervalMs = 0L),
            "Sync should be allowed once the throttle window has elapsed"
        )
    }

    @Test
    fun shouldAutoSync_isPerServer() = runTest {
        val repo = repository()
        repo.markAutoSyncCompleted("server-1")
        assertTrue(
            repo.shouldAutoSync("server-2"),
            "Throttle for one server must not block another"
        )
    }
}
