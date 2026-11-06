# Codebase Concerns

**Analysis Date:** 2026-11-05 (rewrite of the 2026-03-20 audit; resolved entries are summarised at the end)

## Security

**Android WebView (Web mode) runs archived pages with JavaScript and a native bridge:**
- Files: `feature/reader/src/androidMain/kotlin/com/karakept/app/ui/components/reader/HtmlRenderer.android.kt` (WebView settings around line 595), `core/data/src/commonMain/kotlin/com/karakept/app/data/cache/HtmlArchiveProcessor.kt`
- Current settings:
  - `javaScriptEnabled = true`, with `addJavascriptInterface(webInterface, "Android")` exposing a bridge to the page (the highlight JS needs it)
  - `allowFileAccess = true` (cached local images are loaded from `file://`)
  - `mixedContentMode = MIXED_CONTENT_ALWAYS_ALLOW` (HTTP images from a `file://` context)
  - `allowContentAccess = false`; link clicks intercepted by the `WebViewClient`
- Current mitigations (inline HTML only — see below for cached archives): `processForArchive` removes `<script>`, `iframe`/`object`/`embed`/`applet`, `<form>`, and validates `href`/`src` URLs.
- **Cached full-page archives are not sanitised at all.** When the archive is on disk, `HtmlContent.kt` skips processing and the `WebViewAssetLoader` path handler (`HtmlRenderer.android.kt` ~line 361) serves the raw file with only `injectArchiveScripts` applied. Its `<script>`s, handlers and URLs all survive, with the `Android` bridge available. The only barrier is the injected CSP (~line 135), and it is weak: `script-src 'unsafe-inline'` lets inline scripts and handlers run; the `<meta>` is inserted before `</head>`, so scripts earlier in `<head>` run before it takes effect; and `img-src http: https:` leaves an exfiltration channel even though `default-src 'none'` blocks `fetch`.
- Risk (inline path, `processForArchive`): inline event handlers are stripped from a **fixed list** of 25 names. Anything else (`onanimationstart`, `onpointerover`, `ontoggle`, `onfocusin`, SVG `onbegin`, …) survives, and with JavaScript on it runs with access to the `Android` bridge — `onHighlightClick`, `onTextSelected`, `onSelectionChanged`, `onLog`. None reads app data, but a page could fake a selection and drive the highlight-creation UI. `<meta http-equiv="refresh">`, `<base>` and `<link>` are not removed either.
- Fix approach: run cached archives through `processForArchive` too (in the path handler, before injection); inject the CSP as the first child of `<head>` and drop `'unsafe-inline'` in favour of a nonce for the app's own script; strip every attribute whose name starts with `on`, remove `meta[http-equiv]`/`base`, and keep the bridge's surface as small as it is now (no method that reads app data or acts without the user). Consider injecting the app's own JS via `evaluateJavascript` and leaving page JS off if the bridge can do without it.

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

## Concurrency & Race Conditions

**Sequential startup in MainScreenModel:**
- Files: `composeApp/src/commonMain/kotlin/com/karakept/app/ui/screens/main/MainScreenModel.kt` ("Coroutine B" in `init`)
- Details: startup is an explicit sequence — read the persisted default filter, wait for a server, load the first page with that filter, then observe filter and server changes and kick off the throttled auto-sync. It exists because earlier, independent coroutines raced and loaded the list twice.
- Impact: The ordering is load-bearing. A new startup step added outside the sequence can reintroduce a duplicate or wrong-filter load.
- Fix approach: Keep new startup work inside the sequence. `MainScreenModelStartupTest` asserts a single view switch and that the first page is read with the default filter.

