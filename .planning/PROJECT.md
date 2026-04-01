# Karakept KMP

## What This Is

A Kotlin Multiplatform bookmark manager built with Compose Multiplatform, targeting Android and Desktop. Uses Material Design 3, Voyager navigation, Koin DI, and SQLDelight for local storage. Features custom layout editor, undo snackbars, per-list notifications, and DEV build differentiation. ~75-80% business logic test coverage.

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
- ✓ NOTIF-01: Sync digest notification shows bookmark count (#169) — v1.9.0 Phase 12
- ✓ NOTIF-02: Per-list notification fires on new bookmarks (#170) — v1.9.0 Phase 12
- ✓ SAVE-02: Navigate back after saving shows bookmark list (#163) — v1.9.0 Phase 13
- ✓ LIST-02: Smart list reflects quick-action changes immediately (#165) — v1.9.0 Phase 13
- ✓ FILT-04: Pull-to-refresh on Highlights mobile (#164) — v1.9.0 Phase 14
- ✓ UI-01: Scroll-to-top reaches actual top (#168) — v1.9.0 Phase 14
- ✓ UX-01: Undo snackbars for reversible actions (#166) — v1.9.0 Phase 15
- ✓ UX-02: Custom layout improvements (#167) — v1.9.0 Phase 16
- ✓ NFR-01: Regression tests for every fix — v1.9.0
- ✓ DEV-01: Dev build icon/label differentiation (#177) — v1.9.0 Phase 22

### Active

(None — next milestone not yet defined. Run `/gsd:new-milestone` to start.)

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
- v1.9.0 shipped 2026-04-01: 6 bug fixes, 3 UX enhancements (undo snackbars, custom layouts, DEV build differentiation), all with regression tests
- Phases 17-21 deferred from v1.9.0 to next milestone (desktop auth, pre rendering, scroll-to-top for lists, drawer auto-expand, desktop list settings)
- Test files: 37+ test files / ~9,000+ lines of test code across commonTest, androidUnitTest, desktopTest
- Codebase: ~133,300 lines Kotlin, ~75-80% business logic coverage
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
| undoableAction as CoroutineScope extension | Reuse across 4+ call sites; avoids Kotlin trailing lambda ambiguity with named onUndo= | Shared undo snackbar pattern across main screen + viewer ✓ |
| COMPACT_LIST merged into LIST with @Deprecated compat | Deserialization backward compat; fromString() redirects transparently | Single layout type with unified options ✓ |
| Ktor io.ktor.http.Url for extractDomain() | Consistent with FaviconUtils pattern, works across all KMP targets | Domain extraction without java.net.URI ✓ |
| Globe icon behind favicon AsyncImage in Box | Natural fallback without error callbacks | Smooth favicon loading UX ✓ |
| isDevBuild expect/actual + BuildConfig.IS_DEV | Cross-platform DEV detection (Android BuildConfig, Desktop system property) | DEV ribbon/badge on both platforms ✓ |

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
*Last updated: 2026-04-01 after v1.9.0 milestone*
