package com.karakept.app.data.local.dao

import androidx.room3.Dao
import androidx.room3.Delete
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.RawQuery
import androidx.room3.RoomRawQuery
import androidx.room3.RoomWarnings
import androidx.room3.Update
import com.karakept.app.data.local.entity.BookmarkEntity
import kotlinx.coroutines.flow.Flow

// List queries project a fixed column set instead of `SELECT *` so the article body never
// travels with a screenful of rows. The columns left out of that projection — content,
// crawlStatus, crawledAt, summary, summarizationStatus — come back as entity defaults, which is
// what `RoomWarnings.QUERY_MISMATCH` is acknowledging on each of them; read those fields through
// a full-row query (`getBookmarkById`, `getBookmarkByRemoteId`) instead.
@Dao
interface BookmarkDao {
    @Query("""
        SELECT localId, remoteId, serverId, title, url,
               description, imageUrl, bannerImageAssetId, screenshotAssetId, tags, listIds, isStarred, isArchived,
               isRead, createdAt, readingTimeMinutes, readingProgress, readingScrollIndex, readingScrollOffset,
               modifiedAt, progressSyncedAt,
               '' as content
        FROM bookmarks
        WHERE serverId = :serverId
        ORDER BY createdAt DESC
    """)
    @SuppressWarnings(RoomWarnings.QUERY_MISMATCH)
    fun getBookmarksForServer(serverId: String): Flow<List<BookmarkEntity>>

    @Query("SELECT * FROM bookmarks WHERE localId = :id")
    suspend fun getBookmarkById(id: Long): BookmarkEntity?

    @Query("SELECT * FROM bookmarks WHERE localId = :id")
    fun observeBookmarkById(id: Long): Flow<BookmarkEntity?>
    
    @Query("SELECT * FROM bookmarks WHERE remoteId = :remoteId AND serverId = :serverId LIMIT 1")
    suspend fun getBookmarkByRemoteId(remoteId: String, serverId: String): BookmarkEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(bookmark: BookmarkEntity)

    // Returns the generated localIds, in the order of [bookmarks]. Sync relies on this
    // instead of re-reading the whole table to recover the ids it just inserted.
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmarks(bookmarks: List<BookmarkEntity>): List<Long>

    @Update
    suspend fun updateBookmarks(bookmarks: List<BookmarkEntity>)

    @Delete
    suspend fun deleteBookmark(bookmark: BookmarkEntity)

    @Delete
    suspend fun deleteBookmarks(bookmarks: List<BookmarkEntity>)

    @Query("DELETE FROM bookmarks WHERE serverId = :serverId")
    suspend fun deleteAllBookmarksForServer(serverId: String)

    @Query("UPDATE bookmarks SET content = :content, readingTimeMinutes = :readingTime WHERE localId = :localId")
    suspend fun updateContent(localId: Long, content: String, readingTime: Int)

    @Query("""
        UPDATE bookmarks SET
            readingProgress = :progress,
            readingScrollIndex = :scrollIndex,
            readingScrollOffset = :scrollOffset,
            isRead = CASE WHEN :progress >= 1.0 THEN 1 ELSE isRead END
        WHERE localId = :localId
    """)
    suspend fun updateReadingProgress(
        localId: Long,
        progress: Float,
        scrollIndex: Int,
        scrollOffset: Int
    )

    // Update only metadata fields, preserving content
    @Query("""
        UPDATE bookmarks SET
            title = :title,
            url = :url,
            description = :description,
            imageUrl = :imageUrl,
            bannerImageAssetId = :bannerImageAssetId,
            screenshotAssetId = :screenshotAssetId,
            tags = :tags,
            listIds = :listIds,
            isStarred = :isStarred,
            isArchived = :isArchived,
            isRead = :isRead,
            readingTimeMinutes = :readingTimeMinutes,
            modifiedAt = :modifiedAt,
            crawlStatus = :crawlStatus,
            crawledAt = :crawledAt,
            summary = :summary,
            summarizationStatus = :summarizationStatus
        WHERE localId = :localId
    """)
    suspend fun updateBookmarkMetadata(
        localId: Long,
        title: String,
        url: String,
        description: String?,
        imageUrl: String?,
        bannerImageAssetId: String?,
        screenshotAssetId: String?,
        tags: String,
        listIds: String,
        isStarred: Boolean,
        isArchived: Boolean,
        isRead: Boolean,
        readingTimeMinutes: Int,
        modifiedAt: Long?,
        crawlStatus: String?,
        crawledAt: Long?,
        summary: String?,
        summarizationStatus: String?
    )

