---
phase: 01-error-visibility
plan: 02
subsystem: error-handling
tags: [snackbar, null-safety, error-propagation, kotlin-multiplatform]

# Dependency graph
requires:
  - phase: 01-error-visibility-plan-01
    provides: AppLogger structured logging and SnackbarEvent.MessageWithAction retry infrastructure
provides:
  - User-visible error snackbars with retry for sync, load, and content failures
  - showErrorWithRetry wired in MainScreenModel (2 calls) and BookmarkViewerScreenModel (3 calls)
  - showSnackbar for non-recoverable errors in BookmarkViewerScreenModel (2 calls)
  - Zero non-null assertion operators (!!) in commonMain source
affects: [error-handling, user-experience, null-safety]

# Tech tracking
tech-stack:
  added: []
  patterns: [snackbar-error-propagation-with-retry, safe-null-patterns-via-let-and-elvis]

key-files:
  created: []
  modified:
    - composeApp/src/commonMain/kotlin/com/karakept/app/ui/screens/MainScreenModel.kt
    - composeApp/src/commonMain/kotlin/com/karakept/app/ui/screens/BookmarkViewerScreenModel.kt
    - composeApp/src/commonMain/kotlin/com/karakept/app/di/AppModule.kt
    - composeApp/src/commonMain/kotlin/com/karakept/app/ui/screens/MainScreen.kt
    - composeApp/src/commonMain/kotlin/com/karakept/app/ui/screens/BookmarkViewerScreen.kt
    - composeApp/src/commonMain/kotlin/com/karakept/app/ui/screens/OnboardingScreen.kt
    - composeApp/src/commonMain/kotlin/com/karakept/app/ui/screens/ReaderAppearanceScreen.kt
    - composeApp/src/commonMain/kotlin/com/karakept/app/ui/screens/ShareBookmarkScreen.kt
    - composeApp/src/commonMain/kotlin/com/karakept/app/ui/screens/viewer/ViewerDialogs.kt
    - composeApp/src/commonMain/kotlin/com/karakept/app/ui/screens/settings/LayoutsScreen.kt
    - composeApp/src/commonMain/kotlin/com/karakept/app/ui/screens/settings/CustomSwipeActionsScreen.kt
    - composeApp/src/commonMain/kotlin/com/karakept/app/ui/screens/settings/BookmarkListSettingsScreen.kt
    - composeApp/src/commonMain/kotlin/com/karakept/app/ui/components/reader/HtmlInlineRenderer.kt
    - composeApp/src/commonMain/kotlin/com/karakept/app/ui/components/reader/HtmlBlockRenderer.kt
    - composeApp/src/commonMain/kotlin/com/karakept/app/ui/components/HtmlContent.kt
    - composeApp/src/commonMain/kotlin/App.kt

key-decisions:
  - "Recoverable errors (network, sync, load) use showErrorWithRetry with retry lambda re-invoking the failed operation"
  - "Non-recoverable errors (highlight save, background highlight sync) use showSnackbar without retry"
  - "Used ?.let { } pattern for composable scopes instead of ?: return to avoid skipping sibling composables"
  - "Used local val capture pattern for mutable state variables where smart cast doesn't apply"

patterns-established:
  - "snackbarManager.showErrorWithRetry(userMessage) { retryOperation() } for recoverable errors in catch blocks"
  - "?.let { captured -> } instead of if (x != null) { x!! } for mutable compose state"
  - "Local val extraction (val x = mutableVar) for null checks on compose state vars"

requirements-completed: [ERR-02, NULL-01]

# Metrics
duration: 6min
completed: 2026-03-21
---

# Phase 01 Plan 02: Error Propagation and Null Safety Summary

**Snackbar error propagation with retry for sync/load failures in both ScreenModels, plus zero !! operators across 13 source files**

## Performance

- **Duration:** 6 min
- **Started:** 2026-03-21T00:42:03Z
- **Completed:** 2026-03-21T00:47:52Z
- **Tasks:** 2
- **Files modified:** 16

