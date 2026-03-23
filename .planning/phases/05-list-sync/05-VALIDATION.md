---
phase: 05
slug: list-sync
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-03-23
---

# Phase 05 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit + kotlinx-coroutines-test (desktop target) |
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
| 05-01-01 | 01 | 1 | LIST-01 | unit | Manual verification (UI state test) | ❌ W0 | ⬜ pending |
| 05-01-02 | 01 | 1 | LIST-01 | unit | Manual verification (UI state test) | ❌ W0 | ⬜ pending |
| 05-02-01 | 02 | 1 | LIST-02 | unit | Manual verification (sync pipeline test) | ❌ W0 | ⬜ pending |
| 05-02-02 | 02 | 1 | LIST-02 | unit | Manual verification | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

Both fixes are small, well-scoped changes. The existing test infrastructure is integration-focused (tests against live Karakeep server) and not suitable for unit-level validation of these fixes. Manual verification via app testing is recommended.

*Existing infrastructure covers all phase requirements — no new test files needed for Wave 0.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Bookmark disappears from list view after quick-action removal | LIST-01 | UI state flow; integration test infrastructure is server-focused | Remove bookmark from a list via quick action while viewing that list; verify it disappears immediately |
| Bookmark stays visible in All Bookmarks after list removal | LIST-01 | UI state flow context check | Remove bookmark from a list while viewing All Bookmarks; verify bookmark stays visible |
| Content downloaded for offline-enabled list bookmarks on sync | LIST-02 | Requires live server + actual sync cycle | Enable offline sync for a list, trigger sync, verify entries available for offline reading |
| Bookmarks with existing content are skipped during offline sync | LIST-02 | Requires live server + actual sync cycle | Verify sync does not re-fetch content for bookmarks already having content in offline-enabled list |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 60s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
