# Roadmap: Karakept KMP — v1.7.0 Tech Debt Cleanup

## Overview

This milestone cleans up tech debt identified during the code-health milestone audit. The println cleanup is the bulk of the work (~97 calls across 7 files), while the file trimming and redundant inject removal are minor targeted fixes. Logging cleanup comes first since it touches the most files; size/quality fixes follow as a single focused phase.

## Phases

- [x] **Phase 1: Println Cleanup** — Replace all remaining println debug calls with AppLogger or remove them (completed 2026-03-21)
- [ ] **Phase 2: File Trimming & Quality** — Trim oversized files below 500 lines and remove redundant koinInject

## Phase Details

### Phase 1: Println Cleanup
**Goal**: All production debug output uses AppLogger — no raw println remains
**Depends on**: Nothing
**Requirements**: LOG-01
**Plans:** 2/2 plans complete
**Success Criteria** (what must be TRUE):
  1. Zero `println` calls remain in production source files (test files excluded)
  2. Replacement calls use appropriate `AppLogger` severity levels (`.d` for debug, `.e` for errors, `.w` for warnings)
  3. Hot-path logging (called per-item in loops) is removed entirely rather than replaced, per code-health Phase 6 decision

Plans:
- [x] 01-01-PLAN.md — Replace println in BookmarkActionsRepositorySync (consolidated) and BookmarkActionsRepository
- [x] 01-02-PLAN.md — Replace println in BookmarkRepository, HighlightRepository, ImageCacheManager, HtmlRenderer, ViewerScrollRestoration

### Phase 2: File Trimming & Quality
**Goal**: All production files are under 500 lines and no redundant DI calls exist
**Depends on**: Phase 1 (println removal may reduce file sizes)
**Requirements**: SIZE-01, SIZE-02, QUAL-01
**Plans:** 1 plan

**Success Criteria** (what must be TRUE):
  1. `BookmarkSyncPipeline.kt` is under 500 lines
  2. `SettingsRepositoryMutations.kt` is under 500 lines
  3. `App.kt` has exactly one `koinInject<ServerRepository>()` call

Plans:
- [x] 02-01-PLAN.md — Remove redundant koinInject in App.kt and verify file size targets

## Progress

**Execution Order:**
Phases execute in numeric order: 1 -> 2

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 1. Println Cleanup | 2/2 | Complete   | 2026-03-21 |
| 2. File Trimming & Quality | 0/1 | Not started | - |
