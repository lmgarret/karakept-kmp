# Architecture

**Analysis Date:** 2026-03-20

## Pattern Overview

**Overall:** Clean Architecture with MVVM (ScreenModel) for presentation layer, Repository pattern for data layer, and a Domain layer for business logic and actions.

**Key Characteristics:**
- Multiplatform Kotlin (Compose Multiplatform targeting Android)
- Clear separation between UI (Compose Navigation 3 screens), Data (repositories + local/remote sources), and Domain (business logic)
- State management through Kotlin coroutines Flows with ViewModel-based ScreenModels
- Centralized action dispatch through BookmarkActionController with automatic undo support
- Offline-first approach with sync strategies (full, filtered, per-list)
- Generated API client for Remote interactions

## Layers

**UI Layer (Presentation):**
- Purpose: Compose Multiplatform screens and reusable components, state management via ScreenModel
- Location: `composeApp/src/commonMain/kotlin/com/karakept/app/ui/` and `composeApp/src/androidMain/kotlin/com/karakept/app/ui/`
- Contains: Screen composables (MainScreen, BookmarkViewerScreen, LoginScreen, SettingsScreen), reusable components (TagChip, BookmarkTagsDisplay, FilterBottomPanel, TagEditorDialog), theme configuration
- Depends on: Data layer (repositories), Domain layer (business logic utilities, action events)
- Used by: App.kt entry point and the Nav3 `NavDisplay` host (`ui/navigation/`)

**Domain Layer (Business Logic):**
- Purpose: Encapsulate business rules, filtering logic, and centralized action handling
- Location: `composeApp/src/commonMain/kotlin/com/karakept/app/domain/`
- Contains: BookmarkActionController (with undo support), BookmarkActionEvent sealed class hierarchy, ActionSnackbarManager, filter utilities (BookmarkFilterUtils), list hierarchy utilities
- Depends on: Data layer (repositories, DAOs)
- Used by: ScreenModels and repositories

**Data Layer (Repositories):**
- Purpose: Abstract data sources (local DB and remote API), provide unified interfaces for data access
- Location: `composeApp/src/commonMain/kotlin/com/karakept/app/data/repository/`
- Contains: BookmarkRepository, ListRepository, ServerRepository, SettingsRepository, HighlightRepository, BookmarkActionsRepository, BackupRepository
- Depends on: Local data source (DAOs, SQLDelight DB), Remote data source (Ktor client with generated API)
- Used by: ScreenModels and domain layer (BookmarkActionController)

**Local Data Source:**
- Purpose: Persistent storage via Room/SQLDelight, offline state, pending actions queue
- Location: `composeApp/src/commonMain/kotlin/com/karakept/app/data/local/`
- Contains: AppDatabase (Room), DAOs (BookmarkDao, ListDao, ServerDao, etc.), entities (BookmarkEntity, ListEntity, PendingActionEntity, HighlightEntity)
- Platform-specific: `composeApp/src/androidMain/kotlin/com/karakept/app/data/local/` handles database builder and DataStore initialization

**Remote Data Source:**
- Purpose: HTTP communication with Karakept server, API abstraction, offline mode guard
- Location: `composeApp/src/commonMain/kotlin/com/karakept/app/data/remote/`
- Contains: RemoteDataSource (wraps generated API clients), KtorClient (HTTP setup), API client instances (BookmarksApi, ListsApi, TagsApi, HighlightsApi, UsersApi)
- Uses: Ktor HttpClient with authentication interceptor, generated API clients

## Data Flow

**Bookmark Sync Flow:**

1. ScreenModel (MainScreenModel) triggers `syncBookmarks(server)` on user action
2. BookmarkRepository.executeSyncPipeline(SyncConfiguration) called
3. RemoteDataSource guards against offline mode and fetches from API
4. Bookmarks mapped from API model → BookmarkEntity and persisted to Room DB
5. Flow<List<BookmarkEntity>> emitted back to UI
6. ScreenModel collects and updates UI state

**Sync is streamed, and split into two stages.** Cursor pagination is serial, so a large
library would otherwise cost one round trip per 100 bookmarks before a single row reached
the DB. Instead `BookmarkSyncPipeline` commits each page as it arrives:

- Local state (`getBookmarksForServerWithContentInfo`) is read **once** per sync and
  maintained in memory across pages — never re-read per page.
