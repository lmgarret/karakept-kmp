# Karakept KMP — AI Agent Guidelines

## Project Overview

Karakept is a Kotlin Multiplatform (KMP) bookmark manager app built with Compose Multiplatform,
targeting Android and JVM Desktop (Linux, macOS, Windows).
The UI follows **Material Design 3 (MD3)** guidelines throughout.

Key technologies:
- Kotlin 2.4.10 / Compose Multiplatform 1.12.0
- Material3 (`androidx.compose.material3`)
- Compose Navigation 3 (`androidx.navigation3` / `org.jetbrains.androidx.navigation3` 1.1.1) for navigation
- `androidx.lifecycle` `ViewModel` (multiplatform) for per-screen state (MVVM)
- Koin 4.2.2 for dependency injection (incl. `koin-compose-viewmodel`, `koin-compose-navigation3`)
- Room 3.0.2 (`androidx.room3`) for local SQLite storage
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
| Android application shell | `androidApp/` (build types, signing, applicationId — no Kotlin) |
| Generated API client | `api-client/` (OpenAPI-generated, committed) |

### Entry Points

- **`App.kt`**: root composable, initializes Koin, routes to initial screen based on app state.
- **`MainActivity.kt`** (Android): sets up the Compose activity.
- **`AppModule.kt`**: all Koin bindings — repositories as `single()`, ScreenModels as `viewModel()`.
- **`ui/navigation/`**: `AppNavigator`/`LocalNavigator` (back-stack wrapper + CompositionLocal), `appEntryProvider()` + `navKeySerializersModule` (NavKey↔content map and polymorphic registry), and the Shared Axis Z `NavDisplay` transition specs.

### Gradle Modules

| Module | Plugin | Holds |
|---|---|---|
| `:composeApp` | `com.android.kotlin.multiplatform.library` + `jvm("desktop")` | Everything — commonMain, desktopMain, **and all of androidMain** (MainActivity, the workers, the manifest, `res/`) |
| `:androidApp` | `com.android.application` | Nothing but build config: `applicationId`, build types, signing, versioning. **No Kotlin sources.** |
| `:api-client` | `com.android.kotlin.multiplatform.library` + `jvm("desktop")` | The OpenAPI-generated client |

AGP 9 refuses to apply `com.android.application` alongside the Kotlin Multiplatform plugin, and
its replacement (`com.android.kotlin.multiplatform.library`) is library-only. So the KMP module
stays a library and `:androidApp` supplies the parts a library cannot have. Consequences worth
knowing before touching any of it:

- **Android code belongs in `composeApp/src/androidMain`, not `:androidApp`.** `:androidApp`
  having no sources is what keeps the 200-odd `internal` declarations in `commonMain` reachable
  from `MainActivity` and friends, and what keeps `AppIconManager`'s `ALIAS_PACKAGE` (derived
  from `MainActivity::class.java.name`) pointing at `com.karakept.app`.
- **A KMP library has no build types and no `BuildConfig`.** `isDevBuild` reads the
  `karakept_is_dev` bool resource, declared `false` in `composeApp/src/androidMain/res` and
  overridden `true` in `androidApp/src/devRelease/res` — app resources win over library
  resources of the same name. Anything else that used to be a `buildConfigField` goes the
  same way.
- **A KMP library has no `manifestPlaceholders` either**, and the Robolectric host-test manifest
  merge is a real merge that fails on an unresolved one. The app label is
  `@string/karakept_app_name`, overridden in `androidApp/src/devRelease/res`, for the same
  reason. `${applicationId}` still works — AGP substitutes that one itself.
- **Namespaces differ from the applicationId.** `:composeApp` is `com.karakept.app` (two modules
  cannot share a namespace, and this is the one components resolve against); `:androidApp` is
  `com.karakept.app.android`; `applicationId` stays `com.karakept.app`.
- **The Android suite runs once**, as `:composeApp:testAndroidHostTest` — a KMP library has a
  single variant, so the `androidComponents { beforeVariants … }` block that used to switch off
  the release/devRelease copies is gone with the build types.

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

### Icons

