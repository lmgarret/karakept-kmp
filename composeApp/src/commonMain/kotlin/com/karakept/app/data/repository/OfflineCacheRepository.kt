package com.karakept.app.data.repository

import com.karakept.app.data.local.dao.AssetDao
import com.karakept.app.data.local.dao.BookmarkDao
import com.karakept.app.domain.OfflineRetention
import com.karakept.app.utils.AppDispatchers
import com.karakept.app.utils.AppLogger
import com.karakept.app.utils.FileUtils
import com.karakept.app.utils.LocalFileInfo
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** The files cleanup may delete, behind an interface so tests need no real cache directory. */
interface CacheFileStore {
    fun list(): List<LocalFileInfo>
    fun delete(path: String)
}

object ImageCacheFileStore : CacheFileStore {
    override fun list(): List<LocalFileInfo> = FileUtils.listFiles(FileUtils.getImageCacheDirectory())
    override fun delete(path: String) = FileUtils.deleteFile(path)
}

data class OfflineCacheCleanupResult(
    val evictedBookmarks: Int = 0,
    val deletedFiles: Int = 0,
    val freedBytes: Long = 0
)

/**
 * Garbage collection for offline copies: article bodies, the images they reference, hero images
 * and downloaded assets.
 *
 * Runs in three steps, each safe to interrupt:
 * 1. Stamp [com.karakept.app.data.local.entity.BookmarkEntity.readOrArchivedAt] on rows that
 *    became read or archived since the last pass, and clear it on rows that went back.
 * 2. When a retention period is set, drop the body and local asset paths of bookmarks retired
 *    longer than that. The rows themselves stay.
 * 3. Sweep: delete every cache file nothing references any more. Content images are shared
 *    between bookmarks by URL, so a file can only go once no stored body points at it — which
 *    is why eviction clears references and leaves deleting to this step. It also collects what
 *    deleted bookmarks and removed servers left behind.
 */
class OfflineCacheRepository(
    private val bookmarkDao: BookmarkDao,
    private val assetDao: AssetDao,
    private val settingsRepository: SettingsRepository,
    private val appDispatchers: AppDispatchers,
    private val files: CacheFileStore = ImageCacheFileStore,
    private val clock: () -> Long = { System.currentTimeMillis() }
) {
    private val mutex = Mutex()
    private var lastRunAt: Long? = null

    /** [cleanUp], at most once per [MIN_INTERVAL_MILLIS] — reading every stored body is not free. */
    suspend fun cleanUpIfDue(): OfflineCacheCleanupResult? {
        val last = lastRunAt
        if (last != null && clock() - last < MIN_INTERVAL_MILLIS) return null
        return cleanUp()
    }

    suspend fun cleanUp(): OfflineCacheCleanupResult = withContext(appDispatchers.io) {
        mutex.withLock {
            val now = clock()
            lastRunAt = now

            bookmarkDao.clearReadOrArchived()
            bookmarkDao.stampReadOrArchived(now)

            val retentionDays = settingsRepository.activeOfflineRetentionDays.first()
            val evicted = if (retentionDays > 0) {
                val cutoff = OfflineRetention.cutoff(retentionDays, now)
                assetDao.clearLocalPathsOfRetired(cutoff)
                bookmarkDao.evictRetiredContent(cutoff)
            } else 0

            assetDao.deleteOrphaned()
            val (deleted, freed) = sweepUnreferencedFiles(now)

            OfflineCacheCleanupResult(evicted, deleted, freed).also {
                if (it.evictedBookmarks > 0 || it.deletedFiles > 0) {
                    AppLogger.d(TAG, "Evicted ${it.evictedBookmarks} bookmark(s), deleted ${it.deletedFiles} file(s), freed ${it.freedBytes} bytes")
                }
            }
        }
    }

    private suspend fun sweepUnreferencedFiles(now: Long): Pair<Int, Long> {
        // Listed before the references are read: a file written after this listing is not in
        // it, and one written before it but referenced only later is protected by the grace
        // period — a download lands on disk before the row that points at it is committed.
        val candidates = files.list().filter { file ->
            CACHE_FILE_PREFIXES.any(file.name::startsWith) &&
                now - file.lastModifiedMillis > SWEEP_GRACE_MILLIS
        }
        if (candidates.isEmpty()) return 0 to 0L

        val referenced = referencedFileNames()
        var deleted = 0
        var freed = 0L
        for (file in candidates) {
            if (file.name in referenced) continue
            files.delete(file.path)
            deleted++
            freed += file.sizeBytes
        }
        return deleted to freed
    }

    private suspend fun referencedFileNames(): Set<String> {
        val names = mutableSetOf<String>()
        assetDao.getAllLocalPaths().mapTo(names) { it.substringAfterLast('/').substringAfterLast('\\') }
        var after = 0L
        while (true) {
            val page = bookmarkDao.getStoredContentPage(after, CONTENT_PAGE_SIZE)
            if (page.isEmpty()) break
            for (row in page) {
                val content = row.content ?: continue
                FILE_REFERENCE.findAll(content).mapTo(names) { it.groupValues[1] }
            }
            after = page.last().localId
        }
        return names
    }

    companion object {
        private const val TAG = "OfflineCacheRepository"
        private const val CONTENT_PAGE_SIZE = 50
        const val MIN_INTERVAL_MILLIS = 6L * 60 * 60 * 1000
        const val SWEEP_GRACE_MILLIS = 60L * 60 * 1000

        // Every name the app writes into the image cache directory: ImageCacheManager's
        // `img_<hash>`, and the hero/archive/asset names BookmarkRepository and the viewer
        // give downloaded assets. Anything else in there is not ours to delete — on macOS and
        // Windows Coil's disk cache shares the directory.
        val CACHE_FILE_PREFIXES = listOf("img_", "hero_banner_", "hero_screenshot_", "archive_", "asset_")

        // The file name at the end of a `file://` reference. Matched by name rather than by
        // path so a directory with a space in it (Windows user folders) cannot cut it short.
        private val FILE_REFERENCE = Regex(
            """file://[^"'<>]*?[/\\]((?:""" + CACHE_FILE_PREFIXES.joinToString("|") + """)[A-Za-z0-9_.\-]+)"""
        )
    }
}
