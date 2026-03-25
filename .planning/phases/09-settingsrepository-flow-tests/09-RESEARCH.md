# Phase 09: SettingsRepository Flow Tests - Research

**Researched:** 2026-03-25
**Domain:** Kotlin Multiplatform DataStore Preferences testing, Flow-based settings
**Confidence:** HIGH

## Summary

This phase tests the `SettingsRepository` flow layer end-to-end: constructing a real `SettingsRepository` with a hand-written `FakeDataStore<Preferences>`, exercising setter mutations, and asserting that the correct values are emitted from the derived `Flow` properties. The codebase already has a well-established `commonTest` testing pattern using `kotlin.test`, `kotlinx-coroutines-test`, and `mockk`, plus an existing `StoredSettingsSerializationTest` that covers JSON round-trips for the `Stored*Settings` data classes. This phase focuses on the **DataStore interaction layer** that serialization tests do not cover: write-via-setter then read-via-flow roundtrips, default values from empty DataStore, `distinctUntilChanged` deduplication, atomic multi-field mutations (`resetReaderAppearance`), complex type encoding (Color ARGB, CustomSwipeActionConfig JSON), corrupt data fallbacks, and the `currentSettings()`/`restoreSettings()` backup paths.

The `DataStore<Preferences>` interface is minimal (a `data: Flow<T>` property and a `suspend fun updateData(transform)` method), making a `FakeDataStore` straightforward to implement with a `MutableStateFlow<Preferences>` backing store. The `androidx.datastore:datastore-preferences-core` library (v1.1.1) provides `mutablePreferencesOf()` and `emptyPreferences()` factory functions that can be used directly. Turbine is **not** in the dependency graph, so flow assertions should use `first()`, `take(n).toList()`, or `drop(n).first()` from `kotlinx.coroutines.flow`.

**Primary recommendation:** Create a single `FakeDataStore` class in `commonTest` test fixtures and a single `SettingsRepositoryFlowTest.kt` test file covering all 6 categories with write-read roundtrips, defaults, deduplication, and edge cases.

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- Use a hand-written `FakeDataStore` wrapping `MutableStateFlow<Preferences>` -- KMP-compatible, no I/O, no mockk overhead
- Place `FakeDataStore` in `commonTest` test fixtures so Phase 10/11 can reuse it
- Tests go in `commonTest` (desktopTest runner), same as Phase 08 pattern
- All tests use write-read roundtrips: call setter, collect flow, assert emitted value
- All 6 settings categories covered with 2-3 representative flows each
- Verify default values when DataStore is empty (no Preferences key present)
- Verify `distinctUntilChanged`: writing the same value twice emits only once
- Include `currentSettings()` and `restoreSettings()` -- the backup/restore paths
- Representative sample per category: ~3-4 setters each (not all 30+)
- Include `resetReaderAppearance()` -- resets multiple fields atomically, regression risk
- Include complex type round-trips: null Color, non-null Color (ARGB encoding), CustomSwipeActionConfig list
- Include corrupt data edge cases: invalid enum strings in DataStore -> verify fromString fallback to defaults

### Claude's Discretion
None specified -- all decisions are locked.

### Deferred Ideas (OUT OF SCOPE)
None -- discussion stayed within phase scope.
</user_constraints>

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| kotlin-test | (matches Kotlin version) | Test annotations, assertions | Project standard, KMP-compatible |
| kotlinx-coroutines-test | (matches coroutines version) | `runTest {}`, `TestScope`, `StandardTestDispatcher` | Required for suspend/Flow testing |
| androidx.datastore:datastore-preferences-core | 1.1.1 | `DataStore<Preferences>` interface, `Preferences` API, `mutablePreferencesOf()` | Production dependency, provides test utilities |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| mockk | 1.13.12 | Available but NOT needed for this phase | Only if some dependency must be mocked (unlikely) |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| FakeDataStore | mockk-based DataStore mock | FakeDataStore has real atomicity semantics and Flow emission; mock would require manual Flow wiring -- FakeDataStore is better |
| Turbine | `first()` / `take(n).toList()` | Turbine not in deps; stdlib flow operators are sufficient for these tests |

## Architecture Patterns

