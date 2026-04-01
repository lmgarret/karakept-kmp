---
phase: 16-custom-layout-improvements
plan: 04
subsystem: ui
tags: [compose, bookmark-layout, url-display, favicon, coil, kmp]

# Dependency graph
requires:
  - phase: 16-custom-layout-improvements
    provides: UrlDisplay composable, BookmarkLayout data model, LayoutEditorScreen with URL sub-options
provides:
  - UrlIconMode enum with GLOBE_ONLY/FAVICON values and fromString() fallback
  - BookmarkLayout.urlIconMode field (GLOBE_ONLY default)
  - UrlDisplay renders favicon via FaviconUtils when FAVICON mode, globe fallback
  - Editor UI shows URL icon mode sub-options when URL toggle is on
  - Preview in editor reflects selected URL icon mode
affects: [layout-rendering, layout-editor, bookmark-list]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Overlay favicon over globe icon in a Box: globe always visible as fallback, AsyncImage on top for FAVICON mode"
    - "UrlIconMode follows same enum pattern as UrlDisplayMode/UrlPosition: entries.find { it.name == value } ?: default"

key-files:
  created:
    - composeApp/src/commonMain/kotlin/com/karakept/app/data/model/UrlIconMode.kt
    - composeApp/src/commonTest/kotlin/com/karakept/app/data/model/UrlIconModeTest.kt
  modified:
    - composeApp/src/commonMain/kotlin/com/karakept/app/data/model/BookmarkLayout.kt
    - composeApp/src/commonMain/kotlin/com/karakept/app/ui/components/BookmarkLayouts.kt
    - composeApp/src/commonMain/kotlin/com/karakept/app/ui/screens/settings/LayoutEditorScreen.kt
    - composeApp/src/commonMain/kotlin/com/karakept/app/ui/screens/main/MainScreenScaffoldContent.kt
    - composeApp/src/commonMain/kotlin/com/karakept/app/ui/screens/MainScreen.kt
    - composeApp/src/commonMain/kotlin/com/karakept/app/ui/screens/main/BookmarkListContent.kt
    - composeApp/src/commonTest/kotlin/com/karakept/app/ui/screens/settings/LayoutEditorScreenModelTest.kt

key-decisions:
  - "Globe icon always rendered behind favicon AsyncImage using Box overlay — globe acts as fallback without conditional error handler"
  - "UrlIconMode follows established enum pattern with fromString() fallback to GLOBE_ONLY"
  - "urlIconMode stored as String in BookmarkLayout (matching urlDisplayMode/urlPosition pattern) for JSON serialization compat"
  - "Existing layouts with no urlIconMode field deserialize with GLOBE_ONLY default — backward compat preserved"

patterns-established:
  - "URL sub-options (display mode, icon mode) inline in Show card — two options each don't warrant separate section"
  - "Box overlay pattern for icon-with-fallback: base icon + AsyncImage on top"

requirements-completed: [UX-02]

# Metrics
duration: 7min
completed: 2026-03-30
---

# Phase 16 Plan 04: Configurable URL Icon Mode Summary

**UrlIconMode enum (GLOBE_ONLY/FAVICON) wired end-to-end: BookmarkLayout -> MainScreenDisplayConfig -> BookmarkListContent -> UrlDisplay composable with FaviconUtils favicon loading and globe fallback**

## Performance

- **Duration:** 7 min
- **Started:** 2026-03-30T20:25:38Z
- **Completed:** 2026-03-30T20:32:53Z
- **Tasks:** 1 of 2 (Task 2 is human-verify checkpoint)
- **Files modified:** 9

## Accomplishments
- New `UrlIconMode` enum with GLOBE_ONLY/FAVICON values and fromString() fallback (GLOBE_ONLY default)
- `BookmarkLayout.urlIconMode` field added with default for backward-compat JSON deserialization
- `UrlDisplay` composable updated to render site favicon via `FaviconUtils.getFaviconUrl()` when FAVICON mode, globe icon always present as fallback
- Full pipeline wired: BookmarkLayout -> MainScreenDisplayConfig.urlIconMode -> BookmarkListContent -> BookmarkCardLayout/BookmarkListLayout -> UrlDisplay
- Editor UI shows "Globe icon" and "Site favicon" radio options inline within the URL section when URL toggle is enabled
- Preview in `LayoutEditorScreen` reflects the selected URL icon mode
- 5 new tests pass: 3 `UrlIconModeTest` + 2 `LayoutEditorScreenModelTest`

## Task Commits

1. **Task 1: Add UrlIconMode enum, BookmarkLayout field, and wire full pipeline** - `2a293c8` (feat)

## Files Created/Modified
- `composeApp/src/commonMain/kotlin/com/karakept/app/data/model/UrlIconMode.kt` - New enum GLOBE_ONLY/FAVICON with fromString()
- `composeApp/src/commonTest/kotlin/com/karakept/app/data/model/UrlIconModeTest.kt` - 3 enum unit tests
- `composeApp/src/commonMain/kotlin/com/karakept/app/data/model/BookmarkLayout.kt` - Added urlIconMode field
- `composeApp/src/commonMain/kotlin/com/karakept/app/ui/components/BookmarkLayouts.kt` - UrlDisplay supports urlIconMode; both renderers accept new param
- `composeApp/src/commonMain/kotlin/com/karakept/app/ui/screens/settings/LayoutEditorScreen.kt` - updateUrlIconMode(), editor UI radio options, PreviewBookmarkItem updated
- `composeApp/src/commonMain/kotlin/com/karakept/app/ui/screens/main/MainScreenScaffoldContent.kt` - urlIconMode field in MainScreenDisplayConfig; pass-through to BookmarkListContent
- `composeApp/src/commonMain/kotlin/com/karakept/app/ui/screens/MainScreen.kt` - Wire activeLayout.urlIconMode into displayConfig
- `composeApp/src/commonMain/kotlin/com/karakept/app/ui/screens/main/BookmarkListContent.kt` - urlIconMode param added, passed to both renderers
- `composeApp/src/commonTest/kotlin/com/karakept/app/ui/screens/settings/LayoutEditorScreenModelTest.kt` - 2 new ScreenModel tests

## Decisions Made
- Globe icon rendered behind favicon AsyncImage in a Box: the globe always shows through if the favicon fails to load, providing natural fallback without needing error callbacks
- UrlIconMode uses the established `entries.find { it.name == value } ?: GLOBE_ONLY` pattern, consistent with all other layout enums in the project

## Deviations from Plan
None - plan executed exactly as written.

## Issues Encountered
None.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- Task 1 complete. Awaiting human visual verification (Task 2 checkpoint).
- After approval: Phase 16 plan 04 fully closed. UX-02 requirement satisfied.

---
*Phase: 16-custom-layout-improvements*
*Completed: 2026-03-30*
