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
3. **Orphan sweep.** Asset rows whose bookmark no longer exists are deleted. Then every cache file
   whose name carries one of our prefixes and is referenced neither by a stored body (`file://…`)
   nor by an asset row is deleted. Files younger than an hour are skipped: a download lands on
   disk before the row that points at it is committed. Unprefixed files are never touched — on
   macOS and Windows Coil's disk cache shares the directory.

Steps 1 and 2 are a few indexed updates and run after every sync. The sweep reads every stored
body, so after a sync it runs only when step 2 evicted something (that is what leaves files to
free) or once every six hours otherwise, for what deleted bookmarks left behind. A sync fans out
into one pass per list; `cleanUpAfterSync` skips a pass while another is running.

## Not downloading it back

`OfflineRetention.isRetired` is the Kotlin twin of `RETIRED_PREDICATE`. Sync skips content for a
retired bookmark, and the reader shows a fetched body transiently instead of persisting it. It
tests the current read/archived flags as well as the stamp, so a bookmark marked unread downloads
again on the next sync without waiting for cleanup to clear its stamp.
