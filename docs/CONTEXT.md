# Changelog context

Karakept is an end-user app, not a library or an API. Nobody links against this code, so
"breaking" means a **user** loses something, not that a downstream build fails.

## What users notice

- Anything in the bookmark list: layouts (Compact, Rows, Cards, Digest, Magazine), density,
  sorting, filters, tags, swipe and quick actions.
- The reader: text rendering, highlights, search, page turns, the image viewer, reader themes.
- Sync, offline caching, and the pending-action queue — including *not* losing queued edits.
- Settings, backups (export/restore), and server connection warnings.
- Platform behaviour: e-ink mode, hardware page-turn buttons, the system tray, the launcher icon.

Android, macOS and Linux desktop are all first-class. Say which platform a change affects when
it is only one of them.

## Breaking, for this project

- A user's settings, bookmarks, highlights or reading positions are lost or altered.
- A backup file written by an older version can no longer be restored.
- A gesture, key binding or button a user relies on moves or disappears.
- A screen is removed or a feature is dropped.

## Not breaking — leave out or file under internals

- Room database migrations. They are authored to be transparent, and a user never sees a schema
  version. A migration that would *lose* data is breaking; the migration itself is not.
- Refactors, renames, dependency bumps, Gradle/AGP/toolchain changes, CI workflows.
- Tests, test infrastructure, and internal code health.

## Upstream

The app talks to a self-hosted Karakeep server. A change that raises the minimum supported
server version matters to users and belongs in the notes; the server's own releases do not.
