# Phase 3: Code Splitting - Research

**Researched:** 2026-03-21
**Domain:** Kotlin/Compose Multiplatform structural refactoring
**Confidence:** HIGH

## Summary

Phase 3 is a pure structural refactoring — decomposing 6 files totaling ~6,400 lines into focused, single-responsibility modules under 500 lines each. No behavioral changes. The project already has established patterns for splitting (existing `main/` and `viewer/` subdirectories with extracted components), so the task is to continue that pattern for the remaining monolithic code.

The key challenge is that MainScreenModel.kt (1084 lines) has tightly coupled concerns sharing private mutable state (`_accumulatedBookmarks`, `bookmarksMutex`). Splitting it requires either Kotlin extension functions on the class or delegate objects that receive shared state. The repository files are more naturally separable by concern (read/write, sync/local, settings category).

**Primary recommendation:** Use Kotlin extension functions and extracted helper classes to split MainScreenModel concerns. For UI files, extract composable functions into the existing `main/` and `viewer/` subdirectory packages. For repositories, create sub-packages or split into separate files within `repository/`.

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
None — all decisions are at Claude's discretion.

### Claude's Discretion
All implementation choices are at Claude's discretion — pure infrastructure phase. Key decisions include:
- How to split each file (which functions/composables go where)
- Naming conventions for new files (follow existing project conventions)
- Whether to use internal visibility or keep public APIs
- Order of splitting (UI files first, then repositories, or vice versa)
- No single file should exceed 500 lines after splitting

### Deferred Ideas (OUT OF SCOPE)
None
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|-----------------|
| SPLIT-01 | Split MainScreen.kt (1321 lines) into focused composable files | Natural split: scroll-action logic (~165 lines), expanded layout (~210 lines), compact layout, dialogs/overlays, helper composables. Existing `main/` package already holds 4 extracted files. |
| SPLIT-02 | Split MainScreenModel.kt (1084 lines) into focused state management classes | Five concern groups identified: pagination/init (~200 lines), filtering/search (~100 lines), bookmark actions (~200 lines), selection/batch (~200 lines), state declarations/flows (~250 lines) |
| SPLIT-03 | Split BookmarkViewerScreen.kt (1045 lines) into viewer sub-components | Screen entry (100 lines) + BookmarkViewerContent (~900 lines). Content has: scroll behavior (~100 lines), highlight handling (~100 lines), scaffold/layout (~300 lines), snackbar logic. Existing `viewer/` package holds 7 extracted files. |
| SPLIT-04 | Split repository files (~1000 lines each) by concern | BookmarkRepository: queries/paging vs sync pipeline. BookmarkActionsRepository: single actions vs batch actions vs pending queue. SettingsRepository: by settings category (theme/display/reader/swipe/sync/app) + layout management + backup. |
</phase_requirements>

## Standard Stack

No new libraries needed. This is pure refactoring using existing Kotlin language features.

### Core Techniques
| Technique | Purpose | Why Standard |
|-----------|---------|--------------|
| Kotlin extension functions | Add methods to MainScreenModel from separate files | Keeps class definition focused while allowing concern separation across files |
| Package-level functions | Extract composable functions to separate files | Standard Compose pattern for breaking up large screens |
| Inner/nested classes | BookmarkSyncPipeline already uses this pattern | Encapsulates complex pipelines while accessing parent state |
| `internal` visibility | Expose split-module internals without public API leakage | Kotlin's module-level visibility prevents accidental external usage |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Extension functions on ScreenModel | Delegate classes with injected state | Delegates are cleaner but require passing many state references; extensions are simpler for this codebase |
| Separate files in same package | Sub-packages per concern | Sub-packages add import complexity; same-package files match existing `main/` and `viewer/` patterns |

## Architecture Patterns

### Recommended Split Structure

