# Phase 6: Performance Optimization - Research

**Researched:** 2026-03-21
**Domain:** Compose Multiplatform LazyColumn performance, HTML rendering optimization
**Confidence:** HIGH

## Summary

Phase 6 targets two performance concerns: (1) bookmark list scrolling with 1000+ items and (2) HTML article loading in the reader. After investigating the current codebase, the existing implementation is reasonably well-structured -- BookmarkEntity is already `@Immutable`, LazyColumn uses stable keys, pagination is in place, and HTML processing is already offloaded to `Dispatchers.Default`.

The main optimization opportunities are: removing per-item println logging in the list (called for EVERY bookmark during scroll), adding `contentType` to LazyColumn items for faster recycling, caching parsed Ksoup Documents in the ScreenModel to avoid re-parsing, and upgrading the HtmlCache from naive FIFO to proper LRU with bounded size. The HTML reader already renders as a single composable inside one LazyColumn item -- the progressive rendering strategy should leverage the existing `NativeHtmlRenderer` structure by deferring non-visible block rendering.

**Primary recommendation:** Focus on measurable, low-risk changes: remove hot-path logging, add contentType, cache parsed Documents, and verify pagination at scale. Avoid premature architectural rewrites.

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- Primary target: bookmark list item composable weight -- ensure stable keys, avoid recomposition, use @Stable annotations
- Pagination: existing infinite scroll pagination exists in MainScreenModelPagination.kt -- verify it works at scale, ensure smooth loading-more UX
- User explicitly wants: "fake infinite scroll, load bookmarks as we scroll" -- pagination already implemented, phase should verify/optimize it
- Memory growth: ensure Coil image loading uses proper memory/disk cache limits, avoid holding bitmap references in ViewModel state
- Verification: code review + user testing (no Compose compiler metrics needed)
- Cache parsed Ksoup Document in ScreenModel -- avoid re-parsing on recomposition
- Progressive loading: show first N HTML blocks immediately, load rest as user scrolls -- natural with LazyColumn rendering of HTML blocks
- LRU cache: max 5 parsed documents, evict on navigation away
- Memory stable: no unbounded growth from cached content

### Claude's Discretion
- Specific @Stable annotation targets and recomposition optimization details
- Exact LRU cache implementation approach
- HTML block chunking strategy for progressive rendering

### Deferred Ideas (OUT OF SCOPE)
None -- discussion stayed within phase scope
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|-----------------|
| PERF-01 | Verify and optimize LazyColumn rendering for 1000+ bookmarks | Stable keys already in place, @Immutable on BookmarkEntity, contentType missing, per-item println logging is hot-path waste, pagination verified at code level |
| PERF-02 | Cache parsed HTML and lazy-load sections for large articles | NativeHtmlRenderer already uses `remember(html)` for Ksoup parse, but no cross-navigation cache; HtmlCache exists but is FIFO not LRU; viewer renders all HTML blocks in single item -- chunking opportunity exists |
</phase_requirements>

## Standard Stack

### Core (Already in Project)
| Library | Purpose | Perf Relevance |
|---------|---------|----------------|
| Compose Multiplatform | UI framework | LazyColumn, recomposition skipping, @Stable/@Immutable |
| Ksoup (fleeksoft) | HTML parsing | Document parsing is the heavy operation to cache |
| Coil 3 | Image loading | Memory/disk cache configuration for thumbnails |
| Room (SQLite) | Local storage | Pagination queries (LIMIT/OFFSET) |

### No New Dependencies Needed
This phase is purely optimization of existing code. No new libraries required.

## Architecture Patterns

### Current Bookmark List Architecture
```
MainScreenModel
  ├── _accumulatedBookmarks: List<BookmarkEntity>  (paginated, grows as user scrolls)
  ├── pageSize = 20
  └── loadNextPage() → findPageWithItems() → loadBookmarksPage()

BookmarkListContent (LazyColumn)
  ├── itemsIndexed(bookmarks, key = { _, b -> b.remoteId })
  ├── Detects scroll-near-end via snapshotFlow on layoutInfo
  └── Renders BookmarkCardLayout / BookmarkListLayout / BookmarkCompactListLayout
```

