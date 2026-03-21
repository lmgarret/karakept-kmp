# Karakept KMP — v1.7.0 Tech Debt Cleanup

## What This Is

A focused tech debt cleanup milestone addressing items identified during the code-health milestone audit. All functional requirements are already met — this milestone cleans up remaining cosmetic and maintainability issues.

## Core Value

Production code should use structured logging, stay within size targets, and avoid redundant operations.

## Requirements

### Active

- [ ] Replace ~97 remaining `println` debug calls with `AppLogger` or remove them
- [ ] Trim `BookmarkSyncPipeline.kt` below 500 lines (currently 545)
- [ ] Trim `SettingsRepositoryMutations.kt` below 500 lines (currently 511)
- [ ] Remove redundant `koinInject<ServerRepository>()` call in `App.kt`

### Out of Scope

- New features — this is strictly cleanup
- Dependency upgrades (tracked in v2 requirements)
- Platform-specific improvements

## Context

- Code-health milestone completed 2026-03-21, archived in `.planning/archive/code-health/`
- AppLogger infrastructure already in place (built in code-health Phase 1)
- All files were split in code-health Phase 3; two are marginally over the 500-line target
- The app is functional and in active use — changes must not regress behavior

## Constraints

- **Stability**: No regressions — all changes are internal refactoring
- **Build**: Build/test commands run externally by the user, not in this session
- **Scope**: Only address the 4 identified tech debt items

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Aligned milestone versioning to git tags | Previous "v1.0" milestone didn't match repo's actual v1.x.x tags | v1.7.0 follows v1.6.0 |

---
*Created: 2026-03-21*
