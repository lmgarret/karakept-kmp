---
phase: 06-selection-filtering
plan: 02
subsystem: ui
tags: [compose, material3, navigation-drawer, pull-to-refresh, room, stateflow, reactive]

# Dependency graph
requires:
  - phase: 05-list-sync
    provides: HighlightRepository and HighlightDao infrastructure used for count query
provides:
  - Reactive quick filter counters (All, Favorites, Archived, Highlights) on navigation drawer
  - QuickFilterCounts data class with WhileSubscribed(5000) StateFlow in MainScreenModel
  - PullToRefreshBox wrapping HighlightsScreen Scaffold body
  - HighlightDao.getHighlightsCountForServer reactive Flow<Int> count query
affects: [07-ui-tests, future filter plans]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "QuickFilterCounts data class derived from allBookmarks using combine() + stateIn(WhileSubscribed(5000))"
    - "HighlightDao reactive count via @Query(SELECT COUNT(*)) returning Flow<Int>"
    - "PullToRefreshBox wrapping Scaffold body content with modifier.padding(paddingValues)"
    - "BuiltinDrawerItem count param with Modifier.weight(1f) on label + bodySmall/0.6f alpha count text"

key-files:
  created: []
  modified:
    - composeApp/src/commonMain/kotlin/com/karakept/app/data/local/dao/HighlightDao.kt
    - composeApp/src/commonMain/kotlin/com/karakept/app/data/repository/HighlightRepository.kt
    - composeApp/src/commonMain/kotlin/com/karakept/app/ui/screens/MainScreenModel.kt
    - composeApp/src/commonMain/kotlin/com/karakept/app/ui/screens/main/MainScreenDrawer.kt
    - composeApp/src/commonMain/kotlin/com/karakept/app/ui/screens/main/MainScreenExpandedLayout.kt
    - composeApp/src/commonMain/kotlin/com/karakept/app/ui/screens/MainScreen.kt
    - composeApp/src/commonMain/kotlin/com/karakept/app/ui/screens/HighlightsScreen.kt
    - composeApp/src/commonMain/kotlin/com/karakept/app/di/AppModule.kt

key-decisions:
  - "Injected HighlightRepository into MainScreenModel constructor (8th param) to enable reactive highlights count StateFlow"
  - "Used Modifier.weight(1f) on the label Text in BuiltinDrawerItem to push count right without changing Row's horizontalArrangement"
  - "PullToRefreshBox receives paddingValues via modifier; LazyColumn and empty-state Box no longer carry it"

patterns-established:
  - "BuiltinDrawerItem count rendering: Modifier.weight(1f) on label + count Text with bodySmall/0.6f alpha matches ListDrawerItem style"

requirements-completed: [FILT-02, FILT-03]

# Metrics
duration: 15min
completed: 2026-03-23
---

# Phase 06 Plan 02: Quick Filter Counters and Pull-to-Refresh Summary

**Reactive drawer counters for All/Favorites/Archived/Highlights via allBookmarks combine() and Room COUNT query, plus PullToRefreshBox on HighlightsScreen**

## Performance

- **Duration:** 15 min
- **Started:** 2026-03-23T00:00:00Z
- **Completed:** 2026-03-23T00:15:00Z
- **Tasks:** 2
- **Files modified:** 8

## Accomplishments

- Added `getHighlightsCountForServer` reactive `Flow<Int>` DAO query and `getHighlightsCount` passthrough in HighlightRepository
- Added `QuickFilterCounts` data class and two new StateFlows (`quickFilterCounts`, `highlightsCount`) to MainScreenModel with `WhileSubscribed(5000)` and injected `HighlightRepository` into constructor
- Added `count: Int? = null` parameter to `BuiltinDrawerItem` with correct bodySmall/0.6f alpha style matching existing `ListDrawerItem` counts, threaded through `DrawerContent`, `MainScreenDrawer`, `MainScreenExpandedLayout`, and `MainScreen`
- Wrapped HighlightsScreen Scaffold body with `PullToRefreshBox` using `isSyncing` and `syncHighlights()`, properly handling paddingValues at the box level

## Task Commits

1. **Task 1: Add reactive quick filter counters to navigation drawer** - `1d82cd8` (feat)
2. **Task 2: Add pull-to-refresh to Highlights screen** - `dc0f4db` (feat)

## Files Created/Modified

- `HighlightDao.kt` - Added `getHighlightsCountForServer(serverId: String): Flow<Int>` with `SELECT COUNT(*)` query
- `HighlightRepository.kt` - Added `getHighlightsCount(serverId: String): Flow<Int>` passthrough
- `MainScreenModel.kt` - Added `QuickFilterCounts` data class, `quickFilterCounts` StateFlow (combines `selectedServer` + `allBookmarks`), `highlightsCount` StateFlow (flatMapLatest on server), added `HighlightRepository` constructor param
- `AppModule.kt` - Updated `MainScreenModel` Koin factory to pass `get()` for `HighlightRepository` (8th param)
- `MainScreenDrawer.kt` - Added `count: Int?` to `BuiltinDrawerItem`, `quickFilterCounts`/`highlightsCount` params to `DrawerContent` and `MainScreenDrawer`, passes counts to all four `BuiltinDrawerItem` calls
- `MainScreenExpandedLayout.kt` - Added `quickFilterCounts`/`highlightsCount` params, passes to `DrawerContent`
- `MainScreen.kt` - Collects `quickFilterCounts` and `highlightsCount` from screenModel, passes to both `MainScreenExpandedLayout` and `MainScreenDrawer`
- `HighlightsScreen.kt` - Replaced if/else with `PullToRefreshBox` wrapping both empty state and `LazyColumn`, paddingValues moved to box modifier

## Decisions Made

- Injected `HighlightRepository` as 8th param to `MainScreenModel` constructor rather than using `koinInject` inside the class — consistent with how other repositories are injected in this model
- Used `Modifier.weight(1f)` on the label `Text` in `BuiltinDrawerItem` rather than changing `horizontalArrangement` — minimal change that achieves right-alignment of the count
- `PullToRefreshBox` handles `paddingValues` so children (LazyColumn, Box empty state) no longer need `.padding(paddingValues)` — cleaner and avoids double padding

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- Drawer counters are reactive and will update immediately on bookmark archive/star/delete operations
- Highlights count is a live Room query — updates when highlights are added/deleted
- Pull-to-refresh on HighlightsScreen is fully wired to `syncHighlights()` with `isSyncing` indicator
- Both compact (modal drawer) and expanded (3-column) layouts receive the counts

---
*Phase: 06-selection-filtering*
*Completed: 2026-03-23*
