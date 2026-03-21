# Phase 04: Test Coverage - Context

**Gathered:** 2026-03-21
**Status:** Ready for planning

<domain>
## Phase Boundary

Add automated tests for the highest-risk untested code paths — sync pipeline, bookmark actions, and screen model state management. Tests target the refactored, split modules from Phase 3.

</domain>

<decisions>
## Implementation Decisions

### Claude's Discretion
All implementation choices are at Claude's discretion — pure infrastructure phase. Test framework, assertion style, mocking strategy, and test granularity are all at the implementer's discretion given the existing test patterns in the codebase.

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- Existing test infrastructure in `composeApp/src/desktopTest/` with BookmarkSyncIntegrationTest
- kotlinx-coroutines-test for coroutine testing
- Existing mock patterns from integration tests

### Established Patterns
- Tests use `runTest` from kotlinx.coroutines.test
- Integration tests use real DAO with in-memory database
- Remote data source mocked with fake responses

### Integration Points
- Tests target the split modules: BookmarkSyncPipeline, BookmarkActionsRepository extensions, MainScreenModel extensions
- Build via `./gradlew :composeApp:desktopTest`

</code_context>

<specifics>
## Specific Ideas

No specific requirements — infrastructure phase

</specifics>

<deferred>
## Deferred Ideas

None

</deferred>
