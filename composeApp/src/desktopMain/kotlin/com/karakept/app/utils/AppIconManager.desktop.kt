package com.karakept.app.utils

/**
 * Desktop packages ship a fixed icon in the .desktop entry / .icns / .ico, all read by the
 * shell before the JVM starts. There is nothing to swap at runtime.
 */
actual object AppIconManager {
    actual val isSupported: Boolean = false

    actual fun setMonochrome(enabled: Boolean) = Unit
}
