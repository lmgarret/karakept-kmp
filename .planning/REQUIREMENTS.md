# Requirements: Karakept KMP — Code Health

**Defined:** 2026-03-21
**Core Value:** Silent failures must become visible failures — errors surface to developers and users

## v1 Requirements

Requirements for this milestone. Each maps to roadmap phases.

### Error Handling

- [ ] **ERR-01**: Replace all `printStackTrace()` calls with structured logging across repository and UI files
- [ ] **ERR-02**: Propagate errors to UI layer via error flows so users see failures
- [ ] **ERR-03**: Fix debug println in RemoteDataSource with proper error handling
- [ ] **ERR-04**: Remove noisy reading progress logs in the reader

### Null Safety

- [ ] **NULL-01**: Replace all 20 `!!` operators with safe null handling (`?.let`, guards, sealed state)

### Code Complexity

- [ ] **SPLIT-01**: Split MainScreen.kt (1311 lines) into focused composable files
- [ ] **SPLIT-02**: Split MainScreenModel.kt (1034 lines) into focused state management classes
- [ ] **SPLIT-03**: Split BookmarkViewerScreen.kt (1030 lines) into viewer sub-components
- [ ] **SPLIT-04**: Split repository files (~1000 lines each) by concern

### Concurrency

- [ ] **CONC-01**: Refactor MainScreenModel initialization sequence to explicit state machine
- [ ] **CONC-02**: Document and verify read/unread tag cache race condition, add mutex if needed

### Test Coverage

- [ ] **TEST-01**: Add tests for offline-first action queue (ordering, conflicts, timeouts, rejections)
- [ ] **TEST-02**: Add exhaustive FilterConfig combination tests
- [ ] **TEST-03**: Add test for reading progress race (rapid UI changes + sync)

### Security

- [ ] **SEC-01**: Sanitize HTML before rendering in WebView/reader
- [ ] **SEC-02**: Move API credentials from cleartext DB to platform keychain/keystore

### Performance

- [ ] **PERF-01**: Verify and optimize LazyColumn rendering for 1000+ bookmarks
- [ ] **PERF-02**: Cache parsed HTML and lazy-load sections for large articles

## v2 Requirements

Deferred to future release. Tracked but not in current roadmap.

### Dependency Management

- **DEP-01**: Upgrade Room/SQLite from alpha to stable when available
- **DEP-02**: Upgrade Voyager from beta to stable when available
- **DEP-03**: Monitor skiko transitive dependency for regressions

### Platform

- **PLAT-01**: Improve Linux D-Bus/tray icon graceful degradation
- **PLAT-02**: Test on minimal Linux systems without D-Bus

## Out of Scope

| Feature | Reason |
|---------|--------|
| Voyager navigation rewrite | Mitigated with unique key pattern, not broken |
| Already-FIXED items | Action mode crash, skiko version, compose DSL, taskbar duplication already resolved |
| Full test coverage for all code paths | Incremental milestone — focus on highest-risk gaps |
| HtmlRenderer.android.kt split | Android-specific, 990 lines but single-responsibility |

## Traceability

Which phases cover which requirements. Updated during roadmap creation.

| Requirement | Phase | Status |
|-------------|-------|--------|
| ERR-01 | — | Pending |
| ERR-02 | — | Pending |
| ERR-03 | — | Pending |
| ERR-04 | — | Pending |
| NULL-01 | — | Pending |
| SPLIT-01 | — | Pending |
| SPLIT-02 | — | Pending |
| SPLIT-03 | — | Pending |
| SPLIT-04 | — | Pending |
| CONC-01 | — | Pending |
| CONC-02 | — | Pending |
| TEST-01 | — | Pending |
| TEST-02 | — | Pending |
| TEST-03 | — | Pending |
| SEC-01 | — | Pending |
| SEC-02 | — | Pending |
| PERF-01 | — | Pending |
| PERF-02 | — | Pending |

**Coverage:**
- v1 requirements: 18 total
- Mapped to phases: 0
- Unmapped: 18 ⚠️

---
*Requirements defined: 2026-03-21*
*Last updated: 2026-03-21 after initial definition*
