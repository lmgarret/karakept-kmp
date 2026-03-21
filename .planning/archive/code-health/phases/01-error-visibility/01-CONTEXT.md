# Phase 1: Error Visibility - Context

**Gathered:** 2026-03-21
**Status:** Ready for planning

<domain>
## Phase Boundary

Replace all silent failure patterns (`printStackTrace()`, debug `println`, `!!` operators) with structured error handling that surfaces failures to both developers (via logging) and users (via snackbars). Remove noisy reader progress logs. The app must fail visibly, not silently.

</domain>

<decisions>
## Implementation Decisions

### Error surfacing to users
- All errors that affect the user's experience should be surfaced — sync, load, render, etc. Not just sync failures.
- Use snackbar for error display — matches existing undo pattern via `ActionSnackbarManager`
- Error messages should be user-friendly plain language: "Couldn't sync bookmark" — no technical details in the message itself
- Offer a "Retry" action button on snackbars for recoverable errors (network failures, sync errors)
- Non-recoverable errors show snackbar without retry

### Claude's Discretion
- Logging framework choice — whether to add Kermit/Timber or keep structured println. Pick what's simplest for a KMP project.
- Null safety approach per-instance — replace all 20 `!!` operators, but use judgment on the replacement pattern (`.let`, guard, sealed state) based on context
- Which reader progress logs count as "noisy" — remove the frequent polling/progress logs, keep meaningful state change logs
- Error message wording — keep it short and friendly, Claude writes the copy

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Error handling concerns
- `.planning/codebase/CONCERNS.md` — Full list of files with printStackTrace (5+ files), !! operators (8 files with line numbers), RemoteDataSource debug println (line 469)

### Existing patterns
- `.planning/codebase/CONVENTIONS.md` §Error Handling — Documents existing `Result<T>` pattern, sealed state classes for error display
- `.planning/codebase/CONVENTIONS.md` §Logging — Documents current println-with-emoji approach, no production logging framework

### Architecture
- `.planning/codebase/ARCHITECTURE.md` — Layer boundaries (UI → Domain → Data), repository pattern, ScreenModel state management

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `ActionSnackbarManager` (domain layer): Already handles snackbar display for undo actions — extend for error snackbars with retry
- `Result<T>` pattern: Already used in repositories for fallible operations — standardize across all repositories
- `BackupState` sealed class: Example of error state pattern (`BackupState.Error("message")`) — replicate for other screens
- `StateFlow<T>`: Used throughout for state propagation — use for error state flows

### Established Patterns
- Repositories use try-catch + `Result.success()`/`Result.failure()` — keep this pattern, just stop swallowing exceptions
- ScreenModels use `screenModelScope` for coroutine launching — error handling should integrate here
- `SharingStarted.WhileSubscribed(5000)` used for flow lifecycle — error flows should follow same pattern

### Integration Points
- `BookmarkRepository` (5 occurrences of printStackTrace) — highest-impact file
- `BookmarkViewerScreenModel` (4 occurrences of printStackTrace) — reader-related failures
- `ListRepository` (3 occurrences) — list operation failures
- `MainScreenModel` (2 occurrences) — main screen failures
- `RemoteDataSource` line 469 — debug println for read progress sync
- 8 UI files with `!!` operators (see CONCERNS.md for exact line numbers)

</code_context>

<specifics>
## Specific Ideas

- User specifically mentioned removing noisy reading progress logs in the reader — these are the frequent polling/progress update logs that clutter output

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 01-error-visibility*
*Context gathered: 2026-03-21*
