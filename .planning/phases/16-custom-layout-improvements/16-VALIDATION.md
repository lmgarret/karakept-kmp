---
phase: 16
slug: custom-layout-improvements
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-03-30
---

# Phase 16 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | kotlin.test + kotlinx-coroutines-test |
| **Config file** | `composeApp/build.gradle.kts` (commonTest dependencies) |
| **Quick run command** | `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home ./gradlew :composeApp:desktopTest --tests "com.karakept.app.*Layout*"` |
| **Full suite command** | `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home ./gradlew :composeApp:desktopTest` |
| **Estimated runtime** | ~60 seconds (full suite) |

---

## Sampling Rate

- **After every task commit:** Run `JAVA_HOME=... ./gradlew :composeApp:desktopTest --tests "com.karakept.app.*Layout*"`
- **After every plan wave:** Run full suite
- **Before `/gsd:verify-work`:** Full suite must be green
- **Max feedback latency:** 60 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|-----------|-------------------|-------------|--------|
| 16-??-01 | TBD | 0 | UX-02a | unit | `./gradlew :composeApp:desktopTest --tests "*LayoutType*"` | W0 | pending |
| 16-??-02 | TBD | 0 | UX-02b/c | unit | `./gradlew :composeApp:desktopTest --tests "*BookmarkLayout*"` | W0 | pending |
| 16-??-03 | TBD | 0 | UX-02d | unit | `./gradlew :composeApp:desktopTest --tests "*Position*"` | W0 | pending |
| 16-??-04 | TBD | 0 | UX-02e | unit | `./gradlew :composeApp:desktopTest --tests "*UrlUtils*"` | W0 | pending |
| 16-03-T1 | 16-03 | 3 | UX-02f | unit (TDD) | `./gradlew :composeApp:desktopTest --tests "*LayoutEditorScreenModel*"` | Plan 16-03 Task 1 creates it | pending |

*Status: pending / green / red / flaky*

---

## Wave 0 Requirements

- [ ] `composeApp/src/commonTest/kotlin/com/karakept/app/data/model/LayoutTypeTest.kt` -- COMPACT_LIST fromString maps to LIST
- [ ] `composeApp/src/commonTest/kotlin/com/karakept/app/data/model/BookmarkLayoutTest.kt` -- built-in preset values, new field defaults, serialization round-trip
- [ ] `composeApp/src/commonTest/kotlin/com/karakept/app/data/model/UrlPositionTest.kt` -- enum fromString for all values
- [ ] `composeApp/src/commonTest/kotlin/com/karakept/app/data/model/UrlDisplayModeTest.kt` -- enum fromString for all values
- [ ] `composeApp/src/commonTest/kotlin/com/karakept/app/data/model/DescriptionPositionTest.kt` -- enum fromString for all values
- [ ] `composeApp/src/commonTest/kotlin/com/karakept/app/ui/utils/UrlUtilsTest.kt` -- domain extraction from various URL formats

**Note:** UX-02f (LayoutEditorScreenModel update functions) is covered by Plan 16-03 Task 1 via TDD -- test file `LayoutEditorScreenModelTest.kt` is created as part of the RED phase before the update functions are implemented.

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Card layout preview updates live as toggles change | UX-02 | Compose UI rendering | Open LayoutEditorScreen, toggle description/URL/date, verify preview updates |
| "Create new layout" button navigates to LayoutEditorScreen | UX-02 | Voyager navigation | Open per-list settings, tap "Create new layout", verify LayoutEditorScreen opens, create layout, verify it appears in picker |
| URL display truncates with ellipsis on narrow screens | UX-02 | Visual / screen width dependent | Open a bookmark card with long URL, verify truncation with ellipsis |
| COMPACT_LIST layouts load correctly after migration to LIST | UX-02 | Runtime persistence | Have an existing COMPACT_LIST layout, upgrade, verify it renders as LIST with compact defaults |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 60s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
