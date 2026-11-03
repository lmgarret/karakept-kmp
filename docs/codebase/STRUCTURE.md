# Codebase Structure

**Analysis Date:** 2026-03-20

## Directory Layout

```
karakept-kmp/
├── composeApp/                             # Compose Multiplatform module
│   ├── src/
│   │   ├── commonMain/kotlin/com/karakept/app/
│   │   │   ├── App.kt                      # Root composable, entry point routing
│   │   │   ├── Platform.kt                 # expect/actual for platform detection
│   │   │   ├── ui/
│   │   │   │   ├── screens/                # Screen composables and ScreenModels
│   │   │   │   ├── components/             # Reusable UI components
│   │   │   │   ├── theme/                  # Material3 theme, colors, typography
│   │   │   │   ├── transitions/            # Navigation transitions
│   │   │   │   └── utils/                  # UI-specific utilities
│   │   │   ├── data/
│   │   │   │   ├── cache/                  # Image/asset disk caches
│   │   │   │   ├── local/                  # Room database, DAOs, entities, migrations
│   │   │   │   ├── remote/                 # Ktor client, RemoteDataSource, API clients
│   │   │   │   ├── repository/             # Data repositories (abstraction layer)
│   │   │   │   └── model/                  # Data models (Bookmark, Server, FilterConfig, etc.)
│   │   │   ├── domain/                     # Business logic and domain abstractions
│   │   │   │   ├── action/                 # BookmarkActionController, BookmarkActionEvent
│   │   │   │   ├── BookmarkFilterUtils.kt  # Filtering logic
│   │   │   │   └── ListHierarchyUtils.kt   # List hierarchy sorting and filtering
│   │   │   ├── di/                         # Koin dependency injection module
│   │   │   ├── utils/                      # Shared utilities (Date, File, etc.)
│   │   │   ├── api/                        # Generated API model types
│   │   │   └── composeResources/           # Images, fonts, drawable assets
│   │   └── androidMain/kotlin/com/karakept/app/
│   │       ├── data/local/                 # Platform-specific DB/DataStore setup
│   │       ├── ui/                         # Android-specific UI implementations
│   │       └── utils/                      # Android-specific utilities (Share, Haptic, etc.)
│   └── build.gradle.kts                    # Gradle build configuration
├── gradle/                                 # Gradle wrapper and configuration
└── settings.gradle.kts                     # Root Gradle settings
```

## Directory Purposes

**composeApp/src/commonMain/kotlin/com/karakept/app/ui/screens/:**
- Purpose: Screen-level composables and their ScreenModels (MVVM state holders)
- Contains: only feature sub-packages, no files at the root (enforced by `LayerRulesTest`)
- Key files:
  - `main/MainScreenModel.kt` - Primary screen state: server selection, filters, pagination, sync
  - `viewer/BookmarkViewerScreenModel.kt` - Detail view state: highlight management, reader appearance
  - `settings/SettingsScreenModel.kt` - Settings mutations, list management, backup/restore
  - `main/` - MainScreen and its ScreenModel, dialogs, drawer, list content
  - `viewer/` - BookmarkViewerScreen, its ScreenModel and panels
  - `onboarding/` - LoginScreen, OnboardingScreen, notification permission prompt
  - `settings/` - SettingsScreen, ReaderAppearanceScreen, AboutScreen, DevLogsScreen and the nested settings screens (ListManagementScreen, PerListSettingsScreen, LayoutEditorScreen, BackupRestoreScreen)
  - `highlights/` - HighlightsScreen and its ScreenModel
  - `share/` - ShareBookmarkScreen, ShareMultipleBookmarksScreen, SaveErrorScreen

**composeApp/src/commonMain/kotlin/com/karakept/app/ui/components/:**
- Purpose: Reusable, single-purpose composable building blocks
- Contains: generic components at the root (BaseBottomPanel, EinkAware, ListPickerDialog, FilterBottomPanel) and feature subpackages: `tags/` (TagChip, BookmarkTagsDisplay, TagEditorDialog), `bookmark/` (BookmarkActionsMenu, BookmarkLayouts, row items and badges), `highlight/` (HighlightCard and friends), `reader/` (HtmlRenderer, HtmlContent, HighlightTextToolbar, ViewerModeToggle)
- Key files:
  - `tags/TagChip.kt` - Single tag display (canonical design: secondaryContainer, shapes.small, 2dp elevation)
  - `tags/BookmarkTagsDisplay.kt` - Renders comma-separated tag string as FlowRow
  - `FilterBottomPanel.kt` - Filter UI with tag, list, and status controls
  - `bookmark/BookmarkLayouts.kt` - Layout variants for bookmark items (compact, expanded, reader)
  - `BaseBottomPanel.kt` - Base for modal bottom sheets (matches ModalBottomSheet styling)
  - `reader/` - HTML content rendering, highlight toolbar, scrollable containers

