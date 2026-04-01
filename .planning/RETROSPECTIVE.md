# Project Retrospective

*A living document updated after each milestone. Lessons feed forward into future planning.*

## Milestone: v1.8.0 — Bug Fixes & UX Improvements

**Shipped:** 2026-03-25
**Phases:** 5 (03-07) | **Plans:** 10 | **Tasks:** 21

### What Was Built
- Reader UX polish: scroll position persistence, info button in overflow menu, scroll-to-top FAB with settings toggle
- `BookmarkSavingActivity` rewrite: self-contained Android share target with singleTask launch, fresh state on each share
- List & sync fixes: optimistic removal update + per-list offline sync with child list hierarchy expansion
- Selection & filtering: select-all beyond page 1, reactive drawer counters, pull-to-refresh on Highlights
- Test infrastructure: Robolectric 4.14 + compose-ui-test in `androidUnitTest`, 13 ScreenModel + 9 UI/Activity tests

### What Worked
- **Pure function extraction for testability**: pulling `applyRemoveBookmarkTransform` out of the ScreenModel enabled direct unit tests without any Voyager/Koin wiring — clean pattern to repeat
- **Wave 0 TDD**: writing failing test scaffolds before production code in Phase 05 caught spec ambiguities early
- **ScreenModel-centric tests with mockk direct construction**: avoids the full DI graph, tests run fast and stay focused
- **Independent phase ordering**: all 5 phases were independent, enabling flexible sequencing without inter-phase blockers

### What Was Inefficient
- **Stale traceability table**: LIST-01 and LIST-02 traceability rows not updated after Phase 05 completed — manual debt that surfaced at milestone close
- **Phase 07 added late**: UI test infrastructure was added as a separate phase rather than planned from the start; some Phase 05 tests had to be rewritten when the full infrastructure landed

### Patterns Established
- Robolectric `@Config(sdk = [34])` + `createComposeRule()` as the standard androidUnitTest setup
- `key(intentKey)` pattern for forcing fresh Compose navigator state on Android `onNewIntent`
- Pure top-level function extraction for business logic that needs unit testing in isolation from Voyager/Koin
- `Modifier.weight(1f)` on label Text for right-aligned counts in drawer items without Row restructuring

### Key Lessons
1. **Test infrastructure belongs in the first planning wave** — retrofitting it as a final phase created rework; include test scaffolds as Wave 0 tasks in every phase with meaningful logic
2. **Traceability tables need updating at phase completion**, not at milestone close — add a traceability update step to the phase completion checklist
3. **Robolectric 4.14 has a ModalBottomSheet click bug** — performClick() silently fails inside bottom sheets on SDK 29-34; upgrade to 4.15.1 before writing bottom sheet tests

### Cost Observations
- Model mix: ~80% sonnet, ~20% opus (research and retrospective agents)
- Sessions: ~8 sessions across 5 phases
- Notable: Phase 04 (1 plan, 5 files) executed in ~3 minutes — well-scoped single-concern phases are extremely efficient

---

## Milestone: v1.9.0 — Platform Health

**Shipped:** 2026-03-25
**Phases:** 4 (08-11) | **Plans:** 7

### What Was Built
- 60 unit tests for 7 pure utility/model files (ReadingTimeCalculator, HtmlSanitizer, DateUtils, FaviconUtils, AssetUrlUtils, HighlightOffsetFinder, ListSyncConfig)
- 28 unit tests for BookmarkActionController (all 9 action types) and BookmarkActionsRepositorySync (sync processing, retry logic)
- 25 unit tests for BookmarkSyncPipeline covering all 3 sync configurations, differential sync, content sync strategy dispatch
- 53 flow-level tests for SettingsRepository across all 8 settings categories via FakeDataStore
- 29 tests from extracted pure functions (filterTagSuggestions, canAddTag, parseTagString) + TagChip Compose UI tests
- 6 BackupRepository edge-case tests covering blank PIN, setBackupPin branches, malformed JSON, scheduled export, exception swallow

### What Worked
- **Real-object pattern for extension functions**: when extension functions access internal members (actionMutex, pendingActionDao), constructing a real instance with mocked deps is far cleaner than fighting MockK — should be the default approach
- **FakeDataStore with MutableStateFlow + Mutex**: clean, reusable fixture that accurately replicates DataStore semantics without the real implementation's background coroutine complexity
- **Internal top-level function extraction**: marking extracted composable logic as `internal` enables commonTest import without Compose runtime — solves the "testable UI logic" problem cleanly
- **Phase-per-subsystem structure**: each phase targeted a clean boundary (utilities, actions, sync, settings, UI components, backup) — no inter-phase interference, parallelizable

### What Was Inefficient
- **MockK relaxed mock failure discovery at test execution time**: the BookmarkSyncPipeline relaxed mock issue wasn't caught until runtime — a pre-plan check for extension functions accessing internals would save time
- **Phase 09 FakeDataStore not reused in Phase 10/11**: the FakeDataStore was created as a Phase 09 artifact but phases 10 and 11 didn't need DataStore, so the reusability assumption wasn't tested

### Patterns Established
- Real-object pattern: construct real instances with mocked deps for classes with internal extension functions
- FakeDataStore: `MutableStateFlow<Map<Preferences.Key<*>, Any>>` + `Mutex` for atomic DataStore test fixture
- `internal` top-level functions for extracting composable logic testable in commonTest
- `UnconfinedTestDispatcher + toList()` for emission-counting tests on Flow with `distinctUntilChanged`

