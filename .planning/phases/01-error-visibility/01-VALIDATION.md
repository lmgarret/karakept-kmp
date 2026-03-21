---
phase: 1
slug: error-visibility
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-03-21
---

# Phase 1 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | kotlin-test + MockK |
| **Config file** | `composeApp/build.gradle.kts` (test dependencies in `commonTest` sourceSet) |
| **Quick run command** | `./gradlew :composeApp:desktopTest --tests "com.karakept.app.*"` |
| **Full suite command** | `./gradlew :composeApp:desktopTest` |
| **Estimated runtime** | ~30 seconds |

---

## Sampling Rate

- **After every task commit:** `grep -r "printStackTrace\|!!" composeApp/src/ --include="*.kt"` to verify count decreases
- **After every plan wave:** `./gradlew :composeApp:desktopTest`
- **Before `/gsd:verify-work`:** Full suite must be green + all grep checks return 0
- **Max feedback latency:** 30 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|-----------|-------------------|-------------|--------|
| 01-01-01 | 01 | 1 | ERR-01 | static analysis (grep) | `grep -r "printStackTrace" composeApp/src/ --include="*.kt"` returns 0 results | N/A - grep check | ⬜ pending |
| 01-01-02 | 01 | 1 | ERR-03 | static analysis (grep) | `grep -n "println" composeApp/src/commonMain/kotlin/com/karakept/app/data/remote/RemoteDataSource.kt` returns 0 results | N/A - grep check | ⬜ pending |
| 01-01-03 | 01 | 1 | ERR-04 | static analysis (grep) | Noisy progress logs removed from reader | N/A - grep check | ⬜ pending |
| 01-02-01 | 02 | 1 | ERR-02 | unit | `./gradlew :composeApp:desktopTest --tests "*SnackbarManager*"` | ❌ W0 | ⬜ pending |
| 01-02-02 | 02 | 1 | NULL-01 | static analysis (grep) | `grep -rn "!!" composeApp/src/commonMain/ --include="*.kt"` returns 0 relevant results | N/A - grep check | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `composeApp/src/commonTest/kotlin/com/karakept/app/domain/action/ActionSnackbarManagerTest.kt` — stubs for ERR-02 (MessageWithAction variant)
- [ ] Verify existing tests still pass after null safety changes — run full suite

*Existing infrastructure covers most phase requirements via grep-based static analysis.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Snackbar appears on sync failure | ERR-02 | Requires running app with network disconnected | 1. Start app 2. Disconnect network 3. Trigger sync 4. Verify snackbar appears with retry button |
| Retry button re-attempts operation | ERR-02 | Requires user interaction | 1. See error snackbar 2. Tap retry 3. Verify operation re-attempts |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 30s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
