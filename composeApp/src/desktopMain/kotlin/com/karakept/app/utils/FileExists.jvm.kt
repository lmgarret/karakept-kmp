package com.karakept.app.utils

import java.io.File

actual fun fileExists(path: String): Boolean {
    return File(path).exists()
}
