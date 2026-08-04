package com.karakept.app.utils

import com.karakept.app.utils.AppLogger
import java.awt.Desktop
import java.io.File

/** Root data directory, respecting XDG on Linux / Flatpak (see AppDirs.kt for rationale). */
private fun appDataDir(): File = File(
    when {
        System.getenv("FLATPAK_ID") != null ->
            System.getenv("XDG_DATA_HOME")
                ?: "${System.getProperty("user.home")}/.var/app/${System.getenv("FLATPAK_ID")}/data"
        System.getProperty("os.name").lowercase().contains("linux") ->
            "${System.getenv("XDG_DATA_HOME") ?: "${System.getProperty("user.home")}/.local/share"}/karakept"
        else ->
            "${System.getProperty("user.home")}/.karakept"
    }
).also { it.mkdirs() }

/** Cache directory for ephemeral data (image cache, etc.). */
private fun appCacheDir(): File = File(
    when {
        System.getenv("FLATPAK_ID") != null ->
            System.getenv("XDG_CACHE_HOME")
                ?: "${System.getProperty("user.home")}/.var/app/${System.getenv("FLATPAK_ID")}/cache"
        System.getProperty("os.name").lowercase().contains("linux") ->
            "${System.getenv("XDG_CACHE_HOME") ?: "${System.getProperty("user.home")}/.cache"}/karakept"
        else ->
            "${System.getProperty("user.home")}/.karakept"
    }
).also { it.mkdirs() }

actual object FileUtils {
    actual fun getAssetsDirectory(): String =
        File(appDataDir(), "assets").also { it.mkdirs() }.absolutePath

    actual fun getImageCacheDirectory(): String =
        File(appCacheDir(), "image_cache").also { it.mkdirs() }.absolutePath

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
        val appDir = appDataDir()
        return StorageInfo(
            usedBytes = getFolderSize(appDir),
            freeBytes = appDir.freeSpace,
            totalBytes = appDir.totalSpace
        )
    }

    actual fun getBackupDirectory(): String =
        File(appDataDir(), "backups").also { it.mkdirs() }.absolutePath

    actual fun saveFileToDirectory(directoryPath: String, fileName: String, content: ByteArray): String =
        saveFile(directoryPath, fileName, content)

    actual fun getDirectoryDisplayName(directoryPath: String): String = directoryPath

    actual fun readFileAsText(path: String): String? {
        return try {
            File(path).readText()
        } catch (e: Exception) {
            null
        }
    }

    actual fun deleteFile(path: String) {
        try { File(path).delete() } catch (_: Exception) {}
    }

    actual fun shareBackupFile(filePath: String) {
        val file = File(filePath)
        if (!file.exists()) return
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(file.parentFile)
            }
        } catch (e: Exception) {
            // Silently fail – user can navigate manually
        }
    }

    actual fun openFileExternally(path: String, mimeType: String): Boolean {
        // Desktop resolves the handler from the file extension, so mimeType is unused here.
        val file = File(path)
        if (!file.exists()) return false
        return try {
            if (!Desktop.isDesktopSupported()) return false
            val desktop = Desktop.getDesktop()
            if (!desktop.isSupported(Desktop.Action.OPEN)) return false
            desktop.open(file)
            true
        } catch (e: Exception) {
            AppLogger.e("FileUtils", "Failed to open $path externally: ${e.message}", e)
            false
        }
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