- Each page is diffed and written immediately; `BookmarkRepository.pageCommitted` emits,
  and `MainScreenModel` calls `refreshLoadedPagesInPlace` so the list fills in progressively.
- Deletion reconciliation runs **only after the last page**, against the union of every
  page's ids. A fetch that fails part-way commits what it got and deletes nothing.
- The *foreground* stage ends once those rows have landed; `onForegroundComplete` clears the
  per-key `ListSyncStatus` **and releases the SyncKey**, so the progress bar and
  pull-to-refresh spinner stop there and a new sync for the same key can start.
- *Enrichment* — highlights, content download, reading progress — runs afterwards. It is
  deduplicated by a **separate** `enrichmentKeys` set via the `shouldRunEnrichment` gate:
  holding the sync key itself through enrichment meant a pull-to-refresh during a long
  content download hit the dedup check and silently did nothing.

**Bookmark Action Flow (with Undo):**

1. User triggers action in UI (delete, archive, favorite, etc.)
2. ScreenModel emits BookmarkActionEvent to BookmarkActionController
3. BookmarkActionController.executeAction():
   - Captures undo state (original bookmark + position)
   - Routes to BookmarkActionsRepository for backend sync
   - Caches action in undo map (5-second window)
   - Immediately updates local DB
   - Emits ActionSnackbarManager event
4. ActionSnackbarManager displays snackbar with undo button
5. User can undo: BookmarkActionController reads from undo cache and reverses operation
6. Pending actions queued in PendingActionDao for sync when online

**Tag Filtering Flow:**

1. MainScreenModel.currentFilter updated with tag filter
2. BookmarkFilterUtils.filterBookmarks() applied to bookmark stream
3. Filtered list emitted to BookmarkList composable
4. TagEditorDialog (with canCreateNew=false) for filter mode

**List Hierarchy Display:**

1. ListRepository.lists emits List<KarakeepList>
2. ListHierarchyUtils.buildListHierarchy() sorts alphabetically at each level, parents before children
3. filterExpandedHierarchy() applied if collapsible UI (navigation drawer)
4. NavigationDrawerItem rendered for each hierarchy node

**State Management:**

- StateFlow used for UI state (selectedServer, isSyncing, currentFilter, expandedLists)
- SharedFlow used for events (scrollToTopTrigger, createBookmarkResult, undoCompletedEvents)
- viewModelScope cancels flows when the screen's ViewModel is cleared (its Nav3 entry leaves the back stack)
- Coroutines.flow for cold flows (database queries wrapped in flow { emitAll(...) })

## Key Abstractions

**BookmarkActionEvent (Sealed Class):**
- Purpose: Type-safe representation of all possible bookmark mutations
- Examples: Archive, Unarchive, MarkRead, MarkUnread, ToggleFavorite, Delete, UpdateTags, MoveToList, RemoveFromList
- Location: `composeApp/src/commonMain/kotlin/com/karakept/app/domain/action/BookmarkActionEvent.kt`
- Pattern: Sealed class hierarchy with discriminator field `requiresUndo` to determine if undo support applies

**Server (Data Model):**
- Purpose: Represents authenticated connection to a Karakept server
- Location: `composeApp/src/commonMain/kotlin/com/karakept/app/data/model/Server.kt`
- Contains: id, url, apiKey, userId, username
- Used by: All repositories to route requests to correct server

**SyncConfiguration (Internal to BookmarkRepository):**
- Purpose: Describes what bookmarks to sync and how to handle deletions
- Variants: Full (fetch all, delete removed), Filtered (archived/favorited only, upsert only), ForList (specific list)
- Pattern: Sealed class with abstract properties controlling sync behavior

**FilterConfig:**
- Purpose: Encapsulates all active filters (tags, lists, archived, favorited, search query)
- Location: `composeApp/src/commonMain/kotlin/com/karakept/app/data/model/FilterConfig.kt`
- Used by: MainScreenModel and BookmarkFilterUtils

**The effective filter identifies a view.** `MainScreenModel.currentFilter` is what the user
picked; `effectiveFilter` is that plus the child lists it expands into for lists configured
with `includeChildListBookmarks` (`ListHierarchyUtils.expandFilterLists`). The expansion
depends on the drawer's lists, which load asynchronously, so it can change after a view is on
screen. Consequences worth knowing before touching this code:

