# Phase 03 Context: Reader UX

**Phase goal:** Users have a polished, non-disruptive reader experience with reliable scroll position and quick navigation controls
**Requirements:** READER-01, READER-02, READER-03, READER-04
**Created:** 2026-03-23

---

## READER-01: Restore bookmark list scroll position on reader close

**Decision:** Fix the bug — no behavioral ambiguity.
**Expected behavior:** When user closes the reader (`navigator.pop()`), the bookmark list scrolls back to the position it was at before opening the reader.
**Known facts:**
- `MainScreen.kt` already uses `rememberSaveable(key = "main_screen_list_state", saver = LazyListState.Saver)` — scroll state is theoretically saved
- Bug #152 reports the list resets to top on reader close — root cause unknown, needs investigation
- Likely causes: key invalidation forcing recomposition, screen being recreated by Voyager instead of resumed, or a `LazyColumn` key change resetting the saver

---

## READER-02: Info button moves to overflow menu when hero scrolls away

**Decision:** Info button availability is context-dependent:

- **Mobile:** Button lives on the hero banner (existing). When hero scrolls out of view, add a **"Details"** item to the three-dots overflow menu (`ViewerTopBar`). Remove it again if user scrolls back to the hero. Opens the same `BookmarkDetailsPanel` (right-sliding panel).
- **Desktop:** "Details" action is **always visible inline** in the top bar — same pattern as the favorite/star button (may be width-dependent, match existing desktop logic exactly).

**Implementation notes:**
- Use the existing `firstVisibleItemIndex` from `LazyListState` to detect hero visibility (hero is item index 0)
- Overflow menu item appears only when `firstVisibleItemIndex > 0` (on mobile)
- Menu label: `"Details"`, use the same info icon as on the hero banner

---

## READER-03: Scroll-to-top button

**Decision:**

- **Position:** Bottom-left corner (fixed overlay on top of the `LazyColumn`)
- **Size:** Small button (not a full FAB — visually distinct from the existing FAB menu which is bottom-right)
- **Visibility rules (mobile and desktop):**
  1. Never shown while the hero section is visible (hero = `firstVisibleItemIndex == 0`)
  2. Appears when user **scrolls up** (same logic as the existing FAB visibility) — past-hero only
  3. **Exception:** Always visible when user has reached the very end of the article (`LazyListState.layoutInfo.visibleItemsInfo` contains the last item)
- **Animation:** Smooth fade in/out (animated visibility)
- **Action:** Smooth animated scroll to top (`animateScrollToItem(0)`)
- **Platform:** Same placement and behavior on desktop

---

## READER-04: Scroll-to-top toggle in reader settings

**Decision:**

- **Location:** Reader Appearance bottom panel (`ReaderAppearanceBottomPanel`) — alongside text color, font size, font family
- **Label:** `"Scroll-to-top button"`
- **Default:** **ON**
- **Persistence:** Stored in `SettingsRepository` (same pattern as other reader settings like `htmlFontSize`)
- **Effect:** Immediate — toggling hides/shows the button without leaving the reader (driven by a `StateFlow` in `BookmarkViewerScreenModel`)

---

## Code Context

| File | Relevance |
|------|-----------|
| `ui/screens/MainScreen.kt` | Bookmark list with `rememberSaveable` scroll state — bug #152 root cause here |
| `ui/screens/BookmarkViewerContent.kt` | Main reader layout — `LazyColumn` scroll state, overlay placement |
| `ui/screens/viewer/ViewerTopBar.kt` | Three-dots overflow menu — add "Details" item here |
| `ui/screens/viewer/ViewerScrollBehavior.kt` | FAB visibility logic — scroll-to-top button reuses same scroll-up detection |
| `ui/screens/viewer/ViewerScrollRestoration.kt` | Scroll guard & reading progress restoration |
| `ui/components/HeroImageBanner.kt` | Existing info button on hero |
| `ui/screens/viewer/BookmarkDetailsPanel.kt` | Right-sliding panel opened by info button |
| `ui/screens/ReaderAppearanceScreen.kt` | Reader Appearance panel — add scroll-to-top toggle here |
| `data/` (SettingsRepository) | Add new boolean setting for scroll-to-top toggle |

## Tests

**In-scope for this phase:**
- Add a serialization unit test for the new `scrollToTopEnabled` setting in `StoredSettingsSerializationTest` — verify default value (`true`) and JSON round-trip. Follows existing pattern exactly.

**Out of scope (deferred to Phase 07: UI Tests):**
- Compose UI / instrumented tests for READER-01 (scroll restore), READER-02 (info button visibility), READER-03 (scroll-to-top button) — no UI test infrastructure exists yet; Phase 07 adds it.

## Deferred Ideas

- Phase 07 (UI Tests) will cover Compose UI/instrumented tests for all v1.8.0 scenarios including reader UX.
