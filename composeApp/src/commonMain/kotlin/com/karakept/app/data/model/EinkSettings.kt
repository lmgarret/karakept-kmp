package com.karakept.app.data.model

/**
 * Which way a hardware page-turn button moves the content.
 */
enum class PageTurnDirection {
    PREVIOUS,
    NEXT
}

/**
 * Runtime snapshot of the e-ink display preferences.
 *
 * The three sub-toggles are only *effective* when [enabled] is true — [animationsDisabled] and
 * [highContrast] already fold that in, so consumers read them directly.
 */
data class EinkDisplaySettings(
    val enabled: Boolean = false,
    val animationsDisabled: Boolean = false,
    val highContrast: Boolean = false,
    val instantPageScroll: Boolean = false
)

/**
 * Hardware page-turn button configuration.
 *
 * Key codes are platform raw values (Android `KeyEvent.KEYCODE_*`, desktop
 * `Key.keyCode` truncated to Int) captured from the device itself rather than hardcoded, because
 * e-ink readers disagree on which codes their facade buttons emit.
 */
data class PageTurnKeyBindings(
    val enabled: Boolean = true,
    val previousKeyCode: Int? = null,
    val nextKeyCode: Int? = null,
    val overlapPercent: Int = DEFAULT_OVERLAP_PERCENT
) {
    fun directionFor(keyCode: Int): PageTurnDirection? = when {
        !enabled -> null
        previousKeyCode != null && keyCode == previousKeyCode -> PageTurnDirection.PREVIOUS
        nextKeyCode != null && keyCode == nextKeyCode -> PageTurnDirection.NEXT
        else -> null
    }

    companion object {
        const val DEFAULT_OVERLAP_PERCENT = 8
        const val MIN_OVERLAP_PERCENT = 0
        const val MAX_OVERLAP_PERCENT = 50
    }
}
