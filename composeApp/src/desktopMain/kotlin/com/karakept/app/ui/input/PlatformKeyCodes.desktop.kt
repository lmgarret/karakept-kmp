package com.karakept.app.ui.input

/**
 * Desktop never routes the machine's volume keys to an application window, so these are sentinels
 * that no real key code can collide with. The volume binding is a no-op here; desktop turns pages
 * with PageUp/PageDown instead (see `handleDesktopPageKey`).
 */
actual object PlatformKeyCodes {
    actual val VOLUME_UP: Int = Int.MIN_VALUE
    actual val VOLUME_DOWN: Int = Int.MIN_VALUE + 1
}
