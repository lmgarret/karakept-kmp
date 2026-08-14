# Karakept KMP — AI Agent Guidelines

## Project Overview

Karakept is a Kotlin Multiplatform (KMP) bookmark manager app built with Compose Multiplatform,
targeting Android and JVM Desktop (Linux, macOS, Windows).
The UI follows **Material Design 3 (MD3)** guidelines throughout.

Key technologies:
- Kotlin 2.4.0 / Compose Multiplatform 1.11.1
- Material3 (`androidx.compose.material3`)
- Compose Navigation 3 (`androidx.navigation3` / `org.jetbrains.androidx.navigation3` 1.1.1) for navigation
- `androidx.lifecycle` `ViewModel` (multiplatform) for per-screen state (MVVM)
- Koin 4.2.2 for dependency injection (incl. `koin-compose-viewmodel`, `koin-compose-navigation3`)
- Room 2.8.4 for local SQLite storage
- Ktor 3.5.1 for HTTP/API communication
- kotlinx-serialization, kotlinx-coroutines, kotlinx-datetime

---

## Architecture

The app follows **Clean Architecture** with three clear layers:

```
UI Layer (Compose screens + ScreenModels)
    ↓
Domain Layer (business logic, filters, action events)
    ↓
Data Layer (repositories → local Room DB + remote Ktor API)
```

**Key patterns:**
- **ScreenModel** = an `androidx.lifecycle.ViewModel` subclass (named `*ScreenModel` by convention), scoped per Nav3 back-stack entry via `rememberViewModelStoreNavEntryDecorator`, injected with `koinViewModel`.
- **Navigation 3**: the back stack is a developer-owned `NavBackStack` of `@Serializable` `NavKey`s, rendered by `NavDisplay`. Each screen *is* a `NavKey` with a `Content()` composable; routing goes through `AppNavigator`/`LocalNavigator` (`ui/navigation/`). Predictive back (Android 14+) is wired via `NavDisplay`'s `predictivePopTransitionSpec`.
- **Repository pattern**: each domain concept (`BookmarkRepository`, `ListRepository`, etc.) is the single source of truth.
- **Offline-first**: mutations are queued in `PendingActionDao` and synced on reconnect.
- **BookmarkActionController**: centralizes all bookmark mutations with 5-second undo support.
- **StateFlow / SharedFlow**: ScreenModels expose `StateFlow` for UI state and `SharedFlow` for one-shot events.

### Layer Locations

| Layer | Path |
|---|---|
| UI screens & ScreenModels | `composeApp/src/commonMain/kotlin/com/karakept/app/ui/screens/` |
| Reusable UI components | `composeApp/src/commonMain/kotlin/com/karakept/app/ui/components/` |
| Theme (MD3 colors, typography) | `composeApp/src/commonMain/kotlin/com/karakept/app/ui/theme/` |
| Domain logic | `composeApp/src/commonMain/kotlin/com/karakept/app/domain/` |
| Repositories | `composeApp/src/commonMain/kotlin/com/karakept/app/data/repository/` |
| Local DB (Room entities, DAOs, migrations) | `composeApp/src/commonMain/kotlin/com/karakept/app/data/local/` |
| Remote API (Ktor, RemoteDataSource) | `composeApp/src/commonMain/kotlin/com/karakept/app/data/remote/` |
| Domain data models | `composeApp/src/commonMain/kotlin/com/karakept/app/data/model/` |
| DI module | `composeApp/src/commonMain/kotlin/com/karakept/app/di/AppModule.kt` |
| Shared utilities | `composeApp/src/commonMain/kotlin/com/karakept/app/utils/` |
| Android-specific code | `composeApp/src/androidMain/` |
| Generated API client | `api-client/` (OpenAPI-generated, committed) |

### Entry Points

- **`App.kt`**: root composable, initializes Koin, routes to initial screen based on app state.
- **`MainActivity.kt`** (Android): sets up the Compose activity.
- **`AppModule.kt`**: all Koin bindings — repositories as `single()`, ScreenModels as `viewModel()`.
- **`ui/navigation/`**: `AppNavigator`/`LocalNavigator` (back-stack wrapper + CompositionLocal), `appEntryProvider()` + `navKeySerializersModule` (NavKey↔content map and polymorphic registry), and the Shared Axis Z `NavDisplay` transition specs.

