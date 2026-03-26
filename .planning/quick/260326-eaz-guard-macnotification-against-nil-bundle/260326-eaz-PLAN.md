---
quick_id: 260326-eaz
type: quick
---

# Quick Task 260326-eaz: Guard MacNotification against nil bundle crash

## Task
When running the Desktop app via `./gradlew run`, the macOS background sync notification crashes the process with `NSInternalInconsistencyException: bundleProxyForCurrentProcess is nil`.

## Root Cause
`UNUserNotificationCenter.current()` (called inside the `knotify` Swift/JNA library) requires a valid macOS `.app` bundle. Via `gradlew run`, the main bundle URL resolves to the JDK binary directory — not a real `.app`. This throws an ObjC `NSInternalInconsistencyException` that bypasses JVM `try/catch` and crashes the process.

## Fix
In `BackgroundSyncScheduler.showDigestNotification()`, check if the current process is running inside a real `.app/Contents/MacOS/` directory before calling `notification().send()`. If not (e.g. running via `gradlew run`), skip the notification silently.

## File
`composeApp/src/desktopMain/kotlin/com/karakept/app/services/BackgroundSyncScheduler.kt`
