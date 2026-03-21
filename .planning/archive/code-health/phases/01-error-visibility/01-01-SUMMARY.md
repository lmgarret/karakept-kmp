---
phase: 01-error-visibility
plan: 01
subsystem: logging
tags: [applogger, snackbar, error-handling, kotlin-multiplatform]

# Dependency graph
requires: []
provides:
  - AppLogger utility with severity-based structured logging (d/i/w/e)
  - SnackbarEvent.MessageWithAction variant for retry-capable error snackbars
  - showErrorWithRetry convenience method on ActionSnackbarManager
  - Zero printStackTrace calls across entire codebase
affects: [01-error-visibility-plan-02, error-propagation, snackbar-retry]

# Tech tracking
tech-stack:
  added: []
  patterns: [structured-logging-via-applogger, tag-based-error-categorization]

key-files:
  created:
    - composeApp/src/commonMain/kotlin/com/karakept/app/utils/AppLogger.kt
  modified:
    - composeApp/src/commonMain/kotlin/com/karakept/app/domain/action/ActionSnackbarManager.kt
    - composeApp/src/commonMain/kotlin/com/karakept/app/ui/screens/MainScreen.kt
    - composeApp/src/commonMain/kotlin/com/karakept/app/ui/screens/BookmarkViewerScreen.kt
    - composeApp/src/commonMain/kotlin/com/karakept/app/data/repository/BookmarkRepository.kt
    - composeApp/src/commonMain/kotlin/com/karakept/app/data/repository/BookmarkActionsRepository.kt
    - composeApp/src/commonMain/kotlin/com/karakept/app/data/repository/ListRepository.kt
    - composeApp/src/commonMain/kotlin/com/karakept/app/data/repository/HighlightRepository.kt
    - composeApp/src/commonMain/kotlin/com/karakept/app/data/remote/RemoteDataSource.kt
    - composeApp/src/commonMain/kotlin/com/karakept/app/utils/ReadingTimeCalculator.kt
    - composeApp/src/commonMain/kotlin/com/karakept/app/ui/screens/MainScreenModel.kt
    - composeApp/src/commonMain/kotlin/com/karakept/app/ui/screens/BookmarkViewerScreenModel.kt
    - composeApp/src/androidMain/kotlin/com/karakept/app/services/BookmarkSaveService.kt
    - composeApp/src/androidMain/kotlin/com/karakept/app/utils/FileUtils.android.kt
    - composeApp/src/androidMain/kotlin/com/karakept/app/ui/components/HtmlRenderer.android.kt
    - composeApp/src/androidMain/kotlin/com/karakept/app/ui/components/CustomTabOpener.android.kt
    - composeApp/src/desktopMain/kotlin/com/karakept/app/utils/ShareUtils.desktop.kt
    - composeApp/src/desktopMain/kotlin/com/karakept/app/ui/components/CustomTabOpener.jvm.kt

key-decisions:
  - "AppLogger uses println-based output with severity prefix (E/tag: message) for KMP compatibility"
  - "Removed all 6 noisy ReadProgressSync polling println logs from RemoteDataSource per user decision"
  - "Kept all existing error-handling control flow unchanged (return null, return failure, etc.) - only replaced logging calls"

patterns-established:
  - "AppLogger.e(TAG, message, throwable) for all error logging in catch blocks"
  - "TAG constant per file for log categorization (e.g. BookmarkRepo, ListRepo, ViewerModel)"

requirements-completed: [ERR-01, ERR-03, ERR-04]

# Metrics
duration: 6min
completed: 2026-03-21
---

# Phase 01 Plan 01: Error Visibility Foundation Summary

**AppLogger structured logging utility replacing 26 printStackTrace calls, plus SnackbarEvent.MessageWithAction retry infrastructure for Plan 02**

## Performance

- **Duration:** 6 min
- **Started:** 2026-03-21T00:34:16Z
- **Completed:** 2026-03-21T00:39:45Z
- **Tasks:** 2
- **Files modified:** 17

## Accomplishments
- Created AppLogger object with d/i/w/e severity methods and configurable min level
- Extended SnackbarEvent sealed class with MessageWithAction variant for retry-capable error snackbars
- Replaced all 26 printStackTrace() calls across 16 files with AppLogger.e() using contextual tags
- Removed 6 noisy ReadProgressSync polling println lines from RemoteDataSource
- Both snackbar consumers (MainScreen + BookmarkViewerScreen) handle the new MessageWithAction variant