```
ui/screens/
├── MainScreen.kt                    # Screen object + Content() (~300 lines)
├── MainScreenModel.kt               # Class declaration, state, init (~350 lines)
├── MainScreenModelActions.kt         # Extension fns: toggle*, delete, update, add/remove tag (~200 lines)
├── MainScreenModelBatch.kt           # Extension fns: batch*, selection, range (~200 lines)
├── MainScreenModelPagination.kt      # Extension fns: loadNextPage, resetPagination, loadBookmarksPage (~150 lines)
├── main/
│   ├── BookmarkListContent.kt        # (existing, 606 lines — may need own split)
│   ├── MainScreenTopBar.kt           # (existing)
│   ├── MainScreenDrawer.kt           # (existing)
│   ├── MainScreenDialogs.kt          # NEW: RenameListDialog, batch dialogs, filter overlays
│   ├── MainScreenScrollAction.kt     # NEW: scroll-action LaunchedEffect logic
│   ├── MainScreenExpandedLayout.kt   # NEW: 3-column expanded layout composable
│   └── HighlightsListContent.kt      # (existing)
├── BookmarkViewerScreen.kt           # Screen data class only (~100 lines — already clean)
├── BookmarkViewerContent.kt          # NEW: BookmarkViewerContent composable (~400 lines)
├── viewer/
│   ├── ViewerScrollBehavior.kt       # (existing)
│   ├── ViewerHighlightHandling.kt    # NEW: highlight selection/scroll logic
│   ├── ViewerTopBar.kt               # (existing)
│   ├── BookmarkFabMenu.kt            # (existing)
│   ├── BookmarkDetailsPanel.kt       # (existing)
│   ├── ViewerDialogs.kt              # (existing)
│   ├── ContentBodySection.kt         # (existing)
│   ├── DescriptionCard.kt            # (existing)
│   └── HeroBannerSection.kt          # (existing)

data/repository/
├── BookmarkRepository.kt             # Class declaration, query methods, public sync API (~300 lines)
├── BookmarkSyncPipeline.kt           # NEW: BookmarkSyncPipeline inner class + executeSyncPipeline (~500 lines)
├── BookmarkActionsRepository.kt      # Single-item actions + queue/sync infrastructure (~400 lines)
├── BookmarkBatchActionsRepository.kt # NEW: batch* methods extracted OR extension functions (~250 lines)
├── SettingsRepository.kt             # Class declaration, keys, legacy, category flows, backup/restore (~400 lines)
├── SettingsRepositoryMutations.kt    # NEW: all set* methods (~300 lines)
├── SettingsRepositoryLayouts.kt      # NEW: layout CRUD (saveLayout, deleteLayout, etc.) (~100 lines)
```

### Pattern 1: Extension Functions for ScreenModel Splitting
**What:** Define `fun MainScreenModel.batchArchive()` etc. in separate files within the same package.
**When to use:** When the extracted functions need access to private/internal members of the class.
**Key insight:** Extension functions CANNOT access private members. So we need to make shared state `internal`:
```kotlin
// MainScreenModel.kt — change private to internal for shared state
internal val _accumulatedBookmarks = MutableStateFlow<List<BookmarkEntity>>(emptyList())
internal val bookmarksMutex = Mutex()
internal suspend fun updateAccumulatedBookmarks(transform: (List<BookmarkEntity>) -> List<BookmarkEntity>) { ... }

// MainScreenModelBatch.kt — extension function
fun MainScreenModel.batchArchive() {
    val bookmarks = getSelectedBookmarks().filter { !it.isArchived }
    ...
}
```

**Alternatively:** Keep MainScreenModel as the single class but extract pure helper functions and delegate the complex logic to plain classes that receive the needed state references.

### Pattern 2: Composable Function Extraction
**What:** Move `@Composable` functions to separate files in the existing `main/` or `viewer/` packages.
**When to use:** For self-contained UI sections (dialogs, layout modes, overlays).
**Example:** The scroll-action `LaunchedEffect` block (lines 255-419 of MainScreen.kt) is a 165-line self-contained block that can become a composable or utility function.

### Pattern 3: Repository Split by File
**What:** Move groups of methods to new files while keeping them in the same class using extension functions, OR extract inner classes to their own files.
**When to use:** When a repository has distinct concern groups (sync vs queries, single vs batch).
**Key insight for BookmarkRepository:** `BookmarkSyncPipeline` is already an inner class. It can be moved to its own file as a top-level class that receives the needed dependencies. The `SyncConfiguration` sealed class hierarchy should move with it.

