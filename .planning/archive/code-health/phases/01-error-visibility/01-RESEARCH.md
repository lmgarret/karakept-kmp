# Phase 1: Error Visibility - Research

**Researched:** 2026-03-21
**Domain:** Error handling, logging, null safety in Kotlin Multiplatform / Compose Multiplatform
**Confidence:** HIGH

## Summary

Phase 1 addresses silent failure patterns across the Karakept KMP codebase: 18+ `printStackTrace()` calls that swallow exceptions, 20+ `!!` operators that risk null crashes, debug `println` statements used instead of structured logging, and no error propagation to users. The codebase already has the building blocks needed -- `Result<T>` return types, sealed state classes (`BackupState`, `BookmarkLoadingState`), and `ActionSnackbarManager` with `SnackbarEvent` -- but these patterns are not applied consistently.

The core work is mechanical: replace each `printStackTrace()` with proper logging and error propagation, replace each `!!` with safe null handling, extend `ActionSnackbarManager` to support retry actions, and remove/replace noisy `ReadProgressSync` println statements. No new architecture is needed -- just consistent application of existing patterns.

**Primary recommendation:** Keep the existing `println`-based logging but wrap it in a thin `AppLogger` object with severity levels. Do NOT add Kermit or another framework -- the project is small enough that a 20-line wrapper provides all the structure needed without a new dependency. Focus effort on error propagation (making failures visible) rather than logging infrastructure.

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- All errors that affect the user's experience should be surfaced -- sync, load, render, etc. Not just sync failures.
- Use snackbar for error display -- matches existing undo pattern via `ActionSnackbarManager`
- Error messages should be user-friendly plain language: "Couldn't sync bookmark" -- no technical details in the message itself
- Offer a "Retry" action button on snackbars for recoverable errors (network failures, sync errors)
- Non-recoverable errors show snackbar without retry

### Claude's Discretion
- Logging framework choice -- whether to add Kermit/Timber or keep structured println. Pick what's simplest for a KMP project.
- Null safety approach per-instance -- replace all 20 `!!` operators, but use judgment on the replacement pattern (`.let`, guard, sealed state) based on context
- Which reader progress logs count as "noisy" -- remove the frequent polling/progress logs, keep meaningful state change logs
- Error message wording -- keep it short and friendly, Claude writes the copy

### Deferred Ideas (OUT OF SCOPE)
None -- discussion stayed within phase scope
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|-----------------|
| ERR-01 | Replace all `printStackTrace()` calls with structured logging across repository and UI files | AppLogger wrapper pattern; 18 occurrences identified across 8 files with exact line numbers |
| ERR-02 | Propagate errors to UI layer via error flows so users see failures | Extend ActionSnackbarManager with `MessageWithAction` variant for retry; repositories already return `Result<T>` |
| ERR-03 | Fix debug println in RemoteDataSource with proper error handling | 7 println statements at lines 406-469 in RemoteDataSource.kt; replace with AppLogger, keep error-level for failures |
| ERR-04 | Remove noisy reading progress logs in the reader | ReadProgressSync println at lines 406, 414, 443, 451, 456, 466 are noisy polling logs; line 469 is a genuine error |
| NULL-01 | Replace all 20 `!!` operators with safe null handling | 25+ occurrences across 12 files; each needs context-specific replacement (guard clause, `?.let`, early return) |
</phase_requirements>

## Standard Stack

### Core (already in project -- no new dependencies)

| Library | Purpose | Why Standard |
|---------|---------|--------------|
| `kotlin.Result<T>` | Fallible operation return type | Already used in repositories; standard Kotlin pattern |
| `ActionSnackbarManager` | Snackbar event dispatch | Already exists in domain layer; extend with retry support |
| `SnackbarEvent` (sealed class) | Type-safe snackbar types | Already exists; add `MessageWithAction` variant |
| `StateFlow<T>` / `SharedFlow<T>` | Error state propagation | Already used throughout for state management |

### New (project-internal, no external deps)

