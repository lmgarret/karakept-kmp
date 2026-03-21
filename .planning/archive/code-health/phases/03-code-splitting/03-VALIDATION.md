---
phase: 3
slug: code-splitting
status: approved
nyquist_compliant: true
wave_0_complete: true
created: 2026-03-21
---

# Phase 3 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | kotlin.test + kotlinx-coroutines-test |
| **Config file** | composeApp/build.gradle.kts (testImplementation blocks) |
| **Quick run command** | `./gradlew :composeApp:desktopTest` |
| **Full suite command** | `./gradlew :composeApp:desktopTest` |
| **Estimated runtime** | ~30 seconds |

---

## Sampling Rate

- **After every task commit:** Run `./gradlew :composeApp:desktopTest`
- **After every plan wave:** Run `./gradlew :composeApp:desktopTest`
- **Before `/gsd:verify-work`:** Full suite must be green
- **Max feedback latency:** 30 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|-----------|-------------------|-------------|--------|
| 03-01-T1 | 01 | 1 | SPLIT-01 | code audit | `wc -l ui/screens/main/*.kt` (all < 500) | N/A (code audit) | ⬜ pending |
| 03-01-T2 | 01 | 1 | SPLIT-02 | code audit | `wc -l ui/screens/MainScreenModel*.kt` | N/A (code audit) | ⬜ pending |
| 03-02-T1 | 02 | 1 | SPLIT-03 | code audit | `wc -l ui/screens/viewer/*.kt` (all < 500) | N/A (code audit) | ⬜ pending |
| 03-03-T1 | 03 | 2 | SPLIT-04 | code audit | `wc -l data/repository/*.kt` (all < 500) | N/A (code audit) | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

*Existing infrastructure covers all phase requirements — this is a refactoring phase verified by code audit and existing test suite passing.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| App compiles after all splits | ALL | Build verification | Run `./gradlew :composeApp:desktopTest` and verify green |
| No visual regressions | ALL | Requires UI inspection | Launch app, verify main screen, viewer, settings all render correctly |

---

## Validation Sign-Off

- [x] All tasks have `<automated>` verify or Wave 0 dependencies
- [x] Sampling continuity: no 3 consecutive tasks without automated verify
- [x] Wave 0 covers all MISSING references (none — code audit only)
- [x] No watch-mode flags
- [x] Feedback latency < 30s
- [x] `nyquist_compliant: true` set in frontmatter

**Approval:** approved 2026-03-21
