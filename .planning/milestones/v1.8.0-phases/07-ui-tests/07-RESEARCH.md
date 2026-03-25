# Phase 07: UI Tests - Research

**Researched:** 2026-03-24
**Domain:** Compose UI testing with Robolectric in Kotlin Multiplatform
**Confidence:** MEDIUM

## Summary

This phase introduces Compose UI test infrastructure to the Karakept KMP project using Robolectric + `compose-ui-test-junit4` in a new `androidUnitTest` source set. The project currently has no Android-specific unit tests and no Compose UI tests at all -- existing tests live in `commonTest` (pure Kotlin unit tests) and `desktopTest` (JVM-based integration tests with mockk).

The testing approach involves creating fake repository implementations that replace production Koin bindings, then rendering composables or exercising ScreenModels directly to verify behavioral correctness of v1.8.0 scenarios. The ScreenModel-centric approach (testing ScreenModel state transitions directly) is more reliable for most scenarios than full Compose rendering with Robolectric, since several scenarios (select-all, quick filter counts) are primarily state logic rather than visual behavior.

**Primary recommendation:** Use a hybrid approach -- test ScreenModel state directly with mockk fakes for FILT-01/02/03, and use Compose `createComposeRule()` with Robolectric only for tests that genuinely need UI composition (READER-03/04 scroll-to-top visibility, SAVE-01/02 Activity lifecycle). This minimizes Robolectric surface area while still exercising the UI layer where it matters.

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- D-01: Use Robolectric + compose-ui-test (not instrumented on-device). Tests run on JVM -- no emulator or connected device needed. Supported via ui-test-junit4 + Robolectric.
- D-02: Tests live in a new `androidUnitTest` source set in the KMP module.
- D-03: No `androidTest` (instrumented) source set is added in this phase.
- D-04: FILT-01 (select-all) -- Test that calling selectAll() when more than one page exists selects ALL items, not just the first 20. Use a fake repository returning 50 items.
- D-05: FILT-02 (quick filter counters) -- Test that correct counts appear on All Bookmarks, Favorites, Archived, and Highlights drawer items when a known dataset is injected.
- D-06: FILT-03 (pull-to-refresh Highlights) -- Test that performing a pull-to-refresh gesture on HighlightsScreen triggers the sync method.
- D-07: READER-03/04 (scroll-to-top button) -- Test that the scroll-to-top button appears when scrolled past the hero, and that toggling the setting off hides it.
- D-08: SAVE-01/02 (bookmark saving navigation) -- Test back-navigation from reader to bookmark list after save, and that a second share intent creates a fresh activity state.
- D-09: Regression tests from bug history -- Inspect recent git commits (phases 03-06) and identify behaviors worth guarding with regression tests.
- D-10: Use a Koin test module that replaces production bindings with fake implementations.
- D-11: Fakes should be minimal -- implement just the interface methods exercised by the scenario under test.
- D-12: Target behavioral interactions -- not smoke checks, not full end-to-end flows.
- D-13: Avoid testing internal implementation details. Assert observable UI state and user-facing outcomes.

### Claude's Discretion
- Exact Robolectric version and ui-test-junit4 artifact coordinates to add to libs.versions.toml
- Whether to configure testOptions { unitTests { isIncludeAndroidResources = true } } in build.gradle.kts
- Specific @Config annotations per test class if needed
- File naming convention for test classes
- Whether regression tests from git history get their own file or are co-located with scenario tests

### Deferred Ideas (OUT OF SCOPE)
None -- discussion stayed within phase scope.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| FILT-01 | Select-all selects all entries in the list, not just the first page | Test selectAll() on MainScreenModel with fake BookmarkRepository returning >20 items; verify _selectedBookmarkIds.value contains all IDs |
| FILT-02 | Quick Filters display bookmark counters in the navigation drawer | Test QuickFilterCounts computation via MainScreenModel.quickFilterCounts StateFlow with known dataset; optionally test DrawerContent composable rendering |
| FILT-03 | User can pull-to-refresh on the Highlights view | Test HighlightsScreenModel.syncHighlights() sets isSyncing and calls through to repository; Compose gesture test optional |
| READER-03 | Scroll-to-top button appears when scrolled past the hero | Test rememberScrollToTopVisibility composable or BookmarkViewerScreenModel.scrollToTopEnabled flow |
| READER-04 | User can toggle scroll-to-top button visibility in reader settings | Test that scrollToTopEnabled=false hides the button in composition |
| SAVE-01 | User can navigate back from reader to bookmark list after saving | Test BookmarkSavingActivity navigation with ActivityScenario + Robolectric |
| SAVE-02 | Second share intent creates fresh activity state | Test onNewIntent increments intentKey and recomposes with new URL |
</phase_requirements>