| Component | Purpose | When to Use |
|-----------|---------|-------------|
| `AppLogger` object | Structured logging wrapper | Everywhere `println` or `printStackTrace` is used for logging |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| AppLogger (println wrapper) | Kermit 2.0.4 | Kermit adds platform-specific writers (Logcat, OSLog) but is a new dependency for a small project. Project already uses println everywhere. Not worth the migration cost for this phase. |
| AppLogger (println wrapper) | co.touchlab:kermit | Same as above. Consider in future if the app grows or needs crash reporting integration. |

### Recommendation: No new dependencies

This phase requires zero new library dependencies. All work uses existing Kotlin stdlib (`Result`, `?.`, `let`), existing project infrastructure (`ActionSnackbarManager`, `SnackbarEvent`, `StateFlow`), and a new project-internal `AppLogger` object.

## Architecture Patterns

### Recommended AppLogger Structure

```
composeApp/src/commonMain/kotlin/com/karakept/app/utils/
    AppLogger.kt          # New: thin logging wrapper
```

### Pattern 1: AppLogger Object

**What:** A minimal singleton that wraps `println` with severity levels and a tag system.
**When to use:** Everywhere the codebase currently uses `println` or `printStackTrace()` for logging.

```kotlin
// composeApp/src/commonMain/kotlin/com/karakept/app/utils/AppLogger.kt
object AppLogger {
    enum class Level { DEBUG, INFO, WARN, ERROR }

    var minLevel: Level = Level.DEBUG // Set to WARN in production if needed

    fun d(tag: String, message: String) = log(Level.DEBUG, tag, message)
    fun i(tag: String, message: String) = log(Level.INFO, tag, message)
    fun w(tag: String, message: String, throwable: Throwable? = null) =
        log(Level.WARN, tag, message, throwable)
    fun e(tag: String, message: String, throwable: Throwable? = null) =
        log(Level.ERROR, tag, message, throwable)

    private fun log(level: Level, tag: String, message: String, throwable: Throwable? = null) {
        if (level < minLevel) return
        println("${level.name[0]}/$tag: $message")
        throwable?.let { println("${level.name[0]}/$tag: ${it.stackTraceToString()}") }
    }
}
```

### Pattern 2: Extend SnackbarEvent for Retry

**What:** Add a `MessageWithAction` variant to `SnackbarEvent` that supports a custom action label (e.g., "Retry") and callback.
**When to use:** When a recoverable error needs to surface to the user with a retry option.

```kotlin
// Extend existing SnackbarEvent sealed class
data class MessageWithAction(
    val text: String,
    val actionLabel: String,
    val onAction: suspend () -> Unit,
    val duration: SnackbarDuration = SnackbarDuration.Short
) : SnackbarEvent()
```

The `rememberSnackbarHostState` composable in MainScreen.kt (line 1231) already handles `SnackbarEvent` collection -- add a `when` branch for `MessageWithAction`.

### Pattern 3: Error Propagation from Repository to UI

**What:** Repository catch blocks log the error AND propagate it (via Result.failure or by calling snackbar manager), instead of swallowing.
**When to use:** Every `catch (e: Exception) { e.printStackTrace() }` block.

```kotlin
// BEFORE (swallowed)
} catch (e: Exception) {
    e.printStackTrace()
}

// AFTER (logged + propagated)
} catch (e: Exception) {
    AppLogger.e(TAG, "Failed to sync bookmarks: ${e.message}", e)
    snackbarManager.showSnackbar("Couldn't sync bookmarks")
    // OR: return Result.failure(e)  -- depending on context
}
```

**Decision tree for each catch block:**
1. Does this operation affect the user? -> Show snackbar
2. Is it recoverable (network, sync)? -> Show snackbar with retry action
3. Is it internal/background? -> Log only, no snackbar
4. Does the caller check Result? -> Return Result.failure(e)

### Pattern 4: Null Safety Replacement Strategies