**composeApp/src/commonMain/kotlin/com/karakept/app/ui/theme/:**
- Purpose: Material3 theme configuration, color schemes, typography
- Contains: Theme.kt (main theme composable), Color.kt (color palettes), PlatformTheme.kt (platform-specific overrides), ReaderFonts.kt
- Pattern: Uses MaterialTheme.colorScheme and MaterialTheme.typography throughout

**composeApp/src/commonMain/kotlin/com/karakept/app/data/local/:**
- Purpose: Local database access via Room, offline persistence
- Contains:
  - `AppDatabase.kt` - Room database schema declaration
  - `entity/` - Database entity classes (BookmarkEntity, ListEntity, ServerEntity, PendingActionEntity, HighlightEntity, AssetEntity)
  - `dao/` - Data Access Objects (BookmarkDao, ListDao, ServerDao, PendingActionDao, HighlightDao, AssetDao)
  - `migrations/` - Database schema migrations (Migration1To2 through Migration7To8, version 8)
  - `Database.kt` - Platform-agnostic database builder interface
  - `DataStoreFactory.kt` - Settings DataStore for non-schema data (themes, filters, etc.)

**composeApp/src/commonMain/kotlin/com/karakept/app/data/remote/:**
- Purpose: Remote API communication, HTTP client setup
- Contains:
  - `RemoteDataSource.kt` - Wraps generated API clients, handles offline mode guard
  - `KtorClient.kt` - HttpClient configuration with serialization and plugin setup
  - Generated API clients: BookmarksApi, ListsApi, TagsApi, HighlightsApi, UsersApi (auto-generated from server API)

**composeApp/src/commonMain/kotlin/com/karakept/app/data/repository/:**
- Purpose: Data abstraction layer, single source of truth for each domain concept
- Contains:
  - `BookmarkRepository.kt` - Bookmark queries, sync strategies (full/filtered/per-list)
  - `AssetRepository.kt` - Asset (archive, attachment) fetch, cache and detach
  - `ListRepository.kt` - List CRUD and sync
  - `ServerRepository.kt` - Server management and multi-server support
  - `SettingsRepository.kt` - User preferences (theme, offline mode, reader appearance, etc.)
  - `BookmarkActionsRepository.kt` - Low-level bookmark mutations (archive, favorite, tag update, delete)
  - `HighlightRepository.kt` - Highlight CRUD and sync
  - `BackupRepository.kt` - Backup/restore with scheduled export
  - `StoredSettings.kt` - Encrypted settings storage

**composeApp/src/commonMain/kotlin/com/karakept/app/domain/:**
- Purpose: Business logic, domain abstractions, centralized action handling
- Contains:
  - `action/BookmarkActionController.kt` - Centralized action execution with undo support (5-second window, undo cache)
  - `action/BookmarkActionEvent.kt` - Sealed class hierarchy of all bookmarkactions (Archive, Delete, ToggleFavorite, UpdateTags, MoveToList, etc.)
  - `action/ActionSnackbarManager.kt` - Event emitter for snackbar display
  - `BookmarkFilterUtils.kt` - Predicate-based filtering (tags, lists, archived, favorited, search)
  - `ListHierarchyUtils.kt` - List ordering (parents before children, alphabetical at each level), collapsible tree helpers
  - `DefaultFilterResolver.kt` - Resolve default filter based on settings

**composeApp/src/commonMain/kotlin/com/karakept/app/data/cache/:**
- Purpose: Offline caches owned by the data layer
- Contains: HtmlCache, ImageCacheManager (plus the `fileExists` expect/actual), ThumbnailCache, OfflineHeroImages, HtmlArchiveProcessor

**composeApp/src/commonMain/kotlin/com/karakept/app/di/:**
- Purpose: Dependency injection container setup
- Contains:
  - `AppModule.kt` - Koin module defining all singletons (repositories, databases) and factories (ScreenModels)
  - Wires up circular dependency: BookmarkActionsRepository ↔ BookmarkRepository via setBookmarkRepository()

