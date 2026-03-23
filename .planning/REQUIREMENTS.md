# Requirements: Karakept KMP -- v1.8.0 Bug Fixes & UX Improvements

**Defined:** 2026-03-23
**Core Value:** A reliable, well-structured bookmark management app with clean code practices

## v1.8.0 Requirements

### Reader UX

- [x] **READER-01**: Closing the reader view restores the user's scroll position in the bookmark list (#152)
- [x] **READER-02**: Reader info button moves to the three-dots overflow menu when the hero section scrolls out of view (#160)
- [x] **READER-03**: User can tap a scroll-to-top button in the reader to return to the beginning of the article (#161)
- [x] **READER-04**: User can toggle the scroll-to-top button visibility in reader settings (#161)

### Bookmark Saving Activity

- [x] **SAVE-01**: User can navigate back from the reader to the bookmark list after saving via Android share target (#158)
- [x] **SAVE-02**: Sharing a second bookmark from another app creates a fresh saving activity instead of reusing the previous one (#159)

### List & Sync

- [ ] **LIST-01**: Quick actions (e.g. removing a bookmark from a list) are immediately reflected in the currently viewed list (#154)
- [ ] **LIST-02**: Enabling per-list offline sync actually downloads entries for offline reading (#155)

### Selection & Filtering

- [ ] **FILT-01**: Select-all selects all entries in the list, not just the first page (#153)
- [ ] **FILT-02**: Quick Filters (All, Favorites, Archived, Highlights) display bookmark counters in the navigation drawer (#156)
- [ ] **FILT-03**: User can pull-to-refresh on the Highlights view (#157)

## Future Requirements

### From backlog (not in scope for v1.8.0)

- **FEAT-01**: Add 'On open bookmark' custom action (#113)
- **FEAT-02**: Server API version check (#61)
- **FEAT-03**: Add support for bookmark refresh (#18)
- **FEAT-04**: Support other kind of bookmarks (#14)
- **FEAT-05**: Compute reading time for non-synced articles (#9)
- **FEAT-06**: Implement OIDC connect (#5)

## Out of Scope

| Feature | Reason |
|---------|--------|
| Dependency upgrades | Tracked separately |
| New major features (#5, #9, #14, #18, #61, #113) | Deferred to future milestones -- this milestone focuses on fixes and polish |

## Traceability

| Requirement | Phase | Status |
|-------------|-------|--------|
| READER-01 | Phase 03 | Complete |
| READER-02 | Phase 03 | Complete |
| READER-03 | Phase 03 | Complete |
| READER-04 | Phase 03 | Complete |
| SAVE-01 | Phase 04 | Complete |
| SAVE-02 | Phase 04 | Complete |
| LIST-01 | Phase 05 | In Progress |
| LIST-02 | Phase 05 | In Progress |
| FILT-01 | Phase 06 | Pending |
| FILT-02 | Phase 06 | Pending |
| FILT-03 | Phase 06 | Pending |

**Coverage:**
- v1.8.0 requirements: 11 total
- Mapped to phases: 11
- Unmapped: 0

---
*Requirements defined: 2026-03-23*
*Traceability updated: 2026-03-23*
