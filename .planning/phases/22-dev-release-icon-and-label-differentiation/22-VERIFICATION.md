---
phase: 22-dev-release-icon-and-label-differentiation
verified: 2026-03-31T00:00:00Z
status: human_needed
score: 4/4 must-haves verified
human_verification:
  - test: "Install devRelease APK on Android device and confirm launcher icon shows DEV banner"
    expected: "Red horizontal DEV banner visible at bottom of icon in launcher; no banner on production icon"
    why_human: "Icon rendering depends on Android adaptive icon composition — cannot verify visually without device"
  - test: "Open devRelease app and reach onboarding welcome step"
    expected: "Red DEV pill badge visible at bottom-right of the bookmark icon on the welcome screen"
    why_human: "Conditional UI rendering in Compose must be verified visually on device or emulator"
  - test: "Run desktop in dev mode (./gradlew run -Pdev=true) and inspect window title and dock icon"
    expected: "Window title shows 'Karakept (DEV)', dock icon shows red DEV banner, tray tooltip shows 'Karakept (DEV)'"
    why_human: "Desktop icon overlay is programmatic AWT — cannot verify rendering without running the app"
---

# Phase 22: Dev Release Icon & Label Differentiation Verification Report

**Phase Goal:** Distinguish dev release installs from production via visible DEV ribbon on launcher icon and DEV badge in onboarding UI
**Verified:** 2026-03-31
**Status:** human_needed (all automated checks pass; visual rendering requires device/desktop verification)
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths

| #  | Truth | Status | Evidence |
|----|-------|--------|----------|
| 1  | devRelease build shows a distinct launcher icon with DEV ribbon overlay | ✓ VERIFIED | `ic_launcher.xml` references `ic_launcher_foreground_dev`; `ic_launcher_foreground_dev.xml` layer-list composites foreground + `dev_banner`; `dev_banner.xml` contains red `#D32F2F` fill |
| 2  | devRelease build shows DEV badge on OnboardingScreen welcome step | ✓ VERIFIED | `OnboardingScreen.kt:12` imports `isDevBuild`; line 275 gates DEV pill badge on `if (isDevBuild)` |
| 3  | regular release/debug builds show normal icon with no DEV overlay | ✓ VERIFIED | devRelease resources are scoped to `src/devRelease/` source set only; `defaultConfig` has `IS_DEV = false`; devRelease flavor overrides to `true`; overlay code in main.kt gated on `isDevBuild` |
| 4  | desktop build compiles with isDevBuild readable | ✓ VERIFIED | `Platform.jvm.kt:10` declares `actual val isDevBuild: Boolean = System.getProperty("karakept.dev")?.toBoolean() == true` — evaluates to false by default; plan said `= false` but system-property variant is a valid superset delivering the same contract |

**Score:** 4/4 truths verified

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `composeApp/src/devRelease/res/drawable/dev_banner.xml` | Red diagonal DEV ribbon vector drawable | ✓ VERIFIED | Exists; 29 lines; horizontal red banner `#D32F2F` with DEV letter paths in white |
| `composeApp/src/devRelease/res/mipmap-anydpi-v26/ic_launcher.xml` | Adaptive icon overriding foreground with dev banner overlay | ✓ VERIFIED | Exists; references `@drawable/ic_launcher_foreground_dev` as foreground |
| `composeApp/src/commonMain/kotlin/Platform.kt` | expect val isDevBuild: Boolean declaration | ✓ VERIFIED | Line 10: `expect val isDevBuild: Boolean` |
| `composeApp/src/androidMain/kotlin/Platform.android.kt` | actual isDevBuild reading BuildConfig.IS_DEV | ✓ VERIFIED | Line 16: `actual val isDevBuild: Boolean = BuildConfig.IS_DEV` |
| `composeApp/src/desktopMain/kotlin/Platform.jvm.kt` | actual isDevBuild for desktop | ✓ VERIFIED | Line 10: reads `karakept.dev` system property; defaults to false when property absent |

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| `composeApp/build.gradle.kts` | `BuildConfig.IS_DEV` | `buildConfigField` in defaultConfig (false) and devRelease flavor (true) | ✓ WIRED | Lines 165 and 194 confirmed; `buildConfig = true` at line 155 |
| `OnboardingScreen.kt` | `isDevBuild` | import + conditional DEV badge rendering | ✓ WIRED | Line 12: `import isDevBuild`; line 275: `if (isDevBuild)` wraps full DEV pill badge composable |
| `ic_launcher.xml` | `ic_launcher_foreground_dev` | foreground drawable reference | ✓ WIRED | `ic_launcher.xml` foreground points to `@drawable/ic_launcher_foreground_dev`; that file layer-lists `ic_launcher_foreground` + `dev_banner` |

### Data-Flow Trace (Level 4)

