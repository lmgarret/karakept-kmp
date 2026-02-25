package com.karakept.app.data.local

import androidx.room.Room
import androidx.room.RoomDatabase
import com.karakept.app.data.local.migrations.MIGRATION_1_2
import com.karakept.app.data.local.migrations.MIGRATION_2_3
import com.karakept.app.data.local.migrations.MIGRATION_3_4
import com.karakept.app.data.local.migrations.MIGRATION_5_6
import com.karakept.app.data.local.migrations.MIGRATION_6_7
import java.io.File

actual fun getDatabaseBuilder(): RoomDatabase.Builder<AppDatabase> {
    val dbFile = File(System.getProperty("java.io.tmpdir"), "karakept.db")
    return Room.databaseBuilder<AppDatabase>(
        name = dbFile.absolutePath,
    )
        .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_5_6, MIGRATION_6_7)
        .fallbackToDestructiveMigration(true)
}
