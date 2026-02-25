package com.karakept.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.karakept.app.data.local.dao.BookmarkDao
import com.karakept.app.data.local.dao.ServerDao
import com.karakept.app.data.local.entity.BookmarkEntity
import com.karakept.app.data.local.entity.ServerEntity

import com.karakept.app.data.local.dao.SavedFilterDao
import com.karakept.app.data.local.entity.SavedFilterEntity
import com.karakept.app.data.local.dao.AssetDao
import com.karakept.app.data.local.entity.AssetEntity
import com.karakept.app.data.local.entity.PendingActionEntity
import com.karakept.app.data.local.dao.HighlightDao
import com.karakept.app.data.local.entity.HighlightEntity
import com.karakept.app.data.local.dao.PendingActionDao
import com.karakept.app.data.local.dao.ListDao
import com.karakept.app.data.local.entity.ListEntity

@Database(
    entities = [
        ServerEntity::class,
        BookmarkEntity::class,
        SavedFilterEntity::class,
        AssetEntity::class,
        PendingActionEntity::class,
        HighlightEntity::class,
        ListEntity::class
    ],
    version = 7
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun serverDao(): ServerDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun savedFilterDao(): SavedFilterDao
    abstract fun assetDao(): AssetDao
    abstract fun pendingActionDao(): PendingActionDao
    abstract fun highlightDao(): HighlightDao
    abstract fun listDao(): ListDao
}
