# Phase 10: Dialog and Component UI Tests - Research

**Researched:** 2026-03-25
**Domain:** Compose UI testing (Robolectric) + pure Kotlin logic testing for dialog/component layer
**Confidence:** HIGH

## Summary

This phase adds automated tests for the UI component layer, focusing on TagEditorDialog, BookmarkTagsDisplay, TagChip, and ListPickerDialog. The project already has a mature test infrastructure from Phases 07-09: Robolectric 4.14 with SDK 34 for Compose UI tests in `androidUnitTest`, and pure Kotlin tests in `commonTest` using mockk and coroutines-test. No new dependencies or configuration are needed.

The key insight from analyzing the target components is that TagEditorDialog contains **extractable pure logic** (suggestion filtering, the `canAdd` gate, tag deduplication) that is currently inlined as `remember` blocks inside the composable. This logic can be tested either by extracting it into top-level pure functions (testable in `commonTest`) or by testing through the Compose UI layer (Robolectric). BookmarkTagsDisplay's tag parsing (`split(",").map { it.trim() }.filter { it.isNotBlank() }`) is similarly a pure function candidate. TagChip and ListPickerDialog are primarily render + callback tests best suited for `androidUnitTest`.

**Primary recommendation:** Create 2-3 test files: (1) a `commonTest` file testing TagEditorDialog's suggestion filtering and `canAdd` logic as extracted pure functions, (2) a `commonTest` file testing BookmarkTagsDisplay's tag parsing logic, and (3) an `androidUnitTest` file testing TagChip's render states and callback wiring via Compose semantics.

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
None -- all implementation choices are at Claude's discretion (infrastructure/testing phase).

### Claude's Discretion
All implementation choices are at Claude's discretion. Use Phase 07 (androidUnitTest + Robolectric) and Phase 08/09 (commonTest pure logic) patterns. Prioritize the highest-value components: TagEditorDialog (suggestion filtering, create-new gate), BookmarkTagsDisplay (tag rendering from comma-separated string), and TagChip (onClick/onRemove callback wiring). Aim for 2-3 test files covering meaningful logic -- not a smoke test for every component.

### Deferred Ideas (OUT OF SCOPE)
None -- discussion stayed within phase scope.
</user_constraints>

## Project Constraints (from CLAUDE.md)

- Use `TagChip` everywhere a single tag chip is displayed; use `BookmarkTagsDisplay` for tag lists from comma-separated strings
- Use `TagEditorDialog` for all tag add/remove/select flows; do not implement custom tag-selection dialogs
- Use `buildListHierarchy` for sorted hierarchical list display
- Material3 components, `MaterialTheme.*` tokens only -- never hardcode colors/styles
- UI components live in `composeApp/src/commonMain/kotlin/com/karakept/app/ui/components/`
- Tests: androidUnitTest for Compose UI, commonTest for pure logic

## Standard Stack

### Core (already configured -- no additions needed)
| Library | Version | Purpose | Source Set |
|---------|---------|---------|------------|
| compose-ui-test-junit4 | (from libs.versions.toml) | Compose UI semantics testing | androidUnitTest |
| Robolectric | 4.14 | Android framework simulation | androidUnitTest |
| mockk | (from libs.versions.toml) | Mocking for both source sets | commonTest + androidUnitTest |
| kotlinx-coroutines-test | (from libs.versions.toml) | Test dispatchers | commonTest + androidUnitTest |
| kotlin-test | (from libs.versions.toml) | Assertions | commonTest |
| JUnit 4 | (from libs.versions.toml) | Test runner | androidUnitTest |

### Installation
No new dependencies needed. All test infrastructure was established in Phases 07-09.

## Architecture Patterns

### Test File Placement
```
composeApp/src/
  commonTest/kotlin/com/karakept/app/ui/components/
    TagEditorLogicTest.kt          # Pure logic: suggestion filtering, canAdd gate
    BookmarkTagsParsingTest.kt     # Pure logic: comma-separated tag string parsing
  androidUnitTest/kotlin/com/karakept/app/ui/components/
    TagChipTest.kt                 # Compose UI: render states, callback wiring
```

### Pattern 1: Extract-and-Test Pure Logic (commonTest)

**What:** Extract inline `remember` logic from composables into top-level pure functions, then test those functions directly in commonTest without any Compose dependency.

**When to use:** When a composable contains non-trivial logic (filtering, parsing, validation) that can be separated from rendering.

**Example -- TagEditorDialog suggestion filtering:**

