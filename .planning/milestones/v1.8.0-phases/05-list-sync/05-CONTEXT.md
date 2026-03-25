# Phase 05: List & Sync - Context

**Gathered:** 2026-03-23
**Status:** Ready for planning

<domain>
## Phase Boundary

Fix two bugs:
1. **LIST-01**: Quick actions (removing a bookmark from a list) are not immediately reflected in the currently viewed list
2. **LIST-02**: Per-list offline sync toggle (`syncOffline` in `PerListSettingsScreen`) saves the setting but never triggers content download

This phase does NOT add new list features, new sync modes, or UI changes beyond what's necessary to wire up the existing broken behaviors.

</domain>

<decisions>
## Implementation Decisions

### LIST-01: Optimistic removal from list view
- **D-01:** When a bookmark is removed from the current list via quick action, **filter it out of `_accumulatedBookmarks` entirely** — same pattern as `deleteBookmark` and the ARCHIVE swipe action (`updateAccumulatedBookmarks { it.filter { b -> b.remoteId != bookmark.remoteId } }`).
- **D-02:** Do NOT just update `listIds` on the entity in-memory — the `bookmarks` StateFlow derives from `_pendingBookmarks + _accumulatedBookmarks` without re-filtering by list context, so updating `listIds` alone leaves the bookmark visible.

### LIST-02: Offline sync trigger
- **D-03:** Content download for an offline-enabled list happens **automatically on all syncs** — any sync that processes a list with `syncOffline = true` (manual or background) downloads content for that list's bookmarks.
- **D-04:** No separate "Download offline" button or explicit user action is needed beyond enabling the toggle and triggering sync.

### LIST-02: Offline sync strategy — smart incremental with staleness detection
- **D-05:** Use `modifiedAt` (top-level on `Bookmark` DTO, always returned from metadata-only syncs with `includeContent = false`) as a staleness signal. Store it in `BookmarkEntity` as a new column.
- **D-06:** During sync for an offline-enabled list, fetch content only for bookmarks that meet either condition:
  - Content is missing (`content` is null/empty and NOT `"HAS_CONTENT"`)
  - `modifiedAt` from the server differs from the stored `modifiedAt` (bookmark may have been re-crawled)
- **D-07:** Bookmarks that already have `"HAS_CONTENT"` AND whose `modifiedAt` is unchanged are skipped — no redundant fetches.

### LIST-02: `modifiedAt` verification — OPEN QUESTION for planner
- **D-08 (OPEN):** Whether Karakeep actually updates `modifiedAt` when it re-crawls a bookmark (vs. only on user-initiated changes like tagging, archiving) is **unverified**. The planner MUST check the Karakeep API docs or server source before finalizing the staleness strategy:
  - If `modifiedAt` updates on re-crawl → use it as the staleness signal (D-05–D-07 above)
  - If `modifiedAt` does NOT update on re-crawl → check if `crawledAt` (inside `BookmarkContent`, requires `includeContent = true`) is a better signal, or fall back to `includeContent = true` for all offline-enabled list syncs (simpler, always fresh, heavier)

### Claude's Discretion
- DB migration version for adding `modifiedAt` column to `BookmarkEntity`
- Whether to fetch content via `fetchBookmarkContent(bookmarkId, serverId)` per-bookmark or via `includeContent = true` on the list sync request (depending on D-08 resolution)
- Error handling for individual content fetch failures (skip silently vs. log warning)

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Core files being modified
- `composeApp/src/commonMain/kotlin/com/karakept/app/ui/screens/MainScreenModelActions.kt` — `removeBookmarkFromList` function (LIST-01 fix here)
- `composeApp/src/commonMain/kotlin/com/karakept/app/data/repository/BookmarkSyncPipeline.kt` — sync pipeline, `includeContent` parameter, `mapDtoToEntity` content handling
- `composeApp/src/commonMain/kotlin/com/karakept/app/data/local/entity/BookmarkEntity.kt` — needs `modifiedAt` column
- `composeApp/src/commonMain/kotlin/com/karakept/app/data/model/ListSettings.kt` — `syncOffline` flag definition

### API model (generated)
- `api-client/build/generated/openapi/src/main/kotlin/com/karakept/api/model/Bookmark.kt` — top-level `modifiedAt` field
- `api-client/build/generated/openapi/src/main/kotlin/com/karakept/api/model/BookmarkContent.kt` — `crawledAt`, `crawlStatus` fields (fallback if `modifiedAt` insufficient)

### Sync infrastructure
- `composeApp/src/commonMain/kotlin/com/karakept/app/ui/screens/MainScreenModel.kt` — `syncBookmarks()`, `_currentListContext`, `_accumulatedBookmarks` patterns
- `composeApp/src/commonMain/kotlin/com/karakept/app/data/repository/SettingsRepository.kt` — `allListSettings`, `contentSyncStrategy`, `contentSyncConfig`
- `composeApp/src/commonMain/kotlin/com/karakept/app/data/repository/BookmarkRepository.kt` — `syncBookmarksForList`, `fetchBookmarkContent`

### Requirements
- `.planning/REQUIREMENTS.md` — LIST-01, LIST-02 acceptance criteria

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `updateAccumulatedBookmarks { it.filter { } }` — optimistic removal pattern already used by `deleteBookmark` and ARCHIVE swipe action; replicate exactly for `removeBookmarkFromList`
- `fetchBookmarkContent(bookmarkId, serverId)` — per-bookmark content fetch already exists in `BookmarkRepository`
- `BookmarkSyncPipeline.mapDtoToEntity` — already handles `incomingContent` and overwrites when not blank; will handle re-crawled content naturally once it receives it

### Established Patterns
- Optimistic updates: all quick actions use `updateAccumulatedBookmarks` immediately, no DB reload
- `_accumulatedBookmarks` is the paginated in-memory list; `bookmarks` StateFlow = `_pendingBookmarks + _accumulatedBookmarks` with no list-context re-filtering
- `SyncStrategy.PER_LIST` exists as a global mechanism (`contentSyncConfig.selectedLists`); the new `syncOffline` per-list toggle is separate and currently unwired

### Integration Points
- `syncBookmarksForList` → `BookmarkSyncPipeline` with `includeContent = false` today; offline sync wiring connects here
- `SettingsRepository.allListSettings` — Flow of `Map<String, ListSettings>`; accessible in `MainScreenModel` and sync pipeline via DI

</code_context>

<specifics>
## Specific Ideas

- The `modifiedAt` staleness approach requires the planner to verify Karakeep server behavior before committing to it — treat D-08 as a mandatory research step
- `BookmarkEntity` already has no `modifiedAt` field; adding it requires a Room DB migration (increment schema version, add nullable column with default null)

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope.

</deferred>

---

*Phase: 05-list-sync*
*Context gathered: 2026-03-23*
