# Phase 08: Test Coverage Expansion - Research

**Researched:** 2026-03-25
**Domain:** Kotlin Multiplatform testing (unit tests, domain logic, repository logic)
**Confidence:** HIGH

## Summary

The project has a solid test foundation with 35 test files across three source sets: `commonTest` (14 files, ~200 tests), `androidUnitTest` (6 files, ~22 tests with Robolectric + Compose UI testing), and `desktopTest` (15 files, ~122 tests including Docker-based integration tests). The total test count is approximately 344 `@Test` methods.

However, several critical business logic areas remain completely untested: `BookmarkSyncPipeline` (538 lines, core sync orchestration), `BookmarkActionController` (304 lines, action execution with undo), `SettingsRepository` + `SettingsRepositoryMutations` (1003 lines combined, all settings persistence), and several pure utility functions (`HtmlSanitizer`, `ReadingTimeCalculator`, `DateUtils`, `FaviconUtils`, `AssetUrlUtils`, `HighlightOffsetFinder`). The `ListSyncConfig` model (113 lines) has complex recursive logic with no dedicated test.

**Primary recommendation:** Focus on pure-function and mockk-based unit tests in `commonTest` for maximum coverage impact with minimal infrastructure cost. The untested pure utilities and domain models are low-hanging fruit. The `BookmarkActionController` and `BookmarkSyncPipeline` require mockk-based testing but follow the same pattern already established in `BookmarkActionsRepositoryUnitTest`.

## Project Constraints (from CLAUDE.md)

- Kotlin Multiplatform / Compose Multiplatform targeting Android
- Material3 UI layer with Voyager navigation and Koin DI
- SQLDelight for local storage (actually Room based on build config)
- ScreenModel-centric testing with mockk: construct directly, exercise methods, assert StateFlow values
- Pure function extraction for business logic enables unit tests without Voyager/Koin wiring
- Robolectric 4.14 + SDK 34 is the stable androidUnitTest baseline
- JDK 21 required for Gradle (JDK 25 currently installed -- user must set JAVA_HOME)
- Build/test commands run externally by the user, not in session

## Standard Stack

### Core Testing
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| kotlin-test | (matches Kotlin version) | Assertions and test annotations for commonTest | KMP-native, works across all targets |
| kotlinx-coroutines-test | (matches coroutines version) | `runTest`, `StandardTestDispatcher`, `advanceUntilIdle` | Official coroutine testing support |
| mockk | 1.13.12 | Mocking DAOs, repositories, remote data sources | Already used throughout; relaxed mocks reduce boilerplate |

### Platform-Specific
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| JUnit 4 | 4.13.2 | Test runner for androidUnitTest and desktopTest | Platform test source sets |
| Robolectric | 4.14 | Android framework simulation for androidUnitTest | Tests needing Android context (Compose UI tests) |
| compose-ui-test-junit4 | 1.8.0 | Compose UI test rule, node assertions | Compose UI component tests in androidUnitTest |
| testcontainers | 1.20.1 | Docker-based integration tests | desktopTest integration tests only |

### Not Needed
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Turbine (Flow testing) | Manual `collect` with `UnconfinedTestDispatcher` | Project already uses manual collection pattern consistently; adding Turbine is unnecessary churn |
| assertk/kotest assertions | kotlin-test assertions | Project already uses `assertEquals`, `assertTrue`, `assertIs` from kotlin-test; consistent style |

## Architecture Patterns

### Test Source Set Structure
```
composeApp/src/
  commonTest/       # Pure Kotlin tests -- no platform deps, run on all targets
    kotlin/com/karakept/app/
      data/model/       # Serialization, model logic
      data/repository/  # Repository unit tests with mockk
      domain/           # Domain logic (filters, hierarchy, actions)
      ui/screens/       # Pure pagination utils
      utils/            # Utility function tests
  androidUnitTest/  # Robolectric + Compose UI tests
    kotlin/com/karakept/app/ui/screens/  # ScreenModel regression tests
  desktopTest/      # JVM tests (integration + unit)
    kotlin/com/karakept/app/
      data/integration/  # Docker-based API integration tests
      data/model/        # FilterConfig tests
      data/repository/   # Pending action queue, sync content
      data/secure/       # Credential store
      ui/screens/        # Viewer progress, remove from list
      utils/             # BackupCrypto
```

