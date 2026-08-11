# External Integrations

**Analysis Date:** 2026-03-20

## APIs & External Services

**Karakept Backend API:**
- Service: Karakept API (OpenAPI spec)
- Base URL: `https://try.karakept.app/api/v1` (default in `AppModule.kt`)
- What it's used for: Bookmark CRUD, list management, tag operations, highlight annotations, user operations
- SDK/Client: OpenAPI-generated Kotlin client
  - Generated module: `api-client/`
  - Source spec: `/karakeep-upstream/packages/open-api/karakeep-openapi-spec.json`
  - Generated to: `com.karakept.api.client.*` and `com.karakept.api.model.*`
  - Engine: Ktor HttpClient (multiplatform)
  - Auth: Configured via `ApiClient` and `RemoteDataSource` in DI

**API Endpoints (from OpenAPI generation):**
- `BookmarksApi` - Create, read, update, delete bookmarks
  - Generated at: `api-client/build/generated/openapi/src/main/kotlin/com/karakept/api/client/BookmarksApi.kt`
- `ListsApi` - Manage bookmark lists (collections)
- `TagsApi` - Tag management and suggestions
- `HighlightsApi` - Text highlight annotations on bookmarks
- `UsersApi` - User profile and auth operations

**tRPC endpoints (not in the OpenAPI spec):**

A few Karakeep operations are only reachable through the web app's internal tRPC API, so
`RemoteDataSource` posts to them with raw Ktor instead of the generated client. Request
bodies use the batch envelope `{"0":{"json":{…}}}`, built by
`com/karakept/app/utils/TrpcPayloadUtils.kt`.

| Route | Used for |
|---|---|
| `bookmarks.updateReadingProgress` | Push reading progress to the server |
| `bookmarks.getReadingProgress` | Pull reading progress from the server |
| `bookmarks.recrawlBookmark` | Refresh / preserve full page archive / preserve PDF |

`recrawlBookmark` takes `{ bookmarkId, archiveFullPage, storePdf }` and enqueues a background
job — a successful response only means the request was accepted, so callers re-sync the
bookmark afterwards to pick up the result.

Reading progress lives in its own server-side table with **no batch endpoint** — one call per
bookmark in each direction:

- **Push** rides the pending-action queue (`UPDATE_READING_PROGRESS`), so a rejected push is
  retried or parked as a failed action rather than dropped. The single exception is
  `BAD_REQUEST: reading progress can only be saved for link bookmarks`, which no retry can
  fix: `updateReadingProgress` returns `false` and the action is discarded.
- **Pull** happens when a bookmark is opened, and as sync phase 6 for a rotating batch of 50
  bookmarks (`getReadingProgressPullCandidates`, ordered by `progressSyncedAt`). Every sync
  flavour is eligible — browsing by list must not starve the pull — but
  `BookmarkRepository.tryAcquireReadingProgressPull` rations passes so a fan-out of list syncs
  cannot multiply the per-bookmark calls by the number of lists. Only a pull the server
  actually answered advances the cursor.

> These are internal APIs with no compatibility guarantee across Karakeep versions. Every
> call site must degrade to a user-visible error and leave local state untouched. A 404 whose
> body says "No procedure found" is surfaced as `UnsupportedServerActionException` so the UI
> can say the server doesn't support the action rather than offering a pointless retry.

**HTTP Client Configuration:**
- Library: `io.ktor:ktor-client-core` v3.3.2
- Engine: `io.ktor:ktor-client-okhttp` (JVM/Android + Desktop)
- Serialization: kotlinx-serialization JSON
- Logging: Ktor Logger.SIMPLE at INFO level
- Default headers: `Content-Type: application/json`
- JSON configuration:
  - `ignoreUnknownKeys = true` - Ignore unmapped fields
  - `explicitNulls = false` - Don't send explicit null values
  - `encodeDefaults = true` - Serialize fields with default values
  - Code: `com/karakept/app/data/remote/KtorClient.kt`