### Current HTML Viewer Architecture
```
BookmarkViewerScreenModel
  ├── loadBookmark(id) → observeBookmarkById(id) reactive Flow
  ├── transientContent cache (String, per-session, avoids re-fetch)
  └── No parsed Document cache

BookmarkViewerContent (LazyColumn)
  ├── item("hero_banner")
  ├── item("description_card")
  └── item("content_body") → ContentBodySection → HtmlContent → NativeHtmlRenderer
        └── remember(html) { Ksoup.parse(html) }  ← parsed once per composition
        └── Renders ALL blocks in a Column (not individually lazy)
```

### Pattern 1: Remove Hot-Path Logging
**What:** The bookmark list currently has `println("...")` calls at lines 437-444 of BookmarkListContent.kt that execute for EVERY visible item on EVERY recomposition/scroll.
**When to use:** Always -- this is pure waste in production.
**Implementation:** Delete the 4 println statements in the `wrapper { ... }` lambda of BookmarkListContent.kt.

### Pattern 2: Add contentType to LazyColumn
**What:** Compose LazyColumn can reuse item compositions more efficiently when items declare their content type. Currently no `contentType` is specified.
**When to use:** When a LazyColumn has heterogeneous item types (regular items, loading indicator, end-of-list text).
**Example:**
```kotlin
itemsIndexed(
    bookmarks,
    key = { _, bookmark -> bookmark.remoteId },
    contentType = { _, _ -> layoutType } // all bookmark items share same type
) { itemIndex, bookmark ->
    // ...
}

// Loading indicator
if (isLoadingMore) {
    item(contentType = "loading") {
        // ...
    }
}
```

### Pattern 3: Parsed Document LRU Cache
**What:** Cache parsed Ksoup `Document` objects keyed by bookmark ID, max 5 entries, evict oldest on navigation.
**When to use:** When user navigates between bookmarks and returns to a previously viewed one.
**Example:**
```kotlin
// In BookmarkViewerScreenModel or a shared cache object
class ParsedDocumentCache(private val maxSize: Int = 5) {
    private val cache = LinkedHashMap<Long, Document>(maxSize, 0.75f, true)

    fun get(bookmarkId: Long): Document? = cache[bookmarkId]

    fun put(bookmarkId: Long, document: Document) {
        if (cache.size >= maxSize) {
            val oldest = cache.keys.first()
            cache.remove(oldest)
        }
        cache[bookmarkId] = document
    }

    fun remove(bookmarkId: Long) { cache.remove(bookmarkId) }
    fun clear() { cache.clear() }
}
```

### Pattern 4: Progressive HTML Block Rendering
**What:** Instead of rendering all HTML blocks in a single Column inside one LazyColumn item, split into chunks rendered incrementally.
**Current state:** NativeHtmlRenderer renders the ENTIRE document in a single `Column` inside `item("content_body")`. The Column is NOT lazy -- all blocks compose at once.
**Approach:** Since the NativeHtmlRenderer is already a Column of block-level elements, insert a chunking layer: render the first N blocks immediately, then add remaining blocks on subsequent frames using `LaunchedEffect` + `mutableStateOf(visibleBlockCount)`.
**Caution:** Do NOT convert the inner Column to a nested LazyColumn -- nested scrollable containers in the same direction cause measurement issues and break scroll restoration. The chunking approach within the existing Column is safer.

### Anti-Patterns to Avoid
- **Nested LazyColumns in same scroll direction:** Would break scroll measurement and restoration. The viewer already uses a top-level LazyColumn; the HTML content MUST remain a regular Column inside a single item.
- **Premature optimization with Compose compiler metrics:** User explicitly decided against this. Use code review + user testing instead.
- **Re-implementing pagination:** Pagination already works. Verify at scale, don't rewrite.
- **Global singleton Document cache:** Use ScreenModel-scoped cache, not a global object, to ensure cleanup on navigation.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| LRU eviction | Custom linked list | `LinkedHashMap(accessOrder=true)` | Battle-tested, O(1) operations, already in Kotlin stdlib |
| Image memory management | Custom bitmap cache | Coil's built-in memory/disk cache | Coil already handles memory pressure, disk caching, and cancellation |
| Pagination | New paging library | Existing `MainScreenModelPagination.kt` | Already implemented and tested, just needs scale verification |

## Common Pitfalls

### Pitfall 1: Per-Item Side Effects in LazyColumn
**What goes wrong:** Code inside `itemsIndexed { }` runs during composition for every visible item. println, logging, complex computations in this scope cause jank.
**Why it happens:** Developers add debug logging and forget to remove it, or compute derived values inline.
**How to avoid:** Move all computation outside the lambda. Remove all println/logging from item composition scope.
**Warning signs:** Lines 437-444 in BookmarkListContent.kt are exactly this -- `println("...")` for every bookmark rendered.

