# Coding Conventions

**Analysis Date:** 2026-03-20

## Naming Patterns

**Files:**
- PascalCase for Kotlin class names: `TagChip.kt`, `BookmarkRepository.kt`, `AppearanceSettingsScreen.kt`
- ScreenModel files named after screen + suffix: `ReaderAppearanceScreenModel.kt`, `BackupRestoreScreenModel.kt`
- Composable functions named as PascalCase: `TagChip()`, `AppearanceSettingsContent()`
- Test files suffixed with `Test` or `UnitTest`: `BookmarkRepositoryUnitTest.kt`, `ListHierarchyUtilsTest.kt`
- Integration test files suffixed with `IntegrationTest`: `BaseDockerIntegrationTest.kt`, `BookmarkSyncIntegrationTest.kt`

**Functions:**
- camelCase for all functions, both public and private: `createBookmark()`, `syncFavorites()`, `getAllDescendantIds()`
- Function names are verb-based when they perform actions: `exportSettings()`, `importWithPin()`, `setHtmlTextColor()`
- Query/getter functions often use descriptive names: `getBookmarks()`, `getAncestorIds()`, `getDirectoryDisplayName()`
- Composable functions in lowercase followed by parameter list: `fun TagChip(...)`, `fun AppearanceSettingsContent(...)`
- Private helper composables prefixed with uppercase: `ThemeModeOption()`, `AccentColorPickerCard()`

**Variables:**
- camelCase for all variables: `bookmarkDao`, `testServer`, `containerColor`, `syncProgress`
- Boolean properties are descriptive: `isArchived`, `isStarred`, `shouldFetchLists`, `isDockerRunning`
- State flows end with suffix or use descriptive names: `syncProgress`, `_syncProgress` (private mutable), `_state`
- Test setup variables use descriptive names: `testDispatcher`, `testScope`, `testUrl`

**Types:**
- PascalCase for classes, sealed classes, data classes: `Bookmark`, `SyncConfiguration`, `BackupState`
- PascalCase for enums: `CheckboxState`, `ThemeMode`, `ViewerMode`, `DateDisplayMode`
- PascalCase for type aliases and interfaces

## Code Style

**Formatting:**
- Kotlin official code style is enforced: `kotlin.code.style=official` in `gradle.properties`
- Indentation is 4 spaces (Kotlin standard)
- Line wrapping follows Kotlin conventions

**Linting:**
- No explicit linting tool configuration found; relies on Kotlin official style
- IntelliJ IDEA formatting conventions are implied through project setup

## Import Organization

**Order:**
1. Compose framework imports (`androidx.compose.*`)
2. Compose Material3 imports (`androidx.compose.material3.*`)
3. Material icons (`androidx.compose.material.icons.*`)
4. Compose runtime (`androidx.compose.runtime.*`)
5. Compose foundation/layout (`androidx.compose.foundation.*`)
6. Kotlinx serialization and coroutines imports
7. Third-party framework imports (Voyager, Koin, Room, Ktor)
8. Project-local imports (`com.karakept.*`)

**Path Aliases:**
- No path aliases configured; imports use full package paths
- Fully qualified imports used throughout: `com.karakept.app.data.model`, `com.karakept.app.ui.components`

## Error Handling

**Patterns:**
- Result<T> return type used for operations that can fail: `Result.success()`, `Result.failure()`, `result.getOrNull()`, `result.exceptionOrNull()`
- Try-catch blocks wrap fallible operations in repository layer
- Exceptions thrown by remote data source are caught and wrapped in Result
- Null-coalescing with `firstOrNull()` and fallback error returns: `server ?: return Result.failure(Exception(...))`
- Suspend functions may throw exceptions that are caught by callers
- No custom exception types; generic `Exception` is used with descriptive messages

**In Composable UI:**
- State management for error display: `BackupState.Error("message")`
- Sealed classes used to represent operation states: `BackupState.Loading`, `BackupState.Success`, `BackupState.PinRequired`

## Logging

**Framework:** `println()` with emoji prefixes for readability during development

