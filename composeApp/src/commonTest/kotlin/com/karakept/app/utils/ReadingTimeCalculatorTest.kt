package com.karakept.app.utils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit tests for [ReadingTimeCalculator].
 */
class ReadingTimeCalculatorTest {

    @Test
    fun calculateReadingTime_nullContent_returnsZero() {
        assertEquals(0, ReadingTimeCalculator.calculateReadingTime(null))
    }

    @Test
    fun calculateReadingTime_emptyString_returnsZero() {
        assertEquals(0, ReadingTimeCalculator.calculateReadingTime(""))
    }

    @Test
    fun calculateReadingTime_blankString_returnsZero() {
        assertEquals(0, ReadingTimeCalculator.calculateReadingTime("   "))
    }

    @Test
    fun calculateReadingTime_singleWord_returnsOneMinute() {
        val result = ReadingTimeCalculator.calculateReadingTime("<p>hello</p>")
        assertEquals(1, result, "Single word should return minimum 1 minute")
    }

    @Test
    fun calculateReadingTime_250Words_returnsOneMinute() {
        val words = (1..250).joinToString(" ") { "word" }
        val html = "<p>$words</p>"
        val result = ReadingTimeCalculator.calculateReadingTime(html)
        assertEquals(1, result, "250 words at 250 WPM should be ~1 minute")
    }

    @Test
    fun calculateReadingTime_500Words_returnsTwoMinutes() {
        val words = (1..500).joinToString(" ") { "word" }
        val html = "<p>$words</p>"
        val result = ReadingTimeCalculator.calculateReadingTime(html)
        assertEquals(2, result, "500 words at 250 WPM should be ~2 minutes")
    }

    @Test
    fun calculateReadingTime_htmlWithImages_addsImageTime() {
        val words = (1..250).joinToString(" ") { "word" }
        val html = "<p>$words</p><img src='test.jpg'>"
        val result = ReadingTimeCalculator.calculateReadingTime(html)
        assertTrue(result > 1, "250 words + 1 image should exceed 1 minute due to image penalty")
    }

    @Test
    fun calculateReadingTime_onlyImages_returnsOneMinute() {
        val html = "<img src='a.jpg'><img src='b.jpg'><img src='c.jpg'>"
        val result = ReadingTimeCalculator.calculateReadingTime(html)
        assertTrue(result >= 1, "Images-only content should return at least 1 minute")
    }

    @Test
    fun calculateReadingTime_malformedHtml_doesNotThrow() {
        // Should handle gracefully -- return 0 or valid int, never throw
        val result = ReadingTimeCalculator.calculateReadingTime("<<<>>>not html at all<<<")
        assertTrue(result >= 0, "Malformed HTML should return 0 or valid int")
    }

    @Test
    fun formatReadingTime_positiveMinutes_returnsFormattedString() {
        assertEquals("5mn", ReadingTimeCalculator.formatReadingTime(5))
    }

    @Test
    fun formatReadingTime_zero_returnsEmptyString() {
        assertEquals("", ReadingTimeCalculator.formatReadingTime(0))
    }
}
