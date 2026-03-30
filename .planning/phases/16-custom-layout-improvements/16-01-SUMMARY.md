---
phase: 16-custom-layout-improvements
plan: 01
subsystem: data-model
tags: [kotlin-multiplatform, data-model, enums, serialization, ktor, tdd]

# Dependency graph
requires: []
provides:
  - UrlPosition enum (BELOW_TITLE, METADATA_ROW) with fromString() fallback
  - UrlDisplayMode enum (DOMAIN_ONLY, FULL_URL) with fromString() fallback
  - DescriptionPosition enum (BELOW_TITLE, ABOVE_METADATA) with fromString() fallback
  - LayoutType.fromString("COMPACT_LIST") now returns LIST (migration D-01/D-02)
  - LayoutType.COMPACT_LIST deprecated but retained for deserialization compatibility
  - BookmarkLayout.showDescription, descriptionPosition, showUrl, urlDisplayMode, urlPosition fields with backward-compatible defaults
  - BUILTIN_COMPACT migrated to layoutType=LIST with showDescription=false
  - extractDomain() utility in ui/utils/UrlUtils.kt (strips scheme, www, port)
  - 26 new tests across 6 test files — all passing
affects:
  - 16-02 (LayoutEditorScreen uses new fields and enums)
  - 16-03 (LayoutEditorScreenModel update functions use new fields)
  - BookmarkCardRenderer or equivalent composable (showDescription, showUrl rendering)
  - Any code referencing COMPACT_LIST layoutType in stored user data

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "enum withfromString() using entries.find { it.name == value } ?: DEFAULT — consistent fallback pattern across all new enums"
    - "KMP URL parsing using Ktor io.ktor.http.Url (not java.net.URI) for commonMain compatibility"
    - "Scheme guard before Ktor Url parsing: only parse if startsWith http:// or https://, return input as-is otherwise"

key-files:
  created:
    - composeApp/src/commonMain/kotlin/com/karakept/app/data/model/UrlPosition.kt
    - composeApp/src/commonMain/kotlin/com/karakept/app/data/model/UrlDisplayMode.kt
    - composeApp/src/commonMain/kotlin/com/karakept/app/data/model/DescriptionPosition.kt
    - composeApp/src/commonMain/kotlin/com/karakept/app/ui/utils/UrlUtils.kt
    - composeApp/src/commonTest/kotlin/com/karakept/app/data/model/LayoutTypeTest.kt
    - composeApp/src/commonTest/kotlin/com/karakept/app/data/model/UrlPositionTest.kt
    - composeApp/src/commonTest/kotlin/com/karakept/app/data/model/UrlDisplayModeTest.kt
    - composeApp/src/commonTest/kotlin/com/karakept/app/data/model/DescriptionPositionTest.kt
    - composeApp/src/commonTest/kotlin/com/karakept/app/ui/utils/UrlUtilsTest.kt
    - composeApp/src/commonTest/kotlin/com/karakept/app/data/model/BookmarkLayoutTest.kt
  modified:
    - composeApp/src/commonMain/kotlin/com/karakept/app/data/model/LayoutType.kt
    - composeApp/src/commonMain/kotlin/com/karakept/app/data/model/BookmarkLayout.kt

key-decisions:
  - "Use Ktor io.ktor.http.Url (not java.net.URI) for extractDomain() — consistent with existing FaviconUtils pattern, works across all KMP targets"
  - "Scheme guard in extractDomain() prevents Ktor from misinterpreting non-URL strings as localhost — discovered via RED phase test failure"
  - "COMPACT_LIST enum value kept with @Deprecated annotation for backward deserialization compat — fromString never returns it"
  - "New BookmarkLayout fields placed before isBuiltIn to maintain logical parameter grouping"

patterns-established:
  - "TDD RED-GREEN: test files first (compile failure), then minimal implementation"
  - "Enum fromString() pattern: entries.find { it.name == value } ?: DEFAULT (matching MetadataPosition)"

requirements-completed: [UX-02]

# Metrics
duration: 7min
completed: 2026-03-30
---

# Phase 16 Plan 01: Data Model Foundation Summary

**3 new enums (UrlPosition, UrlDisplayMode, DescriptionPosition), LayoutType COMPACT_LIST->LIST migration, 5 new BookmarkLayout fields, and extractDomain() utility — all TDD with 26 passing tests**

## Performance

- **Duration:** 7 min
- **Started:** 2026-03-30T15:26:44Z
- **Completed:** 2026-03-30T15:33:38Z
- **Tasks:** 2
- **Files modified:** 12

## Accomplishments

