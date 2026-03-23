# Karakept KMP

## What This Is

A Kotlin Multiplatform bookmark manager built with Compose Multiplatform, targeting Android and Desktop. Uses Material Design 3, Voyager navigation, Koin DI, and SQLDelight for local storage.

## Core Value

A reliable, well-structured bookmark management app with clean code practices.

## Requirements

### Validated

- ✓ Replace ~97 `println` debug calls with `AppLogger` or remove them — v1.7.0
- ✓ Trim `BookmarkSyncPipeline.kt` below 500 lines — v1.7.0 (493 lines after println cleanup)
- ✓ Trim `SettingsRepositoryMutations.kt` below 500 lines — v1.7.0 (478 lines after println cleanup)
- ✓ Remove redundant `koinInject<ServerRepository>()` in `App.kt` — v1.7.0

### Active

- ✓ Fix reader closing scrolling list to top (#152) — Phase 03
- ✓ Reader info button should move to menu on scroll (#160) — Phase 03 (simplified: always in menu)
- ✓ Scroll-to-top button in reader (#161) — Phase 03
- ✓ Fix select-all only selecting 20 entries (#153) — Phase 06
- ✓ Quick actions not reflected in viewed list (#154) — Phase 05
- ✓ Per-list offline sync not working (#155) — Phase 05
- ✓ Add counters to Quick Filters in drawer (#156) — Phase 06
- ✓ Pull-to-refresh for Highlights (#157) — Phase 06
- ✓ Cannot go back to bookmark list after share-save (#158) — Phase 04
- ✓ State leak between bookmark saving activities (#159) — Phase 04

### Out of Scope

- Dependency upgrades (tracked separately)
- Platform-specific improvements

## Current Milestone: v1.8.0 Bug Fixes & UX Improvements

**Goal:** Fix 6 bugs and deliver 4 UX improvements across reader, bookmark saving, list sync, and filtering.

**Target features:**
- Reader UX: restore scroll position, info button in overflow menu, scroll-to-top button
- Bookmark saving activity: fix navigation and state leak on Android share target
- List & sync: quick action list refresh, per-list offline sync
- Selection & filtering: select-all beyond pagination, quick filter counters, pull-to-refresh highlights

## Context

- v1.7.0 shipped 2026-03-21: tech debt cleanup (logging, file sizes, redundant DI)
- Code-health milestone completed 2026-03-21, archived in `.planning/archive/code-health/`
- AppLogger infrastructure in place with `.d()`, `.i()`, `.w()`, `.e()` severity levels
- All production files under 500 lines
- The app is functional and in active use
- 10 open issues filed 2026-03-22/23 covering bugs and UX improvements

## Constraints

- **Stability**: No regressions in existing bookmark management functionality
- **Build**: Build/test commands run externally by the user, not in this session
- **Platform**: Bookmark saving activity issues (#158, #159) are Android-specific

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Aligned milestone versioning to git tags | Previous "v1.0" milestone didn't match repo's actual v1.x.x tags | v1.7.0 follows v1.6.0 ✓ |
| Hot-path logging removed, not replaced | Per-item loop logging adds noise without debug value | Zero hot-path println calls ✓ |
| Consolidated sync action logging | 53 verbose step traces → ~15 targeted start/success/error logs | Cleaner BookmarkActionsRepositorySync ✓ |

## Evolution

This document evolves at phase transitions and milestone boundaries.

**After each phase transition** (via `/gsd:transition`):
1. Requirements invalidated? → Move to Out of Scope with reason
2. Requirements validated? → Move to Validated with phase reference
3. New requirements emerged? → Add to Active
4. Decisions to log? → Add to Key Decisions
5. "What This Is" still accurate? → Update if drifted

**After each milestone** (via `/gsd:complete-milestone`):
1. Full review of all sections
2. Core Value check — still the right priority?
3. Audit Out of Scope — reasons still valid?
4. Update Context with current state

---
*Last updated: 2026-03-23 after Phase 06 (selection-filtering) complete*
