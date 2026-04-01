# Phase 13: Smart List & Saving Follow-ups - Research

**Researched:** 2026-03-26
**Domain:** Voyager ScreenModel lifecycle in secondary Activity, smart list server sync after quick actions
**Confidence:** HIGH

## Summary

This phase fixes two follow-up bugs from v1.8.0. SAVE-02 concerns `MainScreenModel` failing to load data when instantiated inside `BookmarkSavingActivity` (a secondary Android Activity). LIST-02 concerns smart lists not updating after quick actions that change a bookmark's list membership.

Both bugs are well-scoped. SAVE-02 requires tracing the `MainScreenModel` init state machine in the secondary Activity context to find why data loading stalls. LIST-02 requires adding a post-action sync trigger for all smart lists after list-membership quick actions. Both fixes ship with regression tests per NFR-01.

**Primary recommendation:** For SAVE-02, diagnose and fix the init state machine stall in the secondary Activity. For LIST-02, add smart list sync dispatch after `moveBookmarkToList`, `removeBookmarkFromList`, and the `ADD_TO_LIST` scroll action in `MainScreenModelActions.kt`.

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- SAVE-02 root cause is unknown -- researcher must trace the `MainScreenModel` initialization path when `MainScreen` runs inside `BookmarkSavingActivity`
- SAVE-02 key investigation: `ShareBookmarkScreen.navigator.replaceAll([MainScreen, BookmarkViewerScreen])` creates a fresh `MainScreenModel` via Voyager ScreenModel scope. Trace InitState sequence to find where data loading breaks
- SAVE-02 expected UX: MainScreen after pressing Back must be fully functional with populated bookmark list and correct drawer counters
- SAVE-02 test strategy: ViewModel test verifying MainScreenModel bookmark list state is populated after save flow
- LIST-02: Smart lists have server-owned query logic -- local re-query is not valid. After any quick action changing list membership, trigger `syncBookmarksForList` for every list with `type = SMART`
- LIST-02 scope: All smart lists, not just currently visible one. Syncs are background/non-blocking
- LIST-02 test strategy: ViewModel/repository test verifying `syncBookmarksForList` is called for each smart list after a list-membership quick action

### Claude's Discretion
- How to identify quick actions that affect list membership (add/remove/move vs. star/archive)
- Whether smart list syncs are fired in parallel or sequentially
- Exact timing of sync trigger relative to optimistic UI update

### Deferred Ideas (OUT OF SCOPE)
- None
</user_constraints>

<phase_requirements>

## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| SAVE-02 | Navigate back after saving bookmarks must show bookmark list with correct counters | Init state machine analysis, Koin singleton scope analysis, test pattern from QuickFilterCountsTest |
| LIST-02 | Smart list must reflect quick-action changes immediately via server sync | Smart list type detection via `KarakeepList.Type.SMART`, sync dispatch pattern in `syncBookmarks()`, action identification in `MainScreenModelActions.kt` |
| NFR-01 | Regression tests for every fix | Test infrastructure analysis, MockK patterns, Robolectric test setup |
| NFR-02 | No new regressions -- existing ~201 tests must pass | Existing test suite verified; changes are additive |

</phase_requirements>

## Architecture Patterns

### SAVE-02: MainScreenModel Init State Machine

The `MainScreenModel` init block runs a sequential state machine in `Coroutine B`:

```
Idle -> ResolvingFilter -> WaitingForServer(filter) -> LoadingInitialPage(server, filter) -> Ready
```

Key observations from code analysis:

1. **Koin singleton scope:** `MainScreenModel` is declared as `single {}` in `AppModule.kt` (line 107). This means the SAME instance is shared across the entire Koin application scope.

2. **Koin is initialized once per Application:** `KarakeptApp.onCreate()` calls `startKoin {}`. Since `BookmarkSavingActivity` runs in the same process as `MainActivity`, it shares the same Koin container and thus the same `MainScreenModel` singleton.

