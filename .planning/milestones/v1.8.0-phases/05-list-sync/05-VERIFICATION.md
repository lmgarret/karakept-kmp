---
phase: 05-list-sync
verified: 2026-03-23T19:55:00Z
status: human_needed
score: 5/5 must-haves verified
re_verification:
  previous_status: gaps_found
  previous_score: 4/5
  gaps_closed:
    - "Unit tests exist for removeBookmarkFromList conditional filtering behavior AND call production code (all 3 tests can now serve as a regression gate for LIST-01)"
  gaps_remaining: []
  regressions: []
human_verification:
  - test: "Remove a bookmark from a list while the list is open"
    expected: "The bookmark disappears from the list view immediately without requiring a refresh"
    why_human: "Optimistic UI behavior requires the running app and visual inspection"
  - test: "Enable 'sync offline' on a list and trigger a sync"
    expected: "Bookmarks in that list with no content have their content downloaded (readingTimeMinutes > 0 after sync)"
    why_human: "Requires device running the app, a real Karakeep server, and inspecting DB state after sync"
---

# Phase 05: List Sync Verification Report

**Phase Goal:** Fix two list-management bugs: (1) bookmark removal not reflected visually in the current list view (LIST-01), and (2) per-list offline sync toggle saving the setting but never triggering content download (LIST-02).
**Verified:** 2026-03-23T19:55:00Z
**Status:** human_needed
**Re-verification:** Yes — after gap closure (Plan 02)

## Re-verification Summary

Previous verification (2026-03-23T19:14:57Z) had status `gaps_found` with 1 gap:

- `RemoveBookmarkFromListTest` tests 1 and 3 called `currentProductionTransform` (a hardcoded replica of old buggy code) instead of production code, ensuring those tests always fail; test 2 called an inlined expected-behavior helper ensuring it always passed. No test exercised the real production function.

Gap closure (Plan 02) extracted `applyRemoveBookmarkTransform` as a top-level pure function in `MainScreenModelActions.kt` and rewrote the test file to import and call it directly. The gap is now closed.

**No regressions found in the previously-verified Plan 01 artifacts.**

---

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | Removing a bookmark from a list while viewing that list makes it disappear immediately | ? HUMAN | `applyRemoveBookmarkTransform` at line 169 branches on `currentListContext == listId` and returns `bookmarks.filter { it.remoteId != bookmark.remoteId }` — correct logic confirmed; visual verification needs running app |
| 2 | Removing a bookmark while viewing All Bookmarks keeps it but updates its listIds | ? HUMAN | Else-branch at line 172 maps list and returns `it.copy(listIds = newListIds.joinToString(","))` — correct logic confirmed; behavioral verification needs running app |
| 3 | Triggering a sync when a list has syncOffline=true fetches content for bookmarks lacking content | ? HUMAN | `offlineBookmarks` block in BookmarkSyncPipeline.kt with `fetchContentForBookmarks(offlineBookmarks)` call — correct logic confirmed; end-to-end verification needs running app + Karakeep server |
| 4 | Bookmarks that already have content (readingTimeMinutes > 0) are skipped during offline sync | ✓ VERIFIED | `entity.readingTimeMinutes == 0` guard present in BookmarkSyncPipeline.kt |
| 5 | Bookmarks in child lists of an offline-enabled parent are also fetched | ✓ VERIFIED | `addDescendantListIds` recursive helper and `listsWithChildren` expansion confirmed in BookmarkSyncPipeline.kt |
| T1 | Unit tests exist for removeBookmarkFromList conditional filtering behavior AND call production code | ✓ VERIFIED | All 3 tests in RemoveBookmarkFromListTest import and call `applyRemoveBookmarkTransform` from production code; no hardcoded stubs remain |
| T2 | Unit tests exist for syncContent offline wiring behavior | ✓ VERIFIED | SyncContentOfflineTest.kt has 4 well-formed tests with correct pure-function contract matching production logic |

