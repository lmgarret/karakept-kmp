package com.karakept.app.utils

expect object FileUtils {
    fun getAssetsDirectory(): String
    fun getImageCacheDirectory(): String
    fun saveFile(path: String, fileName: String, content: ByteArray): String
    fun getStorageInfo(): StorageInfo

    /** Returns the directory used for backup files (created if missing). */
    fun getBackupDirectory(): String

    /** Reads the UTF-8 text content of the file at [path], or null if not readable. */
    fun readFileAsText(path: String): String?

    /** Shares / exports a backup file at [filePath] using the platform share sheet or save dialog. */
    fun shareBackupFile(filePath: String)
}

data class StorageInfo(
    val usedBytes: Long,
    val freeBytes: Long,
    val totalBytes: Long
)