### Recommended Project Structure
```
composeApp/src/commonTest/kotlin/com/karakept/app/
  data/
    repository/
      FakeDataStore.kt                      # Reusable test fixture
      SettingsRepositoryFlowTest.kt         # All flow tests for this phase
      StoredSettingsSerializationTest.kt    # (existing -- do not touch)
      BaseRepositoryTest.kt                 # (existing base class)
```

### Pattern 1: FakeDataStore Implementation
**What:** A hand-written `DataStore<Preferences>` backed by `MutableStateFlow<Preferences>` with atomic `updateData` semantics.
**When to use:** Any test that needs a real `SettingsRepository` without file I/O.
**Example:**
```kotlin
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * In-memory DataStore<Preferences> for unit tests.
 * Provides atomic updateData semantics via a Mutex, and emits
 * through a MutableStateFlow so Flow collectors see updates immediately.
 */
class FakeDataStore(
    initial: Preferences = emptyPreferences()
) : DataStore<Preferences> {

    private val _data = MutableStateFlow(initial)
    private val mutex = Mutex()

    override val data: Flow<Preferences> = _data

    override suspend fun updateData(
        transform: suspend (t: Preferences) -> Preferences
    ): Preferences = mutex.withLock {
        _data.updateAndGet { current ->
            transform(current)
        }
    }
}
```

### Pattern 2: Write-Read Roundtrip Test
**What:** Call a mutation setter, then collect the corresponding flow and assert the emitted value.
**When to use:** Every setter test.
**Example:**
```kotlin
@Test
fun setThemeMode_emitsNewThemeMode() = runTest {
    val repo = SettingsRepository(FakeDataStore())
    repo.setThemeMode(ThemeMode.DARK)
    assertEquals(ThemeMode.DARK, repo.themeMode.first())
}
```

### Pattern 3: Default Value Test
**What:** Construct SettingsRepository with empty FakeDataStore, collect a flow, assert the default.
**When to use:** Verifying each category returns correct defaults when DataStore has no data.
**Example:**
```kotlin
@Test
fun themeMode_defaultsToSystem() = runTest {
    val repo = SettingsRepository(FakeDataStore())
    assertEquals(ThemeMode.SYSTEM, repo.themeMode.first())
}
```

### Pattern 4: distinctUntilChanged Deduplication Test
**What:** Write the same value twice, verify only one emission (beyond the initial default).
**When to use:** Verifying that cross-category writes do not cause spurious emissions.
**Example:**
```kotlin
@Test
fun themeMode_writeSameValueTwice_emitsOnce() = runTest {
    val repo = SettingsRepository(FakeDataStore())
    val emissions = mutableListOf<ThemeMode>()
    val job = launch(UnconfinedTestDispatcher(testScheduler)) {
        repo.themeMode.toCollection(emissions)
    }
    repo.setThemeMode(ThemeMode.DARK)
    repo.setThemeMode(ThemeMode.DARK) // duplicate
    runCurrent()
    // Initial default + one change = 2, NOT 3
    assertEquals(2, emissions.size)
    assertEquals(ThemeMode.SYSTEM, emissions[0]) // default
    assertEquals(ThemeMode.DARK, emissions[1])   // the one change
    job.cancel()
}
```

### Pattern 5: Corrupt Data Fallback Test
**What:** Pre-seed the FakeDataStore with an invalid enum string in the Preferences key, then read the flow and assert it falls back to the default.
**When to use:** Verifying `fromString` fallback behavior through the full SettingsRepository pipeline.
**Example:**
```kotlin
@Test
fun themeMode_corruptData_fallsBackToDefault() = runTest {
    val corruptPrefs = mutablePreferencesOf().apply {
        // Write a corrupt theme settings JSON with an invalid themeMode value
        this[stringPreferencesKey("settings_theme_json")] =
            """{"themeMode":"INVALID_VALUE","accentColor":"PURPLE"}"""
    }
    val repo = SettingsRepository(FakeDataStore(corruptPrefs))
    // fromString should fall back to ThemeMode.SYSTEM
    assertEquals(ThemeMode.SYSTEM, repo.themeMode.first())
}
```

