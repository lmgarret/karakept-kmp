# Phase 14: UI Interaction Fixes - Research

**Researched:** 2026-03-27
**Domain:** Compose Multiplatform UI — pull-to-refresh, scroll-to-top, Material3 migration
**Confidence:** HIGH

## Summary

This phase addresses two UI interaction bugs (FILT-04 and UI-01) plus a deprecated API migration. The fixes are straightforward with clear root causes identified during context gathering. All three work items are code-only changes to existing composables with no new dependencies, no data layer changes, and no external service integration.

**FILT-04** (Highlights PTR): `HighlightsListContent.kt` lacks a `PullToRefreshBox` wrapper. The params (`onRefresh`, `isSyncing`) already flow through. The fix is wrapping the content in `PullToRefreshBox` following the exact pattern in `HighlightsScreen.kt:79-82`.

**UI-01** (Scroll-to-top): Two call sites use `animateScrollToItem(0)` without `scrollOffset = 0`: `MainScreen.kt:199` and `BookmarkViewerContent.kt:403`. The fix is adding the explicit `scrollOffset = 0` parameter.

**API migration** (D-06): `BookmarkListContent.kt` and `BookmarkViewerContent.kt` use deprecated `androidx.compose.material.pullrefresh` APIs. `MainScreen.kt` and `MainScreenScaffoldContent.kt` thread the deprecated `PullRefreshState` through. All four files need migration to MD3 `PullToRefreshBox`.

**Primary recommendation:** Fix the two bugs first, then migrate the deprecated PTR API in a separate task since the migration touches the same files and has a wider blast radius.

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- D-01: The bug is in `HighlightsListContent.kt` -- used by compact mobile layout in `MainScreen.kt:364`. The standalone `HighlightsScreen.kt` already has `PullToRefreshBox` but is never used on compact mobile.
- D-02: Add `PullToRefreshBox` inside `HighlightsListContent` itself (not at the call site). The `onRefresh` and `isSyncing` params already exist. Both compact and expanded layouts will benefit.
- D-03: Root cause: `animateScrollToItem(0)` in `MainScreen.kt:199` does not pass `scrollOffset = 0`, so the list may stop a few pixels short when the first item has internal scroll offset.
- D-04: Audit ALL scroll-to-top sites in the app (MainScreen, BookmarkViewerContent, and any others) and fix them all -- not just MainScreen.
- D-06: Migrate `BookmarkListContent.kt` from deprecated Material `pullRefresh` modifier to MD3 `PullToRefreshBox`, matching `HighlightsScreen.kt` pattern. After this phase, the entire app uses one PTR pattern.

### Claude's Discretion
- D-02: Where to add PullToRefreshBox (inside HighlightsListContent recommended)
- D-05: Whether to explicitly reset `savedScrollIndex`/`savedScrollOffset` in MainScreenModel on scroll-to-top, or let the existing `snapshotFlow` observer handle it naturally

### Deferred Ideas (OUT OF SCOPE)
None -- discussion stayed within phase scope
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| FILT-04 | Pull-to-refresh must work on Highlights on mobile compact layout | HighlightsListContent.kt already has `onRefresh`/`isSyncing` params; wrap content in `PullToRefreshBox` following HighlightsScreen.kt pattern |
| UI-01 | Scroll-to-top FAB must reach actual top (index 0, offset 0) across all scroll-to-top sites | Two sites found: MainScreen.kt:199 and BookmarkViewerContent.kt:403; both need `scrollOffset = 0` |
</phase_requirements>

## Architecture Patterns

### Pattern 1: MD3 PullToRefreshBox (target pattern)

**What:** MD3's `PullToRefreshBox` replaces the deprecated Material `pullRefresh` modifier + `PullRefreshIndicator` combo.
**When to use:** Every pull-to-refresh site in the app.
**Reference implementation:** `HighlightsScreen.kt:79-82`

```kotlin
// Source: composeApp/.../HighlightsScreen.kt:79-82
PullToRefreshBox(
    isRefreshing = isSyncing,
    onRefresh = { screenModel.syncHighlights() },
    modifier = Modifier.fillMaxSize().padding(paddingValues)
) {
    // content goes here (LazyColumn, Box, etc.)
}
```

