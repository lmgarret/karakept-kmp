package com.karakept.app.utils

/**
 * Swaps the launcher icon — and the splash screen that pairs with it — between the colour
 * artwork and a black-on-white variant, for readers whose home screen is an e-ink panel.
 */
expect object AppIconManager {
    /**
     * False on platforms whose icon is baked into the packaged application and cannot be
     * changed while it runs. Callers must hide the setting entirely rather than offer a
     * switch that does nothing.
     */
    val isSupported: Boolean

    /** No-op where [isSupported] is false. */
    fun setMonochrome(enabled: Boolean)
}
