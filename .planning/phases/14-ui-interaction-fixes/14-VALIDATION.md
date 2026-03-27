---
phase: 14
slug: ui-interaction-fixes
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-03-27
---

# Phase 14 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | Robolectric 4.14 + Compose UI Test (androidUnitTest) |
| **Config file** | `composeApp/build.gradle.kts` (testOptions block) |
| **Quick run command** | `./gradlew :composeApp:testDebugUnitTest --tests "com.karakept.app.ui.screens.*"` |
| **Full suite command** | `./gradlew :composeApp:testDebugUnitTest` |
| **Estimated runtime** | ~45 seconds |

---

## Sampling Rate

- **After every task commit:** Run quick suite (UI screen tests)
- **After every plan wave:** Run full suite
- **Before `/gsd:verify-work`:** Full suite must be green
- **Max feedback latency:** 45 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|-----------|-------------------|-------------|--------|
| 14-01-01 | 01 | 1 | FILT-04 | UI test | `./gradlew :composeApp:testDebugUnitTest --tests "*HighlightsPullToRefresh*"` | ✅ exists | ⬜ pending |
| 14-01-02 | 01 | 1 | UI-01 | UI test | `./gradlew :composeApp:testDebugUnitTest --tests "*ScrollToTop*"` | ✅ exists | ⬜ pending |
| 14-02-01 | 02 | 1 | FILT-04 | unit | `./gradlew :composeApp:testDebugUnitTest --tests "*BookmarkListContent*"` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

*Existing infrastructure covers all phase requirements.* Robolectric + Compose UI Test already configured. Existing test files:
- `HighlightsPullToRefreshTest.kt` — extend for compact layout PTR
- `ScrollToTopVisibilityTest.kt` — extend for scroll offset verification

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Pull-to-refresh gesture on compact Highlights | FILT-04 | Robolectric may not fully simulate swipe gesture physics | 1. Open app on phone, 2. Navigate to Highlights in compact layout, 3. Pull down — spinner appears and refresh triggers |
| Scroll-to-top reaches pixel 0 | UI-01 | Sub-pixel rendering differences on real devices | 1. Scroll down in bookmark list, 2. Tap scroll-to-top FAB, 3. Verify first item fully visible with no offset |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 45s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
