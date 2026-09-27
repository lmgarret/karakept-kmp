package com.karakept.app.domain

import com.karakept.app.domain.OfflineRetention.DAY_MILLIS
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OfflineRetentionTest {

    private val now = 100 * DAY_MILLIS

    private fun retired(
        isRead: Boolean = true,
        isArchived: Boolean = false,
        since: Long? = now - 8 * DAY_MILLIS,
        days: Int = 7
    ) = OfflineRetention.isRetired(isRead, isArchived, since, days, now)

    @Test
    fun readOrArchivedPastTheWindowIsRetired() {
        assertTrue(retired())
        assertTrue(retired(isRead = false, isArchived = true))
    }

    @Test
    fun insideTheWindowIsNotRetired() {
        assertFalse(retired(since = now - 7 * DAY_MILLIS))
        assertFalse(retired(since = now - DAY_MILLIS))
    }

    @Test
    fun retentionOffNeverRetires() {
        assertFalse(retired(days = 0))
    }

    @Test
    fun aStaleStampOnAnUnreadBookmarkDoesNotRetireIt() {
        // The stamp is only cleared by the next cleanup; the flags are what is current.
        assertFalse(retired(isRead = false, isArchived = false))
    }

    @Test
    fun anUnstampedBookmarkIsNotRetired() {
        assertFalse(retired(since = null))
    }
}
