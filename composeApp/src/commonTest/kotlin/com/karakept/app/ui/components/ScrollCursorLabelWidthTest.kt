package com.karakept.app.ui.components

import com.karakept.app.data.local.entity.BookmarkEntity
import com.karakept.app.data.model.SortOption
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * The bubble reserves the widest label its sort can produce, so it stays one size for a whole
 * drag rather than resizing on every row the thumb passes.
 *
 * That reservation is a list of candidate strings, and it is only as good as its agreement with
 * the formatter: a label longer than anything in it puts the resizing back. The two are checked
 * against each other here rather than measured, because a measurement needs a composition and
 * what can drift is which strings exist, not how wide a font draws them.
 */
@OptIn(ExperimentalTime::class)
class ScrollCursorLabelWidthTest {

    private val now = Clock.System.now().toEpochMilliseconds()

    private fun bookmark(createdAt: Long = now, title: String = "T", readingTime: Int = 0) =
        BookmarkEntity(
            localId = 1,
            remoteId = "r1",
            serverId = "s1",
            url = "https://example.com",
            title = title,
            content = null,
            imageUrl = null,
            bannerImageAssetId = null,
            screenshotAssetId = null,
            description = null,
            createdAt = createdAt,
            isArchived = false,
            isStarred = false,
            readingTimeMinutes = readingTime
        )

    private fun reserved(sort: SortOption) =
        scrollCursorLabelWidths(sort).maxOf { it.length }

    @Test
    fun `no relative date is longer than the width reserved for one`() {
        val day = 86_400_000L
        // Every unit the formatter switches between, at both ends of its range.
        val ages = listOf(
            0L, 30_000L, 59_000L,
            60_000L, 59 * 60_000L,
            3_600_000L, 23 * 3_600_000L,
            day, 6 * day,
            7 * day, 29 * day,
            30 * day, 364 * day,
            365 * day, 40L * 365 * day
        )
        for (sort in listOf(SortOption.NEWEST, SortOption.OLDEST)) {
            val cap = reserved(sort)
            for (age in ages) {
                val label = scrollCursorLabel(bookmark(createdAt = now - age), sort)
                assertTrue(
                    label.length <= cap,
                    "$sort: '$label' (${label.length}) exceeds the $cap reserved for it"
                )
            }
        }
    }

    @Test
    fun `a title label is the single letter the reservation assumes`() {
        for (sort in listOf(SortOption.TITLE_AZ, SortOption.TITLE_ZA)) {
            for (title in listOf("Wide", "mixed case", "0 leading digit", "Ünicode")) {
                val label = scrollCursorLabel(bookmark(title = title), sort)
                assertTrue(label.length == 1, "$sort: '$label' is not one character")
            }
        }
    }

    @Test
    fun `no reading time is longer than the width reserved for one`() {
        for (sort in listOf(SortOption.READING_TIME_SHORT, SortOption.READING_TIME_LONG)) {
            val cap = reserved(sort)
            for (minutes in listOf(0, 1, 9, 10, 99, 100, 999)) {
                val label = scrollCursorLabel(bookmark(readingTime = minutes), sort)
                assertTrue(
                    label.length <= cap,
                    "$sort: '$label' (${label.length}) exceeds the $cap reserved for it"
                )
            }
        }
    }

    @Test
    fun `every sort reserves something`() {
        // A sort added without a reservation would size the bubble to its text again.
        for (sort in SortOption.entries) {
            assertTrue(
                scrollCursorLabelWidths(sort).any { it.isNotEmpty() },
                "$sort reserves no label width"
            )
        }
    }
}
