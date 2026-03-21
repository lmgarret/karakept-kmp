# Phase 2: Concurrency Hardening - Research

**Researched:** 2026-03-21
**Domain:** Kotlin coroutines concurrency, state machine patterns, MutableStateFlow thread safety
**Confidence:** HIGH

## Summary

Phase 2 targets two specific concurrency issues in MainScreenModel and BookmarkActionsRepository. The first (CONC-01) is about making the implicit coroutine startup sequence in `MainScreenModel.init {}` explicit via a state machine. The second (CONC-02) is about auditing the "tag cache race condition" mentioned in BookmarkActionsRepository.

After thorough code review, the tag cache race condition comment at line 57 of BookmarkActionsRepository is **vestigial** -- there is no actual tag cache implementation. The `markAsRead`/`markAsUnread` methods perform direct DB writes with no caching layer, so the originally feared race does not exist in the current code. However, a much more significant concurrency concern exists: `_accumulatedBookmarks` is mutated from **at least 5 independent coroutines** (init Coroutine B, bookmarkChangedEvents collector, undoCompletedEvents collector, user actions like toggleRead/archive/delete, and loadNextPage) using unsynchronized read-modify-write patterns (`_accumulatedBookmarks.value = _accumulatedBookmarks.value.map/filter {...}`). While MutableStateFlow is thread-safe for atomic reads and writes, the read-then-write pattern is not atomic and can lose updates when two coroutines race.

**Primary recommendation:** Introduce a sealed class state machine for MainScreenModel initialization, and protect `_accumulatedBookmarks` mutations with a Mutex to eliminate read-modify-write races.

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
None -- all decisions are at Claude's discretion for this infrastructure phase.

### Claude's Discretion
All implementation choices are at Claude's discretion -- pure infrastructure phase. Key decisions include:
- State machine representation (sealed class, enum, or equivalent construct)
- Whether mutex is needed for tag cache race condition (based on audit findings)
- Verification approach for no-duplicate-loads invariant

### Deferred Ideas (OUT OF SCOPE)
None
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|-----------------|
| CONC-01 | Refactor MainScreenModel initialization sequence to explicit state machine | Startup sequence analyzed (lines 237-290): two concurrent coroutines (A: server sync, B: sequential startup) with implicit ordering via `first {}`. State machine sealed class replaces implicit coroutine sequencing. |
| CONC-02 | Document and verify read/unread tag cache race condition, add mutex if needed | Tag cache comment at BookmarkActionsRepository line 57 is vestigial -- no cache exists. markAsRead/markAsUnread are direct DB operations. However, `_accumulatedBookmarks` read-modify-write races are the real concern (30+ mutation sites from 5+ coroutines). |
</phase_requirements>

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| kotlinx-coroutines-core | (project version) | Mutex, StateFlow, structured concurrency | Already in project dependencies |
| kotlinx-coroutines-test | (project version) | Test dispatchers, runTest, advanceUntilIdle | Already in commonTest dependencies |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| MockK | (project version) | Mocking in unit tests | Already used in BookmarkActionsRepositoryUnitTest |
| kotlin-test | (project version) | Test assertions | Already in commonTest |

No new dependencies are required. All concurrency primitives needed (`Mutex`, `withLock`, sealed classes) are part of the existing Kotlin/coroutines standard library already in the project.

## Architecture Patterns

### Recommended: Sealed Class State Machine for Init (CONC-01)

The current init block has two coroutines with implicit ordering:
- **Coroutine A** (line 239): Watches server list, sets `_selectedServer` on first non-empty list, calls `loadLists()`
- **Coroutine B** (line 260): Reads default filter, waits for server via `selectedServer.first { it != null }`, loads first page, then starts filter/server observers and auto-sync

The implicit contract is that Coroutine A must set a server before Coroutine B can proceed. This works today because `first {}` suspends, but the ordering is not documented or enforced.

**Pattern: InitState sealed class**
```kotlin
private sealed class InitState {
    data object Idle : InitState()
    data object ResolvingFilter : InitState()
    data class WaitingForServer(val filter: FilterConfig) : InitState()
    data class LoadingInitialPage(val server: Server, val filter: FilterConfig) : InitState()
    data class ObservingChanges(val server: Server, val filter: FilterConfig) : InitState()
    data object Ready : InitState()
}
```

This makes the startup steps explicit and auditable. Each transition can be logged, and the "no duplicate loads" invariant becomes verifiable: `resetPaginationAndLoad` is only called during the `LoadingInitialPage` transition.

### Recommended: Mutex for _accumulatedBookmarks Mutations (CONC-02)

