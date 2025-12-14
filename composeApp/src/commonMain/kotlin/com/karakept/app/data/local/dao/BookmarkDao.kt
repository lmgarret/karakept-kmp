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
    @Query("SELECT * FROM bookmarks WHERE serverId = :serverId ORDER BY createdAt DESC")
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
}
