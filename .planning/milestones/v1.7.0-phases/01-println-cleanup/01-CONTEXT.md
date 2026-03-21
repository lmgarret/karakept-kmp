# Phase 1: Println Cleanup - Context

**Gathered:** 2026-03-21
**Status:** Ready for planning

<domain>
## Phase Boundary

Replace all remaining `println` debug calls (~97) in production source files with `AppLogger` calls or remove them entirely. Test files are excluded. Zero `println` calls should remain in production code after this phase.

</domain>

<decisions>
## Implementation Decisions

### Severity mapping
- **D-01:** Error/failure messages (`"Failed to..."`, `"Error..."`, exception catches) → `AppLogger.e()`
- **D-02:** Warnings and unexpected-but-recoverable states (`"Warning:"`, `"not found"`, `"null"`, skip/abort messages) → `AppLogger.w()`
- **D-03:** All other debug traces (step-by-step flow, success confirmations, state logging) → `AppLogger.d()`

### Hot-path removal
- **D-04:** Per-item logging inside loops or per-bookmark iteration is removed entirely, not replaced — per code-health Phase 6 decision
- **D-05:** Borderline cases: `getBookmarksPaged` per-bookmark detail lines (line 279 in BookmarkRepository) and per-action iteration logging in `BookmarkActionsRepositorySync` are hot-path and get removed
- **D-06:** Entry/exit logging for sync operations (called once per sync cycle, not per item) is kept as `.d()`

### Verbose trace consolidation
- **D-07:** `BookmarkActionsRepositorySync.kt` (53 calls): consolidate per-action-type step logging. Keep one `.d()` at action start, one at success, and `.e()` on failure. Remove intermediate step traces (e.g., "Calling updateBookmark with archived=true")
- **D-08:** Other files: convert 1:1 (each println becomes the appropriate AppLogger call) unless it's hot-path per D-04

### Tag naming
- **D-09:** Use the class name as the tag string (e.g., `"BookmarkRepository"`, `"ImageCacheManager"`). Where existing printlns already use a consistent prefix, preserve it as the tag
- **D-10:** `ReadProgressSync:` prefix lines in BookmarkActionsRepositorySync use tag `"ReadProgressSync"` to maintain logical grouping separate from the parent class

### Claude's Discretion
- Exact wording adjustments to log messages when consolidating
- Whether to add companion object TAG constants or inline tag strings
- AppLogger.kt itself keeps its internal println calls (it IS the logging implementation)

</decisions>

<specifics>
## Specific Ideas

No specific requirements — open to standard approaches

</specifics>

<canonical_refs>
## Canonical References

No external specs — requirements are fully captured in decisions above and in:
- `.planning/REQUIREMENTS.md` — LOG-01 requirement definition with per-file call counts
- `.planning/ROADMAP.md` — Phase 1 success criteria (zero println, correct severity, hot-path removed)
- `.planning/archive/code-health/phases/06-performance-optimization/06-CONTEXT.md` — Hot-path logging removal decision origin

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `AppLogger` (`composeApp/src/commonMain/kotlin/com/karakept/app/utils/AppLogger.kt`): Full logging API with `.d()`, `.i()`, `.w()`, `.e()` — direct replacement target for all println calls

### Established Patterns
- `ImageCacheManager.kt` already uses a `TAG` companion constant — this pattern can be followed in other files
- AppLogger uses `println` internally as its output mechanism — these two calls are NOT targets for replacement

### Integration Points
- No new dependencies or wiring needed — AppLogger is already importable from any commonMain file
- `HtmlRenderer.android.kt` is in androidMain, not commonMain — AppLogger is in commonMain so it's accessible

</code_context>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 01-println-cleanup*
*Context gathered: 2026-03-21*
