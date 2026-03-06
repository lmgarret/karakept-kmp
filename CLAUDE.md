# Karakept KMP — Claude Agent Guidelines

## Project Overview

Karakept is a Kotlin Multiplatform (KMP) app for managing bookmarks, built with Compose Multiplatform and targeting Android. The UI follows **Material Design 3 (MD3)** guidelines throughout.

Key technologies:
- Kotlin Multiplatform / Compose Multiplatform
- Material3 (`androidx.compose.material3`)
- Voyager for navigation (`cafe.adriel.voyager`)
- Koin for dependency injection
- SQLDelight for local storage

## Code Conventions

- **UI layer**: `composeApp/src/commonMain/kotlin/com/karakept/app/ui/`
  - `components/` — reusable composables (dialogs, chips, panels, etc.)
  - `screens/` — screen composables and ViewModels (ScreenModel)
  - `utils/` — pure Kotlin utilities used by the UI layer
  - `theme/` — Material3 theme, colors, typography
- **Data layer**: `composeApp/src/commonMain/kotlin/com/karakept/app/data/`
- **API model**: `composeApp/src/commonMain/kotlin/com/karakept/api/model/`

Use `HorizontalDivider` (not deprecated `Divider`) everywhere.

## Reusable UI Components — ALWAYS use these instead of reimplementing

### Tag display

**`TagChip`** (`ui/components/TagChip.kt`)
- Single tag chip with the canonical app design: `secondaryContainer` surface, `shapes.small`, 2dp elevation.
- Use this **everywhere a single tag chip needs to be displayed** (tag editor, search, filter, etc.).
- Supports optional `onRemove` (shows ✕ button) and `onClick` callbacks.

**`BookmarkTagsDisplay`** (`ui/components/BookmarkTagsDisplay.kt`)
- Renders a comma-separated tag string as a `FlowRow` of `TagChip`-styled chips.
- Use this whenever a bookmark's full tag list needs to be rendered (cards, details panel, viewer banner, etc.).
- Accepts `style` (`COMPACT` / `READER`), optional `onTagClick`, and a `modifier`.

> **Rule:** Tags must look the same everywhere in the app — always use `TagChip` or `BookmarkTagsDisplay`. Do **not** use `AssistChip`, `FilterChip`, plain `Text`, or custom surfaces for displaying tags.

### List display and hierarchy

**`buildListHierarchy(lists)`** (`ui/utils/ListHierarchyUtils.kt`)
- Converts a flat `List<KarakeepList>` into `List<Pair<KarakeepList, Int>>` ordered for display: parents before children, alphabetically sorted at each level.
- Use this **everywhere lists are displayed**, including pickers, filter panels, sync settings, and the navigation drawer.

**`filterExpandedHierarchy(hierarchy, expandedIds)`** (`ui/utils/ListHierarchyUtils.kt`)
- Filters a hierarchy to only show branches whose ancestors are all expanded. Use together with `buildListHierarchy` when implementing collapsible tree views (e.g. the navigation drawer).

**`listHasChildren(listId, allLists)`** (`ui/utils/ListHierarchyUtils.kt`)
- Helper to check if a list has direct children.

> **Rule:** Lists must always be displayed sorted and showing hierarchy. Use `buildListHierarchy` — never sort lists manually or show them in a flat unordered layout.

### Menus and bottom sheets

- Use **`ModalBottomSheet`** + **`DropdownMenuItem`** for contextual bookmark action menus (see `BookmarkActionsMenu.kt`).
- Use **`ModalBottomSheet`** + MD3 `ListItem` for list pickers (see `ListPickerDialog.kt`).
- Use **`AlertDialog`** for confirmation dialogs and simple edit dialogs (see `TagEditorDialog.kt`).
- Use **`DropdownMenu`** anchored to an `IconButton` for overflow menus on list items (see `MainScreenDrawer.kt`).

## MD3 Design Guidelines

- Prefer MD3 components: `ListItem`, `NavigationDrawerItem`, `FilterChip`, `OutlinedTextField`, etc.
- Use `MaterialTheme.colorScheme.*`, `MaterialTheme.typography.*`, `MaterialTheme.shapes.*` — never hardcode colors or text styles.
- Destructive actions: use `MaterialTheme.colorScheme.error` for text and icon tint, plus `MenuDefaults.itemColors(textColor = ...)`.
- FAB menus: use `SmallFloatingActionButton` + text labels (not `ExtendedFloatingActionButton`).
