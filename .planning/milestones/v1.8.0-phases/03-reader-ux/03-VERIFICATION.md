---
phase: 03-reader-ux
verified: 2026-03-23T14:30:00Z
status: human_needed
score: 9/9 must-haves verified
human_verification:
  - test: "READER-01: Scroll position persists after closing reader"
    expected: "Scroll to the middle of the bookmark list, open a bookmark in the reader, press back — list stays at same position"
    why_human: "Compose LazyListState behavior and Voyager navigation restore cannot be verified by static analysis"
  - test: "READER-02: Details item in overflow menu always shown"
    expected: "Three-dots overflow menu in reader always includes a Details item with an Info icon that opens the bookmark details panel"
    why_human: "DropdownMenu visibility and panel display require runtime UI interaction"
  - test: "READER-03: Scroll-to-top button visibility rules"
    expected: "No FAB at hero; FAB appears bottom-left after scrolling past hero and scrolling back up; FAB always visible at end of article; tapping FAB smooth-scrolls to top"
    why_human: "Scroll-position-dependent AnimatedVisibility requires runtime testing; end-of-article detection depends on real content"
  - test: "READER-04: Behaviour tab toggle persists"
    expected: "Opening Reader Appearance, switching to Behaviour tab (Tune icon, tab index 4), toggling the Scroll-to-top button switch hides/shows the FAB immediately; setting persists across reader sessions"
    why_human: "UI tab navigation, real-time toggle response, and DataStore persistence require runtime verification"
---

# Phase 03: Reader UX Verification Report

**Phase Goal:** Improve the reader UX with scroll position restore, Details overflow menu item, scroll-to-top button, and appearance toggle
**Verified:** 2026-03-23T14:30:00Z
**Status:** human_needed
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | scrollToTopEnabled setting defaults to true and round-trips through JSON serialization | VERIFIED | `StoredSettings.kt:61` — `val scrollToTopEnabled: Boolean = true`; 4 serialization tests present in `StoredSettingsSerializationTest.kt` lines 144–164 |
| 2 | scrollToTopEnabled is exposed as a StateFlow in BookmarkViewerScreenModel | VERIFIED | `BookmarkViewerScreenModel.kt:106` — `val scrollToTopEnabled: StateFlow<Boolean> = settingsRepository.scrollToTopEnabled` |
| 3 | scrollToTopEnabled can be toggled via setScrollToTopEnabled mutation | VERIFIED | `SettingsRepositoryMutations.kt:250` — `suspend fun SettingsRepository.setScrollToTopEnabled(enabled: Boolean)`; `BookmarkViewerScreenModel.kt:486` — `fun setScrollToTopEnabled(enabled: Boolean)` |
| 4 | Bookmark list scroll position is restored after closing the reader and returning to the list | VERIFIED (code) | `MainScreenModel.kt:111-117` — `savedScrollIndex`/`savedScrollOffset` fields and `saveScrollPosition()`; `MainScreen.kt:142-154` — `LazyListState` initialised from hoisted values with `snapshotFlow` persisting on change; `MainScreenModelPagination.kt:135` — `scrollToTop()` moved to `resetPaginationAndLoad` only | HUMAN NEEDED for runtime confirmation |
| 5 | Details item always appears in the overflow menu | VERIFIED (code) | `ViewerTopBar.kt:251-259` — `if (onDetailsClick != null)` unconditional block with `Text("Details")` and `Icons.Default.Info`; deviation from plan (was conditional on hero visibility) approved by user |
| 6 | A scroll-to-top SmallFloatingActionButton appears at bottom-left with correct visibility rules | VERIFIED (code) | `ViewerScrollBehavior.kt:172-187` — `rememberScrollToTopVisibility` using `derivedStateOf` on `firstVisibleItemIndex` and end-of-list detection; `BookmarkViewerContent.kt:396-409` — `AnimatedVisibility(visible = scrollToTopVisible && scrollToTopEnabled, modifier = Modifier.align(Alignment.BottomStart))` with `SmallFloatingActionButton` and `Icons.Default.ArrowUpward` |
| 7 | Tapping the scroll-to-top button smooth-scrolls to the top of the article | VERIFIED (code) | `BookmarkViewerContent.kt:403` — `onClick = { scope.launch { scrollState.animateScrollToItem(0) } }` |
| 8 | Toggling the scroll-to-top switch in the Behaviour tab immediately hides/shows the button | VERIFIED (code) | `ReaderAppearanceBottomPanel.kt:105-128` — tab index 4 with Tune icon routes to `BehaviourTab(scrollToTopEnabled, onScrollToTopToggle)`; `BehaviourTab:327-341` — `Switch(checked = scrollToTopEnabled, onCheckedChange = onScrollToTopToggle)`; `ViewerContentPanels.kt:71,84-85` — `collectAsState()` + `screenModel.setScrollToTopEnabled(it)` wired at call site |
| 9 | The scroll-to-top setting flows from StoredReaderSettings through SettingsRepository to the UI | VERIFIED | `SettingsRepository.kt:339-340` — `readerSettingsFlow.map { it.scrollToTopEnabled }.distinctUntilChanged()`; full chain: `StoredSettings -> SettingsRepository -> BookmarkViewerScreenModel -> BookmarkViewerContent -> ReaderAppearanceBottomPanel` verified at each link |

