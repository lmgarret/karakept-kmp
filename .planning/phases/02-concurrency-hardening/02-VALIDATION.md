---
phase: 2
slug: concurrency-hardening
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-03-21
---

# Phase 2 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | kotlin.test + kotlinx-coroutines-test |
| **Config file** | composeApp/build.gradle.kts (testImplementation blocks) |
| **Quick run command** | `./gradlew :composeApp:desktopTest --tests "*MainScreenModel*"` |
| **Full suite command** | `./gradlew :composeApp:desktopTest` |
| **Estimated runtime** | ~30 seconds |

---

## Sampling Rate

- **After every task commit:** Run `./gradlew :composeApp:desktopTest --tests "*MainScreenModel*"`
- **After every plan wave:** Run `./gradlew :composeApp:desktopTest`
- **Before `/gsd:verify-work`:** Full suite must be green
- **Max feedback latency:** 30 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|-----------|-------------------|-------------|--------|
| 02-01-01 | 01 | 1 | CONC-01 | code audit | `grep -c "sealed.*InitState\|is InitState" MainScreenModel.kt` | ❌ W0 | ⬜ pending |
| 02-01-02 | 01 | 1 | CONC-01 | code audit | `grep -c "updateAccumulatedBookmarks\|mutex" MainScreenModel.kt` | ❌ W0 | ⬜ pending |
| 02-01-03 | 01 | 1 | CONC-02 | code audit | `grep -c "Mutex\|withLock" BookmarkActionsRepository.kt` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

*Existing infrastructure covers all phase requirements — this is a refactoring phase verified by code audit and existing test suite passing.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| No duplicate bookmark loads on startup | CONC-01 | Requires observing init log output at runtime | Launch app, check logs for exactly one "Initial load" entry per startup |
| Read/unread toggling under rapid clicks | CONC-02 | Requires UI interaction timing | Rapidly toggle read/unread on same bookmark, verify final state matches last action |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 30s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