Current problem -- 30+ sites do read-modify-write on `_accumulatedBookmarks`:
```kotlin
// UNSAFE: Another coroutine can modify .value between read and write
_accumulatedBookmarks.value = _accumulatedBookmarks.value.map { ... }
```

**Pattern: Centralized mutation with Mutex**
```kotlin
private val bookmarksMutex = Mutex()

private suspend fun updateAccumulatedBookmarks(
    transform: (List<BookmarkEntity>) -> List<BookmarkEntity>
) {
    bookmarksMutex.withLock {
        _accumulatedBookmarks.value = transform(_accumulatedBookmarks.value)
    }
}
```

All 30+ mutation sites call `updateAccumulatedBookmarks { list -> list.map/filter {...} }` instead of directly writing `.value`. This is the same pattern already used in BookmarkActionsRepository for `processPendingActions` (line 728-735).

### Recommended Project Structure (no new files)

The changes are in-place refactors within existing files:
```
composeApp/src/commonMain/kotlin/com/karakept/app/ui/screens/
├── MainScreenModel.kt      # Add InitState sealed class, bookmarksMutex,
│                            # updateAccumulatedBookmarks helper
composeApp/src/commonMain/kotlin/com/karakept/app/data/repository/
├── BookmarkActionsRepository.kt  # Document that tag cache is vestigial,
│                                  # clean up dead comment
composeApp/src/commonTest/kotlin/com/karakept/app/data/repository/
├── BaseRepositoryTest.kt              # Existing test base
├── BookmarkActionsRepositoryUnitTest.kt  # Existing tests (no changes expected)
```

### Anti-Patterns to Avoid
- **Replacing MutableStateFlow with Channel:** StateFlow semantics (conflated, always has value) are correct for UI state. Channels would change the semantics.
- **Using AtomicReference instead of Mutex:** Kotlin/Native has different atomicity rules than JVM. Mutex is the portable KMP solution.
- **Making the state machine a separate class:** The init sequence is tightly coupled to MainScreenModel's private state. Extracting it would require exposing too many internals. Keep the sealed class private inside MainScreenModel.
- **Over-engineering with actors:** A simple Mutex is sufficient for the read-modify-write pattern. Actors (Channel-based) add complexity without benefit here since all mutations are synchronous transformations of the list.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Coroutine-safe read-modify-write | Custom CAS loop or AtomicRef | `kotlinx.coroutines.sync.Mutex` + `withLock` | Portable across KMP targets, already imported in project |
| State machine transitions | Boolean flags (`isInitialized`, `hasLoadedFirstPage`) | Sealed class with exhaustive `when` | Compiler-enforced completeness, self-documenting states |
| Test coroutine control | Manual Thread.sleep or delays | `runTest` + `StandardTestDispatcher` + `advanceUntilIdle` | Already established pattern in BaseRepositoryTest |

**Key insight:** The project already uses Mutex in BookmarkActionsRepository (line 728). The pattern is established -- just extend it to MainScreenModel for `_accumulatedBookmarks`.

## Common Pitfalls

### Pitfall 1: Mutex Deadlock from Nested Locks
**What goes wrong:** Calling `updateAccumulatedBookmarks` from within another `bookmarksMutex.withLock` block causes deadlock (Kotlin Mutex is not reentrant).
**Why it happens:** A helper function acquires the mutex, then calls another function that also acquires it.
**How to avoid:** Keep the Mutex scope narrow -- only the `updateAccumulatedBookmarks` helper acquires it. No other code path should hold the mutex and call into this function.
**Warning signs:** Tests hang indefinitely instead of failing.

### Pitfall 2: Breaking collectLatest Semantics
**What goes wrong:** The filter observer uses `collectLatest` (line 272), which cancels the previous collection when a new value arrives. If the state machine transition logic interferes with this cancellation, filter changes may be lost.
**Why it happens:** State machine transitions that suspend (e.g., waiting for mutex) extend the window where `collectLatest` could cancel.
**How to avoid:** Keep `collectLatest` for filter/server observers. The state machine governs only the init sequence, not the ongoing observation phase.
**Warning signs:** Rapid filter changes (e.g., quick drawer taps) occasionally show stale data.

### Pitfall 3: Init State Machine vs. Hot Start
**What goes wrong:** On configuration change, Voyager may re-create the ScreenModel (depending on retention settings). The state machine must handle this gracefully.
**Why it happens:** `screenModelScope` is tied to the ScreenModel lifecycle. If the model is recreated, init runs again.
**How to avoid:** The state machine starts from `Idle` every time -- this is correct behavior. The key invariant is that `resetPaginationAndLoad` is called exactly once during init (not zero, not twice).
**Warning signs:** Duplicate bookmark loads visible in logs.

