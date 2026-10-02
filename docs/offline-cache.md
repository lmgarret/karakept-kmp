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

The *Offline storage* card on *Settings → Sync & Data → Offline Storage*
(`OfflineStorageScreenModel`) shows what offline copies
take — stored bodies (`LENGTH(CAST(content AS BLOB))`, bytes rather than characters) plus the
managed cache files — and how many bookmarks are available offline. Measured when the card opens
and after each action, never in the background: it reads every body.

- **Clean up now** runs the same pass as after a sync, sweep included, without waiting for the
  six-hour interval.
- **Clear offline cache** drops every body and asset path and deletes every managed file, with no
  grace period. Bookmarks, progress and highlights stay. The confirmation says what the current
  content strategy will download again on the next sync.
