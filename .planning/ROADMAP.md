# Roadmap: Karakept KMP

## Milestones

- ✅ **v1.7.0 Tech Debt Cleanup** — Phases 01-02 (shipped 2026-03-21)
- ✅ **v1.8.0 Bug Fixes & UX Improvements** — Phases 03-07 (shipped 2026-03-25)
- ✅ **Platform Health (internal, no release tag)** — Phases 08-11 (archived 2026-03-25)
- 🚧 **v1.9.0 Bug Fixes & UX Polish** — Phases 12-22 (in progress)

## Phases

<details>
<summary>✅ v1.7.0 Tech Debt Cleanup (Phases 01-02) — SHIPPED 2026-03-21</summary>

- [x] Phase 01: Println Cleanup (2/2 plans) — completed 2026-03-21
- [x] Phase 02: File Trimming & Quality (1/1 plan) — completed 2026-03-21

</details>

<details>
<summary>✅ v1.8.0 Bug Fixes & UX Improvements (Phases 03-07) — SHIPPED 2026-03-25</summary>

- [x] Phase 03: Reader UX (2/2 plans) — completed 2026-03-23
- [x] Phase 04: Bookmark Saving Activity (1/1 plan) — completed 2026-03-23
- [x] Phase 05: List & Sync (3/3 plans) — completed 2026-03-23
- [x] Phase 06: Selection & Filtering (2/2 plans) — completed 2026-03-23
- [x] Phase 07: UI Tests (2/2 plans) — completed 2026-03-24

</details>

<details>
<summary>✅ Platform Health (Phases 08-11) — ARCHIVED 2026-03-25 (no release tag)</summary>

- [x] Phase 08: Test Coverage Expansion (3/3 plans) — completed 2026-03-25
- [x] Phase 09: SettingsRepository Flow Tests (1/1 plan) — completed 2026-03-25
- [x] Phase 10: Dialog and Component UI Tests (2/2 plans) — completed 2026-03-25
- [x] Phase 11: BackupRepository Edge Case Tests (1/1 plan) — completed 2026-03-25

</details>

<details open>
<summary>🚧 v1.9.0 Bug Fixes & UX Polish (Phases 12-22) — IN PROGRESS</summary>