3. **The init block runs ONCE:** Because `MainScreenModel` is a Koin singleton, its `init {}` block executes only when first created. When `ShareBookmarkScreen` calls `navigator.replaceAll(listOf(MainScreen, BookmarkViewerScreen))`, `MainScreen` uses `koinScreenModel<MainScreenModel>()` which retrieves the existing singleton.

4. **Critical insight:** The singleton `MainScreenModel` was already initialized by the main Activity. When `BookmarkSavingActivity`'s Navigator creates a fresh Voyager screen stack, the `MainScreenModel` singleton is reused with its existing state. However, the `screenModelScope` is tied to Voyager's ScreenModel lifecycle. When a new Voyager Navigator is created in `BookmarkSavingActivity`, the `MainScreenModel`'s `screenModelScope` may already be cancelled from a previous Navigator, OR the Voyager `koinScreenModel` may create a NEW instance scoped to the new Navigator.

5. **Voyager `koinScreenModel` behavior:** Voyager's `koinScreenModel` uses the Screen's key to scope the ScreenModel. If `MainScreen` is an `object` (singleton Screen), the same key is used. But the ScreenModel's lifecycle is tied to the Navigator -- when the previous Navigator was destroyed, the ScreenModel's `onDispose()` was called. Voyager calls `ScreenModelStore.remove()` for disposed screens, which clears the cached instance. A new Navigator will create a fresh `MainScreenModel`.

6. **Root cause hypothesis (HIGH confidence):** When a NEW `MainScreenModel` is created in `BookmarkSavingActivity`'s Navigator:
   - `Coroutine A` collects `servers` flow (Room-backed, via `ServerRepository.servers`)
   - `Coroutine B` enters `WaitingForServer` and calls `selectedServer.first { it != null }`
   - `Coroutine A` depends on Room emitting the server list
   - In the secondary Activity context, the Room database is shared (same Koin singleton `AppDatabase`), so `servers` SHOULD emit
   - BUT: `DefaultFilterResolver.resolve()` reads from DataStore. DataStore initialization in `BookmarkSavingActivity` may not have completed, OR the DataStore singleton may work fine since it was already initialized by `KarakeptApp`

7. **Alternative hypothesis:** The `bookmarkRepository.getBookmarksPaged()` call in `resetPaginationAndLoad` returns empty results because the DB query requires a server context and the flow timing is wrong.

### SAVE-02: Investigation Strategy

The implementer should:
1. Add logging to the init state machine to identify which state it stalls in
2. Verify that `servers` flow emits in the secondary Activity context
3. Verify that `DefaultFilterResolver.resolve()` completes
4. Check if `resetPaginationAndLoad` is called and what it returns

### LIST-02: Quick Action to Smart List Sync

**Actions that affect list membership** (from `MainScreenModelActions.kt`):
- `moveBookmarkToList(bookmark, listId)` -- adds bookmark to a list
- `removeBookmarkFromList(bookmark, listId)` -- removes bookmark from a list
- `executeScrollAction` with `SwipeAction.ADD_TO_LIST` -- delegates to `moveBookmarkToList`

**Actions that do NOT affect list membership:**
- `toggleBookmarkArchive` -- archive/unarchive status only
- `toggleBookmarkFavorite` -- star status only
- `toggleBookmarkRead` -- read status only
- `deleteBookmark` -- deletes entirely
- `updateBookmarkTags` -- tags only
- `addBookmarkTag` / `removeBookmarkTag` -- tags only

**Smart list detection:** `KarakeepList.Type.SMART` enum value. Filter `listRepository.lists.value` for entries where `type == KarakeepList.Type.SMART`.

**Sync mechanism:** The existing `syncBookmarks()` method in `MainScreenModel` already calls `bookmarkRepository.syncBookmarksForList(server, listId)` when `capturedListContext != null`. For LIST-02, after a list-membership action completes, we need to:
1. Get all smart lists from `listRepository.lists.value`
2. For each smart list, call `bookmarkRepository.syncBookmarksForList(server, smartListId)`
3. This runs in background, non-blocking to UI

