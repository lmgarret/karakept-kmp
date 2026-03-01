# Contributing to Karakept

Thank you for contributing! This document contains important guidelines to follow when adding new features, settings, or options to the app.

## Table of Contents

- [Code Style](#code-style)
- [Adding a New Setting](#adding-a-new-setting)
- [Backup & Restore Checklist](#backup--restore-checklist)

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

#### 1. Add the setting to `SettingsRepository`

Add a preferences key, a `Flow<T>` property, and a `suspend fun set…()` function in:

```
composeApp/src/commonMain/kotlin/com/karakept/app/data/repository/SettingsRepository.kt
```

#### 2. Add a field to `BackupSettings`

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

#### 3. Export the setting in `BackupRepository.buildBackup()`

Open:

```
composeApp/src/commonMain/kotlin/com/karakept/app/data/repository/BackupRepository.kt
```

In `buildBackup()`, read the value from `settingsRepository` and assign it to the `BackupSettings` object:

```kotlin
val settings = BackupSettings(
    // ... existing fields ...
    myNewSetting = prefs.myNewSetting.first()
)
```

#### 4. Restore the setting in `BackupRepository.importFromJson()`

In the same file, inside `importFromJson()`, call the appropriate setter:

```kotlin
prefs.setMyNewSetting(s.myNewSetting)
```

#### 5. (Optional) If the setting is sensitive, note it in the UI

If the setting contains a secret (like an API key), add a note in `BackupRestoreScreen.kt` to remind users that the backup file is sensitive.

---

### Quick reference: files to touch

| File | What to do |
|---|---|
| `SettingsRepository.kt` | Add the key, Flow property, and setter |
| `AppBackup.kt` (`BackupSettings`) | Add the field with a default |
| `BackupRepository.kt` (`buildBackup`) | Export the value |
| `BackupRepository.kt` (`importFromJson`) | Restore the value |

If you skip any of these steps, the CI build will still pass but users will silently lose that setting when they restore a backup. Please follow all four steps.