## Task Commits

Each task was committed atomically:

1. **Task 1: Create AppLogger and extend SnackbarEvent with retry support** - `b4cf529` (feat)
2. **Task 2: Replace all printStackTrace and debug println with AppLogger** - `199ed8f` (fix)

## Files Created/Modified
- `composeApp/src/commonMain/kotlin/com/karakept/app/utils/AppLogger.kt` - Structured logging wrapper with severity levels (d/i/w/e)
- `composeApp/src/commonMain/kotlin/com/karakept/app/domain/action/ActionSnackbarManager.kt` - Added MessageWithAction variant and showErrorWithRetry method
- `composeApp/src/commonMain/kotlin/com/karakept/app/ui/screens/MainScreen.kt` - Handle MessageWithAction in rememberSnackbarHostState
- `composeApp/src/commonMain/kotlin/com/karakept/app/ui/screens/BookmarkViewerScreen.kt` - Handle MessageWithAction in showSnackbarEvent
- `composeApp/src/commonMain/kotlin/com/karakept/app/data/repository/BookmarkRepository.kt` - 5 AppLogger.e replacements
- `composeApp/src/commonMain/kotlin/com/karakept/app/data/repository/BookmarkActionsRepository.kt` - 1 AppLogger.e replacement
- `composeApp/src/commonMain/kotlin/com/karakept/app/data/repository/ListRepository.kt` - 3 AppLogger.e replacements
- `composeApp/src/commonMain/kotlin/com/karakept/app/data/repository/HighlightRepository.kt` - 1 AppLogger.e replacement
- `composeApp/src/commonMain/kotlin/com/karakept/app/data/remote/RemoteDataSource.kt` - Removed 6 noisy println, replaced 1 error with AppLogger.e
- `composeApp/src/commonMain/kotlin/com/karakept/app/utils/ReadingTimeCalculator.kt` - 1 AppLogger.e replacement
- `composeApp/src/commonMain/kotlin/com/karakept/app/ui/screens/MainScreenModel.kt` - 2 AppLogger.e replacements
- `composeApp/src/commonMain/kotlin/com/karakept/app/ui/screens/BookmarkViewerScreenModel.kt` - 4 AppLogger.e replacements
- `composeApp/src/androidMain/kotlin/com/karakept/app/services/BookmarkSaveService.kt` - 1 AppLogger.e replacement
- `composeApp/src/androidMain/kotlin/com/karakept/app/utils/FileUtils.android.kt` - 1 AppLogger.e replacement
- `composeApp/src/androidMain/kotlin/com/karakept/app/ui/components/HtmlRenderer.android.kt` - 2 AppLogger.e replacements
- `composeApp/src/androidMain/kotlin/com/karakept/app/ui/components/CustomTabOpener.android.kt` - 1 AppLogger.e replacement
- `composeApp/src/desktopMain/kotlin/com/karakept/app/utils/ShareUtils.desktop.kt` - 1 AppLogger.e replacement, println replaced with AppLogger.i
- `composeApp/src/desktopMain/kotlin/com/karakept/app/ui/components/CustomTabOpener.jvm.kt` - 1 AppLogger.e replacement

## Decisions Made
- AppLogger uses println-based output with severity prefix for KMP compatibility (no platform-specific logging dependency needed)
- Removed all 6 noisy ReadProgressSync polling printlns from RemoteDataSource (per user decision in ERR-04)
- Kept all existing error-handling control flow unchanged - only replaced logging calls, no behavior changes

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
None

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- AppLogger and MessageWithAction infrastructure ready for Plan 02 to wire error propagation to user-facing snackbars
- All catch blocks now use structured logging, making it straightforward to add snackbar calls alongside AppLogger calls

## Self-Check: PASSED

- FOUND: AppLogger.kt
- FOUND: SUMMARY.md
- FOUND: b4cf529 (Task 1 commit)
- FOUND: 199ed8f (Task 2 commit)

---
*Phase: 01-error-visibility*
*Completed: 2026-03-21*