### Anti-Patterns to Avoid
- **Mocking SettingsRepository itself:** Tests must use a real SettingsRepository with FakeDataStore -- not a mock. Extension functions access internal members.
- **Testing all 30+ setters:** The context specifies a representative sample of ~3-4 per category. Do not test every single setter.
- **Duplicating StoredSettingsSerializationTest coverage:** JSON round-trip tests already exist. Flow tests focus on the DataStore interaction, not JSON encoding.
- **Using file-based DataStore in tests:** Would introduce I/O, platform-specific paths, and flakiness. FakeDataStore is the mandated approach.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Preferences factory | Custom Preferences implementation | `emptyPreferences()` and `mutablePreferencesOf()` from DataStore lib | Already provided by the library, handles all internal contracts |
| Flow testing with timeouts | Custom collect-with-timeout wrappers | `first()`, `take(n).toList()`, `UnconfinedTestDispatcher` | Standard coroutines-test APIs handle this correctly |
| Atomic DataStore updates | Manual lock-free approaches | `Mutex` in FakeDataStore + `MutableStateFlow.updateAndGet` | Matches real DataStore atomicity semantics |

## Common Pitfalls

### Pitfall 1: MutablePreferences vs Preferences in FakeDataStore
**What goes wrong:** `DataStore.edit {}` expects the transform to receive a `MutablePreferences`, but `MutableStateFlow<Preferences>` stores immutable `Preferences`.
**Why it happens:** The `edit` extension function from DataStore calls `updateData` internally, and the transform casts `Preferences` to `MutablePreferences`. The `emptyPreferences()` function returns an instance that is already `MutablePreferences` under the hood, so the cast succeeds.
**How to avoid:** Initialize FakeDataStore with `emptyPreferences()` (default) -- the returned instance supports mutation. When pre-seeding, use `mutablePreferencesOf()` which also returns a `MutablePreferences`.
**Warning signs:** `ClassCastException: Preferences cannot be cast to MutablePreferences` at runtime.

### Pitfall 2: StateFlow Conflation Hiding Emissions
**What goes wrong:** `MutableStateFlow` conflates rapid emissions, so intermediate values may be lost.
**Why it happens:** Two quick writes may conflate if the collector is not fast enough.
**How to avoid:** For deduplication tests, use `UnconfinedTestDispatcher` so emissions are processed eagerly. For simple roundtrip tests, `first()` after the write is sufficient.
**Warning signs:** Test expects N emissions but gets fewer.

### Pitfall 3: edit() Extension Function Availability
**What goes wrong:** The `edit {}` extension on `DataStore<Preferences>` is defined in `androidx.datastore.preferences.core` and calls `updateData` internally. The `FakeDataStore.updateData` must handle the internal `toMutablePreferences()` / `toPreferences()` calls correctly.
**Why it happens:** `edit` calls `updateData { prefs -> prefs.toMutablePreferences().also { transform(it) }.toPreferences() }`. The FakeDataStore just needs `updateData` to work.
**How to avoid:** The FakeDataStore's `updateData` stores whatever the transform returns. Since `edit` does the mutable-to-immutable conversion internally, FakeDataStore does not need special handling.
**Warning signs:** None if `updateData` is implemented correctly.

### Pitfall 4: Internal Visibility of Stored*Settings Classes
**What goes wrong:** `StoredThemeSettings`, `StoredDisplaySettings`, etc. and the `readThemeSettings()` helpers are declared `internal`, so they are accessible from `commonTest` within the same module.
**Why it happens:** Kotlin `internal` visibility means same-module access. Since tests are in the same module (`composeApp`), this is fine.
**How to avoid:** No action needed -- tests can access these classes. For corrupt data tests, directly write to the preferences key (also `internal`).
**Warning signs:** Compilation errors would indicate a module boundary issue (which should not occur here).

### Pitfall 5: Color ARGB Encoding in Round-Trips
**What goes wrong:** `Color(argbInt)` constructor uses the full ARGB integer. If a test uses `0xFF0000` (no alpha), the resulting Color will have 0 alpha and appear transparent.
**Why it happens:** Compose `Color` uses ARGB format where the top 8 bits are alpha.
**How to avoid:** Always use full ARGB values like `0xFFFF0000.toInt()` (opaque red) in tests. The `toArgb()` extension returns the full ARGB int.
**Warning signs:** Test assertion fails because `Color(0xFF0000)` is not equal to expected color with full alpha.

