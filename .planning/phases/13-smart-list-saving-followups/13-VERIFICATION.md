---
phase: 13-smart-list-saving-followups
verified: 2026-03-26T22:00:00Z
status: passed
score: 7/7 must-haves verified
re_verification:
  previous_status: gaps_found
  previous_score: 6/7
  gaps_closed:
    - "After a ForList sync, bookmarks locally in the list but absent from server response have that listId stripped from their listIds"
  gaps_remaining: []
  regressions: []
---

# Phase 13: Smart List & Saving Follow-ups Verification Report

**Phase Goal:** Fix smart-list-saving followup bugs (SAVE-02, LIST-02) identified after Phase 12 UAT.
**Verified:** 2026-03-26T22:00:00Z
**Status:** passed
**Re-verification:** Yes — after gap closure (13-03)

---

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | MainScreenModel reaches InitState.Ready when created inside BookmarkSavingActivity Navigator | VERIFIED | factory registration at AppModule.kt:111; Save02RegressionTest.kt asserts `_accumulatedBookmarks` non-empty after init |
| 2 | Bookmark list is populated (non-empty `_accumulatedBookmarks`) after init completes | VERIFIED | Save02RegressionTest.kt:156 directly asserts `model._accumulatedBookmarks.value.isNotEmpty()` |
| 3 | Drawer counters (quickFilterCounts) emit correct values after init | VERIFIED | Save02RegressionTest.kt:215 asserts `counts.all == 4` matching the 4 stubbed bookmarks |
| 4 | After moveBookmarkToList, syncBookmarksForList is called for every SMART list | VERIFIED | MainScreenModelActions.kt:137 calls `syncSmartLists()`; List02RegressionTest.kt verifies with `coVerify` |
| 5 | After removeBookmarkFromList, syncBookmarksForList is called for every SMART list | VERIFIED | MainScreenModelActions.kt:226 calls `syncSmartLists()`; List02RegressionTest.kt verifies with `coVerify` |
| 6 | After a ForList sync, bookmarks locally in the list but absent from server response have that listId stripped from their listIds | VERIFIED | `reconcileListMembership` private method at BookmarkSyncPipeline.kt:539; called after Phase 4 at lines 98-101 gated by `config is SyncConfiguration.ForList`; `computeStaleListRemovals` internal top-level function at line 597; BookmarkSyncPipelineReconcileTest.kt: 5 tests covering all edge cases |
| 7 | MANUAL lists are NOT synced by the smart list sync trigger | VERIFIED | `syncSmartLists()` at MainScreenModelActions.kt:98 filters `it.type == KarakeepList.Type.SMART`; List02RegressionTest.kt uses `coVerify(exactly = 0)` for manual lists |

**Score:** 7/7 truths verified

---

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `composeApp/src/commonMain/kotlin/com/karakept/app/di/AppModule.kt` | factory registration for MainScreenModel | VERIFIED | Line 111: `factory { MainScreenModel(...) }` with SAVE-02 rationale comment |
| `composeApp/src/androidUnitTest/kotlin/com/karakept/app/ui/screens/Save02RegressionTest.kt` | Regression tests for SAVE-02 (min 50 lines) | VERIFIED | 242 lines; 3 `@Test` methods |
| `composeApp/src/commonMain/kotlin/com/karakept/app/ui/screens/MainScreenModelActions.kt` | `syncSmartLists` helper wired into move and remove | VERIFIED | Function at line 96; called at lines 137 and 226 |
| `composeApp/src/androidUnitTest/kotlin/com/karakept/app/ui/screens/List02RegressionTest.kt` | Regression tests for LIST-02 (min 60 lines) | VERIFIED | 204 lines; 4 `@Test` methods |
| `composeApp/src/commonMain/kotlin/com/karakept/app/data/repository/BookmarkSyncPipeline.kt` | `reconcileListMembership` + `computeStaleListRemovals` + ForList gating | VERIFIED | `reconcileListMembership` at line 539; `computeStaleListRemovals` at line 597; ForList guard at lines 98-101; calls `bookmarkDao.getAllBookmarksForList` (line 544) and `bookmarkDao.updateBookmarkMetadata` (line 550) |
| `composeApp/src/commonTest/kotlin/com/karakept/app/data/repository/BookmarkSyncPipelineReconcileTest.kt` | Unit tests for reconciliation pure function (min 60 lines) | VERIFIED | 120 lines; 5 `@Test` methods covering all edge cases |

---

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| `BookmarkSyncPipeline.execute` | `reconcileListMembership` | `if (config is SyncConfiguration.ForList)` guard | WIRED | Lines 98-101 in BookmarkSyncPipeline.kt |
| `reconcileListMembership` | `computeStaleListRemovals` | direct call at line 546 | WIRED | `val removals = computeStaleListRemovals(localBookmarksInList, serverRemoteIds, listId)` |
| `reconcileListMembership` | `bookmarkDao.updateBookmarkMetadata` | loop over removals at lines 548-565 | WIRED | Each `(localId, newListIds)` pair persisted via metadata update |
| `reconcileListMembership` | `bookmarkDao.getAllBookmarksForList` | call at line 544 | WIRED | DAO method confirmed at BookmarkDao.kt:286 |
| `MainScreenModelActions.moveBookmarkToList` | `syncSmartLists` | call at line 137 | WIRED | Confirmed in MainScreenModelActions.kt |
| `MainScreenModelActions.removeBookmarkFromList` | `syncSmartLists` | call at line 226 | WIRED | Confirmed in MainScreenModelActions.kt |

