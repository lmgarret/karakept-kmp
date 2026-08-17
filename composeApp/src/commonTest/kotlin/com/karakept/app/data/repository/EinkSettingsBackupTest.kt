package com.karakept.app.data.repository

import com.karakept.app.data.model.PageTurnDirection
import com.karakept.app.data.model.ReaderTypography
import com.karakept.app.data.model.RowActionMode
import com.karakept.app.ui.input.PlatformKeyCodes
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The e-ink and reader-typography settings must survive a backup/restore cycle — otherwise
 * moving to a new device silently drops the whole configuration for that device class.
 */
class EinkSettingsBackupTest {

    private lateinit var repo: SettingsRepository

    @BeforeTest
    fun setup() {
        repo = SettingsRepository(FakeDataStore())
    }

    @Test
    fun `e-ink settings survive an export and import`() = runTest {
        repo.setEinkModeEnabled(true)
        repo.setEinkDisableAnimations(false)
        repo.setEinkHighContrast(false)
        repo.setEinkInstantPageScroll(false)
        repo.setPageTurnKeyCode(PageTurnDirection.PREVIOUS, 24)
        repo.setPageTurnKeyCode(PageTurnDirection.NEXT, 25)
        repo.setPageTurnOverlapPercent(20)

        val exported = repo.currentSettings()

        val restoredInto = SettingsRepository(FakeDataStore())
        restoredInto.restoreSettings(exported)

        assertTrue(restoredInto.einkModeEnabled.first())
        assertFalse(restoredInto.einkDisableAnimations.first())
        assertFalse(restoredInto.einkHighContrast.first())
        assertFalse(restoredInto.einkInstantPageScroll.first())

        val bindings = restoredInto.pageTurnKeyBindings.first()
        assertEquals(24, bindings.previousKeyCode)
        assertEquals(25, bindings.nextKeyCode)
        assertEquals(20, bindings.overlapPercent)
    }

    @Test
    fun `the monochrome icon setting survives an export and import`() = runTest {
        repo.setEinkMonochromeIcon(true)

        val restoredInto = SettingsRepository(FakeDataStore())
        restoredInto.restoreSettings(repo.currentSettings())

        assertTrue(restoredInto.einkMonochromeIcon.first())
    }

    @Test
    fun `the monochrome icon is not gated on the master switch`() = runTest {
        // The launcher keeps showing the icon long after e-ink mode is switched off, so the
        // colour artwork must not come back on its own — nor appear just because the master
        // switch went on.
        repo.setEinkMonochromeIcon(true)
        repo.setEinkModeEnabled(true)
        assertTrue(repo.einkMonochromeIcon.first())

        repo.setEinkModeEnabled(false)
        assertTrue(repo.einkMonochromeIcon.first())
    }

    @Test
    fun `enabling e-ink mode alone leaves the icon in colour`() = runTest {
        repo.setEinkModeEnabled(true)
        assertFalse(repo.einkMonochromeIcon.first())
    }

    @Test
    fun `the volume-button preset survives an export and import`() = runTest {
        repo.setPageTurnUseVolumeKeys(true)
        repo.setPageTurnInvertVolumeKeys(true)

        val restoredInto = SettingsRepository(FakeDataStore())
        restoredInto.restoreSettings(repo.currentSettings())

        val bindings = restoredInto.pageTurnKeyBindings.first()
        assertTrue(bindings.useVolumeKeys)
        assertTrue(bindings.invertVolumeKeys)
        assertEquals(PageTurnDirection.PREVIOUS, bindings.directionFor(PlatformKeyCodes.VOLUME_DOWN))
    }

    @Test
    fun `page snapping survives an export and import`() = runTest {
        repo.setPageTurnSnapToContent(false)

        val restoredInto = SettingsRepository(FakeDataStore())
        restoredInto.restoreSettings(repo.currentSettings())

        assertFalse(restoredInto.pageTurnSnapToContent.first())
        assertFalse(restoredInto.pageTurnKeyBindings.first().snapToContent)
    }

    @Test
    fun `snapping supplies the overlap, so the fixed percentage stands down`() = runTest {
        repo.setPageTurnOverlapPercent(20)

        // On by default: the stored percentage is kept but not applied.
        val snapping = repo.pageTurnKeyBindings.first()
        assertTrue(snapping.snapToContent)
        assertEquals(20, snapping.overlapPercent)
        assertEquals(0, snapping.effectiveOverlapPercent)

        repo.setPageTurnSnapToContent(false)
        assertEquals(20, repo.pageTurnKeyBindings.first().effectiveOverlapPercent)
    }

    @Test
    fun `page snapping works with the master e-ink switch off`() = runTest {
        // Same reasoning as instant page turns: the buttons are ungated, so the paging style
        // they use has to be too.
        assertFalse(repo.einkModeEnabled.first())
        assertTrue(repo.pageTurnKeyBindings.first().snapToContent)
    }

    @Test
    fun `page turns stay instant even with the master e-ink switch off`() = runTest {
        // Hardware buttons are not gated on e-ink mode, so the scroll style they use must not be
        // either — otherwise every turn animates for anyone who never flipped the master switch.
        assertFalse(repo.einkModeEnabled.first())
        assertTrue(repo.pageTurnKeyBindings.first().instantPageTurn)

        repo.setEinkInstantPageScroll(false)
        assertFalse(repo.pageTurnKeyBindings.first().instantPageTurn)
    }

