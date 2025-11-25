package com.karakept.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.karakept.app.data.local.dao.BookmarkDao
import com.karakept.app.data.local.dao.ServerDao
import com.karakept.app.data.local.entity.BookmarkEntity
import com.karakept.app.data.local.entity.ServerEntity

@Database(entities = [ServerEntity::class, BookmarkEntity::class], version = 2, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun serverDao(): ServerDao
    abstract fun bookmarkDao(): BookmarkDao
}
