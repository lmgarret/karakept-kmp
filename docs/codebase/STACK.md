# Technology Stack

**Analysis Date:** 2026-08-04

## Languages

**Primary:**
- Kotlin 2.4.0 - Core application logic, multiplatform targets (Android, JVM Desktop)
- JavaScript/TypeScript - Upstream monorepo (Node.js services and tooling)

**Secondary:**
- XML - Android manifest and resource definitions
- JSON - Configuration and OpenAPI specifications

## Runtime

**Environment:**
- JVM 11 (both Android and Desktop targets compile to JVM 11 bytecode)
- Android Runtime (ART) - Android 7.0+ (API 24)
- Java Desktop (JVM via Gradle Compose Desktop)

**Package Manager:**
- Gradle 9.x with Kotlin DSL
- Gradle Wrapper (local versioning)
- npm/pnpm 9.15.9 (for upstream monorepo)

## Frameworks

**Core UI:**
- Jetbrains Compose Multiplatform 1.11.1 - Cross-platform UI framework
- Material Design 3 (androidx.compose.material3) - Material components and theme system
- Compose Material 1.x (androidx.compose.material) - Base Material components

**Navigation:**
- Compose Navigation 3 (1.1.1) - developer-owned back stack of `@Serializable` `NavKey`s rendered by `NavDisplay`; supports Android, desktop, iOS, web
  - `androidx.navigation3:navigation3-runtime` (1.1.1): `NavKey`, `NavBackStack`, `rememberNavBackStack`, `entryProvider`
  - `org.jetbrains.androidx.navigation3:navigation3-ui` (1.1.1): `NavDisplay` (CMP build) + transition/predictive-back specs
  - `org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-navigation3` (2.10.0): per-entry `ViewModelStore` scoping
  - `io.insert-koin:koin-compose-navigation3` + `koin-compose-viewmodel` (4.2.2): `koinViewModel` injection per nav entry
  - App wiring lives in `ui/navigation/` (`AppNavigator`, `LocalNavigator`, `appEntryProvider`, `navKeySerializersModule`, Shared Axis Z specs)

**Networking:**
- Ktor Client 3.5.1 - HTTP client with multiplatform support
  - ktor-client-core: Core HTTP client
  - ktor-client-okhttp: OkHttp engine for JVM (Android + Desktop)
  - ktor-client-content-negotiation: Content type negotiation
  - ktor-serialization-kotlinx-json: JSON serialization plugin
  - ktor-client-auth: Authentication plugin
  - ktor-client-logging: Request/response logging

**Database:**
- Room 2.8.4 - Local SQLite ORM with KMP support
  - androidx-room-runtime: Runtime database support
  - androidx-room-compiler: Code generation via KSP
- SQLite 2.6.2 (bundled) - Embedded database
  - androidx-sqlite-bundled: Bundled SQLite driver for consistent behavior

**State Management:**
- Koin 4.2.2 - Dependency injection
  - koin-core: Core DI container
  - koin-compose: Compose integration
  - koin-android: Android-specific support
  - koin-androidx-workmanager: WorkManager task scheduling

**Serialization:**
- kotlinx-serialization 1.11.0 - Multiplatform serialization
  - kotlinx-serialization-json: JSON codec
- kotlinx-datetime 0.8.0 - Multiplatform date/time handling

**Image Loading:**
- Coil 3.5.0 - Image loading and caching
  - coil-compose: Compose integration with Image() composable
  - coil-network-ktor: Ktor HTTP client engine for image loading

**Local Preferences:**
- DataStore 1.2.1 - Type-safe key-value store (successor to SharedPreferences)
  - androidx-datastore-preferences-core: Core library
  - androidx-datastore-preferences-android: Android-specific implementation

**Async Programming:**
- kotlinx-coroutines 1.11.0 - Async and concurrency primitives
  - kotlinx-coroutines-core: Core coroutine runtime
  - kotlinx-coroutines-android: Android dispatcher integration
  - kotlinx-coroutines-swing: Swing dispatcher for desktop UI thread
  - kotlinx-coroutines-test: Testing utilities

**HTML Parsing:**
- ksoup 0.2.6 - HTML/XML parsing library (KMP-compatible)
- Used for metadata extraction from bookmarked web content

**Notifications:**
- KNotify 0.4.3 - Multiplatform local notifications
  - Android: Native notifications via NotificationManager
  - Desktop: System notifications

**Desktop-Specific:**
- Compose Native Tray 1.3.3 - System tray integration
  - macOS: Native NSStatusBar
  - Windows: Native taskbar
  - Linux: D-Bus interface (via DBus-x11)
- Native File Dialog 1.0.3 - File picker dialogs
  - Linux: GTK file chooser
  - macOS: NSOpenPanel
  - Windows: IFileOpenDialog

**WebView:**
- Compose WebView 1.0.0-beta-02 - Web content rendering
  - macOS: WKWebView
  - Windows: WebView2
  - Linux: WebKitGTK