The composable currently has this inlined:
```kotlin
val suggestions = remember(availableTags, searchInput, tags) {
    if (searchInput.isBlank()) emptyList()
    else availableTags
        .filter { it.contains(searchInput, ignoreCase = true) && !tags.contains(it) }
        .sortedBy { it.lowercase() }
        .take(8)
}
```

Extract to a top-level function:
```kotlin
// In TagEditorDialog.kt (or a new TagEditorUtils.kt)
internal fun filterTagSuggestions(
    availableTags: List<String>,
    searchInput: String,
    currentTags: List<String>
): List<String> {
    if (searchInput.isBlank()) return emptyList()
    return availableTags
        .filter { it.contains(searchInput, ignoreCase = true) && !currentTags.contains(it) }
        .sortedBy { it.lowercase() }
        .take(8)
}
```

Test in commonTest:
```kotlin
class TagEditorLogicTest {
    @Test
    fun `filterTagSuggestions returns matching tags case-insensitively`() {
        val result = filterTagSuggestions(
            availableTags = listOf("Kotlin", "kotlin-kmp", "Java", "JavaScript"),
            searchInput = "kot",
            currentTags = emptyList()
        )
        assertEquals(listOf("Kotlin", "kotlin-kmp"), result)
    }
}
```

**Why this is the highest-value approach:** These tests run on all platforms (JVM, native) without Robolectric overhead, execute in milliseconds, and cover the core business logic. The composable itself becomes a thin wrapper that just calls the extracted function inside `remember`.

### Pattern 2: Compose UI Semantics Testing (androidUnitTest)

**What:** Use Robolectric + createComposeRule() to render a composable and assert via semantics (text, testTag, click actions).

**When to use:** When testing render output, callback wiring, or interaction behavior that requires a Compose runtime.

**Example -- TagChip:**
```kotlin
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class TagChipTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `displays tag text`() {
        composeTestRule.setContent {
            MaterialTheme { TagChip(tag = "kotlin") }
        }
        composeTestRule.onNodeWithText("kotlin").assertIsDisplayed()
    }

    @Test
    fun `remove button triggers onRemove callback`() {
        var removed = false
        composeTestRule.setContent {
            MaterialTheme { TagChip(tag = "test", onRemove = { removed = true }) }
        }
        composeTestRule.onNodeWithContentDescription("Remove tag").performClick()
        assertTrue(removed)
    }
}
```

### Pattern 3: BookmarkTagsDisplay Tag Parsing (commonTest)

**What:** The tag parsing logic `tags.split(",").map { it.trim() }.filter { it.isNotBlank() }` is extractable as a pure function.

**When to use:** When the interesting behavior is string parsing, not rendering.

**Test cases:**
- `"kotlin,android,kmp"` produces `["kotlin", "android", "kmp"]`
- `"kotlin, android , kmp"` (with spaces) still produces `["kotlin", "android", "kmp"]`
- `""` produces empty list (component returns early, no chips rendered)
- `",,"` produces empty list
- `"single"` produces `["single"]`

### Anti-Patterns to Avoid
- **Testing MaterialTheme colors/shapes:** These are theme-provided and tested by the MD3 library itself. Focus on behavior (callbacks, displayed text, visibility).
- **Heavy mocking for simple composables:** TagChip and BookmarkTagsDisplay are stateless composables. Test them by rendering directly with test data, not by mocking internal state.
- **Duplicating ListHierarchyUtils tests:** These already exist in `commonTest/kotlin/com/karakept/app/domain/ListHierarchyUtilsTest.kt`. ListPickerDialog's hierarchy display uses `buildListHierarchy` internally but the hierarchy logic itself is already covered.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Compose UI test infrastructure | Custom test helpers | `createComposeRule()` + semantics API | Already wired in Phase 07 |
| Tag suggestion filtering tests | Inline logic + Compose-level assertions | Extract to pure function + commonTest | Faster, simpler, cross-platform |
| Test data builders for KarakeepList | Complex mock setup | `KarakeepList(id = "x", name = "Y")` directly | Data class with defaults -- no mocking needed |

**Key insight:** KarakeepList is a data class with all-nullable/defaulted parameters. Unlike earlier phases that used `mockk<KarakeepList>(relaxed = true)`, tests for this phase can construct instances directly: `KarakeepList(id = "1", name = "Reading", parentId = null)`. This is cleaner and avoids mockk overhead for simple data carriers.

## Common Pitfalls

