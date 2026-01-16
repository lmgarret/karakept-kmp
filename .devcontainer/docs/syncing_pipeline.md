# Karakept Sync Pipeline

This document details the architecture and logic of the Karakept bookmark synchronization pipeline.

## Overview

The sync pipeline is a unified mechanism responsible for synchronizing bookmarks between the local database and the Karakept server. It handles:
- Fetching bookmark metadata (title, url, tags, etc.)
- Determining list membership
- performing differential sync (Insert/Update/Delete)
- Fetching full content (HTML/Text) based on user configuration

## Sync Architectures

The pipeline supports three distinct configurations (`SyncConfiguration`):

1.  **Full Sync**:
    -   Fetches **all** bookmarks from the server.
    -   Fetches **all** lists and determines membership for every bookmark.
    -   **Deletes** local bookmarks that are no longer on the server.
    -   Used for the primary background sync.

2.  **Filtered Sync**:
    -   Fetches a subset of bookmarks (e.g., only Favorites or Archived).
    -   **Does NOT** fetch full list membership (preserves existing local list associations).
    -   **Does NOT** delete removed bookmarks (only upserts/updates).
    -   Used for quick updates of specific views (e.g., pulling down new favorites).

3.  **List Sync**:
    -   Fetches bookmarks belonging to a specific `listId`.
    -   **Does NOT** delete removed bookmarks (only upserts).
    -   Used when viewing a specific list to ensure it is up-to-date.

## The Pipeline Phases

The `BookmarkSyncPipeline` executes in 5 linear phases:

### Phase 1: Process Pending Actions
-   Uploads any locally pending changes (archives, favorites, moves) to the server.
-   This ensures local state is pushed before we pull new state.

### Phase 2: Fetch Metadata
-   Downloads bookmark DTOs (Data Transfer Objects) from the API.
-   Uses pagination (`cursor`) to retrieve all matching items.
-   **Optimization**: Does *not* request full content (`includeContent=false`) at this stage to save bandwidth.

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

### Phase 5: Content Sync
-   Determines which bookmarks need full content based on the **Content Sync Strategy**.
-   Fetches and stores content for eligible bookmarks.

## Content Sync Strategies

Users can configure *when* content is downloaded for offline reading:

| Strategy | Behavior |
| :--- | :--- |
| **NEVER** | Never downloads content automatically. Content is fetched only when the user opens the bookmark. |
| **PER_BOOKMARK** | (Legacy/Not Fully Used) Same as Never currently. |
| **PER_LIST** | Downloads content **only** if the bookmark belongs to a specific set of "Offline Lists" selected by the user. |
| **ALL** | Downloads content for **every** bookmark. |

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