---

## Code Conventions

### Naming

| Concept | Pattern | Example |
|---|---|---|
| Screen composable | `*Screen.kt` | `MainScreen.kt` |
| ScreenModel | `*ScreenModel.kt` | `MainScreenModel.kt` |
| Repository | `*Repository.kt` | `BookmarkRepository.kt` |
| DAO | `*Dao.kt` | `BookmarkDao.kt` |
| Room entity | `*Entity.kt` | `BookmarkEntity.kt` |
| DB migration | `Migration*To*.kt` | `Migration7To8.kt` |
| Utility | `*Utils.kt` | `ListHierarchyUtils.kt` |
| Unit test | `*UnitTest.kt` or `*Test.kt` | `BookmarkRepositoryUnitTest.kt` |
| Integration test | `*IntegrationTest.kt` | `BookmarkSyncIntegrationTest.kt` |

- Composable functions: **PascalCase** (`TagChip`, `FilterBottomPanel`)
- All other functions: **camelCase** (`syncBookmarks`, `buildListHierarchy`)
- Boolean fields: descriptive (`isArchived`, `isSyncing`, `shouldFetchLists`)
- Private mutable StateFlow: `_name` (backing field), exposed as `name: StateFlow<T>`

### Code Style

- Kotlin official code style (`kotlin.code.style=official` in `gradle.properties`).
- 4-space indentation.
- No hardcoded colors, text styles, or sizes — always use `MaterialTheme.*`.
- Use `HorizontalDivider` (not the deprecated `Divider`).
- Prefer `expect`/`actual` for platform-specific behavior over `if (platform == Android)` checks.
- `Result<T>` for operations that can fail; `suspend` functions may throw and callers catch.
- No custom exception types — use `Exception` with descriptive messages.
- Default comments to none. Only add a comment when the **why** is non-obvious. No multi-line docblocks unless documenting a public API.

### Coroutines

- `viewModelScope` in ScreenModels for lifecycle-aware launching.
- `SharingStarted.WhileSubscribed(5000)` when converting `Flow` to `StateFlow`.
- `runTest(testDispatcher)` in unit tests; base class `BaseRepositoryTest` provides the dispatcher
  and a matching `testAppDispatchers`.
- **Never reference `Dispatchers.IO` / `Dispatchers.Default` directly** outside platform entry
  points. Inject `AppDispatchers` (`utils/AppDispatchers.kt`) and use `appDispatchers.io` /
  `appDispatchers.default`. A hardcoded dispatcher is invisible to `advanceUntilIdle()`, so any
  test driving that code samples state the production code has not reached yet.
- **Dispatchers belong to the data layer, not to ScreenModels.** A repository makes itself
  main-safe; callers never wrap a repository call in `withContext`.
- **Never use `GlobalScope`.** Work that must outlive a screen belongs on the scope of the
  Koin `single` that owns it (see `BookmarkActionsRepository.persistFinalReadingProgress`).

### Where to Add New Code

| New thing | Where |
|---|---|
| New screen | `ui/screens/[Feature]Screen.kt` (a `@Serializable` `NavKey` with `Content()`) + `[Feature]ScreenModel.kt`; register `viewModel { }` in `AppModule.kt`, add an `entry<…>` in `appEntryProvider()` and a `subclass(…)` in `navKeySerializersModule` |
| New reusable component | `ui/components/[Component].kt` |
| New repository | `data/repository/[Domain]Repository.kt`, add `single()` to `AppModule.kt` |
| New DB entity | `data/local/entity/`, new DAO, update `AppDatabase.kt`, add migration, register in builders |
| New domain logic | `domain/[Logic]Utils.kt` or extend `BookmarkActionEvent` + `BookmarkActionController` |
| New shared utility | `utils/[Util]Utils.kt` (commonMain) or `utils/[Util].android.kt` (androidMain) |

---

## Modularization & Code Health