Not applicable — this phase produces icon assets and a compile-time boolean flag. There is no runtime data source to trace. The `isDevBuild` value is a build-time constant (Android) or system-property read at startup (desktop); it flows directly into the conditional rendering — no database, fetch, or async state involved.

### Behavioral Spot-Checks

Step 7b: SKIPPED for APK install/device-side checks (requires running device or emulator). Desktop rendering also requires a running JVM app. The following were verified statically:

| Behavior | Check | Result | Status |
|----------|-------|--------|--------|
| `BuildConfig.IS_DEV` = false in release | `grep buildConfigField build.gradle.kts` | `"false"` in defaultConfig | ✓ PASS |
| `BuildConfig.IS_DEV` = true in devRelease | `grep buildConfigField build.gradle.kts` | `"true"` in devRelease flavor | ✓ PASS |
| DEV badge gated on isDevBuild in OnboardingScreen | `grep if.*isDevBuild OnboardingScreen.kt` | Line 275 match | ✓ PASS |
| ic_launcher.xml uses dev foreground | `cat ic_launcher.xml` | `ic_launcher_foreground_dev` present | ✓ PASS |
| Desktop isDevBuild defaults to false | `cat Platform.jvm.kt` | Property absent → `false` | ✓ PASS |

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|-------------|-------------|--------|----------|
| DEV-01 | 22-01-PLAN.md | Dev release icon and label differentiation | ✓ SATISFIED | All artifacts wired and substantive; see above |

**Note on DEV-01:** This requirement ID is referenced in the PLAN frontmatter and ROADMAP but is **not defined in `.planning/REQUIREMENTS.md`**. REQUIREMENTS.md covers the v1.9.0 milestone (NOTIF-01/02, SAVE-02, LIST-02, FILT-04, UI-01, UX-01, UX-02, NFR-01/02/03) and does not include a DEV-01 entry. DEV-01 is a developer-tooling concern orthogonal to the user-facing v1.9.0 bug fixes. This is a documentation gap — the requirement is effectively defined inline in the ROADMAP ("Add a distinct app icon and 'DEV' label for dev/debug builds") and the implementation fully satisfies that definition. No functional gap.

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| `ic_launcher_foreground_dev.xml` | — | Only 5 lines | ℹ️ Info | Not a stub — it is a layer-list compositing two existing drawables by reference; this is the correct and minimal implementation |

No blockers. No TODO/FIXME/placeholder comments found in phase files. No hardcoded empty state that flows to rendering.

### Human Verification Required

#### 1. Android launcher icon DEV banner

**Test:** Build and install the devRelease APK (`./gradlew installDevRelease`) on an Android device and check the home screen.
**Expected:** The Karakept Dev app icon shows a red horizontal DEV banner in the lower portion of the icon. The production/debug icon (if installed separately) shows no DEV banner.
**Why human:** Adaptive icon composition is performed by the Android launcher — static analysis of the XML files confirms correct wiring but cannot confirm visual rendering.

#### 2. Onboarding DEV badge (Android)

**Test:** Open the devRelease app with fresh data (clear app data or first install) and reach the onboarding welcome step.
**Expected:** A small red "DEV" pill badge appears at the bottom-right of the bookmark icon graphic on the welcome screen.
**Why human:** Compose conditional rendering verified statically but actual badge appearance (size, position, color) requires visual confirmation.

#### 3. Desktop DEV mode (window title, dock icon, tray)

**Test:** Run `./gradlew :composeApp:run -Pdev=true` (with JAVA_HOME set to JDK 21).
**Expected:** Window title reads "Karakept (DEV)", macOS dock icon shows a red DEV banner overlay, system tray tooltip shows "Karakept (DEV)", macOS app name shows "Karakept (DEV)".
**Why human:** Programmatic AWT icon overlay (`overlayDevBanner`) and window title logic are code-verified but visual correctness of the icon overlay requires running the desktop app.

### Gaps Summary

No functional gaps. All automated checks pass:
- `dev_banner.xml` — substantive red-fill vector with DEV letter paths
- `ic_launcher_foreground_dev.xml` — layer-list wiring foreground + banner
- `ic_launcher.xml` — adaptive icon pointing to dev foreground
- `Platform.kt` — expect declaration present
- `Platform.android.kt` — actual reads `BuildConfig.IS_DEV`
- `Platform.jvm.kt` — actual defaults to false via system property
- `build.gradle.kts` — `buildConfig = true`; `IS_DEV = false` in defaultConfig; `IS_DEV = true` in devRelease flavor
- `OnboardingScreen.kt` — imports `isDevBuild` and gates DEV badge behind it

One documentation note: DEV-01 is not formally defined in REQUIREMENTS.md; it appears only in the ROADMAP. This does not affect the implementation but means the requirement is orphaned from the canonical requirements document.

Status is `human_needed` because visual rendering of icon overlays and Compose badges cannot be verified without a running device or emulator.

---

_Verified: 2026-03-31_
_Verifier: Claude (gsd-verifier)_