**What:** Context-specific `!!` replacement based on where it appears.
**When to use:** Each of the 25+ `!!` occurrences.

| Context | Pattern | Example |
|---------|---------|---------|
| Variable checked by `if (x != null)` above | Smart cast (remove `!!`) | `if (x != null) { use(x) }` |
| State variable in composable `when` block | `?: return` guard at top | `val bookmark = selectedBookmark ?: return` |
| Inside `if (showDialog && x != null)` guard | Already safe, remove `!!` | Compose recomposition already guards |
| Map lookup with known key | `?: defaultValue` | `theme.highlightColors["yellow"] ?: Color.Yellow` |
| LaunchedEffect with nullable | `?.let { }` | `pendingEvent?.let { showSnackbar(it) }` |

### Anti-Patterns to Avoid
- **Catch-and-ignore:** `catch (e: Exception) { }` -- always log, always consider propagation
- **Generic error messages everywhere:** "Something went wrong" -- be specific: "Couldn't load bookmark", "Couldn't sync highlights"
- **Retry on non-recoverable errors:** Don't offer retry for parsing failures or null state errors
- **Logging exception message without stacktrace:** `AppLogger.e(TAG, e.message)` -- include the throwable for stack traces in debug

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Snackbar display | Custom toast/dialog system | Existing `ActionSnackbarManager` + `SnackbarHostState` | Already integrated in MainScreen and BookmarkViewerScreen |
| Error state management | New error state classes | Existing sealed class pattern (`BookmarkLoadingState.Error`) | Already established in project |
| Logging framework | Full logging framework with file output, rotation | Simple `AppLogger` object wrapping `println` | Project is small; a 20-line wrapper is sufficient |
| Retry logic | Generic retry wrapper with exponential backoff | Simple lambda passed to `MessageWithAction.onAction` | Each retry is specific to its operation context |

**Key insight:** The project already has 80% of the error handling infrastructure. The gap is not missing tools but inconsistent application of existing patterns.

## Common Pitfalls

### Pitfall 1: Breaking Existing Undo Snackbar Flow
**What goes wrong:** Adding error snackbars that conflict with undo snackbars -- showing them simultaneously or dismissing one when another appears.
**Why it happens:** `SnackbarHostState` only shows one snackbar at a time; a new one dismisses the previous.
**How to avoid:** Error snackbars use `SnackbarDuration.Short` (default) so they don't linger. The existing `extraBufferCapacity = 10` in `ActionSnackbarManager` handles queuing.
**Warning signs:** Undo snackbar disappears immediately after an error occurs.

### Pitfall 2: Coroutine Scope for Retry Callbacks
**What goes wrong:** Retry lambda captures a coroutine scope that has been cancelled (e.g., screen navigated away).
**Why it happens:** `SnackbarEvent.MessageWithAction.onAction` is a suspend lambda; if captured scope is dead, retry silently fails.
**How to avoid:** Execute retry within `screenModelScope` (which is alive as long as the screen exists). The snackbar collector in `rememberSnackbarHostState` already uses `scope.launch` for undo -- follow the same pattern for retry.
**Warning signs:** Retry button does nothing after navigating back to a screen.

### Pitfall 3: Null Safety Changes Breaking Compose Recomposition
**What goes wrong:** Replacing `!!` with `?: return` in a composable causes early return, skipping subsequent composables in the function.
**Why it happens:** In Compose, early returns can skip other UI elements that should still render.
**How to avoid:** In composable functions, prefer wrapping the dependent section in `?.let { }` or moving the null check to an `if` block that only gates the relevant UI section, not the entire composable.
**Warning signs:** Parts of the UI disappear when a nullable value is null.

### Pitfall 4: MainScreen.kt !! Cluster (Lines 1122-1144)
**What goes wrong:** The 10+ `!!` operators on `selectedBookmarkForActions` in MainScreen.kt all reference the same nullable variable inside what appears to be a conditional block.
**Why it happens:** The variable is checked for non-null before the block, but Kotlin smart cast doesn't apply to mutable properties.
**How to avoid:** Capture with a local `val`: `val bookmark = selectedBookmarkForActions ?: return`. Then use `bookmark` (non-null) throughout the block.
**Warning signs:** Crash when user rapidly taps bookmark actions while state changes.