- [ ] Phase 12: Notification Fixes — NOTIF-01, NOTIF-02 (#169, #170) — **Plans:** 1 plan
  Plans:
  - [ ] 12-01-PLAN.md — Fix digest notification count + add per-list notification
- [ ] Phase 13: Smart List & Saving Follow-ups — SAVE-02, LIST-02 (#163, #165) — **Plans:** 3 plans
  Plans:
  - [x] 13-01-PLAN.md — Fix MainScreenModel init stall in secondary Activity (SAVE-02)
  - [x] 13-02-PLAN.md — Add smart list sync after list-membership quick actions (LIST-02)
  - [x] 13-03-PLAN.md — Fix ForList sync pipeline list membership reconciliation (LIST-02 gap closure)
- [ ] Phase 14: UI Interaction Fixes — FILT-04, UI-01 (#164, #168) — **Plans:** 2 plans
  Plans:
  - [x] 14-01-PLAN.md — Fix highlights PTR + scroll-to-top position bug
  - [ ] 14-02-PLAN.md — Migrate deprecated pullRefresh to MD3 PullToRefreshBox
- [ ] Phase 15: Snackbar Undo System — UX-01, NFR-01 (#166) — **Plans:** 2 plans
  Plans:
  - [ ] 15-01-PLAN.md — Wire undo snackbars to MainScreen swipe, bottom sheet, and batch actions
  - [ ] 15-02-PLAN.md — Wire undo snackbars to BookmarkViewer actions + regression tests
- [ ] Phase 16: Custom Layout Improvements — UX-02 (#167) — **Plans:** 4 plans
  Plans:
  - [x] 16-01-PLAN.md — Data model foundation: new enums, BookmarkLayout fields, COMPACT_LIST migration, URL utility (TDD)
  - [x] 16-02-PLAN.md — Rendering pipeline: update renderers, wire display config, delete COMPACT_LIST code paths
  - [x] 16-03-PLAN.md — Editor UI: new toggles/sections + per-list picker "Create new layout" button
  - [x] 16-04-PLAN.md — Gap closure: configurable URL icon mode (globe vs favicon)
- [ ] Phase 17: Desktop 401 Auth Fix — AUTH-01 (#173)
- [ ] Phase 18: Pre Diagram Rendering Fix — RENDER-01 (#171)
- [ ] Phase 19: Scroll-to-top for List Views — UI-02 (#172)
- [ ] Phase 20: Home List Auto-expand in Drawer — NAV-01 (#174)
- [ ] Phase 21: Desktop List Settings in Second Panel — DESK-01 (#175)
- [ ] Phase 22: Dev Release Icon & Label Differentiation — DEV-01 (#177) — **Plans:** 1 plan
  Plans:
  - [x] 22-01-PLAN.md — Cherry-pick and validate DEV icon/label draft implementation

</details>

## Progress

| Phase | Milestone | Plans Complete | Status | Completed |
|-------|-----------|----------------|--------|-----------|
| 01. Println Cleanup | v1.7.0 | 2/2 | Complete | 2026-03-21 |
| 02. File Trimming & Quality | v1.7.0 | 1/1 | Complete | 2026-03-21 |
| 03. Reader UX | v1.8.0 | 2/2 | Complete | 2026-03-23 |
| 04. Bookmark Saving Activity | v1.8.0 | 1/1 | Complete | 2026-03-23 |
| 05. List & Sync | v1.8.0 | 3/3 | Complete | 2026-03-23 |
| 06. Selection & Filtering | v1.8.0 | 2/2 | Complete | 2026-03-23 |
| 07. UI Tests | v1.8.0 | 2/2 | Complete | 2026-03-24 |
| 08. Test Coverage Expansion | Platform Health | 3/3 | Complete | 2026-03-25 |
| 09. SettingsRepository Flow Tests | Platform Health | 1/1 | Complete | 2026-03-25 |
| 10. Dialog and Component UI Tests | Platform Health | 2/2 | Complete | 2026-03-25 |
| 11. BackupRepository Edge Case Tests | Platform Health | 1/1 | Complete | 2026-03-25 |
| 12. Notification Fixes | v1.9.0 | 0/1 | Complete    | 2026-03-26 |
| 13. Smart List & Saving Follow-ups | v1.9.0 | 3/3 | Complete    | 2026-03-26 |
| 14. UI Interaction Fixes | v1.9.0 | 1/2 | Complete    | 2026-03-28 |
| 15. Snackbar Undo System | v1.9.0 | 0/2 | Complete    | 2026-03-28 |
| 16. Custom Layout Improvements | v1.9.0 | 4/4 | Complete   | 2026-03-30 |
| 17. Desktop 401 Auth Fix | v1.9.0 | 0/? | Planned | — |
| 18. Pre Diagram Rendering Fix | v1.9.0 | 0/? | Planned | — |
| 19. Scroll-to-top for List Views | v1.9.0 | 0/? | Planned | — |
| 20. Home List Auto-expand in Drawer | v1.9.0 | 0/? | Planned | — |
| 21. Desktop List Settings in Second Panel | v1.9.0 | 0/? | Planned | — |
| 22. Dev Release Icon & Label Differentiation | v1.9.0 | 1/1 | Complete    | 2026-04-01 |

### Phase 22: Dev Release Icon & Label Differentiation — DEV-01 (#177)

**Goal:** Add a distinct app icon and "DEV" label for dev/debug builds so users can distinguish dev from production installs
**Requirements**: DEV-01
**Depends on:** None
**Plans:** 1/1 plans complete

Plans:
- [ ] 22-01-PLAN.md — Cherry-pick and validate DEV icon/label draft implementation
