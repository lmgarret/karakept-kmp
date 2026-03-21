---
phase: 07-integration-wiring-cleanup
plan: 02
subsystem: auth, logging
tags: [credential-migration, println-cleanup, AppLogger, SecureCredentialStore]

# Dependency graph
requires:
  - phase: 05-security-performance
    provides: SecureCredentialStore and triggerMigration() in ServerRepository
  - phase: 01-error-visibility
    provides: AppLogger utility with d/w/e severity levels
provides:
  - triggerMigration() wired into App.kt startup (credentials promoted to SecureCredentialStore)
  - Zero println calls in App.kt and BookmarkViewerScreenModel.kt
affects: []

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Hot-path code has zero logging (no println, no AppLogger)"
    - "Non-hot-path diagnostics use AppLogger.d/w with component tag"

key-files:
  created: []
  modified:
    - composeApp/src/commonMain/kotlin/App.kt
    - composeApp/src/commonMain/kotlin/com/karakept/app/ui/screens/BookmarkViewerScreenModel.kt

key-decisions:
  - "Coil interceptor and ReadProgressSync hot-path println removed entirely (no replacement)"
  - "Navigation, refresh, and highlight non-hot-path println replaced with AppLogger.d/w"

patterns-established:
  - "Hot-path zero-logging: ReadProgressSync, Coil interceptor have no logging at all"
  - "AppLogger tag convention: 'App' for App.kt, 'ViewerModel' for BookmarkViewerScreenModel"

requirements-completed: [SEC-02, ERR-01]

# Metrics
duration: 3min
completed: 2026-03-21
---

# Phase 07 Plan 02: Cache Wiring & Println Cleanup Summary

**Credential migration wired into App.kt startup via LaunchedEffect; 30 println calls removed/replaced across App.kt and BookmarkViewerScreenModel.kt**

## Performance

- **Duration:** 3 min
- **Started:** 2026-03-21T19:10:25Z
- **Completed:** 2026-03-21T19:14:04Z
- **Tasks:** 2
- **Files modified:** 2

## Accomplishments
- Wired ServerRepository.triggerMigration() into App.kt startup as best-effort LaunchedEffect with CancellationException re-throw
- Removed 3 Coil interceptor hot-path println calls and 8 ReadProgressSync hot-path println calls (zero logging in hot paths)
- Replaced 19 non-hot-path println calls with AppLogger.d/w using appropriate tags ("App", "ViewerModel")

## Task Commits

Each task was committed atomically:

1. **Task 1: Wire triggerMigration and clean App.kt** - `627538c` (feat)
2. **Task 2: Remove all println from BookmarkViewerScreenModel.kt** - `d6584d3` (fix)

## Files Created/Modified
- `composeApp/src/commonMain/kotlin/App.kt` - triggerMigration() call site, println removed/replaced with AppLogger
- `composeApp/src/commonMain/kotlin/com/karakept/app/ui/screens/BookmarkViewerScreenModel.kt` - 20 println calls removed (8 hot-path) or replaced with AppLogger (12 non-hot-path)

## Decisions Made
None - followed plan as specified.

## Deviations from Plan
None - plan executed exactly as written.

## Issues Encountered
None.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- SEC-02 (credential migration) and ERR-01 (println cleanup) are now closed
- Phase 07 is complete -- all integration wiring and cleanup plans executed

---
*Phase: 07-integration-wiring-cleanup*
*Completed: 2026-03-21*
