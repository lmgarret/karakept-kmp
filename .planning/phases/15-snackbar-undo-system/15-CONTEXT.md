# Phase 15: Snackbar Undo System - Context

**Gathered:** 2026-03-28
**Status:** Ready for planning

<domain>
## Phase Boundary

Add Undo buttons to all snackbars triggered by reversible actions across the entire app. Audit all existing snackbar sites, wire undo logic for every reversible action, and add snackbar feedback to bottom sheet actions that currently give zero feedback. Establish the `showSnackbarWithUndo` pattern as the standard for all reversible actions going forward.

Requirement: UX-01 (#166)

</domain>

<decisions>
## Implementation Decisions

### Undo Scope
- **D-01:** All reversible actions get undo snackbars — swipe actions (tag toggle, list toggle) AND bottom sheet actions (archive, favorite, read, move-to-list, update-tags). Only Delete keeps its confirm dialog (server-side permanent).
- **D-02:** Both MainScreen and BookmarkViewerContent get undo snackbars. The viewer already has snackbar infrastructure (ViewerSnackbar.kt with FAB-aware delay) — wire it consistently.

### Undo Mechanics
- **D-03:** Undo uses "re-call reverse API" pattern. Action fires immediately (current behavior). Undo calls the reverse ScreenModel method (e.g., archive→unarchive, addTag→removeTag). No optimistic delay or coroutine cancellation needed.
- **D-04:** Snackbar duration: `SnackbarDuration.Short` (4s) for all undo snackbars. Consistent across all action types.

### Snackbar Feedback Gap
- **D-05:** All bottom sheet actions get confirmation snackbars with undo: "Archived" [Undo], "Added to favorites" [Undo], "Marked as read" [Undo], "Moved to {listName}" [Undo], "Tags updated" [Undo].
- **D-06:** Snackbar messages use past tense ("Archived", "Added to favorites", "Removed tag 'X'"). Action already fired, so past tense is accurate.

### Batch Actions
- **D-07:** Batch archive gets undo snackbar: "Archived N bookmarks" [Undo]. Batch delete keeps the existing confirm dialog (permanent action).
- **D-08:** Claude's Discretion: Audit all selection mode batch actions and apply the same undo pattern to any other reversible batch actions found.

### Claude's Discretion
- D-08: Audit and wire undo for any additional batch actions in selection mode beyond archive/delete
- Implementation detail: Whether to create a shared helper/extension for the "call action → show undo snackbar → on undo call reverse" pattern, per UX-01 guidance ("establish shared UndoableSnackbar pattern/helper if 3+ sites use it")

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Snackbar Infrastructure (already built)
- `composeApp/src/commonMain/kotlin/com/karakept/app/domain/action/ActionSnackbarManager.kt` — Central snackbar manager with `showSnackbar()`, `showSnackbarWithUndo()`, `showErrorWithRetry()`. SnackbarEvent sealed class with Message, MessageWithUndo, MessageWithAction variants.
- `composeApp/src/commonMain/kotlin/com/karakept/app/ui/screens/viewer/ViewerSnackbar.kt` — `rememberSnackbarHostStateWithDelay()` composable + `showSnackbarEvent()` dispatcher handling all three event types. FAB-aware delay logic.

### Main Screen (primary audit target)
- `composeApp/src/commonMain/kotlin/com/karakept/app/ui/screens/MainScreen.kt` — Swipe action handler (~lines 228-256) with plain snackbars for tag/list toggles. SnackbarEvent collection (~lines 455-466) already handles MessageWithUndo.
- `composeApp/src/commonMain/kotlin/com/karakept/app/ui/screens/main/MainScreenDialogs.kt` — `MainScreenBookmarkActionsMenu` (~lines 150-190) handles BookmarkAction dispatch with NO snackbar feedback for most actions.

### Viewer Screen (secondary audit target)
- `composeApp/src/commonMain/kotlin/com/karakept/app/ui/screens/BookmarkViewerContent.kt` — Viewer with snackbar host. Actions dispatched via FAB menu.
- `composeApp/src/commonMain/kotlin/com/karakept/app/ui/screens/BookmarkViewerScreenModel.kt` — Viewer ScreenModel with action methods.

### Action Menu Component
- `composeApp/src/commonMain/kotlin/com/karakept/app/ui/components/BookmarkActionsMenu.kt` — BookmarkAction sealed class (ToggleArchive, ToggleFavorite, ToggleRead, MoveToList, UpdateTags, Delete, Share, OpenInBrowser, Select).

### Selection Mode (batch actions)
- `composeApp/src/commonMain/kotlin/com/karakept/app/ui/screens/MainScreen.kt` — Batch delete confirm dialog, selection mode actions.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `ActionSnackbarManager.showSnackbarWithUndo()` — Already exists, takes message + onUndo lambda. Fully wired in both MainScreen and ViewerSnackbar event collectors.
- `SnackbarEvent.MessageWithUndo` — Already handled in `showSnackbarEvent()` and MainScreen's event collector. No new event types needed.
- `MainScreenModel` — Has all reverse methods: `toggleBookmarkArchive()`, `toggleBookmarkFavorite()`, `toggleBookmarkRead()`, `removeBookmarkTag()`, `addBookmarkTag()`, `moveBookmarkToList()`, `removeBookmarkFromList()`.

### Established Patterns
- Swipe actions use `scope.launch { snackbarManager.showSnackbar("message") }` — convert to `snackbarManager.showSnackbarWithUndo("message") { reverseAction() }`
- Bottom sheet actions dispatch through `MainScreenBookmarkActionsMenu` → `when(action)` block — add snackbar calls after each action
- Viewer uses `rememberSnackbarHostStateWithDelay` which already routes `MessageWithUndo` events to the snackbar host with Undo button

### Integration Points
- `MainScreenDialogs.kt:165-186` — BookmarkAction handler needs snackbar calls added for every reversible action
- `MainScreen.kt:228-256` — Swipe action handler needs `showSnackbar` replaced with `showSnackbarWithUndo`
- Selection mode batch actions — need audit and undo wiring

</code_context>

<specifics>
## Specific Ideas

No specific requirements — open to standard approaches

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 15-snackbar-undo-system*
*Context gathered: 2026-03-28*
