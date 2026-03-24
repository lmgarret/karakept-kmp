# Phase 07: UI Tests - Context

**Gathered:** 2026-03-24
**Status:** Ready for planning

<domain>
## Phase Boundary

Add Compose UI test infrastructure and write behavioral UI tests covering all v1.8.0 scenarios:
- FILT-01: Select-all beyond first page
- FILT-02: Quick filter counters in navigation drawer
- FILT-03: Pull-to-refresh on Highlights
- READER-03/04: Scroll-to-top button visibility and toggle
- SAVE-01/02: Bookmark saving back-navigation and fresh activity on second share

Additionally, add regression-like tests informed by recent bug fixes in git history.

This phase does NOT add new features, refactor production code, or add end-to-end flow tests.

</domain>

<decisions>
## Implementation Decisions

### Test runner & source set
- **D-01:** Use **Robolectric + compose-ui-test** (not instrumented on-device). Tests run on JVM — no emulator or connected device needed. Supported via `ui-test-junit4` + Robolectric.
- **D-02:** Tests live in a **new `androidUnitTest` source set** in the KMP module. This is the correct home for Robolectric tests in KMP — they require Android context but run on JVM. Cannot go in `commonTest` (no Android SDK) or `desktopTest` (wrong runtime).
- **D-03:** No `androidTest` (instrumented) source set is added in this phase.

### Scenario coverage
All v1.8.0 scenarios are in scope:
- **D-04:** **FILT-01 (select-all)** — Test that calling `selectAll()` when more than one page exists selects ALL items, not just the first 20. Use a fake repository returning 50 items.
- **D-05:** **FILT-02 (quick filter counters)** — Test that correct counts appear on All Bookmarks, Favorites, Archived, and Highlights drawer items when a known dataset is injected.
- **D-06:** **FILT-03 (pull-to-refresh Highlights)** — Test that performing a pull-to-refresh gesture on `HighlightsScreen` triggers the sync method.
- **D-07:** **READER-03/04 (scroll-to-top button)** — Test that the scroll-to-top button appears when scrolled past the hero, and that toggling the setting off hides it. Scroll state simulation is acknowledged as tricky — use `LazyListState` manipulation in tests.
- **D-08:** **SAVE-01/02 (bookmark saving navigation)** — Test back-navigation from reader to bookmark list after save, and that a second share intent creates a fresh activity state. Android Activity lifecycle behavior — may need Activity-level Robolectric setup.
- **D-09:** **Regression tests from bug history** — Researcher should inspect recent git commits (especially fixes landed in phases 03–06) and identify behaviors worth guarding with regression tests. These complement the scenario tests.

### Dependency isolation
- **D-10:** Use a **Koin test module** that replaces production bindings with fake implementations. Define `testModule` overriding repository and ScreenModel bindings per test class. Keeps tests fast and deterministic.
- **D-11:** Fakes should be minimal — implement just the interface methods exercised by the scenario under test. No need for full fake implementations upfront.

### Test depth
- **D-12:** Target **behavioral interactions** — not smoke checks, not full end-to-end flows. Tests verify that the actual bug fix behavior works: e.g., tap select-all → assert all 50 items selected, swipe down → assert `isSyncing` was set to true, toggle off → assert scroll-to-top button is not in composition.
- **D-13:** Avoid testing internal implementation details (private state, DAO calls). Assert observable UI state and user-facing outcomes.