### Pattern 1: Pure Function Unit Test (commonTest)
**What:** Test pure functions with no dependencies
**When to use:** Utility classes, model logic, data transformations
**Example:**
```kotlin
// Already used in: PaginationUtilsTest, ListHierarchyUtilsTest, BookmarkFilterUtilsTest
class ReadingTimeCalculatorTest {
    @Test
    fun emptyContent_returnsZero() {
        assertEquals(0, ReadingTimeCalculator.calculateReadingTime(null))
        assertEquals(0, ReadingTimeCalculator.calculateReadingTime(""))
    }
}
```

### Pattern 2: Mockk-Based Repository Test (commonTest)
**What:** Test repository methods with mocked DAOs and data sources
**When to use:** Repository and controller classes with injected dependencies
**Example:**
```kotlin
// Already used in: BookmarkActionsRepositoryUnitTest, BackupRepositoryTest
class BookmarkActionControllerTest : BaseRepositoryTest() {
    private val bookmarkActionsRepository = mockk<BookmarkActionsRepository>(relaxed = true)
    private val bookmarkDao = mockk<BookmarkDao>(relaxed = true)
    private val pendingActionDao = mockk<PendingActionDao>(relaxed = true)
    private val snackbarManager = mockk<ActionSnackbarManager>(relaxed = true)
    private val settingsRepository = mockk<SettingsRepository>(relaxed = true)

    private val controller = BookmarkActionController(
        bookmarkActionsRepository, bookmarkDao, pendingActionDao,
        snackbarManager, settingsRepository
    )

    @Test
    fun archiveAction_callsRepositoryAndShowsSnackbar() = runTest(testDispatcher) {
        val bookmark = makeBookmark()
        coEvery { settingsRepository.resetProgressOnMarkUnread } returns flowOf(false)
        val result = controller.executeAction(BookmarkActionEvent.Archive(bookmark))
        assertIs<BookmarkActionResult.Success>(result)
        coVerify { bookmarkActionsRepository.archiveBookmark(any(), any()) }
    }
}
```

### Pattern 3: Robolectric ScreenModel Test (androidUnitTest)
**What:** Test ScreenModel state changes with mocked dependencies
**When to use:** ScreenModel behavior tests requiring Android runtime
**Example:**
```kotlin
// Already used in: ScrollPositionRegressionTest, QuickFilterCountsTest
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class SomeScreenModelTest {
    private val testDispatcher = StandardTestDispatcher()
    // ... mock all dependencies, construct ScreenModel, assert StateFlow values
}
```

### Anti-Patterns to Avoid
- **Testing Compose UI in commonTest:** Compose testing requires Android context via Robolectric; use androidUnitTest.
- **Mocking DataStore in SettingsRepository tests:** DataStore is hard to mock. Use a real in-memory DataStore (PreferenceDataStoreFactory.create with temp file) or test the pure transform functions only.
- **Testing integration logic as unit tests:** BookmarkSyncPipeline's `execute()` orchestrates 6 phases. Test each private method's logic through focused scenarios, not end-to-end.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Coroutine test dispatchers | Custom dispatcher wrappers | `StandardTestDispatcher` + `runTest` | Already used via BaseRepositoryTest |
| Mock verification | Manual call tracking | mockk `coVerify` / `verify` | Already established pattern |
| Bookmark entity factories | Inline construction in every test | Shared `makeBookmark()` in BaseRepositoryTest | Already exists, reduces duplication |
| Settings test data | Real DataStore instances | mockk `SettingsRepository` with `every { property } returns flowOf(value)` | Consistent with existing tests |

## Common Pitfalls

### Pitfall 1: JDK Version Mismatch
**What goes wrong:** Gradle tasks fail with cryptic errors about Kotlin DSL or class version
**Why it happens:** System default is JDK 25; Gradle/Kotlin requires JDK 21
**How to avoid:** Set `JAVA_HOME` to JDK 21 before running any `./gradlew` command
**Warning signs:** `Unsupported class file major version` errors

