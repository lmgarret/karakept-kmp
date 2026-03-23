# Phase 06: Selection & Filtering - Research

**Researched:** 2026-03-23
**Domain:** Compose Multiplatform UI + Room DAO queries + pagination logic
**Confidence:** HIGH

## Summary

Phase 06 addresses three independent bugs in the Karakept KMP app: (1) select-all only selecting the first page of 20 bookmarks, (2) missing quick filter counters in the navigation drawer, and (3) missing pull-to-refresh on the Highlights screen. All three are well-scoped with clear decisions from the discuss phase.

The codebase is well-structured for these changes. The select-all fix requires a new DAO method and a change to `selectAll()` in `MainScreenModelBatch.kt`. The counter fix replicates the existing `listCounts` pattern in `MainScreenModel`. The pull-to-refresh uses `PullToRefreshBox` from Material3 (available in Compose Multiplatform 1.10.0, the version used by this project).

**Primary recommendation:** Implement the three fixes as independent tasks. All changes are local to existing files with well-established patterns to follow.

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- **D-01:** When `selectAll()` is called and `_hasMoreItems` is true, fetch all matching IDs from DB in one query without loading full entities into memory, add them to `_accumulatedBookmarks`, then select all. This avoids both the UX problem of silently auto-loading pages AND the correctness problem of selecting IDs not present in `_accumulatedBookmarks` (batch operations use `getSelectedBookmarks()` which reads from `_accumulatedBookmarks`).
- **D-02:** Requires a new DAO method -- e.g., `getBookmarkIdsPaged` or a full-scan variant that returns all IDs matching the current filter without the limit/offset constraint.
- **D-03:** When `_hasMoreItems` is false (all pages already loaded), current behavior is correct -- no change needed.
- **D-04:** The current filter (`_currentFilter`) must be applied to the DB query so only matching IDs are selected.
- **D-05:** All Bookmarks counter = non-archived bookmarks only.
- **D-06:** Favorites counter = starred AND non-archived bookmarks only.
- **D-07:** Archived counter = archived bookmarks (all archived, regardless of starred status).
- **D-08:** Highlights drawer item DOES show a count -- total highlights in the DB, same visual style.
- **D-09:** Counts are live/reactive -- derived from `allBookmarks` StateFlow and a new highlights count. Same `SharingStarted.WhileSubscribed(5000)` pattern.
- **D-10:** Counter displayed inline on drawer item, same visual style as existing list item counts (`bodySmall`, muted color, right-aligned).
- **D-11:** Pull-to-refresh triggers `syncHighlights()` -- the same method that auto-fires on screen open.
- **D-12:** Use Material3 `PullToRefreshBox` wrapping the `LazyColumn` in `HighlightsScreen`. The `isSyncing` state drives the refresh indicator.

### Claude's Discretion
- Exact DAO method signature for bulk-ID fetch (D-02)
- Whether to handle the filter-to-DAO translation for the ID query inline in `selectAll()` or via a helper function
- Counter display: whether to show "0" or hide the counter when count is zero (match existing list items behavior)