---

### Data-Flow Trace (Level 4)

The gap closure fixes infrastructure-level sync behavior rather than a UI rendering component. The reconciliation step writes directly to DB rows via `bookmarkDao.updateBookmarkMetadata`. The rendering components downstream (bookmark list, smart list view) already read from the same DAO flows and were verified in prior phases. The 5 unit tests on `computeStaleListRemovals` assert the exact `listIds` string values that would be persisted, covering the full data-flow contract for this phase.

---

### Behavioral Spot-Checks

`computeStaleListRemovals` is a pure Kotlin function in `commonTest` — directly testable without an Android runtime or running server.

| Behavior | Evidence | Status |
|----------|----------|--------|
| All local bookmarks in server response — no removals | Test 1 in BookmarkSyncPipelineReconcileTest.kt | PASS |
| Stale bookmark stripped but other list memberships preserved | Test 2: `smart-1,manual-1` becomes `manual-1` | PASS |
| Bookmark with sole listId stripped — empty string result | Test 3: `smart-1` becomes `""` | PASS |
| Multiple stale bookmarks all flagged | Test 4: 3 stale bookmarks, all 3 returned | PASS |
| Present bookmarks excluded from removal set | Test 5: mixed present/absent — only absent flagged | PASS |
| Full and Filtered syncs unaffected by reconciliation | ForList guard at lines 98-101 — `reconcileListMembership` never called for Full/Filtered | PASS |
| No regressions in full suite | SUMMARY 13-03: 453/459 tests passing; 6 pre-existing Docker failures unchanged | PASS |

---

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|-------------|-------------|--------|----------|
| SAVE-02 | 13-01-PLAN.md | Navigate back after saving bookmarks must show bookmark list | SATISFIED | `factory {}` registration at AppModule.kt:111; Save02RegressionTest.kt: 3 tests confirming non-empty bookmarks and drawer counters after init |
| LIST-02 | 13-02-PLAN.md, 13-03-PLAN.md | Smart list must reflect quick-action changes immediately | SATISFIED | Trigger: `syncSmartLists()` wired in MainScreenModelActions.kt at lines 137 and 226; Reconciliation: `reconcileListMembership` in ForList sync path strips stale listIds via `bookmarkDao.updateBookmarkMetadata`; 5 unit tests confirm edge cases; 4 regression tests confirm trigger wiring |
| NFR-01 | 13-01-PLAN.md, 13-02-PLAN.md, 13-03-PLAN.md | Regression tests for every fix | SATISFIED | Save02RegressionTest.kt (3 tests); List02RegressionTest.kt (4 tests); BookmarkSyncPipelineReconcileTest.kt (5 tests) |
| NFR-02 | 13-01-PLAN.md, 13-02-PLAN.md, 13-03-PLAN.md | No new regressions | SATISFIED | All three plan SUMMARYs report BUILD SUCCESSFUL; reconciliation guarded by `config is SyncConfiguration.ForList` so Full/Filtered sync paths are untouched; 6 pre-existing Docker failures unchanged |

No orphaned requirements: REQUIREMENTS.md maps no additional IDs to Phase 13 beyond SAVE-02 and LIST-02. NFR-01 and NFR-02 are milestone-wide requirements satisfied by this phase's test artifacts.

---

### Anti-Patterns Found

None detected in the files modified by 13-03.

- No TODO/FIXME/placeholder comments in BookmarkSyncPipeline.kt or BookmarkSyncPipelineReconcileTest.kt
- `return emptyList()` in `computeStaleListRemovals` only executes when the filter produces no matches — correct behavior, not a stub
- `emptySet<String>()` in test fixtures is test infrastructure, not production stubbing
- `if (config is SyncConfiguration.ForList)` guard at lines 98-101 is a valid type check, not a placeholder

---

### Human Verification Required

None. All observable truths are covered by automated regression tests and static code analysis. The fix is structural (pipeline reconciliation step) and verified at the pure function level.

---

## Re-verification Summary

**Previous status:** gaps_found (6/7 truths verified)
**Current status:** passed (7/7 truths verified)

**Gap that was found (initial verification):** `BookmarkSyncPipeline.ForList` sync never removed stale list membership — `shouldDeleteRemoved = false` plus merge-only logic in `mapDtoToEntity` meant bookmarks evicted from a smart list server-side remained in local DB membership indefinitely.

**Gap closure (13-03):** Pure function `computeStaleListRemovals` computes which locally-tracked bookmarks are absent from the server response for the synced list. `reconcileListMembership` calls `bookmarkDao.updateBookmarkMetadata` to strip the stale `listId` from each affected bookmark, preserving other list memberships. The step is inserted as Phase 4.5 in `execute()` gated by `config is SyncConfiguration.ForList`, leaving Full and Filtered sync paths unchanged.

Commits: `5c1e888` (RED: failing tests) and `e0253bd` (GREEN: implementation and pipeline wiring) — both confirmed in git log.

---

_Verified: 2026-03-26T22:00:00Z_
_Verifier: Claude (gsd-verifier)_