## Data Storage

**Databases:**

**SQLite (Local):**
- Type: SQLite 3 (via androidx.sqlite:sqlite-bundled)
- Client: Room ORM (androidx.room 2.7.0-alpha11)
- Database: `AppDatabase` at `com/karakept/app/data/local/AppDatabase.kt`
- Current schema version: 8 (migrations in `data/local/migrations/`)
- Entities:
  - `ServerEntity` - Connected Karakept server configuration
  - `BookmarkEntity` - Bookmarks (metadata and sync state)
  - `AssetEntity` - Downloaded asset files (thumbnails, content)
  - `ListEntity` - Bookmark collections
  - `HighlightEntity` - Text highlights on bookmarks
  - `PendingActionEntity` - Offline-first action queue for sync
- Location: Platform-specific
  - Android: `/data/data/com.karakept.app/databases/`
  - Desktop: User config directory (platform-dependent)
- Schema directory: `composeApp/schemas/` (for version tracking)

**Preferences (Key-Value Store):**
- Type: DataStore (androidx.datastore:datastore-preferences-core)
- Purpose: User settings, preferences, app configuration
- Implementation: `DataStore<Preferences>`
- Factory: `com/karakept/app/data/local/DataStoreFactory.kt`
- Multiplatform implementations:
  - Android: `com/karakept/app/data/local/DataStoreFactory.android.kt`
  - Desktop: `com/karakept/app/data/local/DataStoreFactory.desktop.kt` (or JVM)

## Authentication & Identity

**Auth Provider:**
- Custom (Karakept-native auth)
- Authentication handled via `UsersApi` and `RemoteDataSource`
- Token management integrated with `ServerRepository`
- Implementation: `com/karakept/app/data/repository/ServerRepository.kt`

**Server Configuration:**
- Multiple servers supported
- Connection details: base URL, credentials stored in `ServerEntity`
- Active server selected and cached in preferences
- Used by: `RemoteDataSource` to route all API requests

## Monitoring & Observability

**Error Tracking:**
- None detected - Errors logged via Ktor Logger.SIMPLE

**Logs:**
- Ktor client logging at INFO level to stdout/logcat
- No external log aggregation
- Android: Visible via `adb logcat`

## CI/CD & Deployment

**Hosting:**
- App Store/Google Play (inferred from release workflow)
- Desktop distribution: Release artifacts (APK, DMG, EXE, AppImage)

**CI Pipeline:**
- GitHub Actions (`.github/workflows/`)

**Workflows:**

**`release.yml`:**
- Triggers: Push to main branch with specific file paths, or manual dispatch
- Jobs:
  1. `resolve-version` - Determine version from tags or input
  2. Generate AI changelog using Mistral LLM
  3. Build Android APK for multiple ABIs
  4. Build desktop packages (JVM Compose Desktop)
  5. Create GitHub Release with artifacts
- Signing configured via environment variables:
  - `KEYSTORE_PATH` - Path to release keystore
  - `KEYSTORE_PASSWORD` - Keystore password
  - `KEY_ALIAS` - Key alias
  - `KEY_PASSWORD` - Key password
- Python 3.11 for language_data dependency (changelog generation)

**`pr-build.yml`:**
- Triggers: Pull requests
- Validates build and tests pass
- Runs on: ubuntu-latest

**`ci.yml`:**
- Continuous integration on commits
- Lint, build, and test validation

**Build Artifacts:**
- Android: APK files (debug, release variants)
- Desktop: DMG (macOS), EXE (Windows), AppImage (Linux)
- Version info in `gradle.properties` and build.gradle.kts:
  - `versionCode` - Android internal version
  - `versionName` - User-visible version string

## Environment Configuration

