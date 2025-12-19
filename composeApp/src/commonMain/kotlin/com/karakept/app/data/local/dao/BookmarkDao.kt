package com.karakept.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.karakept.app.data.local.entity.BookmarkEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BookmarkDao {
    @Query("""
        SELECT localId, remoteId, originalRemoteId, serverId, title, url,
               description, imageUrl, bannerImageAssetId, screenshotAssetId, tags, listIds, isStarred, isArchived,
               isRead, createdAt, readingTimeMinutes, '' as content
        FROM bookmarks
        WHERE serverId = :serverId
        ORDER BY createdAt DESC
    """)
    fun getBookmarksForServer(serverId: String): Flow<List<BookmarkEntity>>

    @Query("SELECT * FROM bookmarks WHERE localId = :id")
    suspend fun getBookmarkById(id: Long): BookmarkEntity?

    @Query("SELECT * FROM bookmarks WHERE localId = :id")
    fun observeBookmarkById(id: Long): Flow<BookmarkEntity?>
    
    @Query("SELECT * FROM bookmarks WHERE remoteId = :remoteId AND serverId = :serverId LIMIT 1")
    suspend fun getBookmarkByRemoteId(remoteId: Long, serverId: String): BookmarkEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(bookmark: BookmarkEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmarks(bookmarks: List<BookmarkEntity>)

    @Update
    suspend fun updateBookmarks(bookmarks: List<BookmarkEntity>)

    @Delete
    suspend fun deleteBookmark(bookmark: BookmarkEntity)
    
    @Query("DELETE FROM bookmarks WHERE serverId = :serverId")
    suspend fun deleteAllBookmarksForServer(serverId: String)

    @Query("UPDATE bookmarks SET content = :content, readingTimeMinutes = :readingTime WHERE localId = :localId")
    suspend fun updateContent(localId: Long, content: String, readingTime: Int)

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
            readingTimeMinutes = :readingTimeMinutes
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
        readingTimeMinutes: Int
    )

    // Paginated queries
    @Query("""
        SELECT localId, remoteId, originalRemoteId, serverId, title, url,
               description, imageUrl, bannerImageAssetId, screenshotAssetId, tags, listIds, isStarred, isArchived,
               isRead, createdAt, readingTimeMinutes, '' as content
        FROM bookmarks
        WHERE serverId = :serverId
        ORDER BY createdAt DESC
        LIMIT :limit OFFSET :offset
    """)
    suspend fun getBookmarksPagedForServer(
        serverId: String,
        limit: Int,
        offset: Int
    ): List<BookmarkEntity>

    @Query("""
        SELECT localId, remoteId, originalRemoteId, serverId, title, url,
               description, imageUrl, bannerImageAssetId, screenshotAssetId, tags, listIds, isStarred, isArchived,
               isRead, createdAt, readingTimeMinutes, '' as content
        FROM bookmarks
        WHERE serverId = :serverId AND isStarred = 1
        ORDER BY createdAt DESC
        LIMIT :limit OFFSET :offset
    """)
    suspend fun getFavoritesPagedForServer(
        serverId: String,
        limit: Int,
        offset: Int
    ): List<BookmarkEntity>

    @Query("""
        SELECT localId, remoteId, originalRemoteId, serverId, title, url,
               description, imageUrl, bannerImageAssetId, screenshotAssetId, tags, listIds, isStarred, isArchived,
               isRead, createdAt, readingTimeMinutes, '' as content
        FROM bookmarks
        WHERE serverId = :serverId AND isArchived = 1
        ORDER BY createdAt DESC
        LIMIT :limit OFFSET :offset
    """)
    suspend fun getArchivedPagedForServer(
        serverId: String,
        limit: Int,
        offset: Int
    ): List<BookmarkEntity>

    @Query("""
        SELECT localId, remoteId, originalRemoteId, serverId, title, url,
               description, imageUrl, bannerImageAssetId, screenshotAssetId, tags, listIds, isStarred, isArchived,
               isRead, createdAt, readingTimeMinutes, '' as content
        FROM bookmarks
        WHERE serverId = :serverId AND isArchived = 0
        ORDER BY createdAt DESC
        LIMIT :limit OFFSET :offset
    """)
    suspend fun getNotArchivedPagedForServer(
        serverId: String,
        limit: Int,
        offset: Int
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
        SELECT localId, remoteId, originalRemoteId, serverId, title, url,
               description, imageUrl, bannerImageAssetId, screenshotAssetId, tags, listIds, isStarred, isArchived,
               isRead, createdAt, readingTimeMinutes, '' as content
        FROM bookmarks
        WHERE serverId = :serverId
        AND (listIds = :listId
             OR listIds LIKE :listId || ',%'
             OR listIds LIKE '%,' || :listId
             OR listIds LIKE '%,' || :listId || ',%')
        ORDER BY createdAt DESC
        LIMIT :limit OFFSET :offset
    """)
    suspend fun getBookmarksForListPaged(
        serverId: String,
        listId: String,
        limit: Int,
        offset: Int
    ): List<BookmarkEntity>

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

    // Query for sync that includes content length and reading time to determine if content exists
    @Query("""
        SELECT localId, remoteId, originalRemoteId, serverId, title, url,
               description, imageUrl, bannerImageAssetId, screenshotAssetId, tags, listIds, isStarred, isArchived,
               isRead, createdAt, readingTimeMinutes,
               CASE WHEN length(content) > 0 THEN 'HAS_CONTENT' ELSE '' END as content
        FROM bookmarks
        WHERE serverId = :serverId
    """)
    suspend fun getBookmarksForServerWithContentInfo(serverId: String): List<BookmarkEntity>
}
