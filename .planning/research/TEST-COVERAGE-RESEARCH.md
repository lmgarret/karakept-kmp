# Test Coverage Audit & Strategy - Research

**Researched:** 2026-03-24
**Domain:** Kotlin Multiplatform testing (Compose UI, ScreenModel, Repository, Domain)
**Confidence:** HIGH (based on direct code audit of all source sets)

## Summary

Karakept KMP has a solid but narrow test base: **36 test files** across three source sets totaling ~7,250 lines of test code against ~12,000+ lines of production logic. The existing tests follow good patterns (pure function extraction, mockk-based repository tests, Robolectric for Compose UI), but coverage is heavily concentrated on a few areas while large, complex modules remain entirely untested.

The highest-risk gaps are: (1) the **BookmarkSyncPipeline** (538 lines of complex sync orchestration with zero unit tests), (2) **BookmarkActionController** (304 lines of undo/action coordination, untested), (3) the **SettingsRepository** (522 + 481 = 1,003 lines managing all app preferences, untested beyond serialization), and (4) several **ScreenModels** (BookmarkViewerScreenModel at 605 lines, SettingsScreenModel at 571 lines) with no tests.

**Primary recommendation:** Invest in commonTest unit tests for BookmarkSyncPipeline filtering/orchestration logic, BookmarkActionController undo flows, and SettingsRepository read/write correctness. These deliver the highest regression-prevention ROI because they cover complex stateful logic that users interact with constantly.

## Project Constraints (from CLAUDE.md)

- Build/test commands run externally by the user, not in Claude sessions
- JDK 21 required for Gradle (JDK 25 breaks Kotlin DSL): `JAVA_HOME="/opt/homebrew/Cellar/openjdk@21/21.0.10/libexec/openjdk.jdk/Contents/Home"`
- Test command: `./gradlew :composeApp:testDebugUnitTest` (androidUnitTest), `./gradlew :composeApp:desktopTest` (desktopTest)
- Pure function extraction pattern preferred for testability (established in Phase 05)

## Current Test Inventory

### Source Set: `commonTest` (15 files, ~2,807 lines)

| File | Lines | What It Tests | Coverage Quality |
|------|-------|---------------|------------------|
| `BackupRepositoryTest` | 224 | Backup export/import serialization | Good |
| `BaseRepositoryTest` | 21 | Shared test infrastructure (dispatcher, scope) | Infrastructure |
| `BatchOperationsTest` | 331 | Batch bookmark operations (archive, delete, etc.) | Good |
| `BookmarkActionsRepositoryUnitTest` | 251 | moveToList, removeFromList, updateTags (optimistic local + pending action queue) | Good |
| `BookmarkRepositoryUnitTest` | 82 | Basic bookmark repository operations | Thin |
| `ListRepositoryUnitTest` | 100 | List repository operations | Thin |
| `StoredSettingsSerializationTest` | 325 | Settings JSON serialization round-trip | Good |
| `BackupSettingsSerializationTest` | 277 | Backup settings serialization | Good |
| `BookmarkFilterUtilsTest` | 416 | Client-side filter logic (archived, starred, tags, search) | Thorough |
| `DefaultFilterResolverTest` | 128 | Default filter resolution from settings | Good |
| `ListHierarchyUtilsTest` | 168 | List hierarchy building, sorting, filtering | Good |
| `ActionSnackbarManagerTest` | 107 | Snackbar state management | Good |
| `PaginationUtilsTest` | 170 | Pagination offset calculation | Good |
| `HtmlArchiveProcessorTest` | 112 | HTML archive extraction | Good |
| `ParsedDocumentCacheTest` | 95 | Document cache eviction/lookup | Good |

### Source Set: `androidUnitTest` (6 files, ~950 lines)

| File | Lines | What It Tests | Coverage Quality |
|------|-------|---------------|------------------|
| `BookmarkSavingActivityTest` | 99 | Android share target Activity state (reflection-based) | Regression gate for SAVE-01/02 |
| `HighlightsPullToRefreshTest` | 124 | Pull-to-refresh on highlights view via ScreenModel | Regression gate for FILT-03 |
| `MainScreenSelectAllTest` | 218 | selectAll() behavior with pagination | Regression gate for FILT-01 |
| `QuickFilterCountsTest` | 195 | Quick filter drawer count display | Regression gate for FILT-02 |
| `ScrollPositionRegressionTest` | 143 | Scroll position restoration after reader close | Regression gate for READER-01 |
| `ScrollToTopVisibilityTest` | 171 | Scroll-to-top button visibility toggle | Regression gate for READER-03/04 |

### Source Set: `desktopTest` (15 files, ~3,497 lines)

| File | Lines | What It Tests | Coverage Quality |
|------|-------|---------------|------------------|
| `ApiClientIntegrationTest` | 401 | End-to-end API calls against Docker backend | Integration (requires Docker) |
| `BaseDockerIntegrationTest` | 468 | Docker Compose lifecycle management | Infrastructure |
| `BookmarkMutationIntegrationTest` | 249 | Bookmark CRUD against real backend | Integration |
| `BookmarkSyncIntegrationTest` | 320 | Full sync pipeline against real backend | Integration |
| `HighlightRepositoryIntegrationTest` | 264 | Highlight CRUD integration | Integration |
| `ListRepositoryIntegrationTest` | 118 | List operations integration | Integration |
| `ReadingProgressIntegrationTest` | 365 | Reading progress sync integration | Integration |
| `FilterConfigTest` | 201 | FilterConfig model logic | Good unit test |
| `PendingActionQueueTest` | 203 | Pending action ordering, conflict, retry | Good unit test |
| `ServerRepositoryMigrationTest` | 129 | Server migration logic | Good unit test |
| `SyncContentOfflineTest` | 206 | Offline sync filtering logic (pure function) | Good unit test |
| `SecureCredentialStoreTest` | 71 | Credential encryption/decryption | Good unit test |
| `BookmarkViewerProgressTest` | 255 | Viewer progress tracking ScreenModel | Good unit test |
| `RemoveBookmarkFromListTest` | 112 | applyRemoveBookmarkTransform pure function | Good unit test |
| `BackupCryptoTest` | 135 | Backup encryption/decryption | Good unit test |

