---
phase: 05
slug: security-hardening
status: approved
nyquist_compliant: true
wave_0_complete: true
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
| 05-01-01 | 01 | 1 | SEC-01 | unit | `./gradlew :composeApp:desktopTest --tests "*.HtmlArchiveProcessorTest"` | ✅ | ✅ green |
| 05-02-01 | 02 | 1 | SEC-02 | unit | `./gradlew :composeApp:desktopTest --tests "*.SecureCredentialStoreTest"` | ✅ | ✅ green |
| 05-02-02 | 02 | 1 | SEC-02 | integration | `./gradlew :composeApp:desktopTest --tests "*.ServerRepositoryMigrationTest"` | ✅ | ✅ green |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [x] `composeApp/src/commonTest/kotlin/com/karakept/app/utils/HtmlArchiveProcessorTest.kt` — 10 tests for SEC-01 (dangerous element stripping)
- [x] `composeApp/src/desktopTest/kotlin/com/karakept/app/data/secure/SecureCredentialStoreTest.kt` — 5 tests for SEC-02 (PKCS12 roundtrip)
- [x] `composeApp/src/desktopTest/kotlin/com/karakept/app/data/repository/ServerRepositoryMigrationTest.kt` — 6 tests for SEC-02 (migration flow)

*All test files created during phase execution.*

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

**Approval:** approved

---

## Validation Audit 2026-03-21

| Metric | Count |
|--------|-------|
| Gaps found | 0 |
| Resolved | 0 |
| Escalated | 0 |

All 3 test files already exist from phase execution (10 + 5 + 6 = 21 tests). Fixed stale test class name in verification map (HtmlSanitizerTest → HtmlArchiveProcessorTest).
