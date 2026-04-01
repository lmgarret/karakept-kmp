---
phase: 15-snackbar-undo-system
plan: 01
subsystem: ui
tags: [snackbar, undo, material3, compose, batch-actions]

requires: []
provides:
  - "Shared undoableAction CoroutineScope extension function for undo snackbar pattern"
  - "Undo snackbars on all reversible MainScreen swipe actions"
  - "Undo snackbars on all reversible bottom sheet bookmark actions"
  - "Undo snackbars on all reversible batch selection actions with count"
affects: [15-02-PLAN]

tech-stack:
  added: []
  patterns:
    - "undoableAction extension function encapsulating scope.launch + showSnackbarWithUndo"
    - "Batch undo via reverse repository method call in snackbar lambda"

key-files:
  created: []
  modified:
    - composeApp/src/commonMain/kotlin/com/karakept/app/domain/action/ActionSnackbarManager.kt
    - composeApp/src/commonMain/kotlin/com/karakept/app/ui/screens/MainScreen.kt
    - composeApp/src/commonMain/kotlin/com/karakept/app/ui/screens/main/MainScreenDialogs.kt
    - composeApp/src/commonMain/kotlin/com/karakept/app/ui/screens/MainScreenModelBatch.kt

key-decisions:
  - "undoableAction as CoroutineScope extension in ActionSnackbarManager.kt for reuse across 4+ call sites"
  - "batchSetTags gets plain snackbar (no undo) because per-bookmark tag restoration is too complex"
  - "Batch undo snackbar placed after clearSelection() so UI clears immediately while snackbar shows"

patterns-established:
  - "undoableAction pattern: scope.undoableAction(snackbarManager, message) { reverseAction() }"
  - "Batch undo messages include count: 'Archived 3 bookmarks'"
  - "Past tense for all snackbar messages: 'Archived', 'Marked as read', 'Added to favorites'"

requirements-completed: [UX-01, NFR-01]

duration: 4min
completed: 2026-03-28
---

# Phase 15 Plan 01: Snackbar Undo System Summary

**Shared undoableAction helper with undo snackbars wired to all reversible MainScreen actions: swipe, bottom sheet, and batch selection**

## Performance

- **Duration:** 4 min
- **Started:** 2026-03-28T13:49:24Z
- **Completed:** 2026-03-28T13:53:28Z
- **Tasks:** 2
- **Files modified:** 4

## Accomplishments
- Created shared `undoableAction` CoroutineScope extension function in ActionSnackbarManager.kt, used by 4+ call sites
- Wired undo snackbars to all 5 reversible swipe actions (archive, read, favourite, add-tag, add-to-list) and all 5 reversible bottom sheet actions (ToggleArchive, ToggleFavorite, ToggleRead, MoveToList, UpdateTags)
- Wired undo snackbars to 7 reversible batch selection actions with bookmark count in messages
- All snackbar messages use past tense; Delete retains confirm dialog without undo

## Task Commits

Each task was committed atomically:

1. **Task 1: Create shared undoableAction helper and wire undo snackbars to MainScreen swipe and bottom sheet actions** - `2140d20` (feat)
2. **Task 2: Wire undo snackbars to batch selection actions** - `9e29d11` (feat)

## Files Created/Modified
- `composeApp/src/commonMain/kotlin/com/karakept/app/domain/action/ActionSnackbarManager.kt` - Added undoableAction CoroutineScope extension function
- `composeApp/src/commonMain/kotlin/com/karakept/app/ui/screens/MainScreen.kt` - Converted swipe action plain snackbars to undo snackbars (8 calls)
- `composeApp/src/commonMain/kotlin/com/karakept/app/ui/screens/main/MainScreenDialogs.kt` - Added undo snackbars to bottom sheet bookmark actions (6 calls)
- `composeApp/src/commonMain/kotlin/com/karakept/app/ui/screens/MainScreenModelBatch.kt` - Added undo snackbars to batch methods (7 showSnackbarWithUndo + 1 showSnackbar)

## Decisions Made
- Used `undoableAction` as a `CoroutineScope` extension function (not a `MainScreenModel` extension) so it can be reused from any composable scope
- batchSetTags gets a plain confirmation snackbar instead of undo because each bookmark may have had different tags before the batch update, making restoration complex
- Batch move-to-list undo uses `bookmarkActionsRepository.removeFromList()` in a loop since there is no `batchRemoveFromList` repository method

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
None

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- All MainScreen reversible actions now show undo snackbars
- Plan 02 can wire the same pattern to BookmarkViewerContent actions

---
*Phase: 15-snackbar-undo-system*
*Completed: 2026-03-28*
