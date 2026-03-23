---
gsd_state_version: 1.0
milestone: v1.8.0
milestone_name: Bug Fixes & UX Improvements
status: unknown
stopped_at: Completed 04-01-PLAN.md
last_updated: "2026-03-23T16:42:02.845Z"
last_activity: 2026-03-23
progress:
  total_phases: 4
  completed_phases: 2
  total_plans: 3
  completed_plans: 3
---

# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-03-23)

**Core value:** A reliable, well-structured bookmark management app with clean code practices
**Current focus:** Phase 04 — bookmark-saving-activity

## Current Position

Phase: 05
Plan: Not started

## Performance Metrics

**Velocity:**

- Total plans completed: 0
- Average duration: --
- Total execution time: --

## Accumulated Context

### Decisions

- Milestone versioning aligned to git tags (v1.7.0 follows v1.6.0, v1.8.0 follows v1.7.0)
- All four v1.8.0 phases are independent (no inter-phase dependencies) -- can be reordered if needed
- [Phase 03]: Hoisted scroll position into MainScreenModel (Koin singleton) for cross-navigation persistence
- [Phase 03]: scrollToTopEnabled defaults to true, not included in BackupSettings (same pattern as showTagsInViewer)
- [Phase 03]: Details menu item always shown (not conditional on hero visibility) -- simpler and more discoverable
- [Phase 03]: Scroll-to-top toggle in dedicated Behaviour tab (Tune icon) rather than always-visible below tabs
- [Phase 04]: Kept sharedUrl param in App.kt for desktop deep link compatibility (desktop has no Activity equivalent)
- [Phase 04]: Used key(intentKey) pattern to force full Compose tree destruction on onNewIntent for fresh Navigator state

### Roadmap Evolution

- Phase 7 added: UI Tests — Compose UI test infrastructure and instrumented tests for v1.8.0 scenarios (reader UX, bookmark saving, list sync, selection/filtering)

### Pending Todos

None yet.

### Blockers/Concerns

- Build/test commands run externally by the user, not in this session
- Bookmark saving activity issues (#158, #159) are Android-specific

### Quick Tasks Completed

| # | Description | Date | Commit | Directory |
|---|-------------|------|--------|-----------|
| 260322-0jj | Update GitHub Actions release workflow to trigger on pushed tags | 2026-03-21 | 2e43d87 | [260322-0jj-update-github-actions-release-workflow-t](./quick/260322-0jj-update-github-actions-release-workflow-t/) |
| Phase 03 P01 | 29min | 2 tasks | 7 files |
| Phase 03 P02 | 45min | 3 tasks | 10 files |
| Phase 04 P01 | 3min | 2 tasks | 5 files |

## Session Continuity

Last activity: 2026-03-23
Stopped at: Completed 04-01-PLAN.md
Resume file: None
