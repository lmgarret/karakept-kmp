---
phase: 7
slug: integration-wiring-cleanup
status: approved
nyquist_compliant: true
wave_0_complete: true
created: 2026-03-21
---

# Phase 7 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | kotlin.test + MockK (JVM) |
| **Config file** | `build.gradle.kts` (commonTest, desktopTest) |
| **Quick run command** | User runs tests externally (remote devcontainer) |
| **Full suite command** | User runs tests externally |
| **Estimated runtime** | ~30 seconds |

---

## Sampling Rate

- **After every task commit:** `grep -c println` on target files
- **After every plan wave:** `wc -l` on BookmarkSyncPipeline.kt, SettingsRepositoryMutations.kt; `grep -c println` on BookmarkViewerScreenModel.kt, App.kt; `grep -c getCachedOrParseDocument` in composable files; `grep -c triggerMigration` in App.kt
- **Before `/gsd:verify-work`:** Full suite must be green
- **Max feedback latency:** 10 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|-----------|-------------------|-------------|--------|
| 07-01-01 | 01 | 1 | PERF-02 | grep | `grep -c parseDocument composeApp/src/commonMain/kotlin/com/karakept/app/ui/components/reader/NativeHtmlRenderer.kt` | N/A | ✅ green |
| 07-01-02 | 01 | 1 | PERF-02 | grep | `grep -c getCachedOrParseDocument composeApp/src/commonMain/kotlin/com/karakept/app/ui/screens/BookmarkViewerContent.kt` | N/A | ✅ green |
| 07-02-01 | 02 | 1 | SEC-02 | grep | `grep -c triggerMigration composeApp/src/commonMain/kotlin/App.kt` | N/A | ✅ green |
| 07-02-02 | 02 | 1 | ERR-01 | grep | `grep -c println composeApp/src/commonMain/kotlin/com/karakept/app/ui/screens/BookmarkViewerScreenModel.kt` = 0 | N/A | ✅ green |
| 07-02-03 | 02 | 1 | ERR-01 | grep | `grep -c println composeApp/src/commonMain/kotlin/App.kt` = 0 | N/A | ✅ green |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

Existing infrastructure covers all phase requirements. No new test files needed. Existing `ParsedDocumentCacheTest.kt` and `ServerRepositoryMigrationTest.kt` already cover the underlying functionality. Phase 7 wires call sites — verified by grep commands.

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Cache hits on repeated navigation | PERF-02 | Requires runtime navigation flow | Navigate to a bookmark, go back, navigate again — verify no re-parse via AppLogger |
| Credentials promoted to SecureCredentialStore | SEC-02 | Requires app with existing DB credentials | Start app with pre-existing server configs, check SecureCredentialStore populated |

---

## Validation Sign-Off

- [x] All tasks have `<automated>` verify or Wave 0 dependencies
- [x] Sampling continuity: no 3 consecutive tasks without automated verify
- [x] Wave 0 covers all MISSING references
- [x] No watch-mode flags
- [x] Feedback latency < 10s
- [x] `nyquist_compliant: true` set in frontmatter

**Approval:** approved 2026-03-21

---

## Validation Audit 2026-03-21

| Metric | Count |
|--------|-------|
| Gaps found | 0 |
| Resolved | 0 |
| Escalated | 0 |

**Cross-reference results:**
- PERF-02: `ParsedDocumentCacheTest.kt` covers cache logic; grep confirms `getCachedOrParseDocument` wired in BookmarkViewerContent.kt (1 match) and `parseDocument` in NativeHtmlRenderer.kt (2 matches)
- SEC-02: `ServerRepositoryMigrationTest.kt` covers migration logic; grep confirms `triggerMigration` wired in App.kt (1 match)
- ERR-01: grep confirms 0 `println` in both BookmarkViewerScreenModel.kt and App.kt
