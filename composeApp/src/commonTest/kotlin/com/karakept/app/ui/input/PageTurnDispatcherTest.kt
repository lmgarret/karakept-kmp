package com.karakept.app.ui.input

import com.karakept.app.data.model.PageTurnDirection
import com.karakept.app.data.model.PageTurnKeyBindings
import com.karakept.app.data.repository.FakeDataStore
import com.karakept.app.data.repository.SettingsRepository
import com.karakept.app.data.repository.setPageTurnKeyCode
import com.karakept.app.data.repository.setPageTurnKeysEnabled
import com.karakept.app.utils.AppDispatchers
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val KEY_VOLUME_UP = 24
private const val KEY_VOLUME_DOWN = 25
private const val KEY_UNBOUND = 66

@OptIn(ExperimentalCoroutinesApi::class)
class PageTurnDispatcherTest {

    private val testDispatcher = StandardTestDispatcher()

    private val testAppDispatchers = object : AppDispatchers {
        override val io: CoroutineDispatcher = testDispatcher
        override val default: CoroutineDispatcher = testDispatcher
    }

    private fun dispatcherWith(repo: SettingsRepository) =
        PageTurnDispatcher(repo, testAppDispatchers)

    private fun repository() = SettingsRepository(FakeDataStore())

    @Test
    fun `bound key emits its direction and is consumed`() = runTest(testDispatcher) {
        val repo = repository()
        repo.setPageTurnKeyCode(PageTurnDirection.NEXT, KEY_VOLUME_DOWN)
        val dispatcher = dispatcherWith(repo)
        advanceUntilIdle()

        val received = async { dispatcher.events.first() }
        advanceUntilIdle()

        assertTrue(dispatcher.onKeyDown(KEY_VOLUME_DOWN))
        advanceUntilIdle()
        assertEquals(PageTurnDirection.NEXT, received.await())
    }

    @Test
    fun `unbound key is not consumed so the platform still handles it`() = runTest(testDispatcher) {
        val repo = repository()
        repo.setPageTurnKeyCode(PageTurnDirection.NEXT, KEY_VOLUME_DOWN)
        val dispatcher = dispatcherWith(repo)
        advanceUntilIdle()

        assertFalse(dispatcher.onKeyDown(KEY_UNBOUND))
    }

    @Test
    fun `disabling hardware keys releases every bound key`() = runTest(testDispatcher) {
        val repo = repository()
        repo.setPageTurnKeyCode(PageTurnDirection.NEXT, KEY_VOLUME_DOWN)
        repo.setPageTurnKeysEnabled(false)
        val dispatcher = dispatcherWith(repo)
        advanceUntilIdle()

        assertFalse(dispatcher.onKeyDown(KEY_VOLUME_DOWN))
    }

    @Test
    fun `capture mode reports the raw key code and suppresses the page turn`() = runTest(testDispatcher) {
        val repo = repository()
        repo.setPageTurnKeyCode(PageTurnDirection.NEXT, KEY_VOLUME_DOWN)
        val dispatcher = dispatcherWith(repo)
        advanceUntilIdle()

        val captured = async { dispatcher.capturedKeys.first() }
        advanceUntilIdle()
        dispatcher.setCaptureMode(true)

        // A key that is already bound must still be reported raw, not turned into a page turn.
        assertTrue(dispatcher.onKeyDown(KEY_VOLUME_DOWN))
        advanceUntilIdle()
        assertEquals(KEY_VOLUME_DOWN, captured.await())
    }

    @Test
    fun `capture mode consumes keys that are not bound to anything`() = runTest(testDispatcher) {
        val dispatcher = dispatcherWith(repository())
        advanceUntilIdle()
        dispatcher.setCaptureMode(true)

        assertTrue(dispatcher.onKeyDown(KEY_UNBOUND))
    }

    @Test
    fun `bindings follow the repository`() = runTest(testDispatcher) {
        val repo = repository()
        val dispatcher = dispatcherWith(repo)
        advanceUntilIdle()
        assertEquals(PageTurnKeyBindings(), dispatcher.bindings.value)

        repo.setPageTurnKeyCode(PageTurnDirection.PREVIOUS, KEY_VOLUME_UP)
        advanceUntilIdle()

        assertEquals(KEY_VOLUME_UP, dispatcher.bindings.value.previousKeyCode)
        assertTrue(dispatcher.onKeyDown(KEY_VOLUME_UP))
    }

    @Test
    fun `binding a key already used by the other direction releases the old one`() = runTest(testDispatcher) {
        val repo = repository()
        repo.setPageTurnKeyCode(PageTurnDirection.NEXT, KEY_VOLUME_DOWN)
        repo.setPageTurnKeyCode(PageTurnDirection.PREVIOUS, KEY_VOLUME_DOWN)
        val dispatcher = dispatcherWith(repo)
        advanceUntilIdle()

        // Otherwise one key would map to both directions and the lookup order would decide.
        assertEquals(KEY_VOLUME_DOWN, dispatcher.bindings.value.previousKeyCode)
        assertEquals(null, dispatcher.bindings.value.nextKeyCode)
    }

    @Test
    fun `emitDirection is refused while hardware keys are disabled`() = runTest(testDispatcher) {
        val repo = repository()
        repo.setPageTurnKeysEnabled(false)
        val dispatcher = dispatcherWith(repo)
        advanceUntilIdle()

        assertFalse(dispatcher.emitDirection(PageTurnDirection.NEXT))
    }

    @Test
    fun `emitDirection works without any key bound, for the desktop keyboard path`() = runTest(testDispatcher) {
        val dispatcher = dispatcherWith(repository())
        advanceUntilIdle()

        val received = async { dispatcher.events.first() }
        advanceUntilIdle()

        assertTrue(dispatcher.emitDirection(PageTurnDirection.PREVIOUS))
        advanceUntilIdle()
        assertEquals(PageTurnDirection.PREVIOUS, received.await())
    }
}
