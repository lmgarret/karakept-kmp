package com.karakept.app.domain

import com.karakept.app.data.model.DefaultListType
import com.karakept.app.data.model.FilterConfig
import com.karakept.app.data.model.FilterStatus
import com.karakept.app.data.repository.SettingsRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Unit tests for [DefaultFilterResolver].
 *
 * The [DefaultFilterResolver.buildFilter] companion method is pure and tested
 * without any mocking. The full [DefaultFilterResolver.resolve] method is
 * tested against a mocked [SettingsRepository] to verify the atomic combine
 * read behaves correctly.
 */
class DefaultFilterResolverTest {

    // -------------------------------------------------------------------------
    // buildFilter — pure function tests
    // -------------------------------------------------------------------------

    @Test
    fun buildFilter_allBookmarks_returnsEmptyFilter() {
        assertEquals(FilterConfig(), DefaultFilterResolver.buildFilter(DefaultListType.ALL_BOOKMARKS, null))
    }

    @Test
    fun buildFilter_allBookmarks_ignoresListId() {
        // A listId is irrelevant for ALL_BOOKMARKS
        assertEquals(FilterConfig(), DefaultFilterResolver.buildFilter(DefaultListType.ALL_BOOKMARKS, "list-99"))
    }

    @Test
    fun buildFilter_favorites_returnsFavoritesFilter() {
        assertEquals(
            FilterConfig(status = FilterStatus.FAVORITES),
            DefaultFilterResolver.buildFilter(DefaultListType.FAVORITES, null)
        )
    }

    @Test
    fun buildFilter_archived_returnsArchivedFilter() {
        assertEquals(
            FilterConfig(status = FilterStatus.ARCHIVED),
            DefaultFilterResolver.buildFilter(DefaultListType.ARCHIVED, null)
        )
    }

    @Test
    fun buildFilter_specificList_withId_returnsListFilter() {
        assertEquals(
            FilterConfig(lists = listOf("my-list")),
            DefaultFilterResolver.buildFilter(DefaultListType.SPECIFIC_LIST, "my-list")
        )
    }

    @Test
    fun buildFilter_specificList_withNullId_fallsBackToAllBookmarks() {
        assertEquals(
            FilterConfig(),
            DefaultFilterResolver.buildFilter(DefaultListType.SPECIFIC_LIST, null)
        )
    }

    @Test
    fun buildFilter_specificList_wrapsIdInSingleElementList() {
        val result = DefaultFilterResolver.buildFilter(DefaultListType.SPECIFIC_LIST, "abc")
        assertEquals(listOf("abc"), result.lists)
    }

    // -------------------------------------------------------------------------
    // resolve() — integration with mocked SettingsRepository
    // -------------------------------------------------------------------------

    private fun makeRepo(type: DefaultListType, id: String?): SettingsRepository =
        mockk<SettingsRepository>().also {
            every { it.defaultListType } returns flowOf(type)
            every { it.defaultListId } returns flowOf(id)
        }

    @Test
    fun resolve_allBookmarks() = runTest {
        val resolver = DefaultFilterResolver(makeRepo(DefaultListType.ALL_BOOKMARKS, null))
        assertEquals(FilterConfig(), resolver.resolve())
    }

    @Test
    fun resolve_favorites() = runTest {
        val resolver = DefaultFilterResolver(makeRepo(DefaultListType.FAVORITES, null))
        assertEquals(FilterConfig(status = FilterStatus.FAVORITES), resolver.resolve())
    }

    @Test
    fun resolve_archived() = runTest {
        val resolver = DefaultFilterResolver(makeRepo(DefaultListType.ARCHIVED, null))
        assertEquals(FilterConfig(status = FilterStatus.ARCHIVED), resolver.resolve())
    }

    @Test
    fun resolve_specificList_withId() = runTest {
        val resolver = DefaultFilterResolver(makeRepo(DefaultListType.SPECIFIC_LIST, "list-123"))
        assertEquals(FilterConfig(lists = listOf("list-123")), resolver.resolve())
    }

    @Test
    fun resolve_specificList_withNullId_fallsBack() = runTest {
        val resolver = DefaultFilterResolver(makeRepo(DefaultListType.SPECIFIC_LIST, null))
        assertEquals(FilterConfig(), resolver.resolve())
    }

    @Test
    fun resolve_returnsDefaultFilterStatus() = runTest {
        val resolver = DefaultFilterResolver(makeRepo(DefaultListType.ALL_BOOKMARKS, null))
        assertEquals(FilterStatus.ALL, resolver.resolve().status)
    }

    @Test
    fun resolve_specificList_filterHasNoTags() = runTest {
        val resolver = DefaultFilterResolver(makeRepo(DefaultListType.SPECIFIC_LIST, "x"))
        assertEquals(emptyList(), resolver.resolve().tags)
    }
}