### Summary Statistics

| Metric | Value |
|--------|-------|
| Total test files | 36 |
| Total test lines | ~7,254 |
| commonTest (shared, fast) | 15 files / 2,807 lines |
| androidUnitTest (Robolectric) | 6 files / 950 lines |
| desktopTest (JVM, some Docker) | 15 files / 3,497 lines |

## Coverage Gaps

Ranked by risk (combination of code complexity, user impact, and regression likelihood).

### CRITICAL: No Tests At All

| Production File | Lines | Risk | Why It Matters |
|-----------------|-------|------|----------------|
| **BookmarkSyncPipeline.kt** | 538 | CRITICAL | Core sync orchestration. Handles Full/Filtered/ForList modes, page fetching, conflict resolution, image caching. Any bug here corrupts user data or silently drops bookmarks. |
| **BookmarkActionController.kt** | 304 | HIGH | Undo support with timed expiry, action execution coordination. Bugs cause lost undo actions or double-execution. |
| **SettingsRepository.kt** | 522 | HIGH | All user preferences read. Bugs cause settings to silently reset or return wrong defaults. Only serialization is tested (StoredSettingsSerializationTest), not read/write flows. |
| **SettingsRepositoryMutations.kt** | 481 | HIGH | All user preference writes. Bugs cause settings to silently fail to persist. |
| **BookmarkViewerScreenModel.kt** | 605 | MEDIUM-HIGH | Complex reader state management (content loading, progress tracking, action handling). Only reading progress is tested (BookmarkViewerProgressTest). |
| **MainScreenModelActions.kt** | 300 | MEDIUM | Bookmark action dispatch from main screen. Tightly coupled with BookmarkActionController. |
| **MainScreenModelBatch.kt** | 241 | MEDIUM | Batch operations from main screen. BatchOperationsTest covers the repository layer, but ScreenModel coordination is untested. |
| **BookmarkActionsRepositorySync.kt** | 327 | MEDIUM | Sync processing for pending actions. PendingActionQueueTest covers ordering/retry, but pullReadingProgressFromServer and other sync methods are untested. |
| **BookmarkActionsRepositoryBatch.kt** | 185 | MEDIUM | Batch operation wiring. BatchOperationsTest covers some, but not all paths. |
| **SettingsScreenModel.kt** | 571 | LOW-MEDIUM | Settings UI state. Mostly delegates to SettingsRepository. |
| **RemoteDataSource.kt** | 496 | LOW | HTTP calls. Integration tests cover this indirectly via Docker tests. |

### THIN: Tests Exist but Insufficient

| Production File | Lines | Existing Tests | Gap |
|-----------------|-------|---------------|-----|
| **BookmarkRepository.kt** | 479 | 82 lines (very thin) | Only basic operations tested. Missing: pagination queries, filter-aware fetching, getAllBookmarks, content search. |
| **ListRepository.kt** | 162 | 100 lines (thin) | Missing: list CRUD flows, sync integration. |
| **HighlightRepository.kt** | 189 | Integration test only | No unit tests. getHighlightsCount flow (used for drawer counts) untested at unit level. |
| **MainScreenModel.kt** | 538 | Partial (selectAll, filters) | Missing: server switching, list navigation, search, sync triggering, bookmark deletion feedback. |

### UNTESTED UTILITIES

| Utility File | Lines | Risk | Note |
|-------------|-------|------|------|
| **HtmlSanitizer.kt** | 85 | MEDIUM | Sanitizes HTML content for reader. XSS prevention. |
| **ReadingTimeCalculator.kt** | 137 | LOW | Reading time estimation. Wrong values are cosmetic, not data-loss. |
| **ImageCacheManager.kt** | 176 | LOW | File I/O for image cache. Hard to unit test, low regression risk. |
| **DateUtils.kt** | 42 | LOW | Date formatting. |
| **AssetUrlUtils.kt** | 19 | LOW | URL construction. |

## Recommended Stack

The project already has the right test dependencies installed. No new libraries needed.

### Already Installed (verified from libs.versions.toml + build.gradle.kts)

| Library | Version | Source Set | Purpose |
|---------|---------|-----------|---------|
| kotlin-test | (matches Kotlin version) | commonTest | Multiplatform assertions |
| kotlinx-coroutines-test | (matches coroutines version) | commonTest, androidUnitTest, desktopTest | runTest, StandardTestDispatcher |
| mockk | 1.13.12 | commonTest, androidUnitTest, desktopTest | Mocking |
| JUnit 4 | 4.13.2 | androidUnitTest, desktopTest | Test runner |
| Robolectric | 4.14 | androidUnitTest | Android framework simulation |
| compose-ui-test-junit4 | 1.8.0 | androidUnitTest | Compose UI testing |
| androidx-test-core | 1.6.1 | androidUnitTest | Android test utilities |
| Testcontainers | 1.20.1 | desktopTest | Docker integration tests |

### Consider Adding

| Library | Purpose | When | Priority |
|---------|---------|------|----------|
| Turbine (app.cash.turbine) | StateFlow/SharedFlow testing assertions | When writing ScreenModel flow tests | MEDIUM |

