package com.karakept.app.domain

import com.karakept.app.data.local.entity.BookmarkEntity
import com.karakept.app.data.model.FilterConfig
import com.karakept.app.data.model.FilterStatus
import com.karakept.app.data.model.SortOption
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit tests for [BookmarkFilterUtils].
 *
 * All tests use simple [BookmarkEntity] stubs created by [makeBookmark].
 */
class BookmarkFilterUtilsTest {

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private var idCounter = 1L

    private fun makeBookmark(
        title: String = "Title",
        url: String = "https://example.com",
        tags: String = "",
        listIds: String = "",
        isStarred: Boolean = false,
        isArchived: Boolean = false,
        isRead: Boolean = false,
        createdAt: Long = idCounter,
        readingTimeMinutes: Int = 0,
        description: String? = null
    ) = BookmarkEntity(
        localId = idCounter,
        remoteId = "remote-${idCounter}",
        serverId = "server-1",
        title = title,
        url = url,
        description = description,
        imageUrl = null,
        bannerImageAssetId = null,
        screenshotAssetId = null,
        tags = tags,
        listIds = listIds,
        isStarred = isStarred,
        isArchived = isArchived,
        isRead = isRead,
        createdAt = createdAt,
        readingTimeMinutes = readingTimeMinutes,
        content = null
    )

    // -------------------------------------------------------------------------
    // applyClientSideFilters — tag filtering
    // -------------------------------------------------------------------------

    @Test
    fun tagFilter_emptyFilter_returnsAll() {
        val bookmarks = listOf(
            makeBookmark(tags = "kotlin"),
            makeBookmark(tags = "android")
        )
        val result = BookmarkFilterUtils.applyClientSideFilters(bookmarks, FilterConfig())
        assertEquals(2, result.size)
    }

    @Test
    fun tagFilter_singleTag_onlyMatchingReturned() {
        val matching = makeBookmark(tags = "kotlin,android")
        val notMatching = makeBookmark(tags = "ios,swift")
        val result = BookmarkFilterUtils.applyClientSideFilters(
            listOf(matching, notMatching),
            FilterConfig(tags = listOf("kotlin"))
        )
        assertEquals(listOf(matching), result)
    }

    @Test
    fun tagFilter_multipleTagsUseOrLogic() {
        val matchesFirst = makeBookmark(tags = "kotlin")
        val matchesSecond = makeBookmark(tags = "swift")
        val matchesNeither = makeBookmark(tags = "java")
        val result = BookmarkFilterUtils.applyClientSideFilters(
            listOf(matchesFirst, matchesSecond, matchesNeither),
            FilterConfig(tags = listOf("kotlin", "swift"))
        )
        assertEquals(listOf(matchesFirst, matchesSecond), result)
    }

    @Test
    fun tagFilter_handlesWhitespaceInStoredTags() {
        val bookmark = makeBookmark(tags = " kotlin , android ")
        val result = BookmarkFilterUtils.applyClientSideFilters(
            listOf(bookmark),
            FilterConfig(tags = listOf("kotlin"))
        )
        assertEquals(listOf(bookmark), result)
    }

    // -------------------------------------------------------------------------
    // applyClientSideFilters — list filtering
    // -------------------------------------------------------------------------

    @Test
    fun listFilter_singleList_onlyMatchingReturned() {
        val inList = makeBookmark(listIds = "list-1")
        val notInList = makeBookmark(listIds = "list-2")
        val result = BookmarkFilterUtils.applyClientSideFilters(
            listOf(inList, notInList),
            FilterConfig(lists = listOf("list-1"))
        )
        assertEquals(listOf(inList), result)
    }

    @Test
    fun listFilter_multipleListsUseOrLogic() {
        val inFirst = makeBookmark(listIds = "list-a")
        val inSecond = makeBookmark(listIds = "list-b")
        val inNeither = makeBookmark(listIds = "list-c")
        val result = BookmarkFilterUtils.applyClientSideFilters(
            listOf(inFirst, inSecond, inNeither),
            FilterConfig(lists = listOf("list-a", "list-b"))
        )
        assertEquals(listOf(inFirst, inSecond), result)
    }

    @Test
    fun listFilter_bookmarkInMultipleLists_matchesIfAnyListed() {
        val bookmark = makeBookmark(listIds = "list-1,list-2,list-3")
        val result = BookmarkFilterUtils.applyClientSideFilters(
            listOf(bookmark),
            FilterConfig(lists = listOf("list-2"))
        )
        assertEquals(listOf(bookmark), result)
    }