**`AppIcons`** (`ui/icons/AppIcons.kt`) — the app's only source of Material icons.
`Icons.Default.X` becomes `AppIcons.Default.X`, `Icons.AutoMirrored.Filled.X` becomes
`AppIcons.AutoMirrored.Filled.X`, and so on: the shape mirrors upstream, so a call site reads
the same.

`material-icons-extended` is frozen at 1.7.3 and no longer maintained, so the icons the app
draws are vendored instead: `tools/material-icons.txt` lists them, and
`tools/generate_material_icons.py` regenerates `AppIcons.kt` from the 24dp SVGs in
google/material-design-icons — the same artwork the Compose artifact was generated from.
The generator needs network access and is run by hand, never from the build.

> **Rule:** never import `androidx.compose.material.icons.*`. To use an icon the app does not
> draw yet, add its name to `tools/material-icons.txt` and re-run the generator —
> `AppIconsManifestTest` fails on a manifest and a generated file that disagree.

### Tag display

**`TagChip`** (`ui/components/TagChip.kt`)
- Single tag chip: `secondaryContainer` surface, `shapes.small`, 2dp elevation.
- Supports optional `onRemove` (shows ✕ button) and `onClick`.
- **Use everywhere a single tag chip is displayed.**

**`BookmarkTagsDisplay`** (`ui/components/BookmarkTagsDisplay.kt`)
- Renders a comma-separated tag string as a `FlowRow` of `TagChip` chips.
- Accepts `style` (`COMPACT` / `READER`), optional `onTagClick`, `scrollable`, and `modifier`.
- `maxLines` caps how many rows of chips the flow may wrap onto. A caller holding its row to a
  fixed height has to pass it: a second row of chips is the one part of a bookmark row whose height
  the layout settings do not bound.
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

| `descriptionMaxLines` | Line cap, or `DESCRIPTION_LINES_AUTO` to fill the space the thumbnail leaves over. A cap either way: a row held to a tile renders fewer lines when that is what fits (see "Hardware page-turn buttons") |

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
  stacked form: a drawer row's trailing status, an asset download with no `Content-Length`.
- Both used to hold an **indeterminate** `LinearProgressIndicator` or `CircularProgressIndicator`
  — a bar that sweeps forever, which on e-ink shows as a smear or, on panels that throttle
  refreshes, as nothing at all. Determinate bars are fine there and stay as they are: they only
  repaint when progress moves.

**`FloatingBusyCard`** (`ui/components/EinkAware.kt`)
- A centred card holding a **screen-level** busy state on e-ink — the bookmark list's sync, the
  reader's refresh. Bordered under `highContrast` (elevation separates nothing when every
  surface role is the page colour) and takes no pointer input, so the content underneath stays
  scrollable while it is up.
- Screen-level progress used to live in a thin strip pinned to the top edge of the content. That
  is easy to overlook on a monochrome panel: no colour to catch the eye, no motion the display
  renders smoothly. One deliberate block in the middle of the page is the placement that reads.
- On e-ink the **determinate** sync bar moves into the card too. Not because a determinate bar
  is a problem — it isn't — but so sync state lives in one place rather than jumping between the
  card and the top strip as `SyncProgress` resolves.

> **Rule:** a screen-level busy state on e-ink goes in a `FloatingBusyCard`; a row- or
> control-level one stays inline with `InlineLoadingDots`. Off e-ink both keep the strip or
> spinner they already had.

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

### Elements that float above the page

FABs, the "N new bookmarks" pill, snackbars and `FloatingBusyCard` all sit above the content, and
Material gives every one of them the same edge: a drop shadow. Under `highContrast` that edge
disappears twice over — the shadow is a grey gradient the panel cannot render, and the container
colour has already collapsed to the page colour — so a FAB becomes an icon with no button around
it. `floatingSurfaceStyle(elevation)` (`ui/components/EinkAware.kt`) resolves the pair for the
current display: the shadow off e-ink, a 1dp `outline` border on it, never both and never neither.
Feed it into a `Surface` as `shadowElevation = style.shadowElevation` and
`border = style.borderStroke()`.

**`EinkAwareFab`** / **`EinkAwareSmallFab`** (`ui/components/EinkAware.kt`)
- The same treatment already wired into a FAB, plus zeroed pressed/hovered elevation.
- **Use instead of `FloatingActionButton` / `SmallFloatingActionButton` everywhere.**

