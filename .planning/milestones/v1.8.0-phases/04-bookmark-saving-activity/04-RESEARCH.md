# Phase 04: Bookmark Saving Activity - Research

**Researched:** 2026-03-23
**Domain:** Android Activity lifecycle, share-target intents, Compose state isolation, Voyager navigation
**Confidence:** HIGH

## Summary

This phase fixes two bugs in the Android share-target bookmark saving flow: (1) after saving, the user is trapped in the bookmark viewer with no back route to the main list (SAVE-01), and (2) sharing a second URL reuses stale state from the first save (SAVE-02). The fix introduces a dedicated `BookmarkSavingActivity` with `singleTask` launch mode that handles the full save-and-view lifecycle independently from `MainActivity`.

The codebase already has all the building blocks: `ShareBookmarkScreen` composable (loading/error UI), `BookmarkRepository.createBookmark()`, Koin DI via `by inject()`, Voyager `Navigator` with `SlideTransition`, and the `AppTheme` composable. The current `ShareActivity` is a thin relay that extracts a URL and forwards it to `MainActivity` via intent extras -- this delegation pattern is the root cause of both bugs.

**Primary recommendation:** Replace `ShareActivity` with a self-contained `BookmarkSavingActivity` that hosts its own Voyager Navigator, handles the full save lifecycle, and uses `replaceAll` to build a proper back stack after save success.

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- **D-01:** After a bookmark is saved successfully, open `BookmarkViewerScreen` AND build the full back stack so pressing back goes to `MainScreen` (bookmark list). Use `navigator.replaceAll(listOf(MainScreen, BookmarkViewerScreen(bookmark.localId)))`.
- **D-02:** The user should see the saved bookmark immediately in the viewer -- not skip straight to the list.
- **D-03:** Introduce a dedicated `BookmarkSavingActivity` with `android:launchMode="singleTask"`. This replaces the current `ShareActivity -> MainActivity` delegation pattern for the share-target flow.
- **D-04:** `BookmarkSavingActivity` handles the share intent directly (extracts URL, shows saving UI, navigates). It does NOT launch `MainActivity` -- it is self-contained.
- **D-05:** With `singleTask`, if the user shares a second URL while `BookmarkSavingActivity` is already running, Android calls `onNewIntent` on the existing instance, and the activity resets its Compose state cleanly to a fresh `ShareBookmarkScreen` for the new URL.
- **D-06:** The existing `ShareActivity` class is removed (or emptied) since `BookmarkSavingActivity` supersedes it. The `QuickShareActivity` (background WorkManager path) is unchanged.
- **D-07:** On save failure, show both a Retry button (re-attempts `createBookmark(url)` with the same URL) and a Close button (calls `finish()` to return to the sharer app).
- **D-08:** The Retry button reuses the existing `ShareBookmarkScreen` -- reset error state and re-launch the `LaunchedEffect` (or trigger a retry via a state flag).

