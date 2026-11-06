package com.karakept.app.utils

import com.karakept.app.data.model.DateDisplayMode
import kotlin.time.Clock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit tests for [formatBookmarkDate] and [formatAbsolute].
 */
class DateUtilsTest {

    @Test
    fun formatAbsolute_knownTimestamp_returnsFormattedDate() {
        // 1704067200000L = 2024-01-01 00:00:00 UTC
        val result = formatAbsolute(1704067200000L)
        // The result depends on the system timezone, but should match yyyy-MM-dd pattern
        assertTrue(result.matches(Regex("\\d{4}-\\d{2}-\\d{2}")),
            "Expected date format yyyy-MM-dd, got: $result")
    }

    @Test
    fun formatBookmarkDate_absoluteMode_delegatesToFormatAbsolute() {
        val epochMillis = 1704067200000L
        val result = formatBookmarkDate(epochMillis, DateDisplayMode.ABSOLUTE)
        assertTrue(result.matches(Regex("\\d{4}-\\d{2}-\\d{2}")),
            "ABSOLUTE mode should return yyyy-MM-dd format, got: $result")
    }

    @Test
    fun formatBookmarkDate_elapsedMode_returnsRelativeString() {
        val oneMinuteAgo = Clock.System.now().toEpochMilliseconds() - 60_000
        val result = formatBookmarkDate(oneMinuteAgo, DateDisplayMode.ELAPSED)
        assertEquals("1m ago", result, "1 minute ago should show '1m ago'")
    }

    @Test
    fun formatBookmarkDate_elapsedMode_justNow() {
        val fiveSecondsAgo = Clock.System.now().toEpochMilliseconds() - 5_000
        val result = formatBookmarkDate(fiveSecondsAgo, DateDisplayMode.ELAPSED)
        assertEquals("just now", result, "5 seconds ago should show 'just now'")
    }

    @Test
    fun formatBookmarkDate_elapsedMode_hoursAgo() {
        val twoHoursAgo = Clock.System.now().toEpochMilliseconds() - 2 * 3_600_000
        val result = formatBookmarkDate(twoHoursAgo, DateDisplayMode.ELAPSED)
        assertTrue(result.matches(Regex("\\d+h ago")),
            "~2 hours ago should show 'Nh ago', got: $result")
    }

    @Test
    fun formatBookmarkDate_elapsedMode_daysAgo() {
        val threeDaysAgo = Clock.System.now().toEpochMilliseconds() - 3 * 86_400_000
        val result = formatBookmarkDate(threeDaysAgo, DateDisplayMode.ELAPSED)
        assertTrue(result.matches(Regex("\\d+d ago")),
            "~3 days ago should show 'Nd ago', got: $result")
    }

    @Test
    fun formatBookmarkDate_elapsedMode_monthsAgo_noOverflow() {
        // 30 * 86_400_000 = 2,592,000,000 which overflows Int.MAX_VALUE (2,147,483,647)
        // Using it as an Int divisor produces a negative result — this test guards against regression.
        val twoMonthsAgo = Clock.System.now().toEpochMilliseconds() - 60 * 86_400_000L
        val result = formatBookmarkDate(twoMonthsAgo, DateDisplayMode.ELAPSED)
        assertTrue(result.matches(Regex("\\d+mo ago")),
            "~2 months ago should show positive 'Nmo ago', got: $result")
        assertTrue(!result.startsWith("-"),
            "Result must not be negative, got: $result")
    }
}
