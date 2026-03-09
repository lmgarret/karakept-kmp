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

#### 1. Add a field to `BackupSettings` (backup file format)

Open:

```
composeApp/src/commonMain/kotlin/com/karakept/app/data/model/AppBackup.kt
```

Add a field to `BackupSettings` with a **sensible default**, so that older backup files (which won't have this field) still deserialize correctly.

```kotlin
@Serializable
data class BackupSettings(
    // ... existing fields ...
    val myNewSetting: Boolean = false   // <-- add here with the default value
)
```

#### 2. Add a field to the appropriate `Stored*Settings` class (DataStore storage format)

Open:

```
composeApp/src/commonMain/kotlin/com/karakept/app/data/repository/StoredSettings.kt
```

Pick the category that best fits the setting and add the field with the **same default**:

| Category class | Settings it covers |
|---|---|
| `StoredThemeSettings` | Theme mode, accent color |
| `StoredDisplaySettings` | Layout, badges, list visual toggles |
| `StoredReaderSettings` | Viewer mode, fonts, colors, reading speed, progress |
| `StoredSwipeSettings` | Swipe actions and custom swipe configs |
| `StoredSyncSettings` | Content sync strategy and target lists |
| `StoredAppSettings` | Notifications, offline mode, onboarding, auto-export |

```kotlin
@Serializable
internal data class StoredAppSettings(
    // ... existing fields ...
    val myNewSetting: Boolean = false   // <-- add here with the default value
)
```

#### 3. Wire the setting in `SettingsRepository`

Open:

```
composeApp/src/commonMain/kotlin/com/karakept/app/data/repository/SettingsRepository.kt
```

Three places to update:

**a) Add a derived `Flow<T>` from the appropriate category flow:**

```kotlin
val myNewSetting: Flow<Boolean> =
    appSettingsFlow.map { it.myNewSetting }.distinctUntilChanged()
```

**b) Add a setter using the matching `update*Settings` helper:**

```kotlin
suspend fun setMyNewSetting(enabled: Boolean) =
    updateAppSettings { copy(myNewSetting = enabled) }
```

**c) Map the field in `currentSettings()` and `restoreSettings()`:**

```kotlin
// In currentSettings() — read from the category object:
val app = prefs.readAppSettings()
return BackupSettings(
    // ... existing fields ...
    myNewSetting = app.myNewSetting
)

// In restoreSettings() — write to the category blob:
prefs[APP_SETTINGS_KEY] = settingsJson.encodeToString(
    StoredAppSettings(
        // ... existing fields ...
        myNewSetting = s.myNewSetting
    )
)
```

That's it. **`BackupRepository` never needs to be touched.**

---

### Quick reference: files to touch

| File | What to do |
|---|---|
| `AppBackup.kt` (`BackupSettings`) | Add the field with a default (backup file format) |
| `StoredSettings.kt` (correct category) | Add the field with a default (DataStore storage format) |
| `SettingsRepository.kt` | Add the derived `Flow<T>`, setter, and map the field in `currentSettings()` / `restoreSettings()` |

`BackupRepository` is now static — it never needs updating when new settings are added.

---

### Design notes

Settings are stored as **six per-category JSON blobs** in DataStore (e.g. `settings_theme_json`, `settings_reader_json`). This hybrid approach provides:

- **Smaller writes**: only the changed category blob (~100–300 bytes) is re-serialized and written, not the full settings (~1–2 KB).
- **Finer-grained observers**: changing the theme does not wake up observers on reader or sync settings, because each category has its own `Flow` with `distinctUntilChanged()`.

The **backup file format** (`BackupSettings` in `AppBackup.kt`) remains flat. `SettingsRepository.currentSettings()` assembles all categories into a flat `BackupSettings`; `restoreSettings()` splits a flat `BackupSettings` back into the six category blobs in a single atomic DataStore transaction.

**Migration chain** (transparent, handled automatically):
1. New per-category blobs (`settings_theme_json`, etc.) — primary format
2. Single blob (`settings_json`) — written by the previous refactor
3. Original individual DataStore keys (`theme_mode`, `layout_type`, etc.) — written before the single-blob refactor

On first read after an upgrade, the category read helpers walk this chain automatically. Once a per-category key is written, the legacy fallbacks are never consulted again.

**Non-backed-up settings** (session state such as `activeServerId`, `autoOfflineDetected`, and `backup_pin`): stored as individual DataStore keys and excluded from `BackupSettings`.  Note: `backup_pin` (the raw PIN) is stored locally for scheduled auto-exports; only its PBKDF2 hash is written to the backup file.

**Per-list settings and default list** (`perListSettings`, `defaultListType`, `defaultListId`): stored in individual DataStore keys (`per_list_settings`, `default_list_type`, `default_list_id`) and are mapped in `currentSettings()` / `restoreSettings()` directly — they do not go through a category blob.

**Backup encryption**: when the user sets a backup PIN, `BackupRepository.exportToFile()` serializes the `AppBackup` to JSON, encrypts it with AES-256-GCM (key derived via PBKDF2WithHmacSHA256, 100 000 iterations), and wraps the ciphertext in an `EncryptedBackupEnvelope`.  Server connections (including API keys) are restored automatically when importing an encrypted backup because the PIN acts as an explicit trust signal.

For a deeper dive into the backup system architecture, see [docs/backup-restore.md](docs/backup-restore.md).

---

## Documentation

When adding or modifying a significant feature, update the relevant documentation:

- **New settings**: the checklist above keeps `CONTRIBUTING.md` and code in sync — no separate docs needed.
- **New backup-related features** (new fields in `AppBackup`, schema version bumps, new import/export flows): update [docs/backup-restore.md](docs/backup-restore.md).
- **New API integrations or architectural changes**: add or update a file in `docs/`.
