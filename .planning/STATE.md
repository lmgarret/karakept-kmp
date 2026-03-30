---
gsd_state_version: 1.0
milestone: v1.9.0
milestone_name: Bug Fixes & UX Polish
status: Milestone complete
stopped_at: Completed 16-03-PLAN.md
last_updated: "2026-03-30T18:06:49.649Z"
last_activity: 2026-03-30
progress:
  total_phases: 9
  completed_phases: 9
  total_plans: 18
  completed_plans: 18
---

# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-03-25)

**Core value:** A reliable, well-structured bookmark management app with clean code practices
**Current focus:** Phase 16 — custom-layout-improvements

## Current Position

Phase: 16
Plan: Not started

## Accumulated Context

### Decisions

- Milestone versioning aligned to git tags (v1.7.0 → v1.8.0 → v1.9.0)
- Platform Health milestone archived as internal (no git tag); v1.9.0 is the next real release
- FakeDataStore uses MutableStateFlow + Mutex for thread-safe atomic updates
- Real-object pattern for extension functions accessing internal members
- Internal top-level function extraction for composable logic to enable commonTest
- [Phase 12-notification-fixes]: Return value propagation from BookmarkSyncPipeline.execute() instead of racy StateFlow read fixes NOTIF-01 digest notification count
- [Phase 12-notification-fixes]: Post-sync DB query approach for NOTIF-02 per-list notification - keeps Int return type, findListsWithNewBookmarks extracted as internal top-level function for commonTest
- [Phase 13-smart-list-saving-followups]: syncSmartLists fires in parallel nested launches per smart list — independent, fire-and-forget, errors logged not surfaced
- [Phase 13]: MainScreenModel changed from Koin single{} to factory{} — each Voyager Navigator gets a fresh instance with its own screenModelScope
- [Phase 13]: Pure function extraction for reconciliation logic (computeStaleListRemovals) enables direct commonTest testing without mocks
- [Phase 14]: PullToRefreshBox wraps entire Scaffold content including empty state for consistent PTR gesture
- [Phase 14]: Explicit scrollOffset=0 in all animateScrollToItem(0) sites to prevent residual pixel offset
- [Phase 14]: Content lambda extraction pattern for PullToRefreshBox/Box conditional wrapping: shared composable lambda with inner Box for BoxScope access
- [Phase 15-snackbar-undo-system]: undoableAction as CoroutineScope extension for reuse across 4+ call sites; batchSetTags gets plain snackbar (no undo) due to per-bookmark tag complexity
- [Phase 15-snackbar-undo-system]: Used named parameter onUndo= syntax for showSnackbarWithUndo to avoid Kotlin trailing lambda ambiguity
- [Phase 16-custom-layout-improvements]: Use Ktor io.ktor.http.Url (not java.net.URI) for extractDomain() — consistent with FaviconUtils pattern, works across all KMP targets
- [Phase 16-custom-layout-improvements]: COMPACT_LIST enum value kept @Deprecated for deserialization compat; fromString() redirects to LIST transparently
- [Phase 16-custom-layout-improvements]: UrlDisplay implemented as private @Composable in BookmarkLayouts.kt — avoids local composable restrictions, reused by both LIST and CARD renderers
- [Phase 16-custom-layout-improvements]: COMPACT_LIST merged into LIST branch with comma syntax and @Suppress(DEPRECATION) — exhaustive when without code duplication
- [Phase 16-custom-layout-improvements]: URL Display Mode sub-options inline in Show card (not separate section) — two options don't warrant own section
- [Phase 16-custom-layout-improvements]: PerListSettingsScreen: desktop LayoutEditorDialog, mobile navigator.push — matches LayoutsScreen pattern
- [Phase 16]: URL Display Mode sub-options inline in Show card (not separate section) — two options don't warrant own section
- [Phase 16]: PerListSettingsScreen: desktop LayoutEditorDialog, mobile navigator.push — matches LayoutsScreen pattern

### Known Blockers

- Robolectric 4.14 ModalBottomSheet click bug (performClick() silently fails inside bottom sheets on SDK 29-34); upgrade to 4.15.1 before writing bottom sheet interaction tests

### Quick Tasks Completed

| # | Description | Date | Commit | Directory |
|---|-------------|------|--------|-----------|
| 260326-eaz | guard MacNotification against nil bundle when running outside .app bundle | 2026-03-26 | 19695da | [260326-eaz-guard-macnotification-against-nil-bundle](./quick/260326-eaz-guard-macnotification-against-nil-bundle/) |

## Session Continuity

Last activity: 2026-03-30
Stopped at: Completed 16-03-PLAN.md
Resume: Execute 14-02-PLAN.md
