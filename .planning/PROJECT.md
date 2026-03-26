# Karakept KMP

## What This Is

A Kotlin Multiplatform bookmark manager built with Compose Multiplatform, targeting Android and Desktop. Uses Material Design 3, Voyager navigation, Koin DI, and SQLDelight for local storage. The codebase now has ~75-80% business logic test coverage across pure utilities, action layer, sync pipeline, settings, and UI components.

## Core Value

A reliable, well-structured bookmark management app with clean code practices.

## Requirements

### Validated

- ✓ Replace ~97 `println` debug calls with `AppLogger` or remove them — v1.7.0
- ✓ Trim `BookmarkSyncPipeline.kt` below 500 lines — v1.7.0 (493 lines after println cleanup)
- ✓ Trim `SettingsRepositoryMutations.kt` below 500 lines — v1.7.0 (478 lines after println cleanup)
- ✓ Remove redundant `koinInject<ServerRepository>()` in `App.kt` — v1.7.0
- ✓ Fix reader closing scrolling list to top (#152) — v1.8.0 Phase 03
- ✓ Reader info button moves to overflow menu on scroll (#160) — v1.8.0 Phase 03 (always in menu)
- ✓ Scroll-to-top button in reader (#161) — v1.8.0 Phase 03
- ✓ Fix select-all only selecting 20 entries (#153) — v1.8.0 Phase 06
- ✓ Quick actions not reflected in viewed list (#154) — v1.8.0 Phase 05
- ✓ Per-list offline sync not working (#155) — v1.8.0 Phase 05
- ✓ Add counters to Quick Filters in drawer (#156) — v1.8.0 Phase 06
- ✓ Pull-to-refresh for Highlights (#157) — v1.8.0 Phase 06
- ✓ Cannot go back to bookmark list after share-save (#158) — v1.8.0 Phase 04
- ✓ State leak between bookmark saving activities (#159) — v1.8.0 Phase 04
- ✓ Automated regression tests for all v1.8.0 behavioral scenarios — v1.8.0 Phase 07
- ✓ Increase test coverage: `BookmarkSyncPipeline`, `BookmarkActionController`, pure utilities — v1.9.0 Phase 08 (113 new tests, ~75-80% business logic coverage)
- ✓ Dialog and component UI tests: extracted `filterTagSuggestions`, `canAddTag`, `parseTagString` as pure functions; 29 new tests — v1.9.0 Phase 10
- ✓ BackupRepository edge case tests: 6 new tests covering blank PIN guard, setBackupPin branches, malformed JSON, scheduled export trigger, silent exception swallow — v1.9.0 Phase 11
- ✓ SettingsRepository flow tests: 53 flow-level tests across all 8 settings categories via FakeDataStore — v1.9.0 Phase 09

### Active (v1.9.0 Bug Fixes & UX Polish)

- ✓ NOTIF-01: Sync digest notification must show bookmark count (#169) — v1.9.0 Phase 12 (return value propagation from pipeline)
- ✓ NOTIF-02: "Notify on new bookmarks" option on list must fire (#170) — v1.9.0 Phase 12 (post-sync DB query + combined notification)
- ✓ SAVE-02: Navigate back after saving bookmarks must show bookmark list (#163) — v1.9.0 Phase 13 (factory{} scope fix + regression test)
- ✓ LIST-02: Smart list must reflect quick-action changes immediately (#165) — v1.9.0 Phase 13 (syncSmartLists helper + regression tests)
- FILT-04: Pull-to-refresh must work on Highlights on mobile (#164)
- UI-01: Scroll-to-top button must reach the actual top (#168)
- UX-01: Snackbars for reversible actions must include an Undo button (#166)
- UX-02: Custom layout improvements (#167)
- NFR-01: Regression tests for every fix (required by user)

### Out of Scope

- Dependency upgrades (tracked separately)
- Add 'On open bookmark' custom action (#113) — deferred to future milestone
- Server API version check (#61) — deferred to future milestone
- Add support for bookmark refresh (#18) — deferred to future milestone
- Support other kinds of bookmarks (#14) — deferred to future milestone
- Compute reading time for non-synced articles (#9) — deferred to future milestone
- Implement OIDC connect (#5) — deferred to future milestone
- Robolectric ModalBottomSheet click tests — blocked by Robolectric 4.14 bug (performClick() silently fails in bottom sheets on SDK 29-34); requires upgrade to 4.15.1

## Context

- v1.7.0 shipped 2026-03-21: tech debt cleanup (logging, file sizes, redundant DI)
- v1.8.0 shipped 2026-03-25: 6 bugs fixed, 4 UX improvements, Robolectric test infrastructure
- Platform Health archived 2026-03-25 (internal, no git tag): 201 new tests, ~40% → ~75-80% business logic coverage
- v1.9.0 in progress: 6 bug fixes + 2 UX enhancements, all with regression tests — Phase 13 complete (SAVE-02 + LIST-02)
- Test files: 37+ test files / ~9,000+ lines of test code across commonTest, androidUnitTest, desktopTest
- All key untested areas addressed: pure utilities, action layer, sync pipeline, settings flows, UI components, backup edge cases
- The app is functional and in active use

## Constraints

- **Stability**: No regressions in existing bookmark management functionality
- **Build**: Build/test commands run externally by the user, not in this session
- **Platform**: Bookmark saving activity issues are Android-specific
- **JDK**: JDK 21 required for Gradle (JDK 25 breaks Kotlin DSL)

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Aligned milestone versioning to git tags | Previous "v1.0" milestone didn't match repo's actual v1.x.x tags | v1.7.0 follows v1.6.0 ✓ |
| Hot-path logging removed, not replaced | Per-item loop logging adds noise without debug value | Zero hot-path println calls ✓ |
| Hoisted scroll position into MainScreenModel | Cross-navigation persistence needs singleton scope | Scroll position survives reader open/close ✓ |
| scrollToTopEnabled defaults to true, not in BackupSettings | Same pattern as showTagsInViewer; no user impact | Clean settings model ✓ |
| key(intentKey) pattern for onNewIntent fresh state | Forces full Compose tree destruction without Activity recreation | Fresh navigator state on each share ✓ |
| Pure function extraction for test isolation | Avoid Voyager/Koin instantiation overhead in unit tests | applyRemoveBookmarkTransform directly testable ✓ |
| Robolectric 4.14 + SDK 34 for androidUnitTest | Avoids JDK/SDK compatibility issues | Stable test infrastructure ✓ |
| ScreenModel-centric testing with mockk | Direct construction avoids DI graph wiring | Fast, focused unit tests ✓ |
| FakeDataStore via MutableStateFlow + Mutex | Thread-safe atomic updates matching real DataStore semantics | 53 SettingsRepository flow tests passing ✓ |
| Real-object pattern for extension functions | Extension functions accessing internal members conflict with MockK relaxed mocking | BookmarkSyncPipeline fully testable ✓ |
| Internal top-level function extraction for composable logic | Enables direct import in commonTest without Compose runtime | filterTagSuggestions/canAddTag/parseTagString tested in commonTest ✓ |
| platform-health archived without git tag | Internal quality work; milestone completed without versioned release | Test coverage milestone archived as "Platform Health (internal)" ✓ |

## Evolution

This document evolves at phase transitions and milestone boundaries.

**After each phase transition** (via `/gsd:transition`):
1. Requirements invalidated? → Move to Out of Scope with reason
2. Requirements validated? → Move to Validated with phase reference
3. New requirements emerged? → Add to Active
4. Decisions to log? → Add to Key Decisions
5. "What This Is" still accurate? → Update if drifted

**After each milestone** (via `/gsd:complete-milestone`):
1. Full review of all sections
2. Core Value check — still the right priority?
3. Audit Out of Scope — reasons still valid?
4. Update Context with current state

---
*Last updated: 2026-03-26