### Anti-Patterns to Avoid
- **Breaking compilation with visibility changes:** Changing `private` to `internal` on MainScreenModel members changes the API surface. Only do this for members that need cross-file access, and verify no external code depends on them being private.
- **Circular file dependencies:** When extracting, ensure file A doesn't import from file B which imports from file A. Keep the dependency direction clear.
- **Over-splitting:** Don't create files with fewer than 50 lines. A file with one small function adds more cognitive overhead than keeping it inline.
- **Moving code that accesses `this` properties extensively:** If a function reads 10+ properties of the class, it may not benefit from extraction. Focus on groups of functions that share a common subset of state.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Delegation pattern | Custom delegate framework | Kotlin extension functions in same package | Language-native, zero overhead, familiar to Kotlin devs |
| State sharing across split files | Custom state holder objects | `internal` visibility on shared mutable state | Simpler, matches how the codebase already works |

## Common Pitfalls

### Pitfall 1: Breaking Private Member Access in Extension Functions
**What goes wrong:** Extension functions cannot access `private` members of the class they extend. Moving methods to extension functions causes compilation errors.
**Why it happens:** Kotlin extension functions are syntactic sugar for static functions — they don't have access to private internals.
**How to avoid:** Change required members from `private` to `internal`. In this project with a single module, `internal` is effectively the same as `public` within the module but won't leak to external consumers.
**Warning signs:** Compile errors on private member access after extraction.

### Pitfall 2: Koin DI Registration After Class Split
**What goes wrong:** If MainScreenModel's constructor changes (e.g., adding new dependencies for delegate classes), the Koin `single { MainScreenModel(get(), get(), ...) }` call in AppModule.kt will fail at runtime.
**Why it happens:** Koin uses positional parameter injection — adding/removing constructor parameters without updating the module breaks it.
**How to avoid:** Keep the MainScreenModel constructor signature unchanged. Split using extension functions or file-level extraction, not new constructor dependencies. The AppModule.kt `single {}` line should not need to change.
**Warning signs:** Runtime Koin injection failures.

### Pitfall 3: Losing the `screenModelScope` in Extension Functions
**What goes wrong:** Extension functions on MainScreenModel that need `screenModelScope` can't access it if it's inherited from ScreenModel (not a direct property).
**Why it happens:** `screenModelScope` comes from Voyager's ScreenModel interface and is accessible inside the class but not from extension functions directly.
**How to avoid:** Make it accessible: either expose a wrapper property or use `screenModelScope` from the Voyager import directly in the extension function file. Verify by checking Voyager's ScreenModel API — `screenModelScope` is a public extension property on `ScreenModel`, so extension functions on `MainScreenModel` CAN access it.
**Warning signs:** Unresolved reference to `screenModelScope` in new files.

### Pitfall 4: Import Path Confusion After File Moves
**What goes wrong:** After moving composable functions to new files, IDE auto-imports may reference the old location or miss new imports.
**Why it happens:** Functions that were in the same file didn't need imports. Once in separate files, they do.
**How to avoid:** After each split, verify the full import list in both the original and new files. All composable functions in the same package don't need package imports, but functions from other packages do.
**Warning signs:** Unresolved references after splitting.

### Pitfall 5: BookmarkRepository Circular Dependency
**What goes wrong:** BookmarkRepository and BookmarkActionsRepository have a circular dependency resolved via `setBookmarkRepository()` in AppModule.kt. Splitting either repository must preserve this wiring.
**Why it happens:** The circular dependency exists because BookmarkActionsRepository needs to trigger re-sync via BookmarkRepository, and BookmarkRepository delegates to BookmarkActionsRepository for actions.
**How to avoid:** Keep the `setBookmarkRepository()` pattern. If extracting BookmarkSyncPipeline to its own file, pass the BookmarkActionsRepository reference as a constructor parameter, don't create new circular references.

### Pitfall 6: Inner Class Extraction Changes `this` Reference
**What goes wrong:** BookmarkSyncPipeline is an `inner class` of BookmarkRepository, meaning it has implicit access to `this@BookmarkRepository` members. Moving it to a top-level class requires passing all dependencies explicitly.
**Why it happens:** Kotlin `inner` classes capture an implicit reference to the outer class instance.
**How to avoid:** When extracting, explicitly pass all needed outer-class references (bookmarkDao, remoteDataSource, settingsRepository, etc.) as constructor parameters. Count the actual accesses before extracting.

## Code Examples

### Example: Extension Function Split for ScreenModel

