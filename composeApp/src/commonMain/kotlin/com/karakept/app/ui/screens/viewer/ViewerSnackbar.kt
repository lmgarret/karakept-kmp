package com.karakept.app.ui.screens.viewer

import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.karakept.app.domain.action.ActionSnackbarManager
import com.karakept.app.domain.action.SnackbarEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Snackbar host state that delays display while the FAB menu is expanded,
 * and dismisses any visible snackbar when the FAB opens.
 */
@Composable
fun rememberSnackbarHostStateWithDelay(
    snackbarManager: ActionSnackbarManager,
    fabExpanded: Boolean
): SnackbarHostState {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var pendingEvent by remember { mutableStateOf<SnackbarEvent?>(null) }

    // Collect events from manager
    LaunchedEffect(Unit) {
        snackbarManager.snackbarEvents.collect { event ->
            if (fabExpanded) {
                // Store for later display
                pendingEvent = event
            } else {
                // Show immediately
                showSnackbarEvent(snackbarHostState, event, scope)
            }
        }
    }

    // Show pending event when FAB closes
    LaunchedEffect(fabExpanded, pendingEvent) {
        val event = pendingEvent
        if (!fabExpanded && event != null) {
            delay(400) // Wait for FAB animation
            showSnackbarEvent(snackbarHostState, event, scope)
            pendingEvent = null
        }
    }

    // Dismiss snackbar when FAB opens
    LaunchedEffect(fabExpanded) {
        if (fabExpanded) {
            snackbarHostState.currentSnackbarData?.dismiss()
        }
    }

    return snackbarHostState
}

/**
 * Dispatch a [SnackbarEvent] to the given [SnackbarHostState], handling
 * Message, MessageWithUndo, and MessageWithAction variants.
 */
fun showSnackbarEvent(
    snackbarHostState: SnackbarHostState,
    event: SnackbarEvent,
    scope: CoroutineScope
) {
    scope.launch {
        when (event) {
            is SnackbarEvent.Message -> {
                snackbarHostState.showSnackbar(
                    message = event.text,
                    duration = event.duration
                )
            }
            is SnackbarEvent.MessageWithUndo -> {
                val result = snackbarHostState.showSnackbar(
                    message = event.text,
                    actionLabel = "Undo",
                    duration = event.duration
                )
                if (result == SnackbarResult.ActionPerformed) {
                    event.onUndo()
                }
            }
            is SnackbarEvent.MessageWithAction -> {
                val result = snackbarHostState.showSnackbar(
                    message = event.text,
                    actionLabel = event.actionLabel,
                    duration = event.duration
                )
                if (result == SnackbarResult.ActionPerformed) {
                    event.onAction()
                }
            }
        }
    }
}
