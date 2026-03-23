---
phase: 06-selection-filtering
plan: 01
subsystem: database
tags: [room, sqlite, pagination, select-all, bookmark-filter]

# Dependency graph
requires: []
provides:
  - Unpaged BookmarkDao queries (getAllNotArchivedForServer, getAllFavoritesForServer, getAllArchivedForServer, getAllBookmarksForServerSuspend, getAllBookmarksForList)
  - BookmarkRepository.getAllBookmarks() dispatching to correct DAO per FilterStatus
  - Fixed selectAll() that fetches all matching entities beyond the first page
affects: [06-selection-filtering]

# Tech tracking
tech-stack:
  added: []
  patterns: [unpaged-query-for-bulk-select, coroutine-launch-in-sync-fun, update-accumulated-bookmarks-mutex]

key-files:
  created: []
  modified:
    - composeApp/src/commonMain/kotlin/com/karakept/app/data/local/dao/BookmarkDao.kt
    - composeApp/src/commonMain/kotlin/com/karakept/app/data/repository/BookmarkRepository.kt
    - composeApp/src/commonMain/kotlin/com/karakept/app/ui/screens/MainScreenModelBatch.kt

key-decisions:
  - "selectAll() early-returns (selects current page) when hasMoreItems is false -- avoids unnecessary DB round-trip"
  - "selectAll() replaces _accumulatedBookmarks with full result so the list and selection are consistent (no phantom IDs)"
  - "Unpaged DAO queries use '' as content projection to avoid loading heavy HTML content into memory"

patterns-established:
  - "Unpaged DAO pattern: same SELECT columns as paged queries, no LIMIT/OFFSET, used for bulk operations"
  - "selectAll bulk-load: expand list children, single DAO fetch, applyClientSideFilters, applySorting, updateAccumulatedBookmarks, set hasMoreItems=false"

requirements-completed: [FILT-01]

# Metrics
duration: 8min
completed: 2026-03-23
---

# Phase 6 Plan 01: Select-All Beyond Pagination Summary

**Fixed select-all to query all matching DB rows without LIMIT/OFFSET, replacing the accumulated list and selecting every ID -- resolves the 20-entry cap bug (#153)**

## Performance

- **Duration:** 8 min
- **Started:** 2026-03-23T21:20:00Z
- **Completed:** 2026-03-23T21:28:00Z
- **Tasks:** 2
- **Files modified:** 3

## Accomplishments

- Added 5 unpaged DAO suspend functions with lightweight `'' as content` projection (no HTML loaded)
- Added `BookmarkRepository.getAllBookmarks()` with same status/listId dispatch pattern as `getBookmarksPaged()`
- Replaced the 2-line `selectAll()` with a coroutine that fetches all pages, applies client-side filters, updates the accumulated list thread-safely, and selects all IDs

## Task Commits

Each task was committed atomically:

1. **Task 1: Add unpaged DAO queries and repository helper** - `4465baa` (feat)
2. **Task 2: Fix selectAll() to fetch all matching entities** - `2ed7afb` (fix)

## Files Created/Modified

- `composeApp/src/commonMain/kotlin/com/karakept/app/data/local/dao/BookmarkDao.kt` - 5 new unpaged suspend query functions
- `composeApp/src/commonMain/kotlin/com/karakept/app/data/repository/BookmarkRepository.kt` - `getAllBookmarks()` helper method
- `composeApp/src/commonMain/kotlin/com/karakept/app/ui/screens/MainScreenModelBatch.kt` - Fixed `selectAll()`, added `BookmarkFilterUtils` import

## Decisions Made

- `selectAll()` early-returns to existing behavior when `hasMoreItems` is false (no DB round-trip needed -- all data is already loaded)
- `selectAll()` replaces `_accumulatedBookmarks` with the full sorted result so the displayed list and selection are consistent (no stale IDs in selection that aren't in the list)
- Unpaged queries omit `content` column (use `'' as content`) to avoid loading potentially large HTML into memory just to get IDs

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- Select-all fix is complete and ready for plan 02 (quick filter counters)
- No blockers

---
*Phase: 06-selection-filtering*
*Completed: 2026-03-23*
