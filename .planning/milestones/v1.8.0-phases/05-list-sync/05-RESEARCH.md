# Phase 05: List & Sync - Research

**Researched:** 2026-03-23
**Domain:** KMP/Room database, Karakeep API sync, Compose state management
**Confidence:** HIGH

## Summary

This phase addresses two bugs: LIST-01 (bookmark removal from list not visually reflected) and LIST-02 (per-list offline sync toggle saves but never triggers content download). Both bugs have well-understood root causes and straightforward fixes within existing patterns.

LIST-01 is a one-line fix: `removeBookmarkFromList` currently updates the in-memory `listIds` string but does not filter the bookmark out of `_accumulatedBookmarks`, so it remains visible in the list view. The fix is to replicate the `deleteBookmark` pattern (filter by `remoteId`).

LIST-02 requires wiring the existing `syncOffline` per-list setting into the sync pipeline. During any sync that processes bookmarks, bookmarks belonging to offline-enabled lists should have their content fetched. The `modifiedAt` staleness approach from CONTEXT.md D-05/D-06/D-07 needs revision because **Karakeep does NOT update `modifiedAt` on re-crawl** -- only on user actions (tagging, archiving, etc.). The recommended approach is to skip `modifiedAt` and instead use the simpler "missing content" check: fetch content for bookmarks in offline-enabled lists that lack local content.

**Primary recommendation:** Fix LIST-01 with a single filter change in `removeBookmarkFromList`. For LIST-02, wire `syncOffline` per-list settings into `BookmarkSyncPipeline.syncContent()` as a parallel content sync path alongside the existing `SyncStrategy.PER_LIST`, without adding a `modifiedAt` column to `BookmarkEntity`.

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- **D-01:** When a bookmark is removed from the current list via quick action, filter it out of `_accumulatedBookmarks` entirely -- same pattern as `deleteBookmark` and the ARCHIVE swipe action.
- **D-02:** Do NOT just update `listIds` on the entity in-memory -- the `bookmarks` StateFlow derives from `_pendingBookmarks + _accumulatedBookmarks` without re-filtering by list context.
- **D-03:** Content download for an offline-enabled list happens automatically on all syncs -- any sync that processes a list with `syncOffline = true` downloads content for that list's bookmarks.
- **D-04:** No separate "Download offline" button or explicit user action is needed beyond enabling the toggle and triggering sync.
- **D-05:** Use `modifiedAt` as a staleness signal (REVISED -- see D-08 resolution below).
- **D-06:** During sync for an offline-enabled list, fetch content only for bookmarks that meet either condition: content is missing, or `modifiedAt` differs (REVISED -- see D-08 resolution below).
- **D-07:** Bookmarks that already have content AND whose `modifiedAt` is unchanged are skipped (REVISED -- see D-08 resolution below).
- **D-08 (RESOLVED):** Karakeep does NOT update `modifiedAt` on re-crawl. See "D-08 Resolution" section below.

### Claude's Discretion
- DB migration version for adding `modifiedAt` column to `BookmarkEntity` (NO LONGER NEEDED -- see D-08 resolution)
- Whether to fetch content via `fetchBookmarkContent(bookmarkId, serverId)` per-bookmark or via `includeContent = true` on the list sync request
- Error handling for individual content fetch failures (skip silently vs. log warning)