### Pitfall 2: BookmarkSyncPipeline Internal Visibility
**What goes wrong:** Cannot construct `BookmarkSyncPipeline` from test code
**Why it happens:** The class and `SyncConfiguration` are marked `internal`
**How to avoid:** Place tests in `commonTest` under the same package (`com.karakept.app.data.repository`) -- Kotlin `internal` is accessible within the same module
**Warning signs:** Compilation error "Cannot access 'BookmarkSyncPipeline': it is internal"

### Pitfall 3: SettingsRepository DataStore Dependencies
**What goes wrong:** Cannot instantiate SettingsRepository in commonTest without a real DataStore
**Why it happens:** SettingsRepository constructor requires `DataStore<Preferences>`, which needs platform-specific factory
**How to avoid:** For unit tests, mock SettingsRepository entirely. For mutation tests, use desktopTest with a real in-memory DataStore or test only the pure transformation logic
**Warning signs:** `NoClassDefFoundError` for DataStore classes in commonTest

### Pitfall 4: Relaxed Mocks Hiding Bugs
**What goes wrong:** Tests pass but don't actually verify behavior because relaxed mocks return defaults
**Why it happens:** `mockk(relaxed = true)` returns empty strings, false, 0, etc. for unconfigured calls
**How to avoid:** Use `coVerify` to assert expected interactions. Use `coEvery` to set up specific return values for methods under test
**Warning signs:** Tests that only assert "no exception thrown" without verifying state changes

### Pitfall 5: Flaky Coroutine Timing in Tests
**What goes wrong:** Tests fail intermittently due to coroutine scheduling
**Why it happens:** `BookmarkActionController` uses `CoroutineScope(Dispatchers.IO + SupervisorJob())` internally
**How to avoid:** Tests should call `advanceUntilIdle()` after triggering actions. For the controller's internal scope, consider whether `executeAction` (which uses `withContext(Dispatchers.IO)`) can be tested via its return value rather than side effects
**Warning signs:** Tests pass individually but fail in suite, or pass on fast machines but fail on CI

## Code Examples

### makeBookmark() helper (already exists in BaseRepositoryTest pattern)
```kotlin
// Source: commonTest/BookmarkActionsRepositoryUnitTest.kt (inferred pattern)
protected fun makeBookmark(
    localId: Long = 1L,
    remoteId: Long = 100L,
    originalRemoteId: String = "remote-100",
    serverId: String = "server-1",
    listIds: String = "",
    tags: String = "",
    isStarred: Boolean = false,
    isArchived: Boolean = false
): BookmarkEntity = BookmarkEntity(
    localId = localId, remoteId = remoteId,
    originalRemoteId = originalRemoteId, serverId = serverId,
    title = "Test Bookmark", url = "https://example.com",
    tags = tags, listIds = listIds,
    isStarred = isStarred, isArchived = isArchived,
    createdAt = System.currentTimeMillis(),
    readingTimeMinutes = 0, readingProgress = 0f,
    readingScrollIndex = 0, readingScrollOffset = 0,
    content = ""
)
```

### Testing BookmarkSyncPipeline's mapDtoToEntity logic
```kotlin
// Pattern: construct pipeline with mocked deps, exercise specific scenarios
class BookmarkSyncPipelineTest : BaseRepositoryTest() {
    private val bookmarkDao = mockk<BookmarkDao>(relaxed = true)
    private val remoteDataSource = mockk<RemoteDataSource>(relaxed = true)
    private val settingsRepository = mockk<SettingsRepository>(relaxed = true)
    // ... etc

    @Test
    fun fullSync_deletesRemovedBookmarks() = runTest(testDispatcher) {
        // Setup: existing bookmarks in DB, remote returns subset
        coEvery { bookmarkDao.getBookmarksForServer(any()) } returns flowOf(listOf(existingBookmark))
        coEvery { remoteDataSource.fetchBookmarks(any(), any(), any(), any(), any()) } returns
            BookmarkListResponse(bookmarks = emptyList(), nextCursor = null)
        // Execute and verify deletion
    }
}
```

