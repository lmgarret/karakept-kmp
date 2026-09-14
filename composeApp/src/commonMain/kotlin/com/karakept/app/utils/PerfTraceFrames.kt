package com.karakept.app.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import kotlin.time.ExperimentalTime
import kotlin.time.TimeSource

/**
 * Times the frames a state change costs, for the work [PerfTrace.measure] cannot wrap.
 *
 * Publishing a new bookmark window is two milliseconds of state write and then an unknown amount
 * of Compose: recomposition, layout and draw for a dataset that may have grown by a thousand
 * rows, all on the main thread, all *after* the call that caused it returned. Measurements around
 * the write therefore say the publish was free while the thread stalls for 50-80ms immediately
 * afterwards — which is what the captures show, stalls with nothing measured beside them.
 *
 * Two numbers come out of it, because they mean different things:
 *  - `…ToFrame` — from the change to the start of the next frame. Scheduling latency: how long the
 *    main thread was busy with something else before it could begin drawing the change.
 *  - `…Frame` — the span of that frame itself. The cost of absorbing the change: a frame budget is
 *    16ms, so anything materially above that dropped frames.
 *
 * [key] is what identifies a change — pass the dataset itself, so a new list starts a measurement
 * and an unchanged one does not.
 */
@OptIn(ExperimentalTime::class)
@Composable
fun TraceFramesAfterChange(name: String, key: Any?, detail: String = "") {
    LaunchedEffect(key) {
        if (!PerfTrace.enabled) return@LaunchedEffect
        val changed = TimeSource.Monotonic.markNow()
        // Resumes as the next frame begins, before it composes.
        withFrameNanos { }
        val frameStart = TimeSource.Monotonic.markNow()
        PerfTrace.report("${name}ToFrame", changed.elapsedNow().inWholeMilliseconds, detail)
        // Resumes as the frame after that begins, so the span covers the one that drew the change.
        withFrameNanos { }
        PerfTrace.report("${name}Frame", frameStart.elapsedNow().inWholeMilliseconds, detail)
    }
}
