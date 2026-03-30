---
phase: 16-custom-layout-improvements
plan: 02
subsystem: ui
tags: [kotlin-multiplatform, compose-multiplatform, bookmark-layouts, display-config, url-display, description-toggle]

# Dependency graph
requires:
  - phase: 16-01
    provides: UrlPosition/UrlDisplayMode/DescriptionPosition enums, BookmarkLayout new fields, extractDomain utility
provides:
  - BookmarkListLayout accepts showDescription, descriptionPosition, showUrl, urlDisplayMode, urlPosition (5 new params)
  - BookmarkCardLayout accepts showDescription, showUrl, urlDisplayMode, urlPosition (4 new params)
  - URL display composable (UrlDisplay) with globe icon, domain extraction, and ellipsis truncation
  - Description conditionally shown/hidden in both renderers
  - MainScreenDisplayConfig carries all 5 new fields from activeLayout to render composables
  - BookmarkCompactListLayout deleted — all code paths use BookmarkListLayout
  - COMPACT_LIST removed from DefaultDisplaySettingsScreen UI options
affects:
  - 16-03 (LayoutEditorScreenModel preview can wire new fields end-to-end)

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "COMPACT_LIST handled via @Suppress(DEPRECATION) + merged branch — deprecated enum value kept for deserialization, never reachable via fromString()"
    - "UrlDisplay as private composable in BookmarkLayouts.kt — reused by both LIST and CARD renderers"
    - "descriptionPosition ABOVE_METADATA wired inside metadataContent lambda — renders before tags and metadata row"

key-files:
  created: []
  modified:
    - composeApp/src/commonMain/kotlin/com/karakept/app/ui/components/BookmarkLayouts.kt
    - composeApp/src/commonMain/kotlin/com/karakept/app/ui/screens/main/MainScreenScaffoldContent.kt
    - composeApp/src/commonMain/kotlin/com/karakept/app/ui/screens/MainScreen.kt
    - composeApp/src/commonMain/kotlin/com/karakept/app/ui/screens/main/BookmarkListContent.kt
    - composeApp/src/commonMain/kotlin/com/karakept/app/ui/components/BookmarkPlaceholderItem.kt
    - composeApp/src/commonMain/kotlin/com/karakept/app/ui/screens/settings/DefaultDisplaySettingsScreen.kt
    - composeApp/src/commonMain/kotlin/com/karakept/app/ui/screens/settings/LayoutEditorScreen.kt

key-decisions:
  - "UrlDisplay implemented as private composable (not local function) in BookmarkLayouts.kt — avoids @Composable local function restrictions and keeps it reusable between LIST and CARD"
  - "COMPACT_LIST in BookmarkListContent.kt merged with LIST via comma branch syntax with @Suppress(DEPRECATION) — clean when-exhaustive without duplication"
  - "Description ABOVE_METADATA placement handled inside metadataContent lambda to leverage existing ABOVE/BELOW/BESIDE MetadataPosition dispatch"

patterns-established:
  - "5-field display config extension pattern: add to enum model → add to MainScreenDisplayConfig → wire in MainScreen.kt remember block → add to BookmarkListContent signature → pass to renderers"

requirements-completed: [UX-02]

# Metrics
duration: 12min
completed: 2026-03-30
---

# Phase 16 Plan 02: Rendering Pipeline Summary

**5 new layout fields wired end-to-end from BookmarkLayout model through MainScreenDisplayConfig to BookmarkListLayout/BookmarkCardLayout, with URL display (globe icon + domain extraction) and conditional description rendering**

## Performance

- **Duration:** 12 min
- **Started:** 2026-03-30T15:37:19Z
- **Completed:** 2026-03-30T15:49:37Z
- **Tasks:** 2
- **Files modified:** 7

## Accomplishments

- Added 5 new params to `BookmarkListLayout` (showDescription, descriptionPosition, showUrl, urlDisplayMode, urlPosition) and 4 to `BookmarkCardLayout` (no descriptionPosition per D-03) — all with defaults for backward compat
- Implemented URL display with `UrlDisplay` composable: globe icon + `extractDomain()` extraction, ellipsis truncation, renders below title or inline in metadata row
- Implemented conditional description toggle in both renderers with `descriptionPosition` support (BELOW_TITLE / ABOVE_METADATA) in ListLayout
- Deleted `BookmarkCompactListLayout` entirely per D-01; all dispatch sites migrated to `BookmarkListLayout`
- Wired all 5 fields through `MainScreenDisplayConfig` → `BookmarkListContent` → renderers

## Task Commits

Each task was committed atomically:

