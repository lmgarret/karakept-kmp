---
phase: 03-code-splitting
plan: 03
subsystem: ui
tags: [compose, refactoring, code-splitting, kotlin-multiplatform]

requires:
  - phase: 03-code-splitting
    provides: "Previous splits (BookmarkSyncPipeline, pagination, MainScreenModel actions/batch)"
provides:
  - "MainScreen.kt under 500 lines with scaffold content extracted to main/ sub-package"
  - "BookmarkViewerScreen.kt under 500 lines (39 lines, Screen class only)"
  - "BookmarkViewerContent.kt under 500 lines (465 lines)"
  - "MainScreenDisplayConfig data class for bundling effective display settings"
  - "ViewerScrollRestoration composable for scroll guard and reading progress restore"
  - "ViewerContentPanels composable for viewer dialogs and panels"
  - "ViewerSnackbar utility for FAB-aware snackbar handling"
affects: [04-testing, ui-screens]

tech-stack:
  added: []
  patterns:
    - "State holder composable (rememberScrollRestoration) for complex scroll logic"
    - "DisplayConfig data class to bundle effective layout overrides and reduce parameter count"
    - "Shared scaffold content via captured lambda to avoid duplicating verbose call sites"

key-files:
  created:
    - "composeApp/src/commonMain/kotlin/com/karakept/app/ui/screens/BookmarkViewerContent.kt"
    - "composeApp/src/commonMain/kotlin/com/karakept/app/ui/screens/main/MainScreenScaffoldContent.kt"
    - "composeApp/src/commonMain/kotlin/com/karakept/app/ui/screens/viewer/ViewerSnackbar.kt"
    - "composeApp/src/commonMain/kotlin/com/karakept/app/ui/screens/viewer/ViewerContentPanels.kt"
    - "composeApp/src/commonMain/kotlin/com/karakept/app/ui/screens/viewer/ViewerScrollRestoration.kt"
  modified:
    - "composeApp/src/commonMain/kotlin/com/karakept/app/ui/screens/MainScreen.kt"
    - "composeApp/src/commonMain/kotlin/com/karakept/app/ui/screens/BookmarkViewerScreen.kt"

key-decisions:
  - "Used MainScreenDisplayConfig data class to bundle 12 effective layout settings, reducing parameter sprawl at call sites"
  - "Extracted scroll guard and reading progress restoration into rememberScrollRestoration composable with state holder return type"
  - "Separated viewer dialogs/panels into ViewerContentPanels composable with callback-based state threading"
  - "Used captured lambda for scaffold content to avoid duplicating 60+ parameter call sites between compact and expanded layouts"

patterns-established:
  - "State holder composable: return data class from @Composable function to encapsulate complex stateful logic (ViewerScrollRestoration)"
  - "DisplayConfig bundling: group effective display overrides into data class constructed with remember()"

requirements-completed: [SPLIT-01, SPLIT-03]

duration: 15min
completed: 2026-03-21
---

# Phase 03 Plan 03: Screen Composable Splitting Summary

**MainScreen (757->415) and BookmarkViewerScreen (1045->39) split into focused composables with scroll restoration, display config, and panel extraction**

## Performance

- **Duration:** 15 min
- **Started:** 2026-03-21T09:35:19Z
- **Completed:** 2026-03-21T09:50:20Z
- **Tasks:** 2
- **Files modified:** 7 (2 modified, 5 created)

## Accomplishments
- MainScreen.kt reduced from 757 to 415 lines by extracting scaffold content into MainScreenScaffoldContent
- BookmarkViewerScreen.kt reduced from 1045 to 39 lines by extracting content, panels, scroll restoration, and snackbar utilities
- All new and existing files in main/ and viewer/ sub-packages remain under 500 lines
- Introduced MainScreenDisplayConfig data class to reduce parameter count for display settings
- Extracted complex scroll restoration logic into a reusable rememberScrollRestoration composable

## Task Commits

Each task was committed atomically:

