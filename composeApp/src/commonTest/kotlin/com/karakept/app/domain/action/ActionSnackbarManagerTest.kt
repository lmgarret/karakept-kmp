package com.karakept.app.domain.action

import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Behavioral tests for [ActionSnackbarManager] — verifies that each public method
 * emits the correct [SnackbarEvent] variant via [ActionSnackbarManager.snackbarEvents].
 */
class ActionSnackbarManagerTest {

    private fun manager() = ActionSnackbarManager()

    // ------------------------------------------------------------------
    // showSnackbar → SnackbarEvent.Message
    // ------------------------------------------------------------------

    @Test
    fun showSnackbar_emitsMessageEvent() = runTest {
        val manager = manager()
        val collected = mutableListOf<SnackbarEvent>()

        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            manager.snackbarEvents.collect { collected.add(it) }
        }

        manager.showSnackbar("Something happened")

        assertEquals(1, collected.size)
        val event = assertIs<SnackbarEvent.Message>(collected.first())
        assertEquals("Something happened", event.text)

        job.cancel()
    }

    // ------------------------------------------------------------------
    // showSnackbarWithUndo → SnackbarEvent.MessageWithUndo
    // ------------------------------------------------------------------

    @Test
    fun showSnackbarWithUndo_emitsMessageWithUndoEvent() = runTest {
        val manager = manager()
        val collected = mutableListOf<SnackbarEvent>()

        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            manager.snackbarEvents.collect { collected.add(it) }
        }

        var undoCalled = false
        manager.showSnackbarWithUndo("Item deleted", onUndo = { undoCalled = true })

        assertEquals(1, collected.size)
        val event = assertIs<SnackbarEvent.MessageWithUndo>(collected.first())
        assertEquals("Item deleted", event.text)
        event.onUndo()
        assertTrue(undoCalled, "onUndo lambda must invoke the caller-provided callback")

        job.cancel()
    }

    // ------------------------------------------------------------------
    // showErrorWithRetry → SnackbarEvent.MessageWithAction with "Retry" label
    // ------------------------------------------------------------------

    @Test
    fun showErrorWithRetry_emitsMessageWithActionEventWithRetryLabel() = runTest {
        val manager = manager()
        val collected = mutableListOf<SnackbarEvent>()

        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            manager.snackbarEvents.collect { collected.add(it) }
        }

        manager.showErrorWithRetry("Couldn't sync bookmarks") { /* retry */ }

        assertEquals(1, collected.size)
        val event = assertIs<SnackbarEvent.MessageWithAction>(collected.first())
        assertEquals("Couldn't sync bookmarks", event.text)
        assertEquals("Retry", event.actionLabel)

        job.cancel()
    }

    @Test
    fun showErrorWithRetry_retryLambdaIsInvokedWhenOnActionCalled() = runTest {
        val manager = manager()
        val collected = mutableListOf<SnackbarEvent>()

        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            manager.snackbarEvents.collect { collected.add(it) }
        }

        var retryCalled = false
        manager.showErrorWithRetry("Couldn't load bookmarks") { retryCalled = true }

        val event = assertIs<SnackbarEvent.MessageWithAction>(collected.first())
        event.onAction()
        assertTrue(retryCalled, "onAction must invoke the retry lambda provided to showErrorWithRetry")

        job.cancel()
    }
}