### Key Lessons
1. **Check for extension functions accessing internal members before planning MockK strategy** — if present, plan for real-object construction from the start
2. **Coverage milestones are best done in one pass** — splitting test work across many milestones creates context overhead; better to do a dedicated "platform health" sprint all at once
3. **Robolectric 4.14 has a ModalBottomSheet click bug** — document and defer bottom sheet tests until 4.15.1 is upgraded

### Cost Observations
- Model mix: ~85% sonnet (execution), ~15% opus (planning/research agents)
- Sessions: ~5 sessions across 4 phases
- Notable: Phase 09 (53 tests, 1 plan) was the most test-dense single plan — FakeDataStore setup cost amortized well across 53 assertions

---

## Milestone: v1.9.0 — Bug Fixes & UX Polish

**Shipped:** 2026-04-01
**Phases:** 6 (12-16, 22) | **Plans:** 13

### What Was Built
- Notification fixes: digest notification count via return value propagation, per-list notifications via post-sync DB query
- Smart list sync: computeStaleListRemovals for ForList reconciliation, MainScreenModel factory{} scope fix
- UI interaction polish: MD3 PullToRefreshBox migration (all deprecated pullRefresh removed), explicit scroll-to-top offset fix
- Undo snackbar system: shared undoableAction helper wired to 12 reversible action sites across main screen and bookmark viewer
- Custom layout redesign: description/URL toggles with position controls, favicon support via globe fallback, COMPACT_LIST merged into LIST, "Create new layout" in per-list picker
- DEV build differentiation: Android adaptive icon ribbon, Desktop -Dkarakept.dev mode, cross-platform isDevBuild expect/actual

### What Worked
- **undoableAction as CoroutineScope extension**: clean reuse across 4+ call sites, named parameter onUndo= syntax avoided Kotlin trailing lambda ambiguity
- **PullToRefreshBox wrapping entire Scaffold content**: consistent PTR gesture across all states (loaded, empty, error) without duplication
- **Globe icon behind favicon AsyncImage in Box**: natural fallback without onError callbacks — clean composable pattern
- **TDD across Phase 16**: 26 tests written before implementation for data model foundation; all 4 plans followed RED-GREEN cycle
- **Cherry-pick strategy for Phase 22**: adopting a draft implementation from another branch saved significant implementation time

### What Was Inefficient
- **5 phases deferred (17-21)**: requirements added to ROADMAP after REQUIREMENTS.md was finalized — should define requirements before expanding scope
- **Phase plan checkboxes vs actual execution**: several ROADMAP plan entries showed [ ] while SUMMARY.md existed — progress table was more accurate than plan checkbox state
- **Stale VERIFICATION.md references**: 3 phases had verification docs referencing pre-refactor method names (post-execution refactors not reflected)

### Patterns Established
- undoableAction CoroutineScope extension for reusable undo snackbar pattern
- Content lambda extraction for PullToRefreshBox/Box conditional wrapping
- Globe-behind-favicon Box pattern for composable icon fallback
- isDevBuild expect/actual for cross-platform build flavor detection
- @Deprecated enum value with fromString() redirect for deserialization backward compat

### Key Lessons
1. **Define requirements before expanding roadmap scope** — phases 17-21 were added ad hoc and never formalized, creating audit gaps at milestone close
2. **Cherry-pick from draft branches when available** — Phase 22 showed this can collapse implementation to validation-only
3. **VERIFICATION.md should be updated after post-phase refactors** — 3 phases had stale method references; add a "verify docs" step after any refactor touching verified code

### Cost Observations
- Model mix: ~70% sonnet (execution), ~30% opus (planning/research/retrospective)
- Sessions: ~10 sessions across 6 phases
- Notable: Phase 16 (4 plans, most complex) benefited from TDD — each plan was self-contained with clear pass/fail criteria

---

## Cross-Milestone Trends

### Process Evolution

| Milestone | Phases | Plans | Key Change |
|-----------|--------|-------|------------|
| v1.7.0 | 2 | 3 | Established GSD workflow baseline |
| v1.8.0 | 5 | 10 | Added Wave 0 TDD, pure function extraction pattern, Robolectric infrastructure |
| Platform Health | 4 | 7 | Coverage sprint: real-object pattern, FakeDataStore, internal function extraction |
| v1.9.0 | 6 | 13 | Undo system, custom layout redesign, MD3 migration, DEV build differentiation |

### Cumulative Quality

| Milestone | Test Files | Test Lines | Notes |
|-----------|-----------|------------|-------|
| v1.7.0 | ~26 | ~5,800 | Existing desktopTest + commonTest coverage |
| v1.8.0 | 36 | ~7,254 | Added androidUnitTest infrastructure, ScreenModel + Compose UI tests |
| Platform Health | 43+ | ~9,200+ | Coverage sprint: utilities, action layer, sync pipeline, settings, UI components, backup |
| v1.9.0 | 43+ | ~9,200+ | Regression tests for all bug fixes + new features |

### Top Lessons (Verified Across Milestones)

1. **Small, independent phases execute faster and with fewer blockers** — all 4 milestones validated this; resist the urge to batch unrelated work
2. **Pure function extraction pays test dividends immediately** — established v1.8.0, confirmed Platform Health + v1.9.0; default practice
3. **Dedicated coverage sprints are more efficient than interspersed test phases** — Platform Health proved this; v1.9.0 tests were per-feature rather than sprint-based
4. **Define requirements before expanding roadmap scope** — v1.9.0 deferred 5 phases because they were added without formal requirements; always update REQUIREMENTS.md first
