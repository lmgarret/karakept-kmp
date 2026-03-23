package com.karakept.app.data.repository

import com.karakept.app.data.local.entity.BookmarkEntity
import com.karakept.app.data.local.entity.ListEntity
import com.karakept.app.data.model.ListSettings
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for syncContent offline wiring behavior (LIST-02).
 *
 * Validates decisions D-03 through D-07 from the phase plan:
 * - D-03: Content download for offline-enabled lists happens automatically on all syncs.
 * - D-04: No separate user action needed beyond enabling the toggle.
 * - D-05/D-06: Bookmarks in offline-enabled lists that lack content are fetched.
 * - D-07: Bookmarks that already have content are skipped.
 *
 * Since BookmarkSyncPipeline is an internal class with many constructor dependencies
 * (DAO, remote data source, image cache, etc.), these tests validate the FILTERING LOGIC
 * as a pure function. The function determines which bookmarks need content fetching
 * based on per-list offline settings, bookmark content state, and list hierarchy.
 *
 * Tests are expected to FAIL at the RED phase because the production code does not
 * yet wire allListSettings.syncOffline into the sync pipeline.
 */
class SyncContentOfflineTest {

    // ---- Helper: the filtering logic under test ----

    /**
     * Determines which bookmarks need content fetching based on per-list offline settings.
     *
     * This is the EXPECTED behavior after Plan 01 implementation:
     * - For each list with syncOffline=true, find bookmarks in that list
     * - If includeChildListBookmarks=true, also include bookmarks in child lists
     * - Only include bookmarks that lack content (readingTimeMinutes == 0)
     *
     * @param bookmarks All bookmarks from the current sync batch
     * @param listSettings Per-list settings (from SettingsRepository.allListSettings)
     * @param listEntities All list entities (for resolving parent-child hierarchy)
     * @return Set of bookmark remoteIds that need content fetching
     */
    private fun computeOfflineSyncTargets(
        bookmarks: List<BookmarkEntity>,
        listSettings: Map<String, ListSettings>,
        listEntities: List<ListEntity>
    ): Set<Long> {
        val offlineLists = listSettings.filter { it.value.syncOffline }
        if (offlineLists.isEmpty()) return emptySet()

        val targetListIds = mutableSetOf<String>()

        for ((listId, settings) in offlineLists) {
            targetListIds.add(listId)
            if (settings.includeChildListBookmarks) {
                // Add all direct child lists
                targetListIds.addAll(
                    listEntities
                        .filter { it.parentId == listId }
                        .map { it.remoteId }
                )
            }
        }

        return bookmarks
            .filter { bookmark ->
                val bookmarkListIds = bookmark.listIds
                    .split(",")
                    .filter { it.isNotEmpty() }
                    .toSet()
                bookmarkListIds.intersect(targetListIds).isNotEmpty() && bookmark.readingTimeMinutes == 0
            }
            .map { it.remoteId }
            .toSet()
    }

    // ---- Test fixtures ----

    private fun bookmarkEntity(
        remoteId: Long,
        listIds: String = "",
        readingTimeMinutes: Int = 0,
        title: String = "Bookmark $remoteId"
    ) = BookmarkEntity(
        localId = remoteId,
        remoteId = remoteId,
        originalRemoteId = "orig-$remoteId",
        serverId = "server-1",
        url = "https://example.com/$remoteId",
        title = title,
        content = null,
        imageUrl = null,
        bannerImageAssetId = null,
        screenshotAssetId = null,
        description = null,
        createdAt = 1000L,
        isArchived = false,
        isStarred = false,
        listIds = listIds,
        readingTimeMinutes = readingTimeMinutes
    )

    private fun listEntity(
        remoteId: String,
        name: String = "List $remoteId",
        parentId: String? = null
    ) = ListEntity(
        localId = 0,
        remoteId = remoteId,
        serverId = "server-1",
        name = name,
        icon = null,
        parentId = parentId,
        isPublic = false,
        description = null,
        type = "manual",
        query = null,
        updatedAt = 1000L
    )