**composeApp/src/commonMain/kotlin/com/karakept/app/utils/:**
- Purpose: Shared, platform-agnostic utilities
- Contains: DateUtils.kt, FileUtils.kt, AssetUrlUtils.kt, FaviconUtils.kt, ShareUtils.kt, HapticUtils.kt, ReadingTimeCalculator.kt, BackupCrypto.kt

**composeApp/src/commonMain/kotlin/com/karakept/app/data/model/:**
- Purpose: Domain data models and enums
- Contains: Bookmark.kt, Server.kt, FilterConfig.kt, SyncStrategy.kt, ThemeMode.kt, AccentColor.kt, ViewerMode.kt, BookmarkLayout.kt, and more than 15 other value objects
- Pattern: Serializable, immutable data classes

**composeApp/src/androidMain/kotlin/com/karakept/app/:**
- Purpose: Android-specific implementations and platform declarations
- Contains:
  - `data/local/` - Room database builder for Android (getDatabaseBuilder())
  - `ui/` - Android-specific composable variants (BackHandler.android.kt, CustomTabOpener.android.kt, FilePicker.android.kt, etc.)
  - `ui/theme/PlatformTheme.android.kt` - Android Material3 edge-to-edge theming
  - `utils/` - Android-specific helpers (ShareUtils, HapticUtils, FileUtils, BackupCrypto)
- Key files:
  - `ShareActivity.kt` - Activity for share intent handling
  - `WindowThemeSync.kt` - Window theme synchronization with system

## Key File Locations

**Entry Points:**
- `composeApp/src/commonMain/kotlin/App.kt` - Root composable, routing to initial screen based on app state

**Configuration:**
- `composeApp/src/commonMain/kotlin/com/karakept/app/di/AppModule.kt` - All DI bindings
- `composeApp/src/commonMain/kotlin/com/karakept/app/data/local/AppDatabase.kt` - Room database schema version and entities

**Core Logic:**
- `composeApp/src/commonMain/kotlin/com/karakept/app/data/repository/BookmarkRepository.kt` - Bookmark queries and multi-strategy sync
- `composeApp/src/commonMain/kotlin/com/karakept/app/domain/action/BookmarkActionController.kt` - Centralized action dispatch with undo
- `composeApp/src/commonMain/kotlin/com/karakept/app/data/remote/RemoteDataSource.kt` - Remote API abstraction

**UI Composition:**
- `composeApp/src/commonMain/kotlin/com/karakept/app/ui/screens/main/MainScreen.kt` - Primary app screen
- `composeApp/src/commonMain/kotlin/com/karakept/app/ui/screens/viewer/BookmarkViewerScreen.kt` - Bookmark detail view
- `composeApp/src/commonMain/kotlin/com/karakept/app/ui/theme/Theme.kt` - Material3 theme

**Utilities & Helpers:**
- `composeApp/src/commonMain/kotlin/com/karakept/app/domain/ListHierarchyUtils.kt` - List sorting and filtering
- `composeApp/src/commonMain/kotlin/com/karakept/app/domain/BookmarkFilterUtils.kt` - Bookmark predicate filters
- `composeApp/src/commonMain/kotlin/com/karakept/app/ui/components/tags/TagChip.kt` - Reusable tag display
- `composeApp/src/commonMain/kotlin/com/karakept/app/ui/components/tags/BookmarkTagsDisplay.kt` - Tag string rendering

## Naming Conventions

**Files:**
- ScreenModel classes: `*ScreenModel.kt` (MainScreenModel.kt, BookmarkViewerScreenModel.kt)
- Screens: `*Screen.kt` (MainScreen.kt, LoginScreen.kt)
- Repositories: `*Repository.kt` (BookmarkRepository.kt, ListRepository.kt)
- DAOs: `*Dao.kt` (BookmarkDao.kt, ListDao.kt)
- Entities: `*Entity.kt` (BookmarkEntity.kt, ServerEntity.kt)
- Utility functions: `*Utils.kt` (DateUtils.kt, ListHierarchyUtils.kt)
- Migrations: `Migration*To*.kt` (Migration1To2.kt, Migration7To8.kt)

**Directories:**
- Screen directories under `ui/screens/`: one sub-package per feature (screens/main/MainScreen.kt, screens/settings/ListManagementScreen.kt); no files at the `ui/screens/` root
- Component directories: Single-word descriptors (components/reader/, components/ for general)
- Data layer: Clear domain separation (local/, remote/, repository/, model/)