### Pitfall 5: Swapping printStackTrace for Log Without Propagation
**What goes wrong:** Replacing `e.printStackTrace()` with `AppLogger.e(...)` but still swallowing the error -- the log is better, but the user still sees nothing.
**Why it happens:** Mechanical replacement without considering whether the error should surface to the user.
**How to avoid:** For EACH catch block, explicitly decide: (1) log? (2) propagate via Result? (3) show snackbar? Document the decision in a code comment if the answer is "log only".
**Warning signs:** User still sees blank/stale data after a sync failure.

## Code Examples

### Extending ActionSnackbarManager for Retry

```kotlin
// In ActionSnackbarManager.kt - add new method
suspend fun showErrorWithRetry(
    message: String,
    onRetry: suspend () -> Unit,
    duration: SnackbarDuration = SnackbarDuration.Short
) {
    _snackbarEvents.emit(SnackbarEvent.MessageWithAction(message, "Retry", onRetry, duration))
}
```

### Handling MessageWithAction in rememberSnackbarHostState

```kotlin
// In MainScreen.kt rememberSnackbarHostState composable, add branch:
is SnackbarEvent.MessageWithAction -> {
    val result = snackbarHostState.showSnackbar(
        message = event.text,
        actionLabel = event.actionLabel,
        duration = event.duration
    )
    if (result == SnackbarResult.ActionPerformed) {
        scope.launch { event.onAction() }
    }
}
```

### Repository Error Propagation Example

```kotlin
// BookmarkRepository - sync operation (currently swallows errors)
// BEFORE:
} catch (e: Exception) {
    e.printStackTrace()
}

// AFTER:
} catch (e: Exception) {
    AppLogger.e("BookmarkRepo", "Sync pipeline failed: ${e.message}", e)
    snackbarManager.showErrorWithRetry("Couldn't sync bookmarks") {
        // Retry the same operation
        executeSyncPipeline(syncConfiguration)
    }
}
```

### Null Safety - MainScreen selectedBookmarkForActions

```kotlin
// BEFORE (lines 1122-1144):
bookmark = selectedBookmarkForActions!!,
// ... 10 more !! on same variable

// AFTER:
val bookmark = selectedBookmarkForActions ?: return@let
// Use 'bookmark' (non-null) throughout the block
```

### AppLogger Usage