**Score:** 5/5 must-haves verified (automated); 3 truths require human confirmation for end-to-end behavior

---

## Required Artifacts

### Plan 02 Artifacts (Gap Closure)

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `composeApp/src/commonMain/kotlin/com/karakept/app/ui/screens/MainScreenModelActions.kt` | Top-level pure function `applyRemoveBookmarkTransform` extracted; `removeBookmarkFromList` delegates to it | ✓ VERIFIED | Function at line 163; `removeBookmarkFromList` has a single `updateAccumulatedBookmarks` call delegating to it at line 192 |
| `composeApp/src/desktopTest/kotlin/com/karakept/app/ui/screens/RemoveBookmarkFromListTest.kt` | All 3 tests call production `applyRemoveBookmarkTransform`; no hardcoded stubs | ✓ VERIFIED | Import at line 4; 3 `@Test` methods each calling `applyRemoveBookmarkTransform`; `currentProductionTransform` count = 0; inline `applyRemoveBookmarkFromListTransform` count = 0; `assertNull` not present |

### Plan 01 Artifacts (Regression Check)

| Artifact | Status | Regression Evidence |
|----------|--------|---------------------|
| `MainScreenModelActions.kt` — conditional filter/update logic | ✓ NO REGRESSION | `_currentListContext.value` passed as `currentListContext` at line 194; delegation pattern intact |
| `BookmarkSyncPipeline.kt` — per-list offline sync content fetching | ✓ NO REGRESSION | `syncOffline` filter at lines 468, 474 confirmed present |
| `BookmarkRepository.kt` — ListDao constructor parameter | ✓ NO REGRESSION | Not touched by Plan 02 |
| `AppModule.kt` — 9-arg BookmarkRepository constructor | ✓ NO REGRESSION | Not touched by Plan 02 |

---

## Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| `RemoveBookmarkFromListTest.kt` | `MainScreenModelActions.kt applyRemoveBookmarkTransform` | direct import and 3 call sites | ✓ WIRED | Import at line 4; calls in test methods at lines 56, 75, 97 |
| `MainScreenModelActions.kt removeBookmarkFromList` | `MainScreenModelActions.kt applyRemoveBookmarkTransform` | delegation — single `updateAccumulatedBookmarks` call | ✓ WIRED | `applyRemoveBookmarkTransform(currentListContext = _currentListContext.value, ...)` at lines 193-198 |
| `BookmarkSyncPipeline.syncContent()` | `settingsRepository.allListSettings` | reads syncOffline flags | ✓ WIRED (regression-confirmed) | `val allListSettings = settingsRepository.allListSettings.first()` pattern present |

---

## Data-Flow Trace (Level 4)

No changes to data-flow from Plan 02 (pure function extraction only; no data sources modified). Previous Level 4 results carry forward:

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|---------------|--------|--------------------|--------|
| `MainScreenModelActions.kt` | `_currentListContext.value` | `MutableStateFlow<String?>` updated on navigation | Yes | ✓ FLOWING |
| `BookmarkSyncPipeline.kt` | `allListSettings` | `settingsRepository.allListSettings.first()` — Room DB via Flow | Yes | ✓ FLOWING |
| `BookmarkSyncPipeline.kt` | `offlineBookmarks` | filtered from `entities` param (real bookmark entities) | Yes | ✓ FLOWING |

---

## Behavioral Spot-Checks

Step 7b: SKIPPED — JDK 25.0.2 incompatibility prevents Gradle/Kotlin compilation locally (pre-existing issue). Tests are correct Kotlin that import and call production code; execution is routed to human verification.

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| RemoveBookmarkFromListTest all 3 tests pass | `./gradlew :composeApp:desktopTest --tests "...RemoveBookmarkFromListTest"` | Cannot run (JDK 25) | ? SKIP |
| SyncContentOfflineTest all 4 tests pass | `./gradlew :composeApp:desktopTest --tests "...SyncContentOfflineTest"` | Cannot run (JDK 25) | ? SKIP |

