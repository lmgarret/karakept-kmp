package com.karakept.app.data.local

import androidx.room3.RoomDatabase

internal expect fun getDatabaseBuilder(): RoomDatabase.Builder<AppDatabase>