**Functions (Kotlin Conventions):**
- Public: camelCase (buildListHierarchy, filterBookmarks)
- Private: underscore prefix not used (underscore used only for backing fields of properties)
- Composables: PascalCase (MainScreen, TagChip, FilterBottomPanel)
- ScreenModel functions: camelCase (syncBookmarks, toggleFavorite, updateFilter)

**Types (Kotlin Conventions):**
- Classes/Sealed Classes/Data Classes: PascalCase (BookmarkEntity, BookmarkActionEvent, FilterConfig)
- Enums: PascalCase (ThemeMode, ViewerMode, LayoutType)
- Interfaces: PascalCase (Platform, no "I" prefix)

## Where to Add New Code

**New Screen (Feature):**
- Screen composable: `composeApp/src/commonMain/kotlin/com/karakept/app/ui/screens/[feature]/[FeatureName]Screen.kt`
- ScreenModel: `composeApp/src/commonMain/kotlin/com/karakept/app/ui/screens/[feature]/[FeatureName]ScreenModel.kt`
- A new feature gets its own `screens/[feature]/` package; a sub-screen of an existing feature goes in that feature's package
- Register in DI: Add factory binding to `composeApp/src/commonMain/kotlin/com/karakept/app/di/AppModule.kt`

**New Reusable Component:**
- Location: `composeApp/src/commonMain/kotlin/com/karakept/app/ui/components/[ComponentName].kt`
- For category-specific components: Place it in the matching subpackage (`components/tags/`, `components/bookmark/`, `components/highlight/`, `components/reader/`); only generic components live at the root
- Follow existing patterns: Use Material3 composables (ListItem, NavigationDrawerItem, FilterChip), prefer composition over custom Surfaces

**New Repository/Data Service:**
- Location: `composeApp/src/commonMain/kotlin/com/karakept/app/data/repository/[DomainName]Repository.kt`
- Inject in DI: Add single() binding in AppModule.kt
- Use existing DAOs and RemoteDataSource; create new DAOs only if new entity added

**New Database Entity:**
- Entity class: `composeApp/src/commonMain/kotlin/com/karakept/app/data/local/entity/[EntityName]Entity.kt`
- DAO: `composeApp/src/commonMain/kotlin/com/karakept/app/data/local/dao/[EntityName]Dao.kt`
- Add to AppDatabase.kt entities list
- Create migration: `composeApp/src/commonMain/kotlin/com/karakept/app/data/local/migrations/Migration[OldVersion]To[NewVersion].kt`
- Increment version in AppDatabase.kt
- Register migration in database builder (androidMain/Database.android.kt, etc.)

**New Domain Logic:**
- Filters/utilities: `composeApp/src/commonMain/kotlin/com/karakept/app/domain/[LogicName]Utils.kt`
- Actions: Add to BookmarkActionEvent sealed class in `composeApp/src/commonMain/kotlin/com/karakept/app/domain/action/BookmarkActionEvent.kt`, then extend BookmarkActionController.executeAction() to handle
- Business rules: Create new file if domain concept or extend existing repository

**New Utility/Helper:**
- Shared (all platforms): `composeApp/src/commonMain/kotlin/com/karakept/app/utils/[UtilName]Utils.kt`
- Android-specific: `composeApp/src/androidMain/kotlin/com/karakept/app/utils/[UtilName].android.kt`
- UI utilities: `composeApp/src/commonMain/kotlin/com/karakept/app/ui/utils/[UtilName]Utils.kt`

**Platform-Specific Implementation:**
- Declare expect in commonMain: `composeApp/src/commonMain/kotlin/com/karakept/app/utils/[Name].kt`
- Implement actual in androidMain: `composeApp/src/androidMain/kotlin/com/karakept/app/utils/[Name].android.kt`

## Special Directories

**composeApp/src/commonMain/composeResources/:**
- Purpose: Compose Multiplatform resources (images, fonts, drawable assets)
- Generated: Accessed via generated resource constants (Res.drawable.*, Res.font.*)
- Committed: Yes

**composeApp/src/commonMain/kotlin/com/karakept/api/:**
- Purpose: Generated API model types from OpenAPI/tRPC schema
- Generated: Yes (from server API schema)
- Committed: Yes (checked in)
- Pattern: Models correspond to server data types (Bookmark, List, Highlight, etc.)

**composeApp/src/commonMain/kotlin/com/karakept/app/data/local/migrations/:**
- Purpose: Database schema migrations to handle version upgrades
- Generated: No (manually written)
- Committed: Yes
- Pattern: Each file named Migration[OldVersion]To[NewVersion].kt

---

*Structure analysis: 2026-03-20*
