---
phase: 03
slug: reader-ux
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-03-23
---

# Phase 03 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | Kotlin Test (multiplatform) |
| **Config file** | `composeApp/build.gradle.kts` |
| **Quick run command** | `./gradlew :composeApp:jvmTest --tests "*StoredSettingsSerializationTest*"` |
| **Full suite command** | `./gradlew :composeApp:jvmTest` |
| **Estimated runtime** | ~30 seconds (quick), ~2 minutes (full) |

---

## Sampling Rate

- **After every task commit:** Run `./gradlew :composeApp:jvmTest --tests "*StoredSettingsSerializationTest*"`
- **After every plan wave:** Run `./gradlew :composeApp:jvmTest`
- **Before `/gsd:verify-work`:** Full suite must be green
- **Max feedback latency:** 30 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|-----------|-------------------|-------------|--------|
| 03-settings-unit | TBD | W0 | READER-04 | unit | `./gradlew :composeApp:jvmTest --tests "*StoredSettingsSerializationTest*scrollToTop*"` | ❌ W0 | ⬜ pending |
| 03-reader-01 | TBD | 1+ | READER-01 | manual | N/A | N/A | ⬜ pending |
| 03-reader-02 | TBD | 1+ | READER-02 | manual | N/A | N/A | ⬜ pending |
| 03-reader-03 | TBD | 1+ | READER-03 | manual | N/A | N/A | ⬜ pending |
| 03-reader-04 | TBD | 1+ | READER-04 | unit+manual | see above | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `composeApp/src/commonTest/kotlin/com/karakept/app/data/repository/StoredSettingsSerializationTest.kt` — add `scrollToTopEnabled` default value test and JSON round-trip test (no new file needed — add to existing)

*Wave 0 is minimal: one existing test file extended, not a new infrastructure.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Bookmark list scroll position restores after closing reader | READER-01 | No UI test infrastructure (Phase 07) | Open any bookmark, scroll list to middle, open reader, scroll in reader, press back, verify list at same position |
| Info button appears in overflow menu when hero scrolls away | READER-02 | No Compose UI test infra (Phase 07) | Open reader, scroll past hero, open three-dots menu, verify "Details" item present; scroll back to hero, verify item absent |
| Scroll-to-top button appears/hides correctly | READER-03 | No Compose UI test infra (Phase 07) | Scroll past hero: button invisible. Scroll down then up: button appears. Scroll to end: button always visible. Tap: smooth scroll to top |
| Scroll-to-top toggle in Reader Appearance panel | READER-04 | UI behavior | Open reader → Appearance panel → toggle "Scroll-to-top button" off → button disappears immediately; toggle on → reappears; close and reopen reader → state persists |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 30s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
