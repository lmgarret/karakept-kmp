---
phase: quick
plan: 260322-0jj
subsystem: infra
tags: [github-actions, ci-cd, release, flatpak]

requires: []
provides:
  - "Release workflow with tag-push trigger for full versioned releases"
affects: []

tech-stack:
  added: []
  patterns:
    - "Tag-push triggers full release (Android + macOS + Flatpak)"
    - "GITHUB_REF-based version extraction for tag-triggered runs"

key-files:
  created: []
  modified:
    - ".github/workflows/release.yml"

key-decisions:
  - "Tags trigger under on.push alongside branches; GitHub Actions ignores paths filter for tag pushes"
  - "Version extracted from GITHUB_REF for tag triggers, falling back to existing logic for dispatch/nightly"

patterns-established: []

requirements-completed: []

duration: 1min
completed: 2026-03-22
---

# Quick Task 260322-0jj: Update GitHub Actions Release Workflow Summary

**Tag-push (v*) triggers full versioned release with Flatpak; nightly and workflow_dispatch unchanged**

## Performance

- **Duration:** 1 min
- **Started:** 2026-03-21T23:25:04Z
- **Completed:** 2026-03-21T23:25:53Z
- **Tasks:** 1
- **Files modified:** 1

## Accomplishments
- Added `v*` tag trigger to release workflow so pushing a version tag triggers a full release including Flatpak
- Version resolution now extracts version from `GITHUB_REF` when triggered by tag push
- Nightly pre-release condition updated to exclude tag pushes
- Versioned release and Flatpak build conditions updated to include tag pushes

## Task Commits

Each task was committed atomically:

1. **Task 1: Update release workflow to trigger on tag push and adjust version/conditional logic** - `59b0e3a` (feat)

## Files Created/Modified
- `.github/workflows/release.yml` - Added tag trigger, updated version resolution and conditional logic for tag-push releases

## Decisions Made
- Tags are placed under `on.push` as a sibling of `branches`; GitHub Actions does not apply `paths` filter to tag pushes, which is the desired behavior
- Version is extracted directly from `GITHUB_REF` for tag-triggered runs, bypassing AI changelog version detection

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
None

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- Workflow is ready to use; push a `v*` tag to trigger a full versioned release
- No blockers

---
*Plan: quick/260322-0jj*
*Completed: 2026-03-22*

## Self-Check: PASSED