**Turbine rationale:** The existing tests use manual `advanceUntilIdle()` + direct state reads, which works but is verbose and race-prone. Turbine provides `test { awaitItem(); awaitItem() }` syntax that's more readable and catches missed emissions. However, the current pattern works, so this is a nice-to-have.

## Architecture Patterns

### Pattern 1: Pure Function Extraction (ESTABLISHED)

**What:** Extract complex business logic from ScreenModels/Repositories into top-level pure functions.
**When to use:** Any logic that takes inputs and produces outputs without side effects.
**Already used for:** `applyRemoveBookmarkTransform`, `computeOfflineSyncTargets` (in test), `BookmarkFilterUtils`.

```kotlin
// Production code (top-level function, no class dependency)
fun applyRemoveBookmarkTransform(
    currentListContext: String?,
    listId: String,
    bookmarks: List<BookmarkEntity>,
    bookmark: BookmarkEntity
): List<BookmarkEntity> { ... }

// Test: direct call, no mocking needed
@Test
fun viewingTargetList_bookmarkIsFilteredOut() {
    val result = applyRemoveBookmarkTransform(...)
    assertEquals(0, result.size)
}
```

**Recommendation for new tests:** Before writing a complex mockk-heavy test, ask: "Can I extract the logic into a pure function?" If yes, do that first. This is the highest-ROI pattern.

**Candidates for extraction:**
- BookmarkSyncPipeline: page merging logic, conflict resolution, "needs content fetch" determination
- BookmarkActionController: undo expiry logic, action deduplication
- SettingsRepository: default value resolution, migration from legacy keys

### Pattern 2: ScreenModel-Centric Unit Test (ESTABLISHED)

**What:** Construct ScreenModel directly with mockk dependencies, exercise public methods, assert StateFlow values.
**When to use:** Testing ScreenModel behavior without Compose UI rendering.

```kotlin
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class MainScreenSelectAllTest {
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        // ... mock all dependencies with relaxed = true
        // ... stub all Flow properties consumed by stateIn in constructor
    }

    @After
    fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun `selectAll fetches all items when hasMoreItems is true`() = runTest(testDispatcher) {
        val model = createMainScreenModel()
        advanceUntilIdle()
        // ... exercise and assert
    }
}
```

**Key gotcha:** MainScreenModel has 8 constructor parameters and subscribes to many flows in its constructor. Every test must stub ALL subscribed flows or the test hangs/crashes. The existing `MainScreenSelectAllTest` is the canonical reference for how to do this.

### Pattern 3: Repository Unit Test with mockk DAOs (ESTABLISHED)

**What:** Construct repository with mockk DAOs, verify DAO interactions.
**When to use:** Testing repository business logic (optimistic updates, pending actions, data transformations).

```kotlin
class BookmarkActionsRepositoryUnitTest : BaseRepositoryTest() {
    private val bookmarkDao = mockk<BookmarkDao>(relaxed = true)
    private val repository = BookmarkActionsRepository(bookmarkDao = bookmarkDao, ...)

    @Test
    fun moveToList_updatesLocalDbWithNewListId() = runTest(testDispatcher) {
        coEvery { bookmarkDao.getBookmarkByRemoteId(...) } returns bookmark
        repository.moveToList(...)
        coVerify { bookmarkDao.insertBookmark(match { it.listIds == "list-99" }) }
    }
}
```

### Pattern 4: commonTest Over androidUnitTest (RECOMMENDED FOR NEW TESTS)

**What:** Write tests in `commonTest` when they don't need Android framework APIs.
**Why:** commonTest runs on all platforms (JVM, Android, potentially JS/Native). Faster execution, no Robolectric overhead.
**When to use androidUnitTest instead:** Only when you need Compose UI testing (`ComposeTestRule`), Android Activity testing, or Robolectric-provided Android APIs.

Most of the gap tests (BookmarkSyncPipeline, BookmarkActionController, SettingsRepository) can be pure commonTest.

### Anti-Patterns to Avoid

- **Full Koin/Voyager graph in tests:** Never bootstrap the full DI container. Construct classes directly with mockk dependencies.
- **Testing Compose UI for business logic:** If the behavior can be tested at the ScreenModel level, don't write a Compose UI test. UI tests are 10x slower and more fragile.
- **Hardcoded `delay()` in tests:** Use `advanceUntilIdle()` or `advanceTimeBy()` from `kotlinx-coroutines-test` instead.
- **Testing implementation details:** Don't verify exact DAO call counts unless the contract requires it. Prefer asserting state/output.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Flow testing assertions | Manual collect + advanceUntilIdle | Turbine `test {}` block (if added) or existing pattern | Catches missed emissions, timeout protection |
| Fake DataStore | Custom in-memory map | `PreferencesDataStoreFactory.create(storage = ...)` with temp file | DataStore has internal caching/threading that mocks can't replicate |
| Fake Room Database | Mock every DAO method | `Room.inMemoryDatabaseBuilder()` (already used in desktopTest integration tests) | Room generates DAO implementations; mocking bypasses SQL validation |
| Test dispatchers | `Dispatchers.Unconfined` | `StandardTestDispatcher` + `runTest` | Unconfined hides timing bugs; StandardTestDispatcher gives deterministic control |
| BookmarkEntity factory | Copy-paste constructors | Shared `makeBookmark()` helper (already exists in BookmarkActionsRepositoryUnitTest) | 15 constructor parameters; DRY |

## Common Pitfalls

### Pitfall 1: ScreenModel Constructor Flow Subscriptions