- The window is a flow of `(selectedServer, effectiveFilter)`, so a change to either — including
  the expansion resolving after the view is on screen — cancels the reads belonging to the
  previous view rather than racing them. There is no window to write into out of turn.
- A page therefore depends only on the filter it is read with and its offset. No settings
  lookups, nothing captured.

**The bookmark list is virtualized.** `MainScreenModel.bookmarkWindow` is sized by a `COUNT(*)`
over the view and holds only the pages under the viewport; everything else is a
`BookmarkSlot.Placeholder`. Consequences worth knowing:

- An index means a position in the view from the first frame, so a jump — the fast-scroll
  cursor above all — is `scrollToItem` and the page under it is fetched afterwards. It used to
  be a walk: the list was indexed by the rows read so far, so reaching an arbitrary row meant
  reading everything in between (#273).
- **The whole view is one `WHERE`** (`BookmarkRepository.buildViewPredicate`), including the
  clauses that were once applied in Kotlin after the read — tags, a multi-list selection, the
  read filter, the content filter. That is what makes an offset mean a view index; split across
  the two, a read had to over-fetch and estimate what the Kotlin side would discard.
  `buildCountQuery`, `buildPageQuery`, `buildViewIdsQuery` and `buildSearchQuery` all build on
  it, so a count and the rows it counts cannot disagree.
- **Room's invalidation is the refresh.** The count flow re-emits on any write to the table and
  the pages on screen are read again, so a row edited elsewhere, a page a sync has committed,
  and an action's own optimistic write all reach the screen the same way. There are no
  refresh-in-place calls and no list patching; an action writes its row and the list follows.
- **Nothing accumulates**, so the position drift behind #333 is not expressible: each read
  states the offset it wants, and the list's size comes from the table rather than from how far
  a walk got.
- Membership in a comma-separated column (`listIds`, `tags`) is matched with
  `instr(',' || col || ',', ',' || ? || ',')`, never `LIKE` — a tag is free text and may hold
  `%` or `_`.

**Counting does not read the rows.** The drawer's per-list counts come from rows the database
groups on `(listIds, isRead)`, the tag counts from rows grouped on `tags`, and the quick filters
from one pass in SQL. `ListCountUtils` and `TagCountUtils` walk those buckets. The main screen
used to hold the whole table resident (`allBookmarks`) plus a filtered, sorted copy of the
current view, rebuilt on every write, to answer these.

**AppDispatchers:**
- Purpose: The dispatcher set used by the data layer, injected instead of referenced statically
- Location: `composeApp/src/commonMain/kotlin/com/karakept/app/utils/AppDispatchers.kt`
- Bound as a Koin `single<AppDispatchers> { DefaultAppDispatchers() }`; tests bind
  `TestAppDispatchers(testDispatcher)` so every coroutine the code starts stays on the test
  scheduler and is drained by `advanceUntilIdle()`
- Injected into `BookmarkRepository`, `BookmarkActionsRepository`, `ListRepository` and
  `BookmarkActionController`

**ScreenModel (`androidx.lifecycle.ViewModel` subclass):**
- Purpose: Lifecycle-scoped state holder for a screen, survives configuration changes; scoped per Nav3 back-stack entry via `rememberViewModelStoreNavEntryDecorator`
- Pattern: Each screen has a corresponding ScreenModel bound with `viewModel { }` in Koin (LoginScreenModel, MainScreenModel, BookmarkViewerScreenModel, etc.), obtained with `koinViewModel`
- Dependencies injected via Koin

## Entry Points

**App Composable:**
- Location: `composeApp/src/commonMain/kotlin/App.kt`
- Triggers: LaunchedEffect on compose startup
- Responsibilities:
  - Initialize Koin DI (via KoinApplication wrapper)
  - Set up image loader with authentication for asset URLs
  - Route to initial screen based on app state (OnboardingScreen, LoginScreen, MainScreen, ShareBookmarkScreen, BookmarkViewerScreen)
  - Apply theme (AppTheme with Material3 colors and accent color)
  - Initialize the Nav3 `NavDisplay` host (back stack + entry decorators + Shared Axis Z / predictive-back transitions)

**MainScreen / MainScreenModel:**
- Location: `composeApp/src/commonMain/kotlin/com/karakept/app/ui/screens/MainScreen.kt`
- Triggers: After login/onboarding, when servers exist
- Responsibilities:
  - Display list of bookmarks with filters, search, sorting
  - Manage list hierarchy (navigation drawer)
  - Coordinate bookmark sync, delete, edit, and tag operations
  - Report which slots are on screen, so the pages holding them are the ones read
  - Show sync progress and pending bookmark indicators
  - Route to BookmarkViewerScreen for detail view

**BookmarkViewerScreen / BookmarkViewerScreenModel:**
- Location: `composeApp/src/commonMain/kotlin/com/karakept/app/ui/screens/BookmarkViewerScreen.kt`
- Triggers: User taps bookmark from MainScreen or via deep link
- Responsibilities:
  - Render full bookmark details with hero image, HTML content, highlights
  - Display and manage highlights (create, delete, sync)
  - Show reader mode (with font/theme/size customization)
  - Handle tag editing and bookmark actions (favorite, archive, move to list)

## Error Handling

**Strategy:** Try-catch with Result<T> for operations that can fail, OfflineModeException for network requests in offline mode.

**Patterns:**

- RemoteDataSource.guardedCall() blocks all network requests if offline mode enabled → throws OfflineModeException
- BookmarkActionController.executeAction() returns BookmarkActionResult (Success or Error sealed class)
- Repositories catch exceptions and emit empty lists or null on failure (best-effort)
- ScreenModels catch exceptions in LaunchedEffect and emit failure to UI via SharedFlow (e.g., _createBookmarkResult)
- UI displays snackbars via ActionSnackbarManager when actions complete
- Backup/restore operations wrapped in try-catch (best-effort, logged but not fatal)

## Cross-Cutting Concerns

**Logging:**
- println() debug statements throughout (e.g., "🖼️ Coil loading image", "🔐 Adding auth header")
- No centralized logging framework; production should upgrade to proper logger

**Validation:**
- Server URL normalization in RemoteDataSource (strip trailing slash, ensure /api/v1 suffix)
- Bookmark parsing: title sanitization in remote sync, HTML content validation
- Tag filtering: empty tag lists filtered out in BookmarkFilterUtils

**Authentication:**
- Bearer token attached by RemoteDataSource to all generated API client calls
- Asset URLs (Coil images) require Authorization header intercepted in App.kt
- Server credentials stored in Room DB (ServerEntity), sensitive data protected by device encryption

**Synchronization (Offline-First):**
- Pending actions queued in PendingActionDao before network attempt, replayed with exponential backoff
- Queued actions are flushed when the app returns to the foreground (see App.kt)
- Sync mutex in BookmarkRepository prevents concurrent syncs
- SettingsRepository.offlineMode (the user's manual toggle) checked before remote calls
- Exception: server-side crawl actions (`ServerCrawlAction` — refresh / preserve archive /
  preserve PDF, plus deleting an asset on the server) deliberately bypass the pending-action
  queue. They ask the server to run a background job and have no optimistic local counterpart,
  so there is nothing to apply offline or to undo; the UI disables them in offline mode instead.

**Threading:**
- Repositories are main-safe: `BookmarkRepository.executeSyncPipeline` and
  `ListRepository.refreshListsInternal` switch to `appDispatchers.io` themselves, so ScreenModels
  never wrap a repository call in `withContext`
- Background work that must outlive its caller runs on the owning `single`'s scope
  (`BookmarkRepository.repositoryScope`, `BookmarkActionsRepository.repositoryScope`), never on
  `GlobalScope`
- The only remaining direct `Dispatchers` references are platform entry points
  (`main.kt`, `KarakeptApp.kt`, the desktop `FilePicker`) and CPU-bound HTML parsing in
  `ui/components/HtmlContent.kt`

**E-ink display mode:**
- E-ink is its own top-level settings section (`SettingsSection.EINK`), not a link under
  Appearance. Only a couple of its switches are about how the app looks — instant scrolling, row
  action buttons and the hardware page-turn bindings change how it *behaves* — so hanging the
  whole screen off "Appearance" described the minority of it.
- Inside the screen the master "E-ink mode" switch sits above four groups — Display, Motion,
  Interaction, Page-turn buttons — declared in `EinkSettingsSections.kt` rather than in the
  composable. `visibleEinkSettings` holds the gating rules (which switches the master turns off,
  and the two that outlive it: the monochrome icon and the page-turn buttons) and
  `visibleEinkGroups` buckets them, dropping a group whose settings are all gated away so no
  heading is left stranded over nothing. `EinkSettingsContent` renders that list in order,
  which keeps the visibility rules testable without a Compose test rule.
- `LocalEinkMode` (`ui/theme/EinkMode.kt`) is a `staticCompositionLocalOf` resolved once in
  `App.kt` from `SettingsRepository.einkDisplaySettings` and provided by `AppTheme`. It carries
  `animationsDisabled`, `highContrast` and `instantScroll`, each already ANDed with the master
  "E-ink mode" setting so consumers check only the flag they care about.
- A CompositionLocal rather than parameters: the sites that must change behaviour (nav
  transitions, list item animation, image crossfades, skeletons, progress indicators, ripple,
  every elevation-separated surface) are spread across the whole tree, and threading a flag
  through them all would touch far more signatures than it is worth.
- `getColorScheme(…, highContrast = true)` swaps the accent palette for `einkColorScheme()`,
  which flattens every surface role to the page colour and moves all separation onto `outline`.
  It stays orthogonal to `ThemeMode` so it composes with LIGHT/DARK/SYSTEM.
- The launcher icon and splash have a black-on-white variant behind `einkMonochromeIcon`,
  applied by `AppIconManager` (`utils/`, `expect`/`actual`; a no-op on desktop, where the shell
  reads the icon before the JVM starts). Android cannot re-point an icon at runtime, so the
  manifest declares two `activity-alias` launcher entries — `.LauncherDefault` and
  `.LauncherMonochrome` — and the manager enables one and disables the other, always in that
  order so the package is never momentarily without a launcher entry. `KarakeptApp` drives it
  from the settings flow rather than from the switch, so a restored backup lands too.
- Alone among the e-ink settings it is *not* ANDed with the master switch: the home screen goes
  on showing the icon after e-ink mode is turned off, and putting the colour artwork back
  unasked would be a change the user never made.
- The splash follows the icon through `MainActivity.applySplashScreenTheme()`, which needs
  opposite treatment per API level — `SplashScreen.setSplashScreenTheme` from API 31 (the system
  paints the splash before the process exists, so the override applies from the next cold start),
  plain `setTheme` before it (androidx draws the splash as the window background and reads the
  theme in `installSplashScreen()`). The flag is mirrored into SharedPreferences because that
  code runs before a suspending DataStore read has anywhere to go.

**Hardware key input:**
- `PageTurnDispatcher` (`ui/input/`, a Koin `single`) owns the key-code → page-turn mapping and
  broadcasts `PageTurnDirection` over a `SharedFlow`. Screens opt in with `PageTurnScrollEffect`.
- It lives outside Compose because Android must intercept in `MainActivity.dispatchKeyEvent` to
  beat the system volume overlay to a bound volume key — a Compose key modifier runs too late.
- It mirrors bindings into a `StateFlow` on its own scope (built from `appDispatchers.default`),
  because the platform key callback cannot suspend to read them.
- Codes are learned from the device, except the volume rocker — `PageTurnKeyBindings.useVolumeKeys`
  binds it directly, since e-ink readers overwhelmingly wire their facade buttons to it. The two
  codes come from `expect object PlatformKeyCodes`.
- The same window rule constrains the *learning* UI: key capture is rendered inline in the E-ink
  settings screen, never in a Compose `Dialog`, because a dialog is its own platform window and
  `MainActivity.dispatchKeyEvent` never sees the keys pressed while it has focus.
- Page turns scroll instantly off `PageTurnKeyBindings.instantPageTurn` rather than
  `LocalEinkMode.instantScroll`, which folds in the master e-ink switch the buttons are not gated
  on. In the reader an instant move must be re-approved via
  `ScrollRestorationState.approveCurrentPosition()`, or the scroll guard snaps it back.

**Dependency Injection:**
- Koin module configured in AppModule.kt (single instances for repositories, factories for ScreenModels)
- Circular dependency between BookmarkActionsRepository and BookmarkRepository resolved manually in AppModule via setBookmarkRepository()

---

*Architecture analysis: 2026-03-20*
