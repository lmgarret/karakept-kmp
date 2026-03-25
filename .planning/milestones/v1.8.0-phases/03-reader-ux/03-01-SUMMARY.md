---
phase: 03-reader-ux
plan: 01
subsystem: ui
tags: [settings, datastore, voyager, scroll-state, compose]

# Dependency graph
requires: []
provides:
  - scrollToTopEnabled setting flow from StoredReaderSettings through SettingsRepository to BookmarkViewerScreenModel
  - Fixed bookmark list scroll position persistence across reader navigation
affects: [03-02]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Hoist LazyListState position into ScreenModel for cross-navigation persistence"

key-files:
  created: []
  modified:
    - composeApp/src/commonMain/kotlin/com/karakept/app/data/repository/StoredSettings.kt
    - composeApp/src/commonMain/kotlin/com/karakept/app/data/repository/SettingsRepository.kt
    - composeApp/src/commonMain/kotlin/com/karakept/app/data/repository/SettingsRepositoryMutations.kt
    - composeApp/src/commonMain/kotlin/com/karakept/app/ui/screens/BookmarkViewerScreenModel.kt
    - composeApp/src/commonTest/kotlin/com/karakept/app/data/repository/StoredSettingsSerializationTest.kt
    - composeApp/src/commonMain/kotlin/com/karakept/app/ui/screens/MainScreen.kt
    - composeApp/src/commonMain/kotlin/com/karakept/app/ui/screens/MainScreenModel.kt

key-decisions:
  - "Hoisted scroll position into MainScreenModel (Koin singleton) rather than relying solely on rememberSaveable which can lose state across Voyager object Screen navigation"
  - "scrollToTopEnabled defaults to true per user decision in READER-04"

patterns-established:
  - "Hoist LazyListState position into Koin singleton ScreenModel for scroll persistence across Voyager push/pop"

requirements-completed: [READER-04, READER-01]

# Metrics
duration: 29min
completed: 2026-03-23
---

# Phase 03 Plan 01: Settings Plumbing & Scroll Fix Summary

**scrollToTopEnabled setting pipeline from StoredReaderSettings to BookmarkViewerScreenModel with serialization tests, plus bookmark list scroll position persistence fix via hoisted state in MainScreenModel**

## Performance

- **Duration:** 29 min
- **Started:** 2026-03-23T12:47:48Z
- **Completed:** 2026-03-23T13:17:06Z
- **Tasks:** 2
- **Files modified:** 7

## Accomplishments
- Added scrollToTopEnabled Boolean field to StoredReaderSettings with default true, exposed as Flow in SettingsRepository, mutation in SettingsRepositoryMutations, and StateFlow in BookmarkViewerScreenModel
- Added 4 serialization tests covering default value, JSON round-trip, missing-field fallback, and explicit false decoding
- Updated existing non-default round-trip test to include showTagsInViewer and scrollToTopEnabled
- Fixed bookmark list scroll position reset bug (#152) by hoisting scroll index/offset into MainScreenModel singleton

## Task Commits

Each task was committed atomically:

1. **Task 1: Add scrollToTopEnabled setting and serialization tests** - `1c3b7d7` (feat)
2. **Task 2: Fix bookmark list scroll position restore on reader close** - `70531a5` (fix)

## Files Created/Modified
- `composeApp/src/commonMain/kotlin/com/karakept/app/data/repository/StoredSettings.kt` - Added scrollToTopEnabled field to StoredReaderSettings
- `composeApp/src/commonMain/kotlin/com/karakept/app/data/repository/SettingsRepository.kt` - Added scrollToTopEnabled derived Flow
- `composeApp/src/commonMain/kotlin/com/karakept/app/data/repository/SettingsRepositoryMutations.kt` - Added setScrollToTopEnabled mutation
- `composeApp/src/commonMain/kotlin/com/karakept/app/ui/screens/BookmarkViewerScreenModel.kt` - Added scrollToTopEnabled StateFlow
- `composeApp/src/commonTest/kotlin/com/karakept/app/data/repository/StoredSettingsSerializationTest.kt` - Added 4 scrollToTopEnabled tests, updated non-default round-trip test
- `composeApp/src/commonMain/kotlin/com/karakept/app/ui/screens/MainScreen.kt` - Initialize LazyListState from hoisted position, add snapshotFlow to persist position
- `composeApp/src/commonMain/kotlin/com/karakept/app/ui/screens/MainScreenModel.kt` - Added savedScrollIndex/savedScrollOffset and saveScrollPosition()

## Decisions Made
- Hoisted scroll position into MainScreenModel (Koin singleton) rather than relying solely on rememberSaveable, because Voyager object Screen singletons can lose saveable state on push/pop and background sync replaces the bookmark list while the reader is open
- scrollToTopEnabled defaults to true per user decision in READER-04
- Did NOT add scrollToTopEnabled to BackupSettings/currentSettings()/restoreSettings() following the same pattern as showTagsInViewer

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] JDK 25 incompatible with Kotlin compiler**
- **Found during:** Task 1 (test execution)
- **Issue:** System JDK 25.0.2 causes IllegalArgumentException in Kotlin compiler JavaVersion parser
- **Fix:** Used JDK 21 via JAVA_HOME override for all Gradle commands
- **Files modified:** None (runtime configuration only)
- **Verification:** All tests compile and pass with JDK 21

**2. [Rule 3 - Blocking] Gradle task name mismatch**
- **Found during:** Task 1 (test execution)
- **Issue:** Plan specified `jvmTest` task but project uses `desktopTest` for desktop target tests
- **Fix:** Used `:composeApp:desktopTest --tests` instead of `:composeApp:jvmTest --tests`
- **Files modified:** None (command adjustment only)
- **Verification:** Tests run successfully with correct task name

---

**Total deviations:** 2 auto-fixed (2 blocking)
**Impact on plan:** Both were environment/tooling issues, no code scope changes.

## Issues Encountered
- 6 integration tests fail pre-existingly (ApiClientIntegrationTest, BookmarkMutationIntegrationTest, BookmarkSyncIntegrationTest, HighlightRepositoryIntegrationTest, ListRepositoryIntegrationTest, ReadingProgressIntegrationTest) -- these require server connectivity and are unrelated to this plan

## Known Stubs

None - all data flows are fully wired.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness
- scrollToTopEnabled setting is fully plumbed and ready for Plan 02 to build the scroll-to-top button UI
- Bookmark list scroll position fix is independent and complete

---
*Phase: 03-reader-ux*
*Completed: 2026-03-23*
