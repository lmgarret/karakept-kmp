# Karakept Sync Pipeline

This document details the architecture and logic of the Karakept bookmark synchronization pipeline.

## Overview

The sync pipeline is a unified mechanism responsible for synchronizing bookmarks between the local database and the Karakept server. It handles:
- Fetching bookmark metadata (title, url, tags, etc.)
- Determining list membership
- Performing differential sync (Insert/Update/Delete)
- Fetching full content (HTML/Text) based on user configuration

## Sync Architectures

The pipeline supports three distinct configurations (`SyncConfiguration`):

1.  **Full Sync**:
    -   Fetches **all** bookmarks from the server.
    -   Fetches **all** lists and determines membership for every bookmark.
    -   **Deletes** local bookmarks that are no longer on the server.
    -   Used for the primary background sync and the "All Bookmarks" view.

2.  **Filtered Sync**:
    -   Fetches a subset of bookmarks (e.g., only Favorites or Archived).
    -   **Does NOT** fetch full list membership (preserves existing local list associations).
    -   **Does NOT** delete removed bookmarks (only upserts/updates).
    -   Used for quick updates of specific views (e.g., pulling down new favorites).

3.  **List Sync (ForList)**:
    -   Fetches bookmarks belonging to a specific `listId`.
    -   **Does NOT** delete removed bookmarks (only upserts).
    -   Also runs content sync (Phase 5) — gated by the list's `syncOffline` setting.
    -   Used when viewing a specific list to ensure it is up-to-date.

## 4-Step Sync Orchestration (Pull-to-Refresh)

When the user pulls to refresh (or the app starts a sync), `MainScreenModel.syncBookmarks()` follows a strict sequential + parallel order designed to show the user useful content as fast as possible:

```
Step 1: refreshLists(server)
        └─ Updates the navigation drawer immediately.

Step 2: syncCurrentView(server, currentList, currentFilter)
        └─ Syncs the list/filter the user is currently looking at.

Step 3: resetPaginationAndLoad(server, currentFilter)
        └─ Reloads the visible bookmark list from the local DB.
           User sees fresh bookmarks as quickly as possible.

Step 4: syncOtherLists(server, skipKey=currentKey)  [concurrent]
        └─ Each named list is synced independently in parallel.
           Only lists with syncOffline=true download content.
```

Quick-filter views (All Bookmarks, Favorites, Archived) also trigger Step 4, syncing all named lists afterward.

## The Pipeline Phases

The `BookmarkSyncPipeline` executes in 6 linear phases:

### Phase 1: Process Pending Actions
-   Uploads any locally pending changes (archives, favorites, moves) to the server.
-   This ensures local state is pushed before we pull new state.

### Phase 2: Fetch Metadata
-   Downloads bookmark DTOs (Data Transfer Objects) from the API.
-   Uses pagination (`cursor`) to retrieve all matching items.
-   **Optimization**: Does *not* request full content (`includeContent=false`) at this stage to save bandwidth.

### Phase 2.5: Sync Highlights
-   Syncs highlights from the server (skipped for ForList syncs).

### Phase 3: List Membership
-   **Full Sync**: Iterates through all available lists on the server and maps bookmarks to lists.
-   **List Sync**: Assigns all fetched bookmarks to the target list.
-   **Filtered Sync**: Skips this phase to avoid expensive list traversal; preserves existing relationships.

### Phase 4: Differential Sync (Map & Upsert)
-   Maps API DTOs to local `BookmarkEntity` objects.
-   Compares with existing local database entries.
-   **Updates**: Updates metadata for existing bookmarks. Preserves local content if the API update is metadata-only.
-   **Inserts**: Creates new local bookmarks.
-   **Deletes**: Removes local bookmarks not present in the fetch (only in Full Sync).

### Phase 4.5: Reconcile List Membership (ForList only)
-   Removes the synced `listId` from any local bookmark that the server no longer has in that list.
-   Merges list membership (adds `listId` to bookmarks in the list, does not strip other lists).

### Phase 5: Content Sync
-   Determines which bookmarks need full content based on:
    1. The global **Content Sync Strategy** (NEVER / PER_LIST / ALL)
    2. Per-list **syncOffline** setting (applies to ALL sync configurations including ForList)
-   Fetches and stores content for eligible bookmarks.

### Phase 6: Reading Progress Sync
-   Syncs reading progress from the server for in-progress bookmarks (capped for performance).

