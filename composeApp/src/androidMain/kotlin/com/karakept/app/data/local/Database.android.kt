package com.karakept.app.data.local

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import com.karakept.app.data.local.migrations.MIGRATION_1_2
import com.karakept.app.data.local.migrations.MIGRATION_2_3
import com.karakept.app.data.local.migrations.MIGRATION_3_4
import com.karakept.app.data.local.migrations.MIGRATION_5_6
import com.karakept.app.data.local.migrations.MIGRATION_6_7
import com.karakept.app.data.local.migrations.MIGRATION_7_8
import com.karakept.app.data.local.migrations.MIGRATION_8_9
import com.karakept.app.data.local.migrations.MIGRATION_9_10
import com.karakept.app.data.local.migrations.MIGRATION_10_11

// We need to pass context somehow. For now, let's assume we can inject it or access it via a singleton/provider.
// A common pattern in KMP is to pass the context to the Koin module or a factory.
// However, to keep the expect/actual signature simple, we might need a global context provider or pass it in init.
// Let's use a Koin definition instead of a raw expect/actual for the builder if possible, but the expect/actual is for the builder itself.
// Let's change the expect to take a context-like object or just handle it in DI.
// Actually, the best way for Room KMP is to have a function that returns the database instance, and the platform implementation knows how to get the context.

// Revised approach: The expect function will be `fun getDatabase(context: Any? = null): AppDatabase`
// But `Any?` is ugly.
// Let's stick to `getDatabaseBuilder()` but we need context on Android.
// We can use a global `applicationContext` if we set it up in `Application` class.

lateinit var applicationContext: Context

fun getDatabaseBuilder(ctx: Context): RoomDatabase.Builder<AppDatabase> {
    val dbFile = ctx.getDatabasePath("karakept.db")
    return Room.databaseBuilder<AppDatabase>(
        context = ctx,
        name = dbFile.absolutePath
    )
}

// Wait, the expect function signature must match.
// Let's change the expect function in commonMain to not exist, and instead use Koin to provide the database.
// But we need platform specific code to create the builder.
// So we can have `fun Module.platformDatabaseModule()` extension in commonMain? No.

// Let's go with:
// commonMain: expect fun getDatabaseBuilder(): RoomDatabase.Builder<AppDatabase>
// androidMain: actual fun getDatabaseBuilder(): ... (needs context) -> This is hard without global context.

// Alternative:
// commonMain: class DatabaseFactory { fun create(): AppDatabase }
// And expect class DatabaseFactory.

// Let's use the `Room.databaseBuilder` in the DI module directly if possible, but `Room` object is available in commonMain?
// Yes, Room 2.7.0+ supports KMP.
// `Room.databaseBuilder<T>(name = ...)` requires a factory.
// On Android it needs Context.
// On Desktop it needs a path.

// Let's try this:
// In commonMain:
// expect fun getDatabaseBuilder(): RoomDatabase.Builder<AppDatabase>
//
// In Android, we can't implement this without context.
// So we will initialize a global context in `MainActivity` or `Application`.

object AndroidContext {
    lateinit var context: Context
}

actual fun getDatabaseBuilder(): RoomDatabase.Builder<AppDatabase> {
    val ctx = AndroidContext.context
    val dbFile = ctx.getDatabasePath("karakept.db")
    return Room.databaseBuilder<AppDatabase>(
        context = ctx,
        name = dbFile.absolutePath
    )
        .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11)
        .fallbackToDestructiveMigration(true)
}
