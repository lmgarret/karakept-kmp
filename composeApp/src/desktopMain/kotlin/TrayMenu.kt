/** How many of the newest bookmarks the tray's "Recent Bookmarks" submenu lists. */
internal const val TRAY_RECENT_BOOKMARK_COUNT = 8

/** Longest tray menu label before it is ellipsized — native menus neither wrap nor truncate. */
internal const val TRAY_MENU_LABEL_MAX_CHARS = 48

/** Collapse whitespace and cap a bookmark title to what a native menu row shows on one line. */
internal fun trayMenuLabel(text: String): String {
    val collapsed = text.replace(Regex("\\s+"), " ").trim()
    return if (collapsed.length <= TRAY_MENU_LABEL_MAX_CHARS) {
        collapsed
    } else {
        collapsed.take(TRAY_MENU_LABEL_MAX_CHARS - 1).trimEnd() + "…"
    }
}

/** Open a URL in the user's default browser, ignoring failures (headless, no XDG handler). */
internal fun openInBrowser(url: String) {
    try {
        java.awt.Desktop.getDesktop().browse(java.net.URI(url))
    } catch (_: Exception) {
        // Best effort
    }
}