## Project Constraints (from CLAUDE.md)

- UI layer lives in `composeApp/src/commonMain/kotlin/com/karakept/app/ui/`
- Material Design 3 throughout -- use MD3 components in test setups (AppTheme wrapping)
- Voyager for navigation (ScreenModel, not ViewModel)
- Koin for dependency injection
- Existing test patterns: `kotlin.test` assertions, `kotlinx-coroutines-test`, `mockk` for mocking
- Existing tests use `@Before`/`@After` with `Dispatchers.setMain(testDispatcher)` pattern (see BookmarkViewerProgressTest)

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Robolectric | 4.14 | JVM Android runtime for tests | Supports SDK 35 (project targetSdk); 4.16 requires JDK 21 for SDK 36 but project compileSdk=36, so 4.14 for SDK 35 target is safest. JDK 25 available should be fine. |
| androidx.compose.ui:ui-test-junit4 | 1.8.2 (BOM-aligned) | Compose UI test rule and assertions | Standard Compose testing artifact; must match Compose Multiplatform 1.10.0 mapped AndroidX version |
| androidx.compose.ui:ui-test-manifest | 1.8.2 (BOM-aligned) | Test activity for createComposeRule | Required debug dependency for Robolectric Compose tests |
| androidx.test:core | 1.6.1 | AndroidX test core (ActivityScenario) | Required for SAVE-01/02 Activity lifecycle tests |

### Supporting (already in project)
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| mockk | 1.13.12 | Mocking repositories and DAOs | All test classes for fake/mock setup |
| kotlinx-coroutines-test | 1.9.0 | Test dispatcher, runTest, advanceUntilIdle | All ScreenModel tests |
| junit | 4.13.2 | Test runner annotations | @RunWith, @Before, @After, @Test |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Robolectric + ui-test-junit4 | compose.uiTest (CMP common) | compose.uiTest runs in commonTest but cannot access Android APIs (Activity, Intent) needed for SAVE-01/02 |
| mockk fakes | Koin test module with real impls | Heavier setup; existing codebase already uses mockk extensively |
| ActivityScenario (SAVE tests) | Robolectric ActivityController | ActivityScenario is the modern AndroidX API; ActivityController is Robolectric-internal |

### Version Compatibility Note

**CRITICAL:** Compose Multiplatform 1.10.0 maps to AndroidX Compose UI 1.8.x internally. The `ui-test-junit4` artifact version MUST be compatible with whatever Compose version the Compose Multiplatform Gradle plugin resolves. The safest approach is to let Compose Multiplatform manage the version by using `compose.uiTest` accessor in Gradle where possible, and only adding `ui-test-junit4-android` explicitly for the androidUnitTest source set.

The Compose Multiplatform plugin provides `compose.uiTest` which resolves to the correct platform-specific test artifact. For `androidUnitTest`, this may need to be supplemented with explicit Robolectric dependency.

**Recommended approach for build.gradle.kts:**
```kotlin
val androidUnitTest by getting {
    dependencies {
        implementation(compose.uiTest)  // Compose Multiplatform accessor
        implementation(libs.robolectric)
        implementation(libs.junit)
        implementation(libs.mockk)
        implementation(libs.kotlinx.coroutines.test)
        implementation(libs.androidx.test.core)
    }
}
```

**Installation (libs.versions.toml additions):**
```toml
[versions]
robolectric = "4.14"
androidx-test-core = "1.6.1"

[libraries]
robolectric = { module = "org.robolectric:robolectric", version.ref = "robolectric" }
androidx-test-core = { module = "androidx.test:core", version.ref = "androidx-test-core" }
```

## Architecture Patterns

