package com.karakept.app.utils

expect object FileUtils {
    fun getAssetsDirectory(): String
    fun getImageCacheDirectory(): String
    fun saveFile(path: String, fileName: String, content: ByteArray): String
    fun getStorageInfo(): StorageInfo

    /** Returns the directory used for backup files (created if missing). */
    fun getBackupDirectory(): String

    /**
     * Saves [content] as [fileName] inside [directoryPath] and returns an identifier for the
     * saved file (a plain file path on Desktop; a file path or SAF URI string on Android).
     *
     * On Android, [directoryPath] may be a SAF URI (`content://…`) returned by the directory
     * picker, in which case the write goes through the Content Resolver.  Regular file paths
     * are also accepted on all platforms.
     */
    fun saveFileToDirectory(directoryPath: String, fileName: String, content: ByteArray): String

    /**
     * Returns a human-readable label for [directoryPath].
     * On Desktop this is the path itself.  On Android, SAF URIs are decoded to a
     * friendlier form such as "Internal Storage/Downloads".
     */
    fun getDirectoryDisplayName(directoryPath: String): String

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