```kotlin
// File: MainScreenModelActions.kt
package com.karakept.app.ui.screens

import cafe.adriel.voyager.core.model.screenModelScope
import com.karakept.app.domain.action.BookmarkActionEvent
import kotlinx.coroutines.launch

/**
 * Bookmark action methods for MainScreenModel.
 * Handles individual bookmark mutations (archive, favorite, read, delete, tags, lists).
 */

fun MainScreenModel.toggleBookmarkArchive(bookmark: BookmarkEntity) {
    screenModelScope.launch {
        val position = _accumulatedBookmarks.value.indexOfFirst { it.remoteId == bookmark.remoteId }
        val event = if (bookmark.isArchived) {
            BookmarkActionEvent.Unarchive(bookmark)
        } else {
            BookmarkActionEvent.Archive(bookmark)
        }
        bookmarkActionController.executeAction(event, originalPosition = position)
        updateAccumulatedBookmarks { it.filter { b -> b.remoteId != bookmark.remoteId } }
    }
}
// ... other action methods
```

### Example: Composable Extraction for MainScreen Dialogs

```kotlin
// File: main/MainScreenDialogs.kt
package com.karakept.app.ui.screens.main

@Composable
fun RenameListDialog(
    initialName: String,
    initialIcon: String,
    onDismiss: () -> Unit,
    onConfirm: (name: String, icon: String) -> Unit
) {
    // ... existing implementation moved here
}

@Composable
fun BatchDeleteConfirmDialog(
    count: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    // ... extracted from MainScreen.Content()
}
```

### Example: Repository Sync Pipeline Extraction

```kotlin
// File: BookmarkSyncPipeline.kt
package com.karakept.app.data.repository

/**
 * Sync pipeline extracted from BookmarkRepository.
 * Handles all sync modes (Full, Filtered, ForList).
 */
internal class BookmarkSyncPipeline(
    private val config: SyncConfiguration,
    private val bookmarkDao: BookmarkDao,
    private val remoteDataSource: RemoteDataSource,
    private val bookmarkActionsRepository: BookmarkActionsRepository,
    private val settingsRepository: SettingsRepository,
    private val highlightRepository: HighlightRepository,
    private val imageCacheManager: ImageCacheManager,
    private val syncProgress: MutableStateFlow<SyncProgress>
) {
    suspend fun execute() { /* ... */ }
    // Phase methods stay together
}
```

## State of the Art

This phase uses established Kotlin patterns — no library version changes needed.

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| God-object ScreenModels | Concern-separated extension functions | Kotlin 1.0+ (always available) | Better maintainability, testability |
| Monolithic composables | Extracted composable functions in sub-packages | Compose best practice | Faster recomposition, better preview support |

## Splitting Strategy — Detailed Analysis

### MainScreen.kt (1321 lines) — Target: ~300 lines

**Already extracted (4 files, 1405 lines):** BookmarkListContent, MainScreenTopBar, MainScreenDrawer, HighlightsListContent

**Remaining in MainScreen.kt — extraction plan:**

| Section | Lines | Extract To | New File Lines |
|---------|-------|-----------|---------------|
| Scroll-action LaunchedEffect | 255-419 (~165) | `main/MainScreenScrollAction.kt` | ~180 |
| Expanded 3-column layout | 768-1019 (~250) | `main/MainScreenExpandedLayout.kt` | ~280 |
| Dialogs & overlays (filter, actions, batch confirm, add bookmark) | 1077-1227 (~150) | `main/MainScreenDialogs.kt` | ~200 |
| RenameListDialog + rememberSnackbarHostState | 1231-1321 (~90) | `main/MainScreenDialogs.kt` (merge) | — |
| Callback lambdas (drawer*, handleSwipeAction) | 458-547 (~90) | Stay in MainScreen.kt or extract to helper | — |
| **Remaining MainScreen.kt** | | | **~300 lines** |

### MainScreenModel.kt (1084 lines) — Target: ~350 lines

