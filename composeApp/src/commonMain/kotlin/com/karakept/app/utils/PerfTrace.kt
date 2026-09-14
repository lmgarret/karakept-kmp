package com.karakept.app.utils

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlin.time.ExperimentalTime
import kotlin.time.TimeSource

/**
 * Temporary instrumentation for the main-thread work behind the bookmark list's scroll jank.
 *
 * Two things it answers, which guesswork cannot: **how long** a piece of work takes, and **which
 * thread** it ran on. `viewModelScope` is the main dispatcher, and a `stateIn` collects its
 * upstream — every `combine` transform with it — in that context, so work written as a flow
 * operator runs on the UI thread unless something moved it. [watchMainThread] then reports the
 * consequence directly: a stretch where the main thread was not running at all.
 *
 * Dev builds only (see `App`), and meant to be removed once the numbers have been read.
 */
@OptIn(ExperimentalTime::class)
object PerfTrace {
    var enabled: Boolean = false

    private const val TAG = "Perf"

    /** Half a frame at 60Hz. Anything slower than this on the main thread costs a frame. */
    private const val SLOW_MS = 8L

    /** How often the rolling totals are dumped, so a session reads as a table not a firehose. */
    private const val SUMMARY_EVERY_MS = 5_000L

    private const val TICK_MS = 16L

    /**
     * A tick this late means the main thread spent the difference not running.
     *
     * Three frames at 60Hz. The first pass used 100ms and reported almost nothing, while the
     * thing being hunted turned out to be a 25-57ms block landing once a second — invisible to
     * that threshold and very visible to the eye.
     */
    private const val STALL_MS = 48L

    data class Stat(
        val calls: Int = 0,
        val totalMs: Long = 0,
        val maxMs: Long = 0,
        val threads: Set<String> = emptySet(),
        val lastDetail: String = ""
    )

    private val stats = MutableStateFlow<Map<String, Stat>>(emptyMap())
    private var summaryMark = TimeSource.Monotonic.markNow()

    /** Times [block], attributing it to [name]. Returns whatever the block returns. */
    fun <T> measure(name: String, detail: String = "", block: () -> T): T {
        if (!enabled) return block()
        val startThread = currentThreadName()
        val mark = TimeSource.Monotonic.markNow()
        val result = block()
        record(name, mark.elapsedNow().inWholeMilliseconds, detail, startThread)
        return result
    }

    /**
     * As [measure], for work that suspends. The time includes anything it waited on.
     *
     * The thread is recorded on both sides, and for suspending work that is the whole point: one
     * thread name means the block ran where it was called, two mean it hopped and the duration is
     * wall time rather than time stolen from the caller. Reading a single name off the resumption
     * is what made the paged read look like it was blocking the UI thread when it was not.
     */
    suspend fun <T> measureSuspending(name: String, detail: String = "", block: suspend () -> T): T {
        if (!enabled) return block()
        val startThread = currentThreadName()
        val mark = TimeSource.Monotonic.markNow()
        val result = block()
        record(name, mark.elapsedNow().inWholeMilliseconds, detail, startThread)
        return result
    }

    /** Records that [name] happened, for work whose frequency is the problem, not its cost. */
    fun count(name: String, detail: String = "") {
        if (!enabled) return
        record(name, 0, detail, currentThreadName())
    }

    private fun record(name: String, ms: Long, detail: String, startThread: String) {
        val endThread = currentThreadName()
        val thread = if (startThread == endThread) startThread else "$startThread>$endThread"
        stats.update { current ->
            val stat = current[name] ?: Stat()
            current + (name to stat.copy(
                calls = stat.calls + 1,
                totalMs = stat.totalMs + ms,
                maxMs = maxOf(stat.maxMs, ms),
                threads = stat.threads + thread,
                lastDetail = detail
            ))
        }
        if (ms >= SLOW_MS) {
            val suffix = if (detail.isEmpty()) "" else " ($detail)"
            AppLogger.w(TAG, "$name took ${ms}ms on $thread$suffix")
        }
        maybeLogSummary()
    }

    /**
     * Dumps and clears the rolling totals once [SUMMARY_EVERY_MS] have passed.
     *
     * Clearing is what makes a summary readable: each one covers the seconds just gone, so a
     * table logged while scrolling describes that scroll rather than the whole session.
     */
    fun maybeLogSummary() {
        if (!enabled) return
        if (summaryMark.elapsedNow().inWholeMilliseconds < SUMMARY_EVERY_MS) return
        summaryMark = TimeSource.Monotonic.markNow()
        val snapshot = stats.value
        if (snapshot.isEmpty()) return
        stats.value = emptyMap()

        AppLogger.i(TAG, "--- last ${SUMMARY_EVERY_MS / 1000}s ---")
        snapshot.entries
            .sortedByDescending { it.value.totalMs }
            .forEach { (name, stat) ->
                AppLogger.i(
                    TAG,
                    "  $name x${stat.calls} total=${stat.totalMs}ms max=${stat.maxMs}ms " +
                        "on=${stat.threads.joinToString("/")} last=${stat.lastDetail}"
                )
            }
    }

    /**
     * Reports stretches where the main thread stopped running, and keeps the summaries coming
     * when nothing else is being measured.
     *
     * Must be called from a coroutine on the main dispatcher — the composition's own context is
     * one, which is where `App` starts it. The tick is posted for [TICK_MS] out, so however much
     * later it actually resumes is how long the thread was busy elsewhere. This measures the
     * symptom rather than any one cause, so a stall with nothing else logged beside it is still a
     * finding: the cost is somewhere this file is not looking (a Compose pass, most likely).
     */
    suspend fun watchMainThread() {
        if (!enabled) return
        var mark = TimeSource.Monotonic.markNow()
        while (true) {
            delay(TICK_MS)
            val gap = mark.elapsedNow().inWholeMilliseconds
            if (gap >= STALL_MS) {
                AppLogger.w(TAG, "main thread stalled ${gap}ms")
            }
            maybeLogSummary()
            mark = TimeSource.Monotonic.markNow()
        }
    }
}

/** Name of the thread the caller is running on — "main" is the one that matters. */
internal expect fun currentThreadName(): String
