# Phase 03: Reader UX - Research

**Researched:** 2026-03-23
**Domain:** Compose Multiplatform reader view UX (scroll state, overlay buttons, settings persistence)
**Confidence:** HIGH

## Summary

This phase addresses four reader UX requirements: restoring bookmark list scroll position on reader close (bug fix), moving the info button to the overflow menu when hero scrolls away, adding a scroll-to-top button, and persisting a toggle for that button. All four requirements operate within the existing `BookmarkViewerContent.kt` / `ViewerTopBar.kt` / `ViewerScrollBehavior.kt` ecosystem and the `SettingsRepository` category-based storage pattern.

The codebase already has all the building blocks needed: `LazyListState` scroll detection via `firstVisibleItemIndex`, FAB visibility logic based on scroll direction (`rememberFabVisibilityState`), `AnimatedVisibility` for fade in/out, the `ReaderAppearanceBottomPanel` with tabbed settings, and the `StoredReaderSettings` serializable data class with `updateReaderSettings` mutation pattern. No new libraries or external dependencies are required.

**Primary recommendation:** Implement all four requirements as incremental modifications to existing files, following established patterns exactly. The READER-01 bug fix requires investigation of the Voyager screen lifecycle and `rememberSaveable` behavior.

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- READER-01: Fix scroll position restore on reader close. `rememberSaveable(key = "main_screen_list_state", saver = LazyListState.Saver)` exists but scroll resets to top (bug #152). Root cause unknown, needs investigation.
- READER-02: Info button moves to overflow menu when hero scrolls out of view. Mobile: conditional "Details" item in `ViewerTopBar` dropdown. Desktop: always visible inline.
- READER-03: Scroll-to-top button. Bottom-left, small button (not full FAB). Visible only past hero, on scroll-up (same logic as FAB), always visible at end of article. Smooth fade + animated scroll.
- READER-04: Toggle in `ReaderAppearanceBottomPanel`. Label "Scroll-to-top button", default ON, stored in `SettingsRepository` via `StoredReaderSettings`.

### Claude's Discretion
- None explicitly listed.

### Deferred Ideas (OUT OF SCOPE)
- Phase 07 (UI Tests) will cover Compose UI/instrumented tests for all v1.8.0 scenarios including reader UX.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| READER-01 | Closing the reader restores bookmark list scroll position (#152) | Voyager lifecycle analysis; `rememberSaveable` + `LazyListState.Saver` pattern; `MainScreen` is a singleton `object Screen` with `koinScreenModel` |
| READER-02 | Info button moves to overflow menu when hero scrolls away (#160) | `firstVisibleItemIndex` already tracked in `scrollState`; `ViewerTopBar` already has `DropdownMenu`; hero is item index 0 |
| READER-03 | Scroll-to-top button in reader (#161) | `rememberFabVisibilityState` pattern for scroll-direction detection; `AnimatedVisibility` with fade; `LazyListState.animateScrollToItem(0)` |
| READER-04 | Toggle scroll-to-top in reader settings (#161) | `StoredReaderSettings` + `updateReaderSettings` pattern; `ReaderAppearanceBottomPanel` has tabs; `BookmarkViewerScreenModel` exposes `StateFlow` from settings |
</phase_requirements>

## Architecture Patterns

### Relevant Project Structure
```
composeApp/src/commonMain/kotlin/com/karakept/app/
  ui/screens/
    MainScreen.kt                    # Bookmark list (READER-01 target)
    BookmarkViewerContent.kt         # Reader layout (READER-02, READER-03 target)
    BookmarkViewerScreenModel.kt     # ScreenModel exposing settings as StateFlow
    ReaderAppearanceScreen.kt        # Appearance preview screen
    viewer/
      ViewerTopBar.kt                # Overflow menu (READER-02 target)
      ViewerScrollBehavior.kt        # FAB visibility, reading progress, sticky title
      ViewerScrollRestoration.kt     # Scroll guard and progress restore
      HeroBannerSection.kt           # Hero section wrapper
  components/
    HeroImageBanner.kt               # Info button lives here
    ReaderAppearanceBottomPanel.kt   # Settings panel (READER-04 target)
    BaseBottomPanel.kt               # Panel animation base
  data/repository/
    SettingsRepository.kt            # Flows for all settings
    SettingsRepositoryMutations.kt   # Setter extension functions
    StoredSettings.kt                # Serializable data classes
  data/model/
    BackupSettings.kt                # Backup file format
```

### Pattern 1: Settings Storage (for READER-04)
**What:** New boolean settings follow a 3-file pattern.
**When to use:** Adding `scrollToTopEnabled` to reader settings.
**Steps:**
1. Add field to `StoredReaderSettings` in `StoredSettings.kt` (with default value)
2. Add derived `Flow` in `SettingsRepository.kt` (from `readerSettingsFlow`)
3. Add setter extension in `SettingsRepositoryMutations.kt` (using `updateReaderSettings`)
4. Add field to `BackupSettings` (backup format) for backup/restore support
5. Map the field in `currentSettings()` and `restoreSettings()` in `SettingsRepositoryMutations.kt`
6. Expose as `StateFlow` in `BookmarkViewerScreenModel`
7. Add serialization test in `StoredSettingsSerializationTest.kt`

**Reference implementation:** `showTagsInViewer` follows this exact pattern:
```kotlin
// StoredSettings.kt
@Serializable
internal data class StoredReaderSettings(
    // ... existing fields ...
    val showTagsInViewer: Boolean = true
)

// SettingsRepository.kt
val showTagsInViewer: Flow<Boolean> =
    readerSettingsFlow.map { it.showTagsInViewer }.distinctUntilChanged()

// SettingsRepositoryMutations.kt
suspend fun SettingsRepository.setShowTagsInViewer(show: Boolean) =
    updateReaderSettings { copy(showTagsInViewer = show) }
```

### Pattern 2: Scroll-Direction Button Visibility (for READER-03)
**What:** Track scroll direction to show/hide a UI element.
**When to use:** The scroll-to-top button reuses the same scroll-up detection as the FAB.
**Existing implementation in `ViewerScrollBehavior.kt`:**
```kotlin
@Composable
internal fun rememberFabVisibilityState(
    scrollState: LazyListState,
    fabExpanded: Boolean
): Boolean {
    var previousScrollOffset by remember { mutableStateOf(0) }
    var fabVisible by remember { mutableStateOf(true) }
    LaunchedEffect(scrollState.firstVisibleItemScrollOffset, scrollState.firstVisibleItemIndex) {
        val currentOffset = scrollState.firstVisibleItemIndex * 1000 + scrollState.firstVisibleItemScrollOffset
        val scrollingDown = currentOffset > previousScrollOffset
        if (currentOffset > 100) {
            fabVisible = !scrollingDown || fabExpanded
        } else {
            fabVisible = true
        }
        previousScrollOffset = currentOffset
    }
    return fabVisible
}
```

### Pattern 3: Conditional Overflow Menu Item (for READER-02)
**What:** Add a menu item that appears/disappears based on scroll state.
**When to use:** "Details" item in `ViewerTopBar` when hero is scrolled away.
**Key logic:** `scrollState.firstVisibleItemIndex > 0` means hero is off-screen.
**ViewerTopBar already receives hero-visibility state indirectly through `showStickyTitle`** -- but this threshold is based on banner overlap, not exact hero disappearance. Need to pass a new `isHeroVisible` boolean or derive from `firstVisibleItemIndex`.

### Anti-Patterns to Avoid
- **Do NOT create a new composable file for the scroll-to-top button:** It should be an overlay inside `BookmarkViewerContent.kt`'s existing `Box`, positioned alongside the FAB.
- **Do NOT use a separate DataStore key for `scrollToTopEnabled`:** Must go in the `StoredReaderSettings` category blob, following the established pattern.
- **Do NOT use `ScrollToTop` icon from extended icons:** Use `Icons.Default.KeyboardArrowUp` or `Icons.Default.ArrowUpward` which are in the standard material-icons set already imported.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Scroll direction detection | Custom scroll listener | Extend `rememberFabVisibilityState` or create parallel composable | Pattern already handles edge cases (threshold, expanded state) |
| Settings persistence | New DataStore key | `StoredReaderSettings` + `updateReaderSettings` | Category-based storage with migration, backup, and distinctUntilChanged already handled |
| Bottom panel tabs | New tab implementation | Modify existing `ReaderAppearanceBottomPanel` tab count | Consistent UX, shared animation and dismissal logic |
| Animated visibility | Manual alpha animation | `AnimatedVisibility` with `fadeIn()` / `fadeOut()` | Already used for FAB in `BookmarkViewerContent.kt` |

## Common Pitfalls

### Pitfall 1: READER-01 Scroll Restore -- Voyager Recomposition
**What goes wrong:** `rememberSaveable` with `LazyListState.Saver` exists but scroll resets on `navigator.pop()`.
**Why it happens:** Multiple possible root causes:
1. **Voyager `object Screen` lifecycle:** `MainScreen` is a Kotlin `object` (singleton). When Voyager navigates away and back, the composable `Content()` may be fully re-composed. If the `rememberSaveable` key is invalidated or the saved state registry is cleared during navigation, the `LazyListState` resets.
2. **Key invalidation:** The `key = "main_screen_list_state"` is static, which should survive recomposition. But if the `SaveableStateRegistry` is tied to the screen lifecycle and gets cleared, the state is lost.
3. **Data-driven recomposition:** When `bookmarks` or `bookmarkListVersion` state changes while the viewer is open (e.g., background sync), the `LazyColumn` may recompose with new data and reset scroll position even though the `LazyListState` was saved.
**How to investigate:**
- Add logging to track when `LazyListState` is created vs restored
- Check if `rememberSaveable` returns a fresh instance after `pop()`
- Test if the bug occurs when `bookmarks` list doesn't change during viewer navigation
**Likely fix approaches (ordered by probability):**
- If the state IS saved but the list recomposes: the issue is the `LazyColumn` recomposing due to data changes. Fix by ensuring stable item keys.
- If the state is NOT saved: may need to hoist the scroll state to `MainScreenModel` (the singleton) and restore manually.
**Warning signs:** The bug may be intermittent or timing-dependent if related to background sync.

### Pitfall 2: READER-02 Hero Visibility vs Sticky Title
**What goes wrong:** Using `showStickyTitle` as proxy for "hero is off-screen" could show "Details" menu item too early or too late.
**Why it happens:** `showStickyTitle` triggers when scroll offset exceeds `bannerHeightPx - toolbarHeightPx - statusBarInsets`, which is when the toolbar background starts showing. Hero is truly off-screen when `firstVisibleItemIndex > 0`.
**How to avoid:** Use `scrollState.firstVisibleItemIndex > 0` directly for the "Details" menu item visibility, not `showStickyTitle`. This is simpler and exactly matches "hero scrolled out of view."

### Pitfall 3: READER-03 Button Placement Conflict with FAB
**What goes wrong:** Scroll-to-top button and FAB menu could overlap or create visual clutter.
**Why it happens:** FAB is bottom-right, scroll-to-top is bottom-left. Both use scroll-direction visibility logic. If both animate simultaneously, it could look jarring.
**How to avoid:** Scroll-to-top button should be clearly smaller than FAB. Use `SmallFloatingActionButton` or a custom `IconButton` with `surfaceContainerLow` background. Align to `Alignment.BottomStart` with padding. Share the same animation timing (300ms tween) for visual consistency.

### Pitfall 4: READER-03 End-of-Article Detection
**What goes wrong:** End-of-article "always visible" rule might not trigger correctly.
**Why it happens:** `LazyListState.layoutInfo.visibleItemsInfo` may not reliably contain the last item for very short content or when the last item is partially visible.
**How to avoid:** Check `layoutInfo.visibleItemsInfo.lastOrNull()?.index == layoutInfo.totalItemsCount - 1`. This is true when the last item is visible (even partially).

### Pitfall 5: READER-04 BackupSettings Sync
**What goes wrong:** New setting not included in backup/restore.
**Why it happens:** The `BackupSettings` model and `currentSettings()` / `restoreSettings()` must also be updated. Easy to forget.
**How to avoid:** The 3-file pattern is actually 5+ files. Checklist: `StoredSettings.kt`, `SettingsRepository.kt`, `SettingsRepositoryMutations.kt`, `BackupSettings.kt`, `currentSettings()`, `restoreSettings()`, `BookmarkViewerScreenModel.kt`, `StoredSettingsSerializationTest.kt`.

## Code Examples

### Adding "Details" to ViewerTopBar overflow menu (READER-02)
```kotlin
// In ViewerTopBar.kt - add parameter:
internal fun ViewerTopBar(
    // ... existing params ...
    isHeroVisible: Boolean = true,       // NEW
    onDetailsClick: (() -> Unit)? = null, // NEW
) {
    // Inside DropdownMenu, before "Reader Appearance":
    if (!isDesktop && !isHeroVisible && onDetailsClick != null) {
        DropdownMenuItem(
            text = { Text("Details") },
            leadingIcon = {
                Icon(imageVector = Icons.Default.Info, contentDescription = null)
            },
            onClick = {
                onDetailsClick()
                onMenuToggle(false)
            }
        )
        HorizontalDivider()
    }
    // Desktop: "Details" always visible (not conditional on hero)
    if (isDesktop && onDetailsClick != null) {
        DropdownMenuItem(
            text = { Text("Details") },
            leadingIcon = {
                Icon(imageVector = Icons.Default.Info, contentDescription = null)
            },
            onClick = {
                onDetailsClick()
                onMenuToggle(false)
            }
        )
        HorizontalDivider()
    }
}
```

### Scroll-to-top button composable (READER-03)
```kotlin
// In BookmarkViewerContent.kt, inside the Box after LazyColumn:
AnimatedVisibility(
    visible = scrollToTopVisible && scrollToTopEnabled,
    enter = fadeIn(animationSpec = tween(300)),
    exit = fadeOut(animationSpec = tween(300)),
    modifier = Modifier.align(Alignment.BottomStart).padding(16.dp)
) {
    SmallFloatingActionButton(
        onClick = { scope.launch { scrollState.animateScrollToItem(0) } },
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Icon(
            imageVector = Icons.Default.ArrowUpward,
            contentDescription = "Scroll to top"
        )
    }
}
```

### Scroll-to-top visibility logic (READER-03)
```kotlin
// New composable in ViewerScrollBehavior.kt:
@Composable
internal fun rememberScrollToTopVisibility(
    scrollState: LazyListState,
    fabVisible: Boolean  // reuse same scroll-direction signal
): Boolean {
    val isHeroVisible by remember {
        derivedStateOf { scrollState.firstVisibleItemIndex == 0 }
    }
    val isAtEnd by remember {
        derivedStateOf {
            val layoutInfo = scrollState.layoutInfo
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()
            lastVisible != null && lastVisible.index == layoutInfo.totalItemsCount - 1
        }
    }
    // Never show while hero visible; show on scroll-up or at end
    return !isHeroVisible && (fabVisible || isAtEnd)
}
```

### New setting in StoredReaderSettings (READER-04)
```kotlin
// StoredSettings.kt
@Serializable
internal data class StoredReaderSettings(
    // ... existing fields ...
    val scrollToTopEnabled: Boolean = true  // NEW - default ON
)

// SettingsRepository.kt
val scrollToTopEnabled: Flow<Boolean> =
    readerSettingsFlow.map { it.scrollToTopEnabled }.distinctUntilChanged()

// SettingsRepositoryMutations.kt
suspend fun SettingsRepository.setScrollToTopEnabled(enabled: Boolean) =
    updateReaderSettings { copy(scrollToTopEnabled = enabled) }
```

### Adding toggle to ReaderAppearanceBottomPanel (READER-04)
The panel currently has 5 tabs (TextSize, Font, TextColor, Background, Reset). The scroll-to-top toggle should be added as a `Switch` + label row. Two approaches:
1. Add a new tab (index 5) with settings toggles -- but the panel is already at 5 tabs.
2. Add a `Row` with `Switch` above or below the `TabRow` -- keeps it visible across all tabs.

**Recommended:** Add as a `Row` with `Switch` below the tab content area, within the `BaseBottomPanel` content. This avoids adding a 6th tab and keeps the toggle always visible while the panel is open.

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | Kotlin Test (multiplatform) |
| Config file | `composeApp/build.gradle.kts` (test dependencies) |
| Quick run command | `./gradlew :composeApp:jvmTest --tests "com.karakept.app.data.repository.StoredSettingsSerializationTest"` |
| Full suite command | `./gradlew :composeApp:jvmTest` |

### Phase Requirements to Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| READER-01 | Scroll position restores after reader close | manual-only | N/A -- requires UI test infrastructure (Phase 07) | N/A |
| READER-02 | Info button moves to overflow when hero scrolls away | manual-only | N/A -- requires Compose UI test (Phase 07) | N/A |
| READER-03 | Scroll-to-top button appears/disappears correctly | manual-only | N/A -- requires Compose UI test (Phase 07) | N/A |
| READER-04 | scrollToTopEnabled setting persists | unit | `./gradlew :composeApp:jvmTest --tests "*StoredSettingsSerializationTest*scrollToTop*"` | Wave 0 |

### Sampling Rate
- **Per task commit:** `./gradlew :composeApp:jvmTest --tests "*StoredSettingsSerializationTest"` (< 30s)
- **Per wave merge:** `./gradlew :composeApp:jvmTest`
- **Phase gate:** Full suite green before `/gsd:verify-work`

### Wave 0 Gaps
- [ ] Add `scrollToTopEnabled` default value test and JSON round-trip test in `StoredSettingsSerializationTest.kt`
- No new test files needed -- existing test file covers the pattern

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `Divider` | `HorizontalDivider` | Material3 1.2+ | Project already uses `HorizontalDivider` everywhere |
| `LazyListState()` unmanaged | `rememberSaveable(saver = LazyListState.Saver)` | Compose 1.5+ | Already in use but may have a bug |

## Open Questions

1. **READER-01 Root Cause**
   - What we know: `rememberSaveable` with `LazyListState.Saver` exists in `MainScreen.kt` line 137. Bug #152 reports scroll resets to top.
   - What's unclear: Whether the issue is Voyager lifecycle (screen recomposition on `pop()`), data-driven recomposition (bookmark list change while in viewer), or `SaveableStateRegistry` clearing.
   - Recommendation: First task should be investigation-only. Add logging, reproduce, identify root cause before implementing fix. Likely candidates: (a) `bookmarkListVersion` or `bookmarks` list change triggers full `LazyColumn` re-layout; (b) Voyager does not preserve `SaveableStateRegistry` for `object Screen` singletons across push/pop.

2. **READER-04 Toggle Placement in Panel**
   - What we know: `ReaderAppearanceBottomPanel` has 5 tabs in a `TabRow`. Adding a 6th tab would crowd the UI.
   - What's unclear: Whether a `Switch` row outside the tab content or a new "Settings" tab is the better UX.
   - Recommendation: Add a `Switch` row below the tab content area (always visible while panel is open). This matches the pattern of a global toggle that affects the reader but isn't an "appearance" setting.

## Sources

### Primary (HIGH confidence)
- Direct codebase analysis of all files listed in Code Context section
- `StoredSettings.kt`, `SettingsRepository.kt`, `SettingsRepositoryMutations.kt` -- established settings pattern
- `ViewerScrollBehavior.kt` -- existing FAB visibility and reading progress implementations
- `ViewerTopBar.kt` -- existing overflow menu structure
- `BookmarkViewerContent.kt` -- existing reader layout with `LazyColumn` and `Box` overlays
- `MainScreen.kt` line 137 -- `rememberSaveable` with `LazyListState.Saver`
- `AppModule.kt` -- `MainScreenModel` is `single {}`, `BookmarkViewerScreenModel` is `factory {}`

### Secondary (MEDIUM confidence)
- Voyager screen lifecycle behavior with `object Screen` singletons (based on library documentation patterns)

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH - no new dependencies needed, all existing patterns documented from codebase
- Architecture: HIGH - every file touched has been read and patterns extracted
- Pitfalls: HIGH for READER-02/03/04, MEDIUM for READER-01 (root cause unknown until investigated)

**Research date:** 2026-03-23
**Valid until:** 2026-04-23 (stable codebase, no external dependency changes)