## Code Examples

### FakeDataStore for Reuse
```kotlin
// File: commonTest/.../data/repository/FakeDataStore.kt
package com.karakept.app.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class FakeDataStore(
    initial: Preferences = emptyPreferences()
) : DataStore<Preferences> {

    private val _data = MutableStateFlow(initial)
    private val mutex = Mutex()

    override val data: Flow<Preferences> = _data

    override suspend fun updateData(
        transform: suspend (t: Preferences) -> Preferences
    ): Preferences = mutex.withLock {
        _data.updateAndGet { current -> transform(current) }
    }
}
```

### Test Structure Example
```kotlin
// File: commonTest/.../data/repository/SettingsRepositoryFlowTest.kt
class SettingsRepositoryFlowTest {

    private lateinit var fakeDataStore: FakeDataStore
    private lateinit var repo: SettingsRepository

    @BeforeTest
    fun setup() {
        fakeDataStore = FakeDataStore()
        repo = SettingsRepository(fakeDataStore)
    }

    // -- Theme category ------------------------------------------------
    @Test
    fun themeMode_defaultsToSystem() = runTest {
        assertEquals(ThemeMode.SYSTEM, repo.themeMode.first())
    }

    @Test
    fun setThemeMode_dark_emitsDark() = runTest {
        repo.setThemeMode(ThemeMode.DARK)
        assertEquals(ThemeMode.DARK, repo.themeMode.first())
    }

    // -- currentSettings / restoreSettings ----------------------------
    @Test
    fun currentSettings_returnsAllDefaults_whenEmpty() = runTest {
        val settings = repo.currentSettings()
        assertEquals(ThemeMode.SYSTEM.name, settings.themeMode)
        assertEquals(LayoutType.LIST.name, settings.layoutType)
        // ... etc
    }

    @Test
    fun restoreSettings_thenCurrentSettings_roundTrips() = runTest {
        val custom = BackupSettings(
            themeMode = ThemeMode.DARK.name,
            layoutType = LayoutType.CARD.name,
            // ... other non-default values
        )
        repo.restoreSettings(custom)
        val restored = repo.currentSettings()
        assertEquals(custom.themeMode, restored.themeMode)
        assertEquals(custom.layoutType, restored.layoutType)
    }
}
```

### Complex Type Round-Trip: Color
```kotlin
@Test
fun setHtmlTextColor_nonNull_emitsCorrectColor() = runTest {
    val color = Color(0xFFFF0000.toInt()) // opaque red
    repo.setHtmlTextColor(color)
    assertEquals(color, repo.htmlTextColor.first())
}

@Test
fun setHtmlTextColor_null_emitsNull() = runTest {
    repo.setHtmlTextColor(Color(0xFF00FF00.toInt()))
    repo.setHtmlTextColor(null)
    assertNull(repo.htmlTextColor.first())
}
```

### Complex Type Round-Trip: CustomSwipeActionConfig
```kotlin
@Test
fun setCustomSwipeActionConfigs_roundTrips() = runTest {
    val configs = listOf(
        CustomSwipeActionConfig(
            id = "cfg-1",
            type = CustomSwipeActionType.ADD_TAG,
            tagName = "important"
        ),
        CustomSwipeActionConfig(
            id = "cfg-2",
            type = CustomSwipeActionType.ADD_TO_LIST,
            listId = "list-42",
            listName = "Reading List"
        )
    )
    repo.setCustomSwipeActionConfigs(configs)
    assertEquals(configs, repo.customSwipeActionConfigs.first())
}
```

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | kotlin.test + kotlinx-coroutines-test (KMP) |
| Config file | composeApp/build.gradle.kts (commonTest dependencies) |
| Quick run command | `./gradlew :composeApp:desktopTest --tests "com.karakept.app.data.repository.SettingsRepositoryFlowTest" -x javaDoc` |
| Full suite command | `./gradlew :composeApp:desktopTest -x javaDoc` |

