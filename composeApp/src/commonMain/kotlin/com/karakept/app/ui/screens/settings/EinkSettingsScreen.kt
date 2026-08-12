package com.karakept.app.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.ImageNotSupported
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.SwipeVertical
import androidx.compose.material.icons.filled.Tonality
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import com.karakept.app.data.model.PageTurnDirection
import com.karakept.app.data.model.PageTurnKeyBindings
import com.karakept.app.ui.input.PlatformKeyCodes
import com.karakept.app.data.model.RowActionMode
import com.karakept.app.ui.navigation.LocalNavigator
import com.karakept.app.ui.navigation.currentOrThrow
import kotlinx.serialization.Serializable
import org.koin.compose.viewmodel.koinViewModel

@Serializable
class EinkSettingsScreen : NavKey {
    @Composable
    fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        EinkSettingsContent(
            screenModel = koinViewModel(),
            onBack = { navigator.pop() }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EinkSettingsContent(
    screenModel: EinkSettingsScreenModel,
    onBack: () -> Unit,
    showBackButton: Boolean = true
) {
    val einkModeEnabled by screenModel.einkModeEnabled.collectAsState()
    val disableAnimations by screenModel.disableAnimations.collectAsState()
    val highContrast by screenModel.highContrast.collectAsState()
    val instantPageScroll by screenModel.instantPageScroll.collectAsState()
    val keyBindings by screenModel.keyBindings.collectAsState()
    val hideArticleThumbnails by screenModel.hideArticleThumbnails.collectAsState()
    val rowActionMode by screenModel.rowActionMode.collectAsState()

    var capturingFor by remember { mutableStateOf<PageTurnDirection?>(null) }
    var captureTimedOut by remember { mutableStateOf<PageTurnDirection?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("E-ink") },
                navigationIcon = {
                    if (showBackButton) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Top
        ) {
            Text(
                text = "Display",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            SettingSwitchCard(
                title = "E-ink mode",
                description = "Tune the app for an electronic-paper display",
                icon = Icons.Default.Tonality,
                checked = einkModeEnabled,
                onCheckedChange = { screenModel.setEinkModeEnabled(it) }
            )

            if (einkModeEnabled) {
                SettingSwitchCard(
                    title = "Disable animations",
                    description = "Screen transitions, fades and spinners refresh the whole panel and leave ghosting",
                    icon = Icons.Default.Animation,
                    checked = disableAnimations,
                    onCheckedChange = { screenModel.setDisableAnimations(it) }
                )

                SettingSwitchCard(
                    title = "High contrast",
                    description = "Pure black on white, with outlines instead of shaded surfaces",
                    icon = Icons.Default.Contrast,
                    checked = highContrast,
                    onCheckedChange = { screenModel.setHighContrast(it) }
                )

                SettingSwitchCard(
                    title = "Instant scrolling",
                    description = "Page turns, scroll-to-top and in-article jumps land in one " +
                        "repaint instead of gliding. Page turns follow this even with E-ink " +
                        "mode off.",
                    icon = Icons.Default.SwipeVertical,
                    checked = instantPageScroll,
                    onCheckedChange = { screenModel.setInstantPageScroll(it) }
                )

                SettingSwitchCard(
                    title = "Action buttons instead of swipe",
                    description = "A swipe must be tracked across many frames; e-ink panels smear or drop it",
                    icon = Icons.Default.TouchApp,
                    checked = rowActionMode == RowActionMode.BUTTONS,
                    onCheckedChange = {
                        screenModel.setRowActionMode(if (it) RowActionMode.BUTTONS else RowActionMode.SWIPE)
                    }
                )

                SettingSwitchCard(
                    title = "Hide article thumbnails",
                    description = "Photos dither poorly on e-ink. Also available under Reader settings",
                    icon = Icons.Default.ImageNotSupported,
                    checked = hideArticleThumbnails,
                    onCheckedChange = { screenModel.setHideArticleThumbnails(it) }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Page-turn buttons",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Text(
                text = "Bind the device's hardware buttons to turn pages in the reader and the " +
                    "bookmark list. Bound buttons stop doing whatever they normally do while the " +
                    "app is open.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            SettingSwitchCard(
                title = "Enable hardware buttons",
                description = "Turn pages with the device's physical buttons",
                icon = Icons.Default.Keyboard,
                checked = keyBindings.enabled,
                onCheckedChange = { screenModel.setHardwareKeysEnabled(it) }
            )

            if (keyBindings.enabled) {
                SettingSwitchCard(
                    title = "Use volume buttons",
                    description = "Most e-ink readers wire their page buttons to the volume " +
                        "rocker. Volume up turns back, volume down turns forward, and neither " +
                        "changes the volume while the app is open.",
                    icon = Icons.AutoMirrored.Filled.VolumeUp,
                    checked = keyBindings.useVolumeKeys,
                    onCheckedChange = { screenModel.setUseVolumeKeys(it) }
                )

                if (keyBindings.useVolumeKeys) {
                    SettingSwitchCard(
                        title = "Invert volume buttons",
                        description = "Swap the two, for holding the device the other way up",
                        icon = Icons.Default.SwapVert,
                        checked = keyBindings.invertVolumeKeys,
                        onCheckedChange = { screenModel.setInvertVolumeKeys(it) }
                    )
                }

                val startCapture: (PageTurnDirection) -> Unit = { direction ->
                    captureTimedOut = null
                    capturingFor = direction
                    screenModel.captureKeyBinding(direction) { keyCode ->
                        if (keyCode == null) captureTimedOut = direction
                        capturingFor = null
                    }
                }
                val cancelCapture: () -> Unit = {
                    screenModel.cancelCapture()
                    capturingFor = null
                    captureTimedOut = null
                }

                KeyBindingCard(
                    title = "Previous page",
                    keyCode = keyBindings.previousKeyCode,
                    capturing = capturingFor == PageTurnDirection.PREVIOUS,
                    timedOut = captureTimedOut == PageTurnDirection.PREVIOUS,
                    onBind = { startCapture(PageTurnDirection.PREVIOUS) },
                    onCancel = cancelCapture,
                    onClear = { screenModel.clearBinding(PageTurnDirection.PREVIOUS) }
                )

                KeyBindingCard(
                    title = "Next page",
                    keyCode = keyBindings.nextKeyCode,
                    capturing = capturingFor == PageTurnDirection.NEXT,
                    timedOut = captureTimedOut == PageTurnDirection.NEXT,
                    onBind = { startCapture(PageTurnDirection.NEXT) },
                    onCancel = cancelCapture,
                    onClear = { screenModel.clearBinding(PageTurnDirection.NEXT) }
                )

                Card(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Page overlap: ${keyBindings.overlapPercent}%",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "How much of the current page stays on screen after a turn",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Slider(
                            value = keyBindings.overlapPercent.toFloat(),
                            onValueChange = { screenModel.setOverlapPercent(it.toInt()) },
                            valueRange = PageTurnKeyBindings.MIN_OVERLAP_PERCENT.toFloat()..
                                PageTurnKeyBindings.MAX_OVERLAP_PERCENT.toFloat(),
                            steps = PageTurnKeyBindings.MAX_OVERLAP_PERCENT - 1
                        )
                    }
                }
            }
        }
    }
}

/**
 * A single direction's binding, which doubles as the capture prompt.
 *
 * Capture is rendered inline rather than in an `AlertDialog` on purpose. A Compose dialog is a
 * separate platform window on Android, and while it holds focus key events go to *its*
 * `Window.Callback` instead of `MainActivity.dispatchKeyEvent` — the only thing that feeds
 * `PageTurnDispatcher`. In a dialog the prompt could never see the very keys it asks for, and a
 * volume press would fall through to the system volume overlay.
 */
@Composable
private fun KeyBindingCard(
    title: String,
    keyCode: Int?,
    capturing: Boolean,
    timedOut: Boolean,
    onBind: () -> Unit,
    onCancel: () -> Unit,
    onClear: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .clickable(enabled = !capturing, onClick = onBind)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = when {
                        capturing -> "Press the button you want to use…"
                        timedOut -> "No button detected. Some devices handle their page buttons " +
                            "in firmware and never pass them to apps."
                        keyCode != null -> keyLabel(keyCode)
                        else -> "Not set — tap to bind"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            when {
                capturing -> TextButton(onClick = onCancel) { Text("Cancel") }
                keyCode != null -> TextButton(onClick = onClear) { Text("Clear") }
            }
        }
    }
}

/** Raw key codes mean nothing to a reader, so name the ones we can recognise. */
private fun keyLabel(keyCode: Int): String = when (keyCode) {
    PlatformKeyCodes.VOLUME_UP -> "Volume up (key code $keyCode)"
    PlatformKeyCodes.VOLUME_DOWN -> "Volume down (key code $keyCode)"
    else -> "Key code $keyCode"
}

@Composable
private fun SettingSwitchCard(
    title: String,
    description: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.padding(end = 16.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}