**Recommended implementation location:** Extract a helper function (e.g., `syncSmartLists()`) in `MainScreenModelActions.kt` that:
- Reads `listRepository.lists.value` to find all `SMART` type lists
- Launches parallel coroutines to sync each
- Is called at the end of `moveBookmarkToList` and `removeBookmarkFromList`

### Recommended Project Structure

No new files needed. Changes go in existing files:

```
composeApp/src/commonMain/kotlin/com/karakept/app/ui/screens/
  MainScreenModel.kt          # SAVE-02: fix init stall in secondary Activity
  MainScreenModelActions.kt   # LIST-02: add syncSmartLists() after list actions
composeApp/src/androidUnitTest/kotlin/com/karakept/app/ui/screens/
  MainScreenInitTest.kt       # NEW: SAVE-02 regression test
  SmartListSyncTest.kt        # NEW: LIST-02 regression test
```

### Anti-Patterns to Avoid
- **Local smart list query re-evaluation:** Smart list queries are server-owned. Never try to replicate the query logic locally.
- **Blocking UI on smart list sync:** Smart list syncs must be fire-and-forget background operations.
- **Syncing only the current smart list:** All smart lists must be synced because drawer counters need to reflect changes.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Smart list query evaluation | Custom query parser | Server sync via `syncBookmarksForList` | Query logic is server-owned, undocumented, may change |
| ScreenModel lifecycle management | Custom scope tracking | Voyager's built-in `screenModelScope` + Koin singleton | Framework handles lifecycle correctly |

## Common Pitfalls

### Pitfall 1: Voyager ScreenModel Singleton vs Scoped Lifecycle
**What goes wrong:** Assuming `MainScreenModel` Koin singleton persists across Navigator instances. Voyager disposes ScreenModels when their Navigator is destroyed.
**Why it happens:** Koin `single {}` provides the same instance, but Voyager's `ScreenModelStore` has its own lifecycle. When a Navigator is disposed, its ScreenModels are cleared from the store. A new Navigator will request a new instance from Koin... but Koin returns the same singleton. The `screenModelScope` may be cancelled.
**How to avoid:** Verify whether `screenModelScope` is still active when MainScreenModel is reused. If the scope is cancelled, the init coroutines won't run.
**Warning signs:** Init state machine logs not appearing in secondary Activity context.

### Pitfall 2: DataStore Cold Start in Secondary Activity
**What goes wrong:** `DefaultFilterResolver.resolve()` hangs because DataStore hasn't emitted its first value in the secondary Activity's coroutine context.
**Why it happens:** DataStore uses `SharingStarted.WhileSubscribed` or similar; if no active subscriber existed, the first emission requires reading from disk.
**How to avoid:** DataStore is initialized in `KarakeptApp.onCreate()` and is a Koin singleton. As long as the Application is alive, the DataStore instance is available. But `settingsRepository` flows may need an active subscriber to emit.
**Warning signs:** `_initState` stuck at `ResolvingFilter` or `WaitingForServer`.

### Pitfall 3: Race Between Optimistic UI Update and Smart List Sync
**What goes wrong:** Smart list sync completes and calls `resetPaginationAndLoad`, which overwrites the optimistic bookmark removal from the quick action.
**Why it happens:** The sync reload uses `updateAccumulatedBookmarks { newItems }` which replaces the entire list, undoing any optimistic removals.
**How to avoid:** Smart list syncs for non-current lists only need to update drawer counters (which are derived from `allBookmarks` Room flow). For the current smart list, the sync should trigger `resetPaginationAndLoad` which will naturally exclude the bookmark.
**Warning signs:** Bookmark briefly disappears then reappears, or counter flickers.

### Pitfall 4: MockK SharedFlow Default Returns
**What goes wrong:** Relaxed MockK for `SharedFlow<T>` causes `KotlinNothingValueException` in background coroutines.
**Why it happens:** MockK's relaxed mocks return default values, but `SharedFlow.collect {}` never returns normally.
**How to avoid:** Always stub `bookmarkChangedEvents` and `undoCompletedEvents` with real `MutableSharedFlow()` instances (as done in `QuickFilterCountsTest`).
**Warning signs:** Test crashes with `KotlinNothingValueException`.

