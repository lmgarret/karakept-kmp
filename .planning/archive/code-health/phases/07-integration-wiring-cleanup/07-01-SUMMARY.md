---
phase: 07-integration-wiring-cleanup
plan: 01
subsystem: ui
tags: [ksoup, caching, compose, html-rendering, performance]

# Dependency graph
requires:
  - phase: 06-performance-optimization
    provides: ParsedDocumentCache and getCachedOrParseDocument in BookmarkViewerScreenModel
provides:
  - ParsedDocumentCache wired into NativeHtmlRenderer composable chain via lambda threading
affects: []

# Tech tracking
tech-stack:
  added: []
  patterns: [optional lambda threading for cache injection without tight coupling]

key-files:
  created: []
  modified:
    - composeApp/src/commonMain/kotlin/com/karakept/app/ui/components/reader/NativeHtmlRenderer.kt
    - composeApp/src/commonMain/kotlin/com/karakept/app/ui/components/HtmlContent.kt
    - composeApp/src/commonMain/kotlin/com/karakept/app/ui/screens/viewer/ContentBodySection.kt
    - composeApp/src/commonMain/kotlin/com/karakept/app/ui/screens/BookmarkViewerContent.kt

key-decisions:
  - "Nullable lambda with null default preserves backward compatibility for all callers outside the viewer"

patterns-established:
  - "Optional cache injection via lambda: callers pass cache-backed parsing, callees fall back to direct parsing when lambda is null"

requirements-completed: [PERF-02]

# Metrics
duration: 2min
completed: 2026-03-21
---

# Phase 07 Plan 01: Cache Wiring Summary

**ParsedDocumentCache wired into NativeHtmlRenderer via optional lambda threading from BookmarkViewerContent through ContentBodySection and HtmlContent**

## Performance

- **Duration:** 2 min
- **Started:** 2026-03-21T19:10:20Z
- **Completed:** 2026-03-21T19:11:55Z
- **Tasks:** 2
- **Files modified:** 4

## Accomplishments
- NativeHtmlRenderer and HtmlContent accept optional parseDocument lambda for cache-backed Ksoup parsing
- ContentBodySection threads the lambda from BookmarkViewerContent to HtmlContent
- BookmarkViewerContent passes getCachedOrParseDocument as the parseDocument lambda, closing the PERF-02 integration gap
- All existing callers unaffected via null default

## Task Commits

Each task was committed atomically:

1. **Task 1: Add parseDocument lambda to NativeHtmlRenderer and HtmlContent** - `3617056` (feat)
2. **Task 2: Wire cache lambda from BookmarkViewerContent through ContentBodySection** - `c4c3e7d` (feat)

## Files Created/Modified
- `NativeHtmlRenderer.kt` - Added parseDocument parameter, uses it before falling back to Ksoup.parse
- `HtmlContent.kt` - Added parseDocument parameter, passes to NativeHtmlRenderer in READER branch
- `ContentBodySection.kt` - Added parseDocument parameter, passes to HtmlContent
- `BookmarkViewerContent.kt` - Passes getCachedOrParseDocument lambda as parseDocument

## Decisions Made
- Nullable lambda with null default preserves backward compatibility for all callers outside the viewer

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
None

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- Cache wiring complete; re-navigating to a previously viewed bookmark will reuse the cached parsed Document
- Plan 07-02 (println cleanup) is independent and ready to execute

---
*Phase: 07-integration-wiring-cleanup*
*Completed: 2026-03-21*
