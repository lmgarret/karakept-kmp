package com.karakept.app.ui.screens.settings

import com.karakept.app.data.model.SyncStrategy
import com.karakept.app.data.repository.OfflineCacheCleanupResult
import com.karakept.app.data.repository.OfflineCacheRepository
import com.karakept.app.data.repository.OfflineStorageUsage
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
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
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class OfflineStorageScreenModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private val repository = mockk<OfflineCacheRepository>()
    private val before = OfflineStorageUsage(bookmarkCount = 3, bodyBytes = 1000, fileBytes = 5000)
    private val after = OfflineStorageUsage(bookmarkCount = 0, bodyBytes = 0, fileBytes = 0)

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        coEvery { repository.storageUsage() } returns before
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun measuresUsageOnOpen() = runTest(testDispatcher) {
        val model = OfflineStorageScreenModel(repository)
        advanceUntilIdle()

        assertEquals(before, model.usage.value)
        assertFalse(model.isWorking.value)
    }

    @Test
    fun clearAllReportsWhatItFreedAndRemeasures() = runTest(testDispatcher) {
        val model = OfflineStorageScreenModel(repository)
        advanceUntilIdle()
        coEvery { repository.clearAll() } returns OfflineCacheCleanupResult(3, 4, 2048)
        coEvery { repository.storageUsage() } returns after

        model.clearAll()
        assertTrue(model.isWorking.value)
        advanceUntilIdle()

        assertEquals("Cleared 3 offline copies, freed 2.0 KB of files", model.lastResult.value)
        assertEquals(after, model.usage.value)
        assertFalse(model.isWorking.value)
    }

    @Test
    fun cleanUpWithNothingToDoSaysSo() = runTest(testDispatcher) {
        val model = OfflineStorageScreenModel(repository)
        advanceUntilIdle()
        coEvery { repository.cleanUp() } returns OfflineCacheCleanupResult()

        model.cleanUpNow()
        advanceUntilIdle()

        assertEquals("Nothing to clean up", model.lastResult.value)
    }

    @Test
    fun aSecondTapWhileWorkingIsIgnored() = runTest(testDispatcher) {
        val model = OfflineStorageScreenModel(repository)
        advanceUntilIdle()
        coEvery { repository.clearAll() } returns OfflineCacheCleanupResult()

        model.clearAll()
        model.clearAll()
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.clearAll() }
    }

    @Test
    fun aFailureIsReportedAndReleasesTheButtons() = runTest(testDispatcher) {
        val model = OfflineStorageScreenModel(repository)
        advanceUntilIdle()
        coEvery { repository.cleanUp() } throws Exception("disk gone")

        model.cleanUpNow()
        advanceUntilIdle()

        assertEquals("Something went wrong: disk gone", model.lastResult.value)
        assertFalse(model.isWorking.value)
    }

    @Test
    fun summaryAndWarningText() {
        assertEquals("5.9 KB · 3 bookmarks available offline", offlineStorageSummary(before))
        assertEquals("0 B · 1 bookmark available offline", offlineStorageSummary(after.copy(bookmarkCount = 1)))
        assertTrue(clearOfflineCacheWarning(SyncStrategy.ALL).contains("downloads them all again"))
        assertTrue(clearOfflineCacheWarning(SyncStrategy.NEVER).contains("Lists set to sync offline"))
    }
}
