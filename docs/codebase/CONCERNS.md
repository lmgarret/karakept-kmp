# Codebase Concerns

**Analysis Date:** 2026-11-05 (rewrite of the 2026-03-20 audit; resolved entries are summarised at the end)

## Security

**Android WebView (Web mode) runs archived pages with JavaScript and a native bridge:**
- Files: `feature/reader/src/androidMain/kotlin/com/karakept/app/ui/components/reader/HtmlRenderer.android.kt` (WebView settings around line 946), `core/data/src/commonMain/kotlin/com/karakept/app/data/cache/HtmlArchiveProcessor.kt`
- Current settings:
  - `javaScriptEnabled = true`, with `addJavascriptInterface(webInterface, "Android")` exposing a bridge to the page (the highlight JS needs it)
  - `allowFileAccess = true` (cached local images are loaded from `file://`)
  - `mixedContentMode = MIXED_CONTENT_ALWAYS_ALLOW` (HTTP images from a `file://` context)
  - `allowContentAccess = false`; link clicks intercepted by the `WebViewClient`
- Current mitigations: `processForArchive` removes `<script>`, `iframe`/`object`/`embed`/`applet`, `<form>`, and validates `href`/`src` URLs.
- Risk: inline event handlers are stripped from a **fixed list** of 25 names. Anything else (`onanimationstart`, `onpointerover`, `ontoggle`, `onfocusin`, SVG `onbegin`, …) survives, and with JavaScript on it runs with access to the `Android` bridge — `onHighlightClick`, `onTextSelected`, `onSelectionChanged`, `onLog`. None reads app data, but a page could fake a selection and drive the highlight-creation UI. `<meta http-equiv="refresh">`, `<base>` and `<link>` are not removed either.
- Fix approach: strip every attribute whose name starts with `on`, remove `meta[http-equiv]`/`base`, and keep the bridge's surface as small as it is now (no method that reads app data or acts without the user). Consider injecting the app's own JS via `evaluateJavascript` and leaving page JS off if the bridge can do without it.

**Reader mode** sanitises with `HtmlSanitizer` (KSoup `Safelist.relaxed()`) and renders natively, without a WebView, so it is not exposed to the above.

## Dependency Version Issues

**Skiko/tray binary compatibility (RECURRING — currently FIXED):**
- Issue: The system tray library is compiled against Skiko and breaks at desktop startup whenever a Compose Multiplatform bump moves Skiko across a signature change. It has bitten twice.
- Occurrence 1: commit `05f1318` forced skiko to 0.9.37.3 after a `NoSuchMethodError` at startup — `composenativetray:1.1.0` → `platformtools.darkmodedetector:0.7.5` → compose 1.9.0 → skiko 0.9.22.2 (built with Kotlin 1.9.21), which called `kotlin.io.path.PathsKt.createParentDirectories`; in Kotlin 2.x that static moved out of the multifile-class facade to `PathsKt__PathUtilsKt`. That force has since been removed.
- Occurrence 2: Compose 1.12.0 moved Skiko 0.144.6 → 0.150.1, which added a parameter to `Image.encodeToData`. `io.github.kdroidfilter:composenativetray:1.3.3` was compiled against the two-argument form and calls it while rendering the tray icon, so the desktop app died on launch with `NoSuchMethodError`.
- Files: `composeApp/src/desktopMain/kotlin/main.kt`, `gradle/libs.versions.toml`
- Impact: Desktop-only, but fatal at startup — the tray is composed during `main`, so the app never reaches a window.
- Fix approach: The library moved from `io.github.kdroidfilter` (abandoned at 1.3.3) to `dev.nucleusframework:composenativetray`, now on 2.1.7 and built against Skiko 0.150.x. Remember the groupId moved — the old coordinates still resolve and look up to date.
- Why this should stop recurring: 2.1.1 wrapped the call in a `NoSuchMethodError` catch that retries the legacy two-argument overload by reflection (`ComposableIconUtils.encodeToPngBytes`), so the tray now tolerates a Skiko signature change in *either* direction rather than dying at startup. Still worth a desktop smoke-launch after a Compose bump, but this is no longer expected to be fatal.

**Pre-release dependency:**
- Compose WebView 1.0.0-beta-02 is the one pre-release version left in the production app.
- Impact: May receive breaking changes, limited stability guarantees.
- Fix approach: Move to a stable release once one ships; read its release notes on every bump.

## Concurrency & Race Conditions

**Sequential startup in MainScreenModel:**
- Files: `composeApp/src/commonMain/kotlin/com/karakept/app/ui/screens/main/MainScreenModel.kt` ("Coroutine B", around line 636)
- Details: startup is an explicit sequence — read the persisted default filter, wait for a server, load the first page with that filter, then observe filter and server changes and kick off the throttled auto-sync. It exists because earlier, independent coroutines raced and loaded the list twice.
- Impact: The ordering is load-bearing. A new startup step added outside the sequence can reintroduce a duplicate or wrong-filter load.
- Fix approach: Keep new startup work inside the sequence; cover it with a test asserting a single initial load.

