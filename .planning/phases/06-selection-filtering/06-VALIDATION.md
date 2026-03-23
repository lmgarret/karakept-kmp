---
phase: 06
slug: selection-filtering
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-03-23
---

# Phase 06 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 4 / Kotlin test (Android/KMP) |
| **Config file** | `composeApp/build.gradle.kts` |
| **Quick run command** | `./gradlew :composeApp:testDebugUnitTest --tests "com.karakept.app.*"` |
| **Full suite command** | `./gradlew :composeApp:testDebugUnitTest` |
| **Estimated runtime** | ~60 seconds |

---

## Sampling Rate

- **After every task commit:** Run `./gradlew :composeApp:testDebugUnitTest --tests "com.karakept.app.*"`
- **After every plan wave:** Run `./gradlew :composeApp:testDebugUnitTest`
- **Before `/gsd:verify-work`:** Full suite must be green
- **Max feedback latency:** 60 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|-----------|-------------------|-------------|--------|
| 06-01-01 | 01 | 1 | FILT-01 | unit | `./gradlew :composeApp:testDebugUnitTest --tests "*.BookmarkRepositoryTest"` | ❌ W0 | ⬜ pending |
| 06-01-02 | 01 | 1 | FILT-01 | unit | `./gradlew :composeApp:testDebugUnitTest --tests "*.MainScreenModelBatchTest"` | ❌ W0 | ⬜ pending |
| 06-01-03 | 01 | 2 | FILT-02 | unit | `./gradlew :composeApp:testDebugUnitTest --tests "*.MainScreenModelTest"` | ❌ W0 | ⬜ pending |
| 06-01-04 | 01 | 2 | FILT-03 | manual | n/a | n/a | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `composeApp/src/test/.../BookmarkRepositoryTest.kt` — stubs for FILT-01 (getAllBookmarks unpaged)
- [ ] `composeApp/src/test/.../MainScreenModelBatchTest.kt` — stubs for FILT-01 (selectAll correctness)
- [ ] `composeApp/src/test/.../MainScreenModelTest.kt` — stubs for FILT-02 (builtin item counters)

*Existing infrastructure (JUnit/Kotlin test) covers the framework; only test stubs need adding.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Pull-to-refresh triggers sync on Highlights view | FILT-03 | Requires running app on device/emulator | Open Highlights view, pull down, verify spinner appears and list updates |
| Drawer filter counters update after sync | FILT-02 | Requires live data state changes | Open drawer, check counters match bookmark counts for All/Favorites/Archived/Highlights |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 60s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