---

## Requirements Coverage

| Requirement | Source Plans | Description | Status | Evidence |
|-------------|-------------|-------------|--------|----------|
| LIST-01 | 05-00, 05-01, 05-02 | Quick actions immediately reflected in currently viewed list (#154) | ✓ SATISFIED | `applyRemoveBookmarkTransform` pure function implements filter/update-listIds conditional logic; `removeBookmarkFromList` delegates to it; `RemoveBookmarkFromListTest` tests production code directly (valid regression gate) |
| LIST-02 | 05-00, 05-01 | Enabling per-list offline sync actually downloads entries (#155) | ✓ SATISFIED (needs human confirmation) | `syncOffline` block in `syncContent()` with `fetchContentForBookmarks(offlineBookmarks)` call; child list expansion via `addDescendantListIds`; `SyncContentOfflineTest` validates filtering contract |

No orphaned requirements: REQUIREMENTS.md maps LIST-01 and LIST-02 to Phase 05, and both plans claim them. No additional Phase 05 requirement IDs appear in REQUIREMENTS.md that are unaccounted for.

---

## Anti-Patterns Found

None. The previous blockers in `RemoveBookmarkFromListTest.kt` (hardcoded stubs `currentProductionTransform` and `applyRemoveBookmarkFromListTransform`) have been removed. No anti-patterns found in any of the five files modified across Plans 01 and 02.

---

## Human Verification Required

### 1. LIST-01: Bookmark disappears from list on removal

**Test:** Open the app, navigate into any list (e.g. "Reading List"). Use the action menu on a bookmark and choose "Remove from list".
**Expected:** The bookmark disappears from the list view immediately, without a manual refresh or sync.
**Why human:** Optimistic UI state update requires visual inspection in the running app.

### 2. LIST-01: Bookmark stays when removing from All Bookmarks context

**Test:** Navigate to All Bookmarks. Remove a bookmark from a specific list using quick actions.
**Expected:** The bookmark remains visible in All Bookmarks. Only its list membership changes.
**Why human:** Requires running app; the listIds update is not directly visible in this context.

### 3. LIST-02: Per-list offline sync downloads content

**Test:** Enable "Sync offline" on a list that contains bookmarks with no content (never opened in reader). Trigger a manual sync.
**Expected:** After sync completes, opening a bookmark from that list shows article content in reader mode (readingTimeMinutes > 0 in DB).
**Why human:** Requires running app connected to a live Karakeep server, and DB inspection to confirm readingTimeMinutes changed.

### 4. All tests pass with correct JDK

**Test:** Run `./gradlew :composeApp:desktopTest --tests "com.karakept.app.ui.screens.RemoveBookmarkFromListTest" --tests "com.karakept.app.data.repository.SyncContentOfflineTest"` with JDK 17-21.
**Expected:** RemoveBookmarkFromListTest: all 3 tests PASS. SyncContentOfflineTest: all 4 tests PASS.
**Why human:** JDK 25 incompatibility prevents local execution. Test code is structurally correct and calls production functions; compilation and execution must be confirmed on a compatible JDK.

---

## Gaps Summary

No gaps remain. The one gap from the initial verification has been closed:

`RemoveBookmarkFromListTest` now imports and calls the production `applyRemoveBookmarkTransform` function in all 3 test methods. The hardcoded stub helpers `currentProductionTransform` and `applyRemoveBookmarkFromListTransform` have been deleted. The tests will pass with the correct conditional logic and would fail if the conditional branch (`currentListContext == listId`) were removed or reverted.

The only remaining items are 4 human-verification checks that cannot be resolved programmatically (visual UI behavior, running-app + server integration, and JDK-compatible test execution).

---

_Verified: 2026-03-23T19:55:00Z_
_Verifier: Claude (gsd-verifier)_
