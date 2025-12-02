package com.karakept.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.karakept.app.data.local.entity.SavedFilterEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedFilterDao {
    @Query("SELECT * FROM saved_filters ORDER BY displayOrder ASC, id ASC")
    fun getAllSavedFilters(): Flow<List<SavedFilterEntity>>

    @Query("SELECT * FROM saved_filters WHERE isVisibleInDrawer = 1 ORDER BY displayOrder ASC, id ASC")
    fun getVisibleFilters(): Flow<List<SavedFilterEntity>>

    @Query("SELECT * FROM saved_filters WHERE isVisibleInDrawer = 0 ORDER BY displayOrder ASC, id ASC")
    fun getHiddenFilters(): Flow<List<SavedFilterEntity>>

    @Query("SELECT * FROM saved_filters WHERE isDefault = 1 LIMIT 1")
    suspend fun getDefaultFilter(): SavedFilterEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSavedFilter(filter: SavedFilterEntity)

    @Update
    suspend fun updateSavedFilter(filter: SavedFilterEntity)

    @Update
    suspend fun updateSavedFilters(filters: List<SavedFilterEntity>)

    @Delete
    suspend fun deleteSavedFilter(filter: SavedFilterEntity)

    @Query("UPDATE saved_filters SET isDefault = 0")
    suspend fun clearDefaultFilter()
}
