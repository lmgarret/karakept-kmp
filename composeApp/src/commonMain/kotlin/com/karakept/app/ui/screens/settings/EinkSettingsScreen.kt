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
import com.karakept.app.ui.icons.AppIcons
import com.karakept.app.ui.input.PlatformKeyCodes
import com.karakept.app.data.model.RowActionMode
import com.karakept.app.ui.navigation.LocalNavigator
import com.karakept.app.ui.navigation.currentOrThrow
import com.karakept.app.utils.AppIconManager
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
    val monochromeIcon by screenModel.monochromeIcon.collectAsState()
    val keyBindings by screenModel.keyBindings.collectAsState()
    val hideArticleThumbnails by screenModel.hideArticleThumbnails.collectAsState()
    val rowActionMode by screenModel.rowActionMode.collectAsState()

    var capturingFor by remember { mutableStateOf<PageTurnDirection?>(null) }
    var captureTimedOut by remember { mutableStateOf<PageTurnDirection?>(null) }

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

    val groups = visibleEinkGroups(
        EinkSettingsState(
            einkModeEnabled = einkModeEnabled,
            monochromeIconSupported = AppIconManager.isSupported,
            hardwareKeysEnabled = keyBindings.enabled,
            useVolumeKeys = keyBindings.useVolumeKeys
        )
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("E-ink") },
                navigationIcon = {
                    if (showBackButton) {
                        IconButton(onClick = onBack) {
                            Icon(AppIcons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
            SettingSwitchCard(
                title = "E-ink mode",
                description = "Tune the app for an electronic-paper display. Leaves the page-turn " +
                    "buttons and the monochrome icon below alone — those work on any device.",
                icon = AppIcons.Default.Tonality,
                checked = einkModeEnabled,
                onCheckedChange = { screenModel.setEinkModeEnabled(it) }
            )

            groups.forEach { (group, settings) ->
                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = group.title,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                group.description?.let { description ->
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                }

                settings.forEach { setting ->
                    when (setting) {
                        EinkSetting.HIGH_CONTRAST -> SettingSwitchCard(
                            title = "High contrast",
                            description = "Pure black on white, with outlines instead of shaded surfaces",
                            icon = AppIcons.Default.Contrast,
                            checked = highContrast,
                            onCheckedChange = { screenModel.setHighContrast(it) }
                        )

                        EinkSetting.HIDE_ARTICLE_THUMBNAILS -> SettingSwitchCard(
                            title = "Hide article thumbnails",
                            description = "Photos dither poorly on e-ink. Also available under Reader settings",
                            icon = AppIcons.Default.ImageNotSupported,
                            checked = hideArticleThumbnails,
                            onCheckedChange = { screenModel.setHideArticleThumbnails(it) }
                        )

                        // Ungated on purpose: the home screen goes on showing the icon after E-ink
                        // mode is switched off, so the colour artwork must not come back on its own.
                        EinkSetting.MONOCHROME_ICON -> SettingSwitchCard(
                            title = "Monochrome icon",
                            description = "Black-on-white launcher icon and splash screen. Stays " +
                                "put when E-ink mode is off; the splash follows from the next " +
                                "cold start.",
                            icon = AppIcons.Default.InvertColors,
                            checked = monochromeIcon,
                            onCheckedChange = { screenModel.setMonochromeIcon(it) }
                        )

                        EinkSetting.DISABLE_ANIMATIONS -> SettingSwitchCard(
                            title = "Disable animations",
                            description = "Screen transitions, fades and spinners refresh the whole panel and leave ghosting",
                            icon = AppIcons.Default.Animation,
                            checked = disableAnimations,
                            onCheckedChange = { screenModel.setDisableAnimations(it) }
                        )

                        EinkSetting.INSTANT_SCROLLING -> SettingSwitchCard(
                            title = "Instant scrolling",
                            description = "Page turns, scroll-to-top and in-article jumps land in " +
                                "one repaint instead of gliding. Page turns follow this even with " +
                                "E-ink mode off.",
                            icon = AppIcons.Default.SwipeVertical,
                            checked = instantPageScroll,
                            onCheckedChange = { screenModel.setInstantPageScroll(it) }
                        )

                        EinkSetting.ROW_ACTION_BUTTONS -> SettingSwitchCard(
                            title = "Action buttons instead of swipe",
                            description = "A swipe must be tracked across many frames; e-ink panels smear or drop it",
                            icon = AppIcons.Default.TouchApp,
                            checked = rowActionMode == RowActionMode.BUTTONS,
                            onCheckedChange = {
                                screenModel.setRowActionMode(
                                    if (it) RowActionMode.BUTTONS else RowActionMode.SWIPE
                                )
                            }
                        )

                        EinkSetting.HARDWARE_KEYS_ENABLED -> SettingSwitchCard(
                            title = "Enable hardware buttons",
                            description = "Turn pages with the device's physical buttons",
                            icon = AppIcons.Default.Keyboard,
                            checked = keyBindings.enabled,
                            onCheckedChange = { screenModel.setHardwareKeysEnabled(it) }
                        )

                        EinkSetting.USE_VOLUME_KEYS -> SettingSwitchCard(
                            title = "Use volume buttons",
                            description = "Most e-ink readers wire their page buttons to the " +
                                "volume rocker. Volume up turns back, volume down turns forward, " +
                                "and neither changes the volume while the app is open.",
                            icon = AppIcons.AutoMirrored.Filled.VolumeUp,
                            checked = keyBindings.useVolumeKeys,
                            onCheckedChange = { screenModel.setUseVolumeKeys(it) }
                        )

                        EinkSetting.INVERT_VOLUME_KEYS -> SettingSwitchCard(
                            title = "Invert volume buttons",
                            description = "Swap the two, for holding the device the other way up",
                            icon = AppIcons.Default.SwapVert,
                            checked = keyBindings.invertVolumeKeys,
                            onCheckedChange = { screenModel.setInvertVolumeKeys(it) }
                        )

                        EinkSetting.BIND_PREVIOUS -> KeyBindingCard(
                            title = "Previous page",
                            keyCode = keyBindings.previousKeyCode,
                            capturing = capturingFor == PageTurnDirection.PREVIOUS,
                            timedOut = captureTimedOut == PageTurnDirection.PREVIOUS,
                            onBind = { startCapture(PageTurnDirection.PREVIOUS) },
                            onCancel = cancelCapture,
                            onClear = { screenModel.clearBinding(PageTurnDirection.PREVIOUS) }
                        )

                        EinkSetting.BIND_NEXT -> KeyBindingCard(
                            title = "Next page",
                            keyCode = keyBindings.nextKeyCode,
                            capturing = capturingFor == PageTurnDirection.NEXT,
                            timedOut = captureTimedOut == PageTurnDirection.NEXT,
                            onBind = { startCapture(PageTurnDirection.NEXT) },
                            onCancel = cancelCapture,
                            onClear = { screenModel.clearBinding(PageTurnDirection.NEXT) }
                        )

                        EinkSetting.SNAP_TO_CONTENT -> SettingSwitchCard(
                            title = "Snap pages to content",
                            description = "Start each page of an article on a whole line of text, " +
                                "instead of slicing through the one that straddles the edge. With " +
                                "E-ink mode on, pages also end on a whole line and the last page " +
                                "carries on from the previous instead of repeating it.",
                            icon = AppIcons.Default.VerticalAlignTop,
                            checked = keyBindings.snapToContent,
                            onCheckedChange = { screenModel.setSnapToContent(it) }
                        )

                        EinkSetting.PAGE_OVERLAP -> {
                            // Snapping produces its own overlap — as much as it takes to keep the
                            // straddling element whole — so a fixed percentage would stack on it.
                            val overlapEnabled = !keyBindings.snapToContent
                            val disabledAlpha = 0.38f
                            Card(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = "Page overlap: ${keyBindings.overlapPercent}%",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface.copy(
                                            alpha = if (overlapEnabled) 1f else disabledAlpha
                                        )
                                    )
                                    Text(
                                        text = if (overlapEnabled)
                                            "How much of the current page stays on screen after a turn"
                                        else
                                            "Snapping already keeps whatever straddles the edge on " +
                                                "screen, so a fixed overlap would stack on top of it",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                            alpha = if (overlapEnabled) 1f else disabledAlpha
                                        )
                                    )
                                    Slider(
                                        value = keyBindings.overlapPercent.toFloat(),
                                        onValueChange = { screenModel.setOverlapPercent(it.toInt()) },
                                        valueRange = PageTurnKeyBindings.MIN_OVERLAP_PERCENT.toFloat()..
                                            PageTurnKeyBindings.MAX_OVERLAP_PERCENT.toFloat(),
                                        steps = PageTurnKeyBindings.MAX_OVERLAP_PERCENT - 1,
                                        enabled = overlapEnabled
                                    )
                                }
                            }
                        }
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
