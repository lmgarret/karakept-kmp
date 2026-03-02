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

Settings are stored in a single JSON blob under the DataStore key `settings_json`. The `BackupSettings` data class is the canonical schema for both runtime use and backup.

```
DataStore<Preferences>
├── settings_json        ← Single JSON blob of BackupSettings (backed-up)
├── active_server_id     ← Individual key (not backed up)
├── auto_offline_detected
├── last_auto_export_time
└── per_list_settings    ← Separate JSON blob (not backed up)
```

`SettingsRepository` exposes:
- `val settings: Flow<BackupSettings>` — the single source of truth, deserialized on each read.
- Per-setting derived flows (e.g. `val themeMode: Flow<ThemeMode>`) using `.distinctUntilChanged()` to prevent spurious recompositions.
- `suspend fun currentSettings(): BackupSettings` — one-shot snapshot used by `BackupRepository`.
- `suspend fun restoreSettings(s: BackupSettings)` — atomic full replacement used by `BackupRepository`.

`BackupRepository` is intentionally kept static — it never enumerates individual settings and therefore never needs to change when new settings are added.

### Migration from legacy individual keys

Before the single-blob architecture, each setting was stored under its own DataStore key (e.g. `layout_type`, `theme_mode`). On first startup after the migration:

1. `settings_json` is absent.
2. `SettingsRepository.buildBackupSettingsFromLegacy(prefs)` reads all legacy keys and reconstructs a `BackupSettings` object.
3. The next `updateSettings { … }` call writes `settings_json`, sealing the migration.

After the first write, legacy keys are never read again. They are not explicitly deleted — they remain as inert orphan entries in DataStore.

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
    ├── FileUtils.saveFile(backupDir, fileName, bytes)  → absolute path
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
| Export | File saved to `<app-external-files>/backups/`; Android share sheet opened |
| Import | Android system file picker (`.json` filter) |
| Backup directory | `Context.getExternalFilesDir("backups")` |

### Desktop (JVM)

| Action | Behaviour |
|---|---|
| Export | File saved to `<user-home>/.karakept/backups/`; native save dialog opened |
| Import | AWT `JFileChooser` with `.json` filter |
| Backup directory | `System.getProperty("user.home") + "/.karakept/backups"` |

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
| Write amplification | Every setter call re-serializes and writes the entire `BackupSettings` blob (~1–2 KB). With individual keys only the changed key was written. For settings (changed rarely), this is negligible. |
| All observers wake on any change | All derived `Flow<T>` subscribe to the same blob. When `themeMode` changes, the `layoutType` flow also emits. Mitigated by `.distinctUntilChanged()` — no extra recompositions. |
| Blob corruption = all settings reset | If the JSON becomes malformed, `runCatching` falls back to `BackupSettings()` — all settings reset to defaults. With individual keys, only one key would be affected. |
| JSON-in-JSON | `customSwipeConfigsJson` is a JSON string embedded inside the blob. Functional but slightly awkward; could be inlined as `List<CustomSwipeActionConfig>` in a future change. |

---

## Adding a New Setting

See [CONTRIBUTING.md](../CONTRIBUTING.md#adding-a-new-setting) for the full step-by-step checklist. In short:

1. Add a field with a default to `BackupSettings` in `AppBackup.kt`.
2. Add a derived `Flow<T>` and `set…()` setter to `SettingsRepository.kt`.

`BackupRepository` never needs to change.
