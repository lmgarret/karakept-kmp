# Technology Stack

What each dependency is for, and the decisions behind the ones that need explaining.

**Versions live in [`gradle/libs.versions.toml`](../../gradle/libs.versions.toml)** (libraries and
Gradle plugins) and [`mise.toml`](../../mise.toml) (JDK, Gradle, Python). This file deliberately
repeats none of them — a copy here only drifts. Android SDK levels (`android-compileSdk`,
`android-minSdk`, `android-targetSdk`) are in the version catalog too.

## Languages and targets

- **Kotlin Multiplatform**, targeting Android and JVM desktop (Linux, macOS, Windows). All
  modules compile to JVM 11 bytecode.
- The `karakeep-upstream` submodule is the Karakeep TypeScript monorepo; the build only reads its
  OpenAPI spec from it.

## Modules

`:core:model` → `:core:data` → `:core:ui` → `:feature:reader` → `:composeApp`, each a
`com.android.kotlin.multiplatform.library` + `jvm("desktop")`. `:core:testing` holds shared test
fixtures; `:androidApp` is the Android application shell (build types, signing, applicationId —
no sources). Room's KSP runs in `:core:data` only (schemas in `core/data/schemas/`); Compose
resources live in `:composeApp` (app icon, preview drawable) and `:core:ui` (reader fonts). Why it
is split this way is in `AGENTS.md` → "Gradle Modules".

## UI

- **Compose Multiplatform** with **Material 3** (`compose-material3`). `compose-material` (M2) is
  still declared in `:composeApp`, though no source file imports from it directly.
- **Material icons are vendored, not a dependency.** `material-icons-extended` is frozen upstream,
  so the icons the app draws live in `ui/icons/AppIcons.kt`, generated from
  google/material-design-icons (Apache 2.0) by `tools/generate_material_icons.py`.
- **Compose Hot Reload** (bundled with CMP) adds `hotRunDesktop`; it needs JBR 21, provisioned
  through the foojay toolchain resolver.

## Navigation

- **Navigation 3**: a developer-owned back stack of `@Serializable` `NavKey`s rendered by
  `NavDisplay`.
  - `androidx.navigation3:navigation3-runtime` — `NavKey`, `NavBackStack`, `entryProvider`.
  - `org.jetbrains.androidx.navigation3:navigation3-ui` — the CMP build of `NavDisplay`, plus
    transition and predictive-back specs. It pulls a matching runtime transitively; keep the two
    catalog entries on the same version so the pin means what it says.
  - `lifecycle-viewmodel-navigation3` — per-entry `ViewModelStore` scoping.
  - `koin-compose-navigation3` + `koin-compose-viewmodel` — `koinViewModel` per entry.
- App wiring is in `ui/navigation/` (`AppNavigator`, `appEntryProvider`, `navKeySerializersModule`,
  Shared Axis Z specs).

## Data

- **Ktor client** (OkHttp engine on both platforms) with content negotiation, kotlinx JSON, auth
  and logging plugins. `ktor-client-mock` in tests. `RemoteDataSource` is the only consumer of the
  generated API client.
- **Room 3** (`androidx.room3`) over **`sqlite-bundled`**, so both platforms run the same SQLite
  rather than the OS's. History of the move from Room 2: `room3-migration.md`.
- **DataStore (preferences)** for settings — see "Adding a New Setting" in `AGENTS.md`.
- **kotlinx-serialization** (JSON), **kotlinx-datetime**, **kotlinx-coroutines** (`-android` for
  the main dispatcher, `-swing` for the desktop UI thread).
- **Koin** for DI, with `koin-androidx-workmanager` so workers are built from the graph.
- **ksoup** parses article HTML for the native reader and for sanitising (`HtmlSanitizer`,
  `HtmlArchiveProcessor`).
- **Coil 3** (`coil-network-ktor`, so images share the Ktor engine).

## Platform integration

**Android**
- `activity-compose`, `core-splashscreen`.
- `androidx-browser` — Custom Tabs for opening links.
- `androidx-webkit` — `WebSettingsCompat`/`WebViewFeature`, for algorithmic-darkening control in
  Web mode.
- `androidx-core` — `FileProvider` for sharing exported files; declared in `:core:data` rather than
  relied on transitively through activity-compose.
- `work-runtime` — background sync (`BackgroundSyncWorker`) and saving shared links
  (`SaveBookmarkWorker`).

**Desktop**
- **Compose Native Tray** (`dev.nucleusframework`). The groupId moved from `io.github.kdroidfilter`,
  whose last release is incompatible with current Skiko and whose coordinates still resolve — see
  `CONCERNS.md`. Linux needs a D-Bus session for the tray; there is no AWT fallback.
- **Native File Dialog** — GTK chooser / NSOpenPanel / IFileOpenDialog.
- **KNotify** — local notifications on both platforms.

## Build and code generation

- **AGP** with the KMP library plugin; `:androidApp` applies `com.android.application`. The build
  runs on JDK 17+, and CI and `mise` use 21.
- **KSP** — Room only.
- **OpenAPI Generator** — builds `api-client/` (`com.karakept.api.*`) from
  `karakeep-upstream/packages/open-api/karakeep-openapi-spec.json`. Generated code is committed;
  never hand-edit it.
- **`:composeApp:generateAppVersion`** writes `AppVersion.kt` (`name`, `commit`, `dirty`) into a
  generated `commonMain` source dir. It reads `-PversionName` / `-Pcommit` first, then git, and
  never fails without a repository.

## Testing

- `kotlin-test`, JUnit 4, **MockK**, `kotlinx-coroutines-test`.
- `compose.desktop.uiTestJUnit4` for headless layout and semantics checks in `desktopTest`;
  Android UI tests use `ui-test-junit4` under **Robolectric**.
- Patterns and locations: `TESTING.md` and `AGENTS.md` → "Testing Requirements".

## CI

- GitHub Actions: `ci.yml` (desktop suite on PRs), `pr-build.yml`, `release.yml` (build, sign,
  release; the Android suite runs here), `cache-warm.yml` (seeds the Gradle cache from the default
  branch, since setup-gradle only saves its cache there).
- **`gradle/actions/setup-gradle` uses `cache-provider: basic`** — the MIT provider built on
  `actions/cache`. The default `enhanced` provider depends on the proprietary
  `gradle-actions-caching` component under Gradle's commercial terms and is deliberately not used.
- Release notes come from `lmgarret/llm-release-action`, fed by `docs/CONTEXT.md`.
- Signing reads `KEYSTORE_PATH`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.

## Development environment

- `mise run setup` installs the pinned toolchain and the submodule.
- Android SDK for Android builds and the Robolectric suite.
- Linux desktop development: GTK development libraries, and a D-Bus session for the tray.
- `.devcontainer/` provides all of the above (with `setup.sh` to build and bundle).

## Runtime requirements

- **Android:** API level from `android-minSdk`; `POST_NOTIFICATIONS` on Android 13+.
- **Desktop:** JVM 11+. Linux: GTK 3, D-Bus.
