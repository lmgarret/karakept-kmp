# Contributing to Karakept

Thank you for contributing! This document contains important guidelines to follow when adding new features, settings, or options to the app.

## Table of Contents

- [Code Style](#code-style)
- [Adding a New Setting](#adding-a-new-setting)
- [Backup & Restore Checklist](#backup--restore-checklist)
- [Documentation](#documentation)

---

## Code Style

- Follow the existing patterns in the codebase (KMP, Voyager, Koin, Coroutines/Flow).
- Prefer `expect`/`actual` for platform-specific behaviour.
- Add settings as `Flow`-based properties in `SettingsRepository`, with a matching `set…()` suspend function.

---

## Adding a New Setting

When you add a user-configurable setting to the app, you **must** also make it part of the backup/restore cycle.  Follow the checklist below.

### Backup & Restore Checklist

Every setting that should survive a fresh install or device transfer needs to be captured in the backup. Failing to do this means users lose that setting when they restore a backup.

#### 1. Add a field to `BackupSettings`

Open:

```
composeApp/src/commonMain/kotlin/com/karakept/app/data/model/AppBackup.kt
```

Add a field to `BackupSettings` with the **same sensible default** used in `SettingsRepository`, so that older backup files (which won't have this field) still deserialize correctly.

```kotlin
@Serializable
data class BackupSettings(
    // ... existing fields ...
    val myNewSetting: Boolean = false   // <-- add here with the default value
)
```

#### 2. Add the setting to `SettingsRepository`

Open:

```
composeApp/src/commonMain/kotlin/com/karakept/app/data/repository/SettingsRepository.kt
```

Add a derived `Flow<T>` property from the `settings` blob and a setter that uses `updateSettings { copy(…) }`:

```kotlin
// Derived flow
val myNewSetting: Flow<Boolean> =
    settings.map { it.myNewSetting }.distinctUntilChanged()

// Setter
suspend fun setMyNewSetting(enabled: Boolean) =
    updateSettings { copy(myNewSetting = enabled) }
```

Also add the migration fallback in `buildBackupSettingsFromLegacy()` if the setting was ever stored as an individual DataStore key before the single-blob refactor:

```kotlin
internal fun buildBackupSettingsFromLegacy(prefs: Preferences): BackupSettings = BackupSettings(
    // ... existing fields ...
    myNewSetting = prefs[LEGACY_MY_NEW_SETTING_KEY] ?: false
)
```

That's it. **`BackupRepository` never needs to be touched.**

---

### Quick reference: files to touch

| File | What to do |
|---|---|
| `AppBackup.kt` (`BackupSettings`) | Add the field with a default |
| `SettingsRepository.kt` | Add the derived `Flow<T>` and `set…()` using `updateSettings { copy(…) }` |

`BackupRepository` is now static — it never needs updating when new settings are added.
The `buildBackupSettingsFromLegacy()` migration in `SettingsRepository` only needs updating if you are migrating an *existing* individual DataStore key into the blob.

---

### Design notes

Settings are stored as a single JSON blob (`settings_json`) in DataStore. The `BackupSettings` data class is the canonical schema:

- **Adding a setting**: add a field with a default → blob format is forward/backward compatible via `ignoreUnknownKeys = true` and `encodeDefaults = true`.
- **Backup export**: `BackupRepository.buildBackup()` calls `settingsRepository.currentSettings()` — one line, always complete.
- **Backup restore**: `BackupRepository.importFromJson()` calls `settingsRepository.restoreSettings(backup.settings)` — one line, always complete.
- **Non-backed-up settings** (session state such as `activeServerId`, `autoOfflineDetected`): stored as individual DataStore keys and excluded from `BackupSettings`.

For a deeper dive into the backup system architecture, see [docs/backup-restore.md](docs/backup-restore.md).

---

## Documentation

When adding or modifying a significant feature, update the relevant documentation:

- **New settings**: the checklist above keeps `CONTRIBUTING.md` and code in sync — no separate docs needed.
- **New backup-related features** (new fields in `AppBackup`, schema version bumps, new import/export flows): update [docs/backup-restore.md](docs/backup-restore.md).
- **New API integrations or architectural changes**: add or update a file in `docs/`.
