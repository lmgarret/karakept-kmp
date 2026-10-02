package com.karakept.app.ui.screens.settings

import com.karakept.app.data.model.SyncStrategy
import com.karakept.app.data.repository.OfflineCacheCleanupResult
import com.karakept.app.data.repository.OfflineCacheRepository
import com.karakept.app.data.repository.OfflineCleanupEstimate
import com.karakept.app.data.repository.OfflineStorageUsage
import com.karakept.app.data.repository.SettingsRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
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
    private val settings = mockk<SettingsRepository>()
    private val retentionDays = MutableStateFlow<Int?>(null)
    private val capMb = MutableStateFlow<Int?>(null)
    private val before = OfflineStorageUsage(
        bookmarkCount = 3, articleBytes = 1000, imageBytes = 4000, fileBytes = 1000,
        appBytes = 9000, freeBytes = 1_000_000
    )
    private val after = before.copy(bookmarkCount = 0, articleBytes = 0, imageBytes = 0, fileBytes = 0)
    private val estimate = OfflineCleanupEstimate(retiredCopies = 2, retiredBytes = 3000)

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { settings.activeOfflineRetentionDays } returns retentionDays
        every { settings.activeOfflineStorageCapMb } returns capMb
        coEvery { repository.storageUsage() } returns before
        coEvery { repository.estimateCleanup() } returns estimate
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun model() = OfflineStorageScreenModel(repository, settings)

    @Test
    fun measuresUsageAndEstimateOnOpen() = runTest(testDispatcher) {
        val model = model()
        advanceUntilIdle()

        assertEquals(before, model.usage.value)
        assertEquals(estimate, model.estimate.value)
        assertFalse(model.isWorking.value)
        coVerify(exactly = 1) { repository.estimateCleanup() }
    }

    @Test
    fun aSettingChangeReestimatesOnceTypingSettles() = runTest(testDispatcher) {
        val model = model()
        advanceUntilIdle()

        retentionDays.value = 1
        runCurrent()
        retentionDays.value = 14
        runCurrent()
        advanceTimeBy(OfflineStorageScreenModel.ESTIMATE_DEBOUNCE_MILLIS - 1)
        coVerify(exactly = 1) { repository.estimateCleanup() }

        advanceUntilIdle()
        coVerify(exactly = 2) { repository.estimateCleanup() }
        assertEquals(estimate, model.estimate.value)
    }

    @Test
    fun clearAllReportsWhatItFreedAndRemeasures() = runTest(testDispatcher) {
        val model = model()
        advanceUntilIdle()
        coEvery { repository.clearAll() } returns OfflineCacheCleanupResult(3, 4, 2048)
        coEvery { repository.storageUsage() } returns after

        model.clearAll()
        assertTrue(model.isWorking.value)
        advanceUntilIdle()

        assertEquals("Cleared 3 offline copies, freed 2.0 KB", model.lastResult.value)
        assertEquals(after, model.usage.value)
        assertFalse(model.isWorking.value)
        coVerify(exactly = 2) { repository.estimateCleanup() }
    }

    @Test
    fun cleanUpWithNothingToDoSaysSo() = runTest(testDispatcher) {
        val model = model()
        advanceUntilIdle()
        coEvery { repository.cleanUp() } returns OfflineCacheCleanupResult()

        model.cleanUpNow()
        advanceUntilIdle()

        assertEquals("Nothing to clean up", model.lastResult.value)
    }

    @Test
    fun aSecondTapWhileWorkingIsIgnored() = runTest(testDispatcher) {
        val model = model()
        advanceUntilIdle()
        coEvery { repository.clearAll() } returns OfflineCacheCleanupResult()

        model.clearAll()
        model.clearAll()
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.clearAll() }
    }

    @Test
    fun aFailureIsReportedAndReleasesTheButtons() = runTest(testDispatcher) {
        val model = model()
        advanceUntilIdle()
        coEvery { repository.cleanUp() } throws Exception("disk gone")

        model.cleanUpNow()
        advanceUntilIdle()

        assertEquals("Something went wrong: disk gone", model.lastResult.value)
        assertFalse(model.isWorking.value)
    }

    @Test
    fun usageDerivesTheOfflineAndOtherParts() {
        assertEquals(6000, before.offlineBytes)
        assertEquals(3000, before.otherAppBytes)
    }

    @Test
    fun countAndWarningText() {
        assertEquals("3 bookmarks available offline", offlineBookmarkCount(3))
        assertEquals("1 bookmark available offline", offlineBookmarkCount(1))
        assertTrue(clearOfflineCacheWarning(SyncStrategy.ALL).contains("downloads them all again"))
        assertTrue(clearOfflineCacheWarning(SyncStrategy.NEVER).contains("Lists set to sync offline"))
    }
}