**Emission/subscription timing in tests:**
- Files: `core/data/src/commonTest/kotlin/com/karakept/app/data/repository/BookmarkActionsRepositoryUnitTest.kt` (lines 131, 200)
- Details: Tests subscribe before `moveToList`/`removeFromList` emit, or the emission races ahead of the collector.
- Impact: A `SharedFlow` without replay or buffer drops events a late collector would want; the same can happen to a real UI collector that subscribes late.
- Fix approach: Check the `extraBufferCapacity`/replay of those flows against how the UI collects them, and use `turbine`-style collection in the tests rather than ordering by hand.

## Code Complexity & Maintainability

**Files over 1000 lines:**
- `feature/reader/src/commonMain/kotlin/com/karakept/app/ui/components/reader/HtmlBlockRenderer.kt` (1286) — block-level reader rendering
- `feature/reader/src/androidMain/kotlin/com/karakept/app/ui/components/reader/HtmlRenderer.android.kt` (1151) — Android WebView renderer
- `composeApp/src/commonMain/kotlin/com/karakept/app/ui/screens/main/MainScreenModel.kt` (1090) — pagination, filtering, selection, startup, sync triggers
- `composeApp/src/commonMain/kotlin/com/karakept/app/ui/screens/main/BookmarkListContent.kt` (1090) — the bookmark list, tiling, paging
- `composeApp/src/commonMain/kotlin/com/karakept/app/ui/screens/settings/LayoutEditorScreen.kt` (1056) — layout editor
- `core/data/src/commonMain/kotlin/com/karakept/app/data/repository/BookmarkRepository.kt` (1035) — bookmark CRUD and sync
- Impact: High cognitive load, hard review, larger bug surface.
- Fix approach: Extract helper composables to their own files; split `MainScreenModel` by concern (pagination, selection, sync); split `BookmarkRepository` along read/sync lines.

**Stale Voyager references:**
- Comments still describe Voyager behaviour after the move to Navigation 3: `MainScreenModel.kt` (~line 570), `LayoutEditorScreen.kt` (~line 159). (`AppNavigator.kt` and `NavTransitions.kt` mention it only as history.)
- Fix approach: Reword them against Nav3's per-entry `ViewModelStore`, or delete them if the behaviour they explain no longer applies.

**Hardcoded dispatcher:**
- `feature/reader/src/commonMain/kotlin/com/karakept/app/ui/components/reader/HtmlContent.kt` (~line 101) uses `Dispatchers.Default` directly, against the project rule to inject `AppDispatchers`.
- Impact: The HTML processing is invisible to `advanceUntilIdle()` in tests.

**Remaining `!!`:**
- `HtmlBlockRenderer.kt` line 118 (`ref.y!!`), read on the line after `ref.y` is assigned. Safe today; a local `val` would make it obviously so.

## Platform-Specific Fragility

**Linux tray icon and D-Bus:**
- Files: `composeApp/src/desktopMain/kotlin/main.kt` (~line 343)
- Details: On Linux the tray is skipped when `DBUS_SESSION_BUS_ADDRESS` is unset, so the app starts without one instead of failing. Flatpak needs the session-bus socket; the devcontainer needs D-Bus forwarded.
- Impact: Without D-Bus, Linux users get no tray icon and no dark-mode detection.
- Fix approach: Document the D-Bus requirement in the Linux packaging notes.

## Performance (unmeasured)

- **Large libraries:** the list is paged (`PAGE_SIZE`) and rendered by a `LazyColumn`, but nothing has measured scroll or filter changes at 1000+ bookmarks.
- **Long articles:** the reader composes every block eagerly — page-turn snapping depends on it (see "Hardware page-turn buttons" in `AGENTS.md`) — so a very long article costs its full layout on open. Scrolling one used to cost work per block per frame too (a ~20-minute article measured 20 ms median frames on a 120 Hz phone, all on the UI thread); that per-block work is gone (`ReaderScrollCostTest`). Still open: the progressive reveal in `NativeHtmlRenderer` re-measures the whole column every batch, and one lazy item per block would remove the open cost.
- Fix approach: Add a benchmark before optimising the list; re-measure long articles with `adb shell dumpsys gfxinfo <package>` before going further.

## Resolved since the 2026-03-20 audit

- `printStackTrace()` silencing and the debug `println` in `RemoteDataSource` — gone; errors go through `AppLogger`.
- 20× `!!` — down to the one listed above.
- Read/unread tag-cache race in `BookmarkActionsRepository` — the code documents why it no longer applies (~line 113).
- Voyager unique-key workaround for the viewer — Voyager replaced by Navigation 3.
- Credentials in cleartext — moved to the platform `SecureCredentialStore` (`core/data/.../data/secure/`).
- Offline action queue without tests — covered by `PendingActionQueueTest`, `PendingActionDedupTest`, `BookmarkActionsRepositorySyncTest`, `BookmarkSyncPipelineReconcileTest` and others.
- Incomplete FilterConfig crash comment — completed (`BookmarkFilterUtilsTest.kt` ~line 165: tags and lists set together).
- No client-side HTML sanitiser — `HtmlSanitizer` (Reader) and `HtmlArchiveProcessor` (Web) now exist; see the WebView entry for what the latter still misses.
- Reading-progress race in `BookmarkViewerScreenModel` — logic moved to `ViewerScrollRestoration.kt`, gated on `serverProgressChecked`.
- Compose DSL deprecations, ActionMode crash, double taskbar icon on Linux — fixed earlier, dropped from the list.
- Room / SQLite / Voyager pre-releases — all replaced by stable releases.

---

*Concerns audit: 2026-11-05*