### Claude's Discretion
- Exact Robolectric version and `ui-test-junit4` artifact coordinates to add to `libs.versions.toml`
- Whether to configure `testOptions { unitTests { isIncludeAndroidResources = true } }` in `build.gradle.kts` (needed for Robolectric resource loading)
- Specific `@Config` annotations per test class if needed
- File naming convention for test classes (e.g., `MainScreenSelectAllTest`, `HighlightsPullToRefreshTest`)
- Whether regression tests from git history get their own file or are co-located with scenario tests

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Scenarios under test
- `composeApp/src/commonMain/kotlin/com/karakept/app/ui/screens/MainScreenModelBatch.kt` — `selectAll()` implementation (FILT-01)
- `composeApp/src/commonMain/kotlin/com/karakept/app/ui/screens/MainScreenModel.kt` — `_accumulatedBookmarks`, `allBookmarks`, `_currentFilter`, `pageSize = 20`
- `composeApp/src/commonMain/kotlin/com/karakept/app/ui/screens/main/MainScreenDrawer.kt` — `DrawerContent`, `BuiltinDrawerItem` with counter (FILT-02)
- `composeApp/src/commonMain/kotlin/com/karakept/app/ui/screens/HighlightsScreen.kt` — pull-to-refresh layout (FILT-03)
- `composeApp/src/commonMain/kotlin/com/karakept/app/ui/screens/HighlightsScreenModel.kt` — `syncHighlights()`, `isSyncing` (FILT-03)
- `composeApp/src/commonMain/kotlin/com/karakept/app/ui/screens/BookmarkViewerScreen.kt` — scroll-to-top button, hero visibility logic (READER-03/04)
- `composeApp/src/androidMain/kotlin/com/karakept/app/BookmarkSavingActivity.kt` — back-navigation and fresh-activity behavior (SAVE-01/02)

### Existing test patterns (reference for consistency)
- `composeApp/src/commonTest/kotlin/com/karakept/app/ui/screens/PaginationUtilsTest.kt` — existing unit test style
- `composeApp/src/desktopTest/kotlin/com/karakept/app/ui/screens/BookmarkViewerProgressTest.kt` — existing desktop UI test style

### Build configuration
- `composeApp/build.gradle.kts` — current sourceSets, existing test deps (`kotlin.test`, `kotlinx-coroutines-test`, `mockk`); new Robolectric + compose-ui-test deps go here
- `gradle/libs.versions.toml` — version catalog; add Robolectric and ui-test-junit4 entries here

### Regression source
- `git log --oneline phases/03..HEAD` (or equivalent) — recent bug fix commits to identify regression test candidates

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `commonTest` — has `kotlin.test` + `kotlinx-coroutines-test` + `mockk` already; `androidUnitTest` can share mockk
- `desktopTest` — shows how a separate test source set is declared in `build.gradle.kts`; `androidUnitTest` follows same pattern
- Koin `startKoin` / `stopKoin` pattern used in existing integration tests — reusable for test module setup

### Established Patterns
- ScreenModels (Voyager) are thin wrappers over repositories — fake repositories are the right injection point, not full ScreenModel fakes
- `StateFlow` used everywhere for observable state — Compose UI tests can assert on emitted values via `collectAsState` or direct `awaitItem()`
- No existing Koin `testModule` — this phase creates the first one

### Integration Points
- New `androidUnitTest` source set declared in `kotlin { sourceSets { } }` block in `build.gradle.kts`
- `android { testOptions { unitTests { isIncludeAndroidResources = true } } }` likely needed for Robolectric
- `@RunWith(RobolectricTestRunner::class)` on each test class; `@Config(sdk = [33])` or similar

</code_context>

<specifics>
## Specific Ideas

- User explicitly asked for **regression-like tests based on recent bug fixes** — researcher should mine git log from phases 03–06 for behavioral regressions worth guarding (e.g., scroll position restore READER-01, state leak SAVE-02).
- Reader scroll-to-top button tests acknowledged as tricky — researcher should look into how `LazyListState` can be manipulated in Robolectric Compose tests (scrollToItem, etc.).
- SAVE-01/02 Activity lifecycle tests may need `ActivityScenario<BookmarkSavingActivity>` with Robolectric — researcher should confirm this works with KMP's Android target.

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope.

</deferred>

---

*Phase: 07-ui-tests*
*Context gathered: 2026-03-24*
