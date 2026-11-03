package com.karakept.app.data.model

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * Exhaustive combination tests for [FilterConfig].
 *
 * Covers all FilterStatus values, multi-list regression (previously crashing case),
 * multi-tag combinations, all SortOption values, serialization round-trip,
 * and equality contract.
 */
class FilterConfigTest {

    // -----------------------------------------------------------------------
    // Test 1: Default FilterConfig is valid
    // -----------------------------------------------------------------------

    @Test
    fun `default FilterConfig has expected defaults`() {
        val config = FilterConfig()
        assertEquals(FilterStatus.ALL, config.status)
        assertEquals(emptyList(), config.tags)
        assertEquals(emptyList(), config.lists)
        assertEquals(SortOption.NEWEST, config.sort)
    }

    // -----------------------------------------------------------------------
    // Test 2: Single list filter works for all FilterStatus values
    // -----------------------------------------------------------------------

    @Test
    fun `single list filter works for all FilterStatus values`() {
        for (status in FilterStatus.entries) {
            val config = FilterConfig(status = status, lists = listOf("list-1"))
            assertEquals(1, config.lists.size, "lists.size for status=$status")
            assertEquals("list-1", config.lists.first())
            assertEquals(status, config.status)
        }
    }

    // -----------------------------------------------------------------------
    // Test 3: Multi-list filter does not crash (regression test)
    // -----------------------------------------------------------------------

    @Test
    fun `multi-list filter does not crash for any FilterStatus`() {
        val multiLists = listOf("list-1", "list-2", "list-3")
        for (status in FilterStatus.entries) {
            val config = FilterConfig(status = status, lists = multiLists)
            assertEquals(3, config.lists.size, "lists.size for status=$status")
            assertEquals(multiLists, config.lists)
        }
    }

    // -----------------------------------------------------------------------
    // Test 4: Multiple tags filter works
    // -----------------------------------------------------------------------

    @Test
    fun `multiple tags filter works correctly`() {
        val tags = listOf("tag-a", "tag-b", "tag-c")
        val config = FilterConfig(tags = tags)
        assertEquals(3, config.tags.size)
        assertEquals(tags, config.tags)
    }

    // -----------------------------------------------------------------------
    // Test 5: All SortOption values produce valid FilterConfig
    // -----------------------------------------------------------------------

    @Test
    fun `all SortOption values produce valid FilterConfig`() {
        for (sort in SortOption.entries) {
            val config = FilterConfig(sort = sort)
            assertEquals(sort, config.sort, "sort option should round-trip for $sort")
        }
    }

    // -----------------------------------------------------------------------
    // Test 6: Serialization round-trip preserves all fields
    // -----------------------------------------------------------------------

    @Test
    fun `serialization round-trip preserves all fields`() {
        val original = FilterConfig(
            status = FilterStatus.FAVORITES,
            tags = listOf("kotlin", "kmp", "compose"),
            lists = listOf("list-abc", "list-xyz"),
            sort = SortOption.TITLE_AZ
        )
        val json = Json.encodeToString(original)
        val deserialized = Json.decodeFromString<FilterConfig>(json)
        assertEquals(original, deserialized)
    }

    @Test
    fun `serialization round-trip works for all FilterStatus values`() {
        for (status in FilterStatus.entries) {
            val original = FilterConfig(status = status)
            val json = Json.encodeToString(original)
            val deserialized = Json.decodeFromString<FilterConfig>(json)
            assertEquals(original, deserialized, "Round-trip failed for status=$status")
        }
    }

    @Test
    fun `serialization round-trip works for all SortOption values`() {
        for (sort in SortOption.entries) {
            val original = FilterConfig(sort = sort)
            val json = Json.encodeToString(original)
            val deserialized = Json.decodeFromString<FilterConfig>(json)
            assertEquals(original, deserialized, "Round-trip failed for sort=$sort")
        }
    }

    // -----------------------------------------------------------------------
    // Test 7: Equality contract
    // -----------------------------------------------------------------------

    @Test
    fun `identical FilterConfig instances are equal`() {
        val a = FilterConfig(
            status = FilterStatus.ARCHIVED,
            tags = listOf("tag-1"),
            lists = listOf("list-1"),
            sort = SortOption.OLDEST
        )
        val b = FilterConfig(
            status = FilterStatus.ARCHIVED,
            tags = listOf("tag-1"),
            lists = listOf("list-1"),
            sort = SortOption.OLDEST
        )
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `changing status produces inequality`() {
        val base = FilterConfig(status = FilterStatus.ALL)
        val changed = base.copy(status = FilterStatus.ARCHIVED)
        assertNotEquals(base, changed)
    }

    @Test
    fun `changing tags produces inequality`() {
        val base = FilterConfig(tags = listOf("a"))
        val changed = base.copy(tags = listOf("b"))
        assertNotEquals(base, changed)
    }

    @Test
    fun `changing lists produces inequality`() {
        val base = FilterConfig(lists = listOf("list-1"))
        val changed = base.copy(lists = listOf("list-2"))
        assertNotEquals(base, changed)
    }

    @Test
    fun `changing sort produces inequality`() {
        val base = FilterConfig(sort = SortOption.NEWEST)
        val changed = base.copy(sort = SortOption.OLDEST)
        assertNotEquals(base, changed)
    }

    // -----------------------------------------------------------------------
    // Multi-list + multi-tag combination matrix
    // -----------------------------------------------------------------------

    @Test
    fun `all status x list-cardinality x tag-cardinality combinations are valid`() {
        val listVariants = listOf(
            emptyList(),
            listOf("single-list"),
            listOf("list-a", "list-b", "list-c")
        )
        val tagVariants = listOf(
            emptyList(),
            listOf("single-tag"),
            listOf("tag-x", "tag-y", "tag-z")
        )
        for (status in FilterStatus.entries) {
            for (lists in listVariants) {
                for (tags in tagVariants) {
                    val config = FilterConfig(
                        status = status,
                        lists = lists,
                        tags = tags
                    )
                    assertEquals(status, config.status)
                    assertEquals(lists.size, config.lists.size)
                    assertEquals(tags.size, config.tags.size)
                }
            }
        }
    }
}
