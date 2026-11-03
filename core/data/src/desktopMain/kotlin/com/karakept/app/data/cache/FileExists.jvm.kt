package com.karakept.app.data.cache

import java.io.File

actual fun fileExists(path: String): Boolean {
    return File(path).exists()
}
