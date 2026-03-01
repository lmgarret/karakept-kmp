package com.karakept.app.utils

import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
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

    actual fun getImageCacheDirectory(): String {
        val context = AndroidContext.context
        val dir = File(context.filesDir, "image_cache")
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

    actual fun getBackupDirectory(): String {
        val context = AndroidContext.context
        val dir = File(context.getExternalFilesDir(null), "backups")
            ?: File(context.filesDir, "backups")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir.absolutePath
    }

    actual fun readFileAsText(path: String): String? {
        return try {
            File(path).readText()
        } catch (e: Exception) {
            null
        }
    }

    actual fun shareBackupFile(filePath: String) {
        val context = AndroidContext.context
        val file = File(filePath)
        if (!file.exists()) return

        val uri: Uri = try {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
        } catch (e: Exception) {
            // Fallback: share JSON text content
            val content = file.readText()
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, content)
                putExtra(Intent.EXTRA_TITLE, file.name)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(sendIntent, "Share Backup").also {
                it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            return
        }

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Export Backup").also {
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
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
