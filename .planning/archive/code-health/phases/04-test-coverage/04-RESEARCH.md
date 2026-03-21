# Phase 04: Test Coverage - Research

**Researched:** 2026-03-21
**Domain:** Kotlin Multiplatform unit testing — coroutine-based repository and ScreenModel logic
**Confidence:** HIGH

## Summary

Phase 4 adds targeted unit tests for three high-risk, currently untested code paths: the offline-first action queue (ordering, conflicts, timeouts, rejections), FilterConfig combination logic, and the reading progress race condition around `serverProgressChecked`. All tests target the refactored modules from Phase 3.

The existing test infrastructure uses Docker-based **integration tests** in `composeApp/src/desktopTest/` with a real backend. Phase 4 needs **unit tests** that run fast (no Docker) by mocking DAOs and RemoteDataSource with MockK. The project already has `kotlinx-coroutines-test` and `mockk` as test dependencies.

**Primary recommendation:** Write pure unit tests in `desktopTest` using MockK for DAOs/RemoteDataSource, `runTest`/`StandardTestDispatcher` for coroutines, and Room in-memory DB only where DAO interaction ordering matters (TEST-01).

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
None -- all choices at Claude's discretion.

### Claude's Discretion
All implementation choices are at Claude's discretion -- pure infrastructure phase. Test framework, assertion style, mocking strategy, and test granularity are all at the implementer's discretion given the existing test patterns in the codebase.

### Deferred Ideas (OUT OF SCOPE)
None
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|-----------------|
| TEST-01 | Add tests for offline-first action queue (ordering, conflicts, timeouts, rejections) | Unit tests mock PendingActionDao + RemoteDataSource; verify action ordering via `createdAt`, conflict resolution (archive then unarchive), timeout via retryCount >= 5 deletion, server rejection via exception handling |
| TEST-02 | Add exhaustive FilterConfig combination tests | Pure data class tests -- enumerate FilterStatus x tags x lists x SortOption combinations; verify the multi-list case that previously crashed |
| TEST-03 | Add test for reading progress race (rapid UI changes + sync) | Test BookmarkViewerScreenModel `serverProgressChecked` flag ordering; verify that rapid `queueReadingProgressUpdate` calls deduplicate and that `_serverProgressChecked` is set only after pull completes |
</phase_requirements>

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| kotlin-test | (bundled with Kotlin) | Assertions | Already used in existing tests (`assertEquals`, `assertTrue`, etc.) |
| kotlinx-coroutines-test | (matches project coroutines version) | `runTest`, `StandardTestDispatcher`, `advanceUntilIdle` | Already used in all existing tests |
| MockK | (already in deps) | Mocking DAOs and RemoteDataSource | Already used in integration tests via `io.mockk` |
| JUnit 4 | (already in deps) | Test runner | Already used (`@Test` from `org.junit.Test`) |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| Room in-memory DB | (already available) | Real DAO behavior for ordering tests | TEST-01 when verifying action queue ordering with real SQL queries |

No new dependencies needed. Everything is already in the project.

## Architecture Patterns

### Recommended Test Structure
```
composeApp/src/desktopTest/kotlin/com/karakept/app/
├── data/
│   ├── integration/          # Existing Docker-based integration tests
│   ├── model/
│   │   └── FilterConfigTest.kt           # TEST-02
│   └── repository/
│       └── PendingActionQueueTest.kt      # TEST-01
├── ui/screens/
│   └── BookmarkViewerProgressTest.kt      # TEST-03
└── utils/
    └── BackupCryptoTest.kt                # Existing
```

### Pattern 1: Unit Test with MockK (for TEST-01, TEST-03)
**What:** Mock DAOs and RemoteDataSource, test repository/ScreenModel logic in isolation.
**When to use:** When testing business logic that calls DAOs and network.
**Example:**
```kotlin
class PendingActionQueueTest {
    private val pendingActionDao = mockk<PendingActionDao>(relaxed = true)
    private val bookmarkDao = mockk<BookmarkDao>(relaxed = true)
    private val remoteDataSource = mockk<RemoteDataSource>(relaxed = true)
    private val serverRepository = mockk<ServerRepository>()

    @Test
    fun actionsProcessedInCreationOrder() = runTest {
        // Arrange: two actions with different createdAt
        val actions = listOf(
            pendingAction(actionType = ARCHIVE, createdAt = 1000),
            pendingAction(actionType = FAVOURITE, createdAt = 2000),
        )
        coEvery { pendingActionDao.getPendingActionsList(any()) } returns actions
        // ... setup server mock

        // Act
        repository.processPendingActions(testServer)

        // Assert: verify call order
        coVerifyOrder {
            remoteDataSource.updateBookmark(any(), any(), match { it.archived == true })
            remoteDataSource.updateBookmark(any(), any(), match { it.favourited == true })
        }
    }
}
```