    @Test
    fun listFilter_skippedWhenSkipListFilterTrue() {
        val shouldBeFiltered = makeBookmark(listIds = "list-x")
        val result = BookmarkFilterUtils.applyClientSideFilters(
            listOf(shouldBeFiltered),
            FilterConfig(lists = listOf("list-y")),
            skipListFilter = true
        )
        assertEquals(listOf(shouldBeFiltered), result)
    }

    @Test
    fun listFilter_handlesEmptyListIds() {
        val noList = makeBookmark(listIds = "")
        val result = BookmarkFilterUtils.applyClientSideFilters(
            listOf(noList),
            FilterConfig(lists = listOf("list-1"))
        )
        assertTrue(result.isEmpty())
    }

    // -------------------------------------------------------------------------
    // applyClientSideFilters — combined tag + list filtering (regression tests)
    //
    // These tests guard the crash that occurred when FilterConfig had both
    // non-empty `tags` and `lists` simultaneously (e.g. the user selected a
    // list from the drawer AND then added a tag via the filter panel).
    // -------------------------------------------------------------------------

    @Test
    fun combinedFilter_tagAndList_onlyMatchingBothAreReturned() {
        val matchesBoth    = makeBookmark(tags = "kotlin", listIds = "list-1")
        val matchesTagOnly = makeBookmark(tags = "kotlin", listIds = "list-2")
        val matchesListOnly = makeBookmark(tags = "swift",  listIds = "list-1")
        val matchesNeither = makeBookmark(tags = "swift",  listIds = "list-2")

        val result = BookmarkFilterUtils.applyClientSideFilters(
            listOf(matchesBoth, matchesTagOnly, matchesListOnly, matchesNeither),
            FilterConfig(tags = listOf("kotlin"), lists = listOf("list-1"))
        )
        assertEquals(listOf(matchesBoth), result)
    }

    @Test
    fun combinedFilter_tagAndList_multipleTagsAndLists_usesOrLogicWithinEach() {
        val matchesBoth      = makeBookmark(tags = "kotlin", listIds = "list-a")
        val matchesOtherTag  = makeBookmark(tags = "swift",  listIds = "list-a")
        val matchesOtherList = makeBookmark(tags = "kotlin", listIds = "list-b")
        val matchesNeither   = makeBookmark(tags = "java",   listIds = "list-c")

        val result = BookmarkFilterUtils.applyClientSideFilters(
            listOf(matchesBoth, matchesOtherTag, matchesOtherList, matchesNeither),
            FilterConfig(tags = listOf("kotlin", "swift"), lists = listOf("list-a", "list-b"))
        )
        // All three that match at least one tag AND at least one list should be included.
        assertEquals(listOf(matchesBoth, matchesOtherTag, matchesOtherList), result)
    }

    @Test
    fun combinedFilter_skipListFilter_onlyTagFilterApplied() {
        // Simulates the case where the list was already filtered at the DB level.
        // Both bookmarks are in "list-1" (DB already filtered), but only the one
        // with the matching tag should survive.
        val matchingTag    = makeBookmark(tags = "kotlin", listIds = "list-1")
        val nonMatchingTag = makeBookmark(tags = "swift",  listIds = "list-1")

        val result = BookmarkFilterUtils.applyClientSideFilters(
            listOf(matchingTag, nonMatchingTag),
            FilterConfig(tags = listOf("kotlin"), lists = listOf("list-1")),
            skipListFilter = true
        )
        assertEquals(listOf(matchingTag), result)
    }

