package com.karakept.app.data.repository

import com.karakept.app.data.model.ListSyncStatus
import com.karakept.app.data.model.SYNC_KEY_FAVORITES
import com.karakept.app.data.model.SyncKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class SyncKeyTrackerTest {

    @Test
    fun syncKeyIsHeldUntilReleased() = runTest {
        // Arrange
        val tracker = SyncKeyTracker()

        // Act / Assert
        assertTrue(tracker.tryAcquireKey("list-1"))
        assertFalse(tracker.tryAcquireKey("list-1"))
        tracker.releaseKey("list-1")
        assertTrue(tracker.tryAcquireKey("list-1"))
    }

    @Test
    fun distinctKeysDoNotBlockEachOther() = runTest {
        // Arrange
        val tracker = SyncKeyTracker()

        // Act / Assert
        assertTrue(tracker.tryAcquireKey(null))
        assertTrue(tracker.tryAcquireKey(SYNC_KEY_FAVORITES))
    }

    @Test
    fun enrichmentKeyIsIndependentOfSyncKey() = runTest {
        // Arrange
        val tracker = SyncKeyTracker()
        assertTrue(tracker.tryAcquireKey("list-1"))

        // Act / Assert
        assertTrue(tracker.tryAcquireEnrichmentKey("list-1"))
        assertFalse(tracker.tryAcquireEnrichmentKey("list-1"))
        tracker.releaseKey("list-1")
        assertFalse(tracker.tryAcquireEnrichmentKey("list-1"))
        tracker.releaseEnrichmentKey("list-1")
        assertTrue(tracker.tryAcquireEnrichmentKey("list-1"))
        assertTrue(tracker.tryAcquireKey("list-1"))
    }

    @Test
    fun setKeyStatusIsReflectedInProgressMap() {
        // Arrange
        val tracker = SyncKeyTracker()

        // Act
        tracker.setKeyStatus("list-1", ListSyncStatus.FetchingMetadata(3))
        tracker.setKeyStatus(null, ListSyncStatus.FetchingContent(1, 2))

        // Assert
        assertEquals(
            mapOf(
                "list-1" to ListSyncStatus.FetchingMetadata(3),
                null to ListSyncStatus.FetchingContent(1, 2)
            ),
            tracker.perKeyProgress.value
        )
    }

    @Test
    fun idleStatusRemovesTheKey() {
        // Arrange
        val tracker = SyncKeyTracker()
        tracker.setKeyStatus("list-1", ListSyncStatus.FetchingMetadata())

        // Act
        tracker.setKeyStatus("list-1", ListSyncStatus.Idle)

        // Assert
        assertEquals(emptyMap<SyncKey, ListSyncStatus>(), tracker.perKeyProgress.value)
    }
}
