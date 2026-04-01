---
phase: 22-dev-release-icon-and-label-differentiation
plan: 01
status: complete
started: 2026-03-31
completed: 2026-04-01
---

## Summary

Cherry-picked draft DEV icon/label implementation and extended it with desktop support. Dev release builds now show a distinct red "DEV" banner on both Android launcher icons and desktop dock/window icons, plus a DEV badge on the onboarding welcome screen.

## What Was Built

1. **Android DEV icon overlay**: Horizontal red banner with "DEV" text inside the adaptive icon safe zone (bottom strip at y=72-87 in the 108dp viewport)
2. **Android BuildConfig flag**: `IS_DEV` field — `false` in defaultConfig, `true` in devRelease flavor
3. **KMP expect/actual `isDevBuild`**: Common declaration, Android reads `BuildConfig.IS_DEV`, desktop reads `-Dkarakept.dev` system property
4. **Desktop DEV mode**: `./gradlew run -Pdev=true` enables dev mode — overlays red "DEV" banner on dock icon (alpha-clipped to icon shape), shows "(DEV)" in window title, tray tooltip, and macOS app name
5. **Onboarding DEV badge**: Red "DEV" pill badge on the bookmark icon in the welcome step (both platforms)

## Key Files

### Created
- `composeApp/src/devRelease/res/drawable/dev_banner.xml` — Red horizontal banner vector drawable
- `composeApp/src/devRelease/res/drawable/ic_launcher_foreground_dev.xml` — Layer-list compositing foreground + banner
- `composeApp/src/devRelease/res/drawable/ic_notification.xml` — Dev notification icon
- `composeApp/src/devRelease/res/mipmap-anydpi-v26/ic_launcher.xml` — Adaptive icon override for devRelease

### Modified
- `composeApp/build.gradle.kts` — buildConfig flag + `-Pdev` Gradle property for desktop
- `composeApp/src/commonMain/kotlin/Platform.kt` — `expect val isDevBuild: Boolean`
- `composeApp/src/androidMain/kotlin/Platform.android.kt` — `actual val isDevBuild = BuildConfig.IS_DEV`
- `composeApp/src/desktopMain/kotlin/Platform.jvm.kt` — `actual val isDevBuild` reads system property
- `composeApp/src/desktopMain/kotlin/main.kt` — DEV banner overlay on icon, conditional window title
- `composeApp/src/commonMain/kotlin/com/karakept/app/ui/screens/OnboardingScreen.kt` — DEV badge in WelcomeStep
- `composeApp/src/androidMain/res/values/themes.xml` — splash screen icon
- `composeApp/src/androidMain/res/values-night/themes.xml` — splash screen icon (night)

## Deviations

- **Banner redesign**: Original draft used a diagonal corner ribbon, but it fell outside the adaptive icon safe zone and was clipped. Redesigned as a horizontal bottom strip within the 66dp visible area.
- **Desktop support added**: Original plan only covered Android. Extended to desktop with programmatic icon overlay and window title differentiation.

## Verification

- [x] devRelease APK assembles successfully
- [x] Desktop compiles with `isDevBuild` support
- [x] Android launcher icon shows DEV banner (human verified)
- [x] Desktop dock icon shows DEV banner (human verified)
- [x] Window title shows "(DEV)" in dev mode
- [x] Pre-existing test failures confirmed unrelated (list sync, scroll position tests)