    @Test
    fun `reader typography survives an export and import`() = runTest {
        repo.setReaderLineHeightScale(1.4f)
        repo.setReaderHorizontalMarginDp(12)
        repo.setReaderMaxWidthDp(560)

        val restoredInto = SettingsRepository(FakeDataStore())
        restoredInto.restoreSettings(repo.currentSettings())

        val typography = restoredInto.readerTypography.first()
        assertEquals(1.4f, typography.lineHeightScale)
        assertEquals(12, typography.horizontalMarginDp)
        assertEquals(560, typography.maxWidthDp)
    }

    @Test
    fun `unbound page-turn keys stay unbound after a restore`() = runTest {
        val restoredInto = SettingsRepository(FakeDataStore())
        restoredInto.restoreSettings(repo.currentSettings())

        val bindings = restoredInto.pageTurnKeyBindings.first()
        assertNull(bindings.previousKeyCode)
        assertNull(bindings.nextKeyCode)
        assertTrue(bindings.enabled)
    }

    @Test
    fun `binding a key to both directions leaves it bound only to the newer one`() = runTest {
        repo.setPageTurnKeyCode(PageTurnDirection.NEXT, 25)
        repo.setPageTurnKeyCode(PageTurnDirection.PREVIOUS, 25)

        val bindings = repo.pageTurnKeyBindings.first()
        assertEquals(25, bindings.previousKeyCode)
        assertNull(bindings.nextKeyCode)
        assertEquals(PageTurnDirection.PREVIOUS, bindings.directionFor(25))
    }

    @Test
    fun `out-of-range typography values are clamped rather than stored raw`() = runTest {
        repo.setReaderLineHeightScale(99f)
        repo.setReaderHorizontalMarginDp(-10)
        repo.setReaderMaxWidthDp(100_000)

        val typography = repo.readerTypography.first()
        assertEquals(ReaderTypography.MAX_LINE_HEIGHT_SCALE, typography.lineHeightScale)
        assertEquals(ReaderTypography.MIN_HORIZONTAL_MARGIN_DP, typography.horizontalMarginDp)
        assertEquals(ReaderTypography.MAX_MAX_WIDTH_DP, typography.maxWidthDp)
    }

    @Test
    fun `resetting reader appearance restores the default typography`() = runTest {
        repo.setReaderLineHeightScale(1.8f)
        repo.setReaderHorizontalMarginDp(60)
        repo.setReaderMaxWidthDp(1200)

        repo.resetReaderAppearance()

        assertEquals(ReaderTypography(), repo.readerTypography.first())
    }

    @Test
    fun `sub-toggles have no effect while the master switch is off`() = runTest {
        // Defaults have all three sub-toggles on; the master switch must still gate them.
        val settings = repo.einkDisplaySettings.first()
        assertFalse(settings.enabled)
        assertFalse(settings.animationsDisabled)
        assertFalse(settings.highContrast)
        assertFalse(settings.instantPageScroll)
    }

    @Test
    fun `enabling the master switch activates the sub-toggles`() = runTest {
        repo.setEinkModeEnabled(true)

        val settings = repo.einkDisplaySettings.first()
        assertTrue(settings.animationsDisabled)
        assertTrue(settings.highContrast)
        assertTrue(settings.instantPageScroll)
    }

    @Test
    fun `a sub-toggle turned off stays off when the master switch is on`() = runTest {
        repo.setEinkModeEnabled(true)
        repo.setEinkHighContrast(false)

        val settings = repo.einkDisplaySettings.first()
        assertTrue(settings.animationsDisabled)
        assertFalse(settings.highContrast)
    }

    @Test
    fun `hardware keys work independently of the master switch`() = runTest {
        // The buttons are useful on any device that has them, e-ink mode or not.
        repo.setPageTurnKeyCode(PageTurnDirection.NEXT, 25)

        assertFalse(repo.einkModeEnabled.first())
        assertTrue(repo.pageTurnKeyBindings.first().enabled)
        assertEquals(PageTurnDirection.NEXT, repo.pageTurnKeyBindings.first().directionFor(25))
    }

    @Test
    fun `the hero image toggle survives an export and import`() = runTest {
        repo.setShowReaderHeroImage(false)

        val restoredInto = SettingsRepository(FakeDataStore())
        restoredInto.restoreSettings(repo.currentSettings())

        assertFalse(restoredInto.showReaderHeroImage.first())
    }

    @Test
    fun `the hero image is shown unless the user turns it off`() = runTest {
        // Enabling E-ink mode must not hide it either — the toggle is independent.
        repo.setEinkModeEnabled(true)
        assertTrue(repo.showReaderHeroImage.first())
    }

    @Test
    fun `the row action mode survives an export and import`() = runTest {
        repo.setRowActionMode(RowActionMode.BUTTONS)

        val restoredInto = SettingsRepository(FakeDataStore())
        restoredInto.restoreSettings(repo.currentSettings())

        assertEquals(RowActionMode.BUTTONS, restoredInto.rowActionMode.first())
    }

    @Test
    fun `phones keep swiping unless the user asks for buttons`() = runTest {
        // E-ink mode does not switch it either; a swipe still works, it is just unpleasant.
        repo.setEinkModeEnabled(true)
        assertEquals(RowActionMode.SWIPE, repo.rowActionMode.first())
    }
}
