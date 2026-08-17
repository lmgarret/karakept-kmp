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
import com.karakept.app.data.repository.setEinkMonochromeIcon
import com.karakept.app.data.repository.setHideArticleThumbnails
import com.karakept.app.data.repository.setPageTurnKeyCode
import com.karakept.app.data.repository.setPageTurnInvertVolumeKeys
import com.karakept.app.data.repository.setPageTurnKeysEnabled
import com.karakept.app.data.repository.setPageTurnOverlapPercent
import com.karakept.app.data.repository.setPageTurnSnapToContent
import com.karakept.app.data.repository.setPageTurnUseVolumeKeys
import com.karakept.app.data.repository.setRowActionMode
import com.karakept.app.ui.input.PageTurnDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onSubscription
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

    val monochromeIcon: StateFlow<Boolean> = settingsRepository.einkMonochromeIcon.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), false
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

    fun setMonochromeIcon(enabled: Boolean) = launchSetting {
        settingsRepository.setEinkMonochromeIcon(enabled)
    }

    fun setInstantPageScroll(enabled: Boolean) = launchSetting {
        settingsRepository.setEinkInstantPageScroll(enabled)
    }

    fun setHardwareKeysEnabled(enabled: Boolean) = launchSetting {
        settingsRepository.setPageTurnKeysEnabled(enabled)
    }

    fun setUseVolumeKeys(enabled: Boolean) = launchSetting {
        settingsRepository.setPageTurnUseVolumeKeys(enabled)
    }

    fun setInvertVolumeKeys(inverted: Boolean) = launchSetting {
        settingsRepository.setPageTurnInvertVolumeKeys(inverted)
    }

    fun setOverlapPercent(percent: Int) = launchSetting {
        settingsRepository.setPageTurnOverlapPercent(percent)
    }

    fun setSnapToContent(enabled: Boolean) = launchSetting {
        settingsRepository.setPageTurnSnapToContent(enabled)
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
        captureJob?.cancel()
        captureJob = viewModelScope.launch {
            try {
                val keyCode = withTimeoutOrNull(timeoutMillis) {
                    // capturedKeys has no replay, so capture mode must not open until this
                    // collector is registered — otherwise onKeyDown swallows the very first press
                    // and tryEmit drops it for want of a subscriber.
                    pageTurnDispatcher.capturedKeys
                        .onSubscription { pageTurnDispatcher.setCaptureMode(true) }
                        .first()
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
        // Cancelling the job matters as much as clearing the flag: otherwise the timeout keeps
        // running and reports back to a prompt the user already dismissed.
        captureJob?.cancel()
        captureJob = null
        pageTurnDispatcher.setCaptureMode(false)
    }

    fun setHideArticleThumbnails(hide: Boolean) = launchSetting {
        settingsRepository.setHideArticleThumbnails(hide)
    }

    private var captureJob: Job? = null

    private fun launchSetting(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    companion object {
        const val CAPTURE_TIMEOUT_MILLIS = 10_000L
    }
}