## Coverage Gap Analysis

### Priority 1: Pure Utility Functions (commonTest, ~12 new test files, HIGH impact)
| Source File | Lines | Test Exists? | Testable Without Mocks |
|-------------|-------|-------------|------------------------|
| `utils/ReadingTimeCalculator.kt` | 137 | No | Yes -- pure function |
| `utils/HtmlSanitizer.kt` | 85 | No | Yes -- pure function |
| `utils/DateUtils.kt` | 42 | No | Yes -- pure function |
| `utils/FaviconUtils.kt` | 21 | No | Yes -- pure function |
| `utils/AssetUrlUtils.kt` | 19 | No | Yes -- pure function |
| `ui/components/reader/HighlightOffsetFinder.kt` | 118 | No | Yes -- pure function |
| `data/model/ListSyncConfig.kt` | 113 | No | Yes -- model logic |
| `data/model/CheckboxState.next()` | (in ListSyncConfig) | No | Yes -- enum logic |

### Priority 2: Domain/Action Layer (commonTest, ~3 new test files, HIGH impact)
| Source File | Lines | Test Exists? | Notes |
|-------------|-------|-------------|-------|
| `domain/action/BookmarkActionController.kt` | 304 | No | Requires mockk; core user action handler |
| `data/repository/BookmarkActionsRepositorySync.kt` | 327 | No | Sync-specific actions; needs mockk |
| `data/repository/BookmarkActionsRepositoryBatch.kt` | 185 | Partial (BatchOperationsTest) | Batch operations tested; may need gaps filled |

### Priority 3: Sync Pipeline (commonTest, 1 new test file, MEDIUM-HIGH impact)
| Source File | Lines | Test Exists? | Notes |
|-------------|-------|-------------|-------|
| `data/repository/BookmarkSyncPipeline.kt` | 538 | No | Internal class; testable from same package. Complex differential sync logic, content sync decisions |

### Priority 4: Settings Persistence (desktopTest preferred, MEDIUM impact)
| Source File | Lines | Test Exists? | Notes |
|-------------|-------|-------------|-------|
| `data/repository/SettingsRepository.kt` | 522 | No | Read flows from DataStore. Hard to test without real DataStore. Mock for consumer tests |
| `data/repository/SettingsRepositoryMutations.kt` | 481 | No | Write operations. Same DataStore constraint |

### Summary of Existing vs. Needed
- **Existing tests:** ~344 @Test methods across 35 files
- **Estimated new tests:** ~120-150 new @Test methods across ~12-15 new files
- **Estimated coverage improvement:** From ~40% of business logic to ~75-80%

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | kotlin-test + JUnit 4 + mockk 1.13.12 |
| Config file | `composeApp/build.gradle.kts` (source set dependencies) |
| Quick run command | `./gradlew :composeApp:allTests --tests "com.karakept.app.*"` |
| Full suite command | `./gradlew :composeApp:desktopTest :composeApp:testDebugUnitTest` |

### Phase Requirements to Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| COV-01 | Pure utility functions tested | unit | `./gradlew :composeApp:desktopTest --tests "com.karakept.app.utils.*"` | Wave 0 |
| COV-02 | HighlightOffsetFinder tested | unit | `./gradlew :composeApp:desktopTest --tests "com.karakept.app.ui.components.reader.*"` | Wave 0 |
| COV-03 | ListSyncConfig model logic tested | unit | `./gradlew :composeApp:desktopTest --tests "com.karakept.app.data.model.ListSyncConfigTest"` | Wave 0 |
| COV-04 | BookmarkActionController tested | unit | `./gradlew :composeApp:desktopTest --tests "com.karakept.app.domain.action.BookmarkActionControllerTest"` | Wave 0 |
| COV-05 | BookmarkSyncPipeline tested | unit | `./gradlew :composeApp:desktopTest --tests "com.karakept.app.data.repository.BookmarkSyncPipelineTest"` | Wave 0 |
| COV-06 | BookmarkActionsRepositorySync tested | unit | `./gradlew :composeApp:desktopTest --tests "com.karakept.app.data.repository.BookmarkActionsRepositorySyncTest"` | Wave 0 |

