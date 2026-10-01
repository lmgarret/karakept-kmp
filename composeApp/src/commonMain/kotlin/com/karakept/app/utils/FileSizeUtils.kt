package com.karakept.app.utils

import kotlin.math.pow

fun formatFileSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val exp = (kotlin.math.ln(bytes.toDouble()) / kotlin.math.ln(1024.0)).toInt()
    val pre = "KMGTPE"[exp - 1]
    return "%.1f %sB".format(bytes / 1024.0.pow(exp.toDouble()), pre)
}
