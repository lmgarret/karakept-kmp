---
phase: 03-code-splitting
plan: 01
subsystem: data
tags: [kotlin, repository, code-splitting, extension-functions]

# Dependency graph
requires:
  - phase: 02-concurrency-hardening
    provides: "Mutex-protected mutation sites in repositories"
provides:
  - "BookmarkRepository.kt under 500 lines with BookmarkSyncPipeline extracted"
  - "BookmarkActionsRepository.kt under 500 lines with sync processing and batch ops extracted"
  - "SettingsRepository.kt under 500 lines with mutations, backup API, and layout CRUD extracted"
affects: [03-code-splitting, 04-testing]

# Tech tracking
tech-stack:
  added: []
  patterns: [extension-functions-for-code-splitting, internal-visibility-for-cross-file-access]

key-files:
  created:
    - composeApp/src/commonMain/kotlin/com/karakept/app/data/repository/BookmarkActionsRepositorySync.kt
    - composeApp/src/commonMain/kotlin/com/karakept/app/data/repository/SettingsRepositoryMutations.kt
  modified:
    - composeApp/src/commonMain/kotlin/com/karakept/app/data/repository/BookmarkActionsRepository.kt
    - composeApp/src/commonMain/kotlin/com/karakept/app/data/repository/SettingsRepository.kt

key-decisions:
  - "Extracted pullReadingProgressFromServer to sync file (server-facing operation) to bring BAR under 500 lines"
  - "Moved currentSettings/restoreSettings to mutations file (backup API is a mutation concern) to bring SR under 500 lines"
  - "Used internal visibility with private set for highlightDao to maintain encapsulation while enabling cross-file access"

patterns-established:
  - "Extension function extraction: fun ClassName.methodName() in separate file, same package"
  - "private -> internal visibility change for fields accessed by extension functions"

requirements-completed: [SPLIT-04]

# Metrics
duration: 10min
completed: 2026-03-21
---

# Phase 03 Plan 01: Repository Code Splitting Summary

**Split 3 repository files from ~830-988 lines to under 500 each using Kotlin extension functions, extracting sync processing, batch ops, and mutations**

## Performance

- **Duration:** 10 min
- **Started:** 2026-03-21T09:35:00Z
- **Completed:** 2026-03-21T09:45:37Z
- **Tasks:** 2 (Task 1 skipped -- already complete)
- **Files modified:** 4 (2 modified, 2 created)

## Accomplishments
- BookmarkActionsRepository reduced from 831 to 489 lines by extracting sync processing (executeAction, processPendingActions, pullReadingProgressFromServer) to BookmarkActionsRepositorySync.kt
- SettingsRepository reduced from 988 to 491 lines by extracting all set* methods, update helpers, backup API, and layout CRUD to SettingsRepositoryMutations.kt
- All three target repositories now under 500 lines: BookmarkRepository (454), BookmarkActionsRepository (489), SettingsRepository (491)

## Task Commits

Each task was committed atomically:

1. **Task 1: Extract BookmarkSyncPipeline from BookmarkRepository** - `ebfd878` (refactor) -- already committed in prior session, SKIPPED
2. **Task 2: Extract batch/sync from BookmarkActionsRepository and mutations from SettingsRepository** - `f7186aa` (refactor)

## Files Created/Modified
- `BookmarkActionsRepository.kt` - Reduced to 489 lines; changed serverRepository, highlightDao, actionMutex to internal visibility
- `BookmarkActionsRepositorySync.kt` (NEW, 355 lines) - Extension functions for sync processing: processPendingActions, executeAction, pullReadingProgressFromServer
- `SettingsRepository.kt` - Reduced to 491 lines; changed dataStore, settingsJson, all preference keys, and readXxxSettings helpers to internal visibility
- `SettingsRepositoryMutations.kt` (NEW, 511 lines) - Extension functions for all set* methods, update helpers, backup API (currentSettings/restoreSettings), layout CRUD

## Decisions Made
- Extracted pullReadingProgressFromServer to sync file rather than keeping in main file -- it is a server-facing operation that fits with sync processing
- Moved currentSettings/restoreSettings to mutations file -- backup API is a mutation concern (restoreSettings writes to dataStore) and this was needed to bring SettingsRepository under 500 lines
- Used `internal var highlightDao: ... private set` pattern to expose field for cross-file read while keeping write access private

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Moved pullReadingProgressFromServer and backup API to bring files under 500 lines**
- **Found during:** Task 2
- **Issue:** After extracting only the methods specified in the plan, BookmarkActionsRepository was 542 lines and SettingsRepository was 651 lines -- both still over the 500-line target
- **Fix:** Additionally extracted pullReadingProgressFromServer (server-facing sync operation) to BookmarkActionsRepositorySync.kt, and currentSettings/restoreSettings (backup API) to SettingsRepositoryMutations.kt
- **Files modified:** All 4 task files
- **Committed in:** f7186aa

---

**Total deviations:** 1 auto-fixed (blocking -- target line count not met with plan-specified extractions alone)
**Impact on plan:** Additional extractions were necessary to meet the under-500-lines requirement. All moved methods are cohesive with their destination files.

## Issues Encountered
None

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- All 3 repository files under 500 lines, ready for Phase 04 testing
- BookmarkActionsRepositoryBatch.kt (179 lines) was already extracted in a prior partial commit
- BookmarkSyncPipeline.kt (545 lines) was already extracted in a prior commit
- No constructor signature changes -- Koin DI wiring unchanged

## Self-Check: PASSED

- All 5 key files exist
- Commits ebfd878 and f7186aa found in history
- BookmarkRepository.kt: 454 lines (under 500)
- BookmarkActionsRepository.kt: 489 lines (under 500)
- SettingsRepository.kt: 491 lines (under 500)

---
*Phase: 03-code-splitting*
*Completed: 2026-03-21*
