---
phase: 01-println-cleanup
plan: 02
subsystem: data
tags: [logging, applogger, kotlin, repository]

# Dependency graph
requires:
  - phase: 01-println-cleanup
    provides: "AppLogger infrastructure and severity mapping conventions"
provides:
  - "Zero println calls in BookmarkRepository, HighlightRepository, ImageCacheManager, HtmlRenderer, ViewerScrollRestoration"
affects: []

# Tech tracking
tech-stack:
  added: []
  patterns: ["AppLogger severity mapping: .e() for failures, .w() for warnings, .d() for debug traces"]

key-files:
  created: []
  modified:
    - composeApp/src/commonMain/kotlin/com/karakept/app/data/repository/BookmarkRepository.kt
    - composeApp/src/commonMain/kotlin/com/karakept/app/data/repository/HighlightRepository.kt
    - composeApp/src/commonMain/kotlin/com/karakept/app/utils/ImageCacheManager.kt
    - composeApp/src/androidMain/kotlin/com/karakept/app/ui/components/HtmlRenderer.android.kt
    - composeApp/src/commonMain/kotlin/com/karakept/app/ui/screens/viewer/ViewerScrollRestoration.kt

key-decisions:
  - "Standardized tag names to full class names (BookmarkRepository, HighlightRepository) instead of shortened forms"
  - "Removed CONTENT: prefix from log messages since AppLogger adds its own tag prefix"

patterns-established:
  - "Use existing TAG companion constant when available (ImageCacheManager pattern)"

requirements-completed: [LOG-01]

# Metrics
duration: 3min
completed: 2026-03-21
---

# Phase 01 Plan 02: Remaining println Cleanup Summary

**Replaced 37 println calls across 5 files with structured AppLogger logging and removed 1 hot-path per-bookmark detail line**

## Performance

- **Duration:** 3 min
- **Started:** 2026-03-21T22:50:14Z
- **Completed:** 2026-03-21T22:53:30Z
- **Tasks:** 2
- **Files modified:** 5

## Accomplishments
- Converted 16 println calls to AppLogger in BookmarkRepository.kt (8 error, 8 debug)
- Removed 1 hot-path per-bookmark detail line in getBookmarksPaged per D-04/D-05
- Converted 13 println calls to AppLogger in HighlightRepository.kt (1 error, 12 debug)
- Converted 5 println calls in ImageCacheManager.kt using existing TAG constant (3 error, 2 warning)
- Converted 1 println in HtmlRenderer.android.kt to AppLogger.d and 1 in ViewerScrollRestoration.kt to AppLogger.w

## Task Commits

Each task was committed atomically:

1. **Task 1: Replace println calls in BookmarkRepository.kt and HighlightRepository.kt** - `04b8560` (refactor)
2. **Task 2: Replace println calls in ImageCacheManager.kt, HtmlRenderer.android.kt, and ViewerScrollRestoration.kt** - `19e7394` (refactor)

## Files Created/Modified
- `composeApp/src/commonMain/kotlin/com/karakept/app/data/repository/BookmarkRepository.kt` - 16 println to AppLogger, 1 hot-path line removed
- `composeApp/src/commonMain/kotlin/com/karakept/app/data/repository/HighlightRepository.kt` - 13 println to AppLogger, standardized tag from "HighlightRepo" to "HighlightRepository"
- `composeApp/src/commonMain/kotlin/com/karakept/app/utils/ImageCacheManager.kt` - 5 println to AppLogger using TAG constant
- `composeApp/src/androidMain/kotlin/com/karakept/app/ui/components/HtmlRenderer.android.kt` - 1 println to AppLogger.d
- `composeApp/src/commonMain/kotlin/com/karakept/app/ui/screens/viewer/ViewerScrollRestoration.kt` - 1 println to AppLogger.w, added AppLogger import

## Decisions Made
- Standardized tag names to full class names (BookmarkRepository, HighlightRepository) for consistency with D-09 convention
- Removed prefixes like "CONTENT:", "ScrollGuard:", "HighlightRepository:" from messages since AppLogger tag provides context
- Used existing TAG companion constant in ImageCacheManager rather than string literals

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Standardized inconsistent HighlightRepository tag**
- **Found during:** Task 1 (HighlightRepository.kt)
- **Issue:** Existing AppLogger call in syncHighlightsForBookmark used shortened tag "HighlightRepo" instead of full "HighlightRepository"
- **Fix:** Changed to "HighlightRepository" for consistency with the rest of the file
- **Files modified:** HighlightRepository.kt
- **Committed in:** 04b8560

---

**Total deviations:** 1 auto-fixed (1 bug)
**Impact on plan:** Minor consistency fix, no scope creep.

## Issues Encountered
None

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- All 5 files in this plan now have zero println calls
- Combined with plan 01-01, the println cleanup phase is complete
- Ready for phase 02 (file trimming) or verification

---
*Phase: 01-println-cleanup*
*Completed: 2026-03-21*

## Self-Check: PASSED
