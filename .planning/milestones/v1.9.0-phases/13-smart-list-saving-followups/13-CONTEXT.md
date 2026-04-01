# Phase 13: Smart List & Saving Follow-ups - Context

**Gathered:** 2026-03-26
**Status:** Ready for planning

<domain>
## Phase Boundary

Fix two follow-up bugs from v1.8.0:

1. **SAVE-02** — After saving a bookmark via the share activity and pressing Back to the bookmark list (`MainScreen` within `BookmarkSavingActivity`), the list shows empty and drawer counters are 0. Navigation works but data doesn't load correctly.

2. **LIST-02** — Smart lists do not update after quick actions. When a quick action changes a bookmark's list membership, smart lists whose server-side query now excludes that bookmark still show it until the next full sync.

Both fixes ship with regression tests (NFR-01).

</domain>

<decisions>
## Implementation Decisions

### SAVE-02: Root cause diagnosis
- Root cause is **unknown** — researcher must trace the `MainScreenModel` initialization path when `MainScreen` runs inside `BookmarkSavingActivity`.
- Key investigation path: `ShareBookmarkScreen.navigator.replaceAll([MainScreen, BookmarkViewerScreen])` creates a fresh `MainScreenModel` via Voyager ScreenModel scope. Trace the `InitState` sequence (Idle → ResolvingFilter → WaitingForServer → LoadingInitialPage → Ready) to find where data loading breaks or stalls in this secondary Activity context.
- Likely candidates: DataStore server config not emitting promptly (WaitingForServer hangs), or `resetPaginationAndLoad` not populating `_accumulatedBookmarks` correctly from the secondary Activity.

### SAVE-02: Expected UX when Back is pressed
- `MainScreen` shown after pressing Back from `BookmarkViewerScreen` (within `BookmarkSavingActivity`) must be **fully functional**: bookmark list populated, drawer counters correct.
- A brief loading state is acceptable if it resolves quickly — the screen must not stay empty.

### SAVE-02: Test strategy
- ViewModel test (preferably `commonTest` if logic is platform-agnostic, or `androidUnitTest` if it requires Android-specific setup) verifying that `MainScreenModel` bookmark list state is populated after the save flow completes and the screen is shown.

### LIST-02: Smart list update mechanism
- **Smart lists have server-owned query logic** — the app cannot replicate the filter locally. Local re-query is not a valid solution.
- After any quick action that changes a bookmark's list membership (add to list, remove from list, move to list), trigger background `syncBookmarksForList` for **every list with `type = SMART`** in the current server's list collection.
- This ensures: (a) the currently visible smart list shows the correct post-action state, and (b) drawer counters for all smart lists are updated.

### LIST-02: Scope of sync
- **All smart lists** — not just the currently visible one. Drawer counters for smart lists must also be correct after a quick action.
- Syncs run in the background (non-blocking to UI). The user may see counters update after a brief network round-trip.

### LIST-02: Test strategy
- ViewModel/repository test: after a quick action modifies a bookmark's list membership, verify that `syncBookmarksForList` is called for each smart list.
- Preferably via MockK spy/mock on the repository, asserting the sync calls are dispatched.

### Claude's Discretion
- How to identify quick actions that affect list membership (add/remove/move vs. star/archive) — only the former needs to trigger smart list sync.
- Whether smart list syncs are fired in parallel or sequentially.
- Exact timing of the sync trigger relative to the optimistic UI update.

</decisions>

<specifics>
## Specific Ideas

- SAVE-02: If the `WaitingForServer` phase is the culprit, the fix may be as simple as ensuring the server list is already available when `MainScreen` is composed in the secondary Activity (e.g., Koin singleton scope already has servers loaded from the main Activity session).
- LIST-02: The smart list sync could reuse the existing `syncBookmarks()` infrastructure in `MainScreenModel` — trigger it with the smart list IDs rather than adding new sync plumbing.

</specifics>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

No external specs — requirements are fully captured in decisions above.

### SAVE-02: Bookmark saving flow
- `composeApp/src/androidMain/kotlin/com/karakept/app/BookmarkSavingActivity.kt` — Secondary activity hosting the share flow; uses Voyager Navigator with `ShareBookmarkScreen` as root
- `composeApp/src/commonMain/kotlin/com/karakept/app/ui/screens/ShareBookmarkScreen.kt` — On success calls `navigator.replaceAll([MainScreen, BookmarkViewerScreen(bookmark.localId)])`
- `composeApp/src/commonMain/kotlin/com/karakept/app/ui/screens/MainScreenModel.kt` — Sequential init state machine (InitState: Idle → ResolvingFilter → WaitingForServer → LoadingInitialPage → Ready); key functions: `resetPaginationAndLoad`, `syncBookmarks`

### LIST-02: Smart list and quick action infrastructure
- `composeApp/src/commonMain/kotlin/com/karakept/app/ui/screens/MainScreenModelActions.kt` — Quick action dispatch via `bookmarkActionController.executeAction`; contains `moveBookmarkToList`, `removeBookmarkFromList`
- `composeApp/src/commonMain/kotlin/com/karakept/app/data/repository/ListRepository.kt` — List model with `type = SMART`; `refreshLists(server)` fetches list metadata
- `composeApp/src/commonMain/kotlin/com/karakept/app/ui/screens/MainScreenModel.kt` §syncBookmarks — Sync dispatch logic; calls `bookmarkRepository.syncBookmarksForList(server, listId)` per list

### Requirements
- `.planning/REQUIREMENTS.md` §SAVE-02, §LIST-02, §NFR-01, §NFR-02

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `MainScreenModel.syncBookmarks()` — already handles per-list sync via `capturedListContext`; extend or adapt this for smart list batch-sync
- `bookmarkActionsRepository.bookmarkChangedEvents` — existing event bus for per-bookmark mutations; could be extended to carry action type for smart list sync trigger
- `BaseRepositoryTest` — inherit for async test scaffolding in repository-level tests

### Established Patterns
- MockK relaxed mocks for DAOs and repositories (Phases 08–12)
- Pure function extraction for testability (Phases 10–12)
- ScreenModel-centric unit tests that construct the model directly (no DI graph wiring)
- `bookmarkActionsRepository.bookmarkChangedEvents` as post-action notification hook

### Integration Points
- `MainScreenModelActions.kt` — the right place to trigger smart list sync after list-membership quick actions
- `MainScreenModel._accumulatedBookmarks` — in-memory bookmark list that drives both the visible list and drawer counters

</code_context>

<deferred>
## Deferred Ideas

- None — discussion stayed within phase scope.

</deferred>

---

*Phase: 13-smart-list-saving-followups*
*Context gathered: 2026-03-26*