- **Keep code modular and reusable.** Prefer extracting shared logic to utilities or components rather than duplicating.
- **When you identify a refactoring opportunity** that would improve health (reduce duplication, clarify boundaries, improve testability), **mention it explicitly** as a suggestion in your response — but do not implement it unless asked.
- **Composables should be small and focused.** Extract helper composables liberally; pull state up to the ScreenModel.
- **Repository classes are the only public API of the data layer.** DAOs and remote sources are internal.

---

## Reusable UI Components — ALWAYS use these

### Tag display

**`TagChip`** (`ui/components/TagChip.kt`)
- Single tag chip: `secondaryContainer` surface, `shapes.small`, 2dp elevation.
- Supports optional `onRemove` (shows ✕ button) and `onClick`.
- **Use everywhere a single tag chip is displayed.**

**`BookmarkTagsDisplay`** (`ui/components/BookmarkTagsDisplay.kt`)
- Renders a comma-separated tag string as a `FlowRow` of `TagChip` chips.
- Accepts `style` (`COMPACT` / `READER`), optional `onTagClick`, and `modifier`.
- **Use whenever a bookmark's full tag list needs to be rendered.**

> **Rule:** Tags must look the same everywhere. Never use `AssistChip`, `FilterChip`, plain `Text`, or custom surfaces for displaying tags. `BookmarkTagsDisplay` delegates to `TagChip` — keep it that way, or e-ink and theming fixes land in one place and not the other.

### List hierarchy

All hierarchy helpers live in the single `ListHierarchyUtils` object
(`domain/ListHierarchyUtils.kt`) and are called as `ListHierarchyUtils.<fn>(…)`.

**`ListHierarchyUtils.buildListHierarchy(lists)`**
- Converts a flat `List<KarakeepList>` into `List<Pair<KarakeepList, Int>>` sorted parents-before-children, alphabetically at each level.
- **Use everywhere lists are displayed** (pickers, filter panels, sync settings, navigation drawer).

**`ListHierarchyUtils.filterExpandedHierarchy(hierarchy, expandedIds)`**
- Filters hierarchy to branches whose ancestors are all expanded. Use with `buildListHierarchy` for collapsible trees.

**`ListHierarchyUtils.listHasChildren(listId, allLists)`**
- Checks if a list has direct children.

**`ListHierarchyUtils.getAllDescendantIds(parentId, allLists)`** / **`ListHierarchyUtils.getAncestorIds(listId, allLists)`**
- Depth-first descendant / ancestor traversal (cycle-safe). Used for counting nested bookmarks and auto-expanding parent nodes.

> **Rule:** Never sort lists manually or display them in a flat unordered layout.

### Bookmark row styles

`BookmarkLayout` (`data/model/BookmarkLayout.kt`) drives how a bookmark row renders. Three fields
control its container rather than its content:

| Field | Effect |
|---|---|
| `itemContainerStyle` | `CARD` (Material default) or `FLAT` (no container, `HorizontalDivider` between rows) |
| `showThumbnail` | `false` omits the thumbnail box entirely — `thumbnailSize` has no "off" value |
| `readIndicatorStyle` | `DIM` (50% alpha) or `MARKER` (bullet + weight, full contrast throughout) |
| `showRowDivider` | Flat rows only — a card already separates itself |
| `titlePosition` | `BESIDE_THUMBNAIL` or `ABOVE_THUMBNAIL` (title spans the row, image below). No effect without a thumbnail |

| `descriptionMaxLines` | Line cap, or `DESCRIPTION_LINES_AUTO` to fill the space the thumbnail leaves over |

Built-ins run densest to richest — **Compact, Rows, Cards, Digest, Magazine** — and that is the
order the picker shows. `Rows` (flat + divider) is the one to reach for on e-ink; turn its
thumbnail off and it collapses to plain text rows. Enabling E-ink mode never changes the active
layout.

The automatic description count measures the title with a `TextMeasurer` and needs the row's
width, which `BookmarkListContent` measures **once for the whole list** — a row cannot ask for its
own width without a subcomposition, and doing that per row in a `LazyColumn` is what this avoids.

A row wrapper (`SwipeableBookmarkItem`, `QuickActionBookmarkItem`) takes `flat` so the row goes
full-bleed and its divider reaches both edges.

