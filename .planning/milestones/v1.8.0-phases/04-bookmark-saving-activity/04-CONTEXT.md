# Phase 4: Bookmark Saving Activity - Context

**Gathered:** 2026-03-23
**Status:** Ready for planning

<domain>
## Phase Boundary

Fix the Android share-target bookmark saving flow so that users can navigate back to the bookmark list after saving, and so that sharing a second URL immediately after the first shows a clean, fresh saving screen.

Two specific bugs:
- SAVE-01 (#158): After saving via share target, pressing back in the bookmark viewer traps the user (no route to main list)
- SAVE-02 (#159): Sharing a second URL after saving the first reuses stale activity state from the previous save

</domain>

<decisions>
## Implementation Decisions

### Post-save navigation (SAVE-01)
- **D-01:** After a bookmark is saved successfully, open `BookmarkViewerScreen` AND build the full back stack so pressing back goes to `MainScreen` (bookmark list). Use `navigator.replaceAll(listOf(MainScreen, BookmarkViewerScreen(bookmark.localId)))`.
- **D-02:** The user should see the saved bookmark immediately in the viewer — not skip straight to the list.

### Activity architecture for state isolation (SAVE-02)
- **D-03:** Introduce a dedicated `BookmarkSavingActivity` with `android:launchMode="singleTask"`. This replaces the current `ShareActivity → MainActivity` delegation pattern for the share-target flow.
- **D-04:** `BookmarkSavingActivity` handles the share intent directly (extracts URL, shows saving UI, navigates). It does NOT launch `MainActivity` — it is self-contained.
- **D-05:** With `singleTask`, if the user shares a second URL while `BookmarkSavingActivity` is already running, Android calls `onNewIntent` on the existing instance, and the activity resets its Compose state cleanly to a fresh `ShareBookmarkScreen` for the new URL.
- **D-06:** The existing `ShareActivity` class is removed (or emptied) since `BookmarkSavingActivity` supersedes it. The `QuickShareActivity` (background WorkManager path) is unchanged.

### Error handling
- **D-07:** On save failure, show both a **Retry** button (re-attempts `createBookmark(url)` with the same URL) and a **Close** button (calls `finish()` to return to the sharer app).
- **D-08:** The Retry button reuses the existing `ShareBookmarkScreen` — reset error state and re-launch the `LaunchedEffect` (or trigger a retry via a state flag).

### Claude's Discretion
- Exact Compose state reset mechanism inside `BookmarkSavingActivity.onNewIntent` (e.g., mutableStateOf key, setContent re-call, or derived state)
- Whether `BookmarkSavingActivity` uses a Voyager Navigator or a plain Compose scaffold (it's a short-lived flow, not a full nav graph)
- AndroidManifest entry details (theme, label, exported flag)
- Exact UI layout of the saving/error screen — keep consistent with current `ShareBookmarkScreen` design

</decisions>

<specifics>
## Specific Ideas

- `singleTask` is the right launch mode for SAVE-02: Android guarantees a single instance per task and routes new intents to `onNewIntent`, which is exactly the state-reset hook needed.
- Back stack fix: Voyager's `navigator.replaceAll(listOf(MainScreen, BookmarkViewerScreen(id)))` sets up `[MainScreen] → [BookmarkViewerScreen]`, so pressing back in the viewer always goes to the main list.

</specifics>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Share-target activity code
- `composeApp/src/androidMain/kotlin/com/karakept/app/ShareActivity.kt` — Current share-target entry point (to be replaced by BookmarkSavingActivity)
- `composeApp/src/androidMain/kotlin/com/karakept/app/QuickShareActivity.kt` — Background-save path (unchanged — WorkManager + notification only)
- `composeApp/src/androidMain/kotlin/MainActivity.kt` — Main activity; `onNewIntent` currently handles shared_url re-delivery (no longer needed for share flow after D-04)
- `composeApp/src/androidMain/AndroidManifest.xml` — Activity declarations and intent-filters; must be updated for BookmarkSavingActivity

### Saving UI and navigation
- `composeApp/src/commonMain/kotlin/com/karakept/app/ui/screens/ShareBookmarkScreen.kt` — Current saving screen; uses `navigator.replace(BookmarkViewerScreen)` (bug: D-01 fixes this)
- `composeApp/src/commonMain/kotlin/App.kt` — Root composable; currently routes `sharedUrl != null` to `ShareBookmarkScreen` (no longer needed for share flow after D-04)
- `composeApp/src/commonMain/kotlin/com/karakept/app/ui/screens/MainScreen.kt` — Destination for back navigation after save

### Requirements
- `.planning/REQUIREMENTS.md` — SAVE-01, SAVE-02

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `ShareBookmarkScreen` composable: already has loading state, progress indicator, error display — can be reused inside `BookmarkSavingActivity` with the retry button added (D-07)
- `BookmarkRepository.createBookmark(url)`: the save call with status callback — no changes needed
- Voyager `Navigator` + `SlideTransition`: used in `App.kt` for full-app nav; `BookmarkSavingActivity` can use the same pattern scoped to its own `setContent`

### Established Patterns
- Activities in this project use `ComponentActivity` + `setContent { }` + Koin DI
- `QuickShareActivity` injects `BookmarkRepository` via `by inject()` — same pattern for `BookmarkSavingActivity`
- URL extraction from `Intent.EXTRA_TEXT` uses a regex — reuse verbatim from `ShareActivity.handleIntent`

### Integration Points
- `BookmarkSavingActivity` needs to navigate to `BookmarkViewerScreen` on success — this requires a Voyager `Navigator` scoped to the activity, with `replaceAll([MainScreen, BookmarkViewerScreen(id)])` (D-01)
- After `replaceAll`, the activity effectively becomes `MainActivity` with the full nav graph — or it pops back to `MainActivity` after pressing back from viewer. Need to decide if the back from viewer finishes `BookmarkSavingActivity` or pushes into `MainActivity`.

> **Note for planner:** If `BookmarkSavingActivity` hosts its own Voyager Navigator ending at `MainScreen → BookmarkViewerScreen`, pressing back from the viewer will pop to `MainScreen` inside the activity — but there is no `MainActivity` involved. This means `MainScreen` runs inside `BookmarkSavingActivity`. Alternatively, the success path could `startActivity(MainActivity, openBookmarkId=...)` and `finish()` — keeping `BookmarkSavingActivity` short-lived. Either approach achieves D-01; the planner should choose the simpler one.

</code_context>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope.

</deferred>

---

*Phase: 04-bookmark-saving-activity*
*Context gathered: 2026-03-23*