**`EinkAwareSnackbarHost`** (`ui/components/EinkAware.kt`)
- **Use instead of `SnackbarHost` everywhere.** Material's host fades *and* scales its snackbar in
  and out with a spec it does not expose, so under `animationsDisabled` the whole host is replaced
  — which is why `snackbarDurationMillis` exists: dismissing a snackbar once its duration is up is
  the one job that host was still doing.
- Under `highContrast` the visual is replaced too. `Snackbar` hardcodes a 6dp shadow, and its
  `inverseSurface` container is a solid block of ink on the e-ink scheme — a lot of ink to lay
  down, and then ghost, for a message that leaves after four seconds.

> **Rule:** state is carried by the icon and the label, not by the container colour.
> `primaryContainer`, `tertiaryContainer` and `surfaceContainerHigh` are all the page colour under
> `highContrast`, so a FAB menu item that shows "on" by swapping its container shows nothing —
> swap the icon (`Star`/`StarBorder`) and the label ("Favorite"/"Unfavorite") as
> `BookmarkFabMenu` does.

### AI actions

`AiAction` (`domain/action/AiAction.kt`) covers the two server-side AI jobs — generate a summary,
re-run AI tagging. Like `ServerCrawlAction`, and unlike `BookmarkActionEvent`, these deliberately
**bypass `BookmarkActionController` and the pending-action queue**: there is nothing to undo and
no local optimistic result to show while offline.

Each is hidden unless the server has said it will run it, via
`BookmarkActionsRepository.aiCapabilities` — summarizing needs an inference client configured,
re-running AI tagging needs an admin key (Karakeep exposes no user-level re-tag route). A new
surface offering an AI action must gate on the same flags.

The generated text is `BookmarkEntity.summary`, **not** `description`: the crawler reads
`description` from the page's meta tags and Karakeep's inference worker never touches it. Both can
be present, and the reader shows both. See `docs/ai-actions.md`.

### Highlight offsets

A highlight is a character range, and a range means nothing without the stream it counts in. That
stream is **`ReaderTextOffsets`** (`ui/components/reader/ReaderTextOffsets.kt`): the document's
text nodes concatenated in document order and nothing else — the same count karakeep's web app
makes with a `TreeWalker` (`BookmarkHtmlHighlighter.getTextNodeOffset`) and the same one the
WebView viewer's JS makes, so a highlight means the same thing in both viewers and on the server.

`buildReaderTextOffsets(body)` walks it once per document; the reader remembers the result and
every renderer reads its offsets rather than counting along as it draws. That counting is what
drifted: creation resolved a selection through one walk and drawing accumulated another, and
wherever the second forgot something — a container walked with `children()` instead of
`childNodes()`, a `<br>`, the whitespace between two `<li>`s — every later highlight moved a
character or two. Readability wraps an article in a single `<div>`, so nothing ever reset the
drift and it grew down the page.

> **Rule:** never derive an offset by counting text as you render — ask `offsets.startOf(node)` /
> `endOf(node)`. To map a stream range onto a string you built, record a `TextRuns` entry per text
> node and translate through `runs.localRange(start, end)`. The rendered string is not the stream
> (a `<br>` is a character in one and not the other); what was recorded is the only thing relating
> them.

Two things follow from the stream carrying no separators of its own:

- A selection spanning two blocks comes back from Compose joined with a newline, and the stream
  has no character there. Those positions are recorded as **boundaries** instead: they match as
  whitespace and occupy no offset, so the selection resolves and the offsets stay karakeep's.
- Reader search runs on the same offsets (`findSearchMatches`), since the same renderers draw a
  search match and a highlight. It used to keep a walk of its own, written to mirror the renderer
  rather than the resolver, which left search and highlights on two conventions.

Offsets outlive the document they were taken in — another client, an older build, a re-crawl — so
`resolveHighlights` checks each highlight against the text it was created from before drawing it,
and re-resolves the ones that disagree onto the nearest occurrence of that text.

### Highlight colours

