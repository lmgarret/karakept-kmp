package com.karakept.app.utils

import kotlinx.coroutines.CoroutineDispatcher

/**
 * Routes every dispatcher to the test's own [CoroutineDispatcher], so work the code under
 * test starts stays on the test scheduler and is drained by `advanceUntilIdle()`.
 */
class TestAppDispatchers(dispatcher: CoroutineDispatcher) : AppDispatchers {
    override val io: CoroutineDispatcher = dispatcher
    override val default: CoroutineDispatcher = dispatcher
}