### Tag editing and filtering

**`TagEditorDialog`** (`ui/components/TagEditorDialog.kt`)
- `canCreateNew = true` (default): bookmark tag editing — allows free-text tags.
- `canCreateNew = false`: filter-by-tag mode — only existing tags selectable.
- Always pass `availableTags` from the screen's state for suggestions.

> **Rule:** Never implement custom tag-selection dialogs or text fields.

### Loading indicators

**`LoadingDotsIndicator`** (`ui/components/EinkAware.kt`)
- Three dots that fill in sequence — off e-ink a smooth continuous wave, on e-ink a plain
  `delay`-driven step from one dot to the next (one discrete repaint at a time, no interpolation
  to ghost). Takes an optional `label` (shown as `Text` below the dots) and `dotSize`.
- **E-ink-only replacement for skeletons**, not a redesign of the non-e-ink loading state. Every
  skeleton in the app (`BookmarkContentLoader`, `SkeletonLoader`, `ImageLoadingSkeleton`,
  `BookmarkPlaceholderItem`) keeps its original shimmer for normal displays — that visual is
  unchanged — and branches to `LoadingDotsIndicator` only when `LocalEinkMode.current.animationsDisabled`
  is true. A shimmer is a continuous animation, which on e-ink means either permanent ghosting or
  nothing visible at all (its tonal fill collapses into the page color under high contrast); dots
  fixes that without touching how loading looks anywhere else.

**`InlineLoadingDots`** (`ui/components/EinkAware.kt`)
- The same dots on one line, label beside them instead of below. For slots too short for the
  stacked form: the bookmark list's sync strip, the reader top bar's refresh bar, a drawer row's
  trailing status, an asset download with no `Content-Length`.
- Every one of those slots used to hold an **indeterminate** `LinearProgressIndicator` or
  `CircularProgressIndicator` — a bar that sweeps forever, which on e-ink shows as a smear or,
  on panels that throttle refreshes, as nothing at all. Determinate bars are fine there and stay
  as they are: they only repaint when progress moves.

**`BusyIndicator`** (`ui/components/EinkAware.kt`)
- A "working on it" signal: `CircularProgressIndicator` normally, `LoadingDotsIndicator` on e-ink.
  Use it for bottom-of-list "loading more" footers and centred busy states.

> **Rule:** A new skeleton must branch the same way — keep its shimmer for normal displays, swap to
> `LoadingDotsIndicator` under `LocalEinkMode.current.animationsDisabled`. Never let e-ink adjustments
> change what non-e-ink users see. The same goes for an indeterminate bar or spinner: keep it off
> e-ink, swap to `InlineLoadingDots` on it.

### Pull to refresh

**`RefreshableBox`** (`ui/components/EinkAware.kt`) — use instead of `PullToRefreshBox` anywhere a
screen offers refresh.

- Wraps content in `PullToRefreshBox`, or in a plain `Box` when e-ink mode is on or the caller
  passes `enabled = false` (desktop, where there is no finger to pull with).
- The gesture tracks a finger across many frames and drives a spinner that animates until the
  refresh returns — both smear on e-ink — and overscroll is foreign to a reader's page-turn-first
  interaction model.

> **Rule:** refresh must never become unreachable. A screen that drops the gesture has to put a
> `Refresh` `IconButton` in its top bar, gated on `shouldShowRefreshButton(isDesktop, einkMode)`
> — the exact inverse of the `shouldUsePullToRefresh` check `RefreshableBox` makes, so exactly one
> of the two is live at any time.

### Empty states

- A list with nothing in it needs an **explicit empty state**, never a blank area — the two
  are indistinguishable to the user.
- Gate it on `MainScreenModel.isLoadingInitialPage`, which is false only once the first page
  has actually resolved. Rendering the empty state unconditionally flashes "nothing here" on
  every cold start before the list arrives.

### Full-screen image viewer

**`ImageGalleryDialog`** (`ui/components/reader/ImageGalleryDialog.kt`)
- Full-screen `Dialog` on a black scrim wrapping a `HorizontalPager`: swipe left/right between
  every image on the page. Each page (`ZoomableImagePage`) owns its own independent pinch-to-zoom,
  double-tap zoom, mouse scroll-wheel zoom (desktop), and drag-to-pan once zoomed in. Tapping an
  image at 1x dismisses the whole dialog; swiping up does too.
