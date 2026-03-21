---
phase: 6
slug: performance-optimization
status: approved
nyquist_compliant: true
wave_0_complete: true
created: 2026-03-21
---

# Phase 6 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | kotlin.test + kotlinx-coroutines-test |
| **Config file** | composeApp/build.gradle.kts |
| **Quick run command** | `./gradlew :composeApp:testDebugUnitTest --tests "com.karakept.app.*"` |
| **Full suite command** | `./gradlew :composeApp:testDebugUnitTest` |
| **Estimated runtime** | ~30 seconds |

---

## Sampling Rate

- **After every task commit:** Run `./gradlew :composeApp:testDebugUnitTest --tests "com.karakept.app.*"`
- **After every plan wave:** Run `./gradlew :composeApp:testDebugUnitTest`
- **Before `/gsd:verify-work`:** Full suite must be green
- **Max feedback latency:** 30 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|-----------|-------------------|-------------|--------|
| 06-01-01 | 01 | 1 | PERF-01 | manual | User scroll testing (perceptual) | N/A | ⬜ manual-only |
| 06-01-02 | 01 | 1 | PERF-01 | static | `grep -c "contentType" BookmarkListContent.kt` (expect 3) | N/A | ✅ green |
| 06-02-01 | 02 | 1 | PERF-02 | unit | `./gradlew :composeApp:desktopTest --tests "*.ParsedDocumentCacheTest"` | ✅ | ✅ green |
| 06-02-02 | 02 | 1 | PERF-02 | manual | User article load testing (perceptual) | N/A | ⬜ manual-only |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

*Existing infrastructure covers all phase requirements.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Smooth scrolling with 1000+ bookmarks | PERF-01 | Jank is a visual/perceptual metric | Scroll rapidly through large list, observe for frame drops |
| Progressive HTML article loading | PERF-02 | Load time perceived by user | Open a long article, verify content appears progressively |
| Memory stability during scrolling | PERF-01 | Requires profiler or extended observation | Scroll through large list repeatedly, monitor memory in profiler |

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

PERF-01 is structural (contentType + println removal, verifiable by grep). PERF-02 has 6 unit tests (ParsedDocumentCacheTest). Runtime performance behaviors (scroll jank, progressive loading) are inherently manual-only.
