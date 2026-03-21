# Phase 3: Code Splitting - Context

**Gathered:** 2026-03-21
**Status:** Ready for planning

<domain>
## Phase Boundary

Decompose the 6 largest files (1000+ lines each) into focused, single-responsibility modules. No behavioral changes — pure structural refactoring. The app must compile and function identically after splitting.

Target files and current sizes:
- MainScreen.kt (1321 lines) → focused composable files
- MainScreenModel.kt (1084 lines) → separate state management concerns
- BookmarkViewerScreen.kt (1045 lines) → viewer sub-components
- BookmarkActionsRepository.kt (1000 lines) → by concern
- BookmarkRepository.kt (963 lines) → read vs write, sync vs local
- SettingsRepository.kt (988 lines) → by concern

</domain>

<decisions>
## Implementation Decisions

### Claude's Discretion
All implementation choices are at Claude's discretion — pure infrastructure phase. Key decisions include:
- How to split each file (which functions/composables go where)
- Naming conventions for new files (follow existing project conventions)
- Whether to use internal visibility or keep public APIs
- Order of splitting (UI files first, then repositories, or vice versa)
- No single file should exceed 500 lines after splitting

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- `.planning/codebase/STRUCTURE.md` — documents current file organization
- `.planning/codebase/CONVENTIONS.md` — naming patterns, code style
- `.planning/codebase/ARCHITECTURE.md` — layer boundaries (UI → Domain → Data)

### Established Patterns
- Screen composables in `ui/screens/`
- ScreenModel (ViewModel equivalent) paired with screens
- Repositories in `data/repository/`
- Components in `ui/components/`
- Utils in `ui/utils/`

### Integration Points
- MainScreen.kt contains dialogs, drag handles, sync UI, shortcuts, pagination, filtering
- MainScreenModel.kt manages pagination, filtering, selection, expansion state, derived flows
- BookmarkViewerScreen.kt contains reader, highlights, and actions
- Repositories handle read, write, sync, and local operations

</code_context>

<specifics>
## Specific Ideas

No specific requirements — infrastructure phase

</specifics>

<deferred>
## Deferred Ideas

None

</deferred>

---

*Phase: 03-code-splitting*
*Context gathered: 2026-03-21*