**Android Integration:**
- androidx-activity-compose 1.12.4 - Activity + Compose integration
- androidx-browser 1.9.0 - Custom Chrome tabs for opening web content
- androidx-webkit 1.16.0 - WebSettingsCompat/WebViewFeature (algorithmic darkening control for Web mode archive rendering)
- androidx-core-splashscreen 1.2.0 - Splash screen API
- androidx-work-runtime-ktx 2.10.5 - Background task scheduling

## Build & Code Generation

**Build System:**
- Gradle 9.5.1 (wrapper) with AGP 8.13.0 (Android Gradle Plugin); requires JDK 17+ to run the build

**Code Generation:**
- KSP 2.3.9 (Kotlin Symbol Processing) - Annotation processor
- OpenAPI Generator 7.24.0 - Generate API client from OpenAPI spec
  - Generates Kotlin multiplatform client to `com.karakept.api.*`
  - Located at: `api-client/` module
  - Source spec: `/karakeep-upstream/packages/open-api/karakeep-openapi-spec.json`

**Kotlin Compiler Plugin:**
- compose-compiler (bundled with kotlin-plugin-compose) - Compose IR compiler
- org.jetbrains.compose.hot-reload (bundled with CMP 1.11.1) - Compose Hot Reload for desktop dev workflow; adds `hotRunDesktop` Gradle task (requires JBR 21, auto-provisioned via foojay toolchain resolver)

## Key Dependencies

**Critical Infrastructure:**
- Room ORM - Single source of truth for local data
- Ktor HttpClient - All remote API communication
- Koin - Application dependency graph and lifecycle management
- kotlinx-coroutines - Structured concurrency for all async operations

**Serialization Pipeline:**
- OpenAPI Generator → Kotlin models (api-client module)
- kotlinx-serialization JSON codec ↔ API models
- Room Entity mappers ↔ Database models

## Testing & Development

**Testing:**
- JUnit 4.13.2 - Unit test framework
- mockk 1.14.11 - Kotlin mocking library
- kotlinx-coroutines-test - Coroutine testing utilities

**Kotlin Test Framework:**
- kotlin-test - Standard library test assertions

## Configuration

**Environment:**
- `.devcontainer/devcontainer.json` - VS Code devcontainer for development
  - Java 21 base image (bookworm)
  - Android SDK 36 with NDK 26.1
  - Node.js LTS
  - Desktop build dependencies (Ninja, GTK, WebKit)
  - D-Bus for system tray on Linux
  - Includes build/bundle script at `setup.sh`

**Build Properties:**
- `gradle.properties`:
  - JVM args: `-Xmx2048m` max heap
  - Kotlin code style: official
  - Android/Compose settings for Gradle plugin behavior
  - File encoding: UTF-8

**Android Configuration:**
- Min SDK: 24 (Android 7.0)
- Target SDK: 35 (Android 14)
- Compile SDK: 36 (Android 15)
- Application ID: `com.karakept.app`
- Build variants: debug, release, devRelease (staging)

**Desktop Application:**
- Runs on JVM via Compose Desktop
- macOS, Windows, Linux support
- Native menu bar integration via compose-native-tray
- File picker via GTK/native dialogs

**CI/CD:**
- GitHub Actions workflow (`.github/workflows/`)
  - `release.yml` - Version resolution, build, and release
  - `pr-build.yml` - Pull request validation
  - `ci.yml` - Continuous integration
  - `cache-warm.yml` - Seeds the Gradle cache from the default branch
  - `gradle/actions/setup-gradle@v6` (MIT) - Gradle setup + caching in CI. Pinned to
    `cache-provider: basic`, the open-source (MIT) cache provider built on `actions/cache`.
    The v6 default `enhanced` provider relies on the proprietary `gradle-actions-caching`
    component governed by Gradle's commercial Terms of Use and is deliberately not used.
  - Uses LLM for changelog generation (mistral/mistral-large-latest)
  - Signing configured via environment variables (KEYSTORE_PATH, KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD)

## Platform Requirements

**Development:**
- JDK 11+ (devcontainer ships JDK 21)
- Android SDK 36
- Android NDK 26.1+ (for native libraries if needed)
- Node.js LTS (for upstream monorepo tooling)
- GTK development libraries (Linux desktop development)
- WebKit development libraries (Linux desktop development)
- D-Bus (for system tray on Linux desktop)

**Android Runtime:**
- Android 7.0+ (API 24+)
- Minimum 2GB RAM recommended
- Vibration permission required
- POST_NOTIFICATIONS permission (Android 13+)

**Desktop Runtime:**
- JVM 8+ compatible
- Linux: GTK 3+, WebKit2GTK 4.1+, D-Bus
- macOS: 10.13+, native runtime environment
- Windows: WebView2 runtime

---

*Stack analysis: 2026-05-30*