    @Test
    fun combinedFilter_noBookmarkMatchesBoth_returnsEmpty() {
        val tagOnlyMatch  = makeBookmark(tags = "kotlin", listIds = "list-2")
        val listOnlyMatch = makeBookmark(tags = "swift",  listIds = "list-1")

        val result = BookmarkFilterUtils.applyClientSideFilters(
            listOf(tagOnlyMatch, listOnlyMatch),
            FilterConfig(tags = listOf("kotlin"), lists = listOf("list-1"))
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun combinedFilter_emptyBookmarkList_returnsEmpty() {
        val result = BookmarkFilterUtils.applyClientSideFilters(
            emptyList(),
            FilterConfig(tags = listOf("kotlin"), lists = listOf("list-1"))
        )
        assertTrue(result.isEmpty())
    }

    // -------------------------------------------------------------------------
    // applySearchFilter — combined tag + list filtering (regression tests)
    // -------------------------------------------------------------------------

    @Test
    fun searchFilter_withTagAndListFilter_onlyMatchingAllConditionsReturned() {
        val matchesAll    = makeBookmark(tags = "kotlin", listIds = "list-1", title = "Guide")
        val wrongTag      = makeBookmark(tags = "swift",  listIds = "list-1", title = "Guide")
        val wrongList     = makeBookmark(tags = "kotlin", listIds = "list-2", title = "Guide")
        val wrongQuery    = makeBookmark(tags = "kotlin", listIds = "list-1", title = "Unrelated")

        val result = BookmarkFilterUtils.applySearchFilter(
            listOf(matchesAll, wrongTag, wrongList, wrongQuery),
            FilterConfig(tags = listOf("kotlin"), lists = listOf("list-1")),
            "guide"
        )
        assertEquals(listOf(matchesAll), result)
    }

    @Test
    fun searchFilter_withTagAndListFilter_emptyQuery_appliesTagAndListFiltersOnly() {
        val matchesBoth    = makeBookmark(tags = "kotlin", listIds = "list-1")
        val matchesTagOnly = makeBookmark(tags = "kotlin", listIds = "list-2")
        val matchesListOnly = makeBookmark(tags = "swift", listIds = "list-1")

        val result = BookmarkFilterUtils.applySearchFilter(
            listOf(matchesBoth, matchesTagOnly, matchesListOnly),
            FilterConfig(tags = listOf("kotlin"), lists = listOf("list-1")),
            ""
        )
        assertEquals(listOf(matchesBoth), result)
    }

    // -------------------------------------------------------------------------
    // applySorting
    // -------------------------------------------------------------------------

    @Test
    fun sort_newest_descendingCreatedAt() {
        val old = makeBookmark(title = "Old", createdAt = 100)
        val middle = makeBookmark(title = "Middle", createdAt = 200)
        val newest = makeBookmark(title = "New", createdAt = 300)
        val result = BookmarkFilterUtils.applySorting(listOf(old, newest, middle), SortOption.NEWEST)
        assertEquals(listOf(newest, middle, old), result)
    }

    @Test
    fun sort_oldest_ascendingCreatedAt() {
        val old = makeBookmark(title = "Old", createdAt = 100)
        val middle = makeBookmark(title = "Middle", createdAt = 200)
        val newest = makeBookmark(title = "New", createdAt = 300)
        val result = BookmarkFilterUtils.applySorting(listOf(newest, old, middle), SortOption.OLDEST)
        assertEquals(listOf(old, middle, newest), result)
    }

    @Test
    fun sort_titleAz_caseInsensitiveAscending() {
        val a = makeBookmark(title = "Zebra")
        val b = makeBookmark(title = "apple")
        val c = makeBookmark(title = "Mango")
        val result = BookmarkFilterUtils.applySorting(listOf(a, b, c), SortOption.TITLE_AZ)
        assertEquals(listOf("apple", "Mango", "Zebra"), result.map { it.title })
    }

    @Test
    fun sort_titleZa_caseInsensitiveDescending() {
        val a = makeBookmark(title = "Zebra")
        val b = makeBookmark(title = "apple")
        val c = makeBookmark(title = "Mango")
        val result = BookmarkFilterUtils.applySorting(listOf(a, b, c), SortOption.TITLE_ZA)
        assertEquals(listOf("Zebra", "Mango", "apple"), result.map { it.title })
    }

    @Test
    fun sort_readingTimeShort_ascendingTime() {
        val fast = makeBookmark(readingTimeMinutes = 1)
        val slow = makeBookmark(readingTimeMinutes = 10)
        val result = BookmarkFilterUtils.applySorting(listOf(slow, fast), SortOption.READING_TIME_SHORT)
        assertEquals(listOf(fast, slow), result)
    }

    @Test
    fun sort_readingTimeLong_descendingTime() {
        val fast = makeBookmark(readingTimeMinutes = 1)
        val slow = makeBookmark(readingTimeMinutes = 10)
        val result = BookmarkFilterUtils.applySorting(listOf(fast, slow), SortOption.READING_TIME_LONG)
        assertEquals(listOf(slow, fast), result)
    }

    // -------------------------------------------------------------------------
    // applySearchFilter
    // -------------------------------------------------------------------------

    @Test
    fun searchFilter_matchesTitle() {
        val match = makeBookmark(title = "Kotlin Coroutines Guide")
        val noMatch = makeBookmark(title = "Swift Tutorial")
        val result = BookmarkFilterUtils.applySearchFilter(
            listOf(match, noMatch), FilterConfig(), "kotlin"
        )
        assertEquals(listOf(match), result)
    }

    @Test
    fun searchFilter_matchesUrl() {
        val match = makeBookmark(url = "https://kotlinlang.org/docs")
        val noMatch = makeBookmark(url = "https://swift.org")
        val result = BookmarkFilterUtils.applySearchFilter(
            listOf(match, noMatch), FilterConfig(), "kotlinlang"
        )
        assertEquals(listOf(match), result)
    }

    @Test
    fun searchFilter_matchesDescription() {
        val match = makeBookmark(description = "A guide to coroutines")
        val noMatch = makeBookmark(description = "iOS development tips")
        val result = BookmarkFilterUtils.applySearchFilter(
            listOf(match, noMatch), FilterConfig(), "coroutines"
        )
        assertEquals(listOf(match), result)
    }

    @Test
    fun searchFilter_caseInsensitive() {
        val bookmark = makeBookmark(title = "KOTLIN COROUTINES")
        val result = BookmarkFilterUtils.applySearchFilter(
            listOf(bookmark), FilterConfig(), "kotlin"
        )
        assertEquals(listOf(bookmark), result)
    }

    @Test
    fun searchFilter_statusFavorites_onlyStarred() {
        val starred = makeBookmark(isStarred = true, title = "Favorite")
        val notStarred = makeBookmark(isStarred = false, title = "Favorite not starred")
        val result = BookmarkFilterUtils.applySearchFilter(
            listOf(starred, notStarred),
            FilterConfig(status = FilterStatus.FAVORITES),
            "favorite"
        )
        assertEquals(listOf(starred), result)
    }

    @Test
    fun searchFilter_statusAll_excludesArchived() {
        val normal = makeBookmark(isArchived = false, title = "Active")
        val archived = makeBookmark(isArchived = true, title = "Active but archived")
        val result = BookmarkFilterUtils.applySearchFilter(
            listOf(normal, archived),
            FilterConfig(status = FilterStatus.ALL),
            "active"
        )
        assertEquals(listOf(normal), result)
    }

    @Test
    fun searchFilter_statusArchived_onlyArchived() {
        val normal = makeBookmark(isArchived = false, title = "Active")
        val archived = makeBookmark(isArchived = true, title = "Active archived")
        val result = BookmarkFilterUtils.applySearchFilter(
            listOf(normal, archived),
            FilterConfig(status = FilterStatus.ARCHIVED),
            "active"
        )
        assertEquals(listOf(archived), result)
    }

    @Test
    fun searchFilter_statusOffline_excludesArchived() {
        // In search mode allBookmarks strips content, so OFFLINE falls back to non-archived behaviour.
        val normal = makeBookmark(isArchived = false, title = "Active offline")
        val archived = makeBookmark(isArchived = true, title = "Active offline archived")
        val result = BookmarkFilterUtils.applySearchFilter(
            listOf(normal, archived),
            FilterConfig(status = FilterStatus.OFFLINE),
            "active"
        )
        assertEquals(listOf(normal), result)
    }

    @Test
    fun searchFilter_statusOffline_withQuery_matchesTitle() {
        val match = makeBookmark(isArchived = false, title = "Offline Kotlin Article")
        val noMatch = makeBookmark(isArchived = false, title = "Online Swift Article")
        val result = BookmarkFilterUtils.applySearchFilter(
            listOf(match, noMatch),
            FilterConfig(status = FilterStatus.OFFLINE),
            "kotlin"
        )
        assertEquals(listOf(match), result)
    }

    @Test
    fun searchFilter_emptyQuery_returnsAll() {
        val bookmarks = listOf(makeBookmark(), makeBookmark(), makeBookmark())
        val result = BookmarkFilterUtils.applySearchFilter(bookmarks, FilterConfig(), "")
        assertEquals(3, result.size)
    }

    @Test
    fun searchFilter_noMatches_returnsEmpty() {
        val bookmarks = listOf(makeBookmark(title = "Kotlin"), makeBookmark(title = "Swift"))
        val result = BookmarkFilterUtils.applySearchFilter(bookmarks, FilterConfig(), "python")
        assertTrue(result.isEmpty())
    }
}
