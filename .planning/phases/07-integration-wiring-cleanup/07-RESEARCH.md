# Phase 7: Integration Wiring & Cleanup - Research

**Researched:** 2026-03-21
**Domain:** Kotlin/Compose integration wiring, tech debt cleanup
**Confidence:** HIGH

## Summary

Phase 7 closes three non-critical integration gaps identified in the v1.0 milestone audit, plus reduces two files that were over the 500-line target. All work is internal wiring and cleanup -- no new libraries, no new architecture patterns, no external dependencies.

The three integration gaps are: (1) ParsedDocumentCache is allocated but `getCachedOrParseDocument()` is never called from composables -- NativeHtmlRenderer independently parses via `remember(html)`, (2) `ServerRepository.triggerMigration()` has no production call site so existing DB credentials are never promoted to SecureCredentialStore, and (3) 20 `println` calls remain in BookmarkViewerScreenModel.kt plus 10 in App.kt.

**Primary recommendation:** This phase requires no research into external libraries or architecture patterns. It is purely wiring existing code together and removing debug output. All five success criteria are verifiable with grep/wc commands.

**Key update:** BookmarkSyncPipeline.kt is now 493 lines and SettingsRepositoryMutations.kt is 478 lines -- both already under the 500-line target. Success criteria 4 and 5 are already satisfied.

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|-----------------|
| PERF-02 | Cache parsed HTML and lazy-load sections for large articles | Wire `getCachedOrParseDocument()` into NativeHtmlRenderer/HtmlContent composable chain so cache hits occur on back-navigation |
| SEC-02 | Move API credentials from cleartext DB to platform keychain/keystore | Add `triggerMigration()` call site in App.kt startup path so existing DB credentials are migrated to SecureCredentialStore |
| ERR-01 | Replace all println calls with structured logging | Replace 20 println in BookmarkViewerScreenModel.kt and 10 println in App.kt with AppLogger calls or removal |
</phase_requirements>

## Standard Stack

No new libraries needed. All work uses existing project code:

| Component | Location | Purpose |
|-----------|----------|---------|
| ParsedDocumentCache | `ui/utils/ParsedDocumentCache.kt` | LRU cache for parsed Ksoup Documents |
| AppLogger | `utils/AppLogger.kt` | Structured logging with severity levels |
| ServerRepository | `data/repository/ServerRepository.kt` | Server management with triggerMigration() |
| SecureCredentialStore | `data/secure/SecureCredentialStore.kt` | Platform keychain/keystore for API keys |
| NativeHtmlRenderer | `ui/components/reader/NativeHtmlRenderer.kt` | Compose-native HTML renderer |
| HtmlContent | `ui/components/HtmlContent.kt` | Wrapper composable that dispatches to NativeHtmlRenderer |

## Architecture Patterns

### Gap 1: Document Cache Wiring (PERF-02)

**Current state:** `NativeHtmlRenderer` parses HTML independently at line 91-97:
```kotlin
val document = remember(html) {
    Ksoup.parse(html)
}
```
`BookmarkViewerScreenModel` holds a `ParsedDocumentCache` and exposes `getCachedOrParseDocument(bookmarkId, html)` but no composable calls it.

**Wiring approach:** Modify `NativeHtmlRenderer` to accept an optional pre-parsed `Document?` parameter. When provided, skip the internal `remember(html) { Ksoup.parse(html) }` call. The caller (`HtmlContent` or `BookmarkViewerContent`) obtains the document from `screenModel.getCachedOrParseDocument()` and passes it down.

**Alternative simpler approach:** Have `NativeHtmlRenderer` accept a `documentProvider: (String) -> Document?` lambda that defaults to `Ksoup.parse(html)`. The composable in BookmarkViewerContent passes `{ html -> screenModel.getCachedOrParseDocument(bookmarkId, html) }`.

**Recommended approach:** Add a `document: Document? = null` parameter to `NativeHtmlRenderer`. This is simpler, keeps the composable signature clean, and the caller can pass null (triggering internal parsing) when no cache is available. The composable call chain is:

1. `BookmarkViewerContent` has access to `screenModel` and `bookmarkId`
2. `BookmarkViewerContent` calls `HtmlContent` with the HTML
3. `HtmlContent` calls `NativeHtmlRenderer`

The cache must be wired at step 2 or 3. Since `HtmlContent` is a general-purpose composable (also used outside the viewer), the cleanest approach is to add the optional `document` parameter to both `HtmlContent` and `NativeHtmlRenderer`, and pass the cached document from `BookmarkViewerContent`.

### Gap 2: Migration Trigger (SEC-02)

**Current state:** `ServerRepository.triggerMigration()` exists but is only called from `ServerRepositoryMigrationTest.kt`. The `ensureMigrated()` method uses a Mutex-protected double-check pattern and is idempotent.