### Pitfall 1: TagChip's Remove Button Uses IconButton (Not a Clickable Surface)
**What goes wrong:** Trying to find the remove button via `onNodeWithTag` fails because there is no testTag set.
**Why it happens:** The `IconButton` in TagChip uses `contentDescription = "Remove tag"` on the Icon, not a testTag.
**How to avoid:** Use `onNodeWithContentDescription("Remove tag")` to find and click the remove button.
**Warning signs:** Test fails with "no node matches" when using onNodeWithTag.

### Pitfall 2: TagChip onClick Uses Modifier.clickable, Not a Button
**What goes wrong:** `performClick()` on the text node does nothing because the clickable modifier is on the parent Surface.
**Why it happens:** `TagChip` applies `.clickable { onClick() }` on the Surface modifier, not on a Button composable.
**How to avoid:** Find the node with the tag text (`onNodeWithText("tag")`) and call `performClick()` -- Compose semantics propagate click actions from Surface.
**Warning signs:** Click callback not firing in tests.

### Pitfall 3: MaterialTheme Must Wrap Test Content
**What goes wrong:** Crash or rendering issues when testing components that use `MaterialTheme.colorScheme.*` or `MaterialTheme.shapes.*`.
**Why it happens:** All target components reference `MaterialTheme` tokens. Without a wrapping `MaterialTheme { }`, these tokens resolve to defaults that may crash or produce misleading results.
**How to avoid:** Always wrap test content in `MaterialTheme { }` in setContent blocks.
**Warning signs:** Theme-related NPE or assertion on visual properties.

### Pitfall 4: Robolectric Config Annotation Required for Compose UI Tests
**What goes wrong:** Tests fail at class load with Robolectric initialization errors.
**Why it happens:** Missing `@Config(application = android.app.Application::class)` or wrong SDK.
**How to avoid:** Always annotate Compose UI test classes with `@RunWith(RobolectricTestRunner::class)` and `@Config(sdk = [34], application = android.app.Application::class)`. The project has `robolectric.properties` setting `sdk=34` but the `@Config` annotation is still used in existing tests for explicitness.
**Warning signs:** ClassNotFoundException or Robolectric SDK download errors.

### Pitfall 5: TagEditorDialog Contains Internal Mutable State
**What goes wrong:** Attempting to test TagEditorDialog's internal state (tags list, searchInput) from outside the composable is impossible since they are `remember { mutableStateOf(...) }` locals.
**Why it happens:** The dialog manages its own editing state internally; the host only receives results via `onTagsUpdated`.
**How to avoid:** Either (a) extract logic into pure functions tested in commonTest (preferred), or (b) test through the full composable by typing into the text field and clicking buttons via semantics.
**Warning signs:** No way to assert intermediate state.

### Pitfall 6: ListPickerDialog Uses ModalBottomSheet (Hard to Test in Robolectric)
**What goes wrong:** ModalBottomSheet relies on Window/Dialog infrastructure that Robolectric may not fully support. Sheet may not render or may crash.
**Why it happens:** ModalBottomSheet creates a separate window/popup layer that Robolectric's limited window stack does not handle well.
**How to avoid:** Skip full ListPickerDialog compose-level testing. The hierarchy logic is already tested in `ListHierarchyUtilsTest`. If a UI test is needed, test just the inner content (LazyColumn of ListItems) in isolation, not the full ModalBottomSheet wrapper.
**Warning signs:** Tests hang or crash on ModalBottomSheet initialization.

## Code Examples

### Example 1: Extractable canAdd Logic from TagEditorDialog

The `canAdd` computation at line 103-104 of TagEditorDialog.kt:
```kotlin
val exactMatch = availableTags.find { it.equals(searchInput.trim(), ignoreCase = true) }
val canAdd = searchInput.isNotBlank() && !tags.contains(searchInput.trim()) &&
    (canCreateNew || exactMatch != null)
```

Extract as:
```kotlin
internal fun canAddTag(
    searchInput: String,
    currentTags: List<String>,
    availableTags: List<String>,
    canCreateNew: Boolean
): Boolean {
    val trimmed = searchInput.trim()
    if (trimmed.isBlank()) return false
    if (currentTags.contains(trimmed)) return false
    if (canCreateNew) return true
    return availableTags.any { it.equals(trimmed, ignoreCase = true) }
}
```

Test cases:
- `canCreateNew=true` + non-blank input not in currentTags -> true
- `canCreateNew=false` + input matches availableTags (case-insensitive) -> true
- `canCreateNew=false` + input NOT in availableTags -> false
- Input already in currentTags -> false regardless of canCreateNew
- Blank input -> false

