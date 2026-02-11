package com.karakept.app.utils

import java.io.File

actual object FileUtils {
    actual fun getAssetsDirectory(): String {
        val userHome = System.getProperty("user.home")
        val dir = File(userHome, ".karakept/assets")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir.absolutePath
    }

    actual fun getImageCacheDirectory(): String {
        val userHome = System.getProperty("user.home")
        val dir = File(userHome, ".karakept/image_cache")
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
        val userHome = System.getProperty("user.home")
        val appDir = File(userHome, ".karakept")
        if (!appDir.exists()) {
            appDir.mkdirs()
        }
        
        val totalSpace = appDir.totalSpace
        val freeSpace = appDir.freeSpace
        val usedByApp = getFolderSize(appDir)

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