### Phase Requirements -> Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| FLOW-01 | Default values for all 6 categories from empty DataStore | unit | `./gradlew :composeApp:desktopTest --tests "*SettingsRepositoryFlowTest.*defaults*"` | Wave 0 |
| FLOW-02 | Write-read roundtrips for representative setters per category | unit | `./gradlew :composeApp:desktopTest --tests "*SettingsRepositoryFlowTest.*set*"` | Wave 0 |
| FLOW-03 | distinctUntilChanged deduplication (same value twice = 1 emission) | unit | `./gradlew :composeApp:desktopTest --tests "*SettingsRepositoryFlowTest.*dedup*"` | Wave 0 |
| FLOW-04 | resetReaderAppearance() atomically resets multiple fields | unit | `./gradlew :composeApp:desktopTest --tests "*SettingsRepositoryFlowTest.*resetReader*"` | Wave 0 |
| FLOW-05 | Complex type round-trips (Color ARGB, CustomSwipeActionConfig) | unit | `./gradlew :composeApp:desktopTest --tests "*SettingsRepositoryFlowTest.*complex*"` | Wave 0 |
| FLOW-06 | Corrupt data fallback via fromString defaults | unit | `./gradlew :composeApp:desktopTest --tests "*SettingsRepositoryFlowTest.*corrupt*"` | Wave 0 |
| FLOW-07 | currentSettings() returns all defaults when empty | unit | `./gradlew :composeApp:desktopTest --tests "*SettingsRepositoryFlowTest.*currentSettings*"` | Wave 0 |
| FLOW-08 | restoreSettings() -> currentSettings() round-trip | unit | `./gradlew :composeApp:desktopTest --tests "*SettingsRepositoryFlowTest.*restore*"` | Wave 0 |

### Sampling Rate
- **Per task commit:** `./gradlew :composeApp:desktopTest --tests "com.karakept.app.data.repository.SettingsRepositoryFlowTest" -x javaDoc`
- **Per wave merge:** `./gradlew :composeApp:desktopTest -x javaDoc`
- **Phase gate:** Full desktopTest suite green before `/gsd:verify-work`

### Wave 0 Gaps
- [ ] `commonTest/kotlin/com/karakept/app/data/repository/FakeDataStore.kt` -- reusable test fixture
- [ ] `commonTest/kotlin/com/karakept/app/data/repository/SettingsRepositoryFlowTest.kt` -- all flow tests

## Open Questions

1. **MutablePreferences casting in FakeDataStore**
   - What we know: `DataStore.edit {}` internally calls `updateData` with a transform that creates a `MutablePreferences` copy. The FakeDataStore just stores the result.
   - What's unclear: Whether `emptyPreferences()` returns an instance whose `toMutablePreferences()` works correctly in all KMP targets.
   - Recommendation: Verify during implementation by running the first test. If casting fails, the FakeDataStore transform must call `toMutablePreferences()` explicitly. This is LOW risk since the same API is used in production.

## Sources

### Primary (HIGH confidence)
- Direct code inspection of `SettingsRepository.kt`, `SettingsRepositoryMutations.kt`, `StoredSettings.kt` in the project
- Direct code inspection of `StoredSettingsSerializationTest.kt`, `BookmarkActionsRepositorySyncTest.kt`, `BaseRepositoryTest.kt`
- `gradle/libs.versions.toml` for dependency versions (datastore 1.1.1, mockk 1.13.12)
- `composeApp/build.gradle.kts` for test source set configuration

### Secondary (MEDIUM confidence)
- DataStore Preferences API (`emptyPreferences()`, `mutablePreferencesOf()`, `edit {}`) -- from training knowledge of androidx.datastore:datastore-preferences-core 1.1.x API, verified against import statements in production code

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH -- all dependencies verified in build files
- Architecture: HIGH -- FakeDataStore pattern is straightforward; all internal APIs inspected
- Pitfalls: HIGH -- based on direct code inspection of the mutation/flow layer
- Test mapping: HIGH -- every test directly maps to a CONTEXT.md decision

**Research date:** 2026-03-25
**Valid until:** 2026-04-25 (stable -- DataStore API is mature)