### Recommended Test Source Structure
```
composeApp/src/androidUnitTest/
  kotlin/com/karakept/app/
    ui/screens/
      SelectAllTest.kt                    # FILT-01
      QuickFilterCountsTest.kt            # FILT-02
      HighlightsPullToRefreshTest.kt      # FILT-03
      ScrollToTopVisibilityTest.kt        # READER-03/04
      BookmarkSavingActivityTest.kt       # SAVE-01/02
      RegressionTest.kt                   # D-09 regression tests
```

### Pattern 1: ScreenModel-Centric Testing (Preferred for FILT-01/02/03)
**What:** Instantiate the ScreenModel directly with mockk dependencies, exercise its methods, assert on StateFlow emissions.
**When to use:** When the behavior under test is state logic, not visual composition. Most v1.8.0 scenarios fall into this category.
**Example:**
```kotlin
// Source: Existing project pattern from BookmarkViewerProgressTest.kt
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SelectAllTest {
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `selectAll fetches all items when hasMoreItems is true`() = runTest(testDispatcher) {
        // Setup: fake repository returns 50 items
        val fakeBookmarks = (1L..50L).map { createBookmarkEntity(remoteId = it) }
        coEvery { bookmarkRepository.getAllBookmarks(any(), any(), any()) } returns fakeBookmarks

        val screenModel = createMainScreenModel()
        screenModel._hasMoreItems.value = true
        screenModel._selectedServer.value = fakeServer
        screenModel._accumulatedBookmarks.value = fakeBookmarks.take(20)

        screenModel.selectAll()
        advanceUntilIdle()

        assertEquals(50, screenModel.selectedBookmarkIds.value.size)
    }
}
```

### Pattern 2: Compose UI Rule Testing (For READER-03/04)
**What:** Use `createComposeRule()` with Robolectric to render composables and assert on node tree.
**When to use:** When the behavior under test involves composition, visibility, or gesture interaction.
**Example:**
```kotlin
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ScrollToTopVisibilityTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `scroll-to-top button hidden when hero is visible`() {
        val scrollState = LazyListState(firstVisibleItemIndex = 0)

        composeTestRule.setContent {
            val visible = rememberScrollToTopVisibility(scrollState, fabVisible = true)
            if (visible) {
                Text("ScrollToTop", modifier = Modifier.testTag("scrollToTop"))
            }
        }

        composeTestRule.onNodeWithTag("scrollToTop").assertDoesNotExist()
    }
}
```

### Pattern 3: ActivityScenario Testing (For SAVE-01/02)
**What:** Use Robolectric's ActivityScenario to launch BookmarkSavingActivity with a share intent, verify lifecycle behavior.
**When to use:** Activity lifecycle tests (onNewIntent, back navigation).
**Example:**
```kotlin
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BookmarkSavingActivityTest {
    @Test
    fun `onNewIntent increments intentKey for fresh compose tree`() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "https://example.com/article1")
        }
        ActivityScenario.launch<BookmarkSavingActivity>(intent).use { scenario ->
            // First URL rendered
            val newIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, "https://example.com/article2")
            }
            scenario.onActivity { activity ->
                activity.onNewIntent(newIntent)  // Deprecated but functional in test
            }
            // Assert fresh state via Compose assertions
        }
    }
}
```

### Anti-Patterns to Avoid
- **Testing composable internals:** Do not assert on internal state variables or private composable functions. Assert on what the user sees (node presence, text content, click behavior).
- **Full navigation flow tests:** Do not set up Voyager Navigator in tests. Test individual screens or ScreenModels in isolation.
- **Database-level assertions:** Do not verify DAO calls unless the DAO is the explicit subject of the test (per D-13).
- **Over-mocking ScreenModels:** ScreenModels should be instantiated with mocked dependencies, not mocked themselves. This ensures production logic is exercised.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Compose UI assertions | Custom assertion helpers | `composeTestRule.onNodeWithText()`, `onNodeWithTag()`, `assertExists()` | Standard API, well-documented |
| Activity lifecycle driving | Manual lifecycle method calls | `ActivityScenario.launch()` from AndroidX Test | Handles all lifecycle callbacks correctly |
| Coroutine test timing | Thread.sleep or custom dispatchers | `runTest(testDispatcher)` + `advanceUntilIdle()` | Standard coroutines-test pattern already used in project |
| Fake state injection | Complex Koin module replacement | Direct mockk construction with `relaxed = true` | Project already uses this pattern (BookmarkViewerProgressTest) |