| Concern Group | Lines | Extract To | Strategy |
|---------------|-------|-----------|----------|
| State declarations + derived flows | 50-260 (~210) | Keep in MainScreenModel.kt | Core class identity |
| Init block + state machine | 263-367 (~105) | Keep in MainScreenModel.kt | Initialization logic |
| Pagination (loadBookmarksPage, resetPagination, loadNextPage, findPageWithItems) | 369-484 (~115) | `MainScreenModelPagination.kt` | Extension functions |
| Sync + filter + search + list methods | 485-614 (~130) | Keep in MainScreenModel.kt | Small, core methods |
| Bookmark actions (toggle*, delete, update, move, add/remove tag) | 619-799 (~180) | `MainScreenModelActions.kt` | Extension functions |
| Selection + batch operations | 802-1003 (~200) | `MainScreenModelBatch.kt` | Extension functions |
| Scroll action + create bookmark | 1005-1084 (~80) | `MainScreenModelActions.kt` (merge) | Extension functions |
| **Remaining MainScreenModel.kt** | | | **~350 lines** |

**Visibility changes needed:** `_accumulatedBookmarks`, `bookmarksMutex`, `updateAccumulatedBookmarks`, `_selectedBookmarkIds`, `_currentFilter`, `_isSyncing`, `_scrollToTopTrigger`, `_pendingBookmarks`, `_createBookmarkResult`, `_selectedServer`, `_bookmarkListVersion`, `_currentListContext`, `_hasMoreItems`, `_isLoadingMore`, `_currentPage`, `bookmarkActionsRepository`, `bookmarkRepository`, `settingsRepository`, `listRepository`, `bookmarkActionController` — change from `private` to `internal`.

### BookmarkViewerScreen.kt (1045 lines) — Target: ~100 lines (Screen class only)

**Already extracted (7 files, ~1531 lines):** BookmarkDetailsPanel, BookmarkFabMenu, ContentBodySection, DescriptionCard, HeroBannerSection, ViewerDialogs, ViewerTopBar, ViewerScrollBehavior

**BookmarkViewerScreen.kt contains:**
- Screen data class (lines 1-100): Already clean, stays
- `BookmarkViewerContent` composable (lines 102-967): ~865 lines — needs splitting
- `rememberSnackbarHostStateWithDelay` (lines 968-1009): Utility composable
- `showSnackbarEvent` helper (lines 1010-1045): Private helper

**Extraction plan for BookmarkViewerContent:**

| Section | Approx Lines | Extract To |
|---------|-------------|-----------|
| State declarations + LaunchedEffects | ~200 | Stay in BookmarkViewerContent.kt |
| Scroll guard + highlight scroll | ~100 | `viewer/ViewerScrollBehavior.kt` (extend existing) |
| Reading progress restoration | ~80 | `viewer/ViewerScrollBehavior.kt` (extend) |
| Scaffold layout body (LazyColumn content) | ~250 | Stay (calls extracted composables) |
| Snackbar utilities | ~45 | `viewer/ViewerSnackbar.kt` |
| **BookmarkViewerContent.kt** | | **~400 lines** |

### BookmarkRepository.kt (963 lines) — Target: ~350 lines

| Concern | Lines | Extract To |
|---------|-------|-----------|
| SyncConfiguration sealed class + ApiFilters | 32-69 (~38) | `BookmarkSyncPipeline.kt` |
| Class declaration, queries, public sync API | 71-200 (~130) | Keep in BookmarkRepository.kt |
| fetchBookmarkContent + syncSingleBookmark | 193-295 (~103) | Keep in BookmarkRepository.kt |
| Pagination queries (getBookmarksPaged, getBookmarkCount) | 297-351 (~55) | Keep in BookmarkRepository.kt |
| BookmarkSyncPipeline inner class | 353-830 (~477) | `BookmarkSyncPipeline.kt` |
| executeSyncPipeline + cacheHeroAssetsForBookmark | 873-963 (~90) | `BookmarkSyncPipeline.kt` |
| **Remaining BookmarkRepository.kt** | | **~300 lines** |

### BookmarkActionsRepository.kt (1000 lines) — Target: ~450 lines

| Concern | Lines | Extract To |
|---------|-------|-----------|
| Class declaration, setup, single actions | 31-410 (~380) | Keep in BookmarkActionsRepository.kt |
| Batch operations (batch*) | 411-575 (~165) | Extension functions in `BookmarkActionsRepositoryBatch.kt` |
| Infrastructure (performAction, queueAction, triggerAutoSync, processPendingActions, executeAction) | 576-1000 (~425) | Keep in BookmarkActionsRepository.kt |
| **Remaining BookmarkActionsRepository.kt** | | **~450 lines** |

Note: BookmarkActionsRepository at 450 lines is under 500, which meets the requirement. The batch methods are the cleanest extraction point.

