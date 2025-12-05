package com.karakept.app.utils

expect object FileUtils {
    fun getAssetsDirectory(): String
    fun saveFile(path: String, fileName: String, content: ByteArray): String
    fun getStorageInfo(): StorageInfo
}

data class StorageInfo(
    val usedBytes: Long,
    val freeBytes: Long,
    val totalBytes: Long
)
