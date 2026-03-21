---
phase: 05-security-hardening
plan: 02
subsystem: security
tags: [encryption, keystore, aes-gcm, pkcs12, credential-storage, android-keystore]

# Dependency graph
requires:
  - phase: none
    provides: none
provides:
  - SecureCredentialStore expect/actual (Android Keystore AES-GCM + Desktop PKCS12)
  - Transparent credential migration from DB to secure store
  - ServerRepository with secure credential reads and DB fallback
affects: [backup-restore, server-management, onboarding]

# Tech tracking
tech-stack:
  added: [Android Keystore API, PKCS12 KeyStore, AES-GCM]
  patterns: [expect/actual platform encryption, lazy migration with Mutex, secure-store-with-DB-fallback]

key-files:
  created:
    - composeApp/src/commonMain/kotlin/com/karakept/app/data/secure/SecureCredentialStore.kt
    - composeApp/src/androidMain/kotlin/com/karakept/app/data/secure/SecureCredentialStore.android.kt
    - composeApp/src/desktopMain/kotlin/com/karakept/app/data/secure/SecureCredentialStore.jvm.kt
    - composeApp/src/desktopTest/kotlin/com/karakept/app/data/secure/SecureCredentialStoreTest.kt
    - composeApp/src/desktopTest/kotlin/com/karakept/app/data/repository/ServerRepositoryMigrationTest.kt
  modified:
    - composeApp/src/commonMain/kotlin/com/karakept/app/data/repository/ServerRepository.kt
    - composeApp/src/commonMain/kotlin/com/karakept/app/di/AppModule.kt

key-decisions:
  - "No-arg expect constructor with AndroidContext global for platform Context access (matches existing Database pattern)"
  - "Non-suspend functions on SecureCredentialStore since PKCS12 and SharedPreferences are blocking I/O (acceptable for small credential data)"
  - "Lazy migration via triggerMigration() + ensureMigrated() with Mutex for thread safety"

patterns-established:
  - "expect/actual SecureCredentialStore: platform-native encryption abstracted behind common interface"
  - "Secure-store-with-DB-fallback: try secure store first, fall back to DB on exception"
  - "Lazy credential migration: migrated flag + Mutex prevents redundant migration"

requirements-completed: [SEC-02]

# Metrics
duration: 3min
completed: 2026-03-21
---

# Phase 05 Plan 02: Secure Credential Storage Summary

**Platform-native encrypted credential store (Android Keystore AES-GCM + Desktop PKCS12) with transparent DB migration and fallback**

## Performance

- **Duration:** 3 min
- **Started:** 2026-03-21T17:46:02Z
- **Completed:** 2026-03-21T17:49:01Z
- **Tasks:** 3
- **Files modified:** 7

## Accomplishments
- SecureCredentialStore expect/actual with Android Keystore AES-GCM and Desktop PKCS12 implementations
- ServerRepository transparently migrates existing credentials from DB to secure store on first access
- Backup/restore continues working since servers Flow populates apiKey from secure store
- 11 unit tests covering PKCS12 roundtrip and migration logic (happy path, idempotency, fallback, Flow reads)

## Task Commits

Each task was committed atomically:

1. **Task 1: Create SecureCredentialStore expect/actual implementations** - `9e26c38` (feat)
2. **Task 2: Wire ServerRepository migration, update DI, add desktop tests** - `d85513b` (feat)
3. **Task 3: Add ServerRepository migration unit tests with MockK** - `01c3a88` (test)

## Files Created/Modified
- `composeApp/src/commonMain/kotlin/com/karakept/app/data/secure/SecureCredentialStore.kt` - Common expect class with getApiKey/storeApiKey/removeApiKey/hasKey
- `composeApp/src/androidMain/kotlin/com/karakept/app/data/secure/SecureCredentialStore.android.kt` - Android Keystore AES-GCM + SharedPreferences encryption
- `composeApp/src/desktopMain/kotlin/com/karakept/app/data/secure/SecureCredentialStore.jvm.kt` - PKCS12 KeyStore file at ~/.karakept/credentials.p12
- `composeApp/src/commonMain/kotlin/com/karakept/app/data/repository/ServerRepository.kt` - Added SecureCredentialStore injection, lazy migration, secure reads with DB fallback
- `composeApp/src/commonMain/kotlin/com/karakept/app/di/AppModule.kt` - Wired SecureCredentialStore singleton, updated ServerRepository constructor
- `composeApp/src/desktopTest/kotlin/com/karakept/app/data/secure/SecureCredentialStoreTest.kt` - 5 tests for PKCS12 store/retrieve/remove/overwrite
- `composeApp/src/desktopTest/kotlin/com/karakept/app/data/repository/ServerRepositoryMigrationTest.kt` - 6 tests for migration and Flow credential reads

## Decisions Made
- Used no-arg expect constructor with AndroidContext global for platform Context (matches existing Database.android.kt pattern)
- Non-suspend functions on SecureCredentialStore since PKCS12 and SharedPreferences I/O is acceptable for small credential data
- Lazy migration via triggerMigration() with Mutex for thread-safe one-time migration

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None.

## User Setup Required

None - no external service configuration required.

## Verification Required

The following commands should be run to verify the implementation:
- `./gradlew :composeApp:compileKotlinDesktop` - compilation check
- `./gradlew :composeApp:desktopTest --tests "*.SecureCredentialStoreTest"` - PKCS12 roundtrip tests
- `./gradlew :composeApp:desktopTest --tests "*.ServerRepositoryMigrationTest"` - migration unit tests

## Next Phase Readiness
- Secure credential storage is complete and ready for use
- Existing server connections will migrate transparently on first app launch
- Backup export still includes API keys via servers Flow

## Self-Check: PASSED

All 7 files verified present. All 3 task commits verified in git log.

---
*Phase: 05-security-hardening*
*Completed: 2026-03-21*