**Wiring approach:** Call `triggerMigration()` from `App.kt` in a `LaunchedEffect(Unit)` block, similar to the existing `backupRepository.checkAndRunScheduledExport()` call at lines 84-92. This runs once on app startup.

**Call site:** In `App.kt`, after KoinApplication setup where `serverRepository` is already injected (line 30), add a LaunchedEffect:
```kotlin
LaunchedEffect(Unit) {
    serverRepository.triggerMigration()
}
```

This is fire-and-forget, idempotent, and the Mutex ensures thread safety. Place it near the existing backup auto-export LaunchedEffect (line 84).

### Gap 3: println Cleanup (ERR-01)

**Current state:**
- **BookmarkViewerScreenModel.kt:** 20 `println` calls -- all debug tracing for ReadProgressSync, refresh flow, and highlight creation
- **App.kt:** 10 `println` calls -- 3 in Coil interceptor, 7 in navigation LaunchedEffect

**Approach per Phase 6 decision:** "Remove per-item debug println entirely rather than downgrade to AppLogger (hot-path should have zero logging)."

For BookmarkViewerScreenModel.kt:
- ReadProgressSync tracing (lines 170, 183, 190, 193, 206, 338, 343, 353): Remove entirely per Phase 6 decision -- hot-path should have zero logging
- Refresh flow logging (lines 254, 260, 264, 272, 279, 281, 290, 299): Downgrade to `AppLogger.d()` for non-hot-path operations that are useful for debugging
- Highlight creation (lines 562, 567, 571, 573): Downgrade to `AppLogger.d()` for useful diagnostic info

For App.kt:
- Coil interceptor (lines 41, 48, 51): Remove entirely -- hot-path image loading should have zero logging
- Navigation LaunchedEffect (lines 103, 105, 112, 116, 119, 125, 128): Downgrade to `AppLogger.d()` -- startup logging useful for diagnostics, runs once

### File Size Check (Success Criteria 4 & 5)

**Current state verified:**
- `BookmarkSyncPipeline.kt`: **493 lines** (under 500 target)
- `SettingsRepositoryMutations.kt`: **478 lines** (under 500 target)

Both files are already under the 500-line target. The audit was based on an earlier snapshot. **No splitting work needed.**

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Structured logging | Custom logging solution | `AppLogger` | Already exists in project, used everywhere else |
| Secure credential storage | Custom encryption | `SecureCredentialStore` | Already built in Phase 5, platform-specific |
| Document caching | New cache implementation | `ParsedDocumentCache` | Already built in Phase 6, LRU with LinkedHashMap |

## Common Pitfalls

### Pitfall 1: Breaking NativeHtmlRenderer's remember(html) behavior
**What goes wrong:** Passing a cached Document that was parsed from different HTML (e.g., stale cache entry after content update)
**Why it happens:** Cache keyed by bookmarkId, but content can change on refresh
**How to avoid:** The `getCachedOrParseDocument(bookmarkId, html)` method already handles this -- it checks the cache by bookmarkId but parses fresh if the cache misses. The Document is immutable once parsed. If content changes, the composable's `html` parameter changes, triggering recomposition which calls getCachedOrParseDocument with the new HTML.

### Pitfall 2: triggerMigration() blocking app startup
**What goes wrong:** Migration reads all servers from DB and writes to secure store -- could be slow with many servers
**Why it happens:** Called synchronously on startup
**How to avoid:** Already mitigated -- `triggerMigration()` is called in a `LaunchedEffect(Unit)` coroutine (non-blocking). The method is also idempotent (checks `migrated` flag first) so subsequent calls are no-ops.

### Pitfall 3: Removing println from error catch blocks
**What goes wrong:** Accidentally removing error logging that was already converted to AppLogger
**Why it happens:** Grep finds all println, some nearby lines are AppLogger calls
**How to avoid:** Only target the specific println lines listed in the audit. The AppLogger calls at catch blocks (lines 293, 327, 407, 488, 576) should remain untouched.

### Pitfall 4: HtmlContent composable is shared
**What goes wrong:** Adding a required `document` parameter breaks other callers
**Why it happens:** HtmlContent is used by both BookmarkViewer and potentially other screens
**How to avoid:** Make the `document` parameter optional with `null` default. When null, NativeHtmlRenderer falls back to its internal `remember(html) { Ksoup.parse(html) }` behavior.

## Code Examples

### Wiring ParsedDocumentCache into NativeHtmlRenderer

