package com.karakept.app.data.local

import androidx.room3.Room
import androidx.room3.RoomDatabase
import com.karakept.app.data.local.migrations.withAppSchema
import java.io.File

actual fun getDatabaseBuilder(): RoomDatabase.Builder<AppDatabase> {
    val dbFile = File(appDataDir(), "karakept.db")
    return Room.databaseBuilder<AppDatabase>(
        name = dbFile.absolutePath,
    )
        .withAppSchema()
        .fallbackToDestructiveMigration(true)
}
