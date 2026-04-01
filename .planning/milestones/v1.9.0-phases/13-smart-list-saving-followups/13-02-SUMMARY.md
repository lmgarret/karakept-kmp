---
phase: 13-smart-list-saving-followups
plan: "02"
subsystem: ui/screens
tags: [bug-fix, tdd, list-membership, smart-list, sync]
dependency_graph:
  requires: []
  provides: [LIST-02-fix, syncSmartLists]
  affects: [MainScreenModelActions, moveBookmarkToList, removeBookmarkFromList]
tech_stack:
  added: []
  patterns: [fire-and-forget background launch per smart list, TDD red-green]
key_files:
  created:
    - composeApp/src/androidUnitTest/kotlin/com/karakept/app/ui/screens/List02RegressionTest.kt
  modified:
    - composeApp/src/commonMain/kotlin/com/karakept/app/ui/screens/MainScreenModelActions.kt
decisions:
  - syncSmartLists fires in parallel nested launches — one coroutine per smart list — so syncs are independent and non-blocking
  - syncSmartLists is private (not exposed on MainScreenModel) — internal sync concern
  - Errors are caught per-list and logged via AppLogger, not surfaced to UI
metrics:
  duration: 25m
  completed: "2026-03-26T19:22:12Z"
  tasks_completed: 2
  files_changed: 2
---

# Phase 13 Plan 02: LIST-02 Smart List Sync After Quick Actions Summary

**One-liner:** syncSmartLists() private helper added to MainScreenModelActions.kt, firing parallel per-smart-list server re-fetches after moveBookmarkToList and removeBookmarkFromList quick actions.

## What Was Built

### syncSmartLists() helper (MainScreenModelActions.kt)

A new private extension function `syncSmartLists()` was added to `MainScreenModelActions.kt`. It:

1. Guards on `_selectedServer.value` — returns early if no server selected
2. Filters `listRepository.lists.value` for `KarakeepList.Type.SMART` entries only
3. Returns early if no smart lists exist (avoids unnecessary coroutine overhead)
4. Launches a parent coroutine on `screenModelScope` that fans out to one `launch{}` per smart list
5. Each child launch calls `bookmarkRepository.syncBookmarksForList(server, listId)` and catches exceptions, logging them via `AppLogger.e` without affecting the UI

The function is wired as the last call inside the `screenModelScope.launch` block of both:
- `moveBookmarkToList` — after `updateAccumulatedBookmarks`
- `removeBookmarkFromList` — after `updateAccumulatedBookmarks`

`executeScrollAction` with `SwipeAction.ADD_TO_LIST` delegates to `moveBookmarkToList`, so it inherits the fix transitively.

### List02RegressionTest (4 tests)

| Test | Assertion |
|------|-----------|
| moveBookmarkToList triggers sync for SMART lists only | coVerify smart-1 and smart-2 synced; coVerify(0) manual-1 not synced |
| removeBookmarkFromList triggers sync for SMART lists only | coVerify smart-1 and smart-2 synced; coVerify(0) manual-1 not synced |
| moveBookmarkToList does no sync when no SMART lists | coVerify(0) no syncBookmarksForList calls at all |
| executeScrollAction ADD_TO_LIST triggers smart list sync transitively | coVerify smart-1 and smart-2 synced via delegation chain |

## Deviations from Plan

None — plan executed exactly as written.

## Known Stubs

None.

## Test Results

- `./gradlew :composeApp:testDebugUnitTest` — BUILD SUCCESSFUL (includes all 4 new List02RegressionTest tests)
- `./gradlew :composeApp:desktopTest` — 6 pre-existing Docker integration test failures (BaseDockerIntegrationTest subclass tests that require `karakeep-upstream/docker/docker-compose.dev.yml` — absent in the worktree environment); all non-integration desktop tests PASSED

## Self-Check: PASSED

- File `composeApp/src/androidUnitTest/kotlin/com/karakept/app/ui/screens/List02RegressionTest.kt` exists with `class List02RegressionTest` containing 4 `@Test` methods
- File `composeApp/src/commonMain/kotlin/com/karakept/app/ui/screens/MainScreenModelActions.kt` contains `syncSmartLists` function
- `moveBookmarkToList` calls `syncSmartLists()` inside its launch block
- `removeBookmarkFromList` calls `syncSmartLists()` inside its launch block
- Commit `9b49941` exists and includes both files