### SettingsRepository.kt (988 lines) — Target: ~400 lines

| Concern | Lines | Extract To |
|---------|-------|-----------|
| Keys + legacy keys + read helpers | 41-270 (~230) | Keep in SettingsRepository.kt |
| Category flows | 270-300 (~30) | Keep |
| Backup API (currentSettings, restoreSettings) | 300-430 (~130) | Keep |
| Public flows (theme, display, reader, etc.) | 430-590 (~160) | Keep |
| Mutation methods (set*) | 592-850 (~258) | `SettingsRepositoryMutations.kt` |
| Update helpers (updateThemeSettings, etc.) | 881-920 (~40) | Move with mutations |
| Layout CRUD (saveLayout, deleteLayout, setDefault, setListLayout) | 932-988 (~56) | `SettingsRepositoryMutations.kt` (or separate layouts file) |
| **Remaining SettingsRepository.kt** | | **~400 lines** |

## Splitting Order

**Recommended execution order:**

1. **Repositories first** (lower coupling, fewer consumers) — BookmarkSyncPipeline extraction, then SettingsRepository mutations, then BookmarkActionsRepository batch
2. **MainScreenModel next** (depends on repositories being stable) — extension functions for actions, batch, pagination
3. **UI files last** (highest coupling, most visual testing needed) — MainScreen dialogs/layouts, BookmarkViewerContent extraction

This order minimizes merge conflicts: repository splits don't affect UI code, and MainScreenModel splits don't affect MainScreen's composable structure.

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | Kotlin Test (commonTest) + JUnit (desktopTest) |
| Config file | `composeApp/build.gradle.kts` |
| Quick run command | User runs externally (no in-session build env) |
| Full suite command | User runs externally |

### Phase Requirements -> Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| SPLIT-01 | MainScreen.kt under 500 lines, compiles | manual-only | Build + line count check | N/A |
| SPLIT-02 | MainScreenModel.kt under 500 lines, compiles | manual-only | Build + line count check | N/A |
| SPLIT-03 | BookmarkViewerScreen.kt under 500 lines, compiles | manual-only | Build + line count check | N/A |
| SPLIT-04 | Repository files under 500 lines each, compile | manual-only | Build + line count check | N/A |

**Manual-only justification:** This is a structural refactoring with no behavioral changes. The verification is: (1) all files under 500 lines, (2) project compiles, (3) existing tests still pass. No new tests are needed for this phase — tests are Phase 4's concern.

### Sampling Rate
- **Per task commit:** Verify line counts + compilation (user runs build externally)
- **Per wave merge:** Full existing test suite
- **Phase gate:** All target files under 500 lines, clean build, existing tests green

### Wave 0 Gaps
None — no new test infrastructure is needed for a structural refactoring phase.

## Open Questions

1. **BookmarkListContent.kt is already 606 lines (over 500)**
   - What we know: It was previously extracted from MainScreen.kt and is already in the `main/` package
   - What's unclear: Whether SPLIT-01 scope includes splitting this file too, or just MainScreen.kt itself
   - Recommendation: Include it in scope if time permits, but prioritize the 6 explicitly named files first

2. **Extension function access to `screenModelScope`**
   - What we know: Voyager's `screenModelScope` is a public extension property on ScreenModel
   - What's unclear: Whether it's accessible from extension functions on MainScreenModel defined in other files
   - Recommendation: Verify during implementation; if not accessible, wrap it as an internal property. HIGH confidence it works based on Kotlin extension property semantics.

## Sources

### Primary (HIGH confidence)
- Direct codebase analysis of all 6 target files (line counts, function signatures, concern groups)
- Existing split patterns in `ui/screens/main/` (4 files) and `ui/screens/viewer/` (7 files)
- AppModule.kt DI wiring (critical for understanding constructor dependencies)
- CLAUDE.md project conventions (component placement, naming)

### Secondary (MEDIUM confidence)
- Kotlin language specification for extension function visibility rules
- Compose best practices for composable function extraction

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH - pure Kotlin refactoring, no new dependencies
- Architecture: HIGH - follows existing patterns already established in the codebase (main/, viewer/ subdirectories)
- Pitfalls: HIGH - identified from direct analysis of actual code dependencies and visibility constraints

**Research date:** 2026-03-21
**Valid until:** 2026-04-21 (stable — structural refactoring patterns don't change)