**What goes wrong:** Test hangs or crashes with "Job was cancelled" because a ScreenModel subscribes to flows in its `init` block and the mock returns `mockk()` default (which throws).
**Why it happens:** Voyager ScreenModels use `screenModelScope` which starts collecting immediately on construction.
**How to avoid:** Before constructing the ScreenModel, stub EVERY flow property with `every { repo.someFlow } returns flowOf(defaultValue)` or `returns MutableStateFlow(defaultValue)`. Use `relaxed = true` on mockk AND explicit stubs for Flow properties.
**Warning signs:** Test hangs forever, or `KotlinNothingValueException` in test output.

### Pitfall 2: SharedFlow replay=0 Race Condition

**What goes wrong:** Test asserts that a SharedFlow emission was collected, but the collector started after the emission.
**Why it happens:** `MutableSharedFlow(replay = 0)` drops emissions if no one is collecting.
**How to avoid:** Launch collector BEFORE the action, then `advanceUntilIdle()` to ensure the collector is subscribed before the emission.
**Warning signs:** Test passes intermittently. Emission collected is `null` when it should have a value.

### Pitfall 3: Robolectric SDK Version Mismatch

**What goes wrong:** `ClassNotFoundException` or `NoSuchMethodError` when running androidUnitTest.
**Why it happens:** Robolectric 4.14 requires SDK 34 by default. Mismatched `@Config(sdk = [X])` annotations cause failures.
**How to avoid:** Use `@Config(application = Application::class)` without specifying SDK (defaults to 34 with Robolectric 4.14). Already established in existing tests.

### Pitfall 4: JDK 25 Breaks Gradle

**What goes wrong:** `IllegalArgumentException: 25.0.2` when running `./gradlew test`.
**Why it happens:** Gradle 8.13's Kotlin DSL compiler can't parse JDK 25 version string.
**How to avoid:** Always set `JAVA_HOME` to JDK 21 before running Gradle.

### Pitfall 5: mockk `relaxed = true` Hides Bugs

**What goes wrong:** Test passes but the production code is calling the wrong DAO method.
**Why it happens:** `relaxed = true` returns default values for any unstubbed call, so incorrect calls don't fail.
**How to avoid:** Use `relaxed = true` for convenience mocks (settings, server repo), but use explicit `coEvery`/`coVerify` for the DAO calls you're actually testing.

### Pitfall 6: desktopTest vs commonTest Placement

**What goes wrong:** Tests that should be cross-platform are placed in desktopTest, so they don't run on Android CI.
**Why it happens:** Historical accident -- some tests were written when commonTest mockk support was unclear.
**How to avoid:** For new tests, default to `commonTest`. Only use `desktopTest` if you need JVM-specific APIs (File I/O, Docker, Testcontainers) or `androidUnitTest` if you need Compose UI testing / Android APIs.
**Candidates for migration:** `FilterConfigTest`, `PendingActionQueueTest`, `SyncContentOfflineTest`, `RemoveBookmarkFromListTest`, `BookmarkViewerProgressTest` could all be commonTest.

## Prioritized Backlog

Ordered by impact (risk reduction per hour invested). Each item includes estimated effort and recommended source set.

### Tier 1: High Impact, Moderate Effort (DO FIRST)

| # | Target | What to Test | Source Set | Est. Effort | Risk Reduced |
|---|--------|-------------|-----------|-------------|-------------|
| 1 | **BookmarkSyncPipeline** | Extract page-merge logic, conflict resolution, "needs content" filter as pure functions; test each | commonTest | 3-4h | CRITICAL -- sync bugs = data loss |
| 2 | **BookmarkActionController** | Undo flow (execute -> undo within timeout -> verify rollback); undo expiry (execute -> wait -> verify no rollback); double-execute prevention | commonTest | 2-3h | HIGH -- undo bugs = user frustration |
| 3 | **SettingsRepository read paths** | Verify each preference flow returns correct defaults when DataStore is empty; verify writes persist and reads reflect | commonTest (with fake DataStore) | 2-3h | HIGH -- settings bugs affect every session |
| 4 | **HtmlSanitizer** | Script tag removal, event handler stripping, safe HTML passthrough | commonTest | 1h | MEDIUM -- XSS prevention in reader |

### Tier 2: Medium Impact, Lower Effort (DO NEXT)

| # | Target | What to Test | Source Set | Est. Effort | Risk Reduced |
|---|--------|-------------|-----------|-------------|-------------|
| 5 | **BookmarkRepository** (expand) | getAllBookmarks with filters, pagination edge cases (empty result, single page, exact page boundary) | commonTest | 2h | MEDIUM -- pagination bugs = missing bookmarks |
| 6 | **BookmarkActionsRepositorySync** | pullReadingProgressFromServer (server higher, local higher, server null, bookmark not found) | commonTest | 1-2h | MEDIUM -- reading progress sync |
| 7 | **BookmarkViewerScreenModel** (expand) | Content loading states, action dispatch (archive, star, delete from viewer), error handling | androidUnitTest | 2-3h | MEDIUM -- reader is primary UX |
| 8 | **MainScreenModel** (expand) | Server switching, list navigation, search filtering, sync trigger | androidUnitTest | 2-3h | MEDIUM -- main screen orchestration |

### Tier 3: Lower Impact, Quick Wins (FILL IN AS TIME ALLOWS)

| # | Target | What to Test | Source Set | Est. Effort | Risk Reduced |
|---|--------|-------------|-----------|-------------|-------------|
| 9 | **ReadingTimeCalculator** | Edge cases: empty content, very short, very long, HTML with no text | commonTest | 30min | LOW |
| 10 | **DateUtils** | Format edge cases, timezone handling | commonTest | 30min | LOW |
| 11 | **AssetUrlUtils** | URL construction with various server URL formats | commonTest | 15min | LOW |
| 12 | **Migrate desktopTest to commonTest** | Move FilterConfigTest, PendingActionQueueTest, SyncContentOfflineTest, RemoveBookmarkFromListTest, BookmarkViewerProgressTest | commonTest | 1-2h | LOW (quality, not coverage) |