Karakeep gives a highlight one of four colours — `yellow`, `blue`, `green`, `red` — stored as a
plain string. **`HighlightPalette`** (`ui/theme/HighlightPalette.kt`) is the only place that turns
that string into anything visible: `HighlightPalette.styleFor(name)` returns a `HighlightStyle`
with the Compose `color`, a display `label`, the `cssHex` the WebView stylesheet is generated
from, and a `HighlightPattern`. Unknown and null names fall back to yellow, matching both the API
default and `HighlightRepository`'s own.

> **Rule:** never write a `when (color) { "yellow" -> … }` again. There were four such tables and
> they had already drifted — two `<mark>` renderers disagreed on opacity, and the WebView's hex
> literals were maintained by hand.

On a monochrome panel the four fills all land within a couple of greys of each other, so under
`highContrast` the fill stops carrying the colour and the **pattern** does:
solid (yellow) / double (blue) / dashed (green) / dotted (red).

- **`drawHighlightRule(...)`** (`ui/components/HighlightPatterns.kt`) — a `DrawScope` extension
  drawing the pattern as a horizontal rule, growing *upward* from a `bottom` edge so it stays
  inside the band whatever the line height. `AnnotatedClickableText` calls it once per line box of
  every highlighted run, via `drawWithContent` **after** `drawContent()` — a highlight's fill is a
  `SpanStyle.background` painted by the text itself, so a rule put behind the text is covered up.
- **`HighlightPatternBar`** (same file) — the colour accent on a highlight row: a plain fill
  normally, the pattern on e-ink. **Use instead of `Box().background(highlightColor)`.**
- `ReaderThemeData.monochromeHighlight` is non-null only on e-ink and holds the one fill every
  highlight shares. `addHighlightSpan` / `sourceMarkSpanStyle` (`HtmlInlineRenderer.kt`) read it —
  both reader paths (`buildInlineAnnotatedString` and `RenderInlineGroup`) go through them.
- A pattern is only learnable if it is named, so `HighlightCard` and the colour picker spell the
  colour out (`TagChip`, and a label under each swatch) under `highContrast`.

### Reader link hover

A pointer resting on a link in the reader gets a hand cursor and, after a second, a plain tooltip
naming the URL — the affordances a browser gives. Both live in
`ReaderLinkHover.kt` (`ui/components/reader/`) and are wired into `AnnotatedClickableText`, which
every reader text renderer already goes through.

A text block is one composable, so neither can come from a modifier on the link itself: the
character under the pointer is resolved against the block's own `TextLayoutResult` on every move
(`characterAt`, which confirms the candidate against its bounding box so the hand stays off the
margin), and the hand is applied with `overrideDescendants = true` to win over the I-beam the
selectable text asks for.

Hover is mouse-only by construction — events from any other `PointerType` are ignored — so nothing
here fires from touch, and the tooltip is placed clear of the pointer hotspot
(`tooltipPosition`), which would otherwise take the hover from the text and flicker.

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
- The reader's hero banner is page 0 of that list, whichever image was tapped. It is not part of
  the article's HTML, so `heroGalleryImage(...)` builds its entry (with a null `element` and,
  for a synced offline copy, a `localPath` the viewer tries before any URL) and
  `BookmarkViewerContent` publishes it as `GalleryViewerState.heroImage` — at screen level, not
  from the hero item, which `LazyColumn` disposes as soon as it scrolls away. `NativeHtmlRenderer`
  prepends it and publishes the combined list back as `GalleryViewerState.images`, which is what
  the banner's own tap (`openHero()`) opens.
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
  `BaseBottomPanel` do — or, for anything floating above the content, take the border and the
  elevation together from `floatingSurfaceStyle()` (see "Elements that float above the page").
- No shimmer or indeterminate spinner as the only "busy" signal; fall back to the stepped dots
  (`BusyIndicator` / `InlineLoadingDots` in `ui/components/EinkAware.kt` do this).
- `secondaryContainer` is the scheme's one deliberate grey, for small repeated elements like tag
  chips. Every other surface role is the page colour — do not reintroduce tonal steps.
- Gestures that track a finger across many frames (swipe-to-act) smear on e-ink. `RowActionMode`
  lets the bookmark list swap them for the always-visible button cluster desktop uses, and
  `RefreshableBox` swaps pull-to-refresh for a top-bar button.

