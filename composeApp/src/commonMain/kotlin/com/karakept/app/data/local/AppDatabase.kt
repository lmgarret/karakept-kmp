package com.karakept.app.data.local

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import com.karakept.app.data.local.dao.BookmarkDao
import com.karakept.app.data.local.dao.ServerDao
import com.karakept.app.data.local.entity.BookmarkEntity
import com.karakept.app.data.local.entity.ServerEntity

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
        AssetEntity::class,
        PendingActionEntity::class,
        HighlightEntity::class,
        ListEntity::class
    ],
    version = 11
)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun serverDao(): ServerDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun assetDao(): AssetDao
    abstract fun pendingActionDao(): PendingActionDao
    abstract fun highlightDao(): HighlightDao
    abstract fun listDao(): ListDao
}

@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase>