- Created 3 new position/mode enums following the established MetadataPosition pattern with graceful `fromString()` fallback
- Migrated LayoutType: `fromString("COMPACT_LIST")` now returns `LIST`, with `COMPACT_LIST` deprecated but retained for deserialization compat
- Added 5 new fields to BookmarkLayout with backward-compatible defaults — existing serialized layouts deserialize cleanly
- Created `extractDomain()` URL utility using Ktor (consistent with FaviconUtils) — discovered and fixed scheme-guard edge case in RED phase
- Updated BUILTIN_COMPACT to use `layoutType=LIST` with `showDescription=false` for compact display

## Task Commits

Each task was committed atomically:

1. **Task 1: New enums + LayoutType migration + URL utility (TDD)** - `a197263` (feat)
2. **Task 2: BookmarkLayout new fields + BUILTIN_COMPACT migration (TDD)** - `f22fdc6` (feat)

_Note: TDD tasks — tests written first (RED fail), then implementation (GREEN pass)_

## Files Created/Modified

- `data/model/UrlPosition.kt` — New enum: BELOW_TITLE, METADATA_ROW with fromString()
- `data/model/UrlDisplayMode.kt` — New enum: DOMAIN_ONLY, FULL_URL with fromString()
- `data/model/DescriptionPosition.kt` — New enum: BELOW_TITLE, ABOVE_METADATA with fromString()
- `data/model/LayoutType.kt` — COMPACT_LIST mapped to LIST in fromString(), @Deprecated on value
- `data/model/BookmarkLayout.kt` — 5 new fields added; BUILTIN_COMPACT migrated to LIST type
- `ui/utils/UrlUtils.kt` — extractDomain() using Ktor Url with scheme guard
- `commonTest/.../LayoutTypeTest.kt` — 5 tests for migration and enum values
- `commonTest/.../UrlPositionTest.kt` — 3 tests for fromString()
- `commonTest/.../UrlDisplayModeTest.kt` — 3 tests for fromString()
- `commonTest/.../DescriptionPositionTest.kt` — 3 tests for fromString()
- `commonTest/.../UrlUtilsTest.kt` — 6 tests for extractDomain() edge cases
- `commonTest/.../BookmarkLayoutTest.kt` — 6 tests for new fields, builtins, serialization

## Decisions Made

- Used Ktor `io.ktor.http.Url` (not `java.net.URI`) for `extractDomain()` — matches existing `FaviconUtils` pattern and works across all KMP targets without platform-specific code
- Added a scheme guard (`startsWith("http://") || startsWith("https://")`) before Ktor URL parsing — Ktor parses `"not-a-url"` as having host `"localhost"`, breaking the test expectation; the guard returns the input string as-is for non-URLs
- `COMPACT_LIST` kept as enum value with `@Deprecated` annotation — any existing persisted user data referencing `"COMPACT_LIST"` string still deserializes without crash; `fromString()` redirects it to `LIST` transparently

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] extractDomain() returned "localhost" for non-URL input**
- **Found during:** Task 1 (GREEN phase test run)
- **Issue:** Plan specified `java.net.URI` but KMP uses Ktor; Ktor's `Url("not-a-url")` parses without throwing and returns `host = "localhost"`, causing test failure
- **Fix:** Added scheme guard — only invoke Ktor URL parsing when string starts with `http://` or `https://`; return input as-is for anything else
- **Files modified:** `composeApp/src/commonMain/kotlin/com/karakept/app/ui/utils/UrlUtils.kt`
- **Verification:** All 6 UrlUtilsTest tests pass including the non-URL fallback case
- **Committed in:** `a197263` (Task 1 commit)

---

**Total deviations:** 1 auto-fixed (Rule 1 - Bug)
**Impact on plan:** Bug fix was necessary for correctness. The implementation uses Ktor instead of java.net.URI for KMP compatibility (plan noted this as a valid fallback if java.net.URI caused issues). No scope creep.

## Issues Encountered

- JDK path in plan's verification command (`/Library/Java/JavaVirtualMachines/jdk-21.jdk/`) was stale; actual JDK 21 is at `/opt/homebrew/Cellar/openjdk@21/21.0.10/libexec/openjdk.jdk/`. Updated JAVA_HOME for all test runs.

## Known Stubs

None — all new data model fields have concrete default values and are wired correctly.

## Next Phase Readiness

- All model contracts established: 3 enums, updated LayoutType, 5 new BookmarkLayout fields
- Plan 16-02 (LayoutEditorScreen UI) can now reference `UrlPosition`, `UrlDisplayMode`, `DescriptionPosition`, and the new `BookmarkLayout` fields directly
- Plan 16-03 (LayoutEditorScreenModel) can use these types for update function signatures
- No blockers

## Self-Check: PASSED

- All 12 source/test files exist on disk
- Commits a197263 and f22fdc6 verified in git log

---
*Phase: 16-custom-layout-improvements*
*Completed: 2026-03-30*
