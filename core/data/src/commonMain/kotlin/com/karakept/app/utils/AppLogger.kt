package com.karakept.app.utils

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

object AppLogger {
    enum class Level { DEBUG, INFO, WARN, ERROR }

    var minLevel: Level = Level.DEBUG

    /**
     * When true, formatted log lines are retained in a bounded in-memory ring buffer exposed
     * via [history] for the in-app log viewer. Enabled only in dev builds (see App startup) so
     * release builds pay nothing.
     */
    var captureEnabled: Boolean = false

    private const val MAX_HISTORY = 1000
    private val _history = MutableStateFlow<List<String>>(emptyList())

    /** Reactive snapshot of the most recent (up to [MAX_HISTORY]) formatted log lines. */
    val history: StateFlow<List<String>> = _history.asStateFlow()

    fun clearHistory() {
        _history.value = emptyList()
    }

    fun d(tag: String, message: String) = log(Level.DEBUG, tag, message)
    fun i(tag: String, message: String) = log(Level.INFO, tag, message)
    fun w(tag: String, message: String, throwable: Throwable? = null) =
        log(Level.WARN, tag, message, throwable)
    fun e(tag: String, message: String, throwable: Throwable? = null) =
        log(Level.ERROR, tag, message, throwable)

    private fun log(level: Level, tag: String, message: String, throwable: Throwable? = null) {
        if (level < minLevel) return
        val consoleTs = currentTimestamp()
        val consolePrefix = if (consoleTs.isNotEmpty()) "$consoleTs " else ""
        val body = "${level.name[0]}/$tag: $message"
        println("$consolePrefix$body")
        val consoleStack = throwable?.let { "$consolePrefix${level.name[0]}/$tag: ${it.stackTraceToString()}" }
        consoleStack?.let { println(it) }

        if (captureEnabled) {
            // Buffer lines carry their own timestamp so they are useful on Android too, where
            // currentTimestamp() is empty (Logcat prepends it, but the in-app viewer can't see that).
            val ts = bufferTimestamp()
            val line = "$ts $body"
            val stackLine = throwable?.let { "$ts ${level.name[0]}/$tag: ${it.stackTraceToString()}" }
            _history.update { current ->
                val appended = if (stackLine != null) current + line + stackLine else current + line
                if (appended.size > MAX_HISTORY) appended.subList(appended.size - MAX_HISTORY, appended.size).toList()
                else appended
            }
        }
    }

    private fun bufferTimestamp(): String {
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        fun pad(n: Int, width: Int = 2) = n.toString().padStart(width, '0')
        return "${pad(now.hour)}:${pad(now.minute)}:${pad(now.second)}.${pad(now.nanosecond / 1_000_000, 3)}"
    }
}

/** Returns a timestamp prefix for log lines, or empty string if the platform handles it (e.g. Logcat). */
internal expect fun currentTimestamp(): String