**Key differences from deprecated API:**
- No separate `rememberPullRefreshState` — state is internal to `PullToRefreshBox`
- No separate `PullRefreshIndicator` — indicator is built in
- No `Modifier.pullRefresh(state)` — the Box handles the gesture
- Import: `androidx.compose.material3.pulltorefresh.PullToRefreshBox` (not `material.pullrefresh`)
- Requires `@OptIn(ExperimentalMaterial3Api::class)`

### Pattern 2: Scroll-to-top with explicit offset

**What:** `animateScrollToItem(index, scrollOffset)` with both parameters explicit.
**When to use:** Every scroll-to-top trigger.

```kotlin
// Correct:
scrollState.animateScrollToItem(index = 0, scrollOffset = 0)

// Bug (current code):
scrollState.animateScrollToItem(0)  // scrollOffset defaults to 0 but does not reset existing offset
```

**Why the default parameter is insufficient:** The `scrollOffset` parameter defaults to `0`, but when the first visible item already has a non-zero scroll offset from a previous position, `animateScrollToItem(0)` animates to item 0 but may preserve the sub-item pixel offset. Explicitly passing `scrollOffset = 0` forces a full reset.

### Anti-Patterns to Avoid
- **Mixing deprecated and MD3 pull-to-refresh:** After migration, no file should import from `androidx.compose.material.pullrefresh`.
- **Threading PullRefreshState through composable params:** With `PullToRefreshBox`, the state is internal. The parent only passes `isRefreshing: Boolean` and `onRefresh: () -> Unit`.

## Audit Results

### Scroll-to-top sites (complete audit)

| File | Line | Current Code | Fix Needed |
|------|------|-------------|------------|
| `MainScreen.kt` | 199 | `listState.animateScrollToItem(0)` | Add `scrollOffset = 0` |
| `BookmarkViewerContent.kt` | 403 | `scrollState.animateScrollToItem(0)` | Add `scrollOffset = 0` |
| `BookmarkViewerContent.kt` | 189 | `scrollState.animateScrollToItem(contentBodyIndex, offsetPx)` | No fix needed (already has explicit offset) |
| `ViewerScrollRestoration.kt` | 50, 66 | `scrollState.scrollToItem(index, offset)` | No fix needed (restoration, not scroll-to-top) |

**Total scroll-to-top sites needing fix: 2**

### Deprecated pull-to-refresh sites (complete audit)

| File | What it uses | Migration action |
|------|-------------|-----------------|
| `BookmarkListContent.kt` | `PullRefreshState` param, `Modifier.pullRefresh()`, `PullRefreshIndicator` | Replace with `PullToRefreshBox` wrapping content |
| `MainScreenScaffoldContent.kt` | `PullRefreshState` param, passes to `BookmarkListContent` | Remove `PullRefreshState` param, pass `isRefreshing`/`onRefresh` instead |
| `MainScreen.kt` | `rememberPullRefreshState()`, passes state to scaffold | Remove `rememberPullRefreshState()`, pass booleans/lambdas instead |
| `BookmarkViewerContent.kt` | `rememberPullRefreshState()`, `PullRefreshIndicator` | Replace with `PullToRefreshBox` wrapping viewer content |

**Total files needing PTR migration: 4** (plus `HighlightsListContent.kt` which gets a new `PullToRefreshBox` added)

### Deprecated import removal checklist

After migration, these imports must be gone from the entire codebase:
- `androidx.compose.material.pullrefresh.PullRefreshIndicator`
- `androidx.compose.material.pullrefresh.PullRefreshState`
- `androidx.compose.material.pullrefresh.pullRefresh`
- `androidx.compose.material.pullrefresh.rememberPullRefreshState`
- `androidx.compose.material.ExperimentalMaterialApi` (if only used for pullrefresh)

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Pull-to-refresh | Custom gesture + indicator | `PullToRefreshBox` from MD3 | Handles overscroll, indicator animation, nested scroll, accessibility |
| Scroll-to-top animation | Custom scroll logic | `animateScrollToItem(0, 0)` | Built-in smooth animation with proper fling handling |

## Common Pitfalls

### Pitfall 1: PullToRefreshBox must wrap scrollable content directly
**What goes wrong:** If `PullToRefreshBox` wraps a `Box` that contains a `LazyColumn` but the LazyColumn is not the direct child providing nested scroll, the pull gesture may not connect.
**Why it happens:** `PullToRefreshBox` relies on nested scroll connection from the child scrollable.
**How to avoid:** Ensure `PullToRefreshBox` wraps the scrollable composable (LazyColumn/Column) as directly as possible. In `HighlightsListContent`, wrap the content area inside the Scaffold's `paddingValues` lambda.
**Warning signs:** Pull gesture does not trigger refresh on device testing.

