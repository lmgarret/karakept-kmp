---
phase: 05-list-sync
verified: 2026-03-23T19:14:57Z
status: gaps_found
score: 4/5 must-haves verified
gaps:
  - truth: "Unit tests exist for removeBookmarkFromList conditional filtering behavior (AND pass after implementation)"
    status: failed
    reason: >
      Test files exist and compile, but RemoveBookmarkFromListTest has a structural flaw: tests 1 and 3
      call `currentProductionTransform` (an inlined copy of the OLD buggy behavior) and assert the NEW
      expected behavior. They will permanently fail regardless of whether the production fix is applied.
      Test 2 calls `applyRemoveBookmarkFromListTransform` (the expected logic inlined in the test itself)
      and will permanently pass regardless of production code. None of the three tests exercise the actual
      `MainScreenModelActions.removeBookmarkFromList` function in production code. The test suite cannot
      serve as a GREEN-phase regression gate for LIST-01.
    artifacts:
      - path: "composeApp/src/desktopTest/kotlin/com/karakept/app/ui/screens/RemoveBookmarkFromListTest.kt"
        issue: >
          Tests 1 and 3 pass `currentProductionTransform` (hardcoded old behavior) instead of the real
          production function, so they always fail. Test 2 passes `applyRemoveBookmarkFromListTransform`
          (hardcoded expected behavior), so it always passes. No test calls the actual production
          `removeBookmarkFromList` extension function.
    missing:
      - >
        Rewrite RemoveBookmarkFromListTest to test the actual conditional logic in production code.
        The pure-function approach is sound (the transform lambda is testable without a full ScreenModel),
        but the helper must replicate the CURRENT production code path, not a hardcoded stub. Replace
        `currentProductionTransform` calls in tests 1 and 3 with a helper that delegates to the actual
        conditional transform implemented in Plan 01 (i.e., test the real `if (_currentListContext.value
        == listId)` path). Alternatively, extract the transform logic as a standalone pure function in
        production code that both the ScreenModel extension and the test call.
human_verification:
  - test: "Remove a bookmark from a list while the list is open"
    expected: "The bookmark disappears from the list view immediately without requiring a refresh"
    why_human: "Optimistic UI behavior requires the running app and visual inspection"
  - test: "Enable 'sync offline' on a list and trigger a sync"
    expected: "Bookmarks in that list with no content have their content downloaded (readingTimeMinutes > 0 after sync)"
    why_human: "Requires device running the app, a real Karakeep server, and inspecting DB state after sync"
---

# Phase 05: List Sync Verification Report

**Phase Goal:** Fix two list management and sync bugs for v1.8.0 — bookmark removal not visually reflected in list view, and per-list offline sync setting not triggering content download.
**Verified:** 2026-03-23T19:14:57Z
**Status:** gaps_found
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths (Plan 01 — Implementation)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | Removing a bookmark from a list while viewing that list makes it disappear immediately | ? HUMAN | `_currentListContext.value == listId` guard at line 159 in MainScreenModelActions.kt with `filter` path — correct logic present, visual verification needed |
| 2 | Removing a bookmark while viewing All Bookmarks keeps it but updates its listIds | ? HUMAN | `else` branch at line 161 maps listIds — correct logic present, visual verification needed |
| 3 | Triggering a sync when a list has syncOffline=true fetches content for bookmarks lacking content | ? HUMAN | `offlineBookmarks` block in BookmarkSyncPipeline.kt lines 483-493 — correct logic present, end-to-end verification needs running app |
| 4 | Bookmarks that already have content (readingTimeMinutes > 0) are skipped during offline sync | ✓ VERIFIED | `entity.readingTimeMinutes == 0` guard at line 488 in BookmarkSyncPipeline.kt |
| 5 | Bookmarks in child lists of an offline-enabled parent (includeChildListBookmarks=true) are also fetched | ✓ VERIFIED | `addDescendantListIds` recursive helper and `listsWithChildren` expansion block at lines 473-480 in BookmarkSyncPipeline.kt |

### Observable Truths (Plan 00 — Test Scaffolds)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| T1 | Unit tests exist for removeBookmarkFromList conditional filtering behavior | ✗ FAILED | File exists at correct path with 3 test cases, but tests do not exercise production code — see gap below |
| T2 | Unit tests exist for syncContent offline wiring behavior | ✓ VERIFIED | SyncContentOfflineTest.kt has 4 well-formed tests with correct pure-function contract matching production logic |
| T3 | All tests fail initially (RED phase) | ? UNCERTAIN | Cannot run Gradle locally due to JDK 25 incompatibility (pre-existing); static analysis confirms RED intent for LIST-01 tests 1+3 and LIST-02 (tests call production behavior that did not exist) |

