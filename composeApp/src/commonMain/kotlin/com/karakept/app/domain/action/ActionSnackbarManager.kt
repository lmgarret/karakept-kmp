package com.karakept.app.domain.action

import androidx.compose.material3.SnackbarDuration
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * Event representing a snackbar request
 */
sealed class SnackbarEvent {
    data class Message(
        val text: String,
        val duration: SnackbarDuration = SnackbarDuration.Short
    ) : SnackbarEvent()

    data class MessageWithUndo(
        val text: String,
        val onUndo: suspend () -> Unit,
        val duration: SnackbarDuration = SnackbarDuration.Short
    ) : SnackbarEvent()

    data class MessageWithAction(
        val text: String,
        val actionLabel: String,
        val onAction: suspend () -> Unit,
        val duration: SnackbarDuration = SnackbarDuration.Short
    ) : SnackbarEvent()
}

/**
 * Centralized manager for snackbar events across screens
 */
class ActionSnackbarManager {
    private val _snackbarEvents = MutableSharedFlow<SnackbarEvent>(extraBufferCapacity = 10)
    val snackbarEvents: SharedFlow<SnackbarEvent> = _snackbarEvents

    suspend fun showSnackbar(message: String, duration: SnackbarDuration = SnackbarDuration.Short) {
        _snackbarEvents.emit(SnackbarEvent.Message(message, duration))
    }

    suspend fun showSnackbarWithUndo(
        message: String,
        onUndo: suspend () -> Unit,
        duration: SnackbarDuration = SnackbarDuration.Short
    ) {
        _snackbarEvents.emit(SnackbarEvent.MessageWithUndo(message, onUndo, duration))
    }

    suspend fun showErrorWithRetry(
        message: String,
        onRetry: suspend () -> Unit,
        duration: SnackbarDuration = SnackbarDuration.Short
    ) {
        _snackbarEvents.emit(SnackbarEvent.MessageWithAction(message, "Retry", onRetry, duration))
    }
}