### Tier 4: Infrastructure Improvements

| # | Target | What to Do | Est. Effort |
|---|--------|-----------|-------------|
| 13 | **Shared BookmarkEntity factory** | Extract `makeBookmark()` helper to a shared test fixture in commonTest, used by all source sets | 30min |
| 14 | **BaseScreenModelTest** | Create a base class (like BaseRepositoryTest) that handles Dispatchers.setMain/resetMain, common mock stubs for SettingsRepository flows | 1h |
| 15 | **CI test reporting** | Ensure `./gradlew :composeApp:testDebugUnitTest :composeApp:desktopTest` runs in GitHub Actions (if not already) | 1h |

## Code Examples

### Example: Testing BookmarkSyncPipeline with Pure Function Extraction

The sync pipeline's page-merge logic should be extracted as a testable pure function:

```kotlin
// Production code (new top-level function in BookmarkSyncPipeline.kt)
internal fun mergeBookmarkPages(
    existingBookmarks: List<BookmarkEntity>,
    newPage: List<BookmarkEntity>,
    shouldDeleteRemoved: Boolean
): List<BookmarkEntity> {
    // ... merge logic currently buried in the pipeline
}

// Test (commonTest)
class BookmarkSyncMergeTest {
    @Test
    fun `new bookmarks are added to existing list`() {
        val existing = listOf(makeBookmark(remoteId = 1))
        val newPage = listOf(makeBookmark(remoteId = 2))
        val result = mergeBookmarkPages(existing, newPage, shouldDeleteRemoved = false)
        assertEquals(2, result.size)
    }

    @Test
    fun `full sync removes bookmarks not in new data`() {
        val existing = listOf(makeBookmark(remoteId = 1), makeBookmark(remoteId = 2))
        val newPage = listOf(makeBookmark(remoteId = 1))
        val result = mergeBookmarkPages(existing, newPage, shouldDeleteRemoved = true)
        assertEquals(1, result.size)
    }
}
```

### Example: Testing BookmarkActionController Undo