### Claude's Discretion
- Exact Compose state reset mechanism inside `BookmarkSavingActivity.onNewIntent` (e.g., mutableStateOf key, setContent re-call, or derived state)
- Whether `BookmarkSavingActivity` uses a Voyager Navigator or a plain Compose scaffold (it's a short-lived flow, not a full nav graph)
- AndroidManifest entry details (theme, label, exported flag)
- Exact UI layout of the saving/error screen -- keep consistent with current `ShareBookmarkScreen` design

### Deferred Ideas (OUT OF SCOPE)
None -- discussion stayed within phase scope.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| SAVE-01 | User can navigate back from the reader to the bookmark list after saving via Android share target (#158) | `navigator.replaceAll(listOf(MainScreen, BookmarkViewerScreen(id)))` builds proper back stack. Activity architecture ensures Voyager Navigator is scoped to BookmarkSavingActivity with full nav capability. |
| SAVE-02 | Sharing a second bookmark from another app creates a fresh saving activity instead of reusing the previous one (#159) | `singleTask` launch mode + `onNewIntent` handler with state key reset guarantees fresh Compose state per new intent. |
</phase_requirements>

## Standard Stack

### Core (already in project -- no new dependencies)
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Voyager Navigator | existing | Screen navigation with back stack | Already used in App.kt for full-app navigation |
| Koin Android | existing | DI for BookmarkRepository injection | Already used in QuickShareActivity and KarakeptApp |
| Compose Material3 | existing | UI components for saving/error screens | Already used throughout the app |
| AndroidX Activity Compose | existing | `setContent {}` for Compose in Activity | Already used in MainActivity |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| AndroidX Core SplashScreen | existing | Splash screen on activity start | NOT needed for BookmarkSavingActivity (short-lived, no splash) |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Voyager Navigator in BookmarkSavingActivity | Plain Compose scaffold (no navigation) | Voyager is needed because D-01 requires `replaceAll([MainScreen, BookmarkViewerScreen])` -- a plain scaffold cannot build a back stack with MainScreen |
| `singleTask` launch mode | `singleInstance` | `singleInstance` creates its own task and prevents other activities in the same task -- too restrictive. `singleTask` is correct. |
| Dedicated activity | Fix within MainActivity | Does not solve SAVE-02 (shared state pollution). Dedicated activity provides clean isolation. |

**Installation:** No new dependencies needed.

## Architecture Patterns

### Recommended Changes
```
composeApp/src/androidMain/
  kotlin/com/karakept/app/
    BookmarkSavingActivity.kt    # NEW: self-contained share-target activity
    ShareActivity.kt             # REMOVE or empty (superseded)
    MainActivity.kt              # MODIFY: remove shared_url handling from onNewIntent
  AndroidManifest.xml            # MODIFY: replace ShareActivity with BookmarkSavingActivity

composeApp/src/commonMain/
  kotlin/com/karakept/app/ui/screens/
    ShareBookmarkScreen.kt       # MODIFY: add Retry button, accept onClose callback
  kotlin/App.kt                  # MODIFY: remove sharedUrl parameter and routing
```

### Pattern 1: State Reset via Compose Key
**What:** Use a `mutableStateOf` counter or UUID as a key for `setContent` recomposition when `onNewIntent` fires.
**When to use:** When `singleTask` activity receives a new intent and needs to reset all Compose state.
**Example:**
```kotlin
class BookmarkSavingActivity : ComponentActivity() {
    private var sharedUrl by mutableStateOf<String?>(null)
    private var intentKey by mutableStateOf(0) // incremented on each new intent

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sharedUrl = extractUrl(intent)
        setContent {
            // Key forces full recomposition when intentKey changes
            key(intentKey) {
                BookmarkSavingContent(
                    url = sharedUrl ?: return@key,
                    onClose = { finish() }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent) // update the activity's intent reference
        sharedUrl = extractUrl(intent)
        intentKey++ // forces recomposition with fresh state
    }
}
```
**Why this works:** Compose's `key()` composable destroys and recreates all child state (including `remember` blocks, `LaunchedEffect`, and Voyager `Navigator`) when the key changes. This is the simplest and most reliable state reset mechanism.

### Pattern 2: Self-Contained Activity with Voyager Navigator
**What:** `BookmarkSavingActivity` hosts its own Voyager `Navigator` starting at `ShareBookmarkScreen`, then uses `replaceAll` to transition to `[MainScreen, BookmarkViewerScreen]` on success.
**When to use:** This is the required architecture per D-01 and D-04.
**Example:**
```kotlin
@Composable
fun BookmarkSavingContent(url: String, onClose: () -> Unit) {
    val settingsRepository = koinInject<SettingsRepository>()
    val themeMode by settingsRepository.themeMode.collectAsState(initial = ThemeMode.SYSTEM)
    val accentColor by settingsRepository.accentColor.collectAsState(initial = AccentColor.PURPLE)

    AppTheme(themeMode = themeMode, accentColor = accentColor) {
        Navigator(ShareBookmarkScreen(url)) { navigator ->
            SlideTransition(navigator)
        }
    }
}
```

### Pattern 3: Retry via State Flag
**What:** Add a `retryTrigger` state to `ShareBookmarkScreen` that re-launches the save coroutine when incremented.
**When to use:** For D-07/D-08 retry functionality.
**Example:**
```kotlin
var retryTrigger by remember { mutableIntStateOf(0) }
var error by remember { mutableStateOf<String?>(null) }

LaunchedEffect(retryTrigger) {
    error = null
    val result = repository.createBookmark(url) { status -> ... }
    if (result.isSuccess) {
        navigator.replaceAll(listOf(MainScreen, BookmarkViewerScreen(result.getOrThrow().localId)))
    } else {
        error = result.exceptionOrNull()?.message ?: "Unknown error"
    }
}

// In error UI:
Button(onClick = { retryTrigger++ }) { Text("Retry") }
Button(onClick = onClose) { Text("Close") }
```

### Anti-Patterns to Avoid
- **Starting MainActivity from BookmarkSavingActivity on success:** This was the old pattern (ShareActivity -> MainActivity). It causes SAVE-02 because state leaks between activities. Keep BookmarkSavingActivity self-contained per D-04.
- **Using `FLAG_ACTIVITY_CLEAR_TOP` to reset state:** This is unreliable for Compose state resets. Use the key-based recomposition pattern instead.
- **Calling `setContent` again in `onNewIntent`:** While this technically works, it creates a new Composition root and may cause issues with Koin injection scoping. Prefer the `key()` approach which recomposes within the same root.
- **Sharing mutableStateOf between Activity and Composable without proper threading:** The `sharedUrl` and `intentKey` state variables are set on the main thread (Activity lifecycle callbacks run on main thread), so this is safe. Do not access them from background threads.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| URL extraction from shared text | New regex or parser | Reuse `ShareActivity.handleIntent` regex verbatim | The regex `(https?://[\w-]+(\.[\w-]+)+(:\d+)?(/[^\s]*)?)` is already tested in production |
| Saving UI (progress + error) | New composable from scratch | Modify existing `ShareBookmarkScreen` | Already has loading indicator, error display, styling consistent with app |
| DI setup | Manual service creation | Koin `by inject()` (Activity) and `koinInject()` (Composable) | KarakeptApp already initializes Koin at Application level -- all activities get it for free |
| Theme setup | Hardcoded colors | `AppTheme(themeMode, accentColor)` composable | Ensures consistent MD3 theming with user preferences |
| Navigation back stack | Manual Activity back stack | Voyager `navigator.replaceAll(listOf(MainScreen, BookmarkViewerScreen(id)))` | Voyager handles the back stack correctly, including proper screen lifecycle |

**Key insight:** This phase is almost entirely about wiring existing components into a new Activity shell. The composables, repository calls, DI, and theming are all already implemented. The work is architectural plumbing, not feature development.

## Common Pitfalls

### Pitfall 1: Voyager Navigator State Surviving Activity Recreation
**What goes wrong:** If the Compose `key()` is not used correctly, Voyager's `Navigator` may retain screens from the previous intent when `onNewIntent` fires.
**Why it happens:** `remember` and `Navigator` state persist across recompositions unless explicitly invalidated.
**How to avoid:** Wrap the `Navigator` in a `key(intentKey)` block so the entire navigation tree is recreated on each new intent.
**Warning signs:** Second share shows the BookmarkViewerScreen from the first share instead of a fresh ShareBookmarkScreen.

### Pitfall 2: MainScreen ScreenModel Initialization in BookmarkSavingActivity
**What goes wrong:** `MainScreen` uses `MainScreenModel` (a Koin singleton) which loads bookmarks, server config, etc. When `MainScreen` is in the back stack of `BookmarkSavingActivity`, it initializes this model unnecessarily.
**Why it happens:** Voyager initializes `ScreenModel` when the screen is part of the Navigator, even if it is not the current screen.
**How to avoid:** This is acceptable -- `MainScreenModel` is already a singleton and lazy-loaded. The overhead is minimal. However, verify that `MainScreen` does not trigger a full sync when merely added to the back stack (it should only trigger on `Content()` composition).
**Warning signs:** Noticeable delay or network calls when the saving screen loads.

### Pitfall 3: Missing Koin Android Context in BookmarkSavingActivity
**What goes wrong:** `koinInject()` or `by inject()` fails with "No Koin context found" errors.
**Why it happens:** Koin is initialized in `KarakeptApp.onCreate()` at the Application level. As long as `BookmarkSavingActivity` extends `ComponentActivity` and the Application class is `KarakeptApp`, Koin works automatically.
**How to avoid:** Ensure `BookmarkSavingActivity` extends `ComponentActivity` (not plain `Activity`). Do NOT call `startKoin` again.
**Warning signs:** `KoinNotStartedException` at runtime.

### Pitfall 4: ShareActivity Intent Filter Must Transfer to BookmarkSavingActivity
**What goes wrong:** After removing `ShareActivity`, sharing from other apps stops working because the intent filter is gone.
**Why it happens:** The `<intent-filter>` for `ACTION_SEND` + `text/plain` must be moved to the new `BookmarkSavingActivity` declaration.
**How to avoid:** Copy the exact intent filter from `ShareActivity` to `BookmarkSavingActivity` in `AndroidManifest.xml`.
**Warning signs:** "No app can handle this action" when sharing a URL to Karakept.

### Pitfall 5: Activity Theme for BookmarkSavingActivity
**What goes wrong:** The activity shows a white/black flash before Compose content renders, or uses the splash screen theme unnecessarily.
**Why it happens:** `BookmarkSavingActivity` does not need a splash screen -- it is a short-lived saving flow.
**How to avoid:** Use `@style/Theme.App` (not `Theme.App.Starting`) as the activity theme. This avoids the splash screen dependency while keeping transparent status/navigation bars.
**Warning signs:** Splash screen icon appears briefly when sharing a URL.

### Pitfall 6: onNewIntent Not Called When Activity Is Not Running
**What goes wrong:** Developer assumes `onNewIntent` handles all cases, but the first share always goes through `onCreate`.
**Why it happens:** `onNewIntent` is only called when the activity already exists and `singleTask` routes a new intent to it. The first share always calls `onCreate`.
**How to avoid:** Extract URL in both `onCreate` and `onNewIntent`. Both paths must set the state correctly.
**Warning signs:** First share after cold start does nothing.

### Pitfall 7: Stale Intent in Activity After onNewIntent
**What goes wrong:** After `onNewIntent`, `getIntent()` still returns the original intent from `onCreate`.
**Why it happens:** Android does not automatically update the activity's stored intent reference.
**How to avoid:** Call `setIntent(intent)` at the start of `onNewIntent`.
**Warning signs:** After sharing a second URL, the activity re-processes the first URL.

## Code Examples

### BookmarkSavingActivity Structure
```kotlin
// Source: derived from existing QuickShareActivity + ShareActivity patterns in this codebase
class BookmarkSavingActivity : ComponentActivity() {
    private var sharedUrl by mutableStateOf<String?>(null)
    private var intentKey by mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        sharedUrl = extractUrlFromIntent(intent)

        setContent {
            key(intentKey) {
                val url = sharedUrl
                if (url != null) {
                    BookmarkSavingContent(url = url, onClose = { finish() })
                } else {
                    finish()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        sharedUrl = extractUrlFromIntent(intent)
        intentKey++
    }

    private fun extractUrlFromIntent(intent: Intent): String? {
        if (intent.action != Intent.ACTION_SEND || intent.type != "text/plain") return null
        val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return null
        val urlRegex = "(https?://[\\w-]+(\\.[\\w-]+)+(:\\d+)?(/[^\\s]*)?)".toRegex()
        return urlRegex.find(sharedText)?.value ?: sharedText
    }
}
```

### AndroidManifest Entry
```xml
<!-- Replace ShareActivity with BookmarkSavingActivity -->
<activity
    android:name=".BookmarkSavingActivity"
    android:exported="true"
    android:label="${appName}"
    android:launchMode="singleTask"
    android:theme="@style/Theme.App">
    <intent-filter>
        <action android:name="android.intent.action.SEND" />
        <category android:name="android.intent.category.DEFAULT" />
        <data android:mimeType="text/plain" />
    </intent-filter>
</activity>
```

### Modified ShareBookmarkScreen with Retry
```kotlin
// Source: modification of existing ShareBookmarkScreen.kt
data class ShareBookmarkScreen(val url: String) : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val repository = koinInject<BookmarkRepository>()
        var error by remember { mutableStateOf<String?>(null) }
        var status by remember { mutableStateOf("Saving bookmark...") }
        var retryTrigger by remember { mutableIntStateOf(0) }

        // Access onClose from the activity context
        val activity = LocalContext.current as? Activity

        LaunchedEffect(retryTrigger) {
            error = null
            status = "Saving bookmark..."
            val result = repository.createBookmark(url) { newStatus ->
                status = newStatus
            }
            if (result.isSuccess) {
                val bookmark = result.getOrThrow()
                navigator.replaceAll(listOf(MainScreen, BookmarkViewerScreen(bookmark.localId)))
            } else {
                error = result.exceptionOrNull()?.message ?: "Unknown error"
            }
        }

        // UI: same layout as current, but error state adds Retry + Close buttons
    }
}
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `ShareActivity` -> relay to `MainActivity` | `BookmarkSavingActivity` self-contained | This phase | Eliminates state leakage and back stack trap |
| `navigator.replace(BookmarkViewerScreen)` | `navigator.replaceAll(listOf(MainScreen, BookmarkViewerScreen))` | This phase | Proper back stack for viewer -> list navigation |
| Error shows only Close button | Error shows Retry + Close | This phase | User can recover from transient failures |

**Deprecated/outdated:**
- `ShareActivity.kt`: Will be removed/emptied. All its functionality moves to `BookmarkSavingActivity`.
- `MainActivity.onNewIntent` shared_url handling: No longer needed. The share flow no longer routes through MainActivity.
- `App.kt` `sharedUrl` parameter: No longer needed. BookmarkSavingActivity handles its own Compose content.

## Open Questions

1. **MainScreen behavior inside BookmarkSavingActivity**
   - What we know: D-01 requires `replaceAll(listOf(MainScreen, BookmarkViewerScreen(id)))`. This puts `MainScreen` in the back stack. When the user presses back from the viewer, they land on `MainScreen` running inside `BookmarkSavingActivity`.
   - What's unclear: Is this acceptable UX? The user ends up in the full app but running inside `BookmarkSavingActivity` instead of `MainActivity`. The app works identically either way since both are `ComponentActivity` subclasses and Koin provides the same singletons.
   - Recommendation: This is fine. The user does not know or care which Activity hosts the UI. If the user later opens Karakept from the launcher, Android will start `MainActivity` as a separate task. This is the simpler approach vs. `startActivity(MainActivity)` + `finish()` which adds a visible activity transition.

2. **Cleanup of debug logging in MainActivity**
   - What we know: `MainActivity` has extensive `Log.d("DebuggingCtx", ...)` calls that were likely added during bug investigation.
   - What's unclear: Whether cleanup is in scope for this phase.
   - Recommendation: Remove the shared_url handling and associated debug logs from `MainActivity.onNewIntent` as part of this phase. Other debug logs can stay or be cleaned up opportunistically.

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 5 (desktopTest) + Android instrumented tests (androidTest, currently empty) |
| Config file | `composeApp/build.gradle.kts` |
| Quick run command | `./gradlew :composeApp:desktopTest` |
| Full suite command | `./gradlew :composeApp:desktopTest` |

### Phase Requirements -> Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| SAVE-01 | Back navigation from viewer reaches MainScreen after share-save | manual-only | N/A -- requires Android device with share intent | No |
| SAVE-02 | Second share intent resets state to fresh saving screen | manual-only | N/A -- requires Android activity lifecycle with real intents | No |

**Manual-only justification:** Both requirements involve Android Activity lifecycle (intent delivery, `onNewIntent`, back stack behavior) which cannot be tested without Android instrumented tests or a real device. The project has no `androidTest` infrastructure. These are verified via human UAT on a physical device or emulator.

### Sampling Rate
- **Per task commit:** Compile check via `./gradlew :composeApp:assembleDebug`
- **Per wave merge:** `./gradlew :composeApp:desktopTest` (ensures no regression in existing tests)
- **Phase gate:** Full suite green + manual UAT on device

### Wave 0 Gaps
None -- this phase does not require new automated tests. All verification is manual (Android activity lifecycle + intent handling). Existing desktopTest suite should continue passing (no changes to tested code paths).

## Sources

### Primary (HIGH confidence)
- Codebase inspection: `ShareActivity.kt`, `QuickShareActivity.kt`, `MainActivity.kt`, `AndroidManifest.xml`, `ShareBookmarkScreen.kt`, `App.kt`, `KarakeptApp.kt`, `BookmarkViewerScreen.kt`, `MainScreen.kt`, `Theme.kt`
- Android developer documentation on `launchMode="singleTask"` and `onNewIntent` behavior (well-established, stable API)
- Voyager Navigator API: `replaceAll`, `Navigator`, `SlideTransition` (verified from codebase usage in `App.kt`)

### Secondary (MEDIUM confidence)
- Compose `key()` function for state invalidation -- well-documented core Compose API

### Tertiary (LOW confidence)
- None

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH - all libraries already in use, no new dependencies
- Architecture: HIGH - patterns derived directly from existing codebase + established Android lifecycle APIs
- Pitfalls: HIGH - based on direct code analysis and well-known Android activity lifecycle gotchas

**Research date:** 2026-03-23
**Valid until:** 2026-04-23 (stable Android APIs, no expected changes)