- The pager's own swipe-to-change-image gesture is disabled while the active page is zoomed in
  above 1x, so panning a zoomed image never gets mistaken for a page flip.
- The page-wide image list comes from `LocalGalleryImages` (`ReaderGalleryState.kt`), pre-scanned
  once per document by `NativeHtmlRenderer` via `collectGalleryImages`.
- Wired into `RenderResolvedImage` (`HtmlBlockRenderer.kt`) — every `<img>`/`<picture>`
  rendered in Reader mode is tappable to open the gallery positioned on that exact image.
- **Use whenever an image needs a tap-to-enlarge, swipe-between-siblings full-screen view.**

### E-ink mode

The app runs on electronic-paper readers, where every animated frame is a full-panel refresh
that ghosts, and MD3's tonal surface steps collapse into indistinguishable greys.
`LocalEinkMode` (`ui/theme/EinkMode.kt`) carries two flags — `animationsDisabled` and
`highContrast` — both already folded together with the master "E-ink mode" setting.

**New UI must respect it:**
- No animation gated only on itself. Use `AnimatedVisibilityOrPlain`
  (`ui/components/EinkAware.kt`) instead of `AnimatedVisibility`, and
  `LazyListState.scrollToTop(instant)` instead of `animateScrollToItem(0, 0)`.
- Never rely on a tonal fill or `shadowElevation` alone to separate an element from the page —
  under `highContrast` every surface role is the same colour. Add
  `border(1.dp, colorScheme.outline)` in that case, as `TagChip`, `BookmarkLayouts` and
  `BaseBottomPanel` do.
- No shimmer or indeterminate spinner as the only "busy" signal; fall back to the stepped dots
  (`BusyIndicator` / `InlineLoadingDots` in `ui/components/EinkAware.kt` do this).
- `secondaryContainer` is the scheme's one deliberate grey, for small repeated elements like tag
  chips. Every other surface role is the page colour — do not reintroduce tonal steps.
- Gestures that track a finger across many frames (swipe-to-act) smear on e-ink. `RowActionMode`
  lets the bookmark list swap them for the always-visible button cluster desktop uses, and
  `RefreshableBox` swaps pull-to-refresh for a top-bar button.

### Hardware page-turn buttons

`PageTurnDispatcher` (`ui/input/PageTurnDispatcher.kt`, a Koin `single`) maps device key codes to
page turns. Key codes are *learned from the device* in the E-ink settings screen, not hardcoded —
except the volume rocker, which nearly every e-ink reader wires its facade buttons to and which
`PageTurnKeyBindings.useVolumeKeys` offers as a one-switch preset (`invertVolumeKeys` swaps the
two for the other grip). The codes come from `expect object PlatformKeyCodes`, since Android and
desktop number keys differently.

A scrollable screen opts in with `PageTurnScrollEffect(listState)`; pass `enabled = false` when
another pane owns the buttons. Android intercepts in `MainActivity.dispatchKeyEvent` so a bound
volume key never reaches the system volume overlay.

A page is the *visible* band, not the viewport. `viewportEndOffset - viewportStartOffset` reports
the list's full height, so a screen that paints its bars **over** the list — as the reader does,
with no Scaffold `topBar` and content running edge to edge under the system bars — must pass
`obscuredTopPx`/`obscuredBottomPx` (`rememberViewerChromeInsets`) or every turn hides a line or two
behind the bar. A screen that consumes its Scaffold padding, like the bookmark list, passes
nothing.

> **Rule:** never put key capture inside a Compose `Dialog`/`AlertDialog`/`ModalBottomSheet`. Those
> are separate platform windows on Android, and while one holds focus key events go to *its*
> `Window.Callback` instead of `MainActivity.dispatchKeyEvent` — the only thing that feeds the
> dispatcher. A prompt in a dialog can never see the keys it is asking for. Capture inline, as
> `KeyBindingCard` does.