    // Narrow writer for the AI summarize action, which knows nothing about the other metadata.
    @Query("UPDATE bookmarks SET summary = :summary, summarizationStatus = :status WHERE localId = :localId")
    suspend fun updateSummary(localId: Long, summary: String?, status: String?)

    // Stamp the reading-progress rotating cursor after a pull (G3).
    @Query("UPDATE bookmarks SET progressSyncedAt = :syncedAt WHERE localId = :localId")
    suspend fun updateProgressSyncedAt(localId: Long, syncedAt: Long)

    // The same stamp for a whole pass. A pass covers hundreds of rows, and one statement per
    // row is one implicit transaction per row.
    @Query("UPDATE bookmarks SET progressSyncedAt = :syncedAt WHERE localId IN (:localIds)")
    suspend fun markProgressSyncedAt(localIds: List<Long>, syncedAt: Long)

    /**
     * Reading-progress pull candidates for the rotating cursor.
     *
     * Ordered by what the user is most likely to be looking at rather than by staleness
     * alone: the list on screen first, then unread rows — the ones whose progress decides a
     * count the user can see — and only then the least-recently-pulled. A pass covers a
     * bounded slice of the library, so which slice it covers is what decides whether the
     * screen agrees with the server.
     *
     * The list match is deliberately a loose `LIKE`: it only orders rows, never selects them,
     * so a false positive costs nothing and the exact four-way membership test the paged
     * query needs would buy nothing here.
     */
    @Query("""
        SELECT localId, remoteId, readingProgress FROM bookmarks
        WHERE serverId = :serverId
        ORDER BY
            CASE WHEN :listId IS NOT NULL AND listIds LIKE '%' || :listId || '%' THEN 0 ELSE 1 END,
            isRead ASC,
            progressSyncedAt ASC,
            modifiedAt DESC
        LIMIT :limit
    """)
    suspend fun getReadingProgressPullCandidates(
        serverId: String,
        listId: String?,
        limit: Int
    ): List<ProgressPullTarget>

    // Bookmarks whose progress has never been pulled. Drained in full on a freshly connected
    // server so the library does not converge one bounded slice per sync. A projection rather
    // than SELECT *: the pull only needs these columns, and the row carries article content.
    @Query("""
        SELECT localId, remoteId, readingProgress FROM bookmarks
        WHERE serverId = :serverId AND progressSyncedAt = 0
        ORDER BY isRead ASC, modifiedAt DESC
        LIMIT :limit
    """)
    suspend fun getNeverProgressSyncedTargets(serverId: String, limit: Int): List<ProgressPullTarget>

    // Same projection for an explicit set of bookmarks whose progress is missing or stale —
    // the rows the user is actually looking at, refreshed ahead of the rotating cursor
    // reaching them. Staleness rather than "never pulled" so that progress changed on
    // another device shows up on the list being looked at, not one rotation later.
    @Query("""
        SELECT localId, remoteId, readingProgress FROM bookmarks
        WHERE serverId = :serverId AND progressSyncedAt < :staleBefore AND remoteId IN (:remoteIds)
    """)
    suspend fun getStaleProgressTargetsIn(
        serverId: String,
        remoteIds: List<String>,
        staleBefore: Long
    ): List<ProgressPullTarget>

    /**
     * Writes progress that came from the server. Unlike [updateReadingProgress] this also
     * *clears* the read flag below 100%: read state is local-only in this app, and the
     * percentage is the only thing that carries it between devices, so a bookmark marked
     * unread elsewhere has to be able to come back as unread here.
     *
     * Guarded by `readingProgress = :expectedProgress`: the caller snapshots the row's
     * progress before asking the server, and the answer can take seconds to come back. A
     * local mark-as-read/unread landing in that window must win — writing the server's
     * answer on top of it would silently revert the row the user just acted on (#333). The
     * WHERE clause makes the write a no-op once the row has moved, and the affected-row
     * count tells the caller whether that happened.
     */
    @Query("""
        UPDATE bookmarks SET
            readingProgress = :progress,
            readingScrollIndex = :scrollIndex,
            readingScrollOffset = :scrollOffset,
            isRead = CASE WHEN :progress >= 1.0 THEN 1 ELSE 0 END
        WHERE localId = :localId AND readingProgress = :expectedProgress
    """)
    suspend fun applyServerReadingProgress(
        localId: Long,
        expectedProgress: Float,
        progress: Float,
        scrollIndex: Int,
        scrollOffset: Int
    ): Int