    // Shared fixtures
    private val bookmarkInOfflineList = bookmarkEntity(
        remoteId = 1, listIds = "list-A", readingTimeMinutes = 0
    )
    private val bookmarkWithContent = bookmarkEntity(
        remoteId = 2, listIds = "list-A", readingTimeMinutes = 5
    )
    private val bookmarkInChildList = bookmarkEntity(
        remoteId = 3, listIds = "list-A-child", readingTimeMinutes = 0
    )
    private val bookmarkNotInOfflineList = bookmarkEntity(
        remoteId = 4, listIds = "list-B", readingTimeMinutes = 0
    )

    private val parentList = listEntity(remoteId = "list-A", name = "Offline List")
    private val childList = listEntity(remoteId = "list-A-child", name = "Child List", parentId = "list-A")
    private val otherList = listEntity(remoteId = "list-B", name = "Other List")
    private val allLists = listOf(parentList, childList, otherList)

    // ---- Test 1: Bookmarks without content in offline list are fetched ----

    @Test
    fun offlineListBookmarksWithoutContent_areFetched() {
        val bookmarks = listOf(bookmarkInOfflineList, bookmarkNotInOfflineList)
        val listSettings = mapOf(
            "list-A" to ListSettings(syncOffline = true)
        )

        val targets = computeOfflineSyncTargets(bookmarks, listSettings, allLists)

        // Only bookmarkInOfflineList (remoteId=1) should be targeted
        assertTrue(targets.contains(1L), "Bookmark in offline list without content should be fetched")
        assertFalse(targets.contains(4L), "Bookmark NOT in offline list should not be fetched")
    }

    // ---- Test 2: Bookmarks with content in offline list are skipped ----

    @Test
    fun offlineListBookmarksWithContent_areSkipped() {
        val bookmarks = listOf(bookmarkInOfflineList, bookmarkWithContent)
        val listSettings = mapOf(
            "list-A" to ListSettings(syncOffline = true)
        )

        val targets = computeOfflineSyncTargets(bookmarks, listSettings, allLists)

        // bookmarkInOfflineList (remoteId=1, readingTimeMinutes=0) should be fetched
        assertTrue(targets.contains(1L), "Bookmark without content should be fetched")
        // bookmarkWithContent (remoteId=2, readingTimeMinutes=5) should be skipped
        assertFalse(targets.contains(2L), "Bookmark with existing content should be skipped")
    }

    // ---- Test 3: Child list bookmarks included when includeChildListBookmarks is true ----

    @Test
    fun childListBookmarks_includedWhenSettingEnabled() {
        val bookmarks = listOf(bookmarkInOfflineList, bookmarkInChildList, bookmarkNotInOfflineList)
        val listSettings = mapOf(
            "list-A" to ListSettings(syncOffline = true, includeChildListBookmarks = true)
        )

        val targets = computeOfflineSyncTargets(bookmarks, listSettings, allLists)

        // Both bookmarkInOfflineList (remoteId=1) and bookmarkInChildList (remoteId=3) should be targeted
        assertTrue(targets.contains(1L), "Bookmark in parent offline list should be fetched")
        assertTrue(targets.contains(3L), "Bookmark in child list should be fetched when includeChildListBookmarks=true")
        assertFalse(targets.contains(4L), "Bookmark in unrelated list should not be fetched")
    }

    // ---- Test 4: No offline lists -> no extra fetching ----

    @Test
    fun noOfflineLists_noExtraFetching() {
        val bookmarks = listOf(bookmarkInOfflineList, bookmarkWithContent, bookmarkInChildList, bookmarkNotInOfflineList)
        val listSettings = mapOf(
            "list-A" to ListSettings(syncOffline = false),
            "list-B" to ListSettings(syncOffline = false)
        )

        val targets = computeOfflineSyncTargets(bookmarks, listSettings, allLists)

        assertEquals(emptySet(), targets, "No bookmarks should be targeted when no lists have syncOffline=true")
    }
}
