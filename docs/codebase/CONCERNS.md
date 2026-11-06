# Codebase Concerns

**Analysis Date:** 2026-11-05 (rewrite of the 2026-03-20 audit; resolved entries are summarised at the end)

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

## Code Complexity & Maintainability

**Next-largest files (800+ lines):** `BookmarkViewerScreenModel.kt` (867), `OnboardingScreen.kt` (829), `MainScreenModel.kt` (824), `HtmlBlockRenderer.kt` (821). None is urgent; split by the same approach when one grows or is next touched.
- Fix approach: break a single long composable into helper composables of its own file, pull pure logic out where a unit test can reach it, give a ScreenModel's methods extension files by concern (`MainScreenModelSync.kt`, `…Filters.kt`, …) while its state and startup sequence stay in the class, and give a repository internal collaborators it builds itself (`BookmarkSyncRunner`, `BookmarkContentSync`, …) behind unchanged public methods.

## Platform-Specific Fragility

**Linux tray icon and D-Bus:**
- Files: `composeApp/src/desktopMain/kotlin/main.kt` (~lines 344 and 508)
- Details: On Linux the tray is skipped when `DBUS_SESSION_BUS_ADDRESS` is unset, so the app starts without one instead of failing. Flatpak needs the session-bus socket; the devcontainer needs D-Bus forwarded.
- Impact: Without D-Bus, Linux users get no tray icon, and closing the window quits the app instead of hiding it. (`isMenuBarInDarkMode()` only tints the tray icon, so losing it costs nothing else.)
- Unverified risk: the check looks for a session bus, not a tray host. On GNOME without the AppIndicator extension there is a bus but no StatusNotifierItem watcher; unless the library reports that, `trayAvailable` is true and closing the window hides the app with no tray to restore it from.
- Status: the requirement is documented in `INTEGRATIONS.md` (Linux tray) and `STACK.md` (desktop requirements). Still missing: packaging notes for Flatpak (the session-bus socket permission).

## Performance (unmeasured)

- **Large libraries:** the list is paged (`PAGE_SIZE`) and rendered by a `LazyColumn`, but nothing has measured scroll or filter changes at 1000+ bookmarks.
- **Long articles:** the reader still composes and lays out every block, and draws the whole article once when it appears — page-turn snapping depends on every block being laid out (see "Hardware page-turn buttons" in `AGENTS.md`). On an 88-minute read that single draw is ~90–120 ms (Pixel 8). Drawing only the on-screen part is #463.
- Fix approach: Add a benchmark before optimising the list.

## Resolved since the 2026-03-20 audit

- `printStackTrace()` silencing and the debug `println` in `RemoteDataSource` — gone; errors go through `AppLogger`.
- 20× `!!` — all gone.
- Read/unread tag-cache race in `BookmarkActionsRepository` — the code documents why it no longer applies (~line 113).
- Voyager unique-key workaround for the viewer — Voyager replaced by Navigation 3.
- Credentials in cleartext — moved to the platform `SecureCredentialStore` (`core/data/.../data/secure/`).
- Offline action queue without tests — covered by `PendingActionQueueTest`, `PendingActionDedupTest`, `BookmarkActionsRepositorySyncTest`, `BookmarkSyncPipelineReconcileTest` and others.
- Incomplete FilterConfig crash comment — completed (`BookmarkFilterUtilsTest.kt` ~line 165: tags and lists set together).
- No client-side HTML sanitiser — `HtmlSanitizer` (Reader) and `HtmlArchiveProcessor` (Web) now exist.
- Cached Web archives served unsanitised, fixed-list event handler stripping, CSP after `<head>` scripts with `unsafe-inline` — cached archives go through `processForArchive`, every `on*` attribute is stripped, and the CSP is injected first with a script nonce.
- Reading-progress race in `BookmarkViewerScreenModel` — logic moved to `ViewerScrollRestoration.kt`, gated on `serverProgressChecked`.
- Compose DSL deprecations, ActionMode crash, double taskbar icon on Linux — fixed earlier, dropped from the list.
- Room / SQLite / Voyager pre-releases — all replaced by stable releases.
- Six files over 1000 lines (2026-11-05) — split: `BookmarkListContent` (row, drag selection, overlays and `ItemAnimationGate` in their own files; takes `MainScreenDisplayConfig` instead of 24 parameters), `LayoutEditorScreen` (ScreenModel, sections, controls and preview in their own files), `MainScreenModel` (sync and filter methods in extension files), `HtmlBlockRenderer` (images and tables in `HtmlImageRenderer.kt` / `HtmlTableRenderer.kt`), `HtmlRenderer.android.kt` (the WebView highlight script in `ReaderHighlightScript.kt`), `BookmarkRepository` (asset downloads in `BookmarkAssetCache`).
- Stale Voyager comments in `MainScreenModel` and `LayoutEditorScreenModel` — reworded against Nav3's per-entry `ViewModelStore`.
- Hardcoded `Dispatchers.Default` in `HtmlContent.kt` — uses the injected `AppDispatchers`.
- Opening a long article froze the UI (#457) — an 88-minute read was composed, measured and drawn in one 482 ms frame. It is now prepared off the main thread, built a frame's budget at a time behind the loader and drawn once (longest frame ~120 ms); see "Preparing an article" in `AGENTS.md`. Scrolling one no longer costs work per block per frame either (`ReaderScrollCostTest`).
- Last `!!` in `HtmlBlockRenderer.kt` — replaced by a local.
- `BookmarkSyncPipelineTest.kt` (1890 lines) — split by concern into smaller suites.

---

*Concerns audit: 2026-11-05 (maintainability items resolved the same day; re-checked 2026-11-06)*
- Five files near 1000 lines — split on 2026-11-06: `BookmarkViewerContent.kt` (990 → 444), `BookmarkDetailsPanel.kt` (984 → 282), `BookmarkLayouts.kt` (918, now six files), `BookmarkRepository.kt` (902 → 437), `RemoteDataSource.kt` (892 → 143-line facade over `RemoteApiContext` extensions, so MockK stubs of its members keep working).