## Code Examples

### Pattern: Creating MainScreenModel in androidUnitTest with MockK

Source: `QuickFilterCountsTest.kt` (existing project pattern)

```kotlin
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class ExampleTest {
    private val testDispatcher = StandardTestDispatcher()

    // All dependencies as relaxed mocks
    private val serverRepository = mockk<ServerRepository>(relaxed = true)
    private val bookmarkRepository = mockk<BookmarkRepository>(relaxed = true)
    // ... etc

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        // Mandatory stubs for init block flows
        every { serverRepository.servers } returns flowOf(listOf(fakeServer))
        every { settingsRepository.defaultListType } returns flowOf(DefaultListType.ALL_BOOKMARKS)
        every { settingsRepository.defaultListId } returns flowOf(null)
        every { settingsRepository.offlineMode } returns flowOf(false)
        every { listRepository.lists } returns MutableStateFlow(emptyList())
        every { bookmarkActionsRepository.bookmarkChangedEvents } returns MutableSharedFlow<Long>()
        every { bookmarkActionController.undoCompletedEvents } returns MutableSharedFlow<UndoCompletedEvent>()
        // ... all other settings flows
    }

    @After
    fun tearDown() { Dispatchers.resetMain() }

    private fun createModel() = MainScreenModel(
        serverRepository, bookmarkRepository, bookmarkActionsRepository,
        settingsRepository, listRepository, bookmarkActionController,
        snackbarManager, highlightRepository
    )
}
```

### Pattern: Verifying sync calls with MockK coVerify

```kotlin
@Test
fun `list action triggers smart list sync`() = runTest(testDispatcher) {
    val smartList = KarakeepList(id = "smart-1", name = "Unread", type = KarakeepList.Type.SMART)
    val manualList = KarakeepList(id = "manual-1", name = "Read Later", type = KarakeepList.Type.MANUAL)
    every { listRepository.lists } returns MutableStateFlow(listOf(smartList, manualList))

    val model = createModel()
    advanceUntilIdle()

    model.moveBookmarkToList(someBookmark, "manual-1")
    advanceUntilIdle()

    coVerify { bookmarkRepository.syncBookmarksForList(any(), "smart-1") }
    coVerify(exactly = 0) { bookmarkRepository.syncBookmarksForList(any(), "manual-1") }
}
```

### Pattern: Extractable pure function for testability (project convention)

Source: `applyRemoveBookmarkTransform` in `MainScreenModelActions.kt`

The project convention is to extract complex logic into `internal` top-level functions that can be tested in `commonTest` without ScreenModel dependencies. For LIST-02, the smart list filtering logic is simple (filter by `type == SMART`), so a pure function may not be needed. The test can verify behavior via MockK `coVerify` on repository calls.

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | kotlin-test + MockK + Robolectric (androidUnitTest) / kotlin-test + MockK (commonTest) |
| Config file | `composeApp/build.gradle.kts` (test dependencies in sourceSet blocks) |
| Quick run command | `./gradlew :composeApp:testDebugUnitTest --tests "com.karakept.app.ui.screens.*"` |
| Full suite command | `./gradlew :composeApp:allTests` |

### Phase Requirements -> Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| SAVE-02 | MainScreenModel loads bookmarks when created in secondary Activity context | unit (androidUnitTest) | `./gradlew :composeApp:testDebugUnitTest --tests "com.karakept.app.ui.screens.MainScreenInitTest"` | Wave 0 |
| LIST-02 | Smart list sync triggered after moveBookmarkToList | unit (androidUnitTest) | `./gradlew :composeApp:testDebugUnitTest --tests "com.karakept.app.ui.screens.SmartListSyncTest"` | Wave 0 |
| LIST-02 | Smart list sync triggered after removeBookmarkFromList | unit (androidUnitTest) | same test class | Wave 0 |
| NFR-02 | Existing tests still pass | regression | `./gradlew :composeApp:allTests` | Existing |