    // Paginated query — ORDER BY is injected dynamically via RoomRawQuery so the
    // sort option from FilterConfig is applied at the DB level rather than in memory.
    @RawQuery
    suspend fun getBookmarksPaged(query: RoomRawQuery): List<BookmarkEntity>

    // The size of a view, from the same predicate that selects its rows — see
    // BookmarkRepository.buildViewPredicate. A count and the rows it counts cannot disagree
    // when one WHERE defines both.
    @RawQuery
    suspend fun countBookmarks(query: RoomRawQuery): Int

    // The same count, re-emitted whenever the table changes — what a view's total is watched
    // through. Observing a count rather than the rows is the point: the total used to be the
    // size of a 4000-row list rebuilt in memory on every write.
    @RawQuery(observedEntities = [BookmarkEntity::class])
    fun countBookmarksFlow(query: RoomRawQuery): Flow<Int>

    // Identities only, for selecting a whole view without reading its rows — see
    // BookmarkRepository.getViewRemoteIds.
    @RawQuery
    suspend fun selectRemoteIds(query: RoomRawQuery): List<String>

    @Query("""
        SELECT localId, remoteId, serverId, title, url,
               description, imageUrl, bannerImageAssetId, screenshotAssetId, tags, listIds, isStarred, isArchived,
               isRead, createdAt, readingTimeMinutes, readingProgress, readingScrollIndex, readingScrollOffset,
               modifiedAt, progressSyncedAt,
               '' as content
        FROM bookmarks
        WHERE serverId = :serverId AND remoteId IN (:remoteIds)
    """)
    @SuppressWarnings(RoomWarnings.QUERY_MISMATCH)
    suspend fun getBookmarksByRemoteIds(
        serverId: String,
        remoteIds: List<String>
    ): List<BookmarkEntity>

    // Count queries for pagination
    @Query("SELECT COUNT(*) FROM bookmarks WHERE serverId = :serverId")
    suspend fun getTotalBookmarkCount(serverId: String): Int

    @Query("SELECT COUNT(*) FROM bookmarks WHERE serverId = :serverId AND isStarred = 1")
    suspend fun getFavoritesCount(serverId: String): Int

    @Query("SELECT COUNT(*) FROM bookmarks WHERE serverId = :serverId AND isArchived = 1")
    suspend fun getArchivedCount(serverId: String): Int

    // List-filtered queries
    @Query("""
        SELECT COUNT(*)
        FROM bookmarks
        WHERE serverId = :serverId
        AND (listIds = :listId
             OR listIds LIKE :listId || ',%'
             OR listIds LIKE '%,' || :listId
             OR listIds LIKE '%,' || :listId || ',%')
    """)
    suspend fun getBookmarksForListCount(
        serverId: String,
        listId: String
    ): Int

    @Query("SELECT COUNT(*) FROM bookmarks WHERE serverId = :serverId AND isArchived = 0")
    suspend fun getNotArchivedCount(serverId: String): Int

    @Query("""
        SELECT localId, remoteId, serverId, title, url,
               description, imageUrl, bannerImageAssetId, screenshotAssetId, tags, listIds, isStarred, isArchived,
               isRead, createdAt, readingTimeMinutes, readingProgress, readingScrollIndex, readingScrollOffset,
               modifiedAt, progressSyncedAt,
               '' as content
        FROM bookmarks
        WHERE serverId = :serverId AND content IS NOT NULL AND length(content) > 0
        ORDER BY createdAt DESC
    """)
    @SuppressWarnings(RoomWarnings.QUERY_MISMATCH)
    suspend fun getAllOfflineForServer(serverId: String): List<BookmarkEntity>

