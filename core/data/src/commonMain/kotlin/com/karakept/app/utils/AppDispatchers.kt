package com.karakept.app.utils

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

/**
 * The dispatchers used by the data layer, injected rather than referenced statically so
 * tests can substitute a `TestDispatcher`. Without this, work parked on a real thread pool
 * is invisible to `advanceUntilIdle()` and tests sample state the production code has not
 * reached yet.
 */
interface AppDispatchers {
    val io: CoroutineDispatcher
    val default: CoroutineDispatcher
}

class DefaultAppDispatchers : AppDispatchers {
    override val io: CoroutineDispatcher = Dispatchers.IO
    override val default: CoroutineDispatcher = Dispatchers.Default
}
