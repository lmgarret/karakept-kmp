# Phase 16: Custom Layout Improvements - Research

**Researched:** 2026-03-30
**Domain:** Kotlin/Compose Multiplatform layout system, data model evolution, migration
**Confidence:** HIGH

## Summary

Phase 16 enhances the existing custom layout system with four interconnected improvements: unifying LIST and COMPACT_LIST into a single configurable type, making Card preview show all toggled fields, adding URL/domain display with configurable position, and adding a "Create new layout" button in the per-list layout picker. The codebase is well-structured with clear patterns for all of these changes -- the `BookmarkLayout` data class is `@Serializable` with string-encoded enums and default values, making field additions backward-compatible. The editor already has `ToggleRow` and `LayoutRadioOption` helper composables ready for reuse.

The primary risk is the COMPACT_LIST removal touching multiple files (5 files reference it) and the data model migration needing careful default handling. The reward is a cleaner architecture: one fewer layout type, shared options across Card and List, and a more complete editor experience.

**Primary recommendation:** Execute in four waves -- (1) data model additions + new enums, (2) COMPACT_LIST unification + migration, (3) URL display + description controls in editor/renderers, (4) per-list picker "Create" button + test coverage.

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- **D-01:** Merge LIST and COMPACT_LIST into a single "List" type. CARD stays separate as its own distinct type. The unified List type gains shared customization options (show/hide description, description position, title position). Toggling fields off effectively reproduces what COMPACT_LIST is today.
- **D-02:** Auto-migrate existing COMPACT_LIST layouts to LIST with compact-like toggle defaults (description off, tags off, smaller thumbnail). The built-in Compact preset becomes a LIST variant with those defaults.
- **D-03:** CARD gets some of the new shared options -- specifically show/hide description and title position -- but NOT description position (Card's vertical structure always places description below title).
- **D-04:** The editor's Card preview must reflect exactly what the user has enabled -- title always visible, plus description/tags/date/URL based on current toggle states. Changes update the preview live. This applies to all layout types, not just Card.
- **D-05:** New `showUrl` toggle and `urlDisplayMode` option: user can choose between domain-only ("github.com") or full URL. Full URL truncates with ellipsis on narrow screens.
- **D-06:** URL/domain position is configurable -- user picks where it appears (e.g., below title, in metadata row, beside favicon). Adds a new `urlPosition` setting similar to `metadataPosition`.
- **D-07:** Add a "Create new layout" footer button at the bottom of the per-list layout picker dialog (`PerListSettingsScreen`). Tapping navigates to `LayoutEditorScreen`; on return the new layout appears in the picker list.
- **D-08:** Per-list picker gets create-only -- no edit access. Editing layouts stays in the global `LayoutsScreen` settings page.

### Claude's Discretion
- Migration logic for COMPACT_LIST -> LIST conversion (exact default values for toggled fields)
- Specific `urlPosition` enum values (e.g., BELOW_TITLE, METADATA_ROW, BESIDE_FAVICON or similar)
- How the per-list picker dialog handles navigation to/from LayoutEditorScreen (dismiss dialog first, or return to it)

### Deferred Ideas (OUT OF SCOPE)
None -- discussion stayed within phase scope
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| UX-02 | Custom layout improvements: layout picker navigation, Card preview content, List/Card unification with shared options, URL/domain display | All four sub-improvements mapped to specific code changes in BookmarkLayout model, LayoutEditorScreen editor, BookmarkLayouts renderers, PerListSettingsScreen picker |
</phase_requirements>

## Architecture Patterns

### Current Layout System Architecture

```
Data Model Layer:
  data/model/BookmarkLayout.kt          -- @Serializable data class with all fields
  data/model/LayoutType.kt              -- enum: CARD, LIST, COMPACT_LIST
  data/model/MetadataPosition.kt        -- enum: BELOW, BESIDE, ABOVE
  data/model/DateDisplayMode.kt         -- enum: ELAPSED, ABSOLUTE

Storage Layer:
  data/repository/SettingsRepository.kt            -- Flows: customLayouts, defaultLayoutId
  data/repository/SettingsRepositoryMutations.kt   -- CRUD: saveLayout, deleteLayout, setDefaultLayoutId, setListLayoutId

Editor Layer:
  ui/screens/settings/LayoutEditorScreen.kt    -- LayoutEditorScreenModel + LayoutEditorContent composable
  ui/screens/settings/LayoutsScreen.kt         -- Global layout list with FAB

Rendering Layer:
  ui/components/BookmarkLayouts.kt             -- BookmarkCardLayout, BookmarkListLayout, BookmarkCompactListLayout
  ui/components/BookmarkPlaceholderItem.kt     -- Placeholder variants per layout type

Display Config Pipeline:
  MainScreen.kt (line 92)                     -- Builds MainScreenDisplayConfig from activeLayout ?? global settings
  MainScreenScaffoldContent.kt (line 59)      -- MainScreenDisplayConfig data class
  MainScreenModel.kt (line 265)               -- activeLayout StateFlow (per-list -> default -> null)
  BookmarkListContent.kt (line 504)           -- when(layoutType) dispatch to render composables
```

### Pattern: Adding New Fields to BookmarkLayout

The established pattern for extending the layout system (observed from existing fields):

1. **Add field to `BookmarkLayout`** with a sensible default value so existing serialized layouts deserialize without breaking
2. **Add enum** if the field needs constrained values (follow `MetadataPosition` pattern: enum with `companion object { fun fromString() }`)
3. **Add `update*()` function** to `LayoutEditorScreenModel`
4. **Add toggle/radio UI** to `LayoutEditorContent` using existing `ToggleRow` / `LayoutRadioOption` helpers
5. **Wire through `PreviewBookmarkItem`** -- already dispatches to production composables
6. **Add to `MainScreenDisplayConfig`** data class
7. **Add to `displayConfig` construction** in `MainScreen.kt` (activeLayout?.field ?? globalDefault)
8. **Pass through `MainScreenScaffoldContent`** -> `BookmarkListContent` -> render composables
9. **Use in render composables** (`BookmarkCardLayout`, `BookmarkListLayout`)

Because `BookmarkLayout` uses `@Serializable` with Kotlin default values, adding new fields is backward-compatible. Existing stored JSON without the new fields will deserialize with defaults.

### Pattern: Enum with fromString

```kotlin
// Follow MetadataPosition pattern exactly:
enum class UrlPosition {
    BELOW_TITLE, METADATA_ROW;

    companion object {
        fun fromString(value: String): UrlPosition =
            entries.find { it.name == value } ?: BELOW_TITLE
    }
}

enum class UrlDisplayMode {
    DOMAIN_ONLY, FULL_URL;

    companion object {
        fun fromString(value: String): UrlDisplayMode =
            entries.find { it.name == value } ?: DOMAIN_ONLY
    }
}

enum class DescriptionPosition {
    BELOW_TITLE, ABOVE_METADATA;

    companion object {
        fun fromString(value: String): DescriptionPosition =
            entries.find { it.name == value } ?: BELOW_TITLE
    }
}
```

### Pattern: COMPACT_LIST Migration Strategy

The `COMPACT_LIST` enum value must remain parseable for backward compatibility but should map to `LIST`. Recommended approach:

```kotlin
// LayoutType.kt -- keep COMPACT_LIST for deserialization
enum class LayoutType {
    CARD,
    LIST,
    @Deprecated("Merged into LIST. Kept for deserialization compatibility.")
    COMPACT_LIST;

    companion object {
        fun fromString(value: String): LayoutType {
            return when (value.uppercase()) {
                "CARD" -> CARD
                "LIST" -> LIST
                "COMPACT_LIST" -> LIST  // Migration: map to LIST
                else -> LIST
            }
        }
    }
}
```

For the built-in Compact preset, convert to a LIST variant:

```kotlin
val BUILTIN_COMPACT = BookmarkLayout(
    id = BUILTIN_COMPACT_ID,
    name = "Compact List",
    description = "Compact list with title and minimal metadata",
    icon = "List",
    layoutType = LayoutType.LIST.name,  // Changed from COMPACT_LIST
    showTags = false,
    showDescription = false,           // New field
    showDate = true,
    thumbnailSize = 48,
    metadataPosition = MetadataPosition.BESIDE.name,
    isBuiltIn = true
)
```

For user-created layouts that had `layoutType = "COMPACT_LIST"`:
- `LayoutType.fromString("COMPACT_LIST")` returns `LIST` -- handled at read time
- No DataStore migration needed -- the fromString mapping handles it transparently
- New fields get defaults: `showDescription = true` by default in BookmarkLayout, but for migration of COMPACT_LIST presets, the built-in gets `showDescription = false`

### Anti-Patterns to Avoid

- **Separate migration step for DataStore:** The fromString mapping handles COMPACT_LIST -> LIST transparently at read time. Do NOT write a one-time migration that rewrites stored JSON.
- **Removing COMPACT_LIST enum value entirely:** Breaks deserialization of existing stored layouts. Keep the enum value but map it to LIST in fromString().
- **Adding description display to CompactListLayout separately:** The whole point is to DELETE BookmarkCompactListLayout and have BookmarkListLayout handle all row-based rendering via toggle fields.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| URL domain parsing | Regex or manual string splitting | `java.net.URI` / `io.ktor.http.Url` parsing + strip "www." prefix | Edge cases with ports, paths, IDN domains |
| Layout editor toggle UI | Custom Row with Switch | Existing `ToggleRow` composable (LayoutEditorScreen.kt line 677) | Consistent padding, icon, typography |
| Layout editor radio UI | Custom Row with RadioButton | Existing `LayoutRadioOption` composable (LayoutEditorScreen.kt line 648) | Consistent padding, selection styling |

**Key insight:** The existing ToggleRow and LayoutRadioOption composables in LayoutEditorScreen.kt are private. They should stay private but be reused within the same file for all new toggles and radio groups.

## Common Pitfalls

### Pitfall 1: Description Always Shown in Current BookmarkListLayout
**What goes wrong:** The current `BookmarkListLayout` always renders `bookmark.description` when not null (line 374). After adding `showDescription`, forgetting to guard this with the toggle means description always appears.
**Why it happens:** The description rendering is inline in `textContent` lambda, easy to miss.
**How to avoid:** Wrap the existing description Text in `if (showDescription && bookmark.description != null)`.
**Warning signs:** Preview shows description even when toggle is off.

### Pitfall 2: Missing Fields in displayConfig Pipeline
**What goes wrong:** Adding a field to BookmarkLayout but forgetting to propagate it through the full chain: BookmarkLayout -> MainScreenDisplayConfig -> MainScreen.kt displayConfig construction -> MainScreenScaffoldContent -> BookmarkListContent -> render composable.
**Why it happens:** The pipeline has 5-6 hops. Missing any one means the field has no effect at render time.
**How to avoid:** For each new field (showDescription, descriptionPosition, showUrl, urlDisplayMode, urlPosition), trace the full chain. The displayConfig construction in MainScreen.kt (line 91) is the most critical junction.
**Warning signs:** Toggle works in editor preview but not in main bookmark list.

### Pitfall 3: remember() Key List Staleness
**What goes wrong:** The `displayConfig` in MainScreen.kt uses `remember()` with an explicit key list (line 91). Adding new fields without updating the key list means stale cached values.
**Why it happens:** The remember key list must enumerate every dependency.
**How to avoid:** Add ALL new activeLayout fields to the remember() keys. Or better, switch to `derivedStateOf` / `remember(activeLayout, ...)` with activeLayout as a single key since it contains all fields.
**Warning signs:** Layout changes don't reflect until navigating away and back.

### Pitfall 4: BookmarkCompactListLayout References After Deletion
**What goes wrong:** After removing BookmarkCompactListLayout, compile errors in files that still reference it.
**Why it happens:** 5 files reference COMPACT_LIST or BookmarkCompactListLayout: BookmarkListContent.kt, LayoutEditorScreen.kt, DefaultDisplaySettingsScreen.kt, BookmarkPlaceholderItem.kt, BookmarkLayouts.kt.
**How to avoid:** Do a codebase-wide search for `COMPACT_LIST` and `BookmarkCompactListLayout` and update all call sites.
**Warning signs:** Compilation fails after removing the composable.

### Pitfall 5: Per-List Picker Dialog Navigation on Desktop
**What goes wrong:** On desktop, the LayoutEditorDialog pattern is used instead of navigator.push(). If the per-list picker blindly uses navigator.push(), it may break on desktop.
**Why it happens:** LayoutsScreen already handles this split (lines 125-129, 167-173). PerListSettingsScreen must follow the same pattern.
**How to avoid:** Check `getPlatform().isDesktop` and use the dialog pattern for desktop, navigator push for mobile.
**Warning signs:** Desktop layout creation from per-list picker crashes or shows blank screen.

### Pitfall 6: URL Parsing for Domain Extraction
**What goes wrong:** Naive URL parsing with string splitting fails for edge cases: URLs without scheme, URLs with ports, IDN domains, about:blank, etc.
**Why it happens:** URLs in the wild are messy.
**How to avoid:** Use a proper URL parser. The codebase already has `FaviconUtils.getFaviconUrl(bookmark.url)` which parses URLs -- follow the same approach. Wrap in try-catch, fall back to raw URL display on parse failure.
**Warning signs:** Crash or wrong domain display for unusual URLs.

## Code Examples

### New Fields for BookmarkLayout

```kotlin
// Add to BookmarkLayout data class (all with defaults for backward compatibility):
val showDescription: Boolean = true,
val descriptionPosition: String = DescriptionPosition.BELOW_TITLE.name,
val showUrl: Boolean = false,
val urlDisplayMode: String = UrlDisplayMode.DOMAIN_ONLY.name,
val urlPosition: String = UrlPosition.BELOW_TITLE.name,
```

### New Fields for MainScreenDisplayConfig

```kotlin
data class MainScreenDisplayConfig(
    // ... existing fields ...
    val showDescription: Boolean,
    val descriptionPosition: DescriptionPosition,
    val showUrl: Boolean,
    val urlDisplayMode: UrlDisplayMode,
    val urlPosition: UrlPosition,
)
```

### URL Domain Extraction Utility

```kotlin
// In ui/utils/ or alongside FaviconUtils
fun extractDomain(url: String): String {
    return try {
        val host = java.net.URI(url).host ?: return url
        if (host.startsWith("www.")) host.removePrefix("www.") else host
    } catch (_: Exception) {
        url
    }
}
```

### Per-List Picker "Create New Layout" Button

```kotlin
// Inside the AlertDialog text lambda, after the layout forEach loop:
HorizontalDivider()
TextButton(
    onClick = {
        showLayoutPickerDialog = false
        navigator.push(LayoutEditorScreen(layoutId = null))
    },
    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
) {
    Icon(
        Icons.Default.Add,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary
    )
    Spacer(modifier = Modifier.width(8.dp))
    Text(
        "Create new layout",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.primary
    )
}
```

## Files Requiring Changes

### Data Model (Wave 1)
| File | Change | Impact |
|------|--------|--------|
| `data/model/BookmarkLayout.kt` | Add 5 new fields with defaults; update BUILTIN_COMPACT to use LIST type with compact defaults | Core data model |
| `data/model/LayoutType.kt` | Map COMPACT_LIST -> LIST in fromString(); optionally deprecate enum value | Migration compatibility |
| `data/model/UrlPosition.kt` | **NEW** -- enum BELOW_TITLE, METADATA_ROW with fromString() | New enum |
| `data/model/UrlDisplayMode.kt` | **NEW** -- enum DOMAIN_ONLY, FULL_URL with fromString() | New enum |
| `data/model/DescriptionPosition.kt` | **NEW** -- enum BELOW_TITLE, ABOVE_METADATA with fromString() | New enum |

### Editor (Wave 2)
| File | Change | Impact |
|------|--------|--------|
| `ui/screens/settings/LayoutEditorScreen.kt` | Remove COMPACT_LIST radio option; add Description toggle, URL toggle + sub-options, URL Position section, Description Position section; add update functions to ScreenModel; wire new fields through PreviewBookmarkItem | Major changes to editor UI |

### Rendering (Wave 3)
| File | Change | Impact |
|------|--------|--------|
| `ui/components/BookmarkLayouts.kt` | Delete BookmarkCompactListLayout; add showDescription/descriptionPosition/showUrl/urlDisplayMode/urlPosition params to BookmarkListLayout and BookmarkCardLayout | Core rendering |
| `ui/screens/main/MainScreenScaffoldContent.kt` | Add new fields to MainScreenDisplayConfig; pass through to BookmarkListContent | Display config pipeline |
| `ui/screens/MainScreen.kt` | Wire new fields in displayConfig construction + remember keys | Display config pipeline |
| `ui/screens/main/BookmarkListContent.kt` | Remove COMPACT_LIST branch; add new params to LIST and CARD call sites | Rendering dispatch |
| `ui/components/BookmarkPlaceholderItem.kt` | Remove COMPACT_LIST placeholder variant | Placeholder cleanup |
| `ui/screens/settings/DefaultDisplaySettingsScreen.kt` | Remove COMPACT_LIST references | Settings cleanup |

### Per-List Picker (Wave 4)
| File | Change | Impact |
|------|--------|--------|
| `ui/screens/settings/PerListSettingsScreen.kt` | Add "Create new layout" button in layout picker AlertDialog; handle desktop dialog pattern | Navigation enhancement |

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | kotlin.test + kotlinx-coroutines-test |
| Config file | `composeApp/build.gradle.kts` (commonTest dependencies) |
| Quick run command | `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home ./gradlew :composeApp:desktopTest --tests "com.karakept.app.*Layout*"` |
| Full suite command | `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home ./gradlew :composeApp:desktopTest` |

### Phase Requirements -> Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| UX-02a | COMPACT_LIST fromString maps to LIST | unit | `./gradlew :composeApp:desktopTest --tests "*LayoutType*"` | No -- Wave 0 |
| UX-02b | BookmarkLayout new fields have backward-compatible defaults | unit | `./gradlew :composeApp:desktopTest --tests "*BookmarkLayout*"` | No -- Wave 0 |
| UX-02c | BUILTIN_COMPACT is now LIST type with compact defaults | unit | `./gradlew :composeApp:desktopTest --tests "*BookmarkLayout*"` | No -- Wave 0 |
| UX-02d | UrlPosition/UrlDisplayMode/DescriptionPosition fromString works | unit | `./gradlew :composeApp:desktopTest --tests "*Position*"` | No -- Wave 0 |
| UX-02e | Domain extraction handles URLs correctly | unit | `./gradlew :composeApp:desktopTest --tests "*UrlUtils*"` | No -- Wave 0 |
| UX-02f | LayoutEditorScreenModel update functions for new fields | unit | `./gradlew :composeApp:desktopTest --tests "*LayoutEditor*"` | No -- Wave 0 |

### Sampling Rate
- **Per task commit:** Quick run command targeting changed model tests
- **Per wave merge:** Full test suite
- **Phase gate:** Full suite green before `/gsd:verify-work`

### Wave 0 Gaps
- [ ] `composeApp/src/commonTest/kotlin/com/karakept/app/data/model/LayoutTypeTest.kt` -- covers COMPACT_LIST migration mapping
- [ ] `composeApp/src/commonTest/kotlin/com/karakept/app/data/model/BookmarkLayoutTest.kt` -- covers built-in preset values, new field defaults, serialization round-trip
- [ ] `composeApp/src/commonTest/kotlin/com/karakept/app/data/model/UrlPositionTest.kt` -- covers enum fromString
- [ ] `composeApp/src/commonTest/kotlin/com/karakept/app/data/model/UrlDisplayModeTest.kt` -- covers enum fromString
- [ ] `composeApp/src/commonTest/kotlin/com/karakept/app/data/model/DescriptionPositionTest.kt` -- covers enum fromString
- [ ] `composeApp/src/commonTest/kotlin/com/karakept/app/ui/utils/UrlUtilsTest.kt` -- covers domain extraction from various URL formats

## Open Questions

1. **Title position (D-01, D-03 mention it)**
   - What we know: CONTEXT.md mentions "title position" as a shared customization option for both LIST and CARD
   - What's unclear: The UI-SPEC does not define a "Title Position" section or enum. This appears to have been dropped between CONTEXT and UI-SPEC design.
   - Recommendation: Omit title position for now -- the UI-SPEC is the binding contract and does not include it. If needed, it can be added in a future phase.

2. **Desktop dialog pattern for per-list picker**
   - What we know: LayoutsScreen uses `LayoutEditorDialog` for desktop, `navigator.push(LayoutEditorScreen)` for mobile
   - What's unclear: Whether PerListSettingsScreen should follow the same split or can always use navigator.push since it's already a full screen (not a dialog)
   - Recommendation: Follow the same pattern as LayoutsScreen -- use LayoutEditorDialog on desktop, navigator.push on mobile. This is consistent and the dialog pattern already works.

## Sources

### Primary (HIGH confidence)
- Direct codebase analysis of all referenced files
- `BookmarkLayout.kt` -- @Serializable data class with default values confirms backward-compatible field addition
- `LayoutType.kt` -- fromString pattern confirms migration approach
- `MetadataPosition.kt`, `DateDisplayMode.kt` -- enum patterns to follow
- `LayoutEditorScreen.kt` -- ToggleRow/LayoutRadioOption reuse patterns, PreviewBookmarkItem dispatch
- `PerListSettingsScreen.kt` -- layout picker dialog structure (lines 141-205)
- `MainScreen.kt` -- displayConfig construction pipeline (lines 85-112)
- `BookmarkLayouts.kt` -- three render composables, description rendering logic
- `BookmarkListContent.kt` -- layoutType dispatch with COMPACT_LIST branch

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH -- no new libraries needed, all changes use existing patterns
- Architecture: HIGH -- direct codebase analysis, clear extension points identified
- Pitfalls: HIGH -- specific line numbers and code paths identified for all risk areas

**Research date:** 2026-03-30
**Valid until:** 2026-04-30 (stable codebase, no external dependency changes)
