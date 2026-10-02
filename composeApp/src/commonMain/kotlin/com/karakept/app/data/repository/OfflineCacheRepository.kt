package com.karakept.app.data.repository

import com.karakept.app.data.local.dao.AssetDao
import com.karakept.app.data.local.dao.BookmarkDao
import com.karakept.app.domain.OfflineRetention
import com.karakept.app.utils.AppDispatchers
import com.karakept.app.utils.AppLogger
import com.karakept.app.utils.FileUtils
import com.karakept.app.utils.LocalFileInfo
import com.karakept.app.utils.NoThumbnailCache
import com.karakept.app.utils.StorageInfo
import com.karakept.app.utils.ThumbnailCache
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

/**
 * What the app takes on the device: offline copies — article bodies, images (content and hero)
 * and downloaded files (archives, PDFs) — the image loader's thumbnail cache, and the rest.
 * [appBytes] is never less than the parts it is split into, so [otherAppBytes] cannot go negative
 * when they are measured a moment apart.
 */
data class OfflineStorageUsage(
    val bookmarkCount: Int,
    val articleBytes: Long,
    val imageBytes: Long,
    val fileBytes: Long,
    val appBytes: Long,
    val freeBytes: Long,
    val thumbnailCacheBytes: Long = 0
) {
    val offlineBytes: Long get() = articleBytes + imageBytes + fileBytes
    val otherAppBytes: Long get() = appBytes - offlineBytes - thumbnailCacheBytes
}

