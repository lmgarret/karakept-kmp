package com.karakept.app.ui.screens.settings

/**
 * The groups the E-ink settings screen is split into, in the order they are rendered.
 *
 * The screen used to be a single "Display" block hanging off Appearance, which described only a
 * couple of its switches: instant scrolling, row action buttons and the hardware page-turn
 * bindings change how the app *behaves*, not how it looks. E-ink is its own settings section now,
 * and these groups keep that look/behaviour split visible inside it.
 */
enum class EinkSettingGroup(val title: String, val description: String?) {
    DISPLAY("Display", "How the panel is painted"),
    MOTION("Motion", "Every animated frame is a full-panel refresh, and ghosts"),
    INTERACTION("Interaction", "Gestures a panel smears or drops, and what replaces them"),
    PAGE_TURN_BUTTONS(
        "Page-turn buttons",
        "Bind the device's hardware buttons to turn pages in the reader and the bookmark list. " +
            "Bound buttons stop doing whatever they normally do while the app is open."
    )
}

/**
 * One switch (or slider, or binding row) on the E-ink screen.
 *
 * The master "E-ink mode" toggle is deliberately absent: it gates most of these rather than
 * sitting among them, and is rendered above every group.
 */
enum class EinkSetting(val group: EinkSettingGroup) {
    HIGH_CONTRAST(EinkSettingGroup.DISPLAY),
    HIDE_ARTICLE_THUMBNAILS(EinkSettingGroup.DISPLAY),
    MONOCHROME_ICON(EinkSettingGroup.DISPLAY),
    DISABLE_ANIMATIONS(EinkSettingGroup.MOTION),
    INSTANT_SCROLLING(EinkSettingGroup.MOTION),
    ROW_ACTION_BUTTONS(EinkSettingGroup.INTERACTION),
    HARDWARE_KEYS_ENABLED(EinkSettingGroup.PAGE_TURN_BUTTONS),
    USE_VOLUME_KEYS(EinkSettingGroup.PAGE_TURN_BUTTONS),
    INVERT_VOLUME_KEYS(EinkSettingGroup.PAGE_TURN_BUTTONS),
    BIND_PREVIOUS(EinkSettingGroup.PAGE_TURN_BUTTONS),
    BIND_NEXT(EinkSettingGroup.PAGE_TURN_BUTTONS),
    SNAP_TO_CONTENT(EinkSettingGroup.PAGE_TURN_BUTTONS),
    PAGE_OVERLAP(EinkSettingGroup.PAGE_TURN_BUTTONS)
}

/** Everything the visibility rules below depend on. */
data class EinkSettingsState(
    val einkModeEnabled: Boolean,
    val monochromeIconSupported: Boolean,
    val hardwareKeysEnabled: Boolean,
    val useVolumeKeys: Boolean
)

/**
 * Which settings the screen shows for [state], in render order.
 *
 * Two of these survive the master switch being off, and both on purpose: the monochrome launcher
 * icon outlives e-ink mode on the home screen, and the page-turn buttons are usable on any device
 * with hardware keys.
 */
fun visibleEinkSettings(state: EinkSettingsState): List<EinkSetting> =
    EinkSetting.entries.filter { setting ->
        when (setting) {
            EinkSetting.MONOCHROME_ICON -> state.monochromeIconSupported
            EinkSetting.HIGH_CONTRAST,
            EinkSetting.HIDE_ARTICLE_THUMBNAILS,
            EinkSetting.DISABLE_ANIMATIONS,
            EinkSetting.INSTANT_SCROLLING,
            EinkSetting.ROW_ACTION_BUTTONS -> state.einkModeEnabled
            EinkSetting.HARDWARE_KEYS_ENABLED -> true
            EinkSetting.INVERT_VOLUME_KEYS -> state.hardwareKeysEnabled && state.useVolumeKeys
            EinkSetting.USE_VOLUME_KEYS,
            EinkSetting.BIND_PREVIOUS,
            EinkSetting.BIND_NEXT,
            EinkSetting.SNAP_TO_CONTENT,
            EinkSetting.PAGE_OVERLAP -> state.hardwareKeysEnabled
        }
    }

/**
 * The visible settings bucketed into their groups, in render order, with empty groups dropped —
 * a group whose every setting is gated away must not leave its heading behind.
 */
fun visibleEinkGroups(state: EinkSettingsState): List<Pair<EinkSettingGroup, List<EinkSetting>>> {
    val visible = visibleEinkSettings(state).groupBy { it.group }
    return EinkSettingGroup.entries.mapNotNull { group ->
        visible[group]?.takeIf { it.isNotEmpty() }?.let { group to it }
    }
}
