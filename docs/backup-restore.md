# Backup & Restore

This document describes the backup and restore system for Karakept app settings.

## Table of Contents

- [Overview](#overview)
- [What Is Backed Up](#what-is-backed-up)
- [File Format](#file-format)
- [Storage Architecture](#storage-architecture)
- [Export Flow](#export-flow)
- [Import / Restore Flow](#import--restore-flow)
- [Scheduled Auto-Export](#scheduled-auto-export)
- [Versioning and Compatibility](#versioning-and-compatibility)
- [Platform Behaviour](#platform-behaviour)
- [Security Considerations](#security-considerations)
- [Known Trade-offs](#known-trade-offs)
- [Adding a New Setting](#adding-a-new-setting)

---

## Overview

The backup system allows users to export all app settings to a timestamped JSON file and restore them later — on the same device or a different one. It is accessible from **Settings → Backup & Restore**.

Key capabilities:

| Capability | Details |
|---|---|
| Manual export | Creates a JSON file and shares it via the platform share sheet |
| Manual import | Opens the native file picker; restores settings atomically |
| Scheduled auto-export | Runs silently at startup if the configured interval has elapsed |
| Versioned schema | `AppBackup.version` allows future migrations |
| Forward compatibility | Unknown fields in backup files are silently ignored |
| Backward compatibility | Missing fields fall back to their defaults |

---

## What Is Backed Up

### Backed up (`BackupSettings`)

All user-configurable preferences stored in the `settings_json` DataStore blob:

| Group | Settings |
|---|---|
| Layout | `layoutType`, `hideArticleThumbnails`, `showReadingTimeBadge`, `showTags`, `dimReadBookmarks` |
| Viewer / reader | `viewerMode`, `htmlTextColor`, `htmlBackgroundColor`, `htmlFontSize`, `htmlFontFamily`, `readingSpeedWpm`, `trackReadingProgress`, `resetProgressOnMarkUnread`, `linkOpenMode` |
| Theme | `themeMode`, `accentColor` |
| Swipe actions | `swipeLeftAction`, `swipeRightAction`, `customSwipeConfigsJson`, `swipeLeftConfigId`, `swipeRightConfigId` |
| Content sync | `contentSyncStrategy`, `contentSyncTargetLists`, `contentSyncWithChildren` |
| Notifications | `notificationsEnabled` |
| Offline mode | `offlineMode` |
| Onboarding | `onboardingCompleted` |
| Auto-export | `autoExportInterval` |
| Export directory | `backupExportDirectory` (null = platform default) |

### Also included (but not auto-restored)

| Data | Reason not auto-restored |
|---|---|
| Server connections (`servers`) | Contain API keys; require explicit user action to re-add for security |

### Not backed up

| Data | Reason |
|---|---|
| `activeServerId` | Session state; meaningless on another device |
| `autoOfflineDetected` | Transient network state |
| `lastAutoExportTime` | Tracks scheduler state; not a user preference |
| Per-list settings (`per_list_settings`) | List-specific state; list IDs may differ between server instances |
| Bookmarks | Live on the Karakeep server, not in the app |

---

## File Format

Backup files are UTF-8 encoded JSON with the extension `.json`.

**File name pattern:** `karakept_backup_YYYY-MM-DD.json`

### Top-level schema (`AppBackup`)

```json
{
  "version": 1,
  "exportedAt": "2025-03-01 14:23:05 UTC",
  "settings": { ... },
  "servers": [ ... ]
}
```

| Field | Type | Description |
|---|---|---|
| `version` | `Int` | Schema version. Currently `1`. Used for forward migration checks. |
| `exportedAt` | `String` | Human-readable UTC timestamp of when the backup was created. |
| `settings` | `BackupSettings` | All backed-up app settings (see below). |
| `servers` | `List<ServerBackup>` | Server connection info (may be empty). |

### Settings schema (`BackupSettings`)

All fields have defaults — missing fields (from older backups) fall back to their defaults automatically.

```json
{
  "layoutType": "LIST",
  "hideArticleThumbnails": true,
  "showReadingTimeBadge": true,
  "showTags": true,
  "dimReadBookmarks": true,
  "viewerMode": "READER",
  "htmlTextColor": null,
  "htmlBackgroundColor": null,
  "htmlFontSize": 16,
  "htmlFontFamily": "SYSTEM",
  "readingSpeedWpm": 238,
  "trackReadingProgress": true,
  "resetProgressOnMarkUnread": true,
  "linkOpenMode": "CUSTOM_TAB",
  "themeMode": "SYSTEM",
  "accentColor": "PURPLE",
  "swipeLeftAction": "MARK_READ",
  "swipeRightAction": "ARCHIVE",
  "customSwipeConfigsJson": "[]",
  "swipeLeftConfigId": null,
  "swipeRightConfigId": null,
  "contentSyncStrategy": "PER_BOOKMARK",
  "contentSyncTargetLists": [],
  "contentSyncWithChildren": [],
  "notificationsEnabled": true,
  "offlineMode": false,
  "onboardingCompleted": false,
  "autoExportInterval": "NEVER"
}
```

### Server schema (`ServerBackup`)

```json
{
  "id": "uuid-string",
  "url": "https://your-karakeep-instance.example.com",
  "apiKey": "sk-...",
  "label": "My Server"
}
```

> **Security note:** The `apiKey` field contains the full API key. Treat backup files as sensitive credentials and store them securely.

---

## Storage Architecture

Settings are stored as **six per-category JSON blobs** in DataStore. Each category has its own key and corresponding internal data class in `StoredSettings.kt`. This is distinct from the backup file format (`BackupSettings`), which remains a flat data class for backward compatibility with existing backup files.

```
DataStore<Preferences>
├── settings_theme_json    ← StoredThemeSettings  (themeMode, accentColor)
├── settings_display_json  ← StoredDisplaySettings (layoutType, badges, toggles)
├── settings_reader_json   ← StoredReaderSettings  (viewerMode, fonts, colors, speed)
├── settings_swipe_json    ← StoredSwipeSettings   (swipe actions, custom configs)
├── settings_sync_json     ← StoredSyncSettings    (sync strategy, target lists)
├── settings_app_json      ← StoredAppSettings     (notifications, offline, onboarding, auto-export)
├── active_server_id       ← Individual key (not backed up)
├── auto_offline_detected  ← Individual key (not backed up)
├── last_auto_export_time  ← Individual key (not backed up)
└── per_list_settings      ← Separate JSON blob (not backed up)
```

### Why per-category blobs?

| Benefit | Detail |
|---|---|
| **Smaller writes** | Changing `themeMode` only re-serializes the ~50-byte `settings_theme_json` blob, not the full ~1–2 KB `BackupSettings` |
| **Finer-grained observers** | `themeSettingsFlow` only emits when theme settings change; reader/sync/display observers are completely unaffected by a theme write |

### How `SettingsRepository` bridges storage and backup

`SettingsRepository` exposes:
- Six private category flows (e.g. `themeSettingsFlow: Flow<StoredThemeSettings>`), each with `.distinctUntilChanged()` to block propagation of unrelated writes.
- Per-setting derived public flows (e.g. `val themeMode: Flow<ThemeMode>`) derived from their category flow.
- `suspend fun currentSettings(): BackupSettings` — reads all six category blobs in one DataStore snapshot and assembles them into a flat `BackupSettings`. Used by `BackupRepository`.
- `suspend fun restoreSettings(s: BackupSettings)` — splits a flat `BackupSettings` into all six category blobs and writes them atomically in a single `dataStore.edit { }` transaction. Used by `BackupRepository`.

`BackupRepository` is intentionally kept static — it never enumerates individual settings and therefore never needs to change when new settings are added.

### Migration chain

Three generations of storage are supported, handled transparently by the per-category read helpers:

| Generation | Keys | Written by |
|---|---|---|
| Current (gen 3) | `settings_theme_json`, `settings_display_json`, … | This refactor |
| Previous (gen 2) | `settings_json` (single blob) | Previous single-blob refactor |
| Original (gen 1) | `layout_type`, `theme_mode`, … (individual keys) | Original individual-key storage |

On first read after an upgrade, each category read helper tries:
1. Its own per-category key (primary).
2. The `settings_json` single-blob (gen 2 fallback).
3. The original individual DataStore keys (gen 1 fallback).

On the first write, the per-category key is written. Subsequent reads go directly to step 1. Legacy keys are not deleted — they remain as inert orphan entries in DataStore.

---

## Export Flow

```
User taps "Export Now"
    │
    ▼
BackupRestoreScreenModel.exportSettings()
    │
    ▼
BackupRepository.exportToFile()
    ├── buildBackup()
    │       ├── settingsRepository.currentSettings()  → BackupSettings (full blob)
    │       └── serverRepository.servers.first()      → List<ServerInfo>
    │
    ├── Json.encodeToString(backup)  → JSON string
    ├── settingsRepository.backupExportDirectory.first()
    │       ├── non-null  → custom directory (SAF URI on Android, file path on Desktop)
    │       └── null      → FileUtils.getBackupDirectory() (platform default)
    ├── FileUtils.saveFileToDirectory(dir, fileName, bytes)  → path / URI string
    └── settingsRepository.setLastAutoExportTime(now)
    │
    ▼
FileUtils.shareBackupFile(filePath)
    └── Platform share sheet (Android) / save dialog (Desktop)
```

The export is always complete — no setting is ever missed because `currentSettings()` returns the entire blob.

---

## Import / Restore Flow

```
User taps "Choose Backup File"
    │
    ▼
FilePicker (platform-native)
    │  returns UTF-8 JSON string
    ▼
BackupRestoreScreenModel.importSettings(jsonContent)
    │
    ▼
BackupRepository.importFromJson(jsonContent)
    ├── Json.decodeFromString<AppBackup>(jsonContent)
    │       └── ignoreUnknownKeys = true → future fields silently skipped
    │
    ├── version check: backup.version > CURRENT_BACKUP_VERSION → error
    │
    ├── settingsRepository.restoreSettings(backup.settings)
    │       └── DataStore.edit { prefs[SETTINGS_JSON_KEY] = encode(s) }  (atomic write)
    │
    └── returns human-readable summary string
```

Server connections in the backup are **not** automatically restored. The import summary message notifies the user how many servers were found in the backup, prompting them to re-add them manually.

---

## Scheduled Auto-Export

Auto-export is checked at every app startup by `BackupRepository.checkAndRunScheduledExport()`, called from `App.kt`.

| Interval | Elapsed threshold |
|---|---|
| `NEVER` | Never runs |
| `DAILY` | 24 hours |
| `WEEKLY` | 7 × 24 hours |
| `MONTHLY` | 30 × 24 hours |

The `lastAutoExportTime` DataStore key (a Unix epoch millisecond timestamp) tracks when the last export ran. It is updated by `exportToFile()`, which is shared between manual and scheduled exports.

Auto-export failures are silently swallowed — the scheduler is best-effort and must not interrupt the user's app startup.

---

## Versioning and Compatibility

### Schema version

`AppBackup.version` is currently `1`. It must be incremented whenever a **breaking** change is made to the backup schema — one that cannot be handled by the existing `ignoreUnknownKeys` / default-value mechanisms.

`BackupRepository.importFromJson()` rejects backups with a version higher than `CURRENT_BACKUP_VERSION` with a descriptive error.

### Adding fields (non-breaking)

Adding a new field to `BackupSettings` with a default value is always non-breaking:
- **Restoring an old backup** (missing the new field): `ignoreUnknownKeys = false` but `@Serializable` fills the default → settings restored, new field gets its default.
- **Restoring a new backup on an old app**: `ignoreUnknownKeys = true` → new field silently ignored, no crash.

### Removing or renaming fields (breaking)

Removing or renaming a field without a migration path is a breaking change. If necessary:
1. Increment `AppBackup.version`.
2. Add a migration branch in `BackupRepository.importFromJson()` for the old version.

---

## Platform Behaviour

### Android

| Action | Behaviour |
|---|---|
| Export | File saved to the configured export directory (default: `<app-external-files>/backups/`); Android share sheet opened |
| Import | Android system file picker (`.json` filter) |
| Default backup directory | `Context.getExternalFilesDir("backups")` |
| Directory picker | SAF `ACTION_OPEN_DOCUMENT_TREE`; persistable URI permissions are requested so scheduled exports can write without UI interaction |
| Directory storage | SAF URI string (`content://…`) stored in settings; decoded to a human-readable label (e.g. "Internal Storage/Downloads") in the UI |

### Desktop (JVM)

| Action | Behaviour |
|---|---|
| Export | File saved to the configured export directory (default: `<user-home>/.karakept/backups/`); parent folder opened in Finder/Explorer |
| Import | AWT `JFileChooser` with `.json` filter |
| Default backup directory | `System.getProperty("user.home") + "/.karakept/backups"` |
| Directory picker | AWT `JFileChooser` in directory-selection mode |
| Directory storage | Absolute file-system path |

### iOS / other platforms

Not yet implemented. `FilePicker` and `FileUtils.shareBackupFile` use `expect`/`actual` — a new `actual` implementation is needed per platform.

---

## Security Considerations

- Backup files contain **API keys** for all configured servers. They should be treated like passwords.
- There is no encryption at rest. Users are responsible for storing backup files securely (e.g. in a password-protected location).
- `onboardingCompleted = true` is included in the backup. Restoring a backup from another device will skip onboarding on the target device. This is intentional.
- Server connections are included in the backup JSON but are **not automatically restored** on import — users must re-add them manually, providing a deliberate security checkpoint.

---

## Known Trade-offs

| Trade-off | Details |
|---|---|
| Write amplification (reduced) | Changing one setting re-serializes only the category blob (~50–300 bytes) rather than the full ~1–2 KB `BackupSettings`. Still slightly more than a single individual key write, but far better than the previous single-blob approach. |
| Cross-category observer isolation | Writes to one category do not propagate to observers of other categories (achieved via `distinctUntilChanged()` on each category flow). Observers within the same category still wake on any field change in that category. |
| Blob corruption = category reset | If a category JSON becomes malformed, `runCatching` falls back to the category's defaults — only that category's settings reset, not all settings. |
| JSON-in-JSON | `customSwipeConfigsJson` is a JSON string embedded inside `StoredSwipeSettings`. Functional but slightly awkward; could be inlined as `List<CustomSwipeActionConfig>` in a future change. |
| 3 files to touch per new setting | Adding a setting requires updating `BackupSettings` (backup format), the appropriate `Stored*Settings` class (storage format), and wiring in `SettingsRepository`. `BackupRepository` is never touched. |

---

## Adding a New Setting

See [CONTRIBUTING.md](../CONTRIBUTING.md#adding-a-new-setting) for the full step-by-step checklist. In short:

1. Add a field with a default to `BackupSettings` in `AppBackup.kt` (backup file format).
2. Add a field with the same default to the appropriate `Stored*Settings` class in `StoredSettings.kt` (DataStore storage format).
3. In `SettingsRepository.kt`: add a derived `Flow<T>`, a `set…()` setter, and map the field in `currentSettings()` and `restoreSettings()`.

`BackupRepository` never needs to change.
