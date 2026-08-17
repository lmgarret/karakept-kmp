package com.karakept.app.data.model

import com.karakept.app.ui.input.PlatformKeyCodes

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
 *
 * [useVolumeKeys] is the exception: e-ink readers overwhelmingly wire their facade buttons to the
 * volume rocker, so that one pair is offered as a preset rather than something to learn.
 *
 * [instantPageTurn] mirrors the e-ink "Instant scrolling" preference *ungated* by the master e-ink
 * switch, because page-turn buttons themselves are ungated — a smoothly animated jump is the wrong
 * answer on any device with page buttons. [snapToContent] is ungated for the same reason.
 */
data class PageTurnKeyBindings(
    val enabled: Boolean = true,
    val useVolumeKeys: Boolean = false,
    val invertVolumeKeys: Boolean = false,
    val previousKeyCode: Int? = null,
    val nextKeyCode: Int? = null,
    val overlapPercent: Int = DEFAULT_OVERLAP_PERCENT,
    val instantPageTurn: Boolean = true,
    val snapToContent: Boolean = true
) {
    /**
     * Snapping produces its own overlap — exactly as much as it takes to keep the bookmark or the
     * line of text straddling the fold whole — so the fixed percentage would stack on top of it.
     */
    val effectiveOverlapPercent: Int
        get() = if (snapToContent) MIN_OVERLAP_PERCENT else overlapPercent

    fun directionFor(keyCode: Int): PageTurnDirection? {
        if (!enabled) return null
        volumeDirectionFor(keyCode)?.let { return it }
        return when {
            previousKeyCode != null && keyCode == previousKeyCode -> PageTurnDirection.PREVIOUS
            nextKeyCode != null && keyCode == nextKeyCode -> PageTurnDirection.NEXT
            else -> null
        }
    }

    // Checked before the learned codes so the preset still wins if a device also reports the
    // volume rocker under a code the user happened to bind earlier.
    private fun volumeDirectionFor(keyCode: Int): PageTurnDirection? {
        if (!useVolumeKeys) return null
        return when (keyCode) {
            PlatformKeyCodes.VOLUME_UP ->
                if (invertVolumeKeys) PageTurnDirection.NEXT else PageTurnDirection.PREVIOUS
            PlatformKeyCodes.VOLUME_DOWN ->
                if (invertVolumeKeys) PageTurnDirection.PREVIOUS else PageTurnDirection.NEXT
            else -> null
        }
    }

    companion object {
        const val DEFAULT_OVERLAP_PERCENT = 8
        const val MIN_OVERLAP_PERCENT = 0
        const val MAX_OVERLAP_PERCENT = 50
    }
}
