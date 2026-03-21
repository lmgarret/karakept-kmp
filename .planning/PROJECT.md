# Karakept KMP — Code Health Milestone

## What This Is

A systematic code health improvement milestone for Karakept, a Kotlin Multiplatform bookmark manager built with Compose Multiplatform. This milestone addresses concerns surfaced during codebase mapping: silent error handling, null safety risks, race conditions, oversized files, missing test coverage, security gaps, and performance concerns.

## Core Value

Silent failures must become visible failures — errors that are swallowed today (via `printStackTrace()`) must surface to developers and users so the app doesn't silently corrupt state or lose data.

## Requirements

### Validated

- ✓ Bookmark CRUD with offline-first sync — existing
- ✓ Multi-server support with authentication — existing
- ✓ List and tag management with hierarchy — existing
- ✓ Full-text search and filtering — existing
- ✓ Reader view with highlights and reading progress — existing
- ✓ Background sync (Android + Desktop) — existing
- ✓ Material Design 3 theming — existing
- ✓ Android and Desktop (Linux/macOS) targets — existing
- ✓ Backup/restore functionality — existing
- ✓ Replace all `printStackTrace()` calls with structured error handling — Validated in Phase 01: Error Visibility
- ✓ Propagate errors to UI layer via error flows — Validated in Phase 01: Error Visibility
- ✓ Eliminate `!!` operators in favor of safe null handling — Validated in Phase 01: Error Visibility

### Active

- [ ] Split large files (7 files over 1000 lines) into focused modules
- ✓ Address race conditions in MainScreenModel initialization — Validated in Phase 02: Concurrency Hardening
- ✓ Fix read/unread toggling race condition — Validated in Phase 02: Concurrency Hardening (vestigial — no actual race exists)
- ✓ Add tests for offline-first action queue edge cases — Validated in Phase 04: Test Coverage
- ✓ Add tests for filter combination edge cases — Validated in Phase 04: Test Coverage
- ✓ Sanitize HTML in reader view (XSS prevention) — Validated in Phase 05: Security Hardening
- ✓ Move credentials to platform keychain/keystore — Validated in Phase 05: Security Hardening
- [ ] Optimize rendering for large bookmark collections (1000+)
- [ ] Optimize HTML block rendering with caching/lazy loading

### Out of Scope

- Rewriting the Voyager navigation layer — mitigated, not broken
- Upgrading alpha/beta dependencies (Room, Voyager) — monitor only, act when stable releases ship
- Linux D-Bus/tray icon improvements — platform-specific, separate effort
- Already-FIXED items (action mode crash, skiko version, compose DSL deprecations, taskbar duplication)

## Context

- Codebase mapping completed 2026-03-20, documented in `.planning/codebase/`
- CONCERNS.md is the primary input — all active requirements derive from it
- The app is functional and in use — changes must not regress existing behavior
- No build environment available in this session — user runs builds externally
- Silent failures (error handling) is the top pain point

## Constraints

- **Stability**: All changes must be backwards-compatible — no regressions to existing functionality
- **Incremental**: Meaningful progress across all areas, not 100% completion required
- **Tech stack**: Kotlin Multiplatform, Compose Multiplatform, existing dependency set
- **Build**: Build/test commands run externally by the user, not in this session

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Tackle all concern areas | User wants broad incremental progress, not deep-dive on one area | — Pending |
| Prioritize error handling first | Silent failures are the top pain point — fix visibility before fixing logic | — Pending |
| Incremental completion target | App is functional; perfection not required, meaningful improvement is | — Pending |

---
*Last updated: 2026-03-21 — Phase 05 (Security Hardening) complete*
