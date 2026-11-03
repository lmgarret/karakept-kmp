package com.karakept.app.utils

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val timestampFormatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")

internal actual fun currentTimestamp(): String =
    LocalDateTime.now().format(timestampFormatter)