### Monochrome launcher icon

`AppIconManager` (`utils/AppIconManager.kt`, `expect`/`actual`) swaps the launcher icon and splash
for a black-on-white pair. Check `isSupported` before offering it — desktop shells read the icon
before the JVM starts and it is a no-op there.

Android cannot re-point an icon at runtime, so the manifest carries two `activity-alias` launcher
entries (`.LauncherDefault`, `.LauncherMonochrome`) and the manager enables one before disabling
the other. Consequences worth knowing before touching any of it:

- `MainActivity` must stay enabled and keep no launcher `intent-filter` of its own — notification
  intents resolve against it directly.
- Alias `android:name`s expand against the *namespace*, which `applicationIdSuffix` does not
  touch, so a component name built from `packageName` misses on the dev build type.
- `einkMonochromeIcon` is the one e-ink setting **not** ANDed with the master switch: the icon
  outlives the mode on the home screen.
- The splash follows via `MainActivity.applySplashScreenTheme()`, from SharedPreferences rather
  than DataStore — it runs before a suspending read has anywhere to go — and takes effect only
  on the next cold start from API 31, where the system paints the splash before the process
  exists.

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

In the **reader**, a page also **ends on a content boundary**, not on an arbitrary pixel —
`snapToContent` (default on) walks the turn back onto the top of the line of text that would
otherwise be sliced by the edge. Snapping supplies its own overlap, so `effectiveOverlapPercent`
returns 0 while it is on and the "Page overlap" slider is disabled; the two would stack otherwise.
The walk back is capped (`READER_MAX_SNAP_FRACTION` in `ui/utils/PageSnapUtils.kt`) so a fold
landing inside an image or a table is left alone rather than rewinding most of the turn.

The whole article is a *single* lazy item, so `layoutInfo` knows nothing about lines and
`PageTurnScrollEffect`'s `predictiveSnap` answers from a registry instead.
`ReaderSnapRegistry` (`ui/components/reader/ReaderSnapPoints.kt`, provided through
`LocalReaderSnapRegistry`) collects every line top and bottom in root coordinates, fed from
`AnnotatedClickableText`'s existing `onGloballyPositioned` and `onTextLayout` callbacks. Since every
block is eagerly composed — above and below the viewport alike — the landing line is known *before*
the turn and folds into a single `scrollBy`, so no intermediate position is ever observable to the
reader's scroll guard.

The **bookmark list** reaches the same place from the other end. A viewport can only be tiled
exactly by rows of *uniform* height, so under E-ink mode with snapping on every row is held to one
(`ui/utils/RowTilingUtils.kt`, resolved for the active layout by `rememberTiledRows`). A page is
then a whole number of rows, `tiledTurnAdjustment` lands a turn on a row top by arithmetic rather
than by searching a registry, and the list takes the same `trailingPagePaddingFor` so its last turn
lands on a handover.

- The row's natural height is **declared, not measured**: `BookmarkRowMetrics` adds up the
  thumbnail, the title at its two-line cap, the description at its own, the metadata band and the
  paddings. A live measurement cannot work — applying a tile makes every row report the tile back,
  so the quantiser would be reading its own output — and reading the layout instead does not depend
  on whether the list happens to open on two short bookmarks. `BookmarkRowTilingTest` renders the
  real row for every built-in layout and checks the declaration still matches it.
- The count of rows is the *nearest* one, not the one that fits, and the row gives the difference
  back: a description line first (a fixed `descriptionMaxLines` is a cap, not a promise, once a
  tile is imposed), then a few percent off the thumbnail. `BookmarkRowMetrics.minHeightPx` is where
  that stops; below it a tile would crop the metadata row rather than trim the description, so one
  row fewer is taken instead. Tags are capped to a single line under a tile — a second row of chips
  is the one part of a row whose height the layout settings do not bound.
- `Magazine` (`LayoutType.CARD`) is left ragged on purpose: its hero image is sized by its own
  aspect ratio, so no single height is close to two rows and a tile would crop the image.

> **Rule:** a tile is only ever as short as the row can shrink to. Clipping a row to fit does not
> trim a line of prose off the bottom — the bottom of a row is its date and reading time — which is
> why `resolveRowTiling` takes a minimum and steps down rather than squeezing past it.

