# Phase 14: UI Interaction Fixes - Context

**Gathered:** 2026-03-27
**Status:** Ready for planning

<domain>
## Phase Boundary

Fix two UI interaction bugs:
1. **FILT-04**: Pull-to-refresh must work on Highlights on mobile compact layout
2. **UI-01**: Scroll-to-top FAB must reach actual top (index 0, offset 0) across all scroll-to-top sites

Additionally, unify the pull-to-refresh API across the app (migrate deprecated `pullRefresh` to MD3 `PullToRefreshBox`).

</domain>

<decisions>
## Implementation Decisions

### Highlights Pull-to-Refresh (FILT-04)
- **D-01:** The bug is in `HighlightsListContent.kt` — used by compact mobile layout in `MainScreen.kt:364`. The standalone `HighlightsScreen.kt` already has `PullToRefreshBox` but is never used on compact mobile.
- **D-02:** Claude's Discretion: Add `PullToRefreshBox` inside `HighlightsListContent` itself (not at the call site). The `onRefresh` and `isSyncing` params already exist. Both compact and expanded layouts will benefit.

### Scroll-to-Top Fix (UI-01)
- **D-03:** Root cause: `animateScrollToItem(0)` in `MainScreen.kt:199` does not pass `scrollOffset = 0`, so the list may stop a few pixels short when the first item has internal scroll offset.
- **D-04:** Audit ALL scroll-to-top sites in the app (MainScreen, BookmarkViewerContent, and any others) and fix them all — not just MainScreen.
- **D-05:** Claude's Discretion: Whether to explicitly reset `savedScrollIndex`/`savedScrollOffset` in MainScreenModel on scroll-to-top, or let the existing `snapshotFlow` observer handle it naturally.

### Pull-to-Refresh API Consistency
- **D-06:** Migrate `BookmarkListContent.kt` from deprecated Material `pullRefresh` modifier to MD3 `PullToRefreshBox`, matching `HighlightsScreen.kt` pattern. After this phase, the entire app uses one PTR pattern.

### Claude's Discretion
- D-02: Where to add PullToRefreshBox (inside HighlightsListContent recommended)
- D-05: Saved scroll position reset strategy

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Highlights Pull-to-Refresh
- `composeApp/src/commonMain/kotlin/com/karakept/app/ui/screens/main/HighlightsListContent.kt` — Target file for FILT-04 fix (missing PullToRefreshBox)
- `composeApp/src/commonMain/kotlin/com/karakept/app/ui/screens/HighlightsScreen.kt` — Reference implementation with working PullToRefreshBox (lines 79-82)
- `composeApp/src/commonMain/kotlin/com/karakept/app/ui/screens/MainScreen.kt` — Compact layout call site using HighlightsListContent (lines 353-380)
- `composeApp/src/commonMain/kotlin/com/karakept/app/ui/screens/HighlightsScreenModel.kt` — ScreenModel with `isSyncing` and `syncHighlights()`

### Scroll-to-Top
- `composeApp/src/commonMain/kotlin/com/karakept/app/ui/screens/MainScreen.kt` — Primary scroll-to-top trigger (line 199: `animateScrollToItem(0)`)
- `composeApp/src/commonMain/kotlin/com/karakept/app/ui/screens/MainScreenModel.kt` — `scrollToTopTrigger`, `savedScrollIndex`, `savedScrollOffset`
- `composeApp/src/commonMain/kotlin/com/karakept/app/ui/screens/BookmarkViewerContent.kt` — Audit for same scroll-to-top bug

### Pull-to-Refresh Migration
- `composeApp/src/commonMain/kotlin/com/karakept/app/ui/screens/main/BookmarkListContent.kt` — Uses deprecated `pullRefresh`, migrate to `PullToRefreshBox`

### Existing Tests
- `composeApp/src/androidUnitTest/kotlin/com/karakept/app/ui/screens/HighlightsPullToRefreshTest.kt` — Existing PTR test for standalone HighlightsScreen
- `composeApp/src/androidUnitTest/kotlin/com/karakept/app/ui/screens/viewer/ScrollToTopVisibilityTest.kt` — Scroll-to-top visibility tests

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `PullToRefreshBox` (MD3): Already used in `HighlightsScreen.kt` — proven pattern with `isRefreshing` + `onRefresh` params
- `HighlightsScreenModel`: Already exposes `isSyncing` and `syncHighlights()` — no new ViewModel work needed for FILT-04
- `HighlightsPullToRefreshTest.kt`: Existing test pattern for PTR that can be extended

### Established Patterns
- Pull-to-refresh: MD3 `PullToRefreshBox` wrapping content (HighlightsScreen.kt) — target pattern for all PTR
- Scroll-to-top: `SharedFlow<Unit>` trigger collected in `LaunchedEffect` calling `animateScrollToItem()` (MainScreen.kt)
- Scroll position persistence: `snapshotFlow` observer saving index+offset to ScreenModel volatile fields

### Integration Points
- `HighlightsListContent` already accepts `onRefresh` and `isSyncing` params — just needs wrapping in `PullToRefreshBox`
- `BookmarkListContent` uses `pullRefresh` modifier + `PullRefreshIndicator` from deprecated Material — replace with `PullToRefreshBox`
- MainScreen compact layout at line 364 passes all needed params to HighlightsListContent

</code_context>

<specifics>
## Specific Ideas

No specific requirements — open to standard approaches

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 14-ui-interaction-fixes*
*Context gathered: 2026-03-27*
