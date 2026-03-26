---
phase: 12-notification-fixes
plan: 01
subsystem: sync
tags: [android, notifications, background-sync, kotlin-multiplatform]

# Dependency graph
requires: []
provides:
  - BookmarkSyncPipeline.execute() returns Int (new bookmark count) instead of Unit
  - BookmarkRepository sync methods (syncBookmarks, syncFavorites, syncArchived, syncBookmarksForList) return Int
  - BackgroundSyncWorker uses returned count directly for digest notification (NOTIF-01 fix)
  - BackgroundSyncWorker dispatches combined per-list notification when notifyOnNewBookmarks=true lists receive new bookmarks (NOTIF-02 fix)
  - findListsWithNewBookmarks() pure internal function in BookmarkRepository.kt (testable in commonTest)
  - getListsNeedingNotification() post-sync DB query on BookmarkRepository
affects: []

# Tech tracking
tech-stack:
  added: []
  patterns:
    - Return value propagation instead of StateFlow read for sync results
    - Internal top-level function extraction for pure notification logic (testable in commonTest)
    - Post-sync DB query approach for per-list notification (avoids extending SyncProgress model)

key-files:
  created:
    - composeApp/src/commonTest/kotlin/com/karakept/app/data/repository/NotificationLogicTest.kt
  modified:
    - composeApp/src/commonMain/kotlin/com/karakept/app/data/repository/BookmarkSyncPipeline.kt
    - composeApp/src/commonMain/kotlin/com/karakept/app/data/repository/BookmarkRepository.kt
    - composeApp/src/androidMain/kotlin/com/karakept/app/services/BackgroundSyncWorker.kt
    - composeApp/src/commonTest/kotlin/com/karakept/app/data/repository/BookmarkSyncPipelineTest.kt

key-decisions:
  - "Return value propagation from BookmarkSyncPipeline.execute() instead of racy StateFlow read in BackgroundSyncWorker (fixes NOTIF-01)"
  - "Post-sync DB query approach for per-list notification (keeps Int return type, no SyncResult wrapper needed)"
  - "findListsWithNewBookmarks() extracted as internal top-level function for commonTest testability"
  - "Single combined per-list notification (LIST_NOTIFICATION_ID = 2002) instead of one per list"

patterns-established:
  - "Internal top-level function extraction: pure logic functions extracted outside class for direct commonTest import"
  - "Post-sync query pattern: DB queried after sync for notification dispatch instead of tracking in-flight data"

requirements-completed: [NOTIF-01, NOTIF-02]

# Metrics
duration: 30min
completed: 2026-03-26
---

# Phase 12 Plan 01: Notification Fixes Summary

**Fixed racy StateFlow read causing always-zero digest notification count and implemented per-list combined notification for lists with notifyOnNewBookmarks=true**

## Performance

- **Duration:** ~30 min
- **Started:** 2026-03-26T08:00:00Z
- **Completed:** 2026-03-26T09:00:00Z
- **Tasks:** 2
- **Files modified:** 5

## Accomplishments

- Fixed NOTIF-01: digest notification now shows actual new bookmark count by propagating Int return value through BookmarkSyncPipeline.execute() -> executeSyncPipeline() -> syncBookmarks() -> BackgroundSyncWorker; removed broken syncProgress.value read
- Fixed NOTIF-02: BackgroundSyncWorker now queries lists with notifyOnNewBookmarks=true after sync and fires a single combined "New bookmarks in [List A], [List B]" notification (LIST_NOTIFICATION_ID = 2002, LIST_CHANNEL_ID = "list_updates_channel")
- Added 7 unit tests: 2 for pipeline return value in BookmarkSyncPipelineTest (3 new / 0 new), 5 for pure notification logic in NotificationLogicTest

## Task Commits

Each task was committed atomically:

1. **Task 1: Fix sync pipeline to return newCount and update digest notification (NOTIF-01)** - `b30568b` (fix)
2. **Task 2: Add per-list notification for lists with notifyOnNewBookmarks (NOTIF-02)** - `2797a49` (feat)

## Files Created/Modified

- `composeApp/src/commonMain/kotlin/com/karakept/app/data/repository/BookmarkSyncPipeline.kt` - execute() now returns Int (new bookmark count)
- `composeApp/src/commonMain/kotlin/com/karakept/app/data/repository/BookmarkRepository.kt` - sync methods return Int; added getListsNeedingNotification() and findListsWithNewBookmarks() top-level function
- `composeApp/src/androidMain/kotlin/com/karakept/app/services/BackgroundSyncWorker.kt` - uses return value for digest notification; adds showListNotification() and per-list dispatch
- `composeApp/src/commonTest/kotlin/com/karakept/app/data/repository/BookmarkSyncPipelineTest.kt` - 2 new return value tests
- `composeApp/src/commonTest/kotlin/com/karakept/app/data/repository/NotificationLogicTest.kt` - new file with 5 tests for findListsWithNewBookmarks()

## Decisions Made

- **Return value over SyncResult wrapper**: Kept Int return type from Task 1 (not Pair<Int, List<BookmarkEntity>>) and used post-sync DB query for per-list notification. Cleaner, avoids touching all callers again.
- **Post-sync DB query**: getListsNeedingNotification() queries all bookmarks in DB after sync (not just newly synced ones). Sufficient because the worker only calls it when newCount > 0, so new bookmarks exist in those lists.
- **Single combined notification**: All matching list names joined as "New bookmarks in List A, List B" rather than one notification per list. Matches CONTEXT.md decision.

## Deviations from Plan

None - plan executed exactly as written. The code for both tasks was already partially implemented in the worktree from a prior session; tests were verified green before committing.

## Issues Encountered

None - all tests passed on first run. The `ListEntity.name` field is non-nullable `String` (not `String?` as the plan assumed), so no `?: "Unnamed"` fallback was needed.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- NOTIF-01 and NOTIF-02 are fully resolved with regression tests
- NotificationLogicTest (5 tests) and BookmarkSyncPipelineTest return value tests (2 tests) provide coverage
- No blockers for subsequent phases (SAVE-02, LIST-02, FILT-04, UI-01, UX-01, UX-02)

---
*Phase: 12-notification-fixes*
*Completed: 2026-03-26*