### Example 2: BookmarkTagsDisplay Tag Parsing

Extract from BookmarkTagsDisplay.kt lines 41-43:
```kotlin
internal fun parseTagString(tags: String): List<String> =
    tags.split(",").map { it.trim() }.filter { it.isNotBlank() }
```

### Example 3: TagChip Compose UI Test Structure

```kotlin
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class TagChipTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `shows remove icon when onRemove is provided`() {
        composeTestRule.setContent {
            MaterialTheme { TagChip(tag = "test", onRemove = {}) }
        }
        composeTestRule.onNodeWithContentDescription("Remove tag").assertExists()
    }

    @Test
    fun `hides remove icon when onRemove is null`() {
        composeTestRule.setContent {
            MaterialTheme { TagChip(tag = "test") }
        }
        composeTestRule.onNodeWithContentDescription("Remove tag").assertDoesNotExist()
    }
}
```

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 4 + Robolectric 4.14 + compose-ui-test-junit4 (androidUnitTest), kotlin-test (commonTest) |
| Config file | `composeApp/src/androidUnitTest/resources/robolectric.properties` (sdk=34) |
| Quick run command | `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew :composeApp:testDebugUnitTest --tests "com.karakept.app.ui.components.*"` |
| Full suite command | `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew :composeApp:testDebugUnitTest :composeApp:desktopTest` |

### Phase Requirements -> Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| TAG-FILTER | TagEditorDialog suggestion filtering (prefix match, case-insensitive, excludes already-selected, max 8) | unit (commonTest) | `./gradlew :composeApp:desktopTest --tests "*.TagEditorLogicTest"` | Wave 0 |
| TAG-CANADD | TagEditorDialog canAdd gate (canCreateNew flag, exactMatch, blank input, duplicate) | unit (commonTest) | `./gradlew :composeApp:desktopTest --tests "*.TagEditorLogicTest"` | Wave 0 |
| TAG-PARSE | BookmarkTagsDisplay comma-separated string -> list parsing (trim, blank filter) | unit (commonTest) | `./gradlew :composeApp:desktopTest --tests "*.BookmarkTagsParsingTest"` | Wave 0 |
| TAG-CHIP-UI | TagChip render: text displayed, remove icon presence/absence, click/remove callbacks | ui (androidUnitTest) | `./gradlew :composeApp:testDebugUnitTest --tests "*.TagChipTest"` | Wave 0 |

### Sampling Rate
- **Per task commit:** Quick run command for affected test file
- **Per wave merge:** Full suite command
- **Phase gate:** Full suite green before `/gsd:verify-work`

### Wave 0 Gaps
- [ ] `composeApp/src/commonTest/kotlin/com/karakept/app/ui/components/TagEditorLogicTest.kt` -- covers TAG-FILTER, TAG-CANADD
- [ ] `composeApp/src/commonTest/kotlin/com/karakept/app/ui/components/BookmarkTagsParsingTest.kt` -- covers TAG-PARSE
- [ ] `composeApp/src/androidUnitTest/kotlin/com/karakept/app/ui/components/TagChipTest.kt` -- covers TAG-CHIP-UI
- [ ] Extract `filterTagSuggestions()`, `canAddTag()` from TagEditorDialog.kt as `internal` top-level functions
- [ ] Extract `parseTagString()` from BookmarkTagsDisplay.kt as `internal` top-level function

## Sources

### Primary (HIGH confidence)
- Project source code: `TagEditorDialog.kt`, `BookmarkTagsDisplay.kt`, `TagChip.kt`, `ListPickerDialog.kt` -- direct analysis of testable logic
- Existing test files: `ScrollToTopVisibilityTest.kt`, `MainScreenSelectAllTest.kt`, `ListHierarchyUtilsTest.kt` -- established patterns for both androidUnitTest and commonTest
- `composeApp/build.gradle.kts` -- confirmed test dependencies already configured

### Secondary (MEDIUM confidence)
- Compose UI testing semantics API (performClick, onNodeWithText, onNodeWithContentDescription) -- based on existing project usage in Phase 07 tests

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH -- no new dependencies; all infrastructure exists from Phases 07-09
- Architecture: HIGH -- extract-and-test pattern is well-established in this project (see `applyRemoveBookmarkTransform` extraction in Phase 05)
- Pitfalls: HIGH -- derived from direct source code analysis of the target components

**Research date:** 2026-03-25
**Valid until:** 2026-04-25 (stable -- testing infrastructure and target components are not changing)
