---
phase: 4
slug: bookmark-saving-activity
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-03-23
---

# Phase 4 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | Manual (no androidTest infrastructure) |
| **Config file** | none |
| **Quick run command** | `./gradlew assembleDebug` |
| **Full suite command** | `./gradlew assembleDebug` |
| **Estimated runtime** | ~30 seconds |

---

## Sampling Rate

- **After every task commit:** Run `./gradlew assembleDebug`
- **After every plan wave:** Run `./gradlew assembleDebug` + manual device verification
- **Before `/gsd:verify-work`:** Full suite must be green + manual UAT complete
- **Max feedback latency:** 60 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|-----------|-------------------|-------------|--------|
| 04-01-01 | 01 | 1 | SAVE-01 | build | `./gradlew assembleDebug` | ✅ | ⬜ pending |
| 04-01-02 | 01 | 1 | SAVE-02 | build | `./gradlew assembleDebug` | ✅ | ⬜ pending |
| 04-01-03 | 01 | 2 | SAVE-01 | manual | device test | N/A | ⬜ pending |
| 04-01-04 | 01 | 2 | SAVE-02 | manual | device test | N/A | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

*Existing infrastructure covers all phase requirements (build-only automated verification; all behavioral checks are manual).*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| After save, pressing back goes to bookmark list | SAVE-01 | Requires real share intent + back button press on device | Share URL from Chrome → Karakept, save, press back — verify landing on bookmark list |
| Second share shows fresh state | SAVE-02 | Requires sequential share intents to trigger `onNewIntent` | Share URL → save → share different URL immediately — verify new URL shown, no stale data |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 60s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
