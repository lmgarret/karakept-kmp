# Requirements: v1.9.0 Bug Fixes & UX Polish

## Milestone Summary

Fix follow-up bugs from v1.8.0, improve notification accuracy, and add UX enhancements.
Every fix and new feature ships with regression tests.

---

## Functional Requirements

### NOTIF-01: Sync digest notification must show bookmark count (#169)

**What:** When background sync completes and has new bookmarks, the digest notification message must include the exact count (e.g., "3 new bookmarks synced").

**Why:** The current message just says bookmarks were added, making it impossible to gauge sync results at a glance.

**Test requirement:** Unit test verifying notification content string includes count for N bookmarks.

---

### NOTIF-02: "Notify on new bookmarks" option on list must fire (#170)

**What:** When a list has "Notify on new bookmarks" enabled, background sync must trigger a notification whenever a newly synced bookmark matches that list.

**Why:** The feature never fires — bookmarks are synced to the list but no notification is generated.

**Test requirement:** Unit test verifying notification is dispatched when a synced bookmark matches a list with the notify flag enabled.

---

### SAVE-02: Navigate back after saving bookmarks must show bookmark list (#163)

**What:** After saving a bookmark via the share activity and navigating back to the bookmark list, the list must show bookmarks with correct counters in the drawer.

**Why:** Follow-up to SAVE-01 (#162, v1.8.0). The navigation back works but the list shows empty and drawer counters are 0.

**Test requirement:** ViewModel test verifying bookmark list state is populated after the save flow completes.

---

### LIST-02: Smart list must reflect quick-action changes immediately (#165)

**What:** When a quick action adds a bookmark to a regular list (e.g., "Read Later"), the bookmark must disappear from any smart list whose query now excludes it — both immediately after the action and after syncing that list.

**Why:** Follow-up to LIST-01 (#162, v1.8.0). Smart list queries are not re-evaluated after quick actions; full sync from "All Bookmarks" works but per-list sync does not.

**Test requirement:** ViewModel/repository test verifying that after a quick action modifies a bookmark's lists, smart lists with queries that now exclude it no longer return it.

---

### FILT-04: Pull-to-refresh must work on Highlights on mobile (#164)

**What:** The Highlights screen on mobile (compact layout) must support pull-to-refresh to manually trigger a sync of highlights.

**Why:** Follow-up to FILT-03 (#162, v1.8.0). Desktop has a refresh button; mobile has nothing.

**Test requirement:** ViewModel test verifying that a refresh action triggers the highlights sync flow.

---

### UI-01: Scroll-to-top button must reach the actual top (#168)

**What:** The scroll-to-top FAB must scroll the bookmark list fully to index 0 / offset 0 — not stop a few pixels short.

**Why:** Current implementation consistently stops a few pixels short of the top.

**Test requirement:** Unit/ViewModel test verifying scroll state reaches position 0 after invoking scroll-to-top.

---

### UX-01: Snackbars for reversible actions must include an Undo button (#166)

**What:** All snackbars triggered by reversible actions (e.g., removing a bookmark from a list via a quick action, archiving, moving) must include an "Undo" button that reverts the action. Enforce this pattern for all future snackbars.

**Why:** UX standard — users need escape hatches for destructive one-tap actions.

**Scope:** Audit all existing snackbar sites; add Undo where the action is reversible. Establish a shared `UndoableSnackbar` pattern/helper if 3+ sites use it.

**Test requirement:** ViewModel tests verifying that undo restores prior state for each supported action type.

---

### UX-02: Custom layout improvements (#167)

**What:** Multiple sub-improvements to the custom layout editor and picker:
1. Layout picker screens must include a link/button to navigate to the layout creation screen.
2. Card layout preview must display the bookmark title (and other fields).
3. List and Card layouts should be unified with shared customization options: show/hide description, description position, title position.
4. An option to show the URL or domain name, with configurable position.

**Why:** The current custom layout system is minimal — the preview is empty, navigation is missing, and two layout types have redundant separate options.

**Test requirement:** ViewModel tests for layout state changes (show/hide fields, position options). UI logic tests for preview composition where extractable.

---

## Non-Functional Requirements

### NFR-01: Regression tests for every fix

Every bug fix and enhancement in this milestone ships with at least one automated test that would have caught the original bug. No fix without a test.

### NFR-02: No new regressions

The existing ~201 tests must continue to pass. No behavior changes outside the scope of the above requirements.

### NFR-03: Robolectric version awareness

If any test requires clicking inside a `ModalBottomSheet`, upgrade Robolectric to 4.15.1 first (known Robolectric 4.14 bug: `performClick()` silently fails inside bottom sheets on SDK 29-34). Track this as a phase prerequisite where needed.

---

## Out of Scope

- Add 'On open bookmark' custom action (#113) — deferred
- Server API version check (#61) — deferred
- Add support for bookmark refresh (#18) — deferred
- Support other kinds of bookmarks (#14) — deferred
- Compute reading time for non-synced articles (#9) — deferred
- Implement OIDC connect (#5) — deferred
- Dependency upgrades (tracked separately)

---

## Priority Order

1. NOTIF-01, NOTIF-02 — pure notification logic, self-contained
2. SAVE-02, LIST-02 — follow-up bugs with highest user impact
3. FILT-04, UI-01 — polish/interaction fixes
4. UX-01 — snackbar undo system
5. UX-02 — custom layout improvements (most complex)
