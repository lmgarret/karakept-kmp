# Phase 12: Notification Fixes - Context

**Gathered:** 2026-03-25
**Status:** Ready for planning

<domain>
## Phase Boundary

Fix two notification bugs on Android:

1. **NOTIF-01** — The sync digest notification does not show how many bookmarks were synced. The notification body should read "N new bookmark(s) synced" but the count is wrong or the wrong branch fires in practice.

2. **NOTIF-02** — The per-list "Notify on new bookmarks" setting (`ListSettings.notifyOnNewBookmarks`) is stored and displayed in UI but is never consumed during sync. No notification ever fires for lists.

Both fixes ship with unit tests (NFR-01).

Desktop is out of scope for this phase (Desktop notification issues are separate).

</domain>

<decisions>
## Implementation Decisions

### NOTIF-01: Scope and fix approach
- **Android only** — the Desktop digest notification is a separate concern and out of scope for this phase.
- The Android `BackgroundSyncWorker.showDigestNotification(newBookmarksCount: Int)` code path exists and includes the count logic, but the count is wrong or zero in practice.
- Diagnosis needed: trace from `BookmarkRepository.syncProgress` → `SyncProgress.SyncComplete(newBookmarksCount)` → worker reception to find where the count is dropped or miscalculated.
- Fix must make the notification show the actual new-bookmark count (e.g., "3 new bookmarks synced").

### NOTIF-02: Tracking which lists received new bookmarks
- **Post-sync DB query** — after sync completes, query Room to determine which lists (with `notifyOnNewBookmarks=true`) received bookmarks in this sync run.
- Do NOT extend `SyncProgress.SyncComplete` with a per-list map — keep the pipeline untouched.
- The query should check `ListSettings` in DataStore for each list to find those with `notifyOnNewBookmarks=true`, then verify those lists have recently synced bookmarks in the DB.
- "Recently synced" can be approximated by bookmarks added/updated in the current sync window (before/after snapshot or timestamp comparison — planner decides implementation detail).

### NOTIF-02: Notification UX
- **One combined notification** for all matching lists — not one per list.
- Format: "New bookmarks in [List A], [List B]" (or similar) — one Android notification covering all lists that fired.
- This prevents notification spam when multiple lists update in a single sync.

### Test strategy (NFR-01)
- Tests go in `androidUnitTest` (Android-specific WorkManager/notification dispatch) or `commonTest` (shared logic for list query).
- Preferred approach: extract the notification-dispatching logic or per-list query logic as a pure/injectable function so it can be tested without a real `NotificationManager`.
- Mock `NotificationManager` or the dispatching wrapper to assert that `notify()` is called with the correct content string.
- For NOTIF-02 list query: test the logic that reads `ListSettings` for multiple lists and identifies which ones should fire.

### Claude's Discretion
- How `BackgroundSyncWorker` captures the pre/post bookmark count (snapshot or incremental counter) — choose whatever is least invasive.
- Exact wording of the combined notification text.
- Whether a helper function is extracted from the worker for testability, or if the test hooks into the worker directly.

</decisions>

<specifics>
## Specific Ideas

- "New bookmarks in Read Later, Tech Articles" as the combined notification format (or similar concise phrasing).
- The fix for NOTIF-01 should feel like a diagnostic/targeted change — if the count logic is already there in the worker, the issue is likely upstream (wrong value propagated from sync result).

</specifics>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

No external specs — requirements are fully captured in decisions above.

### Notification infrastructure (Android)
- `composeApp/src/androidMain/kotlin/com/karakept/app/services/BackgroundSyncWorker.kt` — Background sync worker that dispatches the digest notification; contains `showDigestNotification(newBookmarksCount: Int)` and permission checks
- `composeApp/src/commonMain/kotlin/com/karakept/app/data/model/SyncProgress.kt` — Defines `SyncProgress.SyncComplete(newBookmarksCount: Int)` — the sync result model

### Per-list notification settings
- `composeApp/src/commonMain/kotlin/com/karakept/app/data/model/ListSettings.kt` — Defines `ListSettings` data class with `notifyOnNewBookmarks: Boolean`
- `composeApp/src/commonMain/kotlin/com/karakept/app/data/repository/SettingsRepository.kt` — `getListSettings(listId)` returns `Flow<ListSettings>`; contains per-list settings read path

### Requirements
- `.planning/REQUIREMENTS.md` §NOTIF-01, §NOTIF-02, §NFR-01, §NFR-03

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `BackgroundSyncWorker.showDigestNotification(newBookmarksCount)` — already handles count-conditional text; fix must make the right count reach it
- `SettingsRepository.getListSettings(listId)` — use this to read per-list `notifyOnNewBookmarks` flags after sync

### Established Patterns
- `BaseRepositoryTest` — inherit for async test scaffolding (`testDispatcher`, `testScope`, `runTest`)
- MockK relaxed mocks for DAOs and RemoteDataSource
- Pure function extraction pattern (established in Phase 10) — extract testable logic out of composables/workers

### Integration Points
- `BackgroundSyncWorker.doWork()` — entry point for sync; result of sync is collected here; NOTIF-01 fix is in this flow
- `SettingsRepository` — bridging point between the worker and per-list settings (NOTIF-02)

</code_context>

<deferred>
## Deferred Ideas

- Desktop digest notification count — separate platform concern, not in this phase
- Per-list notification with individual counts per list (e.g., "3 in Read Later, 1 in Tech") — UX decision was one combined notification; counts per list deferred
- Notification channels per list — out of scope

</deferred>

---

*Phase: 12-notification-fixes*
*Context gathered: 2026-03-25*