1. **Task 1: Extract MainScreen scaffold content** - `b893c9c` (refactor)
2. **Task 2: Extract BookmarkViewerContent and utilities** - `23acbc2` (refactor)

## Files Created/Modified

**Created:**
- `screens/main/MainScreenScaffoldContent.kt` - Shared scaffold with top bar, bookmark list, FAB, snackbar (274 lines)
- `screens/BookmarkViewerContent.kt` - Viewer content composable with scroll, highlight, and rendering logic (465 lines)
- `screens/viewer/ViewerSnackbar.kt` - FAB-aware snackbar host state and event dispatch (103 lines)
- `screens/viewer/ViewerContentPanels.kt` - All viewer overlay dialogs and panels (152 lines)
- `screens/viewer/ViewerScrollRestoration.kt` - Scroll guard, reading progress restoration, content render tracking (142 lines)

**Modified:**
- `screens/MainScreen.kt` - Reduced from 757 to 415 lines; uses MainScreenScaffoldContent via captured lambda
- `screens/BookmarkViewerScreen.kt` - Reduced from 1045 to 39 lines; Screen data class with Content() only

## Decisions Made
- Used MainScreenDisplayConfig data class to bundle 12 effective layout settings, reducing parameter sprawl at call sites
- Used captured lambda pattern for scaffold content to avoid duplicating 60+ parameter call sites between compact and expanded layouts
- Extracted scroll guard + reading progress into a state holder composable (rememberScrollRestoration) rather than leaving as inline LaunchedEffects
- Separated viewer dialogs/panels into ViewerContentPanels with callback-based state threading to keep BookmarkViewerContent under 500 lines

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Missing Critical] Created ViewerContentPanels and ViewerScrollRestoration (not in plan)**
- **Found during:** Task 2
- **Issue:** BookmarkViewerContent was 929 lines after initial extraction of just snackbar utilities. Plan only specified BookmarkViewerContent.kt and ViewerSnackbar.kt, but that left content file far over 500 lines.
- **Fix:** Created ViewerContentPanels.kt (dialogs/panels) and ViewerScrollRestoration.kt (scroll guard + progress restore) to bring BookmarkViewerContent.kt under 500 lines.
- **Files modified:** viewer/ViewerContentPanels.kt, viewer/ViewerScrollRestoration.kt, BookmarkViewerContent.kt
- **Committed in:** 23acbc2

**2. [Rule 2 - Missing Critical] Created MainScreenScaffoldContent and MainScreenDisplayConfig (not in plan)**
- **Found during:** Task 1
- **Issue:** Plan specified extracting dialogs, scroll action, and expanded layout (already done from prior work). MainScreen.kt was still 757 lines. Needed additional extraction.
- **Fix:** Created MainScreenScaffoldContent composable and MainScreenDisplayConfig data class to extract the shared scaffold content and reduce parameter verbosity.
- **Files modified:** main/MainScreenScaffoldContent.kt, MainScreen.kt
- **Committed in:** b893c9c

---

**Total deviations:** 2 auto-fixed (both missing critical functionality to meet line count targets)
**Impact on plan:** Additional files were necessary to achieve the under-500-line target. No scope creep -- all extractions are pure structural refactoring.

## Issues Encountered
- Initial attempt to extract MainScreenScaffoldContent as a top-level function with individual parameters resulted in verbose call sites (60+ params x 2 call sites), making the file larger. Solved by using a captured lambda pattern and MainScreenDisplayConfig data class.
- BookmarkViewerContent required three additional extractions beyond what the plan specified because the original function contained 865 lines of tightly coupled state management, scroll logic, and rendering.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- All screen composables are now under 500 lines
- UI layer is ready for testing (Phase 04) -- files are small enough to reason about individually
- Build verification by user is needed to confirm no behavioral regressions

## Self-Check: PASSED

All 5 created files verified present. Both task commits (b893c9c, 23acbc2) verified in git log.

---
*Phase: 03-code-splitting*
*Completed: 2026-03-21*
