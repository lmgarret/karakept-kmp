# Offline cache cleanup

Offline copies are the article body in `bookmarks.content` plus files in the image cache
directory: content images (`img_<hash>`, shared between bookmarks by URL), hero images
(`hero_banner_*`, `hero_screenshot_*`), archives (`archive_*`) and downloaded assets (`asset_*`).
`OfflineCacheRepository` garbage-collects them after a full sync, at most every six hours.

## Steps

1. **Stamp.** `bookmarks.readOrArchivedAt` records when a bookmark was first seen read or
   archived, and is cleared when it is neither again. Cleanup stamps it rather than every write
   path, so it is precise to the cleanup cadence — plenty for a period counted in days.
2. **Retention** (*Settings → Sync & Data → Offline Storage → Remove read offline copies*, a
   switch, off by default, and a 1–90 day slider that keeps its value while switched off —
   `activeOfflineRetentionDays` is 0 then). Past the chosen number of days, the body is set to NULL and the bookmark's `assets.localPath`s are
   cleared. The row, reading progress and highlights stay.
3. **Orphan sweep.** Asset rows whose bookmark no longer exists are deleted. Then every cache file
   whose name carries one of our prefixes and is referenced neither by a stored body (`file://…`)
   nor by an asset row is deleted. Files younger than an hour are skipped: a download lands on
   disk before the row that points at it is committed. Unprefixed files are never touched — on
   macOS and Windows Coil's disk cache shares the directory.

## Not downloading it back

`OfflineRetention.isRetired` is the Kotlin twin of `RETIRED_PREDICATE`. Sync skips content for a
retired bookmark, and the reader shows a fetched body transiently instead of persisting it. It
tests the current read/archived flags as well as the stamp, so a bookmark marked unread downloads
again on the next sync without waiting for cleanup to clear its stamp.