**Key insight:** The existing test patterns in this project (direct ScreenModel construction with mockk, StandardTestDispatcher, Dispatchers.setMain) are already well-established and should be extended to the new test source set rather than introducing a new testing paradigm.

## Common Pitfalls

### Pitfall 1: Compose Multiplatform Version Mismatch
**What goes wrong:** Using explicit `androidx.compose.ui:ui-test-junit4:X.Y.Z` that conflicts with the version Compose Multiplatform 1.10.0 resolves internally, causing NoSuchMethodError or ClassNotFoundException at test runtime.
**Why it happens:** Compose Multiplatform bundles its own fork/mapping of AndroidX Compose. Explicit AndroidX dependencies can clash.
**How to avoid:** Use `compose.uiTest` Gradle accessor first. If explicit version needed, check resolved dependencies with `./gradlew :composeApp:dependencies --configuration testDebugRuntimeClasspath`.
**Warning signs:** ClassNotFoundException for `androidx.compose.ui.test.*` classes, MethodNotFoundError in test execution.

### Pitfall 2: Robolectric SDK Level vs compileSdk
**What goes wrong:** Robolectric with `@Config(sdk = [36])` requires JDK 21+ and Robolectric 4.16+. Using sdk=36 with Robolectric 4.14 causes unsupported SDK errors.
**Why it happens:** This project has compileSdk=36 but targetSdk=35. Robolectric test SDK does not need to match compileSdk.
**How to avoid:** Use `@Config(sdk = [34])` or `@Config(sdk = [35])` which is well-supported by Robolectric 4.14. Create a `robolectric.properties` file for project-wide default.
**Warning signs:** `UnsupportedOperationException: Robolectric does not support API level 36`.

### Pitfall 3: MainScreenModel Constructor Complexity
**What goes wrong:** MainScreenModel has 8 constructor parameters all injected by Koin, plus it launches coroutines in init/stateIn blocks. Creating it in tests requires careful stubbing of all StateFlow dependencies to avoid NPEs during construction.
**Why it happens:** screenModelScope.launch calls in init and stateIn blocks execute during construction.
**How to avoid:** Follow the BookmarkViewerProgressTest pattern -- mock ALL repository flows with default stubs in @Before, use relaxed = true for mockk. Stub every `settingsRepository.*` flow that feeds a `stateIn`. Consider extracting a `createMainScreenModel()` helper shared across tests.
**Warning signs:** NPE in test setup, "Cannot invoke method on null" for StateFlow operations.

### Pitfall 4: screenModelScope Not Available Outside Voyager
**What goes wrong:** `screenModelScope` is provided by Voyager's ScreenModel lifecycle. In tests, it requires the Main dispatcher to be set.
**Why it happens:** Voyager ScreenModel's scope uses `Dispatchers.Main.immediate` by default.
**How to avoid:** Always call `Dispatchers.setMain(testDispatcher)` in @Before and `Dispatchers.resetMain()` in @After. This is already the established pattern (BookmarkViewerProgressTest).
**Warning signs:** "Module with the Main dispatcher is missing" exception.

### Pitfall 5: Koin Global State Leaking Between Tests
**What goes wrong:** If any test starts Koin (e.g., for Activity tests), global Koin state leaks to subsequent tests causing "A Koin Application has already been started" error.
**Why it happens:** Koin is a global singleton. BookmarkSavingActivity or compose content may call `koinInject()`.
**How to avoid:** For Activity tests, use `KoinApplication { modules(testModule) }` in test setup and `stopKoin()` in @After. For ScreenModel-only tests, avoid Koin entirely -- construct ScreenModels directly with mockk.
**Warning signs:** KoinApplicationAlreadyStartedException, DI resolution failures in second test.