**Score:** 9/9 truths verified (code); 4 require human runtime confirmation

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `data/repository/StoredSettings.kt` | scrollToTopEnabled field in StoredReaderSettings | VERIFIED | Line 61: `val scrollToTopEnabled: Boolean = true` |
| `data/repository/SettingsRepository.kt` | scrollToTopEnabled Flow | VERIFIED | Lines 339-340: Flow with `readerSettingsFlow.map` |
| `data/repository/SettingsRepositoryMutations.kt` | setScrollToTopEnabled setter | VERIFIED | Line 250: `suspend fun SettingsRepository.setScrollToTopEnabled(enabled: Boolean)` |
| `ui/screens/BookmarkViewerScreenModel.kt` | scrollToTopEnabled StateFlow + setScrollToTopEnabled fun | VERIFIED | Line 106: StateFlow; Line 486: setter function |
| `ui/screens/viewer/ViewerTopBar.kt` | Details DropdownMenuItem | VERIFIED | Lines 251-259: always-present Details item with Info icon |
| `ui/screens/viewer/ViewerScrollBehavior.kt` | rememberScrollToTopVisibility composable | VERIFIED | Lines 172-187: full implementation with derivedStateOf |
| `ui/screens/BookmarkViewerContent.kt` | Scroll-to-top SmallFloatingActionButton overlay | VERIFIED | Lines 232-233 (state), 396-409 (FAB overlay), wired to scrollToTopEnabled |
| `ui/components/ReaderAppearanceBottomPanel.kt` | Behaviour tab (index 4) with scroll-to-top Switch | VERIFIED | Lines 67-68 (params), 105-107 (tab), 128 (route), 327-341 (BehaviourTab) |
| `ui/screens/MainScreenModel.kt` | savedScrollIndex/savedScrollOffset/saveScrollPosition | VERIFIED | Lines 111-117 |
| `ui/screens/MainScreenModelPagination.kt` | scrollToTop() in resetPaginationAndLoad only | VERIFIED | Line 135: `scrollToTop()` called there; lines 431,449: comments confirm removal from applyFilter/clearFilter |
| `ui/screens/MainScreen.kt` | LazyListState initialised from hoisted position | VERIFIED | Lines 142-154 |
| `commonTest/.../StoredSettingsSerializationTest.kt` | scrollToTopEnabled serialization tests | VERIFIED | 4 test functions at lines 144-164 covering default, round-trip, missing-field, explicit-false |

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| StoredReaderSettings | SettingsRepository.scrollToTopEnabled | readerSettingsFlow.map | WIRED | `SettingsRepository.kt:339-340` |
| SettingsRepository.scrollToTopEnabled | BookmarkViewerScreenModel.scrollToTopEnabled | stateIn | WIRED | `BookmarkViewerScreenModel.kt:106-107` |
| BookmarkViewerScreenModel.scrollToTopEnabled | BookmarkViewerContent | collectAsState | WIRED | `BookmarkViewerContent.kt:232` |
| ViewerScrollBehavior.rememberScrollToTopVisibility | BookmarkViewerContent | call site | WIRED | `BookmarkViewerContent.kt:233` |
| BookmarkViewerContent.isHeroVisible | ViewerTopBar | onDetailsClick parameter | WIRED | `BookmarkViewerContent.kt:459`: `onDetailsClick = { showDetailsPanel = true }` |
| ReaderAppearanceBottomPanel | screenModel.setScrollToTopEnabled | onScrollToTopToggle callback | WIRED | `ViewerContentPanels.kt:84-85` |
| ViewerDialogs | ReaderAppearanceBottomPanel | scrollToTopEnabled + onScrollToTopToggle params | WIRED | `ViewerDialogs.kt:145-146,184-185` |

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|-------------|-------------|--------|----------|
| READER-01 | 03-01 | Closing the reader view restores the user's scroll position in the bookmark list | SATISFIED | Scroll position hoisted into `MainScreenModel` singleton; `LazyListState` initialised from saved values; `scrollToTop()` moved to `resetPaginationAndLoad` only |
| READER-02 | 03-02 | Reader info button moves to the three-dots overflow menu (always shown per approved deviation) | SATISFIED | `ViewerTopBar.kt:251-259` — unconditional Details item; deviation from plan spec (was hero-conditional) approved by user |
| READER-03 | 03-02 | User can tap a scroll-to-top button in the reader to return to the beginning of the article | SATISFIED | `BookmarkViewerContent.kt:396-409` — `SmallFloatingActionButton` with `animateScrollToItem(0)`; visibility via `rememberScrollToTopVisibility` |
| READER-04 | 03-01 + 03-02 | User can toggle the scroll-to-top button visibility in reader settings | SATISFIED | Setting stored in `StoredReaderSettings.scrollToTopEnabled`; toggle in Behaviour tab (index 4, Tune icon) per approved deviation |