**Patterns:**
- Logging used liberally in repository layer for debugging: `println("Polling for bookmark parsing: attempt $attempts...")`
- Debug emoji prefixes for visual categorization:
  - `📖` for content-related logs
  - `📸` for asset/image-related logs
- Debug logs include operation details, counts, and state changes
- No production-ready logging framework (Timber, SLF4J) integrated; debug output only
- Log statements useful during development and troubleshooting

## Comments

**When to Comment:**
- KDoc comments used for public API functions and classes
- Comments explain the "why" for non-obvious logic, especially in complex algorithms
- Section dividers used in larger classes (e.g., `// ── Export ────────────────────────────`)
- Test comments explain setup, execution, and verification steps in AAA pattern
- Complex logic in sync pipelines and data mapping commented

**JSDoc/KDoc:**
- Single-line KDoc for simple public functions: `/** Reusable tag chip matching the BookmarkTagsDisplay surface design. */`
- Multi-line KDoc with parameter documentation for functions with parameters
- Parameters documented with `@param` tags: `@param tag The tag text to display`
- Return values documented with description following function signature
- Usage examples included in KDoc when behavior is non-obvious
- Example from `TagChip.kt`: full multi-line doc with @param tags for clarity

## Function Design

**Size:**
- Functions are kept small and focused; ~40-100 lines is typical for repository operations
- Large functions like `executeSyncPipeline` are broken into logical sections with comments
- Composable functions are relatively short, pulling out helper composables

**Parameters:**
- Descriptive parameter names: `onStatusChange`, `onRemove`, `onTagClick`, `directoryPath`
- Optional/nullable parameters use `?` and default to null: `onRemove: (() -> Unit)? = null`
- Default parameter values used for optional Modifier parameters: `modifier: Modifier = Modifier`
- Multiple related parameters grouped logically in parameter list

**Return Values:**
- Explicit return types on all public functions (Kotlin best practice)
- Result<T> used for fallible operations
- Flow<T> used for observables: `Flow<List<BookmarkEntity>>`
- StateFlow<T> used for mutable observable state
- Suspend functions return T directly, throwing exceptions on failure
- Composable functions return Unit implicitly

## Module Design

**Exports:**
- Repository classes are the primary public API for data layer: `BookmarkRepository`, `ListRepository`, `SettingsRepository`
- ScreenModel classes expose StateFlow properties for UI observation
- Utility objects use `expect/actual` pattern for platform-specific implementations: `FileUtils`
- Private implementation details use `private` visibility throughout

**Barrel Files:**
- No explicit barrel files observed
- Imports use full package paths directly

## Coroutine Usage

**Patterns:**
- `suspend` functions used for all async operations in repositories
- `runTest(testDispatcher)` used for testing coroutine code
- `screenModelScope` used in ScreenModel for lifecycle-aware coroutine launching
- `StateFlow.stateIn()` used to convert Flow to StateFlow with default values
- `SharingStarted.WhileSubscribed(5000)` timeout parameter prevents memory leaks
- `flow { }` builder used for creating Flow from suspend functions

## Composable Conventions

**Structure:**
- Composable functions use trailing lambda syntax for callbacks: `onRemove: (() -> Unit)? = null`
- State management with `collectAsState()` in Composables: `val value by stateFlow.collectAsState()`
- `remember` used for local mutable state: `var selected by remember { mutableStateOf(false) }`
- Material3 components preferred: `Surface`, `Text`, `Icon`, `IconButton`, `MaterialTheme`
- Layout modifiers follow composition order: padding → size → clickable → conditional logic

**Material3 Usage:**
- Theme colors always use `MaterialTheme.colorScheme.*`: `MaterialTheme.colorScheme.secondaryContainer`
- Typography always uses `MaterialTheme.typography.*`: `MaterialTheme.typography.labelSmall`
- Shapes always use `MaterialTheme.shapes.*`: `MaterialTheme.shapes.small`
- No hardcoded colors, sizes, or styles

---

*Convention analysis: 2026-03-20*