### Sampling Rate
- **Per task commit:** `./gradlew :composeApp:testDebugUnitTest --tests "com.karakept.app.ui.screens.*"`
- **Per wave merge:** `./gradlew :composeApp:allTests`
- **Phase gate:** Full suite green before `/gsd:verify-work`

### Wave 0 Gaps
- [ ] `composeApp/src/androidUnitTest/kotlin/com/karakept/app/ui/screens/MainScreenInitTest.kt` -- covers SAVE-02
- [ ] `composeApp/src/androidUnitTest/kotlin/com/karakept/app/ui/screens/SmartListSyncTest.kt` -- covers LIST-02

## Project Constraints (from CLAUDE.md)

- Use Material3 components and `MaterialTheme.*` theming tokens
- `MainScreenModel` is a Koin singleton (line 107 of `AppModule.kt`)
- Use `HorizontalDivider` not deprecated `Divider`
- Tags must use `TagChip` / `BookmarkTagsDisplay` (not relevant to this phase)
- Lists must use `buildListHierarchy` for display (not relevant to this phase)
- MockK relaxed mocks for DAOs and repositories (established pattern from Phases 08-12)
- Pure function extraction for testability (established pattern)
- ScreenModel-centric unit tests that construct the model directly (no DI graph wiring)

## Open Questions

1. **Voyager ScreenModel scope in secondary Activity**
   - What we know: `MainScreenModel` is Koin `single {}`. Voyager's `koinScreenModel` retrieves it.
   - What's unclear: Whether Voyager disposes and recreates the ScreenModel when a new Navigator is created in a different Activity, or reuses the same instance. The `screenModelScope` behavior on reuse is the key unknown.
   - Recommendation: Add debug logging to `MainScreenModel.init {}` and `MainScreenModel.onDispose()` to observe lifecycle in both Activity contexts. This is the critical first investigation step for SAVE-02.

2. **Smart list sync post-action: parallel vs sequential**
   - What we know: User decision says parallel or sequential is Claude's discretion.
   - Recommendation: Fire in parallel using `launch {}` per smart list inside `screenModelScope.launch {}`. This minimizes total latency and the operations are independent.

## Sources

### Primary (HIGH confidence)
- `composeApp/src/commonMain/kotlin/com/karakept/app/ui/screens/MainScreenModel.kt` -- init state machine, sync logic
- `composeApp/src/commonMain/kotlin/com/karakept/app/ui/screens/MainScreenModelActions.kt` -- quick action dispatch, list-membership actions
- `composeApp/src/commonMain/kotlin/com/karakept/app/data/repository/ListRepository.kt` -- list model with SMART type
- `composeApp/src/commonMain/kotlin/com/karakept/app/di/AppModule.kt` -- Koin module, `MainScreenModel` as `single {}`
- `composeApp/src/androidMain/kotlin/com/karakept/app/BookmarkSavingActivity.kt` -- secondary Activity with Voyager Navigator
- `composeApp/src/commonMain/kotlin/com/karakept/app/ui/screens/ShareBookmarkScreen.kt` -- `navigator.replaceAll` call
- `api-client/build/generated/openapi/src/main/kotlin/com/karakept/api/model/KarakeepList.kt` -- `Type.SMART` enum

### Secondary (MEDIUM confidence)
- `composeApp/src/androidUnitTest/kotlin/com/karakept/app/ui/screens/QuickFilterCountsTest.kt` -- established test pattern for MainScreenModel
- `composeApp/src/commonTest/kotlin/com/karakept/app/domain/action/BookmarkActionControllerTest.kt` -- MockK patterns for action testing

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH - all libraries already in use, no new dependencies
- Architecture: HIGH - both fixes operate within existing patterns (init state machine, action extensions)
- Pitfalls: HIGH - identified from direct code analysis of init flow and action dispatch
- SAVE-02 root cause: MEDIUM - hypothesis is well-supported but needs runtime verification

**Research date:** 2026-03-26
**Valid until:** 2026-04-26 (stable codebase, no external dependency changes)
