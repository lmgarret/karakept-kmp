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
| `admin.adminRetagBookmark` | Re-run AI tagging on one bookmark (**admin only**) |
| `admin.getAdminNoticies` | Probe: does this API key belong to an admin? |

`recrawlBookmark` takes `{ bookmarkId, archiveFullPage, storePdf }` and enqueues a background
job — a successful response only means the request was accepted, so callers re-sync the
bookmark afterwards to pick up the result.

**AI actions.** Karakeep exposes exactly one AI capability over the documented REST API:
`POST /bookmarks/{bookmarkId}/summarize`. It runs the inference **inline** and answers with the
updated record, so unlike every other server job here the result is available when the call
returns. The summary lands in the bookmark's `summary` field, which is not `description`: the
crawler reads that one from the page's meta tags and the inference worker never writes it. Both
are stored (`BookmarkEntity.summary`, added in schema v12) and both can be shown.

Re-running AI tagging has **no user-level route at all**. The only per-bookmark option is
`admin.adminRetagBookmark`, an `adminProcedure` that enqueues an inference job; the alternative,
`recrawlBookmark`, re-downloads the page as a side effect and is already spoken for by "Refresh".
So the tagging action is offered only to admins.

Admin status cannot be read directly — neither `GET /users/me` nor tRPC `users.whoami` returns a
role — so it is discovered by calling something only an admin may call. `admin.getAdminNoticies`
is the cheapest such route: its handler returns an empty object and touches no data. The answer is
cached per server in memory (`BookmarkActionsRepository.aiCapabilities`) and re-probed on next
launch. A transport failure leaves the cached value alone rather than demoting a known admin.

Summarize availability is learned the same way, but lazily: a server with no model configured
answers `400 "No inference client configured"`, which flips `canSummarize` off for that server so
the action stops being offered.

Reading progress lives in its own server-side table, and there is **no procedure that reads
many bookmarks at once** — `getReadingProgress` takes a single `bookmarkId`. tRPC batches at
the *transport* though, which is what Karakeep's own web client does (`httpBatchLink`), so one
query per bookmark does not have to mean one request per bookmark.

- **Push** rides the pending-action queue (`UPDATE_READING_PROGRESS`), so a rejected push is
  retried or parked as a failed action rather than dropped. The single exception is
  `BAD_REQUEST: reading progress can only be saved for link bookmarks`, which no retry can
  fix: `updateReadingProgress` returns `false` and the action is discarded.
- **Pull** goes through `pullReadingProgressForTargets`, which packs
  `READING_PROGRESS_BATCH_SIZE` queries into one request. The batch travels in the query
  string, and how long a URL a deployment accepts is the reverse proxy's business rather than
  the server's, so the size starts conservative and halves on 414/431. Three paths feed it:
  1. *Backfill* — a server whose rows have never been pulled (`progressSyncedAt = 0`) is
     drained in full on the first sync.
  2. *Rotation* — steady state, `PROGRESS_PULL_ROTATION` rows per pass, ordered by what the
     user is most likely to be looking at: the list being synced first, then unread rows, then
     least-recently-pulled. Every sync flavour is eligible (browsing by list must not starve
     it), rationed per server by `BookmarkRepository.tryAcquireReadingProgressPull` so a
     fan-out of list syncs cannot multiply the passes by the number of lists. A full sync and
     the view currently on screen (`isCurrentView`) are exempt: the ration is per server, so
     the list the user just opened would otherwise skip its own pull whenever another list's
     pass took the slot moments earlier.
  3. *Visible rows* — `pullReadingProgressForVisible`, driven by the list's scroll position,
     refreshes what is on screen when its progress is missing or older than
     `VISIBLE_PROGRESS_STALE_AFTER_MS`. Before batching this was the only unbounded path, so
     scrolling was what converged a library; it is now a latency optimisation rather than the
     mechanism.

  The pull runs *before* the content download in the same pipeline: progress decides what the
  user sees, while a content download changes nothing on the list and can run for minutes.

  Only a pull the server actually answered advances the cursor: an id the batch did not come
  back with is "we never found out", not "nothing stored", and holds its row for a retry.

**Read state is local-only.** Karakeep has no read flag, so `readingProgress` is the sole
carrier between devices, and `applyServerReadingProgress` derives `isRead` from it. Two
consequences: the pull takes the server's value even when it is *lower* (otherwise "mark as
unread" — a reset to 0% — could never travel), guarded only by "this device has no progress
push of its own still queued" — pending *or* failed, since a push that ran out of retries is
still local state the server has never heard, and bookmarks with one outstanding are left out
of the batch entirely so no answer can arrive to overwrite them; and marking unread while
*keeping* the reading position stays
local, because no percentage means "unread but 80% in". `getReadingProgress` returns no
timestamp, so real last-writer-wins is not available: two devices reading the same article
settle on the last one to push.

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
- Library: Compose Native Tray 2.1.6 (`dev.nucleusframework:composenativetray`)
- macOS: NSStatusBar menu bar
- Windows: Taskbar icon
- Linux: D-Bus StatusNotifierItem (SNI) via native C/JNI bridge
  - Requires: `dbus-x11` package and running D-Bus session
  - `main.kt` skips the tray entirely when `DBUS_SESSION_BUS_ADDRESS` is unset (devcontainer,
    CI, headless). There has been no AWT fallback since 2.0.0, so the window's close button
    quits instead of hiding when the tray is unavailable — hiding would strand the process.
- Menu is built with the **composable** DSL (`ComposableTrayMenuScope`). The non-composable
  `TrayMenuBuilder` overloads are all `@LowPriorityInOverloadResolution` in 2.x, so a
  `menuContent = { … }` lambda binds to the composable one: state reads are reactive, and
  `painterResource` / `DrawableResource` icons work inside menu and submenu bodies.
- Menu contents (`composeApp/src/desktopMain/kotlin/main.kt`):
  - Server status header, iconed with `Res.drawable.icon`
  - Save Bookmark from Clipboard
  - Recent Bookmarks submenu — the 8 newest, opening in the browser. Reloaded via
    `onMenuOpened` rather than a permanent query subscription; the callback is dispatched
    asynchronously, so a refresh lands for the *next* open.
  - Open in Browser
  - Background Sync `CheckableItem`, two-way bound to `SettingsRepository`
  - Show/Hide Window, Quit
- Icon sizing uses `IconRenderProperties` defaults. Since 2.1.6 they keep the full-resolution
  master and let each backend downsample at draw time (SNI pixmap pyramid, multi-frame ICO,
  16 pt NSImage), so passing a hand-picked target size only costs resolution.
- Icons are not rendered in submenus on GNOME — keep submenu items text-only.

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