**Required Environment Variables:**
- `KEYSTORE_PATH` - Path to Android keystore for signing
- `KEYSTORE_PASSWORD` - Keystore credentials
- `KEY_ALIAS` - Key alias in keystore
- `KEY_PASSWORD` - Key password (falls back to KEYSTORE_PASSWORD)

**API Endpoint Configuration:**
- Default: `https://try.karakept.app/api/v1`
- Configurable per-server in app settings
- Set in `AppModule.kt` line 43

**Offline Mode:**
- Configured via `SettingsRepository`
- Controlled by `offlineModeProvider` in `RemoteDataSource`
- When enabled: Skips remote API calls, relies on local SQLite cache

## Webhooks & Callbacks

**Incoming:**
- Not detected - App is a pure client

**Outgoing:**
- Not detected - No webhook triggers observed

## Sync & Offline Support

**Sync Strategy:**
- Pending Action Queue pattern (via `PendingActionEntity`)
- `BookmarkActionsRepository` coordinates offline-first writes
- On network restoration, queued actions are flushed to API
- Conflict resolution via server-side merge or last-write-wins

**Sync Progress Tracking:**
- `SyncProgress` model tracks sync state
- UI screens receive updates for progress indication
- Implemented in: `com/karakept/app/domain/` action system

## Monorepo Upstream

**Source:**
- `karakeep-upstream/` - Git submodule pointing to upstream Karakeep monorepo
- Contains Node.js/TypeScript services (browser extension, web, API spec)
- Package manager: pnpm 9.15.9
- Key packages:
  - `@karakeep/db` - Database schema and migrations
  - `@karakeep/api` - OpenAPI spec definition
  - `@karakeep/web` - Web frontend
  - `@karakeep/workers` - Server-side workers

**Usage in Karakept KMP:**
- OpenAPI spec at: `karakeep-upstream/packages/open-api/karakeep-openapi-spec.json`
- Consumed by OpenAPI Generator to create `api-client` module
- Spec generation triggered by: `openApiGenerate` Gradle task in `api-client/build.gradle.kts`

## Image & Asset Handling

**Image Loading:**
- Library: Coil 3.0.0 with Ktor network engine
- Caching: Automatic disk + memory caching via Coil
- Manager: `ImageCacheManager` in `com/karakept/app/utils/`
- Network: Uses Ktor HttpClient for downloads

**Asset Storage:**
- Downloaded assets stored in `AssetEntity` (SQLite)
- Accessible via `AssetDao`
- Used for: Thumbnails, preview images

## Desktop Tray & Notifications

**System Tray:**
- Library: Compose Native Tray 1.3.0
- macOS: NSStatusBar menu bar
- Windows: Taskbar icon
- Linux: D-Bus StatusNotifierItem (SNI) via native C/JNI bridge
  - Requires: `dbus-x11` package and running D-Bus session
  - Issue: `platformtools.darkmodedetector` excluded from desktop build to avoid skiko version conflict

**Notifications:**
- Library: KMP Notifier 1.6.1
- Android: NotificationManager API
- Desktop: System notification center
- Usage: Action feedback, sync completion alerts

## Platform-Specific Integrations

**Android:**
- Custom Chrome Tabs (androidx.browser) - For opening bookmark links
- Share activity integration (`ShareActivity.kt`, `QuickShareActivity.kt`)
- WorkManager (androidx.work) - Background task scheduling
- Permissions:
  - `android.permission.VIBRATE` - Haptic feedback
  - `android.permission.POST_NOTIFICATIONS` - Notification permission

**Desktop (Linux/macOS/Windows):**
- Native file picker via Native File Dialog
- WebKit-based content viewer (WebKitGTK on Linux)
- D-Bus integration (Linux system tray)
- Native menu bar (tray icon on Windows/macOS)

## Content Processing

**HTML Parsing:**
- Library: ksoup 0.2.1
- Purpose: Extract metadata from bookmarked web content
- Used by: Metadata extraction during bookmark sync

---

*Integration audit: 2026-03-20*
