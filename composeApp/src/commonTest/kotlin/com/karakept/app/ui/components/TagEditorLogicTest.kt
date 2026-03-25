package com.karakept.app.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TagEditorLogicTest {

    // --- filterTagSuggestions ---

    @Test
    fun filterTagSuggestions_blankInput_returnsEmptyList() {
        val result = filterTagSuggestions(
            availableTags = listOf("Kotlin", "Java"),
            searchInput = "   ",
            currentTags = emptyList()
        )
        assertEquals(emptyList(), result)
    }

    @Test
    fun filterTagSuggestions_prefixMatch_caseInsensitive() {
        val result = filterTagSuggestions(
            availableTags = listOf("Kotlin", "kotlin-kmp", "Java", "JavaScript"),
            searchInput = "kot",
            currentTags = emptyList()
        )
        assertEquals(listOf("Kotlin", "kotlin-kmp"), result)
    }

    @Test
    fun filterTagSuggestions_excludesAlreadySelectedTags() {
        val result = filterTagSuggestions(
            availableTags = listOf("Kotlin", "kotlin-kmp", "Java"),
            searchInput = "kot",
            currentTags = listOf("Kotlin")
        )
        assertEquals(listOf("kotlin-kmp"), result)
    }

    @Test
    fun filterTagSuggestions_sortedAlphabeticallyByLowercase() {
        val result = filterTagSuggestions(
            availableTags = listOf("Zebra-tag", "alpha-tag", "Beta-tag"),
            searchInput = "tag",
            currentTags = emptyList()
        )
        assertEquals(listOf("alpha-tag", "Beta-tag", "Zebra-tag"), result)
    }

    @Test
    fun filterTagSuggestions_max8Results() {
        val tags = (1..15).map { "tag-$it" }
        val result = filterTagSuggestions(
            availableTags = tags,
            searchInput = "tag",
            currentTags = emptyList()
        )
        assertEquals(8, result.size)
    }

    @Test
    fun filterTagSuggestions_exactMatchIncludedWhenNotSelected() {
        val result = filterTagSuggestions(
            availableTags = listOf("Kotlin", "Java"),
            searchInput = "Kotlin",
            currentTags = emptyList()
        )
        assertTrue(result.contains("Kotlin"))
    }

    @Test
    fun filterTagSuggestions_substringMatch() {
        val result = filterTagSuggestions(
            availableTags = listOf("my-kotlin-project", "Kotlin", "java"),
            searchInput = "kotlin",
            currentTags = emptyList()
        )
        assertEquals(listOf("Kotlin", "my-kotlin-project"), result)
    }

    // --- canAddTag ---

    @Test
    fun canAddTag_canCreateNew_nonBlankNotInCurrent_returnsTrue() {
        val result = canAddTag(
            searchInput = "new-tag",
            currentTags = listOf("existing"),
            availableTags = emptyList(),
            canCreateNew = true
        )
        assertTrue(result)
    }

    @Test
    fun canAddTag_cannotCreateNew_matchesAvailable_returnsTrue() {
        val result = canAddTag(
            searchInput = "kotlin",
            currentTags = emptyList(),
            availableTags = listOf("Kotlin", "Java"),
            canCreateNew = false
        )
        assertTrue(result)
    }

    @Test
    fun canAddTag_cannotCreateNew_notInAvailable_returnsFalse() {
        val result = canAddTag(
            searchInput = "unknown",
            currentTags = emptyList(),
            availableTags = listOf("Kotlin", "Java"),
            canCreateNew = false
        )
        assertFalse(result)
    }

    @Test
    fun canAddTag_alreadyInCurrentTags_returnsFalse() {
        val result = canAddTag(
            searchInput = "existing",
            currentTags = listOf("existing"),
            availableTags = listOf("existing"),
            canCreateNew = true
        )
        assertFalse(result)
    }

    @Test
    fun canAddTag_blankInput_returnsFalse() {
        val result = canAddTag(
            searchInput = "   ",
            currentTags = emptyList(),
            availableTags = emptyList(),
            canCreateNew = true
        )
        assertFalse(result)
    }

    @Test
    fun canAddTag_trimsWhitespace() {
        val result = canAddTag(
            searchInput = "  new-tag  ",
            currentTags = listOf("other"),
            availableTags = emptyList(),
            canCreateNew = true
        )
        assertTrue(result)
    }

    // --- findExactTagMatch ---

    @Test
    fun findExactTagMatch_caseInsensitiveMatch() {
        val result = findExactTagMatch("kotlin", listOf("Kotlin", "Java"))
        assertEquals("Kotlin", result)
    }

    @Test
    fun findExactTagMatch_noMatch_returnsNull() {
        val result = findExactTagMatch("unknown", listOf("Kotlin", "Java"))
        assertNull(result)
    }

    @Test
    fun findExactTagMatch_trimsInput() {
        val result = findExactTagMatch("  Kotlin  ", listOf("Kotlin", "Java"))
        assertEquals("Kotlin", result)
    }
}
