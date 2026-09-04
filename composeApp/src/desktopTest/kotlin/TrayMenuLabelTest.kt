import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The tray's "Recent Bookmarks" submenu feeds bookmark titles straight into native menu rows,
 * which neither wrap nor truncate — a long or multi-line title would stretch the menu across
 * the screen, so [trayMenuLabel] has to do it.
 */
class TrayMenuLabelTest {

    @Test
    fun shortLabel_isUnchanged() {
        assertEquals("Karakeep docs", trayMenuLabel("Karakeep docs"))
    }

    @Test
    fun whitespace_isCollapsedAndTrimmed() {
        assertEquals("A very spaced title", trayMenuLabel("  A   very\n spaced\ttitle  "))
    }

    @Test
    fun longLabel_isEllipsizedToTheCap() {
        val label = trayMenuLabel("x".repeat(TRAY_MENU_LABEL_MAX_CHARS * 2))

        assertEquals(TRAY_MENU_LABEL_MAX_CHARS, label.length)
        assertTrue(label.endsWith("…"))
    }

    @Test
    fun labelExactlyAtTheCap_isNotEllipsized() {
        val exact = "x".repeat(TRAY_MENU_LABEL_MAX_CHARS)

        assertEquals(exact, trayMenuLabel(exact))
    }

    @Test
    fun ellipsisDoesNotFollowASpace() {
        val label = trayMenuLabel("y".repeat(TRAY_MENU_LABEL_MAX_CHARS - 1) + " tail")

        assertEquals("y".repeat(TRAY_MENU_LABEL_MAX_CHARS - 1) + "…", label)
    }

    @Test
    fun blankTitle_collapsesToEmpty() {
        assertEquals("", trayMenuLabel("   \n  "))
    }
}