```kotlin
// Replace:
println("📖 CONTENT: ERROR - ${e.message}")
e.printStackTrace()

// With:
AppLogger.e("BookmarkRepo", "Failed to fetch content for bookmark: ${e.message}", e)
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `e.printStackTrace()` | Structured logging with tag + level | Always been best practice | Searchable logs, severity filtering |
| `!!` non-null assertion | Smart casts, `?.let`, sealed states | Kotlin 1.0+ | No more NPE crashes |
| `println` debug logging | Logging framework (Kermit, etc.) | KMP ecosystem ~2022 | Platform-appropriate output |

**For this project:** We are moving from the "old approach" column to structured-but-simple logging. Full framework migration (Kermit) is deferred -- the project can adopt it later when crash reporting or platform-specific log routing becomes necessary.

## Open Questions

1. **ActionSnackbarManager injection into repositories**
   - What we know: `ActionSnackbarManager` is currently in the domain layer, used by `BookmarkActionController`. Repositories don't have direct access to it.
   - What's unclear: Should repositories call snackbar manager directly, or should errors propagate via `Result<T>` to ScreenModels which then call the snackbar manager?
   - Recommendation: Propagate via `Result<T>` to ScreenModels. Repositories should not know about UI concerns. ScreenModels already have access to `ActionSnackbarManager` (via Koin injection on the screens that use it). This keeps the layer boundary clean.

2. **HtmlRenderer.android.kt printStackTrace calls**
   - What we know: 2 occurrences in Android-specific code (noted in CONCERNS.md)
   - What's unclear: Whether these are in scope (they're in `androidMain`, not `commonMain`)
   - Recommendation: Include them -- the phase goal is "no printStackTrace calls remain in the codebase". They follow the same pattern.

3. **How repositories surface errors to ScreenModels**
   - What we know: Some repository methods return `Result<T>`, others return nullable types, others return `Unit` and swallow errors.
   - What's unclear: Standardizing all to `Result<T>` may be a large change touching method signatures.
   - Recommendation: For this phase, focus on the catch blocks that currently swallow errors. Where the method already returns `Result<T>`, use `Result.failure`. Where it returns `Unit` or a nullable, add error logging and use the snackbar manager from the calling ScreenModel. Full `Result<T>` standardization can happen in a later refactoring phase.

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | kotlin-test + MockK |
| Config file | `composeApp/build.gradle.kts` (test dependencies in `commonTest` sourceSet) |
| Quick run command | `./gradlew :composeApp:desktopTest --tests "com.karakept.app.*"` |
| Full suite command | `./gradlew :composeApp:desktopTest` |

### Phase Requirements to Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| ERR-01 | No `printStackTrace()` calls remain | static analysis (grep) | `grep -r "printStackTrace" composeApp/src/ --include="*.kt"` returns 0 results | N/A - grep check |
| ERR-02 | Errors propagate to snackbar manager | unit | `./gradlew :composeApp:desktopTest --tests "*SnackbarManager*" -x` | No - Wave 0 |
| ERR-03 | RemoteDataSource uses AppLogger, not println | static analysis (grep) | `grep -n "println.*ReadProgressSync" composeApp/src/ --include="*.kt"` returns 0 results | N/A - grep check |
| ERR-04 | Noisy progress logs removed | static analysis (grep) | Same as ERR-03 | N/A - grep check |
| NULL-01 | No `!!` operators remain | static analysis (grep) | `grep -rn "!!" composeApp/src/commonMain/ --include="*.kt"` returns 0 relevant results | N/A - grep check |

### Sampling Rate
- **Per task commit:** `grep -r "printStackTrace\|!!" composeApp/src/ --include="*.kt"` to verify count decreases
- **Per wave merge:** `./gradlew :composeApp:desktopTest`
- **Phase gate:** All grep checks return 0, full test suite green

### Wave 0 Gaps
- [ ] `composeApp/src/commonTest/kotlin/com/karakept/app/domain/action/ActionSnackbarManagerTest.kt` -- covers ERR-02 (new MessageWithAction variant works correctly)
- [ ] Verify existing tests still pass after null safety changes -- run full suite

## Sources

### Primary (HIGH confidence)
- Codebase analysis: Direct inspection of all files listed in CONCERNS.md
- `ActionSnackbarManager.kt` -- existing snackbar pattern (42 lines, fully read)
- `BookmarkLoadingState.kt` -- existing sealed error state pattern
- `BackupRestoreScreenModel.kt` -- existing `BackupState` sealed class with Error variant
- `MainScreen.kt` lines 1230-1264 -- existing `rememberSnackbarHostState` composable

### Secondary (MEDIUM confidence)
- [Kermit documentation](https://kermit.touchlab.co/docs/) -- KMP logging library, v2.0.4 current
- [Kermit GitHub](https://github.com/touchlab/Kermit) -- actively maintained, but adds dependency

### Tertiary (LOW confidence)
- None

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH -- all patterns already exist in the codebase, just need consistent application
- Architecture: HIGH -- no new architecture needed, extending existing ActionSnackbarManager
- Pitfalls: HIGH -- identified from direct code inspection of actual catch blocks and !! usage
- Logging recommendation: MEDIUM -- "no new dependency" is a judgment call; Kermit would also work fine

**Research date:** 2026-03-21
**Valid until:** 2026-04-21 (stable domain, no fast-moving dependencies)