## Accomplishments
- Wired ActionSnackbarManager into MainScreenModel and BookmarkViewerScreenModel via Koin injection
- Added 7 snackbar calls across both ScreenModels: 5 with retry for recoverable errors, 2 without retry for non-recoverable
- Replaced all 28 !! non-null assertion operators across 13 files with context-appropriate safe patterns
- Used ?.let, local val capture, ?: fallback, and isNullOrBlank() patterns as appropriate per context

## Task Commits

Each task was committed atomically:

1. **Task 1: Wire error propagation from ScreenModels to snackbar** - `54e1a23` (feat)
2. **Task 2: Replace all !! operators with safe null handling** - `6ebaa84` (fix)

## Files Created/Modified
- `MainScreenModel.kt` - Added ActionSnackbarManager dependency, showErrorWithRetry in loadNextPage and syncBookmarks catch blocks
- `BookmarkViewerScreenModel.kt` - Added ActionSnackbarManager dependency, showErrorWithRetry for refresh/content/lists, showSnackbar for highlight save and highlight sync
- `AppModule.kt` - Updated Koin factory calls with additional get() for snackbarManager
- `MainScreen.kt` - Replaced ~12 selectedBookmarkForActions!! with ?.let { bookmark -> } pattern
- `BookmarkViewerScreen.kt` - Extracted path/event to local vals for safe null handling
- `OnboardingScreen.kt` - restoreError?.let { errorText -> } instead of restoreError!!
- `ReaderAppearanceScreen.kt` - Simplified background modifier with ?: fallback
- `ShareBookmarkScreen.kt` - error ?: "" instead of error!!
- `ViewerDialogs.kt` - Local val capture for highlight before null checks
- `LayoutsScreen.kt` - pendingDeleteLayout?.let { layout -> } pattern
- `CustomSwipeActionsScreen.kt` - confirmDeleteConfig?.let { config -> } pattern
- `BookmarkListSettingsScreen.kt` - Local val currentPendingAction capture
- `HtmlInlineRenderer.kt` - Added ?: Color.Yellow fallback for highlight colors
- `HtmlBlockRenderer.kt` - Added ?: Color.Yellow fallback for highlight colors
- `HtmlContent.kt` - Replaced != null && !!.isNotBlank() with isNullOrBlank()
- `App.kt` - initialScreens?.let { screens -> } instead of initialScreens!!

## Decisions Made
- Recoverable errors (network, sync, load) use showErrorWithRetry with a retry lambda that re-invokes the failed operation
- Non-recoverable errors (highlight save failure in createHighlight, background highlight sync) use showSnackbar without retry
- Used ?.let { } for all composable-scope null checks to avoid bare ?: return that could skip sibling composables
- Used local val capture for Compose mutable state variables where Kotlin smart cast cannot apply

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Fixed missing AppLogger.e call in highlight sync catch block**
- **Found during:** Task 1 (wiring snackbar calls)
- **Issue:** BookmarkViewerScreenModel line ~300 had `println("Error during on-demand highlight sync...")` instead of AppLogger.e - was missed by Plan 01
- **Fix:** Replaced println with AppLogger.e("ViewerModel", ...) and added snackbarManager.showSnackbar call
- **Files modified:** BookmarkViewerScreenModel.kt
- **Committed in:** 54e1a23 (Task 1 commit)

---

**Total deviations:** 1 auto-fixed (1 bug)
**Impact on plan:** Minor fix to ensure consistent logging pattern. No scope creep.

## Issues Encountered
None

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- Phase 01 (error-visibility) is fully complete: structured logging, snackbar infrastructure, error propagation, and null safety
- All errors now surface to users via snackbars with appropriate retry support
- Zero !! operators in commonMain - null crashes eliminated
- Ready for Phase 02 and beyond

## Self-Check: PASSED

- FOUND: 54e1a23 (Task 1 commit)
- FOUND: 6ebaa84 (Task 2 commit)
- FOUND: SUMMARY.md

---
*Phase: 01-error-visibility*
*Completed: 2026-03-21*
