# Phase 10: Dialog and Component UI Tests - Context

**Gathered:** 2026-03-25
**Status:** Ready for planning
**Mode:** Auto-generated (infrastructure phase — discuss skipped)

<domain>
## Phase Boundary

Add automated tests for the UI dialog and component layer. Focus on the most testable logic in dialog components (TagEditorDialog suggestion/filtering logic, BookmarkTagsDisplay tag rendering, TagChip interactions) using the existing test infrastructure from Phases 07-09. Pure logic is tested in commonTest; Compose UI interaction tests go in androidUnitTest (Robolectric). The FakeDataStore from Phase 09 is available for reuse if any components access settings.

</domain>

<decisions>
## Implementation Decisions

### Claude's Discretion
All implementation choices are at Claude's discretion — pure infrastructure/testing phase. Use Phase 07 (androidUnitTest + Robolectric) and Phase 08/09 (commonTest pure logic) patterns. Prioritize the highest-value components: TagEditorDialog (suggestion filtering, create-new gate), BookmarkTagsDisplay (tag rendering from comma-separated string), and TagChip (onClick/onRemove callback wiring). Aim for 2-3 test files covering meaningful logic — not a smoke test for every component.

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- FakeDataStore in commonTest fixtures (Phase 09) — reuse if components access DataStore
- Robolectric 4.14 + compose-ui-test already wired in androidUnitTest (Phase 07)
- Existing androidUnitTest examples: MainScreenSelectAllTest, QuickFilterCountsTest, ScrollToTopVisibilityTest
- Existing commonTest examples: SettingsRepository*Test, BookmarkFilterTest

### Established Patterns
- androidUnitTest: `@RunWith(RobolectricTestRunner)`, `createComposeRule()`, `composeTestRule.setContent { ... }`, assert via semantics
- commonTest: `runTest { }`, `TestScope`, pure function calls, assertion on StateFlow/List values
- Tag logic: `TagEditorDialog` accepts `availableTags: List<String>` and filters suggestions as user types — this filtering is testable as pure logic
- `BookmarkTagsDisplay` accepts a comma-separated `tags: String` and renders chips — rendering logic is testable

### Integration Points
- Components live in `composeApp/src/commonMain/kotlin/com/karakept/app/ui/components/`
- Tests go in `composeApp/src/androidUnitTest/kotlin/com/karakept/app/ui/components/` (Compose UI) or `composeApp/src/commonTest/kotlin/com/karakept/app/ui/components/` (pure logic)

</code_context>

<specifics>
## Specific Ideas

- TagEditorDialog: test that typing a prefix shows matching suggestions; test that `canCreateNew=false` prevents free-text tags from being added
- BookmarkTagsDisplay: test that a comma-separated string like `"kotlin,android,kmp"` renders 3 chips
- TagChip: test that `onRemove` callback fires when the remove button is tapped; test that `onClick` fires when chip is tapped

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope.

</deferred>
