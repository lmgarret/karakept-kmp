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

## Cross-Milestone Trends

### Process Evolution

| Milestone | Phases | Plans | Key Change |
|-----------|--------|-------|------------|
| v1.7.0 | 2 | 3 | Established GSD workflow baseline |
| v1.8.0 | 5 | 10 | Added Wave 0 TDD, pure function extraction pattern, Robolectric infrastructure |
| v1.9.0 | 4 | 7 | Coverage sprint: real-object pattern, FakeDataStore, internal function extraction |

### Cumulative Quality

| Milestone | Test Files | Test Lines | Notes |
|-----------|-----------|------------|-------|
| v1.7.0 | ~26 | ~5,800 | Existing desktopTest + commonTest coverage |
| v1.8.0 | 36 | ~7,254 | Added androidUnitTest infrastructure, ScreenModel + Compose UI tests |
| v1.9.0 | 43+ | ~9,200+ | Coverage sprint: utilities, action layer, sync pipeline, settings, UI components, backup |

### Top Lessons (Verified Across Milestones)

1. **Small, independent phases execute faster and with fewer blockers** — all 3 milestones validated this; resist the urge to batch unrelated work
2. **Pure function extraction pays test dividends immediately** — first established in v1.8.0, confirmed in v1.9.0; should be default practice going forward
3. **Dedicated coverage sprints are more efficient than interspersed test phases** — v1.9.0 proved that a focused "no features, only tests" milestone is clean and fast to plan and execute
