---
phase: 10
slug: dialog-and-component-ui-tests
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-03-25
---

# Phase 10 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 4 + Robolectric 4.14 + compose-ui-test-junit4 (androidUnitTest), kotlin-test (commonTest) |
| **Config file** | `composeApp/src/androidUnitTest/resources/robolectric.properties` (sdk=34) |
| **Quick run command** | `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew :composeApp:testDebugUnitTest --tests "com.karakept.app.ui.components.*"` |
| **Full suite command** | `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew :composeApp:testDebugUnitTest :composeApp:desktopTest` |
| **Estimated runtime** | ~30–60 seconds (component-scoped quick run) |

---

## Sampling Rate

- **After every task commit:** Run quick run command for affected test file
- **After every plan wave:** Run full suite command
- **Before `/gsd:verify-work`:** Full suite must be green
- **Max feedback latency:** ~60 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|-----------|-------------------|-------------|--------|
| 10-01-01 | 01 | 0 | TAG-FILTER, TAG-CANADD | extraction | n/a (code change) | ❌ W0 | ⬜ pending |
| 10-01-02 | 01 | 0 | TAG-PARSE | extraction | n/a (code change) | ❌ W0 | ⬜ pending |
| 10-01-03 | 01 | 1 | TAG-FILTER, TAG-CANADD | unit | `./gradlew :composeApp:desktopTest --tests "*.TagEditorLogicTest"` | ❌ W0 | ⬜ pending |
| 10-01-04 | 01 | 1 | TAG-PARSE | unit | `./gradlew :composeApp:desktopTest --tests "*.BookmarkTagsParsingTest"` | ❌ W0 | ⬜ pending |
| 10-01-05 | 01 | 1 | TAG-CHIP-UI | ui | `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew :composeApp:testDebugUnitTest --tests "*.TagChipTest"` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `composeApp/src/commonTest/kotlin/com/karakept/app/ui/components/TagEditorLogicTest.kt` — stubs for TAG-FILTER, TAG-CANADD
- [ ] `composeApp/src/commonTest/kotlin/com/karakept/app/ui/components/BookmarkTagsParsingTest.kt` — stubs for TAG-PARSE
- [ ] `composeApp/src/androidUnitTest/kotlin/com/karakept/app/ui/components/TagChipTest.kt` — stubs for TAG-CHIP-UI
- [ ] Extract `filterTagSuggestions()`, `canAddTag()` from TagEditorDialog.kt as `internal` top-level functions
- [ ] Extract `parseTagString()` from BookmarkTagsDisplay.kt as `internal` top-level function

*Wave 0 covers extraction of testable functions AND creation of test file stubs.*

---

## Manual-Only Verifications

None — all phase behaviors have automated verification.

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 60s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
