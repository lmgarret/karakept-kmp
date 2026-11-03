package com.karakept.app.domain

import com.karakept.app.domain.OfflineRetention.DAY_MILLIS
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OfflineRetentionTest {

    private val now = 100 * DAY_MILLIS

    private fun retired(
        isRead: Boolean = true,
        isArchived: Boolean = false,
        since: Long? = now - 8 * DAY_MILLIS,
        days: Int? = 7
    ) = OfflineRetention.isRetired(isRead, isArchived, since, days, now)

    @Test
    fun readOrArchivedPastTheWindowIsRetired() {
        assertTrue(retired())
        assertTrue(retired(isRead = false, isArchived = true))
        assertTrue(retired(since = now - 7 * DAY_MILLIS), "the boundary itself counts")
    }

    @Test
    fun insideTheWindowIsNotRetired() {
        assertFalse(retired(since = now - 6 * DAY_MILLIS))
        assertFalse(retired(since = now - DAY_MILLIS))
    }

    @Test
    fun retentionOffNeverRetires() {
        assertFalse(retired(days = null))
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

    @Test
    fun sliderStopsRunDailyThenWeeklyThenMonthly() {
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7, 14, 21, 30, 60, 90), OfflineRetention.SLIDER_STOPS)
    }

    @Test
    fun aTypedValueParksTheSliderOnTheNearestStop() {
        fun stopFor(days: Int) = OfflineRetention.SLIDER_STOPS[OfflineRetention.nearestStopIndex(days)]
        assertEquals(14, stopFor(14))
        assertEquals(14, stopFor(12))
        assertEquals(7, stopFor(10), "ties go to the lower stop")
        assertEquals(30, stopFor(40))
        assertEquals(90, stopFor(500))
    }
}
