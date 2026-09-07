# Room 3.0 migration

Closes [#225](https://github.com/lmgarret/karakept-kmp/issues/225) ("evaluate Room 3.0 when
stable"). Room 3.0.2 went stable on 2026-08-26 (3.0.0 on 2026-07-01) and Room 2.x is now in
maintenance mode, so the app moved from `androidx.room` 2.8.4 to `androidx.room3` 3.0.2.

## Why it was a small change

Room 3.0's breaking changes are mostly about leaving the Android-only, blocking, SupportSQLite
world behind — a world this codebase never lived in, because the data layer was written against the
KMP/`SQLiteDriver` flavour of Room 2.7+.

| Room 3.0 breaking change | Applied here |
|---|---|
| SupportSQLite (`SupportSQLiteDatabase`, `Cursor`) removed | Not used |
| DAO functions must be `suspend` or return a reactive type | Already true: of 84 DAO functions, 73 are `suspend` and 11 return `Flow` |
| Migrations take `SQLiteConnection` | Already true |
| KSP mandatory, no KAPT / Java AP | Already KSP |
| `InvalidationTracker.Observer` removed | Not used |
| `PagingSource` / LiveData / RxJava / `ListenableFuture` need `@DaoReturnTypeConverters` | None used — paging is hand-rolled `LIMIT`/`OFFSET` over `@RawQuery` |
| `runInTransaction` → `withWriteTransaction` | Not used — no `@Transaction`, no manual transactions |
| Type converters and auto-migrations reworked | None used |

## What changed

- `androidx.room:room-runtime` / `room-compiler` → `androidx.room3:room3-runtime` /
  `room3-compiler` 3.0.2, plugin `androidx.room` → `androidx.room3`, and the build's `room { }`
  block → `room3 { }`. `androidx.sqlite:sqlite-bundled` 2.6.2 → 2.7.0, the version Room 3.0.2 is
  built against.
- `androidx.room.*` → `androidx.room3.*` across the 30 files that touch Room. Every annotation
  keeps its name, and `androidx.sqlite.*` (the driver, `SQLiteConnection`, `execSQL`) is not
  renamed.
- `Migration.migrate` is now `suspend`, so all ten `Migration*To*.kt` objects and the one direct
  call site in `Migration8To9Test` changed.
- The ten-migration list, previously duplicated in `Database.android.kt` and `Database.jvm.kt`, is
  now `ALL_MIGRATIONS` in `data/local/migrations/AppMigrations.kt`. A migration reachable on one
  platform but not the other is a silent destructive fallback for whoever is missing it, and the
  upgrade test below reads the same array the app does.

Nothing in the UI, domain or repository layers changed beyond one `RoomRawQuery` import — the
repositories only ever see DAO interfaces, whose signatures are untouched.

## The upgrade path, and why it needed a test

Both database builders call `.fallbackToDestructiveMigration(true)`. That means a schema mismatch
on open is **not** a crash: Room drops every table and rebuilds empty. Bookmarks and lists resync
from the server, but queued `pending_actions`, downloaded offline content and assets, and reading
progress not yet pushed do not. A major-version bump that changed the identity hash in
`room_master_table` would therefore have shipped as silent data loss for every existing install,
while looking perfectly healthy on a fresh one.

It does not: the Room 3 compiler generates the same identity hash for schema v12
(`aaf9dc92279fc0317b9e2b7dc295ffb4`) and the same `room_master_table` layout as Room 2.8 did.
`Room2UpgradeTest` (desktop) pins that down — it writes a database from the v12 DDL exactly as the
Room 2.8 compiler emitted it into `schemas/…/12.json`, identity hash included, opens it with the
Room 3 build and the real migration array, and asserts pre-existing rows survive. Corrupting the
stored hash makes it fail, so it is testing what it claims to.

## Known leftovers

- The Koin singleton in `AppModule.kt` does not call `setQueryCoroutineContext`, so queries run on
  Room's default (`Dispatchers.IO`) while the tests set it explicitly. Worth making explicit and
  routing through `AppDispatchers`.
- There is no `MIGRATION_4_5`, so a v4 database still falls through to the destructive path. That
  predates this change.

## Sources

- [Room 3.0 release notes](https://developer.android.com/jetpack/androidx/releases/room3)
- [Room 3.0 — Modernizing the Room (Android Developers Blog)](https://developer.android.com/blog/posts/modernizing-the-room)
- [Set up Room database for KMP](https://developer.android.com/kotlin/multiplatform/room)