## Content Sync Strategies

Users can configure *when* content is downloaded for offline reading:

| Strategy | Behavior |
| :--- | :--- |
| **NEVER** | Never downloads content automatically. Content is fetched only when the user opens the bookmark. |
| **PER_BOOKMARK** | (Legacy/Not Fully Used) Same as Never currently. |
| **PER_LIST** | Downloads content **only** if the bookmark belongs to a specific set of "Offline Lists" selected by the user. |
| **ALL** | Downloads content for **every** bookmark. |

In addition to the global strategy, individual lists can have **syncOffline = true** in their per-list settings. Bookmarks in those lists have content downloaded during any sync — regardless of the global strategy or sync configuration type (Full, Filtered, or ForList).

## Per-List Sync Status and Deduplication

### SyncKey

Each pipeline execution is identified by a `SyncKey` (a type alias for `String?`):

| SyncKey value | Corresponds to |
| :--- | :--- |
| `null` | Full / All Bookmarks sync |
| `"__FAVORITES__"` | Filtered sync for Favorites |
| `"__ARCHIVED__"` | Filtered sync for Archived |
| `"<listId>"` | ForList sync for that list |

### Deduplication

`BookmarkRepository` prevents redundant concurrent pipelines via a set of active keys:

```
tryAcquireKey(key)  → if already in activeKeys: return 0 immediately (skip)
                      else: add to activeKeys → run pipeline → releaseKey(finally)
```

`ListRepository.refreshLists()` uses a `Mutex.tryLock()` guard with the same skip-if-running semantics. Rapid double-taps on pull-to-refresh therefore issue only one network request for the list fetch.

### perKeyProgress

`BookmarkRepository.perKeyProgress: StateFlow<Map<SyncKey, ListSyncStatus>>` tracks the live status of every running pipeline. Idle keys are removed from the map (not stored as explicit `Idle` entries).

```
ListSyncStatus:
  Idle              → key absent from map
  FetchingMetadata  → indeterminate spinner in drawer
  FetchingContent(current, total) → determinate progress ring in drawer
```

## UX Progress Tracking

### Drawer indicators

Each list item in the navigation drawer shows a `ListCountOrSyncIndicator`:

- **Idle**: displays the bookmark count as a number.
- **FetchingMetadata**: shows a 16 dp indeterminate `CircularProgressIndicator`, or `InlineLoadingDots` in e-ink mode (an indeterminate spinner never stops requesting panel refreshes).
- **FetchingContent**: shows a 16 dp determinate `CircularProgressIndicator` with `progress = current/total`.

### Top bar

`MainScreenModel.currentSyncStatus` is derived from `perKeyProgress` and the current list context:

```
currentSyncStatus = perKeyProgress[resolveCurrentKey(currentList, currentFilter)]
                    ?: ListSyncStatus.Idle
```

Switching lists while a sync is in progress immediately updates `currentSyncStatus` to reflect the new list's status (or Idle if that list is not syncing). Background syncs for other lists continue uninterrupted.

`isSyncing` (used for the pull-to-refresh spinner) is `true` only when the **current list's** key is present in `perKeyProgress` — background other-list syncs do not trigger the pull-to-refresh indicator. In e-ink mode there is no pull gesture: `isSyncing` disables the top bar's `Refresh` button instead, and the sync strip over the list shows `InlineLoadingDots` rather than an indeterminate bar.

## Content Fetching Logic (Precedence)

When fetching content (either during background sync or dynamically in the viewer), the system follows a strict precedence rule to determine the "best" content to show:

1.  **Inline HTML**: Checks `content.htmlContent` from the API response. If present, it is used immediately.
2.  **Asset HTML**: Checks for an asset of type `linkHtmlContent`. If found, it downloads the asset file.
3.  **Text/Note Fallback**: If no HTML is found, it falls back to `note` (user notes) or `content.text` (raw text).

This logic is unified in `BookmarkRepository.fetchRemoteContent` to ensure consistency between background syncs and foreground viewing.

## Dynamic Content Fetching (Transient Viewing)

If a bookmark does not have offline content (e.g., Strategy is "Never"), opening it in the viewer triggers a **Dynamic Fetch**:
1.  The viewer requests content for *just* that bookmark.
2.  The repository fetches it using the same precedence rules above.
3.  **Transient Display**: The content is displayed to the user but **NOT** persisted to the database (unless the user explicitly requests it or logic changes), keeping the database size small.
