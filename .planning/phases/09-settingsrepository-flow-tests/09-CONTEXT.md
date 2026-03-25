# Phase 09: SettingsRepository flow tests - Context

**Gathered:** 2026-03-25
**Status:** Ready for planning

<domain>
## Phase Boundary

Test the SettingsRepository flow layer: all 6 settings categories (theme, display, reader, swipe, sync, app), write→read roundtrips via a FakeDataStore, default value correctness, distinctUntilChanged deduplication, representative mutation setters per category, resetReaderAppearance(), complex type round-trips (Color, CustomSwipeActionConfig), and corrupt data fallback via fromString defaults. Also covers currentSettings() and restoreSettings() as the backup/restore paths.

</domain>

<decisions>
## Implementation Decisions

### DataStore Test Strategy
- Use a hand-written `FakeDataStore` wrapping `MutableStateFlow<Preferences>` — KMP-compatible, no I/O, no mockk overhead
- Place `FakeDataStore` in `commonTest` test fixtures so Phase 10/11 can reuse it
- Tests go in `commonTest` (desktopTest runner), same as Phase 08 pattern
- All tests use write→read roundtrips: call setter, collect flow, assert emitted value

### Coverage Scope
- All 6 settings categories covered with 2-3 representative flows each
- Verify default values when DataStore is empty (no Preferences key present)
- Verify `distinctUntilChanged`: writing the same value twice emits only once
- Include `currentSettings()` and `restoreSettings()` — the backup/restore paths

### Mutations Coverage
- Representative sample per category: ~3-4 setters each (not all 30+)
- Include `resetReaderAppearance()` — resets multiple fields atomically, regression risk
- Include complex type round-trips: null Color, non-null Color (ARGB encoding), CustomSwipeActionConfig list
- Include corrupt data edge cases: invalid enum strings in DataStore → verify fromString fallback to defaults

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- `StoredSettingsSerializationTest` — existing test for model serialization, do not duplicate
- `BookmarkActionsRepositorySyncTest` — established real-object-with-mocked-deps pattern
- `BackupRepositoryTest` — uses SQLDelight in-memory DB; analogous fake pattern for DataStore

### Established Patterns
- `kotlin.test` annotations (`@Test`, `@BeforeTest`)
- `runTest {}` from `kotlinx-coroutines-test` for Flow collection
- `turbine` or `toList(take(N))` for Flow emission assertions
- Naming: `functionName_scenario_expectedBehavior`

### Integration Points
- `SettingsRepository(dataStore: DataStore<Preferences>)` — constructor injection, easily testable
- `SettingsRepositoryMutations.kt` — extension functions on SettingsRepository, tested via the same instance
- All mutation functions are `suspend` — test with `runTest {}`

</code_context>

<specifics>
## Specific Ideas

- `FakeDataStore` should implement `DataStore<Preferences>` with a `MutableStateFlow<Preferences>` backing store and atomic `updateData` semantics
- Test file: `SettingsRepositoryFlowTest.kt` in `commonTest/kotlin/com/karakept/app/data/repository/`

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope.

</deferred>
