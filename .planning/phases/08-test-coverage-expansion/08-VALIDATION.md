---
phase: 8
slug: test-coverage-expansion
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-03-25
---

# Phase 8 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | kotlin-test + JUnit 4 + mockk 1.13.12 |
| **Config file** | `composeApp/build.gradle.kts` |
| **Quick run command** | `./gradlew :composeApp:desktopTest` |
| **Full suite command** | `./gradlew :composeApp:desktopTest :composeApp:testDebugUnitTest` |
| **Estimated runtime** | ~30–60 seconds (desktop), ~90s (full suite) |

---

## Sampling Rate

- **After every task commit:** Run `./gradlew :composeApp:desktopTest`
- **After every plan wave:** Run `./gradlew :composeApp:desktopTest :composeApp:testDebugUnitTest`
- **Before `/gsd:verify-work`:** Full suite must be green
- **Max feedback latency:** 60 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|-----------|-------------------|-------------|--------|
| 8-01-01 | 01 | 1 | COV-01 | unit | `./gradlew :composeApp:desktopTest --tests "com.karakept.app.utils.ReadingTimeCalculatorTest"` | ❌ W0 | ⬜ pending |
| 8-01-02 | 01 | 1 | COV-01 | unit | `./gradlew :composeApp:desktopTest --tests "com.karakept.app.utils.HtmlSanitizerTest"` | ❌ W0 | ⬜ pending |
| 8-01-03 | 01 | 1 | COV-01 | unit | `./gradlew :composeApp:desktopTest --tests "com.karakept.app.utils.DateUtilsTest"` | ❌ W0 | ⬜ pending |
| 8-01-04 | 01 | 1 | COV-01 | unit | `./gradlew :composeApp:desktopTest --tests "com.karakept.app.utils.FaviconUtilsTest"` | ❌ W0 | ⬜ pending |
| 8-01-05 | 01 | 1 | COV-01 | unit | `./gradlew :composeApp:desktopTest --tests "com.karakept.app.utils.AssetUrlUtilsTest"` | ❌ W0 | ⬜ pending |
| 8-01-06 | 01 | 1 | COV-02 | unit | `./gradlew :composeApp:desktopTest --tests "com.karakept.app.ui.components.reader.HighlightOffsetFinderTest"` | ❌ W0 | ⬜ pending |
| 8-01-07 | 01 | 1 | COV-03 | unit | `./gradlew :composeApp:desktopTest --tests "com.karakept.app.data.model.ListSyncConfigTest"` | ❌ W0 | ⬜ pending |
| 8-02-01 | 02 | 2 | COV-04 | unit | `./gradlew :composeApp:desktopTest --tests "com.karakept.app.domain.action.BookmarkActionControllerTest"` | ❌ W0 | ⬜ pending |
| 8-02-02 | 02 | 2 | COV-06 | unit | `./gradlew :composeApp:desktopTest --tests "com.karakept.app.data.repository.BookmarkActionsRepositorySyncTest"` | ❌ W0 | ⬜ pending |
| 8-03-01 | 03 | 3 | COV-05 | unit | `./gradlew :composeApp:desktopTest --tests "com.karakept.app.data.repository.BookmarkSyncPipelineTest"` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `composeApp/src/commonTest/.../utils/ReadingTimeCalculatorTest.kt` — stubs for COV-01
- [ ] `composeApp/src/commonTest/.../utils/HtmlSanitizerTest.kt` — stubs for COV-01
- [ ] `composeApp/src/commonTest/.../utils/DateUtilsTest.kt` — stubs for COV-01
- [ ] `composeApp/src/commonTest/.../utils/FaviconUtilsTest.kt` — stubs for COV-01
- [ ] `composeApp/src/commonTest/.../utils/AssetUrlUtilsTest.kt` — stubs for COV-01
- [ ] `composeApp/src/commonTest/.../ui/components/reader/HighlightOffsetFinderTest.kt` — stubs for COV-02
- [ ] `composeApp/src/commonTest/.../data/model/ListSyncConfigTest.kt` — stubs for COV-03
- [ ] `composeApp/src/commonTest/.../domain/action/BookmarkActionControllerTest.kt` — stubs for COV-04
- [ ] `composeApp/src/commonTest/.../data/repository/BookmarkSyncPipelineTest.kt` — stubs for COV-05
- [ ] `composeApp/src/commonTest/.../data/repository/BookmarkActionsRepositorySyncTest.kt` — stubs for COV-06

---

## Manual-Only Verifications

*All phase behaviors have automated verification.*

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 60s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