### Pitfall 4: _accumulatedBookmarks Race During Sync Reload
**What goes wrong:** `syncBookmarks()` calls `resetPaginationAndLoad()` which sets `_accumulatedBookmarks.value = newItems`. Simultaneously, a `bookmarkChangedEvents` collector modifies the same list. The sync reload overwrites the event-driven update.
**Why it happens:** These are independent coroutines writing to the same MutableStateFlow.
**How to avoid:** Both paths must go through `updateAccumulatedBookmarks` with mutex. For `resetPaginationAndLoad`, the transform is a full replacement (ignoring previous value), but it still needs the lock to prevent interleaving.

### Pitfall 5: Vestigial Tag Cache Comment Misleading Future Developers
**What goes wrong:** Line 57 of BookmarkActionsRepository says "Cache for tag IDs to handle read/unread toggling race conditions" but no cache exists. Future developers may try to "fix" a non-existent cache.
**Why it happens:** Comment was left behind when the cache was removed or never implemented.
**How to avoid:** Remove the vestigial comment. Document in the code that markAsRead/markAsUnread use direct DB operations and that the race condition concern has been audited and found non-applicable.

## Code Examples

### State Machine Sealed Class (CONC-01)
```kotlin
// Inside MainScreenModel
private sealed class InitState {
    data object Idle : InitState()
    data object ResolvingFilter : InitState()
    data class WaitingForServer(val filter: FilterConfig) : InitState()
    data class LoadingInitialPage(val server: Server, val filter: FilterConfig) : InitState()
    data object Ready : InitState()
}

private val _initState = MutableStateFlow<InitState>(InitState.Idle)

init {
    // Coroutine A unchanged -- keeps _selectedServer in sync
    screenModelScope.launch {
        servers.collect { serverList ->
            if (_selectedServer.value == null && serverList.isNotEmpty()) {
                _selectedServer.value = serverList.first()
                loadLists()
            } else if (serverList.isEmpty()) {
                _selectedServer.value = null
            }
        }
    }

    // Coroutine B: explicit state machine
    screenModelScope.launch {
        _initState.value = InitState.ResolvingFilter
        val defaultFilter = defaultFilterResolver.resolve()
        _currentFilter.value = defaultFilter
        if (defaultFilter.lists.size == 1) {
            _currentListContext.value = defaultFilter.lists.first()
        }

        _initState.value = InitState.WaitingForServer(defaultFilter)
        val server = selectedServer.first { it != null } ?: return@launch

        _initState.value = InitState.LoadingInitialPage(server, defaultFilter)
        resetPaginationAndLoad(server, defaultFilter)

        _initState.value = InitState.Ready

        // Start observers (same as current code)
        launch {
            _currentFilter.drop(1).collectLatest { filter ->
                val currentServer = _selectedServer.value ?: return@collectLatest
                resetPaginationAndLoad(currentServer, filter)
            }
        }
        launch {
            _selectedServer.drop(1).collectLatest { newServer ->
                if (newServer != null) {
                    resetPaginationAndLoad(newServer, _currentFilter.value)
                }
            }
        }

        val isOffline = settingsRepository.offlineMode.first()
        if (!isOffline && !_isSyncing.value) {
            syncBookmarks()
        }
    }

    // Remaining collectors unchanged...
}
```

### Mutex-Protected Bookmark Mutations (CONC-02)
```kotlin
private val bookmarksMutex = Mutex()

/**
 * Thread-safe mutation of _accumulatedBookmarks.
 * All code that reads-then-writes _accumulatedBookmarks MUST use this.
 */
private suspend fun updateAccumulatedBookmarks(
    transform: (List<BookmarkEntity>) -> List<BookmarkEntity>
) {
    bookmarksMutex.withLock {
        _accumulatedBookmarks.value = transform(_accumulatedBookmarks.value)
    }
}

// Example: replace current unsafe pattern
// BEFORE:
// _accumulatedBookmarks.value = _accumulatedBookmarks.value.filter { it.remoteId != bookmark.remoteId }
// AFTER:
updateAccumulatedBookmarks { it.filter { b -> b.remoteId != bookmark.remoteId } }
```

