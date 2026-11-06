package com.karakept.app.ui.components.reader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import com.karakept.app.utils.AppLogger
import kotlin.time.TimeSource

/** Diagnostic timings for opening an article in the reader (#457). Filter logcat on `ReaderPerf`. */
internal object ReaderPerf {
    const val TAG = "ReaderPerf"

    fun log(message: String) = AppLogger.d(TAG, message)

    inline fun <T> time(label: String, block: () -> T): T {
        val mark = TimeSource.Monotonic.markNow()
        val result = block()
        log("$label: ${mark.elapsedNow().inWholeMilliseconds} ms")
        return result
    }
}

/** Logs every frame gap over [thresholdMs] for [windowMs] after [key] changes, so stalls show up by size and time. */
@Composable
internal fun ReaderFrameGapLogger(key: Any?, windowMs: Long = 6_000, thresholdMs: Long = 32) {
    LaunchedEffect(key) {
        val start = TimeSource.Monotonic.markNow()
        var last = withFrameNanos { it }
        var stalls = 0
        var stalledMs = 0L
        while (start.elapsedNow().inWholeMilliseconds < windowMs) {
            val now = withFrameNanos { it }
            val gapMs = (now - last) / 1_000_000
            if (gapMs > thresholdMs) {
                stalls++
                stalledMs += gapMs
                ReaderPerf.log("frame gap $gapMs ms at +${start.elapsedNow().inWholeMilliseconds} ms")
            }
            last = now
        }
        ReaderPerf.log("frame gaps over $thresholdMs ms in first ${windowMs / 1000} s: $stalls, $stalledMs ms total")
    }
}
