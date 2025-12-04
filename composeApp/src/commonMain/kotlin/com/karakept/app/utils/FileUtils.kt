package com.karakept.app.utils

expect object FileUtils {
    fun getAssetsDirectory(): String
    fun saveFile(path: String, fileName: String, content: ByteArray): String
}