### Logging Init Transitions for No-Duplicate-Loads Verification
```kotlin
private val _initState = MutableStateFlow<InitState>(InitState.Idle).also { flow ->
    screenModelScope.launch {
        flow.collect { state ->
            AppLogger.d("MainScreenModel", "Init state: ${state::class.simpleName}")
        }
    }
}
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Boolean flags for init state | Sealed class state machines | Kotlin idiom, long-standing | Compiler-enforced exhaustiveness, auditable |
| Synchronized blocks (JVM) | `kotlinx.coroutines.sync.Mutex` | kotlinx.coroutines 1.0+ | Suspending (non-blocking), KMP-compatible |
| `@Volatile` + CAS | `MutableStateFlow` | kotlinx.coroutines 1.3.6+ | Built-in conflation, observation, thread safety for single writes |

**Note:** `MutableStateFlow.update {}` (atomic read-modify-write) exists in kotlinx.coroutines but uses CAS retry loops -- suitable for simple transforms. For transforms that involve suspension or are called from coroutines that should serialize, Mutex is more appropriate and readable. Given that some mutations in MainScreenModel involve DB lookups between read and write (e.g., bookmarkChangedEvents collector at line 296), Mutex is the better choice.

## Open Questions

1. **Should `_accumulatedBookmarks` mutations that are full replacements (not read-modify-write) also use the mutex?**
   - What we know: `resetPaginationAndLoad` does `_accumulatedBookmarks.value = newItems` (line 440), which is a pure write, not read-modify-write. Technically safe without mutex.
   - What's unclear: Whether a concurrent read-modify-write from another coroutine could interleave between the pagination reset and the write, losing the reset.
   - Recommendation: Use mutex for ALL mutations for consistency and to prevent subtle interleaving bugs. The overhead is negligible.

2. **Should `_initState` be exposed for testing?**
   - What we know: The state machine is internal to init. Exposing it enables unit tests that verify transition order.
   - What's unclear: Whether MainScreenModel is testable at all given its many constructor dependencies.
   - Recommendation: Make `_initState` `internal` (visible to tests) and add a log-based verification approach as the primary invariant check. Defer unit testing of MainScreenModel to Phase 4 (TEST requirements).

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | kotlin-test + kotlinx-coroutines-test + MockK |
| Config file | composeApp/build.gradle.kts (commonTest source set, lines 126-132) |
| Quick run command | `./gradlew :composeApp:desktopTest --tests "com.karakept.app.*" -x jvmTest` |
| Full suite command | `./gradlew :composeApp:desktopTest` |

### Phase Requirements to Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| CONC-01 | Init state machine transitions in order, no duplicate loads | manual-only | Log inspection: AppLogger output shows state transitions Idle -> ResolvingFilter -> WaitingForServer -> LoadingInitialPage -> Ready | N/A -- verification via structured logging |
| CONC-02 | _accumulatedBookmarks mutations are race-free | unit (if feasible) | Mutex prevents interleaved writes -- verified by code audit (all mutation sites use updateAccumulatedBookmarks) | Wave 0 gap |
| CONC-02 | Tag cache race condition is documented as non-existent | documentation | Code review: vestigial comment removed, audit documented | N/A |

### Sampling Rate
- **Per task commit:** Code review that all `_accumulatedBookmarks.value =` sites use `updateAccumulatedBookmarks`
- **Per wave merge:** `./gradlew :composeApp:desktopTest` (existing tests must still pass)
- **Phase gate:** Full suite green + manual verification that init log shows correct state transitions

### Wave 0 Gaps
- [ ] No new test files strictly required -- this phase is primarily a refactor verified by code audit and existing test suite passing
- [ ] Optionally: `composeApp/src/commonTest/kotlin/com/karakept/app/ui/screens/MainScreenModelInitTest.kt` -- would require significant mocking infrastructure (7 constructor dependencies); recommend deferring to Phase 4

## Sources

### Primary (HIGH confidence)
- Direct code review of MainScreenModel.kt (1034 lines) -- full analysis of init block, coroutine structure, _accumulatedBookmarks mutation sites
- Direct code review of BookmarkActionsRepository.kt -- Mutex already used at line 728, tag cache comment at line 57 confirmed vestigial
- Direct code review of BookmarkActionsRepositoryUnitTest.kt -- existing test patterns with StandardTestDispatcher

### Secondary (MEDIUM confidence)
- kotlinx.coroutines Mutex documentation (from training data, stable API since 1.0) -- Mutex is non-reentrant, suspending, KMP-compatible
- Kotlin sealed class patterns (stable language feature)

### Tertiary (LOW confidence)
- None -- all findings are based on direct code inspection

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH -- no new dependencies, all primitives already in project
- Architecture: HIGH -- patterns (sealed class state machine, Mutex) are well-established Kotlin idioms; existing Mutex usage in same codebase validates approach
- Pitfalls: HIGH -- identified from direct code analysis of actual race conditions

**Research date:** 2026-03-21
**Valid until:** 2026-04-21 (stable patterns, no external dependency changes)
