---
phase: 7
slug: 07-ui-tests
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-03-24
---

# Phase 7 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | Kotlin Test + Robolectric 4.14 + Compose UI Test |
| **Config file** | `composeApp/build.gradle.kts` (androidUnitTest sourceSet) |
| **Quick run command** | `./gradlew :composeApp:testDebugUnitTest --tests "com.karakept.app.*"` |
| **Full suite command** | `./gradlew :composeApp:testDebugUnitTest` |
| **Estimated runtime** | ~60 seconds |

---

## Sampling Rate

- **After every task commit:** Run `./gradlew :composeApp:testDebugUnitTest --tests "com.karakept.app.*"`
- **After every plan wave:** Run `./gradlew :composeApp:testDebugUnitTest`
- **Before `/gsd:verify-work`:** Full suite must be green
- **Max feedback latency:** 90 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Scenario | Test Type | Automated Command | File Exists | Status |
|---------|------|------|----------|-----------|-------------------|-------------|--------|
| 07-W0-01 | 01 | 0 | Build infra | build | `./gradlew :composeApp:testDebugUnitTest` | ❌ W0 | ⬜ pending |
| 07-01-01 | 01 | 1 | FILT-01 select-all | unit | `./gradlew :composeApp:testDebugUnitTest --tests "*.MainScreenSelectAllTest"` | ❌ W0 | ⬜ pending |
| 07-01-02 | 01 | 1 | FILT-02 counters | unit | `./gradlew :composeApp:testDebugUnitTest --tests "*.MainScreenCountersTest"` | ❌ W0 | ⬜ pending |
| 07-01-03 | 01 | 1 | FILT-03 pull-to-refresh | unit | `./gradlew :composeApp:testDebugUnitTest --tests "*.HighlightsPullToRefreshTest"` | ❌ W0 | ⬜ pending |
| 07-02-01 | 02 | 2 | READER-03/04 scroll-to-top | compose-ui | `./gradlew :composeApp:testDebugUnitTest --tests "*.BookmarkViewerScrollToTopTest"` | ❌ W0 | ⬜ pending |
| 07-02-02 | 02 | 2 | SAVE-01/02 back-nav | activity | `./gradlew :composeApp:testDebugUnitTest --tests "*.BookmarkSavingActivityTest"` | ❌ W0 | ⬜ pending |
| 07-02-03 | 02 | 2 | Regression: scroll position | unit | `./gradlew :composeApp:testDebugUnitTest --tests "*.ScrollPositionRegressionTest"` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `composeApp/build.gradle.kts` — add `androidUnitTest` source set + Robolectric + compose-ui-test deps
- [ ] `gradle/libs.versions.toml` — add robolectric, ui-test-junit4-android version catalog entries
- [ ] Verify `./gradlew :composeApp:testDebugUnitTest` runs without compilation errors before writing tests

*Wave 0 is the build infrastructure task — all test files are created in subsequent waves.*

---

## Manual-Only Verifications

| Behavior | Scenario | Why Manual | Test Instructions |
|----------|----------|------------|-------------------|
| Second share intent creates truly fresh state | SAVE-02 | Activity process isolation is hard to simulate in Robolectric | Launch app, share URL, save bookmark, close app fully, share second URL, verify SaveActivity has empty state |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 90s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
