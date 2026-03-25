---
phase: 05
slug: list-sync
status: draft
nyquist_compliant: true
wave_0_complete: true
created: 2026-03-23
---

# Phase 05 -- Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit + kotlinx-coroutines-test + mockk (desktop target) |
| **Config file** | `composeApp/build.gradle.kts` |
| **Quick run command** | `./gradlew :composeApp:assembleDebug` |
| **Full suite command** | `./gradlew :composeApp:desktopTest -x kspCommonMainKotlinMetadata` |
| **Estimated runtime** | ~60 seconds |

---

## Sampling Rate

- **After every task commit:** Run `./gradlew :composeApp:assembleDebug`
- **After every plan wave:** Run `./gradlew :composeApp:desktopTest -x kspCommonMainKotlinMetadata`
- **Before `/gsd:verify-work`:** Full suite must be green
- **Max feedback latency:** 60 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|-----------|-------------------|-------------|--------|
| 05-00-01 | 00 | 0 | LIST-01 | unit (scaffold) | `./gradlew :composeApp:desktopTest --tests "*.RemoveBookmarkFromListTest" -x kspCommonMainKotlinMetadata` | Created by W0 | pending |
| 05-00-02 | 00 | 0 | LIST-02 | unit (scaffold) | `./gradlew :composeApp:desktopTest --tests "*.SyncContentOfflineTest" -x kspCommonMainKotlinMetadata` | Created by W0 | pending |
| 05-01-01 | 01 | 1 | LIST-01 | unit | `./gradlew :composeApp:desktopTest --tests "*.RemoveBookmarkFromListTest" -x kspCommonMainKotlinMetadata` | Yes (W0) | pending |
| 05-01-02 | 01 | 1 | LIST-02 | unit | `./gradlew :composeApp:desktopTest --tests "*.SyncContentOfflineTest" -x kspCommonMainKotlinMetadata` | Yes (W0) | pending |

*Status: pending / green / red / flaky*

---

## Wave 0 Requirements

Wave 0 (Plan 05-00) creates two test files:

| Test File | Requirement | Behaviors Tested |
|-----------|-------------|------------------|
| `RemoveBookmarkFromListTest.kt` | LIST-01 | Conditional filtering when viewing target list vs. listIds update when viewing other context |
| `SyncContentOfflineTest.kt` | LIST-02 | Content fetch for offline-enabled lists, skip for existing content, child list expansion, no-op when no offline lists |

Tests use mockk + kotlinx-coroutines-test, consistent with existing `PendingActionQueueTest.kt` patterns.

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| End-to-end bookmark removal from list view | LIST-01 | Full UI flow with real navigation | Remove bookmark from a list via quick action while viewing that list; verify it disappears immediately |
| End-to-end content download for offline list | LIST-02 | Requires live server + actual sync cycle | Enable offline sync for a list, trigger sync, verify entries available for offline reading |

---

## Validation Sign-Off

- [x] All tasks have `<automated>` verify commands pointing to Wave 0 test files
- [x] Sampling continuity: no 3 consecutive tasks without automated verify
- [x] Wave 0 covers all MISSING references (Plan 05-00 creates both test files)
- [x] No watch-mode flags
- [x] Feedback latency < 60s
- [x] `nyquist_compliant: true` set in frontmatter

**Approval:** approved
