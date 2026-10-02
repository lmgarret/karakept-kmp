# Offline cache cleanup

Offline copies are the article body in `bookmarks.content` plus files in the image cache
directory: content images (`img_<hash>`, shared between bookmarks by URL), hero images
(`hero_banner_*`, `hero_screenshot_*`), archives (`archive_*`) and downloaded assets (`asset_*`).
`OfflineCacheRepository` garbage-collects them in the background after every sync — full, filtered
or a single list's.

## Steps

1. **Stamp.** `bookmarks.readOrArchivedAt` records when a bookmark was first seen read or
   archived, and is cleared when it is neither again. Cleanup stamps it rather than every write
   path, so it is precise to the sync cadence — plenty for a period counted in days.
2. **Retention** (*Settings → Sync & Data → Offline Storage → Remove read offline copies*, a
   switch, off by default). The period is a slider over `OfflineRetention.SLIDER_STOPS` — daily
   to a week, weekly to a month, then two and three months — plus a field taking any number of
   days from 1 to 999. It keeps its value while switched off; `activeOfflineRetentionDays` is null
   then. Past the chosen number of days, the body is set to NULL and the bookmark's
   `assets.localPath`s are cleared. The row, reading progress and highlights stay.
3. **Storage cap** (*Limit offline storage*, a switch, off by default, with a slider over
   `OfflineRetention.CAP_SLIDER_STOPS_MB` from 100 MB to 10 GB and a field taking any size from
   10 MB to 100 GB, in MB or GB; `activeOfflineStorageCapMb` is null while off). Usage is every stored body plus
   every cache file a copy still references, counted once. Past the budget, whole copies (body
   and downloaded assets) are evicted — read or archived bookmarks first, then unread ones, and
   within each the one opened longest ago (`bookmarks.lastOpenedAt`, set by the reader; the save
   date for one never opened). A shared image only counts as freed once its last referrer goes.
   Evicted rows get `offlineEvictedAt`.
4. **Orphan sweep.** Asset rows whose bookmark no longer exists are deleted. Then every cache file
   whose name carries one of our prefixes and is referenced neither by a stored body (`file://…`)
   nor by an asset row is deleted. Files younger than an hour are skipped: a download lands on
   disk before the row that points at it is committed. Unprefixed files are never touched — on
   macOS and Windows Coil's disk cache shares the directory.

Steps 1 and 2 are a few indexed updates and run after every sync. Steps 3 and 4 read every
stored body, so after a sync they run once every six hours — and the sweep also whenever an
eviction left files to free. A new storage limit therefore takes effect within six hours. A sync
fans out into one pass per list; `cleanUpAfterSync` skips a pass while another is running.

## Not downloading it back

`OfflineRetention.isRetired` is the Kotlin twin of `RETIRED_PREDICATE`. Sync skips content for a
retired bookmark, and the reader shows a fetched body transiently instead of persisting it. It
tests the current read/archived flags as well as the stamp, so a bookmark marked unread downloads
again on the next sync without waiting for cleanup to clear its stamp.

Sync also skips anything with `offlineEvictedAt` set (`OfflineRetention.skipsContentSync`). The
reader stores such a bookmark again when it is opened, and any write of the body clears the
marker — so the cap evicts what nobody reads, and reading something brings it back.

## Storage card

The top of *Settings → Sync & Data → Offline Storage* shows what Karakept takes on the device
(`OfflineCacheRepository.storageUsage`), split into articles (`LENGTH(CAST(content AS BLOB))`,
bytes rather than characters), images (`img_`, `hero_*`), archives & PDFs (`archive_`, `asset_`)
and the rest of the app (grey). The bar is scaled to the app, or to the storage limit when one is
set — never to the device, where the app is a dot against tens of gigabytes; the device's free
space is a line of text. Measured when the page opens and after each action, never in the
background. The old *Storage Usage* card on the main Settings page is gone.

**Clear offline copies** drops every body and asset path, deletes every managed file with no
grace period, and compacts the database. Bookmarks, progress and highlights stay; the
confirmation lists what goes, with sizes, and what the current content strategy will download
again on the next sync.

## Clean up now

The last card of the Cleanup section previews the pass before it runs
(`OfflineCacheRepository.estimateCleanup`): copies past the retention period, what the storage
limit would still evict, files no bookmark uses, and database space a compaction would hand back.
The preview simulates the pass on the same `OfflineSnapshot` the storage limit decides from —
shared images freed only by their last referrer — so it is the cleanup it describes. It is
re-estimated, debounced, whenever the retention period or the limit changes.

**Clean up now** runs the full pass, sweep included, without waiting for the six-hour interval,
then compacts the database.

## Database compaction

The bundled SQLite driver creates the database with `auto_vacuum = FULL`, so an emptied body's
pages are returned on commit. What keeps the space is the write-ahead log: it holds the change
until a checkpoint and keeps its high-water mark afterwards, so clearing a few hundred articles
leaves megabytes there. `AppDatabase.compact()` therefore always runs
`PRAGMA wal_checkpoint(TRUNCATE)`, and runs `VACUUM` first only when `reclaimableBytes()`
(`freelist_count × page_size`) is non-zero — a database created without `auto_vacuum` keeps its
free pages until then. It runs after *Clean up now* and *Clear*, never from the background pass:
a `VACUUM` rewrites the whole file. The estimate's "database space to compact" line is those free
pages, so it reads nothing on an `auto_vacuum` database.
