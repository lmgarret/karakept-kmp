---
phase: 03-reader-ux
plan: 02
subsystem: ui
tags: [compose, material3, reader, scroll, fab, overflow-menu, settings]

# Dependency graph
requires:
  - phase: 03-reader-ux/01
    provides: scrollToTopEnabled StateFlow and settings plumbing
provides:
  - "Details" overflow menu item in reader toolbar
  - Scroll-to-top SmallFloatingActionButton with visibility rules
  - Scroll-to-top toggle in ReaderAppearanceBottomPanel Behaviour tab
  - Scroll position restore on back navigation from reader
affects: []

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "AnimatedVisibility with fadeIn/fadeOut for overlay buttons"
    - "derivedStateOf for scroll-position-dependent UI visibility"
    - "Behaviour tab pattern in BaseBottomPanel for non-appearance settings"

key-files:
  created: []
  modified:
    - composeApp/src/commonMain/kotlin/com/karakept/app/ui/screens/viewer/ViewerTopBar.kt
    - composeApp/src/commonMain/kotlin/com/karakept/app/ui/screens/viewer/ViewerScrollBehavior.kt
    - composeApp/src/commonMain/kotlin/com/karakept/app/ui/screens/BookmarkViewerContent.kt
    - composeApp/src/commonMain/kotlin/com/karakept/app/ui/components/ReaderAppearanceBottomPanel.kt
    - composeApp/src/commonMain/kotlin/com/karakept/app/ui/screens/BookmarkViewerScreenModel.kt
    - composeApp/src/commonMain/kotlin/com/karakept/app/ui/screens/MainScreenModel.kt
    - composeApp/src/commonMain/kotlin/com/karakept/app/ui/screens/MainScreenModelPagination.kt
    - composeApp/src/commonMain/kotlin/com/karakept/app/ui/screens/main/MainScreenExpandedLayout.kt
    - composeApp/src/commonMain/kotlin/com/karakept/app/ui/screens/viewer/ViewerContentPanels.kt
    - composeApp/src/commonMain/kotlin/com/karakept/app/ui/screens/viewer/ViewerDialogs.kt

key-decisions:
  - "Details menu item always shown (not conditional on hero visibility) -- simpler and more discoverable"
  - "Scroll-to-top toggle placed in dedicated Behaviour tab (Tune icon) rather than always-visible below tabs"
  - "Scroll position restore fixed by moving scrollToTop() from applyFilter/clearFilter to resetPaginationAndLoad"

patterns-established:
  - "Behaviour tab in ReaderAppearanceBottomPanel for non-appearance reader settings"

requirements-completed: [READER-02, READER-03]

# Metrics
duration: ~45min
completed: 2026-03-23
---

# Phase 03 Plan 02: Reader UX Improvements Summary

**Overflow "Details" menu item, scroll-to-top FAB with visibility rules, Behaviour tab toggle, and scroll-position restore fix**

## Performance

- **Duration:** ~45 min (across two sessions with human-verify checkpoint)
- **Started:** 2026-03-23
- **Completed:** 2026-03-23
- **Tasks:** 3 (2 auto + 1 human-verify checkpoint)
- **Files modified:** 10

## Accomplishments

- Added "Details" item to reader overflow menu (always visible) linking to the bookmark details panel
- Implemented scroll-to-top SmallFloatingActionButton at bottom-left with AnimatedVisibility: hidden when hero visible, shown on scroll-up past hero or at article end
- Added scroll-to-top toggle in a new "Behaviour" tab (Tune icon) in ReaderAppearanceBottomPanel
- Fixed scroll position restore bug (READER-01) where list reset to top after returning from reader

## Task Commits

Each task was committed atomically:

1. **Task 1: Add conditional Details menu item and scroll-to-top button** - `fef4624` (feat), `a68bb5b` (fix: always show Details)
2. **Task 2: Add scroll-to-top toggle to ReaderAppearanceBottomPanel** - `1e44978` (feat), `6d5766d` (fix: move to Behaviour tab)
3. **Task 3 (extra): Fix scroll restore on back navigation** - `5d4d2aa` (fix)
4. **Task 3: Human-verify checkpoint** - approved, no commit needed

