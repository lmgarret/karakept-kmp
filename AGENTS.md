# Karakept KMP — AI Agent Guidelines

## Project Overview

Karakept is a Kotlin Multiplatform (KMP) bookmark manager app built with Compose Multiplatform,
targeting Android and JVM Desktop (Linux, macOS, Windows).
The UI follows **Material Design 3 (MD3)** guidelines throughout.

Key technologies:
- Kotlin 2.3.20 / Compose Multiplatform 1.11.0
- Material3 (`androidx.compose.material3`)
- Compose Navigation 3 (`androidx.navigation3` / `org.jetbrains.androidx.navigation3` 1.1.1) for navigation
- `androidx.lifecycle` `ViewModel` (multiplatform) for per-screen state (MVVM)
- Koin 4.2.1 for dependency injection (incl. `koin-compose-viewmodel`, `koin-compose-navigation3`)
- Room 2.7.0 for local SQLite storage
- Ktor 3.3.2 for HTTP/API communication
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
- `runTest(testDispatcher)` in unit tests; base class `BaseRepositoryTest` provides the dispatcher.

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

> **Rule:** Tags must look the same everywhere. Never use `AssistChip`, `FilterChip`, plain `Text`, or custom surfaces for displaying tags.

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

### Tag editing and filtering

**`TagEditorDialog`** (`ui/components/TagEditorDialog.kt`)
- `canCreateNew = true` (default): bookmark tag editing — allows free-text tags.
- `canCreateNew = false`: filter-by-tag mode — only existing tags selectable.
- Always pass `availableTags` from the screen's state for suggestions.

> **Rule:** Never implement custom tag-selection dialogs or text fields.

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