Page turns read `PageTurnKeyBindings.instantPageTurn`, not `LocalEinkMode.instantScroll`: the
latter ANDs in the master e-ink switch, and hardware buttons are deliberately usable without it.
Anything that moves the reader instantly must also call
`ScrollRestorationState.approveCurrentPosition()` — the reader's scroll guard snaps back movement
it did not sanction, and an instant scroll is indistinguishable from an unintended jump because it
finishes inside one `scroll {}` block.

### Menus and bottom sheets

- **`ModalBottomSheet`** + `DropdownMenuItem` → contextual bookmark action menus (see `BookmarkActionsMenu.kt`).
- **`ModalBottomSheet`** + MD3 `ListItem` → list pickers (see `ListPickerDialog.kt`).
- **`AlertDialog`** → confirmation and simple edit dialogs.
- **`DropdownMenu`** anchored to `IconButton` → overflow menus on list items.
- **`BaseBottomPanel`** (`ui/components/BaseBottomPanel.kt`) → custom overlay bottom panels (matches `ModalBottomSheet` background and animation style).

---

## MD3 Design Guidelines

- Prefer MD3 components: `ListItem`, `NavigationDrawerItem`, `FilterChip`, `OutlinedTextField`, etc.
- Use `MaterialTheme.colorScheme.*`, `MaterialTheme.typography.*`, `MaterialTheme.shapes.*` — never hardcode.
- Destructive actions: `MaterialTheme.colorScheme.error` for text/icon tint + `MenuDefaults.itemColors(textColor = ...)`.
- FAB menus: `SmallFloatingActionButton` + text labels (not `ExtendedFloatingActionButton`).

---

## Mobile-First, Large Screen Aware

- **Design mobile-first.** The primary target is Android phones; interactions, tap targets, and layouts should work well at compact window widths.
- **Consider larger screens.** When adding new screens or significant UI changes, think about how the layout adapts to tablet/desktop window sizes. Use `WindowSizeClass` or adaptive layouts where appropriate; avoid layouts that look broken or waste space on large screens.
- The desktop (JVM) target is a first-class citizen — do not assume touch-only interaction. Hover states, right-click menus, keyboard navigation, and pointer precision matter on desktop.

---

## Testing Requirements

Every new feature or bug fix **must** include new or updated tests. Tests that become stale due to a change must be updated alongside the code change.

### Test Locations

| Type | Location |
|---|---|
| Common unit tests | `composeApp/src/commonTest/kotlin/com/karakept/app/` |
| Desktop integration tests | `composeApp/src/desktopTest/kotlin/com/karakept/app/data/integration/` |
| Android unit tests | `composeApp/src/androidUnitTest/kotlin/com/karakept/app/` |

### Running the Desktop App

```bash
./gradlew :composeApp:run                    # Run desktop app
./gradlew :composeApp:run -Pdev=true         # Run in dev mode (shows "(DEV)" in title)
./gradlew :composeApp:hotRunDesktop          # Run with Compose Hot Reload (CMP 1.11+, requires JBR 21)
./gradlew :composeApp:hotRunDesktop -Pdev=true  # Hot Reload + dev mode
```

**Hot Reload workflow** — two terminals required:
1. Terminal 1: `./gradlew :composeApp:hotRunDesktop` — starts the app with the hot-reload agent
2. Terminal 2: `./gradlew -t :composeApp:reload` — compiles and signals the agent on every save

Save a `.kt` file → Terminal 2 compiles + notifies the agent → UI updates in-place in Terminal 1.

### Running Tests

```bash
./gradlew test                  # All tests
./gradlew commonTest            # Common (multiplatform) tests only
./gradlew desktopTest           # Desktop integration tests
./gradlew -t test               # Watch mode
./gradlew :composeApp:commonTest --tests BookmarkRepositoryUnitTest
```

### Test Patterns

- Inherit from **`BaseRepositoryTest`** for repository unit tests (provides `testDispatcher`, `testScope`).
- Use **MockK** (`mockk<T>(relaxed = true)`) for dependencies; never mock the system under test.
- Mock `suspend` functions with `coEvery { }`.
- Follow **Arrange / Act / Assert** structure.
- Prefer real data instances for models/entities; mock only external dependencies.
- Integration tests inherit from **`BaseDockerIntegrationTest`** and run against a real Docker backend.

