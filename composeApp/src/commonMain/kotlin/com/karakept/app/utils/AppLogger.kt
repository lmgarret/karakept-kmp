package com.karakept.app.utils

object AppLogger {
    enum class Level { DEBUG, INFO, WARN, ERROR }

    var minLevel: Level = Level.DEBUG

    fun d(tag: String, message: String) = log(Level.DEBUG, tag, message)
    fun i(tag: String, message: String) = log(Level.INFO, tag, message)
    fun w(tag: String, message: String, throwable: Throwable? = null) =
        log(Level.WARN, tag, message, throwable)
    fun e(tag: String, message: String, throwable: Throwable? = null) =
        log(Level.ERROR, tag, message, throwable)

    private fun log(level: Level, tag: String, message: String, throwable: Throwable? = null) {
        if (level < minLevel) return
        val ts = currentTimestamp()
        val prefix = if (ts.isNotEmpty()) "$ts " else ""
        println("$prefix${level.name[0]}/$tag: $message")
        throwable?.let { println("$prefix${level.name[0]}/$tag: ${it.stackTraceToString()}") }
    }
}

/** Returns a timestamp prefix for log lines, or empty string if the platform handles it (e.g. Logcat). */
internal expect fun currentTimestamp(): String
