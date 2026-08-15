package com.karakept.app.ui.screens.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for how the E-ink screen groups and gates its settings.
 *
 * The screen renders straight off [visibleEinkGroups], so what is asserted here is what the user
 * sees: which switches appear for a given state, in which group, and — the part that used to be
 * hand-rolled `if` blocks — that a group whose settings are all gated away takes its heading with
 * it.
 */
class EinkSettingsSectionsTest {

    private fun state(
        einkModeEnabled: Boolean = false,
        monochromeIconSupported: Boolean = true,
        hardwareKeysEnabled: Boolean = false,
        useVolumeKeys: Boolean = false
    ) = EinkSettingsState(
        einkModeEnabled = einkModeEnabled,
        monochromeIconSupported = monochromeIconSupported,
        hardwareKeysEnabled = hardwareKeysEnabled,
        useVolumeKeys = useVolumeKeys
    )

    @Test
    fun `every setting belongs to a group`() {
        val grouped = EinkSetting.entries.groupBy { it.group }
        assertEquals(EinkSettingGroup.entries.toSet(), grouped.keys)
    }

    @Test
    fun `e-ink mode off hides the display and motion switches it gates`() {
        val visible = visibleEinkSettings(state(einkModeEnabled = false))

        assertFalse(EinkSetting.HIGH_CONTRAST in visible)
        assertFalse(EinkSetting.HIDE_ARTICLE_THUMBNAILS in visible)
        assertFalse(EinkSetting.DISABLE_ANIMATIONS in visible)
        assertFalse(EinkSetting.INSTANT_SCROLLING in visible)
        assertFalse(EinkSetting.ROW_ACTION_BUTTONS in visible)
    }

    @Test
    fun `e-ink mode on shows every gated switch`() {
        val visible = visibleEinkSettings(state(einkModeEnabled = true))

        assertTrue(EinkSetting.HIGH_CONTRAST in visible)
        assertTrue(EinkSetting.HIDE_ARTICLE_THUMBNAILS in visible)
        assertTrue(EinkSetting.DISABLE_ANIMATIONS in visible)
        assertTrue(EinkSetting.INSTANT_SCROLLING in visible)
        assertTrue(EinkSetting.ROW_ACTION_BUTTONS in visible)
    }

    @Test
    fun `the monochrome icon and the page-turn switch outlive e-ink mode`() {
        val visible = visibleEinkSettings(state(einkModeEnabled = false))

        assertTrue(EinkSetting.MONOCHROME_ICON in visible)
        assertTrue(EinkSetting.HARDWARE_KEYS_ENABLED in visible)
    }

    @Test
    fun `an unsupported monochrome icon is never offered`() {
        for (einkMode in listOf(false, true)) {
            val visible = visibleEinkSettings(
                state(einkModeEnabled = einkMode, monochromeIconSupported = false)
            )
            assertFalse(EinkSetting.MONOCHROME_ICON in visible, "einkMode=$einkMode")
        }
    }

    @Test
    fun `bindings and overlap appear only once hardware buttons are on`() {
        val off = visibleEinkSettings(state(hardwareKeysEnabled = false))
        assertFalse(EinkSetting.USE_VOLUME_KEYS in off)
        assertFalse(EinkSetting.BIND_PREVIOUS in off)
        assertFalse(EinkSetting.BIND_NEXT in off)
        assertFalse(EinkSetting.PAGE_OVERLAP in off)

        val on = visibleEinkSettings(state(hardwareKeysEnabled = true))
        assertTrue(EinkSetting.USE_VOLUME_KEYS in on)
        assertTrue(EinkSetting.BIND_PREVIOUS in on)
        assertTrue(EinkSetting.BIND_NEXT in on)
        assertTrue(EinkSetting.PAGE_OVERLAP in on)
    }

    @Test
    fun `invert only follows the volume preset being on`() {
        assertFalse(
            EinkSetting.INVERT_VOLUME_KEYS in
                visibleEinkSettings(state(hardwareKeysEnabled = true, useVolumeKeys = false))
        )
        assertTrue(
            EinkSetting.INVERT_VOLUME_KEYS in
                visibleEinkSettings(state(hardwareKeysEnabled = true, useVolumeKeys = true))
        )
        // useVolumeKeys can stay true in storage after the buttons are switched off wholesale.
        assertFalse(
            EinkSetting.INVERT_VOLUME_KEYS in
                visibleEinkSettings(state(hardwareKeysEnabled = false, useVolumeKeys = true))
        )
    }

    @Test
    fun `an empty group takes its heading with it`() {
        val groups = visibleEinkGroups(state(einkModeEnabled = false)).map { it.first }

        assertFalse(EinkSettingGroup.MOTION in groups)
        assertFalse(EinkSettingGroup.INTERACTION in groups)
        // Display survives on the monochrome icon alone, page turns are never gated on e-ink mode.
        assertEquals(listOf(EinkSettingGroup.DISPLAY, EinkSettingGroup.PAGE_TURN_BUTTONS), groups)
    }

    @Test
    fun `display drops out too when the icon is unsupported and e-ink mode is off`() {
        val groups = visibleEinkGroups(
            state(einkModeEnabled = false, monochromeIconSupported = false)
        ).map { it.first }

        assertEquals(listOf(EinkSettingGroup.PAGE_TURN_BUTTONS), groups)
    }

    @Test
    fun `groups render in declaration order and hold only their own settings`() {
        val groups = visibleEinkGroups(
            state(einkModeEnabled = true, hardwareKeysEnabled = true, useVolumeKeys = true)
        )

        assertEquals(EinkSettingGroup.entries.toList(), groups.map { it.first })
        groups.forEach { (group, settings) ->
            assertTrue(settings.isNotEmpty(), "$group rendered empty")
            assertTrue(settings.all { it.group == group }, "$group holds a foreign setting")
        }
    }

    @Test
    fun `grouping never drops or duplicates a visible setting`() {
        for (einkMode in listOf(false, true)) {
            for (iconSupported in listOf(false, true)) {
                for (keysEnabled in listOf(false, true)) {
                    for (volumeKeys in listOf(false, true)) {
                        val current = state(einkMode, iconSupported, keysEnabled, volumeKeys)
                        assertEquals(
                            visibleEinkSettings(current),
                            visibleEinkGroups(current).flatMap { it.second },
                            "lost a setting for $current"
                        )
                    }
                }
            }
        }
    }
}
