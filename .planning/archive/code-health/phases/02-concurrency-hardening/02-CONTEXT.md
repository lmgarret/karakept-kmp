# Phase 2: Concurrency Hardening - Context

**Gathered:** 2026-03-21
**Status:** Ready for planning

<domain>
## Phase Boundary

Refactor MainScreenModel initialization and read/unread toggling to be race-free with documented invariants. No user-facing behavior changes — purely internal hardening.

</domain>

<decisions>
## Implementation Decisions

### Claude's Discretion
All implementation choices are at Claude's discretion — pure infrastructure phase. Key decisions include:
- State machine representation (sealed class, enum, or equivalent construct)
- Whether mutex is needed for tag cache race condition (based on audit findings)
- Verification approach for no-duplicate-loads invariant

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- `MainScreenModel.kt` lines 237-290: current sequential startup coroutine with documented race condition mitigation
- `BookmarkActionsRepository.kt` line 57: tag cache comment for read/unread race condition (appears to be vestigial — no actual cache implementation found)
- `BookmarkActionsRepository.kt` lines 159-196: markAsRead/markAsUnread with optimistic local updates + emit bookmarkChangedEvents
- Existing `BookmarkActionsRepositoryUnitTest.kt` with known race timing comments (lines 111, 180)

### Established Patterns
- `screenModelScope.launch {}` for coroutine lifecycle management
- `StateFlow` + `stateIn()` for state propagation
- `SharingStarted.WhileSubscribed(5000)` for flow lifecycle
- `withContext(Dispatchers.IO)` for IO operations
- `MutableSharedFlow(extraBufferCapacity = 16)` for event emission

### Integration Points
- `MainScreenModel.init {}` — Coroutines A (server sync) and B (sequential startup) are the primary targets
- `BookmarkActionsRepository` — markAsRead/markAsUnread + bookmarkChangedEvents flow
- `DefaultFilterResolver` — provides initial filter config during startup
- `_accumulatedBookmarks` — mutable state that receives updates from multiple coroutines

</code_context>

<specifics>
## Specific Ideas

No specific requirements — infrastructure phase

</specifics>

<deferred>
## Deferred Ideas

None

</deferred>

---

*Phase: 02-concurrency-hardening*
*Context gathered: 2026-03-21*
