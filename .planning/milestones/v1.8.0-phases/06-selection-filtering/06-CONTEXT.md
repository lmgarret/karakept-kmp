# Phase 06: Selection & Filtering - Context

**Gathered:** 2026-03-23
**Status:** Ready for planning

<domain>
## Phase Boundary

Fix three bugs:
1. **FILT-01**: Select-all only selects the first 20 entries (pagination bug) — must select ALL entries in the current list/filter
2. **FILT-02**: Quick Filters (All Bookmarks, Favorites, Archived, Highlights) in the navigation drawer show no counters — must show accurate counts
3. **FILT-03**: Highlights view has no pull-to-refresh gesture — must allow manual refresh

This phase does NOT add new selection modes, new filter types, or UI redesigns beyond what's needed for these three fixes.

</domain>

<decisions>
## Implementation Decisions

### FILT-01: Select-all strategy
- **D-01:** When `selectAll()` is called and `_hasMoreItems` is true (more pages not loaded), **fetch all matching IDs from DB in one query without loading full entities into memory**, add them to `_accumulatedBookmarks`, then select all. This avoids both the UX problem of silently auto-loading pages AND the correctness problem of selecting IDs not present in `_accumulatedBookmarks` (batch operations use `getSelectedBookmarks()` which reads from `_accumulatedBookmarks`).
- **D-02:** Requires a new DAO method — e.g., `getBookmarkIdsPaged` or a full-scan variant that returns all IDs matching the current filter without the `limit`/`offset` constraint.
- **D-03:** When `_hasMoreItems` is false (all pages already loaded), current behavior is correct — no change needed.
- **D-04:** The current filter (`_currentFilter`) must be applied to the DB query so only matching IDs are selected (e.g., viewing Favorites filter should select only starred non-archived bookmarks, not all bookmarks).

### FILT-02: Quick filter counter definitions
- **D-05:** **All Bookmarks** counter = non-archived bookmarks only (matches what the All Bookmarks view shows by default).
- **D-06:** **Favorites** counter = starred AND non-archived bookmarks only (matches what the Favorites filter shows).
- **D-07:** **Archived** counter = archived bookmarks (all archived, regardless of starred status).
- **D-08:** **Highlights** drawer item DOES show a count — total highlights in the DB, same visual style as other quick filter counters.
- **D-09:** Counts are **live/reactive** — derived from `allBookmarks` StateFlow (already used for `listCounts`) and a new highlights count from the highlights repository/DAO. Same `SharingStarted.WhileSubscribed(5000)` pattern as `listCounts`.
- **D-10:** The counter is displayed inline on the drawer item, same visual style as the existing list item counts (`bodySmall`, muted color, right-aligned).

### FILT-03: Pull-to-refresh on Highlights
- **D-11:** Pull-to-refresh triggers `syncHighlights()` — the same method that auto-fires on screen open. No special clear+reload behavior.
- **D-12:** Use Material3 `PullToRefreshBox` (or `rememberPullToRefreshState` + indicator) wrapping the `LazyColumn` in `HighlightsScreen`. The `isSyncing` state from `HighlightsScreenModel` drives the refresh indicator.

### Claude's Discretion
- Exact DAO method signature for bulk-ID fetch (D-02)
- Whether to handle the filter → DAO translation for the ID query inline in `selectAll()` or via a helper function
- Counter display: whether to show "0" or hide the counter when count is zero (match existing list items behavior)

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Selection logic
- `composeApp/src/commonMain/kotlin/com/karakept/app/ui/screens/MainScreenModelBatch.kt` — `selectAll()` (the bug, lines 76–78), `getSelectedBookmarks()` (why IDs must be in `_accumulatedBookmarks`)
- `composeApp/src/commonMain/kotlin/com/karakept/app/ui/screens/MainScreenModel.kt` — `_accumulatedBookmarks`, `allBookmarks`, `_currentFilter`, `pageSize = 20`
- `composeApp/src/commonMain/kotlin/com/karakept/app/ui/screens/MainScreenModelPagination.kt` — `loadBookmarksPage`, `_hasMoreItems`, `updateAccumulatedBookmarks`

### Drawer / quick filter counts
- `composeApp/src/commonMain/kotlin/com/karakept/app/ui/screens/main/MainScreenDrawer.kt` — `DrawerContent` composable, `BuiltinDrawerItem` (no count param today), `ListDrawerItem` (shows count already)
- `composeApp/src/commonMain/kotlin/com/karakept/app/ui/screens/MainScreenModel.kt` — `listCounts` StateFlow (pattern to replicate for quick filter counts), `allBookmarks` StateFlow

### Highlights pull-to-refresh
- `composeApp/src/commonMain/kotlin/com/karakept/app/ui/screens/HighlightsScreen.kt` — current screen layout, `LazyColumn`, `isSyncing` usage
- `composeApp/src/commonMain/kotlin/com/karakept/app/ui/screens/HighlightsScreenModel.kt` — `syncHighlights()`, `isSyncing` state, `loadInitialPage()`

### Requirements
- `.planning/REQUIREMENTS.md` — FILT-01, FILT-02, FILT-03 acceptance criteria

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `allBookmarks` StateFlow in `MainScreenModel` — all bookmarks (unfiltered, reactive) — use as the source for quick filter counts
- `listCounts` pattern in `MainScreenModel` — `combine(selectedServer, lists, allBookmarks, allListSettings)` → `stateIn(WhileSubscribed(5000))` — replicate for quick filter counts
- `syncHighlights()` in `HighlightsScreenModel` — already handles the full sync+reload cycle; pull-to-refresh just calls this
- `isSyncing` StateFlow in `HighlightsScreenModel` — drives loading indicator, also drives PullToRefreshBox indicator

### Established Patterns
- Batch operations read from `_accumulatedBookmarks` only — D-01 strategy (accumulate IDs into `_accumulatedBookmarks`) is mandatory for correctness
- List item counts shown in drawer via `count: Int?` param on `ListDrawerItem` — `BuiltinDrawerItem` needs the same treatment
- `SharingStarted.WhileSubscribed(5000)` used for all derived StateFlows to avoid keeping Room observers alive in background

</code_context>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope.

</deferred>

---

*Phase: 06-selection-filtering*
*Context gathered: 2026-03-23*
