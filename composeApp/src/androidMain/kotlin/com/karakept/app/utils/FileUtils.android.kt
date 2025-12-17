package com.karakept.app.utils

import com.karakept.app.data.local.AndroidContext
import java.io.File

actual object FileUtils {
    actual fun getAssetsDirectory(): String {
        val context = AndroidContext.context
        val dir = File(context.filesDir, "assets")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir.absolutePath
    }

    actual fun saveFile(path: String, fileName: String, content: ByteArray): String {
        val dir = File(path)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        val file = File(dir, fileName)
        file.writeBytes(content)
        return file.absolutePath
    }

    actual fun getStorageInfo(): StorageInfo {
        val context = AndroidContext.context
        val filesDir = context.filesDir
        val totalSpace = filesDir.totalSpace
        val freeSpace = filesDir.freeSpace

        // Calculate total app storage: app size + user data + cache
        var usedByApp = 0L

        // 1. App size (APK)
        try {
            val packageManager = context.packageManager
            val applicationInfo = packageManager.getApplicationInfo(context.packageName, 0)
            usedByApp += File(applicationInfo.sourceDir).length()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. User data: Internal files directory
        usedByApp += getFolderSize(filesDir)

        // 3. User data: Code cache directory (contains optimized code)
        context.codeCacheDir?.let {
            usedByApp += getFolderSize(it)
        }

        // 4. User data: Databases directory
        val databasePath = context.getDatabasePath("dummy").parentFile
        databasePath?.let {
            usedByApp += getFolderSize(it)
        }

        // 5. Cache: Cache directory
        context.cacheDir?.let {
            usedByApp += getFolderSize(it)
        }

        // 6. Cache: External cache directory (if exists)
        context.externalCacheDir?.let {
            usedByApp += getFolderSize(it)
        }

        // 7. External files directory (if exists)
        context.getExternalFilesDir(null)?.let {
            usedByApp += getFolderSize(it)
        }

        return StorageInfo(
            usedBytes = usedByApp,
            freeBytes = freeSpace,
            totalBytes = totalSpace
        )
    }

    private fun getFolderSize(file: File): Long {
        if (!file.exists()) return 0
        if (!file.isDirectory) return file.length()
        var size: Long = 0
        val files = file.listFiles() ?: return 0
        for (child in files) {
            size += getFolderSize(child)
        }
        return size
    }
}
