# Phase 11: BackupRepository Edge Case Tests - Context

**Gathered:** 2026-03-25
**Status:** Ready for planning

<domain>
## Phase Boundary

Add tests for the untested execution paths in `BackupRepository`. The existing `BackupRepositoryTest` (225 lines, 11 tests) covers happy paths and skip branches. This phase adds a new `BackupRepositoryEdgeCaseTest` file targeting 6 specific untested branches:

1. `exportToFile` — blank PIN guard (`require(pin.isNotBlank())`)
2. `importFromJson` — `setBackupPin` called when `backup.settings.backupPinHash != null`
3. `importFromJson` — `setBackupPin` NOT called when `backup.settings.backupPinHash == null`
4. `importFromJson` — malformed/invalid JSON envelope
5. `checkAndRunScheduledExport` — export runs when DAILY interval is due
6. `checkAndRunScheduledExport` — export exception is swallowed silently

</domain>

<decisions>
## Implementation Decisions

### Test Scope
- Test `exportToFile` blank PIN guard — the `require()` guard is the first line of defense for the export path
- Test `importFromJson` setBackupPin branch for both non-null pinHash (called) and null pinHash (not called) — pin persistence is security-relevant and currently untested
- Test `checkAndRunScheduledExport` when export IS due (DAILY only) — existing tests only cover "skip" paths
- Test that `checkAndRunScheduledExport` swallows exceptions silently — best-effort behavior is explicitly documented in source comments

### Interval Coverage
- Test DAILY interval only for the "due" path — WEEKLY and MONTHLY use identical `elapsedMs >= intervalMs` logic
- Existing DAILY not-yet-due test (`1_000L` elapsed) already validates the threshold boundary
- No need to add WEEKLY/MONTHLY due tests — same code path, diminishing value

### File Structure
- New file: `BackupRepositoryEdgeCaseTest.kt` in `commonTest/.../data/repository/` — keeps existing 225-line file clean
- Extend `BaseRepositoryTest` (same pattern as existing test)
- For `checkAndRunScheduledExport` "when due": let FileUtils throw naturally (or succeed on desktop) — `checkAndRunScheduledExport` catches all exceptions so the test validates that `currentSettings()` was called regardless
- Do NOT mock FileUtils via mockkStatic — unnecessary complexity since the exception is swallowed anyway

### Claude's Discretion
- Choice of `lastAutoExportTime = 0L` (epoch) ensures `elapsedMs >= DAY_MS` reliably for DAILY "due" test
- For silent exception swallow test: mock `settingsRepository.currentSettings()` to throw so FileUtils is never reached — cleaner than relying on FileUtils behavior

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- `BaseRepositoryTest` in `commonTest/.../data/repository/` — setup/teardown with `testDispatcher`
- `BackupRepositoryTest.buildEncryptedEnvelope(settings, servers, pin)` helper — currently private, may need to be extracted or duplicated
- `mockkStatic("com.karakept.app.data.repository.SettingsRepositoryMutationsKt")` pattern already in use
- `BackupCrypto.encrypt` and `.decrypt` are real implementations, usable in commonTest (proven by existing tests)

### Established Patterns
- `mockk<SettingsRepository>(relaxed = true)` + specific `coEvery` overrides for properties under test
- `coEvery { settingsRepository.backupPin } returns flowOf(...)` for Flow properties
- `coVerify(exactly = N) { ... }` for call-count assertions
- `assertFailsWith<SomeException> { ... }` for exception path tests
- `runTest(testDispatcher) { }` for all coroutine tests

### Integration Points
- New file goes in: `composeApp/src/commonTest/kotlin/com/karakept/app/data/repository/BackupRepositoryEdgeCaseTest.kt`
- Must import: `BackupCrypto`, `EncryptedBackupEnvelope`, `AppBackup`, `BackupSettings`, `AutoExportInterval`
- `setBackupPin` is an extension function in `SettingsRepositoryMutations` — already mocked via `mockkStatic` in existing test; same pattern needed here

</code_context>

<specifics>
## Specific Ideas

- `exportToFile("")` → `assertFailsWith<IllegalArgumentException>` with message containing "PIN is required"
- `importFromJson` pinHash test: build backup with `backupPinHash = "aGVsbG8=:d29ybGQ="`, import with PIN "1234", `coVerify { settingsRepository.setBackupPin("1234") }`
- `importFromJson` null pinHash test: build backup with `backupPinHash = null` (default BackupSettings), `coVerify(exactly = 0) { settingsRepository.setBackupPin(any()) }`
- Malformed JSON test: pass `"not-valid-json"` as `jsonContent`, `assertFailsWith<Exception>`
- `checkAndRunScheduledExport` when due: set `lastAutoExportTime = flowOf(0L)`, `autoExportInterval = flowOf(AutoExportInterval.DAILY)`, `backupPin = flowOf("1234")`, `currentSettings()` throws to trigger swallow path → `coVerify { settingsRepository.currentSettings() }` was called
- Silent swallow test: same setup but verify no exception propagates from `checkAndRunScheduledExport()`

</specifics>

<deferred>
## Deferred Ideas

- WEEKLY and MONTHLY interval "due" tests — same code path as DAILY, deferred for minimal value gain
- `exportToFile` FileUtils integration tests — platform-specific, out of scope for commonTest
- `BackupRestoreScreenModel` tests — UI layer, separate future phase if needed

</deferred>