No orphaned requirements — all four READER-0x IDs assigned to Phase 03 are claimed by plans and implemented.

### Anti-Patterns Found

None detected. Scanned all phase-modified files for TODO/FIXME/placeholder/stub patterns — no matches found.

### Human Verification Required

#### 1. READER-01: Scroll position persists after closing reader

**Test:** Open the app, scroll the bookmark list to a position past the first few items. Open any bookmark in the reader. Press back. Check the list position.
**Expected:** List is at the same scroll position, not reset to the top.
**Why human:** `LazyListState` restore and Voyager navigation stack behavior cannot be verified by static analysis. The `snapshotFlow` save path and initialisation from `savedScrollIndex`/`savedScrollOffset` require actual Compose runtime execution.

#### 2. READER-02: Details item always present in overflow menu

**Test:** Open a bookmark in the reader. Open the three-dots overflow menu while the hero/banner is visible. Then scroll past the hero and open the menu again.
**Expected:** "Details" with an Info icon is present in both cases. Tapping it opens the bookmark details panel.
**Why human:** DropdownMenu visibility and navigation panel display require UI interaction.

#### 3. READER-03: Scroll-to-top button visibility rules

**Test:** In the reader, while the hero banner is visible, check bottom-left for a FAB. Scroll past the hero, then scroll upward slightly. Scroll to the very end of the article. Tap the FAB when visible.
**Expected:** No FAB at hero. FAB appears bottom-left after scrolling past hero and then scrolling upward. FAB always visible at end of article. Tapping FAB animates smoothly to top.
**Why human:** `AnimatedVisibility` transitions and scroll-position logic require runtime testing. End-of-article detection (`lastVisible.index == totalItemsCount - 1`) depends on real content length.

#### 4. READER-04: Behaviour tab toggle persists setting

**Test:** In the reader, scroll past the hero so the scroll-to-top FAB appears. Open three-dots menu, tap "Reader Appearance". Navigate to the Tune icon tab (tab index 4). Toggle "Scroll-to-top button" switch off, then on. Close and reopen the reader.
**Expected:** FAB disappears immediately when toggled off (without closing the panel). FAB reappears when toggled on. After reopening the reader, the setting is in the state it was left in.
**Why human:** Real-time toggle response and DataStore persistence require runtime verification.

### Approved Deviations (not counted as gaps)

| Deviation | Plan Spec | Actual Implementation | Approval |
|-----------|-----------|----------------------|---------|
| READER-02: Details visibility | Conditional on hero visibility (`isDesktop \|\| !isHeroVisible`) | Always shown (`if (onDetailsClick != null)` unconditional) | User-approved — simpler and more discoverable |
| READER-04: Toggle placement | Always-visible below tabs | Dedicated Behaviour tab (Tune icon, tab index 4) | User-approved — keeps panel organized |
| READER-01: Scroll restore mechanism | Guard `MainScreenScrollAction` or hoist to ScreenModel | Moved `scrollToTop()` to `resetPaginationAndLoad` + hoisted state in `MainScreenModel` | User-verified working |

### Commit Verification

All documented commits verified present in git history:
- `1c3b7d7` feat(03-01): add scrollToTopEnabled setting plumbing with serialization tests
- `70531a5` fix(03-01): preserve bookmark list scroll position across reader navigation
- `fef4624` feat(03-02): add conditional Details menu item and scroll-to-top button
- `a68bb5b` fix(03-02): always show Details in overflow menu, remove conditional logic
- `1e44978` feat(03-02): add scroll-to-top toggle to ReaderAppearanceBottomPanel
- `6d5766d` fix(03-02): move scroll-to-top toggle into dedicated Behaviour tab
- `5d4d2aa` fix(03-01): restore list scroll position after reader back navigation

---

_Verified: 2026-03-23T14:30:00Z_
_Verifier: Claude (gsd-verifier)_