### Pitfall 6: PullToRefreshBox Gesture Simulation
**What goes wrong:** Attempting to simulate pull-to-refresh in Compose test by swiping down may not trigger the `onRefresh` callback reliably in Robolectric.
**Why it happens:** PullToRefreshBox gesture detection depends on touch event timing and overscroll behavior that Robolectric may not fully simulate.
**How to avoid:** Test pull-to-refresh at the ScreenModel level -- call `syncHighlights()` directly and assert `isSyncing` transitions. This tests the behavioral contract (D-12) without depending on gesture fidelity.
**Warning signs:** Swipe gesture test passes locally but fails in CI, or onRefresh never fires.

### Pitfall 7: LazyListState Manipulation in Tests
**What goes wrong:** Creating a `LazyListState(firstVisibleItemIndex = 5)` in tests does not actually populate `layoutInfo` with visible items, so `derivedStateOf` blocks reading `layoutInfo.visibleItemsInfo` return empty results.
**Why it happens:** LazyListState's scroll position is only meaningful within a composed LazyColumn that has measured its layout.
**How to avoid:** For scroll-to-top tests, render the actual composable with enough items in a LazyColumn, then use `composeTestRule.runOnIdle { scrollState.scrollToItem(5) }` to scroll programmatically.
**Warning signs:** `visibleItemsInfo` is empty, `totalItemsCount` is 0, assertions about scroll position always fail.

## Code Examples

### Verified: ScreenModel Construction with Mockk (from existing project)
```kotlin
// Source: composeApp/src/desktopTest/kotlin/.../BookmarkViewerProgressTest.kt
// This is the established pattern in the project

private val testDispatcher = StandardTestDispatcher()

@Before
fun setUp() {
    Dispatchers.setMain(testDispatcher)
    bookmarkDao = mockk(relaxed = true)
    settingsRepository = mockk(relaxed = true)
    // Must stub ALL flows consumed by stateIn in constructor
    every { settingsRepository.viewerMode } returns flowOf(ViewerMode.READER)
    every { settingsRepository.hideArticleThumbnails } returns flowOf(true)
    // ... all other settings flows
}
```

### Verified: MainScreenModel selectAll() Implementation
```kotlin
// Source: composeApp/src/commonMain/kotlin/.../MainScreenModelBatch.kt
// selectAll() checks _hasMoreItems, then fetches all from DB, applies filters, updates state
fun MainScreenModel.selectAll() {
    if (!_hasMoreItems.value) {
        _selectedBookmarkIds.value = _accumulatedBookmarks.value.map { it.remoteId }.toSet()
        return
    }
    screenModelScope.launch {
        val allEntities = bookmarkRepository.getAllBookmarks(server, filter.status, singleListId)
        val filtered = BookmarkFilterUtils.applyClientSideFilters(allEntities, filter, ...)
        updateAccumulatedBookmarks { sorted }
        _hasMoreItems.value = false
        _selectedBookmarkIds.value = sorted.map { it.remoteId }.toSet()
    }
}
```

### Verified: QuickFilterCounts Derivation
```kotlin
// Source: composeApp/src/commonMain/kotlin/.../MainScreenModel.kt
val quickFilterCounts: StateFlow<QuickFilterCounts> = combine(
    selectedServer, allBookmarks
) { server, bookmarks ->
    if (server == null) return@combine QuickFilterCounts()
    QuickFilterCounts(
        all = bookmarks.count { !it.isArchived },
        favorites = bookmarks.count { it.isStarred && !it.isArchived },
        archived = bookmarks.count { it.isArchived }
    )
}.stateIn(screenModelScope, SharingStarted.WhileSubscribed(5000), QuickFilterCounts())
```

### Verified: BookmarkSavingActivity onNewIntent Pattern
```kotlin
// Source: composeApp/src/androidMain/kotlin/.../BookmarkSavingActivity.kt
override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    sharedUrl = extractUrlFromIntent(intent)
    intentKey++  // Forces key(intentKey) recomposition -> fresh Navigator
}
```

### Build Configuration Required
```kotlin
// Source: Robolectric official docs + KMP Bits article
android {
    testOptions {
        unitTests {
            isIncludeAndroidResources = true  // Required for Robolectric resource loading
        }
    }
}
```

## Regression Test Candidates (D-09)

Analysis of git log from phases 03-06 reveals these bug fixes worth guarding:

| Commit | Bug Fixed | Regression Test |
|--------|-----------|-----------------|
| `5d4d2aa` fix(03-01) | List scroll position not restored after reader back navigation | Test that `savedScrollIndex`/`savedScrollOffset` persist through ScreenModel lifecycle |
| `fef4624` feat(03-02) | Scroll-to-top button + conditional Details menu | Covered by READER-03/04 tests |
| `58025ed` fix(05-01) | Conditional optimistic removal in removeBookmarkFromList | Already covered by RemoveBookmarkFromListTest |
| `2ed7afb` fix(06-01) | selectAll() only selected first page | Covered by FILT-01 tests |
| `1d82cd8` feat(06-02) | Quick filter counters in drawer | Covered by FILT-02 tests |
| `dc0f4db` feat(06-02) | Pull-to-refresh on Highlights | Covered by FILT-03 tests |
| `97f6ef6` fix | coerceIn crash when window narrower than min column widths | Edge case: test that narrow viewport does not crash (LOW priority) |

**Recommendation:** Most regressions from phases 03-06 are already covered by the scenario tests. The only additional regression test worth adding is for **scroll position persistence** (savedScrollIndex/savedScrollOffset on MainScreenModel), which is a simple state test that guards READER-01 without duplicating existing tests.

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `createAndroidComposeRule<Activity>()` | `createComposeRule()` (no Activity) | Compose 1.3+ | Simpler test setup, works with Robolectric |
| Espresso for Compose | Compose UI Test APIs | Compose 1.0 | Native Compose testing, no Espresso dependency |
| `@RunWith(AndroidJUnit4::class)` | `@RunWith(RobolectricTestRunner::class)` | Robolectric 4.x | Explicit Robolectric runner for JVM tests |
| Manual Activity lifecycle calls | ActivityScenario API | AndroidX Test 1.2+ | Standardized lifecycle control |

**Deprecated/outdated:**
- `ComposeTestRule` from `compose-test-junit4` (old package) -- use `compose.uiTest` or `ui-test-junit4-android`
- `Dispatchers.setMain(TestCoroutineDispatcher())` -- use `StandardTestDispatcher()` (kotlinx-coroutines-test 1.6+)

## Open Questions

1. **Compose Multiplatform 1.10.0 ui-test version mapping**
   - What we know: CMP 1.10.0 maps to some AndroidX Compose version internally. The `compose.uiTest` accessor should resolve the correct test artifact.
   - What's unclear: Whether `compose.uiTest` works correctly in `androidUnitTest` source set (most examples show it in `commonTest`). May need explicit `ui-test-junit4-android` artifact.
   - Recommendation: Try `compose.uiTest` first. If it fails to resolve for androidUnitTest, fall back to explicit `androidx.compose.ui:ui-test-junit4-android` with version from resolved dependencies. Document the resolution in Wave 0.

2. **Robolectric + Koin Integration for Activity Tests**
   - What we know: BookmarkSavingActivity's `BookmarkSavingContent` uses `koinInject<SettingsRepository>()`. Activity tests need Koin running.
   - What's unclear: Whether Robolectric Application class automatically initializes Koin, or if tests need manual Koin setup.
   - Recommendation: Create a minimal `testModule` with mockk fakes for SettingsRepository. Start Koin in @Before with `startKoin { modules(testModule) }` and stop in @After.

3. **Robolectric 4.14 vs 4.16 with JDK 25**
   - What we know: JDK 25 is installed. Robolectric 4.16 supports SDK 36 with JDK 21+. Robolectric 4.14 supports SDK 35.
   - What's unclear: Whether Robolectric 4.14 works correctly on JDK 25 (released after Robolectric 4.14).
   - Recommendation: Start with Robolectric 4.14 + `@Config(sdk = [34])`. If JDK compatibility issues arise, upgrade to 4.16.

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| JDK | Robolectric runtime | Yes | 25.0.2 | -- |
| Gradle | Build system | Yes | 8.13 | -- |
| Android SDK (compileSdk 36) | Robolectric shadows | Yes (via AGP) | 36 | -- |

**Missing dependencies with no fallback:** None -- all required tools are available.

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 4.13.2 + Robolectric 4.14 + compose-ui-test |
| Config file | `composeApp/build.gradle.kts` (androidUnitTest source set) |
| Quick run command | `./gradlew :composeApp:testDebugUnitTest --tests "com.karakept.app.ui.screens.*"` |
| Full suite command | `./gradlew :composeApp:testDebugUnitTest` |

