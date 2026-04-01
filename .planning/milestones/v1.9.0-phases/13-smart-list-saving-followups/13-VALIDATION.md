---
phase: 13
slug: smart-list-saving-followups
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-03-26
---

# Phase 13 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 4 + MockK + Robolectric 4.14 |
| **Config file** | `composeApp/build.gradle.kts` (androidUnitTest / commonTest blocks) |
| **Quick run command** | `./gradlew :composeApp:testDebugUnitTest --tests "*SAVE02*" --tests "*LIST02*"` |
| **Full suite command** | `./gradlew :composeApp:testDebugUnitTest :composeApp:desktopTest` |
| **Estimated runtime** | ~60-90 seconds |

---

## Sampling Rate

- **After every task commit:** Run quick test command targeting the new test class
- **After every plan wave:** Run full suite command
- **Before `/gsd:verify-work`:** Full suite must be green
- **Max feedback latency:** ~90 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|-----------|-------------------|-------------|--------|
| 13-01-01 | 01 | 1 | SAVE-02 | androidUnitTest | `./gradlew :composeApp:testDebugUnitTest --tests "*SAVE02*"` | ❌ W0 | ⬜ pending |
| 13-01-02 | 01 | 2 | SAVE-02 | androidUnitTest | `./gradlew :composeApp:testDebugUnitTest --tests "*SAVE02*"` | ❌ W0 | ⬜ pending |
| 13-02-01 | 02 | 1 | LIST-02 | androidUnitTest | `./gradlew :composeApp:testDebugUnitTest --tests "*LIST02*"` | ❌ W0 | ⬜ pending |
| 13-02-02 | 02 | 2 | LIST-02 | androidUnitTest | `./gradlew :composeApp:testDebugUnitTest --tests "*LIST02*"` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `composeApp/src/androidUnitTest/kotlin/com/karakept/app/ui/screens/Save02RegressionTest.kt` — stubs for SAVE-02
- [ ] `composeApp/src/androidUnitTest/kotlin/com/karakept/app/ui/screens/List02RegressionTest.kt` — stubs for LIST-02

*Existing infrastructure (MockK, Robolectric, BaseRepositoryTest) covers all other requirements.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Back from viewer shows populated list in BookmarkSavingActivity | SAVE-02 | Requires real Android Activity lifecycle with Koin DI context | Share a URL, tap Back from viewer, verify list and counters visible |
| Smart list visually removes bookmark after quick action | LIST-02 | Requires real network round-trip to server | Add bookmark to regular list via quick action while viewing a smart list; verify bookmark disappears |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 90s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