**Bookmark change events have no production collector:**
- Files: `core/data/src/commonMain/kotlin/com/karakept/app/data/repository/BookmarkActionsRepository.kt` (`bookmarkChangedEvents`, `bookmarksReloaded`)
- Details: both flows are emitted from about 20 places (list moves, AI actions, reading-progress passes), but only tests collect them; no ScreenModel subscribes, and none did before the `:core:data` split either.
- Impact: dead code at best. At worst, a screen that does not otherwise observe Room misses those updates — not yet checked whether the list refreshes after an AI job or a list move by some other route.
- Fix approach: either collect them in `MainScreenModel` (and then the tests' hand-ordered subscription, `BookmarkActionsRepositoryUnitTest` ~lines 127 and 197, is worth replacing with Turbine — the flows have no replay, so a late collector misses events) or delete the flows and their emissions.

## Code Complexity & Maintainability

**Files approaching 1000 lines:**
- `feature/reader/.../screens/viewer/BookmarkViewerContent.kt` (987) and `BookmarkDetailsPanel.kt` (984)
- `core/ui/.../components/bookmark/BookmarkLayouts.kt` (918)
- `core/data/.../data/repository/BookmarkRepository.kt` (904) — grew back past 900 after `BookmarkAssetCache` was extracted
- `core/data/.../data/remote/RemoteDataSource.kt` (892)
- Impact: High cognitive load, hard review, larger bug surface.
- Fix approach: what worked for the files split on 2026-11-05 — break a single long composable into helper composables of its own file, pull pure logic out where a unit test can reach it, and give a ScreenModel's methods extension files by concern (`MainScreenModelSync.kt`, `…Filters.kt`, `…Actions.kt`, `…Batch.kt`) while its state and startup sequence stay in the class.

## Platform-Specific Fragility

**Linux tray icon and D-Bus:**
- Files: `composeApp/src/desktopMain/kotlin/main.kt` (~lines 344 and 508)
- Details: On Linux the tray is skipped when `DBUS_SESSION_BUS_ADDRESS` is unset, so the app starts without one instead of failing. Flatpak needs the session-bus socket; the devcontainer needs D-Bus forwarded.
- Impact: Without D-Bus, Linux users get no tray icon, and closing the window quits the app instead of hiding it. (`isMenuBarInDarkMode()` only tints the tray icon, so losing it costs nothing else.)
- Unverified risk: the check looks for a session bus, not a tray host. On GNOME without the AppIndicator extension there is a bus but no StatusNotifierItem watcher; unless the library reports that, `trayAvailable` is true and closing the window hides the app with no tray to restore it from.
- Status: the requirement is documented in `INTEGRATIONS.md` (Linux tray) and `STACK.md` (desktop requirements). Still missing: packaging notes for Flatpak (the session-bus socket permission).

## Performance (unmeasured)

- **Large libraries:** the list is paged (`PAGE_SIZE`) and rendered by a `LazyColumn`, but nothing has measured scroll or filter changes at 1000+ bookmarks.
- **Long articles:** the reader composes every block eagerly — page-turn snapping depends on it (see "Hardware page-turn buttons" in `AGENTS.md`) — so a very long article costs its full layout on open. Scrolling one used to cost work per block per frame too (a ~20-minute article measured 20 ms median frames on a 120 Hz phone, all on the UI thread); that per-block work is gone (`ReaderScrollCostTest`). Still open: the progressive reveal in `NativeHtmlRenderer` (~line 209: 20 blocks, then 10 more every 16 ms) re-measures the whole column every batch, and one lazy item per block would remove the open cost.
- Fix approach: Add a benchmark before optimising the list; re-measure long articles with `adb shell dumpsys gfxinfo <package>` before going further.

## Resolved since the 2026-03-20 audit

- `printStackTrace()` silencing and the debug `println` in `RemoteDataSource` — gone; errors go through `AppLogger`.
- 20× `!!` — all gone.
- Read/unread tag-cache race in `BookmarkActionsRepository` — the code documents why it no longer applies (~line 113).
- Voyager unique-key workaround for the viewer — Voyager replaced by Navigation 3.
- Credentials in cleartext — moved to the platform `SecureCredentialStore` (`core/data/.../data/secure/`).
- Offline action queue without tests — covered by `PendingActionQueueTest`, `PendingActionDedupTest`, `BookmarkActionsRepositorySyncTest`, `BookmarkSyncPipelineReconcileTest` and others.
- Incomplete FilterConfig crash comment — completed (`BookmarkFilterUtilsTest.kt` ~line 165: tags and lists set together).
- No client-side HTML sanitiser — `HtmlSanitizer` (Reader) and `HtmlArchiveProcessor` (Web) now exist; see the WebView entry for what the latter still misses.
- Reading-progress race in `BookmarkViewerScreenModel` — logic moved to `ViewerScrollRestoration.kt`, gated on `serverProgressChecked`.
- Compose DSL deprecations, ActionMode crash, double taskbar icon on Linux — fixed earlier, dropped from the list.
- Room / SQLite / Voyager pre-releases — all replaced by stable releases.
- Six files over 1000 lines (2026-11-05) — split: `BookmarkListContent` (row, drag selection, overlays and `ItemAnimationGate` in their own files; takes `MainScreenDisplayConfig` instead of 24 parameters), `LayoutEditorScreen` (ScreenModel, sections, controls and preview in their own files), `MainScreenModel` (sync and filter methods in extension files), `HtmlBlockRenderer` (images and tables in `HtmlImageRenderer.kt` / `HtmlTableRenderer.kt`), `HtmlRenderer.android.kt` (the WebView highlight script in `ReaderHighlightScript.kt`), `BookmarkRepository` (asset downloads in `BookmarkAssetCache`).
- Stale Voyager comments in `MainScreenModel` and `LayoutEditorScreenModel` — reworded against Nav3's per-entry `ViewModelStore`.
- Hardcoded `Dispatchers.Default` in `HtmlContent.kt` — uses the injected `AppDispatchers`.
- Last `!!` in `HtmlBlockRenderer.kt` — replaced by a local.
- `BookmarkSyncPipelineTest.kt` (1890 lines) — split by concern into smaller suites.

---

*Concerns audit: 2026-11-05 (maintainability items resolved the same day; re-checked 2026-11-06)*
