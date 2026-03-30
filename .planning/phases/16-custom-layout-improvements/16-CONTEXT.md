# Phase 16: Custom Layout Improvements - Context

**Gathered:** 2026-03-30
**Status:** Ready for planning

<domain>
## Phase Boundary

Enhance the custom layout system with 4 improvements: unify List and CompactList into a single configurable type, fix Card preview to show all toggled fields, add URL/domain display option with configurable position, and add a "Create new layout" button in the per-list layout picker dialog.

</domain>

<decisions>
## Implementation Decisions

### Layout Unification Strategy
- **D-01:** Merge LIST and COMPACT_LIST into a single "List" type. CARD stays separate as its own distinct type. The unified List type gains shared customization options (show/hide description, description position, title position). Toggling fields off effectively reproduces what COMPACT_LIST is today.
- **D-02:** Auto-migrate existing COMPACT_LIST layouts to LIST with compact-like toggle defaults (description off, tags off, smaller thumbnail). The built-in Compact preset becomes a LIST variant with those defaults.
- **D-03:** CARD gets some of the new shared options — specifically show/hide description and title position — but NOT description position (Card's vertical structure always places description below title).

### Card Preview Content
- **D-04:** The editor's Card preview must reflect exactly what the user has enabled — title always visible, plus description/tags/date/URL based on current toggle states. Changes update the preview live. This applies to all layout types, not just Card.

### URL/Domain Display
- **D-05:** New `showUrl` toggle and `urlDisplayMode` option: user can choose between domain-only ("github.com") or full URL. Full URL truncates with ellipsis on narrow screens.
- **D-06:** URL/domain position is configurable — user picks where it appears (e.g., below title, in metadata row, beside favicon). Adds a new `urlPosition` setting similar to `metadataPosition`.

### Picker Navigation
- **D-07:** Add a "Create new layout" footer button at the bottom of the per-list layout picker dialog (`PerListSettingsScreen`). Tapping navigates to `LayoutEditorScreen`; on return the new layout appears in the picker list.
- **D-08:** Per-list picker gets create-only — no edit access. Editing layouts stays in the global `LayoutsScreen` settings page.

### Claude's Discretion
- Migration logic for COMPACT_LIST → LIST conversion (exact default values for toggled fields)
- Specific `urlPosition` enum values (e.g., BELOW_TITLE, METADATA_ROW, BESIDE_FAVICON or similar)
- How the per-list picker dialog handles navigation to/from LayoutEditorScreen (dismiss dialog first, or return to it)

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Requirements
- `.planning/REQUIREMENTS.md` §UX-02 — Full acceptance criteria for custom layout improvements

### Layout System
- `composeApp/src/commonMain/kotlin/com/karakept/app/data/model/BookmarkLayout.kt` — Primary layout data model (fields, built-in presets)
- `composeApp/src/commonMain/kotlin/com/karakept/app/data/model/LayoutType.kt` — Layout type enum (CARD, LIST, COMPACT_LIST)
- `composeApp/src/commonMain/kotlin/com/karakept/app/data/model/MetadataPosition.kt` — Existing position enum pattern to follow for urlPosition
- `composeApp/src/commonMain/kotlin/com/karakept/app/data/model/DateDisplayMode.kt` — Existing display mode enum pattern

### Layout Editor
- `composeApp/src/commonMain/kotlin/com/karakept/app/ui/screens/settings/LayoutEditorScreen.kt` — Editor UI + PreviewBookmarkItem composable + LayoutEditorScreenModel
- `composeApp/src/commonMain/kotlin/com/karakept/app/ui/screens/settings/LayoutsScreen.kt` — Global layout picker with FAB

### Layout Rendering
- `composeApp/src/commonMain/kotlin/com/karakept/app/ui/components/BookmarkLayouts.kt` — Three render composables (BookmarkCardLayout, BookmarkListLayout, BookmarkCompactListLayout)

### Per-List Settings
- `composeApp/src/commonMain/kotlin/com/karakept/app/ui/screens/settings/PerListSettingsScreen.kt` — Per-list layout picker dialog (lines 141-205)

### Layout Resolution
- `composeApp/src/commonMain/kotlin/com/karakept/app/ui/screens/main/MainScreenModel.kt` — activeLayout resolution (lines 261-279)
- `composeApp/src/commonMain/kotlin/com/karakept/app/ui/screens/main/MainScreen.kt` — displayConfig computed property (lines 83-112)

### Storage
- `composeApp/src/commonMain/kotlin/com/karakept/app/data/SettingsRepository.kt` — Layout DataStore keys (lines 505-518)
- `composeApp/src/commonMain/kotlin/com/karakept/app/data/SettingsRepositoryMutations.kt` — saveLayout, deleteLayout, setDefaultLayoutId (lines 426-479)

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `BookmarkLayout` data class: serializable with `@Serializable`, easy to extend with new fields (showDescription, descriptionPosition, titlePosition, showUrl, urlDisplayMode, urlPosition)
- `MetadataPosition` enum: pattern to follow for new position enums (urlPosition)
- `PreviewBookmarkItem` composable: already routes to production composables — just needs the new fields wired through
- `LayoutEditorContent`: section-based editor with `ToggleRow` and `LayoutRadioOption` helpers ready to reuse for new options

### Established Patterns
- Layout fields use String-serialized enum names (e.g., `layoutType: String` not `LayoutType`), with `fromString()` converters
- Built-in presets are companion object singletons in `BookmarkLayout`
- `LayoutEditorScreenModel` has individual `update*()` functions per field
- Per-list settings stored as JSON map in DataStore under `PER_LIST_SETTINGS_KEY`

### Integration Points
- `BookmarkListContent.kt` receives display config as individual parameters — needs new params for description visibility, title/description/URL positions
- `MainScreen.kt` `displayConfig` computed property maps `BookmarkLayout` fields to individual parameters
- `MainScreenDisplayConfig` value object needs new fields
- Backup/restore in `BackupSettings` includes custom layouts JSON — new fields will serialize automatically via `@Serializable`

</code_context>

<specifics>
## Specific Ideas

- User specifically wants LIST + COMPACT_LIST merged (not CARD + LIST as the requirement originally suggested), because they're both row-based and differ only in shown metadata
- Card keeps its distinct identity but gains show/hide description and title position
- URL display should support both domain-only and full URL modes (user chose both, not just domain)

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 16-custom-layout-improvements*
*Context gathered: 2026-03-30*
