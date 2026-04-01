# Quick Task 260326-eaz: Guard MacNotification — Summary

**Date:** 2026-03-26
**Commit:** 19695da

## What was done
Added `isMacAppBundleAvailable()` guard in `BackgroundSyncScheduler.showDigestNotification()`.

The fix checks `ProcessHandle.current().info().command()` for `.app/Contents/MacOS/` to detect whether the app is running as a packaged `.app` bundle. If not (e.g. `gradlew run`), the notification is skipped before the JNA/Swift code is called.

## Why this approach
The existing `try/catch` cannot catch `NSInternalInconsistencyException` — it's an Objective-C native exception that crashes the JVM process directly. The only safe option is to prevent the call to `UNUserNotificationCenter.current()` from happening at all.

## Behavior
- **Packaged `.app` (production):** Notifications work as before
- **`gradlew run` (dev):** Notification silently skipped, no crash
- **Non-macOS platforms:** Guard returns `true` — unaffected
