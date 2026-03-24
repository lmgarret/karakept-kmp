# Roadmap: Karakept KMP

## Milestones

- v1.7.0 Tech Debt Cleanup - Phases 01-02 (shipped 2026-03-21)
- v1.8.0 Bug Fixes & UX Improvements - Phases 03-06 (in progress)

## Phases

<details>
<summary>v1.7.0 Tech Debt Cleanup (Phases 01-02) - SHIPPED 2026-03-21</summary>

### Phase 01: Println Cleanup
**Goal**: All production debug output uses AppLogger -- no raw println remains
**Depends on**: Nothing
**Requirements**: LOG-01
**Plans:** 2/2 plans complete

Plans:
- [x] 01-01-PLAN.md -- Replace println in BookmarkActionsRepositorySync and BookmarkActionsRepository
- [x] 01-02-PLAN.md -- Replace println in BookmarkRepository, HighlightRepository, ImageCacheManager, HtmlRenderer, ViewerScrollRestoration

### Phase 02: File Trimming & Quality
**Goal**: All production files are under 500 lines and no redundant DI calls exist
**Depends on**: Phase 01
**Requirements**: SIZE-01, SIZE-02, QUAL-01
**Plans:** 1/1 plans complete

Plans:
- [x] 02-01-PLAN.md -- Remove redundant koinInject in App.kt and verify file size targets

</details>

### v1.8.0 Bug Fixes & UX Improvements (In Progress)

**Milestone Goal:** Fix 6 bugs and deliver 4 UX improvements across reader, bookmark saving, list sync, and filtering.

- [x] **Phase 03: Reader UX** - Restore scroll position, move info button to overflow menu, add scroll-to-top button (completed 2026-03-23)
- [x] **Phase 04: Bookmark Saving Activity** - Fix Android share-target navigation and state leak (completed 2026-03-23)
- [x] **Phase 05: List & Sync** - Fix quick action list refresh and per-list offline sync (gap closure in progress) (completed 2026-03-23)
- [x] **Phase 06: Selection & Filtering** - Fix select-all pagination, add quick filter counters, add pull-to-refresh for Highlights (completed 2026-03-23)

## Phase Details

### Phase 03: Reader UX
**Goal**: Users have a polished, non-disruptive reader experience with reliable scroll position and quick navigation controls
**Depends on**: Nothing (first phase of v1.8.0)
**Requirements**: READER-01, READER-02, READER-03, READER-04
**Success Criteria** (what must be TRUE):
  1. User opens a bookmark in the reader, scrolls down, closes the reader, and returns to the bookmark list at the same scroll position they left
  2. When the user scrolls past the hero section in the reader, the info button disappears from the hero area and appears as an item in the three-dots overflow menu
  3. User can tap a floating scroll-to-top button in the reader to jump back to the beginning of the article
  4. User can toggle the scroll-to-top button on/off in reader settings, and the preference persists across sessions
**Plans**: 2 plans

Plans:
- [x] 03-01-PLAN.md -- Add scrollToTopEnabled setting plumbing and fix bookmark list scroll restore bug
- [x] 03-02-PLAN.md -- Add Details overflow menu item, scroll-to-top button, and appearance panel toggle

### Phase 04: Bookmark Saving Activity
**Goal**: Android share-target bookmark saving works reliably without trapping the user or leaking state
**Depends on**: Nothing (independent, Android-specific)
**Requirements**: SAVE-01, SAVE-02
**Success Criteria** (what must be TRUE):
  1. User shares a URL from another app to Karakept, saves the bookmark, and can navigate back to the bookmark list without being stuck
  2. User shares a second URL from a different app immediately after saving the first, and the saving activity shows a fresh state with no data from the previous save
**Plans**: 1 plan

Plans:
- [x] 04-01-PLAN.md -- Replace ShareActivity with self-contained BookmarkSavingActivity, add retry/close error handling, fix back stack navigation

