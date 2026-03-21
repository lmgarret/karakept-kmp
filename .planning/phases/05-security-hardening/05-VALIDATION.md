---
phase: 05
slug: security-hardening
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-03-21
---

# Phase 05 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | kotlin.test + MockK (JUnit4 runner on JVM/Desktop) |
| **Config file** | `composeApp/build.gradle.kts` (desktopTest source set) |
| **Quick run command** | `./gradlew :composeApp:desktopTest --tests "*.HtmlSanitizerTest"` |
| **Full suite command** | `./gradlew :composeApp:desktopTest` |
| **Estimated runtime** | ~30 seconds |

---

## Sampling Rate

- **After every task commit:** Run quick test for changed module
- **After every plan wave:** Run full suite
- **Before `/gsd:verify-work`:** Full suite must be green
- **Max feedback latency:** 30 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|-----------|-------------------|-------------|--------|
| 05-01-01 | 01 | 1 | SEC-01 | unit | `./gradlew :composeApp:desktopTest --tests "*.HtmlSanitizerTest"` | ❌ W0 | ⬜ pending |
| 05-02-01 | 02 | 1 | SEC-02 | unit | `./gradlew :composeApp:desktopTest --tests "*.SecureCredentialStoreTest"` | ❌ W0 | ⬜ pending |
| 05-02-02 | 02 | 1 | SEC-02 | integration | `./gradlew :composeApp:desktopTest --tests "*.ServerRepositoryMigrationTest"` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `composeApp/src/desktopTest/kotlin/.../HtmlSanitizerTest.kt` — stubs for SEC-01
- [ ] `composeApp/src/desktopTest/kotlin/.../SecureCredentialStoreTest.kt` — stubs for SEC-02
- [ ] `composeApp/src/desktopTest/kotlin/.../ServerRepositoryMigrationTest.kt` — migration verification

*Existing test infrastructure (kotlin.test + MockK) covers framework requirements.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| WebView renders sanitized HTML correctly on Android | SEC-01 | Android WebView rendering requires device/emulator | Load a bookmark with complex HTML, verify no script execution, formatting preserved |
| Existing server connections work after credential migration | SEC-02 | Requires live server | Add a server, trigger migration, verify sync still works |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 30s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