    @Query("SELECT COUNT(*) FROM bookmarks WHERE serverId = :serverId AND content IS NOT NULL AND length(content) > 0")
    fun getOfflineCountFlow(serverId: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM bookmarks WHERE serverId = :serverId AND content IS NOT NULL AND length(content) > 0")
    suspend fun getOfflineCount(serverId: String): Int

    // Query for sync that includes content length and reading time to determine if content exists
    @Query("""
        SELECT localId, remoteId, serverId, title, url,
               description, imageUrl, bannerImageAssetId, screenshotAssetId, tags, listIds, isStarred, isArchived,
               isRead, createdAt, readingTimeMinutes, readingProgress, readingScrollIndex, readingScrollOffset,
               modifiedAt, progressSyncedAt,
               CASE WHEN length(content) > 0 THEN 'HAS_CONTENT' ELSE '' END as content
        FROM bookmarks
        WHERE serverId = :serverId
    """)
    @SuppressWarnings(RoomWarnings.QUERY_MISMATCH)
    suspend fun getBookmarksForServerWithContentInfo(serverId: String): List<BookmarkEntity>

    // Unpaged queries for select-all (no LIMIT/OFFSET)
    @Query("""
        SELECT localId, remoteId, serverId, title, url,
               description, imageUrl, bannerImageAssetId, screenshotAssetId, tags, listIds, isStarred, isArchived,
               isRead, createdAt, readingTimeMinutes, readingProgress, readingScrollIndex, readingScrollOffset,
               modifiedAt, progressSyncedAt,
               '' as content
        FROM bookmarks
        WHERE serverId = :serverId AND isArchived = 0
        ORDER BY createdAt DESC
    """)
    @SuppressWarnings(RoomWarnings.QUERY_MISMATCH)
    suspend fun getAllNotArchivedForServer(serverId: String): List<BookmarkEntity>

    @Query("""
        SELECT localId, remoteId, serverId, title, url,
               description, imageUrl, bannerImageAssetId, screenshotAssetId, tags, listIds, isStarred, isArchived,
               isRead, createdAt, readingTimeMinutes, readingProgress, readingScrollIndex, readingScrollOffset,
               modifiedAt, progressSyncedAt,
               '' as content
        FROM bookmarks
        WHERE serverId = :serverId AND isStarred = 1
        ORDER BY createdAt DESC
    """)
    @SuppressWarnings(RoomWarnings.QUERY_MISMATCH)
    suspend fun getAllFavoritesForServer(serverId: String): List<BookmarkEntity>

    @Query("""
        SELECT localId, remoteId, serverId, title, url,
               description, imageUrl, bannerImageAssetId, screenshotAssetId, tags, listIds, isStarred, isArchived,
               isRead, createdAt, readingTimeMinutes, readingProgress, readingScrollIndex, readingScrollOffset,
               modifiedAt, progressSyncedAt,
               '' as content
        FROM bookmarks
        WHERE serverId = :serverId AND isArchived = 1
        ORDER BY createdAt DESC
    """)
    @SuppressWarnings(RoomWarnings.QUERY_MISMATCH)
    suspend fun getAllArchivedForServer(serverId: String): List<BookmarkEntity>

    @Query("""
        SELECT localId, remoteId, serverId, title, url,
               description, imageUrl, bannerImageAssetId, screenshotAssetId, tags, listIds, isStarred, isArchived,
               isRead, createdAt, readingTimeMinutes, readingProgress, readingScrollIndex, readingScrollOffset,
               modifiedAt, progressSyncedAt,
               '' as content
        FROM bookmarks
        WHERE serverId = :serverId
        ORDER BY createdAt DESC
    """)
    @SuppressWarnings(RoomWarnings.QUERY_MISMATCH)
    suspend fun getAllBookmarksForServerSuspend(serverId: String): List<BookmarkEntity>

    @Query("""
        SELECT localId, remoteId, serverId, title, url,
               description, imageUrl, bannerImageAssetId, screenshotAssetId, tags, listIds, isStarred, isArchived,
               isRead, createdAt, readingTimeMinutes, readingProgress, readingScrollIndex, readingScrollOffset,
               modifiedAt, progressSyncedAt,
               '' as content
        FROM bookmarks
        WHERE serverId = :serverId
        AND (listIds = :listId
             OR listIds LIKE :listId || ',%'
             OR listIds LIKE '%,' || :listId
             OR listIds LIKE '%,' || :listId || ',%')
        ORDER BY createdAt DESC
    """)
    @SuppressWarnings(RoomWarnings.QUERY_MISMATCH)
    suspend fun getAllBookmarksForList(serverId: String, listId: String): List<BookmarkEntity>
}
