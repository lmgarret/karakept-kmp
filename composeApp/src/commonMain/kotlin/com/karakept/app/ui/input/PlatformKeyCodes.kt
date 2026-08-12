package com.karakept.app.ui.input

/**
 * Raw key codes for the volume rocker, in whatever numbering the platform hands to
 * [PageTurnDispatcher.onKeyDown].
 *
 * E-ink readers wire their facade page-turn buttons to the volume rocker, so binding these two
 * codes is the one mapping that works on most devices without learning anything. They cannot be
 * hardcoded in common code because Android reports `android.view.KeyEvent.KEYCODE_*` while desktop
 * reports a truncated `Key.keyCode`.
 */
expect object PlatformKeyCodes {
    val VOLUME_UP: Int
    val VOLUME_DOWN: Int
}
