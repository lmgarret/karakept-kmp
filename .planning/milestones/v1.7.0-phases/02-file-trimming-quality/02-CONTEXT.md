# Phase 2: File Trimming & Quality - Context

**Gathered:** 2026-03-21
**Status:** Ready for planning

<domain>
## Phase Boundary

Trim oversized files below 500 lines and remove redundant koinInject calls. After Phase 1's println cleanup, both file size targets are already met. The remaining work is removing the redundant `koinInject<ServerRepository>()` call in App.kt.

</domain>

<decisions>
## Implementation Decisions

### Claude's Discretion
All implementation choices are at Claude's discretion — pure infrastructure phase.

Post-Phase 1 status update:
- **SIZE-01:** BookmarkSyncPipeline.kt is now 493 lines (under 500) — no action needed
- **SIZE-02:** SettingsRepositoryMutations.kt is now 478 lines (under 500) — no action needed
- **QUAL-01:** App.kt has 2 `koinInject<ServerRepository>()` calls (lines 31 and 107) — remove the redundant one, keeping whichever is used at the correct scope

</decisions>

<canonical_refs>
## Canonical References

No external specs — requirements fully captured in decisions above and in:
- `.planning/REQUIREMENTS.md` — SIZE-01, SIZE-02, QUAL-01 requirement definitions
- `.planning/ROADMAP.md` — Phase 2 success criteria

</canonical_refs>

<specifics>
## Specific Ideas

No specific requirements — infrastructure phase

</specifics>

<code_context>
## Existing Code Insights

### Integration Points
- `App.kt` (line 31): `val serverRepository = koinInject<ServerRepository>()` — top-level composable scope
- `App.kt` (line 107): `val serverRepository = koinInject<ServerRepository>()` — inner scope, likely redundant with line 31

### Established Patterns
- App.kt uses Koin's `koinInject` for DI in composables
- The outer `serverRepository` (line 31) is likely the one that should remain since it's at the top scope

</code_context>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 02-file-trimming-quality*
*Context gathered: 2026-03-21*
