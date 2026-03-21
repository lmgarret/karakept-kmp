---
phase: 02-concurrency-hardening
plan: 01
subsystem: ui
tags: [kotlin, coroutines, mutex, stateflow, state-machine, concurrency]

requires:
  - phase: 01-error-visibility
    provides: AppLogger for state transition logging
provides:
  - InitState sealed class state machine for MainScreenModel init
  - Mutex-protected updateAccumulatedBookmarks helper for thread-safe bookmark mutations
  - Audit note replacing vestigial tag cache comment
affects: [03-screen-model-splitting, 04-testing]

tech-stack:
  added: [kotlinx.coroutines.sync.Mutex, kotlinx.coroutines.sync.withLock]
  patterns: [state-machine-init, mutex-protected-stateflow-mutation]

key-files:
  created: []
  modified:
    - composeApp/src/commonMain/kotlin/com/karakept/app/ui/screens/MainScreenModel.kt
    - composeApp/src/commonMain/kotlin/com/karakept/app/data/repository/BookmarkActionsRepository.kt

key-decisions:
  - "Mutex over MutableStateFlow.update{} because bookmarkChangedEvents collector suspends between read and write"
  - "Uniform Mutex for all 26 sites (even non-suspending) for simplicity and safety"
  - "InitState sealed class with 5 states covers linear startup: Idle -> ResolvingFilter -> WaitingForServer -> LoadingInitialPage -> Ready"

patterns-established:
  - "State machine init: Use sealed class + MutableStateFlow + AppLogger.d for auditable init sequences"
  - "Mutex-protected mutation: All read-modify-write of shared MutableStateFlow must go through a withLock helper"

requirements-completed: [CONC-01, CONC-02]

duration: 4min
completed: 2026-03-21
---

# Phase 02 Plan 01: Init State Machine and Bookmark Mutation Mutex Summary

**InitState sealed class state machine for MainScreenModel init with Mutex-protected updateAccumulatedBookmarks helper eliminating 26 race-prone mutation sites**

## Performance

- **Duration:** 4 min
- **Started:** 2026-03-21T08:04:56Z
- **Completed:** 2026-03-21T08:09:06Z
- **Tasks:** 2
- **Files modified:** 2

## Accomplishments
- MainScreenModel init now uses explicit InitState sealed class (Idle, ResolvingFilter, WaitingForServer, LoadingInitialPage, Ready) with AppLogger.d transitions
- All 26 _accumulatedBookmarks.value= mutation sites replaced with 25 Mutex-protected updateAccumulatedBookmarks calls (2 undo sites merged into 1)
- Vestigial tag cache race condition comment in BookmarkActionsRepository replaced with audit note

## Task Commits

Each task was committed atomically:

1. **Task 1: Add InitState sealed class state machine to MainScreenModel init** - `273da34` (feat)
2. **Task 2: Mutex-protect all _accumulatedBookmarks mutations and clean vestigial comment** - `156c2bc` (feat)

## Files Created/Modified
- `composeApp/src/commonMain/kotlin/com/karakept/app/ui/screens/MainScreenModel.kt` - Added InitState sealed class, _initState field, logging collector, state transitions in Coroutine B, bookmarksMutex, updateAccumulatedBookmarks helper, replaced all 26 direct mutation sites
- `composeApp/src/commonMain/kotlin/com/karakept/app/data/repository/BookmarkActionsRepository.kt` - Replaced vestigial tag cache comment with Phase 02 audit note

## Decisions Made
- Used Mutex over MutableStateFlow.update{} because bookmarkChangedEvents collector performs a suspending DB lookup between read and write, requiring a lock that holds across suspension points
- Applied Mutex uniformly to all 26 mutation sites (even non-suspending ones) for simplicity rather than mixing two synchronization strategies
- InitState sealed class models a linear state machine matching the existing init flow without changing behavior

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
None

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness
- Concurrency hardening complete for MainScreenModel init and bookmark mutations
- User should run `./gradlew :composeApp:desktopTest` to confirm no regressions
- Ready for Phase 03 (screen model splitting) -- the Mutex pattern and InitState will transfer to split modules

---
*Phase: 02-concurrency-hardening*
*Completed: 2026-03-21*