```kotlin
// NativeHtmlRenderer.kt - add optional document parameter
@Composable
fun NativeHtmlRenderer(
    html: String,
    modifier: Modifier = Modifier,
    preParsedDocument: Document? = null, // NEW: from cache
    // ... existing params
) {
    // Use pre-parsed document if available, otherwise parse internally
    val document = preParsedDocument ?: remember(html) {
        try { Ksoup.parse(html) } catch (e: Exception) { null }
    }
    // ... rest unchanged
}
```

```kotlin
// HtmlContent.kt - thread document through
@Composable
fun HtmlContent(
    html: String?,
    viewerMode: ViewerMode,
    preParsedDocument: Document? = null, // NEW
    // ... existing params
) {
    // ... in READER branch:
    NativeHtmlRenderer(
        html = processedHtml ?: "",
        preParsedDocument = preParsedDocument,
        // ... rest unchanged
    )
}
```

```kotlin
// BookmarkViewerContent.kt - obtain from cache and pass down
val cachedDocument = remember(fullyLoadedState?.bookmark?.localId, fullyLoadedState?.bookmark?.content) {
    val bookmark = fullyLoadedState?.bookmark ?: return@remember null
    val content = bookmark.content ?: return@remember null
    screenModel.getCachedOrParseDocument(bookmark.localId, content)
}
// Pass cachedDocument to HtmlContent as preParsedDocument
```

### Wiring triggerMigration into App.kt startup

```kotlin
// App.kt - add alongside existing backup auto-export
LaunchedEffect(Unit) {
    try {
        serverRepository.triggerMigration()
    } catch (_: Exception) {
        // Migration is best-effort -- existing creds work via DB fallback
    }
}
```

### println replacement patterns

```kotlin
// HOT PATH: Remove entirely (ReadProgressSync, Coil interceptor)
// Before:
println("ReadProgressSync: debounce fired -- saving to DB localId=${state.localId}")
// After:
// (removed)

// NON-HOT PATH: Replace with AppLogger.d (refresh, navigation, highlights)
// Before:
println("BookmarkViewerScreenModel: Starting refresh for bookmark ${currentState.bookmark.remoteId}")
// After:
AppLogger.d("ViewerModel", "Starting refresh for bookmark ${currentState.bookmark.remoteId}")

// ERROR PATH: Replace with AppLogger.w (parsing errors in App.kt)
// Before:
println("   Error parsing openBookmarkId: ${e.message}")
// After:
AppLogger.w("App", "Error parsing openBookmarkId: ${e.message}")
```

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | kotlin.test + MockK (JVM) |
| Config file | `build.gradle.kts` (commonTest, desktopTest) |
| Quick run command | User runs tests externally (remote devcontainer) |
| Full suite command | User runs tests externally |

### Phase Requirements to Test Map
| Req ID | Behavior | Test Type | Automated? | File Exists? |
|--------|----------|-----------|------------|-------------|
| PERF-02 | getCachedOrParseDocument called from composables, cache hits on re-navigation | manual | Manual verify via grep for call site | N/A |
| SEC-02 | triggerMigration called on app startup | manual | Manual verify via grep for call site in App.kt | N/A |
| ERR-01 | No println in BookmarkViewerScreenModel.kt or App.kt | unit | `grep -c println` on both files = 0 | N/A |

### Sampling Rate
- **Per task commit:** `grep -c println` on target files
- **Phase gate:** `wc -l` on BookmarkSyncPipeline.kt, SettingsRepositoryMutations.kt; `grep -c println` on BookmarkViewerScreenModel.kt, App.kt; `grep -c getCachedOrParseDocument` in composable files; `grep -c triggerMigration` in App.kt

### Wave 0 Gaps
None -- this phase is pure wiring and cleanup, no new test files needed. Existing `ParsedDocumentCacheTest.kt` and `ServerRepositoryMigrationTest.kt` already cover the underlying functionality. Phase 7 just wires call sites.

## Open Questions

1. **Should NativeHtmlRenderer accept Document or use a provider lambda?**
   - What we know: Direct Document parameter is simpler; lambda is more flexible
   - Recommendation: Use direct `Document?` parameter -- simpler, and the cache is already in the ScreenModel

2. **Should App.kt println calls in the Coil interceptor be removed or converted?**
   - What we know: Phase 6 decided "hot-path should have zero logging"; Coil image loading is a hot path
   - Recommendation: Remove Coil interceptor println entirely. Keep navigation println as AppLogger.d since they run once at startup.

## Sources

### Primary (HIGH confidence)
- Direct code inspection of all affected files in the repository
- v1.0-MILESTONE-AUDIT.md gap definitions
- Phase 6 decisions from STATE.md

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH - no new libraries, all existing code
- Architecture: HIGH - all three gaps are simple wiring with clear call sites
- Pitfalls: HIGH - well-understood patterns, idempotent operations

**Research date:** 2026-03-21
**Valid until:** Indefinite -- internal wiring, no external dependency changes