### Sampling Rate
- **Per task commit:** `./gradlew :composeApp:desktopTest` (fastest, ~30s)
- **Per wave merge:** `./gradlew :composeApp:desktopTest :composeApp:testDebugUnitTest`
- **Phase gate:** Full suite green before `/gsd:verify-work`

### Wave 0 Gaps
- [ ] `commonTest/.../utils/ReadingTimeCalculatorTest.kt` -- covers COV-01
- [ ] `commonTest/.../utils/HtmlSanitizerTest.kt` -- covers COV-01
- [ ] `commonTest/.../utils/DateUtilsTest.kt` -- covers COV-01
- [ ] `commonTest/.../utils/FaviconUtilsTest.kt` -- covers COV-01
- [ ] `commonTest/.../utils/AssetUrlUtilsTest.kt` -- covers COV-01
- [ ] `commonTest/.../ui/components/reader/HighlightOffsetFinderTest.kt` -- covers COV-02
- [ ] `commonTest/.../data/model/ListSyncConfigTest.kt` -- covers COV-03
- [ ] `commonTest/.../domain/action/BookmarkActionControllerTest.kt` -- covers COV-04
- [ ] `commonTest/.../data/repository/BookmarkSyncPipelineTest.kt` -- covers COV-05
- [ ] `commonTest/.../data/repository/BookmarkActionsRepositorySyncTest.kt` -- covers COV-06

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| JDK 21 | Gradle/Kotlin build | Needs JAVA_HOME override | JDK 25 installed (too new) | User must set JAVA_HOME to JDK 21 path |
| Android SDK | androidUnitTest | Likely (project builds) | -- | -- |
| Docker | desktopTest integration | Likely | -- | Skip integration tests; focus on unit tests |

**Missing dependencies with no fallback:**
- JDK 21 must be available on the system (JDK 25 is the default; user knows this and handles it)

**Missing dependencies with fallback:**
- None. All test dependencies are already declared in build.gradle.kts.

## Open Questions

1. **makeBookmark() helper location**
   - What we know: BookmarkActionsRepositoryUnitTest uses it, likely defined in the test file or BaseRepositoryTest
   - What's unclear: Whether it's shared or duplicated across test files
   - Recommendation: Verify and extract to BaseRepositoryTest if not already shared; new tests should reuse it

2. **commonTest vs desktopTest for mockk tests**
   - What we know: mockk is available in commonTest (declared in build.gradle.kts); some mockk tests are in commonTest (BookmarkActionsRepositoryUnitTest), others in desktopTest (PendingActionQueueTest)
   - What's unclear: Whether mockk works reliably in commonTest for all scenarios (KMP mockk has had platform issues historically)
   - Recommendation: Prefer commonTest for new tests; fall back to desktopTest if compilation issues arise

3. **SettingsRepository testability**
   - What we know: Requires DataStore<Preferences> which needs platform factory
   - What's unclear: Whether PreferenceDataStoreFactory can be used in commonTest
   - Recommendation: Mock SettingsRepository in consumer tests (controller, pipeline). Defer direct SettingsRepository mutation tests to a future phase or keep in desktopTest

## Sources

### Primary (HIGH confidence)
- Project source code: direct file analysis of all 35 test files and ~40 untested source files
- `composeApp/build.gradle.kts`: test dependency declarations and source set configuration
- `gradle/libs.versions.toml`: exact library versions

### Secondary (MEDIUM confidence)
- STATE.md accumulated decisions: "ScreenModel-centric testing with mockk", "Robolectric 4.14 + SDK 34 baseline"
- Existing test patterns: analyzed all test files to extract consistent testing conventions

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH -- all libraries already declared and in use
- Architecture: HIGH -- test patterns extracted from 35 existing test files
- Pitfalls: HIGH -- based on actual project codebase analysis (internal visibility, DataStore constraints, JDK version)
- Coverage gaps: HIGH -- systematic comparison of source files vs test files

**Research date:** 2026-03-25
**Valid until:** 2026-04-25 (stable -- test infrastructure unlikely to change)
