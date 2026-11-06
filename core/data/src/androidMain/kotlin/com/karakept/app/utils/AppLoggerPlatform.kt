package com.karakept.app.utils

/** Logcat already prepends timestamps — return empty to avoid duplication. */
internal actual fun currentTimestamp(): String = ""