### Pitfall 2: Missing contentType Causes Extra Recomposition
**What goes wrong:** Without `contentType`, Compose cannot reuse compositions when items are of different structural types (bookmark vs loading indicator vs end-of-list text).
**Why it happens:** `contentType` is optional and often forgotten.
**How to avoid:** Always specify `contentType` when LazyColumn has heterogeneous items.

### Pitfall 3: Unstable Lambda Captures in LazyColumn Items
**What goes wrong:** Lambda parameters that capture changing state force recomposition of items even when the item data hasn't changed.
**Why it happens:** Lambda identity changes on every recomposition if not `remember`ed.
**How to avoid:** Current code already uses `remember(bookmark.localId, isSelectionMode)` for onClick/onLongClick -- this is correct. Verify no other unstable captures exist.
**Current status:** The `wrapper` lambda and the image URL computation inside `wrapper { }` are re-computed per item per composition. The image URL logic (lines 411-433) involves string concatenation and `fileExists()` calls -- these should be stable since they depend on bookmark fields which are `@Immutable`.

### Pitfall 4: Ksoup.parse() on Main Thread
**What goes wrong:** Parsing large HTML documents blocks the UI thread.
**Why it happens:** `remember(html) { Ksoup.parse(html) }` runs synchronously during composition.
**How to avoid:** The parse already only runs once per unique `html` value (due to `remember`). For the caching layer, parse on `Dispatchers.Default` and expose via StateFlow.
**Current status:** HtmlContent.kt already processes HTML asynchronously via `produceState` + `Dispatchers.Default`. NativeHtmlRenderer then does `remember(html) { Ksoup.parse(html) }` on the ALREADY-PROCESSED (sanitized) HTML, which is a second parse. The sanitization is async but the Ksoup DOM parse is synchronous in composition.

### Pitfall 5: HtmlCache Collision Risk
**What goes wrong:** Current `HtmlCache.generateKey()` uses `html.hashCode()` which can collide for different HTML content.
**Why it happens:** `String.hashCode()` is only 32 bits.
**How to avoid:** Use bookmark ID as cache key instead of HTML content hash. Bookmark ID is unique and stable.
**Current status:** HtmlCache is imported in HtmlContent.kt but grep shows it's not actually USED in the rendering path -- it exists but appears unused. The actual caching happens via `remember(html)` in NativeHtmlRenderer and `produceState` in HtmlContent.

## Code Examples

### Removing Hot-Path Logging (BookmarkListContent.kt lines 436-444)
```kotlin
// REMOVE these 4 println statements entirely:
// println("LIST: Using bannerImage for bookmark ${bookmark.remoteId}")
// println("LIST: Using screenshot for bookmark ${bookmark.remoteId}")
// println("LIST: No asset available for bookmark ...")
// println("LIST: No image available for bookmark ...")
```

### Adding contentType to Bookmark LazyColumn
```kotlin
// In BookmarkListContent.kt, change:
itemsIndexed(bookmarks, key = { _, bookmark -> bookmark.remoteId }) { ... }
// To:
itemsIndexed(
    bookmarks,
    key = { _, bookmark -> bookmark.remoteId },
    contentType = { _, _ -> "bookmark" }
) { ... }
```

### Parsed Document Cache in ScreenModel
```kotlin
// New file or addition to BookmarkViewerScreenModel
private val parsedDocumentCache = LinkedHashMap<Long, com.fleeksoft.ksoup.nodes.Document>(
    5, 0.75f, true // accessOrder=true for LRU behavior
)

fun getCachedDocument(bookmarkId: Long, html: String): Document {
    return parsedDocumentCache.getOrPut(bookmarkId) {
        Ksoup.parse(html)
    }.also {
        // Evict if over limit
        while (parsedDocumentCache.size > 5) {
            val oldest = parsedDocumentCache.keys.first()
            parsedDocumentCache.remove(oldest)
        }
    }
}
```

