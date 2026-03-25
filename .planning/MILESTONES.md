# Milestones

## v1.9.0 Platform Health (Shipped: 2026-03-25)

**Phases completed:** 4 phases, 7 plans

**Key accomplishments:**

- 60 unit tests for 7 pure utility/model files: ReadingTimeCalculator, HtmlSanitizer, DateUtils, FaviconUtils, AssetUrlUtils, HighlightOffsetFinder, ListSyncConfig
- 28 unit tests for BookmarkActionController (all 9 action types) and BookmarkActionsRepositorySync (pending action processing, retry logic)
- 25 unit tests for BookmarkSyncPipeline covering all 3 sync configurations, differential sync, content sync strategy dispatch, and paginated fetch
- 53 flow-level tests for SettingsRepository across all 8 settings categories via FakeDataStore with MutableStateFlow + Mutex
- 29 tests from extracted pure functions (filterTagSuggestions, canAddTag, parseTagString) plus TagChip Compose UI tests via Robolectric
- 6 BackupRepository edge-case tests: blank PIN guard, setBackupPin branches, malformed JSON rejection, scheduled export trigger, silent exception swallow

**Total:** ~201 new tests — ~40% → ~75-80% business logic coverage

---

## v1.8.0 Bug Fixes & UX Improvements (Shipped: 2026-03-25)

**Phases completed:** 6 phases, 10 plans, 21 tasks

**Key accomplishments:**

- scrollToTopEnabled setting pipeline from StoredReaderSettings to BookmarkViewerScreenModel with serialization tests, plus bookmark list scroll position persistence fix via hoisted state in MainScreenModel
- Overflow "Details" menu item, scroll-to-top FAB with visibility rules, Behaviour tab toggle, and scroll-position restore fix
- Self-contained BookmarkSavingActivity with singleTask launch mode, retry/close error handling, and replaceAll back stack fix for Android share target
- TDD RED phase: 7 failing test cases defining LIST-01 conditional filtering and LIST-02 offline sync wiring behavior
- Conditional bookmark removal based on list context (LIST-01) and per-list offline sync content fetching with child list hierarchy expansion (LIST-02)
- applyRemoveBookmarkTransform extracted as pure function; test file rewritten to call production code, closing the LIST-01 regression gate
- Fixed select-all to query all matching DB rows without LIMIT/OFFSET, replacing the accumulated list and selecting every ID -- resolves the 20-entry cap bug (#153)
- Reactive drawer counters for All/Favorites/Archived/Highlights via allBookmarks combine() and Room COUNT query, plus PullToRefreshBox on HighlightsScreen
- Robolectric 4.14 + compose-ui-test infrastructure in androidUnitTest with 13 ScreenModel behavioral tests covering select-all, quick filter counts, pull-to-refresh sync, and scroll position regression
- 4 Compose UI scroll-to-top visibility tests and 5 BookmarkSavingActivity lifecycle tests covering READER-03/04 and SAVE-01/02 scenarios

---

## v1.8.0 Bug Fixes & UX Improvements (In Progress)

**Phases:** 03-06 (4 phases, 11 requirements)
**Goal:** Fix 6 bugs and deliver 4 UX improvements across reader, bookmark saving, list sync, and filtering.

---

## v1.7.0 Tech Debt Cleanup (Shipped: 2026-03-21)

**Phases completed:** 2 phases, 3 plans, 6 tasks

**Key accomplishments:**

- Replaced 60 println calls with consolidated AppLogger logging in BookmarkActionsRepositorySync.kt (53) and BookmarkActionsRepository.kt (7)
- Replaced 37 println calls across 5 files with structured AppLogger logging and removed 1 hot-path per-bookmark detail line
- Removed redundant koinInject<ServerRepository>() shadow in App.kt and verified both large files under 500-line target

---
