---
phase: 04-bookmark-saving-activity
plan: 01
subsystem: ui
tags: [android, activity, voyager, navigation, share-target, compose]

# Dependency graph
requires: []
provides:
  - "BookmarkSavingActivity with singleTask launch mode for Android share-target"
  - "ShareBookmarkScreen retry/close error handling and replaceAll back stack fix"
  - "Clean removal of ShareActivity delegation pattern"
affects: []

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "key(intentKey) recomposition reset pattern for singleTask activities"
    - "retryTrigger mutableIntStateOf pattern for LaunchedEffect retry"
    - "replaceAll(MainScreen, ViewerScreen) for proper back stack in share flow"

key-files:
  created:
    - "composeApp/src/androidMain/kotlin/com/karakept/app/BookmarkSavingActivity.kt"
  modified:
    - "composeApp/src/androidMain/AndroidManifest.xml"
    - "composeApp/src/commonMain/kotlin/com/karakept/app/ui/screens/ShareBookmarkScreen.kt"
    - "composeApp/src/commonMain/kotlin/App.kt"
    - "composeApp/src/androidMain/kotlin/MainActivity.kt"

key-decisions:
  - "Kept sharedUrl param in App.kt for desktop deep link compatibility (desktop has no Activity equivalent)"
  - "Used key(intentKey) to force full Compose tree destruction on onNewIntent, guaranteeing fresh Navigator state"

patterns-established:
  - "key(intentKey) pattern: wrap entire Compose content in key() keyed on an incrementing int to force full recomposition on onNewIntent"
  - "retryTrigger pattern: use mutableIntStateOf counter as LaunchedEffect key for retry-on-error flows"

requirements-completed: [SAVE-01, SAVE-02]

# Metrics
duration: 3min
completed: 2026-03-23
---

# Phase 04 Plan 01: Bookmark Saving Activity Summary

**Self-contained BookmarkSavingActivity with singleTask launch mode, retry/close error handling, and replaceAll back stack fix for Android share target**

## Performance

- **Duration:** 3 min
- **Started:** 2026-03-23T16:34:33Z
- **Completed:** 2026-03-23T16:37:50Z
- **Tasks:** 2
- **Files modified:** 5

## Accomplishments
- Created BookmarkSavingActivity with singleTask launch mode and onNewIntent state reset via key(intentKey)
- Fixed back stack: share-save now navigates to MainScreen + BookmarkViewerScreen via replaceAll, so back button returns to bookmark list (SAVE-01)
- Fixed state leak: onNewIntent increments intentKey, destroying and recreating the entire Compose tree including Voyager Navigator (SAVE-02)
- Added Retry and Close buttons to ShareBookmarkScreen error state with retryTrigger pattern
- Removed old ShareActivity and all shared_url delegation from MainActivity

## Task Commits

Each task was committed atomically:

1. **Task 1: Create BookmarkSavingActivity and update AndroidManifest** - `a832b09` (feat)
2. **Task 2: Add retry/close to ShareBookmarkScreen and clean up MainActivity/App.kt** - `bc7946b` (feat)

## Files Created/Modified
- `composeApp/src/androidMain/kotlin/com/karakept/app/BookmarkSavingActivity.kt` - New self-contained activity for Android share target with singleTask launch mode
- `composeApp/src/androidMain/AndroidManifest.xml` - Replaced ShareActivity entry with BookmarkSavingActivity (singleTask, Theme.App)
- `composeApp/src/commonMain/kotlin/com/karakept/app/ui/screens/ShareBookmarkScreen.kt` - Added onClose param, retryTrigger, replaceAll navigation, Retry/Close buttons
- `composeApp/src/commonMain/kotlin/App.kt` - Removed sharedUrl-specific logging; kept param for desktop compatibility
- `composeApp/src/androidMain/kotlin/MainActivity.kt` - Removed shared_url handling and DebuggingCtx logs
- `composeApp/src/androidMain/kotlin/com/karakept/app/ShareActivity.kt` - Deleted (replaced by BookmarkSavingActivity)

## Decisions Made
- Kept `sharedUrl` parameter in `App()` for desktop deep link compatibility. The desktop platform uses `karakept://` URI scheme to pass shared URLs through `App(sharedUrl = pendingShareUrl)`. Since desktop has no Activity equivalent, removing the parameter would break the desktop share flow. Android no longer passes this parameter.
- Used `@style/Theme.App` (not `Theme.App.Starting`) for BookmarkSavingActivity to avoid unnecessary splash screen display on the share target activity.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Retained sharedUrl parameter in App.kt for desktop compatibility**
- **Found during:** Task 2 (App.kt cleanup)
- **Issue:** Plan specified removing `sharedUrl` from `App()` signature, but desktop `main.kt` passes `pendingShareUrl` via this parameter for the `karakept://` deep link scheme. Removing it would break desktop share functionality.
- **Fix:** Kept `sharedUrl` parameter in `App()` with its routing logic. Removed it only from Android's `MainActivity` which no longer needs it.
- **Files modified:** `composeApp/src/commonMain/kotlin/App.kt`
- **Verification:** Desktop `main.kt` still compiles with `App(sharedUrl = pendingShareUrl)`. Android `MainActivity` no longer references `shared_url`.
- **Committed in:** `bc7946b` (Task 2 commit)

---

**Total deviations:** 1 auto-fixed (1 blocking)
**Impact on plan:** Necessary to preserve desktop platform functionality. The core Android fix is fully delivered -- all `shared_url` delegation removed from Android, BookmarkSavingActivity is self-contained.

## Issues Encountered
None

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- Bookmark saving activity flow is complete and self-contained
- Manual UAT recommended on a physical Android device:
  - SAVE-01: Share URL from browser -> save -> back -> verify landing on bookmark list
  - SAVE-02: Share URL -> save -> share different URL -> verify fresh state
- Build verification with `./gradlew assembleDebug` recommended before release

---
*Phase: 04-bookmark-saving-activity*
*Completed: 2026-03-23*