**Score:** 4/5 implementation truths verifiable (2 need human confirmation, 2 verified, 1 gap on test correctness)

---

## Required Artifacts

### Plan 00 Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `composeApp/src/desktopTest/kotlin/com/karakept/app/ui/screens/RemoveBookmarkFromListTest.kt` | Tests for LIST-01 conditional optimistic removal | ✗ STRUCTURALLY FLAWED | File exists, 3 tests present, contains `removeBookmarkFromList` reference in comments. However tests 1 and 3 call `currentProductionTransform` (hardcoded old behavior) and can never turn GREEN. Test 2 calls an inlined expected helper and always passes. No test path calls the production function. |
| `composeApp/src/desktopTest/kotlin/com/karakept/app/data/repository/SyncContentOfflineTest.kt` | Tests for LIST-02 offline sync content fetching | ✓ VERIFIED | File exists, 4 tests with correct pure-function contract matching production implementation |

### Plan 01 Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `composeApp/src/commonMain/kotlin/com/karakept/app/ui/screens/MainScreenModelActions.kt` | Conditional optimistic removal in removeBookmarkFromList | ✓ VERIFIED | Contains `_currentListContext.value == listId` at line 159; if-branch filters, else-branch updates listIds |
| `composeApp/src/commonMain/kotlin/com/karakept/app/data/repository/BookmarkSyncPipeline.kt` | Per-list offline sync content fetching with child list expansion | ✓ VERIFIED | Contains `syncOffline`, `allListSettings`, `offlineListIds`, `addDescendantListIds`, `getListsForServerOnce`, `offlineBookmarks`, `alreadySyncedIds`, `listDao` — all required patterns present |
| `composeApp/src/commonMain/kotlin/com/karakept/app/data/repository/BookmarkRepository.kt` | ListDao constructor parameter, passed to BookmarkSyncPipeline | ✓ VERIFIED | `listDao: ListDao` at line 36, import at line 5, passed to pipeline at line 363 |
| `composeApp/src/commonMain/kotlin/com/karakept/app/di/AppModule.kt` | 9 get() calls for BookmarkRepository | ✓ VERIFIED | Line 90: `BookmarkRepository(get(), get(), get(), get(), get(), get(), get(), get(), get())` — 9 positional args confirmed |

---

## Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| `MainScreenModelActions.kt removeBookmarkFromList` | `_currentListContext` | conditional check before filter vs map | ✓ WIRED | Pattern `_currentListContext\.value == listId` found at line 159 |
| `BookmarkSyncPipeline.syncContent()` | `settingsRepository.allListSettings` | reads syncOffline flags | ✓ WIRED | `val allListSettings = settingsRepository.allListSettings.first()` at line 466 |
| `BookmarkSyncPipeline.syncContent()` | `listDao.getListsForServerOnce` | expands offlineListIds with descendants | ✓ WIRED | `listDao.getListsForServerOnce(config.server.id)` at line 477, guarded by `listsWithChildren.isNotEmpty()` |

---

## Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|---------------|--------|--------------------|--------|
| `MainScreenModelActions.kt` | `_currentListContext.value` | `MutableStateFlow<String?>` updated by screen navigation (pre-existing) | Yes — set when user navigates into a list | ✓ FLOWING |
| `BookmarkSyncPipeline.kt` | `allListSettings` | `settingsRepository.allListSettings.first()` — reads Room DB via SettingsRepository | Yes — real DB query via Flow | ✓ FLOWING |
| `BookmarkSyncPipeline.kt` | `offlineBookmarks` | filtered from `entities` param (passed from BookmarkRepository) | Yes — real bookmark entities from sync | ✓ FLOWING |

---

## Behavioral Spot-Checks

Step 7b: SKIPPED — JDK 25.0.2 incompatibility prevents Gradle/Kotlin compilation locally (pre-existing issue documented in STATE.md). Module exports and runnable entry points require JDK 17-21. The following checks are routed to human verification instead:

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| RemoveBookmarkFromListTest compiles | `./gradlew :composeApp:desktopTest --tests "...RemoveBookmarkFromListTest"` | Cannot run (JDK 25) | ? SKIP |
| SyncContentOfflineTest compiles | `./gradlew :composeApp:desktopTest --tests "...SyncContentOfflineTest"` | Cannot run (JDK 25) | ? SKIP |
| assembleDebug compiles | `./gradlew :composeApp:assembleDebug` | Cannot run (JDK 25) | ? SKIP |

---

## Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|-------------|-------------|--------|----------|
| LIST-01 | 05-00, 05-01 | Quick actions immediately reflected in currently viewed list (#154) | ✓ SATISFIED (needs human confirmation) | `_currentListContext.value == listId` conditional in `removeBookmarkFromList` — filter path removes bookmark from view; test scaffold exists (structurally flawed — see gap) |
| LIST-02 | 05-00, 05-01 | Enabling per-list offline sync actually downloads entries (#155) | ✓ SATISFIED (needs human confirmation) | `syncOffline` block in `syncContent()` with `fetchContentForBookmarks(offlineBookmarks)` call; child list expansion via `addDescendantListIds`; `SyncContentOfflineTest` correctly validates filtering contract |

No orphaned requirements: REQUIREMENTS.md maps LIST-01 and LIST-02 to Phase 05, and both plans claim them. No additional Phase 05 requirement IDs appear in REQUIREMENTS.md that are unaccounted for.

---

## Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| `RemoveBookmarkFromListTest.kt` | 121-130 | `currentProductionTransform` called in test asserting NEW behavior — test will always fail | ✗ Blocker | Prevents test suite from serving as GREEN-phase gate for LIST-01 |
| `RemoveBookmarkFromListTest.kt` | 143-152 | `applyRemoveBookmarkFromListTransform` is inlined expected behavior — test always passes | ⚠ Warning | False confidence — test 2 passes regardless of production code correctness |
| `RemoveBookmarkFromListTest.kt` | 166-177 | Same as test 1 issue: `currentProductionTransform` called, test always fails | ✗ Blocker | No path to GREEN for test 3 |

Note: No anti-patterns found in the four production files modified by Plan 01.

---

## Human Verification Required

### 1. LIST-01: Bookmark disappears from list on removal

**Test:** Open the app, navigate into any list (e.g. "Reading List"). Long-press or use the three-dot menu on a bookmark and choose "Remove from list".
**Expected:** The bookmark disappears from the list view immediately, without a manual refresh or sync.
**Why human:** Optimistic UI state update requires visual inspection in the running app.

### 2. LIST-01: Bookmark stays when removing from All Bookmarks context

**Test:** Navigate to All Bookmarks. Remove a bookmark from a specific list using quick actions.
**Expected:** The bookmark remains visible in All Bookmarks. Only its list membership changes (not visible directly in this context).
**Why human:** Requires running app; state update is invisible unless inspecting the bookmark detail.

### 3. LIST-02: Per-list offline sync downloads content

**Test:** Enable "Sync offline" on a list that contains bookmarks with no content (never opened in reader). Trigger a manual sync.
**Expected:** After sync completes, opening a bookmark from that list shows article content in reader mode (readingTimeMinutes > 0 in DB).
**Why human:** Requires running app connected to a live Karakeep server, and DB inspection to confirm readingTimeMinutes changed.

### 4. All tests pass with correct JDK

**Test:** Run `./gradlew :composeApp:desktopTest --tests "com.karakept.app.ui.screens.RemoveBookmarkFromListTest" --tests "com.karakept.app.data.repository.SyncContentOfflineTest"` with JDK 17-21.
**Expected:** SyncContentOfflineTest: 4 tests PASS. RemoveBookmarkFromListTest: 2 tests FAIL (tests 1 and 3 — permanent RED due to structural flaw noted in gap), 1 test PASS (test 2).
**Why human:** JDK 25 incompatibility prevents local execution. Note that the expected outcome confirms the structural flaw — tests 1 and 3 should fail even after the fix.

---

## Gaps Summary

One gap blocks full verification:

**RemoveBookmarkFromListTest structural flaw** — Tests 1 and 3 assert the expected LIST-01 behavior against `currentProductionTransform`, which is a hardcoded replica of the OLD buggy code embedded in the test file itself. These tests will always fail regardless of whether the production fix is applied. Test 2 tests the expected logic against `applyRemoveBookmarkFromListTransform`, another hardcoded helper, and will always pass. None of the three tests invokes the actual production `removeBookmarkFromList` function in `MainScreenModelActions.kt`.

The production implementation of LIST-01 is correct and complete. The gap is solely in the test scaffold: it cannot serve as an automated regression gate for the fix.

The SyncContentOfflineTest is structurally sound — it defines `computeOfflineSyncTargets` as a pure function that mirrors the production logic, and all four test cases correctly validate the behavioral contract.

The fix is to rewrite RemoveBookmarkFromListTest so that the helpers test the same conditional logic that was implemented in production. The simplest approach: extract the transform as a standalone top-level function in a production file (e.g., `MainScreenModelActions.kt`) and call it from both the extension function and the test.

---

_Verified: 2026-03-23T19:14:57Z_
_Verifier: Claude (gsd-verifier)_