### Deferred Ideas (OUT OF SCOPE)
None -- discussion stayed within phase scope.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| FILT-01 | Select-all selects all entries in the list, not just the first page (#153) | New DAO methods for ID-only queries; modify `selectAll()` to fetch all matching IDs when `_hasMoreItems` is true, then add full entities to `_accumulatedBookmarks` |
| FILT-02 | Quick Filters display bookmark counters in the navigation drawer (#156) | Replicate `listCounts` pattern; add `count` param to `BuiltinDrawerItem`; add highlights count from `HighlightDao` |
| FILT-03 | User can pull-to-refresh on the Highlights view (#157) | Wrap `LazyColumn` in `PullToRefreshBox` using `isSyncing` state; call `syncHighlights()` on refresh |
</phase_requirements>

## Architecture Patterns

### FILT-01: Select-All Fix

**Current bug (line 76-78 of MainScreenModelBatch.kt):**
```kotlin
fun MainScreenModel.selectAll() {
    _selectedBookmarkIds.value = _accumulatedBookmarks.value.map { it.remoteId }.toSet()
}
```
This only selects IDs from `_accumulatedBookmarks` which only contains loaded pages. When `_hasMoreItems` is true, unseen bookmarks are missed.

**Fix strategy (from D-01):**
1. When `_hasMoreItems` is false -- current behavior is correct, no change.
2. When `_hasMoreItems` is true:
   a. Query DB for ALL matching bookmark entities (not just IDs -- because `getSelectedBookmarks()` reads from `_accumulatedBookmarks` and batch operations need full entities).
   b. Replace `_accumulatedBookmarks` with the full result.
   c. Set `_hasMoreItems` to false (all items are now loaded).
   d. Select all IDs.

**Critical insight from D-01:** The decision says "fetch all matching IDs from DB in one query without loading full entities into memory, add them to `_accumulatedBookmarks`". However, looking at `getSelectedBookmarks()` (line 80-83), it filters `_accumulatedBookmarks` by selected IDs and returns `List<BookmarkEntity>`. All batch operations (archive, delete, tag, etc.) call `getSelectedBookmarks()` and operate on full entities. Therefore, we need full `BookmarkEntity` objects in `_accumulatedBookmarks`, not just IDs. The DAO query should return entities (without content, as existing paged queries do with `'' as content`).

**DAO methods needed:**
The existing DAO has paged variants for each filter status. We need unpaged (no LIMIT/OFFSET) variants that return lightweight entities (no content). The existing `getBookmarksForServer` Flow returns ALL bookmarks but is a Flow, not a suspend function, and includes no filter. We need new suspend functions:

| Filter Status | New DAO Method | SQL WHERE clause |
|---------------|---------------|------------------|
| ALL | `getAllNotArchivedForServer(serverId)` | `isArchived = 0` |
| FAVORITES | `getAllFavoritesForServer(serverId)` | `isStarred = 1` |
| ARCHIVED | `getAllArchivedForServer(serverId)` | `isArchived = 1` |
| ALL_INCLUDING_ARCHIVED | `getAllBookmarksForServer(serverId)` | (none) |
| List filter | `getAllBookmarksForList(serverId, listId)` | list ID match |

All use the same column projection as existing paged queries (`'' as content` to avoid loading heavy content).

**Recommendation for discretion area (filter-to-DAO translation):** Add a helper method `getAllMatchingBookmarks(server, filter)` in `BookmarkRepository` (parallel to `getBookmarksPaged`) that calls the appropriate DAO method. Then `selectAll()` calls this + applies `BookmarkFilterUtils.applyClientSideFilters` for tag/multi-list filters.

### FILT-02: Quick Filter Counters

**Existing pattern (`listCounts` in MainScreenModel, lines 155-173):**
```kotlin
val listCounts: StateFlow<Map<String, Int>> = combine(
    selectedServer, lists, allBookmarks, settingsRepository.allListSettings
) { server, listItems, bookmarks, allSettings -> ... }
.stateIn(screenModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())
```

**New StateFlow needed:**
A `quickFilterCounts` StateFlow derived from `allBookmarks` that computes:
- `allCount` = bookmarks.count { !it.isArchived }
- `favoritesCount` = bookmarks.count { it.isStarred && !it.isArchived }
- `archivedCount` = bookmarks.count { it.isArchived }

For highlights count (D-08), a separate reactive source is needed because highlights are in a different table. Options:
1. Add a `getHighlightsCountForServer(serverId): Flow<Int>` to `HighlightDao` (a `@Query("SELECT COUNT(*)")` returning Flow).
2. Combine this Flow into the counter computation.

**BuiltinDrawerItem changes:**
Currently `BuiltinDrawerItem` has no `count` parameter. Add an optional `count: Int?` parameter and render it identically to `ListDrawerItem` (line 392-399):
```kotlin
count?.let {
    Text(
        text = it.toString(),
        style = MaterialTheme.typography.bodySmall,
        color = (if (selected) selectedTextColor else MaterialTheme.colorScheme.onSurfaceVariant)
            .copy(alpha = 0.6f)
    )
}
```

**Zero-count behavior (discretion):** The existing `ListDrawerItem` passes `count: Int?` -- when the value is `null`, no count is shown. When it is `0`, "0" is displayed. For quick filters, always pass the computed Int (never null), so "0" will display. This matches list item behavior -- lists with 0 bookmarks show "0".

**DrawerContent parameter changes:**
Add `quickFilterCounts` (or individual count params) and `highlightsCount` to `DrawerContent` signature. Thread through from `MainScreenDrawer` and `MainScreenExpandedLayout`.

### FILT-03: Pull-to-Refresh on Highlights

**PullToRefreshBox API (verified available in Compose Multiplatform 1.10.0):**
```kotlin
import androidx.compose.material3.pulltorefresh.PullToRefreshBox

PullToRefreshBox(
    isRefreshing = isSyncing,
    onRefresh = { screenModel.syncHighlights() }
) {
    LazyColumn(...) { ... }
}
```

**Current HighlightsScreen structure (lines 77-139):**
The `Scaffold` body has two branches: empty state and LazyColumn. The `PullToRefreshBox` should wrap the entire content area inside `Scaffold { paddingValues -> }` so that pull-to-refresh works in both states.

**Key detail:** `syncHighlights()` in `HighlightsScreenModel` (line 86-97) already sets `_isSyncing = true`, calls `highlightRepository.syncHighlights(server)`, then calls `loadInitialPage()`, and finally sets `_isSyncing = false`. This is the exact behavior needed for pull-to-refresh.

**ExperimentalMaterial3Api:** Check whether `PullToRefreshBox` requires `@OptIn(ExperimentalMaterial3Api::class)`. In Compose Multiplatform 1.10.0 (which maps to Material3 ~1.4.x+), `PullToRefreshBox` is stable and does not require the experimental opt-in. However, if it does in this version, the `HighlightsScreen` already uses `@OptIn(ExperimentalMaterial3Api::class)` on `Content()`.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Pull-to-refresh gesture | Custom drag detection / offset tracking | `PullToRefreshBox` from material3.pulltorefresh | Handles overscroll, indicator animation, threshold, nested scroll coordination |
| Reactive counting | Manual observer + mutableState bookkeeping | `combine()` + `stateIn()` on existing Flows | Already proven in `listCounts`; avoids timing bugs |
| Bulk entity loading | Loop calling `loadNextPage()` until exhausted | Single DAO query without LIMIT/OFFSET | O(1) queries vs O(n/pageSize) queries; avoids accumulating duplicate state |

## Common Pitfalls

### Pitfall 1: Forgetting client-side filters in selectAll
**What goes wrong:** DAO returns ALL non-archived bookmarks, but user is viewing a tag-filtered or multi-list view. Select-all selects bookmarks not visible in the list.
**Why it happens:** The DB query handles status filtering, but tag and multi-list filtering is done client-side by `BookmarkFilterUtils.applyClientSideFilters`.
**How to avoid:** After fetching all entities from DB, apply the same `applyClientSideFilters(allEntities, currentFilter, skipListFilter)` logic as `loadBookmarksPage` does.
**Warning signs:** Selected count is larger than visible item count.

### Pitfall 2: Not expanding list children for selectAll
**What goes wrong:** When `includeChildListBookmarks` is enabled for a list, selectAll fetches only the parent list's bookmarks, missing children.
**Why it happens:** `loadBookmarksPage` calls `expandListsWithChildren()` to expand filter lists; the new bulk query must do the same.
**How to avoid:** Call `expandListsWithChildren()` on the filter before querying.

### Pitfall 3: Thread safety when replacing _accumulatedBookmarks in selectAll
**What goes wrong:** Race condition if `loadNextPage()` is running concurrently with `selectAll()`.
**Why it happens:** `_accumulatedBookmarks` is a `MutableStateFlow` protected by `bookmarksMutex`. The `selectAll` function currently does NOT use the mutex.
**How to avoid:** Use `updateAccumulatedBookmarks { }` (which acquires the mutex) when modifying `_accumulatedBookmarks` in the new `selectAll()`.

### Pitfall 4: Highlights count needs a reactive Flow, not a one-shot query
**What goes wrong:** Highlights counter in drawer shows stale count after sync adds/removes highlights.
**Why it happens:** Using a `suspend fun getCount()` gives a snapshot, not a live value.
**How to avoid:** Add a `Flow<Int>` count query to `HighlightDao` and combine it into the counter StateFlow.

### Pitfall 5: PullToRefreshBox must wrap a scrollable container
**What goes wrong:** Pull gesture doesn't work when highlights list is empty (showing the "No highlights yet" text).
**Why it happens:** `PullToRefreshBox` needs a scrollable child to detect the overscroll gesture. A plain `Box` with centered text is not scrollable.
**How to avoid:** Always render inside `PullToRefreshBox`, and for the empty state either use a single-item `LazyColumn` or ensure the `PullToRefreshBox` content has `Modifier.fillMaxSize()` (PullToRefreshBox handles the gesture even without scroll in this case).

## Code Examples

### New DAO methods for bulk bookmark fetch (FILT-01)

```kotlin
// BookmarkDao.kt -- add these queries
@Query("""
    SELECT localId, remoteId, originalRemoteId, serverId, title, url,
           description, imageUrl, bannerImageAssetId, screenshotAssetId, tags, listIds, isStarred, isArchived,
           isRead, createdAt, readingTimeMinutes, readingProgress, readingScrollIndex, readingScrollOffset,
           '' as content
    FROM bookmarks
    WHERE serverId = :serverId AND isArchived = 0
    ORDER BY createdAt DESC
""")
suspend fun getAllNotArchivedForServer(serverId: String): List<BookmarkEntity>

@Query("""
    SELECT localId, remoteId, originalRemoteId, serverId, title, url,
           description, imageUrl, bannerImageAssetId, screenshotAssetId, tags, listIds, isStarred, isArchived,
           isRead, createdAt, readingTimeMinutes, readingProgress, readingScrollIndex, readingScrollOffset,
           '' as content
    FROM bookmarks
    WHERE serverId = :serverId AND isStarred = 1
    ORDER BY createdAt DESC
""")
suspend fun getAllFavoritesForServer(serverId: String): List<BookmarkEntity>

@Query("""
    SELECT localId, remoteId, originalRemoteId, serverId, title, url,
           description, imageUrl, bannerImageAssetId, screenshotAssetId, tags, listIds, isStarred, isArchived,
           isRead, createdAt, readingTimeMinutes, readingProgress, readingScrollIndex, readingScrollOffset,
           '' as content
    FROM bookmarks
    WHERE serverId = :serverId AND isArchived = 1
    ORDER BY createdAt DESC
""")
suspend fun getAllArchivedForServer(serverId: String): List<BookmarkEntity>

@Query("""
    SELECT localId, remoteId, originalRemoteId, serverId, title, url,
           description, imageUrl, bannerImageAssetId, screenshotAssetId, tags, listIds, isStarred, isArchived,
           isRead, createdAt, readingTimeMinutes, readingProgress, readingScrollIndex, readingScrollOffset,
           '' as content
    FROM bookmarks
    WHERE serverId = :serverId
    ORDER BY createdAt DESC
""")
suspend fun getAllBookmarksForServerSuspend(serverId: String): List<BookmarkEntity>

@Query("""
    SELECT localId, remoteId, originalRemoteId, serverId, title, url,
           description, imageUrl, bannerImageAssetId, screenshotAssetId, tags, listIds, isStarred, isArchived,
           isRead, createdAt, readingTimeMinutes, readingProgress, readingScrollIndex, readingScrollOffset,
           '' as content
    FROM bookmarks
    WHERE serverId = :serverId
    AND (listIds = :listId
         OR listIds LIKE :listId || ',%'
         OR listIds LIKE '%,' || :listId
         OR listIds LIKE '%,' || :listId || ',%')
    ORDER BY createdAt DESC
""")
suspend fun getAllBookmarksForList(serverId: String, listId: String): List<BookmarkEntity>
```

### BookmarkRepository helper for bulk fetch (FILT-01)

```kotlin
// BookmarkRepository.kt -- add method parallel to getBookmarksPaged
suspend fun getAllBookmarks(
    server: Server,
    status: FilterStatus,
    listId: String? = null
): List<BookmarkEntity> {
    return if (listId != null) {
        bookmarkDao.getAllBookmarksForList(server.id, listId)
    } else {
        when (status) {
            FilterStatus.ALL -> bookmarkDao.getAllNotArchivedForServer(server.id)
            FilterStatus.ALL_INCLUDING_ARCHIVED -> bookmarkDao.getAllBookmarksForServerSuspend(server.id)
            FilterStatus.FAVORITES -> bookmarkDao.getAllFavoritesForServer(server.id)
            FilterStatus.ARCHIVED -> bookmarkDao.getAllArchivedForServer(server.id)
        }
    }
}
```

### Updated selectAll (FILT-01)

```kotlin
// MainScreenModelBatch.kt
fun MainScreenModel.selectAll() {
    if (!_hasMoreItems.value) {
        // All pages loaded -- current behavior is correct
        _selectedBookmarkIds.value = _accumulatedBookmarks.value.map { it.remoteId }.toSet()
        return
    }
    // Fetch all matching entities from DB
    screenModelScope.launch {
        val server = _selectedServer.value ?: return@launch
        val filter = _currentFilter.value

        // Expand list children (same as loadBookmarksPage)
        val expandedFilter = if (filter.lists.isNotEmpty()) {
            val expandedLists = expandListsWithChildren(filter.lists)
            if (expandedLists != filter.lists) filter.copy(lists = expandedLists) else filter
        } else filter

        val singleListId = if (expandedFilter.lists.size == 1) expandedFilter.lists.first() else null
        val allEntities = bookmarkRepository.getAllBookmarks(server, expandedFilter.status, singleListId)

        // Apply client-side filters (tags, multi-list)
        val filtered = BookmarkFilterUtils.applyClientSideFilters(
            allEntities, expandedFilter, skipListFilter = singleListId != null
        )
        val sorted = BookmarkFilterUtils.applySorting(filtered, expandedFilter.sort)

        // Replace accumulated bookmarks and select all
        updateAccumulatedBookmarks { sorted }
        _hasMoreItems.value = false
        _selectedBookmarkIds.value = sorted.map { it.remoteId }.toSet()
    }
}
```

### Highlight count DAO query (FILT-02)

```kotlin
// HighlightDao.kt -- add reactive count
@Query("SELECT COUNT(*) FROM highlights WHERE serverId = :serverId")
fun getHighlightsCountForServer(serverId: String): Flow<Int>
```

### Quick filter counts StateFlow (FILT-02)

```kotlin
// MainScreenModel.kt -- add alongside listCounts
data class QuickFilterCounts(
    val all: Int = 0,
    val favorites: Int = 0,
    val archived: Int = 0
)

val quickFilterCounts: StateFlow<QuickFilterCounts> = combine(
    selectedServer, allBookmarks
) { server, bookmarks ->
    if (server == null) return@combine QuickFilterCounts()
    QuickFilterCounts(
        all = bookmarks.count { !it.isArchived },
        favorites = bookmarks.count { it.isStarred && !it.isArchived },
        archived = bookmarks.count { it.isArchived }
    )
}.stateIn(screenModelScope, SharingStarted.WhileSubscribed(5000), QuickFilterCounts())
```

### PullToRefreshBox in HighlightsScreen (FILT-03)

```kotlin
// HighlightsScreen.kt -- wrap Scaffold content
import androidx.compose.material3.pulltorefresh.PullToRefreshBox

Scaffold(...) { paddingValues ->
    PullToRefreshBox(
        isRefreshing = isSyncing,
        onRefresh = { screenModel.syncHighlights() },
        modifier = Modifier.fillMaxSize().padding(paddingValues)
    ) {
        if (highlights.isEmpty() && !isSyncing) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("No highlights yet", style = MaterialTheme.typography.bodyLarge)
            }
        } else if (highlights.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(state = listState, ...) { ... }
        }
    }
}
```

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | kotlin.test (commonTest + desktopTest) |
| Config file | composeApp/build.gradle.kts |
| Quick run command | `./gradlew :composeApp:desktopTest --tests "com.karakept.app.*"` |
| Full suite command | `./gradlew :composeApp:allTests` |

### Phase Requirements -> Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| FILT-01 | selectAll fetches all matching bookmarks when hasMoreItems=true | unit | `./gradlew :composeApp:desktopTest --tests "*SelectAllTest*"` | Wave 0 |
| FILT-01 | selectAll applies client-side filters (tags, lists) | unit | `./gradlew :composeApp:desktopTest --tests "*SelectAllTest*"` | Wave 0 |
| FILT-01 | selectAll is no-op when hasMoreItems=false | unit | `./gradlew :composeApp:desktopTest --tests "*SelectAllTest*"` | Wave 0 |
| FILT-02 | quickFilterCounts computes correct counts from allBookmarks | unit | `./gradlew :composeApp:commonTest --tests "*QuickFilterCountsTest*"` | Wave 0 |
| FILT-02 | Favorites count excludes archived starred bookmarks | unit | `./gradlew :composeApp:commonTest --tests "*QuickFilterCountsTest*"` | Wave 0 |
| FILT-03 | Pull-to-refresh on HighlightsScreen | manual-only | N/A (gesture requires UI) | N/A |

### Sampling Rate
- **Per task commit:** Quick run relevant test class
- **Per wave merge:** Full suite
- **Phase gate:** Full suite green before `/gsd:verify-work`

### Wave 0 Gaps
- [ ] `composeApp/src/commonTest/.../SelectAllTest.kt` -- pure function test for select-all logic with mock bookmark lists
- [ ] `composeApp/src/commonTest/.../QuickFilterCountsTest.kt` -- pure function test for count computation

## Sources

### Primary (HIGH confidence)
- Codebase analysis: `MainScreenModelBatch.kt`, `MainScreenModel.kt`, `MainScreenModelPagination.kt`, `BookmarkDao.kt`, `BookmarkRepository.kt`, `HighlightsScreen.kt`, `HighlightsScreenModel.kt`, `MainScreenDrawer.kt`, `BookmarkFilterUtils.kt`, `FilterConfig.kt`, `HighlightDao.kt`, `HighlightRepository.kt`
- [PullToRefreshBox API docs](https://kotlinlang.org/api/compose-multiplatform/material3/androidx.compose.material3.pulltorefresh/-pull-to-refresh-box.html)
- [Compose Multiplatform 1.10.0 release](https://github.com/JetBrains/compose-multiplatform/releases/tag/v1.10.0)

### Secondary (MEDIUM confidence)
- PullToRefreshBox ExperimentalMaterial3Api status: likely stable in 1.10.0 but may need @OptIn -- the HighlightsScreen already has this annotation

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH - all components are already in the project (Room, Compose M3, Coroutines Flows)
- Architecture: HIGH - patterns are well-established in the codebase (listCounts, paged queries, client-side filtering)
- Pitfalls: HIGH - identified from direct codebase analysis of actual data flow

**Research date:** 2026-03-23
**Valid until:** 2026-04-23 (stable codebase, no external dependencies changing)