```kotlin
// commonTest
class BookmarkActionControllerTest {
    private val testDispatcher = StandardTestDispatcher()
    private val bookmarkActionsRepo = mockk<BookmarkActionsRepository>(relaxed = true)
    private val bookmarkDao = mockk<BookmarkDao>(relaxed = true)
    private val pendingActionDao = mockk<PendingActionDao>(relaxed = true)
    private val snackbarManager = mockk<ActionSnackbarManager>(relaxed = true)
    private val settingsRepository = mockk<SettingsRepository>(relaxed = true)

    @Test
    fun `undo within timeout restores bookmark`() = runTest(testDispatcher) {
        // Setup: archive a bookmark
        val bookmark = makeBookmark(remoteId = 42, isArchived = false)
        coEvery { bookmarkDao.getBookmarkByRemoteId(42, "server-1") } returns bookmark

        val controller = BookmarkActionController(
            bookmarkActionsRepository = bookmarkActionsRepo,
            bookmarkDao = bookmarkDao,
            pendingActionDao = pendingActionDao,
            snackbarManager = snackbarManager,
            settingsRepository = settingsRepository
        )

        // Execute action, then undo
        controller.executeAction(/* archive bookmark 42 */)
        advanceUntilIdle()
        controller.undoLastAction()
        advanceUntilIdle()

        // Verify the pending action was deleted (undo)
        coVerify { pendingActionDao.deleteAction(any()) }
    }
}
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `Dispatchers.Unconfined` for tests | `StandardTestDispatcher` + `runTest` | kotlinx-coroutines-test 1.6+ | Deterministic virtual time, catch timing bugs |
| Mock Room DAOs | In-memory Room DB for integration tests | Room 2.6+ KMP support | DAOs are auto-generated; mocking bypasses SQL validation |
| `@RunWith(AndroidJUnit4::class)` | `@RunWith(RobolectricTestRunner::class)` | Robolectric 4.x | Faster, no emulator needed |
| Compose UI test for all behavior | ScreenModel-centric testing, UI tests only for rendering | Community best practice ~2024 | 10x faster, more stable |

## Open Questions

1. **Should desktopTest unit tests migrate to commonTest?**
   - What we know: 5 files in desktopTest are pure unit tests that don't use JVM-specific APIs. They could run cross-platform.
   - What's unclear: Whether the migration is worth the effort (tests pass as-is).
   - Recommendation: Low priority. Do it as part of Tier 4 infrastructure work.

2. **Is Turbine worth adding?**
   - What we know: It simplifies Flow testing significantly. The project has ~15 Flow-assertion test cases.
   - What's unclear: Whether the team wants another dependency.
   - Recommendation: Add it when writing BookmarkActionController tests (which heavily test SharedFlow emissions). Don't retrofit existing tests.

3. **Integration test coverage gap**
   - What we know: Docker integration tests exist but require a Karakeep backend instance.
   - What's unclear: Whether these run in CI (GitHub Actions).
   - Recommendation: Ensure unit tests (commonTest + androidUnitTest) are the primary gate. Integration tests are bonus.

## Sources

### Primary (HIGH confidence)
- Direct code audit of all 36 test files and 50+ production files in the repository
- `composeApp/build.gradle.kts` for dependency versions
- `gradle/libs.versions.toml` for version catalog

### Secondary (MEDIUM confidence)
- Established patterns from Phase 05/06/07 (documented in STATE.md decisions)
- Memory file `reference_gradle_test_env.md` for JDK/Gradle compatibility

## Metadata

**Confidence breakdown:**
- Current inventory: HIGH - direct audit of every test file
- Coverage gaps: HIGH - compared test files to production files line by line
- Recommended patterns: HIGH - based on patterns already established in this project
- Prioritized backlog: MEDIUM - effort estimates are approximate, risk ranking is subjective

**Research date:** 2026-03-24
**Valid until:** 2026-06-24 (stable codebase, no major framework upgrades planned)

---

## UI Test Deep-Dive

**Researched:** 2026-03-25
**Domain:** Compose UI testing capabilities beyond ScreenModel-centric tests
**Confidence:** HIGH for current-setup items, MEDIUM for screenshot testing, LOW for commonTest Compose

### What's Already Covered (Phase 07)

The 6 existing androidUnitTest files use two distinct patterns:

1. **ScreenModel-centric tests** (4 files): Construct MainScreenModel with mockk, exercise methods, assert StateFlow values. No Compose rendering involved. Tests: ScrollPositionRegressionTest, MainScreenSelectAllTest, QuickFilterCountsTest, HighlightsPullToRefreshTest.

2. **Compose UI rendering tests** (2 files): Use `createComposeRule()` with real composables. ScrollToTopVisibilityTest renders a LazyColumn and uses `scrollToItem` + `onNodeWithTag` assertions. BookmarkSavingActivityTest uses Robolectric `buildActivity()` with reflection to read private state.

The existing Compose UI tests are limited to **isolated composable functions** (like `rememberScrollToTopVisibility`) that can be tested without Koin, Voyager Navigator, or full screen composition. None of the existing tests render a full screen or interact with dialogs.

### Achievable with Current Setup (Robolectric 4.14 + ComposeTestRule)

**Confidence: HIGH** -- these follow established patterns and verified Robolectric capabilities.

#### 1. AlertDialog-based components (TagEditorDialog, confirmation dialogs)

Compose AlertDialogs render in the same Compose tree and are fully accessible to `composeTestRule`. No shadow or special setup needed.

```kotlin
// Example: TagEditorDialog test
composeTestRule.setContent {
    TagEditorDialog(
        currentTags = listOf("kotlin"),
        availableTags = listOf("kotlin", "android", "compose"),
        onTagsUpdated = { result = it },
        onDismiss = { dismissed = true }
    )
}
composeTestRule.onNodeWithText("kotlin").assertIsDisplayed()  // current tag chip
composeTestRule.onNodeWithText("Save").performClick()          // confirm button
```

**What can be tested:**
- Tag display (current tags shown as chips)
- Tag removal (click X on chip)
- Autocomplete suggestions (type text, verify suggestions appear)
- Confirm/cancel button behavior
- canCreateNew=false mode (free text rejected)

**Setup complexity:** LOW. These components are self-contained composables with callback parameters. No Koin, no ScreenModel, no Navigator needed.

**Estimated effort per dialog:** 1-2 hours for a thorough test suite.

#### 2. Isolated reusable components (TagChip, BookmarkTagsDisplay, ReadingTimeBadge, etc.)

All components in `ui/components/` that accept data parameters and callbacks can be tested with `setContent { Component(...) }` plus semantic assertions.

**What can be tested:**
- TagChip renders tag text, onClick fires, onRemove fires
- BookmarkTagsDisplay renders correct number of chips from comma-separated string
- FilterBottomPanel status/sort chip selection (FilterChip click toggles state)
- StorageUsageBar visual state at 0%, 50%, 100%
- SkeletonLoader renders placeholder items
- ReadingTimeBadge displays formatted time

**Setup complexity:** LOW. Pure composable functions with no external dependencies.

#### 3. FilterPanelContent interactions

The FilterBottomPanel and FilterSidePanel use `BaseBottomPanel` and `Surface` respectively, but their core content (`FilterPanelContent`) can be tested by rendering it directly or through the side panel variant (which does not use ModalBottomSheet).

**What can be tested:**
- Status filter chip selection (All/Favorites/Archived)
- Sort option toggling (Newest/Oldest, Title A-Z/Z-A)
- Tag selection/deselection in filter
- List selection in filter
- Reset button clears all filters

**Setup complexity:** LOW-MEDIUM. FilterPanelContent is private, so test through FilterSidePanel (no bottom sheet) or extract the content composable.

#### 4. ListPickerDialog hierarchy display

ListPickerDialog uses ModalBottomSheet, which has a **known click bug on Robolectric 4.14** (see "Requires Additional Setup" below). However, the hierarchy rendering and display can be tested by extracting the list content into a separate composable or testing the underlying `buildListHierarchy` logic (already covered in commonTest).

#### 5. Loading and error state composables

BookmarkLoadingState.kt and empty-state composables can be tested for correct text/icon rendering based on state parameters.

### Requires Additional Setup

**Confidence: MEDIUM** -- achievable but requires dependency changes or non-trivial wiring.

#### 1. ModalBottomSheet click interactions (BLOCKED on Robolectric 4.14)

**Problem:** Robolectric 4.14 has a confirmed bug where `performClick()` on items inside `ModalBottomSheet` silently fails on SDK levels 27 and 29-34. The root cause is a missing `Path.op` implementation in Robolectric's shadow framework. This was fixed in Robolectric 4.15+ (PR #10288).

**Impact:** ListPickerDialog, BookmarkActionsMenu, and any test that clicks items inside a ModalBottomSheet will not work with the current Robolectric 4.14.

**Fix:** Upgrade `robolectric` from `4.14` to `4.15.1` in `libs.versions.toml`. This is a safe minor version bump.

**Affected components:** ListPickerDialog, BookmarkActionsMenu, any future bottom sheet interactions.

**Estimated effort:** 15 minutes to change version, then 1-2 hours to write bottom sheet tests.

#### 2. Voyager Navigator screen transitions

**Problem:** Voyager provides no testing utilities or test module. To test navigation, you would need to:
1. Create a real `Navigator` composable in the test
2. Push/pop screens and assert which screen is displayed
3. This requires the full screen composable to render, which pulls in Koin dependencies (ScreenModel injection via `rememberScreenModel`)

**Workaround:** Wrap the Navigator in a test that provides a minimal Koin module with mockk-based ScreenModels:

```kotlin
composeTestRule.setContent {
    KoinApplication(application = { modules(testModule) }) {
        Navigator(screen = MainScreen()) { navigator ->
            // test navigation
        }
    }
}
```

**Honest assessment:** This is fragile and high-effort. Each screen's ScreenModel requires full mockk wiring (see the ~30 lines of mock setup in ScrollPositionRegressionTest, and that is for ONE ScreenModel). Testing multi-screen navigation would require wiring 2+ ScreenModels with all their flow stubs.

**Recommendation:** Do NOT pursue Navigator-level testing. Navigation logic in Karakept is simple (push screen, pop screen). The risk is low compared to the setup cost. If specific navigation bugs emerge, write a targeted regression test.

**Estimated effort:** 4-8 hours for a single multi-screen navigation test. Not worth it.

#### 3. Full screen rendering tests (MainScreen, BookmarkViewerScreen)

**Problem:** Full screens use `rememberScreenModel<T>()` which requires Koin. They also use `LocalNavigator.currentOrThrow` which requires a Voyager Navigator parent.

**What it would take:**
- Koin test module with every dependency mocked
- Navigator wrapper composable
- Mock Coil image loader (screens load images)
- Mock platform-specific composables (WebView, CustomTabOpener)

**Honest assessment:** Too much setup for too little value. A full MainScreen render test would be 100+ lines of mock wiring to verify something that manual testing catches in seconds.

**Recommendation:** Continue testing screen behavior through ScreenModel-centric tests. Test individual composable *sections* (like the top bar, fab menu, details panel) if they can be rendered without the full dependency graph.

#### 4. Compose Multiplatform commonTest UI tests

**What exists:** Kotlin docs describe a `compose.uiTest` dependency for commonTest using `runComposeUiTest {}` API. This is platform-independent and works across Android, Desktop, iOS, and Web.

**Setup required:**
```kotlin
commonTest.dependencies {
    @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
    implementation(compose.uiTest)
}
```

**Limitations:**
- Marked as **Experimental** API
- Does NOT use JUnit TestRule -- uses `runComposeUiTest {}` function instead
- Cannot run as Android local tests (would need instrumented runner or Desktop JVM)
- No Robolectric -- would run as JVM/Desktop tests
- The project's existing androidUnitTest Compose tests use `createComposeRule()` (JUnit4 rule) which is incompatible with this API

**Honest assessment:** This would be useful for testing pure composable components that have no Android dependencies (TagChip, BookmarkTagsDisplay, FilterPanelContent). But the project already has working Compose test infrastructure in androidUnitTest. Adding a second Compose test setup in commonTest creates confusion about where to put tests, and the Experimental API status means potential breaking changes.

**Recommendation:** Do NOT add commonTest Compose UI tests at this time. The androidUnitTest setup works well. Revisit when `compose.uiTest` leaves Experimental status.

### Not Practical / Avoid

**Confidence: HIGH** -- these are known anti-patterns or have confirmed technical blockers.

| Scenario | Why Avoid | Alternative |
|----------|-----------|-------------|
| **Full screen render tests** | 100+ lines of mock wiring per screen; Koin + Navigator + Coil + platform mocks | Test ScreenModel behavior; test individual composable sections |
| **Navigation flow tests** | Voyager has no test support; requires full DI graph | Navigation is simple push/pop; test at ScreenModel level if needed |
| **Dark/light theme correctness** | Robolectric renders with JVM fonts, colors may differ from real device; assertions on color values are fragile | Use screenshot tests (Roborazzi) if theme correctness is critical; or manual QA |
| **Animation testing** | Robolectric timing model does not match real frame timing; animation tests are inherently flaky | Skip; animations are cosmetic |
| **WebView content testing** | Robolectric does not support WebView rendering | WebView is platform-specific; test content loading at ScreenModel level |
| **Swipe gesture testing** | SwipeableBookmarkItem uses complex gesture detectors; Robolectric gesture simulation is unreliable for multi-axis swipes | Test swipe action dispatch at ScreenModel level (archive/star on swipe) |

### Screenshot / Golden Testing

#### Roborazzi (Recommended if pursuing screenshot tests)

**What it is:** JVM-based screenshot testing that captures Compose UI output as images and compares against golden files. Runs on Robolectric -- no emulator needed.

**Current version:** 1.59.0+ (actively maintained, frequent releases)

**KMP support:** Supports Android (via Robolectric), Compose Desktop, and Compose iOS. The Desktop support is relevant since Karakept targets Desktop.

**Setup required:**
1. Add Gradle plugin: `io.github.takahirom.roborazzi`
2. Add dependency: `roborazzi-compose` to androidUnitTest
3. Configure golden file directory
4. Write capture tests using `captureRoboImage()` on ComposeTestRule nodes

**What it would catch:** Visual regressions in TagChip styling, bookmark card layouts, filter panel arrangement, theme color changes.

**Honest assessment:** Screenshot tests are high-value for design-system components (TagChip, BookmarkTagsDisplay, BookmarkLayouts) where visual consistency matters. They are LOW-value for full screens (too many mock dependencies, images break on any content change).

**Recommendation:** Worth adding ONLY for the ~5 core reusable components listed in CLAUDE.md (TagChip, BookmarkTagsDisplay, TagEditorDialog, ListPickerDialog hierarchy rendering). Not worth it for full screens.

**Estimated effort:** 2-3 hours for initial setup + 5-6 component golden tests.

#### Paparazzi (NOT recommended)

**Why not:** Paparazzi does not support Kotlin Multiplatform projects directly. It requires creating a separate pure-Android library module as a workaround. Roborazzi is the better fit since it works with Robolectric (already in the project) and supports Desktop.

### UI Test Prioritized Backlog

Ordered by value (regression prevention per hour invested). All items are androidUnitTest unless noted.

| Priority | Target | What to Test | Setup | Est. Effort | Value |
|----------|--------|-------------|-------|-------------|-------|
| **1** | **TagEditorDialog** | Add/remove tags, autocomplete suggestions, canCreateNew modes, confirm/cancel | LOW -- standalone composable | 1.5h | HIGH -- used everywhere tags are edited or filtered |
| **2** | **FilterPanelContent** (via FilterSidePanel) | Status chip selection, sort toggling, tag filter selection, list filter selection, reset | LOW -- render FilterSidePanel with test data | 2h | HIGH -- filter bugs affect every user's workflow |
| **3** | **BookmarkTagsDisplay** | Renders correct chips for comma-separated tags, COMPACT vs READER style, onTagClick callback | LOW -- pure composable | 45min | MEDIUM -- canonical tag display component |
| **4** | **TagChip** | Renders tag text, onClick fires, onRemove shows X and fires, selected state styling | LOW -- pure composable | 30min | MEDIUM -- foundational component |
| **5** | **Upgrade Robolectric to 4.15.1** | Prerequisite for bottom sheet click tests | 15min version bump | 15min | MEDIUM -- unblocks items 6-7 |
| **6** | **ListPickerDialog** (after Robolectric upgrade) | Hierarchy display, current list checkmark, list selection callback | MEDIUM -- ModalBottomSheet, needs Robolectric 4.15.1 | 1h | MEDIUM -- list management |
| **7** | **BookmarkActionsMenu** (after Robolectric upgrade) | Action items displayed, destructive actions styled with error color, click dispatches correct action | MEDIUM -- ModalBottomSheet, needs Robolectric 4.15.1 | 1h | MEDIUM -- action menu correctness |
| **8** | **Roborazzi golden tests for core components** | TagChip, BookmarkTagsDisplay, TagEditorDialog, FilterPanelContent visual snapshots | MEDIUM -- new Gradle plugin + dependency | 3h | LOW-MEDIUM -- catches visual regressions |
| **9** | **BookmarkLoadingState** | Correct text/icon for each loading state variant | LOW -- pure composable | 30min | LOW -- cosmetic |
| **10** | **SkeletonLoader / BookmarkPlaceholderItem** | Renders correct number of placeholder items | LOW -- pure composable | 30min | LOW -- cosmetic |

**Total estimated effort for items 1-7:** ~7 hours
**Total estimated effort for all items:** ~11 hours

### Key Takeaways

1. **The highest-value UI tests are dialog and component tests, not screen tests.** TagEditorDialog and FilterPanelContent are used in multiple places and have interactive behavior worth verifying. Full screen tests are not worth the setup cost.

2. **Robolectric 4.14 blocks ModalBottomSheet testing.** Upgrade to 4.15.1 to unblock ListPickerDialog and BookmarkActionsMenu tests. This is a safe, minor version bump.

3. **Voyager navigation testing is not practical.** No test utilities exist, and the setup cost (full Koin graph + multiple ScreenModels) vastly exceeds the value. Navigation in Karakept is simple push/pop.

4. **commonTest Compose UI tests are premature.** The `compose.uiTest` API is Experimental and adds a second test infrastructure. Stick with androidUnitTest for Compose rendering tests.

5. **Screenshot testing (Roborazzi) is a nice-to-have for design-system components only.** Not worth it for full screens. Adds CI complexity (golden file management). Consider only after the higher-priority behavioral tests are in place.

6. **The existing research backlog (Tier 1-3 items above this section) remains higher priority.** ScreenModel and repository tests prevent data-loss bugs; UI component tests prevent cosmetic and interaction bugs. Do Tier 1-3 first.

### Sources

- [Robolectric ModalBottomSheet click bug -- Issue #9595](https://github.com/robolectric/robolectric/issues/9595) -- confirmed fixed in 4.15.1
- [Kotlin Compose Multiplatform UI Testing docs](https://kotlinlang.org/docs/multiplatform/compose-test.html) -- commonTest compose.uiTest API
- [Roborazzi GitHub](https://github.com/takahirom/roborazzi) -- screenshot testing for Compose
- [Robolectric strategies -- Android Developers](https://developer.android.com/training/testing/local-tests/robolectric) -- capabilities and limitations
- [Blazing fast Compose tests with Robolectric](https://medium.com/@sebaslogen/blazing-fast-compose-tests-with-robolectric-b059f5471495) -- patterns and fidelity
- [Voyager GitHub](https://github.com/adrielcafe/voyager) -- confirmed no test support module exists
- [Comparing Snapshot testing libraries](https://medium.com/@natalia.kulbaka/comparing-snapshot-testing-libraries-paparazzi-roborazzi-compose-previews-screenshot-testing-b7c3b47f7f59) -- Paparazzi vs Roborazzi comparison
