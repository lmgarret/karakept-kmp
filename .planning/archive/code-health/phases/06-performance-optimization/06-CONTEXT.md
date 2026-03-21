# Phase 6: Performance Optimization - Context

**Gathered:** 2026-03-21
**Status:** Ready for planning

<domain>
## Phase Boundary

This phase ensures the app remains responsive with large bookmark collections (1000+) and long HTML articles. Focuses on LazyColumn composable stability, pagination verification, and HTML content caching/lazy-rendering.

</domain>

<decisions>
## Implementation Decisions

### LazyColumn Optimization
- Primary target: bookmark list item composable weight — ensure stable keys, avoid recomposition, use @Stable annotations
- Pagination: existing infinite scroll pagination exists in MainScreenModelPagination.kt — verify it works at scale, ensure smooth loading-more UX
- User explicitly wants: "fake infinite scroll, load bookmarks as we scroll" — pagination already implemented, phase should verify/optimize it
- Memory growth: ensure Coil image loading uses proper memory/disk cache limits, avoid holding bitmap references in ViewModel state
- Verification: code review + user testing (no Compose compiler metrics needed)

### HTML Content Optimization
- Cache parsed Ksoup Document in ScreenModel — avoid re-parsing on recomposition
- Progressive loading: show first N HTML blocks immediately, load rest as user scrolls — natural with LazyColumn rendering of HTML blocks
- LRU cache: max 5 parsed documents, evict on navigation away
- Memory stable: no unbounded growth from cached content

### Claude's Discretion
- Specific @Stable annotation targets and recomposition optimization details
- Exact LRU cache implementation approach
- HTML block chunking strategy for progressive rendering

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- `MainScreenModelPagination.kt` — existing pagination with loadNextPage, hasMoreItems, isLoadingMore
- `BookmarkListContent.kt` — current LazyColumn implementation for bookmark list
- `NativeHtmlRenderer.kt` — Ksoup-based HTML rendering (Desktop READER mode)
- `HtmlBlockRenderer.kt` — renders individual HTML blocks as Compose elements
- `HtmlContent.kt` — orchestrates HTML rendering, chooses READER vs WEB mode
- Coil image loading — already integrated for bookmark thumbnails

### Established Patterns
- Extension functions for ScreenModel concern separation (Phase 3 pattern)
- LazyColumn with rememberLazyListState used throughout
- ViewerScrollRestoration for scroll state persistence

### Integration Points
- `BookmarkListContent.kt` — main list rendering, pagination trigger
- `MainScreenScaffoldContent.kt` — scaffold wrapping the list
- `NativeHtmlRenderer.kt` — HTML block rendering pipeline
- `BookmarkViewerScreenModel.kt` — viewer state, where HTML cache would live

</code_context>

<specifics>
## Specific Ideas

- User wants pagination/infinite scroll verified and working smoothly at scale
- Existing pagination is in MainScreenModelPagination.kt — don't re-implement, optimize

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>