```kotlin
@Test
fun exampleTest() = runTest(testDispatcher) {
    // Arrange
    coEvery { remoteDataSource.fetchBookmarks(any()) } returns listOf(mockDto)

    // Act
    val result = repository.syncBookmarks(testServer)

    // Assert
    assertTrue(result.isSuccess)
}
```

---

## Adding a New Setting

All user-configurable settings must be part of the backup/restore cycle. Touch these three files — `BackupRepository` never needs updating:

| File | What to do |
|---|---|
| `data/model/AppBackup.kt` — `BackupSettings` | Add field with sensible default (backup file format) |
| `data/repository/StoredSettings.kt` — appropriate category class | Add field with same default (DataStore storage format) |
| `data/repository/SettingsRepository.kt` | Add derived `Flow<T>`, setter, and map in `currentSettings()` / `restoreSettings()` |

Settings category classes: `StoredThemeSettings`, `StoredDisplaySettings`, `StoredReaderSettings`, `StoredSwipeSettings`, `StoredSyncSettings`, `StoredAppSettings`.

---

## Adding a New Database Entity

1. Create entity in `data/local/entity/[Name]Entity.kt`.
2. Create DAO in `data/local/dao/[Name]Dao.kt`.
3. Add entity to `AppDatabase.kt` entities list and bump the schema version.
4. Write migration `data/local/migrations/Migration[N]To[N+1].kt`.
5. Register migration in platform database builders (`androidMain/Database.android.kt`, etc.).

---

## Adding a New Dependency

Before adding any dependency:

1. **Check the license.** Verify it is compatible with the project (MIT, Apache 2.0, and similar permissive licenses are fine; GPL/LGPL requires careful review).
2. **Add it to the open-source licenses screen.** The in-app open-source credits screen must be updated to include the new library name, version, and license.
3. **Document it.** Add the dependency to the relevant section in `docs/codebase/STACK.md`.

---

## Documentation

Keep documentation current as the code evolves:

- **New settings** → follow the `CONTRIBUTING.md` checklist (no separate docs needed).
- **New backup-related features** → update `docs/backup-restore.md`.
- **New API integrations or architectural changes** → add/update a file in `docs/`.
- **New reusable components** → document them in this file under "Reusable UI Components".
- **Stack changes** → update `docs/codebase/STACK.md`.
- **Architecture changes** → update `docs/codebase/ARCHITECTURE.md`.

---

## Commit and PR Conventions

- Use **[Conventional Commits](https://www.conventionalcommits.org/)** for all commits:
  ```
  feat: add swipe-to-archive on bookmark list
  fix: prevent duplicate sync on rapid tab switch
  refactor: extract TagChip from BookmarkTagsDisplay
  test: add unit tests for DefaultFilterResolver
  docs: update backup-restore architecture notes
  chore: bump Ktor to 3.3.2
  ```
- PR titles must also follow the Conventional Commits format.
- **Do not mention AI tools, agents, or sessions** in commit messages, PR titles, or PR bodies.
- Keep commits atomic: one logical change per commit.

---

## CI / CD

CI runs on every PR targeting `main` (`.github/workflows/ci.yml`):
- Runs `./gradlew test --rerun` (all tests).
- PRs must be green before merging.

Releases (`.github/workflows/release.yml`):
- Builds signed Android APK/AAB and desktop packages.
- Signing via `KEYSTORE_PATH`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` environment variables.
- Changelog generated automatically.

---

## API Client

The `api-client/` module is **generated** from the Karakeep OpenAPI spec at
`karakeep-upstream/packages/open-api/karakeep-openapi-spec.json` using OpenAPI Generator 7.10.0.

- The spec lives in the `karakeep-upstream` git submodule. If a build fails with a
  missing-spec error on `:api-client:openApiGenerate`, initialize it first:
  `git submodule update --init`.
- **Do not hand-edit** files under `api-client/src/` — they will be overwritten on regeneration.
- Generated models live at `com.karakept.api.*`.
- `RemoteDataSource.kt` is the only place that consumes the generated API clients.