### Progressive Block Rendering in NativeHtmlRenderer
```kotlin
// Inside NativeHtmlRenderer, after parsing document:
val allBlocks = remember(document) {
    body.childNodes().filterIsInstance<Element>().filter { isBlockElement(it) }
}
val totalBlocks = allBlocks.size
var visibleBlocks by remember(html) { mutableStateOf(minOf(20, totalBlocks)) }

// Render visible blocks
for (i in 0 until visibleBlocks) {
    RenderBlock(allBlocks[i], ...)
}

// Progressively reveal remaining blocks
LaunchedEffect(html, totalBlocks) {
    while (visibleBlocks < totalBlocks) {
        withFrameMillis { }  // Wait for next frame
        visibleBlocks = minOf(visibleBlocks + 10, totalBlocks)
    }
}
```

## State of the Art

| Current State | Optimization | Impact |
|---------------|-------------|--------|
| println per item in LazyColumn | Remove all 4 println calls | HIGH -- eliminates I/O on hot path |
| No contentType on LazyColumn items | Add contentType parameter | MEDIUM -- improves item recycling |
| Ksoup parse in remember(html) | Cache Document in ScreenModel LRU | MEDIUM -- avoids re-parse on back-navigation |
| All HTML blocks rendered at once | Progressive rendering with chunking | MEDIUM -- faster initial render for long articles |
| HtmlCache exists but unused | Remove or repurpose for Document cache | LOW -- cleanup |
| Coil image loading | Verify cache limits | LOW -- Coil defaults are generally good |

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | kotlin.test + MockK (commonTest) |
| Config file | build.gradle.kts (test dependencies section) |
| Quick run command | `./gradlew :composeApp:desktopTest --tests "*.PaginationUtilsTest"` |
| Full suite command | `./gradlew :composeApp:desktopTest` |

### Phase Requirements to Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| PERF-01 | Pagination loads pages correctly at scale | unit | `./gradlew :composeApp:desktopTest --tests "*.PaginationUtilsTest"` | Existing |
| PERF-01 | contentType specified on LazyColumn items | manual (code review) | N/A | N/A |
| PERF-01 | No println in hot path | manual (code review) | N/A | N/A |
| PERF-02 | ParsedDocumentCache evicts at max size | unit | `./gradlew :composeApp:desktopTest --tests "*.ParsedDocumentCacheTest"` | Wave 0 |
| PERF-02 | Progressive rendering shows initial blocks | manual (user testing) | N/A | N/A |

### Sampling Rate
- **Per task commit:** `./gradlew :composeApp:desktopTest --tests "*.PaginationUtilsTest"`
- **Per wave merge:** `./gradlew :composeApp:desktopTest`
- **Phase gate:** Full suite green before `/gsd:verify-work`

### Wave 0 Gaps
- [ ] `composeApp/src/commonTest/kotlin/com/karakept/app/utils/ParsedDocumentCacheTest.kt` -- covers PERF-02 cache eviction behavior

## Open Questions

1. **Coil memory cache configuration**
   - What we know: Coil is integrated and handles bookmark thumbnails. Default memory cache is typically 25% of app memory.
   - What's unclear: Whether custom limits are set in the project's Coil configuration.
   - Recommendation: Check `AppModule.kt` or Coil `ImageLoader` setup during implementation. If defaults, document they're sufficient.

2. **Scale of "large articles" for PERF-02**
   - What we know: Articles are HTML content of varying lengths. The current renderer handles them as a single Column.
   - What's unclear: Typical maximum article size in characters/blocks.
   - Recommendation: The progressive rendering (20 blocks initially, 10 per frame) should cover all reasonable sizes. If articles exceed 200+ blocks, the chunking will still complete within a second.

## Sources

### Primary (HIGH confidence)
- Direct codebase analysis of all referenced files
- BookmarkEntity.kt already annotated `@Immutable`
- BookmarkListContent.kt LazyColumn uses stable keys (`remoteId`)
- MainScreenModelPagination.kt pagination implementation reviewed
- NativeHtmlRenderer.kt Document parsing via `remember(html)` verified
- HtmlContent.kt async processing via `produceState` + `Dispatchers.Default` verified

### Secondary (MEDIUM confidence)
- Compose LazyColumn `contentType` optimization -- standard Compose best practice documented in official Android/Compose docs
- `LinkedHashMap(accessOrder=true)` for LRU -- Kotlin/Java stdlib, well-documented

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH -- no new dependencies, all analysis from existing code
- Architecture: HIGH -- direct code review of all relevant files
- Pitfalls: HIGH -- identified from actual code patterns found in review

**Research date:** 2026-03-21
**Valid until:** 2026-04-21 (stable domain, no fast-moving dependencies)
