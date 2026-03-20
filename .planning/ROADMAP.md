# Roadmap: Karakept KMP — Code Health

## Overview

This milestone transforms Karakept from a functional-but-fragile app into one where failures are visible, state is safe, and code is maintainable. Error handling comes first because every subsequent phase benefits from visible failures. Concurrency fixes precede file splitting so that race condition fixes land in their original locations before code moves. Tests follow restructuring to validate the refactored code. Security and performance close out the milestone as independent hardening passes.

## Phases

**Phase Numbering:**
- Integer phases (1, 2, 3): Planned milestone work
- Decimal phases (2.1, 2.2): Urgent insertions (marked with INSERTED)

Decimal phases appear between their surrounding integers in numeric order.

- [ ] **Phase 1: Error Visibility** - Replace silent failures with structured error handling and safe null patterns
- [ ] **Phase 2: Concurrency Hardening** - Eliminate race conditions in initialization and read/unread toggling
- [ ] **Phase 3: Code Splitting** - Break oversized files into focused, single-responsibility modules
- [ ] **Phase 4: Test Coverage** - Add tests for highest-risk untested paths (action queue, filters, reading progress)
- [ ] **Phase 5: Security Hardening** - Sanitize untrusted HTML and protect stored credentials
- [ ] **Phase 6: Performance Optimization** - Verify and optimize rendering for large collections and long articles

## Phase Details

### Phase 1: Error Visibility
**Goal**: Developers and users see failures instead of silent corruption — no more swallowed exceptions or null crashes
**Depends on**: Nothing (foundation for all other phases)
**Requirements**: ERR-01, ERR-02, ERR-03, ERR-04, NULL-01
**Success Criteria** (what must be TRUE):
  1. No `printStackTrace()` calls remain in the codebase — all exceptions use structured logging
  2. When a sync or data operation fails, the user sees an error message (snackbar or error state) instead of silent failure
  3. No `!!` operators remain — all nullable access uses safe patterns (`?.let`, guards, sealed state)
  4. Debug println statements in RemoteDataSource and reader are replaced with proper logging or removed
**Plans**: TBD

Plans:
- [ ] 01-01: Replace printStackTrace and debug prints with structured error handling
- [ ] 01-02: Propagate errors to UI via error flows and eliminate !! operators

### Phase 2: Concurrency Hardening
**Goal**: MainScreenModel initialization and read/unread toggling are race-free with documented invariants
**Depends on**: Phase 1
**Requirements**: CONC-01, CONC-02
**Success Criteria** (what must be TRUE):
  1. MainScreenModel initialization uses an explicit state machine (or equivalent ordered construct) instead of implicit coroutine sequencing
  2. The read/unread tag cache race condition is documented, verified, and protected with a mutex if the audit finds it necessary
  3. No duplicate bookmark loads occur during startup (verifiable by log output or test)
**Plans**: TBD

Plans:
- [ ] 02-01: Refactor MainScreenModel initialization and harden read/unread toggling

### Phase 3: Code Splitting
**Goal**: Large files are decomposed into focused modules that can be understood and tested independently
**Depends on**: Phase 2 (concurrency fixes should land before code moves)
**Requirements**: SPLIT-01, SPLIT-02, SPLIT-03, SPLIT-04
**Success Criteria** (what must be TRUE):
  1. MainScreen.kt is split into focused composable files — no single file exceeds 500 lines
  2. MainScreenModel.kt is split into focused state management classes — pagination, filtering, selection, and sync are separate concerns
  3. BookmarkViewerScreen.kt is split into viewer sub-components — reader, highlights, and actions are separate files
  4. Repository files (BookmarkRepository, BookmarkActionsRepository, SettingsRepository) are split by concern — read vs write, sync vs local
  5. The app compiles and all existing functionality works identically after splitting (no regressions)
**Plans**: TBD

Plans:
- [ ] 03-01: Split MainScreen.kt and MainScreenModel.kt
- [ ] 03-02: Split BookmarkViewerScreen.kt
- [ ] 03-03: Split repository files by concern

### Phase 4: Test Coverage
**Goal**: The highest-risk untested code paths have automated tests that catch regressions
**Depends on**: Phase 3 (tests target the refactored, split modules)
**Requirements**: TEST-01, TEST-02, TEST-03
**Success Criteria** (what must be TRUE):
  1. Offline-first action queue has tests covering: ordering, conflict resolution, timeout handling, and server rejection scenarios
  2. FilterConfig has exhaustive combination tests covering all boolean flag combinations, including the previously-crashing multi-list case
  3. Reading progress race condition has a test covering rapid UI changes followed by sync, verifying the serverProgressChecked flag
**Plans**: TBD

Plans:
- [ ] 04-01: Add offline-first action queue tests
- [ ] 04-02: Add filter combination and reading progress race tests

### Phase 5: Security Hardening
**Goal**: Untrusted content cannot execute in the app, and credentials are protected at rest
**Depends on**: Phase 1 (error handling patterns needed for credential migration errors)
**Requirements**: SEC-01, SEC-02
**Success Criteria** (what must be TRUE):
  1. HTML rendered in the reader/WebView is sanitized before display — script tags, event handlers, and dangerous attributes are stripped
  2. API credentials are stored in platform keychain (Android Keystore / desktop secure storage) instead of cleartext in the Room database
  3. Existing server connections continue to work after credential migration (no re-login required)
**Plans**: TBD

Plans:
- [ ] 05-01: Sanitize HTML in reader view
- [ ] 05-02: Migrate credentials to platform keychain/keystore

### Phase 6: Performance Optimization
**Goal**: The app remains responsive with large bookmark collections and long articles
**Depends on**: Phase 3 (split code is easier to profile and optimize)
**Requirements**: PERF-01, PERF-02
**Success Criteria** (what must be TRUE):
  1. Scrolling through 1000+ bookmarks in LazyColumn shows no visible jank (verified by user testing or profiling)
  2. Opening a long HTML article in the reader does not block the UI — content loads progressively or from cache
  3. Memory usage remains stable when scrolling through large collections (no unbounded growth)
**Plans**: TBD

Plans:
- [ ] 06-01: Optimize LazyColumn rendering for large bookmark lists
- [ ] 06-02: Cache and lazy-load HTML content in reader

## Progress

**Execution Order:**
Phases execute in numeric order: 1 -> 2 -> 3 -> 4 -> 5 -> 6

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 1. Error Visibility | 0/2 | Not started | - |
| 2. Concurrency Hardening | 0/1 | Not started | - |
| 3. Code Splitting | 0/3 | Not started | - |
| 4. Test Coverage | 0/2 | Not started | - |
| 5. Security Hardening | 0/2 | Not started | - |
| 6. Performance Optimization | 0/2 | Not started | - |
