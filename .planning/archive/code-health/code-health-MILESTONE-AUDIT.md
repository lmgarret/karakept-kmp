---
milestone: code-health
audited: 2026-03-21
status: tech_debt
scores:
  requirements: 18/18
  phases: 7/7
  integration: 14/14
  flows: 5/5
nyquist:
  compliant_phases: [1, 2, 3, 4, 5, 6, 7]
  partial_phases: []
  missing_phases: []
  overall: COMPLIANT
gaps:
  requirements: []
  integration: []
  flows: []
tech_debt:
  - phase: 01-error-visibility
    items:
      - "~97 pre-existing println debug calls remain across production code (explicitly noted out-of-scope in Phase 1 verification — ERR-01 targeted printStackTrace only)"
  - phase: 03-code-splitting
    items:
      - "BookmarkSyncPipeline.kt is 545 lines (45 over 500-line goal)"
      - "SettingsRepositoryMutations.kt is 511 lines (11 over 500-line goal)"
  - phase: 07-integration-wiring-cleanup
    items:
      - "App.kt has redundant koinInject<ServerRepository>() — called twice in same composable (functionally correct, cosmetic)"
---

# Code Health Milestone Audit — Code Health

**Audited:** 2026-03-21
**Status:** tech_debt (all requirements met, no critical blockers, accumulated debt noted)

## Requirements Coverage (18/18)

All v1 requirements are satisfied across all three verification sources (VERIFICATION.md, SUMMARY frontmatter, REQUIREMENTS.md traceability).

| REQ-ID | Description | Phase(s) | VERIFICATION | SUMMARY | REQUIREMENTS | Status |
|--------|-------------|----------|-------------|---------|--------------|--------|
| ERR-01 | Replace all `printStackTrace()` with structured logging | 1, 7 | passed | listed | [x] | **satisfied** |
| ERR-02 | Propagate errors to UI via error flows | 1 | passed | listed | [x] | **satisfied** |
| ERR-03 | Fix debug println in RemoteDataSource | 1 | passed | listed | [x] | **satisfied** |
| ERR-04 | Remove noisy reading progress logs in reader | 1 | passed | listed | [x] | **satisfied** |
| NULL-01 | Replace all `!!` operators with safe null handling | 1 | passed | listed | [x] | **satisfied** |
| CONC-01 | Explicit state machine for MainScreenModel init | 2 | passed | listed | [x] | **satisfied** |
| CONC-02 | Document/verify tag cache race condition | 2 | passed | listed | [x] | **satisfied** |
| SPLIT-01 | Split MainScreen.kt into focused composables | 3 | passed | listed | [x] | **satisfied** |
| SPLIT-02 | Split MainScreenModel.kt into focused classes | 3 | passed | listed | [x] | **satisfied** |
| SPLIT-03 | Split BookmarkViewerScreen.kt into sub-components | 3 | passed | listed | [x] | **satisfied** |
| SPLIT-04 | Split repository files by concern | 3 | passed | listed | [x] | **satisfied** |
| TEST-01 | Action queue tests (ordering, conflicts, timeouts) | 4 | passed | listed | [x] | **satisfied** |
| TEST-02 | FilterConfig exhaustive combination tests | 4 | passed | listed | [x] | **satisfied** |
| TEST-03 | Reading progress race test | 4 | passed | listed | [x] | **satisfied** |
| SEC-01 | Sanitize HTML before rendering | 5 | passed | listed | [x] | **satisfied** |
| SEC-02 | Move credentials to platform keychain | 5, 7 | passed | listed | [x] | **satisfied** |
| PERF-01 | Optimize LazyColumn for 1000+ bookmarks | 6 | passed | listed | [x] | **satisfied** |
| PERF-02 | Cache parsed HTML and lazy-load sections | 6, 7 | passed | listed | [x] | **satisfied** |

**Orphaned requirements:** None. All 18 REQ-IDs in REQUIREMENTS.md traceability table appear in at least one phase VERIFICATION.md.

## Phase Verification Summary (7/7)

| Phase | Name | Status | Score | Key Finding |
|-------|------|--------|-------|-------------|
| 1 | Error Visibility | passed | 9/9 | AppLogger + ActionSnackbarManager fully wired |
| 2 | Concurrency Hardening | passed | 4/4 | InitState machine + bookmarksMutex protecting 25 call sites |
| 3 | Code Splitting | passed | 11/11 | All files under 500 lines except 2 extractions marginally over |
| 4 | Test Coverage | passed | 6/6 | 24 tests across 3 test files covering highest-risk paths |
| 5 | Security Hardening | passed | 7/7 | HTML sanitization + SecureCredentialStore (expect/actual) |
| 6 | Performance Optimization | passed | 7/7 | ParsedDocumentCache + progressive rendering + contentType |
| 7 | Integration Wiring & Cleanup | passed | 8/8 | Cache chain wired, migration wired, println cleaned in target files |

