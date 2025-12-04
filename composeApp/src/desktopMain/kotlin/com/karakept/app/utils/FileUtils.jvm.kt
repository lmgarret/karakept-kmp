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

    actual fun saveFile(path: String, fileName: String, content: ByteArray): String {
        val dir = File(path)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        val file = File(dir, fileName)
        file.writeBytes(content)
        return file.absolutePath
    }
}