### Deferred Ideas (OUT OF SCOPE)
None -- discussion stayed within phase scope.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| LIST-01 | Quick actions (e.g. removing a bookmark from a list) are immediately reflected in the currently viewed list (#154) | Direct fix in `removeBookmarkFromList` -- change `list.map {}` to `it.filter {}` following existing `deleteBookmark` pattern |
| LIST-02 | Enabling per-list offline sync actually downloads entries for offline reading (#155) | Wire `syncOffline` from `SettingsRepository.allListSettings` into `BookmarkSyncPipeline.syncContent()` as additional content fetch trigger |
</phase_requirements>

## Project Constraints (from CLAUDE.md)

- UI layer in `composeApp/src/commonMain/kotlin/com/karakept/app/ui/`
- Data layer in `composeApp/src/commonMain/kotlin/com/karakept/app/data/`
- Use Material3 components and `MaterialTheme.colorScheme.*` tokens
- Use `HorizontalDivider` (not deprecated `Divider`)
- Use `buildListHierarchy` for list display (not relevant to this phase)
- Use `TagChip`/`BookmarkTagsDisplay` for tag display (not relevant to this phase)

## D-08 Resolution: modifiedAt Behavior

**Confidence: HIGH** (verified from Karakeep server source code)

The Karakeep server defines `modifiedAt` using Drizzle ORM's `$onUpdate(() => new Date())`, which updates the timestamp whenever the bookmarks row is modified at the DB level. However, the `recrawlBookmark` mutation does **not** update the bookmarks row directly -- it enqueues a job to the `LowPriorityCrawlerQueue`. The crawl worker updates the `bookmarkLinks` table (content), not the `bookmarks` table, so `modifiedAt` is **not updated on re-crawl**.

**What updates `modifiedAt`:** User actions that modify the bookmarks row -- tagging, archiving, favoriting, title edits.

**What does NOT update `modifiedAt`:** Re-crawls, content re-fetches, screenshot re-generation.

**Implication for D-05/D-06/D-07:** The `modifiedAt` staleness approach is unreliable for detecting content changes. A bookmark could have been re-crawled with fresh content, but `modifiedAt` would remain unchanged. Therefore:

1. **Do NOT add `modifiedAt` column to `BookmarkEntity`** -- it provides no value for content staleness detection.
2. **Use the simpler "missing content" check:** Fetch content for bookmarks in offline-enabled lists where `content` is null/empty and not `"HAS_CONTENT"`. This is the same logic already used by `SyncStrategy.ALL` in the existing `syncContent()` method.
3. **For re-crawled content detection:** If future phases need to detect re-crawled content, the `crawledAt` field (inside `BookmarkContent`, requires `includeContent = true`) is the correct signal. But this is out of scope for this phase.

**Source:** [Karakeep server schema.ts](https://github.com/karakeep-app/karakeep/blob/main/packages/db/schema.ts) and [bookmarks.ts router](https://github.com/karakeep-app/karakeep/blob/main/packages/trpc/routers/bookmarks.ts)

## Architecture Patterns

### LIST-01: Optimistic Removal Pattern

The fix follows the exact pattern already established in `MainScreenModelActions.kt`:

**Current (broken) code in `removeBookmarkFromList`:**
```kotlin
// Updates listIds in memory but bookmark stays visible in list view
updateAccumulatedBookmarks { list ->
    list.map { ... it.copy(listIds = newListIds) ... }
}
```

**Fix (replicate deleteBookmark pattern):**
```kotlin
// Filter the bookmark out entirely when viewing the list it was removed from
updateAccumulatedBookmarks { it.filter { b -> b.remoteId != bookmark.remoteId } }
```

**Existing examples of this pattern:**
- `deleteBookmark()` (line 76): `it.filter { b -> b.remoteId != bookmark.remoteId }`
- `toggleBookmarkArchive()` (line 20): same filter pattern
- `executeScrollAction()` ARCHIVE branch (line 219): same filter pattern

**Key insight:** The `bookmarks` StateFlow combines `_pendingBookmarks + _accumulatedBookmarks` without re-filtering by list context. This means updating `listIds` on the entity does nothing for the current view -- the bookmark remains in the combined list. Filtering it out is the only correct approach.

**Edge case -- conditional filtering:** The removal should only filter the bookmark out when the user is currently viewing the list that the bookmark was removed from. If the user is on "All Bookmarks" view, updating `listIds` (the current behavior) is correct. The fix needs to check `_currentListContext.value == listId`.

### LIST-02: Offline Sync Wiring

The sync pipeline already has infrastructure for conditional content fetching in `BookmarkSyncPipeline.syncContent()`. The `syncOffline` per-list setting needs to be added as an additional trigger alongside the existing `SyncStrategy.PER_LIST`.

**Current flow:**
1. `PerListSettingsScreen` has a working `syncOffline` toggle that saves to `SettingsRepository` via `setListSettings()`
2. `SettingsRepository.allListSettings` exposes `Flow<Map<String, ListSettings>>`
3. `BookmarkSyncPipeline.syncContent()` checks `SyncStrategy` but never reads `allListSettings.syncOffline`

**Required wiring:**
1. In `syncContent()`, after the existing `SyncStrategy` switch, add a path that checks `allListSettings` for lists with `syncOffline = true`
2. For bookmarks in those lists that lack content (`readingTimeMinutes == 0` or content is null/empty), fetch content via `fetchRemoteContent()`
3. This runs on every sync (full, list, filtered) as per D-03

**Content fetch approach decision (Claude's Discretion):**
- **Recommended: Per-bookmark fetch via `fetchRemoteContent()`** -- the existing `fetchContentForBookmarks()` method already does this with progress reporting and image caching. Reuse it.
- Alternative: `includeContent = true` on list fetch -- this would double the initial metadata fetch size for all list syncs, even when content is not needed. Not recommended.

### Anti-Patterns to Avoid
- **Do NOT add `modifiedAt` to BookmarkEntity** -- it does not detect content changes (see D-08 resolution)
- **Do NOT create a separate sync path** -- reuse the existing `fetchContentForBookmarks()` infrastructure
- **Do NOT filter in the `bookmarks` StateFlow** -- the flow is designed to not re-filter by context; optimistic removal is the correct pattern

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Content fetch with progress | Custom HTTP content fetcher | `fetchContentForBookmarks()` in `BookmarkSyncPipeline` | Already handles progress, image caching, hero assets |
| Optimistic list removal | Custom StateFlow filtering | `updateAccumulatedBookmarks { it.filter {} }` | Established pattern used by 3+ existing actions |
| Per-list settings access | Direct DataStore reads | `settingsRepository.allListSettings` Flow | Already parsed, typed, and cached |

## Common Pitfalls

### Pitfall 1: Removing bookmark from ALL views, not just the current list
**What goes wrong:** Filtering the bookmark out unconditionally means it vanishes from "All Bookmarks" too, even though it still exists.
**Why it happens:** Blindly copying the `deleteBookmark` pattern without considering context.
**How to avoid:** Check `_currentListContext.value == listId` before filtering. If the user is not viewing the specific list, update `listIds` in memory (current behavior) instead of filtering out.
**Warning signs:** Bookmark disappears from "All Bookmarks" after removing from a list.

### Pitfall 2: Content sync running on every sync for ALL bookmarks
**What goes wrong:** Performance degradation -- fetching content for hundreds of bookmarks on every sync.
**Why it happens:** Not filtering to only bookmarks that lack content in offline-enabled lists.
**How to avoid:** Filter to bookmarks where `readingTimeMinutes == 0` AND bookmark is in a list with `syncOffline = true`.
**Warning signs:** Sync takes much longer after enabling offline sync for a list.

### Pitfall 3: Room migration version mismatch
**What goes wrong:** App crashes on startup with `IllegalStateException: Room migration missing`.
**Why it happens:** Forgetting to increment the DB version or not adding the migration to both Android and Desktop database builders.
**How to avoid:** This pitfall is AVOIDED entirely -- no DB migration needed since `modifiedAt` column is not being added.

### Pitfall 4: Missing list hierarchy in offline sync
**What goes wrong:** Enabling offline sync for a parent list does not sync child list bookmarks.
**Why it happens:** Only checking exact `listId` match without considering hierarchy.
**How to avoid:** Use `ListHierarchyUtils.getAllDescendantIds()` if the list has `includeChildListBookmarks = true` in its settings. This is already a field in `ListSettings`.
**Warning signs:** Child list bookmarks not available offline when parent has `syncOffline = true`.

## Code Examples

### LIST-01 Fix: Conditional optimistic removal
```kotlin
// Source: Pattern from MainScreenModelActions.kt deleteBookmark + archive actions
fun MainScreenModel.removeBookmarkFromList(bookmark: BookmarkEntity, listId: String) {
    screenModelScope.launch {
        val isOnline = !_isSyncing.value
        bookmarkActionsRepository.removeFromList(
            bookmark.remoteId, bookmark.serverId, listId, isOnline
        )
        // If currently viewing the list the bookmark was removed from, filter it out
        if (_currentListContext.value == listId) {
            updateAccumulatedBookmarks { it.filter { b -> b.remoteId != bookmark.remoteId } }
        } else {
            // Otherwise just update listIds in memory
            updateAccumulatedBookmarks { list ->
                list.map {
                    if (it.remoteId == bookmark.remoteId) {
                        val newListIds = it.listIds.split(",")
                            .map { id -> id.trim() }
                            .filter { id -> id.isNotBlank() && id != listId }
                        it.copy(listIds = newListIds.joinToString(","))
                    } else it
                }
            }
        }
    }
}
```

### LIST-02: Offline sync integration point in syncContent()
```kotlin
// Source: Extension of existing BookmarkSyncPipeline.syncContent() pattern
// After existing SyncStrategy switch, add offline-enabled list check:
val offlineListIds = settingsRepository.allListSettings.first()
    .filter { (_, settings) -> settings.syncOffline }
    .keys

if (offlineListIds.isNotEmpty()) {
    val offlineBookmarks = entities.filter { entity ->
        val entityListIds = entity.listIds.split(",").filter { it.isNotEmpty() }.toSet()
        entityListIds.intersect(offlineListIds).isNotEmpty() && entity.readingTimeMinutes == 0
    }
    if (offlineBookmarks.isNotEmpty()) {
        fetchContentForBookmarks(offlineBookmarks)
    }
}
```

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit + kotlinx-coroutines-test (desktop target) |
| Config file | `composeApp/build.gradle.kts` (test dependencies) |
| Quick run command | `./gradlew :composeApp:desktopTest --tests "com.karakept.app.*" -x kspCommonMainKotlinMetadata` |
| Full suite command | `./gradlew :composeApp:desktopTest -x kspCommonMainKotlinMetadata` |

### Phase Requirements to Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| LIST-01 | removeBookmarkFromList filters bookmark out of accumulated list when viewing that list | unit | Manual verification (UI state test) | No -- Wave 0 |
| LIST-01 | removeBookmarkFromList updates listIds when NOT viewing that list | unit | Manual verification (UI state test) | No -- Wave 0 |
| LIST-02 | syncContent fetches content for bookmarks in offline-enabled lists | unit | Manual verification (sync pipeline test) | No -- Wave 0 |
| LIST-02 | syncContent skips bookmarks that already have content | unit | Manual verification | No -- Wave 0 |

### Sampling Rate
- **Per task commit:** Manual build verification (`./gradlew :composeApp:assembleDebug`)
- **Per wave merge:** Full test suite
- **Phase gate:** Full suite green before `/gsd:verify-work`

### Wave 0 Gaps
- Tests for these fixes require mocking `BookmarkRepository`, `SettingsRepository`, and `BookmarkActionsRepository` -- this is integration-level testing
- Existing integration tests in `composeApp/src/desktopTest/` test against a live Karakeep server, not suitable for unit-level validation of these fixes
- **Recommendation:** Verify these fixes manually via app testing rather than creating new automated tests, since the existing test infrastructure is integration-focused and both fixes are small, well-scoped changes

## Sources

### Primary (HIGH confidence)
- Karakeep server source: [schema.ts](https://github.com/karakeep-app/karakeep/blob/main/packages/db/schema.ts) - `modifiedAt` field definition with `$onUpdate`
- Karakeep server source: [bookmarks.ts](https://github.com/karakeep-app/karakeep/blob/main/packages/trpc/routers/bookmarks.ts) - `recrawlBookmark` does NOT update `modifiedAt`
- Project codebase: `MainScreenModelActions.kt` - existing optimistic update patterns
- Project codebase: `BookmarkSyncPipeline.kt` - existing content sync infrastructure
- Project codebase: `BookmarkEntity.kt` - current schema (version 8, no `modifiedAt`)
- Project codebase: `ListSettings.kt` - `syncOffline` field definition
- Project codebase: `RemoteDataSource.kt` - `fetchBookmarksForList` with `includeContent` parameter

### Secondary (MEDIUM confidence)
- [Karakeep 0.22.0 release notes](https://github.com/karakeep-app/karakeep/discussions/964) - `modifiedAt` introduced, updates on tag changes

### Tertiary (LOW confidence)
- None

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH - using only existing project libraries, no new dependencies
- Architecture: HIGH - both fixes follow established patterns in the codebase
- Pitfalls: HIGH - verified against Karakeep server source and existing code patterns

**Research date:** 2026-03-23
**Valid until:** 2026-04-23 (stable domain, no external dependency changes)
