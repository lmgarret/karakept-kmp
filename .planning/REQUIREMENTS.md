# Requirements: Karakept KMP — v1.7.0 Tech Debt Cleanup

**Defined:** 2026-03-21
**Core Value:** Production code uses structured logging, stays within size targets, and avoids redundant operations

## v1.7.0 Requirements

### Logging Cleanup

- [ ] **LOG-01**: Replace all remaining `println` debug calls (~97) with `AppLogger` calls or remove them
  - `BookmarkActionsRepositorySync.kt`: ~53 calls
  - `BookmarkRepository.kt`: ~17 calls
  - `HighlightRepository.kt`: ~13 calls
  - `BookmarkActionsRepository.kt`: ~7 calls
  - `ImageCacheManager.kt`: ~5 calls
  - Other files: ~2 calls

### Code Size

- [ ] **SIZE-01**: Reduce `BookmarkSyncPipeline.kt` to under 500 lines (currently 545)
- [ ] **SIZE-02**: Reduce `SettingsRepositoryMutations.kt` to under 500 lines (currently 511)

### Code Quality

- [ ] **QUAL-01**: Remove redundant `koinInject<ServerRepository>()` call in `App.kt`

## Out of Scope

| Feature | Reason |
|---------|--------|
| New functionality | This is a cleanup-only milestone |
| Dependency upgrades | Tracked separately for future release |
| Platform-specific work | Separate effort |

## Traceability

| Requirement | Phase | Status |
|-------------|-------|--------|
| LOG-01 | Phase 1 | Pending |
| SIZE-01 | Phase 2 | Pending |
| SIZE-02 | Phase 2 | Pending |
| QUAL-01 | Phase 2 | Pending |

**Coverage:**
- v1.7.0 requirements: 4 total
- Mapped to phases: 4
- Unmapped: 0

---
*Requirements defined: 2026-03-21*