/** What a [OfflineCacheRepository.cleanUp] run now would free, part by part. */
data class OfflineCleanupEstimate(
    val retiredCopies: Int = 0,
    val retiredBytes: Long = 0,
    val overLimitCopies: Int = 0,
    val overLimitBytes: Long = 0,
    val unusedFileBytes: Long = 0,
    val reclaimableDatabaseBytes: Long = 0,
    val thumbnailCacheBytes: Long = 0
) {
    val totalBytes: Long
        get() = retiredBytes + overLimitBytes + unusedFileBytes + reclaimableDatabaseBytes + thumbnailCacheBytes
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
 * Runs in four steps, each safe to interrupt:
 * 1. Stamp [com.karakept.app.data.local.entity.BookmarkEntity.readOrArchivedAt] on rows that
 *    became read or archived since the last pass, and clear it on rows that went back.
 * 2. When a retention period is set, drop the body and local asset paths of bookmarks retired
 *    longer than that. The rows themselves stay.
 * 3. When a storage cap is set and exceeded, evict whole offline copies until usage is back
 *    under it — read or archived bookmarks first, then unread ones, least recently opened first
 *    within each. Evicted rows are marked so sync leaves them evicted.
 * 4. Sweep: delete every cache file nothing references any more. Content images are shared
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
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val deviceStorage: () -> StorageInfo = { FileUtils.getStorageInfo() },
    // Emptied bodies only reach the device once the database is compacted (see
    // AppDatabase.compact). Lambdas so tests need no real database.
    private val reclaimableDatabaseBytes: suspend () -> Long = { 0L },
    private val compactDatabase: suspend () -> Unit = {},
    private val thumbnailCache: ThumbnailCache = NoThumbnailCache
) {
    private val mutex = Mutex()
    private var lastSweepAt: Long? = null

    /**
     * The pass run after every sync, list syncs included. Stamping and retention are a few
     * indexed updates, so they run every time. The storage cap and the sweep read every stored
     * body, so they run once per [SWEEP_INTERVAL_MILLIS] — and the sweep also whenever this pass
     * evicted something, which is what leaves files to free.
     *
     * Returns null when another pass is already running: a sync fans out into one pass per list,
     * and one cleanup at a time covers them all.
     */
    suspend fun cleanUpAfterSync(): OfflineCacheCleanupResult? = withContext(appDispatchers.io) {
        if (!mutex.tryLock()) return@withContext null
        try {
            runPass(forceSweep = false)
        } finally {
            mutex.unlock()
        }
    }

    /**
     * The pass "Clean up now" runs: everything at once, then the database compacted and the
     * thumbnail cache emptied — rows download their thumbnails again as they are shown.
     */
    suspend fun cleanUp(): OfflineCacheCleanupResult = withContext(appDispatchers.io) {
        mutex.withLock {
            val result = runPass(forceSweep = true)
            val thumbnails = thumbnailCache.sizeBytes()
            thumbnailCache.clear()
            result.copy(freedBytes = result.freedBytes + compact() + thumbnails)
        }
    }

    /** Compacts the database; returns the free pages that handed back, which it can count. */
    private suspend fun compact(): Long {
        val reclaimable = reclaimableDatabaseBytes()
        compactDatabase()
        return reclaimable
    }

    private suspend fun runPass(forceSweep: Boolean): OfflineCacheCleanupResult {
        val now = clock()

        bookmarkDao.clearReadOrArchived()
        bookmarkDao.stampReadOrArchived(now)

        val retentionDays = settingsRepository.activeOfflineRetentionDays.first()
        val evicted = if (retentionDays != null) {
            val cutoff = OfflineRetention.cutoff(retentionDays, now)
            assetDao.clearLocalPathsOfRetired(cutoff)
            bookmarkDao.evictRetiredContent(cutoff)
        } else 0

        // Both the cap and the sweep read every stored body, so outside an explicit clean-up they
        // wait for their interval — except that an eviction leaves files to free right away.
        val lastSweep = lastSweepAt
        val heavyDue = forceSweep || lastSweep == null || now - lastSweep >= SWEEP_INTERVAL_MILLIS

        val capMb = settingsRepository.activeOfflineStorageCapMb.first()
        val capped = if (heavyDue && capMb != null) {
            enforceStorageCap(capMb * OfflineRetention.MEGABYTE, now)
        } else 0

        val (deleted, freed) = if (heavyDue || evicted > 0 || capped > 0) {
            lastSweepAt = now
            assetDao.deleteOrphaned()
            sweepUnreferencedFiles(now)
        } else 0 to 0L

        return OfflineCacheCleanupResult(evicted + capped, deleted, freed).also {
            if (it.evictedBookmarks > 0 || it.deletedFiles > 0) {
                AppLogger.d(TAG, "Evicted ${it.evictedBookmarks} bookmark(s), deleted ${it.deletedFiles} file(s), freed ${it.freedBytes} bytes")
            }
        }
    }

    suspend fun storageUsage(): OfflineStorageUsage = withContext(appDispatchers.io) {
        val stats = bookmarkDao.getStoredContentStats()
        val managed = files.list().filter(::isManaged)
        val imageBytes = managed.filter(::isImage).sumOf { it.sizeBytes }
        val fileBytes = managed.filterNot(::isImage).sumOf { it.sizeBytes }
        val thumbnails = thumbnailCache.sizeBytes()
        val device = deviceStorage()
        OfflineStorageUsage(
            bookmarkCount = stats.bookmarkCount,
            articleBytes = stats.bodyBytes,
            imageBytes = imageBytes,
            fileBytes = fileBytes,
            appBytes = maxOf(device.usedBytes, stats.bodyBytes + imageBytes + fileBytes + thumbnails),
            freeBytes = device.freeBytes,
            thumbnailCacheBytes = thumbnails
        )
    }

    /**
     * What [cleanUp] would free right now, without changing anything: copies past the retention
     * period, then whatever the storage limit would still evict, files nothing uses, and database
     * space a compaction would hand back. Reads every stored body, like the cleanup it previews.
     *
     * A bookmark read since the last pass has no stamp yet, so it is not counted: the pass that
     * stamps it starts its retention period rather than ending it.
     */
    suspend fun estimateCleanup(): OfflineCleanupEstimate = withContext(appDispatchers.io) {
        val now = clock()
        val snapshot = loadSnapshot()
        val simulation = snapshot.simulation()

        val retentionDays = settingsRepository.activeOfflineRetentionDays.first()
        val retired = snapshot.holders.filter {
            OfflineRetention.isRetired(it.isRead, it.isArchived, it.readOrArchivedAt, retentionDays, now)
        }
        val retiredBytes = retired.sumOf { simulation.evict(it) ?: 0L }

        val capMb = settingsRepository.activeOfflineStorageCapMb.first()
        val usageBeforeCap = simulation.usage
        val overLimit = capMb?.let { snapshot.selectForCap(simulation, it * OfflineRetention.MEGABYTE) }.orEmpty()

        OfflineCleanupEstimate(
            retiredCopies = retired.size,
            retiredBytes = retiredBytes,
            overLimitCopies = overLimit.size,
            overLimitBytes = usageBeforeCap - simulation.usage,
            unusedFileBytes = snapshot.unusedFiles(now, SWEEP_GRACE_MILLIS).sumOf { it.sizeBytes },
            reclaimableDatabaseBytes = reclaimableDatabaseBytes(),
            thumbnailCacheBytes = thumbnailCache.sizeBytes()
        )
    }

    /**
     * Drops every offline copy — bodies and downloaded assets — and deletes the files. Bookmarks,
     * reading progress and highlights stay. The next sync downloads again whatever the content
     * strategy covers.
     */
    suspend fun clearAll(): OfflineCacheCleanupResult = withContext(appDispatchers.io) {
        mutex.withLock {
            val cleared = bookmarkDao.clearAllContent()
            assetDao.clearAllLocalPaths()
            // No grace period: the user asked for everything gone. A download racing this can
            // leave a body pointing at a deleted image, which the reader shows as missing.
            val (deleted, freed) = sweepUnreferencedFiles(clock(), graceMillis = 0)
            OfflineCacheCleanupResult(cleared, deleted, freed + compact())
        }
    }

    private suspend fun sweepUnreferencedFiles(
        now: Long,
        graceMillis: Long = SWEEP_GRACE_MILLIS
    ): Pair<Int, Long> {
        // Listed before the references are read: a file written after this listing is not in
        // it, and one written before it but referenced only later is protected by the grace
        // period — a download lands on disk before the row that points at it is committed.
        val candidates = files.list().filter { file ->
            isManaged(file) && now - file.lastModifiedMillis >= graceMillis
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

    private fun isManaged(file: LocalFileInfo) = CACHE_FILE_PREFIXES.any(file.name::startsWith)

    private fun isImage(file: LocalFileInfo) = IMAGE_FILE_PREFIXES.any(file.name::startsWith)

    private suspend fun referencedFileNames(): Set<String> {
        val names = mutableSetOf<String>()
        assetDao.getAllLocalPaths().mapTo(names, ::fileNameOf)
        forEachStoredContent { _, content -> names += referencesIn(content) }
        return names
    }

    /** Evicts offline copies until what they take is back under [capBytes]. Returns how many. */
    private suspend fun enforceStorageCap(capBytes: Long, now: Long): Int {
        val snapshot = loadSnapshot()
        val evicted = snapshot.selectForCap(snapshot.simulation(), capBytes)
        evicted.map { it.localId }.chunked(SQL_CHUNK).forEach { bookmarkDao.evictForStorageCap(it, now) }
        evicted.forEach { assetDao.clearLocalPathsForBookmark(it.remoteId, it.serverId) }
        return evicted.size
    }

    private suspend fun loadSnapshot(): OfflineSnapshot {
        val managed = files.list().filter(::isManaged)
        val holders = bookmarkDao.getOfflineHolders()
        val holderByRemote = holders.associateBy { it.remoteId to it.serverId }

        val bodyBytes = mutableMapOf<Long, Long>()
        val refsOf = mutableMapOf<Long, MutableSet<String>>()
        forEachStoredContent { localId, content ->
            bodyBytes[localId] = utf8Length(content)
            refsOf.getOrPut(localId) { mutableSetOf() } += referencesIn(content)
        }
        for (asset in assetDao.getLocalPathOwners()) {
            val holder = holderByRemote[asset.bookmarkRemoteId to asset.serverId] ?: continue
            refsOf.getOrPut(holder.localId) { mutableSetOf() } += fileNameOf(asset.localPath)
        }
        return OfflineSnapshot(holders, bodyBytes, refsOf, managed)
    }

    // Keyset-paged so a library of stored articles is never held in memory at once.
    private suspend fun forEachStoredContent(block: (localId: Long, content: String) -> Unit) {
        var after = 0L
        while (true) {
            val page = bookmarkDao.getStoredContentPage(after, CONTENT_PAGE_SIZE)
            if (page.isEmpty()) break
            for (row in page) row.content?.let { block(row.localId, it) }
            after = page.last().localId
        }
    }

    private fun referencesIn(content: String): List<String> =
        FILE_REFERENCE.findAll(content).map { it.groupValues[1] }.toList()

    private fun fileNameOf(path: String) = path.substringAfterLast('/').substringAfterLast('\\')

    companion object {
        private const val TAG = "OfflineCacheRepository"
        private const val CONTENT_PAGE_SIZE = 50
        private const val SQL_CHUNK = 500
        const val SWEEP_INTERVAL_MILLIS = 6L * 60 * 60 * 1000
        const val SWEEP_GRACE_MILLIS = 60L * 60 * 1000

        // Every name the app writes into the image cache directory: ImageCacheManager's
        // `img_<hash>`, and the hero/archive/asset names BookmarkRepository and the viewer
        // give downloaded assets. Anything else in there is not ours to delete — on macOS and
        // Windows Coil's disk cache shares the directory.
        val CACHE_FILE_PREFIXES = listOf("img_", "hero_banner_", "hero_screenshot_", "archive_", "asset_")

        // The managed files that are pictures; the rest are downloaded archives and assets.
        private val IMAGE_FILE_PREFIXES = listOf("img_", "hero_banner_", "hero_screenshot_")

        // The file name at the end of a `file://` reference. Matched by name rather than by
        // path so a directory with a space in it (Windows user folders) cannot cut it short.
        private val FILE_REFERENCE = Regex(
            """file://[^"'<>]*?[/\\]((?:""" + CACHE_FILE_PREFIXES.joinToString("|") + """)[A-Za-z0-9_.\-]+)"""
        )
    }
}
