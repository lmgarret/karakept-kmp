package com.karakept.app.ui.input

import com.karakept.app.data.model.PageTurnDirection
import com.karakept.app.data.model.PageTurnKeyBindings
import com.karakept.app.data.repository.SettingsRepository
import com.karakept.app.utils.AppDispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Routes hardware key presses to page turns.
 *
 * Platform entry points ([com.karakept.app.MainActivity] on Android, the reader's
 * `onPreviewKeyEvent` on desktop) hand raw key codes to [onKeyDown] and honour the returned
 * "consumed" flag; screens collect [events] and scroll themselves. Keeping the mapping here rather
 * than in a composable means the Android side can intercept volume keys before the system volume
 * UI claims them, which a Compose key modifier cannot do.
 *
 * Bindings are captured from the device rather than hardcoded — e-ink readers disagree about which
 * codes their facade buttons emit, so [captureMode] lets the settings screen learn them.
 */
class PageTurnDispatcher(
    settingsRepository: SettingsRepository,
    appDispatchers: AppDispatchers
) {
    private val _bindings = MutableStateFlow(PageTurnKeyBindings())
    val bindings: StateFlow<PageTurnKeyBindings> = _bindings.asStateFlow()

    // Bindings are read on a key press from a platform callback that cannot suspend, so they are
    // mirrored into a StateFlow here. The scope belongs to this singleton, which lives as long as
    // the process — the mirror must outlive any screen that happens to be showing.
    private val scope = CoroutineScope(appDispatchers.default + SupervisorJob())

    init {
        scope.launch {
            settingsRepository.pageTurnKeyBindings.collect { _bindings.value = it }
        }
    }

    // extraBufferCapacity so a key press is never dropped when the collector is mid-scroll.
    private val _events = MutableSharedFlow<PageTurnDirection>(extraBufferCapacity = 4)
    val events: SharedFlow<PageTurnDirection> = _events.asSharedFlow()

    private val _capturedKeys = MutableSharedFlow<Int>(extraBufferCapacity = 4)

    /** Raw key codes, emitted only while [captureMode] is on. Used by the binding UI. */
    val capturedKeys: SharedFlow<Int> = _capturedKeys.asSharedFlow()

    private val _captureMode = MutableStateFlow(false)
    val captureMode: StateFlow<Boolean> = _captureMode.asStateFlow()

    fun updateBindings(bindings: PageTurnKeyBindings) {
        _bindings.value = bindings
    }

    fun setCaptureMode(enabled: Boolean) {
        _captureMode.value = enabled
    }

    /**
     * @return true when the key was handled and the platform should not pass it on. Consuming is
     *   what stops a bound volume key from also opening the system volume overlay.
     */
    fun onKeyDown(keyCode: Int): Boolean {
        if (_captureMode.value) {
            _capturedKeys.tryEmit(keyCode)
            return true
        }
        val direction = _bindings.value.directionFor(keyCode) ?: return false
        return emitDirection(direction)
    }

    /**
     * Requests a page turn directly, bypassing the key-code lookup. Used by the desktop keyboard
     * mapping, where PageUp/PageDown are fixed rather than user-bound.
     *
     * @return true when a screen was listening and the key should be treated as consumed.
     */
    fun emitDirection(direction: PageTurnDirection): Boolean {
        if (!_bindings.value.enabled) return false
        // Nothing page-turnable is on screen (settings, login, an empty back stack), so leave the
        // key to the platform. Without this, binding the volume rocker would cost volume control
        // everywhere in the app rather than only where a page turn means something.
        if (_events.subscriptionCount.value == 0) return false
        _events.tryEmit(direction)
        return true
    }
}
