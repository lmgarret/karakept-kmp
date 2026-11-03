package com.karakept.app.data.local

import androidx.room3.useReaderConnection
import androidx.room3.useWriterConnection

/**
 * Space SQLite holds for reuse but has not handed back to the device: the free pages left by
 * emptied rows. Zero on a database created with `auto_vacuum`, which returns them on commit; a
 * database created without it keeps them until a `VACUUM`.
 */
suspend fun AppDatabase.reclaimableBytes(): Long = useReaderConnection { connection ->
    val freePages = connection.usePrepared("PRAGMA freelist_count") { it.step(); it.getLong(0) }
    val pageSize = connection.usePrepared("PRAGMA page_size") { it.step(); it.getLong(0) }
    freePages * pageSize
}

/**
 * Hands emptied space back to the device. With `auto_vacuum` the file already shrank on commit,
 * but the write-ahead log keeps its high-water mark — a clear of a few hundred articles leaves
 * megabytes there — so it is truncated either way. A `VACUUM` runs only when there are free pages
 * for it to drop: it rewrites the whole file.
 */
suspend fun AppDatabase.compact() {
    val vacuum = reclaimableBytes() > 0
    useWriterConnection { connection ->
        if (vacuum) connection.usePrepared("VACUUM") { it.step() }
        connection.usePrepared("PRAGMA wal_checkpoint(TRUNCATE)") { it.step() }
    }
}