> **Rule:** a new reader text renderer that does not go through `AnnotatedClickableText` silently
> loses line snapping. Register it with `LocalReaderSnapRegistry` or route it through
> `AnnotatedClickableText`. Report from layout callbacks into the registry's plain map only —
> snapshot state written during layout drags the whole article into recomposition on every scroll
> frame. Non-text blocks (images, tables, rules) register nothing on purpose: they have no line to
> keep whole, and the snap cap then leaves those folds alone.

Snapping aligns the *top* of a page. Both edges cannot sit on a boundary at once — they are one
page apart and the content decides where the boundaries fall — so whole pages need leftover space,
and that is what `ui/utils/PagedRendering.kt` adds **under E-ink mode** (the snap itself stays
ungated):

- `Modifier.pagedBottomEdge` paints over whatever the bottom edge cuts. Its height is
  `bottomMaskHeight`, defined as the mirror of `computeSnapAdjustment` rather than recomputed, so
  the band covers *exactly* what the next turn brings back — band more and that content is never
  seen, band less and a sliver of a line still shows. An element too tall to snap is therefore not
  banded either, and stays sliced across both pages on purpose.
- The band is drawn only while the list sits exactly where a turn left it (`PagedPositionState`).
  Mid-drag it would read as content ending early and rows popping in at the edge; comparing
  positions catches every other way the list can move — fling, wheel, scroll restoration — without
  enumerating them.
- `computeTrailingPagePadding` adds blank space after the content so the final turn lands on the
  previous page's handover instead of clamping and re-showing a screenful. It is deliberately
  generous, which is safe only because two things bound it: a clamped turn may still snap
  (`hasTrailingPadding` lifts the `turnReachedFullPage` guard), and `tailFullyVisible` makes a
  forward turn a no-op once the end of the content is on screen — without that the padding buys an
  extra page whose every line the previous one already showed.

> **Rule:** that padding is room for a *turn* to land on a handover, never somewhere to come to
> rest. Anything that positions the list itself must pull back out of it with `blankBelowContent`,
> as `ViewerScrollRestoration` does — a finished bookmark restores to `contentHeight × 1.0`, which
> clamps into the blank space and reopens the article on a page holding a single line.

> **Rule:** a lazy item is not the same shape as the content inside it. The reader's article box runs ~52dp past its final line — the last block's
> padding, the renderer's `Spacer` and its `Column` padding — so `tailFullyVisible` alone keeps
> saying "more below" after the last word is read, and the next turn lands on blank space. The
> reader therefore also passes `moreContentBelow`, answered by `pageWorthTurning` from two registry
> queries: `hasLineBelow` (an edge inside the final line still has to turn) and the gap to
> `contentEndInRoot`, reported by one zero-height marker `NativeHtmlRenderer` places above its
> bottom margin (a closing image registers no lines, and only its extent keeps it reachable).

> **Rule:** anything that changes how a page is bounded has to keep *contiguity* — the visible
> content of one page ends exactly where the next begins. A gap means a line was skipped, an
> overlap means one was shown twice. `PageTurnSnapSimulationTest` models the reader's scrolling end
> to end and asserts it; that is where the spurious final page was caught.

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
| Android unit tests | `composeApp/src/androidHostTest/kotlin/com/karakept/app/` |

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
./gradlew test                  # All tests (alias for allTests)
./gradlew allTests              # Desktop + Android host suites
./gradlew desktopTest           # Desktop (JVM) suite — commonTest + desktopTest
./gradlew :composeApp:testAndroidHostTest   # Android (Robolectric) suite
./gradlew -t test               # Watch mode
./gradlew :composeApp:desktopTest --tests BookmarkRepositoryUnitTest
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
5. Add the migration to `ALL_MIGRATIONS` (`data/local/migrations/AppMigrations.kt`). Every builder
   applies it through `withAppSchema()` — both platforms and the tests that open a real file — so
   a migration left out of that one array is a silent destructive wipe.

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
- Runs `./gradlew desktopTest --rerun` — commonTest + desktopTest, the bulk of coverage.
  The Android (Robolectric) suite runs as a release gate instead (`release.yml`).
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
