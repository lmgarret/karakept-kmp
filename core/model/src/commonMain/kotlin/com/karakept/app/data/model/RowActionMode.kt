package com.karakept.app.data.model

/**
 * How the per-bookmark quick actions are triggered on a touch device.
 *
 * [SWIPE] is the phone default. [BUTTONS] shows the always-visible button cluster desktop uses on
 * hover — a swipe needs the panel to track a finger across many frames, which an e-ink display
 * renders as a smear if it keeps up at all, so a single tap is far more reliable there.
 *
 * Desktop ignores this and always uses buttons; there is nothing to swipe with.
 */
enum class RowActionMode {
    SWIPE, BUTTONS;

    companion object {
        fun fromString(value: String): RowActionMode =
            entries.find { it.name == value } ?: SWIPE
    }
}