## Files Created/Modified

- `ViewerTopBar.kt` - Added "Details" DropdownMenuItem with Info icon, always present
- `ViewerScrollBehavior.kt` - Added `rememberScrollToTopVisibility` composable with derivedStateOf logic
- `BookmarkViewerContent.kt` - Added scroll-to-top SmallFloatingActionButton overlay with AnimatedVisibility
- `ReaderAppearanceBottomPanel.kt` - Added "Behaviour" tab with Tune icon containing scroll-to-top Switch toggle
- `BookmarkViewerScreenModel.kt` - Added setScrollToTopEnabled function
- `MainScreenModel.kt` - Scroll position restore fix (scrollToTop moved)
- `MainScreenModelPagination.kt` - Removed scrollToTop from applyFilter/clearFilter, added to resetPaginationAndLoad
- `MainScreenExpandedLayout.kt` - Wired scroll position state for desktop layout
- `ViewerContentPanels.kt` - Passed scrollToTopEnabled and onScrollToTopToggle to ReaderAppearanceBottomPanel
- `ViewerDialogs.kt` - Updated ReaderAppearanceBottomPanel call with new parameters

## Decisions Made

1. **Details always visible:** Removed the `isHeroVisible` conditional for the Details menu item. Always showing it is simpler, more discoverable, and avoids confusion when users expect it to be there.
2. **Behaviour tab for toggle:** Instead of placing the scroll-to-top toggle always-visible below tabs, it was moved into a dedicated "Behaviour" tab with a Tune icon. This keeps the panel organized and establishes a pattern for future non-appearance settings.
3. **Scroll restore fix location:** The `scrollToTop()` call was moved from `applyFilter`/`clearFilter` to the end of `resetPaginationAndLoad`, which is the correct place since that is where pagination state actually resets.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Details menu item made unconditional**
- **Found during:** Task 1
- **Issue:** Conditional `isHeroVisible` logic was fragile and confusing for users who expected "Details" to always be available
- **Fix:** Removed conditional, always show "Details" in overflow menu
- **Files modified:** ViewerTopBar.kt, BookmarkViewerContent.kt
- **Committed in:** a68bb5b

**2. [Rule 1 - Bug] Scroll-to-top toggle moved to Behaviour tab**
- **Found during:** Task 2
- **Issue:** Placing toggle below tabs made it always visible regardless of selected tab, cluttering the panel
- **Fix:** Created a dedicated "Behaviour" tab (Tune icon) to house the toggle
- **Files modified:** ReaderAppearanceBottomPanel.kt
- **Committed in:** 6d5766d

**3. [Rule 1 - Bug] Fixed scroll position restore on back navigation**
- **Found during:** Task 1 (testing)
- **Issue:** Bookmark list scrolled to top when returning from reader because `scrollToTop()` was called in `applyFilter`/`clearFilter` instead of `resetPaginationAndLoad`
- **Fix:** Moved `scrollToTop()` to `resetPaginationAndLoad` where pagination state actually resets
- **Files modified:** MainScreenModel.kt, MainScreenModelPagination.kt
- **Committed in:** 5d4d2aa

---

**Total deviations:** 3 auto-fixed (3 bug fixes)
**Impact on plan:** All fixes improved UX correctness. No scope creep -- all changes directly serve READER requirements.

## Issues Encountered

None -- implementation proceeded smoothly after deviations were addressed.

## User Setup Required

None - no external service configuration required.

## Known Stubs

None -- all features are fully wired with real data sources.

## Next Phase Readiness

- All four READER requirements (01-04) are complete across plans 01 and 02
- Phase 03 (reader-ux) is fully implemented and human-verified
- No blockers for subsequent phases

## Self-Check: PASSED

- SUMMARY.md: FOUND
- Commit fef4624: FOUND
- Commit a68bb5b: FOUND
- Commit 1e44978: FOUND
- Commit 6d5766d: FOUND
- Commit 5d4d2aa: FOUND

---
*Phase: 03-reader-ux*
*Completed: 2026-03-23*