### Pattern 2: Pure Data Class Test (for TEST-02)
**What:** Test FilterConfig combinations without any mocking.
**When to use:** When testing data model logic and equality/serialization.
**Example:**
```kotlin
class FilterConfigTest {
    @Test
    fun multiListFilterDoesNotCrash() {
        val config = FilterConfig(
            status = FilterStatus.ALL_INCLUDING_ARCHIVED,
            lists = listOf("list-1", "list-2", "list-3"),
            tags = listOf("tag-a"),
            sort = SortOption.NEWEST
        )
        // Verify no exception, correct equality
        assertEquals(3, config.lists.size)
    }
}
```

### Pattern 3: ScreenModel Coroutine Test (for TEST-03)
**What:** Test ScreenModel state flows with `runTest` + `advanceUntilIdle`.
**When to use:** When testing state transitions in ViewModels/ScreenModels.
**Key insight:** The `serverProgressChecked` flag in BookmarkViewerScreenModel must only be set to `true` AFTER `pullReadingProgressFromServer` completes. The test must verify this ordering.

### Anti-Patterns to Avoid
- **Using Docker integration tests for unit-level concerns:** These tests should run in seconds, not minutes. Mock external dependencies.
- **Testing Compose UI rendering:** Phase 4 tests business logic only, not UI composition.
- **Testing the mocks:** Avoid tests that only verify mock setup rather than actual logic.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Coroutine test scheduling | Manual delay/launch orchestration | `runTest` + `advanceUntilIdle()` / `advanceTimeBy()` | Deterministic virtual time built into kotlinx-coroutines-test |
| Mock verification ordering | Manual call-tracking lists | MockK `coVerifyOrder` / `coVerifySequence` | Battle-tested, clear syntax |
| In-memory database | Fake DAO implementations | Room `Room.inMemoryDatabaseBuilder` | Already used in BaseDockerIntegrationTest; gets real SQL behavior |

## Common Pitfalls

### Pitfall 1: Extension Function Testing
**What goes wrong:** The Phase 3 refactoring extracted repository methods as extension functions (`fun BookmarkActionsRepository.processPendingActions(...)`). Testing extension functions requires having a real or carefully-mocked receiver instance.
**Why it happens:** Extension functions in Kotlin are static at the JVM level; you cannot mock them directly with MockK.
**How to avoid:** Construct a real `BookmarkActionsRepository` instance with mocked constructor dependencies (DAOs, RemoteDataSource, ServerRepository). The extension functions will execute against those mocks.
**Warning signs:** `MockKException: missing stub` on the repository itself.

### Pitfall 2: `runTest` Dispatcher Mismatch
**What goes wrong:** Code under test uses `Dispatchers.IO` or `Dispatchers.Default` which escapes `runTest`'s virtual time.
**Why it happens:** `processPendingActions` uses `withContext(Dispatchers.IO)`.
**How to avoid:** Inject the dispatcher or use `Dispatchers.setMain(StandardTestDispatcher())` in `@Before`. For `withContext(Dispatchers.IO)` calls, tests may need `UnconfinedTestDispatcher` or the code must accept an injected dispatcher.
**Warning signs:** Tests hang or have flaky timing.

### Pitfall 3: MockK `relaxed` vs Strict Mocks
**What goes wrong:** `relaxed = true` silently returns default values, hiding bugs where a method should not have been called.
**Why it happens:** Convenience over safety.
**How to avoid:** Use strict mocks for critical DAOs (pendingActionDao, bookmarkDao). Use `relaxed` only for auxiliary dependencies.

### Pitfall 4: FilterConfig Multi-List Crash
**What goes wrong:** The requirements mention a "previously-crashing multi-list case". This implies specific FilterConfig combinations with multiple list IDs caused issues.
**How to avoid:** The test must verify that `applyFilter` with multiple list IDs in `FilterConfig.lists` processes correctly. Need to check what the actual crash was -- likely in the pagination/query logic that consumes FilterConfig.
**Warning signs:** Tests pass on single-list but fail on multi-list.

## Code Examples

### Constructing BookmarkActionsRepository for Tests
The repository requires these dependencies (check constructor):
- `bookmarkDao` (internal)
- `pendingActionDao` (internal)
- `remoteDataSource` (internal)
- `serverRepository` (internal)
- `highlightDao` (internal, nullable)
- `actionMutex` (internal Mutex)
- `jsonSerializer` (internal Json instance)

Since Phase 3 changed visibility from `private` to `internal`, test code in the same package can access these directly.

### Key Test Scenarios for TEST-01

1. **Ordering:** Actions with `createdAt` 1, 2, 3 must execute in that order
2. **Conflict resolution:** Archive + Unarchive for same bookmark -- both execute, last wins
3. **Timeout/retry:** Action with `retryCount >= 5` gets deleted (line 349-352 in Sync file)
4. **Server rejection:** RemoteDataSource throws exception -- retryCount incremented, action kept

### Key Test Scenarios for TEST-02