1. **Task 1: Update renderers - add new params, implement description/URL display, delete CompactListLayout** - `4cf1184` (feat)
2. **Task 2: Wire display config pipeline and remove COMPACT_LIST from dispatch sites** - `ff848a2` (feat)

## Files Created/Modified

- `ui/components/BookmarkLayouts.kt` — Added 5/4 new params to both renderers, new `UrlDisplay` private composable, deleted `BookmarkCompactListLayout`
- `ui/screens/main/MainScreenScaffoldContent.kt` — Added 5 new fields to `MainScreenDisplayConfig` with DescriptionPosition/UrlDisplayMode/UrlPosition imports
- `ui/screens/MainScreen.kt` — Wires new fields in displayConfig remember block from activeLayout
- `ui/screens/main/BookmarkListContent.kt` — Added 5 new params, passes to renderers, merged COMPACT_LIST into LIST branch
- `ui/components/BookmarkPlaceholderItem.kt` — COMPACT_LIST maps to ListPlaceholder with @Suppress
- `ui/screens/settings/DefaultDisplaySettingsScreen.kt` — Removed COMPACT_LIST as user-selectable layout option
- `ui/screens/settings/LayoutEditorScreen.kt` — Fixed: replaced removed BookmarkCompactListLayout with BookmarkListLayout (Rule 3 auto-fix)

## Decisions Made

- `UrlDisplay` implemented as a private `@Composable` in BookmarkLayouts.kt rather than a local function — local composable functions have restrictions in Kotlin, using file-private avoids issues
- COMPACT_LIST merged into LIST branch in `BookmarkListContent.kt` using comma syntax: `LayoutType.LIST, @Suppress("DEPRECATION") LayoutType.COMPACT_LIST ->` — exhaustive when without code duplication
- Description ABOVE_METADATA position placed inside `metadataContent` lambda — this lambda is already dispatched to all three MetadataPosition locations (ABOVE/BELOW/BESIDE), so ABOVE_METADATA description renders naturally before tags/date whenever the metadata lambda is called

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Fixed LayoutEditorScreen.kt reference to deleted BookmarkCompactListLayout**
- **Found during:** Task 1 (after deleting BookmarkCompactListLayout from BookmarkLayouts.kt)
- **Issue:** `LayoutEditorScreen.kt` (Plan 03 scope) referenced `BookmarkCompactListLayout` in its preview `when` block and had an import for it — compile error blocked Task 1 completion
- **Fix:** Removed the import, replaced `COMPACT_LIST -> BookmarkCompactListLayout(...)` with `@Suppress("DEPRECATION") LayoutType.COMPACT_LIST -> BookmarkListLayout(...)` — mirrors what Plan 03 will need anyway
- **Files modified:** `ui/screens/settings/LayoutEditorScreen.kt`
- **Verification:** `./gradlew :composeApp:compileKotlinDesktop` succeeds with no errors
- **Committed in:** `4cf1184` (Task 1 commit, alongside BookmarkLayouts.kt)

---

**Total deviations:** 1 auto-fixed (Rule 3 - Blocking)
**Impact on plan:** Required to compile after deleting `BookmarkCompactListLayout`. Fix was correct per D-01 specification. No scope creep.

## Issues Encountered

- Worktree was on base branch without Plan 01 changes — required `git merge gsd/phase-16-custom-layout-improvements` before starting. Fast-forward merge succeeded cleanly.
- `api-client/build/generated` missing in worktree (openApiGenerate requires `karakeep-upstream/` submodule) — copied generated API client from main repo. Used `-x :api-client:openApiGenerate` for test runs.
- 18 pre-existing test failures confirmed unchanged: 6 integration tests (classMethod setup failures) and 12 `SmartListDeferredRefreshTest` failures — all fail identically on source branch.

## Known Stubs

None — all 5 new params are fully wired from model through to renderer, with real implementations (no placeholder text or empty fallbacks that affect UI rendering).

## Next Phase Readiness

- Plan 16-03 (LayoutEditorScreenModel update functions) can now reference the full set of new BookmarkLayout fields
- The preview composable in `LayoutEditorScreen.kt` already uses the fixed `BookmarkListLayout` call (COMPACT_LIST branch updated in this plan's Rule 3 fix)
- All new parameters have defaults — no existing call sites break
- No blockers

## Self-Check: PASSED

- All 7 modified source files exist on disk (verified by edit tool success)
- Commits 4cf1184 and ff848a2 exist in git log
- `./gradlew :composeApp:compileKotlinDesktop` BUILD SUCCESSFUL
- Test suite: 552 tests completed, 18 pre-existing failures (unchanged from source branch baseline)

---
*Phase: 16-custom-layout-improvements*
*Completed: 2026-03-30*
