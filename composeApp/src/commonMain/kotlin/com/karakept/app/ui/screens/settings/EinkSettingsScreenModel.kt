package com.karakept.app.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.karakept.app.data.model.PageTurnDirection
import com.karakept.app.data.model.PageTurnKeyBindings
import com.karakept.app.data.model.RowActionMode
import com.karakept.app.data.repository.SettingsRepository
import com.karakept.app.data.repository.setEinkDisableAnimations
import com.karakept.app.data.repository.setEinkHighContrast
import com.karakept.app.data.repository.setEinkInstantPageScroll
import com.karakept.app.data.repository.setEinkModeEnabled
import com.karakept.app.data.repository.setHideArticleThumbnails
import com.karakept.app.data.repository.setPageTurnKeyCode
import com.karakept.app.data.repository.setPageTurnKeysEnabled
import com.karakept.app.data.repository.setPageTurnOverlapPercent
import com.karakept.app.data.repository.setRowActionMode
import com.karakept.app.ui.input.PageTurnDispatcher
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class EinkSettingsScreenModel(
    private val settingsRepository: SettingsRepository,
    private val pageTurnDispatcher: PageTurnDispatcher
) : ViewModel() {

    val einkModeEnabled: StateFlow<Boolean> = settingsRepository.einkModeEnabled.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), false
    )

    val disableAnimations: StateFlow<Boolean> = settingsRepository.einkDisableAnimations.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), true
    )

    val highContrast: StateFlow<Boolean> = settingsRepository.einkHighContrast.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), true
    )

    val instantPageScroll: StateFlow<Boolean> = settingsRepository.einkInstantPageScroll.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), true
    )

    val keyBindings: StateFlow<PageTurnKeyBindings> = settingsRepository.pageTurnKeyBindings.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), PageTurnKeyBindings()
    )

    val hideArticleThumbnails: StateFlow<Boolean> = settingsRepository.hideArticleThumbnails.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), true
    )

    val rowActionMode: StateFlow<RowActionMode> = settingsRepository.rowActionMode.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), RowActionMode.SWIPE
    )

    fun setRowActionMode(mode: RowActionMode) = launchSetting {
        settingsRepository.setRowActionMode(mode)
    }

    fun setEinkModeEnabled(enabled: Boolean) = launchSetting {
        settingsRepository.setEinkModeEnabled(enabled)
    }

    fun setDisableAnimations(disable: Boolean) = launchSetting {
        settingsRepository.setEinkDisableAnimations(disable)
    }

    fun setHighContrast(enabled: Boolean) = launchSetting {
        settingsRepository.setEinkHighContrast(enabled)
    }

    fun setInstantPageScroll(enabled: Boolean) = launchSetting {
        settingsRepository.setEinkInstantPageScroll(enabled)
    }

    fun setHardwareKeysEnabled(enabled: Boolean) = launchSetting {
        settingsRepository.setPageTurnKeysEnabled(enabled)
    }

    fun setOverlapPercent(percent: Int) = launchSetting {
        settingsRepository.setPageTurnOverlapPercent(percent)
    }

    fun clearBinding(direction: PageTurnDirection) = launchSetting {
        settingsRepository.setPageTurnKeyCode(direction, null)
    }

    /**
     * Puts the dispatcher into capture mode and binds the next key the device reports to
     * [direction]. Gives up after [timeoutMillis] so a device whose buttons emit nothing the app
     * can see does not leave every key swallowed.
     *
     * @param onFinished called with the captured key code, or null on timeout/cancel.
     */
    fun captureKeyBinding(
        direction: PageTurnDirection,
        timeoutMillis: Long = CAPTURE_TIMEOUT_MILLIS,
        onFinished: (Int?) -> Unit
    ) {
        viewModelScope.launch {
            pageTurnDispatcher.setCaptureMode(true)
            try {
                val keyCode = withTimeoutOrNull(timeoutMillis) {
                    pageTurnDispatcher.capturedKeys.first()
                }
                if (keyCode != null) {
                    settingsRepository.setPageTurnKeyCode(direction, keyCode)
                }
                onFinished(keyCode)
            } finally {
                pageTurnDispatcher.setCaptureMode(false)
            }
        }
    }

    fun cancelCapture() {
        pageTurnDispatcher.setCaptureMode(false)
    }

    fun setHideArticleThumbnails(hide: Boolean) = launchSetting {
        settingsRepository.setHideArticleThumbnails(hide)
    }

    private fun launchSetting(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    companion object {
        const val CAPTURE_TIMEOUT_MILLIS = 10_000L
    }
}
