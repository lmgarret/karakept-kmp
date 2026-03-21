# Karakept KMP

## What This Is

A Kotlin Multiplatform bookmark manager built with Compose Multiplatform, targeting Android and Desktop. Uses Material Design 3, Voyager navigation, Koin DI, and SQLDelight for local storage.

## Core Value

A reliable, well-structured bookmark management app with clean code practices.

## Requirements

### Validated

- ✓ Replace ~97 `println` debug calls with `AppLogger` or remove them — v1.7.0
- ✓ Trim `BookmarkSyncPipeline.kt` below 500 lines — v1.7.0 (493 lines after println cleanup)
- ✓ Trim `SettingsRepositoryMutations.kt` below 500 lines — v1.7.0 (478 lines after println cleanup)
- ✓ Remove redundant `koinInject<ServerRepository>()` in `App.kt` — v1.7.0

### Active

(None — next milestone TBD)

### Out of Scope

- Dependency upgrades (tracked separately)
- Platform-specific improvements

## Context

- v1.7.0 shipped 2026-03-21: tech debt cleanup (logging, file sizes, redundant DI)
- Code-health milestone completed 2026-03-21, archived in `.planning/archive/code-health/`
- AppLogger infrastructure in place with `.d()`, `.i()`, `.w()`, `.e()` severity levels
- All production files under 500 lines
- The app is functional and in active use

## Constraints

- **Stability**: No regressions — all changes are internal refactoring
- **Build**: Build/test commands run externally by the user, not in this session

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Aligned milestone versioning to git tags | Previous "v1.0" milestone didn't match repo's actual v1.x.x tags | v1.7.0 follows v1.6.0 ✓ |
| Hot-path logging removed, not replaced | Per-item loop logging adds noise without debug value | Zero hot-path println calls ✓ |
| Consolidated sync action logging | 53 verbose step traces → ~15 targeted start/success/error logs | Cleaner BookmarkActionsRepositorySync ✓ |

---
*Last updated: 2026-03-21 after v1.7.0 milestone*