### Phase 05: List & Sync
**Goal**: List views stay current after user actions and per-list offline sync downloads content as configured
**Depends on**: Nothing (independent)
**Requirements**: LIST-01, LIST-02
**Success Criteria** (what must be TRUE):
  1. User removes a bookmark from a list via quick actions, and the bookmark immediately disappears from the currently viewed list without manual refresh
  2. User enables offline sync for a specific list in settings, triggers a sync, and the entries in that list are available for offline reading
**Plans**: 3 plans

Plans:
- [x] 05-00-PLAN.md -- Wave 0: Create unit test scaffolds for LIST-01 and LIST-02
- [x] 05-01-PLAN.md -- Fix list removal optimistic update and wire per-list offline sync with child list expansion
- [x] 05-02-PLAN.md -- Gap closure: Extract transform as pure function, rewrite tests to call production code

### Phase 06: Selection & Filtering
**Goal**: Users can effectively select, filter, and refresh their bookmark collections at any scale
**Depends on**: Nothing (independent)
**Requirements**: FILT-01, FILT-02, FILT-03
**Success Criteria** (what must be TRUE):
  1. User taps select-all on a list with more than 20 entries, and all entries in the list are selected (not just the first page)
  2. Quick Filters (All, Favorites, Archived, Highlights) in the navigation drawer display accurate bookmark counters next to each filter name
  3. User can pull-to-refresh on the Highlights view and the list updates with the latest data from the server
**Plans**: 2 plans

Plans:
- [x] 06-01-PLAN.md -- Fix select-all to fetch all matching entities when more pages exist (FILT-01)
- [x] 06-02-PLAN.md -- Add quick filter counters to drawer and pull-to-refresh on Highlights (FILT-02, FILT-03)

## Progress

**Execution Order:**
Phases execute in numeric order: 03 -> 04 -> 05 -> 06 -> 07

| Phase | Milestone | Plans Complete | Status | Completed |
|-------|-----------|----------------|--------|-----------|
| 01. Println Cleanup | v1.7.0 | 2/2 | Complete | 2026-03-21 |
| 02. File Trimming & Quality | v1.7.0 | 1/1 | Complete | 2026-03-21 |
| 03. Reader UX | v1.8.0 | 2/2 | Complete    | 2026-03-23 |
| 04. Bookmark Saving Activity | v1.8.0 | 1/1 | Complete    | 2026-03-23 |
| 05. List & Sync | v1.8.0 | 2/3 | Complete    | 2026-03-23 |
| 06. Selection & Filtering | v1.8.0 | 2/2 | Complete    | 2026-03-23 |
| 07. UI Tests | v1.8.0 | 2/2 | Complete    | 2026-03-24 |

### Phase 7: UI Tests
**Goal:** All v1.8.0 behavioral scenarios have automated regression tests using Robolectric + Compose UI test in androidUnitTest
**Depends on:** Phase 6
**Requirements**: TEST-INFRA, TEST-FILT01, TEST-FILT02, TEST-FILT03, TEST-READER, TEST-SAVE, TEST-REGR
**Success Criteria** (what must be TRUE):
  1. androidUnitTest source set compiles with Robolectric 4.14 + compose-ui-test
  2. selectAll() beyond first page is tested and passes
  3. Quick filter counter computation is tested and passes
  4. Pull-to-refresh on Highlights triggers sync (tested and passes)
  5. Scroll-to-top button visibility logic is tested via Compose rule
  6. BookmarkSavingActivity URL extraction and onNewIntent fresh state are tested
  7. Scroll position persistence regression test passes
**Plans:** 2/2 plans executed

Plans:
- [x] 07-01-PLAN.md -- Build infrastructure + ScreenModel tests (FILT-01, FILT-02, FILT-03, regression)
- [x] 07-02-PLAN.md -- Compose UI tests (READER-03/04) + Activity lifecycle tests (SAVE-01/02)