### Pitfall 2: BookmarkListContent migration changes the parameter signature
**What goes wrong:** `BookmarkListContent` currently takes `pullRefreshState: PullRefreshState` as a parameter. Migrating to `PullToRefreshBox` means the state is created internally, so this param must be removed and replaced with `isRefreshing: Boolean` + `onRefresh: () -> Unit`.
**Why it happens:** The deprecated API separates state from the indicator; the MD3 API bundles them.
**How to avoid:** Update all call sites (`MainScreenScaffoldContent`) simultaneously. The `isRefreshing`/`onRefresh` params already exist on `BookmarkListContent` (`isSyncing`, `onRefresh`).
**Warning signs:** Compile errors in `MainScreenScaffoldContent` and `MainScreen`.

### Pitfall 3: ExperimentalMaterialApi opt-in removal
**What goes wrong:** After removing deprecated pullrefresh, `@OptIn(ExperimentalMaterialApi::class)` may become unused, triggering lint warnings.
**Why it happens:** The opt-in was only needed for `pullRefresh` APIs.
**How to avoid:** Check whether any other Material (not Material3) experimental APIs are used in the file before removing the annotation.
**Warning signs:** Lint warnings about unnecessary opt-in.

### Pitfall 4: Scroll-to-top saved position not reset
**What goes wrong:** After `animateScrollToItem(0, 0)`, the `snapshotFlow` observer in `MainScreen.kt:155-160` fires and updates `savedScrollIndex=0, savedScrollOffset=0`. This happens naturally.
**Why it happens:** The `snapshotFlow` already observes `firstVisibleItemIndex` and `firstVisibleItemScrollOffset`.
**How to avoid:** No explicit reset of `savedScrollIndex`/`savedScrollOffset` is needed (D-05 resolution: let the observer handle it naturally). Verify in testing that after scroll-to-top, the saved values are (0, 0).

### Pitfall 5: Desktop-only pullRefresh exclusion
**What goes wrong:** `BookmarkListContent` currently conditionally applies `pullRefresh` only on non-desktop (line 159). The desktop check must be preserved in the `PullToRefreshBox` migration.
**Why it happens:** Desktop uses a refresh button, not pull gesture.
**How to avoid:** Conditionally wrap content in `PullToRefreshBox` only for non-desktop, or use `PullToRefreshBox` with `enabled = !isDesktop` if supported. Otherwise, keep the conditional wrapper pattern.

### Pitfall 6: Robolectric cannot reliably test PullToRefreshBox gestures
**What goes wrong:** Simulating pull-to-refresh gestures in Robolectric tests is unreliable.
**Why it happens:** Robolectric's touch event simulation does not fully support nested scroll and overscroll gestures.
**How to avoid:** Test the ScreenModel's sync behavior directly (call `syncHighlights()`, verify `isSyncing` transitions and repository calls). The existing `HighlightsPullToRefreshTest.kt` already follows this pattern.

## Code Examples

### FILT-04: Adding PullToRefreshBox to HighlightsListContent

```kotlin
// Inside HighlightsListContent, wrap the content area (inside Scaffold's padding lambda):
// Before:
if (highlights.isEmpty()) {
    // empty state
} else {
    LazyColumn(...)
}

// After:
PullToRefreshBox(
    isRefreshing = isSyncing,
    onRefresh = { onRefresh?.invoke() },
    modifier = Modifier.fillMaxSize()
) {
    if (highlights.isEmpty() && !isSyncing) {
        // empty state
    } else if (highlights.isEmpty()) {
        // loading state
    } else {
        LazyColumn(...)
    }
}
```

### UI-01: Fixing scroll-to-top

```kotlin
// MainScreen.kt:199 — before:
LaunchedEffect(Unit) { screenModel.scrollToTopTrigger.collect { listState.animateScrollToItem(0) } }

// After:
LaunchedEffect(Unit) { screenModel.scrollToTopTrigger.collect { listState.animateScrollToItem(0, 0) } }

// BookmarkViewerContent.kt:403 — before:
onClick = { scope.launch { scrollState.animateScrollToItem(0) } }

// After:
onClick = { scope.launch { scrollState.animateScrollToItem(0, 0) } }
```

