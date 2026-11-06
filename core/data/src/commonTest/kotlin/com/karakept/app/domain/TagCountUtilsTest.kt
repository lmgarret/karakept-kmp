package com.karakept.app.domain

import com.karakept.app.data.local.projection.TagGroup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tag counts are read from rows the database has grouped on the whole `tags` column, so one
 * bucket can carry several tags and stands for several bookmarks at once.
 */
class TagCountUtilsTest {

    private fun groups(vararg pairs: Pair<String, Int>) =
        pairs.map { (tags, count) -> TagGroup(tags, count) }

    @Test
    fun `a bucket's count goes to every tag in it`() {
        val counts = TagCountUtils.countsByTag(
            groups("kotlin,android" to 5, "kotlin" to 3, "rust" to 2)
        )

        assertEquals(8, counts["kotlin"], "counted from both buckets it appears in")
        assertEquals(5, counts["android"])
        assertEquals(2, counts["rust"])
    }

    @Test
    fun `whitespace around a tag is not part of it`() {
        val counts = TagCountUtils.countsByTag(groups("kotlin, android " to 1))

        assertEquals(mapOf("kotlin" to 1, "android" to 1), counts)
    }

    @Test
    fun `an empty tag set contributes nothing`() {
        assertEquals(emptyMap(), TagCountUtils.countsByTag(groups("" to 9)))
        assertEquals(mapOf("a" to 1), TagCountUtils.countsByTag(groups("a,,," to 1)))
    }

    @Test
    fun `every tag in use is listed once, in the app's text order`() {
        val all = TagCountUtils.allTags(groups("Beta,alpha" to 1, "alpha,Gamma" to 1))

        assertEquals(listOf("alpha", "Beta", "Gamma"), all, "case does not separate the letters")
    }

    @Test
    fun `the top tags are the most used, and ties break on the name`() {
        val top = TagCountUtils.topTagsWithCounts(
            groups("rust" to 10, "kotlin" to 3, "android" to 3),
            activeTags = emptyList(),
            limit = 3
        )

        assertEquals(listOf("rust (10)", "android (3)", "kotlin (3)"), top)
    }

    @Test
    fun `a tag the filter is on is shown even when it is not popular`() {
        // A filter must always show what it is filtering on, however rare the tag.
        val top = TagCountUtils.topTagsWithCounts(
            groups("rust" to 10, "kotlin" to 8, "obscure" to 1),
            activeTags = listOf("obscure"),
            limit = 2
        )

        assertEquals(listOf("rust (10)", "kotlin (8)", "obscure (1)"), top)
    }

    @Test
    fun `an active tag no row carries is still shown, without a count`() {
        val top = TagCountUtils.topTagsWithCounts(
            groups("rust" to 1),
            activeTags = listOf("gone"),
            limit = 5
        )

        assertTrue("gone" in top, "the filter is on it, so it has to be visible")
    }

    @Test
    fun `nothing tagged means nothing to show`() {
        assertEquals(emptyList(), TagCountUtils.allTags(emptyList()))
        assertEquals(emptyList(), TagCountUtils.topTagsWithCounts(emptyList(), emptyList()))
    }
}
