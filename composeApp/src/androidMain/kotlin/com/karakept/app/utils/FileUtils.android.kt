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
        val usedByApp = getFolderSize(filesDir)

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