### D-06: BookmarkListContent migration pattern

```kotlin
// Before (BookmarkListContent.kt):
// Params: pullRefreshState: PullRefreshState
Box(
    modifier = Modifier
        .fillMaxSize()
        .then(if (!isDesktop) Modifier.pullRefresh(pullRefreshState) else Modifier)
) {
    // ... LazyColumn and other content ...
    PullRefreshIndicator(refreshing = isSyncing, state = pullRefreshState, ...)
}

// After:
// Remove pullRefreshState param
// Wrap content differently for desktop vs mobile:
if (!isDesktop) {
    PullToRefreshBox(
        isRefreshing = isSyncing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize()
    ) {
        // LazyColumn and other content (no PullRefreshIndicator needed)
    }
} else {
    Box(modifier = Modifier.fillMaxSize()) {
        // same content without PTR
    }
}
```

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit4 + Robolectric + Compose UI Test |
| Config file | `composeApp/build.gradle.kts` (androidUnitTest dependencies) |
| Quick run command | `JAVA_HOME="/opt/homebrew/Cellar/openjdk@21/21.0.10/libexec/openjdk.jdk/Contents/Home" ANDROID_HOME="/opt/homebrew/share/android-commandlinetools" ./gradlew :composeApp:testDebugUnitTest --tests "com.karakept.app.ui.screens.*"` |
| Full suite command | `JAVA_HOME="/opt/homebrew/Cellar/openjdk@21/21.0.10/libexec/openjdk.jdk/Contents/Home" ANDROID_HOME="/opt/homebrew/share/android-commandlinetools" ./gradlew :composeApp:testDebugUnitTest` |

### Phase Requirements to Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| FILT-04 | syncHighlights() triggered by PTR on HighlightsListContent | unit (ScreenModel) | `./gradlew :composeApp:testDebugUnitTest --tests "com.karakept.app.ui.screens.HighlightsPullToRefreshTest"` | Existing (3 tests cover sync behavior) |
| UI-01 | animateScrollToItem(0, 0) reaches position 0/0 | unit (Compose UI) | `./gradlew :composeApp:testDebugUnitTest --tests "com.karakept.app.ui.screens.viewer.ScrollToTopVisibilityTest"` | Existing (visibility only, not position) |

### Sampling Rate
- **Per task commit:** Quick run of affected test classes
- **Per wave merge:** Full test suite
- **Phase gate:** Full suite green before `/gsd:verify-work`

### Wave 0 Gaps
- [ ] `composeApp/src/androidUnitTest/kotlin/com/karakept/app/ui/screens/ScrollToTopPositionTest.kt` -- new test verifying scroll-to-top reaches index=0, offset=0 (existing test only covers FAB visibility, not scroll position). Note: testing `animateScrollToItem` position directly in Robolectric Compose test requires a real `LazyColumn` with items; the existing `ScrollToTopVisibilityTest` already demonstrates this pattern.
- Existing `HighlightsPullToRefreshTest.kt` already covers FILT-04 at the ScreenModel level. No new test file needed for FILT-04 -- the PTR widget behavior cannot be reliably tested in Robolectric (Pitfall 6).

## Sources

### Primary (HIGH confidence)
- Direct code inspection of all referenced files in the repository
- `HighlightsScreen.kt` -- working `PullToRefreshBox` reference implementation
- `BookmarkListContent.kt` -- deprecated PTR implementation to migrate
- `MainScreen.kt` -- scroll-to-top trigger and PTR state creation
- `BookmarkViewerContent.kt` -- second scroll-to-top site and second deprecated PTR site

### Secondary (MEDIUM confidence)
- `animateScrollToItem` behavior with default `scrollOffset` parameter -- based on Compose `LazyListState` API contract. The explicit `scrollOffset = 0` is the documented way to ensure pixel-perfect scroll position.

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH -- no new dependencies, all MD3 APIs already used in codebase
- Architecture: HIGH -- reference implementation exists in codebase (`HighlightsScreen.kt`)
- Pitfalls: HIGH -- based on direct code analysis and established project test patterns

**Research date:** 2026-03-27
**Valid until:** 2026-04-27 (stable Compose APIs, no breaking changes expected)