## Cross-Phase Integration (14/14 wired)

| Export | Provider | Consumer(s) | Status |
|--------|----------|-------------|--------|
| AppLogger | Phase 1 | Phases 2, 5, 6, 7 | WIRED |
| ActionSnackbarManager | Phase 1 | MainScreenModelPagination, BookmarkViewerScreenModel | WIRED |
| SnackbarEvent.MessageWithAction | Phase 1 | MainScreen, ViewerSnackbar | WIRED |
| InitState sealed class | Phase 2 | MainScreenModel init block | WIRED |
| bookmarksMutex + updateAccumulatedBookmarks | Phase 2 | Actions/Batch/Pagination extension files (24 sites) | WIRED |
| BookmarkSyncPipeline | Phase 3 | BookmarkRepository | WIRED |
| Extension functions (Actions/Batch/Pagination) | Phase 3 | MainScreenModel | WIRED |
| BookmarkActionsRepositorySync | Phase 3 | BookmarkActionsRepository, tests | WIRED |
| ParsedDocumentCache | Phase 6 | BookmarkViewerScreenModel | WIRED |
| getCachedOrParseDocument lambda chain | Phase 7 | ViewerContent → ContentBodySection → HtmlContent → NativeHtmlRenderer | WIRED |
| SecureCredentialStore | Phase 5 | ServerRepository (via Koin DI) | WIRED |
| triggerMigration() | Phase 5 | App.kt LaunchedEffect (Phase 7) | WIRED |
| HtmlArchiveProcessor | Phase 5 | HtmlContent WEB mode branch | WIRED |
| pullReadingProgressFromServer | Phase 3 | BookmarkViewerProgressTest (via mockkStatic) | WIRED |

No orphaned exports. No broken connections.

## E2E User Flows (5/5 complete)

| Flow | Path | Status |
|------|------|--------|
| Bookmark sync with error recovery | Sync → catch → AppLogger.e → showErrorWithRetry → snackbar → retry lambda | Complete |
| Bookmark viewer with cached HTML | Open → getCachedOrParseDocument → NativeHtmlRenderer (progressive) → back → reopen (cache hit) | Complete |
| Credential migration on startup | App launch → LaunchedEffect → triggerMigration → DB keys → SecureCredentialStore | Complete |
| HTML archive sanitization | WEB mode → HtmlArchiveProcessor.processForArchive → iframe/embed stripped → safe render | Complete |
| Init state machine startup | Cold start → Idle → ResolvingFilter → WaitingForServer → LoadingInitialPage → Ready | Complete |

## Nyquist Compliance (7/7 compliant)

All phases have VALIDATION.md files with `nyquist_compliant: true` and `wave_0_complete: true`.

| Phase | VALIDATION.md | Compliant |
|-------|---------------|-----------|
| 1 - Error Visibility | exists | true |
| 2 - Concurrency Hardening | exists | true |
| 3 - Code Splitting | exists | true |
| 4 - Test Coverage | exists | true |
| 5 - Security Hardening | exists | true |
| 6 - Performance Optimization | exists | true |
| 7 - Integration Wiring & Cleanup | exists | true |

## Tech Debt Summary

### 1. Remaining println calls (~97 across 7 files)

Phase 1 replaced all `printStackTrace()` calls (ERR-01) but explicitly scoped out pre-existing `println` debug instrumentation. Phase 7 cleaned `App.kt` and `BookmarkViewerScreenModel.kt` per its success criteria. Remaining concentrations:
- `BookmarkActionsRepositorySync.kt`: ~53 calls
- `BookmarkRepository.kt`: ~17 calls
- `HighlightRepository.kt`: ~13 calls
- `BookmarkActionsRepository.kt`: ~7 calls
- `ImageCacheManager.kt`: ~5 calls
- Other files: ~2 calls

AppLogger infrastructure is in place for a future cleanup pass.

### 2. Two extracted files marginally exceed 500-line goal

- `BookmarkSyncPipeline.kt`: 545 lines (cohesive sync pipeline)
- `SettingsRepositoryMutations.kt`: 511 lines (borderline)

Neither blocks functionality. Both are focused single-concern modules.

### 3. Minor cosmetic issues

- `App.kt` calls `koinInject<ServerRepository>()` twice (same singleton returned; redundant but harmless)

### Total: 5 items across 3 categories

---

_Audited: 2026-03-21_
_Auditor: Claude (audit-milestone workflow)_
