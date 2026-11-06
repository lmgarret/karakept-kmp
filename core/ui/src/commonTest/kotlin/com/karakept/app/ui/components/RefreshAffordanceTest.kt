package com.karakept.app.ui.components

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for the pull-to-refresh / refresh-button split that `RefreshableBox` and the screen top
 * bars share.
 *
 * The property that matters is that refresh is never unreachable: whenever the gesture is taken
 * away — on desktop, where there is no finger, or in e-ink mode, where the pull indicator smears
 * — the button must be there instead.
 */
class RefreshAffordanceTest {

    @Test
    fun `pull to refresh is live on a touch surface off e-ink`() {
        assertTrue(shouldUsePullToRefresh(gestureCapable = true, einkMode = false))
    }

    @Test
    fun `e-ink mode turns the pull gesture off`() {
        assertFalse(shouldUsePullToRefresh(gestureCapable = true, einkMode = true))
    }

    @Test
    fun `a surface with no gesture never gets pull to refresh`() {
        assertFalse(shouldUsePullToRefresh(gestureCapable = false, einkMode = false))
        assertFalse(shouldUsePullToRefresh(gestureCapable = false, einkMode = true))
    }

    @Test
    fun `touch off e-ink relies on the gesture alone`() {
        assertFalse(shouldShowRefreshButton(isDesktop = false, einkMode = false))
    }

    @Test
    fun `e-ink shows the refresh button on both platforms`() {
        assertTrue(shouldShowRefreshButton(isDesktop = false, einkMode = true))
        assertTrue(shouldShowRefreshButton(isDesktop = true, einkMode = true))
    }

    @Test
    fun `desktop shows the refresh button off e-ink too`() {
        assertTrue(shouldShowRefreshButton(isDesktop = true, einkMode = false))
    }

    @Test
    fun `exactly one of the gesture and the button is available`() {
        for (isDesktop in listOf(false, true)) {
            for (einkMode in listOf(false, true)) {
                val gesture = shouldUsePullToRefresh(gestureCapable = !isDesktop, einkMode = einkMode)
                val button = shouldShowRefreshButton(isDesktop = isDesktop, einkMode = einkMode)
                assertTrue(
                    gesture != button,
                    "isDesktop=$isDesktop einkMode=$einkMode left refresh with " +
                        "gesture=$gesture button=$button"
                )
            }
        }
    }
}