### Phase Requirements to Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| FILT-01 | selectAll selects all items beyond first page | unit (ScreenModel) | `./gradlew :composeApp:testDebugUnitTest --tests "*.SelectAllTest" -x` | No -- Wave 0 |
| FILT-02 | Quick filter counters show correct values | unit (ScreenModel) | `./gradlew :composeApp:testDebugUnitTest --tests "*.QuickFilterCountsTest" -x` | No -- Wave 0 |
| FILT-03 | Pull-to-refresh triggers sync on Highlights | unit (ScreenModel) | `./gradlew :composeApp:testDebugUnitTest --tests "*.HighlightsPullToRefreshTest" -x` | No -- Wave 0 |
| READER-03 | Scroll-to-top button appears when scrolled past hero | UI (Compose rule) | `./gradlew :composeApp:testDebugUnitTest --tests "*.ScrollToTopVisibilityTest" -x` | No -- Wave 0 |
| READER-04 | Toggle hides scroll-to-top button | UI (Compose rule) | `./gradlew :composeApp:testDebugUnitTest --tests "*.ScrollToTopVisibilityTest" -x` | No -- Wave 0 |
| SAVE-01 | Back navigation from reader to list after save | UI (Activity) | `./gradlew :composeApp:testDebugUnitTest --tests "*.BookmarkSavingActivityTest" -x` | No -- Wave 0 |
| SAVE-02 | Second share creates fresh activity state | UI (Activity) | `./gradlew :composeApp:testDebugUnitTest --tests "*.BookmarkSavingActivityTest" -x` | No -- Wave 0 |

### Sampling Rate
- **Per task commit:** `./gradlew :composeApp:testDebugUnitTest --tests "com.karakept.app.ui.screens.*"`
- **Per wave merge:** `./gradlew :composeApp:testDebugUnitTest`
- **Phase gate:** Full suite green before `/gsd:verify-work`

### Wave 0 Gaps
- [ ] `composeApp/src/androidUnitTest/kotlin/com/karakept/app/ui/screens/` -- directory structure
- [ ] `libs.versions.toml` -- robolectric, androidx-test-core entries
- [ ] `build.gradle.kts` -- androidUnitTest source set with dependencies, testOptions config
- [ ] `composeApp/src/androidUnitTest/resources/robolectric.properties` -- default SDK config
- [ ] All 6 test files listed in Architecture Patterns section

## Sources

### Primary (HIGH confidence)
- Project source code: `composeApp/build.gradle.kts`, `gradle/libs.versions.toml`, `AppModule.kt`, existing test files
- Existing test patterns: `BookmarkViewerProgressTest.kt`, `RemoveBookmarkFromListTest.kt`, `PaginationUtilsTest.kt`
- Production code under test: `MainScreenModelBatch.kt`, `MainScreenModel.kt`, `HighlightsScreenModel.kt`, `BookmarkSavingActivity.kt`, `ViewerScrollBehavior.kt`

### Secondary (MEDIUM confidence)
- [KMP Bits - Robolectric Compose Testing](https://www.kmpbits.com/posts/robolectric-compose/) - Detailed setup guide
- [Kotlin Multiplatform Compose Test Docs](https://kotlinlang.org/docs/multiplatform/compose-test.html) - Official CMP testing guidance
- [Robolectric Releases](https://github.com/robolectric/robolectric/releases/) - Version 4.14 for SDK 35, 4.16 for SDK 36
- [Robolectric AndroidX Test](https://robolectric.org/androidx_test/) - ActivityScenario documentation

### Tertiary (LOW confidence)
- Compose Multiplatform 1.10.0 to AndroidX Compose version mapping -- not verified against official mapping table. May need empirical verification during Wave 0.

## Metadata

**Confidence breakdown:**
- Standard stack: MEDIUM - Robolectric version choice solid, but compose.uiTest resolution in androidUnitTest needs empirical verification
- Architecture: HIGH - Test patterns directly extend existing project conventions
- Pitfalls: HIGH - Identified from project codebase analysis and established Robolectric documentation

**Research date:** 2026-03-24
**Valid until:** 2026-04-24 (stable domain, 30-day validity)
