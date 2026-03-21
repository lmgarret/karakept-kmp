---
phase: 04
slug: test-coverage
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-03-21
---

# Phase 04 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 4 + kotlin-test + MockK + kotlinx-coroutines-test |
| **Config file** | composeApp/build.gradle.kts (testImplementation deps) |
| **Quick run command** | `./gradlew :composeApp:desktopTest --tests "com.karakept.app.data.*"` |
| **Full suite command** | `./gradlew :composeApp:desktopTest` |
| **Estimated runtime** | ~30 seconds |

---

## Sampling Rate

- **After every task commit:** Run quick test command
- **After every plan wave:** Run full suite command
- **Before `/gsd:verify-work`:** Full suite must be green
- **Max feedback latency:** 30 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | Status |
|---------|------|------|-------------|-----------|-------------------|--------|
| 04-01-01 | 01 | 1 | TEST-01 | unit | `./gradlew :composeApp:desktopTest --tests "*.ActionQueueTest"` | ⬜ pending |
| 04-02-01 | 02 | 1 | TEST-02 | unit | `./gradlew :composeApp:desktopTest --tests "*.FilterConfigTest"` | ⬜ pending |
| 04-02-02 | 02 | 1 | TEST-03 | unit | `./gradlew :composeApp:desktopTest --tests "*.ReadingProgressRaceTest"` | ⬜ pending |

---

## Wave 0 Requirements

Existing infrastructure covers all phase requirements. MockK and coroutines-test already in build.gradle.kts.

---

## Manual-Only Verifications

All phase behaviors have automated verification (this IS the test coverage phase).

---

## Validation Sign-Off

- [ ] All tasks have automated verify
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 30s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