FilterConfig is a simple data class with 4 fields. Exhaustive combinations:
- `FilterStatus`: 4 values (ALL, ALL_INCLUDING_ARCHIVED, FAVORITES, ARCHIVED)
- `tags`: empty, single, multiple
- `lists`: empty, single, **multiple** (the crash case)
- `sort`: 6 values

The "exhaustive" requirement likely means: test all FilterStatus values x list cardinalities (0, 1, N) x tag cardinalities (0, 1, N), plus verify serialization round-trip since FilterConfig is `@Serializable`.

### Key Test Scenarios for TEST-03

BookmarkViewerScreenModel has `_serverProgressChecked: MutableStateFlow<Boolean>`:
- Starts `false`
- Set to `true` only after `pullReadingProgressFromServer` completes (lines 307-327)
- Rapid `queueReadingProgressUpdate` calls should deduplicate (tested in integration tests but needs unit coverage)
- The race: UI changes progress locally while server pull is in-flight -- `serverProgressChecked` must gate scroll restoration

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 4 + kotlin-test + MockK + kotlinx-coroutines-test |
| Config file | `composeApp/build.gradle.kts` (desktopTest source set) |
| Quick run command | `./gradlew :composeApp:desktopTest --tests "com.karakept.app.data.model.FilterConfigTest"` |
| Full suite command | `./gradlew :composeApp:desktopTest` |

### Phase Requirements to Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| TEST-01 | Action queue ordering, conflicts, timeouts, rejections | unit | `./gradlew :composeApp:desktopTest --tests "*.PendingActionQueueTest"` | No -- Wave 0 |
| TEST-02 | FilterConfig exhaustive combinations | unit | `./gradlew :composeApp:desktopTest --tests "*.FilterConfigTest"` | No -- Wave 0 |
| TEST-03 | Reading progress race with serverProgressChecked | unit | `./gradlew :composeApp:desktopTest --tests "*.BookmarkViewerProgressTest"` | No -- Wave 0 |

### Sampling Rate
- **Per task commit:** Run the specific test class being added
- **Per wave merge:** `./gradlew :composeApp:desktopTest`
- **Phase gate:** Full suite green before `/gsd:verify-work`

### Wave 0 Gaps
- [ ] `composeApp/src/desktopTest/kotlin/com/karakept/app/data/repository/PendingActionQueueTest.kt` -- covers TEST-01
- [ ] `composeApp/src/desktopTest/kotlin/com/karakept/app/data/model/FilterConfigTest.kt` -- covers TEST-02
- [ ] `composeApp/src/desktopTest/kotlin/com/karakept/app/ui/screens/BookmarkViewerProgressTest.kt` -- covers TEST-03

## Open Questions

1. **What was the multi-list crash in FilterConfig?**
   - What we know: Requirements mention "previously-crashing multi-list case" in TEST-02
   - What's unclear: The exact crash scenario -- was it in pagination SQL, in the ScreenModel filter application, or in UI rendering?
   - Recommendation: Check git history for the fix, or test `applyFilter` with multi-list FilterConfig and verify the pagination query path. The planner should have the implementer investigate `MainScreenModelPagination.kt` with multi-list filters.

2. **BookmarkActionsRepository constructor access**
   - What we know: Phase 3 changed `private` to `internal` for extension function access
   - What's unclear: Whether the constructor itself is accessible for direct instantiation in tests, or if Koin is needed
   - Recommendation: Check constructor visibility during implementation. If Koin-only, create a test helper that builds the repository with mock dependencies.

3. **Dispatcher injection for `withContext(Dispatchers.IO)`**
   - What we know: `processPendingActions` and `executeAction` use `withContext(Dispatchers.IO)` hardcoded
   - What's unclear: Whether tests can override this without modifying production code
   - Recommendation: Use `Dispatchers.setMain()` does NOT affect `Dispatchers.IO`. May need to use `UnconfinedTestDispatcher` or accept that these tests will use real IO dispatcher (still fast since DAOs are mocked). Alternatively, the planner could add a dispatcher parameter.

## Sources

### Primary (HIGH confidence)
- Existing test files in `composeApp/src/desktopTest/` -- established patterns for runTest, MockK, Room in-memory DB
- `BookmarkActionsRepositorySync.kt` -- full source of action processing logic including retry/deletion at 5 retries
- `PendingActionEntity.kt` -- all action types and entity structure
- `FilterConfig.kt` -- simple data class, 4 fields, `@Serializable`
- `BookmarkViewerScreenModel.kt` -- `serverProgressChecked` state flow and race prevention logic
- `composeApp/build.gradle.kts` -- test dependencies confirmed: kotlinx-coroutines-test, mockk

### Secondary (MEDIUM confidence)
- `ReadingProgressIntegrationTest.kt` -- shows deduplication pattern for reading progress updates (integration-level)

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH - all libraries already in project, patterns established
- Architecture: HIGH - follows existing test structure, clear test targets
- Pitfalls: MEDIUM - dispatcher injection question needs validation during implementation

**Research date:** 2026-03-21
**Valid until:** 2026-04-21 (stable domain, no external dependency changes expected)
